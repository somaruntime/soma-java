#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

profile=${1:-full}
case "$profile" in
  fast|full) ;;
  *)
    printf '%s\n' 'usage: ./scripts/check.sh [fast|full]' >&2
    exit 2
    ;;
esac

configured_jobs=${SOMA_CHECK_JOBS:-4}
case "$configured_jobs" in
  ''|*[!0-9]*|0)
    printf '%s\n' \
      "project-check: SOMA_CHECK_JOBS must be a positive integer, got $configured_jobs" >&2
    exit 2
    ;;
esac

available_processors=$(getconf _NPROCESSORS_ONLN 2>/dev/null || true)
case "$available_processors" in
  ''|*[!0-9]*|0)
    available_processors=$(sysctl -n hw.logicalcpu 2>/dev/null || printf '%s' '1')
    ;;
esac
case "$available_processors" in
  ''|*[!0-9]*|0) available_processors=1 ;;
esac
parallel_jobs=$configured_jobs
if [ "$parallel_jobs" -gt "$available_processors" ]; then
  parallel_jobs=$available_processors
fi

mkdir -p target
check_started_at=$(date +%s)
check_temp_root=${TMPDIR:-/tmp}
check_run_dir=$(mktemp -d "$check_temp_root/soma-java-project-check.XXXXXX")

finish_check() {
  status=$?
  finished_at=$(date +%s)
  duration_seconds=$((finished_at - check_started_at))
  if [ "$status" -eq 0 ]; then
    rm -rf -- "$check_run_dir"
    printf '%s\n' \
      "project-check: ok profile=$profile jobs=$parallel_jobs durationSeconds=$duration_seconds"
  else
    printf '%s\n' \
      "project-check: failed profile=$profile jobs=$parallel_jobs durationSeconds=$duration_seconds status=$status logs=$check_run_dir" >&2
  fi
}
trap finish_check 0

run_stage() {
  run_stage_name=$1
  shift
  run_stage_started_at=$(date +%s)
  printf '%s\n' \
    "check-stage: start name=$run_stage_name mode=serial profile=$profile"
  if "$@"; then
    run_stage_finished_at=$(date +%s)
    printf '%s\n' \
      "check-stage: passed name=$run_stage_name mode=serial durationSeconds=$((run_stage_finished_at - run_stage_started_at))"
  else
    run_stage_status=$?
    run_stage_finished_at=$(date +%s)
    printf '%s\n' \
      "check-stage: failed name=$run_stage_name mode=serial durationSeconds=$((run_stage_finished_at - run_stage_started_at)) status=$run_stage_status" >&2
    return "$run_stage_status"
  fi
}

run_parallel_group() {
  group_name=$1
  shift
  batch=1

  while [ "$#" -gt 0 ]; do
    manifest=$check_run_dir/$group_name-$batch.manifest
    : >"$manifest"
    launched=0

    while [ "$#" -gt 0 ] && [ "$launched" -lt "$parallel_jobs" ]; do
      stage_name=$1
      stage_command=$2
      shift 2
      stage_log=$check_run_dir/$group_name-$stage_name.log
      printf '%s\n' \
        "check-stage: start name=$stage_name mode=parallel group=$group_name profile=$profile"
      (
        stage_started_at=$(date +%s)
        stage_status=0
        "$stage_command" || stage_status=$?
        if [ "$stage_status" -eq 0 ]; then
          stage_finished_at=$(date +%s)
          printf '%s\n' \
            "check-stage: passed name=$stage_name mode=parallel group=$group_name durationSeconds=$((stage_finished_at - stage_started_at))"
          exit 0
        fi
        stage_finished_at=$(date +%s)
        printf '%s\n' \
          "check-stage: failed name=$stage_name mode=parallel group=$group_name durationSeconds=$((stage_finished_at - stage_started_at)) status=$stage_status" >&2
        exit "$stage_status"
      ) >"$stage_log" 2>&1 &
      stage_pid=$!
      printf '%s\t%s\t%s\n' \
        "$stage_pid" "$stage_name" "$stage_log" >>"$manifest"
      launched=$((launched + 1))
    done

    batch_status=0
    while IFS='	' read -r stage_pid stage_name stage_log; do
      if wait "$stage_pid"; then
        :
      else
        batch_status=1
      fi
    done <"$manifest"
    while IFS='	' read -r stage_pid stage_name stage_log; do
      cat "$stage_log"
    done <"$manifest"
    if [ "$batch_status" -ne 0 ]; then
      printf '%s\n' \
        "check-group: failed name=$group_name batch=$batch; no additional batch started" >&2
      return 1
    fi
    batch=$((batch + 1))
  done

  printf '%s\n' "check-group: passed name=$group_name jobs=$parallel_jobs"
}

