package com.example.soma.dense;

import com.example.soma.dense.generated.ParticleBatch;
import com.example.soma.dense.generated.ParticleMutableRow;
import com.example.soma.dense.generated.ParticleRow;
import com.example.soma.dense.generated.ParticleRows;
import com.example.soma.dense.generated.ParticleTable;
import com.example.soma.dense.generated.PrimitiveSampleBatch;
import com.example.soma.dense.generated.PrimitiveSampleTable;
import com.hgtech.soma.runtime.BooleanColumnView;
import com.hgtech.soma.runtime.BooleanConsumer;
import com.hgtech.soma.runtime.ByteColumnView;
import com.hgtech.soma.runtime.ByteConsumer;
import com.hgtech.soma.runtime.DoubleColumnView;
import com.hgtech.soma.runtime.FloatColumnView;
import com.hgtech.soma.runtime.FloatConsumer;
import com.hgtech.soma.runtime.FloatColumnPipeline;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.LongColumnView;
import com.hgtech.soma.runtime.MaterializationBudget;
import com.hgtech.soma.runtime.RemoveResult;
import com.hgtech.soma.runtime.SomaRuntimeException;
import com.hgtech.soma.runtime.ShortColumnView;
import com.hgtech.soma.runtime.ShortConsumer;
import com.hgtech.soma.runtime.TableStats;
import com.hgtech.soma.runtime.UpdateResult;

import java.util.List;
import java.util.Random;
import java.lang.management.ManagementFactory;
import java.util.function.LongConsumer;

public final class DenseConsumer {
    private static volatile Object allocationEscape;

    private DenseConsumer() {
    }

