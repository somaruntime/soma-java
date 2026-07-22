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
{
  printf 'commit=%s\n' "$(git rev-parse HEAD)"
  printf 'javaVersion=%s\n' "$($JAVA_HOME/bin/java -version 2>&1 | head -n 1)"
  printf 'javacVersion=%s\n' "$($JAVA_HOME/bin/javac -version 2>&1)"
  printf 'compileCommand=./mvnw -B -ntp -pl soma-examples -am -DskipTests clean compile\n'
  printf 'compileWallMillis=%s\n' "$compile_wall_millis"
  printf 'scanCount=%s\n' "$scan_count"
  printf 'sourceBytes=%s\n' "$source_bytes"
  printf 'sourceLines=%s\n' "$source_lines"
  printf 'topClassBytes=%s\n' "$top_class_bytes"
  printf 'nestedClassBytes=%s\n' "$nested_bytes"
  printf 'nestedClassCount=%s\n' "$nested_count"
  printf 'familyClassBytes=%s\n' "$family_bytes"
  printf 'maximumSourceBytes=%s\n' "$maximum_source_bytes"
  printf 'maximumSourceLines=%s\n' "$maximum_source_lines"
  printf 'maximumFamilyClassBytes=%s\n' "$maximum_family_bytes"
  printf 'maximumNestedClassCount=%s\n' "$maximum_nested_count"
} >"$evidence"
shasum -a 256 "$evidence" \
  soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/GeneratedScanPlan.java \
  soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/GeneratedScanEvaluation.java \
  >"$evidence_dir/checksums.sha256"

printf '%s\n' "scan-code-size-evidence: $evidence_dir"
printf '%s\n' 'scan-code-size-check: ok'
