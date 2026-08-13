#!/bin/sh
set -eu

mode=full
case "$#" in
    0) ;;
    1)
        if [ "$1" = --check ]; then mode=check
        else echo "qualification: unsupported argument: $1" >&2; exit 2
        fi
        ;;
    *) echo "qualification: expected no arguments or --check" >&2; exit 2 ;;
esac

phase_run() {
    phase=$1
    shift
    started=$(date +%s)
    echo "[soma] phase=$phase status=START mode=$mode"
    set +e
    (set -e; "$@")
    status=$?
    set -e
    duration=$(( $(date +%s) - started ))
    if [ "$status" -ne 0 ]; then
        echo "[soma] phase=$phase status=FAIL mode=$mode durationSec=$duration exitCode=$status" >&2
        return "$status"
    fi
    echo "[soma] phase=$phase status=PASS mode=$mode durationSec=$duration"
}

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
session_file="$repo_root/target/qualification/build-session.properties"

qualification_environment() {
    if [ -z "${JAVA_HOME:-}" ]; then
        echo "qualification: JAVA_HOME must select the qualified Java 8 JDK" >&2
        exit 1
    fi
    for tool in java javac jar javap; do
        test -x "$JAVA_HOME/bin/$tool" || {
            echo "qualification: missing JDK tool: $JAVA_HOME/bin/$tool" >&2
            exit 1
        }
    done
    "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "1\.8\.'
    "$JAVA_HOME/bin/javac" -version 2>&1 | grep -q '^javac 1\.8\.'
    mvn -version | grep -q '^Apache Maven 3\.9\.'
    if [ "$mode" = full ]; then
        python3 build-support/qualification/build-session.py self-test --repo "$repo_root"
    fi
}

qualification_codegen() {
    python3 build-support/codegen/generate-grouped-api.py --check
}

find_production_artifacts() {
    runtime_jar=
    runtime_sources=
    runtime_javadoc=
    processor_jar=
    processor_sources=
    processor_javadoc=
    for candidate in "$repo_root"/soma-runtime/target/soma-runtime-*.jar; do
        case "$candidate" in
            *-sources.jar) runtime_sources=$candidate ;;
            *-javadoc.jar) runtime_javadoc=$candidate ;;
            *) runtime_jar=$candidate ;;
        esac
    done
    for candidate in "$repo_root"/soma-processor/target/soma-processor-*.jar; do
        case "$candidate" in
            *-sources.jar) processor_sources=$candidate ;;
            *-javadoc.jar) processor_javadoc=$candidate ;;
            *) processor_jar=$candidate ;;
        esac
    done
    for artifact in "$runtime_jar" "$runtime_sources" "$runtime_javadoc" \
            "$processor_jar" "$processor_sources" "$processor_javadoc"; do
        test -s "$artifact" || {
            echo "qualification: production artifact set is incomplete" >&2
            exit 1
        }
    done
}

create_core_session() {
    find_production_artifacts
    mkdir -p "$(dirname -- "$session_file")"
    python3 build-support/qualification/build-session.py create \
        --repo "$repo_root" --manifest "$session_file" \
        --artifact "runtime=$runtime_jar" \
        --artifact "runtime-sources=$runtime_sources" \
        --artifact "runtime-javadoc=$runtime_javadoc" \
        --artifact "processor=$processor_jar" \
        --artifact "processor-sources=$processor_sources" \
        --artifact "processor-javadoc=$processor_javadoc"
}

qualification_production_build() {
    if [ "$mode" = check ]; then
        mvn clean install
    else
        build-support/qualification/artifact-build.sh
    fi
    create_core_session
}

qualification_capability() {
    SOMA_BUILD_SESSION_FILE="$session_file" \
        build-support/qualification/compression-metadata.sh
}

find_project_jar() {
    directory=$1
    pattern=$2
    result=
    for candidate in "$directory"/$pattern; do
        case "$candidate" in *-sources.jar|*-javadoc.jar) ;; *) result=$candidate ;; esac
    done
    test -s "$result" || {
        echo "qualification: downstream artifact not found in $directory" >&2
        exit 1
    }
    printf '%s\n' "$result"
}

qualification_build_examples() {
    if [ "$mode" = check ]; then
        mvn -f soma-examples/pom.xml clean package
    else
        mvn -f soma-examples/pom.xml clean install
    fi
}

