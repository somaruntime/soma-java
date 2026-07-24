#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'codegen-admission-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi

java_specification=$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
if [ "$java_specification" != '1.8' ]; then
  printf '%s\n' "codegen-admission-check: expected Java 8, got $java_specification" >&2
  exit 1
fi

processor_source=soma-processor/src/main/java/com/hgtech/soma/processor
for source in \
  SomaProcessor.java \
  SomaSchemaModel.java \
  DenseTableCodegenModel.java \
  DenseSelectorCodegenModel.java \
  DenseTableSourceGenerator.java \
  DenseTableSourceEmitter.java \
  DenseAuxiliarySourceEmitter.java \
  DenseScanExecutionSourceSupport.java \
  DenseSelectorSourceSupport.java \
  DenseExactIndexSourceEmitter.java; do
  if [ ! -f "$processor_source/$source" ]; then
    printf '%s\n' "codegen-admission-check: missing codegen responsibility owner $source" >&2
    exit 1
  fi
done

grep -F 'import static com.hgtech.soma.processor.SomaSchemaModel.*;' \
  "$processor_source/SomaProcessor.java" >/dev/null
grep -F 'static final class SchemaModel' "$processor_source/SomaSchemaModel.java" >/dev/null
grep -F 'static final class TableSpec' "$processor_source/DenseTableCodegenModel.java" >/dev/null
grep -F 'static final class SelectorParameter' \
  "$processor_source/DenseSelectorCodegenModel.java" >/dev/null
grep -F 'new DenseAuxiliarySourceEmitter' \
  "$processor_source/DenseTableSourceGenerator.java" >/dev/null
grep -F 'new DenseTableSourceEmitter' \
  "$processor_source/DenseTableSourceGenerator.java" >/dev/null
grep -F 'DenseSelectorCodegenModel.selectorPublicParameterTypes' \
  "$processor_source/SomaProcessor.java" >/dev/null
grep -F 'DenseScanExecutionSourceSupport.*' \
  "$processor_source/DenseTableSourceEmitter.java" >/dev/null

if grep -F 'static final class SchemaModel' "$processor_source/SomaProcessor.java" >/dev/null \
    || grep -F 'static final class TableSpec' \
      "$processor_source/DenseTableSourceGenerator.java" >/dev/null \
    || grep -F 'DenseTableSourceGenerator.' \
      "$processor_source/DenseExactIndexSourceEmitter.java" >/dev/null \
    || grep -F 'DenseSelectorSourceSupport' \
      "$processor_source/SomaProcessor.java" >/dev/null \
    || grep -F 'DenseAuxiliarySourceEmitter' \
      "$processor_source/DenseTableSourceEmitter.java" >/dev/null; then
  printf '%s\n' 'codegen-admission-check: processor/codegen responsibility boundary regressed' >&2
  exit 1
fi

annotations_jar=soma-annotations/target/soma-annotations-0.2.0-SNAPSHOT.jar
processor_jar=soma-processor/target/soma-processor-0.2.0-SNAPSHOT.jar
runtime_jar=soma-runtime-core/target/soma-runtime-core-0.2.0-SNAPSHOT.jar
for artifact in "$annotations_jar" "$processor_jar" "$runtime_jar"; do
  if [ ! -f "$artifact" ]; then
    printf '%s\n' "codegen-admission-check: missing artifact $artifact" >&2
    exit 1
  fi
done

mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/codegen-admission.XXXXXX")
sources=$evidence_dir/sources
mkdir -p "$sources"

package_path() {
  printf '%s' "$1" | tr . /
}

write_schema() {
  root=$1
  package_name=$2
  schema_name=$3
  generated_package=$4
  directory=$root/$(package_path "$package_name")
  mkdir -p "$directory"
  {
    printf '@SomaSchema(name = "%s", generatedPackage = "%s", version = "1")\n' \
      "$schema_name" "$generated_package"
    printf 'package %s;\n\n' "$package_name"
    printf 'import com.hgtech.soma.annotation.SomaSchema;\n'
  } >"$directory/package-info.java"
}

