#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ] \
    || [ ! -x "$JAVA_HOME/bin/javac" ] \
    || [ ! -x "$JAVA_HOME/bin/javap" ]; then
  printf '%s\n' \
    'runtime-scale-qualification: JAVA_HOME must point to Azul Zulu full JDK 8' >&2
  exit 1
fi

java_properties=$("$JAVA_HOME/bin/java" -XshowSettings:properties -version 2>&1)
java_specification=$(printf '%s\n' "$java_properties" |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
java_vendor=$(printf '%s\n' "$java_properties" |
  sed -n 's/^[[:space:]]*java.vendor = //p' | head -n 1)
java_runtime=$(printf '%s\n' "$java_properties" |
  sed -n 's/^[[:space:]]*java.runtime.version = //p' | head -n 1)
if [ "$java_specification" != '1.8' ] \
    || [ "$java_vendor" != 'Azul Systems, Inc.' ] \
    || [ "$java_runtime" != '1.8.0_492-b09' ]; then
  printf '%s\n' \
    "runtime-scale-qualification: expected Zulu 1.8.0_492-b09, got $java_vendor $java_runtime" >&2
  exit 1
fi

physical_memory_gb=$(system_profiler SPHardwareDataType 2>/dev/null |
  sed -n 's/^[[:space:]]*Memory: \([0-9][0-9]*\) GB$/\1/p' |
  head -n 1)
if [ -z "$physical_memory_gb" ] || [ "$physical_memory_gb" -lt 40 ]; then
  printf '%s\n' \
    "runtime-scale-qualification: at least 40GiB physical memory is required, got ${physical_memory_gb:-unknown}GiB" >&2
  exit 1
fi

./mvnw -B -ntp -pl soma-benchmarks -am clean test-compile

mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/runtime-scale-qualification.XXXXXX")
records_dir=$evidence_dir/records
mkdir -p "$records_dir"

commit=$(git rev-parse HEAD)
# Bind qualification identity to the executable product/evidence surface. Reports,
# Temporary governance notes and unrelated repository files may be finalized after
# the run without retroactively changing which implementation was qualified.
git ls-files -co --exclude-standard -- \
  pom.xml mvnw '.mvn/**' \
  'soma-annotations/pom.xml' 'soma-annotations/src/**' \
  'soma-runtime-core/pom.xml' 'soma-runtime-core/src/**' \
  'soma-dataflow/pom.xml' 'soma-dataflow/src/**' \
  'soma-processor/pom.xml' 'soma-processor/src/**' \
  'soma-benchmarks/pom.xml' 'soma-benchmarks/src/**' \
  'soma-examples/pom.xml' 'soma-examples/*/pom.xml' \
  'soma-examples/*/src/**' \
  scripts/check-runtime-scale-qualification.sh |
  LC_ALL=C sort |
  while IFS= read -r source_file; do
    if [ -f "$source_file" ]; then
      shasum -a 256 "$source_file"
    fi
  done >"$evidence_dir/source-files.sha256"
tree_checksum=$(shasum -a 256 "$evidence_dir/source-files.sha256" |
  awk '{print $1}')
tree_state="content-sha256:$tree_checksum"
qualification_id="runtime-scale-qualification-20260728-$(printf '%s' "$tree_checksum" | cut -c1-12)"
cpu_identity=$(system_profiler SPHardwareDataType 2>/dev/null |
  sed -n 's/^[[:space:]]*Chip: //p' | head -n 1)
if [ -z "$cpu_identity" ]; then
  cpu_identity=$(uname -m)
fi

classpath="soma-benchmarks/target/classes:soma-runtime-core/target/classes:soma-dataflow/target/classes"
runner=com.hgtech.soma.benchmarks.RuntimeScaleQualificationRunner
validator=com.hgtech.soma.benchmarks.RuntimeScaleQualificationArtifactValidator

run_lane() {
  lane=$1
  xms=$2
  xmx=$3
  timeout_seconds=$4
  output=$records_dir/$lane.jsonl
  /usr/bin/perl -e 'alarm shift; exec @ARGV' "$timeout_seconds" \
    env SOMA_BENCHMARK_CPU="$cpu_identity" \
    "$JAVA_HOME/bin/java" \
    "-Xms$xms" "-Xmx$xmx" \
    -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
    -cp "$classpath" "$runner" \
    --output "$output" \
    --lane "$lane" \
    --qualification-id "$qualification_id" \
    --commit "$commit" \
    --tree-state "$tree_state" \
    --seed 1397706049
  "$JAVA_HOME/bin/java" -cp "$classpath" "$validator" "$output"
}

run_lane small-fast 512m 2g 300
run_lane medium 1g 3g 600
run_lane 1m 2g 6g 1200
run_lane 10m 4g 10g 2400
run_lane 100m-single 4g 24g 5400
run_lane 100m-double 4g 28g 7200
run_lane 100m-string 4g 12g 7200
run_lane expansion 512m 2g 300
run_lane delivery 512m 2g 600
run_lane soak 512m 2g 1800

artifact=$evidence_dir/runtime-scale-qualification.jsonl
for lane in \
  small-fast \
  medium \
  1m \
  10m \
  100m-single \
  100m-double \
  100m-string \
  expansion \
  delivery \
  soak; do
  sed -n '1p' "$records_dir/$lane.jsonl"
done >"$artifact"

"$JAVA_HOME/bin/java" -cp "$classpath" "$validator" \
  --complete "$artifact"

record_count=$(wc -l <"$artifact" | tr -d ' ')
if [ "$record_count" -ne 10 ]; then
  printf '%s\n' \
    "runtime-scale-qualification: expected 10 records, got $record_count" >&2
  exit 1
fi
if grep -F '"claimAllowed":true' "$artifact" >/dev/null \
    || grep -v -F '"status":"passed"' "$artifact" >/dev/null \
    || grep -v -F '"profile":"production-exact-v1"' "$artifact" >/dev/null; then
  printf '%s\n' \
    'runtime-scale-qualification: claim/status/profile boundary violated' >&2
  exit 1
fi

negative_claim=$evidence_dir/negative-claim.jsonl
sed 's/"claimAllowed":false/"claimAllowed":true/' \
  "$records_dir/small-fast.jsonl" >"$negative_claim"
if "$JAVA_HOME/bin/java" -cp "$classpath" "$validator" "$negative_claim" \
    >"$evidence_dir/negative-claim.out" 2>&1; then
  printf '%s\n' \
    'runtime-scale-qualification: claimAllowed=true was accepted' >&2
  exit 1
fi

negative_scale=$evidence_dir/negative-scale.jsonl
sed 's/"leftRows":100000000/"leftRows":99999999/' \
  "$records_dir/100m-single.jsonl" >"$negative_scale"
if "$JAVA_HOME/bin/java" -cp "$classpath" "$validator" "$negative_scale" \
    >"$evidence_dir/negative-scale.out" 2>&1; then
  printf '%s\n' \
    'runtime-scale-qualification: shrunken 100M profile was accepted' >&2
  exit 1
fi

negative_extra=$evidence_dir/negative-extra-field.jsonl
sed 's/"failureReason":""}/"failureReason":"","extra":1}/' \
  "$records_dir/delivery.jsonl" >"$negative_extra"
if "$JAVA_HOME/bin/java" -cp "$classpath" "$validator" "$negative_extra" \
    >"$evidence_dir/negative-extra-field.out" 2>&1; then
  printf '%s\n' \
    'runtime-scale-qualification: unknown record field was accepted' >&2
  exit 1
fi

schema=soma-benchmarks/src/main/resources/META-INF/soma/runtime-scale-qualification-schema-v1.json
cmp "$schema" \
  soma-benchmarks/target/classes/META-INF/soma/runtime-scale-qualification-schema-v1.json
for class_name in \
  RuntimeScaleQualificationRunner \
  RuntimeScaleQualificationArtifactValidator; do
  major=$("$JAVA_HOME/bin/javap" \
    -classpath soma-benchmarks/target/classes \
    -verbose "com.hgtech.soma.benchmarks.$class_name" |
    sed -n 's/^[[:space:]]*major version: //p' | head -n 1)
  if [ "$major" != '52' ]; then
    printf '%s\n' \
      "runtime-scale-qualification: expected Java 8 major 52 for $class_name" >&2
    exit 1
  fi
done

shasum -a 256 "$artifact" "$schema" \
  "$evidence_dir/source-files.sha256" \
  >"$evidence_dir/checksums.sha256"
"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./mvnw -version
uname -srm
printf '%s\n' \
  "runtime-scale-qualification-evidence: $evidence_dir"
printf '%s\n' \
  'runtime-scale-qualification: ok'
