package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.SomaErrorCategory;
import com.hgtech.soma.runtime.SomaRuntimeException;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Aggregate-local opaque child locator registry；canonical storage是平行数组。 */
public final class ChildOwnershipRegistry {
    private static final AtomicLong NEXT_AGGREGATE_INSTANCE_ID = new AtomicLong(1L);
    private static final byte STAGED = 0;
    private static final byte LIVE = 1;
    private static final byte RELEASED = 2;
    private static final int IDENTITY_TOMBSTONE = -1;
    private static final int MAX_CASCADE_DEPTH = 256;

    private final StorageBudget storageBudget;
    private final long aggregateInstanceId;
    private long retainedBytes;
    private boolean storageReleased;
    private boolean faulted;
    private String firstFaultOperation = "";
    private String firstFaultCode = "";

    private long[] ownerTokens;
    private String[] fields;
    private String[] paths;
    private Object[] children;
    private OwnedChildTable[] lifecycles;
    private byte[] states;
    private int[] generations;
    private int[] freeNext;
    private Object[] identityChildren;
    private int[] identitySlots;
    private long[] cascadeHandles;
    private int[] cascadeStarts;
    private int[] cascadeCounts;
    private String[] cascadeOperations;
    private int cascadeUsed;
    private int cascadeDepth;
    private int identitySize;
    private int identityUsed;
    private int nextSlot = 1; // slot 0 / handle 0 are never live
    private int freeHead = -1;
    private long nextOwnerToken = 1L;
    private boolean materializationActive;
    private String materializationOperation = "";
    private boolean dataFlowActive;
    private String dataFlowOperation = "";
    private int activeTableScopes;
    private String activeTableScope = "";

    public ChildOwnershipRegistry() {
        this(1024L * 1024L * 1024L, 65536L);
    }

    public ChildOwnershipRegistry(long maximumBytes, long maximumTableInstances) {
        aggregateInstanceId = nextAggregateInstanceId();
        storageBudget = new StorageBudget(maximumBytes, maximumTableInstances, this);
        long initialBytes = estimatedRetainedBytes(16, 32, 0);
        storageBudget.reserveBytes(initialBytes, "ownership", "ownership.create");
        try {
            ownerTokens = new long[16];
            fields = new String[16];
            paths = new String[16];
            children = new Object[16];
            lifecycles = new OwnedChildTable[16];
            states = new byte[16];
            generations = new int[16];
            freeNext = new int[16];
            identityChildren = new Object[32];
            identitySlots = new int[32];
            cascadeHandles = new long[0];
            cascadeStarts = new int[MAX_CASCADE_DEPTH];
            cascadeCounts = new int[MAX_CASCADE_DEPTH];
            cascadeOperations = new String[MAX_CASCADE_DEPTH];
            retainedBytes = initialBytes;
        } catch (RuntimeException failure) {
            storageBudget.releaseBytes(initialBytes, "ownership", "ownership.create");
            throw failure;
        } catch (Error failure) {
            storageBudget.releaseBytes(initialBytes, "ownership", "ownership.create");
            throw failure;
        }
    }

    StorageBudget storageBudgetInternal() { return storageBudget; }

    public long aggregateInstanceId() {
        return aggregateInstanceId;
    }

    public Object physicalIdentity() {
        return this;
    }

    SomaRuntimeException internalInvariant(
            String invariant, String path, String operation) {
        markFaulted(operation, "internal_invariant_violation");
        return RuntimeFailures.internalInvariant(invariant, path, operation);
    }

    private SomaRuntimeException internalFailure(
            SomaRuntimeException failure, String operation) {
        markFaulted(failure, operation);
        return failure;
    }

    void markFaulted(String operation, String code) {
        if (faulted) return;
        faulted = true;
        firstFaultOperation = Objects.requireNonNull(operation, "operation");
        firstFaultCode = Objects.requireNonNull(code, "code");
    }

    void markFaulted(SomaRuntimeException failure, String operation) {
        Objects.requireNonNull(failure, "failure");
        if (failure.category() == SomaErrorCategory.INTERNAL) {
            markFaulted(operation, failure.code());
        }
    }

    void markUnexpectedFailure(String operation) {
        markFaulted(operation, "unexpected_operation_failure");
    }

    void finishFaultedTableScope(String operation) {
        Objects.requireNonNull(operation, "operation");
        if (activeTableScopes <= 0) return;
        activeTableScopes--;
        if (activeTableScopes == 0) activeTableScope = "";
    }

