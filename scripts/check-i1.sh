#!/usr/bin/env bash
set -euo pipefail

I1_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
I1_TMP="$(mktemp -d /tmp/soma-i1-qualification.XXXXXX)"
trap 'rm -rf "$I1_TMP"' EXIT

fail_i1() {
    printf 'I1 qualification failed: %s\n' "$1" >&2
    exit 1
}

cd "$I1_ROOT"
command -v java >/dev/null || fail_i1 "java unavailable"
command -v javac >/dev/null || fail_i1 "javac unavailable"
command -v javap >/dev/null || fail_i1 "javap unavailable"
java -version 2>&1 | grep 'version "1\.8\.' >/dev/null ||
    fail_i1 "qualification requires Java 8"

mvn -B -q -pl soma-runtime,soma-processor -am install -DskipTests
mvn -B -q -f tests/i1/consumer/pom.xml clean package

I1_RUNTIME="$I1_ROOT/soma-runtime/target/classes"
I1_CONSUMER="$I1_ROOT/tests/i1/consumer/target/classes"
I1_CP="$I1_CONSUMER:$I1_RUNTIME"

java -Dsoma.test.chunkSize=2 -cp "$I1_CP" \
    com.example.soma.i1.Application
java -cp "$I1_CP" com.example.soma.i1.ConfigureFirst
java -cp "$I1_CP" com.example.soma.i1.PublicationProbe

# Exercise the full generated file-set regeneration, not only the I0 carrier.
I1_REGEN="$I1_TMP/regen"
mkdir -p "$I1_REGEN"
cp -R tests/i1/consumer/src "$I1_REGEN/src"
cp tests/i1/consumer/pom.xml "$I1_REGEN/pom.xml"
perl -pi -e 's#<soma.root>.*?</soma.root>#<soma.root>'"$I1_ROOT"'</soma.root>#' \
    "$I1_REGEN/pom.xml"
mvn -B -q -f "$I1_REGEN/pom.xml" package
I1_REGEN_GENERATED="$I1_REGEN/target/generated-sources/annotations/com/example/soma/i1/soma"
test -f "$I1_REGEN_GENERATED/WorkItemTable.java" || fail_i1 "regen baseline Table missing"
I1_REGEN_MANIFEST="$I1_REGEN/target/classes/META-INF/soma/composition-manifest.properties"
grep -F 'composition.0.generatedFile.count=5' "$I1_REGEN_MANIFEST" >/dev/null ||
    fail_i1 "generated file-set manifest is incomplete"
I1_REGEN_SOURCE_HASH="$(sha256sum "$I1_REGEN_GENERATED/WorkItemTable.java" "$I1_REGEN_MANIFEST")"
mvn -B -q -f "$I1_REGEN/pom.xml" clean package
test "$I1_REGEN_SOURCE_HASH" = \
    "$(sha256sum "$I1_REGEN_GENERATED/WorkItemTable.java" "$I1_REGEN_MANIFEST")" ||
    fail_i1 "clean regeneration was not deterministic"
mv "$I1_REGEN/src/main/java/com/example/soma/i1/soma/schema/WorkItem.java" \
    "$I1_REGEN/src/main/java/com/example/soma/i1/soma/schema/WorkUnit.java"
find "$I1_REGEN/src" -name '*.java' -print0 | xargs -0 perl -pi -e 's/WorkItem/WorkUnit/g; s/workItem/workUnit/g'
mvn -B -q -f "$I1_REGEN/pom.xml" package
test -f "$I1_REGEN_GENERATED/WorkUnitTable.java" || fail_i1 "renamed Table was not regenerated"
test ! -e "$I1_REGEN_GENERATED/WorkItemTable.java" || fail_i1 "renamed Table left stale generated API"
test ! -e "$I1_REGEN/target/classes/com/example/soma/i1/soma/WorkItem.class" ||
    fail_i1 "renamed Table left stale generated class"
rm "$I1_REGEN/src/main/java/com/example/soma/i1/Application.java" \
    "$I1_REGEN/src/main/java/com/example/soma/i1/ConfigureFirst.java" \
    "$I1_REGEN/src/main/java/com/example/soma/i1/PublicationProbe.java"
perl -0pi -e 's/(@SomaField long duration;)/$1\n    \@SomaField long weight;/' \
    "$I1_REGEN/src/main/java/com/example/soma/i1/soma/schema/WorkUnit.java"
mvn -B -q -f "$I1_REGEN/pom.xml" clean package
test ! -e "$I1_REGEN_GENERATED/WorkUnitTable.java" ||
    fail_i1 "I1-ineligible schema left generated Table API"
grep -F 'composition.0.generatedFile.count=1' "$I1_REGEN_MANIFEST" >/dev/null ||
    fail_i1 "ineligible schema manifest retained stale file set"

