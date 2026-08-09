package io.github.somaruntime.benchmarks.kernel;

import io.github.somaruntime.benchmarks.BenchmarkResult;
import io.github.somaruntime.benchmarks.BenchmarkSupport;
import io.github.somaruntime.benchmarks.LongMeasurement;
import io.github.somaruntime.benchmarks.kernel.domain.KernelPayload;
import io.github.somaruntime.benchmarks.kernel.schema.KernelStatus;
import io.github.somaruntime.soma.GroupedDoubleResult;
import io.github.somaruntime.soma.GroupedLongResult;
import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

/** Synthetic physical-kernel qualification without introducing a fourth Example. */
public final class TypeKernelBenchmarkMain {
    private static final long HASH_SEED = 0xcbf29ce484222325L;

    private TypeKernelBenchmarkMain() {}

    public static void main(String[] args) {
        int rows = BenchmarkSupport.rows(args);
        String implementation = BenchmarkSupport.implementation(args);
        BenchmarkSupport.require(!"manual".equals(implementation),
                "type-kernel benchmark has no manual implementation");
        runSoma(rows, implementation);
    }

    private static void runSoma(int rows, String implementation) {
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
            records.reserve(rows);

            int routeCount = Math.min(262_144, Math.max(1_024, rows / 4));
            RouteKey[] routes = routes(routeCount);
            String[] tenants = strings("TENANT-", 64);
            String[] labels = strings("LABEL-", 8_192);
            KernelPayload[] payloads = payloads(256);
            KernelStatus[] statuses = KernelStatus.values();
            weights.reserve((long) routeCount * 2L);

            for (int index = 0; index < routeCount; index++) {
                long id = index * 2L + 1L;
                weights.add(new RouteWeight(id, routes[index], 1L, true));
                weights.add(new RouteWeight(id + 1L, routes[index], 2L, (index & 7) != 0));
            }

            Expected expected = new Expected(rows, routes);
            long ingestStarted = System.nanoTime();
            for (int index = 0; index < rows; index++) {
                int routeIndex = index % routeCount;
                String tenant = index % 97 == 0 ? null : tenants[index % tenants.length];
                KernelStatus status = index % 53 == 0 ? null : statuses[index & 3];
                byte code = (byte) (index % 16 - 8);
                short laneNumber = (short) (index % 1_024);
                char category = (char) ('A' + index % 26);
                int quantity = index % 1_000;
                long amount = 1L + index % 100;
                float ratio = (float) (index % 32 - 16);
                double score = (index % 128 - 64) * 0.5d;
                String label = index % 211 == 0 ? null : labels[index % labels.length];
                KernelPayload payload = index % 101 == 0
                        ? null : payloads[index % payloads.length];
                records.add(new KernelRecord(
                        index + 1L,
                        routes[routeIndex],
                        tenant,
                        status,
                        code,
                        laneNumber,
                        category,
                        quantity,
                        amount,
                        ratio,
                        score,
                        label,
                        payload));
                expected.accept(
                        routeIndex, tenant, status, code, laneNumber, category,
                        quantity, amount, ratio, score, label, payload);
            }
            long ingestNanos = System.nanoTime() - ingestStarted;
            expected.finish();
            BenchmarkSupport.require(records.size(), rows, "kernel ingest size");

            LongMeasurement integral = BenchmarkSupport.measure(() ->
                    BenchmarkSupport.fingerprint(
                            records.code.mapToLong(value -> value).sum(),
                            records.laneNumber.mapToLong(value -> value).sum(),
                            records.category.mapToLong(value -> value).sum(),
                            records.quantity.mapToLong(value -> value).sum(),
                            records.amount.sum()));
            BenchmarkSupport.require(integral.value(), expected.integralFingerprint,
                    "narrow integral kernels");

            LongMeasurement floating = BenchmarkSupport.measure(() -> floatingFingerprint(
                    records.ratio.sum(),
                    records.ratio.average().getAsDouble(),
                    records.score.sum(),
                    records.score.average().getAsDouble()));
            BenchmarkSupport.require(floating.value(), expected.floatingFingerprint,
                    "floating reduction kernels");

            LongMeasurement parallelFloating = BenchmarkSupport.measure(() -> floatingFingerprint(
                    records.ratio.parallel().sum(),
                    records.ratio.parallel().average().getAsDouble(),
                    records.score.parallel().sum(),
                    records.score.parallel().average().getAsDouble()));
            BenchmarkSupport.require(parallelFloating.value(), expected.floatingFingerprint,
                    "parallel floating reduction kernels");

            LongMeasurement indexes = BenchmarkSupport.measure(() ->
                    BenchmarkSupport.fingerprint(
                            records.byRoute(routes[7]).count(),
                            records.byTenant("TENANT-7").count(),
                            records.byStatus(KernelStatus.READY).count(),
                            records.filter(records.tenant.isNull()).count(),
                            records.filter(records.status.isNull()).count()));
            BenchmarkSupport.require(indexes.value(), expected.indexFingerprint,
                    "Value/String/Enum Index and null bucket");

            Comparator<String> nullableStringOrder = Comparator.nullsFirst(String::compareTo);
            Comparator<KernelStatus> nullableEnumOrder = Comparator.nullsFirst(
                    new Comparator<KernelStatus>() {
                        @Override
                        public int compare(KernelStatus left, KernelStatus right) {
                            return left.compareTo(right);
                        }
                    });
            LongMeasurement references = BenchmarkSupport.measure(() -> {
                String[] distinctTenants = records.tenant.distinct()
                        .sorted(nullableStringOrder).toArray();
                KernelStatus[] distinctStatuses = records.status.distinct()
                        .sorted(nullableEnumOrder).toArray();
                return referenceFingerprint(distinctTenants, distinctStatuses);
            });
            BenchmarkSupport.require(references.value(), expected.referenceFingerprint,
                    "nullable String/Enum distinct and order");

            LongMeasurement objects = BenchmarkSupport.measure(() ->
                    payloadFingerprint(records.payload.toArray()));
            BenchmarkSupport.require(objects.value(), expected.payloadFingerprint,
                    "ordinary Object projection/materialization");

            LongMeasurement valueGroup = BenchmarkSupport.measure(() ->
                    routeGroupFingerprint(records.groupBy(records.route).count()));
            BenchmarkSupport.require(valueGroup.value(), expected.routeGroupFingerprint,
                    "nested Value high-cardinality GroupBy");

            LongMeasurement sparseGroup = BenchmarkSupport.measure(() ->
                    stringLongGroupFingerprint(records
                            .filter(records.quantity.lt(10))
                            .groupBy(records.label).sum(records.amount)));
            BenchmarkSupport.require(sparseGroup.value(), expected.sparseGroupFingerprint,
                    "sparse filtered reference GroupBy");

            LongMeasurement mediumGroup = BenchmarkSupport.measure(() ->
                    stringLongGroupFingerprint(records
                            .filter(records.quantity.lt(500))
                            .groupBy(records.label).sum(records.amount)));
            BenchmarkSupport.require(mediumGroup.value(), expected.mediumGroupFingerprint,
                    "medium filtered reference GroupBy");

            LongMeasurement denseGroup = BenchmarkSupport.measure(() ->
                    stringLongGroupFingerprint(records
                            .filter(records.quantity.lt(990))
                            .groupBy(records.label).sum(records.amount)));
            BenchmarkSupport.require(denseGroup.value(), expected.denseGroupFingerprint,
                    "dense filtered reference GroupBy");

            LongMeasurement floatingGroup = BenchmarkSupport.measure(() ->
                    stringDoubleGroupFingerprint(records
                            .groupBy(records.tenant).sum(records.score)));
            BenchmarkSupport.require(floatingGroup.value(), expected.floatingGroupFingerprint,
                    "nullable String floating GroupBy");

            LongMeasurement valueJoin = BenchmarkSupport.measure(() -> records.join(weights)
                    .on(records.route, weights.route)
                    .filter(weights.enabled.eq(true))
                    .mapToLong(pair -> pair.left().amount() + pair.right().weight())
                    .sum());
            BenchmarkSupport.require(valueJoin.value(), expected.joinSum,
                    "nested Value Equality Join multiplicity");

            long fingerprint = BenchmarkSupport.fingerprint(
                    integral.value(), floating.value(), parallelFloating.value(), indexes.value(),
                    references.value(), objects.value(), valueGroup.value(),
                    sparseGroup.value(), mediumGroup.value(), denseGroup.value(),
                    floatingGroup.value(), valueJoin.value());
            new BenchmarkResult("type-kernel", "KERNEL_MATRIX", implementation, rows)
                    .put("correctness", true)
                    .put("compression", compression.name())
                    .put("ingestNanos", ingestNanos)
                    .put("integral", integral)
                    .put("floating", floating)
                    .put("parallelFloating", parallelFloating)
                    .put("index", indexes)
                    .put("reference", references)
                    .put("objectMaterialization", objects)
                    .put("valueGroup", valueGroup)
                    .put("sparseGroup", sparseGroup)
                    .put("mediumGroup", mediumGroup)
                    .put("denseGroup", denseGroup)
                    .put("floatingGroup", floatingGroup)
                    .put("valueJoin", valueJoin)
                    .put("retainedBytes", group._metadata().retainedBytes())
                    .put("sharedFingerprint", fingerprint)
                    .put("fingerprint", fingerprint)
                    .print();
        } finally {
            executor.shutdown();
        }
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

