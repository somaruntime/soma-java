package io.github.somaruntime.soma.benchmarks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/** 无第三方依赖的 baseline parser/comparator negative-path check。 */
public final class PerformanceBaselineComparatorCheck {
    private PerformanceBaselineComparatorCheck() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("directory required");
        File directory = new File(args[0]);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("cannot create " + directory);
        }
        File baseline = new File(directory, "baseline.json");
        File artifacts = new File(directory, "artifacts.jsonl");
        write(baseline, baseline("Zulu", 12.0d, 2));
        writeRecords(artifacts, records("Zulu", false, 10L, 2));

        PerformanceBaselineDefinition definition =
                PerformanceBaselineDefinition.load(baseline);
        PerformanceBaselineComparator.Evaluation passed =
                PerformanceBaselineComparator.evaluate(
                        definition, Arrays.asList(artifacts));
        require("passed".equals(passed.status), "valid baseline must pass");

        File environmentMismatch = new File(directory, "environment-mismatch.jsonl");
        writeRecords(environmentMismatch, records("Other", false, 10L, 2));
        PerformanceBaselineComparator.Evaluation notApplicable =
                PerformanceBaselineComparator.evaluate(
                        definition, Arrays.asList(environmentMismatch));
        require("not-applicable".equals(notApplicable.status),
                "environment mismatch must be not-applicable");

        File regression = new File(directory, "regression.jsonl");
        writeRecords(regression, records("Zulu", false, 13L, 2));
        PerformanceBaselineComparator.Evaluation failed =
                PerformanceBaselineComparator.evaluate(
                        definition, Arrays.asList(regression));
        require("failed".equals(failed.status), "metric regression must fail");

        File badClaim = new File(directory, "bad-claim.jsonl");
        writeRecords(badClaim, records("Zulu", true, 10L, 2));
        expectInvalid(definition, badClaim, "claimAllowed=true");

        File missingFork = new File(directory, "missing-fork.jsonl");
        writeRecords(missingFork, records("Zulu", false, 10L, 1));
        expectInvalid(definition, missingFork, "configured/observed fork mismatch");

        File wrongIdentity = new File(directory, "wrong-identity.jsonl");
        List<LinkedHashMap<String, Object>> wrong = records("Zulu", false, 10L, 2);
        wrong.get(0).put("profile", "other");
        writeRecords(wrongIdentity, wrong);
        expectInvalid(definition, wrongIdentity, "identity mismatch");

        File unbalanced = new File(directory, "unbalanced.jsonl");
        List<LinkedHashMap<String, Object>> uneven = records("Zulu", false, 10L, 2);
        uneven.add(record("Zulu", false, 10L, 1));
        writeRecords(unbalanced, uneven);
        expectInvalid(definition, unbalanced, "unbalanced selector/fork coverage");

        File extraField = new File(directory, "extra-field.jsonl");
        List<LinkedHashMap<String, Object>> extra = records("Zulu", false, 10L, 2);
        extra.get(0).put("unexpected", Long.valueOf(1L));
        writeRecords(extraField, extra);
        expectInvalid(definition, extraField, "extra record field");

        File badBaseline = new File(directory, "bad-baseline.json");
        LinkedHashMap<String, Object> invalidDefinition = baseline("Zulu", 12.0d, 2);
        invalidDefinition.put("claimAllowed", Boolean.TRUE);
        write(badBaseline, invalidDefinition);
        try {
            PerformanceBaselineDefinition.load(badBaseline);
            throw new AssertionError("baseline claimAllowed=true must fail");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        System.out.println("performance-baseline-comparator-cases: 9");
        System.out.println("performance-baseline-comparator-check: ok");
    }

    private static LinkedHashMap<String, Object> baseline(
            String vendor, double limit, int forks) {
        List<Object> metrics = new ArrayList<Object>();
        metrics.add(BenchmarkModel.object(
                "id", "elapsed",
                "selector", BenchmarkModel.object("kind", "measurement"),
                "field", "elapsedNanos",
                "aggregation", "median",
                "comparison", "at-most",
                "reference", Double.valueOf(limit)));
        metrics.add(BenchmarkModel.object(
                "id", "checksum",
                "selector", BenchmarkModel.object("kind", "measurement"),
                "field", "checksum",
                "aggregation", "all-equal",
                "comparison", "equal",
                "reference", Long.valueOf(7L)));
        return BenchmarkModel.object(
                "schemaVersion", PerformanceBaselineDefinition.SCHEMA_VERSION,
                "baselineId", "synthetic-v1",
                "layer", "component",
                "subject", "synthetic",
                "artifactVersion", "synthetic-artifact-v1",
                "calibration", BenchmarkModel.object(
                        "commit", "abc123",
                        "date", "2026-07-24",
                        "forks", Integer.valueOf(forks),
                        "formula", "synthetic"),
                "environment", environment(vendor),
                "minimumForks", Integer.valueOf(forks),
                "identity", BenchmarkModel.object(
                        "profile", "default"),
                "recordShapes", Arrays.asList(BenchmarkModel.object(
                        "id", "measurement",
                        "selector", BenchmarkModel.object(),
                        "fields", Arrays.asList(
                                "artifactVersion", "profile", "kind", "fork",
                                "configuredForks", "elapsedNanos", "checksum",
                                "claimAllowed", "javaVersion", "javaVendor",
                                "javaVmName", "javaVmVersion", "jvmArgs",
                                "osName", "osVersion", "architecture", "cpu",
                                "maxHeapBytes"))),
                "metrics", metrics,
                "claimAllowed", Boolean.FALSE);
    }

    private static LinkedHashMap<String, Object> environment(String vendor) {
        return BenchmarkModel.object(
                "javaVersion", "1.8",
                "javaVendor", vendor,
                "javaVmName", "VM",
                "javaVmVersion", "25",
                "jvmArgs", Arrays.asList("-Xmx1g"),
                "osName", "OS",
                "osVersion", "1",
                "architecture", "arch",
                "cpu", "cpu",
                "maxHeapBytes", Long.valueOf(100L));
    }

    private static List<LinkedHashMap<String, Object>> records(
            String vendor, boolean claim, long elapsed, int records) {
        List<LinkedHashMap<String, Object>> result =
                new ArrayList<LinkedHashMap<String, Object>>();
        for (int fork = 1; fork <= records; fork++) {
            result.add(record(vendor, claim, elapsed, fork));
        }
        return result;
    }

    private static LinkedHashMap<String, Object> record(
            String vendor, boolean claim, long elapsed, int fork) {
        LinkedHashMap<String, Object> result = BenchmarkModel.object(
                "artifactVersion", "synthetic-artifact-v1",
                "profile", "default",
                "kind", "measurement",
                "fork", Integer.valueOf(fork),
                "configuredForks", Integer.valueOf(2),
                "elapsedNanos", Long.valueOf(elapsed),
                "checksum", Long.valueOf(7L),
                "claimAllowed", Boolean.valueOf(claim));
        result.putAll(environment(vendor));
        return result;
    }

    private static void expectInvalid(
            PerformanceBaselineDefinition definition, File artifact, String message)
            throws Exception {
        try {
            PerformanceBaselineComparator.evaluate(
                    definition, Arrays.asList(artifact));
            throw new AssertionError(message + " must fail");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void writeRecords(
            File target, List<LinkedHashMap<String, Object>> records) throws Exception {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(target), StandardCharsets.UTF_8));
        try {
            for (LinkedHashMap<String, Object> record : records) {
                writer.write(BenchmarkModel.Json.write(record));
                writer.newLine();
            }
        } finally {
            writer.close();
        }
    }

    private static void write(File target, Object value) throws Exception {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(target), StandardCharsets.UTF_8));
        try {
            writer.write(BenchmarkModel.Json.write(value));
            writer.newLine();
        } finally {
            writer.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
