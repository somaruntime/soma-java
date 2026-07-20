#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'examples-phase6-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

java_specification=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' "examples-phase6-check: expected Java 8, got $java_specification" >&2
  exit 1
fi

./mvnw -B -ntp -pl soma-examples -am clean verify

mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/phase6-examples.XXXXXX")

classes=$root_dir/soma-examples/target/classes
runtime_classes=$root_dir/soma-runtime-core/target/classes
expected=$root_dir/soma-examples/src/test/fixtures/phase6
scenario_output=$evidence_dir/scenario-output.txt
"$JAVA_HOME/bin/java" -cp "$classes:$runtime_classes" \
  com.hgtech.soma.examples.ScenarioSuite >"$scenario_output"
cat "$scenario_output"
"$JAVA_HOME/bin/java" -cp "$root_dir/soma-examples/target/test-classes:$classes:$runtime_classes" \
  com.hgtech.soma.examples.fjsp.FjspVerificationSuite \
  >"$evidence_dir/fjsp-verification.txt"
cat "$evidence_dir/fjsp-verification.txt"
grep -F 'fjsp-verification: ok' "$evidence_dir/fjsp-verification.txt" >/dev/null
combined_output=$evidence_dir/combined-output.txt
cat "$scenario_output" "$evidence_dir/fjsp-verification.txt" >"$combined_output"

grep -F 'access-pattern-card scenario=fjsp' "$combined_output" >/dev/null
grep -F 'access-pattern-card scenario=vrp' "$scenario_output" >/dev/null
grep -F 'access-pattern-card scenario=simulation' "$scenario_output" >/dev/null
grep -F 'access-pattern-card scenario=game' "$scenario_output" >/dev/null
for scenario in fjsp vrp simulation game; do
  apc_line=$(grep -F "access-pattern-card scenario=$scenario " "$combined_output")
  printf '%s\n' "$apc_line" | grep -E ' rows=[1-9][0-9]* ' >/dev/null
  printf '%s\n' "$apc_line" | grep -E ' hotColumns=[^ ]+' >/dev/null
  printf '%s\n' "$apc_line" | grep -E ' hotLeafWidthsByTable=[^ ]+ ' >/dev/null
  printf '%s\n' "$apc_line" | grep -E ' aggregateHotLeafWidths=[1-9][0-9]* ' >/dev/null
  printf '%s\n' "$apc_line" | grep -E ' workingSetHotLeafBytes=[1-9][0-9]* ' >/dev/null
  printf '%s\n' "$apc_line" | grep -E ' reads=[1-9][0-9]* mutations=[1-9][0-9]* ' >/dev/null
  printf '%s\n' "$apc_line" | grep -F 'observation=executed-result-accounting' >/dev/null
done
grep -F 'scenario=fjsp ' "$combined_output" | grep -F 'aggregateHotLeafWidths=64 ' >/dev/null
grep -F 'scenario=vrp ' "$scenario_output" | grep -F 'aggregateHotLeafWidths=32 ' >/dev/null
grep -F 'scenario=simulation ' "$scenario_output" | grep -F 'aggregateHotLeafWidths=44 ' >/dev/null
grep -F 'scenario=game ' "$scenario_output" | grep -F 'aggregateHotLeafWidths=32 ' >/dev/null
grep -F 'workingSetFormula=assignments.capacity*64' "$combined_output" >/dev/null
grep -F 'workingSetFormula=visits.capacity*32' "$scenario_output" >/dev/null
grep -F 'workingSetFormula=state.capacity*44' "$scenario_output" >/dev/null
grep -F 'workingSetFormula=units.capacity*12+map.capacity*8+moves.capacity*4+damage.capacity*8' \
  "$scenario_output" >/dev/null

fjsp_rows_source=$root_dir/soma-examples/target/generated-sources/annotations/com/hgtech/soma/examples/fjsp/schema/generated/MachineCandidateRows.java
grep -F 'selectionRows=values;selectionLength=length;selectionScanned=scanned' \
  "$fjsp_rows_source" >/dev/null
if grep -F 'class Selection' "$fjsp_rows_source" >/dev/null; then
  printf '%s\n' 'examples-phase6-check: terminal Selection allocation regressed' >&2
  exit 1
fi
grep -F 'private int terminalMaximum()' "$fjsp_rows_source" >/dev/null
grep -F 'identity tie-break must survive packed compaction' \
  "$root_dir/soma-examples/src/test/java/com/hgtech/soma/examples/fjsp/FjspVerificationSuite.java" >/dev/null
grep -F 'application heap preserves deterministic teaching result' \
  "$root_dir/soma-examples/src/test/java/com/hgtech/soma/examples/fjsp/FjspVerificationSuite.java" >/dev/null
grep -F 'new FjspMachineAvailabilityQueue(instance.machines)' \
  "$root_dir/soma-examples/src/main/java/com/hgtech/soma/examples/fjsp/FjspSolver.java" >/dev/null
if grep -F 'instance.machines.rows().sorted' \
    "$root_dir/soma-examples/src/main/java/com/hgtech/soma/examples/fjsp/FjspSolver.java" >/dev/null; then
  printf '%s\n' 'examples-phase6-check: machine dynamic-sort reference leaked into canonical solver' >&2
  exit 1
fi
grep -F 'scenario=vrp ' "$scenario_output" | grep -F 'visits=3' >/dev/null
grep -F 'soma-examples-scenarios: ok' "$scenario_output" >/dev/null
grep '^lane=' "$evidence_dir/fjsp-verification.txt" \
  >"$evidence_dir/lane-markers.txt"
cmp "$expected/expected-lane-markers.txt" "$evidence_dir/lane-markers.txt"

