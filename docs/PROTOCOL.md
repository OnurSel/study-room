# WebSocket protocol (draft)

> Draft; to be finalized in Phase 1. Messages are JSON over STOMP.

| Direction | Destination | Purpose |
|---|---|---|
| client -> server | `/app/room/{code}/timer` | start / pause / reset |
| client -> server | `/app/room/{code}/task` | add / toggle / remove (with `version`) |
| client -> server | `/app/room/{code}/status` | working / break |
| server -> clients | `/topic/room/{code}` | events: timer, task, presence |
| server -> client | `/user/queue/snapshot` | full state on (re)connect |

Rules: every message has a type and a client-generated id (idempotency); size and rate are limited; unknown fields are ignored.
