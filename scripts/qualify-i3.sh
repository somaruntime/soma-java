#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_root"

if [ -z "${JAVA_HOME:-}" ]; then
    echo "i3-qualification: JAVA_HOME must select the qualified Java 8 JDK" >&2
    exit 1
fi

java_cmd="$JAVA_HOME/bin/java"
javac_cmd="$JAVA_HOME/bin/javac"
javap_cmd="$JAVA_HOME/bin/javap"
jar_cmd="$JAVA_HOME/bin/jar"
for tool in "$java_cmd" "$javac_cmd" "$javap_cmd" "$jar_cmd"; do
    if [ ! -x "$tool" ]; then
        echo "i3-qualification: missing JDK tool: $tool" >&2
        exit 1
    fi
done
if ! "$java_cmd" -version 2>&1 | grep -q 'version "1\.8\.' \
        || ! "$javac_cmd" -version 2>&1 | grep -q '^javac 1\.8\.'; then
    echo "i3-qualification: JAVA_HOME is not a Java 8 JDK" >&2
    exit 1
fi

# This is the current qualification owner. Historical I0-I2 scripts preserve
# their point-in-time surface goldens and therefore are not recursively called
# after I3 has legitimately admitted callback filters and query operations.
mvn clean package

runtime_jar=
for candidate in "$repo_root"/soma-runtime/target/soma-runtime-*.jar; do
    case "$candidate" in
        *-sources.jar|*-javadoc.jar) ;;
        *) runtime_jar=$candidate ;;
    esac
done
processor_jar=
for candidate in "$repo_root"/soma-processor/target/soma-processor-*.jar; do
    case "$candidate" in
        *-sources.jar|*-javadoc.jar) ;;
        *) processor_jar=$candidate ;;
    esac
done
if [ -z "$runtime_jar" ] || [ -z "$processor_jar" ]; then
    echo "i3-qualification: production artifacts not found" >&2
    exit 1
fi

work_root=$(mktemp -d "${TMPDIR:-/tmp}/soma-i3-qualification.XXXXXX")
cleanup() {
    case "$work_root" in
        */soma-i3-qualification.*) rm -rf -- "$work_root" ;;
        *) echo "i3-qualification: refusing unsafe cleanup target" >&2 ;;
    esac
}
trap cleanup EXIT HUP INT TERM

classes="$work_root/classes"
generated="$work_root/generated"
second_classes="$work_root/second-classes"
second_generated="$work_root/second-generated"
mkdir -p "$classes" "$generated" "$second_classes" "$second_generated"

schema_sources="$work_root/schema-sources.txt"
find "$repo_root/tests/i3-consumer/src/main/java/example/i3/schema" \
        -name '*.java' -type f -print \
    | LC_ALL=C sort > "$schema_sources"
reverse_schema_sources="$work_root/reverse-schema-sources.txt"
LC_ALL=C sort -r "$schema_sources" > "$reverse_schema_sources"

compile_schema() {
    output_classes=$1
    output_generated=$2
    source_list=$3
    "$javac_cmd" \
        -source 8 \
        -target 8 \
        -encoding UTF-8 \
        -classpath "$runtime_jar" \
        -processorpath "$processor_jar:$runtime_jar" \
        -processor io.github.somaruntime.soma.processor.SomaProcessor \
        -Asoma.fullSourceSet=true \
        -d "$output_classes" \
        -s "$output_generated" \
        @"$source_list"
}

compile_schema "$classes" "$generated" "$schema_sources"
compile_schema "$second_classes" "$second_generated" \
    "$reverse_schema_sources"
diff -qr "$generated" "$second_generated"

"$javac_cmd" \
    -source 8 \
    -target 8 \
    -encoding UTF-8 \
    -proc:none \
    -classpath "$classes:$runtime_jar" \
    -d "$classes" \
    "$repo_root/tests/i3-consumer/src/main/java/example/i3/I3ConsumerMain.java" \
    "$repo_root/tests/i3-consumer/src/main/java/example/i3/I3PlanGoldenMain.java"

