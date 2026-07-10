#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'generated-keyed-phase2-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

fixture_source=$root_dir/soma-testkit/src/test/fixtures/external-maven-keyed
invalid_source=$root_dir/soma-testkit/src/test/fixtures/invalid-keyed-int-slice
expected=$fixture_source/expected
local_repository=$root_dir/soma-testkit/target/phase0-m2/repository
mkdir -p target "$local_repository"
evidence_dir=$(mktemp -d "$root_dir/target/phase2-generated-keyed.XXXXXX")
fixture=$evidence_dir/consumer
repeat_fixture=$evidence_dir/repeat-consumer
invalid_fixture=$evidence_dir/invalid-consumer
mkdir -p "$fixture" "$repeat_fixture" "$invalid_fixture"
cp "$fixture_source/pom.xml" "$fixture/pom.xml"
cp -R "$fixture_source/src" "$fixture/src"
cp "$fixture_source/pom.xml" "$repeat_fixture/pom.xml"
cp -R "$fixture_source/src" "$repeat_fixture/src"
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

for class_name in \
  KeyedParticleTable KeyedParticleMutator KeyedParticleMutableRow KeyedParticleKeys \
  LongKeyedParticleTable LongKeyedParticleMutator LongKeyedParticleKeys; do
  "$JAVA_HOME/bin/javap" -classpath "$fixture/target/classes" -public \
    "com.example.soma.keyed.generated.$class_name" > "$evidence_dir/$class_name.javap.txt"
  cmp "$expected/$class_name.javap.txt" "$evidence_dir/$class_name.javap.txt"
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
if ! grep -q 'HashIntKeySpace keySpace' "$table_source"; then
  printf '%s\n' 'generated-keyed-phase2-check: primitive keyspace binding missing' >&2
  exit 1
fi
if ! grep -q 'HashLongKeySpace keySpace' "$long_table_source"; then
  printf '%s\n' 'generated-keyed-phase2-check: primitive long keyspace binding missing' >&2
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

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./mvnw -version
printf '%s\n' "generated-keyed-phase2-evidence: $evidence_dir"
printf '%s\n' 'generated-keyed-phase2-check: ok'
