# Database: transactions, locking, N+1

## Propagation

https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html

- `REQUIRED` (default): join the current transaction or start one.
- `REQUIRES_NEW`: suspend the current one and start a separate transaction. Commits on its own, so used for an
  outbox write it would bring back dual write.
- `MANDATORY`: must be called inside a transaction, throws otherwise. Used on `OutboxWriter.append`,
  `ProcessedEvents.markProcessed`, `ReservationService.reserve/reject/release`: they only make sense together
  with the caller's change.
- `NESTED`: savepoint inside the current transaction. Needs JDBC savepoints; `JpaTransactionManager` refuses it
  unless `nestedTransactionAllowed` is set. Not used: a short reservation rolls back the whole attempt and the
  rejection goes into a second transaction (`OrderEventsListener.orderPlaced`).
- Self-invocation: `this.method()` does not go through the Spring proxy, so `@Transactional` on the called
  method is ignored. Put the boundary on a bean that is called from outside, or call through another bean.
- Boundaries here: one committed transaction per request or event, on application services / listener methods.
  In product-service the listener opens them with `TransactionOperations`, because a rejected reservation needs
  two attempts.

## Optimistic vs pessimistic locking

- Optimistic: `@Version` column, `UPDATE ... WHERE id = ? AND version = ?`, 0 rows → `ObjectOptimisticLockingFailureException`.
  No locks held while thinking; conflicts are detected at commit. Good when conflicts are rare. Used on
  `Order` (cancel vs stock result).
- Pessimistic: `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`), others wait. Good for hot rows with frequent
  conflicts; risk of waiting and deadlocks.
- Conditional update: `UPDATE stock SET available = available - ? WHERE sku = ? AND available >= ?` is a check
  and write in one statement. Best for counters.
- Lock rows in a fixed order (SKU order in `ReservationService`) to avoid deadlocks.

## Isolation levels (Postgres)

https://www.postgresql.org/docs/current/transaction-iso.html

- `READ COMMITTED` (default): each statement sees data committed before it started. Lost updates possible with
  read-modify-write in Java; fixed here by `@Version` and conditional updates.
- `REPEATABLE READ`: snapshot for the whole transaction; concurrent update of the same row →
  "could not serialize access", the transaction must be retried.
- `SERIALIZABLE` (SSI): as if transactions ran one by one; more serialization failures, retries mandatory.
- Higher levels only make sense with a retry loop around the whole transaction.

## N+1

- Load N parents, then lazy collections one by one → 1 + N queries. Easy to miss, logs look fine.
- Fixes:
  - `join fetch` / `@EntityGraph`: one query. With pagination Hibernate warns (HHH90003004) and paginates in
    memory, reading all rows.
  - Two steps: page of ids, then `where id in (:ids)` with `join fetch` (ours,
    `OrderRepository.findPageWithLines`). `Slice` instead of `Page` skips the count query.
  - `@BatchSize` / `hibernate.default_batch_fetch_size`: lazy collections loaded in batches with `IN`,
    1 + N/batch queries, no query changes needed.
  - DTO projection straight from SQL when entities are not needed.
- Catch it in tests: Hibernate statistics (`generate_statistics=true`, `getPrepareStatementCount()`),
  `OrderPageQueryTest`. The naive version gave 7 statements for a page of 5.