for scenario_package in fjsp vrp simulation game; do
  schema_package="com.hgtech.soma.examples.$scenario_package"
  if [ "$scenario_package" = 'fjsp' ]; then
    schema_package="$schema_package.schema"
  fi
  schema="$classes/META-INF/soma/$schema_package.schema.json"
  schema_hash="$classes/META-INF/soma/$schema_package.schema.sha256"
  test -s "$schema"
  test -s "$schema_hash"
  cmp "$expected/$(basename "$schema")" "$schema"
  cmp "$expected/$(basename "$schema_hash")" "$schema_hash"
  if ! grep -E '^[0-9a-f]{64}$' "$schema_hash" >/dev/null; then
    printf '%s\n' "examples-phase6-check: invalid canonical schema hash: $scenario_package" >&2
    exit 1
  fi
  shasum -a 256 "$schema" "$schema_hash" >>"$evidence_dir/schema-artifacts.sha256"
done

for generated_type in \
  fjsp/schema/generated/OperationDefinitionTable \
  fjsp/schema/generated/MachineTable \
  fjsp/schema/generated/MachineCandidateTable \
  fjsp/schema/generated/OperationAssignmentTable \
  vrp/generated/RouteTable \
  vrp/generated/InsertionCandidateRowTable \
  simulation/generated/StateVectorRowTable \
  simulation/generated/PendingEventRowTable \
  game/generated/GameUnitTable \
  game/generated/MapTileRowTable; do
  test -s "$classes/com/hgtech/soma/examples/$generated_type.class"
done

generated_manifest=$evidence_dir/generated-types.txt
find soma-examples/target/generated-sources/annotations/com/hgtech/soma/examples \
  -type f -name '*.java' | sed 's#^.*/com/hgtech/soma/examples/##;s#\.java$##' |
  LC_ALL=C sort >"$generated_manifest"
generated_count=$(wc -l <"$generated_manifest" | tr -d ' ')
cmp "$expected/expected-generated-types.txt" "$generated_manifest"

public_javap=$evidence_dir/public-api-facts.txt
while IFS='|' read -r binary_name public_fact; do
  test -n "$binary_name"
  test -n "$public_fact"
  if ! "$JAVA_HOME/bin/javap" -classpath "$classes:$runtime_classes" -public \
      "$binary_name" | grep -F "$public_fact" >>"$public_javap"; then
    printf '%s\n' "examples-phase6-check: missing public API fact: $binary_name|$public_fact" >&2
    exit 1
  fi
done <"$expected/expected-public-api-facts.txt"

class_manifest=$evidence_dir/class-major.txt
bad=0
class_count=0
for class_file in $(find "$classes/com/hgtech/soma/examples" -type f -name '*.class' |
  LC_ALL=C sort); do
  major=$(od -An -tx1 -j6 -N2 "$class_file" | tr -d ' ')
  relative=${class_file#"$classes/"}
  printf '%s %s\n' "$major" "$relative" >>"$class_manifest"
  class_count=$((class_count + 1))
  if [ "$major" != '0034' ]; then
    bad=1
  fi
done
if [ "$bad" -ne 0 ]; then
  printf '%s\n' 'examples-phase6-check: non-Java-8 class detected' >&2
  exit 1
fi
if grep -R -E 'java\.util\.stream|java\.lang\.reflect|Class\.forName' \
    soma-examples/src/main/java >/dev/null; then
  printf '%s\n' 'examples-phase6-check: reflective or Stream runtime path detected' >&2
  exit 1
fi
if sed -n '/void release(/,/Candidate select(/p' \
    soma-examples/src/main/java/com/hgtech/soma/examples/fjsp/FjspCandidateFrontier.java |
    grep -F 'definitions.fetch' >/dev/null; then
  printf '%s\n' 'examples-phase6-check: FJSP release hot path materializes definition' >&2
  exit 1
fi
if grep -F 'byGridPosition().fetchAll()' \
    soma-examples/src/main/java/com/hgtech/soma/examples/game/GameScenario.java >/dev/null; then
  printf '%s\n' 'examples-phase6-check: Game uses positional whole-table materialization' >&2
  exit 1
fi
if grep -E 'row\.(position|customerId|machineId|candidateKey)\(\)' \
    soma-examples/src/main/java/com/hgtech/soma/examples/fjsp/FjspCandidateFrontier.java \
    soma-examples/src/main/java/com/hgtech/soma/examples/vrp/VrpScenario.java \
    soma-examples/src/main/java/com/hgtech/soma/examples/game/GameScenario.java \
    >/dev/null; then
  printf '%s\n' 'examples-phase6-check: Value object reconstruction leaked into canonical hot scan' >&2
  exit 1
fi
grep -F 'identity tie-break must survive packed compaction' \
  soma-examples/src/test/java/com/hgtech/soma/examples/fjsp/FjspVerificationSuite.java >/dev/null
grep -F 'non-empty insertion rewrites the shifted route segment' \
  soma-examples/src/main/java/com/hgtech/soma/examples/vrp/VrpScenario.java >/dev/null
grep -F '<artifactId>soma-processor</artifactId>' soma-examples/pom.xml >/dev/null
grep -A2 -F '<artifactId>soma-processor</artifactId>' soma-examples/pom.xml |
  grep -F '<scope>provided</scope>' >/dev/null

shasum -a 256 soma-examples/target/soma-examples-0.1.0-SNAPSHOT.jar \
  >"$evidence_dir/artifact.sha256"
git diff --check

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./mvnw -version
uname -srm
printf '%s\n' "examples-generated-types: $generated_count"
printf '%s\n' "examples-class-major: 52 ($class_count classes)"
printf '%s\n' "examples-phase6-evidence: $evidence_dir"
printf '%s\n' 'examples-phase6-check: ok'
