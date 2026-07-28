#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

./mvnw -B -ntp -pl soma-benchmarks -am test-compile

classpath="soma-benchmarks/target/classes:soma-benchmarks/target/test-classes"
classpath="$classpath:soma-dataflow/target/classes:soma-runtime-core/target/classes"
"$JAVA_HOME/bin/java" -ea -cp "$classpath" \
  io.github.somaruntime.soma.benchmarks.DataFlowReferenceDifferentialCheck

git diff --check
printf '%s\n' 'dataflow-reference-gate: ok'
