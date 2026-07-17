package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.HashIntKeySpace;
import com.hgtech.soma.runtime.generated.HashLongKeySpace;
import com.hgtech.soma.runtime.generated.HashCompositeKeySpace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Deterministic invariant checks for Phase 2 primitive identity material. */
public final class KeySpacePhase2Check {
    private KeySpacePhase2Check() {
    }

    public static void main(String[] args) {
        testHashIntPackedRows();
        testHashIntRandomized();
        testHashLongRandomized();
        testHashCompositeProbeRandomized();
        testRehashAfterDeletedSlots();
        testCapacityOverflowGuards();
        System.out.println("keyspace-phase2-test: ok");
    }

    private static void testHashIntPackedRows() {
        HashIntKeySpace keys = new HashIntKeySpace(0);
        List<Integer> oracle = new ArrayList<Integer>();
        for (int key = 0; key < 96; key += 3) {
            keys.put(key, oracle.size());
            oracle.add(Integer.valueOf(key));
        }
        for (int step = 0; step < 12; step++) {
            int row = (step * 5) % oracle.size();
            int removed = oracle.get(row).intValue();
            int lastIndex = oracle.size() - 1;
            int moved = oracle.get(lastIndex).intValue();
            keys.remove(removed);
            oracle.remove(lastIndex);
            if (row < oracle.size()) {
                oracle.set(row, Integer.valueOf(moved));
                keys.updateRow(moved, row);
            }
            assertEquals(oracle.size(), keys.size(), "packed hash size");
            for (int index = 0; index < oracle.size(); index++) {
                assertEquals(index, keys.rowOf(oracle.get(index).intValue()),
                        "hash locator follows packed swap-remove");
            }
        }
        assertEquals(-1, keys.rowOf(removedSentinel()), "missing hash key");
    }

    private static void testHashIntRandomized() {
        HashIntKeySpace keys = new HashIntKeySpace(0);
        Map<Integer, Integer> oracle = new HashMap<Integer, Integer>();
        Random random = new Random(90210L);
        for (int step = 0; step < 5000; step++) {
            int key = random.nextInt(512) - 256;
            int operation = random.nextInt(3);
            if (operation == 0 && !oracle.containsKey(Integer.valueOf(key))) {
                int row = random.nextInt(4096);
                keys.put(key, row);
                oracle.put(Integer.valueOf(key), Integer.valueOf(row));
            } else if (operation == 1 && oracle.containsKey(Integer.valueOf(key))) {
                keys.remove(key);
                oracle.remove(Integer.valueOf(key));
            } else if (oracle.containsKey(Integer.valueOf(key))) {
                int row = random.nextInt(4096);
                keys.updateRow(key, row);
                oracle.put(Integer.valueOf(key), Integer.valueOf(row));
            }
            assertEquals(oracle.size(), keys.size(), "hash size");
            for (Map.Entry<Integer, Integer> entry : oracle.entrySet()) {
                assertEquals(entry.getValue().intValue(), keys.rowOf(entry.getKey().intValue()),
                        "hash lookup");
            }
        }
    }

    private static void testHashLongRandomized() {
        HashLongKeySpace keys = new HashLongKeySpace(0);
        Map<Long, Integer> oracle = new HashMap<Long, Integer>();
        Random random = new Random(731991L);
        for (int step = 0; step < 5000; step++) {
            long key = ((long) (random.nextInt(512) - 256) << 32)
                    ^ (long) (random.nextInt(512) - 256);
            Long boxedKey = Long.valueOf(key);
            int operation = random.nextInt(3);
            if (operation == 0 && !oracle.containsKey(boxedKey)) {
                int row = random.nextInt(4096);
                keys.put(key, row);
                oracle.put(boxedKey, Integer.valueOf(row));
            } else if (operation == 1 && oracle.containsKey(boxedKey)) {
                keys.remove(key);
                oracle.remove(boxedKey);
            } else if (oracle.containsKey(boxedKey)) {
                int row = random.nextInt(4096);
                keys.updateRow(key, row);
                oracle.put(boxedKey, Integer.valueOf(row));
            }
            assertEquals(oracle.size(), keys.size(), "long hash size");
            for (Map.Entry<Long, Integer> entry : oracle.entrySet()) {
                assertEquals(entry.getValue().intValue(), keys.rowOf(entry.getKey().longValue()),
                        "long hash lookup");
            }
        }
    }

