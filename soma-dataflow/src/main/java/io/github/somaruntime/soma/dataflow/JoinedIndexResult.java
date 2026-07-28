package io.github.somaruntime.soma.dataflow;

import java.util.Arrays;

/**
 * Detached copy of a joined current-Index sequence.
 *
 * <p>This is not a stable row snapshot. Both sides become invalid after the
 * corresponding source Table mutates or changes lifecycle.</p>
 */
public final class JoinedIndexResult {
    private final String leftSource;
    private final String rightSource;
    private final long leftStructuralEpoch;
    private final long rightStructuralEpoch;
    private final int[] leftIndexes;
    private final int[] rightIndexes;
    private final boolean[] rightPresence;

    JoinedIndexResult(
            String leftSource,
            String rightSource,
            long leftStructuralEpoch,
            long rightStructuralEpoch,
            int[] leftIndexes,
            int[] rightIndexes,
            boolean[] rightPresence) {
        this.leftSource = leftSource;
        this.rightSource = rightSource;
        this.leftStructuralEpoch = leftStructuralEpoch;
        this.rightStructuralEpoch = rightStructuralEpoch;
        this.leftIndexes = leftIndexes;
        this.rightIndexes = rightIndexes;
        this.rightPresence = rightPresence;
    }

    public String leftSource() {
        return leftSource;
    }

    public String rightSource() {
        return rightSource;
    }

    public long leftStructuralEpoch() {
        return leftStructuralEpoch;
    }

    public long rightStructuralEpoch() {
        return rightStructuralEpoch;
    }

    public int size() {
        return leftIndexes.length;
    }

    public int leftIndexAt(int position) {
        check(position);
        return leftIndexes[position];
    }

    public boolean isRightPresent(int position) {
        check(position);
        return rightPresence[position];
    }

    public int rightIndexAt(int position) {
        check(position);
        if (!rightPresence[position]) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_absent_join_side",
                    "joined.right",
                    "joined.rightIndexAt");
        }
        return rightIndexes[position];
    }

    public int[] leftIndexes() {
        return Arrays.copyOf(leftIndexes, leftIndexes.length);
    }

    public int[] rightIndexes() {
        return Arrays.copyOf(rightIndexes, rightIndexes.length);
    }

    private void check(int position) {
        if (position < 0 || position >= leftIndexes.length) {
            throw new IndexOutOfBoundsException("joined result position out of range");
        }
    }
}
