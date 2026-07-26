#!/bin/sh

set -eu

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'dataflow-prototype: JAVA_HOME must point to Zulu JDK 8' >&2
  exit 1
fi

java_specification=$("$JAVA_HOME/bin/java" -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
java_vendor=$("$JAVA_HOME/bin/java" -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.vendor = //p' | head -n 1)
if [ "$java_specification" != '1.8' ] || [ "$java_vendor" != 'Azul Systems, Inc.' ]; then
  printf 'dataflow-prototype: requires Zulu JDK 8, found %s / %s\n' \
    "$java_vendor" "$java_specification" >&2
  exit 1
fi

prototype_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
root_dir=$(CDPATH= cd -- "$prototype_dir/../../../.." && pwd)
mkdir -p "$root_dir/target"
evidence_dir=$(mktemp -d "$root_dir/target/dataflow-architecture-prototype.XXXXXX")
api_classes="$evidence_dir/api-classes"
generated_classes="$evidence_dir/generated-classes"
consumer_classes="$evidence_dir/consumer-classes"
mkdir -p "$api_classes" "$generated_classes" "$consumer_classes"

"$JAVA_HOME/bin/javac" -Xlint:all -Werror -encoding UTF-8 -source 8 -target 8 -proc:none \
  -d "$api_classes" \
  "$prototype_dir/api/com/hgtech/soma/dataflow/prototype/PrototypeDataFlow.java"
"$JAVA_HOME/bin/javac" -Xlint:all -Werror -encoding UTF-8 -source 8 -target 8 -proc:none \
  -cp "$api_classes" -d "$generated_classes" \
  "$prototype_dir/generated/com/example/generated/WorkDataFlow.java"
"$JAVA_HOME/bin/javac" -Xlint:all -Werror -encoding UTF-8 -source 8 -target 8 -proc:none \
  -cp "$api_classes:$generated_classes" -d "$consumer_classes" \
  "$prototype_dir/consumer/com/example/consumer/ExternalConsumer.java"

"$JAVA_HOME/bin/java" -cp "$api_classes:$generated_classes:$consumer_classes" \
  com.example.consumer.ExternalConsumer

api_class_count=$(find "$api_classes" -type f -name '*.class' | wc -l | tr -d ' ')
generated_class_count=$(find "$generated_classes" -type f -name '*.class' | wc -l |
  tr -d ' ')
consumer_class_count=$(find "$consumer_classes" -type f -name '*.class' | wc -l |
  tr -d ' ')
api_class_bytes=$(find "$api_classes" -type f -name '*.class' -exec wc -c {} + |
  awk 'END {print $1}')
generated_class_bytes=$(find "$generated_classes" -type f -name '*.class' \
  -exec wc -c {} + | awk 'END {print $1}')
source_bytes=$(find "$prototype_dir" -type f -name '*.java' -exec wc -c {} + |
  awk 'END {print $1}')

{
  printf 'javaVendor=%s\n' "$java_vendor"
  printf 'javaSpecification=%s\n' "$java_specification"
  printf 'apiClassCount=%s\n' "$api_class_count"
  printf 'generatedClassCount=%s\n' "$generated_class_count"
  printf 'consumerClassCount=%s\n' "$consumer_class_count"
  printf 'apiClassBytes=%s\n' "$api_class_bytes"
  printf 'generatedClassBytes=%s\n' "$generated_class_bytes"
  printf 'sourceBytes=%s\n' "$source_bytes"
} >"$evidence_dir/metrics.properties"

printf 'dataflow-prototype-evidence: %s\n' "$evidence_dir"
printf 'dataflow-prototype-api-classes: %s / %s bytes\n' \
  "$api_class_count" "$api_class_bytes"
printf 'dataflow-prototype-generated-classes: %s / %s bytes\n' \
  "$generated_class_count" "$generated_class_bytes"
printf '%s\n' 'dataflow-prototype-check: ok'
