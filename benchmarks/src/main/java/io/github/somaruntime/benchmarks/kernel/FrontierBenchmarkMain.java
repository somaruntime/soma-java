package io.github.somaruntime.benchmarks.kernel;

import io.github.somaruntime.benchmarks.BenchmarkResult;
import io.github.somaruntime.benchmarks.BenchmarkSupport;
import io.github.somaruntime.benchmarks.LongMeasurement;
import io.github.somaruntime.benchmarks.MemoryObserver;
import io.github.somaruntime.benchmarks.kernel.domain.KernelPayload;
import io.github.somaruntime.benchmarks.kernel.schema.KernelStatus;
import io.github.somaruntime.soma.ByteGroupedLongResult;
import io.github.somaruntime.soma.GroupedLongResult;
import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.UpdateResult;
import java.util.Comparator;
import java.util.concurrent.ForkJoinPool;

/**
 * Broad performance-frontier workload over Table, Field, IndexSelection and
 * the principal derived query shapes. Every timed operation validates a
 * deterministic result before its timing is published.
 */
public final class FrontierBenchmarkMain {
    private static final Comparator<String> NULLABLE_STRING_ORDER =
            Comparator.nullsFirst(String::compareTo);

    private FrontierBenchmarkMain() {}

    public static void main(String[] args) {
        int rows = BenchmarkSupport.rows(args);
        String implementation = BenchmarkSupport.implementation(args);
        String scenario = System.getProperty("soma.benchmark.scenario", "frontier-source");
        BenchmarkSupport.require("frontier-source".equals(scenario)
                        || "frontier-stateful".equals(scenario)
                        || "frontier-relation".equals(scenario)
                        || "frontier-mutation".equals(scenario),
                "unknown frontier scenario");
        if ("manual".equals(implementation) || "java-stream".equals(implementation)) {
            FrontierBaselines.run(rows, implementation, scenario);
        } else {
            runSoma(rows, implementation, scenario);
        }
    }

    private static void runSoma(int rows, String implementation, String scenario) {
        ForkJoinPool executor = new ForkJoinPool(BenchmarkSupport.parallelism());
        try {
            long budget = Long.getLong("soma.benchmark.memoryBudgetBytes", 6L << 30);
            SomaCompression compression = "soma-off".equals(implementation)
                    ? SomaCompression.OFF : SomaCompression.AUTO;
            Soma.configure(SomaConfiguration.builder()
                    .memoryBudgetBytes(budget)
                    .parallelExecutor(executor)
                    .compression(compression)
                    .build());

            SomaGroup group = Soma.createGroup();
            KernelRecordTable records = group.kernelRecordTable();
            RouteWeightTable weights = group.routeWeightTable();
            BenchmarkSupport.observeMemory(new MemoryObserver() {
                @Override public long retainedBytes() {
                    return group._metadata().globalRetainedBytes();
                }

                @Override public long temporaryBytes() {
                    return group._metadata().globalTemporaryBytes();
                }
            });

            FrontierData expected = FrontierData.create(rows, false);
            RouteKey[] routes = routes(expected.routeCount);
            ingestWeights(weights, routes);
            LongMeasurement ingest = ingestRecords(records, expected, routes);
            BenchmarkSupport.require(ingest.value(), rows, "frontier ingest size");

            BenchmarkResult result = new BenchmarkResult(
                    scenario, "FRONTIER_MATRIX", implementation, rows)
                    .put("correctness", true)
                    .put("compression", compression.name())
                    .put("ingest", ingest)
                    .put("routeRows", weights.size())
                    .put("retainedBytes", group._metadata().retainedBytes());

            if ("frontier-source".equals(scenario)) {
                runSources(records, expected, result);
            } else if ("frontier-stateful".equals(scenario)) {
                runStateful(records, expected, result);
            } else if ("frontier-relation".equals(scenario)) {
                runRelations(records, weights, expected, result);
            } else {
                runMutations(records, expected, result);
            }
            result.print();
        } finally {
            executor.shutdown();
        }
    }

