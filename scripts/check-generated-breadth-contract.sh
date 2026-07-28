#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"
. "$root_dir/scripts/lib/external-evidence.sh"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'generated-breadth-contract: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

java_specification=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' "generated-breadth-contract: expected Java 8, got $java_specification" >&2
  exit 1
fi
dependency_plugin_version=$(sed -n \
  's:.*<maven.dependency.plugin.version>\([^<]*\)</maven.dependency.plugin.version>.*:\1:p' \
  pom.xml | sed -n '1p')
dependency_plugin=org.apache.maven.plugins:maven-dependency-plugin:$dependency_plugin_version

fixture_source=$root_dir/tests/fixtures/external-maven-breadth
expected=$fixture_source/expected
mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/generated-breadth-contract.XXXXXX")
fixture=$evidence_dir/consumer
repeat_fixture=$evidence_dir/repeat-consumer
mkdir -p "$fixture" "$repeat_fixture"
cp "$fixture_source/pom.xml" "$fixture/pom.xml"
cp -R "$fixture_source/src" "$fixture/src"
cp "$fixture_source/pom.xml" "$repeat_fixture/pom.xml"
cp -R "$fixture_source/src" "$repeat_fixture/src"

if grep -F '<parent>' "$fixture/pom.xml" >/dev/null; then
  printf '%s\n' 'generated-breadth-contract: fixture must not inherit the root reactor parent' >&2
  exit 1
fi

# Consumer从标准Maven local repository解析已安装artifact，不继承reactor classpath。
soma_require_or_install_external_artifacts
soma_external_mvn -B -ntp \
  -f "$fixture/pom.xml" clean package
MAVEN_OPTS='-Duser.language=tr -Duser.country=TR -Duser.timezone=Pacific/Kiritimati' \
  soma_external_mvn -B -ntp \
  -f "$repeat_fixture/pom.xml" clean package

# 同一source的non-clean recompilation必须与clean输出逐文件等价；不能依赖stale generated artifact。
cp -R "$fixture/target/classes" "$evidence_dir/clean-classes"
cp -R "$fixture/target/generated-sources/annotations" \
  "$evidence_dir/clean-generated-sources"
touch "$fixture/src/main/java/com/example/soma/breadth/FullRow.java"
soma_external_mvn -B -ntp \
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
  printf '%s\n' 'generated-breadth-contract: schema hash is not lowercase SHA-256' >&2
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
  FullRowDataFlow \
  FullRowUpdateCursor \
  FullRowMutator \
  FullRowCursor \
  FullRowScan \
  FullRowTable \
  SchemaMetadata \
  StringKeyRowBatch \
  StringKeyRowDataFlow \
  StringKeyRowDelta \
  StringKeyRowKeyTraversal \
  StringKeyRowUpdateCursor \
  StringKeyRowMutator \
  StringKeyRowCursor \
  StringKeyRowScan \
  StringKeyRowTable \
  StringParentBatch \
  StringParentDataFlow \
  StringParentUpdateCursor \
  StringParentMutator \
  StringParentCursor \
  StringParentScan \
  StringParentTable \
  StringSelectorRowBatch \
  StringSelectorRowDataFlow \
  StringSelectorRowUpdateCursor \
  StringSelectorRowMutator \
  StringSelectorRowCursor \
  StringSelectorRowScan \
  StringSelectorRowTable \
  | LC_ALL=C sort >"$evidence_dir/expected-generated-types.txt"
cmp "$evidence_dir/expected-generated-types.txt" "$types_file"

generated_javap=$evidence_dir/generated-public.javap.txt
generated_javap_raw=$evidence_dir/generated-public.raw.txt
set --
while IFS= read -r type; do
  set -- "$@" "com.example.soma.breadth.generated.$type"
done <"$types_file"
"$JAVA_HOME/bin/javap" -classpath "$fixture/target/classes" -public "$@" \
  >"$generated_javap_raw"
