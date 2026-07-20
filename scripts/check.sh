#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

./scripts/check-docs.sh
./mvnw -B -ntp verify
./scripts/check-build-governance.sh
./scripts/check-public-api.sh
./scripts/check-compiler-phase0.sh
./scripts/check-codegen-admission.sh
./scripts/check-internal-names-phase6.sh
./scripts/check-runtime-core-phase1.sh
./scripts/check-keyspace-phase2.sh
./scripts/check-generated-keyed-phase2.sh
./scripts/check-access-phase3.sh
./scripts/check-child-phase4.sh
./scripts/check-testkit-phase4.sh
./scripts/check-value-modifiers-phase5.sh
./scripts/check-defaults-phase5.sh
./scripts/check-floating-value-storage.sh
./scripts/check-breadth-phase5.sh
./scripts/check-table-diagnostics-phase1.sh
./scripts/check-generated-dense-phase1.sh
./scripts/check-external-consumer.sh
./scripts/check-examples-phase6.sh
./scripts/check-benchmark-smoke.sh
./scripts/check-post-cutover-components.sh
./scripts/check-fjsp-allocation-gc.sh
git diff --check

printf '%s\n' 'project-check: ok'
