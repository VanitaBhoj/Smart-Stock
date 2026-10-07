# SmartStock

## Run with Docker Compose

Docker Compose starts the Spring Boot API, PostgreSQL, and Redis. PostgreSQL and Redis use persistent named volumes; the API connects to them using the Compose service names `postgres` and `redis`.

Start the stack from the repository root:

```sh
docker compose up --build -d
```

The API is available at `http://localhost:8080`. Product and inventory reads and customer registration are public; protected operations use HTTP Basic authentication. Local Compose creates the admin account `smartstock-admin` / `change-this-development-password`; override `SMARTSTOCK_ADMIN_USERNAME`, `SMARTSTOCK_ADMIN_PASSWORD`, `DB_USERNAME`, `DB_PASSWORD`, and `REDIS_PASSWORD` in `.env` before use beyond local development. PostgreSQL and Redis are bound to loopback on ports `5432` and `6379`. Deploy the API behind a TLS terminator outside local development because Basic Auth credentials must travel over HTTPS. Customers register at `POST /api/auth/register`.

Swagger UI is at `http://localhost:8080/swagger-ui/index.html`; protected API calls from the UI use their account's Basic Auth credentials. Database schema changes are applied with Flyway migrations before Hibernate validates the schema.

Stop the containers and network:

```sh
docker compose down
```

To also remove the persisted local PostgreSQL and Redis data, run:

```sh
docker compose down --volumes
```

View logs for every service or follow only the API logs:

```sh
docker compose logs
docker compose logs --follow smartstock-api
```

## Connect to PostgreSQL

Connect from inside the database container:

```sh
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Or connect from a host PostgreSQL client to `localhost:5432`, database `smartstock`. The local development defaults are username `smartstock` and password `smartstock`. Set `DB_USERNAME` and `DB_PASSWORD` in the shell or an ignored `.env` file to change them; Compose passes the same values to PostgreSQL and the API.

## Verify Redis

Ping Redis from its container:

```sh
docker compose exec redis sh -c 'redis-cli -a "$REDIS_PASSWORD" ping'
```

Redis should respond with `PONG`. To inspect cache keys after requesting cached product or inventory endpoints:

```sh
docker compose exec redis sh -c 'redis-cli -a "$REDIS_PASSWORD" --scan'
```

## Test the API

Check that the API is responding:

```sh
curl -i -u smartstock-admin:change-this-development-password http://localhost:8080/api/products
```

Create a product and then read the product list:

```sh
curl -i -u smartstock-admin:change-this-development-password -X POST http://localhost:8080/api/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Desk Lamp","sku":"LAMP-001","price":35.00,"category":"Home"}'

curl -i -u smartstock-admin:change-this-development-password http://localhost:8080/api/products
```

For a fresh database, create inventory for the returned product ID (usually `1`) and read it by product ID:

```sh
curl -i -u smartstock-admin:change-this-development-password -X POST http://localhost:8080/api/inventory \
  -H 'Content-Type: application/json' \
  -d '{"productId":1,"availableQuantity":10}'

curl -i -u smartstock-admin:change-this-development-password http://localhost:8080/api/inventory/1
```

## Business event outbox

Order and reservation changes write their business event to PostgreSQL's `outbox_events` table in the same transaction as the change. A scheduled publisher reads committed `PENDING` rows and publishes `OutboxBusinessEvent` Spring application events. Set `OUTBOX_PUBLISHER_INTERVAL_MS` to change the polling delay (default `5000`).

Failed publications remain `PENDING`, increment `attempts`, and are retried on a later poll. Delivery is at least once: the publisher may have to send an event more than once after a crash, so consumers should be idempotent using the outbox event ID.

## Kafka business events

The outbox publisher now sends `ORDER_CREATED`, `ORDER_CONFIRMED`, `ORDER_CANCELLED`, and `RESERVATION_EXPIRED` to Kafka topic `smartstock.business-events` (override with `KAFKA_BUSINESS_EVENTS_TOPIC`). `RESERVATION_CREATED` is still recorded in the outbox but is not forwarded to Kafka. Configure `KAFKA_BOOTSTRAP_SERVERS` and `KAFKA_CONSUMER_GROUP` for non-Compose deployments. Compose starts a single-node Kafka broker; the API uses `kafka:9092`, and host clients can connect to `localhost:9094`.

The producer waits for Kafka acknowledgement before the outbox row is marked processed. Failed sends leave the row pending for the scheduled outbox retry. Kafka consumer failures are retried three times and then sent to the topic's `.DLT` dead-letter topic. The consumer stores processed event IDs in PostgreSQL in the same transaction as its local application event dispatch, so Kafka redelivery is ignored after successful handling.

Delivery is eventually consistent: the PostgreSQL transaction completes first, then the scheduled publisher sends the event. Consumers may observe the event after a short delay, and delivery can repeat if the publisher crashes after Kafka acknowledges but before the outbox row is marked processed. Consumers should remain idempotent.