    private static void testHashCompositeProbeRandomized() {
        HashCompositeKeySpace keys = new HashCompositeKeySpace(0);
        Map<String, Integer> oracle = new HashMap<String, Integer>();
        Map<Integer, String> byRow = new HashMap<Integer, String>();
        Random random = new Random(22334455L);
        int nextRow = 0;
        for (int step = 0; step < 5000; step++) {
            String key = (random.nextInt(17) - 8) + ":" + (random.nextInt(19) - 9);
            int action = random.nextInt(3);
            Integer current = oracle.get(key);
            if (action == 0 && current == null) {
                keys.ensureInsertCapacity();
                long hash = collisionHeavyHash(key);
                int slot = insertionSlot(keys, hash, key, byRow);
                int row = nextRow++;
                keys.putAt(slot, hash, row);
                oracle.put(key, Integer.valueOf(row));
                byRow.put(Integer.valueOf(row), key);
            } else if (action == 1 && current != null) {
                int slot = findSlot(keys, collisionHeavyHash(key), key, byRow);
                if (slot < 0) {
                    throw new AssertionError("composite key missing before remove");
                }
                keys.removeAt(slot);
                oracle.remove(key);
                byRow.remove(current);
            } else if (current != null) {
                int slot = findSlot(keys, collisionHeavyHash(key), key, byRow);
                int replacement = nextRow++;
                keys.updateRowAt(slot, replacement);
                oracle.put(key, Integer.valueOf(replacement));
                byRow.remove(current);
                byRow.put(Integer.valueOf(replacement), key);
            }
            assertEquals(oracle.size(), keys.size(), "composite hash size");
            for (Map.Entry<String, Integer> entry : oracle.entrySet()) {
                int slot = findSlot(keys, collisionHeavyHash(entry.getKey()), entry.getKey(), byRow);
                if (slot < 0) {
                    throw new AssertionError("composite lookup lost identity " + entry.getKey());
                }
                assertEquals(entry.getValue().intValue(), keys.rowAt(slot), "composite row mapping");
            }
        }
    }

