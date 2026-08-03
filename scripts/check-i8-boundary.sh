#!/usr/bin/env bash
set -euo pipefail

I8_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
I8_TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/soma-i8-boundary.XXXXXX")"
I8_RUNTIME_JAR="$I8_ROOT/soma-runtime/target/soma-runtime-1.0.0-SNAPSHOT.jar"
I8_PROCESSOR_JAR="$I8_ROOT/soma-processor/target/soma-processor-1.0.0-SNAPSHOT.jar"

cleanup_i8() {
    case "$I8_TMP_ROOT" in
        */soma-i8-boundary.*) rm -rf "$I8_TMP_ROOT" ;;
        *) printf 'Refusing to remove unexpected I8 temporary path: %s\n' "$I8_TMP_ROOT" >&2 ;;
    esac
}
trap cleanup_i8 EXIT

fail_i8() {
    printf 'I8 boundary qualification failed: %s\n' "$1" >&2
    exit 1
}

sha256_file() {
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        sha256sum "$1" | awk '{print $1}'
    fi
}

require_jar_entry() {
    jar tf "$1" | grep -Fx "$2" >/dev/null || fail_i8 "missing $2 in $1"
}

reject_jar_pattern() {
    # Do not use rg -q under pipefail: an early match can SIGPIPE jar and hide a match.
    if jar tf "$1" | rg "$2" >/dev/null; then
        fail_i8 "unexpected entry matching $2 in $1"
    fi
}

cd "$I8_ROOT"
./scripts/check-i7.sh
mvn -B -q clean package

for jarfile in "$I8_RUNTIME_JAR" "$I8_PROCESSOR_JAR"; do
    [ -f "$jarfile" ] || fail_i8 "missing package artifact $jarfile"
    require_jar_entry "$jarfile" META-INF/LICENSE
    require_jar_entry "$jarfile" META-INF/NOTICE
    require_jar_entry "$jarfile" META-INF/sbom/sbom.cdx.json
    require_jar_entry "$jarfile" META-INF/soma/provenance.properties
    reject_jar_pattern "$jarfile" '(^|/)(project|docs|temp|target|\.git)/'
    unzip -p "$jarfile" META-INF/MANIFEST.MF | grep -F 'Java-Version: 8' >/dev/null ||
        fail_i8 "artifact does not declare Java 8 bytecode: $jarfile"
done

for javadoc in \
    "$I8_ROOT/soma-runtime/target/soma-runtime-1.0.0-SNAPSHOT-javadoc.jar" \
    "$I8_ROOT/soma-processor/target/soma-processor-1.0.0-SNAPSHOT-javadoc.jar"; do
    [ -f "$javadoc" ] || fail_i8 "missing javadoc artifact $javadoc"
    reject_jar_pattern "$javadoc" 'io/github/somaruntime/soma/internal/'
done

I8_MODULE_COUNT="$(rg -c '<module>' pom.xml || true)"
[ "$I8_MODULE_COUNT" = "2" ] || fail_i8 "reactor must contain exactly two production modules"
grep -F '<module>soma-runtime</module>' pom.xml >/dev/null || fail_i8 "runtime module missing"
grep -F '<module>soma-processor</module>' pom.xml >/dev/null || fail_i8 "processor module missing"

if git ls-files | rg '(^|/)(target|build|out)/|SomaGeneratedComposition\.java$'; then
    fail_i8 "generated or build output is tracked"
fi
if git ls-files | rg '(^|/)(legacy|predecessor|compatibility|migration|v2)(/|$)'; then
    fail_i8 "legacy or compatibility surface is tracked"
fi
I8_REFLECTION_HITS="$I8_TMP_ROOT/reflection-hits.txt"
I8_REFLECTION_PATTERN='import[[:space:]]+java\.lang\.reflect|java\.lang\.reflect\.(Constructor|Method|Field)|setAccessible|trySetAccessible|sun\.misc\.Unsafe|jdk\.internal|Class\.forName|get(Declared)?(Method|Field|Constructor)s?[[:space:]]*\(|\.invoke[[:space:]]*\('
I8_REFLECTION_FIXTURE_MATCHES="$I8_TMP_ROOT/reflection-fixture-matches.txt"
rg -n "$I8_REFLECTION_PATTERN" tests/i8/static-scan/ReflectionNegative.java \
    > "$I8_REFLECTION_FIXTURE_MATCHES" || true
for I8_REFLECTION_TOKEN in \
    getMethod getMethods getDeclaredMethod getDeclaredMethods \
    getField getFields getDeclaredField getDeclaredFields \
    getConstructor getConstructors getDeclaredConstructor getDeclaredConstructors \
    invoke setAccessible trySetAccessible; do
    grep -F "$I8_REFLECTION_TOKEN(" "$I8_REFLECTION_FIXTURE_MATCHES" >/dev/null ||
        fail_i8 "reflection scan self-test misses $I8_REFLECTION_TOKEN"
done
rg -n "$I8_REFLECTION_PATTERN" \
    soma-runtime/src/main/java soma-processor/src/main/java --glob '*.java' > "$I8_REFLECTION_HITS" || true
if [ -s "$I8_REFLECTION_HITS" ]; then
    fail_i8 "unadmitted reflection or privileged runtime surface"
fi
I8_NEW_INSTANCE_HITS="$I8_TMP_ROOT/new-instance-hits.txt"
rg -n '[^A-Za-z]newInstance[[:space:]]*\(' \
    soma-runtime/src/main/java soma-processor/src/main/java --glob '*.java' > "$I8_NEW_INSTANCE_HITS" || true
sed 's/java\.lang\.reflect\.Array\.newInstance/[allowed-array-newInstance]/g' \
    "$I8_NEW_INSTANCE_HITS" > "$I8_TMP_ROOT/unallowed-new-instance-hits.txt"
if rg -n 'newInstance[[:space:]]*\(' "$I8_TMP_ROOT/unallowed-new-instance-hits.txt" >/dev/null; then
    fail_i8 "unadmitted reflective newInstance entry point"
fi

mvn -B -q clean package
sha256_file "$I8_RUNTIME_JAR" > "$I8_TMP_ROOT/runtime-first.sha256"
sha256_file "$I8_PROCESSOR_JAR" > "$I8_TMP_ROOT/processor-first.sha256"
mvn -B -q clean package
sha256_file "$I8_RUNTIME_JAR" > "$I8_TMP_ROOT/runtime-second.sha256"
sha256_file "$I8_PROCESSOR_JAR" > "$I8_TMP_ROOT/processor-second.sha256"
cmp -s "$I8_TMP_ROOT/runtime-first.sha256" "$I8_TMP_ROOT/runtime-second.sha256" ||
    fail_i8 "runtime package is not reproducible"
cmp -s "$I8_TMP_ROOT/processor-first.sha256" "$I8_TMP_ROOT/processor-second.sha256" ||
    fail_i8 "processor package is not reproducible"

printf '%s\n' \
    'I8 boundary qualification: PASS' \
    'proofs=i7-regression,java8-package-smoke,license-notice,sbom-provenance,javadoc-internal-boundary,'\
'two-production-modules,no-tracked-build-output,no-legacy-surface,reflection-negative-fixture,'\
'reflection-trust-boundary,'\
'reproducible-runtime-jar,reproducible-processor-jar' \
    'limitations=no-three-scenario-correctness/no-million-row-performance/no-compression/no-full-G9-G10'
