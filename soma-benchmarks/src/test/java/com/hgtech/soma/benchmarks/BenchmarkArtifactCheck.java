package com.hgtech.soma.benchmarks;

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
                65, 0x534f4d41L, 0, 1, 1);
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
                "soma-g5-smoke:kernel.optional_all_present:v3", "wrong-workload", -1);
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
                "kernel.keyspace_domain_load_collision_rehash",
                "keySpaceStats", "{\"garbage\":1}");
        expectInvalid(arbitraryKeySpace, "arbitrary non-empty keyspace evidence");

        File arbitrarySidecar = new File(directory, "invalid-arbitrary-sidecar.jsonl");
        rewriteObjectField(valid, arbitrarySidecar,
                "generated.dense_scratch_replace_order",
                "sidecarStats", "{\"garbage\":1}");
        expectInvalid(arbitrarySidecar, "arbitrary non-empty sidecar evidence");

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

        System.out.println("benchmark-artifact-negative-paths: 17");
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
