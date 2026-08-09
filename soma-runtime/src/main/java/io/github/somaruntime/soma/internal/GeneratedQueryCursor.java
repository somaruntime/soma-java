package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import java.util.IdentityHashMap;

/** Reusable operation-scoped cursor behind one generated query View. */
public final class GeneratedQueryCursor extends TypedValues implements GeneratedRowAccess {

    private final GeneratedTableLayout layout;
    private final IdentityHashMap<Object, Boolean> borrowedViews =
            new IdentityHashMap<Object, Boolean>();
    private TableStateRoot root;
    private SomaOperation operation;
    private Object provenance;
    private Thread participant;
    private boolean accessActive;

    GeneratedQueryCursor(GeneratedTableLayout layout) {
        super(layout);
        this.layout = layout;
    }

    @Override
    public void registerBorrowedView(Object view) {
        if (view == null || root != null) {
            throw new AssertionError("invalid borrowed View registration");
        }
        borrowedViews.put(view, Boolean.TRUE);
    }

    boolean ownsBorrowedView(Object view) {
        return borrowedViews.containsKey(view);
    }

    void begin(
            TableStateRoot boundRoot,
            SomaOperation operationKind,
            Object operationProvenance) {
        if (root != null || boundRoot == null || operationKind == null
                || operationProvenance == null) {
            throw new AssertionError("query cursor operation overlap");
        }
        root = boundRoot;
        operation = operationKind;
        provenance = operationProvenance;
        participant = Thread.currentThread();
        accessActive = false;
        clearReferences();
    }

    void enter(long locator) {
        requireOperation();
        if (accessActive || locator < 0L || locator >= root.size) {
            throw new AssertionError("invalid query cursor locator");
        }
        root.directory.read(locator, this);
        accessActive = true;
    }

    void leave() {
        requireOperation();
        if (!accessActive) throw new AssertionError("query cursor access is not active");
        accessActive = false;
        clearReferences();
    }

    void end() {
        if (root == null) return;
        accessActive = false;
        clearReferences();
        root = null;
        operation = null;
        provenance = null;
        participant = null;
    }

    @Override
    public boolean viewBoolean(int leaf) {
        requireView(leaf, GeneratedTableLayout.BOOLEAN);
        return booleanValue(layout.leafSlot(leaf));
    }

    @Override
    public byte viewByte(int leaf) {
        requireView(leaf, GeneratedTableLayout.BYTE);
        return byteValue(layout.leafSlot(leaf));
    }

    @Override
    public short viewShort(int leaf) {
        requireView(leaf, GeneratedTableLayout.SHORT);
        return shortValue(layout.leafSlot(leaf));
    }

    @Override
    public char viewChar(int leaf) {
        requireView(leaf, GeneratedTableLayout.CHAR);
        return charValue(layout.leafSlot(leaf));
    }

    @Override
    public int viewInt(int leaf) {
        requireView(leaf, GeneratedTableLayout.INT);
        return intValue(layout.leafSlot(leaf));
    }

    @Override
    public long viewLong(int leaf) {
        requireView(leaf, GeneratedTableLayout.LONG);
        return longValue(layout.leafSlot(leaf));
    }

    @Override
    public float viewFloat(int leaf) {
        requireView(leaf, GeneratedTableLayout.FLOAT);
        return floatValue(layout.leafSlot(leaf));
    }

    @Override
    public double viewDouble(int leaf) {
        requireView(leaf, GeneratedTableLayout.DOUBLE);
        return doubleValue(layout.leafSlot(leaf));
    }

    @Override
    public Object viewReference(int leaf) {
        requireView(leaf, GeneratedTableLayout.REFERENCE);
        return reference(layout.leafSlot(leaf));
    }

    private void requireView(int leaf, byte kind) {
        requireOperation();
        if (!accessActive) {
            throw SomaFailures.failure(
                    SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                    operation,
                    "borrowed View is outside its callback scope",
                    provenance);
        }
        if (layout.leafKind(leaf) != kind) {
            throw new AssertionError("generated leaf type drift");
        }
    }

    private void requireOperation() {
        if (root == null || participant != Thread.currentThread()) {
            throw SomaFailures.failure(
                    SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                    operation == null ? SomaOperation.QUERY : operation,
                    "borrowed View is outside its query operation",
                    provenance == null ? new Object() : provenance);
        }
    }
}
