package io.github.somaruntime.soma.internal;

/** Immutable, field-width canonical leaf snapshot with unboxed primitive storage. */
final class TypedLiteral {

    private final GeneratedTableLayout layout;
    private final CanonicalTableIdentity tableIdentity;
    private final int fieldIndex;
    private final int leafStart;
    private final int[] localSlots;
    private final boolean[] booleans;
    private final byte[] bytes;
    private final short[] shorts;
    private final char[] chars;
    private final int[] ints;
    private final long[] longs;
    private final float[] floats;
    private final double[] doubles;
    private final Object[] references;

    TypedLiteral(
            GeneratedTableLayout layout,
            CanonicalTableIdentity tableIdentity,
            int fieldIndex,
            TypedValues source) {
        this(layout, tableIdentity, fieldIndex, source, true);
    }

    private TypedLiteral(
            GeneratedTableLayout layout,
            CanonicalTableIdentity tableIdentity,
            int fieldIndex,
            TypedValues source,
            boolean copySource) {
        if (tableIdentity == null || tableIdentity.descriptor() != layout
                || copySource && source == null) {
            throw new AssertionError("typed literal identity/source is invalid");
        }
        this.layout = layout;
        this.tableIdentity = tableIdentity;
        this.fieldIndex = fieldIndex;
        this.leafStart = layout.fieldStart(fieldIndex);
        int leafCount = layout.fieldLeafCount(fieldIndex);
        this.localSlots = new int[leafCount];
        int[] counts = new int[GeneratedTableLayout.REFERENCE + 1];
        for (int offset = 0; offset < leafCount; offset++) {
            byte kind = layout.leafKind(leafStart + offset);
            localSlots[offset] = counts[kind]++;
        }
        booleans = new boolean[counts[GeneratedTableLayout.BOOLEAN]];
        bytes = new byte[counts[GeneratedTableLayout.BYTE]];
        shorts = new short[counts[GeneratedTableLayout.SHORT]];
        chars = new char[counts[GeneratedTableLayout.CHAR]];
        ints = new int[counts[GeneratedTableLayout.INT]];
        longs = new long[counts[GeneratedTableLayout.LONG]];
        floats = new float[counts[GeneratedTableLayout.FLOAT]];
        doubles = new double[counts[GeneratedTableLayout.DOUBLE]];
        references = new Object[counts[GeneratedTableLayout.REFERENCE]];
        if (!copySource) return;
        for (int offset = 0; offset < leafCount; offset++) {
            int leaf = leafStart + offset;
            int sourceSlot = layout.leafSlot(leaf);
            int local = localSlots[offset];
            switch (layout.leafKind(leaf)) {
                case GeneratedTableLayout.BOOLEAN:
                    booleans[local] = source.booleanValue(sourceSlot); break;
                case GeneratedTableLayout.BYTE:
                    bytes[local] = source.byteValue(sourceSlot); break;
                case GeneratedTableLayout.SHORT:
                    shorts[local] = source.shortValue(sourceSlot); break;
                case GeneratedTableLayout.CHAR:
                    chars[local] = source.charValue(sourceSlot); break;
                case GeneratedTableLayout.INT:
                    ints[local] = source.intValue(sourceSlot); break;
                case GeneratedTableLayout.LONG:
                    longs[local] = source.longValue(sourceSlot); break;
                case GeneratedTableLayout.FLOAT:
                    floats[local] = source.floatValue(sourceSlot); break;
                case GeneratedTableLayout.DOUBLE:
                    doubles[local] = source.doubleValue(sourceSlot); break;
                case GeneratedTableLayout.REFERENCE:
                    references[local] = source.reference(sourceSlot); break;
                default:
                    throw new AssertionError("unknown literal leaf kind");
            }
        }
    }

    static TypedLiteral nullReference(
            GeneratedTableLayout layout,
            CanonicalTableIdentity tableIdentity,
            int fieldIndex) {
        return new TypedLiteral(layout, tableIdentity, fieldIndex, null, false);
    }

    int fieldIndex() { return fieldIndex; }
    GeneratedTableLayout layout() { return layout; }
    CanonicalTableIdentity tableIdentity() { return tableIdentity; }
    int leafCountForTesting() { return localSlots.length; }

    boolean booleanValue(int leaf) { return booleans[local(leaf)]; }
    byte byteValue(int leaf) { return bytes[local(leaf)]; }
    short shortValue(int leaf) { return shorts[local(leaf)]; }
    char charValue(int leaf) { return chars[local(leaf)]; }
    int intValue(int leaf) { return ints[local(leaf)]; }
    long longValue(int leaf) { return longs[local(leaf)]; }
    float floatValue(int leaf) { return floats[local(leaf)]; }
    double doubleValue(int leaf) { return doubles[local(leaf)]; }
    Object referenceValue(int leaf) { return references[local(leaf)]; }

    private int local(int leaf) {
        int offset = leaf - leafStart;
        if (offset < 0 || offset >= localSlots.length) {
            throw new AssertionError("literal leaf outside logical Field");
        }
        return localSlots[offset];
    }
}
