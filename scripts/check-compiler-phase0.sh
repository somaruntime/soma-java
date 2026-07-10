#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'compiler-phase0-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

java_specification=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' "compiler-phase0-check: expected Java 8, got $java_specification" >&2
  exit 1
fi

annotations_jar=soma-annotations/target/soma-annotations-0.1.0-SNAPSHOT.jar
processor_jar=soma-processor/target/soma-processor-0.1.0-SNAPSHOT.jar
for artifact in "$annotations_jar" "$processor_jar"; do
  if [ ! -f "$artifact" ]; then
    printf '%s\n' "compiler-phase0-check: missing artifact $artifact; run reactor package first" >&2
    exit 1
  fi
done

fixture_root=soma-testkit/src/test/fixtures/compiler
success_source=$fixture_root/value-success/src
expected=$fixture_root/value-success/expected
mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/phase0-compiler.XXXXXX")
run_a=$evidence_dir/run-a
run_b=$evidence_dir/run-b
missing=$evidence_dir/missing-plugin
conflict=$evidence_dir/conflict
mutation=$evidence_dir/mutation
plugin_only=$evidence_dir/plugin-only
plugin_option_spoof=$evidence_dir/plugin-option-spoof
ignored=$evidence_dir/ignored-state
generic=$evidence_dir/generic-value
spoof=$evidence_dir/annotation-spoof
spoof_only=$evidence_dir/annotation-spoof-only
cycle=$evidence_dir/value-cycle
duplicate_schema=$evidence_dir/duplicate-schema
injection=$evidence_dir/schema-injection
wildcard=$evidence_dir/wildcard-import
local_value=$evidence_dir/local-value
schema_version=$evidence_dir/schema-version
field_name=$evidence_dir/field-name
mkdir -p \
  "$run_a" "$run_b" "$missing" "$conflict" "$mutation" \
  "$plugin_only" "$plugin_option_spoof" "$ignored" "$generic" \
  "$spoof" "$spoof_only" "$cycle" \
  "$duplicate_schema" "$injection" "$wildcard" "$local_value" \
  "$schema_version" "$field_name"

compile_success() {
  output=$1
  mode=${2:-default}
  if [ "$mode" = 'alternate-locale' ]; then
    set -- \
      -J-Duser.language=tr \
      -J-Duser.country=TR \
      -J-Duser.timezone=Pacific/Kiritimati
  else
    set --
  fi
  "$JAVA_HOME/bin/javac" \
    "$@" \
    -encoding UTF-8 \
    -source 8 \
    -target 8 \
    -cp "$annotations_jar:$processor_jar" \
    -processorpath "$processor_jar:$annotations_jar" \
    -processor com.hgtech.soma.processor.SomaProcessor \
    -Xplugin:SomaValue \
    -d "$output" \
    $(find "$success_source" -type f -name '*.java' | sort)
}

compile_success "$run_a"
compile_success "$run_b" alternate-locale

schema_json=META-INF/soma/com.example.phase0.schema.json
schema_hash=META-INF/soma/com.example.phase0.schema.sha256
diff -r "$run_a" "$run_b"
cmp "$expected/com.example.phase0.schema.json" "$run_a/$schema_json"
cmp "$expected/com.example.phase0.schema.sha256" "$run_a/$schema_hash"

"$JAVA_HOME/bin/javap" -classpath "$run_a" -p com.example.phase0.MachineId \
  >"$evidence_dir/MachineId.javap.txt"
"$JAVA_HOME/bin/javap" -classpath "$run_a" -p com.example.phase0.OperationKey \
  >"$evidence_dir/OperationKey.javap.txt"
cmp "$expected/MachineId.javap.txt" "$evidence_dir/MachineId.javap.txt"
cmp "$expected/OperationKey.javap.txt" "$evidence_dir/OperationKey.javap.txt"

