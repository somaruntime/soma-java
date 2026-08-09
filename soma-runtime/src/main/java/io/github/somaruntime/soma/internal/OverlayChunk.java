package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaOperation;
import java.util.Arrays;

/** Immutable encoded base plus a bounded sparse set of complete-row replacements. */
final class OverlayChunk implements TableChunk {

    private static final long HEADER_BYTES = 96L;
    private static final long ARRAY_HEADER_BYTES = 32L;

    private final EncodedChunk base;
    private final GeneratedTableLayout layout;
    private int[] offsets;
    private TypedValues[] values;
    private int size;

    OverlayChunk(EncodedChunk base, GeneratedTableLayout layout) {
        this(base, layout, new int[4], new TypedValues[4], 0);
    }

    private OverlayChunk(
            EncodedChunk base,
            GeneratedTableLayout layout,
            int[] offsets,
            TypedValues[] values,
            int size) {
        this.base = base;
        this.layout = layout;
        this.offsets = offsets;
        this.values = values;
        this.size = size;
    }

    @Override public ChunkRepresentation representation() {
        return ChunkRepresentation.ENCODED_WITH_OVERLAY;
    }

    @Override public boolean booleanValue(int slot, int offset) {
        TypedValues override = override(offset);
        return override == null ? base.booleanValue(slot, offset)
                : override.booleanValue(slot);
    }

    @Override public byte byteValue(int slot, int offset) {
        TypedValues override = override(offset);
        return override == null ? base.byteValue(slot, offset)
                : override.byteValue(slot);
    }

    @Override public short shortValue(int slot, int offset) {
        TypedValues override = override(offset);
        return override == null ? base.shortValue(slot, offset)
                : override.shortValue(slot);
    }

    @Override public char charValue(int slot, int offset) {
        TypedValues override = override(offset);
        return override == null ? base.charValue(slot, offset)
                : override.charValue(slot);
    }

    @Override public int intValue(int slot, int offset) {
        TypedValues override = override(offset);
        return override == null ? base.intValue(slot, offset)
                : override.intValue(slot);
    }

    @Override public long longValue(int slot, int offset) {
        TypedValues override = override(offset);
        return override == null ? base.longValue(slot, offset)
                : override.longValue(slot);
    }

    @Override public float floatValue(int slot, int offset) {
        TypedValues override = override(offset);
        return override == null ? base.floatValue(slot, offset)
                : override.floatValue(slot);
    }

    @Override public double doubleValue(int slot, int offset) {
        TypedValues override = override(offset);
        return override == null ? base.doubleValue(slot, offset)
                : override.doubleValue(slot);
    }

    @Override public Object referenceValue(int slot, int offset) {
        TypedValues override = override(offset);
        return override == null ? base.referenceValue(slot, offset)
                : override.reference(slot);
    }

    @Override public boolean visitPrimitive(
            byte kind,
            int slot,
            int logicalRows,
            PrimitiveVisitor visitor) {
        for (int offset = 0; offset < logicalRows; offset++) {
            long raw;
            switch (kind) {
                case GeneratedTableLayout.BOOLEAN:
                    raw = booleanValue(slot, offset) ? 1L : 0L;
                    break;
                case GeneratedTableLayout.BYTE:
                    raw = byteValue(slot, offset);
                    break;
                case GeneratedTableLayout.SHORT:
                    raw = shortValue(slot, offset);
                    break;
                case GeneratedTableLayout.CHAR:
                    raw = charValue(slot, offset);
                    break;
                case GeneratedTableLayout.INT:
                    raw = intValue(slot, offset);
                    break;
                case GeneratedTableLayout.LONG:
                    raw = longValue(slot, offset);
                    break;
                case GeneratedTableLayout.FLOAT:
                    raw = Float.floatToIntBits(floatValue(slot, offset));
                    break;
                case GeneratedTableLayout.DOUBLE:
                    raw = Double.doubleToLongBits(doubleValue(slot, offset));
                    break;
                default:
                    throw new AssertionError("not a primitive leaf kind");
            }
            if (!visitor.visit(raw)) return false;
        }
        return true;
    }

    @Override public void read(
            int offset,
            TypedValues destination,
            GeneratedTableLayout ignored) {
        TypedValues override = override(offset);
        if (override == null) base.read(offset, destination, layout);
        else destination.copyFrom(override);
    }

