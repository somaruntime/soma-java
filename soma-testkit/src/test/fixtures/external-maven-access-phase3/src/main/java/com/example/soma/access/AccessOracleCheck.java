package com.example.soma.access;

import com.example.soma.access.generated.AccessRecordBatch;
import com.example.soma.access.generated.AccessRecordTable;
import com.hgtech.soma.runtime.RemoveResult;

import java.util.List;
import java.util.Random;
import java.util.Arrays;
import java.lang.management.ManagementFactory;

/** Deterministic differential oracle for Phase 3 exact indexes and explicit sorting. */
final class AccessOracleCheck {
    private AccessOracleCheck() {
    }

    static void run() {
        final int size = 192;
        int[] code = new int[size];
        int[] state = new int[size];
        int[] group = new int[size];
        int[] score = new int[size];
        Random random = new Random(0x534f4d41L);
        AccessRecordBatch batch = new AccessRecordBatch(size);
        for (int row = 0; row < size; row++) {
            code[row] = 10000 + row;
            state[row] = random.nextInt(7);
            group[row] = random.nextInt(9);
            score[row] = random.nextInt(1000);
            batch.addValues(code[row], state[row], group[row], score[row]);
        }
        AccessRecordTable table = AccessRecordTable.create();
        table.addBatch(batch);
        verify(table, code, state, group, score, size);
        verifyCleanSelectorAllocationShape();

        for (int step = 0; step < 320; step++) {
            int row = random.nextInt(size);
            int nextState = random.nextInt(7);
            int nextScore = random.nextInt(1000);
            table.mutateAt(row).setState(nextState).setScore(nextScore).commit();
            state[row] = nextState;
            score[row] = nextScore;
            if ((step & 31) == 31) verify(table, code, state, group, score, size);
        }
        verify(table, code, state, group, score, size);

        int liveSize = size;
        for (int step = 0; step < 48 && liveSize > 24; step++) {
            final int selectedState = random.nextInt(7);
            final int divisor = 3 + random.nextInt(4);
            final int remainder = random.nextInt(divisor);
            int expectedRemoved = countSelected(
                    code, state, liveSize, selectedState, divisor, remainder);
            RemoveResult removed = table.scanByState(selectedState)
                    .filter(row -> row.code() % divisor == remainder)
                    .remove();
            require(removed.removed() == expectedRemoved,
                    "random exact-source remove count");
            liveSize = applySwapRemove(
                    code, state, group, score, liveSize,
                    selectedState, divisor, remainder);
            require(table.size() == liveSize, "random swap-remove table size");
            verify(table, code, state, group, score, liveSize);
        }
        table.release();
    }

    private static void verifyCleanSelectorAllocationShape() {
        AccessRecordTable small = allocationTable(32, 20000);
        AccessRecordTable large = allocationTable(512, 30000);
        for (int round = 0; round < 1000; round++) {
            small.scanByState(1).count();
            large.scanByState(1).count();
        }
        java.lang.management.ThreadMXBean management = ManagementFactory.getThreadMXBean();
        require(management instanceof com.sun.management.ThreadMXBean,
                "JDK must expose per-thread allocation counter for access evidence");
        com.sun.management.ThreadMXBean allocation =
                (com.sun.management.ThreadMXBean) management;
        if (!allocation.isThreadAllocatedMemoryEnabled()) {
            allocation.setThreadAllocatedMemoryEnabled(true);
        }
        long threadId = Thread.currentThread().getId();
        long smallBytes = allocatedBytes(allocation, threadId, small);
        long largeBytes = allocatedBytes(allocation, threadId, large);
        require(largeBytes <= smallBytes + 16384L,
                "clean selector terminal allocation must not scale with rows small="
                        + smallBytes + " large=" + largeBytes);
        small.release();
        large.release();
    }

    private static AccessRecordTable allocationTable(int rows, int codeBase) {
        AccessRecordBatch batch = new AccessRecordBatch(rows);
        for (int row = 0; row < rows; row++) {
            batch.addValues(codeBase + row, 1, row & 3, row);
        }
        AccessRecordTable table = AccessRecordTable.create();
        table.addBatch(batch);
        table.scanByState(1).count();
        return table;
    }

