#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'breadth-phase5-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

java_specification=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' "breadth-phase5-check: expected Java 8, got $java_specification" >&2
  exit 1
fi
dependency_plugin_version=$(sed -n \
  's:.*<maven.dependency.plugin.version>\([^<]*\)</maven.dependency.plugin.version>.*:\1:p' \
  pom.xml | sed -n '1p')
dependency_plugin=org.apache.maven.plugins:maven-dependency-plugin:$dependency_plugin_version

fixture_source=$root_dir/soma-testkit/src/test/fixtures/external-maven-breadth-phase5
expected=$fixture_source/expected
mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/phase5-breadth.XXXXXX")
fixture=$evidence_dir/consumer
repeat_fixture=$evidence_dir/repeat-consumer
local_repository=$evidence_dir/repository
mkdir -p "$fixture" "$repeat_fixture" "$local_repository"
seed_repository=$root_dir/soma-testkit/target/phase0-m2/repository
if [ -d "$seed_repository" ]; then
  # 只用共享仓库预热 Maven/plugin cache；本次 project artifact 随后重新 install。
  cp -R "$seed_repository/." "$local_repository/"
fi
cp "$fixture_source/pom.xml" "$fixture/pom.xml"
cp -R "$fixture_source/src" "$fixture/src"
cp "$fixture_source/pom.xml" "$repeat_fixture/pom.xml"
cp -R "$fixture_source/src" "$repeat_fixture/src"

if grep -F '<parent>' "$fixture/pom.xml" >/dev/null; then
  printf '%s\n' 'breadth-phase5-check: fixture must not inherit the root reactor parent' >&2
  exit 1
fi

# 将发布形态 artifact 安装到本次 evidence 独占仓库；consumer 不继承 reactor classpath。
./mvnw -B -ntp -Dmaven.repo.local="$local_repository" \
  -pl soma-runtime-core,soma-dataflow,soma-processor -am install -DskipTests
./mvnw -B -ntp -Dmaven.repo.local="$local_repository" \
  -f "$fixture/pom.xml" clean package
MAVEN_OPTS='-Duser.language=tr -Duser.country=TR -Duser.timezone=Pacific/Kiritimati' \
  ./mvnw -B -ntp -Dmaven.repo.local="$local_repository" \
  -f "$repeat_fixture/pom.xml" clean package

# 同一source的non-clean recompilation必须与clean输出逐文件等价；不能依赖stale generated artifact。
cp -R "$fixture/target/classes" "$evidence_dir/clean-classes"
cp -R "$fixture/target/generated-sources/annotations" \
  "$evidence_dir/clean-generated-sources"
touch "$fixture/src/main/java/com/example/soma/breadth/FullRow.java"
./mvnw -B -ntp -Dmaven.repo.local="$local_repository" \
  -f "$fixture/pom.xml" package
diff -r "$evidence_dir/clean-classes" "$fixture/target/classes"
diff -r "$evidence_dir/clean-generated-sources" \
  "$fixture/target/generated-sources/annotations"

# locale/timezone 不得改变 generated source、canonical schema 或 schema hash。
diff -r "$fixture/target/generated-sources/annotations" \
  "$repeat_fixture/target/generated-sources/annotations"
schema=META-INF/soma/com.example.soma.breadth.schema.json
schema_hash=META-INF/soma/com.example.soma.breadth.schema.sha256
cmp "$fixture/target/classes/$schema" "$repeat_fixture/target/classes/$schema"
cmp "$fixture/target/classes/$schema_hash" \
  "$repeat_fixture/target/classes/$schema_hash"
cmp "$expected/com.example.soma.breadth.schema.json" \
  "$fixture/target/classes/$schema"
cmp "$expected/com.example.soma.breadth.schema.sha256" \
  "$fixture/target/classes/$schema_hash"
if ! grep -E '^[0-9a-f]{64}$' "$fixture/target/classes/$schema_hash" >/dev/null; then
  printf '%s\n' 'breadth-phase5-check: schema hash is not lowercase SHA-256' >&2
  exit 1
fi

# normalized golden 必须实际含有 defaults、reference/enum/value 和 String keyed child 事实。
grep -F '"default":{"literal":"-0.0","normalized":"-0.0"}' \
  "$fixture/target/classes/$schema" >/dev/null
grep -F '"default":{"literal":"2026-01-02T03:04:05Z","normalized":"1767323045000"}' \
  "$fixture/target/classes/$schema" >/dev/null
grep -F '"materializedType":"java.lang.String","optional":true' \
  "$fixture/target/classes/$schema" >/dev/null
grep -F '"materializedType":"com.example.soma.breadth.State","optional":true' \
  "$fixture/target/classes/$schema" >/dev/null
grep -F '"materializedType":"com.example.soma.breadth.Point","optional":true' \
  "$fixture/target/classes/$schema" >/dev/null
grep -F '"materializedType":"com.example.soma.breadth.ScalarValue","optional":true' \
  "$fixture/target/classes/$schema" >/dev/null
grep -F '"default":{"literal":"3","normalized":"3"}' \
  "$fixture/target/classes/$schema" >/dev/null
grep -F '"container":"map","keyMaterializedType":"java.lang.String"' \
  "$fixture/target/classes/$schema" >/dev/null

