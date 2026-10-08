# Java Database Migration & SQL Performance Optimizer

A reproducible portfolio project that migrates legacy customer data between two databases, survives interrupted runs, verifies data integrity, and demonstrates an evidence-based SQL index improvement.

> This is a self-initiated demonstration built with synthetic data. It contains no employer or client source code, credentials, or production records.

## What it demonstrates

- Cross-database migration using separate source and target connections
- Keyset pagination and configurable batch sizes
- Normalization of names, emails, and legacy status values
- Idempotent update-or-insert behavior
- Atomic batch writes and persisted checkpoints
- Failure logging and resume from the last successful batch
- Row-count and SHA-256 content validation
- `EXPLAIN` plans before and after a composite index
- Swagger UI, health endpoint, Docker Compose, CI, and integration tests

## Architecture

```text
Legacy DB -> Keyset reader -> Clean & transform -> Transactional batch upsert -> Target DB
                                      |                         |
                                      +--- SHA-256 validation   +--- Checkpoint / resume

Orders table -> EXPLAIN before -> composite index -> EXPLAIN after
```

The default profile uses two isolated in-memory H2 databases, so reviewers can run it immediately. Docker Compose runs the same application against MySQL 8.4 as the source and PostgreSQL 17 as the target.

## Run locally

Requirements: Java 17 and Maven 3.9+.

```bash
mvn spring-boot:run
```

Open Swagger UI at <http://localhost:8080/swagger-ui.html>.

Start a complete migration:

```bash
curl -X POST http://localhost:8080/api/migrations/customers \
  -H "Content-Type: application/json" \
  -d '{"batchSize":50}'
```

Demonstrate interruption and resume:

```bash
curl -X POST http://localhost:8080/api/migrations/customers \
  -H "Content-Type: application/json" \
  -d '{"batchSize":25,"failAfterRows":50}'

curl -X POST http://localhost:8080/api/migrations/customers \
  -H "Content-Type: application/json" \
  -d '{"batchSize":25,"resumeRunId":"RUN_ID_FROM_FIRST_RESPONSE"}'
```

Compare query plans:

```bash
curl -X POST "http://localhost:8080/api/performance/orders/benchmark?customerId=42&repetitions=20"
```

The benchmark returns both plans, average local timings, and whether the optimizer selected `idx_orders_customer_created`. Timings are observations from the current machine—not guaranteed production gains.

## Run with MySQL → PostgreSQL

```bash
docker compose up --build
```

The app creates and seeds synthetic tables automatically. The exposed database ports are `3307` and `5433`, avoiding common local defaults.

## API summary

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/migrations/customers` | Start or resume a migration |
| GET | `/api/migrations/{runId}` | Read checkpoint, status, counts, and checksums |
| POST | `/api/performance/orders/benchmark` | Compare query plans before/after indexing |
| GET | `/actuator/health` | Application health |

## Verification

```bash
mvn verify
docker compose config --quiet
```

The integration suite covers successful migration, data cleansing, idempotency, interruption checkpointing, resume, request validation, missing-run handling, and index-plan detection.

## Technology

Java 17 · Spring Boot 3.5 · Spring JDBC · MySQL · PostgreSQL · H2 · OpenAPI/Swagger · Docker Compose · JUnit 5

## Portfolio materials

- Upwork-ready copy: [`portfolio/upwork-copy.md`](portfolio/upwork-copy.md)
- Gallery images: [`portfolio/gallery`](portfolio/gallery)
