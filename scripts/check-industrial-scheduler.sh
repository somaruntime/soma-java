#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  printf '%s\n' 'industrial-scheduler-check: JAVA_HOME must point to Zulu JDK 8' >&2
  exit 1
fi
java_specification=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
java_vendor=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.vendor = //p' | head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' "industrial-scheduler-check: expected Java 8, got $java_specification" >&2
  exit 1
fi
case "$java_vendor" in
  *Azul*) ;;
  *)
    printf '%s\n' "industrial-scheduler-check: expected Azul Zulu, got $java_vendor" >&2
    exit 1
    ;;
esac

profile=${1:-default}
case "$profile" in
  default)
    heap=256m
    baseline_version=v2
    expected_measurements=3
    expected_jobs=10
    expected_operations=1000
    expected_machines=10
    expected_candidates=3
    ;;
  large)
    heap=512m
    baseline_version=v1
    expected_measurements=1
    expected_jobs=1000
    expected_operations=100000
    expected_machines=100
    expected_candidates=3
    ;;
  long-run)
    heap=256m
    baseline_version=v1
    expected_measurements=1
    expected_jobs=100
    expected_operations=10000
    expected_machines=100
    expected_candidates=3
    ;;
  *)
    printf '%s\n' \
      "industrial-scheduler-check: unsupported profile $profile" >&2
    exit 1
    ;;
esac

application=industrial-dynamic-scheduler
application_dir=$root_dir/soma-examples/$application
pom=$application_dir/pom.xml
benchmark_options=$application_dir/src/test/resources/benchmark/$profile.properties
minimum_forks=$(sed -n 's/^benchmark.forks=//p' "$benchmark_options")
forks=${2:-$minimum_forks}
case "$forks" in
  ''|*[!0-9]*)
    printf '%s\n' \
      "industrial-scheduler-check: invalid fork count $forks" >&2
    exit 1
    ;;
esac
if [ "$forks" -lt "$minimum_forks" ]; then
  printf '%s\n' \
    "industrial-scheduler-check: $profile requires at least $minimum_forks forks" >&2
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
  evidence_dir=$(mktemp -d "$root_dir/target/industrial-scheduler.XXXXXX")
fi
repository=$evidence_dir/repository
mkdir -p "$repository"
application_build_dir=$evidence_dir/application-target
seed_repository=$root_dir/soma-testkit/target/phase0-m2/repository
if [ -d "$seed_repository" ]; then
  cp -R "$seed_repository/." "$repository/"
fi

./mvnw -B -ntp -Dmaven.repo.local="$repository" \
  -pl soma-runtime-core,soma-processor -am install -DskipTests
./mvnw -B -ntp -Dmaven.repo.local="$repository" \
  -Dsoma.build.directory="$application_build_dir" \
  -f "$pom" clean package
if grep -R -a -F 'Unresolved compilation problem' \
    "$application_build_dir/classes" "$application_build_dir/test-classes" \
    >/dev/null; then
  printf '%s\n' \
    'industrial-scheduler-check: compiler-error stub found in isolated build' >&2
  exit 1
fi

dependency_plugin_version=$(sed -n \
  's:.*<maven.dependency.plugin.version>\([^<]*\)</maven.dependency.plugin.version>.*:\1:p' \
  "$pom")
runtime_classpath_file=$evidence_dir/runtime-classpath.txt
./mvnw -B -ntp -Dmaven.repo.local="$repository" -f "$pom" \
  "org.apache.maven.plugins:maven-dependency-plugin:$dependency_plugin_version:build-classpath" \
  -DincludeScope=runtime -Dmdep.outputFile="$runtime_classpath_file"
runtime_classpath=$application_build_dir/test-classes:$application_build_dir/classes:$(cat "$runtime_classpath_file")

main_root=$application_dir/src/main/java/com/hgtech/soma/examples/scheduler
test_root=$application_dir/src/test/java/com/hgtech/soma/examples/scheduler
for package in application config problem solver runtime result schema; do
  if [ ! -d "$main_root/$package" ]; then
    printf '%s\n' \
      "industrial-scheduler-check: missing production package $package" >&2
    exit 1
  fi
done
for package in benchmark fixture oracle verification; do
  if [ ! -d "$test_root/$package" ]; then
    printf '%s\n' \
      "industrial-scheduler-check: missing test concern $package" >&2
    exit 1
  fi
done

