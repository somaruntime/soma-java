#!/usr/bin/env bash
set -euo pipefail

I0_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
I0_TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/soma-i0-qualification.XXXXXX")"
I0_LOCAL_REPOSITORY="${SOMA_I0_MAVEN_REPOSITORY:-}"
I0_REGEN_CONSUMER="$I0_TMP_ROOT/regeneration-consumer"
I0_RUNTIME_SWAP_CLASSES="$I0_TMP_ROOT/runtime-swap-classes"
I0_RUNTIME_SWAP_PROBE_CLASSES="$I0_TMP_ROOT/runtime-swap-probe-classes"
I0_PROCESSOR_HARNESS_TMP="$I0_TMP_ROOT/processor-harness"
I0_REPORT_DIRECTORY="$I0_REPO_ROOT/target/i0-qualification"
I0_REPORT_FILE="$I0_REPORT_DIRECTORY/report.txt"
I0_RUNTIME_JAR="$I0_REPO_ROOT/soma-runtime/target/soma-runtime-1.0.0-SNAPSHOT.jar"
I0_PROCESSOR_JAR="$I0_REPO_ROOT/soma-processor/target/soma-processor-1.0.0-SNAPSHOT.jar"
I0_CONSUMER_DIRECTORY="$I0_REPO_ROOT/tests/i0/consumer"
I0_JAVA_HOME="${JAVA_HOME:-}"
I0_TOOLS_JAR=""
I0_MAVEN_RUN_NUMBER=0
I0_LOCAL_REPOSITORY_MODE="EXPLICIT_OVERRIDE"

cleanup_i0() {
    case "$I0_TMP_ROOT" in
        */soma-i0-qualification.*) rm -rf "$I0_TMP_ROOT" ;;
        *) printf 'Refusing to remove unexpected I0 temporary path: %s\n' "$I0_TMP_ROOT" >&2 ;;
    esac
}
trap cleanup_i0 EXIT

fail_i0() {
    printf 'I0 qualification failed: %s\n' "$1" >&2
    exit 1
}

require_file() {
    [ -f "$1" ] || fail_i0 "missing file $1"
}

require_text() {
    local I0_FILE="$1"
    local I0_TEXT="$2"
    grep -F "$I0_TEXT" "$I0_FILE" >/dev/null ||
        fail_i0 "expected text '$I0_TEXT' in $I0_FILE"
}

reject_text() {
    local I0_FILE="$1"
    local I0_TEXT="$2"
    if grep -F "$I0_TEXT" "$I0_FILE" >/dev/null; then
        fail_i0 "stale text '$I0_TEXT' remains in $I0_FILE"
    fi
}

require_jar_entry() {
    local I0_JAR="$1"
    local I0_ENTRY="$2"
    jar tf "$I0_JAR" | grep -Fx "$I0_ENTRY" >/dev/null ||
        fail_i0 "missing $I0_ENTRY in $I0_JAR"
}

reject_jar_entry() {
    local I0_JAR="$1"
    local I0_ENTRY="$2"
    if jar tf "$I0_JAR" | grep -Fx "$I0_ENTRY" >/dev/null; then
        fail_i0 "unexpected $I0_ENTRY in $I0_JAR"
    fi
}

reject_jar_prefix() {
    local I0_JAR="$1"
    local I0_PREFIX="$2"
    if jar tf "$I0_JAR" | awk -v prefix="$I0_PREFIX" \
            'index($0, prefix) == 1 { found = 1 } END { exit(found ? 0 : 1) }'; then
        fail_i0 "unexpected path prefix $I0_PREFIX in $I0_JAR"
    fi
}

require_jar_text() {
    local I0_JAR="$1"
    local I0_ENTRY="$2"
    local I0_TEXT="$3"
    unzip -p "$I0_JAR" "$I0_ENTRY" | grep -F "$I0_TEXT" >/dev/null ||
        fail_i0 "expected text '$I0_TEXT' in $I0_ENTRY from $I0_JAR"
}

