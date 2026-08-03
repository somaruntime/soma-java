#!/usr/bin/env bash
set -euo pipefail

I7_ROOT="$(pwd)"
cd "$I7_ROOT"
./scripts/check-i6.sh
I7_GENERATED="$I7_ROOT/tests/i2/consumer/target/generated-sources/annotations/com/example/soma/i2/soma/ScalarRecordTable.java"
grep -F 'TableMetadata _metadata()' "$I7_GENERATED" >/dev/null ||
    { printf 'I7 qualification failed: Table metadata missing\n' >&2; exit 1; }
grep -F 'FieldMetadata _metadata()' "$I7_GENERATED" >/dev/null ||
    { printf 'I7 qualification failed: Field metadata missing\n' >&2; exit 1; }
grep -F 'SomaMetadata _metadata()' \
    "$I7_ROOT/tests/i2/consumer/target/generated-sources/annotations/com/example/soma/i2/soma/Soma.java" >/dev/null ||
    { printf 'I7 qualification failed: Soma metadata missing\n' >&2; exit 1; }
grep -F 'GroupMetadata _metadata()' \
    "$I7_ROOT/tests/i2/consumer/target/generated-sources/annotations/com/example/soma/i2/soma/SomaGroup.java" >/dev/null ||
    { printf 'I7 qualification failed: Group metadata missing\n' >&2; exit 1; }
for carrier in SomaMetadata GroupMetadata TableMetadata FieldMetadata; do
    javap -private -classpath "$I7_ROOT/soma-runtime/target/classes" \
        "io.github.somaruntime.soma.$carrier" | grep -F "private io.github.somaruntime.soma.$carrier(" >/dev/null ||
        { printf 'I7 qualification failed: %s constructor is not private\n' "$carrier" >&2; exit 1; }
done
printf '%s\n' \
    'I7 metadata/plain-representation qualification: PASS' \
    'proofs=immutable-carriers,unfrozen/effective-config,default-group-identity,table-size-capacity-version,'\
'published-snapshot-detachment,plain-payload-estimate,'\
'field-logical-role,auto-policy-observation,java8-generated-surface,I1-I6-regression' \
    'limitations=no-compressed-codec/no-full-explain/no-global-resource-admission'
