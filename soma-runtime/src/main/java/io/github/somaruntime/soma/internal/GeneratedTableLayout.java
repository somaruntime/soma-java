package io.github.somaruntime.soma.internal;

import java.util.Arrays;

/** Validated physical leaf and logical identity layout emitted by the processor. */
public final class GeneratedTableLayout {

    public static final byte BOOLEAN = 1;
    public static final byte BYTE = 2;
    public static final byte SHORT = 3;
    public static final byte CHAR = 4;
    public static final byte INT = 5;
    public static final byte LONG = 6;
    public static final byte FLOAT = 7;
    public static final byte DOUBLE = 8;
    public static final byte REFERENCE = 9;

    public static final byte EQ_BOOLEAN = 1;
    public static final byte EQ_BYTE = 2;
    public static final byte EQ_SHORT = 3;
    public static final byte EQ_CHAR = 4;
    public static final byte EQ_INT = 5;
    public static final byte EQ_LONG = 6;
    public static final byte EQ_FLOAT_CANONICAL = 7;
    public static final byte EQ_DOUBLE_CANONICAL = 8;
    public static final byte EQ_STRING_CONTENT = 9;
    public static final byte EQ_ENUM_IDENTITY = 10;
    public static final byte EQ_OBJECT_IDENTITY = 11;

    private final String logicalName;
    private final long defaultCapacity;
    private final byte[] leafKinds;
    private final byte[] equalityKinds;
    private final int[] leafSlots;
    private final int[] kindCounts;
    private final int[] fieldStarts;
    private final int[] fieldCounts;
    private final boolean[] fieldNullables;
    private final int keyFieldIndex;
    private final int[] indexFieldIndexes;
    private final boolean[] keyLeaves;
    private final long rowWidthBytes;

    private GeneratedTableLayout(
            String logicalName,
            long defaultCapacity,
            byte[] leafKinds,
            byte[] equalityKinds,
            int[] fieldStarts,
            int[] fieldCounts,
            boolean[] fieldNullables,
            int keyFieldIndex,
            int[] indexFieldIndexes) {
        this.logicalName = logicalName;
        this.defaultCapacity = defaultCapacity;
        this.leafKinds = leafKinds.clone();
        this.equalityKinds = equalityKinds.clone();
        this.fieldStarts = fieldStarts.clone();
        this.fieldCounts = fieldCounts.clone();
        this.fieldNullables = fieldNullables.clone();
        this.keyFieldIndex = keyFieldIndex;
        this.indexFieldIndexes = indexFieldIndexes.clone();
        this.leafSlots = new int[leafKinds.length];
        this.kindCounts = new int[REFERENCE + 1];
        long width = 0L;
        for (int leaf = 0; leaf < leafKinds.length; leaf++) {
            byte kind = leafKinds[leaf];
            leafSlots[leaf] = kindCounts[kind]++;
            width = Math.addExact(width, width(kind));
        }
        this.rowWidthBytes = width;
        this.keyLeaves = new boolean[leafKinds.length];
        if (keyFieldIndex >= 0) {
            int start = fieldStarts[keyFieldIndex];
            Arrays.fill(keyLeaves, start, start + fieldCounts[keyFieldIndex], true);
        }
    }

