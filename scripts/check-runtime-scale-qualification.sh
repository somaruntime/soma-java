#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"
. "$root_dir/scripts/lib/sha256.sh"
. "$root_dir/scripts/lib/supported-jdk.sh"

soma_require_supported_jdk runtime-scale-qualification

mode=${1:-qualification}
if [ "$#" -gt 1 ] \
    || { [ "$mode" != 'qualification' ] && [ "$mode" != 'research' ]; }; then
  printf '%s\n' \
    'usage: scripts/check-runtime-scale-qualification.sh [qualification|research]' >&2
  exit 2
fi

physical_memory_bytes=''
if command -v sysctl >/dev/null 2>&1; then
  physical_memory_bytes=$(sysctl -n hw.memsize 2>/dev/null || true)
fi
if [ -z "$physical_memory_bytes" ] && [ -r /proc/meminfo ]; then
  physical_memory_kib=$(sed -n \
    's/^MemTotal:[[:space:]]*\([0-9][0-9]*\)[[:space:]]*kB$/\1/p' \
    /proc/meminfo | head -n 1)
  if [ -n "$physical_memory_kib" ]; then
    physical_memory_bytes=$((physical_memory_kib * 1024))
  fi
fi
if [ -z "$physical_memory_bytes" ] \
    && command -v getconf >/dev/null 2>&1; then
  physical_pages=$(getconf _PHYS_PAGES 2>/dev/null || true)
  page_size=$(getconf PAGE_SIZE 2>/dev/null || true)
  if [ -n "$physical_pages" ] && [ -n "$page_size" ]; then
    physical_memory_bytes=$((physical_pages * page_size))
  fi
fi
physical_memory_gb=''
if [ -n "$physical_memory_bytes" ]; then
  physical_memory_gb=$((physical_memory_bytes / 1073741824))
fi
minimum_memory_gb=12
if [ "$mode" = 'research' ]; then
  minimum_memory_gb=40
fi
if [ -z "$physical_memory_gb" ] \
    || [ "$physical_memory_gb" -lt "$minimum_memory_gb" ]; then
  printf '%s\n' \
    "runtime-scale-$mode: at least ${minimum_memory_gb}GiB physical memory is required, got ${physical_memory_gb:-unknown}GiB" >&2
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
      soma_sha256 "$source_file"
    fi
  done >"$evidence_dir/source-files.sha256"
tree_checksum=$(soma_sha256 "$evidence_dir/source-files.sha256" |
  awk '{print $1}')
tree_state="content-sha256:$tree_checksum"
qualification_id="runtime-scale-$mode-20260729-$(printf '%s' "$tree_checksum" | cut -c1-12)"
cpu_identity=''
if command -v system_profiler >/dev/null 2>&1; then
  cpu_identity=$(system_profiler SPHardwareDataType 2>/dev/null |
    sed -n 's/^[[:space:]]*Chip: //p' | head -n 1)
fi
if [ -z "$cpu_identity" ] && [ -r /proc/cpuinfo ]; then
  cpu_identity=$(sed -n \
    's/^model name[[:space:]]*:[[:space:]]*//p' \
    /proc/cpuinfo | head -n 1)
fi
if [ -z "$cpu_identity" ]; then
  cpu_identity=$(uname -m)
fi

classpath="soma-benchmarks/target/classes:soma-runtime-core/target/classes:soma-dataflow/target/classes"
runner=io.github.somaruntime.soma.benchmarks.RuntimeScaleQualificationRunner
validator=io.github.somaruntime.soma.benchmarks.RuntimeScaleQualificationArtifactValidator

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

if [ "$mode" = 'qualification' ]; then
  lanes='small-fast medium 1m-single 1m-double string expansion delivery soak'
  run_lane small-fast 512m 2g 300
  run_lane medium 1g 3g 600
  run_lane 1m-single 1g 4g 1200
  run_lane 1m-double 1g 4g 1800
  run_lane string 2g 6g 1800
  run_lane expansion 512m 2g 300
  run_lane delivery 512m 2g 600
  run_lane soak 512m 2g 1800
else
  lanes='10m-research 100m-single-stress 100m-double-stress 100m-string-stress'
  run_lane 10m-research 4g 10g 2400
  run_lane 100m-single-stress 4g 24g 5400
  run_lane 100m-double-stress 4g 28g 7200
  run_lane 100m-string-stress 4g 12g 7200
fi

artifact=$evidence_dir/runtime-scale-$mode.jsonl
for lane in $lanes; do
  sed -n '1p' "$records_dir/$lane.jsonl"
