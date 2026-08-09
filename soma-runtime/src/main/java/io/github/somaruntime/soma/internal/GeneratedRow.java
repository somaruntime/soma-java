package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.UpdateResult;

/** Reusable operation-scoped exact-typed row staging owned by one generated Table. */
public final class GeneratedRow extends TypedValues
        implements AutoCloseable, GeneratedRowAccess {

    static final int ADD = 1;
    static final int FIND = 2;
    static final int GET = 3;
    static final int UPDATE = 4;
    static final int REMOVE = 5;

    private final GeneratedTable owner;
    private final GeneratedTableLayout layout;
    private final TypedValues original;
    private GroupOperationGuard.Lease operationLease;
    private GlobalMemoryManager.TemporaryLease temporaryLease;
    private int mode;
    private boolean rowLoaded;
    private boolean callbackActive;
    private Thread operationThread;
    private long locator = -1L;

    GeneratedRow(GeneratedTable owner, GeneratedTableLayout layout) {
        super(layout);
        this.owner = owner;
        this.layout = layout;
        this.original = new TypedValues(layout);
    }

    @Override
    public void registerBorrowedView(Object view) {
        if (view == null) {
            throw new AssertionError("invalid borrowed View registration");
        }
        // Mutation Views are guarded by this row's callback epoch. Query mapper
        // escape detection is owned by GeneratedQueryCursor instead.
    }

    void begin(GroupOperationGuard.Lease lease, int operationMode) {
        if (operationLease != null || lease == null) {
            throw new AssertionError("generated row operation overlap");
        }
        operationLease = lease;
        mode = operationMode;
        operationThread = Thread.currentThread();
        rowLoaded = false;
        callbackActive = false;
        locator = -1L;
        clearReferences();
        original.clearReferences();
    }

    public void putBoolean(int leaf, boolean value) { requireInput(leaf, GeneratedTableLayout.BOOLEAN); booleanValue(layout.leafSlot(leaf), value); }
    public void putByte(int leaf, byte value) { requireInput(leaf, GeneratedTableLayout.BYTE); byteValue(layout.leafSlot(leaf), value); }
    public void putShort(int leaf, short value) { requireInput(leaf, GeneratedTableLayout.SHORT); shortValue(layout.leafSlot(leaf), value); }
    public void putChar(int leaf, char value) { requireInput(leaf, GeneratedTableLayout.CHAR); charValue(layout.leafSlot(leaf), value); }
    public void putInt(int leaf, int value) { requireInput(leaf, GeneratedTableLayout.INT); intValue(layout.leafSlot(leaf), value); }
    public void putLong(int leaf, long value) { requireInput(leaf, GeneratedTableLayout.LONG); longValue(layout.leafSlot(leaf), value); }
    public void putFloat(int leaf, float value) { requireInput(leaf, GeneratedTableLayout.FLOAT); floatValue(layout.leafSlot(leaf), value); }
    public void putDouble(int leaf, double value) { requireInput(leaf, GeneratedTableLayout.DOUBLE); doubleValue(layout.leafSlot(leaf), value); }
    public void putReference(int leaf, Object value) { requireInput(leaf, GeneratedTableLayout.REFERENCE); reference(layout.leafSlot(leaf), value); }

    public void requireArgument(Object value, SomaOperation operation, String category) {
        owner.requireArgument(value, operation, category);
    }

    public boolean readBoolean(int leaf) { requireRead(leaf, GeneratedTableLayout.BOOLEAN); return booleanValue(layout.leafSlot(leaf)); }
    public byte readByte(int leaf) { requireRead(leaf, GeneratedTableLayout.BYTE); return byteValue(layout.leafSlot(leaf)); }
    public short readShort(int leaf) { requireRead(leaf, GeneratedTableLayout.SHORT); return shortValue(layout.leafSlot(leaf)); }
    public char readChar(int leaf) { requireRead(leaf, GeneratedTableLayout.CHAR); return charValue(layout.leafSlot(leaf)); }
    public int readInt(int leaf) { requireRead(leaf, GeneratedTableLayout.INT); return intValue(layout.leafSlot(leaf)); }
    public long readLong(int leaf) { requireRead(leaf, GeneratedTableLayout.LONG); return longValue(layout.leafSlot(leaf)); }
    public float readFloat(int leaf) { requireRead(leaf, GeneratedTableLayout.FLOAT); return floatValue(layout.leafSlot(leaf)); }
    public double readDouble(int leaf) { requireRead(leaf, GeneratedTableLayout.DOUBLE); return doubleValue(layout.leafSlot(leaf)); }
    public Object readReference(int leaf) { requireRead(leaf, GeneratedTableLayout.REFERENCE); return reference(layout.leafSlot(leaf)); }

    @Override public boolean viewBoolean(int leaf) { requireView(leaf, GeneratedTableLayout.BOOLEAN); return booleanValue(layout.leafSlot(leaf)); }
    @Override public byte viewByte(int leaf) { requireView(leaf, GeneratedTableLayout.BYTE); return byteValue(layout.leafSlot(leaf)); }
    @Override public short viewShort(int leaf) { requireView(leaf, GeneratedTableLayout.SHORT); return shortValue(layout.leafSlot(leaf)); }
    @Override public char viewChar(int leaf) { requireView(leaf, GeneratedTableLayout.CHAR); return charValue(layout.leafSlot(leaf)); }
    @Override public int viewInt(int leaf) { requireView(leaf, GeneratedTableLayout.INT); return intValue(layout.leafSlot(leaf)); }
    @Override public long viewLong(int leaf) { requireView(leaf, GeneratedTableLayout.LONG); return longValue(layout.leafSlot(leaf)); }
    @Override public float viewFloat(int leaf) { requireView(leaf, GeneratedTableLayout.FLOAT); return floatValue(layout.leafSlot(leaf)); }
    @Override public double viewDouble(int leaf) { requireView(leaf, GeneratedTableLayout.DOUBLE); return doubleValue(layout.leafSlot(leaf)); }
    @Override public Object viewReference(int leaf) { requireView(leaf, GeneratedTableLayout.REFERENCE); return reference(layout.leafSlot(leaf)); }

    public void editBoolean(int leaf, boolean value) { requireEdit(leaf, GeneratedTableLayout.BOOLEAN); booleanValue(layout.leafSlot(leaf), value); }
    public void editByte(int leaf, byte value) { requireEdit(leaf, GeneratedTableLayout.BYTE); byteValue(layout.leafSlot(leaf), value); }
    public void editShort(int leaf, short value) { requireEdit(leaf, GeneratedTableLayout.SHORT); shortValue(layout.leafSlot(leaf), value); }
    public void editChar(int leaf, char value) { requireEdit(leaf, GeneratedTableLayout.CHAR); charValue(layout.leafSlot(leaf), value); }
    public void editInt(int leaf, int value) { requireEdit(leaf, GeneratedTableLayout.INT); intValue(layout.leafSlot(leaf), value); }
    public void editLong(int leaf, long value) { requireEdit(leaf, GeneratedTableLayout.LONG); longValue(layout.leafSlot(leaf), value); }
    public void editFloat(int leaf, float value) { requireEdit(leaf, GeneratedTableLayout.FLOAT); floatValue(layout.leafSlot(leaf), value); }
    public void editDouble(int leaf, double value) { requireEdit(leaf, GeneratedTableLayout.DOUBLE); doubleValue(layout.leafSlot(leaf), value); }
    public void editReference(int leaf, Object value) { requireEdit(leaf, GeneratedTableLayout.REFERENCE); reference(layout.leafSlot(leaf), value); }

    public void add() {
        requireMode(ADD);
        owner.add(this, provenance());
    }

    public boolean find() {
        requireMode(FIND);
        rowLoaded = owner.loadByKey(this, false, provenance());
        return rowLoaded;
    }

    public void get() {
        requireMode(GET);
        rowLoaded = owner.loadByKey(this, true, provenance());
    }

    public boolean locateForUpdate() {
        requireMode(UPDATE);
        locator = owner.locateByKey(this);
        if (locator < 0L) return false;
        owner.load(locator, this);
        original.copyFrom(this);
        rowLoaded = true;
        temporaryLease = owner.admitPointUpdate(provenance());
        return true;
    }

    public void beginEditorCallback() {
        requireMode(UPDATE);
        if (!rowLoaded || callbackActive) throw new AssertionError("invalid Editor callback scope");
        callbackActive = true;
    }

    public void endEditorCallback() {
        requireMode(UPDATE);
        if (!callbackActive) throw new AssertionError("Editor callback scope is not active");
        callbackActive = false;
    }

    public RuntimeException callbackFailure(Exception failure) {
        return SomaFailures.callbackFailure(SomaOperation.UPDATE, failure, provenance());
    }

    public UpdateResult finishUpdate() {
        requireMode(UPDATE);
        if (!rowLoaded || callbackActive) throw new AssertionError("Editor callback is incomplete");
        return owner.update(locator, original, this, provenance());
    }

    public RemoveResult remove() {
        requireMode(REMOVE);
        return owner.remove(this, provenance());
    }

    @Override
    public void close() {
        if (operationLease == null) return;
        callbackActive = false;
        rowLoaded = false;
        locator = -1L;
        mode = 0;
        operationThread = null;
        clearReferences();
        original.clearReferences();
        if (temporaryLease != null) {
            temporaryLease.close();
            temporaryLease = null;
        }
        GroupOperationGuard.Lease lease = operationLease;
        operationLease = null;
        lease.close();
    }

    TypedValues original() {
        return original;
    }

    private Object provenance() {
        requireActive();
        return operationLease.provenance();
    }

    private void requireInput(int leaf, byte kind) {
        requireActive();
        if (rowLoaded || callbackActive) throw new AssertionError("input phase is complete");
        requireKind(leaf, kind);
    }

    private void requireRead(int leaf, byte kind) {
        requireActive();
        if (!rowLoaded) throw new AssertionError("row has not been loaded");
        requireKind(leaf, kind);
    }

    private void requireView(int leaf, byte kind) {
        requireActive();
        if (!callbackActive || !rowLoaded) {
            throw SomaFailures.failure(
                    io.github.somaruntime.soma.SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                    SomaOperation.UPDATE,
                    "borrowed View is outside its callback scope",
                    operationLease.provenance());
        }
        requireKind(leaf, kind);
    }

    private void requireEdit(int leaf, byte kind) {
        requireView(leaf, kind);
        if (layout.keyLeaf(leaf)) {
            throw SomaFailures.invalid(SomaOperation.UPDATE, "Key Field is immutable");
        }
    }

    private void requireKind(int leaf, byte kind) {
        if (layout.leafKind(leaf) != kind) throw new AssertionError("generated leaf type drift");
    }

    private void requireMode(int expected) {
        requireActive();
        if (mode != expected) throw new AssertionError("generated row operation kind drift");
    }

    private void requireActive() {
        if (operationLease == null || operationThread != Thread.currentThread()) {
            throw SomaFailures.failure(
                    io.github.somaruntime.soma.SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                    SomaOperation.UPDATE,
                    "generated row is outside its operation scope",
                    new Object());
        }
    }
}