    private static LongMeasurement ingestRecords(
            KernelRecordTable records,
            FrontierData expected,
            RouteKey[] routes) {
        records.reserve(expected.rows);
        KernelStatus[] statuses = KernelStatus.values();
        KernelPayload[] payloads = payloads(256);
        KernelRecord input = new KernelRecord();
        return BenchmarkSupport.measureOnce(() -> {
            for (int index = 0; index < expected.rows; index++) {
                input.recordId(index + 1L);
                input.route(routes[expected.routeIndex(index)]);
                input.tenant(expected.tenant(index));
                input.status(index % 53 == 0 ? null : statuses[index & 3]);
                input.code(expected.code(index));
                input.laneNumber((short) (index % 1_024));
                input.category((char) ('A' + index % 26));
                input.quantity(expected.quantity(index));
                input.amount(expected.amount(index));
                input.ratio((float) (index % 32 - 16));
                input.score((index % 128 - 64) * 0.5d);
                input.label(expected.label(index));
                input.payload(index % 101 == 0 ? null : payloads[index % payloads.length]);
                records.add(input);
            }
            return records.size();
        });
    }

    private static void ingestWeights(RouteWeightTable weights, RouteKey[] routes) {
        weights.reserve((long) routes.length * 2L);
        for (int index = 0; index < routes.length; index++) {
            long id = index * 2L + 1L;
            weights.add(new RouteWeight(id, routes[index], 1L, true));
            weights.add(new RouteWeight(id + 1L, routes[index], 2L, (index & 7) != 0));
        }
    }

    private static void runSources(
            KernelRecordTable records,
            FrontierData expected,
            BenchmarkResult result) {
        LongMeasurement tableCount = BenchmarkSupport.measure(records::count);
        require(tableCount, expected.rows, "Table count");

        LongMeasurement typedFilter = BenchmarkSupport.measure(() -> records
                .filter(records.quantity.ge(500))
                .mapToLong(records.amount)
                .sum());
        require(typedFilter, expected.tableDenseSum, "Table typed filter");

        LongMeasurement callbackFilter = BenchmarkSupport.measure(() -> records
                .filter(view -> view.quantity() >= 500)
                .mapToLong(view -> view.amount())
                .sum());
        require(callbackFilter, expected.tableDenseSum, "Table callback filter");

        LongMeasurement parallelTypedFilter = BenchmarkSupport.measure(() -> records
                .parallel()
                .filter(records.quantity.ge(500))
                .mapToLong(records.amount)
                .sum());
        require(parallelTypedFilter, expected.tableDenseSum, "parallel Table typed filter");

        LongMeasurement parallelCallbackFilter = BenchmarkSupport.measure(() -> records
                .parallel()
                .filter(view -> view.quantity() >= 500)
                .mapToLong(view -> view.amount())
                .sum());
        require(parallelCallbackFilter, expected.tableDenseSum,
                "parallel Table callback filter");

        LongMeasurement fieldSum = BenchmarkSupport.measure(records.amount::sum);
        require(fieldSum, expected.amountSum, "Field sum");

        LongMeasurement fieldFiltered = BenchmarkSupport.measure(() -> records.amount
                .filter(value -> (value & 1L) == 0L)
                .sum());
        require(fieldFiltered, expected.evenAmountSum, "Field filter");

        LongMeasurement parallelFieldSum = BenchmarkSupport.measure(() ->
                records.amount.parallel().sum());
        require(parallelFieldSum, expected.amountSum, "parallel Field sum");

        LongMeasurement keyLookup = BenchmarkSupport.measure(() -> {
            long sum = 0L;
            int probes = Math.min(10_000, expected.rows);
            for (int probe = 0; probe < probes; probe++) {
                int index = (int) ((probe * 7_919L) % expected.rows);
                sum += records.get(index + 1L).amount();
            }
            return sum;
        });
        require(keyLookup, expected.keyLookupSum, "Key point lookup");

        LongMeasurement indexCount = BenchmarkSupport.measure(() ->
                records.byTenant(FrontierData.PROBE_TENANT).count());
        require(indexCount, expected.tenantProbeCount, "IndexSelection count");

        LongMeasurement indexResidual = BenchmarkSupport.measure(() -> records
                .byTenant(FrontierData.PROBE_TENANT)
                .filter(records.quantity.ge(500))
                .mapToLong(records.amount)
                .sum());
        require(indexResidual, expected.tenantProbeDenseSum, "IndexSelection residual");

        LongMeasurement mappedPrimitive = BenchmarkSupport.measure(() -> records
                .mapToLong(view -> view.amount() + view.quantity())
                .sum());
        require(mappedPrimitive, expected.mappedPrimitiveSum, "mapped primitive");

        LongMeasurement parallelMappedPrimitive = BenchmarkSupport.measure(() -> records
                .parallel()
                .mapToLong(view -> view.amount() + view.quantity())
                .sum());
        require(parallelMappedPrimitive, expected.mappedPrimitiveSum,
                "parallel mapped primitive");

        LongMeasurement fieldMaterialize = BenchmarkSupport.measure(() ->
                FrontierData.sampledLongs(records.amount.toArray()));
        require(fieldMaterialize, expected.materializedAmountFingerprint,
                "Field materialization");

        LongMeasurement mappedReference = BenchmarkSupport.measure(() ->
                FrontierData.hashStrings(records
                        .map(view -> view.label())
                        .distinct()
                        .sorted(NULLABLE_STRING_ORDER)
                        .limit(64)
                        .toArray(String.class)));
        require(mappedReference, expected.mappedReferenceFingerprint,
                "mapped reference pipeline");

        long fingerprint = FrontierData.hash(
                tableCount.value(), typedFilter.value(), callbackFilter.value(),
                fieldSum.value(), fieldFiltered.value(), keyLookup.value(),
                indexCount.value(), indexResidual.value(), mappedPrimitive.value(),
                fieldMaterialize.value(), mappedReference.value());
        result.put("tableCount", tableCount)
                .put("tableTypedFilter", typedFilter)
                .put("tableCallbackFilter", callbackFilter)
                .put("parallelTableTypedFilter", parallelTypedFilter)
                .put("parallelTableCallbackFilter", parallelCallbackFilter)
                .put("fieldSum", fieldSum)
                .put("fieldFiltered", fieldFiltered)
                .put("parallelFieldSum", parallelFieldSum)
                .put("keyLookup10k", keyLookup)
                .put("indexCount", indexCount)
                .put("indexResidual", indexResidual)
                .put("mappedPrimitive", mappedPrimitive)
                .put("parallelMappedPrimitive", parallelMappedPrimitive)
                .put("fieldMaterialize", fieldMaterialize)
                .put("mappedReference", mappedReference)
                .put("sharedFingerprint", fingerprint)
                .put("fingerprint", fingerprint);
    }

