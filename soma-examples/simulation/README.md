# Grassing spatial simulation

This reference application rewrites the grassing individual-based model with SOMA runtime Tables.
It is a complete Java 8 application rather than a code fragment: deterministic configuration,
five model processes, result validation, console statistics, an optional Swing visualization and a
true headless execution path are kept behind explicit application boundaries.

## Runtime model

Two generated Tables are the only authoritative state:

- `GrassCellState`: one stable row per world cell, keyed by `cellId`;
- `GrasserState`: one row per live individual, keyed by `grasserId`, with a secondary `cellId`
  Index and primitive position, energy, direction and `searching` state.

Each tick executes in a fixed business order:

```text
grass growth -> metabolism/death -> reproduction -> grazing -> searching/movement
```

Application code owns that cross-Table protocol and deterministic RNG order. SOMA owns each
Table-local atomic update/add/remove, typed query, Index, GroupBy and Equality Join. The UI consumes
only detached primitive snapshots and never reads or mutates a Table.

## Headless mode

Headless is the canonical performance and CI mode. It creates no window, Toolkit, EDT, render
buffer, frame snapshot or pacing sleep:

```sh
mvn clean install -Dmaven.install.skip=false -DskipTests
mvn -f soma-examples/simulation/pom.xml clean package
java -Djava.awt.headless=true -Xms128m -Xmx1g \
  -cp 'soma-examples/simulation/target/classes:soma-runtime/target/*' \
  io.github.somaruntime.examples.simulation.application.SimulationMain \
  --headless --ticks=1000
```

It completes the same model, statistics, validation and fingerprint as UI mode, prints
`simulation-reference: PASS`, and exits normally.

## UI mode

The documented `config/grassing.properties` enables UI by default:

```sh
java -cp 'soma-examples/simulation/target/classes:soma-runtime/target/*' \
  io.github.somaruntime.examples.simulation.application.SimulationMain --ui
```

Grass quantity is rendered as green intensity. Grazing individuals are white squares; searching
individuals are orange directional triangles. Closing the window requests a clean application stop.
Use `--config=/path/to/file`, `--ticks=N`, `--ui` or `--headless` to select an execution.

## Project structure

```text
configuration/     strict immutable .properties input
engine/api/         synchronous engine, detached statistics/snapshot/result/timings
engine/core/        SOMA runtime ownership, initialization and fixed process schedule
runtime/schema/     two SOMA Table declarations
presentation/       console and optional Swing projection
validation/         final result invariant checks
src/test/           model, determinism, lifecycle and off-screen renderer evidence
```

The Example intentionally does not introduce an ECS framework, generic simulation SPI, event bus,
dependency-injection container or application shadow arrays. Performance work first optimizes normal
public SOMA API usage; a runtime change is admitted only when repeatable profiling proves a generic
SOMA-owned hotspot.

## Performance evidence

The long-lived `simulation-application` benchmark is a headless end-to-end journey with stable
fingerprints and cumulative timings for all five model processes:

```sh
SOMA_BENCHMARK_SCENARIOS=simulation-application \
SOMA_BENCHMARK_ROWS=10000 \
SOMA_BENCHMARK_IMPLEMENTATIONS=soma-auto \
./scripts/benchmark.sh
```

The application projects and sorts primitive `grasserId` arrays before point access instead of
sorting detached row-object arrays. This keeps deterministic process order while avoiding needless
object materialization. SOMA Selection mutation now stages PLAIN updates in a columnar write set and
uses a prevalidated dense remove plan rather than copying every leaf in every touched Chunk. The
improvement is a general runtime mechanism with formal correctness and resource evidence, not an
application shadow state or a scenario-specific patch.
