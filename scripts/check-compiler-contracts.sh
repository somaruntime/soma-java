#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'compiler-contracts: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

java_specification=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' "compiler-contracts: expected Java 8, got $java_specification" >&2
  exit 1
fi

annotations_jar=soma-annotations/target/soma-annotations-0.2.0-SNAPSHOT.jar
processor_jar=soma-processor/target/soma-processor-0.2.0-SNAPSHOT.jar
runtime_jar=soma-runtime-core/target/soma-runtime-core-0.2.0-SNAPSHOT.jar
for artifact in "$annotations_jar" "$processor_jar" "$runtime_jar"; do
  if [ ! -f "$artifact" ]; then
    printf '%s\n' "compiler-contracts: missing artifact $artifact; run reactor package first" >&2
    exit 1
  fi
done
compile_classpath=$annotations_jar:$processor_jar:$runtime_jar

fixture_root=tests/fixtures/compiler
success_source=$fixture_root/value-success/src
plugin_only_source=$fixture_root/plugin-only-location/src
unicode_order_source=$fixture_root/unicode-order/src
unicode_order_expected=$fixture_root/unicode-order/expected
expected=$fixture_root/value-success/expected
mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/compiler-contracts.XXXXXX")
run_a=$evidence_dir/run-a
run_b=$evidence_dir/run-b
missing=$evidence_dir/missing-plugin
conflict=$evidence_dir/conflict
mutation=$evidence_dir/mutation
plugin_only=$evidence_dir/plugin-only
plugin_only_repeat=$evidence_dir/plugin-only-repeat
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
unicode_order=$evidence_dir/unicode-order
unicode_order_repeat=$evidence_dir/unicode-order-repeat
mkdir -p \
  "$run_a" "$run_b" "$missing" "$conflict" "$mutation" \
  "$plugin_only" "$plugin_only_repeat" "$plugin_option_spoof" "$ignored" "$generic" \
  "$spoof" "$spoof_only" "$cycle" \
  "$duplicate_schema" "$injection" "$wildcard" "$local_value" \
  "$schema_version" "$field_name" "$unicode_order" "$unicode_order_repeat"

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
    -cp "$compile_classpath" \
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

compile_unicode_order() {
  output=$1
  shift
  "$JAVA_HOME/bin/javac" \
    "$@" \
    -encoding UTF-8 \
    -source 8 \
    -target 8 \
    -cp "$compile_classpath" \
    -processorpath "$processor_jar:$annotations_jar" \
    -processor com.hgtech.soma.processor.SomaProcessor \
    -Xplugin:SomaValue \
    -d "$output" \
    $(find "$unicode_order_source" -type f -name '*.java' | sort)
}

compile_unicode_order "$unicode_order"
compile_unicode_order "$unicode_order_repeat" \
  -J-Duser.language=tr -J-Duser.country=TR \
  -J-Duser.timezone=Pacific/Kiritimati
unicode_schema=META-INF/soma/com.example.unicode.schema.json
unicode_hash=META-INF/soma/com.example.unicode.schema.sha256
cmp "$unicode_order/$unicode_schema" "$unicode_order_repeat/$unicode_schema"
cmp "$unicode_order/$unicode_hash" "$unicode_order_repeat/$unicode_hash"
cmp "$unicode_order_expected/com.example.unicode.schema.sha256" \
  "$unicode_order/$unicode_hash"
"$JAVA_HOME/bin/java" -cp "$unicode_order" com.example.unicode.UnicodeOrderConsumer

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
  -cp "$compile_classpath" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -d "$missing" \
  $(find "$success_source" -type f -name '*.java' | sort) \
  >"$evidence_dir/missing-plugin.log" 2>&1; then
  printf '%s\n' 'compiler-contracts: missing plugin fixture unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-COMP-001]' "$evidence_dir/missing-plugin.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -XDrawDiagnostics \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$compile_classpath" \
  -proc:none -Xplugin:SomaValue \
  -d "$plugin_only" \
  $(find "$plugin_only_source" -type f -name '*.java' | sort) \
  >"$evidence_dir/plugin-only.log" 2>&1; then
  printf '%s\n' 'compiler-contracts: plugin-only fixture unexpectedly compiled' >&2
  exit 1
