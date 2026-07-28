#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  printf '%s\n' \
    'runtime-contracts: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

./mvnw -B -ntp -pl soma-runtime-core -am test-compile

classpath="soma-runtime-core/target/classes:soma-runtime-core/target/test-classes"
for check_class in \
  RuntimePlanAndMetadataContractCheck \
  RuntimeGroupAndOwnershipContractCheck \
  RuntimeStorageAndAccessContractCheck \
  RuntimeResourceAndFailureContractCheck \
  PrimaryLocatorContractCheck \
  GroupedExactIndexContractCheck
do
  "$JAVA_HOME/bin/java" -cp "$classpath" \
    "io.github.somaruntime.soma.runtime.$check_class"
done

"$JAVA_HOME/bin/java" -version
printf '%s\n' 'runtime-contracts: ok'