# Runtime execution intentionally omits annotations/processor artifacts.
"$JAVA_HOME/bin/java" -cp "$run_a" com.example.phase0.ValueConsumer

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -d "$missing" \
  $(find "$success_source" -type f -name '*.java' | sort) \
  >"$evidence_dir/missing-plugin.log" 2>&1; then
  printf '%s\n' 'compiler-phase0-check: missing plugin fixture unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-COMP-001]' "$evidence_dir/missing-plugin.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar" \
  -proc:none -Xplugin:SomaValue \
  -d "$plugin_only" \
  $(find "$success_source" -type f -name '*.java' | sort) \
  >"$evidence_dir/plugin-only.log" 2>&1; then
  printf '%s\n' 'compiler-phase0-check: plugin-only fixture unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-COMP-005]' "$evidence_dir/plugin-only.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar" \
  -proc:none -Xplugin:SomaValue \
  -XDcom.hgtech.soma.internal.processor.identity=soma-processor-v1 \
  -d "$plugin_option_spoof" \
  $(find "$success_source" -type f -name '*.java' | sort) \
  >"$evidence_dir/plugin-option-spoof.log" 2>&1; then
  printf '%s\n' 'compiler-phase0-check: -XD option spoof bypassed processor handshake' >&2
  exit 1