    public static GeneratedTableLayout create(
            String logicalName,
            long defaultCapacity,
            byte[] leafKinds,
            byte[] equalityKinds,
            int[] fieldStarts,
            int[] fieldCounts,
            boolean[] fieldNullables,
            int keyFieldIndex,
            int[] indexFieldIndexes) {
        if (logicalName == null
                || logicalName.isEmpty()
                || defaultCapacity < 0L
                || leafKinds == null
                || equalityKinds == null
                || leafKinds.length == 0
                || leafKinds.length != equalityKinds.length
                || fieldStarts == null
                || fieldCounts == null
                || fieldNullables == null
                || fieldStarts.length == 0
                || fieldStarts.length != fieldCounts.length
                || fieldStarts.length != fieldNullables.length
                || keyFieldIndex < -1
                || keyFieldIndex >= fieldStarts.length
                || indexFieldIndexes == null) {
            throw new AssertionError("invalid generated Table layout");
        }
        boolean[] coveredLeaves = new boolean[leafKinds.length];
        int previousStart = -1;
        for (int field = 0; field < fieldStarts.length; field++) {
            int start = fieldStarts[field];
            int count = fieldCounts[field];
            long end = (long) start + count;
            if (start < 0
                    || start < previousStart
                    || count <= 0
                    || end > leafKinds.length) {
                throw new AssertionError("non-canonical generated Field layout");
            }
            Arrays.fill(coveredLeaves, start, (int) end, true);
            if (fieldNullables[field]
                    && (count != 1 || leafKinds[start] != REFERENCE)) {
                throw new AssertionError("nullable logical Field is not one reference leaf");
            }
            previousStart = start;
        }
        for (boolean covered : coveredLeaves) {
            if (!covered) {
                throw new AssertionError("generated Field layout does not cover all leaves");
            }
        }
        boolean[] indexes = new boolean[fieldStarts.length];
        for (int field : indexFieldIndexes) {
            if (field < 0 || field >= fieldStarts.length || indexes[field]) {
                throw new AssertionError("invalid generated Index layout");
            }
            indexes[field] = true;
        }
        for (int leaf = 0; leaf < leafKinds.length; leaf++) {
            validateLeaf(leafKinds[leaf], equalityKinds[leaf]);
        }
        return new GeneratedTableLayout(
                logicalName,
                defaultCapacity,
                leafKinds,
                equalityKinds,
                fieldStarts,
                fieldCounts,
                fieldNullables,
                keyFieldIndex,
                indexFieldIndexes);
    }

    String logicalName() {
        return logicalName;
    }

    long defaultCapacity() {
        return defaultCapacity;
    }

    int leafCount() {
        return leafKinds.length;
    }

    int fieldCount() {
        return fieldStarts.length;
    }

    int keyFieldIndex() {
        return keyFieldIndex;
    }

    int indexCount() {
        return indexFieldIndexes.length;
    }

    int indexFieldIndex(int indexOrdinal) {
        if (indexOrdinal < 0 || indexOrdinal >= indexFieldIndexes.length) {
            throw new AssertionError("invalid generated Index ordinal");
        }
        return indexFieldIndexes[indexOrdinal];
    }

    int indexOrdinalForField(int fieldIndex) {
        requireField(fieldIndex);
        for (int ordinal = 0; ordinal < indexFieldIndexes.length; ordinal++) {
            if (indexFieldIndexes[ordinal] == fieldIndex) return ordinal;
        }
        return -1;
    }

    int fieldStart(int fieldIndex) {
        requireField(fieldIndex);
        return fieldStarts[fieldIndex];
    }

    int fieldLeafCount(int fieldIndex) {
        requireField(fieldIndex);
        return fieldCounts[fieldIndex];
    }

    boolean fieldNullable(int fieldIndex) {
        requireField(fieldIndex);
        return fieldNullables[fieldIndex];
    }

    boolean fieldKey(int fieldIndex) {
        requireField(fieldIndex);
        return fieldIndex == keyFieldIndex;
    }

    boolean fieldIndexed(int fieldIndex) {
        return indexOrdinalForField(fieldIndex) >= 0;
    }

    long fieldWidthBytes(int fieldIndex) {
        int start = fieldStart(fieldIndex);
        int count = fieldLeafCount(fieldIndex);
        long result = 0L;
        for (int leaf = start; leaf < start + count; leaf++) {
            result = Math.addExact(result, width(leafKinds[leaf]));
        }
        return result;
    }

    byte leafKind(int leaf) {
        requireLeaf(leaf);
        return leafKinds[leaf];
    }

    byte equalityKind(int leaf) {
        requireLeaf(leaf);
        return equalityKinds[leaf];
    }

