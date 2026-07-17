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
import com.example.soma.access.generated.MutatorAtomicAccessMutator;
import com.example.soma.access.generated.MutatorAtomicAccessTable;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;
import com.hgtech.soma.runtime.SomaRuntimeException;
import com.hgtech.soma.runtime.OperationOutcome;
import com.hgtech.soma.runtime.RemoveResult;
import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.TablePlan;
import com.hgtech.soma.runtime.UpdateResult;
import com.hgtech.soma.runtime.generated.GroupedExactIndex;
import com.hgtech.soma.runtime.generated.HashCompositeKeySpace;

import java.util.List;

public final class AccessConsumer {
    private AccessConsumer() {
    }

    public static void main(String[] args) {
        verifyUniqueBulkScratchBoundaries();
        expectCode("invalid_floating_access_value",
                () -> FloatingAccessTable.create().findByMetric(Float.NaN).count());
        expectCode("invalid_null_value",
                () -> EnumAccessTable.create().findByState(null).count());
        expectCode("invalid_null_value",
                () -> UniquePositionTable.create().findByPositionKey(null).count());

        RuntimePlan accessDefault = AccessRecordTable.defaultRuntimePlan();
        TablePlan tinyStorage = accessDefault.requireTable("AccessRecord")
                .toBuilder().maximumTableStorageBytes(1L).build();
        expectCode("memory_limit_exceeded", () -> AccessRecordTable.create(
                accessDefault.toBuilder().replaceTable(tinyStorage).build()));
        TablePlan invalidStrategy = accessDefault.requireTable("AccessRecord")
                .toBuilder().accessStrategy("none").build();
        expectCode("runtime_plan_mismatch", () -> AccessRecordTable.create(
                accessDefault.toBuilder().replaceTable(invalidStrategy).build()));

        TablePlan boundedPlan = accessDefault.requireTable("AccessRecord")
                .toBuilder().maximumTableStorageBytes(1024L * 1024L).build();
        AccessRecordTable boundedAccess = AccessRecordTable.create(
                accessDefault.toBuilder().replaceTable(boundedPlan).build());
        AccessRecordBatch boundedBatch = new AccessRecordBatch();
        boundedBatch.addValues(1, 1, 1, 1);
        boundedAccess.addBatch(boundedBatch);
        require(boundedAccess.findByState(1).count() == 1L,
                "exact lookup does not need read-time rebuild scratch");
        boundedAccess.release();

        MutatorAtomicAccessBatch atomicBatch = new MutatorAtomicAccessBatch();
        atomicBatch.addValues(1, AccessState.READY);
        MutatorAtomicAccessTable atomicTable = MutatorAtomicAccessTable.create();
        atomicTable.addBatch(atomicBatch);
        MutatorAtomicAccessMutator firstMutation = atomicTable.mutateAt(0);
        MutatorAtomicAccessMutator secondMutation = atomicTable.mutateAt(0);
        firstMutation.setId(2).commit();
        secondMutation.setState(AccessState.RUNNING).commit();
        require(atomicTable.fetchAt(0).id == 2
                        && atomicTable.fetchAt(0).state == AccessState.RUNNING,
                "overlapping mutators do not roll back untouched fields");
        MutatorAtomicAccessMutator firstSameField = atomicTable.mutateAt(0);
        MutatorAtomicAccessMutator secondSameField = atomicTable.mutateAt(0);
        firstSameField.setId(3).commit();
        secondSameField.setId(4).commit();
        require(atomicTable.fetchAt(0).id == 4,
                "same-field overlapping mutators follow commit order");
        atomicTable.mutateAt(0).setId(1).setState(AccessState.READY).commit();
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
        long topOneScratchBefore = table.statsSnapshot().operationScratchCurrentBytes();
        final int[] topOneComparisons = {0};
        AccessRecord stableTopOne = table.rows().sorted((left, right) -> {
            topOneComparisons[0]++;
            return 0;
        }).firstOrThrow();
        require(stableTopOne.code == 10 && topOneComparisons[0] == table.size() - 1,
                "stable arg-min keeps first-on-equal with linear comparisons");
        require(table.statsSnapshot().operationScratchCurrentBytes()
                        == Math.max(16L, topOneScratchBefore),
                "stable top-one does not allocate full sort scratch");
        require(table.findByState(2).count() == 3L, "non-unique index exact source");
        require(table.findByGroup(1).count() == 3L, "repeated index container");
        require(table.findByCode(20).firstOrThrow().score == 10, "unique source");

        List<AccessRecord> groupOne = table.findByGroup(1).sorted((left, right) -> {
            int compared = Integer.compare(right.score(), left.score());
            return compared != 0 ? compared : Integer.compare(left.code(), right.code());
        }).fetchAll();
        require(groupOne.size() == 3, "exact group followed by explicit sort");
        require(groupOne.get(0).score == 30 && groupOne.get(1).score == 20
                && groupOne.get(2).score == 10, "explicit descending order");
        List<AccessRecord> stableEquals = table.findByGroup(2)
                .sorted((left, right) -> {
                    int compared = Integer.compare(right.score(), left.score());
                    return compared != 0 ? compared
                            : Integer.compare(left.code(), right.code());
                }).fetchAll();
        require(stableEquals.get(0).code == 40 && stableEquals.get(1).code == 50,
                "explicit identity tie-break is deterministic");
        require(table.statsSnapshot().exactIndexCount() == 3
                        && table.statsSnapshot().exactIndexEntryCount() == 15L
                        && table.statsSnapshot().exactIndexGroupCount() == 10L,
                "incremental exact index cardinalities are observable");
        require("primitive-exact-hash-v1".equals(
                        table.runtimePlan().requireTable("AccessRecord").accessStrategy()),
                "effective runtime plan declares exact-hash access strategy");
        require(table.statsSnapshot().exactIndexStorageCurrentBytes() > 0L
                        && table.statsSnapshot().exactIndexStorageHighWaterBytes()
                                >= table.statsSnapshot().exactIndexStorageCurrentBytes(),
                "exact-index retained and high-water bytes are observable");
        long storageBeforeCleanRead = table.statsSnapshot()
                .exactIndexStorageCurrentBytes();
        long probesBeforeCleanRead = table.statsSnapshot().exactIndexProbeCount();
        table.findByState(2).count();
        require(table.statsSnapshot().exactIndexStorageCurrentBytes()
                        == storageBeforeCleanRead
                        && table.statsSnapshot().exactIndexProbeCount()
                                > probesBeforeCleanRead,
                "exact lookup probes current index without read-time rebuild");
        table.findByState(2).firstOrThrow();
        require(table.statsSnapshot().lastScanned() == 1L,
                "selector firstOrThrow traverses one exact-index candidate");

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
                "source resolves current exact-index facts at terminal time");
        require(table.findByState(2).count() == 4L,
                "mutation incrementally maintains the exact index");
        require(table.findByGroup(1).sorted((left, right) -> {
            int compared = Integer.compare(right.score(), left.score());
            return compared != 0 ? compared : Integer.compare(left.code(), right.code());
        }).firstOrThrow().code == 20,
                "manual sort observes the published mutation");
        long probesBeforeMutationReads = table.statsSnapshot().exactIndexProbeCount();
        long storageBeforeMutationReads = table.statsSnapshot()
                .exactIndexStorageCurrentBytes();
        table.mutateAt(0).setScore(31).commit();
        table.findByGroup(1).firstOrThrow();
        table.mutateAt(0).setScore(30).commit();
        table.findByGroup(1).firstOrThrow();
        require(table.statsSnapshot().exactIndexProbeCount() > probesBeforeMutationReads
                        && table.statsSnapshot().exactIndexStorageCurrentBytes()
                                == storageBeforeMutationReads,
                "mutation/read alternation remains incremental without rebuild storage");

