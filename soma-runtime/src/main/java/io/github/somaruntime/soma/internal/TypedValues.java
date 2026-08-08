package io.github.somaruntime.soma.internal;

import java.util.Arrays;

/** Exact primitive/reference staging storage; it never boxes primitive leaves. */
class TypedValues {

    private final boolean[] booleans;
    private final byte[] bytes;
    private final short[] shorts;
    private final char[] chars;
    private final int[] ints;
    private final long[] longs;
    private final float[] floats;
    private final double[] doubles;
    private final Object[] references;

    TypedValues(GeneratedTableLayout layout) {
        booleans = new boolean[layout.kindCount(GeneratedTableLayout.BOOLEAN)];
        bytes = new byte[layout.kindCount(GeneratedTableLayout.BYTE)];
        shorts = new short[layout.kindCount(GeneratedTableLayout.SHORT)];
        chars = new char[layout.kindCount(GeneratedTableLayout.CHAR)];
        ints = new int[layout.kindCount(GeneratedTableLayout.INT)];
        longs = new long[layout.kindCount(GeneratedTableLayout.LONG)];
        floats = new float[layout.kindCount(GeneratedTableLayout.FLOAT)];
        doubles = new double[layout.kindCount(GeneratedTableLayout.DOUBLE)];
        references = new Object[layout.kindCount(GeneratedTableLayout.REFERENCE)];
    }

    final void copyFrom(TypedValues source) {
        System.arraycopy(source.booleans, 0, booleans, 0, booleans.length);
        System.arraycopy(source.bytes, 0, bytes, 0, bytes.length);
        System.arraycopy(source.shorts, 0, shorts, 0, shorts.length);
        System.arraycopy(source.chars, 0, chars, 0, chars.length);
        System.arraycopy(source.ints, 0, ints, 0, ints.length);
        System.arraycopy(source.longs, 0, longs, 0, longs.length);
        System.arraycopy(source.floats, 0, floats, 0, floats.length);
        System.arraycopy(source.doubles, 0, doubles, 0, doubles.length);
        System.arraycopy(source.references, 0, references, 0, references.length);
    }

    final void clearReferences() {
        Arrays.fill(references, null);
    }

    final boolean booleanValue(int slot) { return booleans[slot]; }
    final byte byteValue(int slot) { return bytes[slot]; }
    final short shortValue(int slot) { return shorts[slot]; }
    final char charValue(int slot) { return chars[slot]; }
    final int intValue(int slot) { return ints[slot]; }
    final long longValue(int slot) { return longs[slot]; }
    final float floatValue(int slot) { return floats[slot]; }
    final double doubleValue(int slot) { return doubles[slot]; }
    final Object reference(int slot) { return references[slot]; }

    final void booleanValue(int slot, boolean value) { booleans[slot] = value; }
    final void byteValue(int slot, byte value) { bytes[slot] = value; }
    final void shortValue(int slot, short value) { shorts[slot] = value; }
    final void charValue(int slot, char value) { chars[slot] = value; }
    final void intValue(int slot, int value) { ints[slot] = value; }
    final void longValue(int slot, long value) { longs[slot] = value; }
    final void floatValue(int slot, float value) { floats[slot] = value; }
    final void doubleValue(int slot, double value) { doubles[slot] = value; }
    final void reference(int slot, Object value) { references[slot] = value; }
}
