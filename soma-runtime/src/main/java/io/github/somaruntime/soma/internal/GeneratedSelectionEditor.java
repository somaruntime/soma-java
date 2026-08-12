package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;

/** Reusable callback-scoped staging cursor for one sequential Selection update. */
final class GeneratedSelectionEditor extends TypedValues
        implements GeneratedEditorAccess {

    private final GeneratedTableLayout layout;
    private final TypedValues original;
    private TableStateRoot root;
    private Object provenance;
    private Thread participant;
    private boolean active;

    GeneratedSelectionEditor(GeneratedTableLayout layout) {
        super(layout);
        this.layout = layout;
        this.original = new TypedValues(layout);
    }

    @Override
    public void registerBorrowedView(Object view) {
        if (view == null || root != null) {
            throw new AssertionError("invalid Selection Editor registration");
        }
    }

    void begin(TableStateRoot boundRoot, Object operationProvenance) {
        if (root != null || boundRoot == null || operationProvenance == null) {
            throw new AssertionError("Selection Editor operation overlap");
        }
        root = boundRoot;
        provenance = operationProvenance;
        participant = Thread.currentThread();
    }

    void enter(int locator) {
        requireOperation();
        if (active || locator < 0 || locator >= root.size) {
            throw new AssertionError("invalid Selection Editor locator");
        }
        root.directory.read(locator, this);
        original.copyFrom(this);
        active = true;
    }

    TypedValues originalValues() {
        requireActive();
        return original;
    }

    void leave() {
        requireActive();
        active = false;
        clearReferences();
        original.clearReferences();
    }

    void end() {
        if (root == null) return;
        active = false;
        clearReferences();
        original.clearReferences();
        root = null;
        provenance = null;
        participant = null;
    }

    RuntimeException callbackFailure(Exception failure) {
        requireOperation();
        return SomaFailures.callbackFailure(
                SomaOperation.UPDATE, failure, provenance);
    }

    @Override
    public void requireArgument(
            Object value,
            SomaOperation operation,
            String category) {
        if (value == null) {
            throw SomaFailures.invalid(operation, category + " is null");
        }
    }

    @Override public boolean viewBoolean(int leaf) { requireView(leaf, GeneratedTableLayout.BOOLEAN); return booleanValue(layout.leafSlot(leaf)); }
    @Override public byte viewByte(int leaf) { requireView(leaf, GeneratedTableLayout.BYTE); return byteValue(layout.leafSlot(leaf)); }
    @Override public short viewShort(int leaf) { requireView(leaf, GeneratedTableLayout.SHORT); return shortValue(layout.leafSlot(leaf)); }
    @Override public char viewChar(int leaf) { requireView(leaf, GeneratedTableLayout.CHAR); return charValue(layout.leafSlot(leaf)); }
    @Override public int viewInt(int leaf) { requireView(leaf, GeneratedTableLayout.INT); return intValue(layout.leafSlot(leaf)); }
    @Override public long viewLong(int leaf) { requireView(leaf, GeneratedTableLayout.LONG); return longValue(layout.leafSlot(leaf)); }
    @Override public float viewFloat(int leaf) { requireView(leaf, GeneratedTableLayout.FLOAT); return floatValue(layout.leafSlot(leaf)); }
    @Override public double viewDouble(int leaf) { requireView(leaf, GeneratedTableLayout.DOUBLE); return doubleValue(layout.leafSlot(leaf)); }
    @Override public Object viewReference(int leaf) { requireView(leaf, GeneratedTableLayout.REFERENCE); return reference(layout.leafSlot(leaf)); }

    @Override public void editBoolean(int leaf, boolean value) { requireEdit(leaf, GeneratedTableLayout.BOOLEAN); booleanValue(layout.leafSlot(leaf), value); }
    @Override public void editByte(int leaf, byte value) { requireEdit(leaf, GeneratedTableLayout.BYTE); byteValue(layout.leafSlot(leaf), value); }
    @Override public void editShort(int leaf, short value) { requireEdit(leaf, GeneratedTableLayout.SHORT); shortValue(layout.leafSlot(leaf), value); }
    @Override public void editChar(int leaf, char value) { requireEdit(leaf, GeneratedTableLayout.CHAR); charValue(layout.leafSlot(leaf), value); }
    @Override public void editInt(int leaf, int value) { requireEdit(leaf, GeneratedTableLayout.INT); intValue(layout.leafSlot(leaf), value); }
    @Override public void editLong(int leaf, long value) { requireEdit(leaf, GeneratedTableLayout.LONG); longValue(layout.leafSlot(leaf), value); }
    @Override public void editFloat(int leaf, float value) { requireEdit(leaf, GeneratedTableLayout.FLOAT); floatValue(layout.leafSlot(leaf), value); }
    @Override public void editDouble(int leaf, double value) { requireEdit(leaf, GeneratedTableLayout.DOUBLE); doubleValue(layout.leafSlot(leaf), value); }
    @Override public void editReference(int leaf, Object value) { requireEdit(leaf, GeneratedTableLayout.REFERENCE); reference(layout.leafSlot(leaf), value); }

    private void requireView(int leaf, byte kind) {
        requireActive();
        if (layout.leafKind(leaf) != kind) {
            throw new AssertionError("generated leaf type drift");
        }
    }

    private void requireEdit(int leaf, byte kind) {
        requireView(leaf, kind);
        if (layout.keyLeaf(leaf)) {
            throw SomaFailures.invalid(SomaOperation.UPDATE, "Key Field is immutable");
        }
    }

    private void requireActive() {
        requireOperation();
        if (!active) {
            throw SomaFailures.failure(
                    SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                    SomaOperation.UPDATE,
                    "borrowed Editor is outside its callback scope",
                    provenance);
        }
    }

    private void requireOperation() {
        if (root == null || participant != Thread.currentThread()) {
            throw SomaFailures.failure(
                    SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                    SomaOperation.UPDATE,
                    "borrowed Editor is outside its update operation",
                    provenance == null ? new Object() : provenance);
        }
    }
}
