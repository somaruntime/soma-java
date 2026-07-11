#!/bin/sh

set -eu
root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  printf '%s\n' 'testkit-phase4-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

./mvnw -B -ntp -pl soma-testkit -am test-compile -DskipTests
"$JAVA_HOME/bin/java" \
  -cp soma-testkit/target/classes:soma-testkit/target/test-classes \
  com.hgtech.soma.testkit.MaterializedGraphComparatorCheck
printf '%s\n' 'testkit-phase4-check: ok'
