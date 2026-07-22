package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.ColumnGroup;
import com.hgtech.soma.runtime.generated.ChildOwnershipRegistry;
import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.GeneratedMetadata;
import com.hgtech.soma.runtime.generated.GeneratedColumn;
import com.hgtech.soma.runtime.generated.GeneratedScanPlan;
import com.hgtech.soma.runtime.generated.GroupedExactIndex;
import com.hgtech.soma.runtime.generated.HashCompositeKeySpace;
import com.hgtech.soma.runtime.generated.IndexBuffer;
import com.hgtech.soma.runtime.generated.IntColumn;
import com.hgtech.soma.runtime.generated.LongColumn;
import com.hgtech.soma.runtime.generated.MaterializationTracker;
import com.hgtech.soma.runtime.generated.PresenceBitmap;
import com.hgtech.soma.runtime.generated.RuntimeCompatibility;
import com.hgtech.soma.runtime.generated.OwnedChildTable;

import java.util.Locale;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.IntConsumer;

/** Phase 1 runtime-core correctness evidence，使用完整 JDK 8 直接执行。 */
public final class RuntimeCorePhase1Check {
    private RuntimeCorePhase1Check() {
    }

    public static void main(String[] args) {
        testCanonicalIdentityIsLocaleIndependent();
        testPlanReplacementChangesIdentity();
        testResourcePlanCanonicalIdentity();
        testUnicodeCodePointOrderAndLosslessCanonicalText();
        testChildPlanIdentity();
        testOwnershipRegistryInitialStorageBoundary();
        testOwnershipStageGrowthIsAtomicAndRetryable();
        testChildOwnershipRegistry();
        testRetryableCascadeAndRegistryRelease();
        testCompatibilityBoundary();
        testGeneratedScanPlan();
        testDenseColumnsLifecycleAndStats();
        testStructuralRemoveStateTransition();
        testViewLifecycleState();
        testPresenceBitmapAgainstOracle();
        testOptionalColumnTraversalPresenceLanes();
        testMaterializationBudget();
        testExactIndexProtocolAndStats();
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

    private static void testResourcePlanCanonicalIdentity() {
        TablePlan table = TablePlan.builder("Order", RuntimeCompatibility.DENSE_ALGORITHM)
                .initialCapacity(4)
                .growthRatio(5, 4)
                .maximumUpdateScratchBytes(11L)
                .maximumOperationScratchBytes(12L)
                .maximumBulkScratchBytes(13L)
                .maximumTableStorageBytes(14L)
                .keySpaceStrategy(RuntimeCompatibility.HASH_INT_KEY_SPACE)
                .build();
        assertEquals("{\"algorithm\":\"dense-soa-v1\",\"accessStrategy\":\"none\","
                        + "\"growthDenominator\":4,\"growthNumerator\":5,"
                        + "\"initialCapacity\":4,\"keySpaceStrategy\":\"hash-int-v2\","
                        + "\"maximumBulkScratchBytes\":13,"
                        + "\"maximumOperationScratchBytes\":12,"
                        + "\"maximumTableStorageBytes\":14,"
                        + "\"maximumUpdateScratchBytes\":11,"
                        + "\"table\":\"Order\"}",
                table.toCanonicalJson(), "table resource plan canonical order");
        RuntimePlan plan = RuntimePlan.builder(
                        "schema-v1",
                        RuntimeCompatibility.RUNTIME_COMPATIBILITY,
                        RuntimeCompatibility.GENERATED_PROTOCOL,
                        RuntimeCompatibility.PLAN_PROTOCOL,
                        RuntimeCompatibility.ALLOCATION_ESTIMATOR)
                .maximumAggregateStorageBytes(16L)
                .maximumOwnershipTableInstances(17L)
                .addTable(table)
                .build();
        assertEquals("{\"allocationEstimator\":\"soma-materialization-estimator-v1\","
                        + "\"defaultMaterializationBudget\":{"
                        + "\"maximumEstimatedAllocationBytes\":268435456,"
                        + "\"maximumLeafValues\":50000000,"
                        + "\"maximumOwnershipDepth\":32,\"maximumRows\":1000000,"
                        + "\"maximumTableInstances\":100000},"
                        + "\"generatedProtocol\":\"soma-generated-runtime-v4\","
                        + "\"maximumAggregateStorageBytes\":16,"
                        + "\"maximumOwnershipTableInstances\":17,"
                        + "\"planProtocol\":\"soma-runtime-plan-v3\","
                        + "\"runtimeCompatibility\":\"soma-runtime-java8-v4\","
                        + "\"schemaHash\":\"schema-v1\",\"statsMode\":\"summary\","
                        + "\"tables\":[" + table.toCanonicalJson() + "]}",
                plan.toCanonicalJson(), "runtime resource plan canonical order");
    }

    private static void testChildPlanIdentity() {
        final ChildPlan lines = ChildPlan.create("Order", "lines", "Line", 4);
        RuntimePlan base = RuntimePlan.builder(
                "schema-v1",
                RuntimeCompatibility.RUNTIME_COMPATIBILITY,
                RuntimeCompatibility.GENERATED_PROTOCOL,
                RuntimeCompatibility.PLAN_PROTOCOL,
                RuntimeCompatibility.ALLOCATION_ESTIMATOR)
                .addTable(defaultTablePlan())
                .addTable(TablePlan.builder("Line", RuntimeCompatibility.DENSE_ALGORITHM)
                        .initialCapacity(8).growthRatio(3, 2)
                        .maximumUpdateScratchBytes(1024L).build())
                .addChild(lines)
                .build();
        assertEquals(4, base.requireChild("Order", "lines").initialCapacity(),
                "child plan capacity");
        assertEquals("{\"childField\":\"lines\",\"childTable\":\"Line\","
                        + "\"initialCapacity\":4,\"ownerTable\":\"Order\"}",
                lines.toCanonicalJson(), "child plan canonical key order");
        assertFalse(ChildPlan.identity("a", "b\u0000c")
                        .equals(ChildPlan.identity("a\u0000b", "c")),
                "child-plan composite identity must be collision-free");
        RuntimePlan changed = base.toBuilder().replaceChild(
                ChildPlan.create("Order", "lines", "Line", 6)).build();
        assertFalse(base.runtimePlanHash().equals(changed.runtimePlanHash()),
                "child plan change must change runtimePlanHash");
        assertEquals(1, changed.children().size(), "child plan list");

        expectCode("invalid_runtime_plan", new ThrowingRunnable() {
            @Override public void run() {
                base.toBuilder().addChild(lines);
            }
        });
        expectCode("invalid_runtime_plan", new ThrowingRunnable() {
            @Override public void run() {
                RuntimePlan.builder(
                        "schema-v1",
                        RuntimeCompatibility.RUNTIME_COMPATIBILITY,
                        RuntimeCompatibility.GENERATED_PROTOCOL,
                        RuntimeCompatibility.PLAN_PROTOCOL,
                        RuntimeCompatibility.ALLOCATION_ESTIMATOR)
                        .addTable(defaultTablePlan())
                        .addChild(ChildPlan.create("Order", "lines", "Missing", 4))
                        .build();
            }
        });
        expectCode("invalid_runtime_plan", new ThrowingRunnable() {
            @Override public void run() {
                base.requireChild("Order", "missing");
            }
        });
        try {
            ChildPlan.create("", "lines", "Line", 4);
            throw new AssertionError("empty child-plan owner must fail");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            ChildPlan.create("Order", "lines", "Line", 0);
            throw new AssertionError("non-positive child capacity must fail");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            base.children().add(lines);
            throw new AssertionError("child plan list must be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static void testUnicodeCodePointOrderAndLosslessCanonicalText() {
        String bmp = "\uF900";
        String supplementary = "\uD801\uDC00";
        RuntimePlan ordered = RuntimePlan.builder(
                        "unicode-order",
                        RuntimeCompatibility.RUNTIME_COMPATIBILITY,
                        RuntimeCompatibility.GENERATED_PROTOCOL,
                        RuntimeCompatibility.PLAN_PROTOCOL,
                        RuntimeCompatibility.ALLOCATION_ESTIMATOR)
                .addTable(TablePlan.builder(supplementary,
                                RuntimeCompatibility.DENSE_ALGORITHM).build())
                .addTable(TablePlan.builder(bmp,
                                RuntimeCompatibility.DENSE_ALGORITHM).build())
                .build();
        String canonical = ordered.toCanonicalJson();
        String supplementaryTable = "\"table\":\"" + '\\' + "uD801" + '\\'
                + "uDC00\"";
        assertTrue(canonical.indexOf("\"table\":\"" + bmp + "\"")
                        < canonical.indexOf(supplementaryTable),
                "runtime plan must order identities by Unicode code point");
        assertEquals(ordered.runtimePlanHash(), ordered.toBuilder().build().runtimePlanHash(),
                "supplementary runtime plan hash repeatability");

        RuntimePlan isolatedHigh = unicodeNamedPlan("\uD800");
        RuntimePlan isolatedOther = unicodeNamedPlan("\uD801");
        assertTrue(isolatedHigh.toCanonicalJson().contains(String.valueOf('\\') + "uD800"),
                "isolated high surrogate must be escaped losslessly");
        assertTrue(isolatedOther.toCanonicalJson().contains(String.valueOf('\\') + "uD801"),
                "distinct isolated surrogate must be escaped losslessly");
        assertFalse(isolatedHigh.toCanonicalJson().equals(isolatedOther.toCanonicalJson()),
                "isolated surrogate canonical text must not collide");
        assertFalse(isolatedHigh.runtimePlanHash().equals(isolatedOther.runtimePlanHash()),
                "isolated surrogate runtime plan hashes must not collide");
    }

    private static RuntimePlan unicodeNamedPlan(String tableName) {
        return RuntimePlan.builder(
                        "unicode-lossless",
                        RuntimeCompatibility.RUNTIME_COMPATIBILITY,
                        RuntimeCompatibility.GENERATED_PROTOCOL,
                        RuntimeCompatibility.PLAN_PROTOCOL,
                        RuntimeCompatibility.ALLOCATION_ESTIMATOR)
                .addTable(TablePlan.builder(tableName,
                                RuntimeCompatibility.DENSE_ALGORITHM).build())
                .build();
    }

    private static void testChildOwnershipRegistry() {
        ChildOwnershipRegistry registry = new ChildOwnershipRegistry();
        long owner = registry.newOwnerToken();
        final boolean[] released = new boolean[1];
        OwnedChildTable lifecycle = new OwnedChildTable() {
            @Override public boolean hasPinnedSubtree() { return false; }
            @Override public void preflightOwnedRelease(boolean aggregateRelease) { }
            @Override public void releaseOwnedSubtree(boolean aggregateRelease) {
                released[0] = true;
            }
            @Override public long subtreeChildInstanceCount() { return 0L; }
            @Override public long subtreeDescendantRowCount() { return 2L; }
        };
        Object child = new Object();
        long handle = registry.stage(owner, "lines", "Order.lines", child, lifecycle);
        registry.publish(handle, owner, "lines");
        assertTrue(registry.resolve(handle, owner, "lines", "test") == child,
                "child registry resolve");
        assertEquals(2L, registry.descendantRowCount(handle, owner, "lines"),
                "child registry rows");
        expectCode("child_wrong_owner", new ThrowingRunnable() {
            @Override public void run() {
                registry.resolve(handle, owner + 1L, "lines", "test");
            }
        });
        expectCode("child_wrong_owner", new ThrowingRunnable() {
            @Override public void run() {
                registry.resolve(handle, owner, "otherLines", "test");
            }
        });
        expectCode("ownership_cycle", new ThrowingRunnable() {
            @Override public void run() {
                registry.stage(registry.newOwnerToken(), "otherLines",
                        "Other.lines", child, lifecycle);
            }
        });

        registry.beginMaterialization("materialize");
        expectCode("reentrant_access", new ThrowingRunnable() {
            @Override public void run() {
                registry.preflightMutation("child.replace");
            }
        });
        expectCode("reentrant_access", new ThrowingRunnable() {
            @Override public void run() {
                registry.beginMaterialization("materialize.nested");
            }
        });
        registry.endMaterialization();
        registry.preflightMutation("child.replace");
        expectCode("internal_invariant_violation", new ThrowingRunnable() {
            @Override public void run() {
                registry.endMaterialization();
            }
        });

        registry.release(handle, owner, "lines", "test", false);
        assertTrue(released[0], "child release callback");
        expectCode("child_released", new ThrowingRunnable() {
            @Override public void run() {
                registry.resolve(handle, owner, "lines", "test");
            }
        });
        long replacement = registry.stage(owner, "lines", "Order.lines", child, lifecycle);
        registry.publish(replacement, owner, "lines");
        assertFalse(handle == replacement, "reused child slot must advance generation");
        expectCode("child_dangling", new ThrowingRunnable() {
            @Override public void run() {
                registry.resolve(handle, owner, "lines", "test");
            }
        });

        final int entryCount = 48;
        long[] owners = new long[entryCount];
        long[] handles = new long[entryCount];
        Object[] children = new Object[entryCount];
        for (int i = 0; i < entryCount; i++) {
            owners[i] = registry.newOwnerToken();
            children[i] = new Object();
            handles[i] = registry.stage(owners[i], "items", "Root.items",
                    children[i], lifecycle);
            registry.publish(handles[i], owners[i], "items");
        }
        for (int i = 0; i < entryCount; i++) {
            assertTrue(registry.resolve(handles[i], owners[i], "items", "test")
                            == children[i],
                    "registry growth preserves entry " + i);
        }
        for (int i = 0; i < entryCount; i++) {
            registry.release(handles[i], owners[i], "items", "test", true);
        }
        registry.release(replacement, owner, "lines", "test", true);
        registry.releaseStorage();
    }

    private static void testOwnershipRegistryInitialStorageBoundary() {
        final long exactInitialBytes = 53L * 16L + 12L * 32L + 16L * 256L;
        ChildOwnershipRegistry exact = new ChildOwnershipRegistry(exactInitialBytes, 1L);
        exact.releaseStorage();
        try {
            new ChildOwnershipRegistry(exactInitialBytes - 1L, 1L);
            throw new AssertionError("ownership initial limit-1 must fail");
        } catch (SomaRuntimeException failure) {
            assertEquals("memory_limit_exceeded", failure.code(),
                    "ownership initial storage code");
            assertEquals(Long.toString(exactInitialBytes - 1L),
                    failure.context().get("limit"), "ownership initial storage limit");
            assertEquals(Long.toString(exactInitialBytes),
                    failure.context().get("proposed"), "ownership initial storage proposed");
        }
    }

    private static void testOwnershipStageGrowthIsAtomicAndRetryable() {
        final long maximumPeakBeforeFirstSlotGrowth = 6599L;
        ChildOwnershipRegistry registry = new ChildOwnershipRegistry(
                maximumPeakBeforeFirstSlotGrowth, 100L);
        OwnedChildTable lifecycle = new OwnedChildTable() {
            @Override public boolean hasPinnedSubtree() { return false; }
            @Override public void preflightOwnedRelease(boolean aggregateRelease) { }
            @Override public void releaseOwnedSubtree(boolean aggregateRelease) { }
            @Override public long subtreeChildInstanceCount() { return 0L; }
            @Override public long subtreeDescendantRowCount() { return 0L; }
        };
        long[] owners = new long[15];
        long[] handles = new long[15];
        for (int index = 0; index < handles.length; index++) {
            owners[index] = registry.newOwnerToken();
            handles[index] = registry.stage(owners[index], "items", "Root.items",
                    new Object(), lifecycle);
            registry.publish(handles[index], owners[index], "items");
        }
        final long failedOwner = registry.newOwnerToken();
        final Object failedChild = new Object();
        try {
            registry.stage(failedOwner, "items", "Root.items", failedChild, lifecycle);
            throw new AssertionError("slot growth peak limit must fail");
        } catch (SomaRuntimeException failure) {
            assertEquals("memory_limit_exceeded", failure.code(),
                    "slot growth memory code");
            assertEquals(Long.toString(maximumPeakBeforeFirstSlotGrowth),
                    failure.context().get("limit"), "slot growth limit");
            assertEquals("6600", failure.context().get("proposed"),
                    "slot growth combined retained/transient peak");
        }
        registry.release(handles[0], owners[0], "items", "test", false);
        long retried = registry.stage(failedOwner, "items", "Root.items",
                failedChild, lifecycle);
        registry.publish(retried, failedOwner, "items");
        assertTrue(registry.resolve(retried, failedOwner, "items", "test") == failedChild,
                "failed stage must not retain identity and retry must reuse a free slot");
        registry.release(retried, failedOwner, "items", "test", false);
        for (int index = 1; index < handles.length; index++) {
            registry.release(handles[index], owners[index], "items", "test", false);
        }
        registry.releaseStorage();
    }

    private static void testRetryableCascadeAndRegistryRelease() {
        final ChildOwnershipRegistry registry = new ChildOwnershipRegistry(8192L, 8L);
        final int[] firstCalls = {0};
        final int[] secondCalls = {0};
        final boolean[] rejectSecondOnce = {true};
        OwnedChildTable firstLifecycle = lifecycle(firstCalls, null);
        OwnedChildTable secondLifecycle = lifecycle(secondCalls, rejectSecondOnce);
        long firstOwner = registry.newOwnerToken();
        long secondOwner = registry.newOwnerToken();
        Object first = new Object();
        Object second = new Object();
        long firstHandle = registry.stage(
                firstOwner, "items", "Root.items", first, firstLifecycle);
        long secondHandle = registry.stage(
                secondOwner, "items", "Root.items", second, secondLifecycle);
        registry.publish(firstHandle, firstOwner, "items");
        registry.publish(secondHandle, secondOwner, "items");

        registry.beginCascade(2L, 16L, "Root", "clear");
        registry.collectCascade(firstHandle, firstOwner, "items", true, "clear");
        registry.collectCascade(secondHandle, secondOwner, "items", true, "clear");
        try {
            registry.commitCascade(false, "clear");
            throw new AssertionError("controlled cascade release failure must propagate");
        } catch (IllegalStateException expected) {
            // The recursive preflight must reject before either release callback runs.
        }
        assertTrue(registry.resolve(firstHandle, firstOwner, "items", "test") == first,
                "failed cascade keeps first slot live");
        assertTrue(registry.resolve(secondHandle, secondOwner, "items", "test") == second,
                "failed cascade keeps second slot live");

        rejectSecondOnce[0] = false;
        registry.beginCascade(2L, 16L, "Root", "clear");
        registry.collectCascade(firstHandle, firstOwner, "items", true, "clear");
        registry.collectCascade(secondHandle, secondOwner, "items", true, "clear");
        registry.commitCascade(false, "clear");
        assertEquals(1, firstCalls[0], "preflight failure prevents partial first release");
        assertEquals(1, secondCalls[0], "retry releases second descendant once");
        expectCode("child_released", new ThrowingRunnable() {
            @Override public void run() {
                registry.resolve(firstHandle, firstOwner, "items", "test");
            }
        });
        registry.releaseStorage();
    }

    private static OwnedChildTable lifecycle(
            final int[] calls, final boolean[] rejectOnce) {
        return new OwnedChildTable() {
            @Override public boolean hasPinnedSubtree() { return false; }
            @Override public void preflightOwnedRelease(boolean aggregateRelease) {
                if (rejectOnce != null && rejectOnce[0]) {
                    throw new IllegalStateException("controlled release failure");
                }
            }
            @Override public void releaseOwnedSubtree(boolean aggregateRelease) {
                calls[0]++;
            }
            @Override public long subtreeChildInstanceCount() { return 0L; }
            @Override public long subtreeDescendantRowCount() { return 0L; }
        };
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
        final GeneratedMetadata legacyGenerated = new GeneratedMetadata(
                "schema-v1",
                RuntimeCompatibility.GENERATED_TARGET,
                RuntimeCompatibility.COMPILER_IDENTITY,
                "soma-generated-runtime-v1",
                "soma-runtime-java8-v1",
                "soma-runtime-plan-v1",
                RuntimeCompatibility.DENSE_ALGORITHM,
                RuntimeCompatibility.ALLOCATION_ESTIMATOR);
        expectCode("runtime_compatibility_mismatch", new ThrowingRunnable() {
            @Override public void run() {
                RuntimeCompatibility.verify(legacyGenerated, plan, "Order");
            }
        });
        final GeneratedMetadata legacyPlan = new GeneratedMetadata(
                "schema-v1",
                RuntimeCompatibility.GENERATED_TARGET,
                RuntimeCompatibility.COMPILER_IDENTITY,
                RuntimeCompatibility.GENERATED_PROTOCOL,
                RuntimeCompatibility.RUNTIME_COMPATIBILITY,
                "soma-runtime-plan-v1",
                RuntimeCompatibility.DENSE_ALGORITHM,
                RuntimeCompatibility.ALLOCATION_ESTIMATOR);
        expectCode("runtime_plan_mismatch", new ThrowingRunnable() {
            @Override public void run() {
                RuntimeCompatibility.verify(legacyPlan, plan, "Order");
            }
        });
    }

    private static void testGeneratedScanPlan() {
        ReferenceScanPlan plan = new ReferenceScanPlan("Order.byStatus");
        Object first = new Object();
        plan.reference = new Object();
        plan.append((byte) 1, first, 0L, 1);
        plan.append((byte) 2, null, 7L, 2);
        plan.append((byte) 3, null, 9L, 3);
        assertTrue(plan.isCurrent(3), "inline generation is current");
        assertTrue(plan.callback(0) == first, "inline callback identity");
        assertEquals(7L, plan.argument(1), "inline primitive argument");

        plan.append((byte) 4, first, 0L, 4);
        plan.append((byte) 1, first, 0L, 5);
        for (int generation = 6; generation <= 16; generation++) {
            plan.append((byte) 2, null, generation, generation);
        }
        assertEquals(16, plan.stageCount(), "overflow growth preserves stage count");
        assertEquals(16L, plan.argument(15), "overflow growth preserves arguments");
        plan.argument(15, 3L);
        assertEquals(3L, plan.argument(15), "consumed argument is mutable in place");
        try {
            plan.append((byte) 1, first, 0L, 18);
            throw new AssertionError("generation gap must fail");
        } catch (IllegalStateException expected) {
            assertEquals(16, plan.stageCount(), "failed append is not published");
        }

        plan.consume();
        assertFalse(plan.isCurrent(16), "consumed plan is not current");
        plan.clear();
        assertTrue(plan.reference == null, "typed source reference is cleared");
        assertEquals("Order.byStatus", plan.sourcePath(), "prebound source path");
    }

    private static void testDenseColumnsLifecycleAndStats() {
        IntColumn quantity = new IntColumn();
        LongColumn timestamp = new LongColumn();
        PresenceBitmap optional = new PresenceBitmap();
        ColumnGroup columns = newColumnGroup(2, quantity, timestamp, optional);
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
        assertEquals(bitmap.isPresent(64),
                (bitmap.wordAt(1) & 1L) != 0L,
                "word access matches row access");
        try {
            bitmap.wordAt(-1);
            throw new AssertionError("negative presence word index must fail");
        } catch (IndexOutOfBoundsException expected) {
            // expected
        }
        try {
            bitmap.wordAt(5);
            throw new AssertionError("presence word index beyond capacity must fail");
        } catch (IndexOutOfBoundsException expected) {
            // expected
        }
    }

    private static void testOptionalColumnTraversalPresenceLanes() {
        final int size = 66;
        IntColumn values = new IntColumn();
        PresenceBitmap presence = new PresenceBitmap();
        ColumnGroup columns = newColumnGroup(2, values, presence);
        RuntimePlan plan = defaultPlan();
        DenseTableState state = new DenseTableState(
                "Order", plan, plan.requireTable("Order"), columns);
        int start = state.prepareAppend(size);
        for (int row = 0; row < size; row++) {
            values.set(row, row);
        }
        state.commitAppend(start, size);

        IntColumnTraversal optional = new IntColumnTraversal(
                state, values, presence, "Order",
                "quantity.values", "quantity.values.consumer");
        final long[] sum = {0L};
        IntConsumer accumulator = new IntConsumer() {
            @Override public void accept(int value) { sum[0] += value; }
        };

        optional.forEachInt(accumulator);
        assertEquals(0L, sum[0], "all-absent lane skips payloads");
        assertEquals(size, state.statsSnapshot().lastScanned(),
                "all-absent lane scanned semantics");
        assertEquals(0L, state.statsSnapshot().lastMatched(),
                "all-absent lane matched semantics");
        final IntColumnTraversal consumed = optional;
        expectCode("traversal_consumed", new ThrowingRunnable() {
            @Override public void run() { consumed.forEachInt(accumulator); }
        });

        int[] selected = {0, 63, 64, 65};
        for (int i = 0; i < selected.length; i++) {
            presence.setPresent(selected[i]);
        }
        sum[0] = 0L;
        optional = new IntColumnTraversal(
                state, values, presence, "Order",
                "quantity.values", "quantity.values.consumer");
        optional.forEachInt(accumulator);
        assertEquals(192L, sum[0], "mixed word lane visits 63/64 boundary rows");
        assertEquals(size, state.statsSnapshot().lastScanned(),
                "mixed word lane scanned semantics");
        assertEquals(selected.length, state.statsSnapshot().lastMatched(),
                "mixed word lane matched semantics");

        for (int row = 0; row < size; row++) {
            presence.setPresent(row);
        }
        sum[0] = 0L;
        optional = new IntColumnTraversal(
                state, values, presence, "Order",
                "quantity.values", "quantity.values.consumer");
        optional.forEachInt(accumulator);
        assertEquals(2145L, sum[0], "all-present lane visits every payload");
        assertEquals(size, state.statsSnapshot().lastMatched(),
                "all-present lane matched semantics");

        IntColumnTraversal required = new IntColumnTraversal(
                state, values, null, "Order",
                "quantity.values", "quantity.values.consumer");
        sum[0] = 0L;
        required.forEachInt(accumulator);
        assertEquals(2145L, sum[0], "required lane visits every payload");

        presence.clearRange(0, size);
        presence.setPresent(0);
        presence.setPresent(63);
        optional = new IntColumnTraversal(
                state, values, presence, "Order",
                "quantity.values", "quantity.values.consumer");
        final IntColumnTraversal failing = optional;
        expectCode("callback_failed", new ThrowingRunnable() {
            @Override public void run() {
                failing.forEachInt(new IntConsumer() {
                    private int calls;
                    @Override public void accept(int value) {
                        calls++;
                        if (calls == 2) {
                            throw new IllegalStateException("stop");
                        }
                    }
                });
            }
        });
        assertEquals(64L, state.statsSnapshot().lastScanned(),
                "mixed word callback failure scanned semantics");
        assertEquals(2L, state.statsSnapshot().lastMatched(),
                "mixed word callback failure matched semantics");
        expectCode("traversal_consumed", new ThrowingRunnable() {
            @Override public void run() { failing.forEachInt(accumulator); }
        });
    }

    private static void testExactIndexProtocolAndStats() {
        IndexBuffer buffer = new IndexBuffer();
        int[] first = buffer.prepare(3);
        first[0] = 2;
        first[1] = 0;
        first[2] = 1;
        buffer.reset();
        assertEquals(0, buffer.length(), "IndexBuffer reset logical length");
        assertTrue(first == buffer.prepare(2), "IndexBuffer reuses retained storage");
        buffer.release();
        assertTrue(first != buffer.prepare(3), "IndexBuffer release drops storage");

        GroupedExactIndex index = new GroupedExactIndex(0);
        index.ensureCapacity(4, 4);
        int firstGroup = index.createGroup(7L);
        index.link(firstGroup, 0);
        index.link(firstGroup, 1);
        int collisionGroup = index.createGroup(7L);
        index.link(collisionGroup, 2);
        assertEquals(collisionGroup, index.firstGroup(7L), "new same-hash group is bucket head");
        index.recordCollision();
        assertEquals(firstGroup, index.nextHashGroup(collisionGroup),
                "same-hash groups remain traversable for full equality");
        index.unlink(2);
        assertEquals(1, index.groupCount(), "empty exact group is reclaimed");
        index.relocate(1, 3);
        assertFalse(index.isLinked(1), "relocated source is unlinked");
        assertTrue(index.isLinked(3), "relocated destination is linked");
        assertEquals(2, index.entryCount(), "exact index keeps entry cardinality");

        IntColumn value = new IntColumn();
        ColumnGroup columns = newColumnGroup(2, value);
        RuntimePlan plan = accessPlan();
        DenseTableState state = new DenseTableState(
                "Order", plan, plan.requireTable("Order"), columns);
        RuntimeCompatibility.verifyAccess(plan.requireTable("Order"), true);
        TableStats stats = TableStats.withExactIndexes(
                state.statsSnapshot(), 1, index.entryCount(), index.groupCount(),
                index.probeCount(), index.collisionCount(), index.rehashCount(),
                index.retainedBytes(), index.storageHighWaterBytes());
        assertEquals(1, stats.exactIndexCount(), "exact index count stats");
        assertEquals(2L, stats.exactIndexEntryCount(), "exact index entry stats");
        assertEquals(1L, stats.exactIndexGroupCount(), "exact index group stats");
        assertTrue(stats.exactIndexProbeCount() > 0L, "exact index probe stats");
        assertTrue(stats.exactIndexCollisionCount() > 0L, "exact index collision stats");
        index.clear();
        assertEquals(0, index.entryCount(), "exact index clear");
        long retained = index.retainedBytes();
        index.release();
        assertTrue(retained > index.retainedBytes(), "exact index release drops storage");
    }

    private static void testStructuralRemoveStateTransition() {
        IntColumn value = new IntColumn();
        ColumnGroup columns = newColumnGroup(4, value);
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
        RemoveResult result = state.removeResult(4L, 2L, 2L, 1L);
        assertEquals(1L, result.compacted(), "remove compaction count");
        try {
            RemoveResult.create(1L, 1L, 0L, 0L);
            throw new AssertionError("remove result must require removed == matched");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void testViewLifecycleState() {
        IntColumn value = new IntColumn();
        ColumnGroup columns = newColumnGroup(2, value);
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
        final MaterializationTracker overflow = new MaterializationTracker(
                MaterializationBudget.builder()
                        .maximumOwnershipDepth(Integer.MAX_VALUE)
                        .maximumTableInstances(Long.MAX_VALUE)
                        .maximumRows(Long.MAX_VALUE)
                        .maximumLeafValues(Long.MAX_VALUE)
                        .maximumEstimatedAllocationBytes(Long.MAX_VALUE)
                        .build(), "root");
        overflow.addRows(1L);
        expectCode("materialization_budget_exceeded", new ThrowingRunnable() {
            @Override public void run() { overflow.addRows(Long.MAX_VALUE); }
        });
        Object root = new Object();
        Object child = new Object();
        tracker.enterOwnership(root, "root");
        tracker.enterOwnership(child, "root.child");
        expectCode("ownership_cycle", new ThrowingRunnable() {
            @Override public void run() {
                tracker.enterOwnership(root, "root.child.root");
            }
        });
        tracker.exitOwnership(child, "root.child");
        tracker.exitOwnership(root, "root");
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

    private static ColumnGroup newColumnGroup(
            int initialCapacity, GeneratedColumn... columns) {
        return new ColumnGroup(
                "Order", defaultTablePlan(),
                new ChildOwnershipRegistry(1024L * 1024L * 1024L, 65536L),
                initialCapacity, columns);
    }

    private static RuntimePlan accessPlan() {
        return RuntimePlan.builder(
                "schema-v1",
                RuntimeCompatibility.RUNTIME_COMPATIBILITY,
                RuntimeCompatibility.GENERATED_PROTOCOL,
                RuntimeCompatibility.PLAN_PROTOCOL,
                RuntimeCompatibility.ALLOCATION_ESTIMATOR)
                .addTable(TablePlan.builder("Order", RuntimeCompatibility.DENSE_ALGORITHM)
                        .accessStrategy(RuntimeCompatibility.PRIMITIVE_EXACT_HASH)
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

    private static final class ReferenceScanPlan extends GeneratedScanPlan {
        private Object reference;

        private ReferenceScanPlan(String sourcePath) {
            super(sourcePath);
        }

        @Override
        protected void clearSource() {
            reference = null;
        }
    }

    private interface ThrowingRunnable {
        void run();
    }
}
