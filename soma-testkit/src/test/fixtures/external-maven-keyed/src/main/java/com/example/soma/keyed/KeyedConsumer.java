package com.example.soma.keyed;

import com.example.soma.keyed.generated.KeyedParticleBatch;
import com.example.soma.keyed.generated.KeyedParticleKeyTraversal;
import com.example.soma.keyed.generated.KeyedParticleCursor;
import com.example.soma.keyed.generated.KeyedParticleScan;
import com.example.soma.keyed.generated.KeyedParticleTable;
import com.example.soma.keyed.generated.LongKeyedParticleBatch;
import com.example.soma.keyed.generated.LongKeyedParticleTable;
import com.example.soma.keyed.generated.BooleanKeyedBatch;
import com.example.soma.keyed.generated.BooleanKeyedTable;
import com.example.soma.keyed.generated.ByteKeyedBatch;
import com.example.soma.keyed.generated.ByteKeyedTable;
import com.example.soma.keyed.generated.DoubleKeyedBatch;
import com.example.soma.keyed.generated.DoubleKeyedTable;
import com.example.soma.keyed.generated.FloatKeyedBatch;
import com.example.soma.keyed.generated.FloatKeyedTable;
import com.example.soma.keyed.generated.ShortKeyedBatch;
import com.example.soma.keyed.generated.ShortKeyedTable;
import com.example.soma.keyed.generated.SchemaMetadata;
import com.hgtech.soma.runtime.SomaRuntimeException;
import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.metadata.SomaStorageLayout;
import com.hgtech.soma.runtime.metadata.SomaWorkloadProfile;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

public final class KeyedConsumer {
    private KeyedConsumer() {
    }

    public static void main(String[] args) {
        KeyedParticleBatch batch = new KeyedParticleBatch();
        batch.addValues(1, 10, true, 7);
        batch.addValues(2, 20, false, 99);
        batch.addValues(3, 30, true, 9);

        KeyedParticleTable table = KeyedParticleTable.create();
        table.addBatch(batch);
        require(table.containsKey(1), "contains key");
        require(!table.containsKey(99), "missing contains key");
        require(table.fetch(1).energy == 10, "fetch keyed row");
        Optional<KeyedParticle> found = table.find(2);
        require(found.isPresent() && found.get().priority == null, "find keyed row");

        table.mutate(1).setEnergy(11).clearPriority().commit();
        require(table.fetch(1).energy == 11 && table.fetch(1).priority == null,
                "keyed mutator excludes identity and updates fields");

        KeyedParticleKeyTraversal keys = table.keys();
        final int[] sum = new int[] {0};
        keys.forEach(new Consumer<Integer>() {
            @Override
            public void accept(Integer value) {
                sum[0] += value.intValue();
            }
        });
        require(sum[0] == 6, "key traversal forEach");
        expectCode("traversal_consumed", new Action() {
            @Override
            public void run() {
                keys.forEach(new Consumer<Integer>() {
                    @Override public void accept(Integer value) { }
                });
            }
        });
        KeyedParticleKeyTraversal validationFailure = table.keys();
        try {
            validationFailure.fetchAll(null);
            throw new AssertionError("null materialization budget must fail");
        } catch (NullPointerException expected) {
            // Validation happens before one-shot consumption.
        }
        require(validationFailure.fetchAll().size() == 3,
                "failed key traversal validation leaves the handle usable");
        KeyedParticleKeyTraversal reentrantDefaultBudget = table.keys();
        table.limit(1).forEach(candidate ->
                expectCode("reentrant_access", reentrantDefaultBudget::findFirst));
        expectCode("traversal_consumed", reentrantDefaultBudget::fetchAll);
        List<Integer> exported = table.keys().fetchAll();
        require(exported.size() == 3 && exported.get(0).intValue() == 1
                        && exported.get(2).intValue() == 3,
                "key traversal stable value export");
        require(table.keys().findFirst().get().intValue() == 1,
                "key traversal first");

        expectCode("duplicate_key", new Action() {
            @Override
            public void run() {
                KeyedParticleBatch duplicate = new KeyedParticleBatch();
                duplicate.addValues(1, 100, false, 0);
                table.addBatch(duplicate);
            }
        });
        require(table.size() == 3 && table.fetch(1).energy == 11,
                "duplicate append preserves facts");
        expectCode("missing_key", new Action() {
            @Override
            public void run() {
                table.fetch(99);
            }
        });

        table.delete(2);
        require(!table.containsKey(2) && table.fetch(3).energy == 30,
                "delete repairs compacted key slots");
        table.filter(new KeyedParticleScan.Predicate() {
            @Override
            public boolean test(KeyedParticleCursor row) {
                return row.id() == 1;
            }
        }).remove();
        require(table.size() == 1 && table.fetch(3).energy == 30,
                "row remove repairs key slots");

        expectCode("missing_key", new Action() {
            @Override
            public void run() {
                table.delete(1);
            }
        });
        testLongKeyBinding();
        testAdditionalPrimitiveKeyBindings();
        testHashIntGeneratedBinding();
        testRepeatedSmallBatchDoesNotRebuildEveryAppend();
        testBatchInternalDuplicateValidation();
        testSingletonAppendValidationAllocation();
        testKeyedSwapRemoveDifferential();
        testSegmentedKeyedRelocation();
        System.out.println("keyed-consumer: ok");
    }

