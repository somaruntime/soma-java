#!/bin/sh
set -eu

reject_match() {
    set +e
    "$@"
    status=$?
    set -e
    case "$status" in
        0) echo "i5-qualification: forbidden surface detected" >&2; exit 1 ;;
        1) return 0 ;;
        *) exit "$status" ;;
    esac
}

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_root"

if [ -z "${JAVA_HOME:-}" ]; then
    echo "i5-qualification: JAVA_HOME must select the qualified Java 8 JDK" >&2
    exit 1
fi

java_cmd="$JAVA_HOME/bin/java"
javac_cmd="$JAVA_HOME/bin/javac"
javap_cmd="$JAVA_HOME/bin/javap"
jar_cmd="$JAVA_HOME/bin/jar"
for tool in "$java_cmd" "$javac_cmd" "$javap_cmd" "$jar_cmd"; do
    if [ ! -x "$tool" ]; then
        echo "i5-qualification: missing JDK tool: $tool" >&2
        exit 1
    fi
done
if ! "$java_cmd" -version 2>&1 | grep -q 'version "1\.8\.' \
        || ! "$javac_cmd" -version 2>&1 | grep -q '^javac 1\.8\.'; then
    echo "i5-qualification: JAVA_HOME is not a Java 8 JDK" >&2
    exit 1
fi

python3 scripts/generate-grouped-api.py --check
if [ "${SOMA_I5_REUSE_BUILD:-0}" != 1 ]; then
    mvn clean package
fi

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
    echo "i5-qualification: production artifacts not found" >&2
    exit 1
fi

work_root=$(mktemp -d "${TMPDIR:-/tmp}/soma-i5-qualification.XXXXXX")
cleanup() {
    case "$work_root" in
        */soma-i5-qualification.*) rm -rf -- "$work_root" ;;
        *) echo "i5-qualification: refusing unsafe cleanup target" >&2 ;;
    esac
}
trap cleanup EXIT HUP INT TERM

classes="$work_root/classes"
generated="$work_root/generated"
second_classes="$work_root/second-classes"
second_generated="$work_root/second-generated"
mkdir -p "$classes" "$generated" "$second_classes" "$second_generated"

schema_sources="$work_root/schema-sources.txt"
find "$repo_root/tests/i5-consumer/src/main/java/example/i5/schema" \
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
    "$repo_root/tests/i5-consumer/src/main/java/example/i5/I5ConsumerMain.java"
"$java_cmd" -Xms128m -Xmx1g -classpath "$classes:$runtime_jar" \
    example.i5.I5ConsumerMain

negative_output="$work_root/negative.txt"
negative_classes="$work_root/negative-classes"
mkdir -p "$negative_classes"
expect_compile_failure() {
    source_file=$1
    expected_pattern=$2
    if LC_ALL=C "$javac_cmd" \
            -source 8 \
            -target 8 \
            -encoding UTF-8 \
            -proc:none \
            -classpath "$classes:$runtime_jar" \
            -d "$negative_classes" \
            "$source_file" > "$negative_output" 2>&1; then
        echo "i5-qualification: expected compile failure: $source_file" >&2
        exit 1
    fi
    if ! grep -Eq "$expected_pattern" "$negative_output"; then
        echo "i5-qualification: expected diagnostic missing: $expected_pattern" >&2
        cat "$negative_output" >&2
        exit 1
    fi
}

negative_root="$repo_root/tests/i5-consumer/src/negative/java/example/i5"
expect_compile_failure "$negative_root/SelfJoinNegative.java" \
    'no suitable method found|cannot be applied|incompatible types'
expect_compile_failure "$negative_root/PairMaterializationNegative.java" \
    'cannot find symbol.*toList|symbol:.*toList'
expect_compile_failure "$negative_root/OuterSelectNegative.java" \
    'cannot find symbol.*select|symbol:.*select'

"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    example.i5.MachineEventTable > "$work_root/event-table-public.txt"
"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    'example.i5.MachineEventTable$ReadStream' \
    > "$work_root/read-stream-public.txt"
"$javap_cmd" -public -classpath "$runtime_jar" \
    io.github.somaruntime.soma.SomaPairStream \
    > "$work_root/pair-stream-public.txt"
"$javap_cmd" -v -public -classpath "$classes:$runtime_jar" \
    example.i5.MachineEventTable > "$work_root/event-table-verbose.txt"

grep -q 'major version: 52' "$work_root/event-table-verbose.txt"
grep -q 'groupBy(' "$work_root/event-table-public.txt"
grep -q 'join(example.i5.MachineStateTable)' "$work_root/event-table-public.txt"
grep -q 'crossJoin(example.i5.MachineStateTable, long)' \
    "$work_root/event-table-public.txt"
grep -q 'mapToLong(example.i5.MachineEventTable\$EventIdField)' \
    "$work_root/read-stream-public.txt"
reject_match grep -q ' parallel(' "$work_root/event-table-public.txt"
reject_match grep -q ' toList(' "$work_root/pair-stream-public.txt"
if grep -q 'io.github.somaruntime.soma.internal' \
        "$work_root/event-table-public.txt"; then
    echo "i5-qualification: generated public signature leaks internal type" >&2
    exit 1
fi

runtime_inventory="$work_root/runtime-jar.txt"
processor_inventory="$work_root/processor-jar.txt"
"$jar_cmd" tf "$runtime_jar" > "$runtime_inventory"
"$jar_cmd" tf "$processor_jar" > "$processor_inventory"
reject_match grep -q '^org/junit/' "$runtime_inventory"
reject_match grep -q '^org/junit/' "$processor_inventory"
grep -q '^io/github/somaruntime/soma/GroupedLongResult.class$' \
    "$runtime_inventory"
grep -q '^io/github/somaruntime/soma/SomaJoinOnBuilder.class$' \
    "$runtime_inventory"

echo "i5-qualification: PASS"
