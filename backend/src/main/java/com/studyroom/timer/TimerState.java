package com.studyroom.timer;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable, server-authoritative snapshot of one room's shared Pomodoro timer.
 *
 * <p>When RUNNING, {@code endsAt} is the authoritative deadline. Clients must
 * calculate their display from that deadline and their estimated server clock
 * offset, not decrement a local counter as their source of truth.</p>
 *
 * <p>{@code remainingSeconds} is the saved duration when not running. During a
 * running session it is the duration captured at the last start/resume; use
 * {@link #remainingAt(Instant)} to obtain the current remaining time.</p>
 */
public record TimerState(
        String roomCode,
        Phase phase,
        Status status,
        int durationSeconds,
        int remainingSeconds,
        Instant startedAt,
        Instant endsAt,
        long version
) {
    public static final int DEFAULT_FOCUS_SECONDS = 25 * 60;
    public static final int DEFAULT_SHORT_BREAK_SECONDS = 5 * 60;
    public static final int DEFAULT_LONG_BREAK_SECONDS = 15 * 60;
    public static final int MAX_DURATION_SECONDS = 4 * 60 * 60;

    public enum Phase { FOCUS, SHORT_BREAK, LONG_BREAK }

    public enum Status { IDLE, RUNNING, PAUSED, COMPLETED }

    public TimerState {
        if (roomCode == null || !roomCode.matches("[A-Z0-9]{6,12}")) {
            throw new IllegalArgumentException("Invalid room code");
        }
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(status, "status");
        if (durationSeconds <= 0 || durationSeconds > MAX_DURATION_SECONDS) {
            throw new IllegalArgumentException("Duration is out of range");
        }
        if (remainingSeconds < 0 || remainingSeconds > durationSeconds) {
            throw new IllegalArgumentException("Remaining time is out of range");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Version cannot be negative");
        }
        if (status == Status.RUNNING) {
            Objects.requireNonNull(startedAt, "startedAt required while running");
            Objects.requireNonNull(endsAt, "endsAt required while running");
            if (remainingSeconds == 0 || !endsAt.isAfter(startedAt)) {
                throw new IllegalArgumentException("Running timer requires a future deadline");
            }
            if (!endsAt.equals(startedAt.plusSeconds(remainingSeconds))) {
                throw new IllegalArgumentException("Deadline does not match remaining duration");
            }
        } else if (startedAt != null || endsAt != null) {
            throw new IllegalArgumentException("Inactive timer cannot have running timestamps");
        }
        if (status == Status.COMPLETED && remainingSeconds != 0) {
            throw new IllegalArgumentException("Completed timer must have zero remaining");
        }
    }

    public static TimerState initial(String roomCode) {
        return new TimerState(roomCode, Phase.FOCUS, Status.IDLE,
                DEFAULT_FOCUS_SECONDS, DEFAULT_FOCUS_SECONDS, null, null, 0);
    }

    /** Remaining whole seconds, rounded up so displays do not skip a second. */
    public int remainingAt(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status != Status.RUNNING) {
            return remainingSeconds;
        }
        long millis = Duration.between(now, endsAt).toMillis();
        return (int) Math.max(0, Math.min(durationSeconds, (millis + 999) / 1000));
    }

    /** Starts or resumes the current phase; already-running requests are idempotent. */
    public TimerState start(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status == Status.RUNNING) {
            return completeIfElapsed(now);
        }
        int seconds = status == Status.COMPLETED ? durationSeconds : remainingSeconds;
        return new TimerState(roomCode, phase, Status.RUNNING, durationSeconds,
                seconds, now, now.plusSeconds(seconds), version + 1);
    }

    public TimerState pause(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status != Status.RUNNING) {
            return this;
        }
        int seconds = remainingAt(now);
        return new TimerState(roomCode, phase,
                seconds == 0 ? Status.COMPLETED : Status.PAUSED,
                durationSeconds, seconds, null, null, version + 1);
    }

    /** Marks a running phase completed once its deadline has passed. */
    public TimerState completeIfElapsed(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status != Status.RUNNING || now.isBefore(endsAt)) {
            return this;
        }
        return new TimerState(roomCode, phase, Status.COMPLETED,
                durationSeconds, 0, null, null, version + 1);
    }

    /** Resets the current phase without starting it. */
    public TimerState reset() {
        if (status == Status.IDLE && remainingSeconds == durationSeconds) {
            return this;
        }
        return new TimerState(roomCode, phase, Status.IDLE,
                durationSeconds, durationSeconds, null, null, version + 1);
    }

    /** Switches phase and resets its timer; starting remains a separate action. */
    public TimerState switchTo(Phase nextPhase) {
        Objects.requireNonNull(nextPhase, "nextPhase");
        int seconds = switch (nextPhase) {
            case FOCUS -> DEFAULT_FOCUS_SECONDS;
            case SHORT_BREAK -> DEFAULT_SHORT_BREAK_SECONDS;
            case LONG_BREAK -> DEFAULT_LONG_BREAK_SECONDS;
        };
        return new TimerState(roomCode, nextPhase, Status.IDLE,
                seconds, seconds, null, null, version + 1);
    }
}
