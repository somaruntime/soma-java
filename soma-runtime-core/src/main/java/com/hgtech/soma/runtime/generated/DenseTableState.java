package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.OperationOutcome;
import com.hgtech.soma.runtime.RemoveResult;
import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.SomaRuntimeException;
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
    private final ChildOwnershipRegistry ownership;

    private int size;
    private long structuralEpoch;
    private boolean released;
    private boolean childReleased;
    private String ownershipPath = "";
    private int activeViews;
    private boolean operationActive;
    private String activeOperation = "";
    private boolean callbackActive;
    private String activeCallback = "";
    private String dataVersion;
    private long growthCount;
    private long updateScratchCurrentBytes;
    private long updateScratchHighWaterBytes;
    private long operationScratchCurrentBytes;
    private long operationScratchHighWaterBytes;
    private long bulkScratchCurrentBytes;
    private long bulkScratchHighWaterBytes;
    private long keySpaceCurrentBytes;
    private long exactIndexCurrentBytes;
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
        this(tableLogicalName, runtimePlan, tablePlan, columns,
                Objects.requireNonNull(columns, "columns").ownershipInternal());
    }

    public DenseTableState(
            String tableLogicalName,
            RuntimePlan runtimePlan,
            TablePlan tablePlan,
            ColumnGroup columns,
            ChildOwnershipRegistry ownership) {
        this.tableLogicalName = Objects.requireNonNull(tableLogicalName, "tableLogicalName");
        this.runtimePlan = Objects.requireNonNull(runtimePlan, "runtimePlan");
        this.tablePlan = Objects.requireNonNull(tablePlan, "tablePlan");
        this.columns = Objects.requireNonNull(columns, "columns");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        if (!tableLogicalName.equals(tablePlan.tableLogicalName())) {
            throw internalInvariant(
                    "dense_state_table_plan_identity", tableLogicalName, "table.create");
        }
    }

    public int size() { return size; }
    public int capacity() { return columns.capacity(); }
    int storageSegmentCount() { return columns.segmentCount(); }
    public int storageSegmentEndExclusive(
            int rowIndex, int limitExclusive) {
        return columns.segmentEndExclusive(rowIndex, limitExclusive);
    }
    public long structuralEpoch() { return structuralEpoch; }
    public boolean isReleased() { return released; }
    public boolean hasPinnedBorrow() {
        return activeViews > 0 || operationActive || materializationActive || callbackActive;
    }
    public RuntimePlan runtimePlan() { return runtimePlan; }

    public String dataVersion() {
        checkActive("dataVersion");
        return dataVersion;
    }

    public void setDataVersion(String value) {
        if (value == null) throw new NullPointerException("value");
        preflightSafePoint("setDataVersion");
        dataVersion = value;
    }

    public void clearDataVersion() {
        preflightSafePoint("clearDataVersion");
        dataVersion = null;
    }

    public long acquireView(String operation) {
        checkActive(operation);
        if (operationActive) {
            throw RuntimeFailures.reentrantAccess(tableLogicalName, activeOperation, operation);
        }
        ownership.beginTableScopePreflighted(operation);
        activeViews++;
        return structuralEpoch;
    }

    public void releaseView() {
        if (activeViews > 0) {
            activeViews--;
            ownership.endTableScope("columnView");
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
        checkCallbackAccess(operation);
    }

    public void checkCallbackAccess(String operation) {
        ownership.preflightTableAccess(operation);
        if (callbackActive) {
            throw RuntimeFailures.reentrantAccess(
                    tableLogicalName,
                    activeCallback.isEmpty() ? activeOperation : activeCallback,
                    operation);
        }
    }

    private SomaRuntimeException internalInvariant(
            String invariant, String path, String operation) {
        return ownership.internalInvariant(invariant, path, operation);
    }

    public void beginCallback(String callback) {
        if (childReleased) {
            throw RuntimeFailures.childReleased(ownershipPath, callback);
        }
        if (released) {
            throw RuntimeFailures.tableReleased(tableLogicalName, callback);
        }
        if (callbackActive) {
            throw RuntimeFailures.reentrantAccess(
                    tableLogicalName, activeCallback, callback);
        }
        callbackActive = true;
        activeCallback = callback;
    }

    public void endCallback(String callback) {
        if (!callbackActive || !activeCallback.equals(callback)) {
            throw internalInvariant(
                    "callback_scope", tableLogicalName, callback);
        }
        callbackActive = false;
        activeCallback = "";
    }

    public int checkRowIndex(int rowIndex, String operation) {
        checkActive(operation);
        return checkGuardedRowIndex(rowIndex, operation);
    }

    /**
     * Range validation for generated code that already owns an aggregate
     * DataFlow guard and therefore must not re-enter application access
     * preflight.
     */
    public int checkGuardedRowIndex(int rowIndex, String operation) {
        if (rowIndex < 0 || rowIndex >= size) {
            throw RuntimeFailures.invalidRowIndex(
                    tableLogicalName, rowIndex, size, structuralEpoch, operation);
        }
        return rowIndex;
    }

    public void reserve(int expectedCapacity) {
        preflightReserve(
                expectedCapacity, keySpaceCurrentBytes, exactIndexCurrentBytes);
        commitReserve(expectedCapacity);
    }

    public void preflightReserve(
            int expectedCapacity,
            long proposedKeySpaceBytes,
            long proposedExactIndexBytes) {
        requireStructural("reserve");
        if (expectedCapacity < 0) {
            throw new IllegalArgumentException("expectedCapacity must be non-negative");
        }
        if (proposedKeySpaceBytes < 0L || proposedExactIndexBytes < 0L) {
            throw internalInvariant(
                    "reserve_storage_preflight", tableLogicalName, "reserve");
        }
        int required = Math.max(size, expectedCapacity);
        requireMaximumRows(required, "reserve");
        boolean willGrow = required > columns.capacity();
        if (willGrow) {
            requireGrowthAvailable("reserve");
            requireStructuralEpochAvailable("reserve");
        }
        long proposedExternal = replacePart(
                externalStorageBytes(), keySpaceCurrentBytes,
                proposedKeySpaceBytes, "reserve");
        proposedExternal = replacePart(
                proposedExternal, exactIndexCurrentBytes,
                proposedExactIndexBytes, "reserve");
        columns.preflightCapacity(
                required,
                tablePlan.growthNumerator(),
                tablePlan.growthDenominator(),
                proposedExternal,
                "reserve");
    }

    public void commitReserve(int expectedCapacity) {
        requireStructural("reserve");
        if (expectedCapacity < 0) {
            throw new IllegalArgumentException("expectedCapacity must be non-negative");
        }
        int required = Math.max(size, expectedCapacity);
        requireMaximumRows(required, "reserve");
        boolean willGrow = required > columns.capacity();
        if (willGrow) {
            requireGrowthAvailable("reserve");
            requireStructuralEpochAvailable("reserve");
        }
        boolean changed = columns.ensureCapacity(
                required,
                tablePlan.growthNumerator(), tablePlan.growthDenominator());
        if (changed) {
            incrementGrowth("reserve");
            incrementStructuralEpoch("reserve");
        }
        record("reserve", OperationOutcome.SUCCESS, "", 0L, 0L, 0L);
    }

    public void beginOperation(String operation) {
        checkActive(operation);
        if (operationActive || materializationActive) {
            throw RuntimeFailures.reentrantAccess(tableLogicalName, activeOperation, operation);
        }
        ownership.beginTableScopePreflighted(operation);
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

    /** Starts materialization owned by the currently active table operation. */
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
        preflightStructuralOperation(operation, true);
    }

    public void preflightStructuralOperation(String operation, boolean structuralChange) {
        checkActive(operation);
        requireActiveOperation(operation);
        if (materializationActive) {
            throw RuntimeFailures.reentrantAccess(tableLogicalName, "materialize", operation);
        }
        if (activeViews > 0) {
            throw RuntimeFailures.viewPinned(tableLogicalName, operation, activeViews);
        }
        if (structuralChange) requireStructuralEpochAvailable(operation);
    }

    public void endMaterializationSuccess(MaterializationTracker tracker) {
        completeMaterialization(tracker, false);
    }

    public void endMaterializationFailure(MaterializationTracker tracker) {
        completeMaterialization(tracker, true);
    }

    private void completeMaterialization(MaterializationTracker tracker, boolean failed) {
        if (!materializationActive) {
            throw internalInvariant(
                    "materialization_guard", tableLogicalName, "materialize");
        }
        materializationActive = false;
        if (failed) {
            if (materializationFailureCount == Long.MAX_VALUE) {
                throw internalInvariant(
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
            throw internalInvariant(
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
        ownership.endTableScope(operation);
        record(operation, OperationOutcome.SUCCESS, "", scanned, matched, changed);
    }

    public void endOperationFailure(
            String operation, long scanned, long matched, String errorCode) {
        if (RuntimeFailures.isInternalCode(errorCode)) {
            finishFaultedOperation(operation, scanned, matched, errorCode);
            return;
        }
        requireActiveOperation(operation);
        if (scanned < 0L || matched < 0L || matched > scanned) {
            throw internalInvariant(
                    "invalid_failed_operation_counts", tableLogicalName, operation);
        }
        operationActive = false;
        activeOperation = "";
        ownership.endTableScope(operation);
        record(operation, OperationOutcome.FAILED,
                Objects.requireNonNull(errorCode, "errorCode"), scanned, matched, 0L);
    }

    /** Faults the aggregate and clears the local guard before an unexpected failure propagates. */
    public void abortOperation(String operation) {
        ownership.markUnexpectedFailure(operation);
        if (!operationActive || !activeOperation.equals(operation)) {
            recordFault(operation, 0L, 0L, "unexpected_operation_failure");
            return;
        }
        operationActive = false;
        activeOperation = "";
        ownership.finishFaultedTableScope(operation);
        recordFault(operation, 0L, 0L, "unexpected_operation_failure");
    }

    private void finishFaultedOperation(
            String operation, long scanned, long matched, String errorCode) {
        boolean ownsTableScope =
                operationActive && activeOperation.equals(operation);
        ownership.markFaulted(operation, errorCode);
        operationActive = false;
        activeOperation = "";
        callbackActive = false;
        activeCallback = "";
        materializationActive = false;
        if (ownsTableScope) ownership.finishFaultedTableScope(operation);
        recordFault(operation, scanned, matched, errorCode);
    }

    private void recordFault(
            String operation, long scanned, long matched, String errorCode) {
        long safeScanned = Math.max(0L, scanned);
        long safeMatched = Math.max(0L, Math.min(safeScanned, matched));
        record(operation, OperationOutcome.FAILED,
                Objects.requireNonNull(errorCode, "errorCode"),
                safeScanned, safeMatched, 0L);
    }

    public int prepareAppend(int count) {
        requireStructural("addBatch");
        if (count < 0) {
            throw internalInvariant(
                    "negative_append_count", tableLogicalName, "addBatch");
        }
        if (count > 0) requireStructuralEpochAvailable("addBatch");
        int required = checkedSize(size, count, "addBatch");
        preflightAppendStorage(
                count, keySpaceCurrentBytes, exactIndexCurrentBytes, "addBatch");
        if (required > columns.capacity()) requireGrowthAvailable("addBatch");
        if (columns.ensureCapacity(
                required, tablePlan.growthNumerator(), tablePlan.growthDenominator())) {
            incrementGrowth("addBatch");
        }
        return size;
    }

    public void commitAppend(int expectedStartRow, int count) {
        if (expectedStartRow != size || count < 0) {
            throw internalInvariant(
                    "append_commit_identity", tableLogicalName, "addBatch");
        }
        int committedSize = checkedSize(size, count, "addBatch");
        if (count > 0) {
            requireStructuralEpochAvailable("addBatch");
            incrementStructuralEpoch("addBatch");
        }
        size = committedSize;
        record("addBatch", OperationOutcome.SUCCESS, "", count, count, count);
    }

    public int prepareReplace(int newSize) {
        requireStructural("replaceAll");
        if (newSize < 0) {
            throw internalInvariant(
                    "negative_replace_size", tableLogicalName, "replaceAll");
        }
        requireMaximumRows(newSize, "replaceAll");
        if (size != 0 || newSize != 0) requireStructuralEpochAvailable("replaceAll");
        if (newSize > columns.capacity()) requireGrowthAvailable("replaceAll");
        if (columns.ensureCapacity(
                newSize, tablePlan.growthNumerator(), tablePlan.growthDenominator())) {
            incrementGrowth("replaceAll");
        }
        return size;
    }

    public void commitReplace(int expectedPreviousSize, int newSize) {
        if (expectedPreviousSize != size || newSize < 0 || newSize > capacity()) {
            throw internalInvariant(
                    "replace_commit_identity", tableLogicalName, "replaceAll");
        }
        requireMaximumRows(newSize, "replaceAll");
        if (expectedPreviousSize != 0 || newSize != 0) {
            requireStructuralEpochAvailable("replaceAll");
            incrementStructuralEpoch("replaceAll");
        }
        size = newSize;
        record("replaceAll", OperationOutcome.SUCCESS, "", newSize, newSize, newSize);
    }

    public int prepareClear() {
        requireStructural("clear");
        if (size > 0) requireStructuralEpochAvailable("clear");
        return size;
    }

    public void prepareChildChange(String operation) {
        requireStructural(operation);
        requireStructuralEpochAvailable(operation);
    }

    public void commitChildChange(String operation) {
        checkActive(operation);
        requireStructuralEpochAvailable(operation);
        incrementStructuralEpoch(operation);
        record(operation, OperationOutcome.SUCCESS, "", 1L, 1L, 1L);
    }

    public void commitClear(int expectedPreviousSize) {
        if (expectedPreviousSize != size) {
            throw internalInvariant(
                    "clear_commit_identity", tableLogicalName, "clear");
        }
        if (expectedPreviousSize > 0) {
            requireStructuralEpochAvailable("clear");
            incrementStructuralEpoch("clear");
        }
        size = 0;
        record("clear", OperationOutcome.SUCCESS, "", expectedPreviousSize, expectedPreviousSize,
                expectedPreviousSize);
    }

    public void commitStructuralRemove(
            int expectedPreviousSize, int newSize, String operation) {
        requireActiveOperation(operation);
        if (expectedPreviousSize != size || newSize < 0 || newSize > size) {
            throw internalInvariant(
                    "remove_commit_identity", tableLogicalName, operation);
        }
        if (newSize != expectedPreviousSize) {
            requireStructuralEpochAvailable(operation);
            incrementStructuralEpoch(operation);
        }
        size = newSize;
    }

    /**
     * Publishes one non-structural changed-row Delta as one structural epoch.
     */
    public void commitDeltaUpdate(String operation) {
        requireActiveOperation(operation);
        requireStructuralEpochAvailable(operation);
        incrementStructuralEpoch(operation);
    }

    public int prepareRelease() {
        if (released) {
            return -1;
        }
        preflightRootRelease("release");
        return size;
    }

    /** Complete local preflight used before an implicit or explicit Group release. */
    public void preflightRootRelease(String operation) {
        if (released) return;
        checkCallbackAccess(operation);
        if (operationActive || materializationActive || callbackActive) {
            String active = callbackActive ? activeCallback
                    : materializationActive ? "materialize" : activeOperation;
            throw RuntimeFailures.reentrantAccess(
                    tableLogicalName, active, operation);
        }
        if (activeViews > 0) {
            throw RuntimeFailures.viewPinned(
                    tableLogicalName, operation, activeViews);
        }
        requireNoTransientStorage(operation);
        requireStructuralEpochAvailable(operation);
    }

    /** Safe point for runtime marker and parent Group lifecycle coordination. */
    public void preflightSafePoint(String operation) {
        checkActive(operation);
        if (operationActive || materializationActive || callbackActive) {
            String active = callbackActive ? activeCallback
                    : materializationActive ? "materialize" : activeOperation;
            throw RuntimeFailures.reentrantAccess(
                    tableLogicalName, active, operation);
        }
        if (activeViews > 0) {
            throw RuntimeFailures.viewPinned(
                    tableLogicalName, operation, activeViews);
        }
        requireNoTransientStorage(operation);
    }

    public void commitRelease(int expectedPreviousSize) {
        if (expectedPreviousSize < 0) {
            if (!released) {
                throw internalInvariant(
                        "release_idempotence", tableLogicalName, "release");
            }
            return;
        }
        if (released || expectedPreviousSize != size) {
            throw internalInvariant(
                    "release_commit_identity", tableLogicalName, "release");
        }
        requireNoTransientStorage("release");
        requireStructuralEpochAvailable("release");
        size = 0;
        columns.replaceExternalRetainedBytes(externalStorageBytes(), 0L, "release");
        updateScratchCurrentBytes = 0L;
        operationScratchCurrentBytes = 0L;
        keySpaceCurrentBytes = 0L;
        exactIndexCurrentBytes = 0L;
        columns.releaseStorage();
        incrementStructuralEpoch("release");
        released = true;
        invalidateViews("release");
        record("release", OperationOutcome.SUCCESS, "", expectedPreviousSize, expectedPreviousSize,
                expectedPreviousSize);
    }

    public void markOwned(String path) {
        if (released || childReleased || !ownershipPath.isEmpty()) {
            throw internalInvariant(
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

    /**
     * Validates every failure-capable lifecycle condition before an owner publishes a
     * child replacement or starts a recursive release commit.
     */
    public void preflightOwnedRelease(String operation) {
        if (released) return;
        requireNoTransientStorage(operation);
        requireStructuralEpochAvailable(operation);
    }

    public void commitOwnedRelease(boolean aggregateRelease) {
        if (released) return;
        requireNoTransientStorage("ownership.release");
        requireStructuralEpochAvailable("ownership.release");
        int previous = size;
        size = 0;
        columns.replaceExternalRetainedBytes(
                externalStorageBytes(), 0L, "ownership.release");
        updateScratchCurrentBytes = 0L;
        operationScratchCurrentBytes = 0L;
        keySpaceCurrentBytes = 0L;
        exactIndexCurrentBytes = 0L;
        columns.releaseStorage();
        incrementStructuralEpoch("ownership.release");
        released = true;
        childReleased = !aggregateRelease;
        invalidateViews("ownership.release");
        record("ownership.release", OperationOutcome.SUCCESS, "",
                previous, previous, previous);
    }

    public void updateScratch(long currentBytes, long highWaterBytes) {
        if (currentBytes < 0L || highWaterBytes < currentBytes
                || highWaterBytes > tablePlan.maximumUpdateScratchBytes()) {
            throw internalInvariant(
                    "update_scratch_accounting", tableLogicalName, "update");
        }
        replaceExternalStorage(updateScratchCurrentBytes, currentBytes, "update.scratch");
        updateScratchCurrentBytes = currentBytes;
        if (highWaterBytes > updateScratchHighWaterBytes) {
            updateScratchHighWaterBytes = highWaterBytes;
        }
    }

    public void operationScratch(long currentBytes) {
        if (currentBytes < 0L
                || currentBytes > tablePlan.maximumOperationScratchBytes()) {
            throw internalInvariant(
                    "operation_scratch_accounting", tableLogicalName, "operation.scratch");
        }
        replaceExternalStorage(operationScratchCurrentBytes, currentBytes, "operation.scratch");
        operationScratchCurrentBytes = currentBytes;
        if (currentBytes > operationScratchHighWaterBytes) {
            operationScratchHighWaterBytes = currentBytes;
        }
    }

    public void preflightUpdateScratch(long proposed, String operation) {
        preflightExternalStorage(updateScratchCurrentBytes, proposed, operation);
    }

    public void preflightOperationScratch(long proposed, String operation) {
        preflightExternalStorage(operationScratchCurrentBytes, proposed, operation);
    }

    public void preflightKeySpaceStorage(long proposed, String operation) {
        if (proposed > tablePlan.maximumBulkScratchBytes()) {
            throw RuntimeFailures.memoryLimitExceeded(
                    tableLogicalName, operation,
                    tablePlan.maximumBulkScratchBytes(), proposed);
        }
        preflightExternalStorage(keySpaceCurrentBytes, proposed, operation);
    }

    public void preflightExactIndexStorage(long proposed, String operation) {
        if (proposed < 0L) {
            throw internalInvariant(
                    "exact_index_storage_preflight", tableLogicalName, operation);
        }
        preflightExternalStorage(exactIndexCurrentBytes, proposed, operation);
    }

    public void preflightAppendStorage(
            int count,
            long proposedKeySpaceBytes,
            long proposedExactIndexBytes,
            String operation) {
        if (count < 0 || proposedKeySpaceBytes < 0L || proposedExactIndexBytes < 0L) {
            throw internalInvariant(
                    "append_storage_preflight", tableLogicalName, operation);
        }
        int required = checkedSize(size, count, operation);
        long proposedExternal = replacePart(
                externalStorageBytes(), keySpaceCurrentBytes,
                proposedKeySpaceBytes, operation);
        proposedExternal = replacePart(
                proposedExternal, exactIndexCurrentBytes,
                proposedExactIndexBytes, operation);
        columns.preflightCapacity(
                required,
                tablePlan.growthNumerator(),
                tablePlan.growthDenominator(),
                proposedExternal,
                operation);
    }

    public void preflightReplaceStorage(
            int newSize,
            long proposedKeySpaceBytes,
            long proposedExactIndexBytes,
            String operation) {
        if (newSize < 0 || proposedKeySpaceBytes < 0L || proposedExactIndexBytes < 0L) {
            throw internalInvariant(
                    "replace_storage_preflight", tableLogicalName, operation);
        }
        requireMaximumRows(newSize, operation);
        long proposedExternal = replacePart(
                externalStorageBytes(), keySpaceCurrentBytes,
                proposedKeySpaceBytes, operation);
        proposedExternal = replacePart(
                proposedExternal, exactIndexCurrentBytes,
                proposedExactIndexBytes, operation);
        columns.preflightCapacity(
                newSize,
                tablePlan.growthNumerator(),
                tablePlan.growthDenominator(),
                proposedExternal,
                operation);
    }

    /** Reserves one operation's detached batch/key/cascade staging peak. */
    public void reserveBulkScratch(long bytes, String operation) {
        if (bytes < 0L || Long.MAX_VALUE - bulkScratchCurrentBytes < bytes) {
            throw RuntimeFailures.memoryLimitExceeded(
                    tableLogicalName, operation,
                    tablePlan.maximumBulkScratchBytes(), Long.MAX_VALUE);
        }
        long proposed = bulkScratchCurrentBytes + bytes;
        if (proposed > tablePlan.maximumBulkScratchBytes()) {
            throw RuntimeFailures.memoryLimitExceeded(
                    tableLogicalName, operation,
                    tablePlan.maximumBulkScratchBytes(), proposed);
        }
        columns.reserveTransientBytes(bytes, operation);
        bulkScratchCurrentBytes = proposed;
        if (proposed > bulkScratchHighWaterBytes) {
            bulkScratchHighWaterBytes = proposed;
        }
    }

    public void releaseBulkScratch(long bytes, String operation) {
        if (bytes < 0L || bytes > bulkScratchCurrentBytes) {
            throw internalInvariant(
                    "bulk_scratch_accounting", tableLogicalName, operation);
        }
        columns.releaseTransientBytes(bytes, operation);
        bulkScratchCurrentBytes -= bytes;
    }

    public void commitKeySpaceStorage(long previous, long proposed, String operation) {
        if (previous != keySpaceCurrentBytes) {
            throw internalInvariant(
                    "key_space_storage_accounting", tableLogicalName, operation);
        }
        replaceExternalStorage(previous, proposed, operation);
        keySpaceCurrentBytes = proposed;
    }

    public void commitExactIndexStorage(long previous, long proposed, String operation) {
        if (previous != exactIndexCurrentBytes) {
            throw internalInvariant(
                    "exact_index_storage_accounting", tableLogicalName, operation);
        }
        replaceExternalStorage(previous, proposed, operation);
        exactIndexCurrentBytes = proposed;
    }

    /** Rolls back a generated constructor after this state acquired table quota. */
    public void abortConstruction() {
        if (released || size != 0 || bulkScratchCurrentBytes != 0L) {
            throw internalInvariant(
                    "table_construction_rollback", tableLogicalName, "table.create");
        }
        columns.replaceExternalRetainedBytes(
                externalStorageBytes(), 0L, "table.create.rollback");
        keySpaceCurrentBytes = 0L;
        exactIndexCurrentBytes = 0L;
        updateScratchCurrentBytes = 0L;
        operationScratchCurrentBytes = 0L;
        columns.releaseStorage();
        released = true;
    }

    private void preflightExternalStorage(long previousPart, long proposedPart, String operation) {
        long previous = externalStorageBytes();
        long proposed = replacePart(previous, previousPart, proposedPart, operation);
        columns.preflightExternalRetainedBytes(proposed, operation);
    }

    private void replaceExternalStorage(long previousPart, long proposedPart, String operation) {
        long previous = externalStorageBytes();
        long proposed = replacePart(previous, previousPart, proposedPart, operation);
        columns.replaceExternalRetainedBytes(previous, proposed, operation);
    }

    private long externalStorageBytes() {
        long total = updateScratchCurrentBytes;
        total = checkedStorageAdd(total, operationScratchCurrentBytes);
        total = checkedStorageAdd(total, keySpaceCurrentBytes);
        return checkedStorageAdd(total, exactIndexCurrentBytes);
    }

    private long replacePart(
            long total, long previousPart, long proposedPart, String operation) {
        if (previousPart < 0L || proposedPart < 0L || previousPart > total) {
            throw internalInvariant(
                    "table_storage_accounting", tableLogicalName, operation);
        }
        return checkedStorageAdd(total - previousPart, proposedPart);
    }

    private static long checkedStorageAdd(long left, long right) {
        return left < 0L || right < 0L || Long.MAX_VALUE - left < right
                ? Long.MAX_VALUE : left + right;
    }

    private void requireNoTransientStorage(String operation) {
        if (bulkScratchCurrentBytes != 0L) {
            throw internalInvariant(
                    "bulk_scratch_release", tableLogicalName, operation);
        }
    }

    private void invalidateViews(String operation) {
        while (activeViews > 0) {
            activeViews--;
            ownership.endTableScope(operation);
        }
    }

    public UpdateResult updateResult(long scanned, long matched, long changed) {
        return UpdateResult.create(scanned, matched, changed);
    }

    public RemoveResult removeResult(
            long scanned,
            long matched,
            long removed,
            long compacted) {
        return RemoveResult.create(scanned, matched, removed, compacted);
    }

    public TableStats statsSnapshot() {
        return TableStats.withOperationScratch(TableStats.create(
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
                lastOperation,
                lastOutcome,
                lastErrorCode,
                lastScanned,
                lastMatched,
                lastChanged), operationScratchCurrentBytes,
                operationScratchHighWaterBytes);
    }

    public TableStats statsSnapshot(long childInstances, long descendantRows) {
        return TableStats.withOwnershipAndMaterialization(
                statsSnapshot(), childInstances, descendantRows,
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
            throw internalInvariant(
                    "operation_guard_identity", tableLogicalName, operation);
        }
    }

    private int checkedSize(int base, int increment, String operation) {
        long proposed = (long) base + (long) increment;
        requireMaximumRows(proposed, operation);
        return (int) proposed;
    }

    private void requireMaximumRows(long proposed, String operation) {
        if (proposed < 0L || proposed > tablePlan.maximumRows()) {
            throw RuntimeFailures.rowLimitExceeded(
                    tableLogicalName,
                    operation,
                    size,
                    tablePlan.maximumRows(),
                    proposed < 0L ? Long.MAX_VALUE : proposed);
        }
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
            throw internalInvariant(
                    "invalid_operation_counts", tableLogicalName, activeOperation);
        }
    }

    private void incrementStructuralEpoch(String operation) {
        if (structuralEpoch == Long.MAX_VALUE) {
            throw internalInvariant(
                    "structural_epoch_overflow", tableLogicalName, operation);
        }
        structuralEpoch++;
    }

    private void requireStructuralEpochAvailable(String operation) {
        if (structuralEpoch == Long.MAX_VALUE) {
            throw internalInvariant(
                    "structural_epoch_overflow", tableLogicalName, operation);
        }
    }

    private void requireGrowthAvailable(String operation) {
        if (growthCount == Long.MAX_VALUE) {
            throw internalInvariant(
                    "growth_count_overflow", tableLogicalName, operation);
        }
    }

    private void incrementGrowth(String operation) {
        if (growthCount == Long.MAX_VALUE) {
            throw internalInvariant(
                    "growth_count_overflow", tableLogicalName, operation);
        }
        growthCount++;
    }
}