    private static long floatingFingerprint(
            double ratioSum,
            double ratioAverage,
            double scoreSum,
            double scoreAverage) {
        return BenchmarkSupport.fingerprint(
                Double.doubleToLongBits(ratioSum),
                Double.doubleToLongBits(ratioAverage),
                Double.doubleToLongBits(scoreSum),
                Double.doubleToLongBits(scoreAverage));
    }

    private static long referenceFingerprint(
            String[] tenants,
            KernelStatus[] statuses) {
        long hash = BenchmarkSupport.mix(HASH_SEED, tenants.length);
        for (String tenant : tenants) hash = mixNullable(hash, tenant);
        hash = BenchmarkSupport.mix(hash, statuses.length);
        for (KernelStatus status : statuses) {
            hash = BenchmarkSupport.mix(hash, status == null ? -1L : status.ordinal());
        }
        return hash;
    }

    private static long payloadFingerprint(KernelPayload[] values) {
        long hash = BenchmarkSupport.mix(HASH_SEED, values.length);
        for (KernelPayload value : values) {
            hash = BenchmarkSupport.mix(hash, value == null ? -1L : value.identity());
        }
        return hash;
    }

    private static long routeGroupFingerprint(GroupedLongResult<RouteKey> grouped) {
        final long[] hash = {BenchmarkSupport.mix(HASH_SEED, grouped.size())};
        grouped.forEach((key, value) -> {
            hash[0] = BenchmarkSupport.mix(hash[0], key.origin());
            hash[0] = BenchmarkSupport.mix(hash[0], key.destination());
            hash[0] = mixNullable(hash[0], key.lane());
            hash[0] = BenchmarkSupport.mix(hash[0], value);
        });
        return hash[0];
    }

