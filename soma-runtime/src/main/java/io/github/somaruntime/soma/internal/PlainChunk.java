package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaOperation;

/** One PLAIN chunk with one exact Java array per physical leaf. */
final class PlainChunk implements TableChunk {

    private static final long ARRAY_HEADER_BYTES = 32L;

    private final boolean[][] booleans;
    private final byte[][] bytes;
    private final short[][] shorts;
    private final char[][] chars;
    private final int[][] ints;
    private final long[][] longs;
    private final float[][] floats;
    private final double[][] doubles;
    private final Object[][] references;

    PlainChunk(GeneratedTableLayout layout, int rows) {
        booleans = booleanArrays(layout.kindCount(GeneratedTableLayout.BOOLEAN), rows);
        bytes = byteArrays(layout.kindCount(GeneratedTableLayout.BYTE), rows);
        shorts = shortArrays(layout.kindCount(GeneratedTableLayout.SHORT), rows);
        chars = charArrays(layout.kindCount(GeneratedTableLayout.CHAR), rows);
        ints = intArrays(layout.kindCount(GeneratedTableLayout.INT), rows);
        longs = longArrays(layout.kindCount(GeneratedTableLayout.LONG), rows);
        floats = floatArrays(layout.kindCount(GeneratedTableLayout.FLOAT), rows);
        doubles = doubleArrays(layout.kindCount(GeneratedTableLayout.DOUBLE), rows);
        references = referenceArrays(layout.kindCount(GeneratedTableLayout.REFERENCE), rows);
    }

    private PlainChunk(
            boolean[][] booleans,
            byte[][] bytes,
            short[][] shorts,
            char[][] chars,
            int[][] ints,
            long[][] longs,
            float[][] floats,
            double[][] doubles,
            Object[][] references) {
        this.booleans = booleans;
        this.bytes = bytes;
        this.shorts = shorts;
        this.chars = chars;
        this.ints = ints;
        this.longs = longs;
        this.floats = floats;
        this.doubles = doubles;
        this.references = references;
    }

    PlainChunk copy() {
        return new PlainChunk(
                clone(booleans), clone(bytes), clone(shorts), clone(chars),
                clone(ints), clone(longs), clone(floats), clone(doubles),
                clone(references));
    }

    @Override public ChunkRepresentation representation() {
        return ChunkRepresentation.PLAIN;
    }

    @Override public boolean booleanValue(int slot, int offset) {
        return booleans[slot][offset];
    }

    @Override public byte byteValue(int slot, int offset) {
        return bytes[slot][offset];
    }

    @Override public short shortValue(int slot, int offset) {
        return shorts[slot][offset];
    }

    @Override public char charValue(int slot, int offset) {
        return chars[slot][offset];
    }

    @Override public int intValue(int slot, int offset) {
        return ints[slot][offset];
    }

    @Override public long longValue(int slot, int offset) {
        return longs[slot][offset];
    }

    @Override public float floatValue(int slot, int offset) {
        return floats[slot][offset];
    }

    @Override public double doubleValue(int slot, int offset) {
        return doubles[slot][offset];
    }

    @Override public Object referenceValue(int slot, int offset) {
        return references[slot][offset];
    }

    @Override public boolean visitPrimitive(
            byte kind,
            int slot,
            int logicalRows,
            PrimitiveVisitor visitor) {
        switch (kind) {
            case GeneratedTableLayout.BOOLEAN: {
                boolean[] values = booleans[slot];
                for (int i = 0; i < logicalRows; i++) {
                    if (!visitor.visit(values[i] ? 1L : 0L)) return false;
                }
                return true;
            }
            case GeneratedTableLayout.BYTE: {
                byte[] values = bytes[slot];
                for (int i = 0; i < logicalRows; i++) {
                    if (!visitor.visit(values[i])) return false;
                }
                return true;
            }
            case GeneratedTableLayout.SHORT: {
                short[] values = shorts[slot];
                for (int i = 0; i < logicalRows; i++) {
                    if (!visitor.visit(values[i])) return false;
                }
                return true;
            }
            case GeneratedTableLayout.CHAR: {
                char[] values = chars[slot];
                for (int i = 0; i < logicalRows; i++) {
                    if (!visitor.visit(values[i])) return false;
                }
                return true;
            }
            case GeneratedTableLayout.INT: {
                int[] values = ints[slot];
                for (int i = 0; i < logicalRows; i++) {
                    if (!visitor.visit(values[i])) return false;
                }
                return true;
            }
            case GeneratedTableLayout.LONG: {
                long[] values = longs[slot];
                for (int i = 0; i < logicalRows; i++) {
                    if (!visitor.visit(values[i])) return false;
                }
                return true;
            }
            case GeneratedTableLayout.FLOAT: {
                float[] values = floats[slot];
                for (int i = 0; i < logicalRows; i++) {
                    if (!visitor.visit(Float.floatToIntBits(values[i]))) return false;
                }
                return true;
            }
            case GeneratedTableLayout.DOUBLE: {
                double[] values = doubles[slot];
                for (int i = 0; i < logicalRows; i++) {
                    if (!visitor.visit(Double.doubleToLongBits(values[i]))) return false;
                }
                return true;
            }
            default:
                throw new AssertionError("not a primitive leaf kind");
        }
    }

