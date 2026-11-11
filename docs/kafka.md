# Kafka notes

Docs: https://kafka.apache.org/documentation/ , Spring Kafka reference: https://docs.spring.io/spring-kafka/reference/

## Brokers, topics, partitions

- Broker = one Kafka server. A cluster = several brokers. Since 4.0 there is no ZooKeeper, metadata lives in KRaft
  controllers. Our compose runs one node that is both broker and controller.
- Topic = named log, split into partitions. A partition is an append-only file with offsets 0, 1, 2...
- Order is guaranteed only inside a partition. Same key → same partition (hash of the key), so key by the thing whose
  events must stay in order. Here: `orderId`.
- Partition is also the unit of parallelism: in one group one partition is read by one consumer. 3 partitions → at most
  3 useful consumers per group.
- More partitions later is possible but changes key → partition mapping for new records. Pick the number up front.
- `auto.create.topics.enable=false` in compose. Topics come from `NewTopic` beans (`KafkaAdmin` creates them at
  startup): `orders` and `stock-events-dlt` in order-service, `stock-events` and `orders-dlt` in
  product-service.

## Replication

- Each partition has a leader and followers on other brokers. Producers and consumers talk to the leader.
- ISR = in-sync replicas, followers that keep up.
- Typical production setup: replication factor 3, `min.insync.replicas=2`, producer `acks=all`. Then a write
  survives one broker loss, and if two brokers are down the producer gets an error instead of silently writing
  to one copy.
- Locally RF=1, so none of this is real here. `replicas(1)` in `TopicBuilder` only because there is one broker.

## Producer

- `acks=0` fire and forget, `acks=1` leader wrote it, `acks=all` all ISR wrote it.
- Idempotent producer: broker remembers producer id + sequence number per partition and drops a retried duplicate.
  Needs `acks=all`. Both are defaults since Kafka 3.0; we still set them in `application.yml`.
- Idempotence only covers retries of one send inside one producer session. It does not help when the app sends the same
  thing twice (e.g. after a restart). That is what consumer-side idempotency is for.
- `KafkaTemplate.send()` is async. In `KafkaOrderEvents` we only log a failure; in `StockEventPublisher` we `join()` so a
  failed send fails the listener and the record is read again.

## Consumer groups, offsets, rebalancing

- Consumers with the same `group.id` share partitions. Different groups each get all records.
- Offset = position in a partition, committed per group to `__consumer_offsets`.
- Commit after processing → at-least-once. Commit before → at-most-once.
- Spring Kafka turns off `enable.auto.commit` and commits itself. `ack-mode: record` = commit after each record.
- `auto.offset.reset=earliest` matters only when the group has no committed offset yet.
- Rebalance: a consumer joins/leaves/dies → partitions are reassigned. Seen when a second product-service instance
  was started locally: with the classic protocol all partitions were revoked and then split (`orders-0, orders-1` /
  `orders-2`). Cooperative assignors and the new
  consumer protocol (KIP-848, GA in Kafka 4.0) avoid the stop-the-world revoke; we use the defaults.
- Long processing > `max.poll.interval.ms` (5 min) kicks the consumer out of the group.

## Delivery semantics

- At-most-once: may lose, never duplicates.
- At-least-once: never loses (after commit), may duplicate. Our choice.
- Exactly-once in Kafka = idempotent producer + transactions + `read_committed` consumers. Works for
  read-from-Kafka → write-to-Kafka. Our handlers write to Postgres, which is outside the Kafka transaction,
  so exactly-once would not cover it.
- So: at-least-once + idempotent handlers. `reservations` keyed by `orderId` (`ReservationService.reserve`),
  order status changes only from `PENDING` (`StockResultHandler`).

## Errors, DLQ, retry topics

- Spring Kafka default `DefaultErrorHandler`: 10 attempts with no pause, then log and skip. Not what we want.
- Ours (`KafkaConfig` in both services): `ExponentialBackOffWithMaxRetries(4)` 1 → 2 → 4 → 4 s, then
  `DeadLetterPublishingRecoverer` to `<topic>-dlt`, same partition. That is why the DLT has as many partitions.
- Not retryable: `DeserializationException` (default list) plus our `UnknownSkuException`,
  `UnknownOrderException`, `UnsupportedEventException`.
- `ErrorHandlingDeserializer` wraps the JSON deserializer. Without it broken JSON fails inside `poll()` and the
  container keeps failing on the same record. With it the error reaches the handler as a normal failure.
- Broken record arrives at the recoverer as `byte[]`. It needs a template with `ByteArraySerializer`, or the JSON
  serializer would write it as a base64 string.
- DLT headers: `kafka_dlt-exception-fqcn`, `-exception-message`, `-exception-stacktrace`, `-original-topic`,
  `-original-partition`, `-original-offset`... partition/offset are binary ints/longs, not text.
- Blocking retries (ours): the partition waits during retries, order is kept. Fine for short retries.
- `@RetryableTopic`: failed record goes to `topic-retry-0`, `-retry-1`... with delays, main partition keeps going.
  Better throughput, but events of one order can be processed out of order. Not ok for OrderPlaced → StockResult here.

## Spring Kafka bits used

- `@KafkaListener(topics = ...)`, group from `spring.kafka.consumer.group-id`.
- `JacksonJsonSerializer` / `JacksonJsonDeserializer` (Jackson 3 versions in Spring Kafka 4).
  `spring.json.add.type.headers=false` on the producer, `spring.json.use.type.headers=false` +
  `spring.json.value.default.type` on the consumer.
- A single `CommonErrorHandler` bean is picked up by Boot's listener container factory.
- In tests: `@ServiceConnection` sets the container address for Boot's auto-configuration only.
  `${spring.kafka.bootstrap-servers}` still points to compose. Use `KafkaConnectionDetails` in test code.
