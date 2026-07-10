package com.example.soma.dense;

import com.example.soma.dense.generated.ParticleBatch;
import com.example.soma.dense.generated.ParticleMutableRow;
import com.example.soma.dense.generated.ParticleRow;
import com.example.soma.dense.generated.ParticleRows;
import com.example.soma.dense.generated.ParticleTable;
import com.example.soma.dense.generated.PrimitiveSampleBatch;
import com.example.soma.dense.generated.PrimitiveSampleTable;
import com.hgtech.soma.runtime.MaterializationBudget;
import com.hgtech.soma.runtime.SomaRuntimeException;
import com.hgtech.soma.runtime.UpdateResult;

import java.util.List;

public final class DenseConsumer {
    private DenseConsumer() {
    }

    public static void main(String[] args) {
        testAllPrimitiveAndPresenceBindings();

        ParticleBatch batch = new ParticleBatch(2);
        batch.addValues(1, 10L, 1.5f, true, 7);
        batch.addValues(2, 20L, 2.5f, false, 99);
        batch.addValues(new ParticleBatch.Writer() {
            @Override
            public void write(ParticleBatch.RowBuilder row) {
                row.setId(3);
                row.setTicks(30L);
                row.setX(3.5f);
                row.setEnergy(9);
            }
        });

        Particle detached = new Particle();
        detached.id = 4;
        detached.ticks = 40L;
        detached.x = 4.5f;
        detached.energy = null;
        batch.add(detached);
        detached.x = -1.0f;

        ParticleTable table = ParticleTable.create();
        table.addBatch(batch);
        require(table.size() == 4, "addBatch size");
        require(table.capacity() >= 4, "capacity");
        require(table.runtimePlan() == ParticleTable.defaultRuntimePlan(), "default plan identity");

        Particle first = table.fetchAt(0);
        require(first.id == 1 && first.ticks == 10L && first.x == 1.5f,
                "fetchAt primitive facts");
        require(Integer.valueOf(7).equals(first.energy), "fetchAt optional present");
        require(table.fetchAt(1).energy == null, "fetchAt optional absent");
        require(table.fetchAt(3).x == 4.5f, "batch detached copy");

        final long epoch = table.structuralEpoch();
        table.mutateAt(0).setTicks(11L).setEnergy(8).commit();
        require(table.fetchAt(0).ticks == 11L, "mutator required field");
        require(Integer.valueOf(8).equals(table.fetchAt(0).energy), "mutator optional field");
        require(table.structuralEpoch() == epoch, "in-place mutation is not structural");

        ParticleRows selected = table.filter(new ParticleRows.Predicate() {
            @Override
            public boolean test(ParticleRow row) {
                return row.id() >= 2;
            }
        }).skip(1).limit(2);
        require(selected.count() == 2L, "fused row pipeline count");
        expectCode("pipeline_consumed", new Action() {
            @Override
            public void run() {
                selected.count();
            }
        });

        final float beforeTwo = table.fetchAt(1).x;
        final float beforeThree = table.fetchAt(2).x;
        expectCode("callback_failed", new Action() {
            @Override
            public void run() {
                table.filter(new ParticleRows.Predicate() {
                    @Override
                    public boolean test(ParticleRow row) {
                        return row.id() == 2 || row.id() == 3;
                    }
                }).update(new ParticleRows.Updater() {
                    @Override
                    public void update(ParticleMutableRow row) {
                        row.setX(row.x() + 10.0f);
                        if (row.id() == 3) {
                            throw new IllegalStateException("expected callback failure");
                        }
                    }
                });
            }
        });
        require(table.fetchAt(1).x == beforeTwo && table.fetchAt(2).x == beforeThree,
                "failed update atomicity");

        UpdateResult updated = table.filter(new ParticleRows.Predicate() {
            @Override
            public boolean test(ParticleRow row) {
                return row.id() == 2 || row.id() == 3;
            }
        }).update(new ParticleRows.Updater() {
            @Override
            public void update(ParticleMutableRow row) {
                row.setX(row.x() + 1.0f);
                row.clearEnergy();
            }
        });
        require(updated.scanned() == 4L && updated.matched() == 2L
                        && updated.changed() == 2L,
                "update counters");
        require(table.fetchAt(2).energy == null, "update optional clear");

        List<Particle> materialized = table.materialize();
        require(materialized.size() == 4, "whole-table materialization");
        materialized.get(0).x = -100.0f;
        require(table.fetchAt(0).x == 1.5f, "materialization detached boundary");

        final MaterializationBudget tinyBudget = MaterializationBudget.builder()
                .maximumRows(1)
                .build();
        expectCode("materialization_budget_exceeded", new Action() {
            @Override
            public void run() {
                table.materialize(tinyBudget);
            }
        });

        table.replaceAll(new ParticleBatch());
        require(table.size() == 0, "replaceAll empty");
        table.addBatch(batch);
        table.clear();
        require(table.size() == 0, "clear");
        table.release();
        table.release();
        require(table.isReleased() && table.statsSnapshot().released(), "release terminal state");
        expectCode("table_released", new Action() {
            @Override
            public void run() {
                table.fetchAt(0);
            }
        });
    }

    private static void testAllPrimitiveAndPresenceBindings() {
        PrimitiveSampleBatch batch = new PrimitiveSampleBatch();
        batch.addValues(
                true, (byte) 1, (short) 2, 3, 4L, 5.0f, 6.0d,
                true, false,
                true, (byte) 7,
                true, (short) 8,
                true, 9,
                true, 10L,
                true, 11.0f,
                false, 12.0d);
        PrimitiveSampleTable table = PrimitiveSampleTable.create();
        table.addBatch(batch);
        PrimitiveSample row = table.fetchAt(0);
        require(row.requiredBoolean && row.requiredByte == 1 && row.requiredShort == 2,
                "required narrow primitive bindings");
        require(row.requiredInt == 3 && row.requiredLong == 4L
                        && row.requiredFloat == 5.0f && row.requiredDouble == 6.0d,
                "required numeric primitive bindings");
        require(Boolean.FALSE.equals(row.optionalBoolean)
                        && Byte.valueOf((byte) 7).equals(row.optionalByte)
                        && Short.valueOf((short) 8).equals(row.optionalShort)
                        && Integer.valueOf(9).equals(row.optionalInt)
                        && Long.valueOf(10L).equals(row.optionalLong)
                        && Float.valueOf(11.0f).equals(row.optionalFloat)
                        && row.optionalDouble == null,
                "optional primitive presence bindings");
        table.release();
    }

    private static void expectCode(String code, Action action) {
        try {
            action.run();
            throw new AssertionError("expected failure code=" + code);
        } catch (SomaRuntimeException failure) {
            require(code.equals(failure.code()),
                    "failure code expected=" + code + " actual=" + failure.code());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private interface Action {
        void run();
    }
}
