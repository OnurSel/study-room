package com.studyroom.room;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Metadata for an ephemeral study room.
 *
 * <p>Active room state lives in Redis, not PostgreSQL. Timer, presence and
 * tasks are separate concerns so they can be updated and broadcast without
 * rewriting room metadata.</p>
 */
public record Room(
        String code,
        UUID creatorId,
        Instant createdAt,
        Instant expiresAt,
        String name
) {

    /** Rooms created before names existed (or without one) have no name. */
    public Room(String code, UUID creatorId, Instant createdAt, Instant expiresAt) {
        this(code, creatorId, createdAt, expiresAt, null);
    }

    public Room {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(creatorId, "creatorId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");

        if (!code.matches("[A-Z0-9]{6,12}")) {
            throw new IllegalArgumentException("Room code must contain 6-12 uppercase letters or digits");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !now.isBefore(expiresAt);
    }
}
