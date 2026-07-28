#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javap" ]; then
  printf '%s\n' 'public-api-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

annotations_jar=soma-annotations/target/soma-annotations-0.2.0-SNAPSHOT.jar
processor_jar=soma-processor/target/soma-processor-0.2.0-SNAPSHOT.jar
runtime_jar=soma-runtime-core/target/soma-runtime-core-0.2.0-SNAPSHOT.jar
dataflow_jar=soma-dataflow/target/soma-dataflow-0.2.0-SNAPSHOT.jar
expected=tests/fixtures/public-api/current
for artifact in "$annotations_jar" "$processor_jar" "$runtime_jar" "$dataflow_jar"; do
  if [ ! -f "$artifact" ]; then
    printf '%s\n' "public-api-check: missing artifact $artifact" >&2
    exit 1
  fi
done

mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/public-api-contract.XXXXXX")
actual_classification=$evidence_dir/classification.txt
actual_javap=$evidence_dir/public-api.javap.txt

classify_type() {
  type=$1
  case "$type" in
    io.github.somaruntime.soma.annotation.*)
      printf '%s\n' "PUBLIC handwritten $type"
      ;;
    io.github.somaruntime.soma.processor.SomaProcessor|\
    io.github.somaruntime.soma.processor.javac8.SomaJavacPlugin)
      printf '%s\n' "PUBLIC build-provider $type"
      ;;
    io.github.somaruntime.soma.runtime.generated.*)
      printf '%s\n' "PUBLIC generated-runtime $type"
      ;;
    io.github.somaruntime.soma.runtime.GeneratedColumnAccess)
      printf '%s\n' "PUBLIC generated-construction-protocol $type"
      ;;
    io.github.somaruntime.soma.runtime.*)
      printf '%s\n' "PUBLIC handwritten-runtime $type"
      ;;
    io.github.somaruntime.soma.dataflow.generated.*)
      printf '%s\n' "PUBLIC generated-dataflow-protocol $type"
      ;;
    io.github.somaruntime.soma.dataflow.*)
      printf '%s\n' "PUBLIC handwritten-dataflow $type"
      ;;
    io.github.somaruntime.soma.processor.internal.*)
      printf '%s\n' "INTERNAL implementation $type"
      ;;
    *)
      printf '%s\n' "UNCLASSIFIED implementation $type"
      ;;
  esac
}

for artifact in "$annotations_jar" "$processor_jar" "$runtime_jar" "$dataflow_jar"; do
  "$JAVA_HOME/bin/jar" tf "$artifact" | sed -n '/\.class$/p' | while IFS= read -r entry; do
    type=$(printf '%s' "$entry" | sed 's#/#.#g; s#\.class$##')
    declaration=$(
      "$JAVA_HOME/bin/javap" -classpath "$artifact" -public "$type" 2>/dev/null |
        sed -n '/^public /p' | head -n 1
    )
    if [ -n "$declaration" ]; then
      classify_type "$type"
    fi
  done
done | sort >"$actual_classification"

cmp "$expected/classification.txt" "$actual_classification"

while read -r classification role type; do
  if [ "$classification" = 'PUBLIC' ]; then
    printf '%s\n' "## $classification $role $type" >>"$actual_javap"
    case "$type" in
      io.github.somaruntime.soma.annotation.*)
        artifact=$annotations_jar
        ;;
      io.github.somaruntime.soma.runtime.*)
        artifact=$runtime_jar
        ;;
      io.github.somaruntime.soma.dataflow.*)
        artifact=$dataflow_jar
        ;;
      *)
        artifact=$processor_jar
        ;;
    esac
    "$JAVA_HOME/bin/javap" -classpath "$artifact" -public "$type" \
      >>"$actual_javap"
  fi
done <"$actual_classification"

cmp "$expected/public-api.javap.txt" "$actual_javap"

