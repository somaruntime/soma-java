#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'scan-code-size-check: JAVA_HOME must point to Azul Zulu JDK 8' >&2
  exit 1
fi

java_specification=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
java_vendor=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.vendor = //p' | head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' "scan-code-size-check: expected Java 8, got $java_specification" >&2
  exit 1
fi
case "$java_vendor" in
  *Azul*) ;;
  *)
    printf '%s\n' "scan-code-size-check: expected Azul Zulu JDK, got $java_vendor" >&2
    exit 1
    ;;
esac

start_millis=$(perl -MTime::HiRes=time -e 'printf "%.0f", time() * 1000')
./mvnw -B -ntp \
  -pl soma-benchmarks,soma-examples/industrial-dynamic-scheduler,soma-examples/grassing-individual-simulation,soma-examples/real-time-dispatch-rule-engine \
  -am -DskipTests clean compile
end_millis=$(perl -MTime::HiRes=time -e 'printf "%.0f", time() * 1000')
compile_wall_millis=$((end_millis - start_millis))

mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/scan-code-size.XXXXXX")
surface_footprint=$evidence_dir/surface-footprint.tsv
artifact_footprint=$evidence_dir/scan-artifact-footprint.tsv
schema_footprint=$evidence_dir/schema-footprint.tsv
dataflow_footprint=$evidence_dir/dataflow-footprint.tsv

printf '%s\n' \
  'surface	scanCount	sourceBytes	sourceLines	familyClassBytes	nestedClassCount	maxSourceBytes	maxSourceLines	maxFamilyClassBytes	maxNestedClassCount' \
  >"$surface_footprint"
printf '%s\n' \
  'surface	scanType	sourceBytes	sourceLines	topLevelClassBytes	nestedClassCount	nestedClassBytes	familyClassBytes' \
  >"$artifact_footprint"
printf '%s\n' \
  'surface	schemaPackage	tableCount	fieldCount	physicalLeafCount	selectorCount	generatedSourceBytes	scanCount	scanSourceBytes' \
  >"$schema_footprint"
printf '%s\n' \
  'surface	tableCount	dataFlowCount	sourceBytes	sourceLines	familyClassBytes	nestedClassCount	maxSourceBytes	maxSourceLines	maxFamilyClassBytes	maxNestedClassCount' \
  >"$dataflow_footprint"

