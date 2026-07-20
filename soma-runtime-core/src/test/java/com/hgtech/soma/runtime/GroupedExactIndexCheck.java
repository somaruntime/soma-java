package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.GroupedExactIndex;
import com.hgtech.soma.runtime.generated.ExactGroupCounter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** GroupedExactIndex 的独立 randomized/differential 组件证据。 */
public final class GroupedExactIndexCheck {
    private GroupedExactIndexCheck() {
    }

    public static void main(String[] args) {
        testExactGroupCounter();
        testCapacityLifecycleAndFailureBoundaries();
        testSameHashFullEqualityChain();
        testRandomizedAppendRegroupSwapRemoveAndReuse();
        System.out.println("grouped-exact-index-test: ok");
    }

    private static void testExactGroupCounter() {
        ExactGroupCounter counter = new ExactGroupCounter(8);
        assertEquals(ExactGroupCounter.estimatedRetainedBytes(8),
                counter.retainedBytes(), "group-counter retained bytes");
        int first = counter.createGroup(7L, 1);
        int collision = counter.createGroup(7L, 3);
        counter.createGroup(9L, 5);
        assertEquals(collision, counter.firstGroup(7L), "counter hash-chain head");
        assertEquals(first, counter.nextHashGroup(collision), "counter same-hash traversal");
        assertEquals(3, counter.representativeRow(collision),
                "counter representative row");
        assertEquals(3, counter.groupCount(), "counter group count");
        counter.release();
        assertEquals(0L, counter.retainedBytes(), "counter release");
        expectIllegalState(new Action() {
            @Override public void run() { new ExactGroupCounter(0).createGroup(1L, 0); }
        }, "counter capacity boundary");
    }

    private static void testCapacityLifecycleAndFailureBoundaries() {
        GroupedExactIndex cardinalityAware = new GroupedExactIndex(64, 2);
        assertEquals(GroupedExactIndex.estimatedRetainedBytes(64, 2),
                cardinalityAware.retainedBytes(), "cardinality-aware construction");
        cardinalityAware.ensureCapacity(64, 6);
        for (int group = 0; group < 6; group++) {
            cardinalityAware.createGroup(group);
        }
        assertEquals(6, cardinalityAware.groupCount(), "prepared exact groups");
        cardinalityAware.release();

        final GroupedExactIndex index = new GroupedExactIndex(0);
        assertEquals(GroupedExactIndex.estimatedRetainedBytes(0, 0),
                index.retainedBytes(), "empty retained-byte estimate");

        int emptyGroup = index.createGroup(1L);
        expectIllegalState(new Action() {
            @Override public void run() { index.createGroup(2L); }
        }, "unprepared bucket growth");
        expectIllegalArgument(new Action() {
            @Override public void run() { index.link(emptyGroup, 0); }
        }, "unprepared row capacity");
        index.clear();

        index.ensureCapacity(1, 1);
        int group = index.createGroup(7L);
        index.link(group, 0);
        assertEquals(1, index.entryCount(), "single linked entry");
        assertEquals(1, index.groupCount(), "single live group");
        assertTrue(index.isLinked(0), "single row linked");

        expectIllegalArgument(new Action() {
            @Override public void run() { index.link(group, 0); }
        }, "duplicate row link");
        expectIllegalArgument(new Action() {
            @Override public void run() { index.relocate(0, 0); index.link(group, 0); }
        }, "relocate no-op does not unlink");

        long retained = index.retainedBytes();
        index.unlink(0);
        assertEquals(0, index.entryCount(), "unlink entry count");
        assertEquals(0, index.groupCount(), "empty group released");
        assertFalse(index.isLinked(0), "unlinked row state");
        assertEquals(retained, index.retainedBytes(), "unlink retains reusable capacity");

        index.ensureCapacity(64, 32);
        assertTrue(index.retainedBytes() >= retained, "capacity growth retained bytes");
        assertTrue(index.storageHighWaterBytes() >= index.retainedBytes(),
                "storage high-water covers current bytes");
        index.clear();
        assertEquals(0, index.entryCount(), "clear entries");
        assertEquals(0, index.groupCount(), "clear groups");
        assertTrue(index.retainedBytes() > 0L, "clear keeps reusable storage");

        expectIllegalArgument(new Action() {
            @Override public void run() { index.ensureCapacity(-1, 0); }
        }, "negative row request");
        expectIllegalArgument(new Action() {
            @Override public void run() {
                GroupedExactIndex.estimatedRetainedBytes(1, 2);
            }
        }, "groups cannot exceed rows");

        index.release();
        assertEquals(0L, index.retainedBytes(), "release drops current storage");
        expectIllegalState(new Action() {
            @Override public void run() { index.firstGroup(1L); }
        }, "released lookup");
    }

    private static void testSameHashFullEqualityChain() {
        Oracle oracle = new Oracle(16);
        oracle.append(0);
        oracle.append(4);
        oracle.append(8);
        oracle.append(12);
        oracle.verify("collision-chain-build");
        long collisionsBefore = oracle.index.collisionCount();
        assertTrue(oracle.findGroup(0) >= 0, "same-hash group lookup");
        assertTrue(oracle.index.collisionCount() > collisionsBefore,
                "generated full-equality rejection is observable");

        oracle.update(1, 16);
        oracle.remove(0);
        oracle.verify("collision-chain-regroup-remove");
        oracle.clear();
        oracle.append(20);
        oracle.verify("collision-chain-clear-reuse");
    }

