#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_root"

if [ -z "${JAVA_HOME:-}" ]; then
    echo "benchmark: JAVA_HOME must select the qualified Java 8 JDK" >&2
    exit 1
fi
java_cmd="$JAVA_HOME/bin/java"
jfr_cmd="$JAVA_HOME/bin/jfr"
jfrconv_cmd="$JAVA_HOME/bin/jfrconv"
test -x "$java_cmd" || {
    echo "benchmark: Java 8 java tool is required" >&2
    exit 1
}
"$java_cmd" -version 2>&1 | grep -q 'version "1\.8\.'

rows=${SOMA_BENCHMARK_ROWS:-1000000}
runs=${SOMA_BENCHMARK_RUNS:-3}
parallelism=${SOMA_BENCHMARK_PARALLELISM:-8}
workload=${SOMA_BENCHMARK_WORKLOAD:-core}
scenarios=${SOMA_BENCHMARK_SCENARIOS:-"scheduling simulation real-time-dispatch"}
if [ -n "${SOMA_BENCHMARK_IMPLEMENTATIONS:-}" ]; then
    implementations=$SOMA_BENCHMARK_IMPLEMENTATIONS
elif [ "$workload" = frontier ]; then
    implementations="soma-auto"
elif [ "$workload" = composed ] || [ "$workload" = kernel ]; then
    implementations="soma-auto"
else
    implementations="manual soma-auto"
fi
profiler=${SOMA_BENCHMARK_PROFILER:-jfr}
async_event=${SOMA_BENCHMARK_ASYNC_EVENT:-cpu}
time_mode=${SOMA_BENCHMARK_TIME_MODE:-portable}
inner_warmups=${SOMA_BENCHMARK_INNER_WARMUPS:-2}
inner_samples=${SOMA_BENCHMARK_INNER_SAMPLES:-5}
heap_initial=${SOMA_BENCHMARK_XMS:-2g}
heap_maximum=${SOMA_BENCHMARK_XMX:-8g}
memory_budget=${SOMA_BENCHMARK_MEMORY_BUDGET_BYTES:-6442450944}
memory_attribution=${SOMA_BENCHMARK_MEMORY_ATTRIBUTION:-0}
simulation_population=${SOMA_BENCHMARK_SIMULATION_POPULATION:-}
simulation_ticks=${SOMA_BENCHMARK_SIMULATION_TICKS:-}

case "$rows" in *[!0-9]*|'') echo "benchmark: invalid row count" >&2; exit 1 ;; esac
case "$runs" in *[!0-9]*|'') echo "benchmark: invalid run count" >&2; exit 1 ;; esac
case "$parallelism" in *[!0-9]*|'') echo "benchmark: invalid parallelism" >&2; exit 1 ;; esac
case "$inner_warmups" in *[!0-9]*|'') echo "benchmark: invalid inner warmups" >&2; exit 1 ;; esac
case "$inner_samples" in *[!0-9]*|'') echo "benchmark: invalid inner samples" >&2; exit 1 ;; esac
case "$memory_budget" in *[!0-9]*|'') echo "benchmark: invalid memory budget" >&2; exit 1 ;; esac
case "$memory_attribution" in 0|1) ;; *) echo "benchmark: invalid memory attribution flag" >&2; exit 1 ;; esac
case "$simulation_population" in ''|*[!0-9]*)
    test -z "$simulation_population" || { echo "benchmark: invalid simulation population" >&2; exit 1; } ;;
esac
case "$simulation_ticks" in ''|*[!0-9]*)
    test -z "$simulation_ticks" || { echo "benchmark: invalid simulation ticks" >&2; exit 1; } ;;