done >"$artifact"

if [ "$mode" = 'qualification' ]; then
  "$JAVA_HOME/bin/java" -cp "$classpath" "$validator" \
    --complete "$artifact"
else
  "$JAVA_HOME/bin/java" -cp "$classpath" "$validator" "$artifact"
fi

record_count=$(wc -l <"$artifact" | tr -d ' ')
expected_records=8
if [ "$mode" = 'research' ]; then
  expected_records=4
fi
if [ "$record_count" -ne "$expected_records" ]; then
  printf '%s\n' \
    "runtime-scale-$mode: expected $expected_records records, got $record_count" >&2
  exit 1
fi
if grep -F '"claimAllowed":true' "$artifact" >/dev/null; then
  printf '%s\n' \
    "runtime-scale-$mode: claim boundary violated" >&2
  exit 1
fi
if [ "$mode" = 'qualification' ]; then
  if grep -v -F '"status":"passed"' "$artifact" >/dev/null \
      || grep -v -F '"profile":"production-exact-v1"' "$artifact" >/dev/null \
      || grep -v -F '"required":true' "$artifact" >/dev/null; then
    printf '%s\n' \
      'runtime-scale-qualification: status/profile/required boundary violated' >&2
    exit 1
  fi
elif grep -v -F '"profile":"research-stress-v1"' "$artifact" >/dev/null \
    || grep -v -F '"required":false' "$artifact" >/dev/null; then
  printf '%s\n' \
    'runtime-scale-research: profile/required boundary violated' >&2
  exit 1
fi

negative_claim=$evidence_dir/negative-claim.jsonl
sed 's/"claimAllowed":false/"claimAllowed":true/' \
  "$records_dir/$(printf '%s' "$lanes" | awk '{print $1}').jsonl" \
  >"$negative_claim"
if "$JAVA_HOME/bin/java" -cp "$classpath" "$validator" "$negative_claim" \
    >"$evidence_dir/negative-claim.out" 2>&1; then
  printf '%s\n' \
    'runtime-scale-qualification: claimAllowed=true was accepted' >&2
  exit 1
fi

if [ "$mode" = 'qualification' ]; then
  negative_scale=$evidence_dir/negative-scale.jsonl
  sed 's/"leftRows":1000000/"leftRows":999999/' \
    "$records_dir/1m-single.jsonl" >"$negative_scale"
  if "$JAVA_HOME/bin/java" -cp "$classpath" "$validator" "$negative_scale" \
      >"$evidence_dir/negative-scale.out" 2>&1; then
    printf '%s\n' \
      'runtime-scale-qualification: shrunken 1M profile was accepted' >&2
    exit 1
  fi
fi

negative_extra=$evidence_dir/negative-extra-field.jsonl
sed 's/"failureReason":""}/"failureReason":"","extra":1}/' \
  "$records_dir/$(printf '%s' "$lanes" | awk '{print $1}').jsonl" \
  >"$negative_extra"
if "$JAVA_HOME/bin/java" -cp "$classpath" "$validator" "$negative_extra" \
    >"$evidence_dir/negative-extra-field.out" 2>&1; then
  printf '%s\n' \
    'runtime-scale-qualification: unknown record field was accepted' >&2
  exit 1
fi

schema=soma-benchmarks/src/main/resources/META-INF/soma/runtime-scale-qualification-schema-v2.json
cmp "$schema" \
  soma-benchmarks/target/classes/META-INF/soma/runtime-scale-qualification-schema-v2.json
for class_name in \
  RuntimeScaleQualificationRunner \
  RuntimeScaleQualificationArtifactValidator; do
  major=$("$JAVA_HOME/bin/javap" \
    -classpath soma-benchmarks/target/classes \
    -verbose "io.github.somaruntime.soma.benchmarks.$class_name" |
    sed -n 's/^[[:space:]]*major version: //p' | head -n 1)
  if [ "$major" != '52' ]; then
    printf '%s\n' \
      "runtime-scale-qualification: expected Java 8 major 52 for $class_name" >&2
    exit 1
  fi
done

soma_sha256 "$artifact" "$schema" \
  "$evidence_dir/source-files.sha256" \
  >"$evidence_dir/checksums.sha256"
"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./mvnw -version
uname -srm
printf '%s\n' \
  "runtime-scale-$mode-evidence: $evidence_dir"
printf '%s\n' \
  "runtime-scale-$mode: ok"
