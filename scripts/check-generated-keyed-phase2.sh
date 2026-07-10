#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'generated-keyed-phase2-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

fixture_source=$root_dir/soma-testkit/src/test/fixtures/external-maven-keyed
enum_fixture_source=$root_dir/soma-testkit/src/test/fixtures/external-maven-enum-keyed
invalid_source=$root_dir/soma-testkit/src/test/fixtures/invalid-keyed-int-slice
expected=$fixture_source/expected
local_repository=$root_dir/soma-testkit/target/phase0-m2/repository
mkdir -p target "$local_repository"
evidence_dir=$(mktemp -d "$root_dir/target/phase2-generated-keyed.XXXXXX")
fixture=$evidence_dir/consumer
repeat_fixture=$evidence_dir/repeat-consumer
enum_fixture=$evidence_dir/enum-consumer
enum_repeat_fixture=$evidence_dir/enum-repeat-consumer
invalid_fixture=$evidence_dir/invalid-consumer
mkdir -p "$fixture" "$repeat_fixture" "$enum_fixture" "$enum_repeat_fixture" "$invalid_fixture"
cp "$fixture_source/pom.xml" "$fixture/pom.xml"
cp -R "$fixture_source/src" "$fixture/src"
cp "$fixture_source/pom.xml" "$repeat_fixture/pom.xml"
cp -R "$fixture_source/src" "$repeat_fixture/src"
cp "$enum_fixture_source/pom.xml" "$enum_fixture/pom.xml"
cp -R "$enum_fixture_source/src" "$enum_fixture/src"
cp "$enum_fixture_source/pom.xml" "$enum_repeat_fixture/pom.xml"
cp -R "$enum_fixture_source/src" "$enum_repeat_fixture/src"
cp "$invalid_source/pom.xml" "$invalid_fixture/pom.xml"
cp -R "$invalid_source/src" "$invalid_fixture/src"

./mvnw -B -ntp \
  -Dmaven.repo.local="$local_repository" \
  -pl soma-runtime-core,soma-processor -am \
  install -DskipTests

./mvnw -B -ntp \
  -Dmaven.repo.local="$local_repository" \
  -f "$fixture/pom.xml" clean package

MAVEN_OPTS='-Duser.language=tr -Duser.country=TR -Duser.timezone=Pacific/Kiritimati' \
  ./mvnw -B -ntp \
  -Dmaven.repo.local="$local_repository" \
  -f "$repeat_fixture/pom.xml" clean package

./mvnw -B -ntp \
  -Dmaven.repo.local="$local_repository" \
  -f "$enum_fixture/pom.xml" clean package

MAVEN_OPTS='-Duser.language=tr -Duser.country=TR -Duser.timezone=Pacific/Kiritimati' \
  ./mvnw -B -ntp \
  -Dmaven.repo.local="$local_repository" \
  -f "$enum_repeat_fixture/pom.xml" clean package

diff -r "$fixture/target/generated-sources/annotations" \
  "$repeat_fixture/target/generated-sources/annotations"

schema=META-INF/soma/com.example.soma.keyed.schema.json
schema_hash=META-INF/soma/com.example.soma.keyed.schema.sha256
test -s "$fixture/target/classes/$schema"
test -s "$fixture/target/classes/$schema_hash"
cmp "$expected/com.example.soma.keyed.schema.json" "$fixture/target/classes/$schema"
cmp "$expected/com.example.soma.keyed.schema.sha256" "$fixture/target/classes/$schema_hash"
cmp "$fixture/target/classes/$schema" "$repeat_fixture/target/classes/$schema"
cmp "$fixture/target/classes/$schema_hash" "$repeat_fixture/target/classes/$schema_hash"
cmp "$enum_fixture/target/classes/META-INF/soma/com.example.soma.enumkeyed.schema.json" \
  "$enum_repeat_fixture/target/classes/META-INF/soma/com.example.soma.enumkeyed.schema.json"
