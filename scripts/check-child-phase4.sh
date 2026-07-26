#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'child-phase4-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

source_fixture=$root_dir/soma-testkit/src/test/fixtures/external-maven-child-phase4
local_repository=$root_dir/soma-testkit/target/phase0-m2/repository
mkdir -p target "$local_repository"
evidence_dir=$(mktemp -d "$root_dir/target/phase4-child.XXXXXX")
fixture=$evidence_dir/consumer
repeat_fixture=$evidence_dir/repeat-consumer
mkdir -p "$fixture" "$repeat_fixture"
cp "$source_fixture/pom.xml" "$fixture/pom.xml"
cp -R "$source_fixture/src" "$fixture/src"
cp "$source_fixture/pom.xml" "$repeat_fixture/pom.xml"
cp -R "$source_fixture/src" "$repeat_fixture/src"

./mvnw -B -ntp -Dmaven.repo.local="$local_repository" \
  -pl soma-runtime-core,soma-dataflow,soma-processor -am install -DskipTests
./mvnw -B -ntp -Dmaven.repo.local="$local_repository" \
  -f "$fixture/pom.xml" clean package
MAVEN_OPTS='-Duser.language=tr -Duser.country=TR -Duser.timezone=Pacific/Kiritimati' \
  ./mvnw -B -ntp -Dmaven.repo.local="$local_repository" \
  -f "$repeat_fixture/pom.xml" clean package

diff -r "$fixture/target/generated-sources/annotations" \
  "$repeat_fixture/target/generated-sources/annotations"
schema=META-INF/soma/com.example.soma.child.schema.json
schema_hash=META-INF/soma/com.example.soma.child.schema.sha256
cmp "$fixture/target/classes/$schema" "$repeat_fixture/target/classes/$schema"
cmp "$fixture/target/classes/$schema_hash" "$repeat_fixture/target/classes/$schema_hash"
cmp "$source_fixture/expected-schema.json" "$fixture/target/classes/$schema"
cmp "$source_fixture/expected-schema.sha256" "$fixture/target/classes/$schema_hash"

"$JAVA_HOME/bin/java" \
  -cp "$fixture/target/classes:soma-runtime-core/target/soma-runtime-core-0.2.0-SNAPSHOT.jar" \
  com.example.soma.child.ChildConsumer

parent_source=$fixture/target/generated-sources/annotations/com/example/soma/child/generated/ParentRowTable.java
child_source=$fixture/target/generated-sources/annotations/com/example/soma/child/generated/KeyedChildRowTable.java
generated_dir=$fixture/target/generated-sources/annotations/com/example/soma/child/generated
registry_source=$root_dir/soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/ChildOwnershipRegistry.java
grep -F 'LongColumn ownerTokenColumn' "$parent_source" >/dev/null
grep -F 'LongColumn childrenHandleColumn' "$parent_source" >/dev/null
grep -F 'ChildOwnershipRegistry ownership' "$parent_source" >/dev/null
grep -F 'TablePlan childrenTablePlan' "$parent_source" >/dev/null
grep -F 'effectiveChildTablePlan(plan,"children")' "$parent_source" >/dev/null
grep -F 'createOwned(runtimePlan(),ownership,childrenTablePlan' "$parent_source" >/dev/null
grep -F 'ownership.beginCascade' "$parent_source" >/dev/null
grep -F 'ownership.collectCascade' "$parent_source" >/dev/null
grep -F 'ownership.cancelCascade' "$parent_source" >/dev/null
grep -F 'ownership.commitCascade' "$parent_source" >/dev/null
grep -F 'beginRetireRows(0,previous,"clear",true);releaseRetired(false,"clear");clearColumns(0,previous);clearExactIndexes();state.commitClear(previous)' "$parent_source" >/dev/null
grep -F 'beginRetireSelection(selected,count,operation,true);releaseRetired(false,operation);unlinkExactIndexRows(selected,count);int newSize=previous-count' "$parent_source" >/dev/null
grep -F 'accountOwnedAll' "$child_source" >/dev/null
if grep -E 'ObjectColumn<.*Table|List<.*>Column|Map<.*>Column' \
  "$parent_source" "$child_source" >/dev/null; then
  printf '%s\n' 'child-phase4-check: collection/facade leaked into live child storage' >&2
  exit 1
