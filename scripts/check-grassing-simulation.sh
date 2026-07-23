#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  printf '%s\n' 'grassing-simulation-check: JAVA_HOME must point to Zulu JDK 8' >&2
  exit 1
fi
java_specification=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
java_vendor=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.vendor = //p' | head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' \
    "grassing-simulation-check: expected Java 8, got $java_specification" >&2
  exit 1
fi
case "$java_vendor" in
  *Azul*) ;;
  *)
    printf '%s\n' \
      "grassing-simulation-check: expected Azul Zulu, got $java_vendor" >&2
    exit 1
    ;;
esac

application=grassing-individual-simulation
application_dir=$root_dir/soma-examples/$application
pom=$application_dir/pom.xml
mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/grassing-simulation.XXXXXX")
repository=$evidence_dir/repository
mkdir -p "$repository"
seed_repository=$root_dir/soma-testkit/target/phase0-m2/repository
if [ -d "$seed_repository" ]; then
  cp -R "$seed_repository/." "$repository/"
fi

./mvnw -B -ntp -Dmaven.repo.local="$repository" \
  -pl soma-runtime-core,soma-processor -am install -DskipTests
./mvnw -B -ntp -Dmaven.repo.local="$repository" \
  -f "$pom" clean package

dependency_plugin_version=$(sed -n \
  's:.*<maven.dependency.plugin.version>\([^<]*\)</maven.dependency.plugin.version>.*:\1:p' \
  "$pom")
runtime_classpath_file=$evidence_dir/runtime-classpath.txt
./mvnw -B -ntp -Dmaven.repo.local="$repository" -f "$pom" \
  "org.apache.maven.plugins:maven-dependency-plugin:$dependency_plugin_version:build-classpath" \
  -DincludeScope=runtime -Dmdep.outputFile="$runtime_classpath_file"
runtime_classpath=$application_dir/target/classes:$(cat "$runtime_classpath_file")

if grep -R -E \
    'com\.hgtech\.soma\.(runtime|examples\.grassing\.state\.generated)' \
    "$application_dir/src/main/java/com/hgtech/soma/examples/grassing/config" \
    "$application_dir/src/main/java/com/hgtech/soma/examples/grassing/model" \
    "$application_dir/src/main/java/com/hgtech/soma/examples/grassing/support" \
    >/dev/null; then
  printf '%s\n' \
    'grassing-simulation-check: config/generator input depends on SOMA runtime' >&2
  exit 1
fi
if grep -R -F 'InitialStateGenerator' \
    "$application_dir/src/main/java/com/hgtech/soma/examples/grassing/runtime" \
    >/dev/null; then
  printf '%s\n' \
    'grassing-simulation-check: runtime calls the initial-state generator' >&2
  exit 1
fi

verification_log=$evidence_dir/verification.log
for profile in correctness default large long-run; do
  "$JAVA_HOME/bin/java" -Xms512m -Xmx512m -cp "$runtime_classpath" \
    com.hgtech.soma.examples.grassing.evidence.SimulationVerification \
    "$profile" >>"$verification_log"
done
if [ "$(grep -c '^simulation-verification:' "$verification_log")" -ne 4 ] \
    || grep -v 'claimAllowed=false' "$verification_log" >/dev/null; then
  printf '%s\n' \
    'grassing-simulation-check: verification artifact mismatch' >&2
  exit 1
fi
for profile in default large long-run; do
  record=$(grep "^simulation-verification: profile=$profile " \
    "$verification_log")
  population=$(printf '%s\n' "$record" |
    sed -n 's/.* population=\([0-9][0-9]*\) .*/\1/p')
  births=$(printf '%s\n' "$record" |
    sed -n 's/.* births=\([0-9][0-9]*\) .*/\1/p')
  deaths=$(printf '%s\n' "$record" |
    sed -n 's/.* deaths=\([0-9][0-9]*\) .*/\1/p')
  if [ -z "$population" ] || [ "$population" -le 0 ] \
      || [ -z "$births" ] || [ "$births" -le 0 ] \
      || [ -z "$deaths" ] || [ "$deaths" -le 0 ]; then
    printf '%s\n' \
      "grassing-simulation-check: $profile does not sustain birth/death churn" >&2
    exit 1
  fi
