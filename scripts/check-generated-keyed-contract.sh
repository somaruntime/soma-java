#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'generated-keyed-contract: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

fixture_source=$root_dir/tests/fixtures/external-maven-keyed
enum_fixture_source=$root_dir/tests/fixtures/external-maven-enum-keyed
value_fixture_source=$root_dir/tests/fixtures/external-maven-value-keyed
composite_fixture_source=$root_dir/tests/fixtures/external-maven-composite-value-keyed
invalid_source=$root_dir/tests/fixtures/invalid-keyed-int
expected=$fixture_source/expected
local_repository=$root_dir/target/evidence-m2/repository
mkdir -p target "$local_repository"
evidence_dir=$(mktemp -d "$root_dir/target/generated-keyed-contract.XXXXXX")
fixture=$evidence_dir/consumer
repeat_fixture=$evidence_dir/repeat-consumer
enum_fixture=$evidence_dir/enum-consumer
enum_repeat_fixture=$evidence_dir/enum-repeat-consumer
value_fixture=$evidence_dir/value-consumer
value_repeat_fixture=$evidence_dir/value-repeat-consumer
composite_fixture=$evidence_dir/composite-consumer
composite_repeat_fixture=$evidence_dir/composite-repeat-consumer
invalid_fixture=$evidence_dir/invalid-consumer
mkdir -p "$fixture" "$repeat_fixture" "$enum_fixture" "$enum_repeat_fixture" "$value_fixture" "$value_repeat_fixture" "$composite_fixture" "$composite_repeat_fixture" "$invalid_fixture"
cp "$fixture_source/pom.xml" "$fixture/pom.xml"
cp -R "$fixture_source/src" "$fixture/src"
cp "$fixture_source/pom.xml" "$repeat_fixture/pom.xml"
cp -R "$fixture_source/src" "$repeat_fixture/src"
cp "$enum_fixture_source/pom.xml" "$enum_fixture/pom.xml"
cp -R "$enum_fixture_source/src" "$enum_fixture/src"
cp "$enum_fixture_source/pom.xml" "$enum_repeat_fixture/pom.xml"
cp -R "$enum_fixture_source/src" "$enum_repeat_fixture/src"
cp "$value_fixture_source/pom.xml" "$value_fixture/pom.xml"
cp -R "$value_fixture_source/src" "$value_fixture/src"
cp "$value_fixture_source/pom.xml" "$value_repeat_fixture/pom.xml"
cp -R "$value_fixture_source/src" "$value_repeat_fixture/src"
cp "$composite_fixture_source/pom.xml" "$composite_fixture/pom.xml"
cp -R "$composite_fixture_source/src" "$composite_fixture/src"
cp "$composite_fixture_source/pom.xml" "$composite_repeat_fixture/pom.xml"
cp -R "$composite_fixture_source/src" "$composite_repeat_fixture/src"
cp "$invalid_source/pom.xml" "$invalid_fixture/pom.xml"
cp -R "$invalid_source/src" "$invalid_fixture/src"

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

./mvnw -B -ntp \
  -Dmaven.repo.local="$local_repository" \
  -f "$enum_fixture/pom.xml" clean package

MAVEN_OPTS='-Duser.language=tr -Duser.country=TR -Duser.timezone=Pacific/Kiritimati' \
  ./mvnw -B -ntp \
  -Dmaven.repo.local="$local_repository" \
  -f "$enum_repeat_fixture/pom.xml" clean package

./mvnw -B -ntp \
  -Dmaven.repo.local="$local_repository" \
  -f "$value_fixture/pom.xml" clean package

MAVEN_OPTS='-Duser.language=tr -Duser.country=TR -Duser.timezone=Pacific/Kiritimati' \
  ./mvnw -B -ntp \
  -Dmaven.repo.local="$local_repository" \
  -f "$value_repeat_fixture/pom.xml" clean package

./mvnw -B -ntp \
  -Dmaven.repo.local="$local_repository" \
  -f "$composite_fixture/pom.xml" clean package

MAVEN_OPTS='-Duser.language=tr -Duser.country=TR -Duser.timezone=Pacific/Kiritimati' \
  ./mvnw -B -ntp \
  -Dmaven.repo.local="$local_repository" \
  -f "$composite_repeat_fixture/pom.xml" clean package

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
diff -r "$value_fixture/target/generated-sources/annotations" \
  "$value_repeat_fixture/target/generated-sources/annotations"
