package com.studyroom.room;

import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Creates and retrieves short-lived study room metadata in Redis.
 *
 * <p>Each room has an unguessable-enough invitation code for casual sharing,
 * but the code is not an authorization credential. Controllers and socket
 * handlers must authenticate participants and enforce room membership.</p>
 *
 * <p>Room creation uses Redis SET NX with a TTL so concurrent requests cannot
 * claim the same code. Presence-driven expiry extensions can be added later.</p>
 */
@Service
public class RoomService {

    private static final String ROOM_KEY_PREFIX = "studyroom:room:";
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final int MAX_CREATE_ATTEMPTS = 12;
    static final int MAX_NAME_LENGTH = 40;
    private static final Duration ROOM_LIFETIME = Duration.ofHours(24);

    private final RedisTemplate<String, String> redis;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public RoomService(@Qualifier("roomRedisTemplate") RedisTemplate<String, String> redis,
                       ObjectMapper objectMapper) {
        this(redis, objectMapper, Clock.systemUTC());
    }

    // Package-private constructor allows deterministic testing with a fixed clock.
    RoomService(RedisTemplate<String, String> redis, ObjectMapper objectMapper, Clock clock) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Creates a room owned by an authenticated anonymous participant. */
    public Room createRoom(UUID creatorId) {
        return createRoom(creatorId, null);
    }

    /** Creates a room with an optional human-friendly name (trimmed, max 40 chars). */
    public Room createRoom(UUID creatorId, String name) {
        Objects.requireNonNull(creatorId, "creatorId");
        String cleanName = cleanName(name);
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(ROOM_LIFETIME);

        for (int attempt = 0; attempt < MAX_CREATE_ATTEMPTS; attempt++) {
            Room room = new Room(newCode(), creatorId, createdAt, expiresAt, cleanName);
            Boolean created = redis.opsForValue().setIfAbsent(
                    redisKey(room.code()), serialize(room), ROOM_LIFETIME);
            if (Boolean.TRUE.equals(created)) {
                return room;
            }
            if (created == null) {
                throw new IllegalStateException("Redis did not confirm room creation");
            }
        }
        throw new IllegalStateException("Unable to allocate a unique room code");
    }

    /** Returns an active room, or empty if it was never created or has expired. */
    public Optional<Room> findRoom(String code) {
        String normalizedCode = normalizeCode(code);
        String json = redis.opsForValue().get(redisKey(normalizedCode));
        if (json == null) {
            return Optional.empty();
        }

        Room room = deserialize(json);
        if (room.isExpired(clock.instant())) {
            redis.delete(redisKey(normalizedCode));
            return Optional.empty();
        }
        return Optional.of(room);
    }

    public boolean roomExists(String code) {
        return findRoom(code).isPresent();
    }

    /** Removes room metadata. Associated task/presence cleanup belongs to their services. */
    public boolean deleteRoom(String code) {
        return Boolean.TRUE.equals(redis.delete(redisKey(normalizeCode(code))));
    }

    static String cleanName(String name) {
        if (name == null) {
            return null;
        }
        String cleaned = name.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(0, MAX_NAME_LENGTH).trim() : cleaned;
    }

    private String newCode() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder result = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            result.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return result.toString();
    }

    private static String normalizeCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Room code is required");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{6,12}")) {
            throw new IllegalArgumentException("Invalid room code format");
        }
        return normalized;
    }

    private static String redisKey(String code) {
        return ROOM_KEY_PREFIX + code;
    }

    private String serialize(Room room) {
        try {
            return objectMapper.writeValueAsString(room);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize room metadata", e);
        }
    }

    private Room deserialize(String json) {
        try {
            return objectMapper.readValue(json, Room.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not deserialize room metadata", e);
        }
    }
}
