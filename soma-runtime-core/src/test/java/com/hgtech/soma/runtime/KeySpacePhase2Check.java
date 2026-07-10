package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.HashIntKeySpace;
import com.hgtech.soma.runtime.generated.HashLongKeySpace;
import com.hgtech.soma.runtime.generated.HashCompositeKeySpace;
import com.hgtech.soma.runtime.generated.SparseIntKeySpace;

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
        testSparseIntPackedSlots();
        testHashIntRandomized();
        testHashLongRandomized();
        testHashCompositeProbeRandomized();
        System.out.println("keyspace-phase2-test: ok");
    }

    private static void testSparseIntPackedSlots() {
        SparseIntKeySpace keys = new SparseIntKeySpace(127);
        List<Integer> oracle = new ArrayList<Integer>();
        for (int key = 0; key < 96; key += 3) {
            keys.put(key, oracle.size());
            oracle.add(Integer.valueOf(key));
        }
        for (int step = 0; step < 12; step++) {
            int row = (step * 5) % oracle.size();
            oracle.remove(row);
            keys.removeAt(row);
            assertEquals(oracle.size(), keys.size(), "sparse size");
            for (int index = 0; index < oracle.size(); index++) {
                assertEquals(index, keys.rowOf(oracle.get(index).intValue()), "sparse packed slot");
            }
        }
        assertEquals(-1, keys.rowOf(-1), "negative sparse missing");
        assertEquals(-1, keys.rowOf(128), "overflow sparse missing");
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

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
