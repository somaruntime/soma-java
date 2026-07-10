#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javap" ]; then
  printf '%s\n' 'public-api-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

annotations_jar=soma-annotations/target/soma-annotations-0.1.0-SNAPSHOT.jar
processor_jar=soma-processor/target/soma-processor-0.1.0-SNAPSHOT.jar
expected=soma-testkit/src/test/fixtures/public-api/phase0
for artifact in "$annotations_jar" "$processor_jar"; do
  if [ ! -f "$artifact" ]; then
    printf '%s\n' "public-api-check: missing artifact $artifact" >&2
    exit 1
  fi
done

mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/phase0-public-api.XXXXXX")
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
    com.hgtech.soma.processor.internal.*)
      printf '%s\n' "INTERNAL implementation $type"
      ;;
    *)
      printf '%s\n' "UNCLASSIFIED implementation $type"
      ;;
  esac
}

for artifact in "$annotations_jar" "$processor_jar"; do
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
      *)
        artifact=$processor_jar
        ;;
    esac
    "$JAVA_HOME/bin/javap" -classpath "$artifact" -public "$type" \
      >>"$actual_javap"
  fi
done <"$actual_classification"

cmp "$expected/public-api.javap.txt" "$actual_javap"

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
