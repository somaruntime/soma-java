#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'scan-code-size-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

start_millis=$(perl -MTime::HiRes=time -e 'printf "%.0f", time() * 1000')
./mvnw -B -ntp -pl soma-examples -am -DskipTests clean compile
end_millis=$(perl -MTime::HiRes=time -e 'printf "%.0f", time() * 1000')
compile_wall_millis=$((end_millis - start_millis))

generated=soma-examples/target/generated-sources/annotations
classes=soma-examples/target/classes
scan_count=$(find "$generated" -type f -name '*Scan.java' | wc -l | tr -d ' ')
source_bytes=$(find "$generated" -type f -name '*Scan.java' -print0 |
  xargs -0 wc -c | tail -n 1 | awk '{print $1}')
source_lines=$(find "$generated" -type f -name '*Scan.java' -print0 |
  xargs -0 wc -l | tail -n 1 | awk '{print $1}')
top_class_bytes=$(find "$classes" -type f -name '*Scan.class' -print0 |
  xargs -0 wc -c | tail -n 1 | awk '{print $1}')
nested_count=$(find "$classes" -type f -name '*Scan$*.class' | wc -l | tr -d ' ')
nested_bytes=$(find "$classes" -type f -name '*Scan$*.class' -print0 |
  xargs -0 wc -c | tail -n 1 | awk '{print $1}')
family_bytes=$((top_class_bytes + nested_bytes))

baseline_source_bytes=684384
baseline_source_lines=2980
baseline_family_bytes=957266
baseline_nested_count=231
maximum_source_bytes=$((baseline_source_bytes * 115 / 100))
maximum_source_lines=$((baseline_source_lines * 115 / 100))
maximum_family_bytes=$((baseline_family_bytes * 115 / 100))
maximum_nested_count=$((baseline_nested_count * 115 / 100))

if [ "$scan_count" -ne 33 ] \
    || [ "$source_bytes" -gt "$maximum_source_bytes" ] \
    || [ "$source_lines" -gt "$maximum_source_lines" ] \
    || [ "$family_bytes" -gt "$maximum_family_bytes" ] \
    || [ "$nested_count" -gt "$maximum_nested_count" ]; then
  printf '%s\n' "scan-code-size-check: gate failed count=$scan_count sourceBytes=$source_bytes sourceLines=$source_lines familyBytes=$family_bytes nestedCount=$nested_count" >&2
  exit 1
fi

mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/scan-code-size.XXXXXX")
evidence=$evidence_dir/scan-code-size.properties
artifact_footprint=$evidence_dir/scan-artifact-footprint.tsv
schema_footprint=$evidence_dir/schema-footprint.tsv

printf '%s\n' \
  'scanType	sourceBytes	sourceLines	topLevelClassBytes	nestedClassCount	nestedClassBytes	familyClassBytes' \
  >"$artifact_footprint"
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
      if [ ! -f "$nested_class" ]; then
        continue
      fi
      scan_nested_count=$((scan_nested_count + 1))
      nested_class_bytes=$(wc -c <"$nested_class" | tr -d ' ')
      scan_nested_bytes=$((scan_nested_bytes + nested_class_bytes))
    done
    scan_family_bytes=$((top_level_class_bytes + scan_nested_bytes))
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$scan_type" "$scan_source_bytes" "$scan_source_lines" \
      "$top_level_class_bytes" "$scan_nested_count" "$scan_nested_bytes" \
      "$scan_family_bytes"
  done >>"$artifact_footprint"

printf '%s\n' \
  'schemaPackage	tableCount	fieldCount	physicalLeafCount	selectorCount	generatedSourceBytes	scanCount	scanSourceBytes' \
  >"$schema_footprint"
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
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$schema_package" "$schema_tables" "$schema_fields" "$schema_leaves" \
      "$schema_selectors" "$schema_generated_bytes" "$schema_scan_count" \
      "$schema_scan_bytes"
  done >>"$schema_footprint"

artifact_scan_count=$(awk -F '	' 'NR > 1 {count++} END {print count + 0}' \
  "$artifact_footprint")
artifact_source_bytes=$(awk -F '	' 'NR > 1 {sum += $2} END {print sum + 0}' \
  "$artifact_footprint")
artifact_family_bytes=$(awk -F '	' 'NR > 1 {sum += $7} END {print sum + 0}' \
  "$artifact_footprint")
minimum_scan_source_bytes=$(awk -F '	' \
  'NR == 2 {value = $2} NR > 2 && $2 < value {value = $2} END {print value + 0}' \
  "$artifact_footprint")
maximum_scan_source_bytes=$(awk -F '	' \
  'NR > 1 && $2 > value {value = $2} END {print value + 0}' \
  "$artifact_footprint")
average_scan_source_bytes=$((source_bytes / scan_count))
maximum_scan_source_lines=$(awk -F '	' \
  'NR > 1 && $3 > value {value = $3} END {print value + 0}' \
  "$artifact_footprint")
