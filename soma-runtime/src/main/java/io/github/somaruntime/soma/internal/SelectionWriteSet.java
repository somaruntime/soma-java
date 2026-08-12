package io.github.somaruntime.soma.internal;

/**
 * Columnar staging for one Selection update. Values are allocated only for
 * leaves that actually change, and are addressed by frozen Selection position.
 */
final class SelectionWriteSet {

    private final GeneratedTableLayout layout;
    private final IntLocatorBuffer selected;
    private final int selectedSize;
    private final int wordCount;
    private final long[] changedRows;
    private final long[][] changedLeaves;
    private final Object[] values;
    private int changedRowCount;
    private boolean indexedValueChanged;
    private boolean allChangedRowsPlain = true;

    SelectionWriteSet(
            GeneratedTableLayout layout,
            IntLocatorBuffer selected) {
        if (layout == null || selected == null) {
            throw new AssertionError("Selection write set requires layout and membership");
        }
        this.layout = layout;
        this.selected = selected;
        this.selectedSize = selected.size();
        this.wordCount = (int) (((long) selectedSize + Long.SIZE - 1L) / Long.SIZE);
        this.changedRows = new long[wordCount];
        this.changedLeaves = new long[layout.leafCount()][];
        this.values = new Object[layout.leafCount()];
    }

    boolean capture(
            int selectionPosition,
            TypedValues original,
            TypedValues staged,
            TableChunkDirectory directory) {
        requirePosition(selectionPosition);
        boolean rowChanged = false;
        for (int leaf = 0; leaf < layout.leafCount(); leaf++) {
            if (layout.keyLeaf(leaf)
                    || layout.leafValuesEqual(original, staged, leaf)) {
                continue;
            }
            ensureLeaf(leaf);
            set(changedLeaves[leaf], selectionPosition);
            store(leaf, selectionPosition, staged);
            rowChanged = true;
            if (layout.indexedLeaf(leaf)) indexedValueChanged = true;
        }
        if (rowChanged) {
            set(changedRows, selectionPosition);
            changedRowCount++;
            int locator = selected.get(selectionPosition);
            if (directory.chunk(locator / directory.chunkRows())
                    .hasEncodedRepresentation()) {
                allChangedRowsPlain = false;
            }
        }
        return rowChanged;
    }

    int changedRowCount() {
        return changedRowCount;
    }

    boolean indexedValueChanged() {
        return indexedValueChanged;
    }

    boolean canCommitPlainInPlace() {
        return changedRowCount > 0 && allChangedRowsPlain;
    }

    int selectedSize() {
        return selectedSize;
    }

    boolean rowChanged(int selectionPosition) {
        requirePosition(selectionPosition);
        return get(changedRows, selectionPosition);
    }

    int locator(int selectionPosition) {
        requirePosition(selectionPosition);
        return selected.get(selectionPosition);
    }

    void applyTo(TypedValues destination, int selectionPosition) {
        requirePosition(selectionPosition);
        for (int leaf = 0; leaf < changedLeaves.length; leaf++) {
            long[] words = changedLeaves[leaf];
            if (words != null && get(words, selectionPosition)) {
                load(leaf, selectionPosition, destination);
            }
        }
    }

    void commitPlain(TableChunkDirectory directory) {
        if (!canCommitPlainInPlace()) {
            throw new AssertionError("Selection write set is not PLAIN-committable");
        }
        for (int leaf = 0; leaf < changedLeaves.length; leaf++) {
            long[] words = changedLeaves[leaf];
            if (words == null) continue;
            for (int word = 0; word < words.length; word++) {
                long remaining = words[word];
                while (remaining != 0L) {
                    int bit = Long.numberOfTrailingZeros(remaining);
                    int position = word * Long.SIZE + bit;
                    directory.writePlainLeaf(
                            selected.get(position),
                            layout.leafKind(leaf),
                            layout.leafSlot(leaf),
                            values[leaf],
                            position);
                    remaining &= remaining - 1L;
                }
            }
        }
    }

    void clear() {
        for (int leaf = 0; leaf < values.length; leaf++) {
            if (layout.leafKind(leaf) == GeneratedTableLayout.REFERENCE) {
                Object value = values[leaf];
                if (value != null) {
                    Object[] references = (Object[]) value;
                    for (int word = 0; word < changedLeaves[leaf].length; word++) {
                        long remaining = changedLeaves[leaf][word];
                        while (remaining != 0L) {
                            int bit = Long.numberOfTrailingZeros(remaining);
                            references[word * Long.SIZE + bit] = null;
                            remaining &= remaining - 1L;
                        }
                    }
                }
            }
            values[leaf] = null;
            changedLeaves[leaf] = null;
        }
        changedRowCount = 0;
        indexedValueChanged = false;
        allChangedRowsPlain = true;
    }

