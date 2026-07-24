package com.hgtech.soma.benchmarks;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 领域中性的 environment-aware performance baseline comparator。 */
public final class PerformanceBaselineComparator {
    static final String RESULT_SCHEMA_VERSION = "soma-performance-baseline-result-v1";

    private PerformanceBaselineComparator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                    "usage: baseline.json result.json artifact.jsonl...");
        }
        File baselineFile = new File(args[0]);
        File resultFile = new File(args[1]);
        List<File> artifacts = new ArrayList<File>();
        for (int index = 2; index < args.length; index++) {
            artifacts.add(new File(args[index]));
        }
        PerformanceBaselineDefinition baseline =
                PerformanceBaselineDefinition.load(baselineFile);
        Evaluation evaluation = evaluate(baseline, artifacts);
        write(resultFile, evaluation.toMap());
        System.out.println("performance-baseline-result: "
                + resultFile.getAbsolutePath());
        System.out.println("performance-baseline-status: " + evaluation.status);
        if ("failed".equals(evaluation.status)) {
            System.err.println("performance-baseline-failures: "
                    + evaluation.failures);
            System.exit(1);
        }
    }

    static Evaluation evaluate(
            PerformanceBaselineDefinition baseline, List<File> artifacts)
            throws IOException {
        List<Map<String, Object>> records = readArtifacts(artifacts);
        require(!records.isEmpty(), "artifact records");

        Set<Integer> forks = new LinkedHashSet<Integer>();
        Integer configuredForks = null;
        Map<String, Object> firstEnvironment = null;
        for (Map<String, Object> record : records) {
            validateRecordShape(record, baseline.recordShapes);
            require(Boolean.FALSE.equals(record.get("claimAllowed")),
                    "artifact claimAllowed");
            require(baseline.artifactVersion.equals(record.get("artifactVersion")),
                    "artifactVersion");
            int fork = positiveInt(record, "fork");
            forks.add(Integer.valueOf(fork));
            int configured = positiveInt(record, "configuredForks");
            if (configuredForks == null) configuredForks = Integer.valueOf(configured);
            else require(configuredForks.intValue() == configured, "configuredForks");
            for (Map.Entry<String, Object> identity : baseline.identity.entrySet()) {
                require(jsonEquals(value(record, identity.getKey()), identity.getValue()),
                        "identity " + identity.getKey());
            }
            Map<String, Object> environment = environment(record, baseline.environment.keySet());
            if (firstEnvironment == null) firstEnvironment = environment;
            else require(jsonEquals(firstEnvironment, environment),
                    "environment must be stable across records");
        }
        require(configuredForks != null
                        && configuredForks.intValue() == forks.size(),
                "observed fork count");
        require(forks.size() >= baseline.minimumForks, "minimum forks");
        for (int fork = 1; fork <= configuredForks.intValue(); fork++) {
            require(forks.contains(Integer.valueOf(fork)), "contiguous fork indexes");
        }

        List<String> environmentMismatches = new ArrayList<String>();
        for (Map.Entry<String, Object> expected : baseline.environment.entrySet()) {
            Object actual = firstEnvironment.get(expected.getKey());
            if (!jsonEquals(actual, expected.getValue())) {
                environmentMismatches.add(expected.getKey()
                        + " expected=" + display(expected.getValue())
                        + " actual=" + display(actual));
            }
        }

        List<MetricResult> metricResults = new ArrayList<MetricResult>();
        List<String> failures = new ArrayList<String>();
        for (PerformanceBaselineDefinition.MetricRule rule : baseline.metrics) {
            List<Map<String, Object>> selected = select(records, rule.selector);
            require(!selected.isEmpty(), "selector for " + rule.id);
            require(balancedAcrossForks(selected, forks), "fork coverage for " + rule.id);
            List<Object> values = new ArrayList<Object>();
            for (Map<String, Object> record : selected) {
                Object current = value(record, rule.field);
                require(current != null, "metric field " + rule.id);
                values.add(current);
            }
            Aggregated aggregated = aggregate(rule, values);
            require(aggregated.stable, "metric samples not stable for " + rule.id);
            boolean passed = compare(rule, aggregated.value);
            String detail = aggregated.detail;
            if (!passed) {
                failures.add(rule.id + " actual=" + display(aggregated.value)
                        + " reference=" + display(rule.reference)
                        + (detail.isEmpty() ? "" : " " + detail));
            }
            metricResults.add(new MetricResult(rule, aggregated.value, passed, detail));
        }

        String status;
        if (!environmentMismatches.isEmpty()) {
            status = "not-applicable";
            failures.clear();
            for (MetricResult result : metricResults) result.applicable = false;
        } else if (failures.isEmpty()) {
            status = "passed";
        } else {
            status = "failed";
        }
        return new Evaluation(
                baseline, status, artifacts.size(), forks.size(),
                environmentMismatches, metricResults, failures);
    }

    private static List<Map<String, Object>> readArtifacts(List<File> artifacts)
            throws IOException {
        require(artifacts != null && !artifacts.isEmpty(), "artifact files");
        List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
        for (File artifact : artifacts) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(artifact), StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    require(!line.isEmpty(), "empty artifact line");
                    Object parsed = BenchmarkModel.Json.parse(line);
                    require(parsed instanceof Map, "artifact record");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> record = (Map<String, Object>) parsed;
                    records.add(record);
                }
            } finally {
                reader.close();
            }
        }
        return records;
    }

    private static Map<String, Object> environment(
            Map<String, Object> record, Set<String> fields) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        for (String field : fields) {
            Object current = value(record, field);
            require(current != null, "environment " + field);
            result.put(field, current);
        }
        return result;
    }

    private static List<Map<String, Object>> select(
            List<Map<String, Object>> records, Map<String, Object> selector) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> record : records) {
            if (matches(record, selector)) result.add(record);
        }
        return result;
    }

    private static void validateRecordShape(
            Map<String, Object> record,
            List<PerformanceBaselineDefinition.RecordShape> shapes) {
        PerformanceBaselineDefinition.RecordShape matched = null;
        for (PerformanceBaselineDefinition.RecordShape shape : shapes) {
            if (!matches(record, shape.selector)) continue;
            require(matched == null, "record shape ambiguity");
            matched = shape;
        }
        require(matched != null, "record shape missing");
        require(record.keySet().equals(matched.fields),
                "record fields for shape " + matched.id);
    }

    private static boolean matches(
            Map<String, Object> record, Map<String, Object> selector) {
        for (Map.Entry<String, Object> criterion : selector.entrySet()) {
            if (!jsonEquals(value(record, criterion.getKey()), criterion.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean balancedAcrossForks(
            List<Map<String, Object>> selected, Set<Integer> forks) {
        Map<Integer, Integer> counts = new LinkedHashMap<Integer, Integer>();
        for (Integer fork : forks) counts.put(fork, Integer.valueOf(0));
        for (Map<String, Object> record : selected) {
            Integer fork = Integer.valueOf(positiveInt(record, "fork"));
            if (!counts.containsKey(fork)) return false;
            counts.put(fork, Integer.valueOf(counts.get(fork).intValue() + 1));
        }
        Integer expected = null;
        for (Integer count : counts.values()) {
            if (count.intValue() == 0) return false;
            if (expected == null) expected = count;
            else if (!expected.equals(count)) return false;
        }
        return true;
    }

    private static Aggregated aggregate(
            PerformanceBaselineDefinition.MetricRule rule, List<Object> values) {
        if ("all-equal".equals(rule.aggregation)) {
            Object first = values.get(0);
            for (Object value : values) {
                if (!jsonEquals(first, value)) {
                    return new Aggregated(first, false, "samples-not-equal");
                }
            }
            return new Aggregated(first, true, "");
        }
        List<Double> numbers = new ArrayList<Double>();
        for (Object value : values) {
            require(value instanceof Number && finite((Number) value),
                    "numeric metric " + rule.id);
            numbers.add(Double.valueOf(((Number) value).doubleValue()));
        }
        Collections.sort(numbers, new Comparator<Double>() {
            @Override
            public int compare(Double left, Double right) {
                return Double.compare(left.doubleValue(), right.doubleValue());
            }
        });
        if ("maximum".equals(rule.aggregation)) {
            return new Aggregated(numbers.get(numbers.size() - 1), true, "");
        }
        int middle = numbers.size() / 2;
        double median = (numbers.size() & 1) == 1
                ? numbers.get(middle).doubleValue()
                : (numbers.get(middle - 1).doubleValue()
                        + numbers.get(middle).doubleValue()) / 2.0d;
        return new Aggregated(Double.valueOf(median), true, "");
    }

    private static boolean compare(
            PerformanceBaselineDefinition.MetricRule rule, Object actual) {
        if ("equal".equals(rule.comparison)) {
            return jsonEquals(actual, rule.reference);
        }
        require(actual instanceof Number && rule.reference instanceof Number,
                "at-most numeric " + rule.id);
        return ((Number) actual).doubleValue()
                <= ((Number) rule.reference).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private static Object value(Map<String, Object> record, String path) {
        Object current = record;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(part);
        }
        return current;
    }

    private static int positiveInt(Map<String, Object> values, String field) {
        Object value = values.get(field);
        require(value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long, field);
        long number = ((Number) value).longValue();
        require(number > 0L && number <= Integer.MAX_VALUE, field);
        return (int) number;
    }

    private static boolean finite(Number value) {
        double number = value.doubleValue();
        return !Double.isNaN(number) && !Double.isInfinite(number);
    }

    private static boolean jsonEquals(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            if (integral((Number) left) && integral((Number) right)) {
                return ((Number) left).longValue() == ((Number) right).longValue();
            }
            return Double.compare(((Number) left).doubleValue(),
                    ((Number) right).doubleValue()) == 0;
        }
        return left == null ? right == null : left.equals(right);
    }

    private static boolean integral(Number value) {
        return value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long;
    }

    private static String display(Object value) {
        return BenchmarkModel.Json.write(value);
    }

    private static void write(File target, Object value) throws IOException {
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
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
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static final class Aggregated {
        final Object value;
        final boolean stable;
        final String detail;

        Aggregated(Object value, boolean stable, String detail) {
            this.value = value;
            this.stable = stable;
            this.detail = detail;
        }
    }

    static final class MetricResult {
        final PerformanceBaselineDefinition.MetricRule rule;
        final Object actual;
        final boolean passed;
        final String detail;
        boolean applicable = true;

        MetricResult(
                PerformanceBaselineDefinition.MetricRule rule,
                Object actual,
                boolean passed,
                String detail) {
            this.rule = rule;
            this.actual = actual;
            this.passed = passed;
            this.detail = detail;
        }

        LinkedHashMap<String, Object> toMap() {
            return BenchmarkModel.object(
                    "id", rule.id,
                    "aggregation", rule.aggregation,
                    "actual", actual,
                    "comparison", rule.comparison,
                    "reference", rule.reference,
                    "applicable", Boolean.valueOf(applicable),
                    "passed", applicable ? Boolean.valueOf(passed) : null,
                    "detail", detail);
        }
    }

    static final class Evaluation {
        final PerformanceBaselineDefinition baseline;
        final String status;
        final int artifactFiles;
        final int forks;
        final List<String> environmentMismatches;
        final List<MetricResult> metrics;
        final List<String> failures;

        Evaluation(
                PerformanceBaselineDefinition baseline,
                String status,
                int artifactFiles,
                int forks,
                List<String> environmentMismatches,
                List<MetricResult> metrics,
                List<String> failures) {
            this.baseline = baseline;
            this.status = status;
            this.artifactFiles = artifactFiles;
            this.forks = forks;
            this.environmentMismatches = environmentMismatches;
            this.metrics = metrics;
            this.failures = failures;
        }

        LinkedHashMap<String, Object> toMap() {
            List<LinkedHashMap<String, Object>> metricValues =
                    new ArrayList<LinkedHashMap<String, Object>>();
            for (MetricResult metric : metrics) metricValues.add(metric.toMap());
            return BenchmarkModel.object(
                    "schemaVersion", RESULT_SCHEMA_VERSION,
                    "baselineId", baseline.baselineId,
                    "layer", baseline.layer,
                    "subject", baseline.subject,
                    "artifactVersion", baseline.artifactVersion,
                    "status", status,
                    "applicable", Boolean.valueOf(!"not-applicable".equals(status)),
                    "artifactFiles", Integer.valueOf(artifactFiles),
                    "forks", Integer.valueOf(forks),
                    "calibrationCommit", baseline.calibrationCommit,
                    "environmentMismatches", environmentMismatches,
                    "metrics", metricValues,
                    "failures", failures,
                    "claimAllowed", Boolean.FALSE);
        }
    }
}