    public static void main(String[] args) {
        testAllPrimitiveAndPresenceBindings();
        testDenseDifferentialOracle();
        testColumnPipelineAllocationShape();
        testColumnViewAllocationShape();
        testColumnViewAcquisitionAllocation();
        testRowPipelineAllocationShape();

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
        require(table.filter(new ParticleRows.Predicate() {
            @Override
            public boolean test(ParticleRow row) {
                return row.id() > 0;
            }
        }).skip(0).limit(4).filter(new ParticleRows.Predicate() {
            @Override
            public boolean test(ParticleRow row) {
                return row.ticks() > 0L;
            }
        }).skip(0).limit(4).filter(new ParticleRows.Predicate() {
            @Override
            public boolean test(ParticleRow row) {
                return row.x() > 0.0f;
            }
        }).skip(0).limit(4).count() == 4L,
                "deep one-shot row pipeline plan growth");
        expectCode("pipeline_consumed", new Action() {
            @Override
            public void run() {
                selected.count();
            }
        });

        require(table.anyMatch(new ParticleRows.Predicate() {
            @Override
            public boolean test(ParticleRow row) {
                return row.id() == 4;
            }
        }), "anyMatch");
        require(table.noneMatch(new ParticleRows.Predicate() {
            @Override
            public boolean test(ParticleRow row) {
                return row.id() > 4;
            }
        }), "noneMatch");
        require(table.filter(new ParticleRows.Predicate() {
            @Override
            public boolean test(ParticleRow row) {
                return row.id() == 3;
            }
        }).findFirst().get().id == 3, "findFirst detached result");
        expectCode("empty_result", new Action() {
            @Override
            public void run() {
                table.filter(new ParticleRows.Predicate() {
                    @Override
                    public boolean test(ParticleRow row) {
                        return false;
                    }
                }).firstOrThrow();
            }
        });
        List<Particle> descending = table.sorted(new ParticleRows.Comparator() {
            @Override
            public int compare(ParticleRow left, ParticleRow right) {
                return right.id() - left.id();
            }
        }).fetchAll();
        require(descending.size() == 4 && descending.get(0).id == 4
                        && descending.get(3).id == 1,
                "stable primitive-index sorted fetchAll");
        final IndexSnapshot indexSnapshot = table.filter(new ParticleRows.Predicate() {
            @Override
            public boolean test(ParticleRow row) {
                return row.id() >= 3;
            }
        }).rowIndexes();
        int[] indexes = indexSnapshot.toArray();
        require(indexes.length == 2 && indexes[0] == 2 && indexes[1] == 3,
                "primitive rowIndexes");
        indexes[0] = -1;
        require(indexSnapshot.indexAt(0) == 2,
                "IndexSnapshot owns a defensive detached copy");
        IndexSnapshot singleSnapshot = table.limit(1).rowIndexes();
        int[] singleIndexes = singleSnapshot.toArray();
        singleIndexes[0] = -1;
        require(singleSnapshot.size() == 1 && singleSnapshot.indexAt(0) == 0,
                "single IndexSnapshot keeps detached-copy semantics");
        IndexSnapshot emptySnapshot = table.filter(new ParticleRows.Predicate() {
            @Override
            public boolean test(ParticleRow row) {
                return false;
            }
        }).rowIndexes();
        require(emptySnapshot.size() == 0 && emptySnapshot.toArray().length == 0,
                "empty IndexSnapshot remains detached");
        table.requireCurrent(indexSnapshot);
        final ParticleTable otherTable = ParticleTable.create();
        expectCode("index_snapshot_wrong_table", new Action() {
            @Override
            public void run() {
                otherTable.requireCurrent(indexSnapshot);
            }
        });
        otherTable.release();

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

        final long[] tickSum = new long[] {0L};
        table.ticksValues().forEachLong(new LongConsumer() {
            @Override
            public void accept(long value) {
                tickSum[0] += value;
            }
        });
        require(tickSum[0] == 101L, "required long column pipeline");
        final int[] energySum = new int[] {0};
        table.energyValues().forEachInt(value -> energySum[0] += value);
        require(energySum[0] == 8, "optional pipeline visits only present values");
        expectCode("reentrant_access", new Action() {
            @Override
            public void run() {
                table.xValues().forEachFloat(new FloatConsumer() {
                    @Override
                    public void accept(float value) {
                        table.clear();
                    }
                });
            }
        });
        require(table.size() == 4, "column pipeline callback cannot structurally mutate");

        FloatColumnView xView = table.xColumn();
        try {
            require(xView.isPresent(1) && xView.getFloat(1) == 3.5f,
                    "typed float column view");
            table.mutateAt(0).setTicks(12L).commit();
            require(table.fetchAt(0).ticks == 12L && xView.getFloat(1) == 3.5f,
                    "non-structural mutation remains valid under a view");
            expectCode("view_pinned", new Action() {
                @Override
                public void run() {
                    table.clear();
                }
            });
        } finally {
            xView.close();
        }
        expectCode("released_view", new Action() {
            @Override
            public void run() {
                xView.getFloat(0);
            }
        });

        List<Particle> materialized = table.materialize();
        require(materialized.size() == 4, "whole-table materialization");
        TableStats materializedStats = table.statsSnapshot();
        require(materializedStats.lastMaterializationMaximumOwnershipDepth() == 0
                        && materializedStats.lastMaterializationTableInstances() == 1L
                        && materializedStats.lastMaterializationRows() == 4L
                        && materializedStats.lastMaterializationLeafValues() == 13L
                        && materializedStats.lastMaterializationEstimatedAllocationBytes()
                        == 280L,
                "exact optional primitive materialization estimator");
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

        final long epochBeforeFailedRemove = table.structuralEpoch();
        expectCode("callback_failed", new Action() {
            @Override
            public void run() {
                table.filter(new ParticleRows.Predicate() {
                    @Override
                    public boolean test(ParticleRow row) {
                        if (row.id() == 3) {
                            throw new IllegalStateException("expected remove predicate failure");
                        }
                        return row.id() >= 2;
                    }
                }).remove();
            }
        });
        require(table.size() == 4 && table.fetchAt(2).id == 3
                        && table.structuralEpoch() == epochBeforeFailedRemove,
                "failed remove preserves visible table facts");

        RemoveResult removed = table.filter(new ParticleRows.Predicate() {
            @Override
            public boolean test(ParticleRow row) {
                return row.id() == 3;
            }
        }).remove();
        require(removed.scanned() == 4L && removed.matched() == 1L
                        && removed.removed() == 1L && removed.compacted() == 1L,
                "remove counters and dense swap-remove accounting");
        require(table.size() == 3 && table.fetchAt(2).id == 4,
                "remove fills the packed hole from the physical tail");
        expectCode("stale_index_snapshot", new Action() {
            @Override
            public void run() {
                table.requireCurrent(indexSnapshot);
            }
        });
        require(indexSnapshot.indexAt(0) == 2,
                "stale IndexSnapshot remains a raw detached sequence, not an automatic live guard");
        final long epochBeforeEmptyRemove = table.structuralEpoch();
        RemoveResult emptyRemove = table.filter(new ParticleRows.Predicate() {
            @Override
            public boolean test(ParticleRow row) {
                return false;
            }
        }).remove();
        require(emptyRemove.scanned() == 3L && emptyRemove.matched() == 0L
                        && emptyRemove.removed() == 0L && emptyRemove.compacted() == 0L,
                "empty remove counters");
        require(table.structuralEpoch() == epochBeforeEmptyRemove,
                "empty remove is non-structural");

        table.replaceAll(new ParticleBatch());
        require(table.size() == 0, "replaceAll empty");
        table.addBatch(batch);
        table.clear();
        require(table.size() == 0, "clear");
        final IndexSnapshot releaseSnapshot = table.rowIndexes();
        IntColumnView releasedView = table.idColumn();
        table.release();
        table.release();
        require(table.isReleased() && table.statsSnapshot().released(), "release terminal state");
        expectCode("table_released", new Action() {
            @Override
            public void run() {
                releasedView.getInt(0);
            }
        });
        releasedView.close();
        expectCode("table_released", new Action() {
            @Override
            public void run() {
                table.fetchAt(0);
            }
        });
        expectCode("table_released", new Action() {
            @Override
            public void run() {
                table.requireCurrent(releaseSnapshot);
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
        final boolean[] booleanValue = new boolean[] {false};
        table.requiredBooleanValues().forEachBoolean(new BooleanConsumer() {
            @Override
            public void accept(boolean value) {
                booleanValue[0] = value;
            }
        });
        final byte[] byteValue = new byte[] {0};
        table.requiredByteValues().forEachByte(new ByteConsumer() {
            @Override
            public void accept(byte value) {
                byteValue[0] = value;
            }
        });
        final short[] shortValue = new short[] {0};
        table.requiredShortValues().forEachShort(new ShortConsumer() {
            @Override
            public void accept(short value) {
                shortValue[0] = value;
            }
        });
        final int[] intValue = new int[] {0};
        table.requiredIntValues().forEachInt(value -> intValue[0] = value);
        final long[] longValue = new long[] {0L};
        table.requiredLongValues().forEachLong(new LongConsumer() {
            @Override
            public void accept(long value) {
                longValue[0] = value;
            }
        });
        final float[] floatValue = new float[] {0.0f};
        table.requiredFloatValues().forEachFloat(new FloatConsumer() {
            @Override
            public void accept(float value) {
                floatValue[0] = value;
            }
        });
        final double[] doubleValue = new double[] {0.0d};
        table.requiredDoubleValues().forEachDouble(value -> doubleValue[0] = value);
        require(booleanValue[0] && byteValue[0] == 1 && shortValue[0] == 2
                        && intValue[0] == 3 && longValue[0] == 4L
                        && floatValue[0] == 5.0f && doubleValue[0] == 6.0d,
                "all required primitive column pipelines");
        final int[] optionalDoubleCallbacks = new int[] {0};
        table.optionalDoubleValues().forEachDouble(value -> optionalDoubleCallbacks[0]++);
        require(optionalDoubleCallbacks[0] == 0, "absent optional pipeline has no payload callback");
        BooleanColumnView booleanView = table.requiredBooleanColumn();
        ByteColumnView byteView = table.requiredByteColumn();
        ShortColumnView shortView = table.requiredShortColumn();
        IntColumnView intView = table.requiredIntColumn();
        LongColumnView longView = table.requiredLongColumn();
        FloatColumnView floatView = table.requiredFloatColumn();
        DoubleColumnView doubleView = table.requiredDoubleColumn();
        try {
            require(booleanView.isPresent(0) && booleanView.getBoolean(0)
                            && byteView.getByte(0) == 1 && shortView.getShort(0) == 2
                            && intView.getInt(0) == 3 && longView.getLong(0) == 4L
                            && floatView.getFloat(0) == 5.0f
                            && doubleView.getDouble(0) == 6.0d,
                    "all primitive column views");
        } finally {
            booleanView.close();
            byteView.close();
            shortView.close();
            intView.close();
            longView.close();
            floatView.close();
            doubleView.close();
        }
        DoubleColumnView absentOptionalView = table.optionalDoubleColumn();
        try {
            require(!absentOptionalView.isPresent(0), "optional view presence");
            expectCode("optional_absent", new Action() {
                @Override
                public void run() {
                    absentOptionalView.getDouble(0);
                }
            });
        } finally {
            absentOptionalView.close();
        }
        table.release();
    }

    private static void testDenseDifferentialOracle() {
        Random random = new Random(731942L);
        for (int seed = 0; seed < 12; seed++) {
            ParticleBatch batch = new ParticleBatch();
            List<Particle> oracle = new java.util.ArrayList<Particle>();
            int rows = 20 + random.nextInt(25);
            for (int row = 0; row < rows; row++) {
                Particle value = new Particle();
                value.id = row;
                value.ticks = random.nextInt(1000);
                value.x = random.nextFloat() * 100.0f;
                value.energy = random.nextBoolean() ? Integer.valueOf(random.nextInt(100)) : null;
                batch.add(value);
                oracle.add(copy(value));
            }
            ParticleTable table = ParticleTable.create();
            table.addBatch(batch);
            for (int round = 0; round < 5; round++) {
                final int updateDivisor = 2 + random.nextInt(4);
                final int updateRemainder = random.nextInt(updateDivisor);
                table.filter(new ParticleRows.Predicate() {
                    @Override
                    public boolean test(ParticleRow row) {
                        return row.id() % updateDivisor == updateRemainder;
                    }
                }).update(new ParticleRows.Updater() {
                    @Override
                    public void update(ParticleMutableRow row) {
                        row.setX(row.x() + 0.25f);
                        if ((row.id() & 1) == 0) {
                            row.clearEnergy();
                        }
                    }
                });
                for (Particle value : oracle) {
                    if (value.id % updateDivisor == updateRemainder) {
                        value.x += 0.25f;
                        if ((value.id & 1) == 0) {
                            value.energy = null;
                        }
                    }
                }
                final int removeDivisor = 2 + random.nextInt(4);
                final int removeRemainder = random.nextInt(removeDivisor);
                table.filter(new ParticleRows.Predicate() {
                    @Override
                    public boolean test(ParticleRow row) {
                        return row.id() % removeDivisor == removeRemainder;
                    }
                }).remove();
                swapRemoveOracle(oracle, removeDivisor, removeRemainder);
                assertParticles(oracle, table.materialize(), "differential dense facts");
                List<Particle> descending = table.sorted(new ParticleRows.Comparator() {
                    @Override
                    public int compare(ParticleRow left, ParticleRow right) {
                        return right.id() - left.id();
                    }
                }).fetchAll();
                List<Particle> expectedDescending = new java.util.ArrayList<Particle>(oracle);
                java.util.Collections.sort(expectedDescending, new java.util.Comparator<Particle>() {
                    @Override
                    public int compare(Particle left, Particle right) {
                        return right.id - left.id;
                    }
                });
                assertParticles(expectedDescending, descending, "differential sorted facts");
            }
            table.release();
        }
    }

    private static void testColumnPipelineAllocationShape() {
        final FloatConsumer sink = new FloatConsumer() {
            @Override
            public void accept(float value) {
                if (value == Float.MIN_VALUE) {
                    throw new AssertionError("unreachable");
                }
            }
        };
        ParticleTable small = allocationTable(32);
        ParticleTable large = allocationTable(512);
        FloatColumnPipeline smallPipeline = small.xValues();
        FloatColumnPipeline largePipeline = large.xValues();
        for (int round = 0; round < 1000; round++) {
            smallPipeline.forEachFloat(sink);
            largePipeline.forEachFloat(sink);
        }
        java.lang.management.ThreadMXBean management = ManagementFactory.getThreadMXBean();
        require(management instanceof com.sun.management.ThreadMXBean,
                "JDK must expose per-thread allocation counter for shape evidence");
        com.sun.management.ThreadMXBean allocation = (com.sun.management.ThreadMXBean) management;
        if (!allocation.isThreadAllocatedMemoryEnabled()) {
            allocation.setThreadAllocatedMemoryEnabled(true);
        }
        long threadId = Thread.currentThread().getId();
        long smallBytes = allocatedBytes(allocation, threadId, smallPipeline, sink);
        long largeBytes = allocatedBytes(allocation, threadId, largePipeline, sink);
        require(largeBytes <= smallBytes + 16384L,
                "column pipeline allocation must not scale with rows small="
                        + smallBytes + " large=" + largeBytes);
        small.release();
        large.release();
    }

    private static void testColumnViewAllocationShape() {
        ParticleTable small = allocationTable(32);
        ParticleTable large = allocationTable(512);
        FloatColumnView smallView = small.xColumn();
        FloatColumnView largeView = large.xColumn();
        try {
            for (int round = 0; round < 1000; round++) {
                readColumn(smallView, 32);
                readColumn(largeView, 512);
            }
            com.sun.management.ThreadMXBean allocation = allocationCounter();
            long threadId = Thread.currentThread().getId();
            long smallBytes = allocatedColumnViewBytes(
                    allocation, threadId, smallView, 32);
            long largeBytes = allocatedColumnViewBytes(
                    allocation, threadId, largeView, 512);
            require(largeBytes <= smallBytes + 16384L,
                    "column view allocation must not scale with reads small="
                            + smallBytes + " large=" + largeBytes);
        } finally {
            smallView.close();
            largeView.close();
            small.release();
            large.release();
        }
    }

    private static void testColumnViewAcquisitionAllocation() {
        ParticleTable table = allocationTable(1);
        try {
            for (int round = 0; round < 12000; round++) {
                FloatColumnView view = table.xColumn();
                allocationEscape = view;
                view.getFloat(0);
                view.close();
            }
            com.sun.management.ThreadMXBean allocation = allocationCounter();
            long threadId = Thread.currentThread().getId();
            int iterations = 4096;
            long before = allocation.getThreadAllocatedBytes(threadId);
            float checksum = 0.0f;
            for (int round = 0; round < iterations; round++) {
                FloatColumnView view = table.xColumn();
                allocationEscape = view;
                checksum += view.getFloat(0);
                view.close();
            }
            long bytes = allocation.getThreadAllocatedBytes(threadId) - before;
            if (checksum == Float.MIN_VALUE) throw new AssertionError("unreachable");
            require(bytes <= 128L * iterations + 16384L,
                    "column view acquisition must only allocate the view handle bytes="
                            + bytes + " iterations=" + iterations);
        } finally {
            allocationEscape = null;
            table.release();
        }
    }

    private static void testRowPipelineAllocationShape() {
        ParticleTable small = allocationTable(32);
        ParticleTable large = allocationTable(512);
        ParticleRows.Predicate predicate = new ParticleRows.Predicate() {
            @Override
            public boolean test(ParticleRow row) {
                return row.id() >= 0;
            }
        };
        for (int round = 0; round < 1000; round++) {
            small.filter(predicate).count();
            large.filter(predicate).count();
        }
        com.sun.management.ThreadMXBean allocation = allocationCounter();
        long threadId = Thread.currentThread().getId();
        long smallBytes = allocatedRowPipelineBytes(
                allocation, threadId, small, predicate);
        long largeBytes = allocatedRowPipelineBytes(
                allocation, threadId, large, predicate);
        require(largeBytes <= smallBytes + 16384L,
                "row pipeline allocation must not scale with rows small="
                        + smallBytes + " large=" + largeBytes);
        small.release();
        large.release();
    }

    private static com.sun.management.ThreadMXBean allocationCounter() {
        java.lang.management.ThreadMXBean management =
                ManagementFactory.getThreadMXBean();
        require(management instanceof com.sun.management.ThreadMXBean,
                "JDK must expose per-thread allocation counter for shape evidence");
        com.sun.management.ThreadMXBean allocation =
                (com.sun.management.ThreadMXBean) management;
        if (!allocation.isThreadAllocatedMemoryEnabled()) {
            allocation.setThreadAllocatedMemoryEnabled(true);
        }
        return allocation;
    }

    private static float readColumn(FloatColumnView view, int rows) {
        float checksum = 0.0f;
        for (int row = 0; row < rows; row++) {
            checksum += view.getFloat(row);
        }
        return checksum;
    }

    private static long allocatedColumnViewBytes(
            com.sun.management.ThreadMXBean allocation,
            long threadId,
            FloatColumnView view,
            int rows) {
        long before = allocation.getThreadAllocatedBytes(threadId);
        float checksum = 0.0f;
        for (int round = 0; round < 300; round++) {
            checksum += readColumn(view, rows);
        }
        if (checksum == Float.MIN_VALUE) throw new AssertionError("unreachable");
        return allocation.getThreadAllocatedBytes(threadId) - before;
    }

    private static long allocatedRowPipelineBytes(
            com.sun.management.ThreadMXBean allocation,
            long threadId,
            ParticleTable table,
            ParticleRows.Predicate predicate) {
        long before = allocation.getThreadAllocatedBytes(threadId);
        long checksum = 0L;
        for (int round = 0; round < 300; round++) {
            checksum += table.filter(predicate).count();
        }
        if (checksum == Long.MIN_VALUE) throw new AssertionError("unreachable");
        return allocation.getThreadAllocatedBytes(threadId) - before;
    }

    private static ParticleTable allocationTable(int rows) {
        ParticleBatch batch = new ParticleBatch(rows);
        for (int index = 0; index < rows; index++) {
            batch.addValues(index, index, (float) index, false, 0);
        }
        ParticleTable table = ParticleTable.create();
        table.addBatch(batch);
        return table;
    }

    private static long allocatedBytes(
            com.sun.management.ThreadMXBean allocation,
            long threadId,
            FloatColumnPipeline pipeline,
            FloatConsumer sink) {
        long before = allocation.getThreadAllocatedBytes(threadId);
        for (int round = 0; round < 300; round++) {
            pipeline.forEachFloat(sink);
        }
        return allocation.getThreadAllocatedBytes(threadId) - before;
    }

    private static Particle copy(Particle source) {
        Particle result = new Particle();
        result.id = source.id;
        result.ticks = source.ticks;
        result.x = source.x;
        result.energy = source.energy;
        return result;
    }

    private static void assertParticles(List<Particle> expected, List<Particle> actual, String message) {
        require(expected.size() == actual.size(), message + " size");
        for (int index = 0; index < expected.size(); index++) {
            Particle left = expected.get(index);
            Particle right = actual.get(index);
            require(left.id == right.id && left.ticks == right.ticks && left.x == right.x
                            && (left.energy == null ? right.energy == null : left.energy.equals(right.energy)),
                    message + " row=" + index);
        }
    }

    private static void swapRemoveOracle(
            List<Particle> rows, int divisor, int remainder) {
        int previous = rows.size();
        int[] selected = new int[previous];
        int count = 0;
        for (int row = 0; row < previous; row++) {
            if (rows.get(row).id % divisor == remainder) selected[count++] = row;
        }
        int newSize = previous - count;
        int tail = previous - 1;
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
            require(tail >= newSize, "dense oracle swap-remove tail");
            rows.set(write, rows.get(tail--));
        }
        while (rows.size() > newSize) rows.remove(rows.size() - 1);
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
