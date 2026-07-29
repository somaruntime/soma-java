#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

access_component_baseline=soma-benchmarks/src/main/resources/META-INF/soma/performance-baselines/access-component-corretto8-macos-aarch64-v1.json
dataflow_component_baseline=soma-benchmarks/src/main/resources/META-INF/soma/performance-baselines/dataflow-component-corretto8-macos-aarch64-v4.json
scheduler_baseline_dir=soma-examples/industrial-dynamic-scheduler/src/test/resources/benchmark
simulation_baseline_dir=soma-examples/grassing-individual-simulation/src/test/resources/benchmark
scheduler_default_baseline=$scheduler_baseline_dir/performance-baseline-default-corretto8-macos-aarch64-v6.json
scheduler_large_baseline=$scheduler_baseline_dir/performance-baseline-large-corretto8-macos-aarch64-v5.json
scheduler_long_run_baseline=$scheduler_baseline_dir/performance-baseline-long-run-corretto8-macos-aarch64-v5.json
simulation_default_baseline=$simulation_baseline_dir/performance-baseline-default-corretto8-macos-aarch64-v4.json
simulation_large_baseline=$simulation_baseline_dir/performance-baseline-large-corretto8-macos-aarch64-v3.json
simulation_long_run_baseline=$simulation_baseline_dir/performance-baseline-long-run-corretto8-macos-aarch64-v3.json
rtd_baseline_dir=soma-examples/real-time-dispatch-rule-engine/src/test/resources/benchmark
rtd_default_baseline=$rtd_baseline_dir/performance-baseline-default-corretto8-macos-aarch64-v3.json
rtd_large_baseline=$rtd_baseline_dir/performance-baseline-large-corretto8-macos-aarch64-v3.json
rtd_long_run_baseline=$rtd_baseline_dir/performance-baseline-long-run-corretto8-macos-aarch64-v3.json

for baseline in \
  "$access_component_baseline" \
  "$dataflow_component_baseline" \
  "$scheduler_default_baseline" \
  "$scheduler_large_baseline" \
  "$scheduler_long_run_baseline" \
  "$simulation_default_baseline" \
  "$simulation_large_baseline" \
  "$simulation_long_run_baseline" \
  "$rtd_default_baseline" \
  "$rtd_large_baseline" \
  "$rtd_long_run_baseline"; do
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
  soma-examples/real-time-dispatch-rule-engine/src \
  -type f -name 'performance-baseline-*.json' | wc -l | tr -d ' ')
if [ "$component_baseline_count" -ne 2 ] \
    || [ "$application_baseline_count" -ne 9 ]; then
  printf '%s\n' \
    "performance-baseline-architecture-check: expected component=2 and application=9, got component=$component_baseline_count application=$application_baseline_count" >&2
  exit 1
fi

grep -F '"layer": "component"' "$access_component_baseline" >/dev/null
grep -F '"layer": "component"' "$dataflow_component_baseline" >/dev/null
grep -F '"subject": "typed-dataflow-component"' \
  "$dataflow_component_baseline" >/dev/null
grep -F '"minimumForks": 3' "$dataflow_component_baseline" >/dev/null
dataflow_calibration_forks=$(sed -n \
  's/^[[:space:]]*"forks": \([0-9][0-9]*\),$/\1/p' \
  "$dataflow_component_baseline" | head -n 1)
if [ "$dataflow_calibration_forks" != 3 ]; then
  printf '%s\n' \
    'performance-baseline-architecture-check: DataFlow calibration must use exactly 3 bounded forks' >&2
  exit 1
fi
dataflow_calibration_commit=$(sed -n \
  's/^[[:space:]]*"commit": "\([0-9a-f][0-9a-f]*\)",$/\1/p' \
  "$dataflow_component_baseline" | head -n 1)
if ! printf '%s\n' "$dataflow_calibration_commit" |
    grep -E '^[0-9a-f]{40}$' >/dev/null 2>&1; then
  printf '%s\n' \
    'performance-baseline-architecture-check: DataFlow calibration must name a full immutable commit' >&2
  exit 1
fi
if ! git cat-file -e "$dataflow_calibration_commit^{commit}" 2>/dev/null; then
  printf '%s\n' \
    "performance-baseline-architecture-check: DataFlow calibration commit is unavailable: $dataflow_calibration_commit" >&2
  exit 1
