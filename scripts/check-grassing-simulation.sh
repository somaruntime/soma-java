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

profile=${1:-default}
case "$profile" in
  default)
    heap=256m
    baseline_version=v3
    expected_measurements=3
    expected_width=128
    expected_height=72
    expected_population=1000
    expected_ticks=1000
    ;;
  large)
    heap=256m
    baseline_version=v2
    expected_measurements=1
    expected_width=1280
    expected_height=720
    expected_population=100000
    expected_ticks=1000
    ;;
  long-run)
    heap=256m
    baseline_version=v2
    expected_measurements=1
    expected_width=400
    expected_height=225
    expected_population=10000
    expected_ticks=10000
    ;;
  *)
    printf '%s\n' \
      "grassing-simulation-check: unsupported profile $profile" >&2
    exit 1
    ;;
esac

application=grassing-individual-simulation
application_dir=$root_dir/soma-examples/$application
pom=$application_dir/pom.xml
benchmark_options=$application_dir/src/test/resources/benchmark/$profile.properties
minimum_forks=$(sed -n 's/^benchmark.forks=//p' "$benchmark_options")
forks=${2:-$minimum_forks}
case "$forks" in
  ''|*[!0-9]*)
    printf '%s\n' \
      "grassing-simulation-check: invalid fork count $forks" >&2
    exit 1
    ;;
esac
if [ "$forks" -lt "$minimum_forks" ]; then
  printf '%s\n' \
    "grassing-simulation-check: $profile requires at least $minimum_forks forks" >&2
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
  evidence_dir=$(mktemp -d "$root_dir/target/grassing-simulation.XXXXXX")
fi
repository=$evidence_dir/repository
mkdir -p "$repository"
application_build_dir=$evidence_dir/application-target
seed_repository=${SOMA_MAVEN_EVIDENCE_REPOSITORY:-$root_dir/target/evidence-m2/repository}
if [ -d "$seed_repository" ]; then
  cp -R "$seed_repository/." "$repository/"
fi

./mvnw -B -ntp -Dmaven.repo.local="$repository" \
  -pl soma-runtime-core,soma-dataflow,soma-processor -am install -DskipTests
./mvnw -B -ntp -Dmaven.repo.local="$repository" \
  -Dsoma.build.directory="$application_build_dir" \
  -f "$pom" clean package
if grep -R -a -F 'Unresolved compilation problem' \
    "$application_build_dir/classes" "$application_build_dir/test-classes" \
    >/dev/null; then
  printf '%s\n' \
    'grassing-simulation-check: compiler-error stub found in isolated build' >&2
  exit 1
fi

dependency_plugin_version=$(sed -n \
  's:.*<maven.dependency.plugin.version>\([^<]*\)</maven.dependency.plugin.version>.*:\1:p' \
  "$pom")
runtime_classpath_file=$evidence_dir/runtime-classpath.txt
./mvnw -B -ntp -Dmaven.repo.local="$repository" -f "$pom" \
  "org.apache.maven.plugins:maven-dependency-plugin:$dependency_plugin_version:build-classpath" \
  -DincludeScope=runtime -Dmdep.outputFile="$runtime_classpath_file"
runtime_classpath=$application_build_dir/classes:$(cat "$runtime_classpath_file")
test_classpath=$application_build_dir/test-classes:$runtime_classpath

for package in config scenario simulation result runtime schema support; do
  if [ ! -d \
      "$application_dir/src/main/java/io/github/somaruntime/soma/examples/grassing/$package" ]; then
    printf '%s\n' \
      "grassing-simulation-check: missing production package $package" >&2
    exit 1
  fi
done
for retired in model state evidence validation; do
  if find \
      "$application_dir/src/main/java/io/github/somaruntime/soma/examples/grassing/$retired" \
      -type f -print 2>/dev/null | grep . >/dev/null; then
    printf '%s\n' \
      "grassing-simulation-check: retired production package remains: $retired" >&2
    exit 1
  fi
done
if grep -R -E '^import io\.github\.somaruntime\.soma\.(runtime|examples\.grassing\.schema\.generated)' \
    "$application_dir/src/main/java/io/github/somaruntime/soma/examples/grassing/config" \
    "$application_dir/src/main/java/io/github/somaruntime/soma/examples/grassing/scenario" \
    "$application_dir/src/main/java/io/github/somaruntime/soma/examples/grassing/support" \
    >/dev/null; then
  printf '%s\n' \
    'grassing-simulation-check: detached input depends on SOMA runtime' >&2
  exit 1
