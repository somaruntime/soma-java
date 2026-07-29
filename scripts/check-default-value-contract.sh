#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"
. "$root_dir/scripts/lib/project-version.sh"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'default-value-contract: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

project_version=$(soma_project_version)
annotations_jar=soma-annotations/target/soma-annotations-$project_version.jar
processor_jar=soma-processor/target/soma-processor-$project_version.jar
runtime_jar=soma-runtime-core/target/soma-runtime-core-$project_version.jar
dataflow_jar=soma-dataflow/target/soma-dataflow-$project_version.jar
fixture=tests/fixtures/compiler/defaults-invalid
boundary_fixture=tests/fixtures/compiler/defaults-boundary
mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/default-value-contract.XXXXXX")
classes=$evidence_dir/classes
log=$evidence_dir/javac.log
mkdir -p "$classes"
boundary_classes=$evidence_dir/boundary-classes
boundary_generated=$evidence_dir/boundary-generated
mkdir -p "$boundary_classes" "$boundary_generated"

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar:$runtime_jar:$dataflow_jar" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor io.github.somaruntime.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -d "$classes" \
  $(find "$fixture/src" -type f -name '*.java' | sort) \
  >"$log" 2>&1; then
  printf '%s\n' 'default-value-contract: invalid default matrix unexpectedly compiled' >&2
  exit 1
fi

grep -F '[SOMA-TABLE-005] @SomaDefault is allowed only on required @SomaField' "$log" >/dev/null
grep -F '[SOMA-TABLE-005] @SomaDefault does not support an outer value field' "$log" >/dev/null
grep -F '[SOMA-TABLE-005] invalid schema default for state; literalLength=7' "$log" >/dev/null
grep -F '[SOMA-TABLE-005] invalid schema default for score; literalLength=3' "$log" >/dev/null
grep -F '[SOMA-TABLE-005] non-finite value default on strict selector path: value.x' "$log" >/dev/null
grep -F '[SOMA-TABLE-008] value key path cannot depend on @SomaDefault' "$log" >/dev/null
grep -F '[SOMA-TABLE-005] invalid schema default for value; literalLength=4097' "$log" >/dev/null
grep -F '[SOMA-TABLE-005] invalid schema default for hexadecimal; literalLength=7' "$log" >/dev/null
grep -F '[SOMA-TABLE-005] invalid schema default for suffix; literalLength=4' "$log" >/dev/null
grep -F '[SOMA-TABLE-005] invalid schema default for leadingWhitespace; literalLength=4' "$log" >/dev/null
grep -F '[SOMA-TABLE-005] invalid schema default for trailingWhitespace; literalLength=4' "$log" >/dev/null
if grep -F 'state: UNKNOWN' "$log" >/dev/null \
    || grep -F 'score: NaN' "$log" >/dev/null; then
  printf '%s\n' 'default-value-contract: invalid literal leaked into diagnostic' >&2
  exit 1
fi

"$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar:$runtime_jar:$dataflow_jar" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor io.github.somaruntime.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -s "$boundary_generated" \
  -d "$boundary_classes" \
  $(find "$boundary_fixture/src" -type f -name '*.java' | sort)
"$JAVA_HOME/bin/java" -cp "$boundary_classes:$runtime_jar:$dataflow_jar" \
  com.example.soma.defaultboundary.BoundaryDefaultConsumer

if grep -E 'Exception in thread|^[[:space:]]+at (io\.github\.somaruntime|com\.sun\.tools)' "$log" >/dev/null; then
  printf '%s\n' 'default-value-contract: diagnostic leaked internal stack' >&2
  exit 1
fi

"$JAVA_HOME/bin/java" -version >"$evidence_dir/java-version.txt" 2>&1
"$JAVA_HOME/bin/javac" -version >"$evidence_dir/javac-version.txt" 2>&1
uname -a >"$evidence_dir/uname.txt"
printf '%s\n' "default-value-contract-evidence: $evidence_dir"
printf '%s\n' 'default-value-contract: ok'
