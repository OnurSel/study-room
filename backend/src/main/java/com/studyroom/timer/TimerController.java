package com.studyroom.timer;

import com.studyroom.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Objects;

/**
 * REST commands for the server-authoritative Pomodoro timer.
 *
 * <p>Endpoints must remain protected by the HTTP security filter. Identity is
 * required here as defense in depth; room membership enforcement must be
 * integrated before making these endpoints accessible to clients.</p>
 */
@RestController
@RequestMapping("/api/v1/rooms/{code}/timer")
public class TimerController {

    private final TimerService timerService;

    public TimerController(TimerService timerService) {
        this.timerService = Objects.requireNonNull(timerService);
    }

    @GetMapping
    public TimerSnapshot getState(@PathVariable String code,
                                  @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        requireIdentity(identity);
        return snapshot(() -> timerService.getState(code));
    }

    @PostMapping("/start")
    public TimerSnapshot start(@PathVariable String code,
                               @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        requireIdentity(identity);
        return snapshot(() -> timerService.start(code));
    }

    @PostMapping("/pause")
    public TimerSnapshot pause(@PathVariable String code,
                               @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        requireIdentity(identity);
        return snapshot(() -> timerService.pause(code));
    }

    @PostMapping("/reset")
    public TimerSnapshot reset(@PathVariable String code,
                               @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        requireIdentity(identity);
        return snapshot(() -> timerService.reset(code));
    }

    @PostMapping("/phase")
    public TimerSnapshot changePhase(@PathVariable String code,
                                     @RequestBody(required = false) PhaseRequest request,
                                     @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        requireIdentity(identity);
        if (request == null || request.phase() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Timer phase is required");
        }
        return snapshot(() -> timerService.switchPhase(code, request.phase()));
    }

    private static JwtService.AnonymousIdentity requireIdentity(
            JwtService.AnonymousIdentity identity) {
        if (identity == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return identity;
    }

    private static TimerSnapshot snapshot(TimerOperation operation) {
        try {
            TimerState state = operation.execute();
            Instant serverTime = Instant.now();
            return new TimerSnapshot(state, serverTime, state.remainingAt(serverTime));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @FunctionalInterface
    private interface TimerOperation {
        TimerState execute();
    }

    public record PhaseRequest(TimerState.Phase phase) {
    }

    /** Server timestamp allows clients to estimate clock offset. */
    public record TimerSnapshot(TimerState timer, Instant serverTime, int remainingSeconds) {
    }
}
