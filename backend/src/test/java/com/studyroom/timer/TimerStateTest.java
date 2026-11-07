package com.studyroom.timer;

import com.studyroom.timer.TimerState.Phase;
import com.studyroom.timer.TimerState.Status;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Pure-logic tests for per-room settings, the Pomodoro cycle and auto-advance. */
class TimerStateTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private static TimerState fresh() {
        return TimerState.initial("ABC123");
    }

    @Test
    void settingsApplyToIdleTimerImmediately() {
        TimerState s = fresh().withSettings(50, 10, 20, false);
        assertEquals(50 * 60, s.durationSeconds());
        assertEquals(50 * 60, s.remainingSeconds());
        assertEquals(10 * 60, s.shortBreakSeconds());
        assertEquals(20 * 60, s.longBreakSeconds());
    }

    @Test
    void settingsDoNotDisturbRunningTimerUntilNextPhaseOrReset() {
        TimerState running = fresh().start(T0);
        TimerState changed = running.withSettings(50, 10, 20, false);
        assertEquals(Status.RUNNING, changed.status());
        assertEquals(25 * 60, changed.durationSeconds());
        assertEquals(50 * 60, changed.focusSeconds());
        assertEquals(50 * 60, changed.reset().durationSeconds());
    }

    @Test
    void settingsRejectOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> fresh().withSettings(0, 5, 15, false));
        assertThrows(IllegalArgumentException.class, () -> fresh().withSettings(181, 5, 15, false));
        assertThrows(IllegalArgumentException.class, () -> fresh().withSettings(25, 61, 15, false));
        assertThrows(IllegalArgumentException.class, () -> fresh().withSettings(25, 5, 121, false));
    }

    @Test
    void identicalSettingsAreANoOp() {
        TimerState s = fresh();
        assertSame(s, s.withSettings(25, 5, 15, false));
    }

    @Test
    void manualModeCompletesAndStartMovesToNextPhase() {
        TimerState done = fresh().start(T0).completeIfElapsed(T0.plusSeconds(25 * 60));
        assertEquals(Status.COMPLETED, done.status());
        assertEquals(1, done.completedFocus());
        assertEquals(25 * 60, done.totalFocusSeconds());

        TimerState next = done.start(T0.plusSeconds(26 * 60));
        assertEquals(Phase.SHORT_BREAK, next.phase());
        assertEquals(Status.RUNNING, next.status());
    }

    @Test
    void longBreakFollowsEveryFourthFocus() {
        TimerState s = fresh().withSettings(1, 1, 2, true).start(T0);
        // 4 x (1 min focus + 1 min short break) but the 4th break is a 2 min long break.
        Instant later = T0.plusSeconds(60 * 7 + 30); // inside the 4th break (long break 7..9 min)
        TimerState caughtUp = s.completeIfElapsed(later);
        assertEquals(Status.RUNNING, caughtUp.status());
        assertEquals(Phase.LONG_BREAK, caughtUp.phase());
        assertEquals(4, caughtUp.completedFocus());
        assertEquals(4 * 60, caughtUp.totalFocusSeconds());
    }

    @Test
    void autoAdvanceStartsNextPhaseExactlyAtPreviousDeadline() {
        TimerState s = fresh().withSettings(1, 1, 2, true).start(T0);
        TimerState advanced = s.completeIfElapsed(T0.plusSeconds(60 + 10));
        assertEquals(Phase.SHORT_BREAK, advanced.phase());
        assertEquals(Status.RUNNING, advanced.status());
        assertEquals(T0.plusSeconds(60), advanced.startedAt());
        assertEquals(T0.plusSeconds(120), advanced.endsAt());
    }

    @Test
    void breaksDoNotCreditFocusTime() {
        TimerState s = fresh().switchTo(Phase.SHORT_BREAK).start(T0)
                .completeIfElapsed(T0.plusSeconds(5 * 60));
        assertEquals(0, s.completedFocus());
        assertEquals(0, s.totalFocusSeconds());
    }

    @Test
    void pauseAfterDeadlineCompletesInsteadOfPausing() {
        TimerState s = fresh().start(T0).pause(T0.plusSeconds(25 * 60 + 5));
        assertEquals(Status.COMPLETED, s.status());
        assertEquals(1, s.completedFocus());
    }
}