cmp "$value_fixture/target/classes/META-INF/soma/com.example.soma.valuekeyed.schema.json" \
  "$value_repeat_fixture/target/classes/META-INF/soma/com.example.soma.valuekeyed.schema.json"
diff -r "$composite_fixture/target/generated-sources/annotations" \
  "$composite_repeat_fixture/target/generated-sources/annotations"
cmp "$composite_fixture/target/classes/META-INF/soma/com.example.soma.compositekeyed.schema.json" \
  "$composite_repeat_fixture/target/classes/META-INF/soma/com.example.soma.compositekeyed.schema.json"
cmp "$composite_fixture/target/classes/META-INF/soma/com.example.soma.compositekeyed.schema.sha256" \
  "$composite_repeat_fixture/target/classes/META-INF/soma/com.example.soma.compositekeyed.schema.sha256"

for class_name in \
  KeyedParticleTable KeyedParticleMutator KeyedParticleUpdateCursor KeyedParticleKeyTraversal \
  LongKeyedParticleTable LongKeyedParticleMutator LongKeyedParticleKeyTraversal; do
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
    printf '%s\n' "generated-keyed-contract: primitive direct key signature missing for $class_name" >&2
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
enum_mutable_source=$enum_fixture/target/generated-sources/annotations/com/example/soma/enumkeyed/generated/EnumKeyedJobUpdateCursor.java
"$JAVA_HOME/bin/javap" -classpath "$enum_fixture/target/classes" -public \
  com.example.soma.enumkeyed.generated.EnumKeyedJobTable >"$evidence_dir/EnumKeyedJobTable.javap.txt"
if ! grep -F ' containsKey(com.example.soma.enumkeyed.LifecycleState);' \
  "$evidence_dir/EnumKeyedJobTable.javap.txt" >/dev/null \
  || ! grep -F ' fetch(com.example.soma.enumkeyed.LifecycleState);' \
  "$evidence_dir/EnumKeyedJobTable.javap.txt" >/dev/null \
  || ! grep -F 'io.github.somaruntime.soma.runtime.EnumColumnTraversal<com.example.soma.enumkeyed.LifecycleState> stateValues();' \
  "$evidence_dir/EnumKeyedJobTable.javap.txt" >/dev/null; then
  printf '%s\n' 'generated-keyed-contract: enum direct API or column binding missing' >&2
  exit 1
fi
if ! grep -q 'HashIntKeySpace keySpace' "$enum_table_source" \
  || ! grep -q 'EnumColumnView<com.example.soma.enumkeyed.LifecycleState>' "$enum_table_source" \
  || grep -E 'setState|clearState|setUpdateState|updateState' "$enum_mutator_source" "$enum_mutable_source"; then
  printf '%s\n' 'generated-keyed-contract: enum key static binding or no-mutation contract failed' >&2
  exit 1
fi
semantic_schema=$enum_fixture/target/classes/META-INF/soma/com.example.soma.enumkeyed.schema.json
for semantic in DATE TIME DATE_TIME; do
  if ! grep -F "\"semantic\":\"$semantic\"" "$semantic_schema" >/dev/null; then
    printf '%s\n' "generated-keyed-contract: semantic key normalization missing for $semantic" >&2
    exit 1
  fi
done

value_table_source=$value_fixture/target/generated-sources/annotations/com/example/soma/valuekeyed/generated/ValueKeyedMachineTable.java
"$JAVA_HOME/bin/javap" -classpath "$value_fixture/target/classes" -public \
  com.example.soma.valuekeyed.generated.ValueKeyedMachineTable >"$evidence_dir/ValueKeyedMachineTable.javap.txt"
if ! grep -F ' fetch(com.example.soma.valuekeyed.MachineId);' \
  "$evidence_dir/ValueKeyedMachineTable.javap.txt" >/dev/null \
  || ! grep -q 'HashLongKeySpace keySpace' "$value_table_source" \
  || ! grep -F 'if(batch.size()==1)' "$value_table_source" >/dev/null \
  || ! grep -F 'idColumn.set(t,batch.idStorageValue(s));' "$value_table_source" >/dev/null; then
  printf '%s\n' 'generated-keyed-contract: value key direct/primitive binding missing' >&2
  exit 1
fi

