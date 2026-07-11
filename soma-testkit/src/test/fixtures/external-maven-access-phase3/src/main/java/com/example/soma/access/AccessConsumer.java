package com.example.soma.access;

import com.example.soma.access.generated.AccessRecordBatch;
import com.example.soma.access.generated.AccessRecordRows;
import com.example.soma.access.generated.AccessRecordTable;
import com.example.soma.access.generated.VisitBatch;
import com.example.soma.access.generated.VisitTable;
import com.example.soma.access.generated.FloatingAccessBatch;
import com.example.soma.access.generated.FloatingAccessTable;
import com.example.soma.access.generated.EnumAccessBatch;
import com.example.soma.access.generated.EnumAccessTable;
import com.example.soma.access.generated.UniquePositionBatch;
import com.example.soma.access.generated.UniquePositionTable;
import com.example.soma.access.generated.BooleanDoubleAccessBatch;
import com.example.soma.access.generated.BooleanDoubleAccessTable;
import com.example.soma.access.generated.MutatorAtomicAccessBatch;
import com.example.soma.access.generated.MutatorAtomicAccessTable;
import com.hgtech.soma.runtime.SomaRuntimeException;
import com.hgtech.soma.runtime.OperationOutcome;
import com.hgtech.soma.runtime.RemoveResult;
import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.TablePlan;
import com.hgtech.soma.runtime.UpdateResult;

import java.util.List;

public final class AccessConsumer {
    private AccessConsumer() {
    }

