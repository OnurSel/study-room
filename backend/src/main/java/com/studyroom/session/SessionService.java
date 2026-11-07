package com.studyroom.session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Records completed focus sessions and reads their PostgreSQL history.
 *
 * <p>This service does not control the live Pomodoro timer. The caller must
 * supply the actual observed start/end times and statistics when a focus
 * session finishes; it must also authorize access to a room before exposing
 * its history to a client.</p>
 */
@Service
public class SessionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SessionRepository repository;
    private final Clock clock;

    @Autowired
    public SessionService(SessionRepository repository) {
        this(repository, Clock.systemUTC());
    }

    SessionService(SessionRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Saves one completed focus period using measurements supplied by the caller. */
    @Transactional
    public Session recordCompleted(String roomCode, Instant startedAt, Instant endedAt,
                                   int focusSeconds, int completedTaskCount,
                                   int participantCount) {
        String code = normalizeRoomCode(roomCode);
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(endedAt, "endedAt");
        if (startedAt.isAfter(endedAt)) {
            throw new IllegalArgumentException("Session end must not precede start");
        }
        if (endedAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Session end must not be in the future");
        }
        if (focusSeconds < 0 || completedTaskCount < 0 || participantCount < 0) {
            throw new IllegalArgumentException("Session counts must be non-negative");
        }
        Session session = Session.completed(code, startedAt, endedAt,
                focusSeconds, completedTaskCount, participantCount, clock.instant());
        return repository.save(session);
    }

    /** Newest sessions first. Page numbers start at zero. */
    @Transactional(readOnly = true)
    public Page<Session> getHistory(String roomCode, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be non-negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return repository.findByRoomCodeOrderByStartedAtDesc(
                normalizeRoomCode(roomCode), PageRequest.of(page, size));
    }

    /** Returns sessions starting within the half-open interval [from, to). */
    @Transactional(readOnly = true)
    public List<Session> getHistoryBetween(String roomCode, Instant from, Instant to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        return repository
                .findByRoomCodeAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(
                        normalizeRoomCode(roomCode), from, to);
    }

    private static String normalizeRoomCode(String roomCode) {
        if (roomCode == null) {
            throw new IllegalArgumentException("Room code is required");
        }
        String normalized = roomCode.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{6,12}")) {
            throw new IllegalArgumentException("Invalid room code");
        }
        return normalized;
    }
}