    private static void testSegmentedKeyedRelocation() {
        RuntimePlan.Builder builder = SchemaMetadata.newPlan();
        builder.table("KeyedParticle")
                .initialCapacity(4)
                .planningRows(32769)
                .maximumRows(70000)
                .workloadProfile(SomaWorkloadProfile.SCAN_GROWTH);
        RuntimePlan plan = builder.build();
        require(plan.effectiveMetadata().requireTable("KeyedParticle")
                        .storageLayout()
                        == SomaStorageLayout.FLAT_HEAD_SEGMENTED_TAIL,
                "keyed large plan resolves segmented storage");
        KeyedParticleBatch batch = new KeyedParticleBatch(32770);
        for (int id = 0; id < 32770; id++) {
            batch.addValues(id, id * 2, (id & 1) == 0, id);
        }
        KeyedParticleTable table = KeyedParticleTable.create(plan);
        table.addBatch(batch);
        require(table.capacity() == 65536
                        && table.fetch(32767).energy == 65534
                        && table.fetch(32768).energy == 65536
                        && table.fetch(32769).energy == 65538,
                "key locator resolves rows across Segment boundary");
        table.delete(32768);
        require(!table.containsKey(32768)
                        && table.requireIndex(32769) == 32768
                        && table.fetch(32769).energy == 65538,
                "key locator repairs cross-Segment tail-fill relocation");
        table.release();
    }

    private static void testLongKeyBinding() {
        final long firstId = 0x100000003L;
        final long secondId = -0x200000007L;
        LongKeyedParticleBatch batch = new LongKeyedParticleBatch();
        batch.addValues(firstId, 10L);
        batch.addValues(secondId, 20L);
        LongKeyedParticleTable table = LongKeyedParticleTable.create();
        table.addBatch(batch);
        require(table.containsKey(firstId) && table.fetch(secondId).energy == 20L,
                "long key direct binding");
        table.mutate(firstId).setEnergy(11L).commit();
        require(table.fetch(firstId).energy == 11L, "long key mutator");
        require(table.keys().fetchAll().get(1).longValue() == secondId,
                "long key traversal value export");
        table.delete(firstId);
        require(table.fetch(secondId).energy == 20L, "long key compaction repair");
    }