if grep -R -E 'com\.hgtech\.soma\.(runtime|examples\.scheduler\.schema\.generated)' \
    "$application_dir/src/main/java/com/hgtech/soma/examples/scheduler/config" \
    "$application_dir/src/main/java/com/hgtech/soma/examples/scheduler/problem" \
    "$application_dir/src/main/java/com/hgtech/soma/examples/scheduler/support" \
    >/dev/null; then
  printf '%s\n' 'industrial-scheduler-check: generator/input depends on SOMA runtime' >&2
  exit 1
fi
if grep -R -E \
    '^import com\.hgtech\.soma\.examples\.scheduler\.(runtime|schema)' \
    "$main_root/application" \
    >/dev/null; then
  printf '%s\n' \
    'industrial-scheduler-check: application bypasses solver facade' >&2
  exit 1
fi
if grep -R -E \
    '^import com\.hgtech\.soma\.examples\.scheduler\.(benchmark|fixture|oracle|verification)' \
    "$main_root/runtime" "$main_root/solver" \
    >/dev/null; then
  printf '%s\n' \
    'industrial-scheduler-check: production depends on test/evidence' >&2
  exit 1
fi
if grep -R -E \
    '^import com\.hgtech\.soma\.examples\.scheduler\.(application|config|result|solver)' \
    "$main_root/runtime" \
    >/dev/null \
    || grep -R -E \
      '^import com\.hgtech\.soma\.examples\.scheduler\.(application|config|runtime|schema|solver)' \
      "$main_root/result" \
      >/dev/null \
    || grep -R -E \
      '^import com\.hgtech\.soma\.examples\.scheduler\.(application|benchmark|config|fixture|oracle|verification)' \
      "$main_root/solver" \
      >/dev/null \
    || grep -R -E \
      '^import com\.hgtech\.soma\.examples\.scheduler\.(application|config|problem|result|runtime|solver|support)' \
      "$main_root/schema" \
      >/dev/null; then
  printf '%s\n' \
    'industrial-scheduler-check: production package DAG regressed' >&2
  exit 1
fi
if grep -R -F 'SyntheticSchedulingProblemFactory' \
    "$main_root/runtime" \
    >/dev/null; then
  printf '%s\n' 'industrial-scheduler-check: runtime calls the input generator' >&2
  exit 1
fi
if grep -R -F 'benchmark.' "$main_root" \
    >/dev/null; then
  printf '%s\n' \
    'industrial-scheduler-check: benchmark options leaked into production' >&2
  exit 1
fi
if find "$main_root" -type f \( \
    -name '*Fixtures.java' -o -name '*Oracle.java' \
    -o -name '*RuntimeChecks.java' -o -name 'JvmMetrics.java' \
    -o -name 'SchedulerVerification.java' \
    -o -name 'SchedulerBenchmark.java' \) | grep . >/dev/null; then
  printf '%s\n' \
    'industrial-scheduler-check: evidence source remains in production' >&2
  exit 1
fi
if grep -R -E \
    'com\.hgtech\.soma\.examples\.scheduler\.state|SchedulerConfig|SchedulingProblemGenerator|SchedulerRuntimeBootstrap|IndustrialScheduler|runtime\.ScheduleResult' \
    "$application_dir/src" >/dev/null; then
  printf '%s\n' \
    'industrial-scheduler-check: retired package or type identity remains' >&2
  exit 1
fi

jar_manifest=$evidence_dir/production-jar.txt
jar tf "$application_build_dir/industrial-dynamic-scheduler-1.0.0-SNAPSHOT.jar" \
  >"$jar_manifest"
if grep -E \
    '/(benchmark|fixture|oracle|verification)/|SchedulerRuntimeTestAccess|JvmMetrics|SchedulingProblemFixtures|TinyScheduleOracle' \
    "$jar_manifest" >/dev/null; then
  printf '%s\n' \
    'industrial-scheduler-check: production JAR contains evidence classes' >&2
  exit 1
fi

verification_log=$evidence_dir/verification.log
: >"$verification_log"
for verification_profile in correctness "$profile"; do
  "$JAVA_HOME/bin/java" -Xms512m -Xmx512m -cp "$runtime_classpath" \
    com.hgtech.soma.examples.scheduler.verification.SchedulerVerification \
    "$verification_profile" >>"$verification_log"
done
if [ "$(grep -c '^scheduler-verification:' "$verification_log")" -ne 2 ] \
    || grep -v 'claimAllowed=false' "$verification_log" >/dev/null; then
  printf '%s\n' 'industrial-scheduler-check: verification artifact mismatch' >&2
  exit 1
fi

"$JAVA_HOME/bin/java" -Xms256m -Xmx256m -cp "$runtime_classpath" \
  com.hgtech.soma.examples.scheduler.application.SchedulerApplication default \
  >"$evidence_dir/default-run.txt"
