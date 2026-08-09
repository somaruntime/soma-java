package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;
import java.util.Arrays;

/** Sparse eight-level radix directory over exact-typed PLAIN Table chunks. */
final class TableChunkDirectory {

    private static final int RADIX_BITS = 8;
    private static final int RADIX_SIZE = 1 << RADIX_BITS;
    private static final int RADIX_MASK = RADIX_SIZE - 1;
    private static final int LEVELS = 8;
    private static final long ESTIMATED_NODE_BYTES = 96L + 2L * RADIX_SIZE * 8L;

    private final int chunkRows;
    private BranchNode root;
    private long chunkCount;

    private TableChunkDirectory(int chunkRows) {
        this.chunkRows = chunkRows;
    }

    static TableChunkDirectory empty(int chunkRows) {
        requireChunkRows(chunkRows);
        return new TableChunkDirectory(chunkRows);
    }

    static TableChunkDirectory grow(
            TableChunkDirectory source,
            long requiredChunks,
            GeneratedTableLayout layout) {
        if (requiredChunks < source.chunkCount) {
            throw new AssertionError("Table Chunk directory cannot shrink");
        }
        TableChunkDirectory result = source.shallowCopy();
        for (long ordinal = source.chunkCount; ordinal < requiredChunks; ordinal++) {
            result.append(new PlainChunk(layout, source.chunkRows));
        }
        return result;
    }

    TableChunkDirectory copyForUpdate(long locator, TypedValues values) {
        long ordinal = locator / chunkRows;
        int offset = (int) (locator % chunkRows);
        TableChunkDirectory result = shallowCopy();
        PlainChunk replacement = get(ordinal).copy();
        result.replace(ordinal, replacement);
        write(replacement, offset, values);
        return result;
    }

    TableChunkDirectory copyForUpdates(LongLocatorBuffer locators) {
        TableChunkDirectory result = shallowCopy();
        long[] sorted = Arrays.copyOf(locators.backing(), locators.size());
        Arrays.sort(sorted);
        long previousOrdinal = -1L;
        for (long locator : sorted) {
            long ordinal = locator / chunkRows;
            if (ordinal == previousOrdinal) continue;
            result.replace(ordinal, get(ordinal).copy());
            previousOrdinal = ordinal;
        }
        return result;
    }

    TableChunkDirectory copyForSelectionRemove(
            LongLocatorBuffer selection,
            long size,
            TypedValues scratch,
            Object provenance) {
        int selected = selection.size();
        long newSize = size - selected;
        long[] removed = Arrays.copyOf(selection.backing(), selected);
        Arrays.sort(removed);
        for (int index = 0; index < removed.length; index++) {
            if (removed[index] < 0L || removed[index] >= size
                    || (index != 0 && removed[index] == removed[index - 1])) {
                throw new AssertionError("invalid frozen Selection membership");
            }
        }

        int affectedLength = RowExecutionSupport.arrayLength(
                CheckedLong.multiply(
                        selected, 2L, SomaOperation.REMOVE, provenance),
                SomaOperation.REMOVE,
                provenance);
        long[] affectedChunks = new long[affectedLength];
        int affected = 0;
        for (long locator : removed) {
            affectedChunks[affected++] = locator / chunkRows;
        }
        for (long locator = newSize; locator < size; locator++) {
            affectedChunks[affected++] = locator / chunkRows;
        }
        Arrays.sort(affectedChunks);

        TableChunkDirectory result = shallowCopy();
        long previousOrdinal = -1L;
        for (long ordinal : affectedChunks) {
            if (ordinal == previousOrdinal) continue;
            result.replace(ordinal, get(ordinal).copy());
            previousOrdinal = ordinal;
        }

        long tail = size - 1L;
        for (long hole : removed) {
            if (hole >= newSize) break;
            while (Arrays.binarySearch(removed, tail) >= 0) tail--;
            read(tail, scratch);
            result.write(hole, scratch);
            tail--;
        }
        for (long locator = newSize; locator < size; locator++) {
            result.clear(locator);
        }
        scratch.clearReferences();
        return result;
    }

    TableChunkDirectory copyForRemove(long locator, long size) {
        if (locator < 0L || locator >= size || size <= 0L) {
            throw new AssertionError("invalid Table remove locator");
        }
        long tail = size - 1L;
        long targetOrdinal = locator / chunkRows;
        long tailOrdinal = tail / chunkRows;
        TableChunkDirectory result = shallowCopy();
        PlainChunk targetChunk = get(targetOrdinal).copy();
        result.replace(targetOrdinal, targetChunk);
        PlainChunk tailChunk;
        if (tailOrdinal == targetOrdinal) {
            tailChunk = targetChunk;
        } else {
            tailChunk = get(tailOrdinal).copy();
            result.replace(tailOrdinal, tailChunk);
        }
        int targetOffset = (int) (locator % chunkRows);
        int tailOffset = (int) (tail % chunkRows);
        if (locator != tail) {
            copyRow(tailChunk, tailOffset, targetChunk, targetOffset);
        }
        clearRow(tailChunk, tailOffset);
        return result;
    }