generate_value_chain() {
  root=$1
  package_name=$2
  depth=$3
  write_schema "$root" "$package_name" "chain_$depth" "$package_name.generated"
  directory=$root/$(package_path "$package_name")
  index=1
  while [ "$index" -le "$depth" ]; do
    next=$((index + 1))
    file=$(printf '%s/Value%02d.java' "$directory" "$index")
    {
      printf 'package %s;\n\n' "$package_name"
      printf 'import com.hgtech.soma.annotation.SomaField;\n'
      printf 'import com.hgtech.soma.annotation.SomaValue;\n\n'
      printf '@SomaValue\npublic class Value%02d {\n' "$index"
      if [ "$index" -lt "$depth" ]; then
        printf '    @SomaField public Value%02d next;\n' "$next"
      else
        printf '    @SomaField public int value;\n'
      fi
      printf '}\n'
    } >"$file"
    index=$next
  done
}

generate_wide_value() {
  root=$1
  package_name=$2
  type_name=$3
  field_type=$4
  count=$5
  write_schema "$root" "$package_name" "wide_${type_name}" "$package_name.generated"
  directory=$root/$(package_path "$package_name")
  file=$directory/$type_name.java
  {
    printf 'package %s;\n\n' "$package_name"
    printf 'import com.hgtech.soma.annotation.SomaField;\n'
    printf 'import com.hgtech.soma.annotation.SomaValue;\n\n'
    printf '@SomaValue\npublic class %s {\n' "$type_name"
    index=1
    while [ "$index" -le "$count" ]; do
      printf '    @SomaField public %s f%03d;\n' "$field_type" "$index"
      index=$((index + 1))
    done
    printf '}\n'
  } >"$file"
}

generate_wide_table() {
  root=$1
  package_name=$2
  type_name=$3
  count=$4
  write_schema "$root" "$package_name" "wide_${type_name}" "$package_name.generated"
  directory=$root/$(package_path "$package_name")
  file=$directory/$type_name.java
  {
    printf 'package %s;\n\n' "$package_name"
    printf 'import com.hgtech.soma.annotation.SomaField;\n'
    printf 'import com.hgtech.soma.annotation.SomaTable;\n\n'
    printf '@SomaTable\npublic final class %s {\n' "$type_name"
    index=1
    while [ "$index" -le "$count" ]; do
      printf '    @SomaField public int f%03d;\n' "$index"
      index=$((index + 1))
    done
    printf '    public %s() {}\n}\n' "$type_name"
  } >"$file"
}

generate_many_tables() {
  root=$1
  package_name=$2
  count=$3
  selectors=$4
  write_schema "$root" "$package_name" "many_tables_$count" "$package_name.generated"
  directory=$root/$(package_path "$package_name")
  index=1
  while [ "$index" -le "$count" ]; do
    file=$(printf '%s/Table%03d.java' "$directory" "$index")
    {
      printf 'package %s;\n\n' "$package_name"
      printf 'import com.hgtech.soma.annotation.SomaField;\n'
      if [ "$selectors" -gt 0 ]; then
        printf 'import com.hgtech.soma.annotation.SomaIndex;\n'
      fi
      printf 'import com.hgtech.soma.annotation.SomaTable;\n\n'
      printf '@SomaTable\n'
      selector=1
      while [ "$selector" -le "$selectors" ]; do
        printf '@SomaIndex(name = "i%03d_%03d", fields = {"value"})\n' \
          "$index" "$selector"
        selector=$((selector + 1))
      done
      printf 'public final class Table%03d {\n' "$index"
      printf '    @SomaField public int value;\n'
      printf '    public Table%03d() {}\n}\n' "$index"
    } >"$file"
    index=$((index + 1))
  done
}

