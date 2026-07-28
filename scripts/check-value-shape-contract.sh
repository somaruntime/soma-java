#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'value-shape-contract: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

java_specification=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' "value-shape-contract: expected Java 8, got $java_specification" >&2
  exit 1
fi

annotations_jar=soma-annotations/target/soma-annotations-0.2.0-SNAPSHOT.jar
processor_jar=soma-processor/target/soma-processor-0.2.0-SNAPSHOT.jar
runtime_jar=soma-runtime-core/target/soma-runtime-core-0.2.0-SNAPSHOT.jar
for artifact in "$annotations_jar" "$processor_jar" "$runtime_jar"; do
  if [ ! -f "$artifact" ]; then
    printf '%s\n' \
      "value-shape-contract: missing artifact $artifact; run reactor package first" >&2
    exit 1
  fi
done
compile_classpath=$annotations_jar:$processor_jar:$runtime_jar

fixture_root=tests/fixtures/compiler/value-modifiers
mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/value-shape-contract.XXXXXX")

compile_fixture() {
  fixture=$1
  output=$2
  log=$3
  mkdir -p "$output"
  "$JAVA_HOME/bin/javac" \
    -encoding UTF-8 \
    -source 8 \
    -target 8 \
    -cp "$compile_classpath" \
    -processorpath "$processor_jar:$annotations_jar" \
    -processor com.hgtech.soma.processor.SomaProcessor \
    -Xplugin:SomaValue \
    -d "$output" \
    $(find "$fixture_root/$fixture/src" -type f -name '*.java' | sort) \
    >"$log" 2>&1
}

assert_rejected() {
  fixture=$1
  output=$evidence_dir/$fixture
  log=$evidence_dir/$fixture.log
  if compile_fixture "$fixture" "$output" "$log"; then
    printf '%s\n' \
      "value-shape-contract: $fixture fixture unexpectedly compiled" >&2
    exit 1
  fi
  for annotation in SomaKey SomaChild SomaOptional; do
    grep -F \
      "[SOMA-VALUE-003] @SomaValue field cannot declare @$annotation" \
      "$log" >/dev/null
  done
}

assert_rejected fqn
assert_rejected imported

ordinary_output=$evidence_dir/ordinary
ordinary_log=$evidence_dir/ordinary.log
compile_fixture ordinary "$ordinary_output" "$ordinary_log"

ordinary_class=com.example.phase5.ordinary.OrdinaryModifiersValue
"$JAVA_HOME/bin/javap" -classpath "$ordinary_output" -p "$ordinary_class" \
  >"$evidence_dir/OrdinaryModifiersValue.javap.txt"
grep -F 'public final class com.example.phase5.ordinary.OrdinaryModifiersValue' \
  "$evidence_dir/OrdinaryModifiersValue.javap.txt" >/dev/null
for field in keyed child optional; do
  grep -F "public final long $field;" \
    "$evidence_dir/OrdinaryModifiersValue.javap.txt" >/dev/null
done
grep -F \
  'public com.example.phase5.ordinary.OrdinaryModifiersValue(long, long, long);' \
  "$evidence_dir/OrdinaryModifiersValue.javap.txt" >/dev/null

for diagnostic_log in "$evidence_dir"/*.log; do
  if grep -E 'Exception in thread|^[[:space:]]+at (com\.hgtech|com\.sun\.tools)' \
    "$diagnostic_log" >/dev/null; then
    printf '%s\n' \
      "value-shape-contract: diagnostic leaked internal stack: $diagnostic_log" >&2
    exit 1
  fi
done

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
printf '%s\n' "value-shape-contract-evidence: $evidence_dir"
printf '%s\n' 'value-shape-contract: ok'