cmp "$enum_fixture/target/classes/META-INF/soma/com.example.soma.enumkeyed.schema.sha256" \
  "$enum_repeat_fixture/target/classes/META-INF/soma/com.example.soma.enumkeyed.schema.sha256"
diff -r "$enum_fixture/target/generated-sources/annotations" \
  "$enum_repeat_fixture/target/generated-sources/annotations"

for class_name in \
  KeyedParticleTable KeyedParticleMutator KeyedParticleMutableRow KeyedParticleKeys \
  LongKeyedParticleTable LongKeyedParticleMutator LongKeyedParticleKeys; do
  "$JAVA_HOME/bin/javap" -classpath "$fixture/target/classes" -public \
    "com.example.soma.keyed.generated.$class_name" > "$evidence_dir/$class_name.javap.txt"
  cmp "$expected/$class_name.javap.txt" "$evidence_dir/$class_name.javap.txt"
done

check_primitive_table() {
  class_name=$1
  key_type=$2
  output=$evidence_dir/$class_name.javap.txt
  "$JAVA_HOME/bin/javap" -classpath "$fixture/target/classes" -public \
    "com.example.soma.keyed.generated.$class_name" > "$output"
  if ! grep -F " fetch($key_type);" "$output" >/dev/null \
      || ! grep -F " containsKey($key_type);" "$output" >/dev/null \
      || ! grep -F " delete($key_type);" "$output" >/dev/null; then
    printf '%s\n' "generated-keyed-phase2-check: primitive direct key signature missing for $class_name" >&2
    exit 1
  fi
}

check_primitive_table BooleanKeyedTable boolean
check_primitive_table ByteKeyedTable byte
check_primitive_table ShortKeyedTable short
check_primitive_table FloatKeyedTable float
check_primitive_table DoubleKeyedTable double

enum_table_source=$enum_fixture/target/generated-sources/annotations/com/example/soma/enumkeyed/generated/EnumKeyedJobTable.java
enum_mutator_source=$enum_fixture/target/generated-sources/annotations/com/example/soma/enumkeyed/generated/EnumKeyedJobMutator.java
enum_mutable_source=$enum_fixture/target/generated-sources/annotations/com/example/soma/enumkeyed/generated/EnumKeyedJobMutableRow.java
"$JAVA_HOME/bin/javap" -classpath "$enum_fixture/target/classes" -public \
  com.example.soma.enumkeyed.generated.EnumKeyedJobTable >"$evidence_dir/EnumKeyedJobTable.javap.txt"
if ! grep -F ' containsKey(com.example.soma.enumkeyed.LifecycleState);' \
  "$evidence_dir/EnumKeyedJobTable.javap.txt" >/dev/null \
  || ! grep -F ' fetch(com.example.soma.enumkeyed.LifecycleState);' \
  "$evidence_dir/EnumKeyedJobTable.javap.txt" >/dev/null \
  || ! grep -F 'com.hgtech.soma.runtime.EnumColumnPipeline<com.example.soma.enumkeyed.LifecycleState> stateValues();' \
  "$evidence_dir/EnumKeyedJobTable.javap.txt" >/dev/null; then
  printf '%s\n' 'generated-keyed-phase2-check: enum direct API or column binding missing' >&2
  exit 1
fi
if ! grep -q 'HashIntKeySpace keySpace' "$enum_table_source" \
  || ! grep -q 'EnumColumnView<com.example.soma.enumkeyed.LifecycleState>' "$enum_table_source" \
  || grep -E 'setState|clearState|setUpdateState|updateState' "$enum_mutator_source" "$enum_mutable_source"; then
  printf '%s\n' 'generated-keyed-phase2-check: enum key static binding or no-mutation contract failed' >&2
  exit 1
fi
semantic_schema=$enum_fixture/target/classes/META-INF/soma/com.example.soma.enumkeyed.schema.json
for semantic in DATE TIME DATE_TIME; do
  if ! grep -F "\"semantic\":\"$semantic\"" "$semantic_schema" >/dev/null; then
    printf '%s\n' "generated-keyed-phase2-check: semantic key normalization missing for $semantic" >&2
    exit 1
  fi