    void read(long locator, TypedValues destination) {
        long ordinal = locator / chunkRows;
        int offset = (int) (locator % chunkRows);
        PlainChunk chunk = get(ordinal);
        for (int slot = 0; slot < chunk.booleanCount(); slot++) {
            destination.booleanValue(slot, chunk.booleans(slot)[offset]);
        }
        for (int slot = 0; slot < chunk.byteCount(); slot++) {
            destination.byteValue(slot, chunk.bytes(slot)[offset]);
        }
        for (int slot = 0; slot < chunk.shortCount(); slot++) {
            destination.shortValue(slot, chunk.shorts(slot)[offset]);
        }
        for (int slot = 0; slot < chunk.charCount(); slot++) {
            destination.charValue(slot, chunk.chars(slot)[offset]);
        }
        for (int slot = 0; slot < chunk.intCount(); slot++) {
            destination.intValue(slot, chunk.ints(slot)[offset]);
        }
        for (int slot = 0; slot < chunk.longCount(); slot++) {
            destination.longValue(slot, chunk.longs(slot)[offset]);
        }
        for (int slot = 0; slot < chunk.floatCount(); slot++) {
            destination.floatValue(slot, chunk.floats(slot)[offset]);
        }
        for (int slot = 0; slot < chunk.doubleCount(); slot++) {
            destination.doubleValue(slot, chunk.doubles(slot)[offset]);
        }
        for (int slot = 0; slot < chunk.referenceCount(); slot++) {
            destination.reference(slot, chunk.references(slot)[offset]);
        }
    }

    void write(long locator, TypedValues source) {
        write(get(locator / chunkRows), (int) (locator % chunkRows), source);
    }

    private void clear(long locator) {
        clearRow(get(locator / chunkRows), (int) (locator % chunkRows));
    }

    PlainChunk plainChunk(long ordinal) {
        return get(ordinal);
    }

    int chunkRows() {
        return chunkRows;
    }

    long chunkCount() {
        return chunkCount;
    }

    static long estimatedDirectoryBytes(
            long chunks,
            SomaOperation operation,
            Object provenance) {
        if (chunks == 0L) {
            return 0L;
        }
        return CheckedLong.multiply(
                CheckedLong.multiply(chunks, LEVELS, operation, provenance),
                ESTIMATED_NODE_BYTES,
                operation,
                provenance);
    }

    private TableChunkDirectory shallowCopy() {
        TableChunkDirectory result = new TableChunkDirectory(chunkRows);
        for (long ordinal = 0L; ordinal < chunkCount; ordinal++) {
            result.append(get(ordinal));
        }
        return result;
    }

    private PlainChunk get(long ordinal) {
        if (ordinal < 0L || ordinal >= chunkCount || root == null) {
            throw new AssertionError("invalid Table Chunk ordinal");
        }
        BranchNode branch = root;
        for (int level = LEVELS - 1; level > 1; level--) {
            branch = branch.branches[digit(ordinal, level)];
            if (branch == null) {
                throw new AssertionError("missing Table directory branch");
            }
        }
        LeafNode leaf = branch.leaves[digit(ordinal, 1)];
        if (leaf == null) {
            throw new AssertionError("missing Table directory leaf");
        }
        PlainChunk chunk = leaf.chunks[digit(ordinal, 0)];
        if (chunk == null) {
            throw new AssertionError("missing Table Chunk");
        }
        return chunk;
    }

    private void append(PlainChunk chunk) {
        long ordinal = chunkCount;
        if (chunk == null) {
            throw new AssertionError("null Table Chunk");
        }
        if (root == null) {
            root = new BranchNode();
        }
        BranchNode branch = root;
        for (int level = LEVELS - 1; level > 1; level--) {
            int index = digit(ordinal, level);
            BranchNode next = branch.branches[index];
            if (next == null) {
                next = new BranchNode();
                branch.branches[index] = next;
            }
            branch = next;
        }
        int leafIndex = digit(ordinal, 1);
        LeafNode leaf = branch.leaves[leafIndex];
        if (leaf == null) {
            leaf = new LeafNode();
            branch.leaves[leafIndex] = leaf;
        }
        leaf.chunks[digit(ordinal, 0)] = chunk;
        chunkCount++;
    }

    private void replace(long ordinal, PlainChunk replacement) {
        if (replacement == null || ordinal < 0L || ordinal >= chunkCount) {
            throw new AssertionError("invalid Table Chunk replacement");
        }
        BranchNode branch = root;
        for (int level = LEVELS - 1; level > 1; level--) {
            branch = branch.branches[digit(ordinal, level)];
        }
        branch.leaves[digit(ordinal, 1)].chunks[digit(ordinal, 0)] = replacement;
    }