done

"$JAVA_HOME/bin/java" -Xms256m -Xmx256m -cp "$runtime_classpath" \
  com.hgtech.soma.examples.grassing.SimulationApplication default \
  >"$evidence_dir/default-run.txt"
grep -F 'claimAllowed=false' "$evidence_dir/default-run.txt" >/dev/null
grep -F 'config.checksum=' "$evidence_dir/default-run.txt" >/dev/null
grep -F 'input.checksum=' "$evidence_dir/default-run.txt" >/dev/null
grep -F 'result.checksum=' "$evidence_dir/default-run.txt" >/dev/null

forks=$(sed -n 's/^benchmark.forks=//p' \
  "$application_dir/src/main/resources/config/default.properties")
benchmark_artifact=$evidence_dir/benchmark.jsonl
fork=1
while [ "$fork" -le "$forks" ]; do
  "$JAVA_HOME/bin/java" -Xms256m -Xmx256m -cp "$runtime_classpath" \
    com.hgtech.soma.examples.grassing.evidence.SimulationBenchmark default \
    >>"$benchmark_artifact"
  fork=$((fork + 1))
done
if [ "$(wc -l <"$benchmark_artifact" | tr -d ' ')" -ne "$forks" ]; then
  printf '%s\n' 'grassing-simulation-check: fork count mismatch' >&2
  exit 1
fi
grep -F '"artifact":"grassing-simulation-benchmark-v1"' \
  "$benchmark_artifact" >/dev/null
if grep -v '"claimAllowed":false' "$benchmark_artifact" >/dev/null; then
  printf '%s\n' 'grassing-simulation-check: invalid benchmark claim' >&2
  exit 1
fi
for field in inputChecksum resultChecksum schemaHash runtimePlanHash; do
  sed -n "s/.*\\\"$field\\\":\\\"\\([^\\\"]*\\)\\\".*/\\1/p" \
    "$benchmark_artifact" | LC_ALL=C sort -u >"$evidence_dir/$field.txt"
  if [ "$(wc -l <"$evidence_dir/$field.txt" | tr -d ' ')" -ne 1 ]; then
    printf '%s\n' \
      "grassing-simulation-check: unstable $field across forks" >&2
    exit 1
  fi
done
while IFS= read -r record; do
  allocated=$(printf '%s\n' "$record" |
    sed -n 's/.*"allocatedBytes":\([0-9][0-9]*\).*/\1/p')
  ticks=$(printf '%s\n' "$record" |
    sed -n 's/.*"ticks":\([0-9][0-9]*\).*/\1/p')
  measurements=$(printf '%s\n' "$record" |
    sed -n 's/.*"measurements":\([0-9][0-9]*\).*/\1/p')
  elapsed=$(printf '%s\n' "$record" |
    sed -n 's/.*"tickNanos":\([0-9][0-9]*\).*/\1/p')
  growth=$(printf '%s\n' "$record" |
    sed -n 's/.*"populationGrowthCount":\([0-9][0-9]*\).*/\1/p')
  if [ -z "$allocated" ] || [ "$allocated" -le 0 ] \
      || [ -z "$elapsed" ] || [ "$elapsed" -le 0 ] \
      || [ -z "$growth" ] || [ "$growth" -le 0 ]; then
    printf '%s\n' \
      'grassing-simulation-check: missing allocation/time/growth evidence' >&2
    exit 1
  fi
  measured_ticks=$((ticks * measurements))
  if [ "$measured_ticks" -le 0 ] \
      || [ $((allocated / measured_ticks)) -gt 65536 ]; then
    printf '%s\n' \
      'grassing-simulation-check: allocation exceeds 64 KiB per measured tick' >&2
    exit 1
  fi
done <"$benchmark_artifact"

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./mvnw -version
printf '%s\n' "grassing-simulation-benchmark: $benchmark_artifact"
printf '%s\n' "grassing-simulation-evidence: $evidence_dir"
printf '%s\n' 'grassing-simulation-check: ok'
