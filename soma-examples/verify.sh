#!/usr/bin/env bash
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPOSITORY_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)

for project in scheduling simulation dispatch; do
  mvn -q -f "$SCRIPT_DIR/$project/pom.xml" clean verify
done

java -ea -cp "$REPOSITORY_ROOT/soma-runtime/target/classes:$SCRIPT_DIR/scheduling/target/classes" \
  io.github.somaruntime.examples.scheduling.application.SchedulingApplication
java -ea -cp "$REPOSITORY_ROOT/soma-runtime/target/classes:$SCRIPT_DIR/simulation/target/classes" \
  io.github.somaruntime.examples.simulation.application.SimulationApplication
java -ea -cp "$REPOSITORY_ROOT/soma-runtime/target/classes:$SCRIPT_DIR/dispatch/target/classes" \
  io.github.somaruntime.examples.dispatch.application.DispatchApplication
