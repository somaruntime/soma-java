#!/bin/sh
set -eu

reject_match() {
    set +e
    "$@"
    status=$?
    set -e
    case "$status" in
        0) echo "i2-qualification: forbidden surface detected" >&2; exit 1 ;;
        1) return 0 ;;
        *) exit "$status" ;;
    esac
}

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_root"

"$repo_root/scripts/qualify-i1.sh"

if [ -z "${JAVA_HOME:-}" ]; then
    echo "i2-qualification: JAVA_HOME must select the qualified Java 8 JDK" >&2
    exit 1
fi

java_cmd="$JAVA_HOME/bin/java"
javac_cmd="$JAVA_HOME/bin/javac"
javap_cmd="$JAVA_HOME/bin/javap"

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
    echo "i2-qualification: production artifacts not found" >&2
    exit 1
fi

work_root=$(mktemp -d "${TMPDIR:-/tmp}/soma-i2-qualification.XXXXXX")
cleanup() {
    case "$work_root" in
        */soma-i2-qualification.*) rm -rf -- "$work_root" ;;
        *) echo "i2-qualification: refusing unsafe cleanup target" >&2 ;;
    esac
}
trap cleanup EXIT HUP INT TERM

classes="$work_root/classes"
generated="$work_root/generated"
second_classes="$work_root/second-classes"
second_generated="$work_root/second-generated"
mkdir -p "$classes" "$generated" "$second_classes" "$second_generated"

schema_sources="$work_root/schema-sources.txt"
find "$repo_root/tests/i2-consumer/src/main/java/example/i2/schema" \
        -name '*.java' -type f -print \
    | LC_ALL=C sort > "$schema_sources"
printf '%s\n' \
    "$repo_root/tests/i2-consumer/src/main/java/example/i2/Status.java" \
    "$repo_root/tests/i2-consumer/src/main/java/example/i2/Payload.java" \
    >> "$schema_sources"

compile_consumer() {
    output_classes=$1
    output_generated=$2
    # Full regeneration is deliberately a build-host phase before application
    # sources are attributed against the generated public API.
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
        @"$schema_sources"
    "$javac_cmd" \
        -source 8 \
        -target 8 \
        -encoding UTF-8 \
        -proc:none \
        -classpath "$output_classes:$runtime_jar" \
        -d "$output_classes" \
        "$repo_root/tests/i2-consumer/src/main/java/example/i2/I2ConsumerMain.java" \
        "$repo_root/tests/i2-consumer/src/main/java/example/i2/I2ScaleMain.java"
}

compile_consumer "$classes" "$generated"
compile_consumer "$second_classes" "$second_generated"
diff -qr "$generated" "$second_generated"

hash_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

generated_files="$work_root/generated-files.txt"
find "$generated/example/i2" -name '*.java' -type f -print \
    | LC_ALL=C sort > "$generated_files"