    private static long allocatedBytes(
            com.sun.management.ThreadMXBean allocation,
            long threadId,
            AccessRecordTable table) {
        long before = allocation.getThreadAllocatedBytes(threadId);
        for (int round = 0; round < 300; round++) {
            table.scanByState(1).count();
        }
        return allocation.getThreadAllocatedBytes(threadId) - before;
    }

    private static void verify(
            AccessRecordTable table,
            int[] code,
            int[] state,
            int[] group,
            int[] score,
            int size) {
        for (int expectedState = 0; expectedState < 7; expectedState++) {
            int[] actual = table.scanByState(expectedState).indexSnapshot().toArray();
            Arrays.sort(actual);
            int cursor = 0;
            for (int row = 0; row < size; row++) {
                if (state[row] != expectedState) continue;
                require(cursor < actual.length && actual[cursor] == row,
                        "index oracle state=" + expectedState + " row=" + row);
                cursor++;
            }
            require(cursor == actual.length, "index oracle cardinality");
        }
        for (int row = 0; row < size; row += 17) {
            require(table.fetchByCode(code[row]).code == code[row],
                    "unique oracle row=" + row);
        }
        for (int expectedGroup = 0; expectedGroup < 9; expectedGroup++) {
            List<AccessRecord> actual = table.scanByGroup(expectedGroup)
                    .sorted((left, right) -> {
                        int compared = Integer.compare(right.score(), left.score());
                        return compared != 0 ? compared
                                : Integer.compare(left.code(), right.code());
                    }).fetchAll();
            int previousScore = Integer.MAX_VALUE;
            int previousCode = Integer.MIN_VALUE;
            for (AccessRecord record : actual) {
                int row = findRow(code, size, record.code);
                require(row >= 0, "materialized exact-index code is live");
                require(group[row] == expectedGroup, "exact-index group oracle");
                require(score[row] < previousScore
                                || (score[row] == previousScore
                                        && record.code > previousCode),
                        "explicit order oracle descending/identity tie-break");
                previousScore = score[row];
                previousCode = record.code;
            }
        }
        List<AccessRecord> dynamic = table.sorted((left, right) -> {
            int compared = Integer.compare(left.score(), right.score());
            return compared != 0 ? compared : Integer.compare(left.code(), right.code());
        }).fetchAll();
        for (int index = 1; index < dynamic.size(); index++) {
            AccessRecord previous = dynamic.get(index - 1);
            AccessRecord current = dynamic.get(index);
            require(previous.score < current.score
                            || (previous.score == current.score
                                    && previous.code < current.code),
                    "dynamic sort oracle");
        }
    }

    private static int countSelected(
            int[] code,
            int[] state,
            int size,
            int selectedState,
            int divisor,
            int remainder) {
        int count = 0;
        for (int row = 0; row < size; row++) {
            if (state[row] == selectedState && code[row] % divisor == remainder) count++;
        }
        return count;
    }

    private static int applySwapRemove(
            int[] code,
            int[] state,
            int[] group,
            int[] score,
            int size,
            int selectedState,
            int divisor,
            int remainder) {
        int[] selected = new int[size];
        int count = 0;
        for (int row = 0; row < size; row++) {
            if (state[row] == selectedState && code[row] % divisor == remainder) {
                selected[count++] = row;
            }
        }
        int newSize = size - count;
        int tail = size - 1;
        int selectedTail = count - 1;
        for (int hole = 0; hole < count && selected[hole] < newSize; hole++) {
            int write = selected[hole];
            while (tail >= newSize) {
                while (selectedTail >= 0 && selected[selectedTail] > tail) selectedTail--;
                if (selectedTail >= 0 && selected[selectedTail] == tail) {
                    tail--;
                    selectedTail--;
                    continue;
                }
                break;
            }
            require(tail >= newSize, "exact-index oracle swap-remove tail");
            code[write] = code[tail];
            state[write] = state[tail];
            group[write] = group[tail];
            score[write] = score[tail];
            tail--;
        }
        return newSize;
    }

    private static int findRow(int[] code, int size, int expectedCode) {
        for (int row = 0; row < size; row++) {
            if (code[row] == expectedCode) return row;
        }
        return -1;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