    private static void testRehashAfterDeletedSlots() {
        HashIntKeySpace intKeys = new HashIntKeySpace(0);
        HashLongKeySpace longKeys = new HashLongKeySpace(0);
        for (int key = 0; key < 256; key++) {
            intKeys.put(key, key + 1000);
            longKeys.put(((long) key << 32) | (long) key, key + 2000);
        }
        for (int key = 0; key < 256; key += 2) {
            intKeys.remove(key);
            longKeys.remove(((long) key << 32) | (long) key);
        }
        assertEquals(128, intKeys.size(), "int live buckets after delete");
        assertEquals(256, intKeys.used(), "int used retains tombstones");
        assertEquals(128, longKeys.size(), "long live buckets after delete");
        assertEquals(256, longKeys.used(), "long used retains tombstones");
        for (int key = 256; key < 768; key++) {
            intKeys.put(key, key + 1000);
            longKeys.put(((long) key << 32) | (long) key, key + 2000);
        }
        assertEquals(640, intKeys.size(), "int rehash removes deleted slots");
        assertEquals(640, longKeys.size(), "long rehash removes deleted slots");
        for (int key = 1; key < 256; key += 2) {
            assertEquals(key + 1000, intKeys.rowOf(key), "int rehash preserves old live key");
            assertEquals(key + 2000,
                    longKeys.rowOf(((long) key << 32) | (long) key),
                    "long rehash preserves old live key");
        }
        for (int key = 256; key < 768; key++) {
            assertEquals(key + 1000, intKeys.rowOf(key), "int rehash preserves new key");
            assertEquals(key + 2000,
                    longKeys.rowOf(((long) key << 32) | (long) key),
                    "long rehash preserves new key");
        }
        assertHashMetrics(intKeys.capacity(), intKeys.probeCount(),
                intKeys.collisionCount(), intKeys.rehashCount(), "int");
        assertHashMetrics(longKeys.capacity(), longKeys.probeCount(),
                longKeys.collisionCount(), longKeys.rehashCount(), "long");
        int intCapacity = intKeys.capacity();
        int longCapacity = longKeys.capacity();
        intKeys.resetMetrics();
        longKeys.resetMetrics();
        assertEquals(0L, intKeys.probeCount(), "int probe reset");
        assertEquals(0L, intKeys.collisionCount(), "int collision reset");
        assertEquals(0L, intKeys.rehashCount(), "int rehash reset");
        assertEquals(intCapacity, intKeys.capacity(), "int reset preserves capacity");
        assertEquals(0L, longKeys.probeCount(), "long probe reset");
        assertEquals(0L, longKeys.collisionCount(), "long collision reset");
        assertEquals(0L, longKeys.rehashCount(), "long rehash reset");
        assertEquals(longCapacity, longKeys.capacity(), "long reset preserves capacity");
        intKeys.addMetrics(3L, 2L, 1L);
        assertEquals(3L, intKeys.probeCount(), "int carried probes");
        assertEquals(2L, intKeys.collisionCount(), "int carried collisions");
        assertEquals(1L, intKeys.rehashCount(), "int carried rehashes");
        assertIllegalArgument(new Runnable() {
            @Override public void run() { intKeys.addMetrics(1L, 2L, 0L); }
        }, "invalid carried metrics");
        assertEquals(3L, intKeys.probeCount(), "invalid metric carry is atomic");
        intKeys.resetMetrics();

        HashCompositeKeySpace compositeKeys = new HashCompositeKeySpace(0);
        Map<Integer, Long> compositeHashes = new HashMap<Integer, Long>();
        for (int row = 0; row < 256; row++) {
            long hash = (long) (row & 7);
            compositeKeys.ensureInsertCapacity();
            int slot = hashOnlyInsertionSlot(compositeKeys, hash);
            compositeKeys.putAt(slot, hash, row);
            compositeHashes.put(Integer.valueOf(row), Long.valueOf(hash));
        }
        for (int row = 0; row < 256; row += 2) {
            int slot = findHashAndRowSlot(compositeKeys,
                    compositeHashes.get(Integer.valueOf(row)).longValue(), row);
            compositeKeys.removeAt(slot);
            compositeHashes.remove(Integer.valueOf(row));
        }
        assertEquals(128, compositeKeys.size(), "composite live buckets after delete");
        assertEquals(256, compositeKeys.used(), "composite used retains tombstones");
        for (int row = 256; row < 768; row++) {
            long hash = (long) (row & 7);
            compositeKeys.ensureInsertCapacity();
            int slot = hashOnlyInsertionSlot(compositeKeys, hash);
            compositeKeys.putAt(slot, hash, row);
            compositeHashes.put(Integer.valueOf(row), Long.valueOf(hash));
        }
        assertEquals(640, compositeKeys.size(), "composite rehash removes deleted slots");
        for (Map.Entry<Integer, Long> entry : compositeHashes.entrySet()) {
            int slot = findHashAndRowSlot(compositeKeys,
                    entry.getValue().longValue(), entry.getKey().intValue());
            if (slot < 0) {
                throw new AssertionError("composite rehash lost row " + entry.getKey());
            }
        }
        assertHashMetrics(compositeKeys.capacity(), compositeKeys.probeCount(),
                compositeKeys.collisionCount(), compositeKeys.rehashCount(), "composite");
        int compositeCapacity = compositeKeys.capacity();
        compositeKeys.resetMetrics();
        assertEquals(0L, compositeKeys.probeCount(), "composite probe reset");
        assertEquals(0L, compositeKeys.collisionCount(), "composite collision reset");
        assertEquals(0L, compositeKeys.rehashCount(), "composite rehash reset");
        assertEquals(compositeCapacity, compositeKeys.capacity(),
                "composite reset preserves capacity");
    }

