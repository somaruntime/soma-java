#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

component_baseline=soma-benchmarks/src/main/resources/META-INF/soma/performance-baselines/post-cutover-component-zulu8-macos-aarch64-v1.json
scheduler_baseline=soma-examples/industrial-dynamic-scheduler/src/test/resources/benchmark/performance-baseline-zulu8-macos-aarch64-v1.json
simulation_baseline=soma-examples/grassing-individual-simulation/src/test/resources/benchmark/performance-baseline-zulu8-macos-aarch64-v1.json

for baseline in \
  "$component_baseline" \
  "$scheduler_baseline" \
  "$simulation_baseline"; do
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
    || [ "$application_baseline_count" -ne 2 ]; then
  printf '%s\n' \
    "performance-baseline-architecture-check: expected component=1 and application=2, got component=$component_baseline_count application=$application_baseline_count" >&2
  exit 1
fi

grep -F '"layer": "component"' "$component_baseline" >/dev/null
grep -F '"layer": "reference-application"' "$scheduler_baseline" >/dev/null
grep -F '"layer": "reference-application"' "$simulation_baseline" >/dev/null

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
  'performance-baseline-architecture-check: component=1 reference-application=2 public-claim=0'
printf '%s\n' 'performance-baseline-architecture-check: ok'