qualification_run_examples() {
    find_production_artifacts
    java_cmd="$JAVA_HOME/bin/java"
    run_example() {
        module=$1
        main_class=$2
        expected=$3
        shift 3
        output=$("$java_cmd" -Djava.awt.headless=true -Xms128m -Xmx1g \
            -cp "$repo_root/soma-examples/$module/target/classes:$runtime_jar" \
            "$main_class" "$@")
        echo "$output"
        echo "$output" | grep -Fqx "$expected"
    }
    run_example scheduling \
        io.github.somaruntime.examples.scheduling.application.SchedulingMain \
        'scheduling-reference: PASS'
    run_example simulation \
        io.github.somaruntime.examples.simulation.application.SimulationMain \
        'simulation-reference: PASS' \
        --headless --ticks=10
    run_example real-time-dispatch \
        io.github.somaruntime.examples.realtimedispatch.application.RealTimeDispatchMain \
        'real-time-dispatch-reference: PASS'
}

create_full_session() {
    find_production_artifacts
    scheduling_jar=$(find_project_jar \
        "$repo_root/soma-examples/scheduling/target" 'soma-example-scheduling-*.jar')
    simulation_jar=$(find_project_jar \
        "$repo_root/soma-examples/simulation/target" 'soma-example-simulation-*.jar')
    dispatch_jar=$(find_project_jar \
        "$repo_root/soma-examples/real-time-dispatch/target" 'soma-example-real-time-dispatch-*.jar')
    benchmark_jar=$(find_project_jar \
        "$repo_root/benchmarks/target" 'soma-benchmarks-*.jar')
    python3 build-support/qualification/build-session.py create \
        --repo "$repo_root" --manifest "$session_file" \
        --artifact "runtime=$runtime_jar" \
        --artifact "runtime-sources=$runtime_sources" \
        --artifact "runtime-javadoc=$runtime_javadoc" \
        --artifact "processor=$processor_jar" \
        --artifact "processor-sources=$processor_sources" \
        --artifact "processor-javadoc=$processor_javadoc" \
        --artifact "example-scheduling=$scheduling_jar" \
        --artifact "example-simulation=$simulation_jar" \
        --artifact "example-real-time-dispatch=$dispatch_jar" \
        --artifact "benchmarks=$benchmark_jar"
}

qualification_benchmark() {
    mvn -f benchmarks/pom.xml clean package -DskipTests
    create_full_session
    SOMA_BUILD_SESSION_FILE="$session_file" scripts/benchmark.sh
}

qualification_package() {
    SOMA_BUILD_SESSION_FILE="$session_file" scripts/package-local.sh
}

qualification_packaged_consumers() {
    find_production_artifacts
    java_cmd="$JAVA_HOME/bin/java"
    javac_cmd="$JAVA_HOME/bin/javac"
    package_root="$repo_root/target/package"
    packaged_runtime="$package_root/$(basename -- "$runtime_jar")"
    packaged_processor="$package_root/$(basename -- "$processor_jar")"
    test -s "$packaged_runtime" && test -s "$packaged_processor"

    work_root=$(mktemp -d "${TMPDIR:-/tmp}/soma-qualification.XXXXXX")
    cleanup() {
        case "$work_root" in
            */soma-qualification.*) rm -rf -- "$work_root" ;;
            *) echo "qualification: refusing unsafe cleanup target" >&2; exit 1 ;;
        esac
    }
    trap cleanup EXIT HUP INT TERM

    classes="$work_root/classes"
    generated="$work_root/generated"
    mkdir -p "$classes" "$generated"
    schema_sources="$work_root/schema-sources.txt"
    all_sources="$work_root/all-sources.txt"
    find soma-examples/scheduling/src/main/java/io/github/somaruntime/examples/scheduling/runtime/schema \
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

    simulation_classes="$work_root/simulation-classes"
    simulation_generated="$work_root/simulation-generated"
    simulation_schema_sources="$work_root/simulation-schema-sources.txt"
    simulation_all_sources="$work_root/simulation-all-sources.txt"
    mkdir -p "$simulation_classes" "$simulation_generated"
    find soma-examples/simulation/src/main/java/io/github/somaruntime/examples/simulation/runtime/schema \
        -name '*.java' -type f -print | LC_ALL=C sort > "$simulation_schema_sources"
    "$javac_cmd" -source 8 -target 8 -encoding UTF-8 \
        -classpath "$packaged_runtime" \
        -processorpath "$packaged_processor:$packaged_runtime" \
        -processor io.github.somaruntime.soma.processor.SomaProcessor \
        -Asoma.fullSourceSet=true -d "$simulation_classes" -s "$simulation_generated" \
        @"$simulation_schema_sources"
    find soma-examples/simulation/src/main/java "$simulation_generated" \
        -name '*.java' -type f -print | LC_ALL=C sort > "$simulation_all_sources"
    "$javac_cmd" -source 8 -target 8 -encoding UTF-8 -proc:none \
        -classpath "$simulation_classes:$packaged_runtime" \
        -d "$simulation_classes" @"$simulation_all_sources"
    "$java_cmd" -Djava.awt.headless=true -Xms128m -Xmx1g \
        -cp "$simulation_classes:$packaged_runtime" \
        io.github.somaruntime.examples.simulation.application.SimulationMain \
        --headless --ticks=10 \
        --config="$repo_root/soma-examples/simulation/config/grassing.properties" \
        | grep -Fqx 'simulation-reference: PASS'

    trap - EXIT HUP INT TERM
    cleanup
}