hash_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

generated_files="$work_root/generated-files.txt"
find "$generated/example/i3" -name '*.java' -type f -print \
    | LC_ALL=C sort > "$generated_files"
generated_hashes="$work_root/generated-source.sha256"
: > "$generated_hashes"
while IFS= read -r generated_file; do
    generated_name=${generated_file#"$generated/example/i3/"}
    printf '%s  %s\n' "$(hash_file "$generated_file")" "$generated_name" \
        >> "$generated_hashes"
done < "$generated_files"
diff -u "$repo_root/tests/i3-consumer/golden/generated-source.sha256" \
    "$generated_hashes"

generated_class_candidates="$work_root/generated-class-candidates.txt"
: > "$generated_class_candidates"
while IFS= read -r generated_file; do
    generated_type=${generated_file#"$generated/"}
    generated_type=${generated_type%.java}
    generated_class_directory="$classes/${generated_type%/*}"
    generated_top_level=${generated_type##*/}
    find "$generated_class_directory" -maxdepth 1 -type f \
        \( -name "$generated_top_level.class" \
            -o -name "$generated_top_level\$*.class" \) -print
done < "$generated_files" \
    | sed "s#^$classes/##; s#/#.#g; s#\.class\$##" \
    | LC_ALL=C sort -u > "$generated_class_candidates"

generated_public_classes="$work_root/generated-public.classes"
: > "$generated_public_classes"
while IFS= read -r generated_class; do
    generated_declaration=$(LC_ALL=C "$javap_cmd" -public \
        -classpath "$classes:$runtime_jar" "$generated_class" | sed -n '2p')
    case "$generated_declaration" in
        public\ *) printf '%s\n' "$generated_class" \
            >> "$generated_public_classes" ;;
    esac
done < "$generated_class_candidates"
diff -u "$repo_root/tests/i3-consumer/golden/generated-public.classes" \
    "$generated_public_classes"

generated_public_javap="$work_root/generated-public.javap.txt"
generated_verbose_javap="$work_root/generated-public.javap.verbose.txt"
: > "$generated_public_javap"
: > "$generated_verbose_javap"
while IFS= read -r generated_class; do
    printf '===== %s =====\n' "$generated_class" \
        >> "$generated_public_javap"
    LC_ALL=C "$javap_cmd" -public -classpath "$classes:$runtime_jar" \
        "$generated_class" >> "$generated_public_javap"
    printf '===== %s =====\n' "$generated_class" \
        >> "$generated_verbose_javap"
    LC_ALL=C "$javap_cmd" -v -public -classpath "$classes:$runtime_jar" \
        "$generated_class" \
        | sed \
            -e '/^Classfile /d' \
            -e '/^  Last modified /d' \
            -e '/^  MD5 checksum /d' \
            -e '/^  SHA-256 checksum /d' \
        >> "$generated_verbose_javap"
done < "$generated_public_classes"
grep -q 'major version: 52' "$generated_verbose_javap"
if grep -q 'io.github.somaruntime.soma.internal' "$generated_public_javap"; then
    echo "i3-qualification: generated public signature leaks internal type" >&2
    exit 1
fi
generated_public_count=$(wc -l < "$generated_public_classes" | tr -d ' ')
generated_public_digest=$(hash_file "$generated_public_javap")
generated_public_digest_file="$work_root/generated-public.javap.sha256"
printf '%s\n' \
    'format=soma-generated-public-javap-v1' \
    'tool=javap -public' \
    "class.count=$generated_public_count" \
    "sha256=$generated_public_digest" \
    > "$generated_public_digest_file"
diff -u "$repo_root/tests/i3-consumer/golden/generated-public.javap.sha256" \
    "$generated_public_digest_file"
generated_verbose_digest_file="$work_root/generated-public.javap.verbose.sha256"
printf '%s\n' \
    'format=soma-generated-public-javap-verbose-v1' \
    'tool=javap -v -public, normalized path/time/checksum' \
    "class.count=$generated_public_count" \
    "sha256=$(hash_file "$generated_verbose_javap")" \
    > "$generated_verbose_digest_file"
diff -u \
    "$repo_root/tests/i3-consumer/golden/generated-public.javap.verbose.sha256" \
    "$generated_verbose_digest_file"

"$java_cmd" -Xms128m -Xmx1g -classpath "$classes:$runtime_jar" \
    example.i3.I3ConsumerMain
"$java_cmd" -Xms128m -Xmx1g -classpath "$classes:$runtime_jar" \
    example.i3.I3PlanGoldenMain > "$work_root/optimizer-plans.txt"
diff -u "$repo_root/tests/i3-consumer/golden/optimizer-plans.txt" \
    "$work_root/optimizer-plans.txt"

# Recompile and run the complete I2 schema/type/storage breadth consumer against
# the current renderer/runtime. I2's old exact query-surface goldens remain
# historical, while these behavior checks prevent Enum, Value, nullable,
# keyless, multi-Index, relation-Table and scale regressions.
i2_classes="$work_root/i2-classes"
i2_generated="$work_root/i2-generated"
mkdir -p "$i2_classes" "$i2_generated"
i2_schema_sources="$work_root/i2-schema-sources.txt"
find "$repo_root/tests/i2-consumer/src/main/java/example/i2/schema" \
        -name '*.java' -type f -print \
    | LC_ALL=C sort > "$i2_schema_sources"
printf '%s\n' \
    "$repo_root/tests/i2-consumer/src/main/java/example/i2/Status.java" \
    "$repo_root/tests/i2-consumer/src/main/java/example/i2/Payload.java" \
    >> "$i2_schema_sources"
compile_schema "$i2_classes" "$i2_generated" "$i2_schema_sources"
"$javac_cmd" \
    -source 8 \
    -target 8 \
    -encoding UTF-8 \
    -proc:none \
    -classpath "$i2_classes:$runtime_jar" \
    -d "$i2_classes" \
    "$repo_root/tests/i2-consumer/src/main/java/example/i2/I2ConsumerMain.java" \
    "$repo_root/tests/i2-consumer/src/main/java/example/i2/I2ScaleMain.java"
"$java_cmd" -Xms128m -Xmx1g -classpath "$i2_classes:$runtime_jar" \
    example.i2.I2ConsumerMain
"$java_cmd" -Xms256m -Xmx2g -classpath "$i2_classes:$runtime_jar" \
    example.i2.I2ScaleMain

negative_output="$work_root/negative.txt"
negative_classes="$work_root/negative-classes"
mkdir -p "$negative_classes"
expect_compile_failure() {
    source_file=$1
    expected_pattern=$2
    if "$javac_cmd" \
            -source 8 \
            -target 8 \
            -encoding UTF-8 \
            -proc:none \
            -classpath "$classes:$runtime_jar" \
            -d "$negative_classes" \
            "$source_file" > "$negative_output" 2>&1; then
        echo "i3-qualification: expected compile failure: $source_file" >&2
        exit 1
    fi
    if ! grep -Eq "$expected_pattern" "$negative_output"; then
        echo "i3-qualification: expected diagnostic pattern missing: $expected_pattern" >&2
        cat "$negative_output" >&2
        exit 1
    fi
}

negative_root="$repo_root/tests/i3-consumer/src/negative/java/example/i3"
expect_compile_failure "$negative_root/TableDistinctNegative.java" 'distinct'
expect_compile_failure "$negative_root/FieldMutationNegative.java" 'remove'
expect_compile_failure "$negative_root/SelectionMutationNegative.java" 'remove'
expect_compile_failure "$negative_root/MappedNoArgArrayNegative.java" 'toArray'
expect_compile_failure "$negative_root/MappedArrayFactoryNegative.java" 'toArray'
expect_compile_failure "$negative_root/ObjectEqualityNegative.java" 'eq'
expect_compile_failure "$negative_root/ParallelBeforeI6Negative.java" 'parallel'
expect_compile_failure "$negative_root/FlatMapNegative.java" 'flatMap'

"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    example.i3.EventTable > "$work_root/event-table-public.txt"
"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    'example.i3.EventTable$Selection' > "$work_root/selection-public.txt"
"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    'example.i3.EventTable$AmountField' > "$work_root/amount-field-public.txt"
"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    'example.i3.EventTable$PayloadField' > "$work_root/payload-field-public.txt"

grep -q 'filter(io.github.somaruntime.soma.SomaExpression' \
    "$work_root/event-table-public.txt"
grep -q 'filter(io.github.somaruntime.soma.SomaPredicate' \
    "$work_root/event-table-public.txt"
grep -q 'mapToLong(example.i3.EventTable\$AmountField)' \
    "$work_root/event-table-public.txt"
grep -q 'public long sum();' "$work_root/amount-field-public.txt"
! grep -q ' sum();' "$work_root/payload-field-public.txt"
! grep -q ' remove(' "$work_root/selection-public.txt"
! grep -q ' parallel(' "$work_root/event-table-public.txt"
! grep -Rq 'class ReadStream' "$generated"

# Recheck full-regeneration cleanup using the already admitted relation/value
# breadth fixture, but compile it directly against the current two artifacts so
# no mutable local Maven repository is part of this evidence.
regen_initial_classes="$work_root/regen-initial-classes"
regen_initial_generated="$work_root/regen-initial-generated"
regen_renamed_classes="$work_root/regen-renamed-classes"
regen_renamed_generated="$work_root/regen-renamed-generated"
mkdir -p "$regen_initial_classes" "$regen_initial_generated" \
    "$regen_renamed_classes" "$regen_renamed_generated"
find "$repo_root/tests/i2-regeneration/snapshots/initial" \
        -name '*.java' -type f -print \
    | LC_ALL=C sort > "$work_root/regen-initial.txt"
find "$repo_root/tests/i2-regeneration/snapshots/renamed" \
        -name '*.java' -type f -print \
    | LC_ALL=C sort > "$work_root/regen-renamed.txt"
compile_schema "$regen_initial_classes" "$regen_initial_generated" \
    "$work_root/regen-initial.txt"
compile_schema "$regen_renamed_classes" "$regen_renamed_generated" \
    "$work_root/regen-renamed.txt"
test -f "$regen_initial_generated/example/i2stale/AlphaTable.java"
test -f "$regen_initial_generated/example/i2stale/RelationTable.java"
test -f "$regen_renamed_generated/example/i2stale/GammaTable.java"
test -f "$regen_renamed_generated/example/i2stale/RenamedRelationTable.java"
test ! -e "$regen_renamed_generated/example/i2stale/AlphaTable.java"
test ! -e "$regen_renamed_generated/example/i2stale/RelationTable.java"

runtime_inventory="$work_root/runtime-jar.txt"
processor_inventory="$work_root/processor-jar.txt"
"$jar_cmd" tf "$runtime_jar" > "$runtime_inventory"
"$jar_cmd" tf "$processor_jar" > "$processor_inventory"
! grep -q '^org/junit/' "$runtime_inventory"
! grep -q '^org/junit/' "$processor_inventory"
! grep -q 'io/github/somaruntime/soma/ReadStream.class' "$runtime_inventory"

# Freeze the complete shared public runtime ABI, not only generated nested
# classes. This makes every new callback/stream/summary carrier observable.
runtime_class_candidates="$work_root/runtime-class-candidates.txt"
sed -n '/^io\/github\/somaruntime\/soma\/[^/]*\.class$/p' \
        "$runtime_inventory" \
    | sed 's#/#.#g; s#\.class$##' \
    | LC_ALL=C sort -u > "$runtime_class_candidates"
runtime_public_classes="$work_root/runtime-public.classes"
: > "$runtime_public_classes"
while IFS= read -r runtime_class; do
    runtime_declaration=$(LC_ALL=C "$javap_cmd" -public \
        -classpath "$runtime_jar" "$runtime_class" | sed -n '2p')
    case "$runtime_declaration" in
        public\ *) printf '%s\n' "$runtime_class" \
            >> "$runtime_public_classes" ;;
    esac
done < "$runtime_class_candidates"
diff -u "$repo_root/tests/i3-consumer/golden/runtime-public.classes" \
    "$runtime_public_classes"
runtime_public_javap="$work_root/runtime-public.javap.txt"
runtime_verbose_javap="$work_root/runtime-public.javap.verbose.txt"
: > "$runtime_public_javap"
: > "$runtime_verbose_javap"
while IFS= read -r runtime_class; do
    printf '===== %s =====\n' "$runtime_class" >> "$runtime_public_javap"
    LC_ALL=C "$javap_cmd" -public -classpath "$runtime_jar" \
        "$runtime_class" >> "$runtime_public_javap"
    printf '===== %s =====\n' "$runtime_class" \
        >> "$runtime_verbose_javap"
    LC_ALL=C "$javap_cmd" -v -public -classpath "$runtime_jar" \
        "$runtime_class" \
        | sed \
            -e '/^Classfile /d' \
            -e '/^  Last modified /d' \
            -e '/^  MD5 checksum /d' \
            -e '/^  SHA-256 checksum /d' \
        >> "$runtime_verbose_javap"
done < "$runtime_public_classes"
grep -q 'major version: 52' "$runtime_verbose_javap"
if grep -q 'io.github.somaruntime.soma.internal' "$runtime_public_javap"; then
    echo "i3-qualification: runtime public signature leaks internal type" >&2
    exit 1
fi
runtime_public_count=$(wc -l < "$runtime_public_classes" | tr -d ' ')
runtime_public_digest=$(hash_file "$runtime_public_javap")
runtime_public_digest_file="$work_root/runtime-public.javap.sha256"
printf '%s\n' \
    'format=soma-runtime-public-javap-v1' \
    'tool=javap -public' \
    "class.count=$runtime_public_count" \
    "sha256=$runtime_public_digest" \
    > "$runtime_public_digest_file"
diff -u "$repo_root/tests/i3-consumer/golden/runtime-public.javap.sha256" \
    "$runtime_public_digest_file"
runtime_verbose_digest_file="$work_root/runtime-public.javap.verbose.sha256"
printf '%s\n' \
    'format=soma-runtime-public-javap-verbose-v1' \
    'tool=javap -v -public, normalized path/time/checksum' \
    "class.count=$runtime_public_count" \
    "sha256=$(hash_file "$runtime_verbose_javap")" \
    > "$runtime_verbose_digest_file"
diff -u \
    "$repo_root/tests/i3-consumer/golden/runtime-public.javap.verbose.sha256" \
    "$runtime_verbose_digest_file"

processor_classes="$work_root/processor-classes.txt"
sed -n '/^io\/github\/somaruntime\/soma\/processor\/.*\.class$/p' \
        "$processor_inventory" \
    | sed 's#/#.#g; s#\.class$##' > "$processor_classes"
: > "$work_root/processor-bytecode.txt"
while IFS= read -r processor_class; do
    "$javap_cmd" -c -p -classpath "$processor_jar:$runtime_jar" \
        "$processor_class" >> "$work_root/processor-bytecode.txt"
done < "$processor_classes"
if grep -Eq 'java/lang/reflect|java/lang/Class\.forName' \
        "$work_root/processor-bytecode.txt"; then
    echo "i3-qualification: processor depends on reflection" >&2
    exit 1
fi

for report in \
    "$repo_root/soma-runtime/target/surefire-reports/TEST-io.github.somaruntime.soma.internal.GeneratedTableTest.xml" \
    "$repo_root/soma-processor/target/surefire-reports/TEST-io.github.somaruntime.soma.processor.I3QueryGenerationTest.xml" \
    "$repo_root/soma-processor/target/surefire-reports/TEST-io.github.somaruntime.soma.processor.I3QuerySurfaceTest.xml"; do
    test -f "$report"
    grep -q 'failures="0"' "$report"
    grep -q 'errors="0"' "$report"
done

printf '%s\n' 'i3-qualification: ok'
