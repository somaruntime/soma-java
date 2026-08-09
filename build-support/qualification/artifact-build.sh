#!/bin/sh
set -eu

reject_match() {
    set +e
    "$@"
    status=$?
    set -e
    case "$status" in
        0) echo "artifact-build: forbidden surface detected" >&2; exit 1 ;;
        1) return 0 ;;
        *) exit "$status" ;;
    esac
}

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$repo_root"

if [ -z "${JAVA_HOME:-}" ]; then
    echo "artifact-build: JAVA_HOME must select the qualified Java 8 JDK" >&2
    exit 1
fi

java_cmd="$JAVA_HOME/bin/java"
javac_cmd="$JAVA_HOME/bin/javac"
jar_cmd="$JAVA_HOME/bin/jar"
javap_cmd="$JAVA_HOME/bin/javap"
for tool in "$java_cmd" "$javac_cmd" "$jar_cmd" "$javap_cmd"; do
    if [ ! -x "$tool" ]; then
        echo "artifact-build: missing JDK tool: $tool" >&2
        exit 1
    fi
done
if ! "$java_cmd" -version 2>&1 | grep -q 'version "1\.8\.'; then
    echo "artifact-build: JAVA_HOME is not Java 8" >&2
    exit 1
fi
if ! "$javac_cmd" -version 2>&1 | grep -q '^javac 1\.8\.'; then
    echo "artifact-build: JAVA_HOME javac is not Java 8" >&2
    exit 1
fi

module_count=$(grep -c '<module>' "$repo_root/pom.xml")
if [ "$module_count" -ne 2 ] \
        || ! grep -q '<module>soma-runtime</module>' "$repo_root/pom.xml" \
        || ! grep -q '<module>soma-processor</module>' "$repo_root/pom.xml"; then
    echo "artifact-build: reactor must contain exactly the two production modules" >&2
    exit 1
fi
if grep -q '<parent>' "$repo_root/soma-runtime/pom.xml" \
        || grep -q '<parent>' "$repo_root/soma-processor/pom.xml" \
        || ! grep -q '<maven.install.skip>true</maven.install.skip>' "$repo_root/pom.xml" \
        || ! grep -q '<maven.deploy.skip>true</maven.deploy.skip>' "$repo_root/pom.xml"; then
    echo "artifact-build: child POMs must be self-contained and root must be non-published" >&2
    exit 1
fi

work_root=$(mktemp -d "${TMPDIR:-/tmp}/soma-artifact-build.XXXXXX")
cleanup() {
    case "$work_root" in
        */soma-artifact-build.*) rm -rf -- "$work_root" ;;
        *) echo "artifact-build: refusing unsafe cleanup target" >&2 ;;
    esac
}
trap cleanup EXIT HUP INT TERM

mvn clean install

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
    echo "artifact-build: production artifacts not found" >&2
    exit 1
fi

classes="$work_root/classes"
generated="$work_root/generated"
mkdir -p "$classes" "$generated"

"$javac_cmd" \
    -source 8 \
    -target 8 \
    -encoding UTF-8 \
    -classpath "$runtime_jar" \
    -processorpath "$processor_jar:$runtime_jar" \
    -processor io.github.somaruntime.soma.processor.SomaProcessor \
    -Asoma.fullSourceSet=true \
    -d "$classes" \
    -s "$generated" \
    "$repo_root/tests/build-spine/src/main/java/example/i0/schema/package-info.java" \
    "$repo_root/tests/build-spine/src/main/java/example/i0/schema/Sample.java" \
    "$repo_root/tests/build-spine/src/main/java/example/i0/I0ConsumerMain.java"

"$java_cmd" -classpath "$classes:$runtime_jar" example.i0.I0ConsumerMain

mutated_runtime="$work_root/soma-runtime-same-version-mutated.jar"
cp "$runtime_jar" "$mutated_runtime"
touch "$work_root/runtime-build-mismatch-marker"
"$jar_cmd" uf "$mutated_runtime" \
    -C "$work_root" runtime-build-mismatch-marker
if "$java_cmd" -classpath "$classes:$mutated_runtime" example.i0.I0ConsumerMain \
        > "$work_root/mismatch.txt" 2>&1; then
    echo "artifact-build: same-version runtime artifact mismatch was accepted" >&2
    exit 1
fi
grep -q '\[SOMA-0001\]' "$work_root/mismatch.txt"

stale_root="$work_root/stale-consumer"
stale_sources="$stale_root/src/main/java"
mkdir -p "$stale_sources"
cp "$repo_root/tests/regeneration/build-spine/pom.xml" "$stale_root/pom.xml"
cp -R "$repo_root/tests/regeneration/build-spine/snapshots/initial/." "$stale_sources"
mvn -f "$stale_root/pom.xml" clean compile

stale_manifest="$stale_root/target/classes/META-INF/soma/example.stale.schema.properties"
test -f "$stale_root/target/classes/example/stale/schema/Alpha.class"
test -f "$stale_root/target/generated-sources/annotations/example/stale/SomaCompositionLinkage.java"
grep -q 'table=example.stale.schema.Alpha' "$stale_manifest"
cp "$stale_manifest" "$work_root/initial-stale-manifest.properties"

