#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

classpath="soma-benchmarks/target/classes:soma-benchmarks/target/test-classes"
classpath="$classpath:soma-dataflow/target/classes:soma-dataflow/target/test-classes"
classpath="$classpath:soma-runtime-core/target/classes"
if [ "${SOMA_BENCHMARKS_PREPARED:-false}" != 'true' ]; then
  ./mvnw -B -ntp -pl soma-benchmarks -am test-compile
fi

filter_check_class=soma-dataflow/target/test-classes/io/github/somaruntime/soma/dataflow/JoinRuntimeFilterContractCheck.class
if [ ! -s "$filter_check_class" ]; then
  printf '%s\n' \
    "dataflow-contracts: required test class missing: $filter_check_class" >&2
  exit 1
fi
"$JAVA_HOME/bin/java" -cp "$classpath" \
  io.github.somaruntime.soma.dataflow.JoinRuntimeFilterContractCheck

for check_class in \
  DataFlowInvocationContractCheck \
  DataFlowSelectionAndValueContractCheck \
  DataFlowRelationContractCheck \
  DataFlowMutationContractCheck \
  DataFlowExecutionContractCheck \
  DataFlowPointAndDeliveryContractCheck \
  DataFlowShapeAndGraphContractCheck
do
  class_file=soma-benchmarks/target/test-classes/io/github/somaruntime/soma/benchmarks/$check_class.class
  if [ ! -s "$class_file" ]; then
    printf '%s\n' \
      "dataflow-contracts: required test class missing: $class_file" >&2
    exit 1
  fi
  "$JAVA_HOME/bin/java" -cp "$classpath" \
    "io.github.somaruntime.soma.benchmarks.$check_class"
done

git diff --check
printf '%s\n' 'dataflow-contracts: ok'
