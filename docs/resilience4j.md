# Resilience4j notes

Docs: https://resilience4j.readme.io/docs , Spring Boot starter: `resilience4j-spring-boot4` (2.4.0 here) + `spring-boot-starter-aspectj`
for the annotations.

Applied in `ProductCatalogClient.findBySkus` and `OrderController.place`. Config in `order-service/src/main/resources/application.yml`.

## Circuit breaker

States:
- CLOSED: calls go through, results are recorded in a sliding window.
- OPEN: calls are rejected at once with `CallNotPermittedException`. After `wait-duration-in-open-state` it moves on.
- HALF_OPEN: lets `permitted-number-of-calls-in-half-open-state` trial calls through. Good enough → CLOSED, else OPEN again.
- Also DISABLED, FORCED_OPEN, METRICS_ONLY for manual control.

Settings that matter:
- `sliding-window-type`: count-based (last N calls) or time-based (last N seconds).
- `minimum-number-of-calls`: no decision before this many calls. Without it, 1 failure of 1 call = 100%.
- `failure-rate-threshold`, `slow-call-rate-threshold` + `slow-call-duration-threshold`: slow calls count too.
- `record-exceptions` / `ignore-exceptions`: what is a failure. If `record-exceptions` is set, anything else counts as
  success. We record only `HttpServerErrorException` and `ResourceAccessException`, so 4xx never opens the circuit
  (test `clientErrorsDoNotOpenCircuit`).
- Without `automatic-transition-from-open-to-half-open-enabled` the switch to HALF_OPEN happens on the next call
  after the wait, not by a timer. Seen when product-service was stopped and started again locally.

Seen when product-service was stopped locally: 2 earlier successes + 8 failures = 10 calls → 80% → OPEN; after that
`503` came back in 4 ms.

## Retry and backoff

- `max-attempts` includes the first call. 3 = 1 + 2 retries.
- Backoff: `wait-duration` 200 ms, `enable-exponential-backoff` × 2, `enable-randomized-wait` 0.5. With both flags on,
  Resilience4j uses `IntervalFunction.ofExponentialRandomBackoff`.
- Jitter so that many clients that failed together do not retry together.
- `retry-exceptions`: retry only what can get better. 4xx will not.
- `CallNotPermittedException` and `BulkheadFullException` are not in the list, so they are not retried
  (`IGNORED_ERROR` in `/actuator/retryevents`).
- Retry only idempotent things. GET of prices is fine. A POST that creates something is not, unless it has an idempotency key.

## Bulkhead

- Semaphore bulkhead: max N concurrent calls (`max-concurrent-calls: 20`), `max-wait-duration: 0` = reject at once with
  `BulkheadFullException`.
- Thread-pool bulkhead: runs calls in its own pool, only for async (`CompletableFuture`).
- Point: a slow dependency eats at most 20 request threads, the rest of the service keeps working.

## Rate limiter

- `limit-for-period` permits per `limit-refresh-period`. `timeout-duration: 0` = do not wait → `RequestNotPermitted`
  → we map to `429`.
- Per instance. Two instances = 2 × 50/s. A shared limit needs an API gateway (Spring Cloud Gateway with Redis,
  nginx, a cloud gateway) or a central store.

## Order of aspects

Default in Spring Boot: `Retry ( CircuitBreaker ( RateLimiter ( TimeLimiter ( Bulkhead ( call ) ) ) ) )`.
Can be changed with `*-aspect-order` properties.

Consequences here:
- every retry attempt is one call for the circuit breaker;
- an open circuit rejects before the bulkhead and the HTTP call;
- the rejection is not retried.

## Annotations and fallback

- `@Retry(name = "catalog", fallbackMethod = "catalogUnavailable")`, `@CircuitBreaker(name = "catalog")`, `@Bulkhead(name = "catalog")`.
- Annotations work through Spring AOP proxies: no effect on calls from inside the same class, and the method must
  be public on a bean.
- Fallback method: same parameters + one exception parameter, same return type. The most specific exception type wins
  if there are several fallbacks.
- Put the fallback on the outermost aspect, otherwise it fires per attempt and the retry never sees the failure.
- Our fallback throws `CatalogUnavailableException` → `503` + `Retry-After: 10` in `ApiExceptionHandler`.

## TimeLimiter

For `CompletableFuture` / reactive calls. A blocking `RestClient` call cannot be interrupted by it, so here the
HTTP client timeouts (`spring.http.clients.connect-timeout` 1 s, `read-timeout` 2 s) do that job.

## Spring Framework 7 built-ins

Spring Framework 7 has its own `@Retryable` and `@ConcurrencyLimit` (in `org.springframework.resilience`), enabled with
`@EnableResilientMethods`. Simple retry and concurrency limit without extra libraries. No circuit breaker or rate
limiter, so for this project Resilience4j is used for all of it.

## Actuator

`/actuator/circuitbreakers`, `/actuator/circuitbreakerevents`, `/actuator/retryevents` are exposed in order-service.