    private static void testAdditionalPrimitiveKeyBindings() {
        BooleanKeyedBatch booleanBatch = new BooleanKeyedBatch();
        booleanBatch.addValues(true, 1);
        BooleanKeyedTable booleanTable = BooleanKeyedTable.create();
        booleanTable.addBatch(booleanBatch);
        require(booleanTable.fetch(true).payload == 1, "boolean key binding");

        ByteKeyedBatch byteBatch = new ByteKeyedBatch();
        byteBatch.addValues((byte) -7, 2);
        ByteKeyedTable byteTable = ByteKeyedTable.create();
        byteTable.addBatch(byteBatch);
        require(byteTable.fetch((byte) -7).payload == 2, "byte key binding");

        ShortKeyedBatch shortBatch = new ShortKeyedBatch();
        shortBatch.addValues((short) 30001, 3);
        ShortKeyedTable shortTable = ShortKeyedTable.create();
        shortTable.addBatch(shortBatch);
        require(shortTable.fetch((short) 30001).payload == 3, "short key binding");

        FloatKeyedBatch floatBatch = new FloatKeyedBatch();
        floatBatch.addValues(-0.0f, 4);
        FloatKeyedTable floatTable = FloatKeyedTable.create();
        floatTable.addBatch(floatBatch);
        require(Float.floatToIntBits(floatTable.fetch(0.0f).id) == Float.floatToIntBits(0.0f),
                "float negative zero canonicalization");
        expectCode("duplicate_key", new Action() {
            @Override
            public void run() {
                FloatKeyedBatch duplicate = new FloatKeyedBatch();
                duplicate.addValues(0.0f, 5);
                floatTable.addBatch(duplicate);
            }
        });
        expectCode("invalid_floating_access_value", new Action() {
            @Override
            public void run() {
                new FloatKeyedBatch().addValues(Float.NaN, 6);
            }
        });

        DoubleKeyedBatch doubleBatch = new DoubleKeyedBatch();
        doubleBatch.addValues(-0.0d, 7);
        DoubleKeyedTable doubleTable = DoubleKeyedTable.create();
        doubleTable.addBatch(doubleBatch);
        require(Double.doubleToLongBits(doubleTable.fetch(0.0d).id)
                        == Double.doubleToLongBits(0.0d),
                "double negative zero canonicalization");
        expectCode("invalid_floating_access_value", new Action() {
            @Override
            public void run() {
                doubleTable.containsKey(Double.POSITIVE_INFINITY);
            }
        });
    }

    private static void testHashIntGeneratedBinding() {
        RuntimePlan.Builder builder = SchemaMetadata.newPlan()
                .maximumAggregateStorageBytes(16L * 1024L * 1024L);
        builder.table("KeyedParticle")
                .maximumTableStorageBytes(1024L * 1024L)
                .maximumBulkScratchBytes(1024L * 1024L);
        KeyedParticleTable hash =
                KeyedParticleTable.create(builder.build());
        KeyedParticleBatch batch = new KeyedParticleBatch();
        batch.addValues(-1, 10, false, 0);
        batch.addValues(31, 20, true, 7);
        hash.addBatch(batch);
        require(hash.fetch(-1).energy == 10 && hash.fetch(31).priority == 7,
                "generated hash accepts the complete int identity domain");
        require(!hash.containsKey(32) && hash.findIndex(32) == -1,
                "generated hash missing lookup");
        expectCode("missing_key", new Action() {
            @Override public void run() { hash.requireIndex(32); }
        });
        hash.delete(-1);
        require(hash.requireIndex(31) == 0,
                "hash delete repairs packed row mapping");
        require("hash-int-v2".equals(hash.statsSnapshot().keySpaceImplementation()),
                "hash strategy is explicit and observable");
        hash.release();
        require(hash.statsSnapshot().capacity() == 0
                        && hash.statsSnapshot().keySpaceCapacity() == 0,
                "hash release drops table and key storage");
    }

    private static void testRepeatedSmallBatchDoesNotRebuildEveryAppend() {
        KeyedParticleTable table = KeyedParticleTable.create();
        for (int id = 0; id < 64; id++) {
            KeyedParticleBatch one = new KeyedParticleBatch(1);
            one.addValues(id, id, false, 0);
            table.addBatch(one);
        }
        require(table.size() == 64 && table.fetch(63).energy == 63,
                "repeated small keyed append preserves facts");
        require(table.statsSnapshot().keySpaceRehashCount() < 16L,
                "small batches only rebuild at geometric capacity boundaries");
    }

    private static void testBatchInternalDuplicateValidation() {
        final KeyedParticleTable table = KeyedParticleTable.create();
        final KeyedParticleBatch duplicate = new KeyedParticleBatch(2);
        duplicate.addValues(7, 1, false, 0);
        duplicate.addValues(7, 2, false, 0);
        expectCode("duplicate_key", new Action() {
            @Override public void run() { table.addBatch(duplicate); }
        });
        require(table.size() == 0, "batch-internal duplicate append is atomic");
    }

