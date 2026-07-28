package io.github.somaruntime.soma.runtime;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/** Unchecked structured runtime failure envelope。 */
public final class SomaRuntimeException extends RuntimeException {
    private static final int MAX_VALUE_LENGTH = 256;
    private static final int MAX_CONTEXT_LENGTH = 4096;
    private static final String TRUNCATED = "...[truncated]";

    private final SomaErrorCategory category;
    private final String code;
    private final String operation;
    private final String path;
    private final SortedMap<String, String> context;

    private SomaRuntimeException(
            SomaErrorCategory category,
            String code,
            String operation,
            String path,
            SortedMap<String, String> context,
            Throwable cause) {
        super(render(category, code, operation, path, context), cause);
        this.category = category;
        this.code = code;
        this.operation = operation;
        this.path = path;
        this.context = context;
    }

    public static SomaRuntimeException create(
            SomaErrorCategory category,
            String code,
            String operation,
            String path,
            Map<String, String> context,
            Throwable cause) {
        if (category == null) {
            throw new NullPointerException("category");
        }
        String safeCode = CanonicalSupport.required(code, "code");
        String safeOperation = CanonicalSupport.required(operation, "operation");
        String safePath = path == null ? "" : truncate(path, MAX_VALUE_LENGTH);
        if (context == null) {
            throw new NullPointerException("context");
        }
        TreeMap<String, String> safe = new TreeMap<String, String>();
        int remaining = MAX_CONTEXT_LENGTH;
        for (Map.Entry<String, String> entry : context.entrySet()) {
            String key = CanonicalSupport.required(entry.getKey(), "context key");
            String value = entry.getValue() == null ? "" : truncate(entry.getValue(), MAX_VALUE_LENGTH);
            int cost = key.length() + value.length() + 2;
            if (cost > remaining) {
                safe.put("truncated", TRUNCATED);
                break;
            }
            safe.put(key, value);
            remaining -= cost;
        }
        SortedMap<String, String> snapshot = Collections.unmodifiableSortedMap(safe);
        return new SomaRuntimeException(
                category, safeCode, safeOperation, safePath, snapshot, cause);
    }

    public SomaErrorCategory category() { return category; }
    public String code() { return code; }
    public String operation() { return operation; }
    public String path() { return path; }
    public SortedMap<String, String> context() { return context; }

    private static String truncate(String value, int limit) {
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit - TRUNCATED.length()) + TRUNCATED;
    }

    private static String render(
            SomaErrorCategory category,
            String code,
            String operation,
            String path,
            SortedMap<String, String> context) {
        return "[" + code + "] category=" + category.name().toLowerCase(Locale.ROOT)
                + " operation=" + operation + " path=" + path + " context=" + context;
    }
}
