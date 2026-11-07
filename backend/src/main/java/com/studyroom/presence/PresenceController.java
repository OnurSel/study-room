package com.studyroom.presence;

import com.studyroom.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Supplier;

/**
 * Authenticated HTTP operations for room presence.
 *
 * <p>Identity and display name are taken only from the verified JWT principal,
 * never from the request body. Each change is broadcast by PresenceService.
 * Disconnect handling will be integrated with the WebSocket lifecycle later.</p>
 */
@RestController
@RequestMapping("/api/v1/rooms/{code}/presence")
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @PostMapping("/join")
    public ResponseEntity<PresenceService.PresenceSnapshot> join(
            @PathVariable String code,
            @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        var user = requireIdentity(identity);
        var snapshot = execute(() -> presenceService.join(
                code, user.participantId(), user.displayName()));
        return ResponseEntity.ok(snapshot);
    }

    @GetMapping
    public PresenceService.PresenceSnapshot snapshot(
            @PathVariable String code,
            @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        var user = requireIdentity(identity);
        requireMember(code, user);
        return execute(() -> presenceService.snapshot(code));
    }

    @PutMapping("/status")
    public PresenceService.PresenceSnapshot changeStatus(
            @PathVariable String code,
            @Valid @RequestBody ChangeStatusRequest request,
            @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        var user = requireIdentity(identity);
        requireMember(code, user);
        return execute(() -> presenceService.updateStatus(
                code, user.participantId(), request.status()));
    }

    @DeleteMapping("/leave")
    public PresenceService.PresenceSnapshot leave(
            @PathVariable String code,
            @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        var user = requireIdentity(identity);
        requireMember(code, user);
        return execute(() -> presenceService.leave(code, user.participantId()));
    }

    private void requireMember(String code, JwtService.AnonymousIdentity identity) {
        boolean member = execute(() -> presenceService.isMember(code, identity.participantId()));
        if (!member) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Join this room first");
        }
    }

    private static JwtService.AnonymousIdentity requireIdentity(
            JwtService.AnonymousIdentity identity) {
        if (identity == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return identity;
    }

    private static <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    public record ChangeStatusRequest(@NotNull PresenceService.Status status) { }
}