    int leafSlot(int leaf) {
        requireLeaf(leaf);
        return leafSlots[leaf];
    }

    int leafForSlot(byte kind, int slot) {
        if (slot < 0 || slot >= kindCount(kind)) {
            throw new AssertionError("invalid generated leaf slot");
        }
        for (int leaf = 0; leaf < leafKinds.length; leaf++) {
            if (leafKinds[leaf] == kind && leafSlots[leaf] == slot) return leaf;
        }
        throw new AssertionError("missing generated leaf slot");
    }

    int kindCount(byte kind) {
        if (kind < BOOLEAN || kind > REFERENCE) {
            throw new AssertionError("invalid generated leaf kind");
        }
        return kindCounts[kind];
    }

    boolean keyLeaf(int leaf) {
        requireLeaf(leaf);
        return keyLeaves[leaf];
    }

    long rowWidthBytes() {
        return rowWidthBytes;
    }

    /**
     * Conservative retained size for one detached generated Table object and all
     * recursively materialized Value objects. Ordinary referenced application objects
     * remain application-owned and are deliberately not charged again.
     */
    long detachedRowEstimateBytes() {
        long leafAllowance = Math.multiplyExact((long) leafKinds.length, 96L);
        long fieldAllowance = Math.multiplyExact((long) fieldStarts.length, 32L);
        return Math.addExact(
                Math.addExact(128L, rowWidthBytes),
                Math.addExact(leafAllowance, fieldAllowance));
    }

    /**
     * Conservative size of one schema-known Field materialization. The allowance
     * covers a recursively rebuilt Value; ordinary referenced objects are reused,
     * so this intentionally remains conservative for String, Enum and Object.
     */
    long detachedFieldEstimateBytes(int fieldIndex) {
        long leaves = fieldLeafCount(fieldIndex);
        long leafAllowance = Math.multiplyExact(leaves, 96L);
        return Math.addExact(
                Math.addExact(128L, fieldWidthBytes(fieldIndex)),
                leafAllowance);
    }

    long hashField(TableChunkDirectory directory, long locator, int fieldIndex) {
        int start = fieldStart(fieldIndex);
        int count = fieldLeafCount(fieldIndex);
        long hash = 1L;
        TableChunk chunk = directory.chunk(locator / directory.chunkRows());
        int offset = (int) (locator % directory.chunkRows());
        for (int leaf = start; leaf < start + count; leaf++) {
            hash = mixPart(hash, hashStored(chunk, offset, leaf));
        }
        return finishHash(hash);
    }

    long hashField(TypedValues values, int fieldIndex) {
        int start = fieldStart(fieldIndex);
        int count = fieldLeafCount(fieldIndex);
        long hash = 1L;
        for (int leaf = start; leaf < start + count; leaf++) {
            hash = mixPart(hash, hashValue(values, leaf));
        }
        return finishHash(hash);
    }

    boolean fieldEquals(
            TableChunkDirectory directory,
            long locator,
            TypedValues values,
            int fieldIndex) {
        int start = fieldStart(fieldIndex);
        int count = fieldLeafCount(fieldIndex);
        TableChunk chunk = directory.chunk(locator / directory.chunkRows());
        int offset = (int) (locator % directory.chunkRows());
        for (int leaf = start; leaf < start + count; leaf++) {
            if (!leafEquals(chunk, offset, values, leaf)) {
                return false;
            }
        }
        return true;
    }

