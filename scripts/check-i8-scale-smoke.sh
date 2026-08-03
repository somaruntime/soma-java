#!/usr/bin/env bash
set -euo pipefail

I8_SCALE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
I8_SCALE_ROWS="${1:-1000000}"
I8_SCALE_CLASSES="$I8_SCALE_ROOT/target/i8-scale-smoke-classes"
I8_SCALE_OUTPUT="$I8_SCALE_ROOT/target/i8-scale-smoke-output.txt"

cd "$I8_SCALE_ROOT"
I8_JAVA_VERSION="$(java -version 2>&1 | sed -n '1p')"
I8_JAVAC_VERSION="$(javac -version 2>&1 | sed -n '1p')"
case "$I8_JAVA_VERSION" in
    *'1.8.'*) ;;
    *) echo "I8 scale smoke requires Java 8 runtime, got: $I8_JAVA_VERSION" >&2; exit 1 ;;
esac
case "$I8_JAVAC_VERSION" in
    *'1.8.'*) ;;
    *) echo "I8 scale smoke requires Java 8 compiler, got: $I8_JAVAC_VERSION" >&2; exit 1 ;;
esac
./scripts/check-i7.sh
rm -rf "$I8_SCALE_CLASSES"
mkdir -p "$I8_SCALE_CLASSES"
javac -source 8 -target 8 -Xlint:all -Werror \
    -cp "$I8_SCALE_ROOT/soma-runtime/target/classes:$I8_SCALE_ROOT/tests/i2/consumer/target/classes" \
    -d "$I8_SCALE_CLASSES" tests/i8/scale/ScaleQualification.java
I8_SCALE_RUN_OUTPUT="$I8_SCALE_ROOT/target/i8-scale-smoke-run-output.txt"
java -Xms512m -Xmx2g \
    -cp "$I8_SCALE_CLASSES:$I8_SCALE_ROOT/soma-runtime/target/classes:$I8_SCALE_ROOT/tests/i2/consumer/target/classes" \
    com.example.soma.i8.ScaleQualification "$I8_SCALE_ROWS" >"$I8_SCALE_RUN_OUTPUT" 2>&1 &
I8_SCALE_PID=$!
I8_SCALE_STARTED="$(date +%s)"
while kill -0 "$I8_SCALE_PID" 2>/dev/null; do
    I8_SCALE_NOW="$(date +%s)"
    if [ $((I8_SCALE_NOW - I8_SCALE_STARTED)) -ge 150 ]; then
        kill -TERM "$I8_SCALE_PID" 2>/dev/null || true
        sleep 1
        kill -KILL "$I8_SCALE_PID" 2>/dev/null || true
        wait "$I8_SCALE_PID" 2>/dev/null || true
        echo 'I8 scale smoke process watchdog deadline exceeded' >&2
        cat "$I8_SCALE_RUN_OUTPUT" >&2
        exit 1
    fi
    sleep 1
done
if ! wait "$I8_SCALE_PID"; then
    cat "$I8_SCALE_RUN_OUTPUT" >&2
    exit 1
fi
tee "$I8_SCALE_OUTPUT" <"$I8_SCALE_RUN_OUTPUT"
grep -Fx 'status=PASS' "$I8_SCALE_OUTPUT" >/dev/null
grep -Fx "rows=$I8_SCALE_ROWS" "$I8_SCALE_OUTPUT" >/dev/null
grep -Fx 'diagnosticDeadlineMillis=120000' "$I8_SCALE_OUTPUT" >/dev/null
for I8_SCALE_INVALID in 0 -1 15 5000001 abc; do
    I8_SCALE_NEGATIVE_OUTPUT="$I8_SCALE_ROOT/target/i8-scale-smoke-negative-${I8_SCALE_INVALID}.txt"
    if java -Xms256m -Xmx512m \
        -cp "$I8_SCALE_CLASSES:$I8_SCALE_ROOT/soma-runtime/target/classes:$I8_SCALE_ROOT/tests/i2/consumer/target/classes" \
        com.example.soma.i8.ScaleQualification "$I8_SCALE_INVALID" >"$I8_SCALE_NEGATIVE_OUTPUT" 2>&1; then
        echo "expected invalid scale argument to fail: $I8_SCALE_INVALID" >&2
        exit 1
    fi
done
grep -F 'rows must be in [16, 5000000]' "$I8_SCALE_ROOT/target/i8-scale-smoke-negative-0.txt" >/dev/null
grep -F 'rows must be in [16, 5000000]' "$I8_SCALE_ROOT/target/i8-scale-smoke-negative--1.txt" >/dev/null
grep -F 'rows must be in [16, 5000000]' "$I8_SCALE_ROOT/target/i8-scale-smoke-negative-15.txt" >/dev/null
grep -F 'rows must be in [16, 5000000]' "$I8_SCALE_ROOT/target/i8-scale-smoke-negative-5000001.txt" >/dev/null
grep -F 'rows must be a decimal long' "$I8_SCALE_ROOT/target/i8-scale-smoke-negative-abc.txt" >/dev/null
if [ "$I8_SCALE_ROWS" -eq 1000000 ] 2>/dev/null; then
    I8_SCALE_LOAD_PROOF='one-million-row-load'
else
    I8_SCALE_LOAD_PROOF='bounded-scale-load'
fi
I8_SCALE_PROOFS="proofs=java8-harness,$I8_SCALE_LOAD_PROOF,size-capacity,point-lookup,reference-key-fallback,publish-failure-cleanup,sequential-filter,parallel-filter,sequential-parallel-equivalence,checked-sum,group-cardinality,telemetry,invalid-scale-argument-failure"
printf '%s\n' \
    'I8 narrow-scale smoke: PASS' \
    "$I8_SCALE_PROOFS" \
    'safeguards=diagnostic-deadline,process-watchdog' \
    'limitations=no-performance-threshold/no-G9-claim/no-three-scenario-claim'