    private static void testKeyedSwapRemoveDifferential() {
        final int rowCount = 128;
        KeyedParticleBatch batch = new KeyedParticleBatch(rowCount);
        boolean[] live = new boolean[rowCount];
        for (int id = 0; id < rowCount; id++) {
            batch.addValues(id, id * 10, false, 0);
            live[id] = true;
        }
        KeyedParticleTable table = KeyedParticleTable.create();
        table.addBatch(batch);
        Random random = new Random(0x534f4d41L);
        for (int round = 0; round < 24 && table.size() > 8; round++) {
            final int divisor = 5 + random.nextInt(5);
            final int remainder = random.nextInt(divisor);
            int expectedRemoved = 0;
            for (int id = 0; id < rowCount; id++) {
                if (live[id] && id % divisor == remainder) expectedRemoved++;
            }
            long epoch = table.structuralEpoch();
            com.hgtech.soma.runtime.RemoveResult removed = table
                    .filter(row -> row.id() % divisor == remainder)
                    .remove();
            require(removed.removed() == expectedRemoved,
                    "keyed random swap-remove count");
            if (expectedRemoved == 0) {
                require(table.structuralEpoch() == epoch,
                        "empty keyed remove is non-structural");
            }
            for (int id = 0; id < rowCount; id++) {
                if (live[id] && id % divisor == remainder) live[id] = false;
            }
            int expectedSize = 0;
            for (int id = 0; id < rowCount; id++) {
                if (live[id]) {
                    expectedSize++;
                    require(table.containsKey(id) && table.fetch(id).energy == id * 10,
                            "keyed locator survives swap-remove id=" + id);
                } else {
                    require(!table.containsKey(id),
                            "removed keyed identity stays absent id=" + id);
                }
            }
            require(table.size() == expectedSize,
                    "keyed random swap-remove packed size");
        }
        table.release();
    }

    private static void testSingletonAppendValidationAllocation() {
        final int warmup = 12000;
        final int iterations = 2048;
        KeyedParticleBatch[] batches = new KeyedParticleBatch[warmup + iterations];
        for (int id = 0; id < batches.length; id++) {
            batches[id] = new KeyedParticleBatch(1);
            batches[id].addValues(id, id, false, 0);
        }

        RuntimePlan.Builder plan = SchemaMetadata.newPlan();
        plan.table("KeyedParticle").initialCapacity(16384);
        KeyedParticleTable table = KeyedParticleTable.create(
                plan.build());
        for (int index = 0; index < warmup; index++) {
            table.addBatch(batches[index]);
        }

        java.lang.management.ThreadMXBean management = ManagementFactory.getThreadMXBean();
        require(management instanceof com.sun.management.ThreadMXBean,
                "JDK must expose per-thread allocation counter for keyed shape evidence");
        com.sun.management.ThreadMXBean allocation =
                (com.sun.management.ThreadMXBean) management;
        if (!allocation.isThreadAllocatedMemoryEnabled()) {
            allocation.setThreadAllocatedMemoryEnabled(true);
        }
        long threadId = Thread.currentThread().getId();
        long before = allocation.getThreadAllocatedBytes(threadId);
        for (int index = warmup; index < batches.length; index++) {
            table.addBatch(batches[index]);
        }
        long bytes = allocation.getThreadAllocatedBytes(threadId) - before;
        require(bytes <= 8192L,
                "singleton keyed append validation must not allocate a temporary KeySpace bytes="
                        + bytes + " iterations=" + iterations);
        require(table.size() == batches.length
                        && table.fetch(batches.length - 1).energy == batches.length - 1,
                "singleton keyed append allocation fixture preserves facts");
    }
    private static void expectCode(String code, Action action) {
        try {
            action.run();
            throw new AssertionError("expected " + code);
        } catch (SomaRuntimeException expected) {
            require(code.equals(expected.code()), "error code " + code);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private interface Action {
        void run();
    }
}