sha256_file() {
    if command -v shasum >/dev/null; then
        shasum -a 256 "$1" | awk '{print $1}'
    elif command -v sha256sum >/dev/null; then
        sha256sum "$1" | awk '{print $1}'
    else
        fail_i0 "no SHA-256 command is available"
    fi
}

write_artifact_digests() {
    local I0_DIGEST_FILE="$1"
    {
        printf 'soma-runtime.jar=%s\n' \
            "$(sha256_file "$I0_REPO_ROOT/soma-runtime/target/soma-runtime-1.0.0-SNAPSHOT.jar")"
        printf 'soma-runtime-sources.jar=%s\n' \
            "$(sha256_file "$I0_REPO_ROOT/soma-runtime/target/soma-runtime-1.0.0-SNAPSHOT-sources.jar")"
        printf 'soma-runtime-javadoc.jar=%s\n' \
            "$(sha256_file "$I0_REPO_ROOT/soma-runtime/target/soma-runtime-1.0.0-SNAPSHOT-javadoc.jar")"
        printf 'soma-processor.jar=%s\n' \
            "$(sha256_file "$I0_REPO_ROOT/soma-processor/target/soma-processor-1.0.0-SNAPSHOT.jar")"
        printf 'soma-processor-sources.jar=%s\n' \
            "$(sha256_file "$I0_REPO_ROOT/soma-processor/target/soma-processor-1.0.0-SNAPSHOT-sources.jar")"
        printf 'soma-processor-javadoc.jar=%s\n' \
            "$(sha256_file "$I0_REPO_ROOT/soma-processor/target/soma-processor-1.0.0-SNAPSHOT-javadoc.jar")"
    } > "$I0_DIGEST_FILE"
}

system_cpu() {
    local I0_CPU
    if command -v sysctl >/dev/null; then
        I0_CPU="$(sysctl -n machdep.cpu.brand_string 2>/dev/null ||
            sysctl -n hw.model 2>/dev/null || true)"
        if [ -n "$I0_CPU" ]; then
            printf '%s\n' "$I0_CPU"
            return
        fi
    fi
    if command -v system_profiler >/dev/null; then
        I0_CPU="$(system_profiler SPHardwareDataType 2>/dev/null |
            awk -F ': ' '/^[[:space:]]*Chip:/ {print $2; exit}')"
        if [ -n "$I0_CPU" ]; then
            printf '%s\n' "$I0_CPU"
            return
        fi
    fi
    if [ -r /proc/cpuinfo ]; then
        awk -F ': ' '/^model name/ {print $2; exit}' /proc/cpuinfo
    else
        uname -p
    fi
}

system_memory_bytes() {
    local I0_MEMORY
    if command -v sysctl >/dev/null && sysctl -n hw.memsize >/dev/null 2>&1; then
        sysctl -n hw.memsize
        return
    fi
    if command -v system_profiler >/dev/null; then
        I0_MEMORY="$(system_profiler SPHardwareDataType 2>/dev/null |
            awk -F ': ' '/^[[:space:]]*Memory:/ {
                if ($2 ~ / GB$/) {
                    value = $2;
                    sub(/ GB$/, "", value);
                    printf "%.0f\n", value * 1024 * 1024 * 1024;
                    exit;
                }
            }')"
        if [ -n "$I0_MEMORY" ]; then
            printf '%s\n' "$I0_MEMORY"
            return
        fi
    fi
    if [ -r /proc/meminfo ]; then
        awk '/^MemTotal:/ {print $2 * 1024; exit}' /proc/meminfo
    else
        printf 'UNKNOWN\n'
    fi
}

