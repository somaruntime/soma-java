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
expected=soma-testkit/src/test/fixtures/public-api/phase1
for artifact in "$annotations_jar" "$processor_jar" "$runtime_jar" "$dataflow_jar"; do
  if [ ! -f "$artifact" ]; then
    printf '%s\n' "public-api-check: missing artifact $artifact" >&2
    exit 1
  fi
done

mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/phase1-public-api.XXXXXX")
actual_classification=$evidence_dir/classification.txt
actual_javap=$evidence_dir/public-api.javap.txt

classify_type() {
  type=$1
  case "$type" in
    com.hgtech.soma.annotation.*)
      printf '%s\n' "PUBLIC handwritten $type"
      ;;
    com.hgtech.soma.processor.SomaProcessor|\
    com.hgtech.soma.processor.javac8.SomaJavacPlugin)
      printf '%s\n' "PUBLIC build-provider $type"
      ;;
    com.hgtech.soma.runtime.generated.*)
      printf '%s\n' "PUBLIC generated-runtime $type"
      ;;
    com.hgtech.soma.runtime.GeneratedColumnAccess)
      printf '%s\n' "PUBLIC generated-construction-protocol $type"
      ;;
    com.hgtech.soma.runtime.*)
      printf '%s\n' "PUBLIC handwritten-runtime $type"
      ;;
    com.hgtech.soma.dataflow.generated.*)
      printf '%s\n' "PUBLIC generated-dataflow-protocol $type"
      ;;
    com.hgtech.soma.dataflow.*)
      printf '%s\n' "PUBLIC handwritten-dataflow $type"
      ;;
    com.hgtech.soma.processor.internal.*)
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
      com.hgtech.soma.annotation.*)
        artifact=$annotations_jar
        ;;
      com.hgtech.soma.runtime.*)
        artifact=$runtime_jar
        ;;
      com.hgtech.soma.dataflow.*)
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

if grep -F 'com.hgtech.soma.runtime.generated.StorageBudget' \
  "$actual_classification" "$actual_javap" >/dev/null; then
  printf '%s\n' 'public-api-check: internal storage budget leaked into protocol' >&2
  exit 1
fi
plan_surface_javap=$evidence_dir/plan-surface.javap.txt
"$JAVA_HOME/bin/javap" -classpath "$runtime_jar" -public \
  com.hgtech.soma.runtime.RuntimePlan \
  'com.hgtech.soma.runtime.RuntimePlan$Builder' \
  com.hgtech.soma.runtime.TablePlan \
  'com.hgtech.soma.runtime.TablePlan$Builder' >"$plan_surface_javap"
if grep -E '^  public static com\.hgtech\.soma\.runtime\.RuntimePlan\$Builder builder\(java\.lang\.String, java\.lang\.String, java\.lang\.String, java\.lang\.String, java\.lang\.String\);$|^  public static com\.hgtech\.soma\.runtime\.TablePlan\$Builder builder\(java\.lang\.String, java\.lang\.String\);$|^  public com\.hgtech\.soma\.runtime\.RuntimePlan\$Builder (addTable|replaceTable|addChild|replaceChild)\(' \
  "$plan_surface_javap" >/dev/null; then
  printf '%s\n' 'public-api-check: raw application Plan construction surface leaked' >&2
  exit 1
fi
if grep -E '^  public com\.hgtech\.soma\.runtime\.TablePlan\$Builder (keySpaceStrategy|accessStrategy)\(java\.lang\.String\);$' \
  "$plan_surface_javap" >/dev/null; then
  printf '%s\n' 'public-api-check: free-form physical Plan strategy leaked' >&2
  exit 1
fi
grep -F 'public static com.hgtech.soma.runtime.RuntimePlan$Builder builder(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String);' \
  "$actual_javap" >/dev/null
grep -F 'public com.hgtech.soma.runtime.RuntimePlan$TableEditor table(com.hgtech.soma.runtime.metadata.SomaTableMetadata);' \
  "$actual_javap" >/dev/null
bridge_javap=$evidence_dir/generated-column-access.javap.txt
"$JAVA_HOME/bin/javap" -classpath "$runtime_jar" -public \
  com.hgtech.soma.runtime.GeneratedColumnAccess >"$bridge_javap"
if grep -F 'com.hgtech.soma.runtime.generated.' "$bridge_javap" >/dev/null; then
  printf '%s\n' 'public-api-check: generated construction bridge leaked runtime binding type' >&2
  exit 1
fi
if grep -E '^  public com\.hgtech\.soma\.runtime\.(Boolean|Byte|Short|Int|Long|Float|Double|Enum)Column(Traversal|View)\(' \
  "$actual_javap" >/dev/null; then
  printf '%s\n' 'public-api-check: direct Column Traversal/View constructor leaked' >&2
  exit 1
fi
generated_column_javap=$evidence_dir/generated-column.javap.txt
"$JAVA_HOME/bin/javap" -classpath "$runtime_jar" -public \
  com.hgtech.soma.runtime.generated.GeneratedColumn >"$generated_column_javap"
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