awk '
  /^Compiled from / {
    compiled = $0
    next
  }
  compiled != "" {
    type = ""
    if ($1 == "public") {
      for (i = 1; i <= NF; i++) {
        if ($i ~ /^com\.example\.soma\.breadth\.generated\./) {
          type = $i
          sub(/[<{].*$/, "", type)
          sub(/^.*\./, "", type)
          break
        }
      }
    }
    if (type == "") {
      print "generated-breadth-contract: cannot bind batched javap declaration: " \
        $0 >"/dev/stderr"
      exit 2
    }
    print "## " type
    print compiled
    compiled = ""
  }
  {
    print
  }
' "$generated_javap_raw" >"$generated_javap"
cmp "$expected/generated-public.javap.txt" "$generated_javap"
if grep -E 'io\.github\.somaruntime\.soma\.runtime\.generated|DenseTableState|ChildOwnershipRegistry|OwnedChildTable|Handle' \
  "$generated_javap" >/dev/null; then
  printf '%s\n' 'generated-breadth-contract: internal runtime protocol leaked into generated public API' >&2
  exit 1
fi
grep -F 'public void reserve(int);' "$generated_javap" >/dev/null
grep -F 'fetchAll(io.github.somaruntime.soma.runtime.MaterializationBudget);' \
  "$generated_javap" >/dev/null
grep -F 'findFirst(io.github.somaruntime.soma.runtime.MaterializationBudget);' \
  "$generated_javap" >/dev/null
grep -F 'firstOrThrow(io.github.somaruntime.soma.runtime.MaterializationBudget);' \
  "$generated_javap" >/dev/null

string_key_source=$generated_dir/StringKeyRowTable.java
grep -F 'HashCompositeKeySpace' "$string_key_source" >/dev/null
grep -F 'if(batch.size()==1)' "$string_key_source" >/dev/null
if grep -E 'private .*HashMap|Map<java\.lang\.String,Integer>|List<Integer>' \
  "$string_key_source" >/dev/null; then
  printf '%s\n' 'generated-breadth-contract: Java Collection leaked into String key hot path' >&2
  exit 1
fi
string_selector_source=$generated_dir/StringSelectorRowTable.java
grep -F 'StringColumn labelColumn' "$string_selector_source" >/dev/null
grep -F 'sourceLeaf0.hashCode()' "$string_selector_source" >/dev/null
grep -F 'labelColumn.get(row).compareTo(sourceLeaf0)' \
  "$string_selector_source" >/dev/null
if grep -E 'ObjectColumn|ObjectExpression|objectValue|objectParameter' \
  "$generated_dir"/*.java >/dev/null; then
  printf '%s\n' 'generated-breadth-contract: generic Object value protocol leaked into generated source' >&2
  exit 1
fi

# consumer 以及全部 generated/lowered class 必须都是 Java 8 classfile major 52。
class_major=$evidence_dir/class-major.txt
class_files=$evidence_dir/class-files.txt
find "$fixture/target/classes" -type f -name '*.class' | LC_ALL=C sort >"$class_files"
while IFS= read -r class_file; do
  major_hex=$(od -An -tx1 -j6 -N2 "$class_file" | tr -d ' ')
  relative=${class_file#"$fixture/target/classes/"}
  if [ "$major_hex" != '0034' ]; then
    printf '%s\n' \
      "generated-breadth-contract: expected class major 52: $relative=0x$major_hex" >&2
    exit 1
  fi
  printf '%s %s\n' '52' "$relative"
done <"$class_files" >"$class_major"

# 完整 dependency graph 留在 evidence；runtime graph 必须有 core 且没有 build-only processor。
dependency_tree=$evidence_dir/dependency-tree.txt
runtime_tree=$evidence_dir/runtime-dependency-tree.txt
runtime_classpath_file=$evidence_dir/runtime-classpath.txt
soma_external_mvn -B -ntp \
  -f "$fixture/pom.xml" "$dependency_plugin":tree \
  -DoutputFile="$dependency_tree"
soma_external_mvn -B -ntp \
  -f "$fixture/pom.xml" "$dependency_plugin":tree \
  -Dscope=runtime \
  -DoutputFile="$runtime_tree"
soma_external_mvn -B -ntp \
  -f "$fixture/pom.xml" "$dependency_plugin":build-classpath \
  -DincludeScope=runtime \
  -Dmdep.outputFile="$runtime_classpath_file"
grep -F 'io.github.somaruntime.soma:soma-annotations:' "$dependency_tree" >/dev/null
grep -F 'io.github.somaruntime.soma:soma-runtime-core:' "$runtime_tree" >/dev/null
if grep -F 'io.github.somaruntime.soma:soma-processor:' "$runtime_tree" >/dev/null \
    || grep -F '/soma-processor/' "$runtime_classpath_file" >/dev/null; then
  printf '%s\n' 'generated-breadth-contract: processor leaked into runtime dependency graph' >&2
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
printf '%s\n' "generated-breadth-contract-evidence: $evidence_dir"
printf '%s\n' 'generated-breadth-contract: ok'