    public static void main(String[] args) {
        expectCode("invalid_floating_access_value",
                () -> FloatingAccessTable.create().findByMetric(Float.NaN).count());
        expectCode("invalid_null_value",
                () -> EnumAccessTable.create().findByState(null).count());
        expectCode("invalid_null_value",
                () -> UniquePositionTable.create().findByPositionKey(null).count());

        RuntimePlan accessDefault = AccessRecordTable.defaultRuntimePlan();
        TablePlan tinySidecar = accessDefault.requireTable("AccessRecord")
                .toBuilder().maximumSidecarScratchBytes(1L).build();
        AccessRecordTable boundedAccess = AccessRecordTable.create(
                accessDefault.toBuilder().replaceTable(tinySidecar).build());
        AccessRecordBatch boundedBatch = new AccessRecordBatch();
        boundedBatch.addValues(1, 1, 1, 1);
        boundedAccess.addBatch(boundedBatch);
        expectCode("memory_limit_exceeded", () -> boundedAccess.findByState(1).count());
        TablePlan invalidPolicy = accessDefault.requireTable("AccessRecord")
                .toBuilder().sidecarMaintenancePolicy("none").build();
        expectCode("runtime_plan_mismatch", () -> AccessRecordTable.create(
                accessDefault.toBuilder().replaceTable(invalidPolicy).build()));

        MutatorAtomicAccessBatch atomicBatch = new MutatorAtomicAccessBatch();
        atomicBatch.addValues(1, AccessState.READY);
        MutatorAtomicAccessTable atomicTable = MutatorAtomicAccessTable.create();
        atomicTable.addBatch(atomicBatch);
        expectCode("invalid_null_value",
                () -> atomicTable.mutateAt(0).setId(2).setState(null).commit());
        require(atomicTable.fetchAt(0).id == 1
                        && atomicTable.fetchAt(0).state == AccessState.READY,
                "mutator validates all required references before live publish");
        MutatorAtomicAccess.failConstruction = true;
        try {
            atomicTable.findById(1).firstOrThrow();
            throw new AssertionError("expected carrier construction failure");
        } catch (IllegalStateException expected) {
            require("carrier construction failed".equals(expected.getMessage()),
                    "carrier RuntimeException propagates unchanged");
        } finally {
            MutatorAtomicAccess.failConstruction = false;
        }
        require(atomicTable.findById(1).count() == 1L,
                "carrier RuntimeException releases operation guard");

        AccessRecordBatch batch = new AccessRecordBatch(4);
        batch.addValues(10, 2, 1, 30);
        batch.addValues(20, 1, 1, 10);
        batch.addValues(30, 2, 1, 20);
        batch.addValues(40, 2, 2, 99);
        batch.addValues(50, 3, 2, 99);

        AccessRecordTable table = AccessRecordTable.create();
        table.addBatch(batch);
        require(table.findByState(2).count() == 3L, "non-unique index exact source");
        require(table.findByGroup(1).count() == 3L, "repeated index container");
        require(table.findByCode(20).firstOrThrow().score == 10, "unique source");

        List<AccessRecord> groupOne = table.byGroupScore(1).fetchAll();
        require(groupOne.size() == 3, "order prefix group");
        require(groupOne.get(0).score == 30 && groupOne.get(1).score == 20
                && groupOne.get(2).score == 10, "maintained descending order");
        List<AccessRecord> stableEquals = table.byGroupScore(2).fetchAll();
        require(stableEquals.get(0).code == 40 && stableEquals.get(1).code == 50,
                "maintained order is stable for equal selector values");
        require(table.statsSnapshot().sidecarRebuildCount() == 4L
                        && table.statsSnapshot().sidecarRebuildRows() == 20L,
                "initial sidecar rebuild evidence");
        require("primitive-sorted-permutation-v1".equals(
                        table.runtimePlan().requireTable("AccessRecord").accessStrategy())
                        && "dirty-lazy-rebuild-v1".equals(table.runtimePlan()
                                .requireTable("AccessRecord").sidecarMaintenancePolicy())
                        && table.runtimePlan().requireTable("AccessRecord")
                                .maximumSidecarScratchBytes() > 0L,
                "effective runtime plan declares selector strategy and bound");
        require(table.statsSnapshot().sidecarScratchCurrentBytes() > 0L
                        && table.statsSnapshot().sidecarScratchHighWaterBytes()
                                >= table.statsSnapshot().sidecarScratchCurrentBytes(),
                "sidecar retained and rebuild peak bytes are observable");
        table.findByState(2).count();
        require(table.statsSnapshot().sidecarRebuildCount() == 4L,
                "clean sidecar does not rebuild");
        table.findByState(2).firstOrThrow();
        require(table.statsSnapshot().lastScanned() == 1L,
                "selector firstOrThrow reads sidecar lazily");

        try {
            table.findByState(2).filter(row -> {
                throw new AssertionError("terminal error");
            }).count();
            throw new AssertionError("expected terminal error");
        } catch (AssertionError expected) {
            require("terminal error".equals(expected.getMessage()),
                    "unexpected terminal error");
        }
        require(table.findByState(2).count() == 3L,
                "terminal Error closes operation lifecycle");
        expectCode("callback_failed", () -> table.rows().filter(row -> {
            if (row.code() == 20) throw new IllegalArgumentException("predicate failed");
            return false;
        }).count());
        require(table.statsSnapshot().lastScanned() == 2L
                        && table.statsSnapshot().lastMatched() == 0L,
                "failed select records attempted source work");

        AccessRecordRows currentStateOne = table.findByState(1);
        table.mutateAt(1).setState(2).setScore(50).commit();
        require(currentStateOne.count() == 0L,
                "source resolves current sidecar facts at terminal time");
        require(table.statsSnapshot().sidecarDirtyCount() == 2L,
                "mutation dirties only dependent selectors");
        require(table.findByState(2).count() == 4L, "mutation invalidates index sidecar");
        require(table.byGroupScore(1).firstOrThrow().code == 20,
                "mutation invalidates order sidecar");
        long stormRebuilds = table.statsSnapshot().sidecarRebuildCount();
        table.mutateAt(0).setScore(31).commit();
        table.byGroupScore(1).firstOrThrow();
        table.mutateAt(0).setScore(30).commit();
        table.byGroupScore(1).firstOrThrow();
        require(table.statsSnapshot().sidecarRebuildCount() == stormRebuilds + 2L,
                "mutation-read rebuild storm remains observable");

        AccessRecordTable resultTable = AccessRecordTable.create();
        AccessRecordBatch resultBatch = new AccessRecordBatch();
        resultBatch.addValues(101, 1, 1, 1).addValues(102, 1, 1, 2);
        resultTable.addBatch(resultBatch);
        resultTable.findByState(1).count();
        expectCode("callback_failed", () -> resultTable.findByState(1).update(row -> {
            row.setScore(99);
            resultTable.resetStats();
        }));
        require(resultTable.findByCode(101).firstOrThrow().score == 1
                        && resultTable.findByCode(102).firstOrThrow().score == 2,
                "stats reset cannot invalidate operation-local result accounting");
        resultTable.mutateAt(0).setState(2).commit();
        UpdateResult updateResult = resultTable.findByState(2)
                .update(row -> row.setState(row.state()));
        require(updateResult.sidecarMaintained() == 0L
                        && updateResult.sidecarRebuilt() == 1L,
                "update result reports source sidecar rebuild");
        resultTable.mutateAt(0).setState(1).commit();
        RemoveResult removeResult = resultTable.findByState(1).limit(1).remove();
        require(removeResult.sidecarMaintained() == 0L
                        && removeResult.sidecarRebuilt() == 1L,
                "remove result reports source sidecar rebuild");
        long retainedBeforeClear = resultTable.statsSnapshot()
                .sidecarScratchCurrentBytes();
        resultTable.clear();
        require(resultTable.statsSnapshot().sidecarScratchCurrentBytes()
                        == retainedBeforeClear,
                "clear retains sidecar high-water arrays");
        resultTable.release();
        require(resultTable.statsSnapshot().sidecarScratchCurrentBytes() == 0L
                        && resultTable.statsSnapshot().sidecarScratchHighWaterBytes()
                                >= retainedBeforeClear,
                "release drops retained sidecar arrays but preserves high-water evidence");

        table.findByState(2).filter(row -> row.group() == 2).remove();
        require(table.size() == 4 && table.findByState(2).count() == 3L,
                "compaction invalidates sidecars");

        AccessRecordBatch duplicate = new AccessRecordBatch(1);
        duplicate.addValues(20, 9, 9, 9);
        expectCode("unique_constraint_violation", () -> table.addBatch(duplicate));
        require(table.size() == 4 && table.findByCode(20).count() == 1L,
                "duplicate add is atomic");

        expectCode("unique_constraint_violation", () -> table.findByCode(30)
                .update(row -> row.setCode(20)));
        require(table.findByCode(20).count() == 1L && table.findByCode(30).count() == 1L,
                "duplicate update is atomic");
        require(table.statsSnapshot().updateScratchHighWaterBytes()
                        > table.statsSnapshot().updateScratchCurrentBytes(),
                "transient unique validation allocation is not retained as current scratch");
        AccessRecordBatch invalidReplacement = new AccessRecordBatch();
        invalidReplacement.addValues(7, 1, 1, 1).addValues(7, 2, 2, 2);
        expectCode("unique_constraint_violation",
                () -> table.replaceAll(invalidReplacement));
        require(table.size() == 4 && table.findByCode(20).count() == 1L,
                "duplicate replaceAll is atomic");

        RouteId routeOne = new RouteId(1L);
        RouteId routeTwo = new RouteId(2L);
        VisitBatch visits = new VisitBatch();
        visits.addValues(new RoutePositionKey(routeOne, 2), 12)
                .addValues(new RoutePositionKey(routeTwo, 1), 21)
                .addValues(new RoutePositionKey(routeOne, 1), 11);
        VisitTable visitTable = VisitTable.create();
        visitTable.addBatch(visits);
        require(visitTable.findByRoute(routeOne).count() == 2L,
                "nested value grouped index parameter");
        require(visitTable.byRoutePosition(routeOne).firstOrThrow().payload == 11,
                "nested value grouped order prefix");
        require(visitTable.byRoutePosition().fetchAll().size() == 3,
                "whole maintained order overload");

        FloatingAccessBatch floatingBatch = new FloatingAccessBatch();
        floatingBatch.addValues(1, -0.0f).addValues(2, 2.5f);
        FloatingAccessTable floating = FloatingAccessTable.create();
        floating.addBatch(floatingBatch);
        require(floating.findByMetric(0.0f).count() == 1L,
                "floating access canonicalizes negative zero");
        require(Float.floatToIntBits(floating.fetchAt(0).metric)
                        == Float.floatToIntBits(0.0f),
                "floating access stores canonical positive zero");
        require(floating.byMetricOrder().firstOrThrow().id == 1,
                "floating maintained order");
        FloatingAccessBatch canonicalDuplicate = new FloatingAccessBatch();
        canonicalDuplicate.addValues(4, 0.0f);
        expectCode("unique_constraint_violation",
                () -> floating.addBatch(canonicalDuplicate));
        require(floating.size() == 2, "floating unique uses canonical zero");
        FloatingAccessBatch invalidFloating = new FloatingAccessBatch();
        invalidFloating.addValues(3, Float.NaN);
        expectCode("invalid_floating_access_value", () -> floating.addBatch(invalidFloating));
        require(floating.size() == 2, "invalid floating add is atomic");
        expectCode("invalid_floating_access_value",
                () -> floating.findByMetric(Float.POSITIVE_INFINITY).count());
        expectCode("invalid_floating_access_value",
                () -> floating.mutateAt(0).setMetric(Float.NaN).commit());
        require(floating.statsSnapshot().lastOutcome() == OperationOutcome.FAILED
                        && "invalid_floating_access_value".equals(
                                floating.statsSnapshot().lastErrorCode()),
                "mutator validation failure closes operation stats");
        require(floating.findByMetric(0.0f).count() == 1L,
                "invalid floating mutator is atomic");
        expectCode("invalid_floating_access_value", () -> floating.findByMetric(2.5f)
                .update(row -> row.setMetric(Float.NEGATIVE_INFINITY)));
        require(floating.findByMetric(2.5f).count() == 1L,
                "invalid floating update is atomic");
        expectCode("invalid_floating_access_value",
                () -> floating.replaceAll(invalidFloating));
        require(floating.size() == 2, "invalid floating replaceAll is atomic");

        EnumAccessBatch enumBatch = new EnumAccessBatch();
        enumBatch.addValues(1, AccessState.RUNNING, 2)
                .addValues(2, AccessState.READY, 3)
                .addValues(3, AccessState.RUNNING, 1);
        EnumAccessTable enumTable = EnumAccessTable.create();
        enumTable.addBatch(enumBatch);
        require(enumTable.findByState(AccessState.RUNNING).count() == 2L,
                "enum index keeps typed source parameter");
        require(enumTable.byStateRank(AccessState.RUNNING).firstOrThrow().id == 3,
                "enum grouped order prefix");
        long enumDirtyBefore = enumTable.statsSnapshot().sidecarDirtyCount();
        enumTable.findByState(AccessState.READY).update(row -> row.setId(20));
        require(enumTable.statsSnapshot().sidecarDirtyCount() == enumDirtyBefore,
                "unrelated update keeps selector sidecars clean");
        enumTable.findByState(AccessState.RUNNING).update(row -> {
            if (row.id() == 1) row.setState(AccessState.READY);
        });
        require(enumTable.findByState(AccessState.READY).count() == 2L,
                "enum update scratch remains primitive and publishes typed state");
        expectCode("invalid_null_value", () -> enumTable.findByState(null).count());

        BooleanDoubleAccessBatch booleanDoubleBatch = new BooleanDoubleAccessBatch();
        booleanDoubleBatch.addValues(1, true, 2.0d)
                .addValues(2, false, 1.0d)
                .addValues(3, true, 3.0d);
        BooleanDoubleAccessTable booleanDouble = BooleanDoubleAccessTable.create();
        booleanDouble.addBatch(booleanDoubleBatch);
        require(booleanDouble.findByActive(true).count() == 2L,
                "boolean index uses primitive selector binding");
        require(booleanDouble.findByMetric(2.0d).firstOrThrow().id == 1,
                "double unique uses primitive selector binding");
        require(booleanDouble.byActiveMetric(true).firstOrThrow().id == 3,
                "boolean/double grouped DESC order");
        expectCode("invalid_floating_access_value",
                () -> booleanDouble.findByMetric(Double.NaN).count());

        RoutePositionKey firstPosition = new RoutePositionKey(routeOne, 1);
        RoutePositionKey secondPosition = new RoutePositionKey(routeOne, 2);
        UniquePositionBatch positions = new UniquePositionBatch();
        positions.addValues(1, firstPosition).addValues(2, secondPosition);
        UniquePositionTable positionTable = UniquePositionTable.create();
        positionTable.addBatch(positions);
        require(positionTable.findByPositionKey(firstPosition).firstOrThrow().id == 1,
                "composite value grouped unique parameter");
        expectCode("unique_constraint_violation",
                () -> positionTable.mutate(2).setPositionKey(firstPosition).commit());
        require(positionTable.findByPositionKey(secondPosition).count() == 1L,
                "composite unique mutator failure is atomic");
        expectCode("unique_constraint_violation",
                () -> positionTable.findByPositionKey(secondPosition)
                        .update(row -> row.setPositionKey(firstPosition)));
        require(positionTable.findByPositionKey(secondPosition).count() == 1L,
                "composite unique row update failure is atomic");

        AccessOracleCheck.run();
    }

    private static void expectCode(String code, Action action) {
        try {
            action.run();
            throw new AssertionError("expected " + code);
        } catch (SomaRuntimeException failure) {
            require(code.equals(failure.code()), "expected " + code + " but got " + failure.code());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface Action {
        void run();
    }
}