done

mutator_source=$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/KeyedParticleMutator.java
mutable_source=$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/KeyedParticleMutableRow.java
long_mutator_source=$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/LongKeyedParticleMutator.java
long_mutable_source=$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/LongKeyedParticleMutableRow.java
table_source=$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/KeyedParticleTable.java
long_table_source=$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/LongKeyedParticleTable.java
rows_source=$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/KeyedParticleRows.java
if grep -E 'setId|clearId|setUpdateId|updateId' \
  "$mutator_source" "$mutable_source" "$long_mutator_source" "$long_mutable_source"; then
  printf '%s\n' 'generated-keyed-phase2-check: key mutation surface leaked' >&2
  exit 1
fi
if grep -E 'setId|clearId|setUpdateId|updateId' \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/BooleanKeyedMutator.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/BooleanKeyedMutableRow.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/ByteKeyedMutator.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/ByteKeyedMutableRow.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/ShortKeyedMutator.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/ShortKeyedMutableRow.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/FloatKeyedMutator.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/FloatKeyedMutableRow.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/DoubleKeyedMutator.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/DoubleKeyedMutableRow.java"; then
  printf '%s\n' 'generated-keyed-phase2-check: primitive key mutation surface leaked' >&2
  exit 1
fi
if ! grep -q 'HashIntKeySpace keySpace' "$table_source"; then
  printf '%s\n' 'generated-keyed-phase2-check: primitive keyspace binding missing' >&2
  exit 1
fi
if ! grep -q 'HashLongKeySpace keySpace' "$long_table_source"; then
  printf '%s\n' 'generated-keyed-phase2-check: primitive long keyspace binding missing' >&2
  exit 1
fi
if ! grep -q 'KeyCanonicalization.strictFloat' \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/FloatKeyedTable.java"; then
  printf '%s\n' 'generated-keyed-phase2-check: strict float key binding missing' >&2
  exit 1
fi
if ! grep -q 'KeyCanonicalization.strictDouble' \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/DoubleKeyedTable.java"; then
  printf '%s\n' 'generated-keyed-phase2-check: strict double key binding missing' >&2
  exit 1
fi
if grep -E 'java\.util\.stream|Object\[|Integer\[|java\.util\.Iterator|new (ArrayList|LinkedList)' "$rows_source"; then
  printf '%s\n' 'generated-keyed-phase2-check: forbidden row hot-path source shape' >&2
  exit 1
fi

if ./mvnw -B -ntp -Dmaven.repo.local="$local_repository" \
  -f "$invalid_fixture/pom.xml" clean compile >"$evidence_dir/invalid-keyed.log" 2>&1; then
  printf '%s\n' 'generated-keyed-phase2-check: unsupported keyed breadth unexpectedly compiled' >&2
  exit 1
fi
if ! grep -q 'SOMA-TABLE-008' "$evidence_dir/invalid-keyed.log"; then
  cat "$evidence_dir/invalid-keyed.log" >&2
  printf '%s\n' 'generated-keyed-phase2-check: missing keyed fail-closed diagnostic' >&2
  exit 1
fi

"$JAVA_HOME/bin/java" \
  -cp "$fixture/target/classes:$local_repository/com/hgtech/soma/soma-runtime-core/0.1.0-SNAPSHOT/soma-runtime-core-0.1.0-SNAPSHOT.jar" \
  com.example.soma.keyed.KeyedConsumer

"$JAVA_HOME/bin/java" \
  -cp "$enum_fixture/target/classes:$local_repository/com/hgtech/soma/soma-runtime-core/0.1.0-SNAPSHOT/soma-runtime-core-0.1.0-SNAPSHOT.jar" \
  com.example.soma.enumkeyed.EnumKeyedConsumer

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./mvnw -version
printf '%s\n' "generated-keyed-phase2-evidence: $evidence_dir"
printf '%s\n' 'generated-keyed-phase2-check: ok'