maximum_top_level_class_bytes=$(awk -F '	' \
  'NR > 1 && $4 > value {value = $4} END {print value + 0}' \
  "$artifact_footprint")
maximum_scan_family_bytes=$(awk -F '	' \
  'NR > 1 && $7 > value {value = $7} END {print value + 0}' \
  "$artifact_footprint")

schema_count=$(awk -F '	' 'NR > 1 {count++} END {print count + 0}' \
  "$schema_footprint")
table_count=$(awk -F '	' 'NR > 1 {sum += $2} END {print sum + 0}' \
  "$schema_footprint")
field_count=$(awk -F '	' 'NR > 1 {sum += $3} END {print sum + 0}' \
  "$schema_footprint")
physical_leaf_count=$(awk -F '	' 'NR > 1 {sum += $4} END {print sum + 0}' \
  "$schema_footprint")
selector_count=$(awk -F '	' 'NR > 1 {sum += $5} END {print sum + 0}' \
  "$schema_footprint")
schema_scan_count=$(awk -F '	' 'NR > 1 {sum += $7} END {print sum + 0}' \
  "$schema_footprint")
schema_scan_bytes=$(awk -F '	' 'NR > 1 {sum += $8} END {print sum + 0}' \
  "$schema_footprint")

if [ "$artifact_scan_count" -ne "$scan_count" ] \
    || [ "$artifact_source_bytes" -ne "$source_bytes" ] \
    || [ "$artifact_family_bytes" -ne "$family_bytes" ] \
    || [ "$table_count" -ne "$scan_count" ] \
    || [ "$schema_scan_count" -ne "$scan_count" ] \
    || [ "$schema_scan_bytes" -ne "$source_bytes" ] \
    || ! awk -F '	' 'NR > 1 && $2 != $7 {exit 1}' "$schema_footprint"; then
  printf '%s\n' 'scan-code-size-check: normalized footprint evidence mismatch' >&2
  exit 1
fi

{
  printf 'commit=%s\n' "$(git rev-parse HEAD)"
  printf 'javaVersion=%s\n' "$($JAVA_HOME/bin/java -version 2>&1 | head -n 1)"
  printf 'javacVersion=%s\n' "$($JAVA_HOME/bin/javac -version 2>&1)"
  printf 'compileCommand=./mvnw -B -ntp -pl soma-examples -am -DskipTests clean compile\n'
  printf 'compileWallMillis=%s\n' "$compile_wall_millis"
  printf 'scanCount=%s\n' "$scan_count"
  printf 'sourceBytes=%s\n' "$source_bytes"
  printf 'sourceLines=%s\n' "$source_lines"
  printf 'topLevelClassBytes=%s\n' "$top_class_bytes"
  printf 'topClassBytes=%s\n' "$top_class_bytes"
  printf 'nestedClassBytes=%s\n' "$nested_bytes"
  printf 'nestedClassCount=%s\n' "$nested_count"
  printf 'familyClassBytes=%s\n' "$family_bytes"
  printf 'schemaCount=%s\n' "$schema_count"
  printf 'tableCount=%s\n' "$table_count"
  printf 'fieldCount=%s\n' "$field_count"
  printf 'physicalLeafCount=%s\n' "$physical_leaf_count"
  printf 'selectorCount=%s\n' "$selector_count"
  printf 'minimumScanSourceBytes=%s\n' "$minimum_scan_source_bytes"
  printf 'maximumScanSourceBytes=%s\n' "$maximum_scan_source_bytes"
  printf 'averageScanSourceBytes=%s\n' "$average_scan_source_bytes"
  printf 'maximumScanSourceLines=%s\n' "$maximum_scan_source_lines"
  printf 'maximumTopLevelClassBytes=%s\n' "$maximum_top_level_class_bytes"
  printf 'maximumScanFamilyClassBytes=%s\n' "$maximum_scan_family_bytes"
  printf 'regressionBudgetKind=fixed-candidate\n'
  printf 'normalizedEvidenceRole=diagnostic-only\n'
  printf 'generatedSourceAdmissionOwner=CodegenLimits.MAXIMUM_GENERATED_SOURCE_LENGTH\n'
  printf 'maximumSourceBytes=%s\n' "$maximum_source_bytes"
  printf 'maximumSourceLines=%s\n' "$maximum_source_lines"
  printf 'maximumFamilyClassBytes=%s\n' "$maximum_family_bytes"
  printf 'maximumNestedClassCount=%s\n' "$maximum_nested_count"
} >"$evidence"
shasum -a 256 "$evidence" \
  "$artifact_footprint" \
  "$schema_footprint" \
  soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/GeneratedScanPlan.java \
  soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/GeneratedScanEvaluation.java \
  >"$evidence_dir/checksums.sha256"

printf '%s\n' "scan-code-size-evidence: $evidence_dir"
printf '%s\n' 'scan-code-size-check: ok'
