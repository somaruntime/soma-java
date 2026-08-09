#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_root"

if [ -z "${JAVA_HOME:-}" ]; then
    echo "i7-qualification: JAVA_HOME must select the qualified Java 8 JDK" >&2
    exit 1
fi
java_cmd="$JAVA_HOME/bin/java"
javac_cmd="$JAVA_HOME/bin/javac"
javap_cmd="$JAVA_HOME/bin/javap"
jar_cmd="$JAVA_HOME/bin/jar"
for tool in "$java_cmd" "$javac_cmd" "$javap_cmd" "$jar_cmd"; do
    test -x "$tool" || {
        echo "i7-qualification: missing JDK tool: $tool" >&2
        exit 1
    }
done
"$java_cmd" -version 2>&1 | grep -q 'version "1\.8\.'
"$javac_cmd" -version 2>&1 | grep -q '^javac 1\.8\.'

python3 scripts/generate-grouped-api.py --check
if [ "${SOMA_I7_REUSE_BUILD:-0}" != 1 ]; then
    mvn clean package
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
    echo "i7-qualification: production artifacts not found" >&2
    exit 1
}

work_root=$(mktemp -d "${TMPDIR:-/tmp}/soma-i7-qualification.XXXXXX")
cleanup() {
    case "$work_root" in
        */soma-i7-qualification.*) rm -rf -- "$work_root" ;;
        *) echo "i7-qualification: refusing unsafe cleanup target" >&2 ;;
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
    "$repo_root/tests/i5-consumer/src/main/java/example/i5/I5ConsumerMain.java" \
    "$repo_root/tests/i6-consumer/src/main/java/example/i5/I6ConsumerMain.java" \
    "$repo_root/tests/i6-consumer/src/main/java/example/i5/I6CommonPoolMain.java" \
    "$repo_root/tests/i7-consumer/src/main/java/example/i5/I7ConsumerMain.java"

"$java_cmd" -Xms128m -Xmx1g -classpath "$classes:$runtime_jar" \
    example.i5.I5ConsumerMain
"$java_cmd" -Xms128m -Xmx1g -classpath "$classes:$runtime_jar" \
    example.i5.I6ConsumerMain
"$java_cmd" -Xms128m -Xmx1g -classpath "$classes:$runtime_jar" \
    example.i5.I6CommonPoolMain
"$java_cmd" -Xms128m -Xmx1g -classpath "$classes:$runtime_jar" \
    example.i5.I7ConsumerMain

generated_public="$work_root/generated-public.txt"
: > "$generated_public"
for generated_type in \
        example.i5.Soma \
        example.i5.SomaGroup \
        example.i5.MachineEventTable \
        'example.i5.MachineEventTable$RouteField'; do
    "$javap_cmd" -public -classpath "$classes:$runtime_jar" \
        "$generated_type" >> "$generated_public"
done
grep -q 'SomaMetadata _metadata()' "$generated_public"
grep -q 'GroupMetadata _metadata()' "$generated_public"
grep -q 'TableMetadata _metadata()' "$generated_public"
grep -q 'FieldMetadata _metadata()' "$generated_public"
if grep -q 'io.github.somaruntime.soma.internal' "$generated_public"; then
    echo "i7-qualification: generated public signature leaks internal type" >&2
    exit 1
fi

runtime_public="$work_root/runtime-public.txt"
"$javap_cmd" -public -classpath "$runtime_jar" \
    io.github.somaruntime.soma.SomaConfiguration\$Builder \
    io.github.somaruntime.soma.SomaMetadata \
    io.github.somaruntime.soma.GroupMetadata \
    io.github.somaruntime.soma.TableMetadata \
    io.github.somaruntime.soma.FieldMetadata > "$runtime_public"
grep -q 'compression(io.github.somaruntime.soma.SomaCompression)' "$runtime_public"
grep -q 'java.util.OptionalLong effectiveMemoryBudgetBytes()' "$runtime_public"
grep -q 'long representationBytes()' "$runtime_public"
grep -q 'boolean encoded()' "$runtime_public"

"$jar_cmd" tf "$runtime_jar" > "$work_root/runtime-jar.txt"
"$jar_cmd" tf "$processor_jar" > "$work_root/processor-jar.txt"
if grep -q '^org/junit/' "$work_root/runtime-jar.txt" \
        || grep -q '^org/junit/' "$work_root/processor-jar.txt"; then
    echo "i7-qualification: JUnit leaked into a production artifact" >&2
    exit 1
fi
grep -q '^io/github/somaruntime/soma/internal/EncodedChunk.class$' \
    "$work_root/runtime-jar.txt"
grep -q '^io/github/somaruntime/soma/TableMetadata.class$' \
    "$work_root/runtime-jar.txt"

git diff --check
echo "i7-qualification: PASS"
