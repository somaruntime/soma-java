package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;

/** Detached exact-typed literal/Index probe issued by one generated Table. */
public final class GeneratedProbe extends TypedValues {

    private final GeneratedTable owner;
    private final GeneratedTableLayout layout;
    private final int fieldIndex;
    private boolean sealed;

    GeneratedProbe(GeneratedTable owner, GeneratedTableLayout layout, int fieldIndex) {
        super(layout);
        this.owner = owner;
        this.layout = layout;
        this.fieldIndex = fieldIndex;
    }

    public void putBoolean(int leaf, boolean value) { require(leaf, GeneratedTableLayout.BOOLEAN); booleanValue(layout.leafSlot(leaf), value); }
    public void putByte(int leaf, byte value) { require(leaf, GeneratedTableLayout.BYTE); byteValue(layout.leafSlot(leaf), value); }
    public void putShort(int leaf, short value) { require(leaf, GeneratedTableLayout.SHORT); shortValue(layout.leafSlot(leaf), value); }
    public void putChar(int leaf, char value) { require(leaf, GeneratedTableLayout.CHAR); charValue(layout.leafSlot(leaf), value); }
    public void putInt(int leaf, int value) { require(leaf, GeneratedTableLayout.INT); intValue(layout.leafSlot(leaf), value); }
    public void putLong(int leaf, long value) { require(leaf, GeneratedTableLayout.LONG); longValue(layout.leafSlot(leaf), value); }
    public void putFloat(int leaf, float value) { require(leaf, GeneratedTableLayout.FLOAT); floatValue(layout.leafSlot(leaf), value); }
    public void putDouble(int leaf, double value) { require(leaf, GeneratedTableLayout.DOUBLE); doubleValue(layout.leafSlot(leaf), value); }
    public void putReference(int leaf, Object value) { require(leaf, GeneratedTableLayout.REFERENCE); reference(layout.leafSlot(leaf), value); }

    public void requireArgument(Object value, SomaOperation operation, String category) {
        owner.requireArgument(value, operation, category);
    }

    public GeneratedProbe seal() {
        sealed = true;
        return this;
    }

    GeneratedTable owner() { return owner; }
    int fieldIndex() { return fieldIndex; }

    void requireSealed(GeneratedTable expected, int expectedField) {
        if (!sealed || owner != expected || fieldIndex != expectedField) {
            throw SomaFailures.invalid(
                    io.github.somaruntime.soma.SomaOperation.QUERY,
                    "Field literal/probe belongs to another generated endpoint");
        }
    }

    private void require(int leaf, byte kind) {
        if (sealed
                || leaf < layout.fieldStart(fieldIndex)
                || leaf >= layout.fieldStart(fieldIndex) + layout.fieldLeafCount(fieldIndex)
                || layout.leafKind(leaf) != kind) {
            throw new AssertionError("generated probe leaf drift");
        }
    }
}
