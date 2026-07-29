#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"
. "$root_dir/scripts/lib/project-version.sh"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'floating-value-storage-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

project_version=$(soma_project_version)
annotations_jar=soma-annotations/target/soma-annotations-$project_version.jar
processor_jar=soma-processor/target/soma-processor-$project_version.jar
runtime_jar=soma-runtime-core/target/soma-runtime-core-$project_version.jar
dataflow_jar=soma-dataflow/target/soma-dataflow-$project_version.jar
fixture=tests/fixtures/compiler/floating-value-storage
mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/floating-value-storage.XXXXXX")
classes=$evidence_dir/classes
generated=$evidence_dir/generated
mkdir -p "$classes" "$generated"

"$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar:$runtime_jar:$dataflow_jar" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor io.github.somaruntime.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -s "$generated" -d "$classes" \
  $(find "$fixture/src" -type f -name '*.java' | LC_ALL=C sort)

generated_table=$generated/com/example/soma/floatingvalue/generated/FloatingRowTable.java
if grep -E 'strict(Float|Double)(Storage|Key)' "$generated_table" >/dev/null; then
  printf '%s\n' 'floating-value-storage-check: ordinary Value leaf used strict key storage' >&2
  exit 1
fi

"$JAVA_HOME/bin/java" -cp "$classes:$runtime_jar:$dataflow_jar" \
  com.example.soma.floatingvalue.FloatingValueConsumer
printf '%s\n' "floating-value-storage-evidence: $evidence_dir"
printf '%s\n' 'floating-value-storage-check: ok'
