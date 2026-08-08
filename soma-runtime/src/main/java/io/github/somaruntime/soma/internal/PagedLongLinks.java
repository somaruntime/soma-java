package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;

/** Long-domain paged next-locator storage for non-unique Index postings. */
final class PagedLongLinks {

    private static final int RADIX_BITS = 8;
    private static final int RADIX_SIZE = 1 << RADIX_BITS;
    private static final int RADIX_MASK = RADIX_SIZE - 1;
    private static final int LEVELS = 8;
    private static final long NODE_BYTES = 64L + RADIX_SIZE * 8L;

    private final int pageRows;
    private BranchNode root;
    private long pageCount;

    private PagedLongLinks(int pageRows) {
        this.pageRows = pageRows;
    }

    static PagedLongLinks empty(int pageRows) {
        return new PagedLongLinks(pageRows);
    }

    PagedLongLinks ensureLocator(long locator) {
        long requiredPages = locator / pageRows + 1L;
        if (requiredPages <= pageCount) {
            return this;
        }
        PagedLongLinks result = shallowCopy();
        while (result.pageCount < requiredPages) {
            result.append(new long[pageRows]);
        }
        return result;
    }

    long next(long locator) {
        long encoded = page(locator)[(int) (locator % pageRows)];
        return encoded == 0L ? -1L : encoded - 1L;
    }

    void link(long locator, long nextLocator) {
        page(locator)[(int) (locator % pageRows)] = nextLocator + 1L;
    }

    long managedBytes(SomaOperation operation, Object provenance) {
        long arrays = CheckedLong.multiply(
                CheckedLong.multiply(pageCount, pageRows, operation, provenance),
                Long.BYTES,
                operation,
                provenance);
        long nodes = CheckedLong.multiply(
                CheckedLong.multiply(pageCount, LEVELS, operation, provenance),
                NODE_BYTES,
                operation,
                provenance);
        return CheckedLong.add(arrays, nodes, operation, provenance);
    }

    private PagedLongLinks shallowCopy() {
        PagedLongLinks result = new PagedLongLinks(pageRows);
        for (long ordinal = 0L; ordinal < pageCount; ordinal++) {
            result.append(pageByOrdinal(ordinal));
        }
        return result;
    }

    private long[] page(long locator) {
        return pageByOrdinal(locator / pageRows);
    }

    private long[] pageByOrdinal(long ordinal) {
        if (ordinal < 0L || ordinal >= pageCount || root == null) {
            throw new AssertionError("invalid Index link page");
        }
        BranchNode branch = root;
        for (int level = LEVELS - 1; level > 1; level--) {
            branch = branch.branches[digit(ordinal, level)];
            if (branch == null) throw new AssertionError("missing Index link branch");
        }
        LeafNode leaf = branch.leaves[digit(ordinal, 1)];
        if (leaf == null) throw new AssertionError("missing Index link leaf");
        long[] page = leaf.pages[digit(ordinal, 0)];
        if (page == null) throw new AssertionError("missing Index link page");
        return page;
    }

    private void append(long[] page) {
        long ordinal = pageCount;
        if (root == null) root = new BranchNode();
        BranchNode branch = root;
        for (int level = LEVELS - 1; level > 1; level--) {
            int digit = digit(ordinal, level);
            if (branch.branches[digit] == null) branch.branches[digit] = new BranchNode();
            branch = branch.branches[digit];
        }
        int leafDigit = digit(ordinal, 1);
        if (branch.leaves[leafDigit] == null) branch.leaves[leafDigit] = new LeafNode();
        branch.leaves[leafDigit].pages[digit(ordinal, 0)] = page;
        pageCount++;
    }

    private static int digit(long ordinal, int level) {
        return (int) ((ordinal >>> (level * RADIX_BITS)) & RADIX_MASK);
    }

    private static final class BranchNode {
        private final BranchNode[] branches = new BranchNode[RADIX_SIZE];
        private final LeafNode[] leaves = new LeafNode[RADIX_SIZE];
    }

    private static final class LeafNode {
        private final long[][] pages = new long[RADIX_SIZE][];
    }
}