# 从所有 generated top-level source 重建完整类型 manifest，并对每个类型做 public javap。
generated_dir=$fixture/target/generated-sources/annotations/com/example/soma/breadth/generated
types_file=$evidence_dir/generated-types.txt
for source in "$generated_dir"/*.java; do
  basename "$source" .java
done | LC_ALL=C sort >"$types_file"
printf '%s\n' \
  FullRowBatch \
  FullRowUpdateCursor \
  FullRowMutator \
  FullRowCursor \
  FullRowScan \
  FullRowTable \
  StringKeyRowBatch \
  StringKeyRowKeyTraversal \
  StringKeyRowUpdateCursor \
  StringKeyRowMutator \
  StringKeyRowCursor \
  StringKeyRowScan \
  StringKeyRowTable \
  StringParentBatch \
  StringParentUpdateCursor \
  StringParentMutator \
  StringParentCursor \
  StringParentScan \
  StringParentTable \
  | LC_ALL=C sort >"$evidence_dir/expected-generated-types.txt"
cmp "$evidence_dir/expected-generated-types.txt" "$types_file"

generated_javap=$evidence_dir/generated-public.javap.txt
while IFS= read -r type; do
  printf '## %s\n' "$type"
  "$JAVA_HOME/bin/javap" -classpath "$fixture/target/classes" -public \
    "com.example.soma.breadth.generated.$type"
done <"$types_file" >"$generated_javap"
cmp "$expected/generated-public.javap.txt" "$generated_javap"
if grep -E 'com\.hgtech\.soma\.runtime\.generated|DenseTableState|ChildOwnershipRegistry|OwnedChildTable|Handle' \
  "$generated_javap" >/dev/null; then
  printf '%s\n' 'breadth-phase5-check: internal runtime protocol leaked into generated public API' >&2
  exit 1
fi
grep -F 'public void reserve(int);' "$generated_javap" >/dev/null
grep -F 'fetchAll(com.hgtech.soma.runtime.MaterializationBudget);' \
  "$generated_javap" >/dev/null
grep -F 'findFirst(com.hgtech.soma.runtime.MaterializationBudget);' \
  "$generated_javap" >/dev/null
grep -F 'firstOrThrow(com.hgtech.soma.runtime.MaterializationBudget);' \
  "$generated_javap" >/dev/null

string_key_source=$generated_dir/StringKeyRowTable.java
grep -F 'HashCompositeKeySpace' "$string_key_source" >/dev/null
grep -F 'if(batch.size()==1)' "$string_key_source" >/dev/null
if grep -E 'private .*HashMap|Map<java\.lang\.String,Integer>|List<Integer>' \
  "$string_key_source" >/dev/null; then
  printf '%s\n' 'breadth-phase5-check: Java Collection leaked into String key hot path' >&2
  exit 1
fi

# consumer 以及全部 generated/lowered class 必须都是 Java 8 classfile major 52。
class_major=$evidence_dir/class-major.txt
class_files=$evidence_dir/class-files.txt
find "$fixture/target/classes" -type f -name '*.class' | LC_ALL=C sort >"$class_files"
while IFS= read -r class_file; do
  major=$($JAVA_HOME/bin/javap -verbose "$class_file" |
    sed -n 's/^[[:space:]]*major version: //p' | head -n 1)
  relative=${class_file#"$fixture/target/classes/"}
  printf '%s %s\n' "$major" "$relative"
  if [ "$major" != '52' ]; then
    printf '%s\n' "breadth-phase5-check: expected class major 52: $relative=$major" >&2
    exit 1
  fi
done <"$class_files" >"$class_major"

# 完整 dependency graph 留在 evidence；runtime graph 必须有 core 且没有 build-only processor。
dependency_tree=$evidence_dir/dependency-tree.txt
runtime_tree=$evidence_dir/runtime-dependency-tree.txt
runtime_classpath_file=$evidence_dir/runtime-classpath.txt
./mvnw -B -ntp -Dmaven.repo.local="$local_repository" \
  -f "$fixture/pom.xml" "$dependency_plugin":tree \
  -DoutputFile="$dependency_tree"
./mvnw -B -ntp -Dmaven.repo.local="$local_repository" \
  -f "$fixture/pom.xml" "$dependency_plugin":tree \
  -Dscope=runtime \
  -DoutputFile="$runtime_tree"
./mvnw -B -ntp -Dmaven.repo.local="$local_repository" \
  -f "$fixture/pom.xml" "$dependency_plugin":build-classpath \
  -DincludeScope=runtime \
  -Dmdep.outputFile="$runtime_classpath_file"
grep -F 'com.hgtech.soma:soma-annotations:' "$dependency_tree" >/dev/null
grep -F 'com.hgtech.soma:soma-runtime-core:' "$runtime_tree" >/dev/null
if grep -F 'com.hgtech.soma:soma-processor:' "$runtime_tree" >/dev/null \
    || grep -F '/soma-processor/' "$runtime_classpath_file" >/dev/null; then
  printf '%s\n' 'breadth-phase5-check: processor leaked into runtime dependency graph' >&2
  exit 1
fi
runtime_classpath=$(sed -n '1p' "$runtime_classpath_file")

"$JAVA_HOME/bin/java" \
  -cp "$fixture/target/classes:$runtime_classpath" \
  com.example.soma.breadth.BreadthConsumer

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./mvnw -version
uname -srm
printf '%s\n' "breadth-phase5-evidence: $evidence_dir"
printf '%s\n' 'breadth-phase5-check: ok'
