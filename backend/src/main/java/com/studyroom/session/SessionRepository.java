package com.studyroom.session;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read/write access to completed study-session history in PostgreSQL.
 * Active rooms, timers and presence are stored in Redis instead.
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {

    /** Most recent completed sessions for one room, with pagination. */
    Page<Session> findByRoomCodeOrderByStartedAtDesc(String roomCode, Pageable pageable);

    /** Completed sessions for a room during a half-open time window [from, to). */
    List<Session> findByRoomCodeAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(
            String roomCode, Instant from, Instant to);
}