fi
if grep -E 'public .+\(int rowIndex([,)])' "$generated_dir"/*Table.java >/dev/null; then
  printf '%s\n' 'child-phase4-check: public child facade rowIndex parameter leaked' >&2
  exit 1
fi
if grep -E 'ArrayList|HashMap|Map<' "$registry_source" >/dev/null; then
  printf '%s\n' 'child-phase4-check: Java Collection leaked into ownership registry storage' >&2
  exit 1
fi
if ! grep -F 'createOwned(RuntimePlan plan,ChildOwnershipRegistry ownership,TablePlan tablePlan,String path)' \
    "$generated_dir"/*Table.java >/dev/null 2>&1; then
  printf '%s\n' 'child-phase4-check: owned child did not receive pre-bound TablePlan' >&2
  exit 1
fi
if grep -E 'static .* createOwned\(.*(verifySchemaPlan|toBuilder)' \
    "$generated_dir"/*Table.java >/dev/null; then
  printf '%s\n' 'child-phase4-check: owned child repeated schema verification or TablePlan copy' >&2
  exit 1
fi

grep -F '"role":"child"' "$fixture/target/classes/$schema" >/dev/null
grep -F '"container":"list"' "$fixture/target/classes/$schema" >/dev/null
grep -F '"container":"map"' "$fixture/target/classes/$schema" >/dev/null
grep -F '"child":{"container":"list","rowJavaType":"com.example.soma.child.ChildRow","tableLogicalName":"child_rows"},"javaName":"children"' \
  "$fixture/target/classes/$schema" >/dev/null
grep -F '"child":{"container":"map","keyMaterializedType":"java.lang.Integer","rowJavaType":"com.example.soma.child.KeyedChildRow","tableLogicalName":"keyed_child_rows"},"javaName":"keyedChildren"' \
  "$fixture/target/classes/$schema" >/dev/null
if grep -F 'initialCapacity' "$fixture/target/classes/$schema" >/dev/null; then
  printf '%s\n' 'child-phase4-check: runtime capacity hint leaked into logical schema hash' >&2
  exit 1
fi

"$JAVA_HOME/bin/javap" -classpath "$fixture/target/classes" -public \
  com.example.soma.child.generated.ParentRowTable \
  >"$evidence_dir/ParentRowTable.javap.txt"
"$JAVA_HOME/bin/javap" -classpath "$fixture/target/classes" -public \
  com.example.soma.child.generated.ParentRowBatch \
  >"$evidence_dir/ParentRowBatch.javap.txt"
grep -F 'children(int);' "$evidence_dir/ParentRowTable.javap.txt" >/dev/null
grep -F 'ensureOptionalChildren(int);' "$evidence_dir/ParentRowTable.javap.txt" >/dev/null
grep -F 'replaceChildren(int, com.example.soma.child.generated.ChildRowBatch);' \
  "$evidence_dir/ParentRowTable.javap.txt" >/dev/null
grep -F 'unsetOptionalChildren(int);' "$evidence_dir/ParentRowTable.javap.txt" >/dev/null
if grep -E 'ChildOwnershipRegistry|OwnedChildTable|Handle' \
  "$evidence_dir/ParentRowTable.javap.txt" >/dev/null; then
  printf '%s\n' 'child-phase4-check: runtime ownership protocol leaked into public facade' >&2
  exit 1
fi
grep -F 'addValues(int, com.example.soma.child.generated.ChildRowBatch, com.example.soma.child.generated.ChildRowBatch, com.example.soma.child.generated.KeyedChildRowBatch);' \
  "$evidence_dir/ParentRowBatch.javap.txt" >/dev/null
if grep -F ' copy();' "$evidence_dir/ParentRowBatch.javap.txt" >/dev/null; then
  printf '%s\n' 'child-phase4-check: internal Batch copy leaked into public API' >&2
  exit 1
fi

phase4_javap=$evidence_dir/phase4-generated.javap.txt
types_file=$evidence_dir/phase4-generated-types.txt
for source in "$generated_dir"/*.java; do
  basename "$source" .java
done | LC_ALL=C sort >"$types_file"
while IFS= read -r type; do
  printf '## %s\n' "$type"
  "$JAVA_HOME/bin/javap" -classpath "$fixture/target/classes" -public \
    "com.example.soma.child.generated.$type"
done <"$types_file" >"$phase4_javap"
cmp "$source_fixture/expected-generated-public.javap.txt" "$phase4_javap"

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./mvnw -version
uname -srm

printf '%s\n' "child-phase4-evidence: $evidence_dir"
printf '%s\n' 'child-phase4-check: ok'
