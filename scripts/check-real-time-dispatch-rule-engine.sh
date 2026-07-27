#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  printf '%s\n' \
    'rtd-rule-engine-check: JAVA_HOME must point to Zulu JDK 8' >&2
  exit 1
fi
java_specification=$($JAVA_HOME/bin/java \
  -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' |
  head -n 1)
java_vendor=$($JAVA_HOME/bin/java \
  -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.vendor = //p' |
  head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' \
    "rtd-rule-engine-check: expected Java 8, got $java_specification" >&2
  exit 1
fi
case "$java_vendor" in
  *Azul*) ;;
  *)
    printf '%s\n' \
      "rtd-rule-engine-check: expected Azul Zulu, got $java_vendor" >&2
    exit 1
    ;;
esac

profile=${1:-default}
case "$profile" in
  default)
    heap=256m
    baseline_version=v1
    expected_measurements=3
    expected_initial_work=256
    expected_arrivals=32
    expected_cycles=24
    expected_total_work=1024
    expected_resources=32
    expected_capabilities=8
    expected_workers=4
    ;;
  large)
    heap=512m
    baseline_version=v1
    expected_measurements=1
    expected_initial_work=5000
    expected_arrivals=500
    expected_cycles=20
    expected_total_work=15000
    expected_resources=128
    expected_capabilities=16
    expected_workers=4
    ;;
  long-run)
    heap=512m
    baseline_version=v1
    expected_measurements=1
    expected_initial_work=500
    expected_arrivals=25
    expected_cycles=500
    expected_total_work=13000
    expected_resources=64
    expected_capabilities=8
    expected_workers=4
    ;;
  *)
    printf '%s\n' \
      "rtd-rule-engine-check: unsupported profile $profile" >&2
    exit 1
    ;;
esac

application=real-time-dispatch-rule-engine
application_dir=$root_dir/soma-examples/$application
pom=$application_dir/pom.xml
benchmark_options=$application_dir/src/test/resources/benchmark/$profile.properties
minimum_forks=$(sed -n \
  's/^benchmark.forks=//p' "$benchmark_options")
forks=${2:-$minimum_forks}
case "$forks" in
  ''|*[!0-9]*)
    printf '%s\n' \
      "rtd-rule-engine-check: invalid fork count $forks" >&2
    exit 1
    ;;
esac
if [ "$forks" -lt "$minimum_forks" ]; then
  printf '%s\n' \
    "rtd-rule-engine-check: $profile requires at least $minimum_forks forks" >&2
  exit 1
fi

