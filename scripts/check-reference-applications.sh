#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ] \
    || [ ! -x "$JAVA_HOME/bin/javap" ]; then
  printf '%s\n' 'reference-app-check: JAVA_HOME must point to Azul Zulu JDK 8' >&2
  exit 1
fi

java_specification=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
java_vendor=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.vendor = //p' | head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' "reference-app-check: expected Java 8, got $java_specification" >&2
  exit 1
fi
case "$java_vendor" in
  *Azul*) ;;
  *)
    printf '%s\n' "reference-app-check: expected Azul Zulu JDK, got $java_vendor" >&2
    exit 1
    ;;
esac

root_version=$(sed -n \
  's:.*<version>\([^<]*\)</version>.*:\1:p' pom.xml | sed -n '1p')
dependency_plugin_version=$(sed -n \
  's:.*<maven.dependency.plugin.version>\([^<]*\)</maven.dependency.plugin.version>.*:\1:p' \
  pom.xml | sed -n '1p')
dependency_plugin=org.apache.maven.plugins:maven-dependency-plugin:$dependency_plugin_version

grep -F '<packaging>pom</packaging>' soma-examples/pom.xml >/dev/null
for application in industrial-dynamic-scheduler grassing-individual-simulation; do
  grep -F "<module>$application</module>" soma-examples/pom.xml >/dev/null
done
if find soma-examples/src -type f -print 2>/dev/null | grep . >/dev/null \
    || grep -F '<dependencies>' soma-examples/pom.xml >/dev/null \
    || grep -F '<artifactId>soma-examples</artifactId>' soma-benchmarks/pom.xml >/dev/null; then
  printf '%s\n' 'reference-app-check: aggregator or benchmark dependency boundary regressed' >&2
  exit 1
fi
for retired in \
  scripts/check-examples-phase6.sh \
  scripts/check-fjsp-allocation-gc.sh \
  scripts/run-fjsp-100k-benchmark.sh; do
  if [ -e "$retired" ]; then
    printf '%s\n' "reference-app-check: retired current path remains: $retired" >&2
    exit 1
  fi
done
if grep -R -E '^import com\.hgtech\.soma\.(annotation|runtime)' \
    soma-examples/industrial-dynamic-scheduler/src/main/java/com/hgtech/soma/examples/scheduler/problem \
    soma-examples/grassing-individual-simulation/src/main/java/com/hgtech/soma/examples/grassing/scenario \
    >/dev/null; then
  printf '%s\n' 'reference-app-check: detached input model/generator imports SOMA runtime' >&2
  exit 1
fi
if grep -R -E \
    'SyntheticSchedulingProblemFactory|SyntheticSimulationScenarioFactory' \
    soma-examples/industrial-dynamic-scheduler/src/main/java/com/hgtech/soma/examples/scheduler/runtime \
    soma-examples/grassing-individual-simulation/src/main/java/com/hgtech/soma/examples/grassing/runtime \
    >/dev/null; then
  printf '%s\n' 'reference-app-check: runtime loop refers back to input generator' >&2
  exit 1
fi

mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/reference-applications.XXXXXX")
repository=$evidence_dir/repository
mkdir -p "$repository"
seed_repository=$root_dir/soma-testkit/target/phase0-m2/repository
if [ -d "$seed_repository" ]; then
  cp -R "$seed_repository/." "$repository/"
fi

./mvnw -B -ntp -Dmaven.repo.local="$repository" \
  -pl soma-runtime-core,soma-processor -am install -DskipTests