    public void releaseStorage() {
        if (storageReleased) return;
        if (cascadeDepth != 0 || dataFlowActive || activeTableScopes != 0) {
            throw internalInvariant(
                    "cascade_release_scope", "ownership", "release");
        }
        if (identitySize != 0 || storageBudget.currentTableInstances() != 0L
                || storageBudget.transientBytes() != 0L
                || storageBudget.currentBytes() != retainedBytes) {
            throw internalInvariant(
                    "aggregate_release_drain", "ownership", "release");
        }
        ownerTokens = new long[0];
        fields = new String[0];
        paths = new String[0];
        children = new Object[0];
        lifecycles = new OwnedChildTable[0];
        states = new byte[0];
        generations = new int[0];
        freeNext = new int[0];
        identityChildren = new Object[0];
        identitySlots = new int[0];
        cascadeHandles = new long[0];
        cascadeStarts = new int[0];
        cascadeCounts = new int[0];
        cascadeOperations = new String[0];
        cascadeUsed = 0;
        cascadeDepth = 0;
        identitySize = 0;
        identityUsed = 0;
        nextSlot = 0;
        freeHead = -1;
        storageBudget.releaseBytes(retainedBytes, "ownership", "release");
        retainedBytes = 0L;
        storageReleased = true;
    }

    /** Starts one aggregate-wide two-pass materialization guard. */
    public void beginMaterialization(String operation) {
        String requested = Objects.requireNonNull(operation, "operation");
        preflightTableAccess(requested);
        if (materializationActive) {
            throw RuntimeFailures.reentrantAccess(
                    "ownership", materializationOperation, requested);
        }
        materializationOperation = requested;
        materializationActive = true;
    }

    /** Ends the current aggregate-wide materialization guard. */
    public void endMaterialization() {
        if (!materializationActive) {
            throw internalInvariant(
                    "ownership_materialization_guard", "ownership", "materialize");
        }
        materializationActive = false;
        materializationOperation = "";
    }

    /**
     * Starts explicit object materialization nested inside the aggregate's
     * already-active DataFlow read guard.
     */
    public void beginDataFlowMaterialization(String operation) {
        String requested = Objects.requireNonNull(operation, "operation");
        if (!dataFlowActive) {
            throw internalInvariant(
                    "dataflow_materialization_guard",
                    "ownership",
                    requested);
        }
        if (materializationActive) {
            throw RuntimeFailures.reentrantAccess(
                    "ownership", materializationOperation, requested);
        }
        materializationOperation = requested;
        materializationActive = true;
    }

    public void endDataFlowMaterialization(String operation) {
        String requested = Objects.requireNonNull(operation, "operation");
        if (!dataFlowActive || !materializationActive
                || !materializationOperation.equals(requested)) {
            throw internalInvariant(
                    "dataflow_materialization_guard",
                    "ownership",
                    requested);
        }
        materializationActive = false;
        materializationOperation = "";
    }

    /** Rejects any visible aggregate mutation while two-pass materialization is active. */
    public void preflightMutation(String operation) {
        String requested = Objects.requireNonNull(operation, "operation");
        preflightTableAccess(requested);
        if (materializationActive) {
            throw RuntimeFailures.reentrantAccess(
                    "ownership", materializationOperation, requested);
        }
    }

    /** Rejects application Table access while one aggregate DataFlow guard is active. */
    public void preflightTableAccess(String operation) {
        String requested = Objects.requireNonNull(operation, "operation");
        if (faulted && !isFaultTolerantOperation(requested)) requireTrusted(requested);
        if (dataFlowActive) {
            throw RuntimeFailures.reentrantAccess(
                    "ownership", dataFlowOperation, requested);
        }
    }

    /** Tracks a callback-scoped operation or ColumnView pin for DataFlow preflight. */
    public void beginTableScope(String operation) {
        String requested = Objects.requireNonNull(operation, "operation");
        preflightTableAccess(requested);
        beginTableScopePreflighted(requested);
    }

    void beginTableScopePreflighted(String operation) {
        String requested = Objects.requireNonNull(operation, "operation");
        if (activeTableScopes == Integer.MAX_VALUE) {
            throw internalInvariant(
                    "aggregate_scope_overflow", "ownership", requested);
        }
        if (activeTableScopes == 0) {
            activeTableScope = requested;
        }
        activeTableScopes++;
    }

    public void endTableScope(String operation) {
        String requested = Objects.requireNonNull(operation, "operation");
        if (activeTableScopes <= 0) {
            throw internalInvariant(
                    "aggregate_scope_underflow", "ownership", requested);
        }
        activeTableScopes--;
        if (activeTableScopes == 0) {
            activeTableScope = "";
        }
    }