    @Override public void read(
            int offset,
            TypedValues destination,
            GeneratedTableLayout layout) {
        for (int slot = 0; slot < booleans.length; slot++) {
            destination.booleanValue(slot, booleans[slot][offset]);
        }
        for (int slot = 0; slot < bytes.length; slot++) {
            destination.byteValue(slot, bytes[slot][offset]);
        }
        for (int slot = 0; slot < shorts.length; slot++) {
            destination.shortValue(slot, shorts[slot][offset]);
        }
        for (int slot = 0; slot < chars.length; slot++) {
            destination.charValue(slot, chars[slot][offset]);
        }
        for (int slot = 0; slot < ints.length; slot++) {
            destination.intValue(slot, ints[slot][offset]);
        }
        for (int slot = 0; slot < longs.length; slot++) {
            destination.longValue(slot, longs[slot][offset]);
        }
        for (int slot = 0; slot < floats.length; slot++) {
            destination.floatValue(slot, floats[slot][offset]);
        }
        for (int slot = 0; slot < doubles.length; slot++) {
            destination.doubleValue(slot, doubles[slot][offset]);
        }
        for (int slot = 0; slot < references.length; slot++) {
            destination.reference(slot, references[slot][offset]);
        }
    }

    @Override public TableChunk mutableCopy(GeneratedTableLayout layout) {
        return copy();
    }

    @Override public void write(
            int offset,
            TypedValues source,
            GeneratedTableLayout layout) {
        for (int slot = 0; slot < booleans.length; slot++) {
            booleans[slot][offset] = source.booleanValue(slot);
        }
        for (int slot = 0; slot < bytes.length; slot++) {
            bytes[slot][offset] = source.byteValue(slot);
        }
        for (int slot = 0; slot < shorts.length; slot++) {
            shorts[slot][offset] = source.shortValue(slot);
        }
        for (int slot = 0; slot < chars.length; slot++) {
            chars[slot][offset] = source.charValue(slot);
        }
        for (int slot = 0; slot < ints.length; slot++) {
            ints[slot][offset] = source.intValue(slot);
        }
        for (int slot = 0; slot < longs.length; slot++) {
            longs[slot][offset] = source.longValue(slot);
        }
        for (int slot = 0; slot < floats.length; slot++) {
            floats[slot][offset] = source.floatValue(slot);
        }
        for (int slot = 0; slot < doubles.length; slot++) {
            doubles[slot][offset] = source.doubleValue(slot);
        }
        for (int slot = 0; slot < references.length; slot++) {
            references[slot][offset] = source.reference(slot);
        }
    }

    @Override public void clear(int offset, GeneratedTableLayout layout) {
        for (int slot = 0; slot < booleans.length; slot++) booleans[slot][offset] = false;
        for (int slot = 0; slot < bytes.length; slot++) bytes[slot][offset] = 0;
        for (int slot = 0; slot < shorts.length; slot++) shorts[slot][offset] = 0;
        for (int slot = 0; slot < chars.length; slot++) chars[slot][offset] = 0;
        for (int slot = 0; slot < ints.length; slot++) ints[slot][offset] = 0;
        for (int slot = 0; slot < longs.length; slot++) longs[slot][offset] = 0L;
        for (int slot = 0; slot < floats.length; slot++) floats[slot][offset] = 0.0f;
        for (int slot = 0; slot < doubles.length; slot++) doubles[slot][offset] = 0.0d;
        for (int slot = 0; slot < references.length; slot++) references[slot][offset] = null;
    }

