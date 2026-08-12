package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;
import java.util.Arrays;

/**
 * Frozen dense-compaction plan for one Selection remove. The plan is prepared
 * completely before final commit and contains only primitive locator state.
 */
final class SelectionRemovePlan {

    private final int oldSize;
    private final int newSize;
    private final int[] removed;
    private final int[] holes;
    private final int[] sources;

    private SelectionRemovePlan(
            int oldSize,
            int newSize,
            int[] removed,
            int[] holes,
            int[] sources) {
        this.oldSize = oldSize;
        this.newSize = newSize;
        this.removed = removed;
        this.holes = holes;
        this.sources = sources;
    }

    static SelectionRemovePlan prepare(
            IntLocatorBuffer selection,
            int oldSize,
            Object provenance) {
        int selected = selection.size();
        int newSize = oldSize - selected;
        int[] removed = Arrays.copyOf(selection.backing(), selected);
        Arrays.sort(removed);
        int moved = 0;
        for (int index = 0; index < removed.length; index++) {
            int locator = removed[index];
            if (locator < 0 || locator >= oldSize
                    || (index != 0 && locator == removed[index - 1])) {
                throw new AssertionError("invalid frozen Selection membership");
            }
            if (locator < newSize) moved++;
        }
        int[] holes = new int[RowExecutionSupport.arrayLength(
                moved, SomaOperation.REMOVE, provenance)];
        int[] sources = new int[holes.length];
        int tail = oldSize - 1;
        int removedTail = removed.length - 1;
        int move = 0;
        for (int hole : removed) {
            if (hole >= newSize) break;
            while (removedTail >= 0 && removed[removedTail] == tail) {
                tail--;
                removedTail--;
            }
            if (tail < newSize || tail <= hole) {
                throw new AssertionError("invalid Selection compaction source");
            }
            holes[move] = hole;
            sources[move] = tail;
            move++;
            tail--;
        }
        if (move != holes.length) {
            throw new AssertionError("incomplete Selection remove move plan");
        }
        return new SelectionRemovePlan(
                oldSize, newSize, removed, holes, sources);
    }

    int oldSize() {
        return oldSize;
    }

    int newSize() {
        return newSize;
    }

    int removedCount() {
        return removed.length;
    }

    int removed(int position) {
        return removed[position];
    }

    int moveCount() {
        return holes.length;
    }

    int hole(int position) {
        return holes[position];
    }

    int source(int position) {
        return sources[position];
    }

    int[] sourceLocators() {
        int[] result = new int[newSize];
        for (int locator = 0; locator < newSize; locator++) {
            result[locator] = locator;
        }
        for (int move = 0; move < holes.length; move++) {
            result[holes[move]] = sources[move];
        }
        return result;
    }

    void clear() {
        Arrays.fill(removed, 0);
        Arrays.fill(holes, 0);
        Arrays.fill(sources, 0);
    }
}