generated_hashes="$work_root/generated-source.sha256"
: > "$generated_hashes"
while IFS= read -r generated_file; do
    generated_name=${generated_file#"$generated/example/i2/"}
    printf '%s  %s\n' "$(hash_file "$generated_file")" "$generated_name" \
        >> "$generated_hashes"
done < "$generated_files"
diff -u "$repo_root/tests/i2-consumer/golden/generated-source.sha256" \
    "$generated_hashes"

# Discover every class emitted from each generated top-level source before
# consulting the committed inventory. This makes a newly added public nested
# type observable rather than silently excluding it from the golden.
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
diff -u "$repo_root/tests/i2-consumer/golden/generated-public.classes" \
    "$generated_public_classes"

generated_public_javap="$work_root/generated-public.javap.txt"
: > "$generated_public_javap"
while IFS= read -r generated_class; do
    printf '===== %s =====\n' "$generated_class" \
        >> "$generated_public_javap"
    LC_ALL=C "$javap_cmd" -public -classpath "$classes:$runtime_jar" \
        "$generated_class" >> "$generated_public_javap"
done < "$generated_public_classes"

generated_public_digest=$(hash_file "$generated_public_javap")
generated_public_count=$(wc -l < "$generated_public_classes" | tr -d ' ')
generated_public_digest_file="$work_root/generated-public.javap.sha256"
printf '%s\n' \
    'format=soma-generated-public-javap-v1' \
    'tool=javap -public' \
    "class.count=$generated_public_count" \
    "sha256=$generated_public_digest" \
    > "$generated_public_digest_file"
diff -u "$repo_root/tests/i2-consumer/golden/generated-public.javap.sha256" \
    "$generated_public_digest_file"

"$java_cmd" -Xms128m -Xmx1g -classpath "$classes:$runtime_jar" \
    example.i2.I2ConsumerMain
"$java_cmd" -Xms256m -Xmx2g -classpath "$classes:$runtime_jar" \
    example.i2.I2ScaleMain

stale_root="$work_root/stale-consumer"
stale_sources="$stale_root/src/main/java"
mkdir -p "$stale_sources"
cp "$repo_root/tests/i2-regeneration/pom.xml" "$stale_root/pom.xml"
cp -R "$repo_root/tests/i2-regeneration/snapshots/initial/." "$stale_sources"
mvn -f "$stale_root/pom.xml" clean compile

stale_generated="$stale_root/target/generated-sources/annotations/example/i2stale"
stale_classes="$stale_root/target/classes/example/i2stale"
stale_manifest="$stale_root/target/classes/META-INF/soma/example.i2stale.schema.properties"
for initial_type in Code Alpha AlphaTable Relation RelationTable; do
    test -f "$stale_generated/$initial_type.java"
    test -f "$stale_classes/$initial_type.class"
done
grep -q 'public IndexSelection byGroupId(int value)' "$stale_generated/AlphaTable.java"
grep -q 'public IndexSelection byLeftCode(example.i2stale.Code value)' \
    "$stale_generated/RelationTable.java"
grep -q 'example.i2stale.AlphaTable' "$stale_manifest"
grep -q 'example.i2stale.RelationTable' "$stale_manifest"

case "$stale_sources" in
    "$work_root"/*) rm -rf -- "$stale_sources" ;;
    *) echo "i2-qualification: refusing unsafe stale-source cleanup" >&2; exit 1 ;;
esac
mkdir -p "$stale_sources"
cp -R "$repo_root/tests/i2-regeneration/snapshots/renamed/." "$stale_sources"
mvn -f "$stale_root/pom.xml" clean compile

for initial_type in Code Alpha AlphaTable Relation RelationTable; do
    test ! -e "$stale_generated/$initial_type.java"
    test ! -e "$stale_classes/$initial_type.class"
done
for renamed_type in RenamedCode Gamma GammaTable RenamedRelation RenamedRelationTable; do
    test -f "$stale_generated/$renamed_type.java"
    test -f "$stale_classes/$renamed_type.class"
done
grep -q 'public IndexSelection byBucket(int value)' "$stale_generated/GammaTable.java"
grep -q 'public IndexSelection bySourceCode(example.i2stale.RenamedCode value)' \
    "$stale_generated/RenamedRelationTable.java"
reject_match grep -Eq 'example\.i2stale\.(Code|Alpha|Relation)' "$stale_manifest"
grep -q 'example.i2stale.GammaTable' "$stale_manifest"
grep -q 'example.i2stale.RenamedRelationTable' "$stale_manifest"

negative_output="$work_root/negative.txt"
mkdir -p "$work_root/negative-classes"
expect_compile_failure() {
    source_file=$1
    expected_pattern=$2
    if "$javac_cmd" \
            -source 8 \
            -target 8 \
            -encoding UTF-8 \
            -proc:none \
            -classpath "$classes:$runtime_jar" \
            -d "$work_root/negative-classes" \
            "$source_file" > "$negative_output" 2>&1; then
        echo "i2-qualification: expected compile failure: $source_file" >&2
        exit 1
    fi
    if ! grep -Eq "$expected_pattern" "$negative_output"; then
        echo "i2-qualification: expected diagnostic pattern missing: $expected_pattern" >&2
        cat "$negative_output" >&2
        exit 1
    fi
}

negative_root="$repo_root/tests/i2-consumer/src/negative/java/example/i2"
expect_compile_failure "$negative_root/ObjectEqualityNegative.java" 'eq'
expect_compile_failure "$negative_root/KeyEditorNegative.java" 'key'
expect_compile_failure "$negative_root/KeylessPointNegative.java" 'find'
expect_compile_failure "$negative_root/ValueDefaultConstructorNegative.java" \
    'MachineId|constructor'

"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    example.i2.AllTypesTable > "$work_root/all-types-table-public.txt"
"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    'example.i2.AllTypesTable$PayloadField' > "$work_root/payload-field-public.txt"
"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    'example.i2.AllTypesTable$Editor' > "$work_root/editor-public.txt"
"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    example.i2.LogEntryTable > "$work_root/keyless-public.txt"

grep -q 'public io.github.somaruntime.soma.RemoveResult remove(example.i2.MachinePair);' \
    "$work_root/all-types-table-public.txt"
grep -q 'public example.i2.AllTypesTable$IndexSelection byName(java.lang.String);' \
    "$work_root/all-types-table-public.txt"
grep -q 'public example.i2.AllTypesTable$IndexSelection byMachineId(example.i2.MachineId);' \
    "$work_root/all-types-table-public.txt"
grep -q 'implements io.github.somaruntime.soma.SomaFieldEndpoint' \
    "$work_root/payload-field-public.txt"
grep -q 'isNull();' "$work_root/payload-field-public.txt"
reject_match grep -q ' eq(' "$work_root/payload-field-public.txt"
reject_match grep -q 'void key(' "$work_root/editor-public.txt"
reject_match grep -q ' find(' "$work_root/keyless-public.txt"
reject_match grep -q ' remove(' "$work_root/keyless-public.txt"

grep -q 'public MachinePair(example.i2.MachineId' \
    "$generated/example/i2/MachinePair.java"
reject_match grep -q 'public MachinePair()' "$generated/example/i2/MachinePair.java"
grep -q 'implements io.github.somaruntime.soma.SomaFieldEndpoint' \
    "$generated/example/i2/AllTypesTable.java"
reject_match grep -Rq 'GeneratedLongTable' "$generated"

runtime_classes="$work_root/runtime-classes.txt"
"$JAVA_HOME/bin/jar" tf "$runtime_jar" \
    | sed -n '/^io\/github\/somaruntime\/soma\/internal\/.*\.class$/p' \
    | sed 's#/#.#g; s#\.class$##' > "$runtime_classes"
reject_match grep -Eq 'GeneratedLong|LongChunk|LongStateRoot|PlainLongChunk' "$runtime_classes"

: > "$work_root/runtime-bytecode.txt"
while IFS= read -r runtime_class; do
    "$javap_cmd" -c -p -classpath "$runtime_jar" "$runtime_class" \
        >> "$work_root/runtime-bytecode.txt"
done < "$runtime_classes"

: > "$work_root/generated-bytecode.txt"
for generated_class_file in "$classes"/example/i2/AllTypesTable*.class \
        "$classes"/example/i2/ScaleRecordTable*.class; do
    generated_class=${generated_class_file#"$classes"/}
    generated_class=$(printf '%s' "$generated_class" \
        | sed 's#/#.#g; s#\.class$##')
    "$javap_cmd" -c -p -classpath "$classes:$runtime_jar" "$generated_class" \
        >> "$work_root/generated-bytecode.txt"
    "$javap_cmd" -verbose -classpath "$classes:$runtime_jar" "$generated_class" \
        | grep -q 'major version: 52'
done

if grep -Eq 'java/lang/(Long|Integer|Boolean|Byte|Short|Character|Float|Double)\.(valueOf|[a-zA-Z]+Value)|java/lang/reflect' \
        "$work_root/runtime-bytecode.txt" "$work_root/generated-bytecode.txt"; then
    echo "i2-qualification: hot primitive path contains boxing or reflection" >&2
    exit 1
fi

processor_classes="$work_root/processor-classes.txt"
"$JAVA_HOME/bin/jar" tf "$processor_jar" \
    | sed -n '/^io\/github\/somaruntime\/soma\/processor\/.*\.class$/p' \
    | sed 's#/#.#g; s#\.class$##' > "$processor_classes"
: > "$work_root/processor-bytecode.txt"
while IFS= read -r processor_class; do
    "$javap_cmd" -c -p -classpath "$processor_jar:$runtime_jar" "$processor_class" \
        >> "$work_root/processor-bytecode.txt"
done < "$processor_classes"
if grep -Eq 'java/lang/reflect|java/lang/Class\.forName' \
        "$work_root/processor-bytecode.txt"; then
    echo "i2-qualification: processor depends on reflection" >&2
    exit 1
fi

printf '%s\n' 'i2-qualification: ok'
