package com.studyroom.timer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyroom.room.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for timer synchronization and Redis compare-and-set behavior. */
class TimerServiceTest {

    private static final String ROOM = "ABC123";
    private static final String KEY = "studyroom:timer:" + ROOM;
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AtomicReference<String> stored = new AtomicReference<>();

    private RedisTemplate<String, String> redis;
    private ValueOperations<String, String> values;
    private RoomService rooms;
    private SimpMessagingTemplate messaging;
    private Clock clock;
    private TimerService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stored.set(null);
        redis = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        rooms = mock(RoomService.class);
        messaging = mock(SimpMessagingTemplate.class);
        clock = mock(Clock.class);

        when(rooms.roomExists(ROOM)).thenReturn(true);
        when(clock.instant()).thenReturn(NOW);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(KEY)).thenAnswer(invocation -> stored.get());
        when(values.setIfAbsent(eq(KEY), anyString(), any(Duration.class)))
                .thenAnswer(invocation -> stored.compareAndSet(null, invocation.getArgument(1)));
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                anyList(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    List<String> keys = invocation.getArgument(1);
                    assertEquals(List.of(KEY), keys);
                    String previous = invocation.getArgument(2);
                    String next = invocation.getArgument(3);
                    return stored.compareAndSet(previous, next) ? 1L : 0L;
                });

        service = new TimerService(redis, mapper, rooms, messaging, clock);
    }

    @Test
    void newRoomGetsIdleFocusTimer() {
        TimerState state = service.getState(ROOM);

        assertEquals(TimerState.Phase.FOCUS, state.phase());
        assertEquals(TimerState.Status.IDLE, state.status());
        assertEquals(25 * 60, state.remainingSeconds());
        assertEquals(0L, state.version());
        verifyNoInteractions(messaging);
    }

    @Test
    void startIsIdempotentAndBroadcastsOnlyOneChange() {
        TimerState started = service.start(ROOM);
        TimerState repeated = service.start(ROOM);

        assertEquals(TimerState.Status.RUNNING, started.status());
        assertEquals(NOW.plusSeconds(25 * 60), started.endsAt());
        assertEquals(1L, started.version());
        assertEquals(started, repeated);
        verify(messaging, times(1)).convertAndSend("/topic/rooms/" + ROOM + "/timer", started);
    }

    @Test
    void pausePreservesServerCalculatedRemainingTime() {
        service.start(ROOM);
        when(clock.instant()).thenReturn(NOW.plusSeconds(65));

        TimerState paused = service.pause(ROOM);

        assertEquals(TimerState.Status.PAUSED, paused.status());
        assertEquals(25 * 60 - 65, paused.remainingSeconds());
        assertNull(paused.endsAt());
        assertEquals(2L, paused.version());
    }

    @Test
    void expiredRunningTimerCompletesOnStateRequest() {
        service.start(ROOM);
        when(clock.instant()).thenReturn(NOW.plusSeconds(25 * 60 + 1));

        TimerState completed = service.getState(ROOM);

        assertEquals(TimerState.Status.COMPLETED, completed.status());
        assertEquals(0, completed.remainingSeconds());
        assertEquals(2L, completed.version());
    }

    @Test
    void unknownRoomCannotCreateTimer() {
        when(rooms.roomExists(ROOM)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.start(ROOM));
        assertNull(stored.get());
        verifyNoInteractions(messaging);
    }
}
