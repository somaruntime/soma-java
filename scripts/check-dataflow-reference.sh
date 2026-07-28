#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

classpath="soma-benchmarks/target/classes:soma-benchmarks/target/test-classes"
classpath="$classpath:soma-dataflow/target/classes:soma-runtime-core/target/classes"
reference_class=soma-benchmarks/target/test-classes/io/github/somaruntime/soma/benchmarks/DataFlowReferenceDifferentialCheck.class
if [ "${SOMA_BENCHMARKS_PREPARED:-false}" != 'true' ]; then
  ./mvnw -B -ntp -pl soma-benchmarks -am test-compile
fi
if [ ! -s "$reference_class" ]; then
  printf '%s\n' \
    "dataflow-reference-gate: required test class missing: $reference_class" >&2
  exit 1
fi
"$JAVA_HOME/bin/java" -ea -cp "$classpath" \
  io.github.somaruntime.soma.benchmarks.DataFlowReferenceDifferentialCheck

git diff --check
printf '%s\n' 'dataflow-reference-gate: ok'