    private static long stringLongGroupFingerprint(GroupedLongResult<String> grouped) {
        final long[] hash = {BenchmarkSupport.mix(HASH_SEED, grouped.size())};
        grouped.forEach((key, value) -> {
            hash[0] = mixNullable(hash[0], key);
            hash[0] = BenchmarkSupport.mix(hash[0], value);
        });
        return hash[0];
    }

    private static long stringDoubleGroupFingerprint(GroupedDoubleResult<String> grouped) {
        final long[] hash = {BenchmarkSupport.mix(HASH_SEED, grouped.size())};
        grouped.forEach((key, value) -> {
            hash[0] = mixNullable(hash[0], key);
            hash[0] = BenchmarkSupport.mix(hash[0], Double.doubleToLongBits(value));
        });
        return hash[0];
    }

    private static long mixNullable(long hash, String value) {
        return BenchmarkSupport.mix(hash, value == null ? -1L : value.hashCode());
    }

    private static void add(LinkedHashMap<String, Long> values, String key, long value) {
        Long previous = values.get(key);
        values.put(key, Long.valueOf(previous == null ? value : previous.longValue() + value));
    }

    private static void addDouble(
            LinkedHashMap<String, Double> values,
            String key,
            double value) {
        Double previous = values.get(key);
        values.put(key, Double.valueOf(previous == null ? value : previous.doubleValue() + value));
    }

