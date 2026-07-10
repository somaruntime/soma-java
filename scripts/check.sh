#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

./scripts/check-docs.sh
./mvnw -B -ntp verify
./scripts/check-public-api.sh
./scripts/check-compiler-phase0.sh
./scripts/check-runtime-core-phase1.sh
./scripts/check-keyspace-phase2.sh
./scripts/check-table-diagnostics-phase1.sh
./scripts/check-generated-dense-phase1.sh
./scripts/check-external-consumer.sh
git diff --check

printf '%s\n' 'project-check: ok'