composite_table_source=$composite_fixture/target/generated-sources/annotations/com/example/soma/compositekeyed/generated/OperationStateTable.java
composite_batch_source=$composite_fixture/target/generated-sources/annotations/com/example/soma/compositekeyed/generated/OperationStateBatch.java
"$JAVA_HOME/bin/javap" -classpath "$composite_fixture/target/classes" -public \
  com.example.soma.compositekeyed.generated.OperationStateTable >"$evidence_dir/OperationStateTable.javap.txt"
if ! grep -F ' fetch(com.example.soma.compositekeyed.OperationKey);' \
  "$evidence_dir/OperationStateTable.javap.txt" >/dev/null \
  || ! grep -q 'HashCompositeKeySpace keySpace' "$composite_table_source" \
  || ! grep -F 'if(batch.size()==1)' "$composite_table_source" >/dev/null \
  || ! grep -q 'StringColumn' "$composite_table_source" \
  || ! grep -q 'compositeBatchTableEquals' "$composite_table_source" \
  || ! grep -q 'new com.example.soma.compositekeyed.Coordinate' "$composite_table_source"; then
  printf '%s\n' 'generated-keyed-contract: composite value key static binding missing' >&2
  exit 1
fi
if grep -E 'HashMap|Object\[\].*key|new (Tuple|OperationKey)\(' "$composite_table_source" \
  | grep -v -e 'keyValue(int row)' -e 'materializeOwnedMap' >/dev/null \
  || grep -E 'OperationKey\[|List<.*OperationKey' "$composite_batch_source" >/dev/null; then
  printf '%s\n' 'generated-keyed-contract: composite hot path uses object key storage or transient tuple' >&2
  exit 1
fi

mutator_source=$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/KeyedParticleMutator.java
mutable_source=$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/KeyedParticleUpdateCursor.java
long_mutator_source=$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/LongKeyedParticleMutator.java
long_mutable_source=$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/LongKeyedParticleUpdateCursor.java
table_source=$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/KeyedParticleTable.java
long_table_source=$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/LongKeyedParticleTable.java
scan_source=$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/KeyedParticleScan.java
if grep -E 'setId|clearId|setUpdateId|updateId' \
  "$mutator_source" "$mutable_source" "$long_mutator_source" "$long_mutable_source"; then
  printf '%s\n' 'generated-keyed-contract: key mutation surface leaked' >&2
  exit 1
fi
if grep -E 'setId|clearId|setUpdateId|updateId' \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/BooleanKeyedMutator.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/BooleanKeyedUpdateCursor.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/ByteKeyedMutator.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/ByteKeyedUpdateCursor.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/ShortKeyedMutator.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/ShortKeyedUpdateCursor.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/FloatKeyedMutator.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/FloatKeyedUpdateCursor.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/DoubleKeyedMutator.java" \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/DoubleKeyedUpdateCursor.java"; then
  printf '%s\n' 'generated-keyed-contract: primitive key mutation surface leaked' >&2
  exit 1
fi
if ! grep -q 'IntKeySpace keySpace' "$table_source" \
  || ! grep -F 'if(batch.size()==1)' "$table_source" >/dev/null \
  || ! grep -q 'HashIntKeySpace staged=newAppendValidationKeySpace' "$table_source"; then
  printf '%s\n' 'generated-keyed-contract: planned int keyspace protocol binding missing' >&2
  exit 1
