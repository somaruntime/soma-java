#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'access-phase3-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

fixture_source=$root_dir/soma-testkit/src/test/fixtures/external-maven-access-phase3
expected=$fixture_source/expected
local_repository=$root_dir/soma-testkit/target/phase0-m2/repository
mkdir -p target "$local_repository"
evidence_dir=$(mktemp -d "$root_dir/target/phase3-access.XXXXXX")
fixture=$evidence_dir/consumer
repeat_fixture=$evidence_dir/repeat-consumer
mkdir -p "$fixture" "$repeat_fixture"
cp "$fixture_source/pom.xml" "$fixture/pom.xml"
cp -R "$fixture_source/src" "$fixture/src"
cp "$fixture_source/pom.xml" "$repeat_fixture/pom.xml"
cp -R "$fixture_source/src" "$repeat_fixture/src"

./mvnw -B -ntp \
  -Dmaven.repo.local="$local_repository" \
  -pl soma-runtime-core,soma-processor -am \
  install -DskipTests

./mvnw -B -ntp -Dmaven.repo.local="$local_repository" \
  -f "$fixture/pom.xml" clean package
MAVEN_OPTS='-Duser.language=tr -Duser.country=TR -Duser.timezone=Pacific/Kiritimati' \
  ./mvnw -B -ntp -Dmaven.repo.local="$local_repository" \
  -f "$repeat_fixture/pom.xml" clean package

diff -r "$fixture/target/generated-sources/annotations" \
  "$repeat_fixture/target/generated-sources/annotations"
schema=META-INF/soma/com.example.soma.access.schema.json
schema_hash=META-INF/soma/com.example.soma.access.schema.sha256
cmp "$fixture/target/classes/$schema" "$repeat_fixture/target/classes/$schema"
cmp "$fixture/target/classes/$schema_hash" "$repeat_fixture/target/classes/$schema_hash"
cmp "$expected/com.example.soma.access.schema.json" "$fixture/target/classes/$schema"
cmp "$expected/com.example.soma.access.schema.sha256" "$fixture/target/classes/$schema_hash"

"$JAVA_HOME/bin/java" \
  -cp "$fixture/target/classes:soma-runtime-core/target/soma-runtime-core-0.2.0-SNAPSHOT.jar" \
  com.example.soma.access.AccessConsumer

javap_table() {
  class_name=$1
  "$JAVA_HOME/bin/javap" -classpath "$fixture/target/classes" -public \
    "com.example.soma.access.generated.$class_name" \
    >"$evidence_dir/$class_name.javap.txt"
}
javap_table AccessRecordTable
javap_table VisitTable
javap_table UniquePositionTable
cmp "$expected/AccessRecordTable.javap.txt" "$evidence_dir/AccessRecordTable.javap.txt"
cmp "$expected/VisitTable.javap.txt" "$evidence_dir/VisitTable.javap.txt"
cmp "$expected/UniquePositionTable.javap.txt" "$evidence_dir/UniquePositionTable.javap.txt"

grep -F 'scanByState(int);' "$evidence_dir/AccessRecordTable.javap.txt" >/dev/null
grep -F 'scanByGroup(int);' "$evidence_dir/AccessRecordTable.javap.txt" >/dev/null
grep -F 'containsByCode(int);' "$evidence_dir/AccessRecordTable.javap.txt" >/dev/null
grep -F 'findIndexByCode(int);' "$evidence_dir/AccessRecordTable.javap.txt" >/dev/null
grep -F 'fetchByCode(int);' "$evidence_dir/AccessRecordTable.javap.txt" >/dev/null
grep -F 'scanByCode(int);' "$evidence_dir/AccessRecordTable.javap.txt" >/dev/null
grep -F 'scanByRoute(com.example.soma.access.RouteId);' "$evidence_dir/VisitTable.javap.txt" >/dev/null
grep -F 'fetchByPositionKey(com.example.soma.access.RoutePositionKey);' \
  "$evidence_dir/UniquePositionTable.javap.txt" >/dev/null

access_table=$fixture/target/generated-sources/annotations/com/example/soma/access/generated/AccessRecordTable.java
unique_table=$fixture/target/generated-sources/annotations/com/example/soma/access/generated/UniquePositionTable.java
enum_table=$fixture/target/generated-sources/annotations/com/example/soma/access/generated/EnumAccessTable.java
boolean_double_table=$fixture/target/generated-sources/annotations/com/example/soma/access/generated/BooleanDoubleAccessTable.java
scan_source=$fixture/target/generated-sources/annotations/com/example/soma/access/generated/AccessRecordScan.java
grep -F 'GroupedExactIndex selector' "$access_table" >/dev/null
grep -F 'linkExactIndexRows' "$access_table" >/dev/null
grep -F 'relocateExactIndexRows' "$access_table" >/dev/null
grep -F 'IndexBuffer candidateScratch' "$access_table" >/dev/null
grep -F 'updateField1Leaf0' "$unique_table" >/dev/null
grep -F 'private int[] updateField1=' "$enum_table" >/dev/null
grep -F 'private boolean[] updateField1=' "$boolean_double_table" >/dev/null
grep -F 'private double[] updateField2=' "$boolean_double_table" >/dev/null
grep -F 'RowToMutator' "$boolean_double_table" >/dev/null
if grep -E 'markSelector.*Dirty|RowPermutationSidecar|SparseIntKeySpace' \
  "$access_table" "$unique_table" "$boolean_double_table" >/dev/null; then
  printf '%s\n' 'access-phase3-check: removed rebuild/sparse protocol leaked' >&2
  exit 1
fi
grep -F 'private final Source plan;private final int generation;' "$scan_source" >/dev/null
grep -F 'static class Source extends GeneratedScanPlan{private AccessRecordTable table;' \
  "$scan_source" >/dev/null
grep -F 'int size(){AccessRecordTable table=table();group=' "$scan_source" >/dev/null
grep -F 'int rowAt(int position){AccessRecordTable table=table();' "$scan_source" >/dev/null
grep -F 'if(plan.stageCount()==0){int cardinality=plan.size();' "$scan_source" >/dev/null
grep -F 'static final class ExactSource0 extends Source' "$scan_source" >/dev/null
if grep -F 'int fill(int[] target)' "$scan_source" >/dev/null \
    || grep -F 'new Source(){' "$scan_source" >/dev/null; then
  printf '%s\n' 'access-phase3-check: Scan handle/source representation regressed' >&2
  exit 1
fi
if grep -E 'List<Integer>|HashMap|Object\[\]|RoutePositionKey\[\] update' \
  "$access_table" "$unique_table" "$enum_table" "$boolean_double_table" \
  | grep -v 'materializeOwnedMap' >/dev/null \
  || grep -E 'List<Integer>|HashMap|RoutePositionKey\[\] update' \
  "$scan_source" >/dev/null; then
  printf '%s\n' 'access-phase3-check: object/collection hot storage leaked' >&2
  exit 1
fi
if ! grep -F '"kind":"index"' "$fixture/target/classes/$schema" >/dev/null \
    || ! grep -F '"kind":"unique"' "$fixture/target/classes/$schema" >/dev/null \
    || grep -F '"kind":"order"' "$fixture/target/classes/$schema" >/dev/null \
    || grep -F '"direction"' "$fixture/target/classes/$schema" >/dev/null; then
  printf '%s\n' 'access-phase3-check: normalized selector facts missing' >&2
  exit 1
fi

printf '%s\n' "access-phase3-evidence: $evidence_dir"
printf '%s\n' 'access-phase3-check: ok'