    private static long expectedStringLong(LinkedHashMap<String, Long> values) {
        long hash = BenchmarkSupport.mix(HASH_SEED, values.size());
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            hash = mixNullable(hash, entry.getKey());
            hash = BenchmarkSupport.mix(hash, entry.getValue().longValue());
        }
        return hash;
    }

    private static long expectedStringDouble(LinkedHashMap<String, Double> values) {
        long hash = BenchmarkSupport.mix(HASH_SEED, values.size());
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            hash = mixNullable(hash, entry.getKey());
            hash = BenchmarkSupport.mix(
                    hash, Double.doubleToLongBits(entry.getValue().doubleValue()));
        }
        return hash;
    }

    private static final class Expected {
        private final int rows;
        private final RouteKey[] routes;
        private final LinkedHashMap<String, Long> sparse = new LinkedHashMap<String, Long>();
        private final LinkedHashMap<String, Long> medium = new LinkedHashMap<String, Long>();
        private final LinkedHashMap<String, Long> dense = new LinkedHashMap<String, Long>();
        private final LinkedHashMap<String, Double> floating = new LinkedHashMap<String, Double>();
        private long codeSum;
        private long laneSum;
        private long categorySum;
        private long quantitySum;
        private long amountSum;
        private double ratioSum;
        private double scoreSum;
        private long routeProbeCount;
        private long tenantProbeCount;
        private long statusProbeCount;
        private long nullTenantCount;
        private long nullStatusCount;
        private long payloadFingerprint = HASH_SEED;
        private long joinSum;
        private long integralFingerprint;
        private long floatingFingerprint;
        private long indexFingerprint;
        private long referenceFingerprint;
        private long routeGroupFingerprint;
        private long sparseGroupFingerprint;
        private long mediumGroupFingerprint;
        private long denseGroupFingerprint;
        private long floatingGroupFingerprint;

        Expected(int rows, RouteKey[] routes) {
            this.rows = rows;
            this.routes = routes;
            payloadFingerprint = BenchmarkSupport.mix(payloadFingerprint, rows);
        }

        void accept(
                int routeIndex,
                String tenant,
                KernelStatus status,
                byte code,
                short laneNumber,
                char category,
                int quantity,
                long amount,
                float ratio,
                double score,
                String label,
                KernelPayload payload) {
            codeSum += code;
            laneSum += laneNumber;
            categorySum += category;
            quantitySum += quantity;
            amountSum += amount;
            ratioSum += ratio;
            scoreSum += score;
            if (routeIndex == 7) routeProbeCount++;
            if ("TENANT-7".equals(tenant)) tenantProbeCount++;
            if (status == KernelStatus.READY) statusProbeCount++;
            if (tenant == null) nullTenantCount++;
            if (status == null) nullStatusCount++;
            payloadFingerprint = BenchmarkSupport.mix(
                    payloadFingerprint, payload == null ? -1L : payload.identity());
            if (quantity < 10) add(sparse, label, amount);
            if (quantity < 500) add(medium, label, amount);
            if (quantity < 990) add(dense, label, amount);
            addDouble(floating, tenant, score);
            joinSum += amount + 1L;
            if ((routeIndex & 7) != 0) joinSum += amount + 2L;
        }

        void finish() {
            integralFingerprint = BenchmarkSupport.fingerprint(
                    codeSum, laneSum, categorySum, quantitySum, amountSum);
            floatingFingerprint = floatingFingerprint(
                    ratioSum, ratioSum / rows, scoreSum, scoreSum / rows);
            indexFingerprint = BenchmarkSupport.fingerprint(
                    routeProbeCount, tenantProbeCount, statusProbeCount,
                    nullTenantCount, nullStatusCount);
            String[] tenantValues = new String[65];
            tenantValues[0] = null;
            for (int index = 0; index < 64; index++) tenantValues[index + 1] = "TENANT-" + index;
            Arrays.sort(tenantValues, Comparator.nullsFirst(String::compareTo));
            KernelStatus[] statusValues = {
                    null,
                    KernelStatus.READY,
                    KernelStatus.RUNNING,
                    KernelStatus.BLOCKED,
                    KernelStatus.COMPLETE
            };
            referenceFingerprint = referenceFingerprint(tenantValues, statusValues);
            routeGroupFingerprint = expectedRouteGroups();
            sparseGroupFingerprint = expectedStringLong(sparse);
            mediumGroupFingerprint = expectedStringLong(medium);
            denseGroupFingerprint = expectedStringLong(dense);
            floatingGroupFingerprint = expectedStringDouble(floating);
        }

        private long expectedRouteGroups() {
            long hash = BenchmarkSupport.mix(HASH_SEED, routes.length);
            int quotient = rows / routes.length;
            int remainder = rows % routes.length;
            for (int index = 0; index < routes.length; index++) {
                RouteKey key = routes[index];
                hash = BenchmarkSupport.mix(hash, key.origin());
                hash = BenchmarkSupport.mix(hash, key.destination());
                hash = mixNullable(hash, key.lane());
                hash = BenchmarkSupport.mix(hash, quotient + (index < remainder ? 1L : 0L));
            }
            return hash;
        }
    }
}