    @Override public TableChunk mutableCopy(GeneratedTableLayout ignored) {
        int[] copiedOffsets = Arrays.copyOf(offsets, offsets.length);
        TypedValues[] copiedValues = new TypedValues[values.length];
        for (int index = 0; index < size; index++) {
            copiedValues[index] = new TypedValues(layout);
            copiedValues[index].copyFrom(values[index]);
        }
        return new OverlayChunk(
                base, layout, copiedOffsets, copiedValues, size);
    }

    @Override public void write(
            int offset,
            TypedValues source,
            GeneratedTableLayout ignored) {
        TypedValues destination = writable(offset);
        destination.copyFrom(source);
    }

    @Override public void clear(int offset, GeneratedTableLayout ignored) {
        TypedValues destination = writable(offset);
        destination.copyFrom(new TypedValues(layout));
    }

    @Override public PlainChunk materialize(GeneratedTableLayout ignored) {
        PlainChunk plain = base.materialize(layout);
        for (int index = 0; index < size; index++) {
            plain.write(offsets[index], values[index], layout);
        }
        return plain;
    }

    @Override public TableChunk finish(
            int logicalRows,
            SomaCompression policy,
            GeneratedTableLayout ignored,
            SomaOperation operation,
            Object provenance) {
        if (policy == SomaCompression.OFF || logicalRows != base.rows()) {
            return materialize(layout);
        }
        int rebuildThreshold = Math.max(64, base.rows() >>> 3);
        if (size > rebuildThreshold) {
            return ChunkEncoder.finish(
                    materialize(layout), logicalRows, policy,
                    layout, operation, provenance);
        }
        return this;
    }

    @Override public long managedBytes(
            GeneratedTableLayout ignored,
            SomaOperation operation,
            Object provenance) {
        long result = CheckedLong.add(
                base.managedBytes(layout, operation, provenance),
                HEADER_BYTES + 2L * ARRAY_HEADER_BYTES,
                operation,
                provenance);
        result = CheckedLong.add(
                result,
                CheckedLong.multiply(
                        offsets.length, 12L, operation, provenance),
                operation,
                provenance);
        long perRow = CheckedLong.add(
                layout.rowWidthBytes(),
                CheckedLong.multiply(
                        layout.leafCount(), 32L, operation, provenance),
                operation,
                provenance);
        return CheckedLong.add(
                result,
                CheckedLong.multiply(size, perRow, operation, provenance),
                operation,
                provenance);
    }

    @Override public boolean hasEncodedRepresentation() {
        return true;
    }

    @Override public long plainEquivalentBytesForField(
            GeneratedTableLayout ignored,
            int fieldIndex,
            SomaOperation operation,
            Object provenance) {
        return base.plainEquivalentBytesForField(
                layout, fieldIndex, operation, provenance);
    }

    @Override public long representationBytesForField(
            GeneratedTableLayout ignored,
            int fieldIndex,
            SomaOperation operation,
            Object provenance) {
        long baseBytes = base.representationBytesForField(
                layout, fieldIndex, operation, provenance);
        long overlayBytes = CheckedLong.multiply(
                size,
                CheckedLong.add(
                        layout.fieldWidthBytes(fieldIndex),
                        CheckedLong.multiply(
                                layout.fieldLeafCount(fieldIndex),
                                32L,
                                operation,
                                provenance),
                        operation,
                        provenance),
                operation,
                provenance);
        return CheckedLong.add(baseBytes, overlayBytes, operation, provenance);
    }

    int overlaySize() {
        return size;
    }

    private TypedValues override(int offset) {
        int index = indexOf(offset);
        return index < 0 ? null : values[index];
    }

    private TypedValues writable(int offset) {
        if (offset < 0 || offset >= base.rows()) {
            throw new AssertionError("invalid encoded overlay offset");
        }
        int index = indexOf(offset);
        if (index >= 0) return values[index];
        ensureCapacity(size + 1);
        offsets[size] = offset;
        TypedValues created = new TypedValues(layout);
        values[size] = created;
        size++;
        return created;
    }

    private int indexOf(int offset) {
        for (int index = 0; index < size; index++) {
            if (offsets[index] == offset) return index;
        }
        return -1;
    }

    private void ensureCapacity(int required) {
        if (required <= offsets.length) return;
        int capacity = offsets.length << 1;
        while (capacity < required) capacity <<= 1;
        offsets = Arrays.copyOf(offsets, capacity);
        values = Arrays.copyOf(values, capacity);
    }
}