    private static void runStateful(
            KernelRecordTable records,
            FrontierData expected,
            BenchmarkResult result) {
        LongMeasurement fieldDistinct = BenchmarkSupport.measure(() ->
                FrontierData.hashLongs(records.amount.distinct().sorted().toArray()));
        require(fieldDistinct, expected.distinctFingerprint, "Field distinct/sort");

        LongMeasurement fieldSortLimit = BenchmarkSupport.measure(() ->
                FrontierData.hashLongs(records.amount.sorted()
                        .limit(FrontierData.RESULT_LIMIT).toArray()));
        require(fieldSortLimit, expected.fieldTopFingerprint, "Field sort/limit");

        LongMeasurement fieldTop = BenchmarkSupport.measure(() ->
                FrontierData.hashLongs(records.amount.top(FrontierData.RESULT_LIMIT).toArray()));
        require(fieldTop, expected.fieldTopFingerprint, "Field top");

        LongMeasurement parallelFieldTop = BenchmarkSupport.measure(() ->
                FrontierData.hashLongs(records.amount.parallel()
                        .top(FrontierData.RESULT_LIMIT).toArray()));
        require(parallelFieldTop, expected.fieldTopFingerprint, "parallel Field top");

        LongMeasurement tableTop = BenchmarkSupport.measure(() ->
                FrontierData.hashLongs(records
                        .top(FrontierData.RESULT_LIMIT,
                                records.quantity.desc().then(records.recordId.asc()))
                        .mapToLong(records.recordId)
                        .toArray()));
        require(tableTop, expected.tableTopFingerprint, "Table top");

        LongMeasurement parallelTableTop = BenchmarkSupport.measure(() ->
                FrontierData.hashLongs(records.parallel()
                        .top(FrontierData.RESULT_LIMIT,
                                records.quantity.desc().then(records.recordId.asc()))
                        .mapToLong(records.recordId)
                        .toArray()));
        require(parallelTableTop, expected.tableTopFingerprint, "parallel Table top");

        LongMeasurement tableSlice = BenchmarkSupport.measure(() ->
                FrontierData.hashLongs(records
                        .skip(expected.rows / 2L)
                        .limit(FrontierData.RESULT_LIMIT)
                        .mapToLong(records.recordId)
                        .toArray()));
        require(tableSlice, expected.skipLimitFingerprint, "Table skip/limit");

        LongMeasurement referenceDistinct = BenchmarkSupport.measure(() ->
                FrontierData.hashStrings(records.label.distinct()
                        .sorted(NULLABLE_STRING_ORDER)
                        .limit(64)
                        .toArray()));
        require(referenceDistinct, expected.mappedReferenceFingerprint,
                "reference Field distinct/sort");

        LongMeasurement parallelReferenceDistinct = BenchmarkSupport.measure(() ->
                FrontierData.hashStrings(records.label.parallel()
                        .distinct()
                        .sorted(NULLABLE_STRING_ORDER)
                        .limit(64)
                        .toArray()));
        require(parallelReferenceDistinct, expected.mappedReferenceFingerprint,
                "parallel reference Field distinct/sort");

        long fingerprint = FrontierData.hash(
                fieldDistinct.value(), fieldSortLimit.value(), fieldTop.value(),
                tableTop.value(), tableSlice.value(), referenceDistinct.value());
        result.put("fieldDistinct", fieldDistinct)
                .put("fieldSortLimit", fieldSortLimit)
                .put("fieldTop", fieldTop)
                .put("parallelFieldTop", parallelFieldTop)
                .put("tableTop", tableTop)
                .put("parallelTableTop", parallelTableTop)
                .put("tableSlice", tableSlice)
                .put("referenceDistinct", referenceDistinct)
                .put("parallelReferenceDistinct", parallelReferenceDistinct)
                .put("sharedFingerprint", fingerprint)
                .put("fingerprint", fingerprint);
    }

