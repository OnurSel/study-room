package com.studyroom.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyroom.room.Room;
import com.studyroom.room.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit-level contract tests for shared task mutations and optimistic versioning. */
class TaskServiceTest {

    private static final String CODE = "ABC123";
    private static final String KEY = "studyroom:tasks:" + CODE;
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, String> storage = new HashMap<>();
    private RedisTemplate<String, String> redis;
    private HashOperations<String, String, String> hashes;
    private RoomService rooms;
    private SimpMessagingTemplate messaging;
    private TaskService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        storage.clear();
        redis = mock(RedisTemplate.class);
        hashes = mock(HashOperations.class);
        rooms = mock(RoomService.class);
        messaging = mock(SimpMessagingTemplate.class);
        when(rooms.findRoom(CODE)).thenReturn(Optional.of(
                new Room(CODE, USER, NOW.minusSeconds(60), NOW.plusSeconds(3600))));
        doReturn(hashes).when(redis).opsForHash();
        when(hashes.get(eq(KEY), anyString())).thenAnswer(call -> storage.get(call.getArgument(1)));
        when(hashes.values(KEY)).thenAnswer(call -> List.copyOf(storage.values()));

        // Simulate the Redis Lua create/update results (both take 3 args; update's 3rd is task JSON).
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenAnswer(call -> {
                    assertEquals(List.of(KEY), call.getArgument(1));
                    String id = call.getArgument(2);
                    String second = call.getArgument(3);
                    String third = call.getArgument(4);
                    if (third.startsWith("{")) { // UPDATE: id, expectedVersion, nextJson
                        long expected = Long.parseLong(second);
                        String old = storage.get(id);
                        if (old == null) return -1L;
                        if (mapper.readValue(old, Task.class).version() != expected) return 0L;
                        storage.put(id, third);
                        return 1L;
                    }
                    // CREATE: id, json, expiresAtEpoch
                    if (storage.containsKey(id)) return 0L;
                    storage.put(id, second);
                    assertEquals(Long.toString(NOW.plusSeconds(3600).getEpochSecond()), third);
                    return 1L;
                });
        // DELETE: id, expectedVersion
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenAnswer(call -> {
                    assertEquals(List.of(KEY), call.getArgument(1));
                    String id = call.getArgument(2);
                    long expected = Long.parseLong(call.getArgument(3));
                    String old = storage.get(id);
                    if (old == null) return -1L;
                    if (mapper.readValue(old, Task.class).version() != expected) return 0L;
                    storage.remove(id);
                    return 1L;
                });

        service = new TaskService(redis, mapper, rooms, messaging,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void addTaskCreatesAnIncompleteVersionZeroTaskAndPublishesEvent() {
        Task created = service.addTask(CODE, "  Read chapter 1  ", USER);

        assertEquals("Read chapter 1", created.title());
        assertFalse(created.completed());
        assertEquals(0L, created.version());
        assertEquals(USER, created.createdBy());
        assertEquals(NOW, created.createdAt());
        assertEquals(List.of(created), service.listTasks(CODE));
        verify(messaging).convertAndSend(eq("/topic/rooms/" + CODE + "/tasks"),
                argThat((TaskService.TaskEvent event) ->
                        "CREATED".equals(event.type()) && created.equals(event.payload())));
    }

    @Test
    void completingTaskIncrementsVersionAndRejectsStaleWrites() {
        Task task = service.addTask(CODE, "Practice questions", USER);
        Task completed = service.setCompleted(CODE, task.id(), true, task.version());

        assertTrue(completed.completed());
        assertEquals(1L, completed.version());
        assertEquals(completed, service.listTasks(CODE).getFirst());
        assertThrows(TaskService.TaskConflictException.class,
                () -> service.setCompleted(CODE, task.id(), false, 0L));
        assertThrows(TaskService.TaskConflictException.class,
                () -> service.renameTask(CODE, task.id(), "Changed", 0L));
    }

    @Test
    void renameTaskTrimsTitleAndPreservesTaskIdentity() {
        Task task = service.addTask(CODE, "Original", USER);
        Task renamed = service.renameTask(CODE, task.id(), "  Updated title  ", 0L);

        assertEquals(task.id(), renamed.id());
        assertEquals("Updated title", renamed.title());
        assertEquals(1L, renamed.version());
        assertEquals(task.createdAt(), renamed.createdAt());
    }

    @Test
    void unchangedCompletionDoesNotChangeVersionOrBroadcastUpdate() {
        Task task = service.addTask(CODE, "Focus", USER);
        clearInvocations(messaging);

        Task unchanged = service.setCompleted(CODE, task.id(), false, 0L);

        assertEquals(task, unchanged);
        verifyNoInteractions(messaging);
    }

    @Test
    void removeTaskChecksVersionAndRemovesOnlyOnMatchingVersion() {
        Task task = service.addTask(CODE, "Disposable", USER);
        Task updated = service.setCompleted(CODE, task.id(), true, 0L);

        assertThrows(TaskService.TaskConflictException.class,
                () -> service.removeTask(CODE, task.id(), 0L));
        assertEquals(1, service.listTasks(CODE).size());

        service.removeTask(CODE, updated.id(), updated.version());
        assertTrue(service.listTasks(CODE).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> service.removeTask(CODE, task.id(), 1L));
    }

    @Test
    void missingRoomAndInvalidTitlesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.addTask(CODE, "   ", USER));
        assertThrows(IllegalArgumentException.class,
                () -> service.addTask(CODE, "x".repeat(201), USER));
        assertThrows(IllegalArgumentException.class,
                () -> service.addTask("UNKNOWN", "Task", USER));
    }
}