run_success() {
  name=$1
  source_root=$2
  output=$evidence_dir/$name
  mkdir -p "$output/classes" "$output/generated"
  "$JAVA_HOME/bin/javac" \
    -encoding UTF-8 -source 8 -target 8 -proc:only \
    -cp "$annotations_jar:$processor_jar:$runtime_jar" \
    -processorpath "$processor_jar:$annotations_jar" \
    -processor com.hgtech.soma.processor.SomaProcessor \
    -Xplugin:SomaValue \
    -s "$output/generated" -d "$output/classes" \
    $(find "$source_root" -type f -name '*.java' | LC_ALL=C sort) \
    >"$output/compile.log" 2>&1
}

run_full_success() {
  name=$1
  source_root=$2
  output=$evidence_dir/$name
  mkdir -p "$output/classes" "$output/generated"
  "$JAVA_HOME/bin/javac" \
    -encoding UTF-8 -source 8 -target 8 \
    -cp "$annotations_jar:$processor_jar:$runtime_jar" \
    -processorpath "$processor_jar:$annotations_jar" \
    -processor com.hgtech.soma.processor.SomaProcessor \
    -Xplugin:SomaValue \
    -s "$output/generated" -d "$output/classes" \
    $(find "$source_root" -type f -name '*.java' | LC_ALL=C sort) \
    >"$output/compile.log" 2>&1
}

assert_no_product_output() {
  name=$1
  output=$evidence_dir/$name
  if find "$output/generated" -type f -name '*.java' -print -quit | grep . >/dev/null \
      || find "$output/classes/META-INF/soma" -type f -print -quit 2>/dev/null | grep . >/dev/null; then
    printf '%s\n' "codegen-admission-check: $name emitted product artifacts before admission completed" >&2
    exit 1
  fi
}

run_failure() {
  name=$1
  source_root=$2
  diagnostic=$3
  dimension=${4:-}
  output=$evidence_dir/$name
  mkdir -p "$output/classes" "$output/generated"
  if "$JAVA_HOME/bin/javac" \
    -encoding UTF-8 -source 8 -target 8 -proc:only \
    -cp "$annotations_jar:$processor_jar:$runtime_jar" \
    -processorpath "$processor_jar:$annotations_jar" \
    -processor com.hgtech.soma.processor.SomaProcessor \
    -Xplugin:SomaValue \
    -s "$output/generated" -d "$output/classes" \
    $(find "$source_root" -type f -name '*.java' | LC_ALL=C sort) \
    >"$output/compile.log" 2>&1; then
    printf '%s\n' "codegen-admission-check: $name unexpectedly compiled" >&2
    exit 1
  fi
  grep -F "[$diagnostic]" "$output/compile.log" >/dev/null
  if [ -n "$dimension" ]; then
    grep -F "dimension=$dimension" "$output/compile.log" >/dev/null
  fi
  assert_no_product_output "$name"
}

# Generated source builder必须接受exact limit并拒绝limit+1。
"$JAVA_HOME/bin/java" \
  -cp soma-processor/target/test-classes:soma-processor/target/classes \
  com.hgtech.soma.processor.CodegenAdmissionCheck \
  >"$evidence_dir/source-builder.log"

depth32=$sources/depth32
depth33=$sources/depth33
generate_value_chain "$depth32" com.example.admission.depth32 32
generate_value_chain "$depth33" com.example.admission.depth33 33
run_success depth32 "$depth32"
run_failure depth33 "$depth33" SOMA-GEN-003 value-depth

value_int254=$sources/value-int254
value_int255=$sources/value-int255
value_long127=$sources/value-long127
value_long128=$sources/value-long128
generate_wide_value "$value_int254" com.example.admission.valueint254 WideInt254 int 254
generate_wide_value "$value_int255" com.example.admission.valueint255 WideInt255 int 255
generate_wide_value "$value_long127" com.example.admission.valuelong127 WideLong127 long 127
generate_wide_value "$value_long128" com.example.admission.valuelong128 WideLong128 long 128
run_success value-int254 "$value_int254"
run_failure value-int255 "$value_int255" SOMA-GEN-003 value-constructor-slots
run_success value-long127 "$value_long127"
run_failure value-long128 "$value_long128" SOMA-GEN-003 value-constructor-slots

