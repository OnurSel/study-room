package com.studyroom.room;

import com.studyroom.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * REST endpoints for creating study rooms and checking invitations.
 *
 * <p>These operations require a validated anonymous JWT. The security filter
 * and authorization rules must be enabled in SecurityConfig before these
 * endpoints can be used. Joining a room and publishing presence will be
 * implemented separately so a lookup is never mistaken for membership.</p>
 */
@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        JwtService.AnonymousIdentity user = requireIdentity(identity);
        Room room = roomService.createRoom(user.participantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(room, user.participantId()));
    }

    @GetMapping("/{code}")
    public RoomResponse getRoom(
            @PathVariable String code,
            @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        JwtService.AnonymousIdentity user = requireIdentity(identity);
        final Room room;
        try {
            room = roomService.findRoom(code)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Room not found"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid room code", ex);
        }
        return toResponse(room, user.participantId());
    }

    private static JwtService.AnonymousIdentity requireIdentity(
            JwtService.AnonymousIdentity identity) {
        if (identity == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return identity;
    }

    private static RoomResponse toResponse(Room room, java.util.UUID requester) {
        return new RoomResponse(room.code(), room.createdAt(), room.expiresAt(),
                room.creatorId().equals(requester));
    }

    /** {@code owner} tells the requesting participant whether they created this room. */
    public record RoomResponse(String code, Instant createdAt, Instant expiresAt, boolean owner) {
    }
}
