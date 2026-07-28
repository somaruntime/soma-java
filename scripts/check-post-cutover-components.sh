#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ] \
    || [ ! -x "$JAVA_HOME/bin/javap" ]; then
  printf '%s\n' 'post-cutover-component-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

java_specification=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' "post-cutover-component-check: expected Java 8, got $java_specification" >&2
  exit 1
fi

mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/post-cutover-components.XXXXXX")
commit=$(git rev-parse HEAD)
cpu_identity=$(./scripts/benchmark-cpu-identity.sh)
forks=5
baseline=soma-benchmarks/src/main/resources/META-INF/soma/performance-baselines/post-cutover-component-zulu8-macos-aarch64-v1.json
baseline_result=$evidence_dir/performance-baseline-result.json

./mvnw -B -ntp -pl soma-benchmarks -am test-compile

if grep -F 'com.hgtech.soma.examples' \
    soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PostCutoverComponentBenchmark.java \
    >/dev/null; then
  printf '%s\n' 'post-cutover-component-check: example domain import detected' >&2
  exit 1
fi

classpath="soma-benchmarks/target/classes:soma-runtime-core/target/classes"
classpath="$classpath:soma-dataflow/target/classes"

long_sum_type='com.hgtech.soma.benchmarks.PostCutoverComponentBenchmark$LongSum'
long_sum_descriptor=$("$JAVA_HOME/bin/javap" \
  -classpath soma-benchmarks/target/classes -p "$long_sum_type")
if ! printf '%s\n' "$long_sum_descriptor" |
    grep -F 'PostCutoverComponentBenchmark$LongSum();' >/dev/null \
    || printf '%s\n' "$long_sum_descriptor" |
    grep -F 'PostCutoverComponentBenchmark$LongSum(com.hgtech.soma.benchmarks.PostCutoverComponentBenchmark$LongSum);' \
      >/dev/null; then
  printf '%s\n' \
    'post-cutover-component-check: unstable synthetic LongSum constructor detected' >&2
  exit 1
fi

if "$JAVA_HOME/bin/java" -cp "$classpath" \
    com.hgtech.soma.benchmarks.PostCutoverComponentBenchmark --unknown value \
    >"$evidence_dir/invalid-option.log" 2>&1; then
  printf '%s\n' 'post-cutover-component-check: unknown CLI option accepted' >&2
  exit 1
fi

fork=1
while [ "$fork" -le "$forks" ]; do
  SOMA_BENCHMARK_CPU="$cpu_identity" "$JAVA_HOME/bin/java" \
    -Xms256m -Xmx512m -cp "$classpath" \
    com.hgtech.soma.benchmarks.PostCutoverComponentBenchmark \
    --output "$evidence_dir/component-fork-$fork.jsonl" \
    --commit "$commit" --fork "$fork" --forks "$forks" \
    --warmup 2000 --iterations 5000
  fork=$((fork + 1))
done
set -- "$evidence_dir"/component-fork-*.jsonl
"$JAVA_HOME/bin/java" -cp "$classpath" \
  com.hgtech.soma.benchmarks.PostCutoverComponentArtifactValidator "$@"
"$JAVA_HOME/bin/java" \
  -cp "soma-benchmarks/target/test-classes:$classpath" \
  com.hgtech.soma.benchmarks.PerformanceBaselineComparatorCheck \
  "$evidence_dir/baseline-negative-paths"
"$JAVA_HOME/bin/java" -cp "$classpath" \
  com.hgtech.soma.benchmarks.PerformanceBaselineComparator \
  "$baseline" "$baseline_result" "$@"

record_count=0
for artifact in "$@"; do
  current_count=$(wc -l <"$artifact" | tr -d ' ')
  record_count=$((record_count + current_count))
done
if [ "$record_count" -ne $((40 * forks)) ]; then
  printf '%s\n' \
    "post-cutover-component-check: expected $((40 * forks)) records, got $record_count" >&2
  exit 1
fi
for artifact in "$@"; do
  if grep -v -F '"schemaVersion":"soma-post-cutover-component-v2"' \
      "$artifact" >/dev/null \
      || grep -v -F '"claimAllowed":false' "$artifact" >/dev/null; then
    printf '%s\n' \
      'post-cutover-component-check: invalid schema or claim boundary' >&2
    exit 1
  fi
done
for lane in \
  candidate_scan.packed_zero_count \
  candidate_scan.packed_one_filter_count \
  candidate_scan.exact_zero_count \
  candidate_scan.exact_zero_index \
  candidate_scan.exact_one_filter_count \
  candidate_scan.exact_two_stage_count \
  candidate_scan.exact_three_stage_count \
  candidate_scan.exact_four_stage_overflow_count \
  candidate_scan.exact_five_stage_overflow_count \
  candidate_scan.exact_sixteen_stage_overflow_count \
  candidate_scan.exact_filter_sort_index \
  candidate_scan.exact_filter_sort_snapshot \
  candidate_scan.exact_filter_sort_materialize \
  point.primary_find_index \
  key_traversal.first_materialize \
  column_traversal.long_for_each; do
  if ! grep -F "\"lane\":\"$lane\"" "$@" >/dev/null; then
    printf '%s\n' "post-cutover-component-check: missing lane $lane" >&2
    exit 1
  fi
done

shasum -a 256 "$@" \
  "$baseline" "$baseline_result" \
  soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PostCutoverComponentBenchmark.java \
  soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PostCutoverComponentArtifactValidator.java \
  soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PerformanceBaselineDefinition.java \
  soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PerformanceBaselineComparator.java \
  soma-benchmarks/src/test/java/com/hgtech/soma/benchmarks/PerformanceBaselineComparatorCheck.java \
  soma-benchmarks/target/classes/META-INF/soma/com.hgtech.soma.benchmarks.schema.schema.json \
  soma-benchmarks/target/classes/META-INF/soma/com.hgtech.soma.benchmarks.schema.schema.sha256 \
  >"$evidence_dir/checksums.sha256"

for class_name in \
  PostCutoverComponentBenchmark \
  PostCutoverComponentArtifactValidator \
  PerformanceBaselineComparator \
  PerformanceBaselineDefinition; do
  major=$($JAVA_HOME/bin/javap -classpath soma-benchmarks/target/classes -verbose \
    "com.hgtech.soma.benchmarks.$class_name" |
    sed -n 's/^[[:space:]]*major version: //p' | head -n 1)
  if [ "$major" != '52' ]; then
    printf '%s\n' "post-cutover-component-check: expected Java 8 major 52 for $class_name" >&2
    exit 1
  fi
done

"$JAVA_HOME/bin/java" -version
baseline_status=$(sed -n \
  's/.*"status":"\([^"]*\)".*/\1/p' "$baseline_result")
printf '%s\n' "post-cutover-component-baseline: $baseline_status"
printf '%s\n' "post-cutover-component-evidence: $evidence_dir"
printf '%s\n' 'post-cutover-component-check: ok'
