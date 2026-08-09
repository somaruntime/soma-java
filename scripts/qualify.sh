#!/bin/sh
set -eu

reject_match() {
    set +e
    "$@"
    status=$?
    set -e
    case "$status" in
        0) echo "qualification: forbidden surface detected" >&2; exit 1 ;;
        1) return 0 ;;
        *) exit "$status" ;;
    esac
}

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_root"

if [ -z "${JAVA_HOME:-}" ]; then
    echo "qualification: JAVA_HOME must select the qualified Java 8 JDK" >&2
    exit 1
fi
java_cmd="$JAVA_HOME/bin/java"
javac_cmd="$JAVA_HOME/bin/javac"
jar_cmd="$JAVA_HOME/bin/jar"
for tool in "$java_cmd" "$javac_cmd" "$jar_cmd"; do
    test -x "$tool" || {
        echo "qualification: missing JDK tool: $tool" >&2
        exit 1
    }
done
"$java_cmd" -version 2>&1 | grep -q 'version "1\.8\.'
"$javac_cmd" -version 2>&1 | grep -q '^javac 1\.8\.'

python3 build-support/codegen/generate-grouped-api.py --check
if [ "${SOMA_QUALIFY_REUSE_BUILD:-0}" != 1 ]; then
    mvn clean package
    SOMA_COMPRESSION_REUSE_BUILD=1 build-support/qualification/compression-metadata.sh
fi

# Install exactly the just-built SNAPSHOT pair so the three projects are true
# downstream consumers with an isolated processorpath rather than reactor internals.
mvn install -Dmaven.install.skip=false -DskipTests
mvn -f soma-examples/pom.xml clean package -DskipTests

runtime_jar=
processor_jar=
for candidate in "$repo_root"/soma-runtime/target/soma-runtime-*.jar; do
    case "$candidate" in *-sources.jar|*-javadoc.jar) ;; *) runtime_jar=$candidate ;; esac
done
for candidate in "$repo_root"/soma-processor/target/soma-processor-*.jar; do
    case "$candidate" in *-sources.jar|*-javadoc.jar) ;; *) processor_jar=$candidate ;; esac
done
test -n "$runtime_jar" && test -n "$processor_jar"

run_example() {
    module=$1
    main_class=$2
    expected=$3
    output=$("$java_cmd" -Xms128m -Xmx1g \
        -cp "$repo_root/soma-examples/$module/target/classes:$runtime_jar" \
        "$main_class")
    echo "$output"
    echo "$output" | grep -qx "$expected"
}
run_example scheduling \
    io.github.somaruntime.examples.scheduling.application.SchedulingMain \
    'scheduling-reference: PASS'
run_example simulation \
    io.github.somaruntime.examples.simulation.application.SimulationMain \
    'simulation-reference: PASS'
run_example real-time-dispatch \
    io.github.somaruntime.examples.realtimedispatch.application.RealTimeDispatchMain \
    'real-time-dispatch-reference: PASS'

if [ "${SOMA_QUALIFY_SKIP_BENCHMARK:-0}" != 1 ]; then
    SOMA_BENCHMARK_REUSE_BUILD=1 scripts/benchmark.sh
fi
SOMA_PACKAGE_REUSE_BUILD=1 scripts/package-local.sh

package_root="$repo_root/target/package"
packaged_runtime="$package_root/$(basename -- "$runtime_jar")"
packaged_processor="$package_root/$(basename -- "$processor_jar")"
test -s "$packaged_runtime" && test -s "$packaged_processor"

work_root=$(mktemp -d "${TMPDIR:-/tmp}/soma-qualification.XXXXXX")
cleanup() {
    case "$work_root" in */soma-qualification.*) rm -rf -- "$work_root" ;; *) exit 1 ;; esac
}
trap cleanup EXIT HUP INT TERM
classes="$work_root/classes"
generated="$work_root/generated"
mkdir -p "$classes" "$generated"
schema_sources="$work_root/schema-sources.txt"
all_sources="$work_root/all-sources.txt"
find soma-examples/scheduling/src/main/java/io/github/somaruntime/examples/scheduling/schema \
    -name '*.java' -type f -print | LC_ALL=C sort > "$schema_sources"
"$javac_cmd" -source 8 -target 8 -encoding UTF-8 \
    -classpath "$packaged_runtime" \
    -processorpath "$packaged_processor:$packaged_runtime" \
    -processor io.github.somaruntime.soma.processor.SomaProcessor \
    -Asoma.fullSourceSet=true -d "$classes" -s "$generated" @"$schema_sources"
find soma-examples/scheduling/src/main/java "$generated" \
    -name '*.java' -type f -print | LC_ALL=C sort > "$all_sources"
"$javac_cmd" -source 8 -target 8 -encoding UTF-8 -proc:none \
    -classpath "$classes:$packaged_runtime" -d "$classes" @"$all_sources"
"$java_cmd" -Xms128m -Xmx1g -cp "$classes:$packaged_runtime" \
    io.github.somaruntime.examples.scheduling.application.SchedulingMain \
    | grep -qx 'scheduling-reference: PASS'

archive=
for candidate in "$package_root"/soma-java-*-source-bundle.tar.gz; do archive=$candidate; done
test -s "$archive"
if tar -tzf "$archive" | grep -E '(^|/)(project|tests|scripts|target|\.git)(/|$)|(^|/)build-support/(codegen|delivery|qualification)(/|$)|/src/test/'; then
    echo "qualification: source delivery contains internal evidence" >&2
    exit 1
fi
tar -tzf "$archive" | grep -q '/soma-runtime/src/main/'
tar -tzf "$archive" | grep -q '/soma-processor/src/main/'
tar -tzf "$archive" | grep -q '/soma-examples/scheduling/src/main/'

(cd "$package_root" && if command -v sha256sum >/dev/null 2>&1; then
    sha256sum -c checksums.sha256
else
    shasum -a 256 -c checksums.sha256
fi)
python3 - "$package_root/sbom.spdx.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    document = json.load(source)
assert document["spdxVersion"] == "SPDX-2.3"
packages = {item["name"] for item in document["packages"]}
assert packages == {"soma-runtime", "soma-processor"}
PY

mvn -pl soma-runtime,soma-processor dependency:tree -Dscope=runtime \
    > "$work_root/runtime-dependency-tree.txt"
reject_match grep -q 'org.junit' "$work_root/runtime-dependency-tree.txt"
reject_match grep -R -n -E 'setAccessible|java\.lang\.reflect\.(Field|Constructor)|sun\.misc\.Unsafe' \
    soma-runtime/src/main soma-processor/src/main
for artifact in "$packaged_runtime" "$packaged_processor"; do
    "$jar_cmd" tf "$artifact" > "$work_root/$(basename -- "$artifact").txt"
    reject_match grep -q '^org/junit/' "$work_root/$(basename -- "$artifact").txt"
    reject_match grep -Eq '(^|/)(DataFlow|Transformation|Candidate)(\$|\.|/)' \
        "$work_root/$(basename -- "$artifact").txt"
done

grep -q '11d5960a326750d5838078e36cf38b85af677262' .github/workflows/ci.yml
grep -q 'cf277c60eb25467037889841efdb72551f06f6c3' .github/workflows/ci.yml
grep -q 'workflow_dispatch' .github/workflows/release-qualification.yml
grep -q 'publication=none' "$package_root/provenance.properties"
git diff --check
echo "qualification: PASS"