fi
if "$JAVA_HOME/bin/javac" \
  -J-Duser.language=tr -J-Duser.country=TR \
  -J-Duser.timezone=Pacific/Kiritimati \
  -XDrawDiagnostics \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$compile_classpath" \
  -proc:none -Xplugin:SomaValue \
  -d "$plugin_only_repeat" \
  $(find "$plugin_only_source" -type f -name '*.java' | sort) \
  >"$evidence_dir/plugin-only-repeat.log" 2>&1; then
  printf '%s\n' 'compiler-contracts: repeated plugin-only fixture unexpectedly compiled' >&2
  exit 1
fi
cmp "$evidence_dir/plugin-only.log" "$evidence_dir/plugin-only-repeat.log"
test "$(grep -c -F '[SOMA-COMP-005]' "$evidence_dir/plugin-only.log")" -eq 1
grep -F 'PluginOnlyValue.java:7:8: compiler.err.proc.messager: [SOMA-COMP-005] SomaValue plugin requires active processor soma-processor-v1' \
  "$evidence_dir/plugin-only.log" >/dev/null
if find "$plugin_only" "$plugin_only_repeat" -type f \
    \( -name '*.class' -o -path '*/META-INF/soma/*' \) | grep . >/dev/null; then
  printf '%s\n' 'compiler-contracts: plugin-only failure emitted artifacts' >&2
  exit 1
fi

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$compile_classpath" \
  -proc:none -Xplugin:SomaValue \
  -XDcom.hgtech.soma.internal.processor.identity=soma-processor-v1 \
  -d "$plugin_option_spoof" \
  $(find "$success_source" -type f -name '*.java' | sort) \
  >"$evidence_dir/plugin-option-spoof.log" 2>&1; then
  printf '%s\n' 'compiler-contracts: -XD option spoof bypassed processor handshake' >&2
  exit 1