        AccessRecordTable resultTable = AccessRecordTable.create();
        AccessRecordBatch resultBatch = new AccessRecordBatch();
        resultBatch.addValues(101, 1, 1, 1).addValues(102, 1, 1, 2);
        resultTable.addBatch(resultBatch);
        resultTable.findByState(1).count();
        expectCode("reentrant_access", () -> resultTable.findByState(1).update(row -> {
            row.setScore(99);
            resultTable.resetStats();
        }));
        expectCode("reentrant_access",
                () -> resultTable.forEach(row -> resultTable.size()));
        expectCode("reentrant_access",
                () -> resultTable.scoreValues().forEachInt(value -> resultTable.size()));
        expectCode("reentrant_access", () -> resultTable.sorted((left, right) -> {
            resultTable.runtimePlan();
            return 0;
        }).count());
        AccessRecordTable callbackPeer = AccessRecordTable.create();
        resultTable.forEach(row -> callbackPeer.size());
        require(resultTable.findByCode(101).firstOrThrow().score == 1
                        && resultTable.findByCode(102).firstOrThrow().score == 2,
                "stats reset cannot invalidate operation-local result accounting");
        resultTable.mutateAt(0).setState(2).commit();
        UpdateResult updateResult = resultTable.findByState(2)
                .update(row -> row.setState(row.state()));
        require(updateResult.scanned() == 1L && updateResult.matched() == 1L
                        && updateResult.changed() == 0L,
                "update result reports operation work only");
        resultTable.mutateAt(0).setState(1).commit();
        RemoveResult removeResult = resultTable.findByState(1).limit(1).remove();
        require(removeResult.scanned() == 1L && removeResult.matched() == 1L
                        && removeResult.removed() == 1L,
                "remove result reports operation work only");
        long retainedBeforeClear = resultTable.statsSnapshot()
                .exactIndexStorageCurrentBytes();
        resultTable.clear();
        require(resultTable.statsSnapshot().exactIndexStorageCurrentBytes()
                        == retainedBeforeClear
                        && resultTable.statsSnapshot().exactIndexEntryCount() == 0L,
                "clear retains exact-index arrays while removing entries");
        resultTable.release();
        require(resultTable.statsSnapshot().exactIndexStorageCurrentBytes() == 0L
                        && resultTable.statsSnapshot().exactIndexStorageHighWaterBytes()
                                >= retainedBeforeClear,
                "release drops exact-index arrays but preserves high-water evidence");