    boolean fieldEquals(
            TableChunkDirectory directory,
            long leftLocator,
            long rightLocator,
            int fieldIndex) {
        int start = fieldStart(fieldIndex);
        int count = fieldLeafCount(fieldIndex);
        TableChunk left = directory.chunk(leftLocator / directory.chunkRows());
        TableChunk right = directory.chunk(rightLocator / directory.chunkRows());
        int leftOffset = (int) (leftLocator % directory.chunkRows());
        int rightOffset = (int) (rightLocator % directory.chunkRows());
        for (int leaf = start; leaf < start + count; leaf++) {
            int slot = leafSlots[leaf];
            switch (leafKinds[leaf]) {
                case BOOLEAN:
                    if (left.booleanValue(slot, leftOffset) != right.booleanValue(slot, rightOffset)) return false;
                    break;
                case BYTE:
                    if (left.byteValue(slot, leftOffset) != right.byteValue(slot, rightOffset)) return false;
                    break;
                case SHORT:
                    if (left.shortValue(slot, leftOffset) != right.shortValue(slot, rightOffset)) return false;
                    break;
                case CHAR:
                    if (left.charValue(slot, leftOffset) != right.charValue(slot, rightOffset)) return false;
                    break;
                case INT:
                    if (left.intValue(slot, leftOffset) != right.intValue(slot, rightOffset)) return false;
                    break;
                case LONG:
                    if (left.longValue(slot, leftOffset) != right.longValue(slot, rightOffset)) return false;
                    break;
                case FLOAT:
                    if (Float.floatToIntBits(left.floatValue(slot, leftOffset))
                            != Float.floatToIntBits(right.floatValue(slot, rightOffset))) return false;
                    break;
                case DOUBLE:
                    if (Double.doubleToLongBits(left.doubleValue(slot, leftOffset))
                            != Double.doubleToLongBits(right.doubleValue(slot, rightOffset))) return false;
                    break;
                case REFERENCE:
                    if (!referenceEquals(
                            left.referenceValue(slot, leftOffset),
                            right.referenceValue(slot, rightOffset),
                            equalityKinds[leaf])) return false;
                    break;
                default:
                    throw new AssertionError("unknown leaf kind");
            }
        }
        return true;
    }

    boolean joinCompatible(
            int fieldIndex,
            GeneratedTableLayout other,
            int otherFieldIndex) {
        int count = fieldLeafCount(fieldIndex);
        if (count != other.fieldLeafCount(otherFieldIndex)) return false;
        int leftStart = fieldStart(fieldIndex);
        int rightStart = other.fieldStart(otherFieldIndex);
        for (int offset = 0; offset < count; offset++) {
            if (leafKinds[leftStart + offset] != other.leafKinds[rightStart + offset]
                    || equalityKinds[leftStart + offset]
                    != other.equalityKinds[rightStart + offset]) {
                return false;
            }
        }
        return true;
    }

    boolean joinFieldEquals(
            TableChunkDirectory leftDirectory,
            long leftLocator,
            int leftFieldIndex,
            GeneratedTableLayout rightLayout,
            TableChunkDirectory rightDirectory,
            long rightLocator,
            int rightFieldIndex) {
        if (!joinCompatible(leftFieldIndex, rightLayout, rightFieldIndex)) {
            throw new AssertionError("incompatible generated Join Fields");
        }
        if (storedFieldIsNull(leftDirectory, leftLocator, leftFieldIndex)
                || rightLayout.storedFieldIsNull(
                        rightDirectory, rightLocator, rightFieldIndex)) {
            return false;
        }
        int count = fieldLeafCount(leftFieldIndex);
        int leftStart = fieldStart(leftFieldIndex);
        int rightStart = rightLayout.fieldStart(rightFieldIndex);
        TableChunk left = leftDirectory.chunk(
                leftLocator / leftDirectory.chunkRows());
        TableChunk right = rightDirectory.chunk(
                rightLocator / rightDirectory.chunkRows());
        int leftOffset = (int) (leftLocator % leftDirectory.chunkRows());
        int rightOffset = (int) (rightLocator % rightDirectory.chunkRows());
        for (int index = 0; index < count; index++) {
            int leftLeaf = leftStart + index;
            int rightLeaf = rightStart + index;
            int leftSlot = leafSlots[leftLeaf];
            int rightSlot = rightLayout.leafSlots[rightLeaf];
            switch (leafKinds[leftLeaf]) {
                case BOOLEAN:
                    if (left.booleanValue(leftSlot, leftOffset)
                            != right.booleanValue(rightSlot, rightOffset)) return false;
                    break;
                case BYTE:
                    if (left.byteValue(leftSlot, leftOffset)
                            != right.byteValue(rightSlot, rightOffset)) return false;
                    break;
                case SHORT:
                    if (left.shortValue(leftSlot, leftOffset)
                            != right.shortValue(rightSlot, rightOffset)) return false;
                    break;
                case CHAR:
                    if (left.charValue(leftSlot, leftOffset)
                            != right.charValue(rightSlot, rightOffset)) return false;
                    break;
                case INT:
                    if (left.intValue(leftSlot, leftOffset)
                            != right.intValue(rightSlot, rightOffset)) return false;
                    break;
                case LONG:
                    if (left.longValue(leftSlot, leftOffset)
                            != right.longValue(rightSlot, rightOffset)) return false;
                    break;
                case REFERENCE:
                    if (!referenceEquals(
                            left.referenceValue(leftSlot, leftOffset),
                            right.referenceValue(rightSlot, rightOffset),
                            equalityKinds[leftLeaf])) return false;
                    break;
                default:
                    throw new AssertionError("non-keyable Join leaf kind");
            }
        }
        return true;
    }

