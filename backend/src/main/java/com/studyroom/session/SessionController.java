package com.studyroom.session;

import com.studyroom.presence.PresenceService;
import com.studyroom.security.JwtService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Read-only endpoints for completed focus-session history.
 *
 * <p>Session recording is deliberately not exposed as a client endpoint:
 * completed sessions must be recorded by trusted server-side timer logic.
 * History is accessible only to a participant currently joined to the room.
 * The HTTP security configuration must authenticate bearer tokens before these
 * endpoints are enabled.</p>
 */
@RestController
@RequestMapping("/api/v1/rooms/{roomCode}/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final PresenceService presenceService;

    public SessionController(SessionService sessionService, PresenceService presenceService) {
        this.sessionService = sessionService;
        this.presenceService = presenceService;
    }

    @GetMapping
    public Page<SessionResponse> list(
            @PathVariable String roomCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        String code = requireMembership(roomCode, identity);
        try {
            return sessionService.getHistory(code, page, size).map(SessionResponse::from);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/range")
    public List<SessionResponse> listBetween(
            @PathVariable String roomCode,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        String code = requireMembership(roomCode, identity);
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must be before 'to'");
        }
        // A bounded window avoids unbounded history responses.
        if (java.time.Duration.between(from, to).compareTo(java.time.Duration.ofDays(31)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date range cannot exceed 31 days");
        }
        return sessionService.getHistoryBetween(code, from, to).stream()
                .map(SessionResponse::from)
                .toList();
    }

    private String requireMembership(String roomCode, JwtService.AnonymousIdentity identity) {
        if (identity == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (roomCode == null || !roomCode.trim().toUpperCase(Locale.ROOT).matches("[A-Z0-9]{6,12}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid room code");
        }
        String code = roomCode.trim().toUpperCase(Locale.ROOT);
        try {
            boolean joined = presenceService.snapshot(code).participants().stream()
                    .anyMatch(participant -> participant.id().equals(identity.participantId()));
            if (!joined) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Join the room to view its history");
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found", ex);
        }
        return code;
    }

    public record SessionResponse(
            UUID id,
            String roomCode,
            Instant startedAt,
            Instant endedAt,
            int focusSeconds,
            int completedTaskCount,
            int participantCount) {
        static SessionResponse from(Session session) {
            return new SessionResponse(session.getId(), session.getRoomCode(),
                    session.getStartedAt(), session.getEndedAt(),
                    session.getFocusSeconds(), session.getCompletedTaskCount(),
                    session.getParticipantCount());
        }
    }
}
