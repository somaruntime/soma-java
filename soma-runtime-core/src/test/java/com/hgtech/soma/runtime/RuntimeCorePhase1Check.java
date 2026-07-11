package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.ColumnGroup;
import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.GeneratedMetadata;
import com.hgtech.soma.runtime.generated.HashCompositeKeySpace;
import com.hgtech.soma.runtime.generated.IntColumn;
import com.hgtech.soma.runtime.generated.LongColumn;
import com.hgtech.soma.runtime.generated.MaterializationTracker;
import com.hgtech.soma.runtime.generated.PresenceBitmap;
import com.hgtech.soma.runtime.generated.RuntimeCompatibility;
import com.hgtech.soma.runtime.generated.RowPermutationSidecar;

import java.util.Locale;
import java.util.Random;
import java.util.TreeMap;

/** Phase 1 runtime-core correctness evidence，使用完整 JDK 8 直接执行。 */
public final class RuntimeCorePhase1Check {
    private RuntimeCorePhase1Check() {
    }

    public static void main(String[] args) {
        testCanonicalIdentityIsLocaleIndependent();
        testPlanReplacementChangesIdentity();
        testCompatibilityBoundary();
        testDenseColumnsLifecycleAndStats();
        testStructuralRemoveStateTransition();
        testViewLifecycleState();
        testPresenceBitmapAgainstOracle();
        testMaterializationBudget();
        testSidecarProtocolAndStats();
        assertTrue(HashCompositeKeySpace.estimatedPeakBytes(1024)
                        > 40L * 1024L,
                "composite hash peak estimator includes final rehash coexistence");
        testBoundedFailureEnvelope();
        expectCode("empty_result", new ThrowingRunnable() {
            @Override
            public void run() {
                throw com.hgtech.soma.runtime.generated.RuntimeFailures.emptyResult(
                        "Order", "rows.firstOrThrow");
            }
        });
        System.out.println("runtime-core-phase1-test: ok");
    }

    private static void testCanonicalIdentityIsLocaleIndependent() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            String usPlan = defaultPlan().runtimePlanHash();
            String usBudget = MaterializationBudget.defaults().identity();
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals(usPlan, defaultPlan().runtimePlanHash(), "plan identity must ignore default locale");
            assertEquals(usBudget, MaterializationBudget.defaults().identity(),
                    "budget identity must ignore default locale");
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static void testPlanReplacementChangesIdentity() {
        RuntimePlan original = defaultPlan();
        TablePlan replacement = original.requireTable("Order")
                .toBuilder().initialCapacity(64).build();
        RuntimePlan changed = original.toBuilder().replaceTable(replacement).build();
        assertFalse(original.runtimePlanHash().equals(changed.runtimePlanHash()),
                "effective plan changes must change runtimePlanHash");
        assertEquals(64, changed.requireTable("Order").initialCapacity(), "replacement must be effective");
    }

    private static void testCompatibilityBoundary() {
        RuntimePlan plan = defaultPlan();
        TablePlan table = RuntimeCompatibility.verify(metadata("schema-v1"), plan, "Order");
        assertEquals("Order", table.tableLogicalName(), "verified table identity");
        RuntimeCompatibility.verifyAccess(table, false);

        RuntimePlan mismatch = RuntimePlan.builder(
                "schema-v2",
                RuntimeCompatibility.RUNTIME_COMPATIBILITY,
                RuntimeCompatibility.GENERATED_PROTOCOL,
                RuntimeCompatibility.PLAN_PROTOCOL,
                RuntimeCompatibility.ALLOCATION_ESTIMATOR)
                .addTable(defaultTablePlan())
                .build();
        expectCode("schema_hash_mismatch", new ThrowingRunnable() {
            @Override
            public void run() {
                RuntimeCompatibility.verify(metadata("schema-v1"), mismatch, "Order");
            }
        });
    }

