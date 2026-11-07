package com.studyroom.timer;

import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyroom.room.RoomService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Redis-backed, server-authoritative timer shared by every participant in a room.
 *
 * <p>Mutations use a compare-and-swap Lua script so concurrent commands cannot
 * overwrite one another. Timer values are persisted as JSON and retain the
 * Redis key's TTL on updates. The server broadcasts each successful mutation;
 * reconnecting clients should request a fresh snapshot with {@link #getState}.</p>
 *
 * <p>Authorization is deliberately not handled here: the HTTP/STOMP entry
 * points must verify that the caller belongs to the requested room.</p>
 */
@Service
public class TimerService {

    private static final String TIMER_PREFIX = "studyroom:timer:";
    private static final Duration TIMER_TTL = Duration.ofHours(24);
    private static final int MAX_RETRIES = 16;
    private static final String TOPIC_PREFIX = "/topic/rooms/";

    private static final DefaultRedisScript<Long> COMPARE_AND_SET = new DefaultRedisScript<>(
            "local old = redis.call('GET', KEYS[1]); " +
            "if old ~= ARGV[1] then return 0 end; " +
            "redis.call('SET', KEYS[1], ARGV[2], 'KEEPTTL'); " +
            "return 1", Long.class);

    private final RedisTemplate<String, String> redis;
    private final ObjectMapper mapper;
    private final RoomService rooms;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    @Autowired
    public TimerService(@Qualifier("roomRedisTemplate") RedisTemplate<String, String> redis,
                        ObjectMapper mapper,
                        RoomService rooms,
                        SimpMessagingTemplate messaging) {
        this(redis, mapper, rooms, messaging, Clock.systemUTC());
    }

    TimerService(RedisTemplate<String, String> redis, ObjectMapper mapper,
                 RoomService rooms, SimpMessagingTemplate messaging, Clock clock) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.rooms = Objects.requireNonNull(rooms, "rooms");
        this.messaging = Objects.requireNonNull(messaging, "messaging");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Returns the latest authoritative state, completing an elapsed timer if needed. */
    public TimerState getState(String roomCode) {
        return mutate(roomCode, state -> state.completeIfElapsed(clock.instant()));
    }

    public TimerState start(String roomCode) {
        return mutate(roomCode, state -> state.start(clock.instant()));
    }

    public TimerState pause(String roomCode) {
        return mutate(roomCode, state -> state.pause(clock.instant()));
    }

    public TimerState reset(String roomCode) {
        return mutate(roomCode, TimerState::reset);
    }

    public TimerState switchPhase(String roomCode, TimerState.Phase phase) {
        Objects.requireNonNull(phase, "phase");
        return mutate(roomCode, state -> state.switchTo(phase));
    }

    private TimerState mutate(String roomCode, UnaryOperator<TimerState> change) {
        String code = normalize(roomCode);
        // Also ensures that an expired/nonexistent room cannot acquire a new timer.
        if (!rooms.roomExists(code)) {
            throw new IllegalArgumentException("Room does not exist or has expired");
        }

        String key = TIMER_PREFIX + code;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String before = redis.opsForValue().get(key);
            if (before == null) {
                TimerState initial = TimerState.initial(code);
                Boolean created = redis.opsForValue().setIfAbsent(
                        key, serialize(initial), TIMER_TTL);
                if (created == null) {
                    throw new IllegalStateException("Redis could not initialize timer");
                }
                // Re-read in case another caller initialized it concurrently.
                continue;
            }

            TimerState current = deserialize(before);
            TimerState updated = Objects.requireNonNull(change.apply(current), "updated state");
            if (updated.equals(current)) {
                return current;
            }
            Long success = redis.execute(COMPARE_AND_SET,
                    Collections.singletonList(key), before, serialize(updated));
            if (Long.valueOf(1).equals(success)) {
                messaging.convertAndSend(TOPIC_PREFIX + code + "/timer", updated);
                return updated;
            }
        }
        throw new IllegalStateException("Timer was updated too frequently; retry the request");
    }

    private static String normalize(String roomCode) {
        if (roomCode == null) {
            throw new IllegalArgumentException("Room code is required");
        }
        String code = roomCode.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9]{6,12}")) {
            throw new IllegalArgumentException("Invalid room code");
        }
        return code;
    }

    private String serialize(TimerState state) {
        try {
            return mapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize timer state", e);
        }
    }

    private TimerState deserialize(String json) {
        try {
            return mapper.readValue(json, TimerState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not deserialize timer state", e);
        }
    }
}