fi
if grep -R -E \
    '^import io\.github\.somaruntime\.soma\.examples\.grassing\.(runtime|schema|simulation|result|scenario)' \
    "$application_dir/src/main/java/io/github/somaruntime/soma/examples/grassing/config" \
    >/dev/null \
    || grep -R -E \
      '^import io\.github\.somaruntime\.soma\.examples\.grassing\.(runtime|schema|simulation|result)' \
      "$application_dir/src/main/java/io/github/somaruntime/soma/examples/grassing/scenario" \
      >/dev/null \
    || grep -R -E '^import io\.github\.somaruntime\.soma\.' \
      "$application_dir/src/main/java/io/github/somaruntime/soma/examples/grassing/support" \
      >/dev/null \
    || grep -R -E \
      '^import io\.github\.somaruntime\.soma\.(runtime|examples\.grassing)' \
      "$application_dir/src/main/java/io/github/somaruntime/soma/examples/grassing/schema" \
      >/dev/null \
    || grep -R -E \
      '^import io\.github\.somaruntime\.soma\.examples\.grassing\.(application|simulation|evidence|validation)' \
      "$application_dir/src/main/java/io/github/somaruntime/soma/examples/grassing/runtime" \
      >/dev/null \
    || grep -R -E \
      '^import io\.github\.somaruntime\.soma\.examples\.grassing\.(config|schema|support|evidence|validation)' \
      "$application_dir/src/main/java/io/github/somaruntime/soma/examples/grassing/simulation" \
      >/dev/null; then
  printf '%s\n' \
    'grassing-simulation-check: production package DAG regressed' >&2
  exit 1
fi
if grep -R -E '^import io\.github\.somaruntime\.soma\.' \
    "$application_dir/src/main/java/io/github/somaruntime/soma/examples/grassing/result" \
    >/dev/null; then
  printf '%s\n' \
    'grassing-simulation-check: detached result depends on SOMA' >&2
  exit 1
fi
if grep -E '^import io\.github\.somaruntime\.soma\.examples\.grassing\.(runtime|schema)' \
    "$application_dir/src/main/java/io/github/somaruntime/soma/examples/grassing/SimulationApplication.java" \
    >/dev/null; then
  printf '%s\n' \
    'grassing-simulation-check: application bypasses the simulation facade' >&2
  exit 1
fi
if grep -R -E \
    'examples\.grassing\.(evidence|validation)|SimulationRuntime(TestAccess|Checks)|benchmark\.(warmup|forks|measurements)' \
    "$application_dir/src/main/java" >/dev/null; then
  printf '%s\n' \
    'grassing-simulation-check: test/evidence responsibility leaked into production' >&2
  exit 1
fi
if grep -R -E \
    'examples\.grassing\.(state|model)|SimulationRuntimeBootstrap|InitialStateGenerator|SimulationInitialState' \
    "$application_dir/src/main/java" >/dev/null; then
  printf '%s\n' \
    'grassing-simulation-check: retired production identity remains' >&2
  exit 1
fi
main_profiles=$(find "$application_dir/src/main/resources/config" \
  -type f -name '*.properties' | wc -l | tr -d ' ')
if [ "$main_profiles" -ne 1 ] \
    || [ ! -f "$application_dir/src/main/resources/config/default.properties" ] \
    || grep -R -F 'benchmark.' "$application_dir/src/main/resources" \
      >/dev/null; then
  printf '%s\n' \
    'grassing-simulation-check: production config/resource boundary regressed' >&2
  exit 1
fi
for resource_profile in correctness large long-run; do
  if [ ! -f \
      "$application_dir/src/test/resources/config/$resource_profile.properties" ]; then
    printf '%s\n' \
      "grassing-simulation-check: missing test profile $resource_profile" >&2
    exit 1
  fi
done
if [ ! -f \
    "$application_dir/src/test/resources/benchmark/default.properties" ]; then
  printf '%s\n' \
    'grassing-simulation-check: missing benchmark options' >&2
  exit 1
fi

production_jar=$application_build_dir/grassing-individual-simulation-1.0.0-SNAPSHOT.jar
jar_manifest=$evidence_dir/production-jar.txt
"$JAVA_HOME/bin/jar" tf "$production_jar" >"$jar_manifest"
if grep -E \
    'grassing/(evidence|validation|model|state)/|SimulationRuntime(TestAccess|Checks)|config/(correctness|large|long-run)\.properties|benchmark/' \
    "$jar_manifest" >/dev/null; then
  printf '%s\n' \
    'grassing-simulation-check: production JAR contains test or retired content' >&2
  exit 1
fi
if ! grep -F \
    'io/github/somaruntime/soma/examples/grassing/schema/generated/GrasserStateTable.class' \
    "$jar_manifest" >/dev/null; then
  printf '%s\n' \
    'grassing-simulation-check: production JAR lacks current schema projection' >&2
  exit 1