fi
if grep -E -i 'working-tree|dirty-candidate|thresholds=(relaxed|expanded)' \
    "$dataflow_component_baseline" >/dev/null 2>&1 \
    || ! grep -F 'thresholds=unchanged' \
      "$dataflow_component_baseline" >/dev/null 2>&1; then
  printf '%s\n' \
    'performance-baseline-architecture-check: DataFlow calibration provenance is mutable or relaxes thresholds' >&2
  exit 1
fi

check_application_baseline() {
  baseline=$1
  subject=$2
  profile=$3
  artifact_version=$4

  grep -F '"layer": "reference-application"' "$baseline" >/dev/null
  grep -F "\"subject\": \"$subject\"" "$baseline" >/dev/null
  grep -F "\"artifactVersion\": \"$artifact_version\"" "$baseline" >/dev/null
  grep -F "\"profile\": \"$profile\"" "$baseline" >/dev/null
  grep -F '"minimumForks": 3' "$baseline" >/dev/null
  calibration_forks=$(sed -n \
    's/^[[:space:]]*"forks": \([0-9][0-9]*\),$/\1/p' \
    "$baseline" | head -n 1)
  if [ -z "$calibration_forks" ] || [ "$calibration_forks" -lt 5 ]; then
    printf '%s\n' \
      "performance-baseline-architecture-check: $baseline requires at least 5 calibration forks" >&2
    exit 1
  fi
}

check_application_baseline \
  "$scheduler_default_baseline" industrial-dynamic-scheduler default \
  industrial-scheduler-benchmark-v4
check_application_baseline \
  "$scheduler_large_baseline" industrial-dynamic-scheduler large \
  industrial-scheduler-benchmark-v4
check_application_baseline \
  "$scheduler_long_run_baseline" industrial-dynamic-scheduler long-run \
  industrial-scheduler-benchmark-v4
check_application_baseline \
  "$simulation_default_baseline" grassing-individual-simulation default \
  grassing-simulation-benchmark-v3
check_application_baseline \
  "$simulation_large_baseline" grassing-individual-simulation large \
  grassing-simulation-benchmark-v3
check_application_baseline \
  "$simulation_long_run_baseline" grassing-individual-simulation long-run \
  grassing-simulation-benchmark-v3
check_application_baseline \
  "$rtd_default_baseline" real-time-dispatch-rule-engine default \
  rtd-dispatch-benchmark-v1
check_application_baseline \
  "$rtd_large_baseline" real-time-dispatch-rule-engine large \
  rtd-dispatch-benchmark-v1
check_application_baseline \
  "$rtd_long_run_baseline" real-time-dispatch-rule-engine long-run \
  rtd-dispatch-benchmark-v1

grep -F "$access_component_baseline" \
  scripts/check-access-performance.sh >/dev/null
grep -F "$dataflow_component_baseline" \
  scripts/check-dataflow-performance.sh >/dev/null
grep -F \
  'performance-baseline-$profile-corretto8-macos-aarch64-$baseline_version.json' \
  scripts/check-industrial-scheduler.sh >/dev/null
grep -F \
  'performance-baseline-$profile-corretto8-macos-aarch64-$baseline_version.json' \
  scripts/check-grassing-simulation.sh >/dev/null
grep -F \
  'performance-baseline-$profile-corretto8-macos-aarch64-$baseline_version.json' \
  scripts/check-real-time-dispatch-rule-engine.sh >/dev/null
for script in \
  scripts/check-access-performance.sh \
  scripts/check-dataflow-performance.sh \
  scripts/check-industrial-scheduler.sh \
  scripts/check-grassing-simulation.sh \
  scripts/check-real-time-dispatch-rule-engine.sh; do
  grep -F 'PerformanceBaselineComparator' "$script" >/dev/null
done

if grep -R -F '<artifactId>soma-benchmarks</artifactId>' \
    soma-examples/industrial-dynamic-scheduler/pom.xml \
    soma-examples/grassing-individual-simulation/pom.xml \
    soma-examples/real-time-dispatch-rule-engine/pom.xml >/dev/null \
    || grep -R -F 'io.github.somaruntime.soma.examples' \
      soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/PerformanceBaselineDefinition.java \
      soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/PerformanceBaselineComparator.java \
      >/dev/null; then
  printf '%s\n' \
    'performance-baseline-architecture-check: ownership boundary regressed' >&2
  exit 1
fi

printf '%s\n' \
  'performance-baseline-architecture-check: component=2 reference-application=9 public-claim=0'
printf '%s\n' 'performance-baseline-architecture-check: ok'