    boolean logicalRowEquals(TypedValues left, TypedValues right) {
        for (int leaf = 0; leaf < leafKinds.length; leaf++) {
            if (!leafEquals(left, right, leaf)) {
                return false;
            }
        }
        return true;
    }

    boolean fieldEquals(TypedValues left, TypedValues right, int fieldIndex) {
        int start = fieldStart(fieldIndex);
        int count = fieldLeafCount(fieldIndex);
        for (int leaf = start; leaf < start + count; leaf++) {
            if (!leafEquals(left, right, leaf)) return false;
        }
        return true;
    }

    boolean fieldValueIsNull(TypedValues values, int fieldIndex) {
        requireField(fieldIndex);
        if (!fieldNullables[fieldIndex]) return false;
        return values.reference(leafSlot(fieldStart(fieldIndex))) == null;
    }

    boolean storedFieldIsNull(
            TableChunkDirectory directory,
            long locator,
            int fieldIndex) {
        requireField(fieldIndex);
        if (!fieldNullables[fieldIndex]) return false;
        int leaf = fieldStart(fieldIndex);
        return directory.referenceValue(locator, leafSlot(leaf)) == null;
    }

    int compareStored(
            TableChunkDirectory directory,
            long locator,
            TypedValues literal,
            int fieldIndex) {
        if (fieldLeafCount(fieldIndex) != 1) {
            throw new AssertionError("only scalar ordered Fields are comparable");
        }
        int leaf = fieldStart(fieldIndex);
        int slot = leafSlot(leaf);
        switch (leafKinds[leaf]) {
            case BYTE:
                return Byte.compare(directory.byteValue(locator, slot), literal.byteValue(slot));
            case SHORT:
                return Short.compare(directory.shortValue(locator, slot), literal.shortValue(slot));
            case CHAR:
                return Character.compare(directory.charValue(locator, slot), literal.charValue(slot));
            case INT:
                return Integer.compare(directory.intValue(locator, slot), literal.intValue(slot));
            case LONG:
                return Long.compare(directory.longValue(locator, slot), literal.longValue(slot));
            case FLOAT:
                return Float.compare(directory.floatValue(locator, slot), literal.floatValue(slot));
            case DOUBLE:
                return Double.compare(directory.doubleValue(locator, slot), literal.doubleValue(slot));
            case REFERENCE:
                Object left = directory.referenceValue(locator, slot);
                Object right = literal.reference(slot);
                if (left == null || right == null) {
                    return left == right ? 0 : left == null ? -1 : 1;
                }
                if (equalityKinds[leaf] == EQ_STRING_CONTENT) {
                    return ((String) left).compareTo((String) right);
                }
                if (equalityKinds[leaf] == EQ_ENUM_IDENTITY) {
                    return Integer.compare(((Enum<?>) left).ordinal(), ((Enum<?>) right).ordinal());
                }
                throw new AssertionError("ordinary Object has no intrinsic order");
            default:
                throw new AssertionError("Field kind has no intrinsic order");
        }
    }

