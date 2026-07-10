package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.HashIntKeySpace;
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

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
