package com.hgtech.soma.runtime.generated;

import java.util.Arrays;
import java.util.Objects;

/** Aggregate-local opaque child locator registry；canonical storage是平行数组。 */
public final class ChildOwnershipRegistry {
    private static final byte STAGED = 0;
    private static final byte LIVE = 1;
    private static final byte RELEASED = 2;
    private static final int IDENTITY_TOMBSTONE = -1;
    private static final int MAX_CASCADE_DEPTH = 256;

    private final StorageBudget storageBudget;
    private long retainedBytes;
    private boolean storageReleased;

    private long[] ownerTokens = new long[16];
    private String[] fields = new String[16];
    private String[] paths = new String[16];
    private Object[] children = new Object[16];
    private OwnedChildTable[] lifecycles = new OwnedChildTable[16];
    private byte[] states = new byte[16];
    private int[] generations = new int[16];
    private int[] freeNext = new int[16];
    private Object[] identityChildren = new Object[32];
    private int[] identitySlots = new int[32];
    private long[] cascadeHandles = new long[0];
    private int[] cascadeStarts = new int[MAX_CASCADE_DEPTH];
    private int[] cascadeCounts = new int[MAX_CASCADE_DEPTH];
    private String[] cascadeOperations = new String[MAX_CASCADE_DEPTH];
    private int cascadeUsed;
    private int cascadeDepth;
    private int identitySize;
    private int identityUsed;
    private int nextSlot = 1; // slot 0 / handle 0 are never live
    private int freeHead = -1;
    private long nextOwnerToken = 1L;
    private boolean materializationActive;
    private String materializationOperation = "";

    public ChildOwnershipRegistry() {
        this(1024L * 1024L * 1024L, 65536L);
    }

    public ChildOwnershipRegistry(long maximumBytes, long maximumTableInstances) {
        storageBudget = new StorageBudget(maximumBytes, maximumTableInstances);
        retainedBytes = estimatedRetainedBytes(
                ownerTokens.length, identitySlots.length, cascadeHandles.length);
        storageBudget.reserveBytes(retainedBytes, "ownership", "ownership.create");
    }

    StorageBudget storageBudgetInternal() { return storageBudget; }

    public void releaseStorage() {
        if (storageReleased) return;
        if (cascadeDepth != 0) {
            throw RuntimeFailures.internalInvariant(
                    "cascade_release_scope", "ownership", "release");
        }
        if (identitySize != 0 || storageBudget.currentTableInstances() != 0L
                || storageBudget.transientBytes() != 0L
                || storageBudget.currentBytes() != retainedBytes) {
            throw RuntimeFailures.internalInvariant(
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
            throw RuntimeFailures.internalInvariant(
                    "ownership_materialization_guard", "ownership", "materialize");
        }
        materializationActive = false;
        materializationOperation = "";
    }

    /** Rejects any visible aggregate mutation while two-pass materialization is active. */
    public void preflightMutation(String operation) {
        String requested = Objects.requireNonNull(operation, "operation");
        if (materializationActive) {
            throw RuntimeFailures.reentrantAccess(
                    "ownership", materializationOperation, requested);
        }
    }

    public long newOwnerToken() {
        if (nextOwnerToken == Long.MAX_VALUE) {
            throw RuntimeFailures.internalInvariant(
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
            throw RuntimeFailures.ownershipCycle(requiredPath, "child.stage");
        }
        ensureIdentityInsertCapacity();
        if (freeHead < 0 && nextSlot == Integer.MAX_VALUE) {
            throw RuntimeFailures.memoryLimitExceeded(
                    "ownership", "child.stage", Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
        int slot;
        if (freeHead >= 0) {
            slot = freeHead;
        } else {
            ensureCapacity(nextSlot + 1);
            slot = nextSlot;
        }
        int generation = generations[slot] + 1;
        if (generation <= 0) {
            throw RuntimeFailures.internalInvariant(
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
        if (states[index] != LIVE) throw RuntimeFailures.childDangling(paths[index], operation);
        OwnedChildTable lifecycle = lifecycles[index];
        lifecycle.releaseOwnedSubtree(aggregateRelease);
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
            throw RuntimeFailures.internalInvariant(
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
            // External lifecycle callbacks run while every registry slot is still LIVE.
            for (int i = 0; i < count; i++) {
                int index = index(cascadeHandles[start + i], operation);
                if (states[index] != LIVE) {
                    throw RuntimeFailures.childDangling(paths[index], operation);
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
            throw RuntimeFailures.internalInvariant("child_stage_state", paths[index], operation);
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
            throw RuntimeFailures.childDangling(paths[index], operation);
        }
        return index;
    }

    private int index(long handle, String operation) {
        int slot = (int) handle;
        int generation = (int) (handle >>> 32);
        if (slot <= 0 || slot >= nextSlot || generation <= 0
                || generations[slot] != generation) {
            throw RuntimeFailures.childDangling("ownership", operation);
        }
        int index = slot;
        if (states[index] != RELEASED
                && (children[index] == null || lifecycles[index] == null)) {
            throw RuntimeFailures.childDangling("ownership", operation);
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
        throw RuntimeFailures.internalInvariant(
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
            throw RuntimeFailures.childWrongOwner(paths[index], operation);
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
            throw RuntimeFailures.internalInvariant(
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

    private static void requireOwner(long ownerToken, String path, String operation) {
        if (ownerToken <= 0L) throw RuntimeFailures.childWrongOwner(path, operation);
    }
}
