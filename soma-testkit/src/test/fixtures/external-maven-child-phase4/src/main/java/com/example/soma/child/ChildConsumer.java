package com.example.soma.child;

import com.example.soma.child.generated.ChildRowBatch;
import com.example.soma.child.generated.ChildRowTable;
import com.example.soma.child.generated.ParentRowBatch;
import com.example.soma.child.generated.ParentRowTable;
import com.example.soma.child.generated.ParentRowScan;
import com.example.soma.child.generated.ChildRowScan;
import com.example.soma.child.generated.ChildRowCursor;
import com.example.soma.child.generated.KeyedChildRowBatch;
import com.example.soma.child.generated.KeyedChildRowTable;
import com.example.soma.child.generated.GrandchildRowTable;
import com.example.soma.child.generated.FloatingParentRowBatch;
import com.example.soma.child.generated.FloatingParentRowTable;
import com.example.soma.child.generated.SchemaMetadata;
import com.hgtech.soma.runtime.ChildPlan;
import com.hgtech.soma.runtime.SomaRuntimeException;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;
import com.hgtech.soma.runtime.MaterializationBudget;
import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.StringResourceProfile;
import com.hgtech.soma.runtime.TableStats;
import com.hgtech.soma.runtime.generated.GeneratedRuntimePlan;
import com.hgtech.soma.runtime.generated.MaterializationAllocation;
import com.hgtech.soma.runtime.generated.RuntimeCompatibility;
import com.hgtech.soma.runtime.metadata.SomaStorageLayout;
import com.hgtech.soma.runtime.metadata.SomaWorkloadProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChildConsumer {
    private ChildConsumer() {}

    public static void main(String[] args) {
        testSegmentedChildHandleRelocation();
        testSnapshotBoundaries();
        testFloatingChildKeys();
        testOwnershipInstanceQuotaAndRetry();
        testRecursiveReplacementPreflightAndRetry();
        final RuntimePlan defaultPlan = ParentRowTable.defaultRuntimePlan();
        RuntimePlan.Builder extraTableBuilder = defaultPlan.toBuilder();
        GeneratedRuntimePlan.addTable(
                extraTableBuilder,
                GeneratedRuntimePlan.table(
                        "unexpected",
                        defaultPlan.requireTable("parent_rows").algorithm(),
                        16, 16, Integer.MAX_VALUE, 3, 2,
                        268435456L, 268435456L,
                        268435456L, 268435456L,
                        "none",
                        RuntimeCompatibility.PRIMARY_LOCATOR_LAYOUT_FORMULA,
                        "none",
                        RuntimeCompatibility.STORAGE_LAYOUT_FORMULA, 1,
                        false,
                        StringResourceProfile.unprofiled()));
        final RuntimePlan extraTablePlan = extraTableBuilder.build();
        expectCode("invalid_runtime_plan", new Action() {
            public void run() { ParentRowTable.create(extraTablePlan); }
        });
        RuntimePlan.Builder extraChildBuilder = defaultPlan.toBuilder();
        GeneratedRuntimePlan.addChild(
                extraChildBuilder,
                ChildPlan.create(
                        "parent_rows", "unexpected", "child_rows", 1));
        final RuntimePlan extraChildPlan = extraChildBuilder.build();
        expectCode("invalid_runtime_plan", new Action() {
            public void run() { ParentRowTable.create(extraChildPlan); }
        });

        ChildRow first = child(11);
        ParentRow parent = new ParentRow();
        parent.id = 7;
        parent.children = new ArrayList<ChildRow>();
        parent.children.add(first);
        parent.optionalChildren = null;
        parent.keyedChildren = new HashMap<Integer, KeyedChildRow>();
        KeyedChildRow keyedChild = new KeyedChildRow();
        keyedChild.id = 5;
        keyedChild.value = 55;
        keyedChild.grandchildren = new ArrayList<GrandchildRow>();
        GrandchildRow grandchild = new GrandchildRow();
        grandchild.amount = 555L;
        keyedChild.grandchildren.add(grandchild);
        parent.keyedChildren.put(Integer.valueOf(5), keyedChild);

        ParentRowTable table = ParentRowTable.create();
        table.addBatch(new ParentRowBatch().add(parent));
        check(table.size() == 1, "parent size");
        ParentRowTable directTable = ParentRowTable.create();
        directTable.addBatch(new ParentRowBatch().addValues(
                99, new ChildRowBatch().add(child(1)), null,
                new KeyedChildRowBatch().add(keyedChild(2, 2))));
        check(directTable.children(0).size() == 1, "direct child batch setter");
        check(!directTable.optionalChildrenPresent(0), "direct optional child absent");
        expectCode("invalid_null_value", new Action() {
            public void run() {
                new ParentRowBatch().addValues(
                        1, null, null, new KeyedChildRowBatch());
            }
        });
        directTable.release();

        ParentRowTable lazyTable = ParentRowTable.create();
        lazyTable.addBatch(new ParentRowBatch().add(emptyParent(98)));
        check(lazyTable.statsSnapshot().childInstanceCount() == 0L,
                "required empty is physically lazy");
        ParentRow lazyMaterialized = lazyTable.materialize().get(0);
        check(lazyMaterialized.children.isEmpty()
                        && lazyMaterialized.keyedChildren.isEmpty(),
                "required empty materialization shape");
        TableStats lazyStats = lazyTable.statsSnapshot();
        check(lazyStats.lastMaterializationMaximumOwnershipDepth() == 1
                        && lazyStats.lastMaterializationTableInstances() == 3L
                        && lazyStats.lastMaterializationRows() == 1L
                        && lazyStats.lastMaterializationLeafValues() == 1L
                        && lazyStats.lastMaterializationEstimatedAllocationBytes() == 184L,
                "required empty exact materialization estimator");
        check(lazyTable.children(0).capacity() == 2,
                "child field initial capacity override");
        check(lazyTable.runtimePlan().requireChild("parent_rows", "children")
                        .initialCapacity() == 2,
                "child plan initial capacity");
        check(lazyTable.keyedChildren(0).capacity() == 3,
                "keyed child initial capacity override");
        check(lazyTable.runtimePlan().requireChild("parent_rows", "keyedChildren")
                        .initialCapacity() == 3,
                "keyed child plan initial capacity");
        check(lazyTable.statsSnapshot().childInstanceCount() == 2L,
                "required facade allocation facts");
        lazyTable.release();

        ParentRowTable optionalEmptyTable = ParentRowTable.create();
        optionalEmptyTable.addBatch(new ParentRowBatch().add(emptyParent(97)));
        optionalEmptyTable.ensureOptionalChildren(0);
        check(optionalEmptyTable.materialize().get(0).optionalChildren.isEmpty(),
                "optional present-empty detached shape");
        TableStats optionalEmptyStats = optionalEmptyTable.statsSnapshot();
        check(optionalEmptyStats.lastMaterializationMaximumOwnershipDepth() == 1
                        && optionalEmptyStats.lastMaterializationTableInstances() == 4L
                        && optionalEmptyStats.lastMaterializationRows() == 1L
                        && optionalEmptyStats.lastMaterializationLeafValues() == 1L
                        && optionalEmptyStats.lastMaterializationEstimatedAllocationBytes()
                        == 224L,
                "optional present-empty exact materialization estimator");
        optionalEmptyTable.release();
        ChildRowTable children = table.children(0);
        check(children.size() == 1, "required child size");
        expectCode("owned_child_release", new Action() {
            public void run() { children.release(); }
        });
        check(children.size() == 1, "owned release fail closed");
        check(!table.optionalChildrenPresent(0), "optional absent");

        List<ParentRow> materialized = table.materialize();
        check(materialized.get(0).children.size() == 1, "recursive list");
        check(materialized.get(0).children.get(0).value == 11, "recursive value");
        check(materialized.get(0).optionalChildren == null, "recursive optional absent");
        check(materialized.get(0).keyedChildren.get(Integer.valueOf(5)).value == 55,
                "recursive keyed child");
        check(materialized.get(0).keyedChildren.get(Integer.valueOf(5))
                .grandchildren.get(0).amount == 555L, "recursive grandchild");
        final KeyedChildRowTable oldKeyed = table.keyedChildren(0);
        KeyedChildRow duplicateA = keyedChild(9, 90);
        KeyedChildRow duplicateB = keyedChild(9, 91);
        final KeyedChildRowBatch duplicateBatch = new KeyedChildRowBatch()
                .add(duplicateA).add(duplicateB);
        expectCode("duplicate_key", new Action() {
            public void run() { table.replaceKeyedChildren(0, duplicateBatch); }
        });
        check(oldKeyed.size() == 1 && oldKeyed.fetch(5).value == 55,
                "failed replacement preserves old subtree");

        ChildRowTable optional = table.ensureOptionalChildren(0);
        check(table.materialize().get(0).optionalChildren.isEmpty(),
                "optional present-empty shape");
        optional.addBatch(new ChildRowBatch().add(child(22)));
        check(table.optionalChildrenPresent(0), "optional present");
        check(table.materialize().get(0).optionalChildren.get(0).value == 22,
                "optional recursive value");
        table.unsetOptionalChildren(0);
        expectCode("child_released", new Action() {
            public void run() { optional.size(); }
        });

        ChildRowTable oldRequired = table.children(0);
        table.replaceChildren(0, new ChildRowBatch().add(child(33)));
        expectCode("child_released", new Action() {
            public void run() { oldRequired.size(); }
        });
        check(table.children(0).fetchAt(0).value == 33, "replacement");
        TableStats stats = table.statsSnapshot();
        check(stats.childInstanceCount() == 3L, "child instance stats");
        check(stats.descendantRowCount() == 3L, "descendant row stats");
        check(stats.materializationInvocationCount() >= 2L, "materialization invocations");
        final MaterializationBudget shallow = MaterializationBudget.defaults().toBuilder()
                .maximumOwnershipDepth(1).build();
        expectCode("materialization_budget_exceeded", new Action() {
            public void run() { table.materialize(shallow); }
        });
        check(table.statsSnapshot().materializationFailureCount() == 1L,
                "materialization failure stats");

        long resetEpoch = table.structuralEpoch();
        int resetCapacity = table.capacity();
        String resetPlan = table.runtimePlan().runtimePlanHash();
        table.resetStats();
        TableStats reset = table.statsSnapshot();
        check(reset.materializationInvocationCount() == 0L, "reset invocations zero");
        check(reset.materializationFailureCount() == 0L, "reset failures zero");
        check(reset.lastMaterializationBudgetIdentity().isEmpty(), "reset budget empty");
        check(reset.lastMaterializationMaximumOwnershipDepth() == 0,
                "reset depth zero");
        check(reset.lastMaterializationTableInstances() == 0L
                        && reset.lastMaterializationRows() == 0L
                        && reset.lastMaterializationLeafValues() == 0L
                        && reset.lastMaterializationEstimatedAllocationBytes() == 0L,
                "reset last counters zero");
        check(table.structuralEpoch() == resetEpoch && table.capacity() == resetCapacity,
                "reset preserves table identity");
        check(table.runtimePlan().runtimePlanHash().equals(resetPlan),
                "reset preserves runtime plan");
        check(reset.childInstanceCount() == 3L && reset.descendantRowCount() == 3L,
                "reset preserves child facts");
        table.materialize();
        TableStats measured = table.statsSnapshot();
        check(measured.materializationInvocationCount() == 1L, "reset invocation stats");
        check(measured.childInstanceCount() == 3L, "reset preserves child facts");
        check(measured.lastMaterializationMaximumOwnershipDepth() == 2,
                "exact ownership depth");
        check(measured.lastMaterializationTableInstances() == 4L,
                "exact table instances");
        check(measured.lastMaterializationRows() == 4L, "exact reachable rows");
        check(measured.lastMaterializationLeafValues() == 5L,
                "exact reachable leaves");
        check(measured.lastMaterializationEstimatedAllocationBytes() == 520L,
                "exact allocation estimator");
        expectBudget(table, measured, "depth",
                measured.lastMaterializationMaximumOwnershipDepth() - 1L);
        expectBudget(table, measured, "tableInstances",
                measured.lastMaterializationTableInstances() - 1L);
        expectBudget(table, measured, "rows", measured.lastMaterializationRows() - 1L);
        expectBudget(table, measured, "leaves",
                measured.lastMaterializationLeafValues() - 1L);
        expectBudget(table, measured, "bytes",
                measured.lastMaterializationEstimatedAllocationBytes() - 1L);
        MaterializationBudget exact = MaterializationBudget.builder()
                .maximumOwnershipDepth(measured.lastMaterializationMaximumOwnershipDepth())
                .maximumTableInstances(measured.lastMaterializationTableInstances())
                .maximumRows(measured.lastMaterializationRows())
                .maximumLeafValues(measured.lastMaterializationLeafValues())
                .maximumEstimatedAllocationBytes(
                        measured.lastMaterializationEstimatedAllocationBytes())
                .build();
        check(table.materialize(exact).size() == 1, "all budget exact boundary");

        ParentRowTable shared = ParentRowTable.create();
        shared.addBatch(new ParentRowBatch()
                .add(emptyParent(101)).add(emptyParent(102)));
        final long sharedEpoch = shared.structuralEpoch();
        final MaterializationBudget oneRow = MaterializationBudget.defaults().toBuilder()
                .maximumRows(1L).build();
        expectCode("materialization_budget_exceeded", new Action() {
            public void run() { shared.fetchAll(oneRow); }
        });
        check(shared.size() == 2 && shared.structuralEpoch() == sharedEpoch,
                "fetchAll shared budget preserves facts and epoch");
        shared.release();

        MaterializationBudget customDefault = MaterializationBudget.defaults().toBuilder()
                .maximumRows(77L).build();
        RuntimePlan customPlan = SchemaMetadata.newPlan()
                .defaultMaterializationBudget(customDefault).build();
        ParentRowTable customTable = ParentRowTable.create(customPlan);
        customTable.addBatch(new ParentRowBatch().add(emptyParent(103)));
        customTable.materialize();
        check(customDefault.identity().equals(
                        customTable.statsSnapshot().lastMaterializationBudgetIdentity()),
                "custom plan default budget identity");
        MaterializationBudget perCall = customDefault.toBuilder().maximumRows(88L).build();
        customTable.materialize(perCall);
        check(perCall.identity().equals(
                        customTable.statsSnapshot().lastMaterializationBudgetIdentity()),
                "per-call budget identity");
        customTable.release();

        table.resetStats();
        table.fetchAt(0);
        table.fetchAt(0, MaterializationBudget.defaults());
        table.findFirst();
        table.findFirst(MaterializationBudget.defaults());
        table.firstOrThrow();
        table.firstOrThrow(MaterializationBudget.defaults());
        table.fetchAll();
        table.fetchAll(MaterializationBudget.defaults());
        table.materialize();
        table.materialize(MaterializationBudget.defaults());
        check(table.statsSnapshot().materializationInvocationCount() == 10L,
                "all row materialization overload stats");

        final ChildRowTable materializationChild = table.children(0);
        ParentRow.constructionHook = new Runnable() {
            public void run() { materializationChild.clear(); }
        };
        expectCode("reentrant_access", new Action() {
            public void run() { table.materialize(); }
        });
        ParentRow.constructionHook = null;
        check(materializationChild.size() == 1, "two-pass guard preserves descendant");

        materializationChild.fetchAt(0);
        final long childInvocationCount = materializationChild.statsSnapshot()
                .materializationInvocationCount();
        ParentRow.constructionHook = new Runnable() {
            public void run() { materializationChild.resetStats(); }
        };
        expectCode("reentrant_access", new Action() {
            public void run() { table.materialize(); }
        });
        ParentRow.constructionHook = null;
        check(materializationChild.statsSnapshot().materializationInvocationCount()
                        == childInvocationCount,
                "two-pass guard preserves descendant stats");

        verifyUnexpectedMaterializationFailure(parent, false);
        verifyUnexpectedMaterializationFailure(parent, true);

        final long allocationEpoch = table.structuralEpoch();
        final long allocationFailures =
                table.statsSnapshot().materializationFailureCount();
        MaterializationAllocation.Scope allocationScope =
                MaterializationAllocation.installForCurrentThread(
                        new MaterializationAllocation.Provider() {
                            public boolean allow(
                                    String phase, long estimatedBytes, String path) {
                                return false;
                            }
                        });
        try {
            expectCode("allocation_failure", new Action() {
                public void run() { table.materialize(); }
            });
        } finally {
            allocationScope.close();
        }
        check(table.structuralEpoch() == allocationEpoch,
                "allocation failure preserves epoch");
        check(table.size() == 1 && materializationChild.size() == 1,
                "allocation failure preserves facts");
        check(table.statsSnapshot().materializationFailureCount()
                        == allocationFailures + 1L,
                "allocation failure stats");
        check(table.materialize().size() == 1, "allocation scope recovery");

        KeyedChildRowTable keyedStats = table.keyedChildren(0);
        check(keyedStats.statsSnapshot().childInstanceCount() == 1L,
                "owned child stats scope");
        check(keyedStats.statsSnapshot().descendantRowCount() == 1L,
                "owned child descendant scope");
        keyedStats.resetStats();
        keyedStats.fetch(5);
        long requiredRowBytes = keyedStats.statsSnapshot()
                .lastMaterializationEstimatedAllocationBytes();
        check(keyedStats.find(5).isPresent(), "default find present");
        check(keyedStats.statsSnapshot().lastMaterializationEstimatedAllocationBytes()
                        == requiredRowBytes + 16L,
                "present Optional exact allocation estimator");
        check(!keyedStats.find(404).isPresent(), "default find empty");
        check(keyedStats.statsSnapshot().lastMaterializationTableInstances() == 1L
                        && keyedStats.statsSnapshot().lastMaterializationRows() == 0L
                        && keyedStats.statsSnapshot().lastMaterializationLeafValues() == 0L
                        && keyedStats.statsSnapshot()
                        .lastMaterializationEstimatedAllocationBytes() == 0L,
                "empty Optional exact allocation estimator");
        check(keyedStats.find(5, MaterializationBudget.defaults()).isPresent(),
                "explicit find present");
        keyedStats.fetch(5);
        keyedStats.fetch(5, MaterializationBudget.defaults());
        keyedStats.materialize();
        keyedStats.materialize(MaterializationBudget.defaults());
        check(keyedStats.statsSnapshot().materializationInvocationCount() == 8L,
                "find materialization stats");

        final GrandchildRowTable releasedGrandchild = keyedStats.grandchildren(5);
        keyedStats.clear();
        check(keyedStats.size() == 0, "child clear keeps child open");
        expectCode("child_released", new Action() {
            public void run() { releasedGrandchild.size(); }
        });

        ParentRow movedParent = new ParentRow();
        movedParent.id = 8;
        movedParent.children = new ArrayList<ChildRow>();
        movedParent.optionalChildren = null;
        movedParent.keyedChildren = new HashMap<Integer, KeyedChildRow>();
        table.addBatch(new ParentRowBatch().add(movedParent));
        final ChildRowTable movedChild = table.children(1);
        final ChildRowTable deletedChild = table.children(0);
        movedChild.addBatch(new ChildRowBatch().add(child(44)));
        table.filter(new ParentRowScan.Predicate() {
            public boolean test(com.example.soma.child.generated.ParentRowCursor row) {
                return row.id() == 7;
            }
        }).remove();
        check(table.size() == 1, "parent compaction size");
        expectCode("child_released", new Action() {
            public void run() { deletedChild.size(); }
        });
        check(movedChild.fetchAt(0).value == 44, "child facade survives row compaction");
        check(table.children(0).fetchAt(0).value == 44, "moved child locator repair");

        final ChildRowTable pinnedChild = table.children(0);
        pinnedChild.forEach(new ChildRowScan.Consumer() {
            public void accept(ChildRowCursor row) {
                expectCode("view_pinned", new Action() {
                    public void run() { table.clear(); }
                });
            }
        });
        check(table.size() == 1, "active child operation pins parent");
        IntColumnView parentView = table.idColumn();
        expectCode("view_pinned", new Action() {
            public void run() { table.remove(); }
        });
        check(table.size() == 1, "parent view remove atomic");
        parentView.close();
        IntColumnView view = pinnedChild.valueColumn();
        expectCode("view_pinned", new Action() {
            public void run() { table.clear(); }
        });
        check(table.size() == 1, "pinned clear atomic");
        view.close();
        table.clear();
        check(table.size() == 0, "cascade clear");

        ParentRow second = new ParentRow();
        second.id = 9;
        second.children = new ArrayList<ChildRow>();
        second.keyedChildren = new HashMap<Integer, KeyedChildRow>();
        table.addBatch(new ParentRowBatch().add(second));
        ChildRowTable releasedByRoot = table.children(0);
        table.release();
        table.release();
        expectCode("table_released", new Action() {
            public void run() { releasedByRoot.size(); }
        });
        System.out.println("child-phase4-consumer: ok");
    }

    private static void testSegmentedChildHandleRelocation() {
        RuntimePlan.Builder builder = SchemaMetadata.newPlan();
        builder.table("parent_rows")
                .initialCapacity(4)
                .planningRows(32769)
                .maximumRows(70000)
                .workloadProfile(SomaWorkloadProfile.SCAN_GROWTH);
        RuntimePlan plan = builder.build();
        check(plan.requireTable("parent_rows").storageLayout()
                        == SomaStorageLayout.FLAT_HEAD_SEGMENTED_TAIL
                        && plan.requireTable("parent_rows").flatHeadRows() == 32768
                        && plan.requireTable("parent_rows").segmentRows() == 32768,
                "segmented parent Plan identity");

        ParentRowBatch batch = new ParentRowBatch(32770);
        ChildRowBatch emptyChildren = new ChildRowBatch(0);
        KeyedChildRowBatch emptyKeyedChildren = new KeyedChildRowBatch(0);
        for (int row = 0; row < 32770; row++) {
            batch.addValues(
                    row, emptyChildren, null, emptyKeyedChildren);
        }
        ParentRowTable table = ParentRowTable.create(plan);
        table.addBatch(batch);

        ChildRowTable headChild = table.children(32767);
        ChildRowTable removedChild = table.children(32768);
        ChildRowTable movedChild = table.children(32769);
        headChild.addBatch(new ChildRowBatch().add(child(67)));
        removedChild.addBatch(new ChildRowBatch().add(child(68)));
        movedChild.addBatch(new ChildRowBatch().add(child(69)));
        check(table.children(32767).fetchAt(0).value == 67
                        && table.children(32768).fetchAt(0).value == 68
                        && table.children(32769).fetchAt(0).value == 69,
                "child handles cross flat-head and tail boundary");

        table.filter(new ParentRowScan.Predicate() {
            public boolean test(
                    com.example.soma.child.generated.ParentRowCursor row) {
                return row.id() == 32768;
            }
        }).remove();
        check(table.size() == 32769
                        && table.fetchAt(32768).id == 32769
                        && table.children(32768).fetchAt(0).value == 69
                        && movedChild.fetchAt(0).value == 69,
                "swap-remove repairs segmented child handle locator");
        expectCode("child_released", new Action() {
            public void run() {
                removedChild.size();
            }
        });
        table.release();
    }

    private static ChildRow child(int value) {
        ChildRow row = new ChildRow();
        row.value = value;
        return row;
    }

    private static KeyedChildRow keyedChild(int id, int value) {
        KeyedChildRow row = new KeyedChildRow();
        row.id = id;
        row.value = value;
        row.grandchildren = new ArrayList<GrandchildRow>();
        return row;
    }

    private static ParentRow emptyParent(int id) {
        ParentRow row = new ParentRow();
        row.id = id;
        row.children = new ArrayList<ChildRow>();
        row.optionalChildren = null;
        row.keyedChildren = new HashMap<Integer, KeyedChildRow>();
        return row;
    }

    private static void testSnapshotBoundaries() {
        ParentRow carrier = emptyParent(201);
        ChildRow carrierChild = child(41);
        carrier.children.add(carrierChild);
        KeyedChildRow carrierKeyed = keyedChild(7, 71);
        GrandchildRow carrierGrandchild = new GrandchildRow();
        carrierGrandchild.amount = 701L;
        carrierKeyed.grandchildren.add(carrierGrandchild);
        carrier.keyedChildren.put(Integer.valueOf(7), carrierKeyed);
        ParentRowBatch carrierSnapshot = new ParentRowBatch().add(carrier);
        carrierChild.value = 99;
        carrier.children.clear();
        carrierKeyed.value = 99;
        carrierGrandchild.amount = 999L;
        carrier.keyedChildren.clear();
        ParentRowTable carrierTable = ParentRowTable.create();
        carrierTable.addBatch(carrierSnapshot);
        ParentRow carrierResult = carrierTable.materialize().get(0);
        check(carrierResult.children.get(0).value == 41, "carrier child snapshot");
        check(carrierResult.keyedChildren.get(Integer.valueOf(7)).value == 71,
                "carrier map snapshot");
        check(carrierResult.keyedChildren.get(Integer.valueOf(7))
                        .grandchildren.get(0).amount == 701L,
                "carrier descendant snapshot");
        carrierResult.children.clear();
        carrierResult.keyedChildren.get(Integer.valueOf(7)).grandchildren.clear();
        carrierResult.keyedChildren.clear();
        check(carrierTable.children(0).size() == 1, "materialized list detached");
        check(carrierTable.keyedChildren(0).fetch(7).value == 71,
                "materialized map detached");
        check(carrierTable.keyedChildren(0).grandchildren(7).size() == 1,
                "materialized descendant detached");
        carrierTable.release();

        ChildRowBatch children = new ChildRowBatch().add(child(51));
        KeyedChildRowBatch keyed = new KeyedChildRowBatch().add(keyedChild(8, 81));
        ParentRowBatch directSnapshot = new ParentRowBatch().addValues(
                202, children, null, keyed);
        children.clear();
        children.add(child(59));
        keyed.clear();
        keyed.add(keyedChild(9, 91));
        ParentRowTable directTable = ParentRowTable.create();
        directTable.addBatch(directSnapshot);
        check(directTable.children(0).fetchAt(0).value == 51,
                "direct ChildBatch deep copy");
        check(directTable.keyedChildren(0).containsKey(8)
                        && !directTable.keyedChildren(0).containsKey(9),
                "direct keyed ChildBatch deep copy");
        directTable.release();

        final ParentRowBatch mismatchBatch = new ParentRowBatch().add(emptyParent(204));
        final ParentRow mismatch = emptyParent(203);
        mismatch.keyedChildren.put(Integer.valueOf(6), keyedChild(5, 1));
        expectCode("child_key_mismatch", new Action() {
            public void run() { mismatchBatch.add(mismatch); }
        });
        check(mismatchBatch.size() == 1, "child key mismatch preserves Batch size");
        ParentRowTable mismatchTable = ParentRowTable.create();
        mismatchTable.addBatch(mismatchBatch);
        check(mismatchTable.size() == 1 && mismatchTable.fetchAt(0).id == 204,
                "child key mismatch preserves existing Batch snapshot");
        mismatchTable.release();
    }

    private static void testFloatingChildKeys() {
        FloatingParentRow row = new FloatingParentRow();
        row.id = 1;
        row.floatChildren = new HashMap<Float, FloatKeyedChildRow>();
        FloatKeyedChildRow floatChild = new FloatKeyedChildRow();
        floatChild.id = -0.0f;
        floatChild.value = 11;
        row.floatChildren.put(Float.valueOf(+0.0f), floatChild);
        row.doubleChildren = new HashMap<Double, DoubleKeyedChildRow>();
        DoubleKeyedChildRow doubleChild = new DoubleKeyedChildRow();
        doubleChild.id = -0.0d;
        doubleChild.value = 22;
        row.doubleChildren.put(Double.valueOf(+0.0d), doubleChild);
        FloatingParentRowTable table = FloatingParentRowTable.create();
        table.addBatch(new FloatingParentRowBatch().add(row));
        FloatingParentRow result = table.materialize().get(0);
        check(result.floatChildren.size() == 1, "float child map identity");
        check(Float.floatToIntBits(result.floatChildren.keySet().iterator().next()) == 0,
                "float child map positive zero");
        check(result.doubleChildren.size() == 1, "double child map identity");
        check(Double.doubleToLongBits(result.doubleChildren.keySet().iterator().next()) == 0L,
                "double child map positive zero");
        table.release();
    }

    private static void testOwnershipInstanceQuotaAndRetry() {
        RuntimePlan twoInstances = SchemaMetadata.newPlan()
                .maximumOwnershipTableInstances(2L).build();
        ParentRowTable table = ParentRowTable.create(twoInstances);
        table.addBatch(new ParentRowBatch()
                .add(emptyParent(301)).add(emptyParent(302)));
        final ChildRowTable first = table.children(0);
        long epoch = table.structuralEpoch();
        long childInstances = table.statsSnapshot().childInstanceCount();
        expectMemoryLimit(2L, 3L, new Action() {
            public void run() { table.keyedChildren(1); }
        });
        check(table.size() == 2 && table.structuralEpoch() == epoch
                        && table.statsSnapshot().childInstanceCount() == childInstances,
                "instance quota failure preserves facts/epoch");
        table.filter(new ParentRowScan.Predicate() {
            public boolean test(com.example.soma.child.generated.ParentRowCursor row) {
                return row.id() == 301;
            }
        }).remove();
        expectCode("child_released", new Action() {
            public void run() { first.size(); }
        });
        check(table.keyedChildren(0).size() == 0,
                "instance quota retry succeeds after subtree release");
        table.release();

        RuntimePlan fourInstances = SchemaMetadata.newPlan()
                .maximumOwnershipTableInstances(4L).build();
        ParentRowTable replacement = ParentRowTable.create(fourInstances);
        replacement.addBatch(new ParentRowBatch().add(parentWithNestedKeyed(303, 5, 51, 501L)));
        final KeyedChildRowTable old = replacement.keyedChildren(0);
        KeyedChildRowBatch next = new KeyedChildRowBatch()
                .add(keyedWithGrandchild(6, 61, 601L));
        long replacementEpoch = replacement.structuralEpoch();
        expectMemoryLimit(4L, 5L, new Action() {
            public void run() { replacement.replaceKeyedChildren(0, next); }
        });
        check(replacement.structuralEpoch() == replacementEpoch
                        && replacement.statsSnapshot().childInstanceCount() == 2L
                        && old.fetch(5).value == 51
                        && old.grandchildren(5).fetchAt(0).amount == 501L,
                "mid-subtree quota failure discards staged subtree and preserves old");
        old.clear();
        replacement.replaceKeyedChildren(0, next);
        expectCode("child_released", new Action() {
            public void run() { old.size(); }
        });
        check(replacement.keyedChildren(0).fetch(6).value == 61
                        && replacement.keyedChildren(0).grandchildren(6)
                        .fetchAt(0).amount == 601L,
                "mid-subtree replacement retry succeeds after old descendant release");
        replacement.release();
    }

    private static void testRecursiveReplacementPreflightAndRetry() {
        ParentRowTable table = ParentRowTable.create();
        table.addBatch(new ParentRowBatch().add(parentWithNestedKeyed(401, 7, 71, 701L)));
        final KeyedChildRowTable old = table.keyedChildren(0);
        LongColumnView pinned = old.grandchildren(7).amountColumn();
        KeyedChildRowBatch next = new KeyedChildRowBatch()
                .add(keyedWithGrandchild(8, 81, 801L));
        long epoch = table.structuralEpoch();
        expectCode("view_pinned", new Action() {
            public void run() { table.replaceKeyedChildren(0, next); }
        });
        check(table.structuralEpoch() == epoch
                        && table.statsSnapshot().childInstanceCount() == 2L
                        && old.fetch(7).value == 71
                        && old.grandchildren(7).fetchAt(0).amount == 701L,
                "recursive replacement preflight preserves old and discards staged subtree");
        pinned.close();
        table.replaceKeyedChildren(0, next);
        expectCode("child_released", new Action() {
            public void run() { old.size(); }
        });
        check(table.keyedChildren(0).fetch(8).value == 81,
                "recursive replacement retry publishes new subtree");
        table.release();
    }

    private static ParentRow parentWithNestedKeyed(
            int parentId, int childId, int value, long amount) {
        ParentRow parent = emptyParent(parentId);
        parent.keyedChildren.put(Integer.valueOf(childId),
                keyedWithGrandchild(childId, value, amount));
        return parent;
    }

    private static KeyedChildRow keyedWithGrandchild(int id, int value, long amount) {
        KeyedChildRow row = keyedChild(id, value);
        GrandchildRow grandchild = new GrandchildRow();
        grandchild.amount = amount;
        row.grandchildren.add(grandchild);
        return row;
    }

    private static void expectMemoryLimit(long limit, long proposed, Action action) {
        try {
            action.run();
            throw new AssertionError("expected memory_limit_exceeded");
        } catch (SomaRuntimeException failure) {
            check("memory_limit_exceeded".equals(failure.code()),
                    "instance limit code " + failure.code());
            check(Long.toString(limit).equals(failure.context().get("limit"))
                            && Long.toString(proposed).equals(
                            failure.context().get("proposed")),
                    "instance limit/proposed context");
        }
    }

    private static void expectBudget(
            final ParentRowTable table, TableStats measured,
            String dimension, long limit) {
        MaterializationBudget.Builder builder = MaterializationBudget.builder()
                .maximumOwnershipDepth(measured.lastMaterializationMaximumOwnershipDepth())
                .maximumTableInstances(measured.lastMaterializationTableInstances())
                .maximumRows(measured.lastMaterializationRows())
                .maximumLeafValues(measured.lastMaterializationLeafValues())
                .maximumEstimatedAllocationBytes(
                        measured.lastMaterializationEstimatedAllocationBytes());
        if ("depth".equals(dimension)) builder.maximumOwnershipDepth((int) limit);
        else if ("tableInstances".equals(dimension)) builder.maximumTableInstances(limit);
        else if ("rows".equals(dimension)) builder.maximumRows(limit);
        else if ("leaves".equals(dimension)) builder.maximumLeafValues(limit);
        else if ("bytes".equals(dimension)) builder.maximumEstimatedAllocationBytes(limit);
        final MaterializationBudget budget = builder.build();
        long epoch = table.structuralEpoch();
        int size = table.size();
        int childSize = table.children(0).size();
        try {
            table.materialize(budget);
            throw new AssertionError("expected budget failure " + dimension);
        } catch (SomaRuntimeException failure) {
            check("materialization_budget_exceeded".equals(failure.code()),
                    "budget code " + failure.code());
            String expectedDimension = "depth".equals(dimension)
                    ? "maximumOwnershipDepth"
                    : "tableInstances".equals(dimension)
                    ? "maximumTableInstances"
                    : "rows".equals(dimension)
                    ? "maximumRows"
                    : "leaves".equals(dimension)
                    ? "maximumLeafValues"
                    : "maximumEstimatedAllocationBytes";
            check(expectedDimension.equals(failure.context().get("dimension")),
                    "budget dimension");
            check(Long.toString(limit).equals(failure.context().get("limit")),
                    "budget limit");
            check(Long.parseLong(failure.context().get("proposed")) > limit,
                    "budget proposed");
            check(Long.parseLong(failure.context().get("current")) >= 0L,
                    "budget current");
            check(budget.identity().equals(failure.context().get("budgetIdentity")),
                    "budget identity");
            check(!failure.path().isEmpty(), "budget path");
        }
        check(table.structuralEpoch() == epoch && table.size() == size
                        && table.children(0).size() == childSize,
                "budget failure preserves facts");
    }

    private static void expectCode(String code, Action action) {
        try {
            action.run();
            throw new AssertionError("expected " + code);
        } catch (SomaRuntimeException failure) {
            check(code.equals(failure.code()), "code " + failure.code());
        }
    }

    private static void verifyUnexpectedMaterializationFailure(
            ParentRow parent, boolean error) {
        final ParentRowTable failed = ParentRowTable.create();
        failed.addBatch(new ParentRowBatch().add(parent));
        final ChildRowTable failedChild = failed.children(0);
        final long failures =
                failed.statsSnapshot().materializationFailureCount();
        if (error) {
            final Error expected = new AssertionError("expected carrier error");
            ParentRow.constructionHook = new Runnable() {
                public void run() { throw expected; }
            };
            try {
                failed.materialize();
                throw new AssertionError("carrier Error not propagated");
            } catch (Error actual) {
                check(actual == expected, "carrier Error identity");
            } finally {
                ParentRow.constructionHook = null;
            }
        } else {
            final RuntimeException expected =
                    new IllegalStateException("expected carrier runtime failure");
            ParentRow.constructionHook = new Runnable() {
                public void run() { throw expected; }
            };
            try {
                failed.materialize();
                throw new AssertionError("carrier RuntimeException not propagated");
            } catch (RuntimeException actual) {
                check(actual == expected, "carrier RuntimeException identity");
            } finally {
                ParentRow.constructionHook = null;
            }
        }
        check(failed.statsSnapshot().materializationFailureCount() == failures + 1L,
                "unexpected carrier failure stats");
        expectCode("internal_invariant_violation", new Action() {
            public void run() { failed.size(); }
        });
        expectCode("internal_invariant_violation", new Action() {
            public void run() { failedChild.size(); }
        });
        failed.release();
        check(failed.isReleased(), "faulted ownership aggregate release");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface Action { void run(); }
}
