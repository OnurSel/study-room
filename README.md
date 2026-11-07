# Study Room

> **Status: planning.** Nothing below is built yet; this README describes the target. It will be updated as each phase in [ROADMAP.md](ROADMAP.md) is finished, and only with things that were actually tested.

A real-time shared study room: friends join with a code, run **one synchronized Pomodoro timer**, see each other's live status, and tick off a **shared task list** that updates for everyone instantly. No accounts.

## Why this project
It is a small, honest exercise in the hard parts of real-time systems:
- **Timer synchronization** across clients (server-authoritative, clock-offset correction).
- **Reconnect and state resync** after a dropped connection.
- **Concurrent edits** to the same task (optimistic versioning).
- **Abuse limits** (rate limiting, room TTL) on a free-tier deployment.

## Planned stack
Java 21, Spring Boot (WebSocket/STOMP), Redis (live room state + pub/sub), PostgreSQL (session history), a plain web client, Docker, GitHub Actions, free-tier hosting (Render + Neon + a free Redis).

## Repository layout
```
backend/    Spring Boot service (packages: room, timer, task, presence, session, security, config)
web/        Browser client
load-test/  WebSocket load/latency scenarios
docs/       Scope, architecture, protocol, testing, deployment, ADRs
scripts/    Dev helpers
```

## Documentation
- [Roadmap](ROADMAP.md) - phases, exit criteria, estimates
- [Scope](docs/SCOPE.md) - what is in and out
- [Architecture](docs/ARCHITECTURE.md) - design and trade-offs
- [Protocol](docs/PROTOCOL.md) - WebSocket messages
- [Testing](docs/TESTING.md) - what is measured and how
- [Deployment](docs/DEPLOYMENT.md) - free-tier setup

## License
To be chosen before the repository is made public (MIT is a common default).