run_maven() {
    local I0_ATTEMPT
    local I0_LOG_FILE
    for I0_ATTEMPT in 1 2 3; do
        I0_MAVEN_RUN_NUMBER=$((I0_MAVEN_RUN_NUMBER + 1))
        I0_LOG_FILE="$I0_TMP_ROOT/maven-$I0_MAVEN_RUN_NUMBER.log"
        if [ "$I0_ATTEMPT" -eq 1 ]; then
            if mvn -B -q -Dmaven.repo.local="$I0_LOCAL_REPOSITORY" "$@" \
                    >"$I0_LOG_FILE" 2>&1; then
                return 0
            fi
        else
            if mvn -U -B -q -Dmaven.repo.local="$I0_LOCAL_REPOSITORY" "$@" \
                    >"$I0_LOG_FILE" 2>&1; then
                return 0
            fi
        fi
        if [ "$I0_ATTEMPT" -lt 3 ] &&
                grep -E 'Could not transfer artifact|Premature end of Content-Length|Connection reset|Read timed out|status code: (429|500|502|503|504)' \
                    "$I0_LOG_FILE" >/dev/null; then
            printf 'Retrying Maven after a repository transfer failure (%s/3).\n' \
                "$I0_ATTEMPT" >&2
            continue
        fi
        cat "$I0_LOG_FILE" >&2
        return 1
    done
    return 1
}

cd "$I0_REPO_ROOT"

if [ -z "$I0_JAVA_HOME" ] && [ -x /usr/libexec/java_home ]; then
    I0_JAVA_HOME="$(/usr/libexec/java_home -v 1.8)"
fi
[ -n "$I0_JAVA_HOME" ] || fail_i0 "JAVA_HOME must identify a full Java 8 JDK"
I0_TOOLS_JAR="$I0_JAVA_HOME/lib/tools.jar"
export JAVA_HOME="$I0_JAVA_HOME"

command -v java >/dev/null || fail_i0 "java is unavailable"
command -v javac >/dev/null || fail_i0 "javac is unavailable"
command -v javap >/dev/null || fail_i0 "javap is unavailable"
command -v jar >/dev/null || fail_i0 "jar is unavailable"
command -v unzip >/dev/null || fail_i0 "unzip is unavailable"
command -v mvn >/dev/null || fail_i0 "Maven is unavailable"

if [ -z "$I0_LOCAL_REPOSITORY" ]; then
    I0_USER_HOME="$(java -XshowSettings:properties -version 2>&1 | \
        awk -F ' = ' '/^[[:space:]]*user.home = / {print $2; exit}')"
    [ -n "$I0_USER_HOME" ] || fail_i0 "Java user.home is unavailable"
    I0_LOCAL_REPOSITORY="$I0_USER_HOME/.m2/repository"
    I0_LOCAL_REPOSITORY_MODE="JAVA_USER_HOME_CACHE"
fi

java -version 2>&1 | grep 'version "1\.8\.' >/dev/null ||
    fail_i0 "qualification requires Java 8"
javac -version 2>&1 | grep '1\.8\.' >/dev/null ||
    fail_i0 "qualification requires a full Java 8 JDK"
require_file "$I0_TOOLS_JAR"

run_maven clean install

require_file "$I0_RUNTIME_JAR"
require_file "$I0_PROCESSOR_JAR"
write_artifact_digests "$I0_TMP_ROOT/artifacts-first.txt"

for I0_MODE in \
    class-load observe build-only configure-first default-first \
    null-config invalid-builder constructors public-surface; do
    java -cp \
        "$I0_REPO_ROOT/soma-runtime/target/test-classes:$I0_REPO_ROOT/soma-runtime/target/classes" \
        io.github.somaruntime.soma.internal.RuntimeConfigurationProbe "$I0_MODE"
done

mkdir -p "$I0_PROCESSOR_HARNESS_TMP"
java "-Djava.io.tmpdir=$I0_PROCESSOR_HARNESS_TMP" -cp \
    "$I0_REPO_ROOT/soma-processor/target/test-classes:$I0_REPO_ROOT/soma-processor/target/classes:$I0_TOOLS_JAR" \
    io.github.somaruntime.soma.processor.I0ProcessorHarness \
    "$I0_REPO_ROOT/soma-runtime/target/classes"

run_maven -f "$I0_CONSUMER_DIRECTORY/pom.xml" clean package
java -cp \
    "$I0_CONSUMER_DIRECTORY/target/classes:$I0_RUNTIME_JAR" \
    com.example.scheduler.Application

