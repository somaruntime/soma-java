#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_root"

"$repo_root/scripts/qualify-i0.sh"

if [ -z "${JAVA_HOME:-}" ]; then
    echo "i1-qualification: JAVA_HOME must select the qualified Java 8 JDK" >&2
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
    echo "i1-qualification: production artifacts not found" >&2
    exit 1
fi

work_root=$(mktemp -d "${TMPDIR:-/tmp}/soma-i1-qualification.XXXXXX")
cleanup() {
    case "$work_root" in
        */soma-i1-qualification.*) rm -rf -- "$work_root" ;;
        *) echo "i1-qualification: refusing unsafe cleanup target" >&2 ;;
    esac
}
trap cleanup EXIT HUP INT TERM

classes="$work_root/classes"
generated="$work_root/generated"
second_classes="$work_root/second-classes"
second_generated="$work_root/second-generated"
mkdir -p "$classes" "$generated" "$second_classes" "$second_generated"

positive_sources="$work_root/positive-sources.txt"
find "$repo_root/tests/i1-consumer/src/main/java" -name '*.java' -type f -print \
    | LC_ALL=C sort > "$positive_sources"

compile_consumer() {
    output_classes=$1
    output_generated=$2
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
        @"$positive_sources"
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

generated_hashes="$work_root/generated-source.sha256"
for generated_name in Entity.java EntityTable.java Soma.java SomaGroup.java; do
    generated_file="$generated/example/i1/$generated_name"
    printf '%s  %s\n' "$(hash_file "$generated_file")" "$generated_name" \
        >> "$generated_hashes"
done
diff -u "$repo_root/tests/i1-consumer/golden/generated-source.sha256" \
    "$generated_hashes"

for main_class in \
    example.i1.I1ConsumerMain \
    example.i1.ConfigureFirstMain \
    example.i1.ClassLoadThenConfigureMain \
    example.i1.DefaultFirstMain \
    example.i1.CapabilityRejectionMain \
    example.i1.BudgetFailureMain; do
    "$java_cmd" -classpath "$classes:$runtime_jar" "$main_class"
done

stale_root="$work_root/stale-consumer"
stale_sources="$stale_root/src/main/java"
mkdir -p "$stale_sources"
cp "$repo_root/tests/i1-regeneration/pom.xml" "$stale_root/pom.xml"
cp -R "$repo_root/tests/i1-regeneration/snapshots/initial/." "$stale_sources"
mvn -f "$stale_root/pom.xml" clean compile

stale_generated="$stale_root/target/generated-sources/annotations/example/i1stale"
stale_classes="$stale_root/target/classes/example/i1stale"
stale_manifest="$stale_root/target/classes/META-INF/soma/example.i1stale.schema.properties"
test -f "$stale_generated/Alpha.java"
test -f "$stale_generated/AlphaTable.java"
test -f "$stale_classes/Alpha.class"
test -f "$stale_classes/AlphaTable.class"
grep -q 'example.i1stale.AlphaTable' "$stale_manifest"

case "$stale_sources" in
    "$work_root"/*) rm -rf -- "$stale_sources" ;;
    *) echo "i1-qualification: refusing unsafe stale-source cleanup" >&2; exit 1 ;;
esac
mkdir -p "$stale_sources"
cp -R "$repo_root/tests/i1-regeneration/snapshots/renamed/." "$stale_sources"
mvn -f "$stale_root/pom.xml" clean compile

test ! -e "$stale_generated/Alpha.java"
test ! -e "$stale_generated/AlphaTable.java"
test ! -e "$stale_classes/Alpha.class"
test ! -e "$stale_classes/AlphaTable.class"
test -f "$stale_generated/Gamma.java"
test -f "$stale_generated/GammaTable.java"
test -f "$stale_classes/Gamma.class"
test -f "$stale_classes/GammaTable.class"
! grep -q 'example.i1stale.Alpha' "$stale_manifest"
grep -q 'example.i1stale.GammaTable' "$stale_manifest"

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
        echo "i1-qualification: expected compile failure: $source_file" >&2
        exit 1
    fi
    if ! grep -Eq "$expected_pattern" "$negative_output"; then
        echo "i1-qualification: expected diagnostic pattern missing: $expected_pattern" >&2
        cat "$negative_output" >&2
        exit 1
    fi
}

negative_root="$repo_root/tests/i1-consumer/src/negative/java/example/i1"
expect_compile_failure "$negative_root/DirectConstructionNegative.java" \
    'private access|has private access'
expect_compile_failure "$negative_root/GroupConstructionNegative.java" \
    'private access|has private access|cannot be applied'
expect_compile_failure "$negative_root/TableConstructionNegative.java" \
    'private access|has private access|cannot be applied'
expect_compile_failure "$negative_root/ViewConstructionNegative.java" \
    'private access|has private access|cannot be applied'
expect_compile_failure "$negative_root/SyntheticBridgeConstructionNegative.java" \
    'private access|cannot be applied|has private access'
expect_compile_failure "$negative_root/KeyEditorNegative.java" \
    'id|cannot be applied'
expect_compile_failure "$negative_root/FutureSurfaceNegative.java" \
    'parallel'
expect_compile_failure "$negative_root/CallbackFilterNegative.java" \
    'filter|SomaExpression'
expect_compile_failure "$negative_root/SelectionUpdateNegative.java" \
    'update'
expect_compile_failure "$negative_root/RawRuntimeConstructionNegative.java" \
    'createGroup|cannot be applied'
expect_compile_failure "$negative_root/SharedSecretFactoryNegative.java" \
    'failureAccess|not public|cannot be accessed'
expect_compile_failure "$negative_root/SchemaLeakNegative.java" \
    'not public|cannot be accessed'

"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    example.i1.EntityTable > "$work_root/entity-table-public.txt"
"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    'example.i1.EntityTable$Editor' > "$work_root/editor-public.txt"
"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    example.i1.Soma > "$work_root/soma-public.txt"

generated_public="$work_root/generated-public.javap.txt"
: > "$generated_public"
for generated_public_class in \
    example.i1.Soma \
    example.i1.SomaGroup \
    example.i1.Entity \
    example.i1.EntityTable \
    'example.i1.EntityTable$View' \
    'example.i1.EntityTable$Editor' \
    'example.i1.EntityTable$Selection' \
    'example.i1.EntityTable$IdField' \
    'example.i1.EntityTable$ValueField'; do
    printf '===== %s =====\n' "$generated_public_class" >> "$generated_public"
    "$javap_cmd" -public -classpath "$classes:$runtime_jar" \
        "$generated_public_class" >> "$generated_public"
    "$javap_cmd" -verbose -classpath "$classes:$runtime_jar" \
        "$generated_public_class" | grep -q 'major version: 52'
done
diff -u "$repo_root/tests/i1-consumer/golden/generated-public.javap.txt" \
    "$generated_public"

grep -q 'public long size();' "$work_root/entity-table-public.txt"
grep -q 'public void add(example.i1.Entity);' "$work_root/entity-table-public.txt"
grep -q 'public java.util.Optional<example.i1.Entity> find(long);' \
    "$work_root/entity-table-public.txt"
grep -q 'public io.github.somaruntime.soma.UpdateResult update' \
    "$work_root/entity-table-public.txt"
grep -q 'public void value(long);' "$work_root/editor-public.txt"
! grep -q 'void id(long)' "$work_root/editor-public.txt"
! grep -q 'parallel' "$work_root/entity-table-public.txt"
grep -q 'public io.github.somaruntime.soma.RemoveResult remove(long);' \
    "$work_root/entity-table-public.txt"
! grep -q 'EntityTable();' "$work_root/entity-table-public.txt"
! grep -q 'Soma();' "$work_root/soma-public.txt"

runtime_classes="$work_root/runtime-classes.txt"
"$JAVA_HOME/bin/jar" tf "$runtime_jar" \
    | sed -n '/^io\/github\/somaruntime\/soma\/internal\/.*\.class$/p' \
    | sed 's#/#.#g; s#\.class$##' > "$runtime_classes"
: > "$work_root/runtime-bytecode.txt"
while IFS= read -r runtime_class; do
    "$javap_cmd" -c -p -classpath "$runtime_jar" "$runtime_class" \
        >> "$work_root/runtime-bytecode.txt"
done < "$runtime_classes"

: > "$work_root/generated-bytecode.txt"
for generated_class_file in "$classes"/example/i1/EntityTable*.class; do
    generated_class=${generated_class_file#"$classes"/}
    generated_class=$(printf '%s' "$generated_class" \
        | sed 's#/#.#g; s#\.class$##')
    "$javap_cmd" -c -p -classpath "$classes:$runtime_jar" "$generated_class" \
        >> "$work_root/generated-bytecode.txt"
done

if grep -Eq 'java/lang/(Long|Integer|Boolean|Byte|Short|Character|Float|Double)\.(valueOf|[a-zA-Z]+Value)|java/lang/reflect' \
        "$work_root/runtime-bytecode.txt" "$work_root/generated-bytecode.txt"; then
    echo "i1-qualification: hot primitive path contains boxing or reflection" >&2
    exit 1
fi

test -f "$generated/example/i1/Soma.java"
test -f "$generated/example/i1/SomaGroup.java"
test -f "$generated/example/i1/Entity.java"
test -f "$generated/example/i1/EntityTable.java"
grep -q 'implements io.github.somaruntime.soma.SomaKeyableField<View, java.lang.Long>' \
    "$generated/example/i1/EntityTable.java"
! grep -q 'parallel(' "$generated/example/i1/EntityTable.java"
grep -q 'public io.github.somaruntime.soma.RemoveResult remove(long key)' \
    "$generated/example/i1/EntityTable.java"

printf '%s\n' 'i1-qualification: ok'