mkdir -p target
if [ "$#" -ge 3 ]; then
  case "$3" in
    /*) evidence_dir=$3 ;;
    *) evidence_dir=$root_dir/$3 ;;
  esac
  mkdir -p "$evidence_dir"
else
  evidence_dir=$(mktemp -d \
    "$root_dir/target/rtd-rule-engine.XXXXXX")
fi
repository=$evidence_dir/repository
application_build_dir=$evidence_dir/application-target
mkdir -p "$repository"
seed_repository=$root_dir/soma-testkit/target/phase0-m2/repository
if [ -d "$seed_repository" ]; then
  cp -R "$seed_repository/." "$repository/"
fi

./mvnw -B -ntp -Dmaven.repo.local="$repository" \
  -pl soma-runtime-core,soma-dataflow,soma-processor \
  -am install -DskipTests
./mvnw -B -ntp -Dmaven.repo.local="$repository" \
  -Dsoma.build.directory="$application_build_dir" \
  -f "$pom" clean package
if grep -R -a -F 'Unresolved compilation problem' \
    "$application_build_dir/classes" \
    "$application_build_dir/test-classes" >/dev/null; then
  printf '%s\n' \
    'rtd-rule-engine-check: compiler-error stub in isolated build' >&2
  exit 1
fi

dependency_plugin_version=$(sed -n \
  's:.*<maven.dependency.plugin.version>\([^<]*\)</maven.dependency.plugin.version>.*:\1:p' \
  "$pom")
runtime_classpath_file=$evidence_dir/runtime-classpath.txt
./mvnw -B -ntp -Dmaven.repo.local="$repository" -f "$pom" \
  "org.apache.maven.plugins:maven-dependency-plugin:$dependency_plugin_version:build-classpath" \
  -DincludeScope=runtime \
  -Dmdep.outputFile="$runtime_classpath_file"
runtime_classpath=$application_build_dir/classes:$(cat "$runtime_classpath_file")
test_classpath=$application_build_dir/test-classes:$runtime_classpath

main_root=$application_dir/src/main/java/com/hgtech/soma/examples/rtd
test_root=$application_dir/src/test/java/com/hgtech/soma/examples/rtd
for package in config dispatch feed result rule runtime schema support; do
  if [ ! -d "$main_root/$package" ]; then
    printf '%s\n' \
      "rtd-rule-engine-check: missing production package $package" >&2
    exit 1
  fi
done
for package in benchmark evidence reference validation; do
  if [ ! -d "$test_root/$package" ]; then
    printf '%s\n' \
      "rtd-rule-engine-check: missing test concern $package" >&2
    exit 1
  fi
done

if grep -R -E '^import com\.hgtech\.soma\.(annotation|runtime|dataflow)' \
    "$main_root/config" "$main_root/feed" "$main_root/support" \
    >/dev/null; then
  printf '%s\n' \
    'rtd-rule-engine-check: detached input depends on SOMA runtime' >&2
  exit 1
fi
if grep -R -E '^import com\.hgtech\.soma\.' \
    "$main_root/result" >/dev/null; then
  printf '%s\n' \
    'rtd-rule-engine-check: detached result depends on SOMA' >&2
  exit 1
fi
if grep -R -F 'SyntheticDispatchScenarioFactory' \
    "$main_root/runtime" "$main_root/rule" "$main_root/dispatch" \
    >/dev/null; then
  printf '%s\n' \
    'rtd-rule-engine-check: runtime execution calls input generator' >&2
  exit 1
fi
if grep -R -E \
    '^import com\.hgtech\.soma\.examples\.(scheduler|grassing)' \
    "$application_dir/src" >/dev/null \
    || grep -E \
      '<artifactId>(industrial-dynamic-scheduler|grassing-individual-simulation)</artifactId>' \
      "$pom" >/dev/null; then
  printf '%s\n' \
    'rtd-rule-engine-check: cross-application dependency detected' >&2
  exit 1
fi
if grep -R -E \
    'examples\.rtd\.(benchmark|evidence|reference|validation)|benchmark\.(warmup|forks|measurements)' \
    "$application_dir/src/main" >/dev/null; then
  printf '%s\n' \
    'rtd-rule-engine-check: test/evidence responsibility leaked into production' >&2
  exit 1
fi
if grep -F '<parent>' "$pom" >/dev/null \
    || grep -E \
      '<artifactId>(soma-testkit|soma-examples|examples-common)</artifactId>' \
      "$pom" >/dev/null; then
  printf '%s\n' \
    'rtd-rule-engine-check: application POM is not independent' >&2
  exit 1
fi

main_profiles=$(find "$application_dir/src/main/resources/config" \
  -type f -name '*.properties' | wc -l | tr -d ' ')
if [ "$main_profiles" -ne 1 ] \
    || [ ! -f \
      "$application_dir/src/main/resources/config/default.properties" ] \
    || grep -R -F 'benchmark.' \
      "$application_dir/src/main/resources" >/dev/null; then
  printf '%s\n' \
    'rtd-rule-engine-check: production config boundary regressed' >&2
  exit 1
fi
for resource_profile in correctness large long-run; do
  if [ ! -f \
      "$application_dir/src/test/resources/config/$resource_profile.properties" ] \
      || [ ! -f \
      "$application_dir/src/test/resources/benchmark/$resource_profile.properties" ]; then
    printf '%s\n' \
      "rtd-rule-engine-check: missing test profile $resource_profile" >&2
    exit 1
  fi
done
if [ ! -f \
    "$application_dir/src/test/resources/benchmark/default.properties" ]; then
  printf '%s\n' \
    'rtd-rule-engine-check: missing default benchmark options' >&2
  exit 1
fi

production_jar=$application_build_dir/$application-1.0.0-SNAPSHOT.jar
jar_manifest=$evidence_dir/production-jar.txt
"$JAVA_HOME/bin/jar" tf "$production_jar" >"$jar_manifest"
if grep -E \
    'rtd/(benchmark|evidence|reference|validation)/|config/(correctness|large|long-run)\.properties|benchmark/' \
    "$jar_manifest" >/dev/null; then
  printf '%s\n' \
    'rtd-rule-engine-check: production JAR contains test content' >&2
  exit 1
fi
for required in \
  com/hgtech/soma/examples/rtd/RealTimeDispatchApplication.class \
  com/hgtech/soma/examples/rtd/schema/generated/WorkStateTable.class \
  com/hgtech/soma/examples/rtd/schema/generated/ResourceStateTable.class; do
  if ! grep -F "$required" "$jar_manifest" >/dev/null; then
    printf '%s\n' \
      "rtd-rule-engine-check: production JAR lacks $required" >&2
    exit 1
  fi
done

verification_log=$evidence_dir/verification.log
: >"$verification_log"
for verification_profile in correctness "$profile"; do
  "$JAVA_HOME/bin/java" -ea -Xms512m -Xmx512m \
    -cp "$test_classpath" \
    com.hgtech.soma.examples.rtd.evidence.DispatchVerification \
    "$verification_profile" >>"$verification_log"
done
if [ "$(grep -c '^rtd-dispatch-verification:' \
    "$verification_log")" -ne 2 ] \
    || grep -v 'claimAllowed=false' "$verification_log" >/dev/null; then
  printf '%s\n' \
    'rtd-rule-engine-check: verification artifact mismatch' >&2
  exit 1
fi

"$JAVA_HOME/bin/java" -Xms256m -Xmx256m \
  -cp "$runtime_classpath" \
  com.hgtech.soma.examples.rtd.RealTimeDispatchApplication \
  default >"$evidence_dir/default-run.txt"
for field in config.checksum input.checksum result.checksum definition; do
  grep -F "$field=" "$evidence_dir/default-run.txt" >/dev/null
done
grep -F 'claimAllowed=false' "$evidence_dir/default-run.txt" \
  >/dev/null

benchmark_artifact=$evidence_dir/benchmark.jsonl
: >"$benchmark_artifact"
benchmark_commit=$(git rev-parse HEAD)
benchmark_cpu=$(./scripts/benchmark-cpu-identity.sh)
fork=1
while [ "$fork" -le "$forks" ]; do
  SOMA_BENCHMARK_COMMIT="$benchmark_commit" \
  SOMA_BENCHMARK_FORK="$fork" \
  SOMA_BENCHMARK_FORKS="$forks" \
  SOMA_BENCHMARK_CPU="$benchmark_cpu" \
  "$JAVA_HOME/bin/java" -Xms"$heap" -Xmx"$heap" \
    -cp "$test_classpath" \
    com.hgtech.soma.examples.rtd.benchmark.DispatchBenchmark \
    "$profile" "$profile" >>"$benchmark_artifact"
  fork=$((fork + 1))
done
if [ "$(wc -l <"$benchmark_artifact" | tr -d ' ')" \
    -ne "$forks" ]; then
  printf '%s\n' 'rtd-rule-engine-check: fork count mismatch' >&2
  exit 1
fi
grep -F '"artifactVersion":"rtd-dispatch-benchmark-v1"' \
  "$benchmark_artifact" >/dev/null
grep -F "\"configuredForks\":$forks" \
  "$benchmark_artifact" >/dev/null
grep -F "\"profile\":\"$profile\"" \
  "$benchmark_artifact" >/dev/null
if grep -v '"claimAllowed":false' \
    "$benchmark_artifact" >/dev/null; then
  printf '%s\n' \
    'rtd-rule-engine-check: invalid benchmark claim' >&2
  exit 1
fi

for field in \
  configChecksum inputChecksum resultChecksum schemaHash runtimePlanHash \
  definitionIdentity templateIdentity demandChecksum; do
  sed -n "s/.*\\\"$field\\\":\\\"\\([^\\\"]*\\)\\\".*/\\1/p" \
    "$benchmark_artifact" | LC_ALL=C sort -u \
    >"$evidence_dir/$field.txt"
  if [ "$(wc -l <"$evidence_dir/$field.txt" | tr -d ' ')" \
      -ne 1 ]; then
    printf '%s\n' \
      "rtd-rule-engine-check: unstable $field across forks" >&2
    exit 1
  fi
