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
 *
 * <p>Per-room settings ({@code focusSeconds}, {@code shortBreakSeconds},
 * {@code longBreakSeconds}, {@code autoAdvance}) travel with the state. Older
 * stored states without these fields fall back to the defaults.</p>
 */
public record TimerState(
        String roomCode,
        Phase phase,
        Status status,
        int durationSeconds,
        int remainingSeconds,
        Instant startedAt,
        Instant endsAt,
        long version,
        int focusSeconds,
        int shortBreakSeconds,
        int longBreakSeconds,
        boolean autoAdvance,
        int completedFocus,
        long totalFocusSeconds
) {
    public static final int DEFAULT_FOCUS_SECONDS = 25 * 60;
    public static final int DEFAULT_SHORT_BREAK_SECONDS = 5 * 60;
    public static final int DEFAULT_LONG_BREAK_SECONDS = 15 * 60;
    public static final int MAX_DURATION_SECONDS = 4 * 60 * 60;

    /** A long break follows every Nth completed focus session. */
    public static final int FOCUS_SESSIONS_PER_CYCLE = 4;

    public static final int MAX_FOCUS_MINUTES = 180;
    public static final int MAX_SHORT_BREAK_MINUTES = 60;
    public static final int MAX_LONG_BREAK_MINUTES = 120;

    /** Safety cap when catching up several automatically advanced phases. */
    private static final int MAX_CATCH_UP_STEPS = 200;

    public enum Phase { FOCUS, SHORT_BREAK, LONG_BREAK }

    public enum Status { IDLE, RUNNING, PAUSED, COMPLETED }

    public TimerState {
        if (roomCode == null || !roomCode.matches("[A-Z0-9]{6,12}")) {
            throw new IllegalArgumentException("Invalid room code");
        }
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(status, "status");
        // Backwards compatibility with states stored before settings existed.
        if (focusSeconds <= 0) focusSeconds = DEFAULT_FOCUS_SECONDS;
        if (shortBreakSeconds <= 0) shortBreakSeconds = DEFAULT_SHORT_BREAK_SECONDS;
        if (longBreakSeconds <= 0) longBreakSeconds = DEFAULT_LONG_BREAK_SECONDS;
        if (completedFocus < 0 || totalFocusSeconds < 0) {
            throw new IllegalArgumentException("Counters cannot be negative");
        }
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
                DEFAULT_FOCUS_SECONDS, DEFAULT_FOCUS_SECONDS, null, null, 0,
                DEFAULT_FOCUS_SECONDS, DEFAULT_SHORT_BREAK_SECONDS, DEFAULT_LONG_BREAK_SECONDS,
                false, 0, 0);
    }

    /** Configured length of the given phase in this room. */
    public int secondsFor(Phase p) {
        return switch (p) {
            case FOCUS -> focusSeconds;
            case SHORT_BREAK -> shortBreakSeconds;
            case LONG_BREAK -> longBreakSeconds;
        };
    }

    /** The phase that follows the current one in the Pomodoro cycle. */
    public Phase nextPhase() {
        if (phase != Phase.FOCUS) {
            return Phase.FOCUS;
        }
        // completedFocus already includes the session that is being finished/has finished.
        return completedFocus > 0 && completedFocus % FOCUS_SESSIONS_PER_CYCLE == 0
                ? Phase.LONG_BREAK : Phase.SHORT_BREAK;
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

    /**
     * Starts or resumes the current phase. If the previous phase has completed,
     * the next phase of the cycle begins. Already-running requests are idempotent.
     */
    public TimerState start(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status == Status.RUNNING) {
            return completeIfElapsed(now);
        }
        if (status == Status.COMPLETED) {
            return switchTo(nextPhase()).runFrom(now);
        }
        return runFrom(now);
    }

    public TimerState pause(Instant now) {
        Objects.requireNonNull(now, "now");
        TimerState current = completeIfElapsed(now);
        if (current.status != Status.RUNNING) {
            return current;
        }
        int seconds = current.remainingAt(now);
        return new TimerState(roomCode, phase, Status.PAUSED,
                durationSeconds, seconds, null, null, current.version + 1,
                focusSeconds, shortBreakSeconds, longBreakSeconds, autoAdvance,
                completedFocus, totalFocusSeconds);
    }

    /**
     * Marks a running phase completed once its deadline has passed. With
     * {@code autoAdvance}, the following phases start back-to-back from the exact
     * previous deadline, so a client that polls late still sees the right phase.
     */
    public TimerState completeIfElapsed(Instant now) {
        Objects.requireNonNull(now, "now");
        TimerState state = this;
        int steps = 0;
        while (state.status == Status.RUNNING && !now.isBefore(state.endsAt)) {
            Instant boundary = state.endsAt;
            TimerState finished = state.finishPhase();
            if (!state.autoAdvance || ++steps > MAX_CATCH_UP_STEPS) {
                return finished;
            }
            state = finished.switchTo(finished.nextPhase()).runFrom(boundary);
        }
        return state;
    }

    /** Resets the current phase (using the room's current settings) without starting it. */
    public TimerState reset() {
        int seconds = secondsFor(phase);
        if (status == Status.IDLE && remainingSeconds == seconds && durationSeconds == seconds) {
            return this;
        }
        return new TimerState(roomCode, phase, Status.IDLE,
                seconds, seconds, null, null, version + 1,
                focusSeconds, shortBreakSeconds, longBreakSeconds, autoAdvance,
                completedFocus, totalFocusSeconds);
    }

    /** Switches phase and resets its timer; starting remains a separate action. */
    public TimerState switchTo(Phase nextPhase) {
        Objects.requireNonNull(nextPhase, "nextPhase");
        int seconds = secondsFor(nextPhase);
        return new TimerState(roomCode, nextPhase, Status.IDLE,
                seconds, seconds, null, null, version + 1,
                focusSeconds, shortBreakSeconds, longBreakSeconds, autoAdvance,
                completedFocus, totalFocusSeconds);
    }

    /**
     * Applies new room settings. An idle timer adopts the new length of its phase
     * immediately; a running or paused phase keeps its countdown and the new
     * lengths apply from the next phase or the next reset.
     */
    public TimerState withSettings(int focusMinutes, int shortBreakMinutes,
                                   int longBreakMinutes, boolean auto) {
        requireMinutes("Focus", focusMinutes, MAX_FOCUS_MINUTES);
        requireMinutes("Short break", shortBreakMinutes, MAX_SHORT_BREAK_MINUTES);
        requireMinutes("Long break", longBreakMinutes, MAX_LONG_BREAK_MINUTES);
        int focus = focusMinutes * 60;
        int shortBreak = shortBreakMinutes * 60;
        int longBreak = longBreakMinutes * 60;
        if (focus == focusSeconds && shortBreak == shortBreakSeconds
                && longBreak == longBreakSeconds && auto == autoAdvance) {
            return this;
        }
        TimerState updated = new TimerState(roomCode, phase, status,
                durationSeconds, remainingSeconds, startedAt, endsAt, version + 1,
                focus, shortBreak, longBreak, auto, completedFocus, totalFocusSeconds);
        return status == Status.IDLE ? updated.resetKeepingVersion() : updated;
    }

    private TimerState resetKeepingVersion() {
        int seconds = secondsFor(phase);
        return new TimerState(roomCode, phase, Status.IDLE,
                seconds, seconds, null, null, version,
                focusSeconds, shortBreakSeconds, longBreakSeconds, autoAdvance,
                completedFocus, totalFocusSeconds);
    }

    private static void requireMinutes(String label, int minutes, int max) {
        if (minutes < 1 || minutes > max) {
            throw new IllegalArgumentException(label + " must be between 1 and " + max + " minutes");
        }
    }

    /** Starts the current (idle/paused) phase at {@code from}. */
    private TimerState runFrom(Instant from) {
        int seconds = remainingSeconds;
        return new TimerState(roomCode, phase, Status.RUNNING, durationSeconds,
                seconds, from, from.plusSeconds(seconds), version + 1,
                focusSeconds, shortBreakSeconds, longBreakSeconds, autoAdvance,
                completedFocus, totalFocusSeconds);
    }

    /** Converts an elapsed running phase to COMPLETED, crediting finished focus time. */
    private TimerState finishPhase() {
        boolean focus = phase == Phase.FOCUS;
        return new TimerState(roomCode, phase, Status.COMPLETED,
                durationSeconds, 0, null, null, version + 1,
                focusSeconds, shortBreakSeconds, longBreakSeconds, autoAdvance,
                focus ? completedFocus + 1 : completedFocus,
                focus ? totalFocusSeconds + durationSeconds : totalFocusSeconds);
    }
}