cp -R "$I0_CONSUMER_DIRECTORY" "$I0_REGEN_CONSUMER"
run_maven -f "$I0_REGEN_CONSUMER/pom.xml" package
I0_GENERATED_SOURCE="$I0_REGEN_CONSUMER/target/generated-sources/soma/com/example/scheduler/soma/internal/SomaGeneratedComposition.java"
I0_GENERATED_MANIFEST="$I0_REGEN_CONSUMER/target/classes/META-INF/soma/composition-manifest.properties"
require_text "$I0_GENERATED_SOURCE" "MachineState"
require_text "$I0_GENERATED_SOURCE" "TransportTime"
require_text "$I0_GENERATED_MANIFEST" \
    "composition.0.table.0.qualifiedName=com.example.scheduler.soma.schema.MachineState"

mv \
    "$I0_REGEN_CONSUMER/src/main/java/com/example/scheduler/soma/schema/MachineState.java" \
    "$I0_REGEN_CONSUMER/src/main/java/com/example/scheduler/soma/schema/MachineStatus.java"
sed -i.bak 's/MachineState/MachineStatus/g' \
    "$I0_REGEN_CONSUMER/src/main/java/com/example/scheduler/soma/schema/MachineStatus.java"
rm "$I0_REGEN_CONSUMER/src/main/java/com/example/scheduler/soma/schema/MachineStatus.java.bak"
run_maven -f "$I0_REGEN_CONSUMER/pom.xml" package
require_text "$I0_GENERATED_SOURCE" "MachineStatus"
reject_text "$I0_GENERATED_SOURCE" "MachineState"
require_text "$I0_GENERATED_MANIFEST" \
    "composition.0.table.0.qualifiedName=com.example.scheduler.soma.schema.MachineStatus"
[ ! -e "$I0_REGEN_CONSUMER/target/classes/com/example/scheduler/soma/schema/MachineState.class" ] ||
    fail_i0 "renamed Table left a stale class"

rm "$I0_REGEN_CONSUMER/src/main/java/com/example/scheduler/soma/schema/MachineStatus.java"
run_maven -f "$I0_REGEN_CONSUMER/pom.xml" package
reject_text "$I0_GENERATED_SOURCE" "MachineStatus"
reject_text "$I0_GENERATED_MANIFEST" "MachineStatus"
require_text "$I0_GENERATED_SOURCE" "TransportTime"
[ ! -e "$I0_REGEN_CONSUMER/target/classes/com/example/scheduler/soma/schema/MachineStatus.class" ] ||
    fail_i0 "deleted Table left a stale class"

mkdir -p "$I0_RUNTIME_SWAP_CLASSES" "$I0_RUNTIME_SWAP_PROBE_CLASSES"
javac -source 8 -target 8 -Xlint:all -Werror \
    -d "$I0_RUNTIME_SWAP_CLASSES" \
    "$I0_REPO_ROOT/tests/i0/runtime-swap/fake-runtime/io/github/somaruntime/soma/internal/SomaRuntimeAccess.java"
javac -source 8 -target 8 -Xlint:all -Werror \
    -cp "$I0_CONSUMER_DIRECTORY/target/classes" \
    -d "$I0_RUNTIME_SWAP_PROBE_CLASSES" \
    "$I0_REPO_ROOT/tests/i0/runtime-swap/probe/com/example/scheduler/RuntimeSwapProbe.java"
java -cp \
    "$I0_RUNTIME_SWAP_PROBE_CLASSES:$I0_CONSUMER_DIRECTORY/target/classes:$I0_RUNTIME_SWAP_CLASSES" \
    com.example.scheduler.RuntimeSwapProbe