done
for field in \
  generatorVersion seed initialWork arrivalsPerCycle cycles totalWork \
  resources capabilities \
  configuredWorkers dispatchedWork pendingWork warmup measurements \
  workExecutions maximumWorkers admittedProcessingMinutes; do
  sed -n "s/.*\\\"$field\\\":\\([0-9][0-9]*\\).*/\\1/p" \
    "$benchmark_artifact" | LC_ALL=C sort -u \
    >"$evidence_dir/$field.txt"
  if [ "$(wc -l <"$evidence_dir/$field.txt" | tr -d ' ')" \
      -ne 1 ]; then
    printf '%s\n' \
      "rtd-rule-engine-check: unstable $field across forks" >&2
    exit 1
  fi
done
for expectation in \
  "initialWork:$expected_initial_work" \
  "arrivalsPerCycle:$expected_arrivals" \
  "cycles:$expected_cycles" \
  "totalWork:$expected_total_work" \
  "resources:$expected_resources" \
  "capabilities:$expected_capabilities" \
  "configuredWorkers:$expected_workers" \
  "warmup:1" \
  "measurements:$expected_measurements" \
  "workExecutions:$((expected_total_work * expected_measurements))"; do
  field=${expectation%%:*}
  expected=${expectation#*:}
  actual=$(cat "$evidence_dir/$field.txt")
  if [ "$actual" != "$expected" ]; then
    printf '%s\n' \
      "rtd-rule-engine-check: $profile $field expected $expected, got $actual" >&2
    exit 1
  fi
done
actual_workers=$(cat "$evidence_dir/maximumWorkers.txt")
if [ "$actual_workers" -le 1 ] \
    || [ "$actual_workers" -gt "$expected_workers" ]; then
  printf '%s\n' \
    "rtd-rule-engine-check: invalid parallel worker evidence $actual_workers" >&2
  exit 1
fi
fork=1
while [ "$fork" -le "$forks" ]; do
  if [ "$(grep -c "\"fork\":$fork," \
      "$benchmark_artifact")" -ne 1 ]; then
    printf '%s\n' \
      "rtd-rule-engine-check: missing or duplicate fork $fork" >&2
    exit 1
  fi
  fork=$((fork + 1))
done
while IFS= read -r record; do
  allocated=$(printf '%s\n' "$record" |
    sed -n 's/.*"callerAllocatedBytes":\([0-9][0-9]*\).*/\1/p')
  dispatch=$(printf '%s\n' "$record" |
    sed -n 's/.*"dispatchNanos":\([0-9][0-9]*\).*/\1/p')
  if [ -z "$allocated" ] || [ "$allocated" -le 0 ] \
      || [ -z "$dispatch" ] || [ "$dispatch" -le 0 ]; then
    printf '%s\n' \
      'rtd-rule-engine-check: missing allocation/time evidence' >&2
    exit 1
  fi
done <"$benchmark_artifact"

baseline=$application_dir/src/test/resources/benchmark/performance-baseline-$profile-zulu8-macos-aarch64-$baseline_version.json
if [ "${SOMA_APPLICATION_PERFORMANCE_MODE:-compare}" = calibration ]; then
  baseline_result=
else
  baseline_result=$evidence_dir/performance-baseline-result.json
  ./mvnw -B -ntp -Dmaven.repo.local="$repository" \
    -pl soma-benchmarks -am test-compile
  "$JAVA_HOME/bin/java" \
    -cp "$root_dir/soma-benchmarks/target/classes" \
    com.hgtech.soma.benchmarks.PerformanceBaselineComparator \
    "$baseline" "$baseline_result" "$benchmark_artifact"
fi

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./mvnw -version
if [ -n "$baseline_result" ]; then
  printf '%s\n' "rtd-rule-engine-baseline: $baseline_result"
else
  printf '%s\n' 'rtd-rule-engine-baseline: calibration-only'
fi
printf '%s\n' "rtd-rule-engine-benchmark: $benchmark_artifact"
printf '%s\n' "rtd-rule-engine-evidence: $evidence_dir"
printf '%s\n' \
  "rtd-rule-engine-check: profile=$profile forks=$forks ok"
