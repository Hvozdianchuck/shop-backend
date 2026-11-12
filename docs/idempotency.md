# Idempotency

## Why duplicates happen

- Producer retries (covered by the idempotent producer, only within one producer session).
- Outbox relay: sent but crashed before `published_at` → batch sent again.
- Consumer: processed but crashed before committing the offset → record read again. Also on rebalance.
- So at-least-once is the normal mode, and handlers have to tolerate a repeat.

## Idempotent consumer

- Store ids of processed events, check before handling. Here `processed_events (event_id PK, processed_at)`.
- Check and record in one statement: `INSERT ... ON CONFLICT DO NOTHING`, look at the row count
  (`ProcessedEvents.markProcessed`). Select-then-insert is check-then-act: two deliveries can both pass the
  select.
- Same transaction as the business change and the outgoing outbox row. If processing fails, the id is rolled
  back too and the retry is not treated as a duplicate (`ProcessedEventsTest.forgetsEventWhenProcessingRollsBack`).
- The id must be stable across redeliveries: here the outbox row id, sent as the `eventId` header. A random id
  generated at send time would defeat the check.
- Metric `events.duplicates` counts skipped ones.

## How long to keep ids

- Longer than a duplicate can arrive. Records live 7 days in the topic (`retention.ms` default), ids are kept
  14 days (`OutboxCleanup`).
- If ids are forgotten too early, an old redelivered record (for example after resetting a consumer group)
  would be processed again.

## Other ways

- Natural idempotency: operations that are safe to repeat (`status = CONFIRMED` only from `PENDING`, upsert by
  key). We keep these rules as a second line of defence.
- v1 used the business key: `reservations.order_id` PK and resending the stored result. Works for one event
  type, but every handler needed its own variant.
- HTTP side: an `Idempotency-Key` header on POST, server stores key → response. Not done here; a client retry
  of `POST /api/orders` creates a second order.
