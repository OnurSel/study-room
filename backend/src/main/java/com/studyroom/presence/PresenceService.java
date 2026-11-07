package com.studyroom.presence;

import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyroom.room.Room;
import com.studyroom.room.RoomService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores each room's current participants in a Redis hash and publishes snapshots.
 *
 * <p>Only authenticated handlers should invoke mutating methods, passing the
 * participant UUID and display name from the verified token (never from an
 * untrusted request body). Socket connect/disconnect integration is handled
 * separately. The Redis hash is scoped to the lifetime of the parent room.</p>
 *
 * <p>Room capacity enforcement below is adequate for a single server instance;
 * a multi-instance deployment needs an atomic Redis script for join/capacity.</p>
 */
@Service
public class PresenceService {

    private static final String KEY_PREFIX = "studyroom:presence:";
    private static final int MAX_PARTICIPANTS = 20;

    private final RedisTemplate<String, String> redis;
    private final RoomService rooms;
    private final ObjectMapper mapper;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    @Autowired
    public PresenceService(
            @Qualifier("roomRedisTemplate") RedisTemplate<String, String> redis,
            RoomService rooms,
            ObjectMapper mapper,
            SimpMessagingTemplate messaging) {
        this(redis, rooms, mapper, messaging, Clock.systemUTC());
    }

    PresenceService(RedisTemplate<String, String> redis, RoomService rooms,
                    ObjectMapper mapper, SimpMessagingTemplate messaging, Clock clock) {
        this.redis = Objects.requireNonNull(redis);
        this.rooms = Objects.requireNonNull(rooms);
        this.mapper = Objects.requireNonNull(mapper);
        this.messaging = Objects.requireNonNull(messaging);
        this.clock = Objects.requireNonNull(clock);
    }

    public enum Status { JOINED, WORKING, ON_BREAK }

    public record Participant(UUID id, String displayName, Status status, Instant updatedAt) { }

    public record PresenceSnapshot(String roomCode, List<Participant> participants, Instant serverTime) { }

    /** Register a participant who has explicitly joined the room. */
    public synchronized PresenceSnapshot join(String roomCode, UUID participantId, String displayName) {
        Room room = requireRoom(roomCode);
        Objects.requireNonNull(participantId, "participantId");
        validateDisplayName(displayName);
        String key = key(room.code());
        String field = participantId.toString();

        Boolean existing = redis.opsForHash().hasKey(key, field);
        if (!Boolean.TRUE.equals(existing)) {
            Long count = redis.opsForHash().size(key);
            if (count != null && count >= MAX_PARTICIPANTS) {
                throw new IllegalStateException("Room is full");
            }
        }

        Participant participant = new Participant(participantId, displayName.trim(),
                Status.JOINED, clock.instant());
        redis.opsForHash().put(key, field, serialize(participant));
        alignExpiry(key, room);
        return publish(room.code());
    }

    /** Update an existing participant; callers must authenticate the UUID. */
    public PresenceSnapshot updateStatus(String roomCode, UUID participantId, Status status) {
        Room room = requireRoom(roomCode);
        Objects.requireNonNull(participantId, "participantId");
        Objects.requireNonNull(status, "status");
        String key = key(room.code());
        Object current = redis.opsForHash().get(key, participantId.toString());
        if (current == null) {
            throw new IllegalArgumentException("Participant has not joined this room");
        }
        Participant previous = deserialize(current.toString());
        Participant updated = new Participant(participantId, previous.displayName(), status, clock.instant());
        redis.opsForHash().put(key, participantId.toString(), serialize(updated));
        alignExpiry(key, room);
        return publish(room.code());
    }

    /** Remove a participant when they leave or their last connection closes. */
    public PresenceSnapshot leave(String roomCode, UUID participantId) {
        Room room = requireRoom(roomCode);
        Objects.requireNonNull(participantId, "participantId");
        redis.opsForHash().delete(key(room.code()), participantId.toString());
        return publish(room.code());
    }

    /** Return the current room presence without publishing an event. */
    public PresenceSnapshot snapshot(String roomCode) {
        Room room = requireRoom(roomCode);
        List<Participant> participants = redis.opsForHash().values(key(room.code())).stream()
                .map(value -> deserialize(value.toString()))
                .sorted(Comparator.comparing(Participant::displayName)
                        .thenComparing(Participant::id))
                .toList();
        return new PresenceSnapshot(room.code(), participants, clock.instant());
    }

    public boolean isMember(String roomCode, UUID participantId) {
        Room room = requireRoom(roomCode);
        Objects.requireNonNull(participantId, "participantId");
        return Boolean.TRUE.equals(redis.opsForHash()
                .hasKey(key(room.code()), participantId.toString()));
    }

    private PresenceSnapshot publish(String code) {
        PresenceSnapshot snapshot = snapshot(code);
        messaging.convertAndSend("/topic/rooms/" + code + "/presence", snapshot);
        return snapshot;
    }

    private Room requireRoom(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Room code is required");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return rooms.findRoom(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Room does not exist or has expired"));
    }

    private void alignExpiry(String presenceKey, Room room) {
        Duration remaining = Duration.between(clock.instant(), room.expiresAt());
        if (remaining.isPositive()) {
            redis.expire(presenceKey, remaining);
        }
    }

    private static void validateDisplayName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 40) {
            throw new IllegalArgumentException("Display name must contain 1 to 40 characters");
        }
    }

    private static String key(String code) {
        return KEY_PREFIX + code;
    }

    private String serialize(Participant participant) {
        try {
            return mapper.writeValueAsString(participant);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize participant", ex);
        }
    }

    private Participant deserialize(String json) {
        try {
            return mapper.readValue(json, Participant.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not deserialize participant", ex);
        }
    }
}
