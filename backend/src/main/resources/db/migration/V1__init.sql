-- Study Room: initial PostgreSQL schema.
-- Redis is the source of truth for active rooms, presence, timers and tasks.
-- PostgreSQL stores completed session history only.

CREATE TABLE study_sessions (
    id UUID PRIMARY KEY,
    room_code VARCHAR(12) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NOT NULL,
    focus_seconds INTEGER NOT NULL DEFAULT 0,
    completed_task_count INTEGER NOT NULL DEFAULT 0,
    participant_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_study_sessions_time_range
        CHECK (ended_at >= started_at),
    CONSTRAINT ck_study_sessions_focus_seconds
        CHECK (focus_seconds >= 0),
    CONSTRAINT ck_study_sessions_completed_task_count
        CHECK (completed_task_count >= 0),
    CONSTRAINT ck_study_sessions_participant_count
        CHECK (participant_count >= 0)
);

CREATE INDEX idx_study_sessions_room_started
    ON study_sessions (room_code, started_at DESC);
