#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/fjsp-allocation-gc.XXXXXX")
artifact=$evidence_dir/fjsp-100k-allocation-gc.jsonl

SOMA_FJSP_JVM_ARGS='-Xms512m -Xmx512m -Xmn96m -XX:+UseParallelGC' \
  ./scripts/run-fjsp-100k-benchmark.sh \
  --warmup 1 --measurements 1 --output "$artifact"

record_count=$(wc -l <"$artifact" | tr -d ' ')
if [ "$record_count" -ne 1 ]; then
  printf '%s\n' "fjsp-allocation-gc-check: expected 1 record, got $record_count" >&2
  exit 1
fi

for expected in \
  '"schemaVersion":"soma-fjsp-benchmark-v2"' \
  '"scenario":"fjsp.solve.fcfs_spt_100k"' \
  '"claimAllowed":false' \
  '"operations":100000' \
  '"allocationMethod":"thread-mxbean-current-thread-exact"' \
  '"gcCollectorNames":"'; do
  if ! grep -F "$expected" "$artifact" >/dev/null; then
    printf '%s\n' "fjsp-allocation-gc-check: missing evidence $expected" >&2
    exit 1
  fi
done

if grep -E '"(importAllocatedBytes|solveAllocatedBytes|exportAllocatedBytes|totalAllocatedBytes|allocatedBytesPerOperation)":null' \
    "$artifact" >/dev/null \
    || grep -F '"gcCollectorNames":""' "$artifact" >/dev/null; then
  printf '%s\n' 'fjsp-allocation-gc-check: allocation/GC observation unavailable' >&2
  exit 1
fi

integer_field() {
  field=$1
  sed -n "s/.*\"$field\":\([0-9][0-9]*\).*/\1/p" "$artifact"
}

import_bytes=$(integer_field importAllocatedBytes)
solve_bytes=$(integer_field solveAllocatedBytes)
export_bytes=$(integer_field exportAllocatedBytes)
total_bytes=$(integer_field totalAllocatedBytes)
for field in youngGcCount youngGcTimeMillis fullGcCount fullGcTimeMillis \
    unknownGcCount unknownGcTimeMillis; do
  value=$(integer_field "$field")
  if [ -z "$value" ]; then
    printf '%s\n' "fjsp-allocation-gc-check: missing numeric field $field" >&2
    exit 1
  fi
done

if [ -z "$import_bytes" ] || [ -z "$solve_bytes" ] \
    || [ -z "$export_bytes" ] || [ -z "$total_bytes" ] \
    || [ "$total_bytes" -ne $((import_bytes + solve_bytes + export_bytes)) ]; then
  printf '%s\n' 'fjsp-allocation-gc-check: phase allocation total mismatch' >&2
  exit 1
fi

if ! grep -E '"allocatedBytesPerOperation":[0-9]+([.][0-9]+)?' \
    "$artifact" >/dev/null; then
  printf '%s\n' 'fjsp-allocation-gc-check: allocation/op is not numeric' >&2
  exit 1
fi

shasum -a 256 "$artifact" \
  soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/FjspScaleBenchmark.java \
  soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/JvmRuntimeMetrics.java \
  >"$evidence_dir/checksums.sha256"

printf '%s\n' "fjsp-allocation-gc-evidence: $evidence_dir"
printf '%s\n' 'fjsp-allocation-gc-check: ok'
