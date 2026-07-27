#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ] \
    || [ ! -x "$JAVA_HOME/bin/javap" ]; then
  printf '%s\n' \
    'dataflow-performance-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

java_specification=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' \
    "dataflow-performance-check: expected Java 8, got $java_specification" >&2
  exit 1
fi

mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/dataflow-performance.XXXXXX")
commit=$(git rev-parse HEAD)
cpu_identity=$(./scripts/benchmark-cpu-identity.sh)
forks=3
baseline=soma-benchmarks/src/main/resources/META-INF/soma/performance-baselines/dataflow-component-zulu8-macos-aarch64-v1.json
baseline_result=$evidence_dir/performance-baseline-result.json

./mvnw -B -ntp -pl soma-benchmarks -am test-compile

if grep -F 'com.hgtech.soma.examples' \
    soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/DataFlowComponentBenchmark.java \
    >/dev/null; then
  printf '%s\n' \
    'dataflow-performance-check: example domain import detected' >&2
  exit 1
fi

classpath="soma-benchmarks/target/classes:soma-dataflow/target/classes:soma-runtime-core/target/classes"

benchmark_type='com.hgtech.soma.benchmarks.DataFlowComponentBenchmark'
benchmark_descriptor=$("$JAVA_HOME/bin/javap" \
  -classpath soma-benchmarks/target/classes -p "$benchmark_type")
if printf '%s\n' "$benchmark_descriptor" |
    grep -E ' access\$[0-9]+\(' >/dev/null; then
  printf '%s\n' \
    'dataflow-performance-check: unstable outer synthetic accessor detected' >&2
  exit 1
fi

if "$JAVA_HOME/bin/java" -cp "$classpath" \
    com.hgtech.soma.benchmarks.DataFlowComponentBenchmark --unknown value \
    >"$evidence_dir/invalid-option.log" 2>&1; then
  printf '%s\n' \
    'dataflow-performance-check: unknown CLI option accepted' >&2
  exit 1
fi

SOMA_BENCHMARK_CPU="$cpu_identity" "$JAVA_HOME/bin/java" \
  -Xms256m -Xmx512m -cp "$classpath" \
  com.hgtech.soma.benchmarks.DataFlowComponentBenchmark \
  --output "$evidence_dir/admission.jsonl" \
  --commit "$commit" --fork 1 --forks 1 \
  --warmup 0 --iterations 1
admission_count=$(wc -l <"$evidence_dir/admission.jsonl" | tr -d ' ')
if [ "$admission_count" -ne 15 ] \
    || grep -v -F '"schemaVersion":"soma-dataflow-component-v1"' \
      "$evidence_dir/admission.jsonl" >/dev/null \
    || grep -v -F '"claimAllowed":false' \
      "$evidence_dir/admission.jsonl" >/dev/null; then
  printf '%s\n' \
    'dataflow-performance-check: class-load admission failed' >&2
  exit 1
fi

fork=1
while [ "$fork" -le "$forks" ]; do
  SOMA_BENCHMARK_CPU="$cpu_identity" "$JAVA_HOME/bin/java" \
    -Xms256m -Xmx512m -cp "$classpath" \
    com.hgtech.soma.benchmarks.DataFlowComponentBenchmark \
    --output "$evidence_dir/dataflow-fork-$fork.jsonl" \
    --commit "$commit" --fork "$fork" --forks "$forks" \
    --warmup 32 --iterations 64
  fork=$((fork + 1))
done

set -- "$evidence_dir"/dataflow-fork-*.jsonl
"$JAVA_HOME/bin/java" -cp "$classpath" \
  com.hgtech.soma.benchmarks.PerformanceBaselineComparator \
  "$baseline" "$baseline_result" "$@"

record_count=0
for artifact in "$@"; do
  current_count=$(wc -l <"$artifact" | tr -d ' ')
  record_count=$((record_count + current_count))
  if grep -v -F '"schemaVersion":"soma-dataflow-component-v1"' \
      "$artifact" >/dev/null \
      || grep -v -F '"claimAllowed":false' "$artifact" >/dev/null; then
    printf '%s\n' \
      'dataflow-performance-check: invalid schema or claim boundary' >&2
    exit 1
  fi
done
if [ "$record_count" -ne $((15 * forks)) ]; then
  printf '%s\n' \
    "dataflow-performance-check: expected $((15 * forks)) records, got $record_count" >&2
  exit 1
fi

shasum -a 256 "$@" "$baseline" "$baseline_result" \
  soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/DataFlowComponentBenchmark.java \
  soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/JvmRuntimeMetrics.java \
  soma-dataflow/src/main/java/com/hgtech/soma/dataflow/CandidateProgram.java \
  >"$evidence_dir/checksums.sha256"

for class_name in DataFlowComponentBenchmark PerformanceBaselineComparator; do
  major=$($JAVA_HOME/bin/javap -classpath soma-benchmarks/target/classes \
    -verbose "com.hgtech.soma.benchmarks.$class_name" |
    sed -n 's/^[[:space:]]*major version: //p' | head -n 1)
  if [ "$major" != '52' ]; then
    printf '%s\n' \
      "dataflow-performance-check: expected Java 8 major 52 for $class_name" >&2
    exit 1
  fi
done

"$JAVA_HOME/bin/java" -version
baseline_status=$(sed -n \
  's/.*"status":"\([^"]*\)".*/\1/p' "$baseline_result")
printf '%s\n' "dataflow-component-baseline: $baseline_status"
printf '%s\n' "dataflow-performance-evidence: $evidence_dir"
printf '%s\n' 'dataflow-performance-check: ok'