table254=$sources/table254
table255=$sources/table255
table256=$sources/table256
table257=$sources/table257
generate_wide_table "$table254" com.example.admission.table254 WideTable254 254
generate_wide_table "$table255" com.example.admission.table255 WideTable255 255
generate_wide_table "$table256" com.example.admission.table256 WideTable256 256
generate_wide_table "$table257" com.example.admission.table257 WideTable257 257
run_success table254 "$table254"
grep -F 'public WideTable254Batch addValues(int f001' \
  "$evidence_dir/table254/generated/com/example/admission/table254/generated/WideTable254Batch.java" >/dev/null
run_success table255 "$table255"
if grep -F 'public WideTable255Batch addValues(int f001' \
  "$evidence_dir/table255/generated/com/example/admission/table255/generated/WideTable255Batch.java" >/dev/null; then
  printf '%s\n' 'codegen-admission-check: receiver+255-slot direct Batch overload was emitted' >&2
  exit 1
fi
# Representative success boundary must survive full generated-source compile/load admission,
# not merely processor-only generation.
run_full_success table256 "$table256"
test -f "$evidence_dir/table256/classes/com/example/admission/table256/generated/WideTable256Table.class"
grep -F 'private final class ExactIndexStage{ExactIndexStage(){}' \
  "$evidence_dir/table256/generated/com/example/admission/table256/generated/WideTable256Table.java" \
  >/dev/null
run_failure table257 "$table257" SOMA-GEN-003 table-physical-leaves

many256=$sources/many256
many257=$sources/many257
generate_many_tables "$many256" com.example.admission.many256 256 0
generate_many_tables "$many257" com.example.admission.many257 257 0
run_success many256 "$many256"
run_failure many257 "$many257" SOMA-GEN-003 schema-tables

# Direct及nested Value都必须闭包在同一schema compilation graph。
cross_direct=$sources/cross-direct
write_schema "$cross_direct" com.example.admission.crossdirect.a cross_direct_a \
  com.example.admission.crossdirect.a.generated
write_schema "$cross_direct" com.example.admission.crossdirect.b cross_direct_b \
  com.example.admission.crossdirect.b.generated
mkdir -p "$cross_direct/com/example/admission/crossdirect/a" \
  "$cross_direct/com/example/admission/crossdirect/b"
{
  printf 'package com.example.admission.crossdirect.a;\n'
  printf 'import com.hgtech.soma.annotation.SomaField;\n'
  printf 'import com.hgtech.soma.annotation.SomaValue;\n'
  printf '@SomaValue public class ExternalValue { @SomaField public int value; }\n'
} >"$cross_direct/com/example/admission/crossdirect/a/ExternalValue.java"
{
  printf 'package com.example.admission.crossdirect.b;\n'
  printf 'import com.example.admission.crossdirect.a.ExternalValue;\n'
  printf 'import com.hgtech.soma.annotation.SomaField;\n'
  printf 'import com.hgtech.soma.annotation.SomaTable;\n'
  printf '@SomaTable public final class DirectTable {\n'
  printf '  @SomaField public ExternalValue value; public DirectTable() {}\n}\n'
} >"$cross_direct/com/example/admission/crossdirect/b/DirectTable.java"
run_failure cross-direct "$cross_direct" SOMA-VALUE-008

cross_nested=$sources/cross-nested
write_schema "$cross_nested" com.example.admission.crossnested.a cross_nested_a \
  com.example.admission.crossnested.a.generated
write_schema "$cross_nested" com.example.admission.crossnested.b cross_nested_b \
  com.example.admission.crossnested.b.generated
