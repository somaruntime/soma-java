package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.ColumnGroup;
import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.GeneratedMetadata;
import com.hgtech.soma.runtime.generated.IntColumn;
import com.hgtech.soma.runtime.generated.LongColumn;
import com.hgtech.soma.runtime.generated.MaterializationTracker;
import com.hgtech.soma.runtime.generated.PresenceBitmap;
import com.hgtech.soma.runtime.generated.RuntimeCompatibility;

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
        testPresenceBitmapAgainstOracle();
        testMaterializationBudget();
        testBoundedFailureEnvelope();
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

    private static void testMaterializationBudget() {
        MaterializationBudget budget = MaterializationBudget.builder()
                .maximumRows(2)
                .maximumTableInstances(1)
                .maximumLeafValues(4)
                .maximumEstimatedAllocationBytes(32)
                .build();
        MaterializationTracker tracker = new MaterializationTracker(budget, "root");
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
