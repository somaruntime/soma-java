package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;

/** Sparse eight-level radix directory over the non-negative long chunk domain. */
final class PagedChunkDirectory {

    private static final int RADIX_BITS = 8;
    private static final int RADIX_SIZE = 1 << RADIX_BITS;
    private static final int RADIX_MASK = RADIX_SIZE - 1;
    private static final int LEVELS = 8;
    private static final long ESTIMATED_NODE_BYTES = 96L + 2L * RADIX_SIZE * 8L;

    private BranchNode root;
    private long chunkCount;

    private PagedChunkDirectory() {
    }

    static PagedChunkDirectory empty() {
        return new PagedChunkDirectory();
    }

    static PagedChunkDirectory grow(
            PagedChunkDirectory source,
            long requiredChunks,
            int fieldCount,
            int chunkRows) {
        PagedChunkDirectory result = new PagedChunkDirectory();
        for (long ordinal = 0L; ordinal < source.chunkCount; ordinal++) {
            result.put(ordinal, source.get(ordinal));
        }
        for (long ordinal = source.chunkCount; ordinal < requiredChunks; ordinal++) {
            result.put(ordinal, new PlainLongChunk(fieldCount, chunkRows));
        }
        return result;
    }

    PagedChunkDirectory copyReplacing(long ordinal, PlainLongChunk replacement) {
        PagedChunkDirectory result = new PagedChunkDirectory();
        for (long current = 0L; current < chunkCount; current++) {
            result.put(current, current == ordinal ? replacement : get(current));
        }
        return result;
    }

    long chunkCount() {
        return chunkCount;
    }

    PlainLongChunk plainChunk(long ordinal) {
        LongChunk chunk = get(ordinal);
        switch (chunk.representation()) {
            case PLAIN:
                return (PlainLongChunk) chunk;
            default:
                throw new AssertionError("unknown SOMA Chunk representation");
        }
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

    private LongChunk get(long ordinal) {
        if (ordinal < 0L || ordinal >= chunkCount || root == null) {
            throw new AssertionError("invalid SOMA Chunk ordinal");
        }
        BranchNode branch = root;
        for (int level = LEVELS - 1; level > 1; level--) {
            int index = digit(ordinal, level);
            branch = branch.branches[index];
            if (branch == null) {
                throw new AssertionError("missing SOMA directory branch");
            }
        }
        LeafNode leaf = branch.leaves[digit(ordinal, 1)];
        if (leaf == null) {
            throw new AssertionError("missing SOMA directory leaf");
        }
        LongChunk chunk = leaf.chunks[digit(ordinal, 0)];
        if (chunk == null) {
            throw new AssertionError("missing SOMA Chunk");
        }
        return chunk;
    }

    private void put(long ordinal, LongChunk chunk) {
        if (ordinal != chunkCount || ordinal < 0L || chunk == null) {
            throw new AssertionError("non-canonical SOMA Chunk append");
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

    private static int digit(long ordinal, int level) {
        return (int) ((ordinal >>> (level * RADIX_BITS)) & RADIX_MASK);
    }

    private static final class BranchNode {

        private final BranchNode[] branches = new BranchNode[RADIX_SIZE];
        private final LeafNode[] leaves = new LeafNode[RADIX_SIZE];
    }

    private static final class LeafNode {

        private final LongChunk[] chunks = new LongChunk[RADIX_SIZE];
    }
}