measure_surface() {
  surface=$1
  module=$2
  expected_scan_count=$3
  maximum_source_bytes=$4
  maximum_source_lines=$5
  maximum_family_bytes=$6
  maximum_nested_count=$7
  maximum_dataflow_source_bytes=$8
  maximum_dataflow_source_lines=$9
  maximum_dataflow_family_bytes=${10}
  maximum_dataflow_nested_count=${11}

  generated=$module/target/generated-sources/annotations
  classes=$module/target/classes
  if [ ! -d "$generated" ] || [ ! -d "$classes/META-INF/soma" ]; then
    printf '%s\n' "scan-code-size-check: missing generated surface $surface" >&2
    exit 1
  fi

  scan_count=$(find "$generated" -type f -name '*Scan.java' | wc -l | tr -d ' ')
  source_bytes=$(find "$generated" -type f -name '*Scan.java' -exec wc -c {} + |
    awk '$2 != "total" {sum += $1} END {print sum + 0}')
  source_lines=$(find "$generated" -type f -name '*Scan.java' -exec wc -l {} + |
    awk '$2 != "total" {sum += $1} END {print sum + 0}')
  top_class_bytes=$(find "$classes" -type f -name '*Scan.class' -exec wc -c {} + |
    awk '$2 != "total" {sum += $1} END {print sum + 0}')
  nested_bytes=$(find "$classes" -type f -name '*Scan$*.class' -exec wc -c {} + |
    awk '$2 != "total" {sum += $1} END {print sum + 0}')
  nested_count=$(find "$classes" -type f -name '*Scan$*.class' | wc -l | tr -d ' ')
  family_bytes=$((top_class_bytes + nested_bytes))

  if [ "$scan_count" -ne "$expected_scan_count" ] \
      || [ "$source_bytes" -gt "$maximum_source_bytes" ] \
      || [ "$source_lines" -gt "$maximum_source_lines" ] \
      || [ "$family_bytes" -gt "$maximum_family_bytes" ] \
      || [ "$nested_count" -gt "$maximum_nested_count" ]; then
    printf '%s\n' \
      "scan-code-size-check: $surface failed count=$scan_count sourceBytes=$source_bytes sourceLines=$source_lines familyBytes=$family_bytes nestedCount=$nested_count" >&2
    exit 1
  fi

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$surface" "$scan_count" "$source_bytes" "$source_lines" "$family_bytes" \
    "$nested_count" "$maximum_source_bytes" "$maximum_source_lines" \
    "$maximum_family_bytes" "$maximum_nested_count" >>"$surface_footprint"

  dataflow_count=$(find "$generated" -type f -name '*DataFlow.java' |
    wc -l | tr -d ' ')
  dataflow_source_bytes=$(find "$generated" -type f -name '*DataFlow.java' \
    -exec wc -c {} + |
    awk '$2 != "total" {sum += $1} END {print sum + 0}')
  dataflow_source_lines=$(find "$generated" -type f -name '*DataFlow.java' \
    -exec wc -l {} + |
    awk '$2 != "total" {sum += $1} END {print sum + 0}')
  dataflow_top_class_bytes=$(find "$classes" -type f -name '*DataFlow.class' \
    -exec wc -c {} + |
    awk '$2 != "total" {sum += $1} END {print sum + 0}')
  dataflow_nested_bytes=$(find "$classes" -type f -name '*DataFlow$*.class' \
    -exec wc -c {} + |
    awk '$2 != "total" {sum += $1} END {print sum + 0}')
  dataflow_nested_count=$(find "$classes" -type f -name '*DataFlow$*.class' |
    wc -l | tr -d ' ')
  dataflow_family_bytes=$((dataflow_top_class_bytes + dataflow_nested_bytes))
  if [ "$dataflow_count" -ne "$expected_scan_count" ] \
      || [ "$dataflow_source_bytes" -gt "$maximum_dataflow_source_bytes" ] \
      || [ "$dataflow_source_lines" -gt "$maximum_dataflow_source_lines" ] \
      || [ "$dataflow_family_bytes" -gt "$maximum_dataflow_family_bytes" ] \
      || [ "$dataflow_nested_count" -gt "$maximum_dataflow_nested_count" ]; then
    printf '%s\n' \
      "scan-code-size-check: $surface DataFlow footprint failed count=$dataflow_count sourceBytes=$dataflow_source_bytes sourceLines=$dataflow_source_lines familyBytes=$dataflow_family_bytes nestedCount=$dataflow_nested_count" >&2
    exit 1
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$surface" "$expected_scan_count" "$dataflow_count" \
    "$dataflow_source_bytes" "$dataflow_source_lines" \
    "$dataflow_family_bytes" "$dataflow_nested_count" \
    "$maximum_dataflow_source_bytes" "$maximum_dataflow_source_lines" \
    "$maximum_dataflow_family_bytes" "$maximum_dataflow_nested_count" \
    >>"$dataflow_footprint"

  find "$generated" -type f -name '*Scan.java' | LC_ALL=C sort |
    while IFS= read -r scan_source; do
      relative=${scan_source#"$generated/"}
      scan_type=$(printf '%s' "${relative%.java}" | tr / .)
      class_file=$classes/${relative%.java}.class
      class_directory=$(dirname "$class_file")
      class_stem=$(basename "${class_file%.class}")
      scan_source_bytes=$(wc -c <"$scan_source" | tr -d ' ')
      scan_source_lines=$(wc -l <"$scan_source" | tr -d ' ')
      top_level_class_bytes=$(wc -c <"$class_file" | tr -d ' ')
      scan_nested_count=0
      scan_nested_bytes=0
      for nested_class in "$class_directory"/"$class_stem"\$*.class; do
        [ -f "$nested_class" ] || continue
        scan_nested_count=$((scan_nested_count + 1))
        nested_class_bytes=$(wc -c <"$nested_class" | tr -d ' ')
        scan_nested_bytes=$((scan_nested_bytes + nested_class_bytes))
      done
      scan_family_bytes=$((top_level_class_bytes + scan_nested_bytes))
      printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$surface" "$scan_type" "$scan_source_bytes" "$scan_source_lines" \
        "$top_level_class_bytes" "$scan_nested_count" "$scan_nested_bytes" \
        "$scan_family_bytes"
    done >>"$artifact_footprint"

  find "$classes/META-INF/soma" -type f -name '*.schema.json' | LC_ALL=C sort |
    while IFS= read -r schema_artifact; do
      schema_package=$(sed -n \
        's/.*"schemaPackage":"\([^"]*\)".*/\1/p' "$schema_artifact")
      generated_package=$(sed -n \
        's/.*"generatedPackage":"\([^"]*\)".*/\1/p' "$schema_artifact")
      schema_generated=$generated/$(printf '%s' "$generated_package" | tr . /)
      schema_tables=$(grep -o '"kind":"\(dense\|keyed\)"' \
        "$schema_artifact" | wc -l | tr -d ' ')
      schema_fields=$(grep -o '"role":"[^"]*"' \
        "$schema_artifact" | wc -l | tr -d ' ')
      schema_leaves=$(grep -o '"leafPath":"[^"]*"' \
        "$schema_artifact" | wc -l | tr -d ' ')
      schema_selectors=$(grep -o '"kind":"\(index\|unique\)"' \
        "$schema_artifact" | wc -l | tr -d ' ')
      schema_generated_bytes=$(find "$schema_generated" -type f -name '*.java' \
        -exec wc -c {} + | awk '$2 != "total" {sum += $1} END {print sum + 0}')
      schema_scan_count=$(find "$schema_generated" -type f -name '*Scan.java' |
        wc -l | tr -d ' ')
      schema_scan_bytes=$(find "$schema_generated" -type f -name '*Scan.java' \
        -exec wc -c {} + | awk '$2 != "total" {sum += $1} END {print sum + 0}')
      printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$surface" "$schema_package" "$schema_tables" "$schema_fields" \
        "$schema_leaves" "$schema_selectors" "$schema_generated_bytes" \
        "$schema_scan_count" "$schema_scan_bytes"
    done >>"$schema_footprint"
}

# Baselines are the current immutable application candidates plus 15%.
measure_surface neutral-benchmark soma-benchmarks \
  6 166428 740 234066 51 78510 787 267263 71
measure_surface industrial-scheduler \
  soma-examples/industrial-dynamic-scheduler \
  9 251396 1081 358335 75 115761 1136 418589 105
measure_surface grassing-simulation \
  soma-examples/grassing-individual-simulation \
  2 55010 244 78517 17 25047 258 89307 23
measure_surface rtd-rule-engine \
  soma-examples/real-time-dispatch-rule-engine \
  2 54559 243 76900 18 25757 260 90786 25

total_scans=$(awk -F '	' 'NR > 1 {sum += $2} END {print sum + 0}' \
  "$surface_footprint")
artifact_scans=$(awk -F '	' 'NR > 1 {count++} END {print count + 0}' \
  "$artifact_footprint")
schema_tables=$(awk -F '	' 'NR > 1 {sum += $3} END {print sum + 0}' \
  "$schema_footprint")
schema_scans=$(awk -F '	' 'NR > 1 {sum += $8} END {print sum + 0}' \
  "$schema_footprint")
dataflow_tables=$(awk -F '	' 'NR > 1 {sum += $2} END {print sum + 0}' \
  "$dataflow_footprint")
dataflow_types=$(awk -F '	' 'NR > 1 {sum += $3} END {print sum + 0}' \
  "$dataflow_footprint")
if [ "$total_scans" -ne 19 ] \
    || [ "$artifact_scans" -ne "$total_scans" ] \
    || [ "$schema_tables" -ne "$total_scans" ] \
    || [ "$schema_scans" -ne "$total_scans" ] \
    || [ "$dataflow_tables" -ne "$total_scans" ] \
    || [ "$dataflow_types" -ne "$total_scans" ]; then
  printf '%s\n' 'scan-code-size-check: normalized footprint evidence mismatch' >&2
  exit 1
fi

evidence=$evidence_dir/scan-code-size.properties
{
  printf 'commit=%s\n' "$(git rev-parse HEAD)"
  printf 'javaVersion=%s\n' "$($JAVA_HOME/bin/java -version 2>&1 | head -n 1)"
  printf 'javacVersion=%s\n' "$($JAVA_HOME/bin/javac -version 2>&1)"
  printf 'compileWallMillis=%s\n' "$compile_wall_millis"
  printf 'surfaceCount=4\n'
  printf 'scanCount=%s\n' "$total_scans"
  printf 'dataFlowCompanionCount=%s\n' "$dataflow_types"
  printf 'dataFlowGenerationRule=one-companion-per-table\n'
  printf 'regressionBudgetKind=fixed-candidate-per-surface\n'
  printf 'normalizedEvidenceRole=diagnostic-only\n'
  printf 'generatedSourceAdmissionOwner=CodegenLimits.MAXIMUM_GENERATED_SOURCE_LENGTH\n'
  printf 'claimAllowed=false\n'
} >"$evidence"
shasum -a 256 "$evidence" "$surface_footprint" "$artifact_footprint" \
  "$schema_footprint" "$dataflow_footprint" \
  soma-processor/src/main/java/com/hgtech/soma/processor/DenseDataFlowSourceEmitter.java \
  soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/GeneratedScanPlan.java \
  soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/GeneratedScanEvaluation.java \
  >"$evidence_dir/checksums.sha256"

printf '%s\n' "scan-code-size-evidence: $evidence_dir"
printf '%s\n' 'scan-code-size-check: ok'
