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

    long hashField(TableChunkDirectory directory, long locator, int fieldIndex) {
        int start = fieldStart(fieldIndex);
        int count = fieldLeafCount(fieldIndex);
        long hash = 1L;
        PlainChunk chunk = directory.plainChunk(locator / directory.chunkRows());
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
        PlainChunk chunk = directory.plainChunk(locator / directory.chunkRows());
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
        PlainChunk left = directory.plainChunk(leftLocator / directory.chunkRows());
        PlainChunk right = directory.plainChunk(rightLocator / directory.chunkRows());
        int leftOffset = (int) (leftLocator % directory.chunkRows());
        int rightOffset = (int) (rightLocator % directory.chunkRows());
        for (int leaf = start; leaf < start + count; leaf++) {
            int slot = leafSlots[leaf];
            switch (leafKinds[leaf]) {
                case BOOLEAN:
                    if (left.booleans(slot)[leftOffset] != right.booleans(slot)[rightOffset]) return false;
                    break;
                case BYTE:
                    if (left.bytes(slot)[leftOffset] != right.bytes(slot)[rightOffset]) return false;
                    break;
                case SHORT:
                    if (left.shorts(slot)[leftOffset] != right.shorts(slot)[rightOffset]) return false;
                    break;
                case CHAR:
                    if (left.chars(slot)[leftOffset] != right.chars(slot)[rightOffset]) return false;
                    break;
                case INT:
                    if (left.ints(slot)[leftOffset] != right.ints(slot)[rightOffset]) return false;
                    break;
                case LONG:
                    if (left.longs(slot)[leftOffset] != right.longs(slot)[rightOffset]) return false;
                    break;
                case FLOAT:
                    if (Float.floatToIntBits(left.floats(slot)[leftOffset])
                            != Float.floatToIntBits(right.floats(slot)[rightOffset])) return false;
                    break;
                case DOUBLE:
                    if (Double.doubleToLongBits(left.doubles(slot)[leftOffset])
                            != Double.doubleToLongBits(right.doubles(slot)[rightOffset])) return false;
                    break;
                case REFERENCE:
                    if (!referenceEquals(
                            left.references(slot)[leftOffset],
                            right.references(slot)[rightOffset],
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
        PlainChunk left = leftDirectory.plainChunk(
                leftLocator / leftDirectory.chunkRows());
        PlainChunk right = rightDirectory.plainChunk(
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
                    if (left.booleans(leftSlot)[leftOffset]
                            != right.booleans(rightSlot)[rightOffset]) return false;
                    break;
                case BYTE:
                    if (left.bytes(leftSlot)[leftOffset]
                            != right.bytes(rightSlot)[rightOffset]) return false;
                    break;
                case SHORT:
                    if (left.shorts(leftSlot)[leftOffset]
                            != right.shorts(rightSlot)[rightOffset]) return false;
                    break;
                case CHAR:
                    if (left.chars(leftSlot)[leftOffset]
                            != right.chars(rightSlot)[rightOffset]) return false;
                    break;
                case INT:
                    if (left.ints(leftSlot)[leftOffset]
                            != right.ints(rightSlot)[rightOffset]) return false;
                    break;
                case LONG:
                    if (left.longs(leftSlot)[leftOffset]
                            != right.longs(rightSlot)[rightOffset]) return false;
                    break;
                case REFERENCE:
                    if (!referenceEquals(
                            left.references(leftSlot)[leftOffset],
                            right.references(rightSlot)[rightOffset],
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
        PlainChunk chunk = directory.plainChunk(locator / directory.chunkRows());
        return chunk.references(leafSlot(leaf))[(int) (locator % directory.chunkRows())] == null;
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
        PlainChunk chunk = directory.plainChunk(locator / directory.chunkRows());
        int offset = (int) (locator % directory.chunkRows());
        int slot = leafSlot(leaf);
        switch (leafKinds[leaf]) {
            case BYTE:
                return Byte.compare(chunk.bytes(slot)[offset], literal.byteValue(slot));
            case SHORT:
                return Short.compare(chunk.shorts(slot)[offset], literal.shortValue(slot));
            case CHAR:
                return Character.compare(chunk.chars(slot)[offset], literal.charValue(slot));
            case INT:
                return Integer.compare(chunk.ints(slot)[offset], literal.intValue(slot));
            case LONG:
                return Long.compare(chunk.longs(slot)[offset], literal.longValue(slot));
            case FLOAT:
                return Float.compare(chunk.floats(slot)[offset], literal.floatValue(slot));
            case DOUBLE:
                return Double.compare(chunk.doubles(slot)[offset], literal.doubleValue(slot));
            case REFERENCE:
                Object left = chunk.references(slot)[offset];
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
        PlainChunk left = directory.plainChunk(leftLocator / directory.chunkRows());
        PlainChunk right = directory.plainChunk(rightLocator / directory.chunkRows());
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
            PlainChunk left,
            int leftOffset,
            PlainChunk right,
            int rightOffset,
            int leaf) {
        int slot = leafSlot(leaf);
        switch (leafKinds[leaf]) {
            case BOOLEAN:
                return Boolean.compare(
                        left.booleans(slot)[leftOffset], right.booleans(slot)[rightOffset]);
            case BYTE:
                return Byte.compare(
                        left.bytes(slot)[leftOffset], right.bytes(slot)[rightOffset]);
            case SHORT:
                return Short.compare(
                        left.shorts(slot)[leftOffset], right.shorts(slot)[rightOffset]);
            case CHAR:
                return Character.compare(
                        left.chars(slot)[leftOffset], right.chars(slot)[rightOffset]);
            case INT:
                return Integer.compare(
                        left.ints(slot)[leftOffset], right.ints(slot)[rightOffset]);
            case LONG:
                return Long.compare(
                        left.longs(slot)[leftOffset], right.longs(slot)[rightOffset]);
            case FLOAT:
                return Float.compare(
                        left.floats(slot)[leftOffset], right.floats(slot)[rightOffset]);
            case DOUBLE:
                return Double.compare(
                        left.doubles(slot)[leftOffset], right.doubles(slot)[rightOffset]);
            case REFERENCE:
                Object leftValue = left.references(slot)[leftOffset];
                Object rightValue = right.references(slot)[rightOffset];
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

    private long hashStored(PlainChunk chunk, int offset, int leaf) {
        int slot = leafSlots[leaf];
        switch (leafKinds[leaf]) {
            case BOOLEAN:
                return chunk.booleans(slot)[offset] ? 1231L : 1237L;
            case BYTE:
                return chunk.bytes(slot)[offset];
            case SHORT:
                return chunk.shorts(slot)[offset];
            case CHAR:
                return chunk.chars(slot)[offset];
            case INT:
                return chunk.ints(slot)[offset];
            case LONG:
                long longValue = chunk.longs(slot)[offset];
                return longValue ^ longValue >>> 32;
            case FLOAT:
                return Float.floatToIntBits(chunk.floats(slot)[offset]);
            case DOUBLE:
                long doubleBits = Double.doubleToLongBits(chunk.doubles(slot)[offset]);
                return doubleBits ^ doubleBits >>> 32;
            case REFERENCE:
                return referenceHash(chunk.references(slot)[offset], equalityKinds[leaf]);
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
            PlainChunk chunk,
            int offset,
            TypedValues values,
            int leaf) {
        int slot = leafSlots[leaf];
        switch (leafKinds[leaf]) {
            case BOOLEAN:
                return chunk.booleans(slot)[offset] == values.booleanValue(slot);
            case BYTE:
                return chunk.bytes(slot)[offset] == values.byteValue(slot);
            case SHORT:
                return chunk.shorts(slot)[offset] == values.shortValue(slot);
            case CHAR:
                return chunk.chars(slot)[offset] == values.charValue(slot);
            case INT:
                return chunk.ints(slot)[offset] == values.intValue(slot);
            case LONG:
                return chunk.longs(slot)[offset] == values.longValue(slot);
            case FLOAT:
                return Float.floatToIntBits(chunk.floats(slot)[offset])
                        == Float.floatToIntBits(values.floatValue(slot));
            case DOUBLE:
                return Double.doubleToLongBits(chunk.doubles(slot)[offset])
                        == Double.doubleToLongBits(values.doubleValue(slot));
            case REFERENCE:
                return referenceEquals(
                        chunk.references(slot)[offset],
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
