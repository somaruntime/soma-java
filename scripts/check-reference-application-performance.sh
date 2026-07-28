#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  printf '%s\n' \
    'usage: check-reference-application-performance.sh <fast|scale|soak|full> [evidence-dir]' >&2
  exit 1
fi

mode=$1
case "$mode" in
  fast)
    profile=default
    ;;
  scale)
    profile=large
    ;;
  soak)
    profile=long-run
    ;;
  full)
    profile=
    ;;
  *)
    printf '%s\n' \
      'usage: check-reference-application-performance.sh <fast|scale|soak|full> [evidence-dir]' >&2
    exit 1
    ;;
esac

mkdir -p target
if [ "$#" -eq 2 ]; then
  evidence_dir=$2
  mkdir -p "$evidence_dir"
else
  evidence_dir=$(mktemp -d \
    "$root_dir/target/reference-performance-$mode.XXXXXX")
fi

if [ "$mode" = full ]; then
  "$0" fast "$evidence_dir/fast"
  "$0" scale "$evidence_dir/scale"
  "$0" soak "$evidence_dir/soak"
  ./scripts/check-performance-baseline-architecture.sh
else
  ./scripts/check-industrial-scheduler.sh \
    "$profile" 3 "$evidence_dir/industrial-scheduler"
  ./scripts/check-grassing-simulation.sh \
    "$profile" 3 "$evidence_dir/grassing-simulation"
  ./scripts/check-real-time-dispatch-rule-engine.sh \
    "$profile" 3 "$evidence_dir/rtd-rule-engine"
fi

printf '%s\n' "reference-application-$mode-performance: $evidence_dir"
printf '%s\n' "reference-application-$mode-performance: ok"
