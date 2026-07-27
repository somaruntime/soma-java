#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'table-diagnostics-phase1-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

annotations_jar=soma-annotations/target/soma-annotations-0.2.0-SNAPSHOT.jar
processor_jar=soma-processor/target/soma-processor-0.2.0-SNAPSHOT.jar
runtime_jar=soma-runtime-core/target/soma-runtime-core-0.2.0-SNAPSHOT.jar
dataflow_jar=soma-dataflow/target/soma-dataflow-0.2.0-SNAPSHOT.jar
fixture_root=soma-testkit/src/test/fixtures/compiler
mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/phase1-table-diagnostics.XXXXXX")

compile_failure() {
  fixture_name=$1
  expected_code=$2
  output=$evidence_dir/$fixture_name
  mkdir -p "$output"
  if "$JAVA_HOME/bin/javac" \
    -encoding UTF-8 -source 8 -target 8 \
    -cp "$annotations_jar:$processor_jar:$runtime_jar:$dataflow_jar" \
    -processorpath "$processor_jar:$annotations_jar" \
    -processor com.hgtech.soma.processor.SomaProcessor \
    -Xplugin:SomaValue \
    -d "$output" \
    $(find "$fixture_root/$fixture_name/src" -type f -name '*.java' | sort) \
    >"$evidence_dir/$fixture_name.log" 2>&1; then
    printf '%s\n' "table-diagnostics-phase1-check: $fixture_name unexpectedly compiled" >&2
    exit 1
  fi
  grep -F "[$expected_code]" "$evidence_dir/$fixture_name.log" >/dev/null
}

compile_success() {
  fixture_name=$1
  output=$evidence_dir/$fixture_name
  mkdir -p "$output"
  "$JAVA_HOME/bin/javac" \
    -encoding UTF-8 -source 8 -target 8 \
    -cp "$annotations_jar:$processor_jar:$runtime_jar:$dataflow_jar" \
    -processorpath "$processor_jar:$annotations_jar" \
    -processor com.hgtech.soma.processor.SomaProcessor \
    -Xplugin:SomaValue \
    -d "$output" \
    $(find "$fixture_root/$fixture_name/src" -type f -name '*.java' | sort) \
    >"$evidence_dir/$fixture_name.log" 2>&1
}

compile_failure table-non-public SOMA-TABLE-001
compile_failure table-final-field SOMA-TABLE-006
compile_failure table-optional-primitive SOMA-TABLE-005
compile_failure table-arbitrary-object SOMA-TABLE-005
compile_failure table-checked-constructor SOMA-TABLE-006
compile_failure table-capacity SOMA-TABLE-007
compile_failure table-generated-collision SOMA-GEN-001
compile_failure table-invalid-selector SOMA-TABLE-009
compile_failure table-selector-placement SOMA-TABLE-009
compile_success table-selector-string
compile_failure table-selector-collision SOMA-GEN-001
compile_failure table-invalid-child SOMA-TABLE-007
compile_failure table-child-cycle SOMA-TABLE-011
compile_failure table-invalid-child-shape SOMA-TABLE-010

grep -F 'selector annotations are only valid on @SomaTable types' \
  "$evidence_dir/table-selector-placement.log" >/dev/null
grep -F 'arbitrary Java object is not a SOMA schema field' \
  "$evidence_dir/table-arbitrary-object.log" >/dev/null
grep -F 'store a stable ID and keep application objects in a sidecar/registry' \
  "$evidence_dir/table-arbitrary-object.log" >/dev/null
test -f "$evidence_dir/table-selector-string/com/example/tablebad/stringselector/generated/BadStringSelectorTable.class"
grep -F 'selector path is optional and cannot be indexed: optionalValue' \
  "$evidence_dir/table-invalid-selector.log" >/dev/null
grep -F 'cyclic child ownership declaration:' \
  "$evidence_dir/table-child-cycle.log" >/dev/null

if grep -E 'Exception in thread|^[[:space:]]+at (com\.hgtech|com\.sun\.tools)' \
  "$evidence_dir"/*.log >/dev/null; then
  printf '%s\n' 'table-diagnostics-phase1-check: diagnostic leaked internal stack' >&2
  exit 1
fi

printf '%s\n' "table-diagnostics-phase1-evidence: $evidence_dir"
printf '%s\n' 'table-diagnostics-phase1-check: ok'