esac
test "$rows" -ge 10000 && test "$rows" -le 20000000
test "$runs" -ge 1 && test "$runs" -le 7
test "$parallelism" -ge 1 && test "$parallelism" -le 16
test "$inner_warmups" -ge 0 && test "$inner_warmups" -le 20
test "$inner_samples" -ge 1 && test "$inner_samples" -le 21
test $((inner_samples % 2)) -eq 1
test "$memory_budget" -ge 1 && test "$memory_budget" -le 34359738368
case "$profiler" in none|jfr|async) ;; *) echo "benchmark: invalid profiler" >&2; exit 1 ;; esac
case "$async_event" in cpu|alloc) ;; *) echo "benchmark: invalid async event" >&2; exit 1 ;; esac
case "$time_mode" in portable|extended) ;; *) echo "benchmark: invalid time mode" >&2; exit 1 ;; esac
case "$workload" in core|composed|kernel|frontier) ;; *) echo "benchmark: invalid workload" >&2; exit 1 ;; esac
test -n "$implementations"
for implementation in $implementations; do
    case "$implementation" in
        manual|java-stream|soma-auto|soma-off) ;;
        *) echo "benchmark: invalid implementation: $implementation" >&2; exit 1 ;;
    esac
done
test -n "$scenarios"
for scenario in $scenarios; do
    case "$scenario" in
        scheduling|simulation|simulation-application|real-time-dispatch|type-kernel|frontier-source|frontier-stateful|frontier-relation|frontier-mutation) ;;
        *) echo "benchmark: invalid scenario: $scenario" >&2; exit 1 ;;
    esac
done
for implementation in $implementations; do
    case "$implementation" in
        manual|java-stream)
            for scenario in $scenarios; do
                case "$scenario" in
                    frontier-relation|frontier-mutation)
                        echo "benchmark: $implementation baseline is not defined for $scenario" >&2
                        exit 1
                        ;;
                esac
            done
            ;;
    esac
done
test "$(printf '%s\n' $implementations | LC_ALL=C sort -u | wc -l | tr -d ' ')" \
    -eq "$(printf '%s\n' $implementations | wc -l | tr -d ' ')"
test "$(printf '%s\n' $scenarios | LC_ALL=C sort -u | wc -l | tr -d ' ')" \
    -eq "$(printf '%s\n' $scenarios | wc -l | tr -d ' ')"

if [ "$profiler" = jfr ]; then
    test -x "$jfr_cmd" || {
        echo "benchmark: selected JDK does not provide the jfr tool" >&2
        exit 1
    }
fi
async_library="$JAVA_HOME/lib/libasyncProfiler.dylib"
if [ "$profiler" = async ]; then
    test -f "$async_library" && test -x "$jfrconv_cmd" || {
        echo "benchmark: selected JDK does not provide bundled async-profiler tools" >&2
        exit 1
    }
fi

if [ "${SOMA_BENCHMARK_REUSE_BUILD:-0}" != 1 ]; then
    mvn clean install -Dmaven.install.skip=false -DskipTests
    mvn -f soma-examples/pom.xml clean install -DskipTests
    mvn -f benchmarks/pom.xml clean package -DskipTests
fi

runtime_jar=
for candidate in "$repo_root"/soma-runtime/target/soma-runtime-*.jar; do
    case "$candidate" in *-sources.jar|*-javadoc.jar) ;; *) runtime_jar=$candidate ;; esac
done
test -n "$runtime_jar" || {
    echo "benchmark: runtime artifact not found" >&2
    exit 1
}
test -d "$repo_root/benchmarks/target/classes"
for module in scheduling simulation real-time-dispatch; do
    test -d "$repo_root/soma-examples/$module/target/classes"
done

benchmark_classpath="$repo_root/benchmarks/target/classes:$repo_root/soma-examples/scheduling/target/classes:$repo_root/soma-examples/simulation/target/classes:$repo_root/soma-examples/real-time-dispatch/target/classes:$runtime_jar"
if [ -n "${SOMA_BENCHMARK_OUTPUT_ROOT:-}" ]; then
    output_root=$SOMA_BENCHMARK_OUTPUT_ROOT
elif [ "$workload" = composed ]; then
    output_root="$repo_root/target/benchmark/composed"
elif [ "$workload" = kernel ]; then
    output_root="$repo_root/target/benchmark/kernel"
elif [ "$workload" = frontier ]; then
    output_root="$repo_root/target/benchmark/frontier"
else
    output_root="$repo_root/target/benchmark"
