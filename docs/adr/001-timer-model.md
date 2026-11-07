# ADR-001: Timer model

- Status: proposed
- Date: 2026-11-07

## Context
All clients in a room must show the same Pomodoro timer. Sending a tick every second is wasteful and drifts under latency.

## Decision (proposed)
Store `state`, `startedAtEpochMs`, `durationMs` (and `pausedRemainingMs`) on the server. Clients derive remaining time locally using a measured offset between their clock and the server clock (estimated at connect and refreshed periodically).

## Consequences
- Low traffic; no per-second events.
- Accuracy depends on the offset estimate; drift must be **measured** (Phase 3), not assumed.
- Pause/resume must update the stored values atomically (single writer per room).
