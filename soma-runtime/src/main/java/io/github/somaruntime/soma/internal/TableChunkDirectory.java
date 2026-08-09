package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaOperation;
import java.util.Arrays;

/** Sparse eight-level radix directory over representation-independent Table chunks. */
final class TableChunkDirectory {

    private static final int RADIX_BITS = 8;
    private static final int RADIX_SIZE = 1 << RADIX_BITS;
    private static final int RADIX_MASK = RADIX_SIZE - 1;
    private static final int LEVELS = 8;
    private static final long ESTIMATED_NODE_BYTES = 96L + 2L * RADIX_SIZE * 8L;

    private final int chunkRows;
    private final GeneratedTableLayout layout;
    private BranchNode root;
    private long chunkCount;
    /*
     * Chunk representations change only on candidate-directory construction.
     * Ordinary append writes populate an already allocated PlainChunk, so its
     * managed size is stable.  Cache the aggregate on the directory generation
     * instead of walking every chunk after every atomic add.
     */
    private volatile long cachedChunkManagedBytes = -1L;
    private long[] touched = new long[0];
    private int touchedCount;

    private TableChunkDirectory(int chunkRows, GeneratedTableLayout layout) {
        this.chunkRows = chunkRows;
        this.layout = layout;
    }

    static TableChunkDirectory empty(
            int chunkRows,
            GeneratedTableLayout layout) {
        requireChunkRows(chunkRows);
        if (layout == null) throw new AssertionError("null Table layout");
        return new TableChunkDirectory(chunkRows, layout);
    }

    static TableChunkDirectory grow(
            TableChunkDirectory source,
            long requiredChunks,
            GeneratedTableLayout layout) {
        if (layout != source.layout) {
            throw new AssertionError("Table layout identity changed");
        }
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
        TableChunk replacement = get(ordinal).mutableCopy(layout);
        result.replaceTouched(ordinal, replacement);
        replacement.write(offset, values, layout);
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
            result.replaceTouched(ordinal, get(ordinal).mutableCopy(layout));
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
            result.replaceTouched(ordinal, get(ordinal).mutableCopy(layout));
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
        TableChunk targetChunk = get(targetOrdinal).mutableCopy(layout);
        result.replaceTouched(targetOrdinal, targetChunk);
        TableChunk tailChunk;
        if (tailOrdinal == targetOrdinal) {
            tailChunk = targetChunk;
        } else {
            tailChunk = get(tailOrdinal).mutableCopy(layout);
            result.replaceTouched(tailOrdinal, tailChunk);
        }
        int targetOffset = (int) (locator % chunkRows);
        int tailOffset = (int) (tail % chunkRows);
        if (locator != tail) {
            TypedValues scratch = new TypedValues(layout);
            tailChunk.read(tailOffset, scratch, layout);
            targetChunk.write(targetOffset, scratch, layout);
            scratch.clearReferences();
        }
        tailChunk.clear(tailOffset, layout);
        return result;
    }

    void finishTouched(
            long size,
            SomaCompression compression,
            SomaOperation operation,
            Object provenance) {
        for (int index = 0; index < touchedCount; index++) {
            long ordinal = touched[index];
            long first = CheckedLong.multiply(
                    ordinal, chunkRows, operation, provenance);
            long remaining = Math.max(0L, size - first);
            int logicalRows = (int) Math.min((long) chunkRows, remaining);
            TableChunk finished = get(ordinal).finish(
                    logicalRows, compression, layout, operation, provenance);
            set(ordinal, finished);
        }
        touchedCount = 0;
    }

    void read(long locator, TypedValues destination) {
        long ordinal = locator / chunkRows;
        int offset = (int) (locator % chunkRows);
        get(ordinal).read(offset, destination, layout);
    }

    void write(long locator, TypedValues source) {
        get(locator / chunkRows).write(
                (int) (locator % chunkRows), source, layout);
    }

    private void clear(long locator) {
        get(locator / chunkRows).clear(
                (int) (locator % chunkRows), layout);
    }

    TableChunk chunk(long ordinal) {
        return get(ordinal);
    }

    PlainChunk plainChunk(long ordinal) {
        return get(ordinal).materialize(layout);
    }

    boolean booleanValue(long locator, int slot) {
        return get(locator / chunkRows).booleanValue(
                slot, (int) (locator % chunkRows));
    }

    byte byteValue(long locator, int slot) {
        return get(locator / chunkRows).byteValue(
                slot, (int) (locator % chunkRows));
    }

    short shortValue(long locator, int slot) {
        return get(locator / chunkRows).shortValue(
                slot, (int) (locator % chunkRows));
    }

    char charValue(long locator, int slot) {
        return get(locator / chunkRows).charValue(
                slot, (int) (locator % chunkRows));
    }

    int intValue(long locator, int slot) {
        return get(locator / chunkRows).intValue(
                slot, (int) (locator % chunkRows));
    }

    long longValue(long locator, int slot) {
        return get(locator / chunkRows).longValue(
                slot, (int) (locator % chunkRows));
    }

    float floatValue(long locator, int slot) {
        return get(locator / chunkRows).floatValue(
                slot, (int) (locator % chunkRows));
    }