fi
grep -F '[SOMA-COMP-005]' "$evidence_dir/plugin-option-spoof.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -XDrawDiagnostics \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar" \
  -proc:none -Xplugin:SomaValue \
  -d "$conflict" \
  $(find "$fixture_root/value-conflict/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/value-conflict.log" 2>&1; then
  printf '%s\n' 'compiler-phase0-check: conflicting value fixture unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-VALUE-002]' "$evidence_dir/value-conflict.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -XDrawDiagnostics \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$run_a" -proc:none \
  -d "$mutation" \
  $(find "$fixture_root/value-mutation/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/value-mutation.log" 2>&1; then
  printf '%s\n' 'compiler-phase0-check: final field mutation unexpectedly compiled' >&2
  exit 1
fi
grep -F 'compiler.err.cant.assign.val.to.final.var: value' \
  "$evidence_dir/value-mutation.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar" \
  -proc:none -Xplugin:SomaValue \
  -d "$ignored" \
  $(find "$fixture_root/value-ignore/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/value-ignore.log" 2>&1; then
  printf '%s\n' 'compiler-phase0-check: ignored instance state unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-VALUE-003]' "$evidence_dir/value-ignore.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar" \
  -proc:none -Xplugin:SomaValue \
  -d "$generic" \
  $(find "$fixture_root/value-generic/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/value-generic.log" 2>&1; then
  printf '%s\n' 'compiler-phase0-check: generic value unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-VALUE-001]' "$evidence_dir/value-generic.log" >/dev/null

"$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -d "$spoof" \
  $(find "$success_source" "$fixture_root/annotation-spoof/src" \
    -type f -name '*.java' | sort)
"$JAVA_HOME/bin/javap" -classpath "$spoof" -p com.example.fake.NotSoma \
  >"$evidence_dir/NotSoma.javap.txt"
cmp "$fixture_root/annotation-spoof/expected/NotSoma.javap.txt" \
  "$evidence_dir/NotSoma.javap.txt"

"$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar" \
  -proc:none -Xplugin:SomaValue \
  -d "$spoof_only" \
  $(find "$fixture_root/annotation-spoof/src" -type f -name '*.java' | sort)
"$JAVA_HOME/bin/javap" -classpath "$spoof_only" -p com.example.fake.NotSoma \
  >"$evidence_dir/NotSoma-only.javap.txt"
cmp "$fixture_root/annotation-spoof/expected/NotSoma.javap.txt" \
  "$evidence_dir/NotSoma-only.javap.txt"

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -d "$cycle" \
  $(find "$fixture_root/value-cycle/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/value-cycle.log" 2>&1; then
  printf '%s\n' 'compiler-phase0-check: cyclic value graph unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-VALUE-007]' "$evidence_dir/value-cycle.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -d "$duplicate_schema" \
  $(find "$fixture_root/duplicate-schema/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/duplicate-schema.log" 2>&1; then
  printf '%s\n' 'compiler-phase0-check: duplicate schema names unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-SCHEMA-005]' "$evidence_dir/duplicate-schema.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -d "$injection" \
  $(find "$fixture_root/schema-injection/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/schema-injection.log" 2>&1; then
  printf '%s\n' 'compiler-phase0-check: schema path injection unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-SCHEMA-002]' "$evidence_dir/schema-injection.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -d "$wildcard" \
  $(find "$fixture_root/wildcard-import/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/wildcard-import.log" 2>&1; then
  printf '%s\n' 'compiler-phase0-check: wildcard compiler annotation unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-COMP-006]' "$evidence_dir/wildcard-import.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar" \
  -proc:none -Xplugin:SomaValue \
  -d "$local_value" \
  $(find "$fixture_root/value-local/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/value-local.log" 2>&1; then
  printf '%s\n' 'compiler-phase0-check: local value unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-VALUE-001]' "$evidence_dir/value-local.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -d "$schema_version" \
  $(find "$fixture_root/schema-version/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/schema-version.log" 2>&1; then
  printf '%s\n' 'compiler-phase0-check: boundary-whitespace schema version compiled' >&2
  exit 1
fi
grep -F '[SOMA-SCHEMA-004]' "$evidence_dir/schema-version.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -d "$field_name" \
  $(find "$fixture_root/field-name/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/field-name.log" 2>&1; then
  printf '%s\n' 'compiler-phase0-check: invalid logical field name compiled' >&2
  exit 1
fi
grep -F '[SOMA-VALUE-004]' "$evidence_dir/field-name.log" >/dev/null

for diagnostic_log in "$evidence_dir"/*.log; do
  if [ "$(basename "$diagnostic_log")" = 'unsupported-compiler.log' ]; then
    continue
  fi
  if grep -E 'Exception in thread|^[[:space:]]+at (com\.hgtech|com\.sun\.tools)' \
    "$diagnostic_log" >/dev/null; then
    printf '%s\n' \
      "compiler-phase0-check: supported diagnostic leaked internal stack: $diagnostic_log" >&2
    exit 1
  fi
done

if [ -n "${SOMA_UNSUPPORTED_JAVAC:-}" ]; then
  if [ ! -x "$SOMA_UNSUPPORTED_JAVAC" ]; then
    printf '%s\n' 'compiler-phase0-check: SOMA_UNSUPPORTED_JAVAC is not executable' >&2
    exit 1
  fi
  unsupported=$evidence_dir/unsupported-compiler
  mkdir -p "$unsupported"
  if "$SOMA_UNSUPPORTED_JAVAC" \
    --release 8 \
    -cp "$annotations_jar:$processor_jar" \
    -proc:none \
    -Xplugin:SomaValue \
    -d "$unsupported" \
    $(find "$success_source" -type f -name '*.java' | sort) \
    >"$evidence_dir/unsupported-compiler.log" 2>&1; then
    printf '%s\n' 'compiler-phase0-check: unsupported compiler unexpectedly compiled' >&2
    exit 1
  fi
  grep -F '[SOMA-COMP-002] SomaValue requires full JDK 8 javac' \
    "$evidence_dir/unsupported-compiler.log" >/dev/null
  "$SOMA_UNSUPPORTED_JAVAC" -version
  printf '%s\n' 'compiler-phase0-unsupported: rejected with SOMA-COMP-002'
else
  printf '%s\n' \
    'compiler-phase0-unsupported: skipped (set SOMA_UNSUPPORTED_JAVAC for this lane)'
fi

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
printf '%s\n' "compiler-phase0-evidence: $evidence_dir"
printf '%s\n' 'compiler-phase0-check: ok'