    private static void testRandomizedAppendRegroupSwapRemoveAndReuse() {
        Oracle oracle = new Oracle(256);
        Random random = new Random(0x5A17E11DL);
        for (int step = 0; step < 12000; step++) {
            int operation = random.nextInt(100);
            if (oracle.size == 0 || (operation < 34 && oracle.size < 256)) {
                oracle.append(random.nextInt(48) - 24);
            } else if (operation < 61) {
                oracle.update(random.nextInt(oracle.size), random.nextInt(48) - 24);
            } else if (operation < 88) {
                oracle.remove(random.nextInt(oracle.size));
            } else if (operation < 92) {
                oracle.clear();
            } else {
                oracle.index.ensureCapacity(oracle.size + 8, 8);
            }
            oracle.verify("random-step-" + step);
        }
        assertTrue(oracle.index.probeCount() > 0L, "randomized lookup probes");
        assertTrue(oracle.index.collisionCount() > 0L, "randomized collisions");
        assertTrue(oracle.index.rehashCount() > 0L, "randomized bucket rehash");
        oracle.index.resetMetrics();
        assertEquals(0L, oracle.index.probeCount(), "reset probes");
        assertEquals(0L, oracle.index.collisionCount(), "reset collisions");
        assertEquals(0L, oracle.index.rehashCount(), "reset rehashes");
    }

    private static final class Oracle {
        final GroupedExactIndex index = new GroupedExactIndex(0);
        final int[] values;
        final Map<Integer, Integer> groups = new HashMap<Integer, Integer>();
        int size;

        Oracle(int maximumRows) {
            values = new int[maximumRows];
        }

        void append(int value) {
            boolean newGroup = !groups.containsKey(value);
            index.ensureCapacity(size + 1, newGroup ? 1 : 0);
            int group = newGroup ? createGroup(value) : groups.get(value).intValue();
            values[size] = value;
            index.link(group, size);
            size++;
        }

        void update(int row, int value) {
            int previousValue = values[row];
            if (previousValue == value) return;
            int previousGroup = groups.get(previousValue).intValue();
            boolean releasesGroup = index.groupSize(previousGroup) == 1;
            index.unlink(row);
            if (releasesGroup) groups.remove(previousValue);

            boolean newGroup = !groups.containsKey(value);
            index.ensureCapacity(size, newGroup ? 1 : 0);
            int group = newGroup ? createGroup(value) : groups.get(value).intValue();
            values[row] = value;
            index.link(group, row);
        }

        void remove(int row) {
            int value = values[row];
            int group = groups.get(value).intValue();
            boolean releasesGroup = index.groupSize(group) == 1;
            index.unlink(row);
            if (releasesGroup) groups.remove(value);

            int tail = size - 1;
            if (row != tail) {
                index.relocate(tail, row);
                values[row] = values[tail];
            }
            size--;
        }

        void clear() {
            index.clear();
            groups.clear();
            size = 0;
        }

        int createGroup(int value) {
            int group = index.createGroup(hash(value));
            groups.put(value, Integer.valueOf(group));
            return group;
        }

        int findGroup(int value) {
            int group = index.firstGroup(hash(value));
            while (group >= 0) {
                int representative = index.representativeRow(group);
                if (values[representative] == value) return group;
                index.recordCollision();
                group = index.nextHashGroup(group);
            }
            return -1;
        }

        void verify(String path) {
            assertEquals(size, index.entryCount(), path + " entry count");
            assertEquals(groups.size(), index.groupCount(), path + " group count");
            assertTrue(index.storageHighWaterBytes() >= index.retainedBytes(),
                    path + " storage high-water");

            Set<Integer> visited = new HashSet<Integer>();
            for (Map.Entry<Integer, Integer> expected : groups.entrySet()) {
                int value = expected.getKey().intValue();
                int group = findGroup(value);
                assertEquals(expected.getValue().intValue(), group,
                        path + " located group value=" + value);
                int count = 0;
                for (int row = index.firstRow(group); row >= 0; row = index.nextRow(row)) {
                    assertTrue(row < size, path + " group row in packed range");
                    assertEquals(value, values[row], path + " group full equality");
                    assertTrue(visited.add(Integer.valueOf(row)),
                            path + " row linked exactly once");
                    count++;
                    assertTrue(count <= size, path + " no row-link cycle");
                }
                assertEquals(index.groupSize(group), count, path + " group size");
            }
            assertEquals(size, visited.size(), path + " all rows reached");
            for (int row = 0; row < size; row++) {
                assertTrue(index.isLinked(row), path + " packed row linked=" + row);
            }
        }

        private static long hash(int value) {
            return (long) (value & 3);
        }
    }

    private static void expectIllegalArgument(Action action, String message) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException: " + message);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void expectIllegalState(Action action, String message) {
        try {
            action.run();
            throw new AssertionError("expected IllegalStateException: " + message);
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private interface Action {
        void run();
    }
}