qualification_supply_chain() {
    find_production_artifacts
    package_root="$repo_root/target/package"
    packaged_runtime="$package_root/$(basename -- "$runtime_jar")"
    packaged_processor="$package_root/$(basename -- "$processor_jar")"
    work_root=$(mktemp -d "${TMPDIR:-/tmp}/soma-supply-chain.XXXXXX")
    cleanup() {
        case "$work_root" in
            */soma-supply-chain.*) rm -rf -- "$work_root" ;;
            *) echo "qualification: refusing unsafe cleanup target" >&2; exit 1 ;;
        esac
    }
    trap cleanup EXIT HUP INT TERM

    archive=
    for candidate in "$package_root"/soma-java-*-source-bundle.tar.gz; do archive=$candidate; done
    test -s "$archive"
    if tar -tzf "$archive" | grep -E '(^|/)(benchmarks|project|tests|scripts|target|\.git)(/|$)|(^|/)build-support/(codegen|delivery|qualification)(/|$)|/src/test/'; then
        echo "qualification: source delivery contains internal evidence" >&2
        exit 1
    fi
    tar -tzf "$archive" | grep -q '/soma-runtime/src/main/'
    tar -tzf "$archive" | grep -q '/soma-processor/src/main/'
    tar -tzf "$archive" | grep -q '/soma-examples/scheduling/src/main/'
    tar -tzf "$archive" | grep -q '/soma-examples/scheduling/config/fjsp-standard.properties'
    tar -tzf "$archive" | grep -q '/soma-examples/simulation/src/main/'
    tar -tzf "$archive" | grep -q '/soma-examples/simulation/config/grassing.properties'

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
    grep -q '^publication=none$' "$package_root/provenance.properties"
    grep -q '^source.candidate.sha256=[0-9a-f]\{64\}$' \
        "$package_root/provenance.properties"

    trap - EXIT HUP INT TERM
    cleanup
}

qualification_repository_hygiene() {
    find_production_artifacts
    jar_cmd="$JAVA_HOME/bin/jar"
    reject_match grep -R -n -E 'setAccessible|java\.lang\.reflect\.(Field|Constructor)|sun\.misc\.Unsafe' \
        soma-runtime/src/main soma-processor/src/main
    work_root=$(mktemp -d "${TMPDIR:-/tmp}/soma-hygiene.XXXXXX")
    cleanup() {
        case "$work_root" in
            */soma-hygiene.*) rm -rf -- "$work_root" ;;
            *) echo "qualification: refusing unsafe cleanup target" >&2; exit 1 ;;
        esac
    }
    trap cleanup EXIT HUP INT TERM
    for artifact in "$runtime_jar" "$processor_jar"; do
        inventory="$work_root/$(basename -- "$artifact").txt"
        "$jar_cmd" tf "$artifact" > "$inventory"
        reject_match grep -q '^org/junit/' "$inventory"
        reject_match grep -Eq '(^|/)(DataFlow|Transformation|Candidate)(\$|\.|/)' "$inventory"
    done
    grep -q 'fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09' .github/workflows/ci.yml
    grep -q 'b6effb05e454b25005698d916606bdc6ffcbf961' .github/workflows/ci.yml
    grep -q 'fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09' .github/workflows/release-qualification.yml
    grep -q 'b6effb05e454b25005698d916606bdc6ffcbf961' .github/workflows/release-qualification.yml
    grep -q 'workflow_dispatch' .github/workflows/release-qualification.yml
    reject_match grep -q '^  push:' .github/workflows/release-qualification.yml
    grep -Fq 'group: ci-${{ github.workflow }}-${{ github.ref }}' .github/workflows/ci.yml
    grep -q 'cancel-in-progress: true' .github/workflows/ci.yml
    git diff --check
    trap - EXIT HUP INT TERM
    cleanup
}

phase_run environment qualification_environment
phase_run codegen qualification_codegen
phase_run production-build qualification_production_build
phase_run capability-qualification qualification_capability
phase_run examples-build qualification_build_examples
phase_run examples-smoke qualification_run_examples

if [ "$mode" = full ]; then
    phase_run benchmark qualification_benchmark
    phase_run local-package qualification_package
    phase_run packaged-consumer qualification_packaged_consumers
    phase_run supply-chain qualification_supply_chain
fi

phase_run repository-hygiene qualification_repository_hygiene
echo "qualification: PASS (mode=$mode)"
