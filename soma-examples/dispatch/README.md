# Real-time dispatch scenario

This project models the pending work queue of a real-time dispatch service.
The dispatch policy and external assignment side effect remain application
responsibilities; SOMA provides the keyed/indexed state, typed predicates,
detached candidate projection, and point publication.

## Structure

- `soma/schema/PendingDispatch.java`: generated hot-state Table with machine and
  priority Indexes.
- `domain/`: detached decision and business status types.
- `application/`: candidate lookup, explicit policy ordering, and assignment
  workflow.

## Workflow

1. Reserve the expected pending-row capacity.
2. Narrow by `byMachineId`, then apply a typed status predicate.
3. Map to detached `DispatchDecision` values and sort by the business priority
   policy in ordinary Java code.
4. Publish assignment and completion with Key-based point updates.
5. Use explicit `parallel()` only for a read-only readiness count; no per-stream
   executor is created.

Run from the repository root with `./soma-examples/verify.sh` or:

```sh
mvn -f soma-examples/dispatch/pom.xml clean verify
java -ea -cp soma-runtime/target/classes:soma-examples/dispatch/target/classes \
  io.github.somaruntime.examples.dispatch.application.DispatchApplication
```

This is a deterministic correctness smoke, not a G9 performance or release claim.
