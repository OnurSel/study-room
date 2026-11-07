package com.studyroom.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for Redis-backed, short-lived study rooms. */
class RoomServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final UUID CREATOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PREFIX = "studyroom:room:";

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, String> storage = new HashMap<>();
    private RedisTemplate<String, String> redis;
    private ValueOperations<String, String> values;
    private RoomService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        storage.clear();
        redis = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class)))
                .thenAnswer(call -> {
                    String key = call.getArgument(0);
                    String value = call.getArgument(1);
                    return storage.putIfAbsent(key, value) == null;
                });
        when(values.get(anyString())).thenAnswer(call -> storage.get(call.getArgument(0)));
        when(redis.delete(anyString())).thenAnswer(call -> storage.remove(call.getArgument(0)) != null);
        service = new RoomService(redis, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsRoomWithUniqueCodeAndTwentyFourHourExpiry() {
        Room room = service.createRoom(CREATOR);

        assertTrue(room.code().matches("[A-Z0-9]{8}"));
        assertEquals(CREATOR, room.creatorId());
        assertEquals(NOW, room.createdAt());
        assertEquals(NOW.plusSeconds(24 * 60 * 60), room.expiresAt());
        assertEquals(room, service.findRoom(room.code()).orElseThrow());
        verify(values).setIfAbsent(eq(PREFIX + room.code()), anyString(),
                eq(java.time.Duration.ofHours(24)));
    }

    @Test
    void normalizesRoomCodeOnLookup() {
        Room room = service.createRoom(CREATOR);

        assertEquals(Optional.of(room), service.findRoom("  " + room.code().toLowerCase() + "  "));
        assertTrue(service.roomExists(room.code().toLowerCase()));
    }

    @Test
    void missingAndDeletedRoomsAreNotFound() {
        assertTrue(service.findRoom("UNKNOWN1").isEmpty());
        Room room = service.createRoom(CREATOR);

        assertTrue(service.deleteRoom(room.code()));
        assertFalse(service.deleteRoom(room.code()));
        assertFalse(service.roomExists(room.code()));
    }

    @Test
    void expiredRoomIsDiscardedEvenIfRedisStillContainsIt() throws Exception {
        Room expired = new Room("EXPIRED1", CREATOR, NOW.minusSeconds(7200), NOW.minusSeconds(1));
        storage.put(PREFIX + expired.code(), mapper.writeValueAsString(expired));

        assertTrue(service.findRoom(expired.code()).isEmpty());
        assertFalse(storage.containsKey(PREFIX + expired.code()));
    }

    @Test
    void invalidCodesAndNullCreatorAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.findRoom("bad!"));
        assertThrows(IllegalArgumentException.class, () -> service.findRoom(" "));
        assertThrows(IllegalArgumentException.class, () -> service.findRoom(null));
        assertThrows(NullPointerException.class, () -> service.createRoom(null));
    }

    @Test
    void rejectsRoomCreationWhenRedisDoesNotConfirmTheWrite() {
        when(values.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class)))
                .thenReturn(null);

        assertThrows(IllegalStateException.class, () -> service.createRoom(CREATOR));
    }

    @Test
    void rejectsRoomCreationAfterRepeatedCodeCollisions() {
        when(values.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class)))
                .thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.createRoom(CREATOR));
        verify(values, times(12)).setIfAbsent(anyString(), anyString(), any(java.time.Duration.class));
    }
}