for I0_MODULE in soma-runtime soma-processor; do
    require_file "$I0_REPO_ROOT/$I0_MODULE/target/$I0_MODULE-1.0.0-SNAPSHOT.jar"
    require_file "$I0_REPO_ROOT/$I0_MODULE/target/$I0_MODULE-1.0.0-SNAPSHOT-sources.jar"
    require_file "$I0_REPO_ROOT/$I0_MODULE/target/$I0_MODULE-1.0.0-SNAPSHOT-javadoc.jar"
    require_jar_entry "$I0_REPO_ROOT/$I0_MODULE/target/$I0_MODULE-1.0.0-SNAPSHOT.jar" "META-INF/LICENSE"
    require_jar_entry "$I0_REPO_ROOT/$I0_MODULE/target/$I0_MODULE-1.0.0-SNAPSHOT.jar" "META-INF/NOTICE"
    require_jar_entry "$I0_REPO_ROOT/$I0_MODULE/target/$I0_MODULE-1.0.0-SNAPSHOT.jar" "META-INF/sbom/sbom.cdx.json"
    require_jar_entry "$I0_REPO_ROOT/$I0_MODULE/target/$I0_MODULE-1.0.0-SNAPSHOT.jar" "META-INF/soma/provenance.properties"
    I0_JAR_COUNT="$(find "$I0_REPO_ROOT/$I0_MODULE/target" -maxdepth 1 \
        -type f -name '*.jar' | wc -l | tr -d '[:space:]')"
    [ "$I0_JAR_COUNT" = "3" ] ||
        fail_i0 "$I0_MODULE produced an unexpected classifier set"
done

require_jar_entry "$I0_RUNTIME_JAR" "META-INF/soma/runtime-contract.properties"
reject_jar_entry "$I0_RUNTIME_JAR" "META-INF/services/javax.annotation.processing.Processor"
reject_jar_entry "$I0_RUNTIME_JAR" \
    "io/github/somaruntime/soma/processor/SomaProcessor.class"
require_jar_entry "$I0_PROCESSOR_JAR" "META-INF/soma/processor-contract.properties"
require_jar_entry "$I0_PROCESSOR_JAR" "META-INF/services/javax.annotation.processing.Processor"
reject_jar_entry "$I0_PROCESSOR_JAR" "io/github/somaruntime/soma/SomaSchema.class"
reject_jar_prefix \
    "$I0_REPO_ROOT/soma-runtime/target/soma-runtime-1.0.0-SNAPSHOT-javadoc.jar" \
    "io/github/somaruntime/soma/internal/"

require_jar_text "$I0_RUNTIME_JAR" "META-INF/MANIFEST.MF" \
    "Automatic-Module-Name: io.github.somaruntime.soma"
require_jar_text "$I0_RUNTIME_JAR" "META-INF/MANIFEST.MF" \
    "Soma-Contract-Version: 1"
require_jar_text "$I0_RUNTIME_JAR" "META-INF/soma/provenance.properties" \
    "artifact=io.github.somaruntime:soma-runtime"
require_jar_text "$I0_RUNTIME_JAR" "META-INF/sbom/sbom.cdx.json" \
    '"name": "soma-runtime"'
require_jar_text "$I0_PROCESSOR_JAR" "META-INF/MANIFEST.MF" \
    "Automatic-Module-Name: io.github.somaruntime.soma.processor"
require_jar_text "$I0_PROCESSOR_JAR" "META-INF/MANIFEST.MF" \
    "Soma-Contract-Version: 1"
require_jar_text "$I0_PROCESSOR_JAR" "META-INF/soma/provenance.properties" \
    "artifact=io.github.somaruntime:soma-processor"
require_jar_text "$I0_PROCESSOR_JAR" "META-INF/sbom/sbom.cdx.json" \
    '"name": "soma-processor"'
require_jar_text "$I0_PROCESSOR_JAR" \
    "META-INF/services/javax.annotation.processing.Processor" \
    "io.github.somaruntime.soma.processor.SomaProcessor"

javap -verbose -classpath "$I0_RUNTIME_JAR" io.github.somaruntime.soma.SomaConfiguration |
    grep 'major version: 52' >/dev/null || fail_i0 "runtime class is not Java 8 bytecode"
javap -verbose -classpath "$I0_PROCESSOR_JAR" io.github.somaruntime.soma.processor.SomaProcessor |
    grep 'major version: 52' >/dev/null || fail_i0 "processor class is not Java 8 bytecode"

[ -d "$I0_LOCAL_REPOSITORY/io/github/somaruntime/soma-runtime/1.0.0-SNAPSHOT" ] ||
    fail_i0 "runtime artifact was not installed"
