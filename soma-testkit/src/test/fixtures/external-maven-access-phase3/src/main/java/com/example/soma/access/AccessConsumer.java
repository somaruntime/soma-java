package com.example.soma.access;

import com.example.soma.access.generated.AccessRecordBatch;
import com.example.soma.access.generated.AccessRecordDataFlow;
import com.example.soma.access.generated.AccessRecordScan;
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
import com.hgtech.soma.dataflow.DataFlowContext;
import com.hgtech.soma.dataflow.LongColumnResult;

import java.util.List;

public final class AccessConsumer {
    private AccessConsumer() {
    }

    public static void main(String[] args) {
        verifyDataFlowConsumer();
        verifyUniqueBulkScratchBoundaries();
        verifyUniquePointFamily();
        expectCode("invalid_floating_access_value",
                () -> FloatingAccessTable.create().scanByMetric(Float.NaN).count());
        expectCode("invalid_null_value",
                () -> EnumAccessTable.create().scanByState(null).count());
        expectCode("invalid_null_value",
                () -> UniquePositionTable.create().findIndexByPositionKey(null));

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
        require(boundedAccess.scanByState(1).count() == 1L,
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
            atomicTable.scanById(1).firstOrThrow();
            throw new AssertionError("expected carrier construction failure");
        } catch (IllegalStateException expected) {
            require("carrier construction failed".equals(expected.getMessage()),
                    "carrier RuntimeException propagates unchanged");
        } finally {
            MutatorAtomicAccess.failConstruction = false;
        }
        expectCode("internal_invariant_violation",
                () -> atomicTable.scanById(1).count());
        require(atomicTable.statsSnapshot().rows() == 1L,
                "carrier RuntimeException keeps bounded diagnostics");
        atomicTable.release();
        require(atomicTable.isReleased(),
                "carrier RuntimeException keeps terminal release available");

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
        AccessRecord stableTopOne = table.sorted((left, right) -> {
            topOneComparisons[0]++;
            return 0;
        }).firstOrThrow();
        require(stableTopOne.code == 10 && topOneComparisons[0] == table.size() - 1,
                "stable arg-min keeps first-on-equal with linear comparisons");
        require(table.statsSnapshot().operationScratchCurrentBytes()
                        == Math.max(16L, topOneScratchBefore),
                "stable top-one does not allocate full sort scratch");
        require(table.scanByState(2).count() == 3L, "non-unique index exact source");
        require(table.scanByGroup(1).count() == 3L, "repeated index container");
        require(table.fetchByCode(20).score == 10, "unique point access");
        require(table.scanByCode(20).count() == 1L, "unique scan bridge");

        List<AccessRecord> groupOne = table.scanByGroup(1).sorted((left, right) -> {
            int compared = Integer.compare(right.score(), left.score());
            return compared != 0 ? compared : Integer.compare(left.code(), right.code());
        }).fetchAll();
        require(groupOne.size() == 3, "exact group followed by explicit sort");
        require(groupOne.get(0).score == 30 && groupOne.get(1).score == 20
                && groupOne.get(2).score == 10, "explicit descending order");
        List<AccessRecord> stableEquals = table.scanByGroup(2)
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
        table.scanByState(2).count();
        require(table.statsSnapshot().exactIndexStorageCurrentBytes()
                        == storageBeforeCleanRead
                        && table.statsSnapshot().exactIndexProbeCount()
                                > probesBeforeCleanRead,
                "exact lookup probes current index without read-time rebuild");
        table.scanByState(2).firstOrThrow();
        require(table.statsSnapshot().lastScanned() == 1L,
                "selector firstOrThrow traverses one exact-index candidate");

        final AccessRecordTable errorTable = AccessRecordTable.create();
        errorTable.addBatch(batch);
        try {
            errorTable.scanByState(2).filter(row -> {
                throw new AssertionError("terminal error");
            }).count();
            throw new AssertionError("expected terminal error");
        } catch (AssertionError expected) {
            require("terminal error".equals(expected.getMessage()),
                    "unexpected terminal error");
        }
        expectCode("internal_invariant_violation",
                () -> errorTable.scanByState(2).count());
        require(errorTable.statsSnapshot().rows() == 5L,
                "terminal Error keeps bounded diagnostics");
        errorTable.release();
        expectCode("callback_failed", () -> table.filter(row -> {
            if (row.code() == 20) throw new IllegalArgumentException("predicate failed");
            return false;
        }).count());
        require(table.statsSnapshot().lastScanned() == 2L
                        && table.statsSnapshot().lastMatched() == 0L,
                "failed select records attempted source work");

        AccessRecordScan currentStateOne = table.scanByState(1);
        table.mutateAt(1).setState(2).setScore(50).commit();
        require(currentStateOne.count() == 0L,
                "source resolves current exact-index facts at terminal time");
        require(table.scanByState(2).count() == 4L,
                "mutation incrementally maintains the exact index");
        require(table.scanByGroup(1).sorted((left, right) -> {
            int compared = Integer.compare(right.score(), left.score());
            return compared != 0 ? compared : Integer.compare(left.code(), right.code());
        }).firstOrThrow().code == 20,
                "manual sort observes the published mutation");
        long probesBeforeMutationReads = table.statsSnapshot().exactIndexProbeCount();
        long storageBeforeMutationReads = table.statsSnapshot()
                .exactIndexStorageCurrentBytes();
        table.mutateAt(0).setScore(31).commit();
        table.scanByGroup(1).firstOrThrow();
        table.mutateAt(0).setScore(30).commit();
        table.scanByGroup(1).firstOrThrow();
        require(table.statsSnapshot().exactIndexProbeCount() > probesBeforeMutationReads
                        && table.statsSnapshot().exactIndexStorageCurrentBytes()
                                == storageBeforeMutationReads,
                "mutation/read alternation remains incremental without rebuild storage");

        AccessRecordTable resultTable = AccessRecordTable.create();
        AccessRecordBatch resultBatch = new AccessRecordBatch();
        resultBatch.addValues(101, 1, 1, 1).addValues(102, 1, 1, 2);
        resultTable.addBatch(resultBatch);
        resultTable.scanByState(1).count();
        expectCode("reentrant_access", () -> resultTable.scanByState(1).update(row -> {
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
        require(resultTable.fetchByCode(101).score == 1
                        && resultTable.fetchByCode(102).score == 2,
                "stats reset cannot invalidate operation-local result accounting");
        resultTable.mutateAt(0).setState(2).commit();
        UpdateResult updateResult = resultTable.scanByState(2)
                .update(row -> row.setState(row.state()));
        require(updateResult.scanned() == 1L && updateResult.matched() == 1L
                        && updateResult.changed() == 0L,
                "update result reports operation work only");
        resultTable.mutateAt(0).setState(1).commit();
        RemoveResult removeResult = resultTable.scanByState(1).limit(1).remove();
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

        table.scanByState(2).filter(row -> row.group() == 2).remove();
        require(table.size() == 4 && table.scanByState(2).count() == 3L,
                "swap-remove relocates exact-index row links");

        AccessRecordBatch duplicate = new AccessRecordBatch(1);
        duplicate.addValues(20, 9, 9, 9);
        expectCode("unique_constraint_violation", () -> table.addBatch(duplicate));
        require(table.size() == 4 && table.containsByCode(20),
                "duplicate add is atomic");

        long uniqueStorageBeforePointMutation = table.statsSnapshot()
                .exactIndexStorageCurrentBytes();
        expectCode("unique_constraint_violation",
                () -> table.mutateByCode(30).setCode(20).commit());
        require(table.containsByCode(20) && table.containsByCode(30),
                "duplicate update is atomic");
        require(table.statsSnapshot().exactIndexStorageCurrentBytes()
                        == uniqueStorageBeforePointMutation,
                "failed unique point mutation retains the published index storage");
        AccessRecordBatch invalidReplacement = new AccessRecordBatch();
        invalidReplacement.addValues(7, 1, 1, 1).addValues(7, 2, 2, 2);
        expectCode("unique_constraint_violation",
                () -> table.replaceAll(invalidReplacement));
        require(table.size() == 4 && table.containsByCode(20),
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
                                > reservedExactBytes
                        && visitTable.statsSnapshot().exactIndexRehashCount()
                                > reservedExactRehashes
                        && visitTable.statsSnapshot().exactIndexEntryCount() == 3L
                        && visitTable.statsSnapshot().exactIndexGroupCount() == 2L,
                "reserve covers row links while append admits actual exact groups");
        require(visitTable.findIndex(routeOne.value, 1) >= 0
                        && visitTable.findIndex(routeOne.value, 99) == -1,
                "flattened composite key locator avoids key carrier materialization");
        int visitRow = visitTable.requireIndex(routeOne.value, 1);
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
        require(visitTable.scanByRoute(routeOne).count() == 2L,
                "nested value grouped index parameter");
        require(visitTable.scanByRoute(routeOne).sorted((left, right) ->
                        Integer.compare(left.keyPositionValue(), right.keyPositionValue()))
                        .firstOrThrow().payload == 11,
                "nested value exact source composes with explicit sort");
        require(visitTable.sorted((left, right) -> {
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
        require(floating.scanByMetric(0.0f).count() == 1L,
                "floating access canonicalizes negative zero");
        require(Float.floatToIntBits(floating.fetchAt(0).metric)
                        == Float.floatToIntBits(0.0f),
                "floating access stores canonical positive zero");
        require(floating.sorted((left, right) ->
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
                () -> floating.scanByMetric(Float.POSITIVE_INFINITY).count());
        expectCode("invalid_floating_access_value",
                () -> floating.mutateAt(0).setMetric(Float.NaN).commit());
        require(floating.statsSnapshot().lastOutcome() == OperationOutcome.FAILED
                        && "invalid_floating_access_value".equals(
                                floating.statsSnapshot().lastErrorCode()),
                "mutator validation failure closes operation stats");
        require(floating.scanByMetric(0.0f).count() == 1L,
                "invalid floating mutator is atomic");
        expectCode("invalid_floating_access_value", () -> floating.scanByMetric(2.5f)
                .update(row -> row.setMetric(Float.NEGATIVE_INFINITY)));
        require(floating.scanByMetric(2.5f).count() == 1L,
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
        require(enumTable.scanByState(AccessState.RUNNING).count() == 2L,
                "enum index keeps typed source parameter");
        require(enumTable.scanByState(AccessState.RUNNING)
                        .sorted((left, right) -> Integer.compare(left.rank(), right.rank()))
                        .firstOrThrow().id == 3,
                "enum exact source composes with explicit sort");
        long enumStorageBefore = enumTable.statsSnapshot()
                .exactIndexStorageCurrentBytes();
        enumTable.scanByState(AccessState.READY).update(row -> row.setId(20));
        require(enumTable.statsSnapshot().exactIndexStorageCurrentBytes()
                        == enumStorageBefore,
                "unrelated update leaves exact-index storage unchanged");
        enumTable.scanByState(AccessState.RUNNING).update(row -> {
            if (row.id() == 1) row.setState(AccessState.READY);
        });
        require(enumTable.scanByState(AccessState.READY).count() == 2L,
                "enum update scratch remains primitive and publishes typed state");
        expectCode("invalid_null_value", () -> enumTable.scanByState(null).count());

        BooleanDoubleAccessBatch booleanDoubleBatch = new BooleanDoubleAccessBatch();
        booleanDoubleBatch.addValues(1, true, 2.0d)
                .addValues(2, false, 1.0d)
                .addValues(3, true, 3.0d);
        BooleanDoubleAccessTable booleanDouble = BooleanDoubleAccessTable.create();
        booleanDouble.addBatch(booleanDoubleBatch);
        require(booleanDouble.scanByActive(true).count() == 2L,
                "boolean index uses primitive selector binding");
        require(booleanDouble.fetchByMetric(2.0d).id == 1,
                "double unique uses primitive selector binding");
        require(booleanDouble.scanByActive(true).sorted((left, right) ->
                        Double.compare(right.metric(), left.metric()))
                        .firstOrThrow().id == 3,
                "boolean exact source composes with double DESC order");
        expectCode("invalid_floating_access_value",
                () -> booleanDouble.findIndexByMetric(Double.NaN));

        RoutePositionKey firstPosition = new RoutePositionKey(routeOne, 1);
        RoutePositionKey secondPosition = new RoutePositionKey(routeOne, 2);
        UniquePositionBatch positions = new UniquePositionBatch();
        positions.addValues(1, firstPosition).addValues(2, secondPosition);
        UniquePositionTable positionTable = UniquePositionTable.create();
        positionTable.addBatch(positions);
        require(positionTable.fetchByPositionKey(firstPosition).id == 1,
                "composite value grouped unique parameter");
        expectCode("unique_constraint_violation",
                () -> positionTable.mutate(2).setPositionKey(firstPosition).commit());
        require(positionTable.containsByPositionKey(secondPosition),
                "composite unique mutator failure is atomic");
        expectCode("unique_constraint_violation",
                () -> positionTable.mutateByPositionKey(secondPosition)
                        .setPositionKey(firstPosition).commit());
        require(positionTable.containsByPositionKey(secondPosition),
                "composite unique point mutation failure is atomic");

        AccessOracleCheck.run();
    }

    private static void verifyDataFlowConsumer() {
        AccessRecordTable table = AccessRecordTable.create();
        DataFlowContext context = DataFlowContext.sequential();
        try {
            table.addBatch(new AccessRecordBatch(4)
                    .addValues(10, 2, 1, 30)
                    .addValues(20, 1, 1, 10)
                    .addValues(30, 2, 1, 20)
                    .addValues(40, 2, 2, 20));
            AccessRecordDataFlow.Source source =
                    AccessRecordDataFlow.source("records");
            LongColumnResult result = source.candidates()
                    .filter(source.columns().state().equalTo(2L))
                    .sortedBy(source.columns().score().descending()
                            .then(source.columns().code().ascending()))
                    .limit(2L)
                    .project(source.columns().code())
                    .toColumn()
                    .compile()
                    .newInvocation(context)
                    .bind(source, AccessRecordDataFlow.bind(table))
                    .execute();
            require(result.size() == 2
                            && result.valueAt(0) == 10L
                            && result.valueAt(1) == 30L,
                    "external generated DataFlow consumer");
        } finally {
            context.close();
            table.release();
        }
    }

    private static void verifyUniquePointFamily() {
        AccessRecordTable table = AccessRecordTable.create();
        try {
            table.addBatch(new AccessRecordBatch(2)
                    .addValues(10, 1, 1, 100)
                    .addValues(20, 2, 2, 200));
            require(table.containsByCode(10) && !table.containsByCode(99),
                    "unique contains hit/miss");
            int index = table.findIndexByCode(10);
            require(index >= 0 && table.requireIndexByCode(10) == index
                            && table.findIndexByCode(99) == -1,
                    "unique scalar Index hit/miss");
            require(table.findByCode(10).isPresent()
                            && !table.findByCode(99).isPresent()
                            && table.fetchByCode(10).score == 100,
                    "unique materialization hit/miss");
            expectCode("empty_result", () -> table.requireIndexByCode(99));
            expectCode("empty_result", () -> table.fetchByCode(99));

            table.mutateByCode(10).setCode(11).setScore(101).commit();
            require(!table.containsByCode(10)
                            && table.fetchByCode(11).score == 101
                            && table.scanByCode(11).count() == 1L,
                    "unique point mutation and Scan bridge");
            expectCode("unique_constraint_violation",
                    () -> table.mutateByCode(11).setCode(20).commit());
            require(table.containsByCode(11) && table.containsByCode(20),
                    "unique conflict is atomic");

            table.deleteByCode(11);
            require(!table.containsByCode(11) && table.size() == 1,
                    "unique point delete");
            expectCode("empty_result", () -> table.deleteByCode(11));
        } finally {
            table.release();
        }
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
