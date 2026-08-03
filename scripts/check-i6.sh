#!/usr/bin/env bash
set -euo pipefail

I6_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$I6_ROOT"
./scripts/check-i5.sh
I6_CP="$I6_ROOT/tests/i2/consumer/target/classes:$I6_ROOT/soma-runtime/target/classes"
java -cp "$I6_CP" com.example.soma.i2.ParallelFailureProbe
I6_GENERATED="$I6_ROOT/tests/i2/consumer/target/generated-sources/annotations/com/example/soma/i2/soma/ScalarRecordTable.java"
grep -F 'public Selection parallel()' "$I6_GENERATED" >/dev/null ||
    { printf 'I6 qualification failed: parallel marker missing\n' >&2; exit 1; }
grep -F 'parallelCount' "$I6_GENERATED" >/dev/null ||
    { printf 'I6 qualification failed: typed parallel count linkage missing\n' >&2; exit 1; }
grep -F 'ForkJoinPool' "$I6_ROOT/soma-runtime/src/main/java/io/github/somaruntime/soma/internal/SomaRuntimeAccess.java" >/dev/null ||
    { printf 'I6 qualification failed: shared executor owner missing\n' >&2; exit 1; }
printf '%s\n' \
    'I6 parallel qualification: PASS' \
    'proofs=typed-parallel-count,custom-forkjoin-pool,caller-participation,saturated-pool-progress,start-gate,foreign-view-rejection,range-order,sequential-equivalence,'\
'callback-barrier,unsupported-terminal-preclaim,shutdown-rejection,java8-generated-surface,I1-I5-regression' \
    'limitations=bounded typed count only; no full G7/parallel materialization/Join/mutation claim'