I1_GENERATED="$I1_ROOT/tests/i1/consumer/target/generated-sources/annotations/com/example/soma/i1/soma"
test -f "$I1_GENERATED/Soma.java" || fail_i1 "generated Soma.java missing"
test -f "$I1_GENERATED/WorkItemTable.java" || fail_i1 "generated Table missing"
grep -F "SomaFieldEndpoint" "$I1_GENERATED/WorkItemTable.java" >/dev/null ||
    fail_i1 "generated endpoint marker missing"
grep -F "public static class View" "$I1_GENERATED/WorkItemTable.java" >/dev/null ||
    fail_i1 "generated View is not a static borrowed type"
grep -F "public static final class Editor extends View" "$I1_GENERATED/WorkItemTable.java" >/dev/null ||
    fail_i1 "generated Editor/View inheritance contract missing"
grep -F "private WorkItemTable(" "$I1_GENERATED/WorkItemTable.java" >/dev/null ||
    fail_i1 "Table constructor is not private"
if grep -E 'java\.lang\.reflect|Long\.valueOf|longValue\(' \
        "$I1_GENERATED/WorkItemTable.java" >/dev/null; then
    fail_i1 "primitive generated hot path contains reflection or boxing"
fi

javap -classpath "$I1_CP" -private com.example.soma.i1.soma.WorkItemTable |
    grep -F "public void add(com.example.soma.i1.soma.WorkItem);" >/dev/null ||
    fail_i1 "generated add signature missing"
javap -classpath "$I1_CP" -private com.example.soma.i1.soma.WorkItemTable |
    grep -F "public io.github.somaruntime.soma.UpdateResult update(long, java.util.function.Consumer" \
    >/dev/null || fail_i1 "generated update signature missing"

mkdir -p "$I1_TMP/negative-classes"
if javac -source 8 -target 8 -Xlint:all -Werror \
        -cp "$I1_CP" -d "$I1_TMP/negative-classes" \
        tests/i1/negative/ExpressionLambda.java \
        >"$I1_TMP/expression-negative.out" 2>&1; then
    fail_i1 "SomaExpression incorrectly accepted a lambda"
fi
grep -F "functional interface" "$I1_TMP/expression-negative.out" >/dev/null ||
    fail_i1 "negative diagnostic did not identify non-functional expression"
if javac -source 8 -target 8 -Xlint:all -Werror \
        -cp "$I1_CP" -d "$I1_TMP/negative-classes" \
        tests/i1/negative/DirectConstruction.java \
        >"$I1_TMP/construction-negative.out" 2>&1; then
    fail_i1 "private Table constructor was accessible"
fi
grep -E "has private access|cannot be applied" "$I1_TMP/construction-negative.out" >/dev/null ||
    fail_i1 "negative diagnostic did not identify private construction"
test ! -e "$I1_TMP/negative-classes/com/example/soma/i1/negative/ExpressionLambda.class" ||
    fail_i1 "negative compile left an expression class"
test ! -e "$I1_TMP/negative-classes/com/example/soma/i1/negative/DirectConstruction.class" ||
    fail_i1 "negative compile left a construction class"

mkdir -p "$I1_TMP/symbol-classes"
if javac -source 8 -target 8 -Xlint:all -Werror \
        -processor io.github.somaruntime.soma.processor.SomaProcessor \
        -processorpath "$I1_ROOT/soma-processor/target/classes:$I1_ROOT/soma-runtime/target/classes" \
        -Asoma.fullSourceSet=true -cp "$I1_CP" -d "$I1_TMP/symbol-classes" \
        tests/i1/negative/package-info.java tests/i1/negative/ReservedName.java \
        >"$I1_TMP/symbol-negative.out" 2>&1; then
    fail_i1 "generated symbol collision was accepted"
fi
grep -F "SOMA-0303" "$I1_TMP/symbol-negative.out" >/dev/null ||
    fail_i1 "symbol collision diagnostic was not stable"

printf '%s\n' \
    "I1 primitive keyed Table qualification: PASS" \
    "proofs=java8-generated-surface,default-group,explicit-group,chunk-boundary,"\
"reserve-add-find-get,typed-filter,callback-filter,point-update,missing-update,"\
    "duplicate-key,missing-key,detached-fetch,callback-scope,failed-callback,"\
    "cross-thread-scope,error-cleanup,candidate-root-publish,prevalidated-publish,version-invariant,"\
    "foreign-expression-preclaim,immediate-predecessor-claim,"\
    "typed-expression-negative,"\
    "generated-symbol-negative,multi-file-full-regeneration,ineligible-file-set-cleanup,"\
    "no-reflection/no-boxing-static-scan"
