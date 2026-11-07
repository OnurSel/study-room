package com.studyroom.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted summary of a completed study session.
 *
 * <p>Active timer, tasks and presence remain in Redis; PostgreSQL stores only
 * completed-session history. This entity matches V1__init.sql.</p>
 */
@Entity
@Table(name = "study_sessions", indexes = {
        @Index(name = "idx_study_sessions_room_started", columnList = "room_code, started_at DESC")
})
public class Session {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "room_code", nullable = false, length = 12, updatable = false)
    private String roomCode;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false, updatable = false)
    private Instant endedAt;

    @Column(name = "focus_seconds", nullable = false, updatable = false)
    private int focusSeconds;

    @Column(name = "completed_task_count", nullable = false, updatable = false)
    private int completedTaskCount;

    @Column(name = "participant_count", nullable = false, updatable = false)
    private int participantCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Session() {
        // Required by JPA.
    }

    private Session(UUID id, String roomCode, Instant startedAt, Instant endedAt,
                    int focusSeconds, int completedTaskCount, int participantCount,
                    Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.roomCode = Objects.requireNonNull(roomCode, "roomCode");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.endedAt = Objects.requireNonNull(endedAt, "endedAt");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");

        if (!roomCode.matches("[A-Z0-9]{6,12}")) {
            throw new IllegalArgumentException("Invalid room code");
        }
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("endedAt must not precede startedAt");
        }
        if (focusSeconds < 0 || completedTaskCount < 0 || participantCount < 0) {
            throw new IllegalArgumentException("Session counters must not be negative");
        }

        this.focusSeconds = focusSeconds;
        this.completedTaskCount = completedTaskCount;
        this.participantCount = participantCount;
    }

    public static Session completed(String roomCode, Instant startedAt, Instant endedAt,
                                    int focusSeconds, int completedTaskCount,
                                    int participantCount, Instant recordedAt) {
        return new Session(UUID.randomUUID(), roomCode, startedAt, endedAt,
                focusSeconds, completedTaskCount, participantCount, recordedAt);
    }

    public UUID getId() { return id; }
    public String getRoomCode() { return roomCode; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public int getFocusSeconds() { return focusSeconds; }
    public int getCompletedTaskCount() { return completedTaskCount; }
    public int getParticipantCount() { return participantCount; }
    public Instant getCreatedAt() { return createdAt; }
}
