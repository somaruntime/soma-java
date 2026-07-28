#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'generated-naming-contract: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

./mvnw -B -ntp -pl soma-processor -am package -DskipTests
./mvnw -B -ntp -pl soma-dataflow -am package -DskipTests

annotations_jar=soma-annotations/target/soma-annotations-0.2.0-SNAPSHOT.jar
processor_jar=soma-processor/target/soma-processor-0.2.0-SNAPSHOT.jar
runtime_jar=soma-runtime-core/target/soma-runtime-core-0.2.0-SNAPSHOT.jar
dataflow_jar=soma-dataflow/target/soma-dataflow-0.2.0-SNAPSHOT.jar
fixture=tests/fixtures/compiler/internal-names
mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/generated-naming-contract.XXXXXX")
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
  $(find "$fixture/src" -type f -name '*.java' | sort)

table_source=$generated/com/example/internalnames/generated/InternalNamesRowTable.java
test -s "$table_source"
if rg -n '\browIndex\b|rowIndexes\(' "$generated" >/dev/null \
    || rg -n 'public .*\(int rowIndex\)' \
      soma-runtime-core/src/main/java/io/github/somaruntime/soma/runtime/*ColumnView.java >/dev/null; then
  printf '%s\n' 'generated-naming-contract: public rowIndex parameter leaked' >&2
  exit 1
fi
if rg -n -i --glob '!**/target/**' \
    'row[- ]pipeline|column pipeline|key pipeline|generated-row-pipeline|allocatedRowPipeline' \
    soma-processor/src/main soma-runtime-core/src/main \
    soma-examples/industrial-dynamic-scheduler/src/main \
    soma-examples/grassing-individual-simulation/src/main \
    soma-benchmarks/src/main tests/fixtures >/dev/null; then
  printf '%s\n' 'generated-naming-contract: superseded core access vocabulary leaked' >&2
  exit 1
fi
grep -F 'private int updateScratchCapacity;' "$table_source" >/dev/null
grep -F 'private int[] updateField1=new int[0];' "$table_source" >/dev/null
grep -F 'private int[] updateField2=new int[0];' "$table_source" >/dev/null
grep -F 'private int[] updateField3=new int[0];' "$table_source" >/dev/null
grep -F 'private int[] updateField4=new int[0];' "$table_source" >/dev/null
if grep -E 'private int\[\] update(Capacity|ScratchCapacity|UpdateScratchCapacity|CandidateScratch)=' \
    "$table_source" >/dev/null; then
  printf '%s\n' 'generated-naming-contract: schema-derived scratch member leaked' >&2
  exit 1
fi

"$JAVA_HOME/bin/java" -cp "$classes:$runtime_jar:$dataflow_jar" \
  com.example.internalnames.InternalNamesConsumer
"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
printf '%s\n' "generated-naming-contract-evidence: $evidence_dir"
printf '%s\n' 'generated-naming-contract: ok'