    private static void runRelations(
            KernelRecordTable records,
            RouteWeightTable weights,
            FrontierData expected,
            BenchmarkResult result) {
        LongMeasurement lowGroup = BenchmarkSupport.measure(() ->
                byteGroupFingerprint(records.groupBy(records.code).sum(records.amount)));
        require(lowGroup, expected.lowGroupFingerprint, "low-cardinality GroupBy");

        LongMeasurement parallelLowGroup = BenchmarkSupport.measure(() ->
                byteGroupFingerprint(records.parallel()
                        .groupBy(records.code).sum(records.amount)));
        require(parallelLowGroup, expected.lowGroupFingerprint,
                "parallel low-cardinality GroupBy");

        LongMeasurement highGroup = BenchmarkSupport.measure(() ->
                stringGroupFingerprint(records.groupBy(records.label).sum(records.amount)));
        require(highGroup, expected.highGroupFingerprint, "high-cardinality GroupBy");

        LongMeasurement parallelHighGroup = BenchmarkSupport.measure(() ->
                stringGroupFingerprint(records.parallel()
                        .groupBy(records.label).sum(records.amount)));
        require(parallelHighGroup, expected.highGroupFingerprint,
                "parallel high-cardinality GroupBy");

        LongMeasurement joinCount = BenchmarkSupport.measure(() -> records.join(weights)
                .on(records.route, weights.route)
                .count());
        require(joinCount, expected.rows * 2L, "Equality Join count");

        LongMeasurement joinFiltered = BenchmarkSupport.measure(() -> records.join(weights)
                .on(records.route, weights.route)
                .filter(weights.enabled.eq(true))
                .mapToLong(pair -> pair.left().amount() + pair.right().weight())
                .sum());
        require(joinFiltered, expected.enabledJoinSum, "Equality Join typed filter");

        LongMeasurement parallelJoinFiltered = BenchmarkSupport.measure(() -> records.join(weights)
                .on(records.route, weights.route)
                .inner()
                .parallel()
                .filter(weights.enabled.eq(true))
                .mapToLong(pair -> pair.left().amount() + pair.right().weight())
                .sum());
        require(parallelJoinFiltered, expected.enabledJoinSum,
                "parallel Equality Join typed filter");

        LongMeasurement semi = BenchmarkSupport.measure(() -> records.join(weights)
                .on(records.route, weights.route)
                .semi()
                .count());
        require(semi, expected.rows, "Semi Join count");

        LongMeasurement anti = BenchmarkSupport.measure(() -> records.join(weights)
                .on(records.route, weights.route)
                .anti()
                .count());
        require(anti, 0L, "Anti Join count");

        long fingerprint = FrontierData.hash(
                lowGroup.value(), highGroup.value(), joinCount.value(),
                joinFiltered.value(), semi.value(), anti.value());
        result.put("lowGroup", lowGroup)
                .put("parallelLowGroup", parallelLowGroup)
                .put("highGroup", highGroup)
                .put("parallelHighGroup", parallelHighGroup)
                .put("joinCount", joinCount)
                .put("joinFiltered", joinFiltered)
                .put("parallelJoinFiltered", parallelJoinFiltered)
                .put("semiJoin", semi)
                .put("antiJoin", anti)
                .put("sharedFingerprint", fingerprint)
                .put("fingerprint", fingerprint);
    }