for application in industrial-dynamic-scheduler grassing-individual-simulation; do
  application_dir=$root_dir/soma-examples/$application
  pom=$application_dir/pom.xml
  if grep -F '<parent>' "$pom" >/dev/null \
      || grep -E '<artifactId>(soma-testkit|soma-examples|examples-common)</artifactId>' \
        "$pom" >/dev/null; then
    printf '%s\n' "reference-app-check: non-isolated POM dependency in $application" >&2
    exit 1
  fi
  application_soma_version=$(sed -n \
    's:.*<soma.version>\([^<]*\)</soma.version>.*:\1:p' "$pom")
  if [ "$application_soma_version" != "$root_version" ]; then
    printf '%s\n' \
      "reference-app-check: $application consumes $application_soma_version, expected $root_version" >&2
    exit 1
  fi
  if grep -R -E \
      'com\.hgtech\.soma\.(processor|testkit|runtime\.generated)|com\.hgtech\.soma\.examples\.(fjsp|vrp|simulation|game)' \
      "$application_dir/src/main/java" >/dev/null; then
    printf '%s\n' "reference-app-check: forbidden source import in $application" >&2
    exit 1
  fi

  first_build=$evidence_dir/$application-first-target
  repeat_build=$evidence_dir/$application-repeat-target
  ./mvnw -B -ntp -Dmaven.repo.local="$repository" \
    -Dsoma.build.directory="$first_build" \
    -f "$pom" clean package

  generated=$first_build/generated-sources/annotations
  classes=$first_build/classes
  if grep -R -a -F 'Unresolved compilation problem' \
      "$classes" "$first_build/test-classes" >/dev/null; then
    printf '%s\n' \
      "reference-app-check: compiler-error stub in first $application build" >&2
    exit 1
  fi
  first_dir=$evidence_dir/$application-first
  mkdir -p "$first_dir"
  find "$generated" -type f -name '*.java' | LC_ALL=C sort |
    sed "s#^$generated/##" >"$first_dir/generated-manifest.txt"
  find "$classes/META-INF/soma" -type f -name '*.schema.*' | LC_ALL=C sort |
    sed "s#^$classes/##" >"$first_dir/schema-manifest.txt"
  while IFS= read -r relative; do
    shasum -a 256 "$generated/$relative"
  done <"$first_dir/generated-manifest.txt" >"$first_dir/generated.sha256"
  while IFS= read -r relative; do
    shasum -a 256 "$classes/$relative"
  done <"$first_dir/schema-manifest.txt" >"$first_dir/schema.sha256"

  runtime_classpath_file=$evidence_dir/$application-runtime-classpath.txt
  ./mvnw -B -ntp -Dmaven.repo.local="$repository" -f "$pom" \
    "$dependency_plugin":build-classpath -DincludeScope=runtime \
    -Dmdep.outputFile="$runtime_classpath_file"
  grep -F '/soma-runtime-core/' "$runtime_classpath_file" >/dev/null
  if grep -E '/(soma-processor|soma-testkit|soma-examples)/' \
      "$runtime_classpath_file" >/dev/null; then
    printf '%s\n' "reference-app-check: build/test artifact leaked into $application runtime" >&2
    exit 1
  fi

  ./mvnw -B -ntp -Dmaven.repo.local="$repository" \
    -Dsoma.build.directory="$repeat_build" \
    -f "$pom" clean package

  repeat_generated=$repeat_build/generated-sources/annotations
  repeat_classes=$repeat_build/classes
  if grep -R -a -F 'Unresolved compilation problem' \
      "$repeat_classes" "$repeat_build/test-classes" >/dev/null; then
    printf '%s\n' \
      "reference-app-check: compiler-error stub in repeat $application build" >&2
    exit 1
  fi
  find "$repeat_generated" -type f -name '*.java' | LC_ALL=C sort |
    sed "s#^$repeat_generated/##" \
      >"$evidence_dir/$application-generated-repeat.txt"
  find "$repeat_classes/META-INF/soma" -type f -name '*.schema.*' |
    LC_ALL=C sort |
    sed "s#^$repeat_classes/##" \
      >"$evidence_dir/$application-schema-repeat.txt"
  cmp "$first_dir/generated-manifest.txt" \
    "$evidence_dir/$application-generated-repeat.txt"
  cmp "$first_dir/schema-manifest.txt" \
    "$evidence_dir/$application-schema-repeat.txt"
  while IFS= read -r relative; do
    shasum -a 256 "$repeat_generated/$relative"
  done <"$first_dir/generated-manifest.txt" >"$evidence_dir/$application-generated-repeat.sha256"
  while IFS= read -r relative; do
    shasum -a 256 "$repeat_classes/$relative"
  done <"$first_dir/schema-manifest.txt" >"$evidence_dir/$application-schema-repeat.sha256"
  sed "s#$generated/##" "$first_dir/generated.sha256" \
    >"$evidence_dir/$application-generated-first-normalized.sha256"
  sed "s#$repeat_generated/##" \
    "$evidence_dir/$application-generated-repeat.sha256" \
    >"$evidence_dir/$application-generated-repeat-normalized.sha256"
  sed "s#$classes/##" "$first_dir/schema.sha256" \
    >"$evidence_dir/$application-schema-first-normalized.sha256"
  sed "s#$repeat_classes/##" \
    "$evidence_dir/$application-schema-repeat.sha256" \
    >"$evidence_dir/$application-schema-repeat-normalized.sha256"
  cmp "$evidence_dir/$application-generated-first-normalized.sha256" \
    "$evidence_dir/$application-generated-repeat-normalized.sha256"
  cmp "$evidence_dir/$application-schema-first-normalized.sha256" \
    "$evidence_dir/$application-schema-repeat-normalized.sha256"

  class_count=0
  for class_file in $(find "$classes" -type f -name '*.class' | LC_ALL=C sort); do
    major=$(od -An -tx1 -j6 -N2 "$class_file" | tr -d ' ')
    if [ "$major" != '0034' ]; then
      printf '%s\n' "reference-app-check: non-Java-8 class in $application" >&2
      exit 1
    fi
    class_count=$((class_count + 1))
  done
  generated_count=$(wc -l <"$first_dir/generated-manifest.txt" | tr -d ' ')
  schema_count=$(wc -l <"$first_dir/schema-manifest.txt" | tr -d ' ')
  if [ "$generated_count" -le 0 ] || [ "$schema_count" -ne 2 ]; then
    printf '%s\n' "reference-app-check: missing generated/schema artifacts in $application" >&2
    exit 1
  fi
  printf '%s\n' \
    "$application generatedTypes=$generated_count classes=$class_count schemaArtifacts=$schema_count" \
    >>"$evidence_dir/application-summary.txt"
done

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./mvnw -version
printf '%s\n' "reference-app-evidence: $evidence_dir"
printf '%s\n' 'reference-app-check: ok'
