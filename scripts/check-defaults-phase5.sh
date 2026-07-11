#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'defaults-phase5-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

annotations_jar=soma-annotations/target/soma-annotations-0.1.0-SNAPSHOT.jar
processor_jar=soma-processor/target/soma-processor-0.1.0-SNAPSHOT.jar
runtime_jar=soma-runtime-core/target/soma-runtime-core-0.1.0-SNAPSHOT.jar
fixture=soma-testkit/src/test/fixtures/compiler/defaults-invalid-phase5
mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/defaults-phase5.XXXXXX")
classes=$evidence_dir/classes
log=$evidence_dir/javac.log
mkdir -p "$classes"

if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$annotations_jar:$processor_jar:$runtime_jar" \
  -processorpath "$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor \
  -Xplugin:SomaValue \
  -d "$classes" \
  $(find "$fixture/src" -type f -name '*.java' | sort) \
  >"$log" 2>&1; then
  printf '%s\n' 'defaults-phase5-check: invalid default matrix unexpectedly compiled' >&2
  exit 1
fi

grep -F '[SOMA-TABLE-005] @SomaDefault is allowed only on required @SomaField' "$log" >/dev/null
grep -F '[SOMA-TABLE-005] @SomaDefault does not support an outer value field' "$log" >/dev/null
grep -F '[SOMA-TABLE-005] invalid schema default for state: UNKNOWN' "$log" >/dev/null
grep -F '[SOMA-TABLE-005] invalid schema default for score: NaN' "$log" >/dev/null
grep -F '[SOMA-TABLE-005] non-finite value default on strict selector path: value.x' "$log" >/dev/null
grep -F '[SOMA-TABLE-008] value key path cannot depend on @SomaDefault' "$log" >/dev/null

if grep -E 'Exception in thread|^[[:space:]]+at (com\.hgtech|com\.sun\.tools)' "$log" >/dev/null; then
  printf '%s\n' 'defaults-phase5-check: diagnostic leaked internal stack' >&2
  exit 1
fi

"$JAVA_HOME/bin/java" -version >"$evidence_dir/java-version.txt" 2>&1
"$JAVA_HOME/bin/javac" -version >"$evidence_dir/javac-version.txt" 2>&1
uname -a >"$evidence_dir/uname.txt"
printf '%s\n' "defaults-phase5-evidence: $evidence_dir"
printf '%s\n' 'defaults-phase5-check: ok'