    int compareStored(
            TableChunkDirectory directory,
            long leftLocator,
            long rightLocator,
            int fieldIndex) {
        int start = fieldStart(fieldIndex);
        int count = fieldLeafCount(fieldIndex);
        TableChunk left = directory.chunk(leftLocator / directory.chunkRows());
        TableChunk right = directory.chunk(rightLocator / directory.chunkRows());
        int leftOffset = (int) (leftLocator % directory.chunkRows());
        int rightOffset = (int) (rightLocator % directory.chunkRows());
        for (int leaf = start; leaf < start + count; leaf++) {
            int compared = compareLeaf(
                    left, leftOffset, right, rightOffset, leaf);
            if (compared != 0) return compared;
        }
        return 0;
    }

    private int compareLeaf(
            TableChunk left,
            int leftOffset,
            TableChunk right,
            int rightOffset,
            int leaf) {
        int slot = leafSlot(leaf);
        switch (leafKinds[leaf]) {
            case BOOLEAN:
                return Boolean.compare(
                        left.booleanValue(slot, leftOffset), right.booleanValue(slot, rightOffset));
            case BYTE:
                return Byte.compare(
                        left.byteValue(slot, leftOffset), right.byteValue(slot, rightOffset));
            case SHORT:
                return Short.compare(
                        left.shortValue(slot, leftOffset), right.shortValue(slot, rightOffset));
            case CHAR:
                return Character.compare(
                        left.charValue(slot, leftOffset), right.charValue(slot, rightOffset));
            case INT:
                return Integer.compare(
                        left.intValue(slot, leftOffset), right.intValue(slot, rightOffset));
            case LONG:
                return Long.compare(
                        left.longValue(slot, leftOffset), right.longValue(slot, rightOffset));
            case FLOAT:
                return Float.compare(
                        left.floatValue(slot, leftOffset), right.floatValue(slot, rightOffset));
            case DOUBLE:
                return Double.compare(
                        left.doubleValue(slot, leftOffset), right.doubleValue(slot, rightOffset));
            case REFERENCE:
                Object leftValue = left.referenceValue(slot, leftOffset);
                Object rightValue = right.referenceValue(slot, rightOffset);
                if (leftValue == null || rightValue == null) {
                    return leftValue == rightValue ? 0 : leftValue == null ? -1 : 1;
                }
                if (equalityKinds[leaf] == EQ_STRING_CONTENT) {
                    return ((String) leftValue).compareTo((String) rightValue);
                }
                if (equalityKinds[leaf] == EQ_ENUM_IDENTITY) {
                    return Integer.compare(
                            ((Enum<?>) leftValue).ordinal(),
                            ((Enum<?>) rightValue).ordinal());
                }
                throw new AssertionError("ordinary Object has no intrinsic order");
            default:
                throw new AssertionError("Field kind has no intrinsic order");
        }
    }

