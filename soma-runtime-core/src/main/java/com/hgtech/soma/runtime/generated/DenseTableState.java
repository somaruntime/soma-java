package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.OperationOutcome;
import com.hgtech.soma.runtime.RemoveResult;
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
    private boolean childReleased;
    private String ownershipPath = "";
    private int activeViews;
    private boolean operationActive;
    private String activeOperation = "";
    private long growthCount;
    private long updateScratchCurrentBytes;
    private long updateScratchHighWaterBytes;
    private long sidecarDirtyCount;
    private long sidecarRebuildCount;
    private long sidecarRebuildRows;
    private long sidecarScratchCurrentBytes;
    private long sidecarScratchHighWaterBytes;
    private String lastOperation = "";
    private OperationOutcome lastOutcome = OperationOutcome.NONE;
    private String lastErrorCode = "";
    private long lastScanned;
    private long lastMatched;
    private long lastChanged;
    private boolean materializationActive;
    private long materializationInvocationCount;
    private long materializationFailureCount;
    private String lastMaterializationBudgetIdentity = "";
    private int lastMaterializationMaximumOwnershipDepth;
    private long lastMaterializationTableInstances;
    private long lastMaterializationRows;
    private long lastMaterializationLeafValues;
    private long lastMaterializationEstimatedAllocationBytes;

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
    public boolean hasPinnedBorrow() {
        return activeViews > 0 || operationActive || materializationActive;
    }
    public RuntimePlan runtimePlan() { return runtimePlan; }
    public long sidecarRebuildCount() { return sidecarRebuildCount; }

    public long acquireView(String operation) {
        checkActive(operation);
        if (operationActive) {
            throw RuntimeFailures.reentrantAccess(tableLogicalName, activeOperation, operation);
        }
        activeViews++;
        return structuralEpoch;
    }

    public void releaseView() {
        if (activeViews > 0) {
            activeViews--;
        }
    }

    public void checkView(long capturedEpoch, int rowIndex, String operation) {
        checkActive(operation);
        if (capturedEpoch != structuralEpoch) {
            throw RuntimeFailures.staleView(
                    tableLogicalName, capturedEpoch, structuralEpoch, operation);
        }
        checkRowIndex(rowIndex, operation);
    }

    public void checkActive(String operation) {
        if (childReleased) {
            throw RuntimeFailures.childReleased(ownershipPath, operation);
        }
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
        if (operationActive || materializationActive) {
            throw RuntimeFailures.reentrantAccess(tableLogicalName, activeOperation, operation);
        }
        operationActive = true;
        activeOperation = operation;
    }

    public void beginMaterialization(String operation) {
        checkActive(operation);
        if (operationActive || materializationActive) {
            throw RuntimeFailures.reentrantAccess(
                    tableLogicalName,
                    materializationActive ? "materialize" : activeOperation,
                    operation);
        }
        recordMaterializationInvocation(operation);
        materializationActive = true;
    }

    /** Starts materialization owned by the currently active row-pipeline operation. */
    public void beginOperationMaterialization(String operation) {
        checkActive(operation);
        requireActiveOperation(operation);
        if (materializationActive) {
            throw RuntimeFailures.reentrantAccess(tableLogicalName, "materialize", operation);
        }
        recordMaterializationInvocation(operation);
        materializationActive = true;
    }

    /** Structural publish inside an active mutating terminal still observes ColumnView pins. */
    public void preflightStructuralOperation(String operation) {
        checkActive(operation);
        requireActiveOperation(operation);
        if (materializationActive) {
            throw RuntimeFailures.reentrantAccess(tableLogicalName, "materialize", operation);
        }
        if (activeViews > 0) {
            throw RuntimeFailures.viewPinned(tableLogicalName, operation, activeViews);
        }
    }

    public void endMaterializationSuccess(MaterializationTracker tracker) {
        completeMaterialization(tracker, false);
    }

    public void endMaterializationFailure(MaterializationTracker tracker) {
        completeMaterialization(tracker, true);
    }

    private void completeMaterialization(MaterializationTracker tracker, boolean failed) {
        if (!materializationActive) {
            throw RuntimeFailures.internalInvariant(
                    "materialization_guard", tableLogicalName, "materialize");
        }
        materializationActive = false;
        if (failed) {
            if (materializationFailureCount == Long.MAX_VALUE) {
                throw RuntimeFailures.internalInvariant(
                        "materialization_failure_overflow", tableLogicalName, "materialize");
            }
            materializationFailureCount++;
        }
        if (tracker != null) {
            lastMaterializationBudgetIdentity = tracker.budgetIdentity();
            lastMaterializationMaximumOwnershipDepth = tracker.maximumDepth();
            lastMaterializationTableInstances = tracker.tableInstances();
            lastMaterializationRows = tracker.rows();
            lastMaterializationLeafValues = tracker.leafValues();
            lastMaterializationEstimatedAllocationBytes = tracker.estimatedBytes();
        }
    }

    private void recordMaterializationInvocation(String operation) {
        if (materializationInvocationCount == Long.MAX_VALUE) {
            throw RuntimeFailures.internalInvariant(
                    "materialization_invocation_overflow", tableLogicalName, operation);
        }
        materializationInvocationCount++;
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

    /** Clears the operation guard when application code or a JVM Error must propagate unchanged. */
    public void abortOperation(String operation) {
        requireActiveOperation(operation);
        operationActive = false;
        activeOperation = "";
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

    public void prepareChildChange(String operation) {
        requireStructural(operation);
    }

    public void commitChildChange(String operation) {
        checkActive(operation);
        structuralEpoch++;
        record(operation, OperationOutcome.SUCCESS, "", 1L, 1L, 1L);
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
        record("clear", OperationOutcome.SUCCESS, "", expectedPreviousSize, expectedPreviousSize,
                expectedPreviousSize);
    }

    public void commitStructuralRemove(
            int expectedPreviousSize, int newSize, String operation) {
        requireActiveOperation(operation);
        if (expectedPreviousSize != size || newSize < 0 || newSize > size) {
            throw RuntimeFailures.internalInvariant(
                    "remove_commit_identity", tableLogicalName, operation);
        }
        size = newSize;
        if (newSize != expectedPreviousSize) {
            structuralEpoch++;
        }
    }

    public int prepareRelease() {
        if (released) {
            return -1;
        }
        if (operationActive || materializationActive) {
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
        activeViews = 0;
        structuralEpoch++;
        record("release", OperationOutcome.SUCCESS, "", expectedPreviousSize, expectedPreviousSize,
                expectedPreviousSize);
    }

    public void markOwned(String path) {
        if (released || childReleased || !ownershipPath.isEmpty()) {
            throw RuntimeFailures.internalInvariant(
                    "owned_table_identity", tableLogicalName, "child.create");
        }
        ownershipPath = Objects.requireNonNull(path, "path");
    }

    public boolean isOwned() { return !ownershipPath.isEmpty(); }

    public void rejectOwnedRelease(String operation) {
        if (isOwned()) {
            throw RuntimeFailures.ownedChildRelease(ownershipPath, operation);
        }
    }

    public void commitOwnedRelease(boolean aggregateRelease) {
        if (released) return;
        int previous = size;
        size = 0;
        released = true;
        childReleased = !aggregateRelease;
        activeViews = 0;
        structuralEpoch++;
        record("ownership.release", OperationOutcome.SUCCESS, "",
                previous, previous, previous);
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

    public void sidecarsDirtied(long count) {
        if (count < 0L || Long.MAX_VALUE - sidecarDirtyCount < count) {
            throw RuntimeFailures.internalInvariant(
                    "sidecar_dirty_stats", tableLogicalName, "sidecar.dirty");
        }
        sidecarDirtyCount += count;
    }

    public void sidecarRebuilt(long rows) {
        if (rows < 0L || sidecarRebuildCount == Long.MAX_VALUE
                || Long.MAX_VALUE - sidecarRebuildRows < rows) {
            throw RuntimeFailures.internalInvariant(
                    "sidecar_rebuild_stats", tableLogicalName, "sidecar.rebuild");
        }
        sidecarRebuildCount++;
        sidecarRebuildRows += rows;
    }

    public void sidecarScratch(long currentBytes, long highWaterBytes) {
        if (currentBytes < 0L || highWaterBytes < currentBytes
                || highWaterBytes > tablePlan.maximumSidecarScratchBytes()) {
            throw RuntimeFailures.internalInvariant(
                    "sidecar_scratch_accounting", tableLogicalName, "sidecar.rebuild");
        }
        sidecarScratchCurrentBytes = currentBytes;
        if (highWaterBytes > sidecarScratchHighWaterBytes) {
            sidecarScratchHighWaterBytes = highWaterBytes;
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

    public RemoveResult removeResult(
            long scanned,
            long matched,
            long removed,
            long compacted,
            long sidecarMaintained,
            long sidecarRebuilt) {
        return RemoveResult.create(scanned, matched, removed, compacted,
                sidecarMaintained, sidecarRebuilt);
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
                activeViews,
                growthCount,
                updateScratchCurrentBytes,
                updateScratchHighWaterBytes,
                sidecarDirtyCount,
                sidecarRebuildCount,
                sidecarRebuildRows,
                sidecarScratchCurrentBytes,
                sidecarScratchHighWaterBytes,
                lastOperation,
                lastOutcome,
                lastErrorCode,
                lastScanned,
                lastMatched,
                lastChanged);
    }

    public TableStats statsSnapshot(long childInstances, long descendantRows) {
        return TableStats.withPhase4(statsSnapshot(), childInstances, descendantRows,
                materializationInvocationCount, materializationFailureCount,
                lastMaterializationBudgetIdentity,
                lastMaterializationMaximumOwnershipDepth,
                lastMaterializationTableInstances,
                lastMaterializationRows,
                lastMaterializationLeafValues,
                lastMaterializationEstimatedAllocationBytes);
    }

    public void resetStats() {
        if (operationActive || materializationActive) {
            throw RuntimeFailures.reentrantAccess(
                    tableLogicalName,
                    materializationActive ? "materialize" : activeOperation,
                    "resetStats");
        }
        lastOperation = "";
        lastOutcome = OperationOutcome.NONE;
        lastErrorCode = "";
        lastScanned = 0L;
        lastMatched = 0L;
        lastChanged = 0L;
        sidecarDirtyCount = 0L;
        sidecarRebuildCount = 0L;
        sidecarRebuildRows = 0L;
        materializationInvocationCount = 0L;
        materializationFailureCount = 0L;
        lastMaterializationBudgetIdentity = "";
        lastMaterializationMaximumOwnershipDepth = 0;
        lastMaterializationTableInstances = 0L;
        lastMaterializationRows = 0L;
        lastMaterializationLeafValues = 0L;
        lastMaterializationEstimatedAllocationBytes = 0L;
    }

    private void requireStructural(String operation) {
        checkActive(operation);
        if (operationActive || materializationActive) {
            throw RuntimeFailures.reentrantAccess(tableLogicalName, activeOperation, operation);
        }
        if (activeViews > 0) {
            throw RuntimeFailures.viewPinned(tableLogicalName, operation, activeViews);
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
