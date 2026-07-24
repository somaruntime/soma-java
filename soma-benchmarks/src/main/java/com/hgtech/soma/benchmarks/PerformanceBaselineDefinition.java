package com.hgtech.soma.benchmarks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 领域中性的 performance baseline v1 定义。 */
final class PerformanceBaselineDefinition {
    static final String SCHEMA_VERSION = "soma-performance-baseline-v1";
    private static final Set<String> ROOT_FIELDS = fields(
            "schemaVersion", "baselineId", "layer", "subject", "artifactVersion",
            "calibration", "environment", "minimumForks", "identity",
            "recordShapes", "metrics", "claimAllowed");
    private static final Set<String> CALIBRATION_FIELDS = fields(
            "commit", "date", "forks", "formula");
    private static final Set<String> ENVIRONMENT_FIELDS = fields(
            "javaVersion", "javaVendor", "javaVmName", "javaVmVersion", "jvmArgs",
            "osName", "osVersion", "architecture", "cpu", "maxHeapBytes");
    private static final Set<String> RULE_FIELDS = fields(
            "id", "selector", "field", "aggregation", "comparison", "reference");
    private static final Set<String> SHAPE_FIELDS = fields(
            "id", "selector", "fields");

    final String baselineId;
    final String layer;
    final String subject;
    final String artifactVersion;
    final String calibrationCommit;
    final String calibrationDate;
    final int calibrationForks;
    final String calibrationFormula;
    final LinkedHashMap<String, Object> environment;
    final int minimumForks;
    final LinkedHashMap<String, Object> identity;
    final List<RecordShape> recordShapes;
    final List<MetricRule> metrics;

    private PerformanceBaselineDefinition(
            String baselineId,
            String layer,
            String subject,
            String artifactVersion,
            String calibrationCommit,
            String calibrationDate,
            int calibrationForks,
            String calibrationFormula,
            LinkedHashMap<String, Object> environment,
            int minimumForks,
            LinkedHashMap<String, Object> identity,
            List<RecordShape> recordShapes,
            List<MetricRule> metrics) {
        this.baselineId = baselineId;
        this.layer = layer;
        this.subject = subject;
        this.artifactVersion = artifactVersion;
        this.calibrationCommit = calibrationCommit;
        this.calibrationDate = calibrationDate;
        this.calibrationForks = calibrationForks;
        this.calibrationFormula = calibrationFormula;
        this.environment = environment;
        this.minimumForks = minimumForks;
        this.identity = identity;
        this.recordShapes = recordShapes;
        this.metrics = metrics;
    }

    static PerformanceBaselineDefinition load(File source) throws IOException {
        Object parsed = BenchmarkModel.Json.parse(read(source));
        Map<String, Object> root = object(parsed, "baseline root");
        requireFields(root, ROOT_FIELDS, "baseline root");
        require(SCHEMA_VERSION.equals(root.get("schemaVersion")), "schemaVersion");
        require(Boolean.FALSE.equals(root.get("claimAllowed")), "claimAllowed");

        String baselineId = string(root, "baselineId");
        String layer = string(root, "layer");
        require("component".equals(layer) || "reference-application".equals(layer),
                "layer");
        String subject = string(root, "subject");
        String artifactVersion = string(root, "artifactVersion");

        Map<String, Object> calibration = object(root.get("calibration"), "calibration");
        requireFields(calibration, CALIBRATION_FIELDS, "calibration");
        String calibrationCommit = string(calibration, "commit");
        String calibrationDate = string(calibration, "date");
        int calibrationForks = positiveInt(calibration, "forks");
        String calibrationFormula = string(calibration, "formula");

        Map<String, Object> environmentValue =
                object(root.get("environment"), "environment");
        requireFields(environmentValue, ENVIRONMENT_FIELDS, "environment");
        LinkedHashMap<String, Object> environment =
                copyMap(environmentValue, "environment", true);
        for (String field : ENVIRONMENT_FIELDS) {
            if (!"jvmArgs".equals(field) && !"maxHeapBytes".equals(field)) {
                string(environment, field);
            }
        }
        require(environment.get("jvmArgs") instanceof List, "environment.jvmArgs");
        stringList((List<?>) environment.get("jvmArgs"), "environment.jvmArgs");
        positiveLong(environment, "maxHeapBytes");

        int minimumForks = positiveInt(root, "minimumForks");
        require(calibrationForks >= minimumForks, "calibration forks");

        Map<String, Object> identityValue = object(root.get("identity"), "identity");
        require(!identityValue.isEmpty(), "identity");
        LinkedHashMap<String, Object> identity = copyMap(identityValue, "identity", false);

        Object shapeValue = root.get("recordShapes");
        require(shapeValue instanceof List && !((List<?>) shapeValue).isEmpty(),
                "recordShapes");
        List<RecordShape> recordShapes = new ArrayList<RecordShape>();
        Set<String> shapeIds = new LinkedHashSet<String>();
        for (Object value : (List<?>) shapeValue) {
            Map<String, Object> shape = object(value, "record shape");
            requireFields(shape, SHAPE_FIELDS, "record shape");
            String id = string(shape, "id");
            require(shapeIds.add(id), "duplicate record shape " + id);
            LinkedHashMap<String, Object> selector =
                    copyMap(object(shape.get("selector"), "shape selector"),
                            "shape selector", false);
            Object shapeFields = shape.get("fields");
            require(shapeFields instanceof List && !((List<?>) shapeFields).isEmpty(),
                    "record shape fields " + id);
            stringList((List<?>) shapeFields, "record shape fields " + id);
            LinkedHashSet<String> exactFields = new LinkedHashSet<String>();
            for (Object field : (List<?>) shapeFields) {
                require(exactFields.add((String) field),
                        "duplicate record field " + id + "." + field);
            }
            recordShapes.add(new RecordShape(id, selector, exactFields));
        }

        Object metricValue = root.get("metrics");
        require(metricValue instanceof List && !((List<?>) metricValue).isEmpty(), "metrics");
        List<MetricRule> metrics = new ArrayList<MetricRule>();
        Set<String> ids = new LinkedHashSet<String>();
        for (Object value : (List<?>) metricValue) {
            Map<String, Object> rule = object(value, "metric rule");
            requireFields(rule, RULE_FIELDS, "metric rule");
            String id = string(rule, "id");
            require(ids.add(id), "duplicate metric id " + id);
            LinkedHashMap<String, Object> selector =
                    copyMap(object(rule.get("selector"), "selector"), "selector", false);
            String field = string(rule, "field");
            String aggregation = string(rule, "aggregation");
            require("all-equal".equals(aggregation)
                            || "maximum".equals(aggregation)
                            || "median".equals(aggregation),
                    "aggregation for " + id);
            String comparison = string(rule, "comparison");
            require("equal".equals(comparison) || "at-most".equals(comparison),
                    "comparison for " + id);
            Object reference = rule.get("reference");
            require(scalar(reference), "reference for " + id);
            if ("at-most".equals(comparison)) {
                require(reference instanceof Number && finite((Number) reference),
                        "numeric reference for " + id);
            }
            if (("maximum".equals(aggregation) || "median".equals(aggregation))
                    && !(reference instanceof Number)) {
                throw new IllegalArgumentException("numeric aggregation for " + id);
            }
            metrics.add(new MetricRule(
                    id, selector, field, aggregation, comparison, reference));
        }

        return new PerformanceBaselineDefinition(
                baselineId, layer, subject, artifactVersion,
                calibrationCommit, calibrationDate, calibrationForks,
                calibrationFormula, environment, minimumForks, identity,
                recordShapes, metrics);
    }