    int compareValues(TypedValues left, TypedValues right, int fieldIndex) {
        if (fieldLeafCount(fieldIndex) != 1) {
            throw new AssertionError("only scalar ordered Fields are comparable");
        }
        int leaf = fieldStart(fieldIndex);
        int slot = leafSlot(leaf);
        switch (leafKinds[leaf]) {
            case BYTE:
                return Byte.compare(left.byteValue(slot), right.byteValue(slot));
            case SHORT:
                return Short.compare(left.shortValue(slot), right.shortValue(slot));
            case CHAR:
                return Character.compare(left.charValue(slot), right.charValue(slot));
            case INT:
                return Integer.compare(left.intValue(slot), right.intValue(slot));
            case LONG:
                return Long.compare(left.longValue(slot), right.longValue(slot));
            case FLOAT:
                return Float.compare(left.floatValue(slot), right.floatValue(slot));
            case DOUBLE:
                return Double.compare(left.doubleValue(slot), right.doubleValue(slot));
            case REFERENCE:
                Object leftValue = left.reference(slot);
                Object rightValue = right.reference(slot);
                if (leftValue == null || rightValue == null) {
                    return leftValue == rightValue ? 0 : leftValue == null ? -1 : 1;
                }
                if (equalityKinds[leaf] == EQ_STRING_CONTENT) {
                    return ((String) leftValue).compareTo((String) rightValue);
                }
                if (equalityKinds[leaf] == EQ_ENUM_IDENTITY) {
                    return Integer.compare(
                            ((Enum<?>) leftValue).ordinal(),
                            ((Enum<?>) rightValue).ordinal());
                }
                throw new AssertionError("ordinary Object has no intrinsic order");
            default:
                throw new AssertionError("Field kind has no intrinsic order");
        }
    }

    private long hashStored(TableChunk chunk, int offset, int leaf) {
        int slot = leafSlots[leaf];
        switch (leafKinds[leaf]) {
            case BOOLEAN:
                return chunk.booleanValue(slot, offset) ? 1231L : 1237L;
            case BYTE:
                return chunk.byteValue(slot, offset);
            case SHORT:
                return chunk.shortValue(slot, offset);
            case CHAR:
                return chunk.charValue(slot, offset);
            case INT:
                return chunk.intValue(slot, offset);
            case LONG:
                long longValue = chunk.longValue(slot, offset);
                return longValue ^ longValue >>> 32;
            case FLOAT:
                return Float.floatToIntBits(chunk.floatValue(slot, offset));
            case DOUBLE:
                long doubleBits = Double.doubleToLongBits(chunk.doubleValue(slot, offset));
                return doubleBits ^ doubleBits >>> 32;
            case REFERENCE:
                return referenceHash(chunk.referenceValue(slot, offset), equalityKinds[leaf]);
            default:
                throw new AssertionError("unknown leaf kind");
        }
    }

    private long hashValue(TypedValues values, int leaf) {
        int slot = leafSlots[leaf];
        switch (leafKinds[leaf]) {
            case BOOLEAN:
                return values.booleanValue(slot) ? 1231L : 1237L;
            case BYTE:
                return values.byteValue(slot);
            case SHORT:
                return values.shortValue(slot);
            case CHAR:
                return values.charValue(slot);
            case INT:
                return values.intValue(slot);
            case LONG:
                long longValue = values.longValue(slot);
                return longValue ^ longValue >>> 32;
            case FLOAT:
                return Float.floatToIntBits(values.floatValue(slot));
            case DOUBLE:
                long doubleBits = Double.doubleToLongBits(values.doubleValue(slot));
                return doubleBits ^ doubleBits >>> 32;
            case REFERENCE:
                return referenceHash(values.reference(slot), equalityKinds[leaf]);
            default:
                throw new AssertionError("unknown leaf kind");
        }
    }

    private boolean leafEquals(
            TableChunk chunk,
            int offset,
            TypedValues values,
            int leaf) {
        int slot = leafSlots[leaf];
        switch (leafKinds[leaf]) {
            case BOOLEAN:
                return chunk.booleanValue(slot, offset) == values.booleanValue(slot);
            case BYTE:
                return chunk.byteValue(slot, offset) == values.byteValue(slot);
            case SHORT:
                return chunk.shortValue(slot, offset) == values.shortValue(slot);
            case CHAR:
                return chunk.charValue(slot, offset) == values.charValue(slot);
            case INT:
                return chunk.intValue(slot, offset) == values.intValue(slot);
            case LONG:
                return chunk.longValue(slot, offset) == values.longValue(slot);
            case FLOAT:
                return Float.floatToIntBits(chunk.floatValue(slot, offset))
                        == Float.floatToIntBits(values.floatValue(slot));
            case DOUBLE:
                return Double.doubleToLongBits(chunk.doubleValue(slot, offset))
                        == Double.doubleToLongBits(values.doubleValue(slot));
            case REFERENCE:
                return referenceEquals(
                        chunk.referenceValue(slot, offset),
                        values.reference(slot),
                        equalityKinds[leaf]);
            default:
                throw new AssertionError("unknown leaf kind");
        }
    }

