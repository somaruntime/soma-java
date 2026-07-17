#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'fjsp-100k-benchmark: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

java_specification=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' "fjsp-100k-benchmark: expected Java 8, got $java_specification" >&2
  exit 1
fi

./mvnw -B -ntp -pl soma-benchmarks -am package

classpath="$root_dir/soma-benchmarks/target/classes"
classpath="$classpath:$root_dir/soma-examples/target/classes"
classpath="$classpath:$root_dir/soma-runtime-core/target/classes"

"$JAVA_HOME/bin/java" ${SOMA_FJSP_JVM_ARGS:--Xms2g -Xmx4g} \
  -cp "$classpath" com.hgtech.soma.benchmarks.FjspScaleBenchmark "$@"