fi
mkdir -p "$output_root"
raw_results="$output_root/results.jsonl"
rm -f "$output_root/summary.json" "$output_root/summary.md"
: > "$raw_results"
{
    echo "commit=$(git rev-parse HEAD)"
    if test -n "$(git status --porcelain=v1)"; then echo "tree.state=dirty"; else echo "tree.state=clean"; fi
    echo "rows=$rows"
    echo "runs=$runs"
    echo "parallelism=$parallelism"
    echo "workload=$workload"
    echo "scenarios=$scenarios"
    echo "implementations=$implementations"
    echo "profiler=$profiler"
    echo "asyncEvent=$async_event"
    echo "timeMode=$time_mode"
    echo "innerWarmups=$inner_warmups"
    echo "innerSamples=$inner_samples"
    echo "xms=$heap_initial"
    echo "xmx=$heap_maximum"
    echo "memoryBudgetBytes=$memory_budget"
    echo "memoryAttribution=$memory_attribution"
    echo "os=$(uname -a)"
    "$java_cmd" -version 2>&1
    mvn -version
    if command -v sysctl >/dev/null 2>&1; then
        sysctl -n machdep.cpu.brand_string 2>/dev/null || true
        sysctl -n hw.physicalcpu 2>/dev/null || true
        sysctl -n hw.logicalcpu 2>/dev/null || true
        sysctl -n hw.memsize 2>/dev/null || true
    fi
} > "$output_root/environment.txt"

run_benchmark() {
    scenario=$1
    main_class=$2
    implementation=$3
    run_number=$4
    run_root="$output_root/$scenario-$implementation-rows$rows-p$parallelism-run$run_number"
    stdout_log="$run_root.stdout.log"
    time_log="$run_root.time.log"
    gc_log="$run_root.gc.log"
    jfr_file="$run_root.jfr"
    async_jfr="$run_root-$async_event.jfr"
    async_collapsed="$run_root-$async_event.collapsed"
    async_html="$run_root-$async_event.html"
    rm -f "$stdout_log" "$time_log" "$gc_log" "$jfr_file" \
        "$async_jfr" "$async_collapsed" "$async_html" \
        "$run_root.jfr-summary.txt"

    set -- "$java_cmd" \
        "-Xms$heap_initial" "-Xmx$heap_maximum" \
        -XX:+UseParallelGC \
        -XX:+PrintGCDetails -XX:+PrintGCDateStamps "-Xloggc:$gc_log" \
        "-Dsoma.benchmark.parallelism=$parallelism" \
        "-Dsoma.benchmark.workload=$workload" \
        "-Dsoma.benchmark.scenario=$scenario" \
        "-Dsoma.benchmark.run=$run_number" \
        "-Dsoma.benchmark.innerWarmups=$inner_warmups" \
        "-Dsoma.benchmark.innerSamples=$inner_samples" \
        "-Dsoma.benchmark.memoryBudgetBytes=$memory_budget"
    if [ "$memory_attribution" = 1 ]; then
        set -- "$@" -Dsoma.benchmark.memoryAttribution=true
    fi
    if [ "$scenario" = simulation-application ]; then
        if [ -n "$simulation_population" ]; then
            set -- "$@" "-Dsoma.benchmark.simulation.population=$simulation_population"
        fi
        if [ -n "$simulation_ticks" ]; then
            set -- "$@" "-Dsoma.benchmark.simulation.ticks=$simulation_ticks"
        fi
    fi
    if [ "$implementation" = soma-auto ] && [ "$run_number" -eq 1 ]; then
        if [ "$profiler" = jfr ]; then
            set -- "$@" -XX:+UnlockCommercialFeatures -XX:+FlightRecorder \
                "-XX:StartFlightRecording=filename=$jfr_file,dumponexit=true,settings=profile"
        elif [ "$profiler" = async ]; then
            set -- "$@" "-agentpath:$async_library=start,event=$async_event,file=$async_jfr"
        fi
    fi
    set -- "$@" -cp "$benchmark_classpath" "$main_class" "$rows" "$implementation"

    set +e
    if [ "$time_mode" = portable ]; then
        /usr/bin/time -p "$@" > "$stdout_log" 2> "$time_log"
    else
        python3 benchmarks/tools/run-with-rusage.py -- "$@" \
            > "$stdout_log" 2> "$time_log"
    fi
    run_status=$?
    set -e
    if [ "$run_status" -ne 0 ]; then
        cat "$stdout_log" >&2
        cat "$time_log" >&2
        exit "$run_status"
    fi
    grep -q '^BENCHMARK ' "$stdout_log"
    python3 benchmarks/tools/collect-result.py \
        --stdout "$stdout_log" --time "$time_log" >> "$raw_results"
    if [ -s "$jfr_file" ]; then
        "$jfr_cmd" summary "$jfr_file" > "$run_root.jfr-summary.txt"
    fi
    if [ -s "$async_jfr" ]; then
        if [ "$async_event" = cpu ]; then async_conversion=--cpu;
        else async_conversion=--alloc;
        fi
        "$jfrconv_cmd" "$async_conversion" -o collapsed \
            "$async_jfr" "$async_collapsed"
        "$jfrconv_cmd" "$async_conversion" -o html \
            "$async_jfr" "$async_html"
    fi
}

