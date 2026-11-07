# Deployment (free tier)

One Docker image (`Dockerfile` at the repo root) serves the API, the WebSocket endpoint and the built web client from a single origin, so no CORS setup is needed.

Free-tier limits change: verify each provider's pricing page before relying on them. The free web service sleeps when idle (slow first connection); free Postgres/Redis have storage and connection limits.

## Services
- **Web service**: Render, runtime Docker, repo root, health check path `/actuator/health`.
- **PostgreSQL**: Neon. Flyway creates the schema on first start.
- **Redis**: any Redis (Render Key Value, Upstash...). Use TLS providers with `SPRING_DATA_REDIS_SSL_ENABLED=true`.

## Environment variables (see `.env.example`)
`DATABASE_URL` (JDBC form), `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `STUDYROOM_JWT_SECRET` (>= 32 bytes) (`STUDYROOM_WEBSOCKET_ALLOWED_ORIGINS` is optional: same-origin connections are always allowed).

## Local smoke test
```
docker compose up -d            # Postgres + Redis
cd backend && mvn spring-boot:run
cd web && npm run dev           # http://localhost:5173
```
