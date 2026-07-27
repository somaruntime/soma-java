#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

mkdir -p target
if [ "$#" -eq 1 ]; then
  evidence_dir=$1
  mkdir -p "$evidence_dir"
elif [ "$#" -eq 0 ]; then
  evidence_dir=$(mktemp -d \
    "$root_dir/target/reference-performance-fast.XXXXXX")
else
  printf '%s\n' \
    'usage: check-reference-application-fast-performance.sh [evidence-dir]' >&2
  exit 1
fi

./scripts/run-industrial-scheduler-performance.sh \
  default 3 "$evidence_dir/industrial-scheduler"
./scripts/run-grassing-simulation-performance.sh \
  default 3 "$evidence_dir/grassing-simulation"
./scripts/run-real-time-dispatch-rule-engine-performance.sh \
  default 3 "$evidence_dir/rtd-rule-engine"

printf '%s\n' "reference-application-fast-performance: $evidence_dir"
printf '%s\n' 'reference-application-fast-performance: ok'
