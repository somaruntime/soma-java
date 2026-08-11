package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import java.util.IdentityHashMap;

/** Reusable operation-scoped cursor behind one generated query View. */
public final class GeneratedQueryCursor implements GeneratedRowAccess {

    private final GeneratedTableLayout layout;
    private final IdentityHashMap<Object, Boolean> borrowedViews =
            new IdentityHashMap<Object, Boolean>();
    private TableStateRoot root;
    private SomaOperation operation;
    private Object provenance;
    private Thread participant;
    private TableChunk chunk;
    private int offset;
    private boolean accessActive;

    GeneratedQueryCursor(GeneratedTableLayout layout) {
        this.layout = layout;
        this.offset = -1;
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
        chunk = null;
        offset = -1;
        accessActive = false;
    }

    void enter(int locator) {
        requireOperation();
        if (accessActive || locator < 0 || locator >= root.size) {
            throw new AssertionError("invalid query cursor locator");
        }
        TableChunkDirectory directory = root.directory;
        chunk = directory.chunk(locator / directory.chunkRows());
        offset = locator % directory.chunkRows();
        accessActive = true;
    }

    void leave() {
        requireOperation();
        if (!accessActive) throw new AssertionError("query cursor access is not active");
        accessActive = false;
        chunk = null;
        offset = -1;
    }

    void end() {
        if (root == null) return;
        accessActive = false;
        chunk = null;
        offset = -1;
        root = null;
        operation = null;
        provenance = null;
        participant = null;
    }

    @Override
    public boolean viewBoolean(int leaf) {
        requireView(leaf, GeneratedTableLayout.BOOLEAN);
        return chunk.booleanValue(layout.leafSlot(leaf), offset);
    }

    @Override
    public byte viewByte(int leaf) {
        requireView(leaf, GeneratedTableLayout.BYTE);
        return chunk.byteValue(layout.leafSlot(leaf), offset);
    }

    @Override
    public short viewShort(int leaf) {
        requireView(leaf, GeneratedTableLayout.SHORT);
        return chunk.shortValue(layout.leafSlot(leaf), offset);
    }

    @Override
    public char viewChar(int leaf) {
        requireView(leaf, GeneratedTableLayout.CHAR);
        return chunk.charValue(layout.leafSlot(leaf), offset);
    }

    @Override
    public int viewInt(int leaf) {
        requireView(leaf, GeneratedTableLayout.INT);
        return chunk.intValue(layout.leafSlot(leaf), offset);
    }

    @Override
    public long viewLong(int leaf) {
        requireView(leaf, GeneratedTableLayout.LONG);
        return chunk.longValue(layout.leafSlot(leaf), offset);
    }

    @Override
    public float viewFloat(int leaf) {
        requireView(leaf, GeneratedTableLayout.FLOAT);
        return chunk.floatValue(layout.leafSlot(leaf), offset);
    }

    @Override
    public double viewDouble(int leaf) {
        requireView(leaf, GeneratedTableLayout.DOUBLE);
        return chunk.doubleValue(layout.leafSlot(leaf), offset);
    }

    @Override
    public Object viewReference(int leaf) {
        requireView(leaf, GeneratedTableLayout.REFERENCE);
        return chunk.referenceValue(layout.leafSlot(leaf), offset);
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