mkdir -p "$cross_nested/com/example/admission/crossnested/a" \
  "$cross_nested/com/example/admission/crossnested/b"
{
  printf 'package com.example.admission.crossnested.a;\n'
  printf 'import com.hgtech.soma.annotation.SomaField;\n'
  printf 'import com.hgtech.soma.annotation.SomaValue;\n'
  printf '@SomaValue public class ExternalValue { @SomaField public int value; }\n'
} >"$cross_nested/com/example/admission/crossnested/a/ExternalValue.java"
{
  printf 'package com.example.admission.crossnested.b;\n'
  printf 'import com.example.admission.crossnested.a.ExternalValue;\n'
  printf 'import com.hgtech.soma.annotation.SomaField;\n'
  printf 'import com.hgtech.soma.annotation.SomaValue;\n'
  printf '@SomaValue public class LocalValue { @SomaField public ExternalValue nested; }\n'
} >"$cross_nested/com/example/admission/crossnested/b/LocalValue.java"
run_failure cross-nested "$cross_nested" SOMA-VALUE-008

# Existing FQN、cross-schema generated FQN及Object final member必须在Filer write前拒绝。
existing_fqn=$sources/existing-fqn
write_schema "$existing_fqn" com.example.admission.existing existing_fqn \
  com.example.admission.existing.generated
mkdir -p "$existing_fqn/com/example/admission/existing/generated"
{
  printf 'package com.example.admission.existing;\n'
  printf 'import com.hgtech.soma.annotation.*;\n'
  printf '@SomaTable public final class Existing {\n'
  printf '  @SomaField public int value; public Existing() {}\n}\n'
} >"$existing_fqn/com/example/admission/existing/Existing.java"
printf 'package com.example.admission.existing.generated; public final class ExistingTable {}\n' \
  >"$existing_fqn/com/example/admission/existing/generated/ExistingTable.java"
run_failure existing-fqn "$existing_fqn" SOMA-GEN-001

generated_fqn=$sources/generated-fqn
write_schema "$generated_fqn" com.example.admission.generatedfqn.a generated_fqn_a \
  com.example.admission.generatedfqn.shared
write_schema "$generated_fqn" com.example.admission.generatedfqn.b generated_fqn_b \
  com.example.admission.generatedfqn.shared
for package_name in a b; do
  directory=$generated_fqn/com/example/admission/generatedfqn/$package_name
  mkdir -p "$directory"
  {
    printf 'package com.example.admission.generatedfqn.%s;\n' "$package_name"
    printf 'import com.hgtech.soma.annotation.*;\n'
    printf '@SomaTable public final class Same {\n'
    printf '  @SomaField public int value; public Same() {}\n}\n'
  } >"$directory/Same.java"
done
run_failure generated-fqn "$generated_fqn" SOMA-GEN-001

object_member=$sources/object-member
write_schema "$object_member" com.example.admission.objectmember object_member \
  com.example.admission.objectmember.generated
mkdir -p "$object_member/com/example/admission/objectmember"
{
  printf 'package com.example.admission.objectmember;\n'
  printf 'import com.hgtech.soma.annotation.*;\n'
  printf '@SomaTable public final class ObjectMember {\n'
  printf '  @SomaField public int wait; public ObjectMember() {}\n}\n'
} >"$object_member/com/example/admission/objectmember/ObjectMember.java"
run_failure object-member "$object_member" SOMA-GEN-001

# 一个极多selector的单table必须以per-source budget失败，不泄漏partial output。
source_limit=$sources/source-limit
write_schema "$source_limit" com.example.admission.sourcelimit source_limit \
  com.example.admission.sourcelimit.generated
mkdir -p "$source_limit/com/example/admission/sourcelimit"
{
  printf 'package com.example.admission.sourcelimit;\n'
  printf 'import com.hgtech.soma.annotation.*;\n'
  printf '@SomaTable\n'
  selector=1
  while [ "$selector" -le 5000 ]; do
    printf '@SomaIndex(name = "i%04d", fields = {"value"})\n' "$selector"
    selector=$((selector + 1))
  done
  printf 'public final class SourceLimit {\n'
  printf '  @SomaField public int value; public SourceLimit() {}\n}\n'
} >"$source_limit/com/example/admission/sourcelimit/SourceLimit.java"
run_failure source-limit "$source_limit" SOMA-GEN-003 generated-source-utf16