    /**
     * Acquires the aggregate-local exclusive execution guard used by generated
     * DataFlow bindings.
     */
    public void beginDataFlow(String operation) {
        String requested = Objects.requireNonNull(operation, "operation");
        requireTrusted(requested);
        if (dataFlowActive) {
            throw RuntimeFailures.reentrantAccess(
                    "ownership", dataFlowOperation, requested);
        }
        if (materializationActive || activeTableScopes != 0 || cascadeDepth != 0) {
            String active = materializationActive
                    ? materializationOperation
                    : activeTableScopes != 0 ? activeTableScope : "ownership.cascade";
            throw RuntimeFailures.reentrantAccess("ownership", active, requested);
        }
        if (storageReleased) {
            throw RuntimeFailures.tableReleased("ownership", requested);
        }
        dataFlowOperation = requested;
        dataFlowActive = true;
    }

    private void requireTrusted(String operation) {
        if (faulted) {
            throw RuntimeFailures.internalInvariant(
                    "faulted_aggregate", "ownership", operation);
        }
    }

    private static boolean isFaultTolerantOperation(String operation) {
        return "runtimePlan".equals(operation)
                || "isReleased".equals(operation)
                || "statsSnapshot".equals(operation)
                || "release".equals(operation);
    }

    public void endDataFlow(String operation) {
        String requested = Objects.requireNonNull(operation, "operation");
        if (!dataFlowActive || !dataFlowOperation.equals(requested)
                || materializationActive) {
            throw internalInvariant(
                    "dataflow_aggregate_guard", "ownership", requested);
        }
        dataFlowActive = false;
        dataFlowOperation = "";
    }

    private static long nextAggregateInstanceId() {
        while (true) {
            long current = NEXT_AGGREGATE_INSTANCE_ID.get();
            if (current <= 0L || current == Long.MAX_VALUE) {
                throw RuntimeFailures.internalInvariant(
                        "aggregate_instance_id_exhausted", "ownership", "table.create");
            }
            if (NEXT_AGGREGATE_INSTANCE_ID.compareAndSet(current, current + 1L)) {
                return current;
            }
        }
    }

    public long newOwnerToken() {
        if (nextOwnerToken == Long.MAX_VALUE) {
            throw internalInvariant(
                    "child_owner_token_exhausted", "ownership", "child.create");
        }
        return nextOwnerToken++;
    }

