# Scheduling scenario

This is a runnable application-shaped scenario, not a toy snippet.  It models
the hot scheduling state in a generated SOMA Table and keeps decision ordering,
compensation, and orchestration in ordinary application code.

## Structure

- `soma/schema/Job.java`: package-private schema declaration; the processor owns
  generated `Job`, `JobTable`, `Soma`, and `SomaGroup`.
- `domain/`: detached decision values and business lifecycle enum.
- `application/`: load, candidate selection, and state-transition workflow.

The current executable SOMA surface provides one scalar Table per composition.
The scenario therefore does not pretend that an unqualified multi-table Join
exists.  Machine capacity and cross-table compensation remain explicit future
application boundaries until that surface has its own conformance evidence.

## Workflow

1. Reserve the expected row capacity before loading.
2. Use the generated `byMachineId` Index and typed `state` predicate to select
   candidates.
3. Detach records before sorting in OOP code; never retain a borrowed View.
4. Publish state transitions through point `update`, leaving the Key immutable.
5. Use explicit `parallel()` only for a read-only count with an application-owned
   `ForkJoinPool`.

Run from the repository root with `./soma-examples/verify.sh` or build this project with:

```sh
mvn -f soma-examples/scheduling/pom.xml clean verify
java -ea -cp soma-runtime/target/classes:soma-examples/scheduling/target/classes \
  io.github.somaruntime.examples.scheduling.application.SchedulingApplication
```

The command is a local qualification smoke, not a G9 performance or release claim.