    private static void write(PlainChunk chunk, int offset, TypedValues source) {
        for (int slot = 0; slot < chunk.booleanCount(); slot++) {
            chunk.booleans(slot)[offset] = source.booleanValue(slot);
        }
        for (int slot = 0; slot < chunk.byteCount(); slot++) {
            chunk.bytes(slot)[offset] = source.byteValue(slot);
        }
        for (int slot = 0; slot < chunk.shortCount(); slot++) {
            chunk.shorts(slot)[offset] = source.shortValue(slot);
        }
        for (int slot = 0; slot < chunk.charCount(); slot++) {
            chunk.chars(slot)[offset] = source.charValue(slot);
        }
        for (int slot = 0; slot < chunk.intCount(); slot++) {
            chunk.ints(slot)[offset] = source.intValue(slot);
        }
        for (int slot = 0; slot < chunk.longCount(); slot++) {
            chunk.longs(slot)[offset] = source.longValue(slot);
        }
        for (int slot = 0; slot < chunk.floatCount(); slot++) {
            chunk.floats(slot)[offset] = source.floatValue(slot);
        }
        for (int slot = 0; slot < chunk.doubleCount(); slot++) {
            chunk.doubles(slot)[offset] = source.doubleValue(slot);
        }
        for (int slot = 0; slot < chunk.referenceCount(); slot++) {
            chunk.references(slot)[offset] = source.reference(slot);
        }
    }

    private static void copyRow(
            PlainChunk source,
            int sourceOffset,
            PlainChunk target,
            int targetOffset) {
        for (int slot = 0; slot < source.booleanCount(); slot++) {
            target.booleans(slot)[targetOffset] = source.booleans(slot)[sourceOffset];
        }
        for (int slot = 0; slot < source.byteCount(); slot++) {
            target.bytes(slot)[targetOffset] = source.bytes(slot)[sourceOffset];
        }
        for (int slot = 0; slot < source.shortCount(); slot++) {
            target.shorts(slot)[targetOffset] = source.shorts(slot)[sourceOffset];
        }
        for (int slot = 0; slot < source.charCount(); slot++) {
            target.chars(slot)[targetOffset] = source.chars(slot)[sourceOffset];
        }
        for (int slot = 0; slot < source.intCount(); slot++) {
            target.ints(slot)[targetOffset] = source.ints(slot)[sourceOffset];
        }
        for (int slot = 0; slot < source.longCount(); slot++) {
            target.longs(slot)[targetOffset] = source.longs(slot)[sourceOffset];
        }
        for (int slot = 0; slot < source.floatCount(); slot++) {
            target.floats(slot)[targetOffset] = source.floats(slot)[sourceOffset];
        }
        for (int slot = 0; slot < source.doubleCount(); slot++) {
            target.doubles(slot)[targetOffset] = source.doubles(slot)[sourceOffset];
        }
        for (int slot = 0; slot < source.referenceCount(); slot++) {
            target.references(slot)[targetOffset] = source.references(slot)[sourceOffset];
        }
    }

    private static void clearRow(PlainChunk chunk, int offset) {
        for (int slot = 0; slot < chunk.booleanCount(); slot++) chunk.booleans(slot)[offset] = false;
        for (int slot = 0; slot < chunk.byteCount(); slot++) chunk.bytes(slot)[offset] = 0;
        for (int slot = 0; slot < chunk.shortCount(); slot++) chunk.shorts(slot)[offset] = 0;
        for (int slot = 0; slot < chunk.charCount(); slot++) chunk.chars(slot)[offset] = 0;
        for (int slot = 0; slot < chunk.intCount(); slot++) chunk.ints(slot)[offset] = 0;
        for (int slot = 0; slot < chunk.longCount(); slot++) chunk.longs(slot)[offset] = 0L;
        for (int slot = 0; slot < chunk.floatCount(); slot++) chunk.floats(slot)[offset] = 0.0f;
        for (int slot = 0; slot < chunk.doubleCount(); slot++) chunk.doubles(slot)[offset] = 0.0d;
        for (int slot = 0; slot < chunk.referenceCount(); slot++) chunk.references(slot)[offset] = null;
    }

    private static int digit(long ordinal, int level) {
        return (int) ((ordinal >>> (level * RADIX_BITS)) & RADIX_MASK);
    }

    private static void requireChunkRows(int rows) {
        if (rows <= 0 || (rows & (rows - 1)) != 0) {
            throw new AssertionError("Table Chunk rows must be a positive power of two");
        }
    }

    private static final class BranchNode {
        private final BranchNode[] branches = new BranchNode[RADIX_SIZE];
        private final LeafNode[] leaves = new LeafNode[RADIX_SIZE];
    }

    private static final class LeafNode {
        private final PlainChunk[] chunks = new PlainChunk[RADIX_SIZE];
    }
}
