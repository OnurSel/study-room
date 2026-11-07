# Roadmap

Part-time estimate: **3-4 weeks** (a working core in ~1 week). Estimates are guesses, not measurements. Each phase has an **exit criterion**; do not start the next phase before it is met.

## Phase 0 - Setup (1 day)
- [ ] Create the **public** GitHub repo; choose a license.
- [ ] Spring Boot skeleton (Java 21), Flyway, Actuator health.
- [ ] `docker-compose.yml` for local PostgreSQL + Redis.
- [ ] CI skeleton (build + test) green on `main`.
- [ ] ADR-001 (timer model) reviewed.
**Exit:** `mvn verify` and the CI run are green on an empty skeleton.

## Phase 1 - Core (3-5 days)
- [ ] Anonymous identity (short-lived JWT, display name only).
- [ ] Create room / join by code; WebSocket (STOMP) connection.
- [ ] Presence: joined / working / on break / left, broadcast live.
- [ ] Shared Pomodoro timer (start, pause, break, reset) - server-authoritative.
- [ ] Shared task list: add, tick, remove, live to everyone.
- [ ] Minimal web client.
**Exit:** two browser tabs (and a phone) in one room see the same timer and task changes within an ordinary network round trip.

## Phase 2 - Resilience (4-6 days)
- [ ] Reconnect: client resumes and receives a full state snapshot.
- [ ] Task conflicts resolved with a version number (reject stale writes, client refetches).
- [ ] Rate limits per connection and per room; max room size.
- [ ] Room TTL: empty rooms expire; graceful shutdown notifies clients.
- [ ] Input validation and message size limits.
**Exit:** killing and restoring a client's network never leaves it on a wrong timer or stale task list (covered by tests).

## Phase 3 - Tests and measurements (3-5 days)
- [ ] Unit tests (timer math, versioning, room rules).
- [ ] Integration tests with Testcontainers (PostgreSQL + Redis) and WebSocket clients.
- [ ] Timer drift measurement between two clients (record the method and the numbers).
- [ ] Load/latency scenario in `load-test/`; run locally under free-tier-like limits (e.g. 512 MB).
- [ ] Coverage report (JaCoCo); publish the measured number only.
**Exit:** `docs/TESTING.md` contains real measured results and how to reproduce them.

## Phase 4 - Deploy and polish (2-3 days)
- [ ] Deploy: Render + Neon + free Redis (see `docs/DEPLOYMENT.md`).
- [ ] CI builds the Docker image; Dependabot enabled.
- [ ] README: screenshots/GIF, run instructions, honest limitations (free tier sleeps).
**Exit:** a stranger can open the live link, create a room and use it with a friend.

## Phase 5 - Real usage (1-2 days + waiting)
- [ ] Run 1-2 real sessions with friends/classmates; collect issues.
- [ ] Fix the top problems; record how many people actually used it.
**Exit:** README states real usage numbers (or states that there were none).

## Phase 6 - Optional
- [ ] Session summary (minutes worked per person, tasks done).
- [ ] Simple room history page.

## What goes on a CV (only after the matching phase)
- After Phase 3: tests, coverage and drift/latency numbers **as measured**.
- After Phase 4: "live on <host>".
- After Phase 5: real user count.
Do not claim scalability, load capacity or calibration that was not measured.