[ -d "$I0_LOCAL_REPOSITORY/io/github/somaruntime/soma-processor/1.0.0-SNAPSHOT" ] ||
    fail_i0 "processor artifact was not installed"
[ ! -e "$I0_LOCAL_REPOSITORY/io/github/somaruntime/build/soma-java-reactor" ] ||
    fail_i0 "reactor POM was installed as a third artifact"

run_maven -f "$I0_REPO_ROOT/soma-runtime/pom.xml" \
    org.apache.maven.plugins:maven-dependency-plugin:3.6.1:tree \
    -DoutputFile="$I0_TMP_ROOT/runtime-dependencies.txt"
run_maven -f "$I0_REPO_ROOT/soma-processor/pom.xml" \
    org.apache.maven.plugins:maven-dependency-plugin:3.6.1:tree \
    -DoutputFile="$I0_TMP_ROOT/processor-dependencies.txt"
if grep -E '^[[:space:]]*[+\\]-' "$I0_TMP_ROOT/runtime-dependencies.txt" >/dev/null; then
    fail_i0 "runtime has a production dependency"
fi
if grep -E '^[[:space:]]*[+\\]-' "$I0_TMP_ROOT/processor-dependencies.txt" >/dev/null; then
    fail_i0 "processor has a production dependency"
fi

if git ls-files | grep -E '(^|/)target/|SomaGeneratedComposition\.java$' >/dev/null; then
    fail_i0 "generated/build output is committed"
fi

run_maven clean package
write_artifact_digests "$I0_TMP_ROOT/artifacts-second.txt"
cmp -s "$I0_TMP_ROOT/artifacts-first.txt" "$I0_TMP_ROOT/artifacts-second.txt" ||
    fail_i0 "two clean builds produced different artifact digests"

mkdir -p "$I0_REPORT_DIRECTORY"
I0_GIT_DIRTY="false"
if [ -n "$(git status --porcelain)" ]; then
    I0_GIT_DIRTY="true"
fi
I0_MAX_HEAP_BYTES="$(java -cp \
    "$I0_REPO_ROOT/soma-runtime/target/test-classes:$I0_REPO_ROOT/soma-runtime/target/classes" \
    io.github.somaruntime.soma.internal.RuntimeConfigurationProbe environment)"
{
    printf 'scope=I0\n'
    printf 'result=PASS\n'
    printf 'git.commit=%s\n' "$(git rev-parse HEAD)"
    printf 'git.dirty=%s\n' "$I0_GIT_DIRTY"
    printf 'jdk=%s\n' "$(java -version 2>&1 | head -n 1)"
    printf 'maven=%s\n' "$(mvn -version | head -n 1)"
    printf 'maven.localRepositoryMode=%s\n' "$I0_LOCAL_REPOSITORY_MODE"
    printf 'os=%s %s\n' "$(uname -s)" "$(uname -r)"
    printf 'arch=%s\n' "$(uname -m)"
    printf 'cpu=%s\n' "$(system_cpu)"
    printf 'memory.bytes=%s\n' "$(system_memory_bytes)"
    printf 'maxHeap.bytes=%s\n' "$I0_MAX_HEAP_BYTES"
    printf 'runtime.sha256=%s\n' "$(sha256_file "$I0_RUNTIME_JAR")"
    printf 'processor.sha256=%s\n' "$(sha256_file "$I0_PROCESSOR_JAR")"
    printf 'consumer.manifest.sha256=%s\n' \
        "$(sha256_file "$I0_CONSUMER_DIRECTORY/target/classes/META-INF/soma/composition-manifest.properties")"
    printf 'proofs=clean-reactor,reproducible-artifacts,configuration-order,processor-positive-negative,independent-consumer,full-regeneration,runtime-swap,two-artifact,java8-bytecode,zero-production-dependency\n'
    printf 'limitations=I0 build and carrier scope only; no public Table API, performance, package publication, or release claim\n'
} > "$I0_REPORT_FILE"

cat "$I0_REPORT_FILE"