    private static String read(File source) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(source), StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        try {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                result.append(buffer, 0, count);
            }
        } finally {
            reader.close();
        }
        return result.toString();
    }

    private static LinkedHashMap<String, Object> copyMap(
            Map<String, Object> source, String owner, boolean allowList) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            require(scalar(value) || (allowList && value instanceof List),
                    owner + "." + entry.getKey());
            if (value instanceof List) {
                stringList((List<?>) value, owner + "." + entry.getKey());
                result.put(entry.getKey(), new ArrayList<Object>((List<?>) value));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String owner) {
        require(value instanceof Map, owner);
        return (Map<String, Object>) value;
    }

    private static String string(Map<String, Object> values, String field) {
        Object value = values.get(field);
        require(value instanceof String && !((String) value).isEmpty(), field);
        return (String) value;
    }

    private static int positiveInt(Map<String, Object> values, String field) {
        long value = positiveLong(values, field);
        require(value <= Integer.MAX_VALUE, field);
        return (int) value;
    }

    private static long positiveLong(Map<String, Object> values, String field) {
        Object value = values.get(field);
        require(value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long, field);
        long result = ((Number) value).longValue();
        require(result > 0L, field);
        return result;
    }

    private static boolean scalar(Object value) {
        return value instanceof String || value instanceof Boolean
                || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double;
    }

    private static boolean finite(Number value) {
        double number = value.doubleValue();
        return !Double.isNaN(number) && !Double.isInfinite(number);
    }

    private static void stringList(List<?> values, String field) {
        for (Object value : values) {
            require(value instanceof String && !((String) value).isEmpty(), field);
        }
    }

    private static void requireFields(
            Map<String, Object> values, Set<String> expected, String owner) {
        require(values.keySet().equals(expected), owner + " fields");
    }

    private static Set<String> fields(String... values) {
        return new LinkedHashSet<String>(Arrays.asList(values));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    static final class MetricRule {
        final String id;
        final LinkedHashMap<String, Object> selector;
        final String field;
        final String aggregation;
        final String comparison;
        final Object reference;

        MetricRule(
                String id,
                LinkedHashMap<String, Object> selector,
                String field,
                String aggregation,
                String comparison,
                Object reference) {
            this.id = id;
            this.selector = selector;
            this.field = field;
            this.aggregation = aggregation;
            this.comparison = comparison;
            this.reference = reference;
        }
    }

    static final class RecordShape {
        final String id;
        final LinkedHashMap<String, Object> selector;
        final Set<String> fields;

        RecordShape(
                String id,
                LinkedHashMap<String, Object> selector,
                Set<String> fields) {
            this.id = id;
            this.selector = selector;
            this.fields = fields;
        }
    }
}