    private static void runMutations(
            KernelRecordTable records,
            FrontierData expected,
            BenchmarkResult result) {
        int pointOperations = Math.min(10_000, expected.rows);
        LongMeasurement pointUpdate = BenchmarkSupport.measureOnce(() -> {
            long matched = 0L;
            long changed = 0L;
            for (int probe = 0; probe < pointOperations; probe++) {
                UpdateResult update = records.update(probe + 1L,
                        editor -> editor.amount(editor.amount() + 1L));
                matched += update.matched();
                changed += update.changed();
            }
            return FrontierData.hash(matched, changed);
        });
        require(pointUpdate, FrontierData.hash(pointOperations, pointOperations),
                "point update batch");

        long updateCount = countMatching(expected.rows, 990, 10);
        LongMeasurement selectionUpdate = BenchmarkSupport.measureOnce(() -> {
            UpdateResult update = records
                    .filter(records.quantity.ge(990))
                    .update(editor -> editor.amount(editor.amount() + 1L));
            return FrontierData.hash(update.matched(), update.changed());
        });
        require(selectionUpdate, FrontierData.hash(updateCount, updateCount),
                "selection update");

        long removeCount = countMatching(expected.rows, 0, 10);
        LongMeasurement selectionRemove = BenchmarkSupport.measureOnce(() -> {
            RemoveResult remove = records
                    .filter(records.quantity.lt(10))
                    .remove();
            return remove.removed();
        });
        require(selectionRemove, removeCount, "selection remove");

        LongMeasurement postState = BenchmarkSupport.measure(() -> FrontierData.hash(
                records.count(), records.byTenant(FrontierData.PROBE_TENANT).count()));
        long expectedPostTenant = expected.tenantProbeCount
                - removedTenantProbeCount(expected.rows);
        long expectedPostState = FrontierData.hash(
                expected.rows - removeCount, expectedPostTenant);
        require(postState, expectedPostState, "post-mutation Table/Index state");

        long fingerprint = FrontierData.hash(
                pointUpdate.value(), selectionUpdate.value(),
                selectionRemove.value(), postState.value());
        result.put("pointUpdate10k", pointUpdate)
                .put("selectionUpdate", selectionUpdate)
                .put("selectionRemove", selectionRemove)
                .put("postMutationState", postState)
                .put("sharedFingerprint", fingerprint)
                .put("fingerprint", fingerprint);
    }

    private static long countMatching(int rows, int firstRemainder, int remainders) {
        long count = 0L;
        for (int index = firstRemainder; index < rows; index += 1_000) {
            count += Math.min(remainders, rows - index);
        }
        return count;
    }

    private static long removedTenantProbeCount(int rows) {
        long count = 0L;
        for (int index = 0; index < rows; index++) {
            if (index % 1_000 < 10
                    && FrontierData.PROBE_TENANT.equals(
                            index % 97 == 0 ? null : "TENANT-" + index % 64)) {
                count++;
            }
        }
        return count;
    }

    private static long byteGroupFingerprint(ByteGroupedLongResult grouped) {
        final long[] hash = {BenchmarkSupport.mix(FrontierData.HASH_SEED, grouped.size())};
        grouped.forEach((key, value) -> {
            hash[0] = BenchmarkSupport.mix(hash[0], key);
            hash[0] = BenchmarkSupport.mix(hash[0], value);
        });
        return hash[0];
    }

    private static long stringGroupFingerprint(GroupedLongResult<String> grouped) {
        final long[] hash = {BenchmarkSupport.mix(FrontierData.HASH_SEED, grouped.size())};
        grouped.forEach((key, value) -> {
            hash[0] = FrontierData.mixNullable(hash[0], key);
            hash[0] = BenchmarkSupport.mix(hash[0], value);
        });
        return hash[0];
    }

    private static RouteKey[] routes(int count) {
        RouteKey[] result = new RouteKey[count];
        String[] lanes = strings("LANE-", 32);
        for (int index = 0; index < count; index++) {
            result[index] = new RouteKey(
                    index / 1_024L,
                    index % 1_024L,
                    index % 17 == 0 ? null : lanes[index % lanes.length]);
        }
        return result;
    }

    private static String[] strings(String prefix, int count) {
        String[] result = new String[count];
        for (int index = 0; index < count; index++) result[index] = prefix + index;
        return result;
    }

    private static KernelPayload[] payloads(int count) {
        KernelPayload[] result = new KernelPayload[count];
        for (int index = 0; index < count; index++) result[index] = new KernelPayload(index);
        return result;
    }

    private static void require(LongMeasurement measurement, long expected, String subject) {
        BenchmarkSupport.require(measurement.value(), expected, subject);
    }
}