# 256-table plan保持单source有界，但schema aggregate必须受独立总量约束。
schema_total=$sources/schema-total
generate_many_tables "$schema_total" com.example.admission.schematotal 256 40
run_failure schema-total "$schema_total" SOMA-GEN-003 schema-generated-source-utf16

# 后续round由其他processor生成的SOMA declaration必须fail closed。
late_processor_source=$evidence_dir/late-processor-source
late_processor_classes=$evidence_dir/late-processor-classes
mkdir -p "$late_processor_source/com/example/admission/late" "$late_processor_classes"
{
  printf 'package com.example.admission.late;\n'
  printf 'import java.io.*; import java.util.*; import javax.annotation.processing.*;\n'
  printf 'import javax.lang.model.*; import javax.lang.model.element.*;\n'
  printf 'import javax.tools.JavaFileObject;\n'
  printf '@SupportedAnnotationTypes("*") @SupportedSourceVersion(SourceVersion.RELEASE_8)\n'
  printf 'public final class LateSomaProcessor extends AbstractProcessor {\n'
  printf '  private boolean generated;\n'
  printf '  public boolean process(Set<? extends TypeElement> a, RoundEnvironment r) {\n'
  printf '    if (!generated && !r.processingOver()) { generated = true; try {\n'
  printf '      JavaFileObject f = processingEnv.getFiler().createSourceFile('
  printf '"com.example.admission.lateinput.LateRow");\n'
  printf '      Writer w = f.openWriter(); w.write('
  printf '"package com.example.admission.lateinput; import com.hgtech.soma.annotation.*; '
  printf '@SomaTable public final class LateRow { @SomaField public int value; public LateRow() {} }");'
  printf ' w.close();\n'
  printf '    } catch (IOException e) { throw new RuntimeException(e); } } return false; }\n'
  printf '}\n'
} >"$late_processor_source/com/example/admission/late/LateSomaProcessor.java"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 8 -target 8 -proc:none \
  -cp "$annotations_jar" -d "$late_processor_classes" \
  "$late_processor_source/com/example/admission/late/LateSomaProcessor.java"
late_input=$sources/late-input
write_schema "$late_input" com.example.admission.lateinput late_input \
  com.example.admission.lateinput.generated
late_output=$evidence_dir/late-round
mkdir -p "$late_output/classes" "$late_output/generated"
if "$JAVA_HOME/bin/javac" \
  -encoding UTF-8 -source 8 -target 8 -proc:only \
  -cp "$annotations_jar:$processor_jar:$runtime_jar" \
  -processorpath "$late_processor_classes:$processor_jar:$annotations_jar" \
  -processor com.hgtech.soma.processor.SomaProcessor,com.example.admission.late.LateSomaProcessor \
  -Xplugin:SomaValue -s "$late_output/generated" -d "$late_output/classes" \
  $(find "$late_input" -type f -name '*.java' | LC_ALL=C sort) \
  >"$late_output/compile.log" 2>&1; then
  printf '%s\n' 'codegen-admission-check: late SOMA declaration unexpectedly compiled' >&2
  exit 1
fi
grep -F '[SOMA-COMP-007]' "$late_output/compile.log" >/dev/null
if find "$late_output/classes/META-INF/soma" -type f -print -quit 2>/dev/null | grep . >/dev/null; then
  printf '%s\n' 'codegen-admission-check: late declaration published schema resources' >&2
  exit 1
fi

if grep -E 'Exception in thread|^[[:space:]]+at (com\.hgtech|com\.sun\.tools)' \
  "$evidence_dir"/*/compile.log >/dev/null 2>&1; then
  printf '%s\n' 'codegen-admission-check: supported diagnostic leaked internal stack' >&2
  exit 1
fi

"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
printf '%s\n' "codegen-admission-evidence: $evidence_dir"
printf '%s\n' 'codegen-admission-check: ok'