if grep -F 'io.github.somaruntime.soma.runtime.generated.StorageBudget' \
  "$actual_classification" "$actual_javap" >/dev/null; then
  printf '%s\n' 'public-api-check: internal storage budget leaked into protocol' >&2
  exit 1
fi
plan_surface_javap=$evidence_dir/plan-surface.javap.txt
"$JAVA_HOME/bin/javap" -classpath "$runtime_jar" -public \
  io.github.somaruntime.soma.runtime.RuntimePlan \
  'io.github.somaruntime.soma.runtime.RuntimePlan$Builder' \
  io.github.somaruntime.soma.runtime.TablePlan \
  'io.github.somaruntime.soma.runtime.TablePlan$Builder' >"$plan_surface_javap"
if grep -E '^  public static io\.github\.somaruntime\.soma\.runtime\.RuntimePlan\$Builder builder\(java\.lang\.String, java\.lang\.String, java\.lang\.String, java\.lang\.String, java\.lang\.String\);$|^  public static io\.github\.somaruntime\.soma\.runtime\.TablePlan\$Builder builder\(java\.lang\.String, java\.lang\.String\);$|^  public io\.github\.somaruntime\.soma\.runtime\.RuntimePlan\$Builder (addTable|replaceTable|addChild|replaceChild)\(' \
  "$plan_surface_javap" >/dev/null; then
  printf '%s\n' 'public-api-check: raw application Plan construction surface leaked' >&2
  exit 1
fi
if grep -E '^  public io\.github\.somaruntime\.soma\.runtime\.TablePlan\$Builder (keySpaceStrategy|accessStrategy)\(java\.lang\.String\);$' \
  "$plan_surface_javap" >/dev/null; then
  printf '%s\n' 'public-api-check: free-form physical Plan strategy leaked' >&2
  exit 1
fi
grep -F 'public static io.github.somaruntime.soma.runtime.RuntimePlan$Builder builder(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String);' \
  "$actual_javap" >/dev/null
grep -F 'public io.github.somaruntime.soma.runtime.RuntimePlan$TableEditor table(io.github.somaruntime.soma.runtime.metadata.SomaTableMetadata);' \
  "$actual_javap" >/dev/null
bridge_javap=$evidence_dir/generated-column-access.javap.txt
"$JAVA_HOME/bin/javap" -classpath "$runtime_jar" -public \
  io.github.somaruntime.soma.runtime.GeneratedColumnAccess >"$bridge_javap"
if grep -F 'io.github.somaruntime.soma.runtime.generated.' "$bridge_javap" >/dev/null; then
  printf '%s\n' 'public-api-check: generated construction bridge leaked runtime binding type' >&2
  exit 1
fi
if grep -E '^  public io\.github\.somaruntime\.soma\.runtime\.(Boolean|Byte|Short|Int|Long|Float|Double|Enum)Column(Traversal|View)\(' \
  "$actual_javap" >/dev/null; then
  printf '%s\n' 'public-api-check: direct Column Traversal/View constructor leaked' >&2
  exit 1
fi
generated_column_javap=$evidence_dir/generated-column.javap.txt
"$JAVA_HOME/bin/javap" -classpath "$runtime_jar" -public \
  io.github.somaruntime.soma.runtime.generated.GeneratedColumn >"$generated_column_javap"
if grep -E '^  public abstract (long (estimatedBytes|retainedBytes)|void releaseStorage)' \
  "$generated_column_javap" >/dev/null; then
  printf '%s\n' 'public-api-check: internal column accounting leaked' >&2
  exit 1
fi

annotation_contract_classes=$evidence_dir/annotation-contract
mkdir -p "$annotation_contract_classes"
"$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 -proc:none \
  -cp "$annotations_jar" \
  -d "$annotation_contract_classes" \
  "$expected/AnnotationContractConsumer.java"
"$JAVA_HOME/bin/java" \
  -cp "$annotation_contract_classes:$annotations_jar" \
  AnnotationContractConsumer

printf '%s\n' "public-api-evidence: $evidence_dir"
printf '%s\n' 'public-api-check: ok'
