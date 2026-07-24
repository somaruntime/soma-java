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
    "$root_dir/target/reference-performance-full.XXXXXX")
else
  printf '%s\n' \
    'usage: check-reference-application-full-performance.sh [evidence-dir]' >&2
  exit 1
fi

./scripts/check-reference-application-fast-performance.sh \
  "$evidence_dir/fast"
./scripts/check-reference-application-scale-performance.sh \
  "$evidence_dir/scale"
./scripts/check-reference-application-soak-performance.sh \
  "$evidence_dir/soak"
./scripts/check-performance-baseline-architecture.sh

printf '%s\n' "reference-application-full-performance: $evidence_dir"
printf '%s\n' 'reference-application-full-performance: ok'
