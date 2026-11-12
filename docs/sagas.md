# Sagas

https://microservices.io/patterns/data/saga.html

- A business transaction across services without a distributed transaction: a sequence of local transactions,
  each publishing an event or a command for the next step.
- If a later step fails, earlier steps are undone by compensating actions. Compensation is a new action, not a
  rollback: stock that was taken is given back (`ReservationService.release`), the order is marked `CANCELLED`.

## Choreography vs orchestration

Choreography (ours): services react to each other's events.
- + no central component, services are loosely coupled.
- − the flow is spread over the services, hard to see as a whole; cyclic dependencies between events are easy
  to create.

Orchestration: one component (orchestrator) keeps the saga state and sends commands: "reserve stock",
"charge payment", "cancel reservation".
- + the whole flow and its compensations in one place, easy to add steps, timeouts and branches.
- − one more service with its own state and storage; risk of moving business logic into it.

When an orchestrator pays off: 3+ participants, branches ("if payment fails, release stock and notify"),
timeouts on steps ("no answer from payment in 10 min"). With order + stock it would be an extra moving part.
Tools if needed: Temporal, Camunda, or a hand-written state machine with its own table.

## Things to get right

- Compensations must be idempotent and must work even if the forward step never happened (release with no
  reservation does nothing).
- Order of events: `OrderCancelled` uses the same key as `OrderPlaced`, so it cannot overtake it in the
  partition. Otherwise product-service could get "cancel" before "place" and keep the stock forever.
- Semantic lock: `PENDING` shows the saga is still running; status rules decide what late events may still do.
- No isolation between sagas: other requests see intermediate states (`PENDING`, reserved but not confirmed).
- Pivot step: after some point the saga only goes forward. Here there is none, everything can be cancelled.
