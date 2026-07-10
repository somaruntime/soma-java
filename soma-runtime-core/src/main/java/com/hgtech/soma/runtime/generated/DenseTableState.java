package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.OperationOutcome;
import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.TablePlan;
import com.hgtech.soma.runtime.TableStats;
import com.hgtech.soma.runtime.UpdateResult;

import java.util.Objects;

/** Generated dense facade 共享的 packed membership/lifecycle/stats state。 */
public final class DenseTableState {
    private final String tableLogicalName;
    private final RuntimePlan runtimePlan;
    private final TablePlan tablePlan;
    private final ColumnGroup columns;

    private int size;
    private long structuralEpoch;
    private boolean released;
    private boolean operationActive;
    private String activeOperation = "";
    private long growthCount;
    private long updateScratchCurrentBytes;
    private long updateScratchHighWaterBytes;
    private String lastOperation = "";
    private OperationOutcome lastOutcome = OperationOutcome.NONE;
    private String lastErrorCode = "";
    private long lastScanned;
    private long lastMatched;
    private long lastChanged;

    public DenseTableState(
            String tableLogicalName,
            RuntimePlan runtimePlan,
            TablePlan tablePlan,
            ColumnGroup columns) {
        this.tableLogicalName = Objects.requireNonNull(tableLogicalName, "tableLogicalName");
        this.runtimePlan = Objects.requireNonNull(runtimePlan, "runtimePlan");
        this.tablePlan = Objects.requireNonNull(tablePlan, "tablePlan");
        this.columns = Objects.requireNonNull(columns, "columns");
        if (!tableLogicalName.equals(tablePlan.tableLogicalName())) {
            throw RuntimeFailures.internalInvariant(
                    "dense_state_table_plan_identity", tableLogicalName, "table.create");
        }
    }

    public int size() { return size; }
    public int capacity() { return columns.capacity(); }
    public long structuralEpoch() { return structuralEpoch; }
    public boolean isReleased() { return released; }
    public RuntimePlan runtimePlan() { return runtimePlan; }

    public void checkActive(String operation) {
        if (released) {
            throw RuntimeFailures.tableReleased(tableLogicalName, operation);
        }
    }

    public int checkRowIndex(int rowIndex, String operation) {
        checkActive(operation);
        if (rowIndex < 0 || rowIndex >= size) {
            throw RuntimeFailures.invalidRowIndex(
                    tableLogicalName, rowIndex, size, structuralEpoch, operation);
        }
        return rowIndex;
    }

    public void beginOperation(String operation) {
        checkActive(operation);
        if (operationActive) {
            throw RuntimeFailures.reentrantAccess(tableLogicalName, activeOperation, operation);
        }
        operationActive = true;
        activeOperation = operation;
    }

    public void endOperationSuccess(
            String operation, long scanned, long matched, long changed) {
        requireActiveOperation(operation);
        validateCounts(scanned, matched, changed);
        operationActive = false;
        activeOperation = "";
        record(operation, OperationOutcome.SUCCESS, "", scanned, matched, changed);
    }

    public void endOperationFailure(
            String operation, long scanned, long matched, String errorCode) {
        requireActiveOperation(operation);
        if (scanned < 0L || matched < 0L || matched > scanned) {
            throw RuntimeFailures.internalInvariant(
                    "invalid_failed_operation_counts", tableLogicalName, operation);
        }
        operationActive = false;
        activeOperation = "";
        record(operation, OperationOutcome.FAILED,
                Objects.requireNonNull(errorCode, "errorCode"), scanned, matched, 0L);
    }

    public int prepareAppend(int count) {
        requireStructural("addBatch");
        if (count < 0) {
            throw RuntimeFailures.internalInvariant(
                    "negative_append_count", tableLogicalName, "addBatch");
        }
        int required = checkedSize(size, count, "addBatch");
        if (columns.ensureCapacity(
                required, tablePlan.growthNumerator(), tablePlan.growthDenominator())) {
            growthCount++;
        }
        return size;
    }

    public void commitAppend(int expectedStartRow, int count) {
        if (expectedStartRow != size || count < 0) {
            throw RuntimeFailures.internalInvariant(
                    "append_commit_identity", tableLogicalName, "addBatch");
        }
        size = checkedSize(size, count, "addBatch");
        if (count > 0) {
            structuralEpoch++;
        }
        record("addBatch", OperationOutcome.SUCCESS, "", count, count, count);
    }

    public int prepareReplace(int newSize) {
        requireStructural("replaceAll");
        if (newSize < 0) {
            throw RuntimeFailures.internalInvariant(
                    "negative_replace_size", tableLogicalName, "replaceAll");
        }
        if (columns.ensureCapacity(
                newSize, tablePlan.growthNumerator(), tablePlan.growthDenominator())) {
            growthCount++;
        }
        return size;
    }

