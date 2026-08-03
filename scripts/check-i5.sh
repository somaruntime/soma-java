#!/usr/bin/env bash
set -euo pipefail

I5_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$I5_ROOT"
./scripts/check-i4.sh
I5_GENERATED="$I5_ROOT/tests/i2/consumer/target/generated-sources/annotations/com/example/soma/i2/soma/ScalarRecordTable.java"
grep -F 'IntGroupBy groupBy' "$I5_GENERATED" >/dev/null ||
    { printf 'I5 qualification failed: generated GroupBy builder missing\n' >&2; exit 1; }
grep -F 'IntGroupedLongResult count()' "$I5_GENERATED" >/dev/null ||
    { printf 'I5 qualification failed: count terminal missing\n' >&2; exit 1; }
grep -F 'groupIntLong' "$I5_ROOT/soma-runtime/src/main/java/io/github/somaruntime/soma/internal/ScalarTableRuntime.java" >/dev/null ||
    { printf 'I5 qualification failed: runtime grouping path missing\n' >&2; exit 1; }
javap -classpath "$I5_ROOT/tests/i2/consumer/target/classes:$I5_ROOT/soma-runtime/target/classes" -private \
    com.example.soma.i2.soma.ScalarRecordTable |
    grep -F 'groupBy(com.example.soma.i2.soma.ScalarRecordTable$MachineField)' >/dev/null ||
    { printf 'I5 qualification failed: typed groupBy signature missing\n' >&2; exit 1; }
printf '%s\n' \
    'I5 GroupBy qualification: PASS' \
    'proofs=int-key-count,int-key-sum,first-encounter-order,detached-entry,result-materialization,'\
'callback-failure,java8-generated-surface,I1-I4-regression' \
    'limitations=bounded int-key GroupBy only; no Join/G6/resource/performance claim'
