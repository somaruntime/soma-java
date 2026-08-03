#!/usr/bin/env bash
set -euo pipefail

I2_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
I2_TMP="$(mktemp -d /tmp/soma-i2-qualification.XXXXXX)"
trap 'rm -rf "$I2_TMP"' EXIT

fail_i2() { printf 'I2 qualification failed: %s\n' "$1" >&2; exit 1; }

cd "$I2_ROOT"
java -version 2>&1 | grep 'version "1\.8\.' >/dev/null || fail_i2 "qualification requires Java 8"
mvn -B -q -pl soma-runtime,soma-processor -am install -DskipTests
mvn -B -q -f tests/i2/consumer/pom.xml clean package

I2_CP="$I2_ROOT/tests/i2/consumer/target/classes:$I2_ROOT/soma-runtime/target/classes"
java -cp "$I2_CP" com.example.soma.i2.Application
java -cp "$I2_CP" com.example.soma.i2.RuntimeContractProbe

I2_GENERATED="$I2_ROOT/tests/i2/consumer/target/generated-sources/annotations/com/example/soma/i2/soma"
test -f "$I2_GENERATED/ScalarRecordTable.java" || fail_i2 "generated scalar table missing"
grep -F 'FieldKind.BOOLEAN' "$I2_GENERATED/ScalarRecordTable.java" >/dev/null || fail_i2 "boolean storage shape missing"
grep -F 'FieldKind.DOUBLE' "$I2_GENERATED/ScalarRecordTable.java" >/dev/null || fail_i2 "double storage shape missing"
grep -F 'byMachine' "$I2_GENERATED/ScalarRecordTable.java" >/dev/null || fail_i2 "index accessor missing"
if grep -E 'java\.lang\.reflect\.(Field|Method|Constructor)|(^|[^A-Za-z])(Long|Integer|Double)\.valueOf' "$I2_GENERATED/ScalarRecordTable.java" >/dev/null; then
    fail_i2 "generated scalar source contains field reflection or explicit boxing"
fi
if awk '/long findKey/{inside=1} /private Object valueAt/{inside=0} inside' \
    "$I2_ROOT/soma-runtime/src/main/java/io/github/somaruntime/soma/internal/ScalarTableRuntime.java" |
    grep -F 'valueAt(' >/dev/null; then
    fail_i2 "primitive key lookup still boxes through valueAt"
fi
grep -F 'final DirectoryRoot[] roots' \
    "$I2_ROOT/soma-runtime/src/main/java/io/github/somaruntime/soma/internal/ScalarTableRuntime.java" >/dev/null ||
    fail_i2 "directory is not represented by a two-level root"

javap -classpath "$I2_CP" -private com.example.soma.i2.soma.ScalarRecordTable |
    grep -F 'public void add(com.example.soma.i2.soma.ScalarRecord);' >/dev/null || fail_i2 "add signature missing"
javap -classpath "$I2_CP" -private com.example.soma.i2.soma.ScalarRecordTable |
    grep -F 'public java.util.Optional<com.example.soma.i2.soma.ScalarRecord> find(long);' >/dev/null || fail_i2 "typed find signature missing"

printf '%s\n' \
    'I2 scalar type/storage breadth qualification: PASS' \
    'proofs=java8-generated-surface,all-primitive-fields,enum-reference,nullable-string,'\
'multi-index,null-index,typed-filter,canonical-float-double,point-update,duplicate-key,'\
'detached-fetch,reserve-growth,default-group-identity,no-op-root-publication,string-content-equality,'\
'checked-overflow-structured-failure,no-reflection-static-scan'
