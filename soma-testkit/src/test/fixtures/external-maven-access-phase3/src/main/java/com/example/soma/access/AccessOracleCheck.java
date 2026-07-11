package com.example.soma.access;

import com.example.soma.access.generated.AccessRecordBatch;
import com.example.soma.access.generated.AccessRecordTable;

import java.util.List;
import java.util.Random;
import java.lang.management.ManagementFactory;

/** Deterministic differential oracle for Phase 3 selector/order maintenance. */
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
        verify(table, code, state, group, score);
        verifyCleanSelectorAllocationShape();

        for (int step = 0; step < 320; step++) {
            int row = random.nextInt(size);
            int nextState = random.nextInt(7);
            int nextScore = random.nextInt(1000);
            table.mutateAt(row).setState(nextState).setScore(nextScore).commit();
            state[row] = nextState;
            score[row] = nextScore;
            if ((step & 31) == 31) verify(table, code, state, group, score);
        }
        verify(table, code, state, group, score);
    }

    private static void verifyCleanSelectorAllocationShape() {
        AccessRecordTable small = allocationTable(32, 20000);
        AccessRecordTable large = allocationTable(512, 30000);
        for (int round = 0; round < 1000; round++) {
            small.findByState(1).count();
            large.findByState(1).count();
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
        table.findByState(1).count();
        return table;
    }

    private static long allocatedBytes(
            com.sun.management.ThreadMXBean allocation,
            long threadId,
            AccessRecordTable table) {
        long before = allocation.getThreadAllocatedBytes(threadId);
        for (int round = 0; round < 300; round++) {
            table.findByState(1).count();
        }
        return allocation.getThreadAllocatedBytes(threadId) - before;
    }

    private static void verify(
            AccessRecordTable table, int[] code, int[] state, int[] group, int[] score) {
        for (int expectedState = 0; expectedState < 7; expectedState++) {
            int[] actual = table.findByState(expectedState).rowIndexes();
            int cursor = 0;
            for (int row = 0; row < state.length; row++) {
                if (state[row] != expectedState) continue;
                require(cursor < actual.length && actual[cursor] == row,
                        "index oracle state=" + expectedState + " row=" + row);
                cursor++;
            }
            require(cursor == actual.length, "index oracle cardinality");
        }
        for (int row = 0; row < code.length; row += 17) {
            require(table.findByCode(code[row]).firstOrThrow().code == code[row],
                    "unique oracle row=" + row);
        }
        for (int expectedGroup = 0; expectedGroup < 9; expectedGroup++) {
            List<AccessRecord> actual = table.byGroupScore(expectedGroup).fetchAll();
            int previousScore = Integer.MAX_VALUE;
            int previousRow = -1;
            for (AccessRecord record : actual) {
                int row = record.code - 10000;
                require(group[row] == expectedGroup, "order oracle group");
                require(score[row] < previousScore
                                || (score[row] == previousScore && row > previousRow),
                        "order oracle descending/stable");
                previousScore = score[row];
                previousRow = row;
            }
        }
        List<AccessRecord> dynamic = table.rows().sorted((left, right) -> {
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

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
