# CQRS

https://martinfowler.com/bliki/CQRS.html

- Separate model for writes (commands, business rules) and for reads (queries shaped for screens).
- Often with a separate read store fed by events: a read model (projection). Reads become eventually consistent.

When it pays off:
- reads need data from several services (order + payment + delivery in one screen) and joining at query time
  is slow or impossible;
- read and write load differ a lot, or reads need another store (search, reports);
- the write model is complex and queries keep fighting it.

Cost: another store, a projector that must be idempotent and replayable, lag between write and read, rebuilds
when the projection changes.

Not here: `GET /api/orders` reads the same two tables the service writes, from one database. The only read
problem was N+1, solved with two queries (see [database.md](database.md)). If a "customer orders with delivery
status" screen appeared, a read model built from `orders` and delivery events would be the first candidate.
