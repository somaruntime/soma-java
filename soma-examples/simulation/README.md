# Simulation scenario

This project treats a simulation event queue as a serious application state
owner.  The clock, entity state machine, and step policy belong to the
application; SOMA owns the indexed event membership and table-local mutation.

## Structure

- `soma/schema/SimulationEvent.java`: generated scalar event Table schema.
- `domain/`: event vocabulary and deterministic clock.
- `application/`: step, cancellation, detached event materialization, and
  entity-state transition workflow.

Dates and times are represented as application-owned primitive minutes, which
matches SOMA's explicit time-encoding boundary.  The workflow materializes
events before mutating the Table, so no callback-scoped View is retained.

## Workflow

1. Reserve and insert the event queue.
2. Locate a time slice through the generated `byDueMinute` Index.
3. Filter cancelled events with a typed expression.
4. Materialize detached events, apply the domain transition in encounter order,
   and remove each event by immutable Key.
5. Cancel an event with point `update`; no hidden transaction or external side
   effect is attributed to SOMA.

Run from the repository root with `./soma-examples/verify.sh` or:

```sh
mvn -f soma-examples/simulation/pom.xml clean verify
java -ea -cp soma-runtime/target/classes:soma-examples/simulation/target/classes \
  io.github.somaruntime.examples.simulation.application.SimulationApplication
```

This is a deterministic correctness smoke, not a G9 scale or release claim.