    private boolean leafEquals(TypedValues left, TypedValues right, int leaf) {
        int slot = leafSlots[leaf];
        switch (leafKinds[leaf]) {
            case BOOLEAN:
                return left.booleanValue(slot) == right.booleanValue(slot);
            case BYTE:
                return left.byteValue(slot) == right.byteValue(slot);
            case SHORT:
                return left.shortValue(slot) == right.shortValue(slot);
            case CHAR:
                return left.charValue(slot) == right.charValue(slot);
            case INT:
                return left.intValue(slot) == right.intValue(slot);
            case LONG:
                return left.longValue(slot) == right.longValue(slot);
            case FLOAT:
                return Float.floatToIntBits(left.floatValue(slot))
                        == Float.floatToIntBits(right.floatValue(slot));
            case DOUBLE:
                return Double.doubleToLongBits(left.doubleValue(slot))
                        == Double.doubleToLongBits(right.doubleValue(slot));
            case REFERENCE:
                return referenceEquals(
                        left.reference(slot), right.reference(slot), equalityKinds[leaf]);
            default:
                throw new AssertionError("unknown leaf kind");
        }
    }

    private static long referenceHash(Object value, byte equality) {
        if (value == null) {
            return 0L;
        }
        if (equality == EQ_STRING_CONTENT) {
            return value.hashCode();
        }
        if (equality == EQ_ENUM_IDENTITY || equality == EQ_OBJECT_IDENTITY) {
            return System.identityHashCode(value);
        }
        throw new AssertionError("invalid reference equality kind");
    }

    private static boolean referenceEquals(Object left, Object right, byte equality) {
        if (equality == EQ_STRING_CONTENT) {
            return left == right || left != null && left.equals(right);
        }
        if (equality == EQ_ENUM_IDENTITY || equality == EQ_OBJECT_IDENTITY) {
            return left == right;
        }
        throw new AssertionError("invalid reference equality kind");
    }

    private void requireLeaf(int leaf) {
        if (leaf < 0 || leaf >= leafKinds.length) {
            throw new AssertionError("invalid generated leaf ordinal");
        }
    }

    private void requireField(int field) {
        if (field < 0 || field >= fieldStarts.length) {
            throw new AssertionError("invalid generated Field ordinal");
        }
    }

    private static void validateLeaf(byte kind, byte equality) {
        if (kind < BOOLEAN || kind > REFERENCE
                || equality < EQ_BOOLEAN || equality > EQ_OBJECT_IDENTITY) {
            throw new AssertionError("invalid generated leaf contract");
        }
        if (kind == REFERENCE) {
            if (equality != EQ_STRING_CONTENT
                    && equality != EQ_ENUM_IDENTITY
                    && equality != EQ_OBJECT_IDENTITY) {
                throw new AssertionError("reference leaf has primitive equality");
            }
        } else if (equality != kind) {
            throw new AssertionError("primitive leaf kind/equality mismatch");
        }
    }

    private static long width(byte kind) {
        switch (kind) {
            case BOOLEAN:
            case BYTE:
                return 1L;
            case SHORT:
            case CHAR:
                return 2L;
            case INT:
            case FLOAT:
                return 4L;
            case LONG:
            case DOUBLE:
            case REFERENCE:
                return 8L;
            default:
                throw new AssertionError("unknown leaf kind");
        }
    }

    private static long mixPart(long current, long part) {
        return current * 31L + part;
    }

    private static long finishHash(long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53l;
        mixed ^= mixed >>> 33;
        return mixed;
    }
}