fi
grep -F '[SOMA-COMP-005]' "$evidence_dir/plugin-option-spoof.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -XDrawDiagnostics \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$compile_classpath" \
  -proc:none -Xplugin:SomaValue \
  -d "$conflict" \
  $(find "$fixture_root/value-conflict/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/value-conflict.log" 2>&1; then
  printf '%s\n' 'compiler-contracts: conflicting value fixture unexpectedly compiled' >&2
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
  printf '%s\n' 'compiler-contracts: final field mutation unexpectedly compiled' >&2
  exit 1
fi
grep -F 'compiler.err.cant.assign.val.to.final.var: value' \
  "$evidence_dir/value-mutation.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$compile_classpath" \
  -proc:none -Xplugin:SomaValue \
  -d "$ignored" \
  $(find "$fixture_root/value-ignore/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/value-ignore.log" 2>&1; then
  printf '%s\n' 'compiler-contracts: ignored instance state unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-VALUE-003]' "$evidence_dir/value-ignore.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$compile_classpath" \
  -proc:none -Xplugin:SomaValue \
  -d "$generic" \
  $(find "$fixture_root/value-generic/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/value-generic.log" 2>&1; then
  printf '%s\n' 'compiler-contracts: generic value unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-VALUE-001]' "$evidence_dir/value-generic.log" >/dev/null

"$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$compile_classpath" \
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
  -cp "$compile_classpath" \
  -proc:none -Xplugin:SomaValue \
  -d "$spoof_only" \
  $(find "$fixture_root/annotation-spoof/src" -type f -name '*.java' | sort)
"$JAVA_HOME/bin/javap" -classpath "$spoof_only" -p com.example.fake.NotSoma \
  >"$evidence_dir/NotSoma-only.javap.txt"
cmp "$fixture_root/annotation-spoof/expected/NotSoma.javap.txt" \
  "$evidence_dir/NotSoma-only.javap.txt"

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$compile_classpath" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -d "$cycle" \
  $(find "$fixture_root/value-cycle/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/value-cycle.log" 2>&1; then
  printf '%s\n' 'compiler-contracts: cyclic value graph unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-VALUE-007]' "$evidence_dir/value-cycle.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$compile_classpath" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -d "$duplicate_schema" \
  $(find "$fixture_root/duplicate-schema/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/duplicate-schema.log" 2>&1; then
  printf '%s\n' 'compiler-contracts: duplicate schema names unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-SCHEMA-005]' "$evidence_dir/duplicate-schema.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$compile_classpath" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -d "$injection" \
  $(find "$fixture_root/schema-injection/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/schema-injection.log" 2>&1; then
  printf '%s\n' 'compiler-contracts: schema path injection unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-SCHEMA-002]' "$evidence_dir/schema-injection.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$compile_classpath" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -d "$wildcard" \
  $(find "$fixture_root/wildcard-import/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/wildcard-import.log" 2>&1; then
  printf '%s\n' 'compiler-contracts: wildcard compiler annotation unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-COMP-006]' "$evidence_dir/wildcard-import.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$compile_classpath" \
  -proc:none -Xplugin:SomaValue \
  -d "$local_value" \
  $(find "$fixture_root/value-local/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/value-local.log" 2>&1; then
  printf '%s\n' 'compiler-contracts: local value unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-VALUE-001]' "$evidence_dir/value-local.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$compile_classpath" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -d "$schema_version" \
  $(find "$fixture_root/schema-version/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/schema-version.log" 2>&1; then
  printf '%s\n' 'compiler-contracts: boundary-whitespace schema version compiled' >&2
  exit 1
fi
grep -F '[SOMA-SCHEMA-004]' "$evidence_dir/schema-version.log" >/dev/null

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$compile_classpath" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -d "$field_name" \
  $(find "$fixture_root/field-name/src" -type f -name '*.java' | sort) \
  >"$evidence_dir/field-name.log" 2>&1; then
  printf '%s\n' 'compiler-contracts: invalid logical field name compiled' >&2
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
      "compiler-contracts: supported diagnostic leaked internal stack: $diagnostic_log" >&2
    exit 1
  fi
done

if [ -n "${SOMA_UNSUPPORTED_JAVAC:-}" ]; then
  if [ ! -x "$SOMA_UNSUPPORTED_JAVAC" ]; then
    printf '%s\n' 'compiler-contracts: SOMA_UNSUPPORTED_JAVAC is not executable' >&2
    exit 1
  fi
  unsupported=$evidence_dir/unsupported-compiler
  mkdir -p "$unsupported"
  if "$SOMA_UNSUPPORTED_JAVAC" \
    --release 8 \
    -cp "$compile_classpath" \
    -proc:none \
    -Xplugin:SomaValue \
    -d "$unsupported" \
    $(find "$success_source" -type f -name '*.java' | sort) \
    >"$evidence_dir/unsupported-compiler.log" 2>&1; then
    printf '%s\n' 'compiler-contracts: unsupported compiler unexpectedly compiled' >&2
    exit 1
  fi
  grep -F '[SOMA-COMP-002] SomaValue requires full JDK 8 javac' \
    "$evidence_dir/unsupported-compiler.log" >/dev/null
  "$SOMA_UNSUPPORTED_JAVAC" -version
  printf '%s\n' 'compiler-unsupported: rejected with SOMA-COMP-002'
else
  printf '%s\n' \
    'compiler-unsupported: skipped (set SOMA_UNSUPPORTED_JAVAC for this lane)'
fi

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
printf '%s\n' "compiler-contracts-evidence: $evidence_dir"
printf '%s\n' 'compiler-contracts: ok'
