#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"
. "$root_dir/scripts/lib/external-evidence.sh"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'external-consumer-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

java_specification=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' "external-consumer-check: expected Java 8, got $java_specification" >&2
  exit 1
fi

dependency_plugin_version=$(sed -n \
  's:.*<maven.dependency.plugin.version>\([^<]*\)</maven.dependency.plugin.version>.*:\1:p' \
  pom.xml | sed -n '1p')
dependency_plugin=org.apache.maven.plugins:maven-dependency-plugin:$dependency_plugin_version

fixture_source=$root_dir/tests/fixtures/external-maven-value
expected=$fixture_source/expected
mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/external-consumer-contract.XXXXXX")
fixture=$evidence_dir/consumer
mkdir -p "$fixture"
cp "$fixture_source/pom.xml" "$fixture/pom.xml"
cp -R "$fixture_source/src" "$fixture/src"

# Install repository artifacts in published shape. The consumer below is a separate
# Maven project and neither inherits the root parent nor receives reactor classpaths.
soma_require_or_install_external_artifacts

soma_external_mvn -B -ntp \
  -f "$fixture/pom.xml" clean package

schema=META-INF/soma/com.example.soma.external.schema.json
schema_hash=META-INF/soma/com.example.soma.external.schema.sha256
cp "$fixture/target/classes/$schema" "$evidence_dir/clean.schema.json"
cp "$fixture/target/classes/$schema_hash" "$evidence_dir/clean.schema.sha256"
cp "$fixture/target/classes/com/example/soma/external/ExternalId.class" \
  "$evidence_dir/clean-ExternalId.class"
cp "$fixture/target/external-maven-value-consumer-1.0.0-SNAPSHOT.jar" \
  "$evidence_dir/clean-consumer.jar"
cmp "$expected/com.example.soma.external.schema.json" \
  "$evidence_dir/clean.schema.json"
cmp "$expected/com.example.soma.external.schema.sha256" \
  "$evidence_dir/clean.schema.sha256"

# Exercise a real incremental schema mutation. Package-owned resource paths remain
# stable while canonical contents/hash change, then return byte-for-byte to clean.
cp "$fixture_source/variants/schema-renamed/package-info.java" \
  "$fixture/src/main/java/com/example/soma/external/package-info.java"
touch "$fixture/src/main/java/com/example/soma/external/package-info.java"
soma_external_mvn -B -ntp \
  -f "$fixture/pom.xml" compile \
  >"$evidence_dir/incremental-renamed.log"
grep -F 'Compiling ' "$evidence_dir/incremental-renamed.log" >/dev/null
cmp "$fixture_source/variants/schema-renamed/expected.schema.json" \
  "$fixture/target/classes/$schema"
cmp "$fixture_source/variants/schema-renamed/expected.schema.sha256" \
  "$fixture/target/classes/$schema_hash"
resource_count=$(find "$fixture/target/classes/META-INF/soma" \
  -type f -name '*.schema.*' | wc -l | tr -d ' ')
if [ "$resource_count" != '2' ]; then
  printf '%s\n' \
    "external-consumer-check: expected 2 package-owned schema resources, got $resource_count" >&2
  exit 1
fi

cp "$fixture_source/src/main/java/com/example/soma/external/package-info.java" \
  "$fixture/src/main/java/com/example/soma/external/package-info.java"
touch "$fixture/src/main/java/com/example/soma/external/package-info.java"
soma_external_mvn -B -ntp \
  -f "$fixture/pom.xml" compile \
  >"$evidence_dir/incremental-restored.log"
grep -F 'Compiling ' "$evidence_dir/incremental-restored.log" >/dev/null
cmp "$evidence_dir/clean.schema.json" "$fixture/target/classes/$schema"
cmp "$evidence_dir/clean.schema.sha256" "$fixture/target/classes/$schema_hash"
cmp "$evidence_dir/clean-ExternalId.class" \
  "$fixture/target/classes/com/example/soma/external/ExternalId.class"

"$JAVA_HOME/bin/javap" \
  -classpath "$fixture/target/classes" \
  -p com.example.soma.external.ExternalId \
  >"$evidence_dir/ExternalId.javap.txt"
cmp "$expected/ExternalId.javap.txt" "$evidence_dir/ExternalId.javap.txt"

# Maven runtime graph must contain runtime-core and must exclude the build-only processor.
runtime_classpath_file=$evidence_dir/runtime-classpath.txt
soma_external_mvn -B -ntp \
  -f "$fixture/pom.xml" \
  "$dependency_plugin":build-classpath \
  -DincludeScope=runtime \
  -Dmdep.outputFile="$runtime_classpath_file"
grep -F '/soma-runtime-core/' "$runtime_classpath_file" >/dev/null
grep -F '/soma-dataflow/' "$runtime_classpath_file" >/dev/null
if grep -F '/soma-processor/' "$runtime_classpath_file" >/dev/null; then
  printf '%s\n' 'external-consumer-check: processor leaked into runtime graph' >&2
  exit 1
fi
runtime_classpath=$(sed -n '1p' "$runtime_classpath_file")

"$JAVA_HOME/bin/java" \
  -cp "$fixture/target/classes:$runtime_classpath" \
  com.example.soma.external.ExternalConsumer

# The lowered value also has no hidden linkage when dependencies are minimized.
"$JAVA_HOME/bin/java" \
  -cp "$fixture/target/classes" \
  com.example.soma.external.ExternalConsumer

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./mvnw -version
printf '%s\n' "external-consumer-evidence: $evidence_dir"
printf '%s\n' 'external-consumer-check: ok'
