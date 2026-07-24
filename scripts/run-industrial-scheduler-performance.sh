#!/bin/sh

set -eu

if [ "$#" -ne 3 ]; then
  printf '%s\n' \
    'usage: run-industrial-scheduler-performance.sh <default|large|long-run> <forks> <evidence-dir>' >&2
  exit 1
fi

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
exec "$root_dir/scripts/check-industrial-scheduler.sh" "$1" "$2" "$3"