    private static void testDenseColumnsLifecycleAndStats() {
        IntColumn quantity = new IntColumn();
        LongColumn timestamp = new LongColumn();
        PresenceBitmap optional = new PresenceBitmap();
        ColumnGroup columns = new ColumnGroup(2, quantity, timestamp, optional);
        RuntimePlan plan = defaultPlan();
        DenseTableState state = new DenseTableState(
                "Order", plan, plan.requireTable("Order"), columns);

        int start = state.prepareAppend(3);
        assertEquals(0, start, "first append start");
        quantity.set(0, 7);
        quantity.set(1, 9);
        timestamp.set(0, 11L);
        optional.setPresent(1);
        state.commitAppend(start, 3);
        assertEquals(3, state.size(), "append size");
        assertTrue(state.capacity() >= 3, "append must grow all columns");
        assertEquals(7, quantity.get(0), "int column value");
        assertEquals(11L, timestamp.get(0), "long column value");
        assertTrue(optional.isPresent(1), "optional presence");

        expectCode("invalid_row_index", new ThrowingRunnable() {
            @Override
            public void run() {
                state.checkRowIndex(3, "get");
            }
        });

        state.beginOperation("count");
        expectCode("reentrant_access", new ThrowingRunnable() {
            @Override
            public void run() {
                state.beginOperation("update");
            }
        });
        state.endOperationSuccess("count", 3, 2, 0);
        TableStats counted = state.statsSnapshot();
        assertEquals("count", counted.lastOperation(), "last operation");
        assertEquals(3L, counted.lastScanned(), "scanned count");
        assertEquals(2L, counted.lastMatched(), "matched count");

        int previous = state.prepareClear();
        quantity.clearRange(0, previous);
        timestamp.clearRange(0, previous);
        optional.clearRange(0, previous);
        state.commitClear(previous);
        assertEquals(0, state.size(), "clear size");
        assertEquals(0, optional.presentCount(), "clear presence");

        int releaseSize = state.prepareRelease();
        state.commitRelease(releaseSize);
        state.commitRelease(state.prepareRelease());
        assertTrue(state.isReleased(), "release state");
        expectCode("table_released", new ThrowingRunnable() {
            @Override
            public void run() {
                state.checkActive("get");
            }
        });
    }

    private static void testPresenceBitmapAgainstOracle() {
        final int capacity = 257;
        PresenceBitmap bitmap = new PresenceBitmap();
        bitmap.commitCapacity(bitmap.stageCapacity(capacity));
        boolean[] oracle = new boolean[320];
        Random random = new Random(0x534f4d41L);
        for (int iteration = 0; iteration < 10000; iteration++) {
            int action = random.nextInt(3);
            if (action == 0) {
                int index = random.nextInt(capacity);
                bitmap.setPresent(index);
                oracle[index] = true;
            } else if (action == 1) {
                int index = random.nextInt(capacity);
                bitmap.clearPresent(index);
                oracle[index] = false;
            } else {
                int length = random.nextInt(65);
                int source = random.nextInt(capacity - length + 1);
                int target = random.nextInt(capacity - length + 1);
                boolean[] staged = new boolean[length];
                System.arraycopy(oracle, source, staged, 0, length);
                System.arraycopy(staged, 0, oracle, target, length);
                bitmap.copyFrom(bitmap, source, target, length);
            }
            int expectedCount = 0;
            for (int i = 0; i < capacity; i++) {
                assertEquals(oracle[i], bitmap.isPresent(i), "presence oracle row " + i);
                if (oracle[i]) {
                    expectedCount++;
                }
            }
            assertEquals(expectedCount, bitmap.presentCount(), "presence count");
        }
    }