    public void commitReplace(int expectedPreviousSize, int newSize) {
        if (expectedPreviousSize != size || newSize < 0 || newSize > capacity()) {
            throw RuntimeFailures.internalInvariant(
                    "replace_commit_identity", tableLogicalName, "replaceAll");
        }
        size = newSize;
        if (expectedPreviousSize != 0 || newSize != 0) {
            structuralEpoch++;
        }
        record("replaceAll", OperationOutcome.SUCCESS, "", newSize, newSize, newSize);
    }

    public int prepareClear() {
        requireStructural("clear");
        return size;
    }

    public void commitClear(int expectedPreviousSize) {
        if (expectedPreviousSize != size) {
            throw RuntimeFailures.internalInvariant(
                    "clear_commit_identity", tableLogicalName, "clear");
        }
        size = 0;
        if (expectedPreviousSize > 0) {
            structuralEpoch++;
        }
        record("clear", OperationOutcome.SUCCESS, "", expectedPreviousSize, 0L,
                expectedPreviousSize);
    }

    public int prepareRelease() {
        if (released) {
            return -1;
        }
        if (operationActive) {
            throw RuntimeFailures.reentrantAccess(tableLogicalName, activeOperation, "release");
        }
        return size;
    }

    public void commitRelease(int expectedPreviousSize) {
        if (expectedPreviousSize < 0) {
            if (!released) {
                throw RuntimeFailures.internalInvariant(
                        "release_idempotence", tableLogicalName, "release");
            }
            return;
        }
        if (released || expectedPreviousSize != size) {
            throw RuntimeFailures.internalInvariant(
                    "release_commit_identity", tableLogicalName, "release");
        }
        size = 0;
        released = true;
        structuralEpoch++;
        record("release", OperationOutcome.SUCCESS, "", expectedPreviousSize, 0L,
                expectedPreviousSize);
    }

    public void updateScratch(long currentBytes, long highWaterBytes) {
        if (currentBytes < 0L || highWaterBytes < currentBytes
                || highWaterBytes > tablePlan.maximumUpdateScratchBytes()) {
            throw RuntimeFailures.internalInvariant(
                    "update_scratch_accounting", tableLogicalName, "update");
        }
        updateScratchCurrentBytes = currentBytes;
        if (highWaterBytes > updateScratchHighWaterBytes) {
            updateScratchHighWaterBytes = highWaterBytes;
        }
    }

    public UpdateResult updateResult(
            long scanned,
            long matched,
            long changed,
            long sidecarMaintained,
            long sidecarRebuilt) {
        return UpdateResult.create(
                scanned, matched, changed, sidecarMaintained, sidecarRebuilt);
    }

    public TableStats statsSnapshot() {
        return TableStats.create(
                runtimePlan.schemaHash(),
                runtimePlan.runtimeCompatibility(),
                runtimePlan.runtimePlanHash(),
                runtimePlan.statsMode(),
                size,
                capacity(),
                structuralEpoch,
                released,
                0,
                growthCount,
                updateScratchCurrentBytes,
                updateScratchHighWaterBytes,
                lastOperation,
                lastOutcome,
                lastErrorCode,
                lastScanned,
                lastMatched,
                lastChanged);
    }

    public void resetStats() {
        lastOperation = "";
        lastOutcome = OperationOutcome.NONE;
        lastErrorCode = "";
        lastScanned = 0L;
        lastMatched = 0L;
        lastChanged = 0L;
    }

    private void requireStructural(String operation) {
        checkActive(operation);
        if (operationActive) {
            throw RuntimeFailures.reentrantAccess(tableLogicalName, activeOperation, operation);
        }
    }

    private void requireActiveOperation(String operation) {
        if (!operationActive || !activeOperation.equals(operation)) {
            throw RuntimeFailures.internalInvariant(
                    "operation_guard_identity", tableLogicalName, operation);
        }
    }

    private int checkedSize(int base, int increment, String operation) {
        long proposed = (long) base + (long) increment;
        if (proposed > Integer.MAX_VALUE) {
            throw RuntimeFailures.memoryLimitExceeded(
                    tableLogicalName, operation, Integer.MAX_VALUE, proposed);
        }
        return (int) proposed;
    }

    private void record(
            String operation,
            OperationOutcome outcome,
            String errorCode,
            long scanned,
            long matched,
            long changed) {
        lastOperation = operation;
        lastOutcome = outcome;
        lastErrorCode = errorCode;
        lastScanned = scanned;
        lastMatched = matched;
        lastChanged = changed;
    }

    private void validateCounts(long scanned, long matched, long changed) {
        if (scanned < 0L || matched < 0L || changed < 0L
                || changed > matched || matched > scanned) {
            throw RuntimeFailures.internalInvariant(
                    "invalid_operation_counts", tableLogicalName, activeOperation);
        }
    }
}
