package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaOperation;
import java.util.Arrays;

/** Sparse int-domain radix directory over representation-independent Table chunks. */
final class TableChunkDirectory {

    private static final int RADIX_BITS = 8;
    private static final int RADIX_SIZE = 1 << RADIX_BITS;
    private static final int RADIX_MASK = RADIX_SIZE - 1;
    private static final int LEVELS = 4;
    private static final long ESTIMATED_NODE_BYTES = 96L + 2L * RADIX_SIZE * 8L;

    private final int chunkRows;
    private final GeneratedTableLayout layout;
    private BranchNode root;
    private int chunkCount;
    /*
     * Chunk representations change only on candidate-directory construction.
     * Ordinary append writes populate an already allocated PlainChunk, so its
     * managed size is stable.  Cache the aggregate on the directory generation
     * instead of walking every chunk after every atomic add.
     */
    private volatile long cachedChunkManagedBytes = -1L;
    private int[] touched = new int[0];
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
            int requiredChunks,
            GeneratedTableLayout layout) {
        if (layout != source.layout) {
            throw new AssertionError("Table layout identity changed");
        }
        if (requiredChunks < source.chunkCount) {
            throw new AssertionError("Table Chunk directory cannot shrink");
        }
        TableChunkDirectory result = source.shallowCopy();
        for (int ordinal = source.chunkCount; ordinal < requiredChunks; ordinal++) {
            result.append(new PlainChunk(layout, source.chunkRows));
        }
        return result;
    }

    TableChunkDirectory copyForUpdate(int locator, TypedValues values) {
        int ordinal = locator / chunkRows;
        int offset = locator % chunkRows;
        TableChunkDirectory result = shallowCopy();
        TableChunk replacement = get(ordinal).mutableCopy(layout);
        result.replaceTouched(ordinal, replacement);
        replacement.write(offset, values, layout);
        return result;
    }

    TableChunkDirectory copyForUpdates(IntLocatorBuffer locators) {
        TableChunkDirectory result = shallowCopy();
        int[] sorted = Arrays.copyOf(locators.backing(), locators.size());
        Arrays.sort(sorted);
        int previousOrdinal = -1;
        for (int locator : sorted) {
            int ordinal = locator / chunkRows;
            if (ordinal == previousOrdinal) continue;
            result.replaceTouched(ordinal, get(ordinal).mutableCopy(layout));
            previousOrdinal = ordinal;
        }
        return result;
    }

    TableChunkDirectory copyForSelectionRemove(
            IntLocatorBuffer selection,
            int size,
            TypedValues scratch,
            Object provenance) {
        int selected = selection.size();
        int newSize = size - selected;
        int[] removed = Arrays.copyOf(selection.backing(), selected);
        Arrays.sort(removed);
        for (int index = 0; index < removed.length; index++) {
            if (removed[index] < 0 || removed[index] >= size
                    || (index != 0 && removed[index] == removed[index - 1])) {
                throw new AssertionError("invalid frozen Selection membership");
            }
        }

        int affectedLength = RowExecutionSupport.arrayLength(
                CheckedLong.multiply(
                        selected, 2L, SomaOperation.REMOVE, provenance),
                SomaOperation.REMOVE,
                provenance);
        int[] affectedChunks = new int[affectedLength];
        int affected = 0;
        for (int locator : removed) {
            affectedChunks[affected++] = locator / chunkRows;
        }
        for (int locator = newSize; locator < size; locator++) {
            affectedChunks[affected++] = locator / chunkRows;
        }
        Arrays.sort(affectedChunks);

        TableChunkDirectory result = shallowCopy();
        int previousOrdinal = -1;
        for (int ordinal : affectedChunks) {
            if (ordinal == previousOrdinal) continue;
            result.replaceTouched(ordinal, get(ordinal).mutableCopy(layout));
            previousOrdinal = ordinal;
        }

        int tail = size - 1;
        for (int hole : removed) {
            if (hole >= newSize) break;
            while (Arrays.binarySearch(removed, tail) >= 0) tail--;
            read(tail, scratch);
            result.write(hole, scratch);
            tail--;
        }
        for (int locator = newSize; locator < size; locator++) {
            result.clear(locator);
        }
        scratch.clearReferences();
        return result;
    }

    TableChunkDirectory copyForRemove(int locator, int size) {
        if (locator < 0 || locator >= size || size <= 0) {
            throw new AssertionError("invalid Table remove locator");
        }
        int tail = size - 1;
        int targetOrdinal = locator / chunkRows;
        int tailOrdinal = tail / chunkRows;
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
        int targetOffset = locator % chunkRows;
        int tailOffset = tail % chunkRows;
        if (locator != tail) {
            TypedValues scratch = new TypedValues(layout);
            tailChunk.read(tailOffset, scratch, layout);
            targetChunk.write(targetOffset, scratch, layout);
            scratch.clearReferences();
        }
        tailChunk.clear(tailOffset, layout);
        return result;
    }

    boolean canRemoveInPlace(int locator, int size) {
        if (locator < 0 || locator >= size || size <= 0) {
            throw new AssertionError("invalid Table remove locator");
        }
        int tail = size - 1;
        return get(locator / chunkRows) instanceof PlainChunk
                && get(tail / chunkRows) instanceof PlainChunk;
    }

    /**
     * Executes the bounded, allocation-free payload part of a prevalidated
     * point remove. The caller owns the exclusive Group final-commit window.
     */
    void removeInPlace(int locator, int size) {
        if (!canRemoveInPlace(locator, size)) {
            throw new AssertionError("in-place remove requires PLAIN chunks");
        }
        int tail = size - 1;
        PlainChunk targetChunk = (PlainChunk) get(locator / chunkRows);
        PlainChunk tailChunk = (PlainChunk) get(tail / chunkRows);
        int tailOffset = tail % chunkRows;
        if (locator != tail) {
            targetChunk.copyRowFrom(
                    tailChunk, tailOffset, locator % chunkRows);
        }
        tailChunk.clear(tailOffset, layout);
    }

    void finishTouched(
            int size,
            SomaCompression compression,
            SomaOperation operation,
            Object provenance) {
        for (int index = 0; index < touchedCount; index++) {
            int ordinal = touched[index];
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

    void read(int locator, TypedValues destination) {
        int ordinal = locator / chunkRows;
        int offset = locator % chunkRows;
        get(ordinal).read(offset, destination, layout);
    }

    void write(int locator, TypedValues source) {
        get(locator / chunkRows).write(
                locator % chunkRows, source, layout);
    }

    private void clear(int locator) {
        get(locator / chunkRows).clear(
                locator % chunkRows, layout);
    }

    TableChunk chunk(int ordinal) {
        return get(ordinal);
    }

    PlainChunk plainChunk(int ordinal) {
        return get(ordinal).materialize(layout);
    }

    boolean booleanValue(int locator, int slot) {
        return get(locator / chunkRows).booleanValue(
                slot, locator % chunkRows);
    }

    byte byteValue(int locator, int slot) {
        return get(locator / chunkRows).byteValue(
                slot, locator % chunkRows);
    }

    short shortValue(int locator, int slot) {
        return get(locator / chunkRows).shortValue(
                slot, locator % chunkRows);
    }

    char charValue(int locator, int slot) {
        return get(locator / chunkRows).charValue(
                slot, locator % chunkRows);
    }

    int intValue(int locator, int slot) {
        return get(locator / chunkRows).intValue(
                slot, locator % chunkRows);
    }

    long longValue(int locator, int slot) {
        return get(locator / chunkRows).longValue(
                slot, locator % chunkRows);
    }

    float floatValue(int locator, int slot) {
        return get(locator / chunkRows).floatValue(
                slot, locator % chunkRows);
    }

    double doubleValue(int locator, int slot) {
        return get(locator / chunkRows).doubleValue(
                slot, locator % chunkRows);
    }

    Object referenceValue(int locator, int slot) {
        return get(locator / chunkRows).referenceValue(
                slot, locator % chunkRows);
    }

    int chunkRows() {
        return chunkRows;
    }

    int chunkCount() {
        return chunkCount;
    }

    long chunkManagedBytes(SomaOperation operation, Object provenance) {
        long cached = cachedChunkManagedBytes;
        if (cached >= 0L) return cached;
        long result = 0L;
        for (int ordinal = 0; ordinal < chunkCount; ordinal++) {
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
        for (int ordinal = 0; ordinal < chunkCount; ordinal++) {
            if (get(ordinal).hasEncodedRepresentation()) result++;
        }
        return result;
    }

    long fieldPlainEquivalentBytes(
            int fieldIndex,
            SomaOperation operation,
            Object provenance) {
        long result = 0L;
        for (int ordinal = 0; ordinal < chunkCount; ordinal++) {
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
        for (int ordinal = 0; ordinal < chunkCount; ordinal++) {
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
            int chunks,
            SomaOperation operation,
            Object provenance) {
        if (chunks == 0) return 0L;
        return CheckedLong.multiply(
                CheckedLong.multiply(chunks, LEVELS, operation, provenance),
                ESTIMATED_NODE_BYTES,
                operation,
                provenance);
    }

    private TableChunkDirectory shallowCopy() {
        TableChunkDirectory result = new TableChunkDirectory(chunkRows, layout);
        for (int ordinal = 0; ordinal < chunkCount; ordinal++) {
            result.append(get(ordinal));
        }
        result.cachedChunkManagedBytes = cachedChunkManagedBytes;
        return result;
    }

    private TableChunk get(int ordinal) {
        if (ordinal < 0 || ordinal >= chunkCount || root == null) {
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
        int ordinal = chunkCount;
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

    private void replaceTouched(int ordinal, TableChunk replacement) {
        set(ordinal, replacement);
        markTouched(ordinal);
    }

    private void set(int ordinal, TableChunk replacement) {
        if (replacement == null || ordinal < 0 || ordinal >= chunkCount) {
            throw new AssertionError("invalid Table Chunk replacement");
        }
        BranchNode branch = root;
        for (int level = LEVELS - 1; level > 1; level--) {
            branch = branch.branches[digit(ordinal, level)];
        }
        branch.leaves[digit(ordinal, 1)].chunks[digit(ordinal, 0)] = replacement;
        cachedChunkManagedBytes = -1L;
    }

    private void markTouched(int ordinal) {
        for (int index = 0; index < touchedCount; index++) {
            if (touched[index] == ordinal) return;
        }
        if (touchedCount == touched.length) {
            int capacity = touched.length == 0 ? 4 : touched.length << 1;
            touched = Arrays.copyOf(touched, capacity);
        }
        touched[touchedCount++] = ordinal;
    }

    private static int digit(int ordinal, int level) {
        return (ordinal >>> (level * RADIX_BITS)) & RADIX_MASK;
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
