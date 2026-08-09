#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_root"

if [ -z "${JAVA_HOME:-}" ]; then
    echo "i8-benchmark: JAVA_HOME must select the qualified Java 8 JDK" >&2
    exit 1
fi
java_cmd="$JAVA_HOME/bin/java"
jfr_cmd="$JAVA_HOME/bin/jfr"
test -x "$java_cmd" && test -x "$jfr_cmd" || {
    echo "i8-benchmark: Java 8 java/jfr tools are required" >&2
    exit 1
}
"$java_cmd" -version 2>&1 | grep -q 'version "1\.8\.'

rows=${SOMA_I8_ROWS:-1000000}
runs=${SOMA_I8_RUNS:-3}
parallelism=${SOMA_I8_PARALLELISM:-8}
case "$rows" in *[!0-9]*|'') echo "i8-benchmark: invalid row count" >&2; exit 1 ;; esac
case "$runs" in *[!0-9]*|'') echo "i8-benchmark: invalid run count" >&2; exit 1 ;; esac
case "$parallelism" in *[!0-9]*|'') echo "i8-benchmark: invalid parallelism" >&2; exit 1 ;; esac
test "$rows" -ge 10000 && test "$rows" -le 2000000
test "$runs" -ge 1 && test "$runs" -le 5
test "$parallelism" -ge 1 && test "$parallelism" -le 16

if [ "${SOMA_I8_REUSE_BUILD:-0}" != 1 ]; then
    mvn clean install -Dmaven.install.skip=false -DskipTests
    mvn -f soma-examples/pom.xml clean package -DskipTests
fi

runtime_jar=
for candidate in "$repo_root"/soma-runtime/target/soma-runtime-*.jar; do
    case "$candidate" in *-sources.jar|*-javadoc.jar) ;; *) runtime_jar=$candidate ;; esac
done
test -n "$runtime_jar" || {
    echo "i8-benchmark: runtime artifact not found" >&2
    exit 1
}

output_root="$repo_root/target/i8-profile"
mkdir -p "$output_root"
summary="$output_root/profile-results.txt"
: > "$summary"
{
    echo "rows=$rows"
    echo "runs=$runs"
    echo "parallelism=$parallelism"
    echo "os=$(uname -a)"
    "$java_cmd" -version 2>&1
    if command -v sysctl >/dev/null 2>&1; then
        sysctl -n machdep.cpu.brand_string 2>/dev/null || true
        sysctl -n hw.ncpu 2>/dev/null || true
        sysctl -n hw.memsize 2>/dev/null || true
    fi
} > "$output_root/environment.txt"

run_profile() {
    module=$1
    main_class=$2
    run_number=$3
    run_root="$output_root/$module-run-$run_number"
    stdout_log="$run_root.stdout.log"
    time_log="$run_root.time.log"
    gc_log="$run_root.gc.log"
    jfr_file="$run_root.jfr"
    rm -f "$stdout_log" "$time_log" "$gc_log" "$jfr_file" \
        "$run_root.jfr-summary.txt"

    set -- "$java_cmd" \
        -Xms2g -Xmx8g \
        -XX:+UseParallelGC \
        -XX:+PrintGCDetails -XX:+PrintGCDateStamps "-Xloggc:$gc_log" \
        "-Dsoma.profile.parallelism=$parallelism"
    if [ "$run_number" -eq 1 ]; then
        set -- "$@" -XX:+UnlockCommercialFeatures -XX:+FlightRecorder \
            "-XX:StartFlightRecording=filename=$jfr_file,dumponexit=true,settings=profile"
    fi
    set -- "$@" -cp \
        "$repo_root/soma-examples/$module/target/test-classes:$repo_root/soma-examples/$module/target/classes:$runtime_jar" \
        "$main_class" "$rows"

    set +e
    if [ "$(uname -s)" = Darwin ]; then
        /usr/bin/time -l "$@" > "$stdout_log" 2> "$time_log"
    else
        /usr/bin/time -v "$@" > "$stdout_log" 2> "$time_log"
    fi
    run_status=$?
    set -e
    profile_line=$(grep '^PROFILE ' "$stdout_log" || true)
    if [ -z "$profile_line" ]; then
        cat "$stdout_log" >&2
        cat "$time_log" >&2
        if [ "$run_status" -eq 0 ]; then exit 1; else exit "$run_status"; fi
    fi
    echo "$profile_line" >> "$summary"
    if [ "$run_number" -eq 1 ]; then
        test -s "$jfr_file"
        "$jfr_cmd" summary "$jfr_file" > "$run_root.jfr-summary.txt"
    fi
}

run_number=1
while [ "$run_number" -le "$runs" ]; do
    run_profile scheduling \
        io.github.somaruntime.examples.scheduling.profile.SchedulingProfileMain \
        "$run_number"
    run_profile simulation \
        io.github.somaruntime.examples.simulation.profile.SimulationProfileMain \
        "$run_number"
    run_profile real-time-dispatch \
        io.github.somaruntime.examples.realtimedispatch.profile.RealTimeDispatchProfileMain \
        "$run_number"
    run_number=$((run_number + 1))
done

cat "$summary"
echo "i8-benchmark: PASS ($output_root)"
