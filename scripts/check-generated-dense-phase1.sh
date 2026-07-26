#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'generated-dense-phase1-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

fixture_source=$root_dir/soma-testkit/src/test/fixtures/external-maven-dense
expected=$fixture_source/expected
local_repository=$root_dir/soma-testkit/target/phase0-m2/repository
mkdir -p target "$local_repository"
evidence_dir=$(mktemp -d "$root_dir/target/phase1-generated-dense.XXXXXX")
fixture=$evidence_dir/consumer
repeat_fixture=$evidence_dir/repeat-consumer
mkdir -p "$fixture" "$repeat_fixture"
cp "$fixture_source/pom.xml" "$fixture/pom.xml"
cp -R "$fixture_source/src" "$fixture/src"
cp "$fixture_source/pom.xml" "$repeat_fixture/pom.xml"
cp -R "$fixture_source/src" "$repeat_fixture/src"

./mvnw -B -ntp \
  -Dmaven.repo.local="$local_repository" \
  -pl soma-runtime-core,soma-dataflow,soma-processor -am \
  install -DskipTests

./mvnw -B -ntp \
  -Dmaven.repo.local="$local_repository" \
  -f "$fixture/pom.xml" clean package

MAVEN_OPTS='-Duser.language=tr -Duser.country=TR -Duser.timezone=Pacific/Kiritimati' \
  ./mvnw -B -ntp \
  -Dmaven.repo.local="$local_repository" \
  -f "$repeat_fixture/pom.xml" clean package

diff -r "$fixture/target/generated-sources/annotations" \
  "$repeat_fixture/target/generated-sources/annotations"

schema=META-INF/soma/com.example.soma.dense.schema.json
schema_hash=META-INF/soma/com.example.soma.dense.schema.sha256
test -s "$fixture/target/classes/$schema"
test -s "$fixture/target/classes/$schema_hash"
test -s "$fixture/target/generated-sources/annotations/com/example/soma/dense/generated/ParticleTable.java"
cmp "$expected/com.example.soma.dense.schema.json" "$fixture/target/classes/$schema"
cmp "$expected/com.example.soma.dense.schema.sha256" "$fixture/target/classes/$schema_hash"
cmp "$fixture/target/classes/$schema" "$repeat_fixture/target/classes/$schema"
cmp "$fixture/target/classes/$schema_hash" "$repeat_fixture/target/classes/$schema_hash"

"$JAVA_HOME/bin/javap" -classpath "$fixture/target/classes" -public \
  com.example.soma.dense.generated.ParticleTable > "$evidence_dir/ParticleTable.javap.txt"
"$JAVA_HOME/bin/javap" -classpath "$fixture/target/classes" -public \
  com.example.soma.dense.generated.ParticleBatch > "$evidence_dir/ParticleBatch.javap.txt"
"$JAVA_HOME/bin/javap" -classpath "$fixture/target/classes" -c \
  com.example.soma.dense.generated.ParticleScan > "$evidence_dir/ParticleScan.bytecode.txt"
cmp "$expected/ParticleTable.javap.txt" "$evidence_dir/ParticleTable.javap.txt"
cmp "$expected/ParticleBatch.javap.txt" "$evidence_dir/ParticleBatch.javap.txt"
scan_source=$fixture/target/generated-sources/annotations/com/example/soma/dense/generated/ParticleScan.java
table_source=$fixture/target/generated-sources/annotations/com/example/soma/dense/generated/ParticleTable.java
scan_plan_source=$root_dir/soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/GeneratedScanPlan.java
column_view_source=$root_dir/soma-runtime-core/src/main/java/com/hgtech/soma/runtime/AbstractColumnView.java
grep -F 'private static final class Inline' "$scan_plan_source" >/dev/null
grep -F 'private static final class Overflow' "$scan_plan_source" >/dev/null
grep -F 'plan.isCurrent(generation)' "$scan_source" >/dev/null
grep -F 'private final Source plan;private final int generation;' "$scan_source" >/dev/null
grep -F 'static class Source extends GeneratedScanPlan{private ParticleTable table;' \
  "$scan_source" >/dev/null
grep -F 'private static boolean packedAnyMatch' "$table_source" >/dev/null
if grep -E 'class (Inline|Overflow|Evaluation|Plan)' "$scan_source" >/dev/null \
    || grep -F 'packedAnyMatch' "$scan_source" >/dev/null \
    || grep -F 'new ParticleScan(this' "$table_source" >/dev/null; then
  printf '%s\n' 'generated-dense-phase1-check: shared plan or direct packed executor regressed' >&2
  exit 1
fi
if grep -E 'new Selection|boolean\[\] seen|operation\+"\.predicate"|Arrays\.copyOf\(kinds,n\+1\)' \
  "$scan_source" >/dev/null; then
  printf '%s\n' 'generated-dense-phase1-check: candidate scan temporary allocation shape regressed' >&2
  exit 1
fi
if grep -E 'java\.util\.stream|Integer\[|new (ArrayList|LinkedList)|for\([^)]*\).*new (Cursor|UpdateCursor)' "$scan_source"; then
  printf '%s\n' 'generated-dense-phase1-check: forbidden candidate scan hot-path source shape' >&2
  exit 1
fi
if grep -E 'java/util/stream|java/lang/(Boolean|Byte|Short|Integer|Long|Float|Double)\.valueOf|java/util/Iterator' "$evidence_dir/ParticleScan.bytecode.txt"; then
  printf '%s\n' 'generated-dense-phase1-check: forbidden candidate scan hot-path bytecode shape' >&2
  exit 1
fi
if grep -F 'field + ".column"' "$column_view_source" >/dev/null \
    || grep -F 'ColumnViewOperations' "$column_view_source" >/dev/null; then
  printf '%s\n' 'generated-dense-phase1-check: ColumnView hot construction rebuilt diagnostic strings' >&2
  exit 1
fi
"$JAVA_HOME/bin/java" \
  -cp "$fixture/target/classes:$local_repository/com/hgtech/soma/soma-runtime-core/0.2.0-SNAPSHOT/soma-runtime-core-0.2.0-SNAPSHOT.jar:$local_repository/com/hgtech/soma/soma-dataflow/0.2.0-SNAPSHOT/soma-dataflow-0.2.0-SNAPSHOT.jar" \
  com.example.soma.dense.DenseConsumer

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./mvnw -version
printf '%s\n' "generated-dense-phase1-evidence: $evidence_dir"
printf '%s\n' 'generated-dense-phase1-check: ok'