    double doubleValue(long locator, int slot) {
        return get(locator / chunkRows).doubleValue(
                slot, (int) (locator % chunkRows));
    }

    Object referenceValue(long locator, int slot) {
        return get(locator / chunkRows).referenceValue(
                slot, (int) (locator % chunkRows));
    }

    int chunkRows() {
        return chunkRows;
    }

    long chunkCount() {
        return chunkCount;
    }

    long chunkManagedBytes(SomaOperation operation, Object provenance) {
        long cached = cachedChunkManagedBytes;
        if (cached >= 0L) return cached;
        long result = 0L;
        for (long ordinal = 0L; ordinal < chunkCount; ordinal++) {
            result = CheckedLong.add(
                    result,
                    get(ordinal).managedBytes(layout, operation, provenance),
                    operation,
                    provenance);
        }
        cachedChunkManagedBytes = result;
        return result;
    }

    long plainEquivalentBytes(SomaOperation operation, Object provenance) {
        return CheckedLong.multiply(
                chunkCount,
                ChunkEncoder.plainManagedBytes(
                        layout, chunkRows, operation, provenance),
                operation,
                provenance);
    }

    long encodedChunkCount() {
        long result = 0L;
        for (long ordinal = 0L; ordinal < chunkCount; ordinal++) {
            if (get(ordinal).hasEncodedRepresentation()) result++;
        }
        return result;
    }

    long fieldPlainEquivalentBytes(
            int fieldIndex,
            SomaOperation operation,
            Object provenance) {
        long result = 0L;
        for (long ordinal = 0L; ordinal < chunkCount; ordinal++) {
            result = CheckedLong.add(
                    result,
                    get(ordinal).plainEquivalentBytesForField(
                            layout, fieldIndex, operation, provenance),
                    operation,
                    provenance);
        }
        return result;
    }

    long fieldRepresentationBytes(
            int fieldIndex,
            SomaOperation operation,
            Object provenance) {
        long result = 0L;
        for (long ordinal = 0L; ordinal < chunkCount; ordinal++) {
            result = CheckedLong.add(
                    result,
                    get(ordinal).representationBytesForField(
                            layout, fieldIndex, operation, provenance),
                    operation,
                    provenance);
        }
        return result;
    }

    static long estimatedDirectoryBytes(
            long chunks,
            SomaOperation operation,
            Object provenance) {
        if (chunks == 0L) return 0L;
        return CheckedLong.multiply(
                CheckedLong.multiply(chunks, LEVELS, operation, provenance),
                ESTIMATED_NODE_BYTES,
                operation,
                provenance);
    }

    private TableChunkDirectory shallowCopy() {
        TableChunkDirectory result = new TableChunkDirectory(chunkRows, layout);
        for (long ordinal = 0L; ordinal < chunkCount; ordinal++) {
            result.append(get(ordinal));
        }
        result.cachedChunkManagedBytes = cachedChunkManagedBytes;
        return result;
    }

    private TableChunk get(long ordinal) {
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
        TableChunk chunk = leaf.chunks[digit(ordinal, 0)];
        if (chunk == null) throw new AssertionError("missing Table Chunk");
        return chunk;
    }

    private void append(TableChunk chunk) {
        long ordinal = chunkCount;
        if (chunk == null) throw new AssertionError("null Table Chunk");
        if (root == null) root = new BranchNode();
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
        cachedChunkManagedBytes = -1L;
    }

    private void replaceTouched(long ordinal, TableChunk replacement) {
        set(ordinal, replacement);
        markTouched(ordinal);
    }

    private void set(long ordinal, TableChunk replacement) {
        if (replacement == null || ordinal < 0L || ordinal >= chunkCount) {
            throw new AssertionError("invalid Table Chunk replacement");
        }
        BranchNode branch = root;
        for (int level = LEVELS - 1; level > 1; level--) {
            branch = branch.branches[digit(ordinal, level)];
        }
        branch.leaves[digit(ordinal, 1)].chunks[digit(ordinal, 0)] = replacement;
        cachedChunkManagedBytes = -1L;
    }

    private void markTouched(long ordinal) {
        for (int index = 0; index < touchedCount; index++) {
            if (touched[index] == ordinal) return;
        }
        if (touchedCount == touched.length) {
            int capacity = touched.length == 0 ? 4 : touched.length << 1;
            touched = Arrays.copyOf(touched, capacity);
        }
        touched[touchedCount++] = ordinal;
    }

    private static int digit(long ordinal, int level) {
        return (int) ((ordinal >>> (level * RADIX_BITS)) & RADIX_MASK);
    }

    private static void requireChunkRows(int rows) {
        if (rows <= 0 || (rows & (rows - 1)) != 0) {
            throw new AssertionError(
                    "Table Chunk rows must be a positive power of two");
        }
    }

    private static final class BranchNode {
        private final BranchNode[] branches = new BranchNode[RADIX_SIZE];
        private final LeafNode[] leaves = new LeafNode[RADIX_SIZE];
    }

    private static final class LeafNode {
        private final TableChunk[] chunks = new TableChunk[RADIX_SIZE];
    }
}
