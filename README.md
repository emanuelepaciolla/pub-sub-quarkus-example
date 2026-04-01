# example-quarkus-pub-sub

Demo application built with Quarkus and GCP Pub/Sub, with PostgreSQL persistence.
Demonstrates multi-topic publish/subscribe, Dead Letter Queue handling, JWT authentication, and retry policies.

## Stack

- **Quarkus** — runtime
- **GCP Pub/Sub** — messaging (emulator supported for local dev)
- **PostgreSQL** — persistence via Hibernate ORM
- **SmallRye JWT** — RS256 authentication
- **OpenAPI / Swagger UI** — API documentation

## Architecture

```
REST Client
    │
    ▼
MessageController
    ├── publish → PublisherManager → GCP Pub/Sub Topic
    │
    └── query  → MessageService → PostgreSQL

GCP Pub/Sub Subscriptions
    ├── SubscriberManager    → MessageService → PostgreSQL
    └── DlqSubscriberManager → (dead letter handling)
```

**Topics:** `demo-topic`, `user-events`, `system-events`, `notification-events`
**DLQ:** `messages-dlq` — receives messages after 5 failed delivery attempts

## Local setup

**Prerequisites:** Docker, Java 21+, Maven

```bash
# Start dependencies (Postgres + Pub/Sub emulator + Adminer)
cd example-quarkus-pub-sub
docker-compose up

# Run in dev mode (hot reload)
./mvnw quarkus:dev
```

The app starts on port **8080**.

| URL | Description |
|-----|-------------|
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/q/health` | Health check |
| `http://localhost:8081` | Adminer (DB UI) |

## Configuration

All config is in `src/main/resources/application.properties`.
Environment variables override defaults in production:

| Env var | Default | Description |
|---------|---------|-------------|
| `GCP_PROJECT_ID` | `local-project` | GCP project ID |
| `PUBSUB_EMULATOR_HOST` | _(empty — uses real GCP)_ | Emulator host, e.g. `localhost:8085` |
| `DB_URL` | `jdbc:postgresql://localhost:5432/gcpdemo` | JDBC URL |
| `DB_USERNAME` | `postgres` | DB user |
| `DB_PASSWORD` | `postgres` | DB password |

## Authentication

Endpoints require a JWT (RS256). The public key is loaded from `META-INF/resources/publicKey.pem`.
Health endpoints (`/q/health/**`) are public.

For local testing, use `get-token.sh` to generate a signed token with the dev private key.

## Running tests

```bash
./mvnw test

# Single class
./mvnw test -Dtest=MessageServiceTest
```

Tests run against a local Postgres instance (`gcpdemo_test`) and the Pub/Sub emulator.
