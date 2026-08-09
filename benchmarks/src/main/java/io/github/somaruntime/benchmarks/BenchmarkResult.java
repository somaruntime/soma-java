package io.github.somaruntime.benchmarks;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stable machine-readable result emitted once by each fresh benchmark JVM. */
public final class BenchmarkResult {
    private final LinkedHashMap<String, Object> values = new LinkedHashMap<String, Object>();

    public BenchmarkResult(String scenario, String model, String implementation, int rows) {
        put("schemaVersion", 1L);
        put("scenario", scenario);
        put("model", model);
        put("implementation", implementation);
        put("workload", BenchmarkSupport.workload());
        put("rows", rows);
        put("parallelism", BenchmarkSupport.parallelism());
        put("run", BenchmarkSupport.runNumber());
    }

    public BenchmarkResult put(String name, long value) {
        values.put(name, Long.valueOf(value));
        return this;
    }

    public BenchmarkResult put(String name, boolean value) {
        values.put(name, Boolean.valueOf(value));
        return this;
    }

    public BenchmarkResult put(String name, String value) {
        if (value == null) throw new NullPointerException("value");
        values.put(name, value);
        return this;
    }

    public BenchmarkResult put(String name, LongMeasurement measurement) {
        put(name + "Value", measurement.value());
        put(name + "MinNanos", measurement.minimumNanos());
        put(name + "MedianNanos", measurement.medianNanos());
        put(name + "MaxNanos", measurement.maximumNanos());
        return this;
    }

    public void print() {
        StringBuilder json = new StringBuilder(512);
        json.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) json.append(',');
            first = false;
            quote(json, entry.getKey());
            json.append(':');
            Object value = entry.getValue();
            if (value instanceof String) quote(json, (String) value);
            else json.append(value);
        }
        json.append('}');
        System.out.println("BENCHMARK " + json);
    }

    private static void quote(StringBuilder target, String value) {
        target.append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"': target.append("\\\""); break;
                case '\\': target.append("\\\\"); break;
                case '\b': target.append("\\b"); break;
                case '\f': target.append("\\f"); break;
                case '\n': target.append("\\n"); break;
                case '\r': target.append("\\r"); break;
                case '\t': target.append("\\t"); break;
                default:
                    if (current < 0x20) {
                        String hex = Integer.toHexString(current);
                        target.append("\\u");
                        for (int pad = hex.length(); pad < 4; pad++) target.append('0');
                        target.append(hex);
                    } else {
                        target.append(current);
                    }
            }
        }
        target.append('"');
    }
}
