# SOMA Java scenario applications

`soma-examples` is a non-published application workspace for the three
canonical SOMA journeys:

| Subproject | Scenario | Primary SOMA role |
|---|---|---|
| `scheduling` | deterministic job scheduling | indexed candidate selection and point state transitions |
| `simulation` | event-driven simulation | indexed event order, detached decisions, and event removal |
| `dispatch` | real-time dispatch | low-allocation pending lookup and explicit parallel read |

These are serious consumer-shaped applications, not snippets.  Every project
has its own Maven boundary, domain layer, application service, schema package,
generated API, and runnable qualification entry point.  The application owns
business policy, orchestration, time encoding, external side effects, and
compensation.  SOMA owns only the schema-defined Table state and the operations
that its public API actually exposes.

## Deliberate product boundary

The production root reactor remains exactly `soma-runtime` plus
`soma-processor`.  This parent POM is intentionally outside the root reactor,
has install/deploy disabled, and is not a third SOMA production artifact.
The projects consume the local `soma-runtime` and `soma-processor` snapshots
through the normal Java 8 classpath/processorpath boundary.

The current executable generated surface is one scalar Table per composition.
The examples therefore use one authoritative SOMA Table per scenario and do
not fake a multi-table Join with private runtime APIs.  Domain objects can
represent the surrounding application state, while the missing multi-table
relation surface remains an explicit implementation/qualification boundary.
When that public surface is qualified, the scenarios can add ordinary Tables,
endpoint Indexes, and typed Equality Join without changing their application
boundaries.

## Build and qualification

From the repository root:

```sh
./soma-examples/verify.sh
```

The script compiles all three independent projects with Java 8, regenerates
their source, and runs their assertion-backed deterministic qualification
entry points.  The result is scenario correctness evidence only; it does not
close G9 performance, G10 package/release, or publish a package.

For one project, use its POM directly:

```sh
mvn -f soma-examples/scheduling/pom.xml clean verify
```

Generated source and `target/` output are build products and must not be
committed.
