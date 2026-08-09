package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaOperation;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable per-leaf encoded Chunk. */
final class EncodedChunk implements TableChunk {

    private static final long CHUNK_HEADER_BYTES = 128L;
    private static final long COLUMN_HEADER_BYTES = 48L;
    private static final long ARRAY_HEADER_BYTES = 32L;
    private static final int RLE_MIN_AVERAGE_RUN_LENGTH = 4;

    private final int rows;
    private final PrimitiveColumn[] booleans;
    private final PrimitiveColumn[] bytes;
    private final PrimitiveColumn[] shorts;
    private final PrimitiveColumn[] chars;
    private final PrimitiveColumn[] ints;
    private final PrimitiveColumn[] longs;
    private final PrimitiveColumn[] floats;
    private final PrimitiveColumn[] doubles;
    private final ReferenceColumn[] references;

    private EncodedChunk(
            int rows,
            PrimitiveColumn[] booleans,
            PrimitiveColumn[] bytes,
            PrimitiveColumn[] shorts,
            PrimitiveColumn[] chars,
            PrimitiveColumn[] ints,
            PrimitiveColumn[] longs,
            PrimitiveColumn[] floats,
            PrimitiveColumn[] doubles,
            ReferenceColumn[] references) {
        this.rows = rows;
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

    static EncodedChunk encode(
            PlainChunk source,
            int rows,
            GeneratedTableLayout layout,
            SomaOperation operation,
            Object provenance) {
        PrimitiveColumn[] booleans = new PrimitiveColumn[source.booleanCount()];
        for (int slot = 0; slot < booleans.length; slot++) {
            booleans[slot] = BitBooleanColumn.create(source.booleans(slot), rows);
        }
        PrimitiveColumn[] bytes = new PrimitiveColumn[source.byteCount()];
        for (int slot = 0; slot < bytes.length; slot++) {
            bytes[slot] = integral(source.bytes(slot), rows, GeneratedTableLayout.BYTE);
        }
        PrimitiveColumn[] shorts = new PrimitiveColumn[source.shortCount()];
        for (int slot = 0; slot < shorts.length; slot++) {
            shorts[slot] = integral(source.shorts(slot), rows, GeneratedTableLayout.SHORT);
        }
        PrimitiveColumn[] chars = new PrimitiveColumn[source.charCount()];
        for (int slot = 0; slot < chars.length; slot++) {
            chars[slot] = integral(source.chars(slot), rows, GeneratedTableLayout.CHAR);
        }
        PrimitiveColumn[] ints = new PrimitiveColumn[source.intCount()];
        for (int slot = 0; slot < ints.length; slot++) {
            ints[slot] = integral(source.ints(slot), rows, GeneratedTableLayout.INT);
        }
        PrimitiveColumn[] longs = new PrimitiveColumn[source.longCount()];
        for (int slot = 0; slot < longs.length; slot++) {
            longs[slot] = integral(source.longs(slot), rows, GeneratedTableLayout.LONG);
        }
        PrimitiveColumn[] floats = new PrimitiveColumn[source.floatCount()];
        for (int slot = 0; slot < floats.length; slot++) {
            floats[slot] = PlainFloatColumn.create(source.floats(slot), rows);
        }
        PrimitiveColumn[] doubles = new PrimitiveColumn[source.doubleCount()];
        for (int slot = 0; slot < doubles.length; slot++) {
            doubles[slot] = PlainDoubleColumn.create(source.doubles(slot), rows);
        }
        ReferenceColumn[] references = new ReferenceColumn[source.referenceCount()];
        for (int slot = 0; slot < references.length; slot++) {
            int leaf = layout.leafForSlot(GeneratedTableLayout.REFERENCE, slot);
            byte equality = layout.equalityKind(leaf);
            references[slot] = equality == GeneratedTableLayout.EQ_STRING_CONTENT
                    || equality == GeneratedTableLayout.EQ_ENUM_IDENTITY
                    ? DictionaryReferenceColumn.create(
                            source.references(slot), rows,
                            equality == GeneratedTableLayout.EQ_ENUM_IDENTITY)
                    : PlainReferenceColumn.create(source.references(slot), rows);
        }
        return new EncodedChunk(
                rows, booleans, bytes, shorts, chars, ints, longs,
                floats, doubles, references);
    }

    @Override public ChunkRepresentation representation() {
        return ChunkRepresentation.ENCODED;
    }

    @Override public boolean booleanValue(int slot, int offset) {
        return booleans[slot].raw(offset) != 0L;
    }

    @Override public byte byteValue(int slot, int offset) {
        return (byte) bytes[slot].raw(offset);
    }

    @Override public short shortValue(int slot, int offset) {
        return (short) shorts[slot].raw(offset);
    }

    @Override public char charValue(int slot, int offset) {
        return (char) chars[slot].raw(offset);
    }

    @Override public int intValue(int slot, int offset) {
        return (int) ints[slot].raw(offset);
    }

    @Override public long longValue(int slot, int offset) {
        return longs[slot].raw(offset);
    }

    @Override public float floatValue(int slot, int offset) {
        return Float.intBitsToFloat((int) floats[slot].raw(offset));
    }

    @Override public double doubleValue(int slot, int offset) {
        return Double.longBitsToDouble(doubles[slot].raw(offset));
    }

    @Override public Object referenceValue(int slot, int offset) {
        return references[slot].value(offset);
    }

    @Override public void read(
            int offset,
            TypedValues destination,
            GeneratedTableLayout layout) {
        for (int slot = 0; slot < booleans.length; slot++) {
            destination.booleanValue(slot, booleanValue(slot, offset));
        }
        for (int slot = 0; slot < bytes.length; slot++) {
            destination.byteValue(slot, byteValue(slot, offset));
        }
        for (int slot = 0; slot < shorts.length; slot++) {
            destination.shortValue(slot, shortValue(slot, offset));
        }
        for (int slot = 0; slot < chars.length; slot++) {
            destination.charValue(slot, charValue(slot, offset));
        }
        for (int slot = 0; slot < ints.length; slot++) {
            destination.intValue(slot, intValue(slot, offset));
        }
        for (int slot = 0; slot < longs.length; slot++) {
            destination.longValue(slot, longValue(slot, offset));
        }
        for (int slot = 0; slot < floats.length; slot++) {
            destination.floatValue(slot, floatValue(slot, offset));
        }
        for (int slot = 0; slot < doubles.length; slot++) {
            destination.doubleValue(slot, doubleValue(slot, offset));
        }
        for (int slot = 0; slot < references.length; slot++) {
            destination.reference(slot, referenceValue(slot, offset));
        }
    }

    @Override public TableChunk mutableCopy(GeneratedTableLayout layout) {
        return new OverlayChunk(this, layout);
    }

    @Override public void write(
            int offset,
            TypedValues source,
            GeneratedTableLayout layout) {
        throw new AssertionError("encoded Chunk is immutable");
    }

    @Override public void clear(int offset, GeneratedTableLayout layout) {
        throw new AssertionError("encoded Chunk is immutable");
    }

    @Override public PlainChunk materialize(GeneratedTableLayout layout) {
        PlainChunk plain = new PlainChunk(layout, rows);
        TypedValues scratch = new TypedValues(layout);
        for (int offset = 0; offset < rows; offset++) {
            read(offset, scratch, layout);
            plain.write(offset, scratch, layout);
        }
        scratch.clearReferences();
        return plain;
    }

    @Override public TableChunk finish(
            int logicalRows,
            SomaCompression policy,
            GeneratedTableLayout layout,
            SomaOperation operation,
            Object provenance) {
        return policy == SomaCompression.AUTO && logicalRows == rows
                ? this : materialize(layout);
    }

    @Override public long managedBytes(
            GeneratedTableLayout layout,
            SomaOperation operation,
            Object provenance) {
        long result = CHUNK_HEADER_BYTES;
        result = add(result, booleans, operation, provenance);
        result = add(result, bytes, operation, provenance);
        result = add(result, shorts, operation, provenance);
        result = add(result, chars, operation, provenance);
        result = add(result, ints, operation, provenance);
        result = add(result, longs, operation, provenance);
        result = add(result, floats, operation, provenance);
        result = add(result, doubles, operation, provenance);
        for (ReferenceColumn column : references) {
            result = CheckedLong.add(
                    result, column.managedBytes(), operation, provenance);
        }
        return result;
    }

    @Override public boolean hasEncodedRepresentation() {
        return true;
    }

    @Override public long plainEquivalentBytesForField(
            GeneratedTableLayout layout,
            int fieldIndex,
            SomaOperation operation,
            Object provenance) {
        long payload = CheckedLong.multiply(
                rows,
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

    @Override public long representationBytesForField(
            GeneratedTableLayout layout,
            int fieldIndex,
            SomaOperation operation,
            Object provenance) {
        int start = layout.fieldStart(fieldIndex);
        int end = start + layout.fieldLeafCount(fieldIndex);
        long result = 0L;
        for (int leaf = start; leaf < end; leaf++) {
            int slot = layout.leafSlot(leaf);
            long bytes;
            switch (layout.leafKind(leaf)) {
                case GeneratedTableLayout.BOOLEAN: bytes = booleans[slot].managedBytes(); break;
                case GeneratedTableLayout.BYTE: bytes = this.bytes[slot].managedBytes(); break;
                case GeneratedTableLayout.SHORT: bytes = shorts[slot].managedBytes(); break;
                case GeneratedTableLayout.CHAR: bytes = chars[slot].managedBytes(); break;
                case GeneratedTableLayout.INT: bytes = ints[slot].managedBytes(); break;
                case GeneratedTableLayout.LONG: bytes = longs[slot].managedBytes(); break;
                case GeneratedTableLayout.FLOAT: bytes = floats[slot].managedBytes(); break;
                case GeneratedTableLayout.DOUBLE: bytes = doubles[slot].managedBytes(); break;
                case GeneratedTableLayout.REFERENCE: bytes = references[slot].managedBytes(); break;
                default: throw new AssertionError("unknown encoded leaf kind");
            }
            result = CheckedLong.add(result, bytes, operation, provenance);
        }
        return result;
    }

    int rows() {
        return rows;
    }

    private static long add(
            long current,
            PrimitiveColumn[] columns,
            SomaOperation operation,
            Object provenance) {
        long result = current;
        for (PrimitiveColumn column : columns) {
            result = CheckedLong.add(
                    result, column.managedBytes(), operation, provenance);
        }
        return result;
    }

    private static PrimitiveColumn integral(byte[] values, int rows, byte kind) {
        long[] widened = new long[rows];
        for (int i = 0; i < rows; i++) widened[i] = values[i];
        return chooseIntegral(widened, rows, kind, 1L);
    }

    private static PrimitiveColumn integral(short[] values, int rows, byte kind) {
        long[] widened = new long[rows];
        for (int i = 0; i < rows; i++) widened[i] = values[i];
        return chooseIntegral(widened, rows, kind, 2L);
    }

    private static PrimitiveColumn integral(char[] values, int rows, byte kind) {
        long[] widened = new long[rows];
        for (int i = 0; i < rows; i++) widened[i] = values[i];
        return chooseIntegral(widened, rows, kind, 2L);
    }

    private static PrimitiveColumn integral(int[] values, int rows, byte kind) {
        long[] widened = new long[rows];
        for (int i = 0; i < rows; i++) widened[i] = values[i];
        return chooseIntegral(widened, rows, kind, 4L);
    }

    private static PrimitiveColumn integral(long[] values, int rows, byte kind) {
        long[] widened = new long[rows];
        System.arraycopy(values, 0, widened, 0, rows);
        return chooseIntegral(widened, rows, kind, 8L);
    }

    private static PrimitiveColumn chooseIntegral(
            long[] values,
            int rows,
            byte kind,
            long width) {
        RleColumn rle = RleColumn.create(values, rows);
        long plainBytes = COLUMN_HEADER_BYTES + ARRAY_HEADER_BYTES + width * rows;
        // RLE lookup is logarithmic in run count. Marginal byte savings from
        // two- or three-element long runs do not repay that random-access tax.
        boolean sufficientlyLongRuns = (long) rle.runCount()
                * RLE_MIN_AVERAGE_RUN_LENGTH <= rows;
        return sufficientlyLongRuns && rle.managedBytes() < plainBytes
                ? rle : PlainIntegralColumn.create(values, kind, rows);
    }

    private interface PrimitiveColumn {
        long raw(int offset);
        long managedBytes();
    }

    private static final class BitBooleanColumn implements PrimitiveColumn {
        private final long[] words;

        private BitBooleanColumn(long[] words) {
            this.words = words;
        }

        static BitBooleanColumn create(boolean[] source, int rows) {
            long[] words = new long[(rows + 63) >>> 6];
            for (int i = 0; i < rows; i++) {
                if (source[i]) words[i >>> 6] |= 1L << (i & 63);
            }
            return new BitBooleanColumn(words);
        }

        @Override public long raw(int offset) {
            return (words[offset >>> 6] >>> (offset & 63)) & 1L;
        }

        @Override public long managedBytes() {
            return COLUMN_HEADER_BYTES + ARRAY_HEADER_BYTES + 8L * words.length;
        }
    }

    private static final class PlainIntegralColumn implements PrimitiveColumn {
        private final byte kind;
        private final byte[] bytes;
        private final short[] shorts;
        private final char[] chars;
        private final int[] ints;
        private final long[] longs;

        private PlainIntegralColumn(
                byte kind, byte[] bytes, short[] shorts, char[] chars,
                int[] ints, long[] longs) {
            this.kind = kind;
            this.bytes = bytes;
            this.shorts = shorts;
            this.chars = chars;
            this.ints = ints;
            this.longs = longs;
        }

        static PlainIntegralColumn create(long[] source, byte kind, int rows) {
            if (kind == GeneratedTableLayout.BYTE) {
                byte[] values = new byte[rows];
                for (int i = 0; i < rows; i++) values[i] = (byte) source[i];
                return new PlainIntegralColumn(kind, values, null, null, null, null);
            }
            if (kind == GeneratedTableLayout.SHORT) {
                short[] values = new short[rows];
                for (int i = 0; i < rows; i++) values[i] = (short) source[i];
                return new PlainIntegralColumn(kind, null, values, null, null, null);
            }
            if (kind == GeneratedTableLayout.CHAR) {
                char[] values = new char[rows];
                for (int i = 0; i < rows; i++) values[i] = (char) source[i];
                return new PlainIntegralColumn(kind, null, null, values, null, null);
            }
            if (kind == GeneratedTableLayout.INT) {
                int[] values = new int[rows];
                for (int i = 0; i < rows; i++) values[i] = (int) source[i];
                return new PlainIntegralColumn(kind, null, null, null, values, null);
            }
            return new PlainIntegralColumn(
                    kind, null, null, null, null, source.clone());
        }

        @Override public long raw(int offset) {
            if (kind == GeneratedTableLayout.BYTE) return bytes[offset];
            if (kind == GeneratedTableLayout.SHORT) return shorts[offset];
            if (kind == GeneratedTableLayout.CHAR) return chars[offset];
            if (kind == GeneratedTableLayout.INT) return ints[offset];
            return longs[offset];
        }

        @Override public long managedBytes() {
            int length = bytes != null ? bytes.length
                    : shorts != null ? shorts.length
                    : chars != null ? chars.length
                    : ints != null ? ints.length : longs.length;
            long width = bytes != null ? 1L
                    : shorts != null || chars != null ? 2L
                    : ints != null ? 4L : 8L;
            return COLUMN_HEADER_BYTES + ARRAY_HEADER_BYTES + width * length;
        }
    }

    private static final class RleColumn implements PrimitiveColumn {
        private final long[] values;
        private final int[] ends;

        private RleColumn(long[] values, int[] ends) {
            this.values = values;
            this.ends = ends;
        }

        static RleColumn create(long[] source, int rows) {
            if (rows == 0) return new RleColumn(new long[0], new int[0]);
            long[] values = new long[rows];
            int[] ends = new int[rows];
            int runs = 0;
            long current = source[0];
            for (int offset = 1; offset <= rows; offset++) {
                if (offset == rows || source[offset] != current) {
                    values[runs] = current;
                    ends[runs] = offset;
                    runs++;
                    if (offset < rows) current = source[offset];
                }
            }
            long[] compactValues = new long[runs];
            int[] compactEnds = new int[runs];
            System.arraycopy(values, 0, compactValues, 0, runs);
            System.arraycopy(ends, 0, compactEnds, 0, runs);
            return new RleColumn(compactValues, compactEnds);
        }

        @Override public long raw(int offset) {
            int low = 0;
            int high = ends.length - 1;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (offset < ends[middle]) high = middle;
                else low = middle + 1;
            }
            return values[low];
        }

        @Override public long managedBytes() {
            return COLUMN_HEADER_BYTES + 2L * ARRAY_HEADER_BYTES
                    + 12L * values.length;
        }

        int runCount() {
            return values.length;
        }
    }

    private static final class PlainFloatColumn implements PrimitiveColumn {
        private final float[] values;
        private PlainFloatColumn(float[] values) { this.values = values; }
        static PlainFloatColumn create(float[] source, int rows) {
            float[] values = new float[rows];
            System.arraycopy(source, 0, values, 0, rows);
            return new PlainFloatColumn(values);
        }
        @Override public long raw(int offset) {
            return Float.floatToRawIntBits(values[offset]);
        }
        @Override public long managedBytes() {
            return COLUMN_HEADER_BYTES + ARRAY_HEADER_BYTES + 4L * values.length;
        }
    }

    private static final class PlainDoubleColumn implements PrimitiveColumn {
        private final double[] values;
        private PlainDoubleColumn(double[] values) { this.values = values; }
        static PlainDoubleColumn create(double[] source, int rows) {
            double[] values = new double[rows];
            System.arraycopy(source, 0, values, 0, rows);
            return new PlainDoubleColumn(values);
        }
        @Override public long raw(int offset) {
            return Double.doubleToRawLongBits(values[offset]);
        }
        @Override public long managedBytes() {
            return COLUMN_HEADER_BYTES + ARRAY_HEADER_BYTES + 8L * values.length;
        }
    }

    private interface ReferenceColumn {
        Object value(int offset);
        long managedBytes();
    }

    private static final class PlainReferenceColumn implements ReferenceColumn {
        private final Object[] values;
        private PlainReferenceColumn(Object[] values) { this.values = values; }
        static PlainReferenceColumn create(Object[] source, int rows) {
            Object[] values = new Object[rows];
            System.arraycopy(source, 0, values, 0, rows);
            return new PlainReferenceColumn(values);
        }
        @Override public Object value(int offset) { return values[offset]; }
        @Override public long managedBytes() {
            return COLUMN_HEADER_BYTES + ARRAY_HEADER_BYTES + 8L * values.length;
        }
    }

    private static final class DictionaryReferenceColumn implements ReferenceColumn {
        private final Object[] dictionary;
        private final int[] codes;

        private DictionaryReferenceColumn(Object[] dictionary, int[] codes) {
            this.dictionary = dictionary;
            this.codes = codes;
        }

        static ReferenceColumn create(Object[] source, int rows, boolean identity) {
            Map<Object, Integer> lookup = identity
                    ? new IdentityHashMap<Object, Integer>()
                    : new LinkedHashMap<Object, Integer>();
            List<Object> dictionary = new ArrayList<Object>();
            int[] codes = new int[rows];
            for (int offset = 0; offset < rows; offset++) {
                Object value = source[offset];
                Integer code = lookup.get(value);
                if (code == null && !lookup.containsKey(value)) {
                    code = dictionary.size();
                    lookup.put(value, code);
                    dictionary.add(value);
                }
                codes[offset] = code;
            }
            Object[] values = dictionary.toArray(new Object[dictionary.size()]);
            long dictionaryBytes = COLUMN_HEADER_BYTES + 2L * ARRAY_HEADER_BYTES
                    + 8L * values.length + 4L * codes.length;
            long plainBytes = COLUMN_HEADER_BYTES + ARRAY_HEADER_BYTES + 8L * rows;
            return dictionaryBytes < plainBytes
                    ? new DictionaryReferenceColumn(values, codes)
                    : PlainReferenceColumn.create(source, rows);
        }

        @Override public Object value(int offset) {
            return dictionary[codes[offset]];
        }

        @Override public long managedBytes() {
            return COLUMN_HEADER_BYTES + 2L * ARRAY_HEADER_BYTES
                    + 8L * dictionary.length + 4L * codes.length;
        }
    }
}
