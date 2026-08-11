package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;

/** Int-domain paged next-locator storage for non-unique Index postings. */
final class PagedIntLinks {

    private static final int RADIX_BITS = 8;
    private static final int RADIX_SIZE = 1 << RADIX_BITS;
    private static final int RADIX_MASK = RADIX_SIZE - 1;
    private static final int LEVELS = 4;
    private static final long NODE_BYTES = 64L + RADIX_SIZE * 8L;

    private final int pageRows;
    private BranchNode root;
    private int pageCount;

    private PagedIntLinks(int pageRows) {
        this.pageRows = pageRows;
    }

    static PagedIntLinks empty(int pageRows) {
        return new PagedIntLinks(pageRows);
    }

    PagedIntLinks ensureLocator(int locator) {
        int requiredPages = locator / pageRows + 1;
        if (requiredPages <= pageCount) return this;
        PagedIntLinks result = shallowCopy();
        while (result.pageCount < requiredPages) result.append(new int[pageRows]);
        return result;
    }

    int next(int locator) {
        int encoded = page(locator)[locator % pageRows];
        return encoded == 0 ? -1 : encoded - 1;
    }

    void link(int locator, int nextLocator) {
        page(locator)[locator % pageRows] = nextLocator + 1;
    }

    long managedBytes(SomaOperation operation, Object provenance) {
        long arrays = CheckedLong.multiply(
                CheckedLong.multiply(pageCount, pageRows, operation, provenance),
                Integer.BYTES,
                operation,
                provenance);
        long nodes = CheckedLong.multiply(
                CheckedLong.multiply(pageCount, LEVELS, operation, provenance),
                NODE_BYTES,
                operation,
                provenance);
        return CheckedLong.add(arrays, nodes, operation, provenance);
    }

    private PagedIntLinks shallowCopy() {
        PagedIntLinks result = new PagedIntLinks(pageRows);
        for (int ordinal = 0; ordinal < pageCount; ordinal++) {
            result.append(pageByOrdinal(ordinal));
        }
        return result;
    }

    private int[] page(int locator) {
        return pageByOrdinal(locator / pageRows);
    }

    private int[] pageByOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= pageCount || root == null) {
            throw new AssertionError("invalid Index link page");
        }
        BranchNode branch = root;
        for (int level = LEVELS - 1; level > 1; level--) {
            branch = branch.branches[digit(ordinal, level)];
            if (branch == null) throw new AssertionError("missing Index link branch");
        }
        LeafNode leaf = branch.leaves[digit(ordinal, 1)];
        if (leaf == null) throw new AssertionError("missing Index link leaf");
        int[] page = leaf.pages[digit(ordinal, 0)];
        if (page == null) throw new AssertionError("missing Index link page");
        return page;
    }

    private void append(int[] page) {
        int ordinal = pageCount;
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

    private static int digit(int ordinal, int level) {
        return (ordinal >>> (level * RADIX_BITS)) & RADIX_MASK;
    }

    private static final class BranchNode {
        private final BranchNode[] branches = new BranchNode[RADIX_SIZE];
        private final LeafNode[] leaves = new LeafNode[RADIX_SIZE];
    }

    private static final class LeafNode {
        private final int[][] pages = new int[RADIX_SIZE][];
    }
}