    private static void testCapacityOverflowGuards() {
        final int impossibleExpectedSize = (1 << 29) + 1;
        assertIllegalArgument(new Runnable() {
            @Override public void run() {
                new HashIntKeySpace(impossibleExpectedSize);
            }
        }, "int expected size overflow");
        assertIllegalArgument(new Runnable() {
            @Override public void run() {
                new HashLongKeySpace(impossibleExpectedSize);
            }
        }, "long expected size overflow");
        assertIllegalArgument(new Runnable() {
            @Override public void run() {
                new HashCompositeKeySpace(impossibleExpectedSize);
            }
        }, "composite expected size overflow");
        assertIllegalArgument(new Runnable() {
            @Override public void run() {
                HashCompositeKeySpace.estimatedPeakBytes(impossibleExpectedSize);
            }
        }, "composite estimator expected size overflow");
    }

    private static int removedSentinel() {
        return Integer.MIN_VALUE;
    }

    private static int hashOnlyInsertionSlot(HashCompositeKeySpace keys, long hash) {
        int deleted = -1;
        for (int slot = keys.firstSlot(hash); !keys.isEmpty(slot); slot = keys.nextSlot(slot)) {
            if (!keys.isLive(slot) && deleted < 0) {
                deleted = slot;
            }
        }
        if (deleted >= 0) {
            return deleted;
        }
        for (int slot = keys.firstSlot(hash); ; slot = keys.nextSlot(slot)) {
            if (keys.isEmpty(slot)) {
                return slot;
            }
        }
    }

    private static int findHashAndRowSlot(HashCompositeKeySpace keys, long hash, int row) {
        for (int slot = keys.firstSlot(hash); !keys.isEmpty(slot); slot = keys.nextSlot(slot)) {
            if (keys.isLive(slot) && keys.hashAt(slot) == hash && keys.rowAt(slot) == row) {
                return slot;
            }
        }
        return -1;
    }

    private static int findSlot(
            HashCompositeKeySpace keys, long hash, String key, Map<Integer, String> byRow) {
        for (int slot = keys.firstSlot(hash); !keys.isEmpty(slot); slot = keys.nextSlot(slot)) {
            if (keys.isLive(slot) && keys.hashAt(slot) == hash
                    && key.equals(byRow.get(Integer.valueOf(keys.rowAt(slot))))) {
                return slot;
            }
        }
        return -1;
    }

    private static int insertionSlot(
            HashCompositeKeySpace keys, long hash, String key, Map<Integer, String> byRow) {
        int deleted = -1;
        for (int slot = keys.firstSlot(hash); !keys.isEmpty(slot); slot = keys.nextSlot(slot)) {
            if (keys.isLive(slot)) {
                if (keys.hashAt(slot) == hash
                        && key.equals(byRow.get(Integer.valueOf(keys.rowAt(slot))))) {
                    throw new AssertionError("duplicate composite insertion");
                }
            } else if (deleted < 0) {
                deleted = slot;
            }
        }
        for (int slot = keys.firstSlot(hash); ; slot = keys.nextSlot(slot)) {
            if (keys.isEmpty(slot)) {
                return deleted < 0 ? slot : deleted;
            }
        }
    }

    private static long collisionHeavyHash(String key) {
        return (long) (key.hashCode() & 3);
    }

    private static void assertHashMetrics(
            int capacity, long probes, long collisions, long rehashes, String kind) {
        if (capacity <= 4 || probes <= 0L || collisions <= 0L || rehashes <= 0L
                || collisions > probes) {
            throw new AssertionError(kind + " hash metrics are inconsistent: capacity="
                    + capacity + " probes=" + probes + " collisions=" + collisions
                    + " rehashes=" + rehashes);
        }
    }

    private static void assertIllegalArgument(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message + " must fail");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