run_named_scenario() {
    scenario=$1
    implementation=$2
    run_number=$3
    case "$scenario" in
        scheduling)
            run_benchmark scheduling \
                io.github.somaruntime.benchmarks.scheduling.SchedulingBenchmarkMain \
                "$implementation" "$run_number"
            ;;
        simulation)
            run_benchmark simulation \
                io.github.somaruntime.benchmarks.simulation.SimulationBenchmarkMain \
                "$implementation" "$run_number"
            ;;
        simulation-application)
            case "$implementation" in
                soma-auto|soma-off) ;;
                *) echo "benchmark: $implementation is not defined for simulation-application" >&2; exit 1 ;;
            esac
            run_benchmark simulation-application \
                io.github.somaruntime.benchmarks.simulation.GrassingApplicationBenchmarkMain \
                "$implementation" "$run_number"
            ;;
        real-time-dispatch)
            run_benchmark real-time-dispatch \
                io.github.somaruntime.benchmarks.realtimedispatch.RealTimeDispatchBenchmarkMain \
                "$implementation" "$run_number"
            ;;
        type-kernel)
            run_benchmark type-kernel \
                io.github.somaruntime.benchmarks.kernel.TypeKernelBenchmarkMain \
                "$implementation" "$run_number"
            ;;
        frontier-source|frontier-stateful|frontier-relation|frontier-mutation)
            run_benchmark "$scenario" \
                io.github.somaruntime.benchmarks.kernel.FrontierBenchmarkMain \
                "$implementation" "$run_number"
            ;;
    esac
}

run_scenarios_forward() {
    implementation=$1
    run_number=$2
    for scenario in $scenarios; do
        run_named_scenario "$scenario" "$implementation" "$run_number"
    done
}

run_scenarios_reverse() {
    implementation=$1
    run_number=$2
    reversed_scenarios=$(printf '%s\n' $scenarios | awk '{ value[NR] = $0 } END { for (position = NR; position >= 1; position--) print value[position] }')
    for scenario in $reversed_scenarios; do
        run_named_scenario "$scenario" "$implementation" "$run_number"
    done
}

run_number=1
while [ "$run_number" -le "$runs" ]; do
    if [ $((run_number % 2)) -eq 1 ]; then
        mode_order=$implementations
    else
        mode_order=$(printf '%s\n' $implementations | awk '{ value[NR] = $0 } END { for (position = NR; position >= 1; position--) print value[position] }')
    fi
    for implementation in $mode_order; do
        if [ $((run_number % 2)) -eq 1 ]; then
            run_scenarios_forward "$implementation" "$run_number"
        else
            run_scenarios_reverse "$implementation" "$run_number"
        fi
    done
    run_number=$((run_number + 1))
done

python3 benchmarks/tools/summarize.py \
    --input "$raw_results" \
    --output-json "$output_root/summary.json" \
    --output-markdown "$output_root/summary.md" \
    --expected-runs "$runs"
cat "$output_root/summary.md"
echo "benchmark: PASS ($output_root)"
