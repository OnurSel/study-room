# Architecture (draft)

> Draft. Update after each phase; mark anything not yet built as *planned*.

```
 Browser  <--WebSocket/STOMP-->  Spring Boot  <-->  Redis   (live room state, pub/sub)
                                      |
                                      +----------> PostgreSQL (session summaries, history)
```

## Key decisions (see ADRs)
- **Server-authoritative timer.** The server stores `startedAtEpochMs`, `durationMs`, `state`. Clients never tick-sync every second; they compute remaining time locally from the stored values plus a measured **clock offset** to the server. See `adr/001-timer-model.md`.
- **Redis for live state, PostgreSQL for history.** Room state is ephemeral and fast-changing; summaries are durable.
- **Optimistic versioning for tasks.** Each task has a `version`; a write with a stale version is rejected and the client refetches.
- **Snapshot on connect.** Every (re)connection first receives the full room snapshot, then incremental events.

## Packages (backend)
`room` (lifecycle, codes) | `timer` (state machine) | `task` (list + versioning) | `presence` | `session` (summaries) | `security` (anonymous JWT, rate limits) | `config`

## Known limits (expected)
- Free-tier hosting sleeps when idle; first connection can be slow.
- A single instance in v1; horizontal scaling is out of scope (Redis pub/sub keeps the door open).
