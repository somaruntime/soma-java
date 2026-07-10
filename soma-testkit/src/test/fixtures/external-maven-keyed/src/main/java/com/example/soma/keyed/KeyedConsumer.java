package com.example.soma.keyed;

import com.example.soma.keyed.generated.KeyedParticleBatch;
import com.example.soma.keyed.generated.KeyedParticleKeys;
import com.example.soma.keyed.generated.KeyedParticleRow;
import com.example.soma.keyed.generated.KeyedParticleRows;
import com.example.soma.keyed.generated.KeyedParticleTable;
import com.example.soma.keyed.generated.LongKeyedParticleBatch;
import com.example.soma.keyed.generated.LongKeyedParticleTable;
import com.hgtech.soma.runtime.SomaRuntimeException;

import java.util.List;
import java.util.Optional;
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

        KeyedParticleKeys keys = table.keys();
        final int[] sum = new int[] {0};
        keys.forEach(new Consumer<Integer>() {
            @Override
            public void accept(Integer value) {
                sum[0] += value.intValue();
            }
        });
        require(sum[0] == 6, "key pipeline forEach");
        List<Integer> exported = table.keys().fetchAll();
        require(exported.size() == 3 && exported.get(0).intValue() == 1
                        && exported.get(2).intValue() == 3,
                "key pipeline stable value export");
        require(table.keys().findFirst().get().intValue() == 1,
                "key pipeline first");

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
        table.filter(new KeyedParticleRows.Predicate() {
            @Override
            public boolean test(KeyedParticleRow row) {
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
        System.out.println("keyed-consumer: ok");
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
                "long key pipeline value export");
        table.delete(firstId);
        require(table.fetch(secondId).energy == 20L, "long key compaction repair");
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