fi
for contract in \
  io.github.somaruntime.soma.examples.grassing.scenario.SimulationScenario \
  io.github.somaruntime.soma.examples.grassing.simulation.Simulator \
  io.github.somaruntime.soma.examples.grassing.simulation.SimulationSession \
  io.github.somaruntime.soma.examples.grassing.result.SimulationResult; do
  "$JAVA_HOME/bin/javap" -classpath "$runtime_classpath" "$contract" \
    >"$evidence_dir/$(printf '%s' "$contract" | tr . _).javap"
done

verification_log=$evidence_dir/verification.log
: >"$verification_log"
for verification_profile in correctness "$profile"; do
  "$JAVA_HOME/bin/java" -Xms512m -Xmx512m -cp "$test_classpath" \
    io.github.somaruntime.soma.examples.grassing.evidence.SimulationVerification \
    "$verification_profile" >>"$verification_log"
done
if [ "$(grep -c '^simulation-verification:' "$verification_log")" -ne 2 ] \
    || grep -v 'claimAllowed=false' "$verification_log" >/dev/null; then
  printf '%s\n' \
    'grassing-simulation-check: verification artifact mismatch' >&2
  exit 1
fi
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

"$JAVA_HOME/bin/java" -Xms256m -Xmx256m -cp "$runtime_classpath" \
  io.github.somaruntime.soma.examples.grassing.SimulationApplication default \
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
  "$JAVA_HOME/bin/java" -Xms"$heap" -Xmx"$heap" -cp "$test_classpath" \
    io.github.somaruntime.soma.examples.grassing.evidence.SimulationBenchmark \
    "$profile" "$profile" \
    >>"$benchmark_artifact"
  fork=$((fork + 1))
done
if [ "$(wc -l <"$benchmark_artifact" | tr -d ' ')" -ne "$forks" ]; then
  printf '%s\n' 'grassing-simulation-check: fork count mismatch' >&2
  exit 1
fi
grep -F '"artifactVersion":"grassing-simulation-benchmark-v3"' \
    "$benchmark_artifact" >/dev/null
grep -F "\"configuredForks\":$forks" "$benchmark_artifact" >/dev/null
grep -F "\"profile\":\"$profile\"" "$benchmark_artifact" >/dev/null
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
for field in worldWidth worldHeight worldCells ticks initialPopulation \
  maximumPopulation warmup measurements tickExecutions; do
  sed -n "s/.*\\\"$field\\\":\\([0-9][0-9]*\\).*/\\1/p" \
    "$benchmark_artifact" | LC_ALL=C sort -u >"$evidence_dir/$field.txt"
  if [ "$(wc -l <"$evidence_dir/$field.txt" | tr -d ' ')" -ne 1 ]; then
    printf '%s\n' \
      "grassing-simulation-check: unstable $field across forks" >&2
    exit 1
  fi
done
for expectation in \
  "worldWidth:$expected_width" \
  "worldHeight:$expected_height" \
  "ticks:$expected_ticks" \
  "initialPopulation:$expected_population" \
  "warmup:1" \
  "measurements:$expected_measurements" \
  "tickExecutions:$((expected_ticks * expected_measurements))"; do
  field=${expectation%%:*}
  expected=${expectation#*:}
  actual=$(cat "$evidence_dir/$field.txt")
  if [ "$actual" != "$expected" ]; then
    printf '%s\n' \
      "grassing-simulation-check: $profile $field expected $expected, got $actual" >&2
    exit 1
  fi
done
fork=1
while [ "$fork" -le "$forks" ]; do
  if [ "$(grep -c "\"fork\":$fork," "$benchmark_artifact")" -ne 1 ]; then
    printf '%s\n' \
      "grassing-simulation-check: missing or duplicate fork $fork" >&2
    exit 1
  fi
  fork=$((fork + 1))
done
while IFS= read -r record; do
  allocated=$(printf '%s\n' "$record" |
    sed -n 's/.*"allocatedBytes":\([0-9][0-9]*\).*/\1/p')
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
    io.github.somaruntime.soma.benchmarks.PerformanceBaselineComparator \
    "$baseline" "$baseline_result" "$benchmark_artifact"
fi

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./mvnw -version
if [ -n "$baseline_result" ]; then
  printf '%s\n' "grassing-simulation-baseline: $baseline_result"
else
  printf '%s\n' 'grassing-simulation-baseline: calibration-only'
fi
printf '%s\n' "grassing-simulation-benchmark: $benchmark_artifact"
printf '%s\n' "grassing-simulation-evidence: $evidence_dir"
printf '%s\n' "grassing-simulation-check: profile=$profile forks=$forks ok"
