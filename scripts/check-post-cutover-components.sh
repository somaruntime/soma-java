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
artifact=$evidence_dir/post-cutover-components.jsonl
commit=$(git rev-parse HEAD)
cpu_identity=$(uname -m)

./mvnw -B -ntp -pl soma-benchmarks -am test-compile

classpath="soma-benchmarks/target/classes:soma-runtime-core/target/classes:soma-examples/target/classes"
SOMA_BENCHMARK_CPU="$cpu_identity" "$JAVA_HOME/bin/java" \
  -Xms256m -Xmx512m -cp "$classpath" \
  com.hgtech.soma.benchmarks.PostCutoverComponentBenchmark \
  --output "$artifact" --commit "$commit" --warmup 2000 --iterations 5000
"$JAVA_HOME/bin/java" -cp "$classpath" \
  com.hgtech.soma.benchmarks.PostCutoverComponentArtifactValidator "$artifact"

if "$JAVA_HOME/bin/java" -cp "$classpath" \
    com.hgtech.soma.benchmarks.PostCutoverComponentBenchmark --unknown value \
    >"$evidence_dir/invalid-option.log" 2>&1; then
  printf '%s\n' 'post-cutover-component-check: unknown CLI option accepted' >&2
  exit 1
fi

record_count=$(wc -l <"$artifact" | tr -d ' ')
if [ "$record_count" -ne 36 ]; then
  printf '%s\n' "post-cutover-component-check: expected 36 records, got $record_count" >&2
  exit 1
fi
if grep -v -F '"schemaVersion":"soma-post-cutover-component-v1"' "$artifact" >/dev/null \
    || grep -v -F '"claimAllowed":false' "$artifact" >/dev/null; then
  printf '%s\n' 'post-cutover-component-check: invalid schema or claim boundary' >&2
  exit 1
fi
for lane in \
  pipeline.packed_source_count \
  pipeline.packed_filter_count \
  pipeline.exact_source_count \
  pipeline.exact_skip_count \
  pipeline.exact_filter_count \
  pipeline.exact_filter_skip_limit_count \
  pipeline.exact_filter_skip_limit_filter_count \
  pipeline.exact_filter_skip_limit_filter_skip_count \
  pipeline.exact_filter_sort_snapshot \
  pipeline.exact_filter_sort_materialize \
  key.first_materialize \
  column.long_for_each; do
  if ! grep -F "\"lane\":\"$lane\"" "$artifact" >/dev/null; then
    printf '%s\n' "post-cutover-component-check: missing lane $lane" >&2
    exit 1
  fi
done

shasum -a 256 "$artifact" \
  soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PostCutoverComponentBenchmark.java \
  soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PostCutoverComponentArtifactValidator.java \
  >"$evidence_dir/checksums.sha256"

for class_name in PostCutoverComponentBenchmark PostCutoverComponentArtifactValidator; do
  major=$($JAVA_HOME/bin/javap -classpath soma-benchmarks/target/classes -verbose \
    "com.hgtech.soma.benchmarks.$class_name" |
    sed -n 's/^[[:space:]]*major version: //p' | head -n 1)
  if [ "$major" != '52' ]; then
    printf '%s\n' "post-cutover-component-check: expected Java 8 major 52 for $class_name" >&2
    exit 1
  fi
done

"$JAVA_HOME/bin/java" -version
printf '%s\n' "post-cutover-component-evidence: $evidence_dir"
printf '%s\n' 'post-cutover-component-check: ok'