    /** Allocation-free final-commit write of one already validated leaf column. */
    void writeSelectionValue(
            int offset,
            byte kind,
            int slot,
            Object source,
            int position) {
        switch (kind) {
            case GeneratedTableLayout.BOOLEAN:
                booleans[slot][offset] = ((boolean[]) source)[position];
                break;
            case GeneratedTableLayout.BYTE:
                bytes[slot][offset] = ((byte[]) source)[position];
                break;
            case GeneratedTableLayout.SHORT:
                shorts[slot][offset] = ((short[]) source)[position];
                break;
            case GeneratedTableLayout.CHAR:
                chars[slot][offset] = ((char[]) source)[position];
                break;
            case GeneratedTableLayout.INT:
                ints[slot][offset] = ((int[]) source)[position];
                break;
            case GeneratedTableLayout.LONG:
                longs[slot][offset] = ((long[]) source)[position];
                break;
            case GeneratedTableLayout.FLOAT:
                floats[slot][offset] = ((float[]) source)[position];
                break;
            case GeneratedTableLayout.DOUBLE:
                doubles[slot][offset] = ((double[]) source)[position];
                break;
            case GeneratedTableLayout.REFERENCE:
                references[slot][offset] = ((Object[]) source)[position];
                break;
            default:
                throw new AssertionError("unknown Selection write-set leaf kind");
        }
    }

    /** Final-commit row move between already validated PLAIN chunks. */
    void copyRowFrom(PlainChunk source, int sourceOffset, int targetOffset) {
        for (int slot = 0; slot < booleans.length; slot++) {
            booleans[slot][targetOffset] = source.booleans[slot][sourceOffset];
        }
        for (int slot = 0; slot < bytes.length; slot++) {
            bytes[slot][targetOffset] = source.bytes[slot][sourceOffset];
        }
        for (int slot = 0; slot < shorts.length; slot++) {
            shorts[slot][targetOffset] = source.shorts[slot][sourceOffset];
        }
        for (int slot = 0; slot < chars.length; slot++) {
            chars[slot][targetOffset] = source.chars[slot][sourceOffset];
        }
        for (int slot = 0; slot < ints.length; slot++) {
            ints[slot][targetOffset] = source.ints[slot][sourceOffset];
        }
        for (int slot = 0; slot < longs.length; slot++) {
            longs[slot][targetOffset] = source.longs[slot][sourceOffset];
        }
        for (int slot = 0; slot < floats.length; slot++) {
            floats[slot][targetOffset] = source.floats[slot][sourceOffset];
        }
        for (int slot = 0; slot < doubles.length; slot++) {
            doubles[slot][targetOffset] = source.doubles[slot][sourceOffset];
        }
        for (int slot = 0; slot < references.length; slot++) {
            references[slot][targetOffset] = source.references[slot][sourceOffset];
        }
    }

    @Override public PlainChunk materialize(GeneratedTableLayout layout) {
        return this;
    }

    @Override public TableChunk finish(
            int logicalRows,
            SomaCompression policy,
            GeneratedTableLayout layout,
            SomaOperation operation,
            Object provenance) {
        return ChunkEncoder.finish(
                this, logicalRows, policy, layout, operation, provenance);
    }

    @Override public long managedBytes(
            GeneratedTableLayout layout,
            SomaOperation operation,
            Object provenance) {
        return ChunkEncoder.plainManagedBytes(
                layout, booleans.length == 0
                        ? firstLength() : booleans[0].length,
                operation, provenance);
    }

    @Override public boolean hasEncodedRepresentation() {
        return false;
    }

    @Override public long plainEquivalentBytesForField(
            GeneratedTableLayout layout,
            int fieldIndex,
            SomaOperation operation,
            Object provenance) {
        return fieldBytes(layout, fieldIndex, operation, provenance);
    }

    @Override public long representationBytesForField(
            GeneratedTableLayout layout,
            int fieldIndex,
            SomaOperation operation,
            Object provenance) {
        return fieldBytes(layout, fieldIndex, operation, provenance);
    }

