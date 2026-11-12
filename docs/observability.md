# Observability

Spring Boot: https://docs.spring.io/spring-boot/reference/actuator/metrics.html ,
https://docs.spring.io/spring-boot/reference/actuator/tracing.html
W3C Trace Context: https://www.w3.org/TR/trace-context/

## Metrics

- Micrometer = facade, registry decides the backend. `micrometer-registry-prometheus` → `/actuator/prometheus`.
- Prometheus pulls (scrapes) that endpoint every N seconds; Grafana draws dashboards from Prometheus queries.
  Neither runs here, the endpoint is enough to see the numbers.
- Names: `outbox.pending` becomes `outbox_pending`, counters get `_total`.
- Ours (`MessagingAutoConfiguration`, `OutboxRelay`, `ProcessedEvents`): `outbox.pending` (gauge, a count query
  on each scrape), `outbox.publish.failures`, `events.duplicates`.
- The OpenTelemetry starter also brings `micrometer-registry-otlp`, which pushes metrics to
  `localhost:4318`. Switched off (`management.otlp.metrics.export.enabled=false`), Jaeger takes traces only.
- Useful alerts: `outbox_pending` growing for minutes; lag growing; DLT topics getting records.

## Consumer lag

- Lag = last offset in the partition − committed offset of the group. Growing lag = consumer too slow or
  stopped.
- From the client: `kafka_consumer_fetch_manager_records_lag_max` (per consumer, only while it runs).
- From the broker side, works when the consumer is down:
  `kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group product-service`.
- In production usually an exporter (kafka-exporter, Burrow) that reads group offsets.

## Tracing

- Micrometer Tracing (facade) + OpenTelemetry bridge, export over OTLP/HTTP to Jaeger
  (`management.opentelemetry.tracing.export.otlp.endpoint`). Sampling 1.0 locally (default is 0.1).
- Trace = tree of spans with one trace id. Context travels in the `traceparent` header:
  `00-<trace id>-<span id>-<flags>`.
- Spring instruments HTTP server/client itself. Kafka needs `spring.kafka.template.observation-enabled` and
  `spring.kafka.listener.observation-enabled`; then `traceparent` is written to and read from record headers.
- Outbox breaks the chain: the relay sends from its own thread later. `OutboxWriter` injects the current
  context into the `headers` column (`Propagator.inject`), `OutboxRelay` extracts it and starts the
  "outbox send" span with that parent. Test: `OutboxRelayTest.continuesTraceOfTheRequestThatWroteTheEvent`.
- Gotcha: Boot creates the `TextMapPropagator` only when tracing export is enabled. In tests export is off by
  default (`spring.test.tracing.export=false`), so propagation was silently no-op. Same trap in the cluster:
  switch off only the exporter (`management.tracing.export.otlp.enabled=false`), not
  `management.tracing.export.enabled`.

## Logs and correlation

- With tracing on, Boot adds `[traceId-spanId]` to each log line. That is the correlation id; a second id would
  only confuse.
- Structured JSON in the `k8s` profile: `logging.structured.format.console=ecs`. Fields `trace.id`,
  `span.id`. Locally plain text, easier to read.
- No personal data or message text in logs.
