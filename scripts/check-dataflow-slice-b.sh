#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

./mvnw -B -ntp -pl soma-benchmarks -am test

classpath="soma-benchmarks/target/classes:soma-benchmarks/target/test-classes"
classpath="$classpath:soma-dataflow/target/classes:soma-runtime-core/target/classes"
"$JAVA_HOME/bin/java" -cp "$classpath" \
  com.hgtech.soma.benchmarks.DataFlowFoundationCheck
"$JAVA_HOME/bin/java" -cp "$classpath" \
  com.hgtech.soma.benchmarks.DataFlowSliceACheck
"$JAVA_HOME/bin/java" -cp "$classpath" \
  com.hgtech.soma.benchmarks.DataFlowSliceBCheck

git diff --check
printf '%s\n' 'dataflow-slice-b-gate: ok'
