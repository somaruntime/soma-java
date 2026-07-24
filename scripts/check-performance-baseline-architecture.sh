#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

component_baseline=soma-benchmarks/src/main/resources/META-INF/soma/performance-baselines/post-cutover-component-zulu8-macos-aarch64-v1.json
scheduler_baseline_dir=soma-examples/industrial-dynamic-scheduler/src/test/resources/benchmark
simulation_baseline_dir=soma-examples/grassing-individual-simulation/src/test/resources/benchmark
scheduler_default_baseline=$scheduler_baseline_dir/performance-baseline-default-zulu8-macos-aarch64-v2.json
scheduler_large_baseline=$scheduler_baseline_dir/performance-baseline-large-zulu8-macos-aarch64-v1.json
scheduler_long_run_baseline=$scheduler_baseline_dir/performance-baseline-long-run-zulu8-macos-aarch64-v1.json
simulation_default_baseline=$simulation_baseline_dir/performance-baseline-default-zulu8-macos-aarch64-v2.json
simulation_large_baseline=$simulation_baseline_dir/performance-baseline-large-zulu8-macos-aarch64-v1.json
simulation_long_run_baseline=$simulation_baseline_dir/performance-baseline-long-run-zulu8-macos-aarch64-v1.json

for baseline in \
  "$component_baseline" \
  "$scheduler_default_baseline" \
  "$scheduler_large_baseline" \
  "$scheduler_long_run_baseline" \
  "$simulation_default_baseline" \
  "$simulation_large_baseline" \
  "$simulation_long_run_baseline"; do
  if [ ! -f "$baseline" ]; then
    printf '%s\n' \
      "performance-baseline-architecture-check: missing baseline $baseline" >&2
    exit 1
  fi
  grep -F '"schemaVersion": "soma-performance-baseline-v1"' \
    "$baseline" >/dev/null
  grep -F '"claimAllowed": false' "$baseline" >/dev/null
  if grep -F '"claimAllowed": true' "$baseline" >/dev/null; then
    printf '%s\n' \
      "performance-baseline-architecture-check: claim enabled in $baseline" >&2
    exit 1
  fi
done

component_baseline_count=$(find \
  soma-benchmarks/src/main/resources/META-INF/soma/performance-baselines \
  -type f -name '*.json' | wc -l | tr -d ' ')
application_baseline_count=$(find \
  soma-examples/industrial-dynamic-scheduler/src \
  soma-examples/grassing-individual-simulation/src \
  -type f -name 'performance-baseline-*.json' | wc -l | tr -d ' ')
if [ "$component_baseline_count" -ne 1 ] \
    || [ "$application_baseline_count" -ne 6 ]; then
  printf '%s\n' \
    "performance-baseline-architecture-check: expected component=1 and application=6, got component=$component_baseline_count application=$application_baseline_count" >&2
  exit 1
fi

grep -F '"layer": "component"' "$component_baseline" >/dev/null

check_application_baseline() {
  baseline=$1
  subject=$2
  profile=$3
  artifact_version=$4

  grep -F '"layer": "reference-application"' "$baseline" >/dev/null
  grep -F "\"subject\": \"$subject\"" "$baseline" >/dev/null
  grep -F "\"artifactVersion\": \"$artifact_version\"" "$baseline" >/dev/null
  grep -F "\"profile\": \"$profile\"" "$baseline" >/dev/null
  grep -F '"forks": 9' "$baseline" >/dev/null
  grep -F '"minimumForks": 3' "$baseline" >/dev/null
}

check_application_baseline \
  "$scheduler_default_baseline" industrial-dynamic-scheduler default \
  industrial-scheduler-benchmark-v3
check_application_baseline \
  "$scheduler_large_baseline" industrial-dynamic-scheduler large \
  industrial-scheduler-benchmark-v3
check_application_baseline \
  "$scheduler_long_run_baseline" industrial-dynamic-scheduler long-run \
  industrial-scheduler-benchmark-v3
check_application_baseline \
  "$simulation_default_baseline" grassing-individual-simulation default \
  grassing-simulation-benchmark-v3
check_application_baseline \
  "$simulation_large_baseline" grassing-individual-simulation large \
  grassing-simulation-benchmark-v3
check_application_baseline \
  "$simulation_long_run_baseline" grassing-individual-simulation long-run \
  grassing-simulation-benchmark-v3

grep -F "$component_baseline" scripts/check-post-cutover-components.sh >/dev/null
grep -F \
  'performance-baseline-$profile-zulu8-macos-aarch64-$baseline_version.json' \
  scripts/check-industrial-scheduler.sh >/dev/null
grep -F \
  'performance-baseline-$profile-zulu8-macos-aarch64-$baseline_version.json' \
  scripts/check-grassing-simulation.sh >/dev/null
for script in \
  scripts/check-post-cutover-components.sh \
  scripts/check-industrial-scheduler.sh \
  scripts/check-grassing-simulation.sh; do
  grep -F 'PerformanceBaselineComparator' "$script" >/dev/null
done

if grep -R -F '<artifactId>soma-benchmarks</artifactId>' \
    soma-examples/industrial-dynamic-scheduler/pom.xml \
    soma-examples/grassing-individual-simulation/pom.xml >/dev/null \
    || grep -R -F 'com.hgtech.soma.examples' \
      soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PerformanceBaselineDefinition.java \
      soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PerformanceBaselineComparator.java \
      >/dev/null; then
  printf '%s\n' \
    'performance-baseline-architecture-check: ownership boundary regressed' >&2
  exit 1
fi

printf '%s\n' \
  'performance-baseline-architecture-check: component=1 reference-application=6 public-claim=0'
printf '%s\n' 'performance-baseline-architecture-check: ok'