fi
if grep -F 'stageAppendKeys' "$table_source" "$composite_table_source" >/dev/null \
  || ! grep -F 'validateAppendKeys(batch);ensureAppendKeyCapacity(count,"addBatch");ensureExactIndexAppendCapacity(size()+count,batch,"addBatch");int start=state.prepareAppend(count);boolean keysAppended=false;try{copyBatch(batch,0,start,count);appendKeys(batch,start);keysAppended=true;linkExactIndexRows(start,count);state.commitAppend(start,count)' "$table_source" >/dev/null \
  || ! grep -F 'validateAppendKeys(batch);ensureAppendKeyCapacity(count,"addBatch");ensureExactIndexAppendCapacity(size()+count,batch,"addBatch");int start=state.prepareAppend(count);boolean keysAppended=false;try{copyBatch(batch,0,start,count);appendKeys(batch,start);keysAppended=true;linkExactIndexRows(start,count);state.commitAppend(start,count)' "$composite_table_source" >/dev/null \
  || ! grep -F 'if(keysAppended)rollbackAppendKeys(batch,start);clearColumns(start,start+count)' "$table_source" >/dev/null \
  || ! grep -F 'if(keysAppended)rollbackAppendKeys(batch,start);clearColumns(start,start+count)' "$composite_table_source" >/dev/null \
  || ! grep -F 'stageReplacementKeys(batch);staged.inheritMetrics(keySpace.probeCount(),keySpace.collisionCount(),keySpace.rehashCount(),keySpace.storageHighWaterBytes());ExactIndexStage stagedIndexes=stageExactIndexes(batch,"replaceAll");int count=batch.size();try{state.preflightReplaceStorage(count,staged.retainedBytes(),stagedIndexes.retainedBytes(),"replaceAll");int previous=state.prepareReplace(count);copyBatch(batch,0,0,count)' "$composite_table_source" >/dev/null \
  || ! grep -F 'publishKeySpace(staged,"replaceAll");staged=null;stagedIndexes.publish("replaceAll");stagedIndexes=null;state.commitReplace(previous,count)' "$composite_table_source" >/dev/null \
  || ! grep -F 'discardKeySpace(staged,"replaceAll");if(stagedIndexes!=null)stagedIndexes.discard("replaceAll")' "$composite_table_source" >/dev/null; then
  printf '%s\n' 'generated-keyed-contract: incremental append/replacement atomicity shape missing' >&2
  exit 1
fi
if ! grep -q 'HashLongKeySpace keySpace' "$long_table_source"; then
  printf '%s\n' 'generated-keyed-contract: primitive long keyspace binding missing' >&2
  exit 1
fi
if ! grep -q 'KeyCanonicalization.strictFloat' \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/FloatKeyedTable.java"; then
  printf '%s\n' 'generated-keyed-contract: strict float key binding missing' >&2
  exit 1
fi
if ! grep -q 'KeyCanonicalization.strictDouble' \
  "$fixture/target/generated-sources/annotations/com/example/soma/keyed/generated/DoubleKeyedTable.java"; then
  printf '%s\n' 'generated-keyed-contract: strict double key binding missing' >&2
  exit 1
fi
if grep -E 'java\.util\.stream|Integer\[|java\.util\.Iterator|new (ArrayList|LinkedList)' "$scan_source"; then
  printf '%s\n' 'generated-keyed-contract: forbidden candidate scan hot-path source shape' >&2
  exit 1
fi

if ./mvnw -B -ntp -Dmaven.repo.local="$local_repository" \
  -f "$invalid_fixture/pom.xml" clean compile >"$evidence_dir/invalid-keyed.log" 2>&1; then
  printf '%s\n' 'generated-keyed-contract: unsupported keyed breadth unexpectedly compiled' >&2
  exit 1
fi
if ! grep -q 'SOMA-TABLE-008' "$evidence_dir/invalid-keyed.log"; then
  cat "$evidence_dir/invalid-keyed.log" >&2
  printf '%s\n' 'generated-keyed-contract: missing keyed fail-closed diagnostic' >&2
  exit 1
fi

runtime_classpath="$local_repository/io/github/somaruntime/soma/soma-runtime-core/0.2.0-SNAPSHOT/soma-runtime-core-0.2.0-SNAPSHOT.jar:$local_repository/io/github/somaruntime/soma/soma-dataflow/0.2.0-SNAPSHOT/soma-dataflow-0.2.0-SNAPSHOT.jar"
"$JAVA_HOME/bin/java" \
  -cp "$fixture/target/classes:$runtime_classpath" \
  com.example.soma.keyed.KeyedConsumer

"$JAVA_HOME/bin/java" \
  -cp "$enum_fixture/target/classes:$runtime_classpath" \
  com.example.soma.enumkeyed.EnumKeyedConsumer

"$JAVA_HOME/bin/java" \
  -cp "$value_fixture/target/classes:$runtime_classpath" \
  com.example.soma.valuekeyed.ValueKeyedConsumer

"$JAVA_HOME/bin/java" \
  -cp "$composite_fixture/target/classes:$runtime_classpath" \
  com.example.soma.compositekeyed.CompositeValueKeyedConsumer

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./mvnw -version
printf '%s\n' "generated-keyed-contract-evidence: $evidence_dir"
printf '%s\n' 'generated-keyed-contract: ok'
