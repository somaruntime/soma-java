package com.hgtech.soma.runtime.generated;

import java.util.Arrays;
import java.util.Objects;

/** Aggregate-local opaque child locator registry；canonical storage是平行数组。 */
public final class ChildOwnershipRegistry {
    private static final byte STAGED = 0;
    private static final byte LIVE = 1;
    private static final byte RELEASED = 2;
    private static final int IDENTITY_TOMBSTONE = -1;

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
    private int identitySize;
    private int identityUsed;
    private int nextSlot = 1; // slot 0 / handle 0 are never live
    private int freeHead = -1;
    private long nextOwnerToken = 1L;
    private boolean materializationActive;
    private String materializationOperation = "";

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
        states[index] = RELEASED;
        OwnedChildTable lifecycle = lifecycles[index];
        lifecycle.releaseOwnedSubtree(false);
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
        states[index] = RELEASED;
        OwnedChildTable lifecycle = lifecycles[index];
        lifecycle.releaseOwnedSubtree(aggregateRelease);
        free(index);
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
    }

    private static void requireOwner(long ownerToken, String path, String operation) {
        if (ownerToken <= 0L) throw RuntimeFailures.childWrongOwner(path, operation);
    }
}
