# Outbox and CDC

Pattern: https://microservices.io/patterns/data/transactional-outbox.html
Debezium outbox router: https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html

## Dual write

- "Save to DB, then send to Kafka" is two writes to two systems with no shared transaction. Crash between them →
  data without event (or event without data if you send first).
- v1 sent after commit (`@TransactionalEventListener(AFTER_COMMIT)`). That avoids "event for a rolled back
  order" but still loses the event on a crash right after commit.
- Kafka transactions do not help: they cover Kafka only, Postgres is outside.

## Outbox

- Write the event into an `outbox` table in the same DB transaction as the change. Atomic, because it is one
  database.
- Something else moves rows to Kafka later. Delivery becomes at-least-once → consumers must be idempotent
  (see [idempotency.md](idempotency.md)).
- Here: `OutboxWriter.append` (MANDATORY propagation, throws without a transaction), `OutboxRelay`,
  `OutboxCleanup` in `messaging-support`. Table in `V2__create_outbox_and_processed_events.sql` (orders),
  `V4__...` (products).
- Partial index `ON outbox (seq) WHERE published_at IS NULL`: the relay only looks at unsent rows, index stays
  small however much history there is.
- Payload is Avro JSON encoding, so the relay, not the request, talks to Schema Registry, and rows can be read
  with psql.
- Trace context goes into the `headers` jsonb column, see [observability.md](observability.md).

## Polling relay

- Every 200 ms, one transaction: `pg_try_advisory_xact_lock` → select up to 100 by `seq` → send all → wait for
  acks → `published_at = now()` → commit.
- Advisory lock = only one copy sends. Xact variant is released at commit/rollback or when the connection dies.
  https://www.postgresql.org/docs/current/explicit-locking.html#ADVISORY-LOCKS
- Why not `FOR UPDATE SKIP LOCKED`: it spreads rows over copies, so two events of one order can be sent by two
  copies in parallel and arrive swapped.
- `seq` (bigserial) instead of `created_at`: timestamps collide and come from different clocks.
  Caveat: sequence values are taken at insert, not at commit, so two concurrent transactions can commit in the
  other order. Fine here because events of one aggregate are written one after another, never concurrently.
- If Kafka is down: send times out (producer `delivery.timeout.ms` 8 s, relay waits 10 s), rollback, rows stay,
  backoff doubles up to 5 s. Producer timeouts are lowered in `application.yml`, the default would block for 2
  minutes.

## Scaling the relay

- One active copy is the bottleneck. To scale and keep per-key order: split the outbox into N buckets by
  `hash(aggregate_id) % N`, one lock per bucket, each copy takes the buckets it can lock. Same key always in the
  same bucket → still in order.
- Or move to CDC.

## CDC with Debezium

- Debezium reads the Postgres WAL (logical replication slot) and produces change events, runs inside Kafka
  Connect.
- Outbox event router SMT: turns inserts into the outbox table into proper events: topic from
  `aggregatetype`, key from `aggregateid`, value from `payload`. Rows can be deleted right after insert, the WAL
  still has them.
- Pros: no polling, lower latency, no load from queries, order follows commit order.
- Cons: Kafka Connect cluster to run and monitor, replication slot can fill the disk if the connector stops,
  another moving part in local setup.
- The table looks the same for both, so switching later does not touch service code.
