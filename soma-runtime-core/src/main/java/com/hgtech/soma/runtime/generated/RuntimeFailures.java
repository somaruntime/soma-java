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

    public static SomaRuntimeException indexSnapshotWrongTable(
            String table, String operation) {
        return create(SomaErrorCategory.INVALID_INPUT, "index_snapshot_wrong_table",
                operation, table, empty(), null);
    }

    public static SomaRuntimeException staleIndexSnapshot(
            String table, long capturedEpoch, long currentEpoch, String operation) {
        Map<String, String> context = context("capturedEpoch", capturedEpoch);
        context.put("currentEpoch", Long.toString(currentEpoch));
        return create(SomaErrorCategory.LIFECYCLE, "stale_index_snapshot",
                operation, table, context, null);
    }

    public static SomaRuntimeException optionalAbsent(String table, String field, String operation) {
        return create(SomaErrorCategory.LOOKUP, "optional_absent", operation,
                table + "." + field, empty(), null);
    }

    public static SomaRuntimeException emptyResult(String table, String operation) {
        return create(SomaErrorCategory.LOOKUP, "empty_result", operation,
                table, empty(), null);
    }

    public static SomaRuntimeException duplicateKey(String table, int key, String operation) {
        return create(SomaErrorCategory.CONFLICT, "duplicate_key", operation,
                table, context("key", key), null);
    }

    public static SomaRuntimeException missingKey(String table, int key, String operation) {
        return create(SomaErrorCategory.LOOKUP, "missing_key", operation,
                table, context("key", key), null);
    }

    public static SomaRuntimeException duplicateKey(String table, long key, String operation) {
        return create(SomaErrorCategory.CONFLICT, "duplicate_key", operation,
                table, context("key", key), null);
    }

    public static SomaRuntimeException missingKey(String table, long key, String operation) {
        return create(SomaErrorCategory.LOOKUP, "missing_key", operation,
                table, context("key", key), null);
    }

    public static SomaRuntimeException duplicateKey(String table, boolean key, String operation) {
        return create(SomaErrorCategory.CONFLICT, "duplicate_key", operation,
                table, context("key", key), null);
    }

    public static SomaRuntimeException missingKey(String table, boolean key, String operation) {
        return create(SomaErrorCategory.LOOKUP, "missing_key", operation,
                table, context("key", key), null);
    }

    public static SomaRuntimeException duplicateKey(String table, byte key, String operation) {
        return create(SomaErrorCategory.CONFLICT, "duplicate_key", operation,
                table, context("key", key), null);
    }

    public static SomaRuntimeException missingKey(String table, byte key, String operation) {
        return create(SomaErrorCategory.LOOKUP, "missing_key", operation,
                table, context("key", key), null);
    }

    public static SomaRuntimeException duplicateKey(String table, short key, String operation) {
        return create(SomaErrorCategory.CONFLICT, "duplicate_key", operation,
                table, context("key", key), null);
    }

    public static SomaRuntimeException missingKey(String table, short key, String operation) {
        return create(SomaErrorCategory.LOOKUP, "missing_key", operation,
                table, context("key", key), null);
    }

    public static SomaRuntimeException duplicateKey(String table, float key, String operation) {
        return create(SomaErrorCategory.CONFLICT, "duplicate_key", operation,
                table, context("key", key), null);
    }

    public static SomaRuntimeException missingKey(String table, float key, String operation) {
        return create(SomaErrorCategory.LOOKUP, "missing_key", operation,
                table, context("key", key), null);
    }

    public static SomaRuntimeException duplicateKey(String table, double key, String operation) {
        return create(SomaErrorCategory.CONFLICT, "duplicate_key", operation,
                table, context("key", key), null);
    }

    public static SomaRuntimeException missingKey(String table, double key, String operation) {
        return create(SomaErrorCategory.LOOKUP, "missing_key", operation,
                table, context("key", key), null);
    }

    /** enum identity 使用声明成员名，绝不调用调用方可覆盖的 toString()。 */
    public static SomaRuntimeException duplicateKey(String table, Enum<?> key, String operation) {
        return create(SomaErrorCategory.CONFLICT, "duplicate_key", operation,
                table, context("key", key.name()), null);
    }

    /** enum identity 使用声明成员名，绝不调用调用方可覆盖的 toString()。 */
    public static SomaRuntimeException missingKey(String table, Enum<?> key, String operation) {
        return create(SomaErrorCategory.LOOKUP, "missing_key", operation,
                table, context("key", key.name()), null);
    }

    public static SomaRuntimeException duplicateValueKey(
            String table, String keyField, String operation) {
        return create(SomaErrorCategory.CONFLICT, "duplicate_key", operation,
                table, context("key", keyField), null);
    }

    public static SomaRuntimeException uniqueConstraintViolation(
            String table, String selector, int leftRow, int rightRow, String operation) {
        Map<String, String> context = context("selector", selector);
        context.put("leftRow", Integer.toString(leftRow));
        context.put("rightRow", Integer.toString(rightRow));
        return create(SomaErrorCategory.CONFLICT, "unique_constraint_violation", operation,
                table + "." + selector, context, null);
    }

    public static SomaRuntimeException missingValueKey(
            String table, String keyField, String operation) {
        return create(SomaErrorCategory.LOOKUP, "missing_key", operation,
                table, context("key", keyField), null);
    }

    public static SomaRuntimeException duplicateDeltaTarget(
            String table, int firstEntry, int duplicateEntry, String operation) {
        Map<String, String> context = context(
                "firstEntry", Integer.toString(firstEntry));
        context.put("duplicateEntry", Integer.toString(duplicateEntry));
        return create(SomaErrorCategory.CONFLICT, "duplicate_delta_target",
                operation, table, context, null);
    }

    public static SomaRuntimeException deltaInsertTargetPresent(
            String table, int entry, String operation) {
        return create(SomaErrorCategory.CONFLICT, "delta_insert_target_present",
                operation, table, context("entry", entry), null);
    }

    public static SomaRuntimeException deltaTargetAbsent(
            String table, int entry, String operation) {
        return create(SomaErrorCategory.LOOKUP, "delta_target_absent",
                operation, table, context("entry", entry), null);
    }

    public static SomaRuntimeException staleDelta(
            String table, long expectedEpoch, long currentEpoch, String operation) {
        Map<String, String> context = context(
                "expectedEpoch", Long.toString(expectedEpoch));
        context.put("currentEpoch", Long.toString(currentEpoch));
        return create(SomaErrorCategory.LIFECYCLE, "stale_delta",
                operation, table, context, null);
    }

    public static SomaRuntimeException invalidFloatingAccessValue(
            String table, String field, String valueClass, String operation) {
        return create(SomaErrorCategory.INVALID_INPUT, "invalid_floating_access_value", operation,
                table + "." + field, context("valueClass", valueClass), null);
    }

    public static SomaRuntimeException invalidNullValue(String table, String field, String operation) {
        return create(SomaErrorCategory.INVALID_INPUT, "invalid_null_value", operation,
                table + "." + field, empty(), null);
    }

    public static <E extends Enum<E>> E requiredEnumValue(
            String table, String field, E value, String operation) {
        if (value == null) {
            throw invalidNullValue(table, field, operation);
        }
        return value;
    }

    public static <T> T requiredValue(String table, String field, T value, String operation) {
        if (value == null) {
            throw invalidNullValue(table, field, operation);
        }
        return value;
    }

    public static SomaRuntimeException missingRequiredField(String table, String field, String operation) {
        return create(SomaErrorCategory.INVALID_INPUT, "missing_required_field", operation,
                table + "." + field, empty(), null);
    }

    public static SomaRuntimeException tableReleased(String table, String operation) {
        return create(SomaErrorCategory.LIFECYCLE, "table_released", operation, table, empty(), null);
    }

    public static SomaRuntimeException releasedView(String table, String operation) {
        return create(SomaErrorCategory.LIFECYCLE, "released_view", operation, table, empty(), null);
    }

    public static SomaRuntimeException staleView(
            String table, long capturedEpoch, long currentEpoch, String operation) {
        Map<String, String> context = context("capturedEpoch", capturedEpoch);
        context.put("currentEpoch", Long.toString(currentEpoch));
        return create(SomaErrorCategory.LIFECYCLE, "stale_view", operation, table, context, null);
    }

    public static SomaRuntimeException viewPinned(
            String table, String operation, int activeViews) {
        return create(SomaErrorCategory.CONFLICT, "view_pinned", operation, table,
                context("activeViews", activeViews), null);
    }

    public static SomaRuntimeException pipelineConsumed(String table, String operation) {
        return create(SomaErrorCategory.LIFECYCLE, "pipeline_consumed", operation, table, empty(), null);
    }

    public static SomaRuntimeException traversalConsumed(String table, String operation) {
        return create(SomaErrorCategory.LIFECYCLE, "traversal_consumed", operation, table, empty(), null);
    }

    public static SomaRuntimeException ownedDataFlowSource(
            String path, String operation) {
        return create(SomaErrorCategory.INVALID_INPUT, "owned_dataflow_source",
                operation, path, empty(), null);
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

    public static SomaRuntimeException rowLimitExceeded(
            String table,
            String operation,
            long current,
            long limit,
            long proposed) {
        Map<String, String> context = context("current", current);
        context.put("limit", Long.toString(limit));
        context.put("proposed", Long.toString(proposed));
        return create(SomaErrorCategory.RESOURCE, "row_limit_exceeded",
                operation, table, context, null);
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

    public static SomaRuntimeException allocationFailure(
            String phase, long requestedEstimate, String path, RuntimeException cause) {
        Map<String, String> context = context("phase", phase);
        context.put("requestedEstimate", Long.toString(requestedEstimate));
        context.put("causeType", cause == null
                ? "provider_rejected" : cause.getClass().getName());
        return create(SomaErrorCategory.RESOURCE, "allocation_failure",
                "materialize", path, context, cause);
    }

    public static SomaRuntimeException childWrongOwner(String path, String operation) {
        return create(SomaErrorCategory.INTERNAL, "child_wrong_owner", operation,
                path, empty(), null);
    }

    public static SomaRuntimeException childDangling(String path, String operation) {
        return create(SomaErrorCategory.INTERNAL, "child_dangling", operation,
                path, empty(), null);
    }

    public static SomaRuntimeException childReleased(String path, String operation) {
        return create(SomaErrorCategory.LIFECYCLE, "child_released", operation,
                path, empty(), null);
    }

    public static SomaRuntimeException ownedChildRelease(String path, String operation) {
        return create(SomaErrorCategory.LIFECYCLE, "owned_child_release", operation,
                path, empty(), null);
    }

    public static SomaRuntimeException childKeyMismatch(String path, String operation) {
        return create(SomaErrorCategory.INVALID_INPUT, "child_key_mismatch", operation,
                path, empty(), null);
    }

    public static SomaRuntimeException ownershipCycle(String path, String operation) {
        return create(SomaErrorCategory.INTERNAL, "ownership_cycle", operation,
                path, empty(), null);
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

    static boolean isInternalCode(String code) {
        return "internal_invariant_violation".equals(code)
                || "child_wrong_owner".equals(code)
                || "child_dangling".equals(code)
                || "ownership_cycle".equals(code);
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
