#!/bin/sh
set -eu

reject_match() {
    set +e
    "$@"
    status=$?
    set -e
    case "$status" in
        0) echo "parallel-execution: forbidden surface detected" >&2; exit 1 ;;
        1) return 0 ;;
        *) exit "$status" ;;
    esac
}

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$repo_root"

if [ -z "${JAVA_HOME:-}" ]; then
    echo "parallel-execution: JAVA_HOME must select the qualified Java 8 JDK" >&2
    exit 1
fi
java_cmd="$JAVA_HOME/bin/java"
javac_cmd="$JAVA_HOME/bin/javac"
javap_cmd="$JAVA_HOME/bin/javap"
jar_cmd="$JAVA_HOME/bin/jar"
for tool in "$java_cmd" "$javac_cmd" "$javap_cmd" "$jar_cmd"; do
    test -x "$tool" || {
        echo "parallel-execution: missing JDK tool: $tool" >&2
        exit 1
    }
done
"$java_cmd" -version 2>&1 | grep -q 'version "1\.8\.'
"$javac_cmd" -version 2>&1 | grep -q '^javac 1\.8\.'

python3 build-support/codegen/generate-grouped-api.py --check
if [ -z "${SOMA_BUILD_SESSION_FILE:-}" ]; then
    mvn clean package
else
    python3 build-support/qualification/build-session.py verify \
        --repo "$repo_root" --manifest "$SOMA_BUILD_SESSION_FILE" \
        --require runtime --require processor
fi

runtime_jar=
for candidate in "$repo_root"/soma-runtime/target/soma-runtime-*.jar; do
    case "$candidate" in *-sources.jar|*-javadoc.jar) ;; *) runtime_jar=$candidate ;; esac
done
processor_jar=
for candidate in "$repo_root"/soma-processor/target/soma-processor-*.jar; do
    case "$candidate" in *-sources.jar|*-javadoc.jar) ;; *) processor_jar=$candidate ;; esac
done
test -n "$runtime_jar" && test -n "$processor_jar" || {
    echo "parallel-execution: production artifacts not found" >&2
    exit 1
}

work_root=$(mktemp -d "${TMPDIR:-/tmp}/soma-parallel-execution.XXXXXX")
cleanup() {
    case "$work_root" in
        */soma-parallel-execution.*) rm -rf -- "$work_root" ;;
        *) echo "parallel-execution: refusing unsafe cleanup target" >&2 ;;
    esac
}
trap cleanup EXIT HUP INT TERM

classes="$work_root/classes"
generated="$work_root/generated"
second_classes="$work_root/second-classes"
second_generated="$work_root/second-generated"
mkdir -p "$classes" "$generated" "$second_classes" "$second_generated"

schema_sources="$work_root/schema-sources.txt"
find "$repo_root/tests/group-relation/src/main/java/example/i5/schema" \
        -name '*.java' -type f -print | LC_ALL=C sort > "$schema_sources"
reverse_schema_sources="$work_root/reverse-schema-sources.txt"
LC_ALL=C sort -r "$schema_sources" > "$reverse_schema_sources"

compile_schema() {
    "$javac_cmd" -source 8 -target 8 -encoding UTF-8 \
        -classpath "$runtime_jar" \
        -processorpath "$processor_jar:$runtime_jar" \
        -processor io.github.somaruntime.soma.processor.SomaProcessor \
        -Asoma.fullSourceSet=true \
        -d "$1" -s "$2" @"$3"
}
compile_schema "$classes" "$generated" "$schema_sources"
compile_schema "$second_classes" "$second_generated" "$reverse_schema_sources"
diff -qr "$generated" "$second_generated"

"$javac_cmd" -source 8 -target 8 -encoding UTF-8 -proc:none \
    -classpath "$classes:$runtime_jar" -d "$classes" \
    "$repo_root/tests/group-relation/src/main/java/example/i5/I5ConsumerMain.java" \
    "$repo_root/tests/parallel-execution/src/main/java/example/i5/I6ConsumerMain.java" \
    "$repo_root/tests/parallel-execution/src/main/java/example/i5/I6CommonPoolMain.java"

"$java_cmd" -Xms128m -Xmx1g -classpath "$classes:$runtime_jar" \
    example.i5.I5ConsumerMain
"$java_cmd" -Xms128m -Xmx1g -classpath "$classes:$runtime_jar" \
    example.i5.I6ConsumerMain
"$java_cmd" -Xms128m -Xmx1g -classpath "$classes:$runtime_jar" \
    example.i5.I6CommonPoolMain

"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    example.i5.MachineEventTable > "$work_root/table.txt"
"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    'example.i5.MachineEventTable$Stream' > "$work_root/stream.txt"
"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    'example.i5.MachineEventTable$Selection' > "$work_root/selection.txt"
"$javap_cmd" -public -classpath "$classes:$runtime_jar" \
    'example.i5.MachineEventTable$ReadStream' > "$work_root/read-stream.txt"
"$javap_cmd" -public -classpath "$runtime_jar" \
    io.github.somaruntime.soma.SomaConfiguration\$Builder \
    > "$work_root/configuration-builder.txt"
"$javap_cmd" -public -classpath "$runtime_jar" \
    io.github.somaruntime.soma.MappedStream \
    io.github.somaruntime.soma.SomaLongStream \
    io.github.somaruntime.soma.SomaPairStream \
    > "$work_root/shared-streams.txt"

grep -q 'MachineEventTable\$Stream parallel()' "$work_root/table.txt"
grep -q 'MachineEventTable\$Stream parallel()' "$work_root/stream.txt"
grep -q 'MachineEventTable\$Selection parallel()' "$work_root/selection.txt"
grep -q 'MachineEventTable\$ReadStream parallel()' "$work_root/read-stream.txt"
grep -q 'parallelExecutor(java.util.concurrent.ForkJoinPool)' \
    "$work_root/configuration-builder.txt"
grep -q ' parallel()' "$work_root/shared-streams.txt"
reject_match grep -q ' sequential(' "$work_root/shared-streams.txt"

if grep -q 'io.github.somaruntime.soma.internal' \
        "$work_root/table.txt" "$work_root/stream.txt" \
        "$work_root/selection.txt" "$work_root/read-stream.txt"; then
    echo "parallel-execution: generated public signature leaks internal type" >&2
    exit 1
fi

"$jar_cmd" tf "$runtime_jar" > "$work_root/runtime-jar.txt"
"$jar_cmd" tf "$processor_jar" > "$work_root/processor-jar.txt"
reject_match grep -q '^org/junit/' "$work_root/runtime-jar.txt"
reject_match grep -q '^org/junit/' "$work_root/processor-jar.txt"
grep -q '^io/github/somaruntime/soma/internal/CanonicalParallelRowScheduler.class$' \
    "$work_root/runtime-jar.txt"

git diff --check
echo "parallel-execution: PASS"