    private void ensureLeaf(int leaf) {
        if (values[leaf] != null) return;
        changedLeaves[leaf] = new long[wordCount];
        switch (layout.leafKind(leaf)) {
            case GeneratedTableLayout.BOOLEAN:
                values[leaf] = new boolean[selectedSize];
                break;
            case GeneratedTableLayout.BYTE:
                values[leaf] = new byte[selectedSize];
                break;
            case GeneratedTableLayout.SHORT:
                values[leaf] = new short[selectedSize];
                break;
            case GeneratedTableLayout.CHAR:
                values[leaf] = new char[selectedSize];
                break;
            case GeneratedTableLayout.INT:
                values[leaf] = new int[selectedSize];
                break;
            case GeneratedTableLayout.LONG:
                values[leaf] = new long[selectedSize];
                break;
            case GeneratedTableLayout.FLOAT:
                values[leaf] = new float[selectedSize];
                break;
            case GeneratedTableLayout.DOUBLE:
                values[leaf] = new double[selectedSize];
                break;
            case GeneratedTableLayout.REFERENCE:
                values[leaf] = new Object[selectedSize];
                break;
            default:
                throw new AssertionError("unknown Selection write-set leaf kind");
        }
    }

    private void store(int leaf, int position, TypedValues source) {
        int slot = layout.leafSlot(leaf);
        switch (layout.leafKind(leaf)) {
            case GeneratedTableLayout.BOOLEAN:
                ((boolean[]) values[leaf])[position] = source.booleanValue(slot);
                break;
            case GeneratedTableLayout.BYTE:
                ((byte[]) values[leaf])[position] = source.byteValue(slot);
                break;
            case GeneratedTableLayout.SHORT:
                ((short[]) values[leaf])[position] = source.shortValue(slot);
                break;
            case GeneratedTableLayout.CHAR:
                ((char[]) values[leaf])[position] = source.charValue(slot);
                break;
            case GeneratedTableLayout.INT:
                ((int[]) values[leaf])[position] = source.intValue(slot);
                break;
            case GeneratedTableLayout.LONG:
                ((long[]) values[leaf])[position] = source.longValue(slot);
                break;
            case GeneratedTableLayout.FLOAT:
                ((float[]) values[leaf])[position] = source.floatValue(slot);
                break;
            case GeneratedTableLayout.DOUBLE:
                ((double[]) values[leaf])[position] = source.doubleValue(slot);
                break;
            case GeneratedTableLayout.REFERENCE:
                ((Object[]) values[leaf])[position] = source.reference(slot);
                break;
            default:
                throw new AssertionError("unknown Selection write-set leaf kind");
        }
    }

    private void load(int leaf, int position, TypedValues destination) {
        int slot = layout.leafSlot(leaf);
        switch (layout.leafKind(leaf)) {
            case GeneratedTableLayout.BOOLEAN:
                destination.booleanValue(slot, ((boolean[]) values[leaf])[position]);
                break;
            case GeneratedTableLayout.BYTE:
                destination.byteValue(slot, ((byte[]) values[leaf])[position]);
                break;
            case GeneratedTableLayout.SHORT:
                destination.shortValue(slot, ((short[]) values[leaf])[position]);
                break;
            case GeneratedTableLayout.CHAR:
                destination.charValue(slot, ((char[]) values[leaf])[position]);
                break;
            case GeneratedTableLayout.INT:
                destination.intValue(slot, ((int[]) values[leaf])[position]);
                break;
            case GeneratedTableLayout.LONG:
                destination.longValue(slot, ((long[]) values[leaf])[position]);
                break;
            case GeneratedTableLayout.FLOAT:
                destination.floatValue(slot, ((float[]) values[leaf])[position]);
                break;
            case GeneratedTableLayout.DOUBLE:
                destination.doubleValue(slot, ((double[]) values[leaf])[position]);
                break;
            case GeneratedTableLayout.REFERENCE:
                destination.reference(slot, ((Object[]) values[leaf])[position]);
                break;
            default:
                throw new AssertionError("unknown Selection write-set leaf kind");
        }
    }

    private void requirePosition(int position) {
        if (position < 0 || position >= selectedSize) {
            throw new AssertionError("invalid Selection write-set position");
        }
    }

    private static void set(long[] words, int position) {
        words[position >>> 6] |= 1L << (position & 63);
    }

    private static boolean get(long[] words, int position) {
        return (words[position >>> 6] & 1L << (position & 63)) != 0L;
    }
}
