package io.github.somaruntime.soma.benchmarks;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 无JUnit依赖的runner/artifact/schema negative-path验证。 */
public final class BenchmarkArtifactCheck {
    private BenchmarkArtifactCheck() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("evidence directory required");
        File directory = new File(args[0]);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("cannot create " + directory);
        }
        BenchmarkConfig config = new BenchmarkConfig("artifact-check", "smoke-test",
                65, 0x534f4d41L, 0, 1, 2);
        List<BenchmarkRecord> records = SmokeLaneSuite.run(config, new BenchmarkEnvironment());
        File valid = new File(directory, "valid.jsonl");
        BenchmarkModel.write(valid, records);
        require(BenchmarkArtifactValidator.validate(valid) == SmokeLaneSuite.REQUIRED_LANES.size(),
                "valid artifact record count");

        File badClaim = new File(directory, "invalid-claim.jsonl");
        rewrite(valid, badClaim, "\"claimAllowed\":false", "\"claimAllowed\":true", -1);
        expectInvalid(badClaim, "claimAllowed=true");

        File missingLane = new File(directory, "invalid-missing-lane.jsonl");
        rewrite(valid, missingLane, null, null, SmokeLaneSuite.REQUIRED_LANES.size() - 1);
        expectInvalid(missingLane, "missing required lane");

        File extraField = new File(directory, "invalid-extra-field.jsonl");
        rewrite(valid, extraField, "{", "{\"unexpected\":1,", -1);
        expectInvalid(extraField, "unexpected root field");

        File wrongWorkload = new File(directory, "invalid-workload-id.jsonl");
        rewrite(valid, wrongWorkload,
                "soma-g5-smoke:kernel.optional_all_present:v4", "wrong-workload", -1);
        expectInvalid(wrongWorkload, "wrong workload id");

        File emptyEvidence = new File(directory, "invalid-empty-workload-evidence.jsonl");
        rewriteObjectField(valid, emptyEvidence, "kernel.optional_all_present",
                "workloadEvidence", "{}");
        expectInvalid(emptyEvidence, "empty workload evidence");

        File zeroTiming = new File(directory, "invalid-zero-measurement.jsonl");
        rewriteScalarField(valid, zeroTiming, "kernel.optional_all_present",
                "measurementNanos", "0");
        expectInvalid(zeroTiming, "zero measurement metric");

        File wrongNestedType = new File(directory, "invalid-nested-type.jsonl");
        rewriteScalarField(valid, wrongNestedType, "kernel.optional_all_present",
                "rows", "\"65\"");
        expectInvalid(wrongNestedType, "wrong nested metric type");

        File wrongProof = new File(directory, "invalid-lane-proof.jsonl");
        rewrite(valid, wrongProof, "\"proof\":\"optional-bitmap-scan\"",
                "\"proof\":\"wrong-proof\"", -1);
        expectInvalid(wrongProof, "wrong lane proof");

        File arbitrarySelector = new File(directory, "invalid-arbitrary-selector.jsonl");
        rewriteObjectField(valid, arbitrarySelector, "kernel.optional_all_present",
                "selectorStats", "{\"garbage\":1}");
        expectInvalid(arbitrarySelector, "arbitrary non-empty selector evidence");

        File arbitraryKeySpace = new File(directory, "invalid-arbitrary-keyspace.jsonl");
        rewriteObjectField(valid, arbitraryKeySpace,
                "kernel.keyspace_full_domain_load_collision_rehash",
                "keySpaceStats", "{\"garbage\":1}");
        expectInvalid(arbitraryKeySpace, "arbitrary non-empty keyspace evidence");

        File arbitraryExactIndex = new File(directory, "invalid-arbitrary-exact-index.jsonl");
        rewriteObjectField(valid, arbitraryExactIndex,
                "generated.exact_index_incremental_lookup",
                "exactIndexStats", "{\"garbage\":1}");
        expectInvalid(arbitraryExactIndex, "arbitrary non-empty exact-index evidence");

        File arbitraryMaterialization = new File(directory,
                "invalid-arbitrary-materialization.jsonl");
        rewriteObjectField(valid, arbitraryMaterialization,
                "materialization.budget_boundary",
                "materializationStats", "{\"garbage\":1}");
        expectInvalid(arbitraryMaterialization,
                "arbitrary non-empty materialization evidence");

        File zeroReads = new File(directory, "invalid-zero-reads.jsonl");
        rewriteObjectField(valid, zeroReads, "kernel.optional_all_present",
                "mutationReadRatio", "{\"mutations\":0,\"reads\":0}");
        expectInvalid(zeroReads, "scan with zero reads");

        File zeroMutations = new File(directory, "invalid-zero-mutations.jsonl");
        rewriteObjectField(valid, zeroMutations, "generated.pipeline_fusion",
                "mutationReadRatio", "{\"mutations\":0,\"reads\":65}");
        expectInvalid(zeroMutations, "changed rows with zero mutations");

        File missingMaterialization = new File(directory,
                "invalid-materialization-not-applicable.jsonl");
        rewriteObjectField(valid, missingMaterialization, "kernel.key_lookup_normal",
                "materializationStats",
                "{\"applicable\":false,\"reason\":\"wrong\"}");
        expectInvalid(missingMaterialization, "executed materialization marked not-applicable");

        File allocatedWithoutObservation = new File(directory,
                "invalid-allocated-without-observation.jsonl");
        rewrite(valid, allocatedWithoutObservation,
                "\"allocatedBytes\":null", "\"allocatedBytes\":0", -1);
        expectInvalid(allocatedWithoutObservation, "unobserved allocation numeric zero");

        File wrongObservationKind = new File(directory,
                "invalid-observation-kind.jsonl");
        rewrite(valid, wrongObservationKind,
                "\"kind\":\"not-observed\"", "\"kind\":\"measured\"", -1);
        expectInvalid(wrongObservationKind, "wrong allocation observation kind");

        File staleOptionalAggregate = new File(directory,
                "invalid-stale-optional-aggregate.jsonl");
        rewriteNestedScalarField(valid, staleOptionalAggregate,
                "kernel.optional_all_present", "selectorStats", "present", "65");
        expectInvalid(staleOptionalAggregate, "optional nested counter kept first iteration");

        File staleRecursiveRows = new File(directory,
                "invalid-stale-recursive-rows.jsonl");
        rewriteNestedScalarField(valid, staleRecursiveRows,
                "generated.materialization_recursive_success",
                "materializationStats", "rows", "4");
        expectInvalid(staleRecursiveRows, "recursive rows kept first iteration");

        File staleRecursiveEstimate = new File(directory,
                "invalid-stale-recursive-estimate.jsonl");
        rewriteNestedScalarField(valid, staleRecursiveEstimate,
                "generated.materialization_recursive_success",
                "materializationStats", "estimatedBytes", "784");
        expectInvalid(staleRecursiveEstimate, "recursive estimate kept first iteration");

        File staleBudgetAggregate = new File(directory,
                "invalid-stale-budget-aggregate.jsonl");
        rewriteNestedScalarField(valid, staleBudgetAggregate,
                "materialization.budget_boundary", "materializationStats",
                "boundarySuccesses", "5");
        expectInvalid(staleBudgetAggregate, "budget counters kept first iteration");

        File staleColumnViewAggregate = new File(directory,
                "invalid-stale-column-view-aggregate.jsonl");
        rewriteNestedScalarField(valid, staleColumnViewAggregate,
                "kernel.column_view", "columnViewStats", "acquired", "1");
        expectInvalid(staleColumnViewAggregate, "ColumnView counters kept first iteration");

        File staleDenseWorkspace = new File(directory,
                "invalid-stale-dense-workspace.jsonl");
        rewriteNestedScalarField(valid, staleDenseWorkspace,
                "generated.dense_scratch_replace_sort", "selectorStats", "replaceRows", "65");
        expectInvalid(staleDenseWorkspace, "dense workspace counter kept first iteration");

        File staleFrontierExactIndex = new File(directory,
                "invalid-stale-frontier-exact-index.jsonl");
        rewriteNestedScalarField(valid, staleFrontierExactIndex,
                "generated.keyed_frontier", "exactIndexStats", "currentBytes", "0");
        expectInvalid(staleFrontierExactIndex, "frontier exact-index storage mismatch");

        File setupKeySpaceLeak = new File(directory,
                "invalid-setup-keyspace-allocation.jsonl");
        rewriteNestedScalarField(valid, setupKeySpaceLeak,
                "generated.keyed_frontier", "keySpaceStats",
                "keySpaceAllocationBytes", "851968");
        expectInvalid(setupKeySpaceLeak,
                "setup-retained main KeySpace counted as measurement allocation");

        File staleAppendValidationAllocation = new File(directory,
                "invalid-stale-append-validation-allocation.jsonl");
        rewriteNestedScalarField(valid, staleAppendValidationAllocation,
                "generated.keyed_frontier", "keySpaceStats",
                "appendValidationKeySpaceAllocationBytes", "3328");
        expectInvalid(staleAppendValidationAllocation,
                "append-validation KeySpace allocation kept first iteration");

        File wrongHotColumn = new File(directory, "invalid-hot-column.jsonl");
        rewrite(valid, wrongHotColumn, "\"projectedArrivalSecond\"",
                "\"deltaDurationSeconds\"", -1);
        expectInvalid(wrongHotColumn, "Access Pattern Card uses a non-schema hot column");

        File wrongExactLookupTouched = new File(directory,
                "invalid-exact-lookup-touched.jsonl");
        rewriteNestedScalarField(valid, wrongExactLookupTouched,
                "generated.exact_index_incremental_lookup", "exactIndexStats",
                "lookupTouchedBytes", "1");
        expectInvalid(wrongExactLookupTouched,
                "exact-lookup touched bytes contradict traversed rows");

        File staleChildInstances = new File(directory,
                "invalid-stale-child-instances.jsonl");
        rewriteNestedScalarField(valid, staleChildInstances,
                "child_locality.parent_scan_vs_flat", "selectorStats",
                "childInstances", "1");
        expectInvalid(staleChildInstances, "child instance counter kept first iteration");

        File zeroTouched = new File(directory, "invalid-zero-touched.jsonl");
        rewriteScalarField(valid, zeroTouched, "kernel.key_lookup_normal",
                "touchedBytesEstimate", "0");
        expectInvalid(zeroTouched, "lookup with false-zero touched bytes");

        File zeroWorkingSet = new File(directory, "invalid-zero-working-set.jsonl");
        rewriteScalarField(valid, zeroWorkingSet, "generated.pipeline_fusion",
                "workingSetEstimate", "0");
        expectInvalid(zeroWorkingSet, "executed workload with false-zero working set");

        File zeroAllocationEstimate = new File(directory,
                "invalid-zero-allocation-estimate.jsonl");
        rewriteNestedScalarField(valid, zeroAllocationEstimate,
                "kernel.key_lookup_normal", "allocationEstimate", "bytes", "0");
        expectInvalid(zeroAllocationEstimate,
                "materializing lookup with false-zero allocation estimate");

        File duplicateBoundary = new File(directory,
                "invalid-duplicate-boundary.jsonl");
        rewrite(valid, duplicateBoundary,
                "\"dimension\":\"maximumTableInstances\"",
                "\"dimension\":\"maximumOwnershipDepth\"", -1);
        expectInvalid(duplicateBoundary, "duplicate materialization boundary dimension");

        File staleStatsRows = new File(directory, "invalid-stats-row-count.jsonl");
        rewriteNestedScalarField(valid, staleStatsRows, "kernel.stats_mode_overhead",
                "rowCounts", "scanned", "260");
        expectInvalid(staleStatsRows, "stats scan rows omit rows-per-operation factor");

        File leadingZero = new File(directory, "invalid-leading-zero.jsonl");
        rewrite(valid, leadingZero, "\"warmupIterations\":0",
                "\"warmupIterations\":01", -1);
        expectInvalid(leadingZero, "JSON leading zero");

        File exponentOverflow = new File(directory, "invalid-exponent-overflow.jsonl");
        rewriteScalarField(valid, exponentOverflow, "kernel.optional_all_present",
                "operationsPerSecond", "1e309");
        expectInvalid(exponentOverflow, "non-finite exponent overflow");

        System.out.println("benchmark-artifact-negative-paths: 36");
        System.out.println("benchmark-artifact-check: ok");
    }

    private static void rewriteObjectField(File source, File target, String lane,
                                           String field, String replacement) throws Exception {
        rewriteField(source, target, lane, field, replacement, true);
    }

    private static void rewriteScalarField(File source, File target, String lane,
                                           String field, String replacement) throws Exception {
        rewriteField(source, target, lane, field, replacement, false);
    }

    private static void rewriteNestedScalarField(File source, File target, String lane,
                                                  String objectField, String field,
                                                  String replacement) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(source), StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(target), StandardCharsets.UTF_8));
        try {
            String line;
            boolean changed = false;
            while ((line = reader.readLine()) != null) {
                if (!changed && line.contains("\"lane\":\"" + lane + "\"")) {
                    String objectMarker = "\"" + objectField + "\":{";
                    int objectStart = line.indexOf(objectMarker);
                    if (objectStart < 0) throw new AssertionError("missing object " + objectField);
                    int objectEnd = objectStart + objectMarker.length();
                    int depth = 1;
                    while (depth > 0) {
                        char value = line.charAt(objectEnd++);
                        if (value == '{') depth++;
                        else if (value == '}') depth--;
                    }
                    String marker = "\"" + field + "\":";
                    int start = line.indexOf(marker, objectStart);
                    if (start < 0 || start >= objectEnd) {
                        throw new AssertionError("missing nested field " + field);
                    }
                    int valueStart = start + marker.length();
                    int valueEnd = valueStart;
                    while (valueEnd < objectEnd && line.charAt(valueEnd) != ','
                            && line.charAt(valueEnd) != '}') valueEnd++;
                    line = line.substring(0, valueStart) + replacement
                            + line.substring(valueEnd);
                    changed = true;
                }
                writer.write(line);
                writer.newLine();
            }
            if (!changed) throw new AssertionError("lane not found " + lane);
        } finally {
            try { reader.close(); } finally { writer.close(); }
        }
    }

    private static void rewriteField(File source, File target, String lane, String field,
                                     String replacement, boolean object) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(source), StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(target), StandardCharsets.UTF_8));
        try {
            String line;
            boolean changed = false;
            while ((line = reader.readLine()) != null) {
                if (!changed && line.contains("\"lane\":\"" + lane + "\"")) {
                    String marker = "\"" + field + "\":";
                    int start = line.indexOf(marker);
                    if (start < 0) throw new AssertionError("missing field " + field);
                    int valueStart = start + marker.length();
                    int valueEnd;
                    if (object) {
                        int depth = 0;
                        valueEnd = valueStart;
                        do {
                            char value = line.charAt(valueEnd++);
                            if (value == '{') depth++;
                            else if (value == '}') depth--;
                        } while (depth > 0);
                    } else {
                        valueEnd = valueStart;
                        while (valueEnd < line.length() && line.charAt(valueEnd) != ','
                                && line.charAt(valueEnd) != '}') valueEnd++;
                    }
                    line = line.substring(0, valueStart) + replacement + line.substring(valueEnd);
                    changed = true;
                }
                writer.write(line);
                writer.newLine();
            }
            if (!changed) throw new AssertionError("lane not found " + lane);
        } finally {
            try { reader.close(); } finally { writer.close(); }
        }
    }

    private static void rewrite(File source, File target, String from, String to,
                                int skipLine) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(source), StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(target), StandardCharsets.UTF_8));
        try {
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                if (index++ == skipLine) continue;
                if (from != null) line = line.replace(from, to);
                writer.write(line);
                writer.newLine();
            }
        } finally {
            try { reader.close(); } finally { writer.close(); }
        }
    }

    private static void expectInvalid(File artifact, String message) throws Exception {
        try {
            BenchmarkArtifactValidator.validate(artifact);
            throw new AssertionError(message + " must fail");
        } catch (IllegalArgumentException expected) {
            // expected strict schema/semantic rejection
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
