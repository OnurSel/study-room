package com.studyroom.task;

import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyroom.room.Room;
import com.studyroom.room.RoomService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared task storage backed by a Redis hash (one hash per study room).
 *
 * <p>Each task is an immutable JSON snapshot. Lua scripts enforce optimistic
 * version checks atomically so simultaneous updates cannot silently overwrite
 * one another. Callers must enforce authentication and room membership before
 * invoking this service. Clients should re-fetch the list after reconnecting.</p>
 */
@Service
public class TaskService {

    private static final String TASKS_PREFIX = "studyroom:tasks:";
    private static final String TOPIC_PREFIX = "/topic/rooms/";

    private static final DefaultRedisScript<Long> CREATE = new DefaultRedisScript<>(
            "if redis.call('HSETNX', KEYS[1], ARGV[1], ARGV[2]) == 0 then return 0 end; " +
            "redis.call('EXPIREAT', KEYS[1], ARGV[3]); " +
            "return 1", Long.class);

    private static final DefaultRedisScript<Long> UPDATE = new DefaultRedisScript<>(
            "local previous = redis.call('HGET', KEYS[1], ARGV[1]); " +
            "if not previous then return -1 end; " +
            "local task = cjson.decode(previous); " +
            "if tonumber(task.version) ~= tonumber(ARGV[2]) then return 0 end; " +
            "redis.call('HSET', KEYS[1], ARGV[1], ARGV[3]); " +
            "return 1", Long.class);

    private static final DefaultRedisScript<Long> DELETE = new DefaultRedisScript<>(
            "local previous = redis.call('HGET', KEYS[1], ARGV[1]); " +
            "if not previous then return -1 end; " +
            "local task = cjson.decode(previous); " +
            "if tonumber(task.version) ~= tonumber(ARGV[2]) then return 0 end; " +
            "redis.call('HDEL', KEYS[1], ARGV[1]); " +
            "return 1", Long.class);

    private final RedisTemplate<String, String> redis;
    private final ObjectMapper mapper;
    private final RoomService rooms;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    @Autowired
    public TaskService(@Qualifier("roomRedisTemplate") RedisTemplate<String, String> redis,
                       ObjectMapper mapper, RoomService rooms,
                       SimpMessagingTemplate messaging) {
        this(redis, mapper, rooms, messaging, Clock.systemUTC());
    }

    TaskService(RedisTemplate<String, String> redis, ObjectMapper mapper,
                RoomService rooms, SimpMessagingTemplate messaging, Clock clock) {
        this.redis = Objects.requireNonNull(redis);
        this.mapper = Objects.requireNonNull(mapper);
        this.rooms = Objects.requireNonNull(rooms);
        this.messaging = Objects.requireNonNull(messaging);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Full authoritative snapshot, sorted in creation order. */
    public List<Task> listTasks(String roomCode) {
        Room room = requireRoom(roomCode);
        return redis.<String, String>opsForHash().values(key(room.code())).stream()
                .map(this::deserialize)
                .sorted(Comparator.comparing(Task::createdAt).thenComparing(Task::id))
                .toList();
    }

    public Task addTask(String roomCode, String title, UUID creatorId) {
        Room room = requireRoom(roomCode);
        Task task = Task.create(room.code(), title, creatorId, clock.instant());
        Long result = redis.execute(CREATE, List.of(key(room.code())), task.id().toString(),
                serialize(task), Long.toString(room.expiresAt().getEpochSecond()));
        if (!Long.valueOf(1).equals(result)) {
            throw new IllegalStateException("Could not create the task");
        }
        broadcast(room.code(), "CREATED", task);
        return task;
    }

    public Task setCompleted(String roomCode, UUID taskId, boolean completed, long expectedVersion) {
        return update(roomCode, taskId, expectedVersion,
                task -> task.withCompleted(completed, clock.instant()));
    }

    public Task renameTask(String roomCode, UUID taskId, String title, long expectedVersion) {
        return update(roomCode, taskId, expectedVersion,
                task -> task.withTitle(title, clock.instant()));
    }

    public void removeTask(String roomCode, UUID taskId, long expectedVersion) {
        Room room = requireRoom(roomCode);
        validateVersion(expectedVersion);
        Objects.requireNonNull(taskId, "taskId");
        Long outcome = redis.execute(DELETE, List.of(key(room.code())), taskId.toString(),
                Long.toString(expectedVersion));
        checkOutcome(outcome);
        broadcast(room.code(), "DELETED", new TaskDeleted(taskId, expectedVersion));
    }

    private Task update(String roomCode, UUID taskId, long expectedVersion,
                        java.util.function.UnaryOperator<Task> mutation) {
        Room room = requireRoom(roomCode);
        validateVersion(expectedVersion);
        Objects.requireNonNull(taskId, "taskId");
        String raw = redis.<String, String>opsForHash().get(key(room.code()), taskId.toString());
        if (raw == null) {
            throw new IllegalArgumentException("Task does not exist");
        }
        Task previous = deserialize(raw);
        if (previous.version() != expectedVersion) {
            throw new TaskConflictException("Task was modified; refresh and retry");
        }
        Task updated = mutation.apply(previous);
        if (updated == previous) {
            // Still verify that the version is current; no stale no-op success.
            String current = redis.<String, String>opsForHash().get(key(room.code()), taskId.toString());
            if (current == null) {
                throw new IllegalArgumentException("Task does not exist");
            }
            if (deserialize(current).version() != expectedVersion) {
                throw new TaskConflictException("Task was modified; refresh and retry");
            }
            return previous;
        }
        Long outcome = redis.execute(UPDATE, List.of(key(room.code())), taskId.toString(),
                Long.toString(expectedVersion), serialize(updated));
        checkOutcome(outcome);
        broadcast(room.code(), "UPDATED", updated);
        return updated;
    }

    private Room requireRoom(String roomCode) {
        if (roomCode == null) {
            throw new IllegalArgumentException("Room code is required");
        }
        String code = roomCode.trim().toUpperCase(Locale.ROOT);
        return rooms.findRoom(code)
                .orElseThrow(() -> new IllegalArgumentException("Room does not exist or has expired"));
    }

    private static void validateVersion(long version) {
        if (version < 0) {
            throw new IllegalArgumentException("Expected version must not be negative");
        }
    }

    private static void checkOutcome(Long result) {
        if (Long.valueOf(-1).equals(result)) {
            throw new IllegalArgumentException("Task does not exist");
        }
        if (Long.valueOf(0).equals(result)) {
            throw new TaskConflictException("Task was modified; refresh and retry");
        }
        if (!Long.valueOf(1).equals(result)) {
            throw new IllegalStateException("Redis did not confirm the task mutation");
        }
    }

    private void broadcast(String roomCode, String type, Object payload) {
        messaging.convertAndSend(TOPIC_PREFIX + roomCode + "/tasks",
                new TaskEvent(type, payload, clock.instant()));
    }

    private String serialize(Task task) {
        try {
            return mapper.writeValueAsString(task);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize task", e);
        }
    }

    private Task deserialize(String json) {
        try {
            return mapper.readValue(json, Task.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialize task", e);
        }
    }

    private static String key(String roomCode) {
        return TASKS_PREFIX + roomCode;
    }

    public record TaskEvent(String type, Object payload, Instant serverTime) {}
    public record TaskDeleted(UUID id, long previousVersion) {}

    public static class TaskConflictException extends RuntimeException {
        public TaskConflictException(String message) {
            super(message);
        }
    }
}