grep -F 'claimAllowed=false' "$evidence_dir/default-run.txt" >/dev/null
grep -F 'config.checksum=' "$evidence_dir/default-run.txt" >/dev/null
grep -F 'input.checksum=' "$evidence_dir/default-run.txt" >/dev/null
grep -F 'result.checksum=' "$evidence_dir/default-run.txt" >/dev/null

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
  "$JAVA_HOME/bin/java" -Xms"$heap" -Xmx"$heap" -cp "$runtime_classpath" \
    com.hgtech.soma.examples.scheduler.benchmark.SchedulerBenchmark \
    "$profile" "$profile" \
    >>"$benchmark_artifact"
  fork=$((fork + 1))
done
if [ "$(wc -l <"$benchmark_artifact" | tr -d ' ')" -ne "$forks" ]; then
  printf '%s\n' 'industrial-scheduler-check: fork count mismatch' >&2
  exit 1
fi
grep -F '"artifactVersion":"industrial-scheduler-benchmark-v3"' \
    "$benchmark_artifact" >/dev/null
grep -F "\"configuredForks\":$forks" "$benchmark_artifact" >/dev/null
grep -F "\"profile\":\"$profile\"" "$benchmark_artifact" >/dev/null
if grep -v '"claimAllowed":false' "$benchmark_artifact" >/dev/null; then
  printf '%s\n' 'industrial-scheduler-check: invalid benchmark claim' >&2
  exit 1
fi
for field in inputChecksum resultChecksum schemaHash runtimePlanHash; do
  sed -n "s/.*\\\"$field\\\":\\\"\\([^\\\"]*\\)\\\".*/\\1/p" \
    "$benchmark_artifact" | LC_ALL=C sort -u >"$evidence_dir/$field.txt"
  if [ "$(wc -l <"$evidence_dir/$field.txt" | tr -d ' ')" -ne 1 ]; then
    printf '%s\n' "industrial-scheduler-check: unstable $field across forks" >&2
    exit 1
  fi
done
for field in jobs operations machines candidatesPerOperation \
  warmup measurements operationExecutions frontierCapacity; do
  sed -n "s/.*\\\"$field\\\":\\([0-9][0-9]*\\).*/\\1/p" \
    "$benchmark_artifact" | LC_ALL=C sort -u >"$evidence_dir/$field.txt"
  if [ "$(wc -l <"$evidence_dir/$field.txt" | tr -d ' ')" -ne 1 ]; then
    printf '%s\n' \
      "industrial-scheduler-check: unstable $field across forks" >&2
    exit 1
  fi
done
for expectation in \
  "jobs:$expected_jobs" \
  "operations:$expected_operations" \
  "machines:$expected_machines" \
  "candidatesPerOperation:$expected_candidates" \
  "warmup:1" \
  "measurements:$expected_measurements" \
  "operationExecutions:$((expected_operations * expected_measurements))"; do
  field=${expectation%%:*}
  expected=${expectation#*:}
  actual=$(cat "$evidence_dir/$field.txt")
  if [ "$actual" != "$expected" ]; then
    printf '%s\n' \
      "industrial-scheduler-check: $profile $field expected $expected, got $actual" >&2
    exit 1
  fi
done
fork=1
while [ "$fork" -le "$forks" ]; do
  if [ "$(grep -c "\"fork\":$fork," "$benchmark_artifact")" -ne 1 ]; then
    printf '%s\n' \
      "industrial-scheduler-check: missing or duplicate fork $fork" >&2
    exit 1
  fi
  fork=$((fork + 1))
done
while IFS= read -r record; do
  allocated=$(printf '%s\n' "$record" |
    sed -n 's/.*"allocatedBytes":\([0-9][0-9]*\).*/\1/p')
  solve=$(printf '%s\n' "$record" |
    sed -n 's/.*"solveNanos":\([0-9][0-9]*\).*/\1/p')
  if [ -z "$allocated" ] || [ "$allocated" -le 0 ] \
      || [ -z "$solve" ] || [ "$solve" -le 0 ]; then
    printf '%s\n' 'industrial-scheduler-check: missing allocation/time evidence' >&2
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
  printf '%s\n' "industrial-scheduler-baseline: $baseline_result"
else
  printf '%s\n' 'industrial-scheduler-baseline: calibration-only'
fi
printf '%s\n' "industrial-scheduler-benchmark: $benchmark_artifact"
printf '%s\n' "industrial-scheduler-evidence: $evidence_dir"
printf '%s\n' "industrial-scheduler-check: profile=$profile forks=$forks ok"