    private static void testSidecarProtocolAndStats() {
        RowPermutationSidecar sidecar = new RowPermutationSidecar();
        assertTrue(sidecar.isDirty(), "new sidecar dirty");
        int[] staged = sidecar.stage(3);
        staged[0] = 2;
        staged[1] = 0;
        staged[2] = 1;
        sidecar.commit(staged, 3);
        assertFalse(sidecar.isDirty(), "committed sidecar current");
        assertEquals(2, sidecar.rowAt(0), "sidecar permutation");
        sidecar.markDirty();
        assertTrue(sidecar.isDirty(), "sidecar dirty transition");
        assertTrue(staged == sidecar.stage(2), "dirty rebuild reuses permutation high-water");
        int[] scratch = sidecar.scratch(3);
        assertTrue(scratch == sidecar.scratch(2), "sidecar sort scratch high-water reuse");
        assertEquals(24L, sidecar.retainedBytes(), "sidecar retained primitive bytes");
        assertEquals(24L, sidecar.rebuildPeakBytes(3), "same-size rebuild peak");
        sidecar.clear();
        assertEquals(0, sidecar.size(), "sidecar clear");
        sidecar.markDirty();
        assertTrue(staged == sidecar.stage(3), "clear retains sidecar capacity");
        sidecar.release();
        assertTrue(staged != sidecar.stage(3), "release drops retained permutation");
        assertTrue(scratch != sidecar.scratch(3), "release drops retained scratch");

        IntColumn value = new IntColumn();
        ColumnGroup columns = new ColumnGroup(2, value);
        RuntimePlan plan = accessPlan();
        DenseTableState state = new DenseTableState(
                "Order", plan, plan.requireTable("Order"), columns);
        RuntimeCompatibility.verifyAccess(plan.requireTable("Order"), true);
        state.sidecarScratch(24L, 48L);
        state.sidecarsDirtied(2L);
        state.sidecarRebuilt(7L);
        TableStats stats = state.statsSnapshot();
        assertEquals(2L, stats.sidecarDirtyCount(), "sidecar dirty stats");
        assertEquals(1L, stats.sidecarRebuildCount(), "sidecar rebuild stats");
        assertEquals(7L, stats.sidecarRebuildRows(), "sidecar rebuild row stats");
        assertEquals(24L, stats.sidecarScratchCurrentBytes(),
                "sidecar scratch current stats");
        assertEquals(48L, stats.sidecarScratchHighWaterBytes(),
                "sidecar scratch high-water stats");
        state.resetStats();
        assertEquals(0L, state.statsSnapshot().sidecarRebuildCount(),
                "sidecar stats reset");
    }

