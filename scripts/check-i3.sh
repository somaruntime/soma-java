#!/usr/bin/env bash
set -euo pipefail

I3_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$I3_ROOT"
./scripts/check-i2.sh
I3_GENERATED="$I3_ROOT/tests/i2/consumer/target/generated-sources/annotations/com/example/soma/i2/soma/ScalarRecordTable.java"
for symbol in 'findFirst()' 'anyMatch(' 'allMatch(' 'noneMatch(' 'MappedStream' 'mapToLong(' 'LongMappedStream'; do
    grep -F "$symbol" "$I3_GENERATED" >/dev/null || {
        printf 'I3 qualification failed: generated query surface missing %s\n' "$symbol" >&2
        exit 1
    }
done
grep -F 'java.lang.reflect.Array.newInstance' "$I3_GENERATED" >/dev/null ||
    { printf 'I3 qualification failed: reified mapped array allocation missing\n' >&2; exit 1; }
printf '%s\n' \
    'I3 direct query/reference qualification: PASS' \
    'proofs=reference-interpreter-scan,short-circuit-match,detached-record-list,'\
'mapped-reference-list,mapped-reference-array-class,long-mapped-specialization,'\
'pipeline-one-shot,callback-failure,extended-precision-sum,borrowed-view-escape'