        table.findByState(2).filter(row -> row.group() == 2).remove();
        require(table.size() == 4 && table.findByState(2).count() == 3L,
                "swap-remove relocates exact-index row links");

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
        visitTable.reserve(64);
        int reservedKeyCapacity = visitTable.statsSnapshot().keySpaceCapacity();
        long reservedKeyRehashes = visitTable.statsSnapshot().keySpaceRehashCount();
        long reservedExactBytes = visitTable.statsSnapshot()
                .exactIndexStorageCurrentBytes();
        long reservedExactRehashes = visitTable.statsSnapshot()
                .exactIndexRehashCount();
        require(visitTable.capacity() >= 64 && reservedKeyCapacity >= 64
                        && reservedExactBytes > 0L,
                "reserve covers columns, primary locator and exact indexes");
        visitTable.addBatch(visits);
        require(visitTable.statsSnapshot().keySpaceCapacity() == reservedKeyCapacity
                        && visitTable.statsSnapshot().keySpaceRehashCount()
                                == reservedKeyRehashes
                        && visitTable.statsSnapshot().exactIndexStorageCurrentBytes()
                                == reservedExactBytes
                        && visitTable.statsSnapshot().exactIndexRehashCount()
                                == reservedExactRehashes,
                "reserved import does not regrow locator or exact-index arrays");
        require(visitTable.findRowIndex(routeOne.value, 1) >= 0
                        && visitTable.findRowIndex(routeOne.value, 99) == -1,
                "flattened composite key locator avoids key carrier materialization");
        int visitRow = visitTable.rowIndexOf(routeOne.value, 1);
        try (LongColumnView routeIds = visitTable.keyRouteIdValueColumn();
             IntColumnView positions = visitTable.keyPositionValueColumn()) {
            require(routeIds.getLong(visitRow) == routeOne.value
                            && positions.getInt(visitRow) == 1,
                    "flattened value key leaf columns expose packed facts");
        }
        final long[] visitedRouteIds = new long[1];
        visitTable.forEach(row -> visitedRouteIds[0] += row.keyRouteIdValue());
        require(visitedRouteIds[0] == 4L,
                "flattened value leaf row getter avoids reconstructing the key object");
        expectCode("reentrant_access",
                () -> visitTable.keys().forEach(key -> visitTable.size()));
        require(visitTable.findByRoute(routeOne).count() == 2L,
                "nested value grouped index parameter");
        require(visitTable.findByRoute(routeOne).sorted((left, right) ->
                        Integer.compare(left.keyPositionValue(), right.keyPositionValue()))
                        .firstOrThrow().payload == 11,
                "nested value exact source composes with explicit sort");
        require(visitTable.rows().sorted((left, right) -> {
            int compared = Long.compare(
                    left.keyRouteIdValue(), right.keyRouteIdValue());
            return compared != 0 ? compared : Integer.compare(
                    left.keyPositionValue(), right.keyPositionValue());
        }).fetchAll().size() == 3,
                "whole-table explicit order remains available");