    int rowCapacity() {
        return firstLength();
    }

    private long fieldBytes(
            GeneratedTableLayout layout,
            int fieldIndex,
            SomaOperation operation,
            Object provenance) {
        long payload = CheckedLong.multiply(
                rowCapacity(),
                layout.fieldWidthBytes(fieldIndex),
                operation,
                provenance);
        long arrays = CheckedLong.multiply(
                layout.fieldLeafCount(fieldIndex),
                ARRAY_HEADER_BYTES,
                operation,
                provenance);
        return CheckedLong.add(payload, arrays, operation, provenance);
    }

    private int firstLength() {
        if (booleans.length != 0) return booleans[0].length;
        if (bytes.length != 0) return bytes[0].length;
        if (shorts.length != 0) return shorts[0].length;
        if (chars.length != 0) return chars[0].length;
        if (ints.length != 0) return ints[0].length;
        if (longs.length != 0) return longs[0].length;
        if (floats.length != 0) return floats[0].length;
        if (doubles.length != 0) return doubles[0].length;
        return references[0].length;
    }

    boolean[] booleans(int slot) { return booleans[slot]; }
    byte[] bytes(int slot) { return bytes[slot]; }
    short[] shorts(int slot) { return shorts[slot]; }
    char[] chars(int slot) { return chars[slot]; }
    int[] ints(int slot) { return ints[slot]; }
    long[] longs(int slot) { return longs[slot]; }
    float[] floats(int slot) { return floats[slot]; }
    double[] doubles(int slot) { return doubles[slot]; }
    Object[] references(int slot) { return references[slot]; }

    int booleanCount() { return booleans.length; }
    int byteCount() { return bytes.length; }
    int shortCount() { return shorts.length; }
    int charCount() { return chars.length; }
    int intCount() { return ints.length; }
    int longCount() { return longs.length; }
    int floatCount() { return floats.length; }
    int doubleCount() { return doubles.length; }
    int referenceCount() { return references.length; }

    private static boolean[][] booleanArrays(int count, int rows) {
        boolean[][] result = new boolean[count][];
        for (int index = 0; index < count; index++) result[index] = new boolean[rows];
        return result;
    }

    private static byte[][] byteArrays(int count, int rows) {
        byte[][] result = new byte[count][];
        for (int index = 0; index < count; index++) result[index] = new byte[rows];
        return result;
    }

    private static short[][] shortArrays(int count, int rows) {
        short[][] result = new short[count][];
        for (int index = 0; index < count; index++) result[index] = new short[rows];
        return result;
    }

    private static char[][] charArrays(int count, int rows) {
        char[][] result = new char[count][];
        for (int index = 0; index < count; index++) result[index] = new char[rows];
        return result;
    }

    private static int[][] intArrays(int count, int rows) {
        int[][] result = new int[count][];
        for (int index = 0; index < count; index++) result[index] = new int[rows];
        return result;
    }

    private static long[][] longArrays(int count, int rows) {
        long[][] result = new long[count][];
        for (int index = 0; index < count; index++) result[index] = new long[rows];
        return result;
    }

    private static float[][] floatArrays(int count, int rows) {
        float[][] result = new float[count][];
        for (int index = 0; index < count; index++) result[index] = new float[rows];
        return result;
    }

    private static double[][] doubleArrays(int count, int rows) {
        double[][] result = new double[count][];
        for (int index = 0; index < count; index++) result[index] = new double[rows];
        return result;
    }

    private static Object[][] referenceArrays(int count, int rows) {
        Object[][] result = new Object[count][];
        for (int index = 0; index < count; index++) result[index] = new Object[rows];
        return result;
    }

    private static boolean[][] clone(boolean[][] source) {
        boolean[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }

    private static byte[][] clone(byte[][] source) {
        byte[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }

    private static short[][] clone(short[][] source) {
        short[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }

    private static char[][] clone(char[][] source) {
        char[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }

    private static int[][] clone(int[][] source) {
        int[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }

    private static long[][] clone(long[][] source) {
        long[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }

    private static float[][] clone(float[][] source) {
        float[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }

    private static double[][] clone(double[][] source) {
        double[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }

    private static Object[][] clone(Object[][] source) {
        Object[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }
}