    private static void testStructuralRemoveStateTransition() {
        IntColumn value = new IntColumn();
        ColumnGroup columns = new ColumnGroup(4, value);
        RuntimePlan plan = defaultPlan();
        DenseTableState state = new DenseTableState(
                "Order", plan, plan.requireTable("Order"), columns);
        int start = state.prepareAppend(4);
        state.commitAppend(start, 4);
        long epoch = state.structuralEpoch();
        state.beginOperation("rows.remove");
        state.commitStructuralRemove(4, 2, "rows.remove");
        state.endOperationSuccess("rows.remove", 4L, 2L, 2L);
        assertEquals(2, state.size(), "remove size");
        assertEquals(epoch + 1L, state.structuralEpoch(), "remove structural epoch");
        RemoveResult result = state.removeResult(4L, 2L, 2L, 1L, 0L, 0L);
        assertEquals(1L, result.compacted(), "remove compaction count");
        try {
            RemoveResult.create(1L, 1L, 0L, 0L, 0L, 0L);
            throw new AssertionError("remove result must require removed == matched");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void testViewLifecycleState() {
        IntColumn value = new IntColumn();
        ColumnGroup columns = new ColumnGroup(2, value);
        RuntimePlan plan = defaultPlan();
        final DenseTableState state = new DenseTableState(
                "Order", plan, plan.requireTable("Order"), columns);
        int start = state.prepareAppend(1);
        state.commitAppend(start, 1);
        final long capturedEpoch = state.acquireView("value.column");
        assertEquals(1, state.statsSnapshot().activeViews(), "acquired view count");
        expectCode("view_pinned", new ThrowingRunnable() {
            @Override
            public void run() {
                state.prepareAppend(1);
            }
        });
        state.releaseView();
        start = state.prepareAppend(1);
        state.commitAppend(start, 1);
        expectCode("stale_view", new ThrowingRunnable() {
            @Override
            public void run() {
                state.checkView(capturedEpoch, 0, "value.column.get");
            }
        });
        state.acquireView("value.column");
        int releasedSize = state.prepareRelease();
        state.commitRelease(releasedSize);
        assertEquals(0, state.statsSnapshot().activeViews(), "release invalidates view count");
        expectCode("table_released", new ThrowingRunnable() {
            @Override
            public void run() {
                state.checkView(state.structuralEpoch(), 0, "value.column.get");
            }
        });
    }

    private static void testMaterializationBudget() {
        MaterializationBudget budget = MaterializationBudget.builder()
                .maximumRows(2)
                .maximumTableInstances(1)
                .maximumLeafValues(4)
                .maximumEstimatedAllocationBytes(32)
                .build();
        MaterializationTracker tracker = new MaterializationTracker(budget, "root");
        tracker.checkOwnershipDepth(0);
        tracker.addTableInstances(1);
        tracker.addRows(2);
        tracker.addLeafValues(4);
        tracker.addEstimatedBytes(32);
        assertEquals(budget.identity(), tracker.budgetIdentity(), "budget identity");
        expectCode("materialization_budget_exceeded", new ThrowingRunnable() {
            @Override
            public void run() {
                tracker.addRows(1);
            }
        });
        final MaterializationTracker zeroDepth = new MaterializationTracker(
                budget.toBuilder().maximumOwnershipDepth(0).build(), "root.child");
        zeroDepth.checkOwnershipDepth(0);
        expectCode("materialization_budget_exceeded", new ThrowingRunnable() {
            @Override
            public void run() {
                zeroDepth.checkOwnershipDepth(1);
            }
        });
    }

    private static void testBoundedFailureEnvelope() {
        TreeMap<String, String> context = new TreeMap<String, String>();
        StringBuilder oversized = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            oversized.append('x');
        }
        context.put("detail", oversized.toString());
        SomaRuntimeException failure = SomaRuntimeException.create(
                SomaErrorCategory.INVALID_INPUT, "test_failure", "test", oversized.toString(),
                context, null);
        assertTrue(failure.path().length() <= 256, "path must be bounded");
        assertTrue(failure.context().get("detail").length() <= 256, "context value must be bounded");
        try {
            failure.context().put("mutable", "no");
            throw new AssertionError("context must be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static RuntimePlan defaultPlan() {
        return RuntimePlan.builder(
                "schema-v1",
                RuntimeCompatibility.RUNTIME_COMPATIBILITY,
                RuntimeCompatibility.GENERATED_PROTOCOL,
                RuntimeCompatibility.PLAN_PROTOCOL,
                RuntimeCompatibility.ALLOCATION_ESTIMATOR)
                .addTable(defaultTablePlan())
                .build();
    }

    private static TablePlan defaultTablePlan() {
        return TablePlan.builder("Order", RuntimeCompatibility.DENSE_ALGORITHM).build();
    }

    private static RuntimePlan accessPlan() {
        return RuntimePlan.builder(
                "schema-v1",
                RuntimeCompatibility.RUNTIME_COMPATIBILITY,
                RuntimeCompatibility.GENERATED_PROTOCOL,
                RuntimeCompatibility.PLAN_PROTOCOL,
                RuntimeCompatibility.ALLOCATION_ESTIMATOR)
                .addTable(TablePlan.builder("Order", RuntimeCompatibility.DENSE_ALGORITHM)
                        .accessStrategy(RuntimeCompatibility.PRIMITIVE_SORTED_PERMUTATION)
                        .sidecarMaintenancePolicy(RuntimeCompatibility.DIRTY_LAZY_REBUILD)
                        .maximumSidecarScratchBytes(1024L)
                        .build())
                .build();
    }

    private static GeneratedMetadata metadata(String schemaHash) {
        return new GeneratedMetadata(
                schemaHash,
                RuntimeCompatibility.GENERATED_TARGET,
                RuntimeCompatibility.COMPILER_IDENTITY,
                RuntimeCompatibility.GENERATED_PROTOCOL,
                RuntimeCompatibility.RUNTIME_COMPATIBILITY,
                RuntimeCompatibility.PLAN_PROTOCOL,
                RuntimeCompatibility.DENSE_ALGORITHM,
                RuntimeCompatibility.ALLOCATION_ESTIMATOR);
    }

    private static void expectCode(String code, ThrowingRunnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("expected SomaRuntimeException code=" + code);
        } catch (SomaRuntimeException failure) {
            assertEquals(code, failure.code(), "failure code");
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private interface ThrowingRunnable {
        void run();
    }
}