case "$stale_sources" in
    "$work_root"/*) rm -rf -- "$stale_sources" ;;
    *) echo "artifact-build: refusing unsafe stale-source cleanup" >&2; exit 1 ;;
esac
mkdir -p "$stale_sources"
cp -R "$repo_root/tests/regeneration/build-spine/snapshots/renamed/." "$stale_sources"
mvn -f "$stale_root/pom.xml" clean compile

test ! -e "$stale_root/target/classes/example/stale/schema/Alpha.class"
test -f "$stale_root/target/classes/example/stale/schema/Gamma.class"
test -f "$stale_root/target/generated-sources/annotations/example/stale/SomaCompositionLinkage.java"
reject_match grep -q 'table=example.stale.schema.Alpha' "$stale_manifest"
grep -q 'table=example.stale.schema.Gamma' "$stale_manifest"
reject_match cmp -s "$work_root/initial-stale-manifest.properties" "$stale_manifest"

"$jar_cmd" tf "$runtime_jar" > "$work_root/runtime-jar.txt"
"$jar_cmd" tf "$processor_jar" > "$work_root/processor-jar.txt"

grep -qx 'META-INF/LICENSE' "$work_root/runtime-jar.txt"
grep -qx 'META-INF/NOTICE' "$work_root/runtime-jar.txt"
grep -qx 'META-INF/soma/linkage.properties' "$work_root/runtime-jar.txt"
grep -qx 'META-INF/LICENSE' "$work_root/processor-jar.txt"
grep -qx 'META-INF/NOTICE' "$work_root/processor-jar.txt"
grep -qx 'META-INF/soma/linkage.properties' "$work_root/processor-jar.txt"
grep -qx 'META-INF/services/javax.annotation.processing.Processor' "$work_root/processor-jar.txt"

if grep -q '^org/junit/' "$work_root/runtime-jar.txt" \
        || grep -q '^org/junit/' "$work_root/processor-jar.txt"; then
    echo "artifact-build: JUnit leaked into a production artifact" >&2
    exit 1
fi

runtime_base=$(basename "$runtime_jar" .jar)
processor_base=$(basename "$processor_jar" .jar)
runtime_sources="$repo_root/soma-runtime/target/$runtime_base-sources.jar"
runtime_javadoc="$repo_root/soma-runtime/target/$runtime_base-javadoc.jar"
processor_sources="$repo_root/soma-processor/target/$processor_base-sources.jar"
processor_javadoc="$repo_root/soma-processor/target/$processor_base-javadoc.jar"
for source_archive in "$runtime_sources" "$processor_sources"; do
    test -f "$source_archive"
    "$jar_cmd" tf "$source_archive" > "$work_root/classifier.txt"
    grep -qx 'META-INF/LICENSE' "$work_root/classifier.txt"
    grep -qx 'META-INF/NOTICE' "$work_root/classifier.txt"
done
for javadoc_archive in "$runtime_javadoc" "$processor_javadoc"; do
    test -f "$javadoc_archive"
    "$jar_cmd" tf "$javadoc_archive" > "$work_root/classifier.txt"
    grep -qx 'index.html' "$work_root/classifier.txt"
done

"$javap_cmd" -verbose -classpath "$runtime_jar" io.github.somaruntime.soma.SomaSchema \
    | grep -q 'major version: 52'
"$javap_cmd" -verbose -classpath "$processor_jar:$runtime_jar" \
    io.github.somaruntime.soma.processor.SomaProcessor \
    | grep -q 'major version: 52'

test -f "$classes/META-INF/soma/example.i0.schema.properties"
test -f "$generated/example/i0/SomaCompositionLinkage.java"
grep -q '^runtime.build.sha256=[0-9a-f]\{64\}$' \
    "$classes/META-INF/soma/example.i0.schema.properties"

hash_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

first_hashes="$work_root/first-hashes.txt"
for artifact in "$runtime_jar" "$runtime_sources" "$runtime_javadoc" \
        "$processor_jar" "$processor_sources" "$processor_javadoc"; do
    printf '%s  %s\n' "$(hash_file "$artifact")" "$(basename "$artifact")" \
        >> "$first_hashes"
done

mvn clean package -DskipTests

second_hashes="$work_root/second-hashes.txt"
for artifact in "$runtime_jar" "$runtime_sources" "$runtime_javadoc" \
        "$processor_jar" "$processor_sources" "$processor_javadoc"; do
    printf '%s  %s\n' "$(hash_file "$artifact")" "$(basename "$artifact")" \
        >> "$second_hashes"
done
if ! cmp -s "$first_hashes" "$second_hashes"; then
    echo "artifact-build: clean package artifacts are not reproducible" >&2
    diff -u "$first_hashes" "$second_hashes" >&2 || true
    exit 1
fi

printf '%s\n' 'artifact-build: ok'