check_orchestrator_contract() {
  probe_log=$check_run_dir/orchestrator-contract.log
  if run_parallel_group orchestrator-probe \
      probe-pass true \
      probe-fail false >"$probe_log" 2>&1; then
    printf '%s\n' \
      'orchestrator-contract: failed parallel command was reported as passed' >&2
    cat "$probe_log" >&2
    return 1
  fi
  if ! grep -F \
      'check-stage: failed name=probe-fail mode=parallel group=orchestrator-probe' \
      "$probe_log" >/dev/null \
      || ! grep -F \
        'check-group: failed name=orchestrator-probe' \
        "$probe_log" >/dev/null; then
    printf '%s\n' \
      'orchestrator-contract: missing fail-closed parallel evidence' >&2
    cat "$probe_log" >&2
    return 1
  fi
  printf '%s\n' 'orchestrator-contract: ok'
}

prepare_external_artifacts() {
  if ! ./mvnw -B -ntp \
      -pl soma-runtime-core,soma-dataflow,soma-processor -am \
      install -DskipTests; then
    return 1
  fi
  SOMA_EXTERNAL_ARTIFACTS_PREPARED=true
  export SOMA_EXTERNAL_ARTIFACTS_PREPARED
}

check_diff() {
  git diff --check
}

check_code_size_isolation() {
  root_sentinel=$root_dir/target/.code-size-isolation.$$
  benchmark_sentinel=$root_dir/soma-benchmarks/target/.code-size-isolation.$$
  mkdir -p "$root_dir/target" "$root_dir/soma-benchmarks/target"
  printf '%s\n' 'preserve' >"$root_sentinel"
  printf '%s\n' 'preserve' >"$benchmark_sentinel"

  code_size_status=0
  ./scripts/check-scan-code-size.sh || code_size_status=$?
  if [ "$code_size_status" -eq 0 ] \
      && { [ ! -f "$root_sentinel" ] || [ ! -f "$benchmark_sentinel" ]; }; then
    printf '%s\n' \
      'project-check: code-size Gate mutated shared checkout target output' >&2
    code_size_status=1
  fi
  rm -f -- "$root_sentinel" "$benchmark_sentinel"
  return "$code_size_status"
}

run_stage toolchain ./scripts/check-toolchain.sh
run_stage docs ./scripts/check-docs.sh
run_stage performance-baseline-architecture \
  ./scripts/check-performance-baseline-architecture.sh
run_stage reactor-verify ./mvnw -B -ntp verify
SOMA_REACTOR_PREPARED=true
SOMA_BENCHMARKS_PREPARED=true
SOMA_VALIDATION_CONTEXT_RECORDED=true
export SOMA_REACTOR_PREPARED
export SOMA_BENCHMARKS_PREPARED
export SOMA_VALIDATION_CONTEXT_RECORDED

if [ "$profile" = 'fast' ]; then
  run_stage diff check_diff
  exit 0
fi

run_stage orchestrator-contract check_orchestrator_contract
run_stage build-governance ./scripts/check-build-governance.sh
run_parallel_group compile-surface \
  public-api ./scripts/check-public-api.sh \
  compiler-contracts ./scripts/check-compiler-contracts.sh \
  codegen-admission ./scripts/check-codegen-admission.sh

run_stage generated-naming ./scripts/check-generated-naming-contract.sh
run_stage runtime-contracts ./scripts/check-runtime-contracts.sh
run_parallel_group scalar-contracts \
  value-shape ./scripts/check-value-shape-contract.sh \
  default-value ./scripts/check-default-value-contract.sh \
  floating-value-storage ./scripts/check-floating-value-storage.sh \
  schema-diagnostics ./scripts/check-schema-diagnostics-contract.sh

run_stage prepare-external-artifacts prepare_external_artifacts
run_parallel_group generated-consumers \
  generated-keyed ./scripts/check-generated-keyed-contract.sh \
  generated-access ./scripts/check-generated-access-contract.sh \
  generated-ownership ./scripts/check-generated-ownership-contract.sh \
  generated-breadth ./scripts/check-generated-breadth-contract.sh
run_parallel_group integration-consumers \
  generated-dense ./scripts/check-generated-dense-contract.sh \
  external-consumer ./scripts/check-external-consumer.sh \
  reference-applications ./scripts/check-reference-applications.sh

run_stage code-size check_code_size_isolation

run_stage dataflow-contracts ./scripts/check-dataflow-contracts.sh
run_stage dataflow-reference ./scripts/check-dataflow-reference.sh
run_stage benchmark-smoke ./scripts/check-benchmark-smoke.sh
# Performance evidence must remain isolated from all other CPU, cache, memory and GC work.
run_stage access-performance ./scripts/check-access-performance.sh
run_stage dataflow-performance ./scripts/check-dataflow-performance.sh
run_stage diff check_diff
