package com.studyroom.task;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of a task shared by all members of a study room.
 *
 * <p>Live tasks are stored in Redis. The monotonically increasing version is
 * used for optimistic concurrency control: commands must supply the version
 * they last observed, and the task service must reject stale updates.</p>
 */
public record Task(
        UUID id,
        String roomCode,
        String title,
        boolean completed,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static final int MAX_TITLE_LENGTH = 200;

    public Task {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");

        if (roomCode == null || !roomCode.matches("[A-Z0-9]{6,12}")) {
            throw new IllegalArgumentException("Invalid room code");
        }
        if (title == null || title.isBlank() || title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Task title must be 1-200 characters");
        }
        title = title.strip();
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot precede createdAt");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Task version cannot be negative");
        }
    }

    public static Task create(String roomCode, String title, UUID createdBy, Instant now) {
        return new Task(UUID.randomUUID(), roomCode, title, false, createdBy, now, now, 0);
    }

    /** Returns an updated snapshot; service must check expected version atomically. */
    public Task withCompleted(boolean newCompleted, Instant now) {
        Objects.requireNonNull(now, "now");
        if (newCompleted == completed) {
            return this;
        }
        return new Task(id, roomCode, title, newCompleted, createdBy,
                createdAt, now, version + 1);
    }

    /** Returns a renamed snapshot; service must check expected version atomically. */
    public Task withTitle(String newTitle, Instant now) {
        Objects.requireNonNull(now, "now");
        if (Objects.equals(title, newTitle == null ? null : newTitle.strip())) {
            return this;
        }
        return new Task(id, roomCode, newTitle, completed, createdBy,
                createdAt, now, version + 1);
    }
}
