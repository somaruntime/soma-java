#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ] \
    || [ ! -x "$JAVA_HOME/bin/javap" ]; then
  printf '%s\n' 'benchmark-smoke-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

java_specification=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' "benchmark-smoke-check: expected Java 8, got $java_specification" >&2
  exit 1
fi

mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/benchmark-smoke.XXXXXX")
commit=$(git rev-parse HEAD)
cpu_identity=$(uname -m)

./mvnw -B -ntp -pl soma-benchmarks -am clean test-compile

classpath="soma-benchmarks/target/classes:soma-benchmarks/target/test-classes:soma-runtime-core/target/classes:soma-examples/target/classes"
artifact=$evidence_dir/benchmark-smoke.jsonl
repeat_artifact=$evidence_dir/benchmark-smoke-repeat.jsonl

"$JAVA_HOME/bin/java" -cp "$classpath" \
  com.hgtech.soma.benchmarks.BenchmarkArtifactCheck "$evidence_dir/negative"
SOMA_BENCHMARK_CPU="$cpu_identity" "$JAVA_HOME/bin/java" -cp "$classpath" \
  com.hgtech.soma.benchmarks.BenchmarkSmokeRunner \
  --output "$artifact" --commit "$commit" --scale smoke \
  --rows 128 --seed 1397706049 --warmup 1 --forks 1 --measurements 2
"$JAVA_HOME/bin/java" -cp "$classpath" \
  com.hgtech.soma.benchmarks.BenchmarkArtifactValidator "$artifact"

SOMA_BENCHMARK_CPU="$cpu_identity" "$JAVA_HOME/bin/java" -cp "$classpath" \
  com.hgtech.soma.benchmarks.BenchmarkSmokeRunner \
  --output "$repeat_artifact" --commit "$commit" --scale smoke \
  --rows 128 --seed 1397706049 --warmup 1 --forks 1 --measurements 2
"$JAVA_HOME/bin/java" -cp "$classpath" \
  com.hgtech.soma.benchmarks.BenchmarkArtifactValidator "$repeat_artifact"

sed -n 's/.*"lane":"\([^"]*\)".*/\1/p' "$artifact" >"$evidence_dir/lanes.txt"
sed -n 's/.*"lane":"\([^"]*\)".*/\1/p' "$repeat_artifact" \
  >"$evidence_dir/repeat-lanes.txt"
cmp "$evidence_dir/lanes.txt" "$evidence_dir/repeat-lanes.txt"

record_count=$(wc -l <"$artifact" | tr -d ' ')
if [ "$record_count" -ne 20 ]; then
  printf '%s\n' "benchmark-smoke-check: expected 20 records, got $record_count" >&2
  exit 1
fi
if grep -F '"claimAllowed":true' "$artifact" >/dev/null \
    || grep -v -F '"claimAllowed":false' "$artifact" >/dev/null \
    || grep -v -F '"level":"smoke"' "$artifact" >/dev/null \
    || grep -v -F '"status":"passed"' "$artifact" >/dev/null; then
  printf '%s\n' 'benchmark-smoke-check: invalid smoke claim/status record' >&2
  exit 1
fi
for expected in \
  '"schemaVersion":"soma-benchmark-smoke-v4"' \
  '"artifactVersion":"soma-java-benchmark-runner-v4"' \
  '"scale":{"preset":"smoke","rows":128' \
  '"seed":1397706049' \
  '"warmupIterations":1' \
  '"forks":1' \
  '"measurementIterations":2'; do
  if grep -v -F "$expected" "$artifact" >/dev/null \
      || grep -v -F "$expected" "$repeat_artifact" >/dev/null; then
    printf '%s\n' "benchmark-smoke-check: missing deterministic metadata $expected" >&2
    exit 1
  fi
done

if grep -v -F '"allocatedBytes":null' "$artifact" >/dev/null \
    || grep -v -F '"allocationPerOperation":{"method":"not-observed","estimatedBytes":null}' \
      "$artifact" >/dev/null \
    || grep -v -F '"observationKinds":' "$artifact" >/dev/null; then
  printf '%s\n' 'benchmark-smoke-check: v4 observation semantics missing' >&2
  exit 1
fi

if "$JAVA_HOME/bin/java" -cp "$classpath" \
    com.hgtech.soma.benchmarks.BenchmarkSmokeRunner --forks 2 \
    >"$evidence_dir/invalid-forks.log" 2>&1; then
  printf '%s\n' 'benchmark-smoke-check: multi-fork smoke unexpectedly accepted' >&2
  exit 1
fi
if "$JAVA_HOME/bin/java" -cp "$classpath" \
    com.hgtech.soma.benchmarks.BenchmarkSmokeRunner --unknown value \
    >"$evidence_dir/invalid-option.log" 2>&1; then
  printf '%s\n' 'benchmark-smoke-check: unknown CLI option unexpectedly accepted' >&2
  exit 1
fi

shasum -a 256 "$artifact" \
  soma-benchmarks/src/main/resources/META-INF/soma/benchmark-smoke-schema-v4.json \
  >"$evidence_dir/checksums.sha256"
find soma-benchmarks/src -type f | LC_ALL=C sort >"$evidence_dir/implementation-files.txt"
printf '%s\n' \
  soma-benchmarks/README.md \
  soma-benchmarks/docs/README.md \
  soma-benchmarks/docs/benchmark-evidence-contract.md \
  scripts/check-benchmark-smoke.sh \
  >>"$evidence_dir/implementation-files.txt"
while IFS= read -r implementation_file; do
  shasum -a 256 "$implementation_file"
done <"$evidence_dir/implementation-files.txt" \
  >"$evidence_dir/implementation-checksums.sha256"

cmp soma-benchmarks/src/main/resources/META-INF/soma/benchmark-smoke-schema-v4.json \
  soma-benchmarks/target/classes/META-INF/soma/benchmark-smoke-schema-v4.json

for class_name in BenchmarkSmokeRunner BenchmarkArtifactValidator; do
  major=$($JAVA_HOME/bin/javap -classpath soma-benchmarks/target/classes -verbose \
    "com.hgtech.soma.benchmarks.$class_name" |
    sed -n 's/^[[:space:]]*major version: //p' | head -n 1)
  if [ "$major" != '52' ]; then
    printf '%s\n' "benchmark-smoke-check: expected Java 8 major 52 for $class_name" >&2
    exit 1
  fi
done

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./mvnw -version
uname -srm
printf '%s\n' "benchmark-smoke-evidence: $evidence_dir"
printf '%s\n' 'benchmark-smoke-check: ok'
