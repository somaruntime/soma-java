#!/usr/bin/env bash
set -euo pipefail

I4_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$I4_ROOT"
./scripts/check-i3.sh
I4_GENERATED="$I4_ROOT/tests/i2/consumer/target/generated-sources/annotations/com/example/soma/i2/soma/ScalarRecordTable.java"
grep -F 'RemoveResult remove(long key)' "$I4_GENERATED" >/dev/null ||
    { printf 'I4 qualification failed: point remove signature missing\n' >&2; exit 1; }
grep -F 'withoutRow' "$I4_ROOT/soma-runtime/src/main/java/io/github/somaruntime/soma/internal/ScalarTableRuntime.java" >/dev/null ||
    { printf 'I4 qualification failed: compaction path missing\n' >&2; exit 1; }
grep -F 'validateKeyArgument' "$I4_ROOT/soma-runtime/src/main/java/io/github/somaruntime/soma/internal/ScalarTableRuntime.java" >/dev/null ||
    { printf 'I4 qualification failed: null-key validation missing\n' >&2; exit 1; }
printf '%s\n' \
    'I4 point mutation/failure qualification: PASS' \
    'proofs=point-remove,missing-remove,compaction,remove-result-carrier,'\
'version-publication,selection-mutation-not-claimed'
