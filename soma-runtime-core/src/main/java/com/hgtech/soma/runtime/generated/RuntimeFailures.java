package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.SomaErrorCategory;
import com.hgtech.soma.runtime.SomaRuntimeException;

import java.util.Map;
import java.util.TreeMap;

/** Generated bindings 使用的 bounded structured failure factories。 */
public final class RuntimeFailures {
    private RuntimeFailures() {
    }

    public static SomaRuntimeException invalidRowIndex(
            String table, int index, int size, long epoch, String operation) {
        Map<String, String> context = context("index", index);
        context.put("size", Integer.toString(size));
        context.put("epoch", Long.toString(epoch));
        return create(SomaErrorCategory.INVALID_INPUT, "invalid_row_index", operation, table, context, null);
    }

    public static SomaRuntimeException optionalAbsent(String table, String field, String operation) {
        return create(SomaErrorCategory.LOOKUP, "optional_absent", operation,
                table + "." + field, empty(), null);
    }

    public static SomaRuntimeException emptyResult(String table, String operation) {
        return create(SomaErrorCategory.LOOKUP, "empty_result", operation,
                table, empty(), null);
    }

    public static SomaRuntimeException invalidNullValue(String table, String field, String operation) {
        return create(SomaErrorCategory.INVALID_INPUT, "invalid_null_value", operation,
                table + "." + field, empty(), null);
    }

    public static SomaRuntimeException missingRequiredField(String table, String field, String operation) {
        return create(SomaErrorCategory.INVALID_INPUT, "missing_required_field", operation,
                table + "." + field, empty(), null);
    }

    public static SomaRuntimeException tableReleased(String table, String operation) {
        return create(SomaErrorCategory.LIFECYCLE, "table_released", operation, table, empty(), null);
    }

    public static SomaRuntimeException pipelineConsumed(String table, String operation) {
        return create(SomaErrorCategory.LIFECYCLE, "pipeline_consumed", operation, table, empty(), null);
    }

    public static SomaRuntimeException mutationConsumed(String table, String operation) {
        return create(SomaErrorCategory.LIFECYCLE, "mutation_consumed", operation, table, empty(), null);
    }

    public static SomaRuntimeException staleMutator(String table, long capturedEpoch, long currentEpoch) {
        Map<String, String> context = context("capturedEpoch", capturedEpoch);
        context.put("currentEpoch", Long.toString(currentEpoch));
        return create(SomaErrorCategory.LIFECYCLE, "stale_mutator", "mutator.commit", table, context, null);
    }

    public static SomaRuntimeException reentrantAccess(
            String table, String activeOperation, String requestedOperation) {
        Map<String, String> context = context("activeOperation", activeOperation);
        context.put("requestedOperation", requestedOperation);
        return create(SomaErrorCategory.LIFECYCLE, "reentrant_access", requestedOperation,
                table, context, null);
    }

    public static SomaRuntimeException callbackFailed(
            String table, String operation, String stage, Throwable cause) {
        return create(SomaErrorCategory.CALLBACK, "callback_failed", operation, table,
                context("stage", stage), cause);
    }

    public static SomaRuntimeException memoryLimitExceeded(
            String table, String operation, long limit, long proposed) {
        Map<String, String> context = context("limit", limit);
        context.put("proposed", Long.toString(proposed));
        return create(SomaErrorCategory.RESOURCE, "memory_limit_exceeded", operation,
                table, context, null);
    }

    public static SomaRuntimeException materializationBudgetExceeded(
            String dimension,
            long limit,
            long current,
            long proposed,
            String budgetIdentity,
            String path) {
        Map<String, String> context = context("dimension", dimension);
        context.put("limit", Long.toString(limit));
        context.put("current", Long.toString(current));
        context.put("proposed", Long.toString(proposed));
        context.put("budgetIdentity", budgetIdentity);
        return create(SomaErrorCategory.RESOURCE, "materialization_budget_exceeded",
                "materialize", path, context, null);
    }

    public static SomaRuntimeException compatibilityMismatch(
            String code, String expected, String actual, String path) {
        Map<String, String> context = context("expected", expected);
        context.put("actual", actual);
        return create(SomaErrorCategory.COMPATIBILITY, code, "table.create", path, context, null);
    }

    public static SomaRuntimeException invalidRuntimePlan(String path, String reason) {
        return create(SomaErrorCategory.INVALID_INPUT, "invalid_runtime_plan", "table.create",
                path, context("reason", reason), null);
    }

    public static SomaRuntimeException internalInvariant(
            String invariantId, String table, String operation) {
        return create(SomaErrorCategory.INTERNAL, "internal_invariant_violation", operation,
                table, context("invariant", invariantId), null);
    }

    private static SomaRuntimeException create(
            SomaErrorCategory category,
            String code,
            String operation,
            String path,
            Map<String, String> context,
            Throwable cause) {
        return SomaRuntimeException.create(category, code, operation, path, context, cause);
    }

    private static Map<String, String> empty() {
        return new TreeMap<String, String>();
    }

    private static Map<String, String> context(String key, Object value) {
        Map<String, String> result = empty();
        result.put(key, String.valueOf(value));
        return result;
    }
}
