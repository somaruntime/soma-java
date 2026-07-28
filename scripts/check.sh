#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

./scripts/check-docs.sh
./scripts/check-performance-baseline-architecture.sh
./mvnw -B -ntp verify
./scripts/check-build-governance.sh
./scripts/check-public-api.sh
./scripts/check-compiler-contracts.sh
./scripts/check-codegen-admission.sh
./scripts/check-generated-naming-contract.sh
./scripts/check-runtime-contracts.sh
./scripts/check-generated-keyed-contract.sh
./scripts/check-generated-access-contract.sh
./scripts/check-generated-ownership-contract.sh
./scripts/check-value-shape-contract.sh
./scripts/check-default-value-contract.sh
./scripts/check-floating-value-storage.sh
./scripts/check-generated-breadth-contract.sh
./scripts/check-schema-diagnostics-contract.sh
./scripts/check-generated-dense-contract.sh
./scripts/check-external-consumer.sh
./scripts/check-reference-applications.sh
./scripts/check-dataflow-contracts.sh
./scripts/check-dataflow-reference.sh
./scripts/check-scan-code-size.sh
./scripts/check-benchmark-smoke.sh
./scripts/check-access-performance.sh
./scripts/check-dataflow-performance.sh
git diff --check

printf '%s\n' 'project-check: ok'
