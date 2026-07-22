#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'floating-value-storage-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

annotations_jar=soma-annotations/target/soma-annotations-0.2.0-SNAPSHOT.jar
processor_jar=soma-processor/target/soma-processor-0.2.0-SNAPSHOT.jar
runtime_jar=soma-runtime-core/target/soma-runtime-core-0.2.0-SNAPSHOT.jar
fixture=soma-testkit/src/test/fixtures/compiler/floating-value-storage
mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/floating-value-storage.XXXXXX")
classes=$evidence_dir/classes
generated=$evidence_dir/generated
mkdir -p "$classes" "$generated"

"$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar:$runtime_jar" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -s "$generated" -d "$classes" \
  $(find "$fixture/src" -type f -name '*.java' | LC_ALL=C sort)

generated_table=$generated/com/example/soma/floatingvalue/generated/FloatingRowTable.java
if grep -E 'strict(Float|Double)(Storage|Key)' "$generated_table" >/dev/null; then
  printf '%s\n' 'floating-value-storage-check: ordinary Value leaf used strict key storage' >&2
  exit 1
fi

"$JAVA_HOME/bin/java" -cp "$classes:$runtime_jar" \
  com.example.soma.floatingvalue.FloatingValueConsumer
printf '%s\n' "floating-value-storage-evidence: $evidence_dir"
printf '%s\n' 'floating-value-storage-check: ok'