        FloatingAccessBatch floatingBatch = new FloatingAccessBatch();
        floatingBatch.addValues(1, -0.0f).addValues(2, 2.5f);
        FloatingAccessTable floating = FloatingAccessTable.create();
        floating.addBatch(floatingBatch);
        require(floating.findByMetric(0.0f).count() == 1L,
                "floating access canonicalizes negative zero");
        require(Float.floatToIntBits(floating.fetchAt(0).metric)
                        == Float.floatToIntBits(0.0f),
                "floating access stores canonical positive zero");
        require(floating.rows().sorted((left, right) ->
                        Float.compare(left.metric(), right.metric()))
                        .firstOrThrow().id == 1,
                "floating explicit order");
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
        require(enumTable.findByState(AccessState.RUNNING)
                        .sorted((left, right) -> Integer.compare(left.rank(), right.rank()))
                        .firstOrThrow().id == 3,
                "enum exact source composes with explicit sort");
        long enumStorageBefore = enumTable.statsSnapshot()
                .exactIndexStorageCurrentBytes();
        enumTable.findByState(AccessState.READY).update(row -> row.setId(20));
        require(enumTable.statsSnapshot().exactIndexStorageCurrentBytes()
                        == enumStorageBefore,
                "unrelated update leaves exact-index storage unchanged");
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
        require(booleanDouble.findByActive(true).sorted((left, right) ->
                        Double.compare(right.metric(), left.metric()))
                        .firstOrThrow().id == 3,
                "boolean exact source composes with double DESC order");
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

    private static void verifyUniqueBulkScratchBoundaries() {
        AccessRecordBatch two = new AccessRecordBatch()
                .addValues(1, 1, 1, 1)
                .addValues(2, 1, 1, 2);
        long appendScratch = HashCompositeKeySpace.estimatedPeakBytes(2);
        AccessRecordTable appendExact = AccessRecordTable.create(
                accessPlanWithBulk(appendScratch));
        appendExact.addBatch(two);
        require(appendExact.size() == 2, "unique append exact bulk limit");
        appendExact.release();

        AccessRecordTable appendBounded = AccessRecordTable.create(
                accessPlanWithBulk(appendScratch - 1L));
        long appendEpoch = appendBounded.structuralEpoch();
        int appendCapacity = appendBounded.capacity();
        expectMemoryLimit(appendScratch - 1L, appendScratch,
                () -> appendBounded.addBatch(two));
        require(appendBounded.size() == 0
                        && appendBounded.structuralEpoch() == appendEpoch
                        && appendBounded.capacity() == appendCapacity,
                "unique append bulk failure preserves facts/capacity/epoch");
        AccessRecordBatch one = new AccessRecordBatch().addValues(3, 1, 1, 3);
        appendBounded.addBatch(one);
        require(appendBounded.size() == 1 && appendBounded.fetchAt(0).code == 3,
                "unique append retry with admissible scratch");
        appendBounded.release();

        long replaceScratch = 3L * GroupedExactIndex.estimatedRetainedBytes(2, 2);
        AccessRecordTable replaceExact = AccessRecordTable.create(
                accessPlanWithBulk(replaceScratch));
        replaceExact.replaceAll(two);
        require(replaceExact.size() == 2, "exact-index replace exact bulk limit");
        replaceExact.release();

        AccessRecordTable replaceBounded = AccessRecordTable.create(
                accessPlanWithBulk(replaceScratch - 1L));
        replaceBounded.addBatch(one);
        long replaceEpoch = replaceBounded.structuralEpoch();
        int replaceCapacity = replaceBounded.capacity();
        expectMemoryLimit(replaceScratch - 1L, replaceScratch,
                () -> replaceBounded.replaceAll(two));
        require(replaceBounded.size() == 1 && replaceBounded.fetchAt(0).code == 3
                        && replaceBounded.structuralEpoch() == replaceEpoch
                        && replaceBounded.capacity() == replaceCapacity,
                "unique replace bulk failure preserves facts/capacity/epoch");
        replaceBounded.replaceAll(new AccessRecordBatch().addValues(4, 1, 1, 4));
        require(replaceBounded.size() == 1 && replaceBounded.fetchAt(0).code == 4,
                "unique replace retry with admissible scratch");
        replaceBounded.release();
    }

    private static RuntimePlan accessPlanWithBulk(long maximumBulkScratchBytes) {
        RuntimePlan base = AccessRecordTable.defaultRuntimePlan();
        TablePlan table = base.requireTable("AccessRecord").toBuilder()
                .maximumBulkScratchBytes(maximumBulkScratchBytes).build();
        return base.toBuilder().replaceTable(table).build();
    }

    private static void expectMemoryLimit(long limit, long proposed, Action action) {
        try {
            action.run();
            throw new AssertionError("expected memory_limit_exceeded");
        } catch (SomaRuntimeException failure) {
            require("memory_limit_exceeded".equals(failure.code()),
                    "bulk limit code " + failure.code());
            require(Long.toString(limit).equals(failure.context().get("limit"))
                            && Long.toString(proposed).equals(
                            failure.context().get("proposed")),
                    "bulk limit/proposed context");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface Action {
        void run();
    }
}