    public long stage(
            long ownerToken,
            String field,
            String path,
            Object child,
            OwnedChildTable lifecycle) {
        requireOwner(ownerToken, path, "child.stage");
        String requiredField = Objects.requireNonNull(field, "field");
        String requiredPath = Objects.requireNonNull(path, "path");
        Object requiredChild = Objects.requireNonNull(child, "child");
        OwnedChildTable requiredLifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        if (identitySlot(requiredChild) >= 0) {
            throw internalFailure(
                    RuntimeFailures.ownershipCycle(requiredPath, "child.stage"),
                    "child.stage");
        }
        if (freeHead < 0 && nextSlot == Integer.MAX_VALUE) {
            throw RuntimeFailures.memoryLimitExceeded(
                    "ownership", "child.stage", Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
        int slot;
        if (freeHead >= 0) {
            slot = freeHead;
        } else {
            ensureStageCapacity(nextSlot + 1);
            slot = nextSlot;
        }
        if (freeHead >= 0) ensureStageCapacity(ownerTokens.length);
        int generation = generations[slot] + 1;
        if (generation <= 0) {
            throw internalInvariant(
                    "child_handle_generation_exhausted", "ownership", "child.stage");
        }
        generations[slot] = generation;
        ownerTokens[slot] = ownerToken;
        fields[slot] = requiredField;
        paths[slot] = requiredPath;
        children[slot] = requiredChild;
        lifecycles[slot] = requiredLifecycle;
        states[slot] = STAGED;
        if (slot == nextSlot) {
            nextSlot++;
        } else {
            freeHead = freeNext[slot];
        }
        insertIdentity(requiredChild, slot);
        return ((long) generation << 32) | ((long) slot & 0xffffffffL);
    }

    public void publish(long handle, long ownerToken, String field) {
        int index = staged(handle, ownerToken, field, "child.publish");
        states[index] = LIVE;
    }

    public void discardStaged(long handle, long ownerToken, String field) {
        int index = staged(handle, ownerToken, field, "child.discard");
        OwnedChildTable lifecycle = lifecycles[index];
        lifecycle.preflightOwnedRelease(false);
        lifecycle.releaseOwnedSubtree(false);
        states[index] = RELEASED;
        free(index);
    }

    public Object resolve(
            long handle,
            long ownerToken,
            String field,
            String operation) {
        return children[live(handle, ownerToken, field, operation)];
    }

    public void preflightPinned(
            long handle,
            long ownerToken,
            String field,
            String operation) {
        if (handle == 0L) return;
        int index = live(handle, ownerToken, field, operation);
        if (lifecycles[index].hasPinnedSubtree()) {
            throw RuntimeFailures.viewPinned(paths[index], operation, 1);
        }
    }

    public void release(
            long handle,
            long ownerToken,
            String field,
            String operation,
            boolean aggregateRelease) {
        if (handle == 0L) return;
        int index = index(handle, operation);
        if (states[index] == RELEASED) return;
        validateOwner(index, ownerToken, field, operation);
        if (states[index] != LIVE) {
            throw internalFailure(
                    RuntimeFailures.childDangling(paths[index], operation), operation);
        }
        OwnedChildTable lifecycle = lifecycles[index];
        lifecycle.preflightOwnedRelease(aggregateRelease);
        releasePreflighted(index, aggregateRelease);
    }

    public void preflightRelease(
            long handle,
            long ownerToken,
            String field,
            String operation,
            boolean aggregateRelease) {
        if (handle == 0L) return;
        int index = live(handle, ownerToken, field, operation);
        lifecycles[index].preflightOwnedRelease(aggregateRelease);
    }

    public void releasePreflighted(
            long handle,
            long ownerToken,
            String field,
            String operation,
            boolean aggregateRelease) {
        if (handle == 0L) return;
        int index = live(handle, ownerToken, field, operation);
        releasePreflighted(index, aggregateRelease);
    }

    private void releasePreflighted(int index, boolean aggregateRelease) {
        lifecycles[index].releaseOwnedSubtree(aggregateRelease);
        states[index] = RELEASED;
        free(index);
    }

    /**
     * Starts a reusable primitive cascade collection. Expected failures leave every registry
     * slot LIVE so the caller can retry after the external condition is corrected.
     */
    public void beginCascade(
            long expectedHandles,
            long maximumBulkScratchBytes,
            String table,
            String operation) {
        if (cascadeDepth >= cascadeStarts.length) {
            throw RuntimeFailures.memoryLimitExceeded(
                    table, operation, maximumBulkScratchBytes, Long.MAX_VALUE);
        }
        if (expectedHandles < 0L || expectedHandles > Integer.MAX_VALUE) {
            throw RuntimeFailures.memoryLimitExceeded(
                    table, operation, maximumBulkScratchBytes, Long.MAX_VALUE);
        }
        long requiredBytes = checkedMultiply(expectedHandles, 8L);
        if (requiredBytes > maximumBulkScratchBytes) {
            throw RuntimeFailures.memoryLimitExceeded(
                    table, operation, maximumBulkScratchBytes, requiredBytes);
        }
        long totalRequired = (long) cascadeUsed + expectedHandles;
        if (totalRequired > Integer.MAX_VALUE) {
            throw RuntimeFailures.memoryLimitExceeded(
                    table, operation, maximumBulkScratchBytes, Long.MAX_VALUE);
        }
        ensureCascadeCapacity((int) totalRequired, table, operation);
        cascadeStarts[cascadeDepth] = cascadeUsed;
        cascadeCounts[cascadeDepth] = 0;
        cascadeOperations[cascadeDepth] = Objects.requireNonNull(operation, "operation");
        cascadeDepth++;
    }

    public void collectCascade(
            long handle,
            long ownerToken,
            String field,
            boolean requireUnpinned,
            String operation) {
        requireCascade(operation);
        if (handle == 0L) return;
        int index = live(handle, ownerToken, field, operation);
        if (requireUnpinned && lifecycles[index].hasPinnedSubtree()) {
            throw RuntimeFailures.viewPinned(paths[index], operation, 1);
        }
        int frame = cascadeDepth - 1;
        if (cascadeUsed >= cascadeHandles.length) {
            throw internalInvariant(
                    "cascade_collection_capacity", paths[index], operation);
        }
        cascadeHandles[cascadeUsed++] = handle;
        cascadeCounts[frame]++;
    }

    public void commitCascade(boolean aggregateRelease, String operation) {
        requireCascade(operation);
        int frame = cascadeDepth - 1;
        int start = cascadeStarts[frame];
        int count = cascadeCounts[frame];
        try {
            // Complete the entire recursive resource/pin preflight before any visible release.
            for (int i = 0; i < count; i++) {
                int index = index(cascadeHandles[start + i], operation);
                if (states[index] != LIVE) {
                    throw internalFailure(
                            RuntimeFailures.childDangling(paths[index], operation), operation);
                }
                lifecycles[index].preflightOwnedRelease(aggregateRelease);
            }
            // External lifecycle callbacks run while every registry slot is still LIVE.
            for (int i = 0; i < count; i++) {
                int index = index(cascadeHandles[start + i], operation);
                if (states[index] != LIVE) {
                    throw internalFailure(
                            RuntimeFailures.childDangling(paths[index], operation), operation);
                }
                lifecycles[index].releaseOwnedSubtree(aggregateRelease);
            }
            // Publishing RELEASED/free is non-callback and cannot fail on expected input.
            for (int i = 0; i < count; i++) {
                int index = index(cascadeHandles[start + i], operation);
                states[index] = RELEASED;
                free(index);
            }
        } finally {
            finishCascade();
        }
    }

    /** Recursively proves that the current cascade can commit without expected failure. */
    public void preflightCascade(boolean aggregateRelease, String operation) {
        requireCascade(operation);
        int frame = cascadeDepth - 1;
        int start = cascadeStarts[frame];
        int count = cascadeCounts[frame];
        try {
            for (int i = 0; i < count; i++) {
                int index = index(cascadeHandles[start + i], operation);
                if (states[index] != LIVE) {
                    throw internalFailure(
                            RuntimeFailures.childDangling(paths[index], operation), operation);
                }
                lifecycles[index].preflightOwnedRelease(aggregateRelease);
            }
        } finally {
            finishCascade();
        }
    }

    public void cancelCascade(String operation) {
        requireCascade(operation);
        finishCascade();
    }

    long retainedBytes() { return retainedBytes; }
    long cascadeScratchBytes() {
        return 8L * (long) cascadeHandles.length
                + 16L * (long) cascadeStarts.length;
    }

    public long childInstanceCount(long handle, long ownerToken, String field) {
        if (handle == 0L) return 0L;
        int index = live(handle, ownerToken, field, "statsSnapshot");
        return 1L + lifecycles[index].subtreeChildInstanceCount();
    }

    public long descendantRowCount(long handle, long ownerToken, String field) {
        if (handle == 0L) return 0L;
        return lifecycles[live(handle, ownerToken, field, "statsSnapshot")]
                .subtreeDescendantRowCount();
    }

    public boolean hasPinned(long handle, long ownerToken, String field, String operation) {
        return handle != 0L
                && lifecycles[live(handle, ownerToken, field, operation)].hasPinnedSubtree();
    }

    private int staged(long handle, long ownerToken, String field, String operation) {
        int index = index(handle, operation);
        validateOwner(index, ownerToken, field, operation);
        if (states[index] != STAGED) {
            throw internalInvariant("child_stage_state", paths[index], operation);
        }
        return index;
    }

    private int live(long handle, long ownerToken, String field, String operation) {
        int index = index(handle, operation);
        if (states[index] == RELEASED) {
            throw RuntimeFailures.childReleased(
                    paths[index] == null ? "ownership" : paths[index], operation);
        }
        validateOwner(index, ownerToken, field, operation);
        if (states[index] != LIVE) {
            throw internalFailure(
                    RuntimeFailures.childDangling(paths[index], operation), operation);
        }
        return index;
    }

    private int index(long handle, String operation) {
        int slot = (int) handle;
        int generation = (int) (handle >>> 32);
        if (slot <= 0 || slot >= nextSlot || generation <= 0
                || generations[slot] != generation) {
            throw internalFailure(
                    RuntimeFailures.childDangling("ownership", operation), operation);
        }
        int index = slot;
        if (states[index] != RELEASED
                && (children[index] == null || lifecycles[index] == null)) {
            throw internalFailure(
                    RuntimeFailures.childDangling("ownership", operation), operation);
        }
        return index;
    }

    private void free(int index) {
        removeIdentity(children[index], index);
        ownerTokens[index] = 0L;
        fields[index] = null;
        paths[index] = null;
        children[index] = null;
        lifecycles[index] = null;
        freeNext[index] = freeHead;
        freeHead = index;
    }

    private int identitySlot(Object child) {
        int mask = identitySlots.length - 1;
        int index = identityHash(child) & mask;
        while (identitySlots[index] != 0) {
            if (identitySlots[index] > 0 && identityChildren[index] == child) {
                return identitySlots[index];
            }
            index = (index + 1) & mask;
        }
        return -1;
    }

    /** Stages slot-array and identity-array growth as one admission/allocation/commit unit. */
    private void ensureStageCapacity(int requiredSlots) {
        boolean rebuildIdentity = (identityUsed + 1L) * 3L
                > (long) identitySlots.length * 2L;
        int identityCapacity = identitySlots.length;
        if (rebuildIdentity && (long) identitySize * 3L >= (long) identitySlots.length) {
            if (identitySlots.length > (1 << 29)) {
                throw RuntimeFailures.memoryLimitExceeded(
                        "ownership", "child.stage", Integer.MAX_VALUE, Integer.MAX_VALUE);
            }
            identityCapacity = identitySlots.length << 1;
        }
        int slotCapacity = ownerTokens.length;
        while (slotCapacity < requiredSlots) {
            int grown = slotCapacity + (slotCapacity >>> 1);
            if (grown <= slotCapacity || grown < 0) {
                slotCapacity = Integer.MAX_VALUE;
                break;
            }
            slotCapacity = grown;
        }
        boolean growSlots = slotCapacity != ownerTokens.length;
        if (!rebuildIdentity && !growSlots) return;

        long nextBytes = estimatedRetainedBytes(
                slotCapacity, identityCapacity, cascadeHandles.length);
        long delta = nextBytes - retainedBytes;
        long transientBytes = 0L;
        if (rebuildIdentity) {
            transientBytes = checkedAddForStage(
                    transientBytes, 12L * (long) identitySlots.length);
        }
        if (growSlots) {
            transientBytes = checkedAddForStage(
                    transientBytes, 53L * (long) ownerTokens.length);
        }
        storageBudget.reserveBytes(delta, "ownership", "child.stage");
        boolean transientReserved = false;
        try {
            storageBudget.reserveTransientBytes(
                    transientBytes, "ownership", "child.stage");
            transientReserved = true;

            long[] stagedOwnerTokens = ownerTokens;
            String[] stagedFields = fields;
            String[] stagedPaths = paths;
            Object[] stagedChildren = children;
            OwnedChildTable[] stagedLifecycles = lifecycles;
            byte[] stagedStates = states;
            int[] stagedGenerations = generations;
            int[] stagedFreeNext = freeNext;
            if (growSlots) {
                stagedOwnerTokens = Arrays.copyOf(ownerTokens, slotCapacity);
                stagedFields = Arrays.copyOf(fields, slotCapacity);
                stagedPaths = Arrays.copyOf(paths, slotCapacity);
                stagedChildren = Arrays.copyOf(children, slotCapacity);
                stagedLifecycles = Arrays.copyOf(lifecycles, slotCapacity);
                stagedStates = Arrays.copyOf(states, slotCapacity);
                stagedGenerations = Arrays.copyOf(generations, slotCapacity);
                stagedFreeNext = Arrays.copyOf(freeNext, slotCapacity);
            }

            Object[] stagedIdentityChildren = identityChildren;
            int[] stagedIdentitySlots = identitySlots;
            if (rebuildIdentity) {
                stagedIdentityChildren = new Object[identityCapacity];
                stagedIdentitySlots = new int[identityCapacity];
                for (int i = 0; i < identitySlots.length; i++) {
                    if (identitySlots[i] <= 0) continue;
                    insertIdentity(stagedIdentityChildren, stagedIdentitySlots,
                            identityChildren[i], identitySlots[i]);
                }
            }

            ownerTokens = stagedOwnerTokens;
            fields = stagedFields;
            paths = stagedPaths;
            children = stagedChildren;
            lifecycles = stagedLifecycles;
            states = stagedStates;
            generations = stagedGenerations;
            freeNext = stagedFreeNext;
            identityChildren = stagedIdentityChildren;
            identitySlots = stagedIdentitySlots;
            if (rebuildIdentity) identityUsed = identitySize;
            retainedBytes = nextBytes;
        } catch (RuntimeException failure) {
            if (transientReserved) storageBudget.releaseTransientBytes(
                    transientBytes, "ownership", "child.stage");
            storageBudget.releaseBytes(delta, "ownership", "child.stage");
            throw failure;
        } catch (Error failure) {
            if (transientReserved) storageBudget.releaseTransientBytes(
                    transientBytes, "ownership", "child.stage");
            storageBudget.releaseBytes(delta, "ownership", "child.stage");
            throw failure;
        }
        storageBudget.releaseTransientBytes(
                transientBytes, "ownership", "child.stage");
    }

    private static long checkedAddForStage(long left, long right) {
        if (right < 0L || Long.MAX_VALUE - left < right) {
            throw RuntimeFailures.memoryLimitExceeded(
                    "ownership", "child.stage", Long.MAX_VALUE, Long.MAX_VALUE);
        }
        return left + right;
    }

    private void ensureIdentityInsertCapacity() {
        if ((identityUsed + 1L) * 3L <= (long) identitySlots.length * 2L) return;
        int next;
        if ((long) identitySize * 3L < (long) identitySlots.length) {
            next = identitySlots.length;
        } else {
            if (identitySlots.length > (1 << 29)) {
                throw RuntimeFailures.memoryLimitExceeded(
                        "ownership", "child.stage", Integer.MAX_VALUE, Integer.MAX_VALUE);
            }
            next = identitySlots.length << 1;
        }
        long nextBytes = estimatedRetainedBytes(
                ownerTokens.length, next, cascadeHandles.length);
        long delta = nextBytes - retainedBytes;
        storageBudget.reserveBytes(delta, "ownership", "child.stage");
        long transientBytes = 12L * (long) identitySlots.length;
        boolean transientReserved = false;
        try {
            storageBudget.reserveTransientBytes(
                    transientBytes, "ownership", "child.stage");
            transientReserved = true;
            Object[] stagedChildren = new Object[next];
            int[] stagedSlots = new int[next];
            for (int i = 0; i < identitySlots.length; i++) {
                if (identitySlots[i] <= 0) continue;
                insertIdentity(stagedChildren, stagedSlots,
                        identityChildren[i], identitySlots[i]);
            }
            identityChildren = stagedChildren;
            identitySlots = stagedSlots;
            identityUsed = identitySize;
            retainedBytes = nextBytes;
        } catch (RuntimeException failure) {
            if (transientReserved) storageBudget.releaseTransientBytes(
                    transientBytes, "ownership", "child.stage");
            storageBudget.releaseBytes(delta, "ownership", "child.stage");
            throw failure;
        } catch (Error failure) {
            if (transientReserved) storageBudget.releaseTransientBytes(
                    transientBytes, "ownership", "child.stage");
            storageBudget.releaseBytes(delta, "ownership", "child.stage");
            throw failure;
        }
        storageBudget.releaseTransientBytes(
                transientBytes, "ownership", "child.stage");
    }

    private void insertIdentity(Object child, int slot) {
        int mask = identitySlots.length - 1;
        int index = identityHash(child) & mask;
        int tombstone = -1;
        while (identitySlots[index] != 0) {
            if (identitySlots[index] == IDENTITY_TOMBSTONE && tombstone < 0) {
                tombstone = index;
            }
            index = (index + 1) & mask;
        }
        int target = tombstone >= 0 ? tombstone : index;
        if (tombstone < 0) identityUsed++;
        identityChildren[target] = child;
        identitySlots[target] = slot;
        identitySize++;
    }

    private static void insertIdentity(
            Object[] childrenByIdentity,
            int[] slotsByIdentity,
            Object child,
            int slot) {
        int mask = slotsByIdentity.length - 1;
        int index = identityHash(child) & mask;
        while (slotsByIdentity[index] != 0) index = (index + 1) & mask;
        childrenByIdentity[index] = child;
        slotsByIdentity[index] = slot;
    }

    private void removeIdentity(Object child, int slot) {
        int mask = identitySlots.length - 1;
        int index = identityHash(child) & mask;
        while (identitySlots[index] != 0) {
            if (identitySlots[index] == slot && identityChildren[index] == child) {
                identityChildren[index] = null;
                identitySlots[index] = IDENTITY_TOMBSTONE;
                identitySize--;
                return;
            }
            index = (index + 1) & mask;
        }
        throw internalInvariant(
                "child_identity_registry_missing", "ownership", "child.release");
    }

    private static int identityHash(Object child) {
        int hash = System.identityHashCode(child);
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        return hash;
    }

    private void validateOwner(int index, long ownerToken, String field, String operation) {
        requireOwner(ownerToken, paths[index], operation);
        if (ownerTokens[index] != ownerToken || fields[index] == null
                || !fields[index].equals(field)) {
            throw internalFailure(
                    RuntimeFailures.childWrongOwner(paths[index], operation), operation);
        }
    }

    private void ensureCapacity(int required) {
        if (required <= ownerTokens.length) return;
        int next = ownerTokens.length;
        while (next < required) {
            int grown = next + (next >>> 1);
            if (grown <= next || grown < 0) {
                next = Integer.MAX_VALUE;
                break;
            }
            next = grown;
        }
        long nextBytes = estimatedRetainedBytes(
                next, identitySlots.length, cascadeHandles.length);
        long delta = nextBytes - retainedBytes;
        storageBudget.reserveBytes(delta, "ownership", "child.stage");
        long transientBytes = 53L * (long) ownerTokens.length;
        boolean transientReserved = false;
        try {
            storageBudget.reserveTransientBytes(
                    transientBytes, "ownership", "child.stage");
            transientReserved = true;
            long[] stagedOwnerTokens = Arrays.copyOf(ownerTokens, next);
            String[] stagedFields = Arrays.copyOf(fields, next);
            String[] stagedPaths = Arrays.copyOf(paths, next);
            Object[] stagedChildren = Arrays.copyOf(children, next);
            OwnedChildTable[] stagedLifecycles = Arrays.copyOf(lifecycles, next);
            byte[] stagedStates = Arrays.copyOf(states, next);
            int[] stagedGenerations = Arrays.copyOf(generations, next);
            int[] stagedFreeNext = Arrays.copyOf(freeNext, next);
            ownerTokens = stagedOwnerTokens;
            fields = stagedFields;
            paths = stagedPaths;
            children = stagedChildren;
            lifecycles = stagedLifecycles;
            states = stagedStates;
            generations = stagedGenerations;
            freeNext = stagedFreeNext;
            retainedBytes = nextBytes;
        } catch (RuntimeException failure) {
            if (transientReserved) storageBudget.releaseTransientBytes(
                    transientBytes, "ownership", "child.stage");
            storageBudget.releaseBytes(delta, "ownership", "child.stage");
            throw failure;
        } catch (Error failure) {
            if (transientReserved) storageBudget.releaseTransientBytes(
                    transientBytes, "ownership", "child.stage");
            storageBudget.releaseBytes(delta, "ownership", "child.stage");
            throw failure;
        }
        storageBudget.releaseTransientBytes(
                transientBytes, "ownership", "child.stage");
    }

    private void ensureCascadeCapacity(int required, String table, String operation) {
        if (required <= cascadeHandles.length) return;
        int next = cascadeHandles.length == 0 ? 4 : cascadeHandles.length;
        while (next < required) {
            int grown = next + (next >>> 1);
            if (grown <= next || grown < 0) {
                next = required;
                break;
            }
            next = grown;
        }
        long nextBytes = estimatedRetainedBytes(
                ownerTokens.length, identitySlots.length, next);
        long delta = nextBytes - retainedBytes;
        long oldArrayBytes = 8L * (long) cascadeHandles.length;
        storageBudget.reserveBytes(delta, table, operation);
        boolean transientReserved = false;
        try {
            storageBudget.reserveTransientBytes(oldArrayBytes, table, operation);
            transientReserved = true;
            cascadeHandles = Arrays.copyOf(cascadeHandles, next);
            retainedBytes = nextBytes;
        } catch (RuntimeException failure) {
            if (transientReserved) {
                storageBudget.releaseTransientBytes(oldArrayBytes, table, operation);
            }
            storageBudget.releaseBytes(delta, table, operation);
            throw failure;
        } catch (Error failure) {
            if (transientReserved) {
                storageBudget.releaseTransientBytes(oldArrayBytes, table, operation);
            }
            storageBudget.releaseBytes(delta, table, operation);
            throw failure;
        }
        storageBudget.releaseTransientBytes(oldArrayBytes, table, operation);
    }

    private void requireCascade(String operation) {
        if (cascadeDepth <= 0
                || !cascadeOperations[cascadeDepth - 1].equals(operation)) {
            throw internalInvariant(
                    "cascade_scope", "ownership", operation);
        }
    }

    private void finishCascade() {
        int frame = cascadeDepth - 1;
        int start = cascadeStarts[frame];
        Arrays.fill(cascadeHandles, start, cascadeUsed, 0L);
        cascadeUsed = start;
        cascadeStarts[frame] = 0;
        cascadeCounts[frame] = 0;
        cascadeOperations[frame] = null;
        cascadeDepth--;
    }

    private static long estimatedRetainedBytes(
            int slotCapacity, int identityCapacity, int cascadeCapacity) {
        return 53L * (long) slotCapacity + 12L * (long) identityCapacity
                + 8L * (long) cascadeCapacity
                + 16L * (long) MAX_CASCADE_DEPTH;
    }

    private static long checkedMultiply(long left, long right) {
        return left < 0L || right < 0L || left > Long.MAX_VALUE / right
                ? Long.MAX_VALUE : left * right;
    }

    private void requireOwner(long ownerToken, String path, String operation) {
        if (ownerToken <= 0L) {
            throw internalFailure(
                    RuntimeFailures.childWrongOwner(path, operation), operation);
        }
    }
}
