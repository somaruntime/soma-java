#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  printf '%s\n' 'runtime-core-phase1-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

./mvnw -B -ntp -pl soma-runtime-core -am test-compile
"$JAVA_HOME/bin/java" \
  -cp "soma-runtime-core/target/classes:soma-runtime-core/target/test-classes" \
  com.hgtech.soma.runtime.RuntimeCorePhase1Check
"$JAVA_HOME/bin/java" \
  -cp "soma-runtime-core/target/classes:soma-runtime-core/target/test-classes" \
  com.hgtech.soma.runtime.GroupedExactIndexCheck
"$JAVA_HOME/bin/java" -version

printf '%s\n' 'runtime-core-phase1-check: ok'
