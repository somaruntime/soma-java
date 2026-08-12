package io.github.somaruntime.benchmarks.kernel;

import io.github.somaruntime.benchmarks.BenchmarkResult;
import io.github.somaruntime.benchmarks.BenchmarkSupport;
import io.github.somaruntime.benchmarks.LongMeasurement;
import io.github.somaruntime.benchmarks.MemoryObserver;
import io.github.somaruntime.soma.internal.DirectChunkLongSum;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.stream.IntStream;

/** Raw-loop and Java Stream semantic baselines for comparable frontier kernels. */
final class FrontierBaselines {
    private static final Comparator<String> NULLABLE_STRING_ORDER =
            Comparator.nullsFirst(String::compareTo);

    private FrontierBaselines() {}

    static void run(int rows, String implementation, String scenario) {
        BenchmarkSupport.require("frontier-source".equals(scenario)
                        || "frontier-stateful".equals(scenario),
                "manual/java-stream baselines are defined for source and stateful families");
        BenchmarkSupport.observeMemory(new MemoryObserver() {
            @Override public long retainedBytes() { return 0L; }
            @Override public long temporaryBytes() { return 0L; }
        });
        FrontierData data = FrontierData.create(rows, true);
        if ("frontier-source".equals(scenario)) {
            runSources(data, implementation);
        } else {
            runStateful(data, implementation);
        }
    }

    private static void runSources(FrontierData data, String implementation) {
        boolean streams = "java-stream".equals(implementation);
        DirectChunkLongSum directChunks = streams
                ? null : new DirectChunkLongSum(data.amounts);
        LongMeasurement tableCount = BenchmarkSupport.measure(() -> data.rows);
        LongMeasurement typedFilter = BenchmarkSupport.measure(() ->
                streams ? streamDenseSum(data, false) : manualDenseSum(data));
        LongMeasurement callbackFilter = BenchmarkSupport.measure(() ->
                streams ? streamDenseSum(data, false) : manualDenseSum(data));
        LongMeasurement parallelTypedFilter = streams
                ? BenchmarkSupport.measure(() -> streamDenseSum(data, true))
                : null;
        LongMeasurement parallelCallbackFilter = streams
                ? BenchmarkSupport.measure(() -> streamDenseSum(data, true))
                : null;
        LongMeasurement fieldSum = BenchmarkSupport.measure(() ->
                streams ? Arrays.stream(data.amounts).sum() : manualAmountSum(data));
        LongMeasurement directChunkFieldSum = streams
                ? null : BenchmarkSupport.measure(directChunks::sum);
        LongMeasurement fieldFiltered = BenchmarkSupport.measure(() -> streams
                ? Arrays.stream(data.amounts).filter(value -> (value & 1L) == 0L).sum()
                : manualEvenAmountSum(data));
        LongMeasurement parallelFieldSum = streams
                ? BenchmarkSupport.measure(
                        () -> Arrays.stream(data.amounts).parallel().sum())
                : null;
        LongMeasurement keyLookup = BenchmarkSupport.measure(() ->
                streams ? streamKeyLookup(data) : manualKeyLookup(data));
        LongMeasurement indexCount = BenchmarkSupport.measure(() -> streams
                ? IntStream.range(0, data.rows)
                        .filter(index -> FrontierData.PROBE_TENANT.equals(data.tenants[index]))
                        .count()
                : data.tenantProbeSize);
        LongMeasurement indexResidual = BenchmarkSupport.measure(() -> streams
                ? IntStream.range(0, data.rows)
                        .filter(index -> FrontierData.PROBE_TENANT.equals(data.tenants[index]))
                        .filter(index -> data.quantities[index] >= 500)
                        .mapToLong(index -> data.amounts[index])
                        .sum()
                : manualIndexResidual(data));
        LongMeasurement mappedPrimitive = BenchmarkSupport.measure(() -> streams
                ? IntStream.range(0, data.rows)
                        .mapToLong(index -> data.amounts[index] + data.quantities[index])
                        .sum()
                : manualMappedPrimitive(data));
        LongMeasurement parallelMappedPrimitive = streams
                ? BenchmarkSupport.measure(() -> IntStream.range(0, data.rows)
                        .parallel()
                        .mapToLong(index -> data.amounts[index] + data.quantities[index])
                        .sum())
                : null;
        LongMeasurement fieldMaterialize = BenchmarkSupport.measure(() ->
                FrontierData.sampledLongs(streams
                        ? Arrays.stream(data.amounts).toArray()
                        : data.amounts.clone()));
        LongMeasurement parallelFieldMaterialize = streams
                ? BenchmarkSupport.measure(() -> FrontierData.sampledLongs(
                        Arrays.stream(data.amounts).parallel().toArray()))
                : null;
        LongMeasurement typedFilterMaterialize = BenchmarkSupport.measure(() ->
                FrontierData.hashLongs(streams
                        ? IntStream.range(0, data.rows)
                                .filter(index -> data.quantities[index] >= 500)
                                .mapToLong(index -> data.amounts[index])
                                .toArray()
                        : manualTypedFilterMaterialize(data)));
        LongMeasurement parallelTypedFilterMaterialize = streams
                ? BenchmarkSupport.measure(() -> FrontierData.hashLongs(
                        IntStream.range(0, data.rows)
                                .parallel()
                                .filter(index -> data.quantities[index] >= 500)
                                .mapToLong(index -> data.amounts[index])
                                .toArray()))
                : null;
        LongMeasurement mappedReference = BenchmarkSupport.measure(() ->
                FrontierData.hashStrings(streams
                        ? Arrays.stream(data.labels)
                                .distinct()
                                .sorted(NULLABLE_STRING_ORDER)
                                .limit(64)
                                .toArray(String[]::new)
                        : manualDistinctLabels(data)));

        require(tableCount, data.rows, "baseline Table count");
        require(typedFilter, data.tableDenseSum, "baseline typed filter");
        require(callbackFilter, data.tableDenseSum, "baseline callback filter");
        if (streams) {
            require(parallelTypedFilter, data.tableDenseSum,
                    "baseline parallel typed filter");
            require(parallelCallbackFilter, data.tableDenseSum,
                    "baseline parallel callback filter");
        }
        require(fieldSum, data.amountSum, "baseline Field sum");
        if (!streams) {
            require(directChunkFieldSum, data.amountSum,
                    "direct PLAIN Chunk Field sum");
        }
        require(fieldFiltered, data.evenAmountSum, "baseline Field filter");
        if (streams) {
            require(parallelFieldSum, data.amountSum,
                    "baseline parallel Field sum");
        }
        require(keyLookup, data.keyLookupSum, "baseline key lookup");
        require(indexCount, data.tenantProbeCount, "baseline Index count");
        require(indexResidual, data.tenantProbeDenseSum, "baseline Index residual");
        require(mappedPrimitive, data.mappedPrimitiveSum, "baseline mapped primitive");
        if (streams) {
            require(parallelMappedPrimitive, data.mappedPrimitiveSum,
                    "baseline parallel mapped primitive");
        }
        require(fieldMaterialize, data.materializedAmountFingerprint,
                "baseline Field materialization");
        if (streams) {
            require(parallelFieldMaterialize,
                    data.materializedAmountFingerprint,
                    "baseline parallel Field materialization");
        }
        require(typedFilterMaterialize,
                data.typedFilterMaterializedFingerprint,
                "baseline typed filter materialization");
        if (streams) {
            require(parallelTypedFilterMaterialize,
                    data.typedFilterMaterializedFingerprint,
                    "baseline parallel typed filter materialization");
        }
        require(mappedReference, data.mappedReferenceFingerprint,
                "baseline mapped reference");

        long fingerprint = FrontierData.hash(
                tableCount.value(), typedFilter.value(), callbackFilter.value(),
                fieldSum.value(), fieldFiltered.value(), keyLookup.value(),
                indexCount.value(), indexResidual.value(), mappedPrimitive.value(),
                fieldMaterialize.value(), typedFilterMaterialize.value(),
                mappedReference.value());
        BenchmarkResult result = new BenchmarkResult(
                "frontier-source", "FRONTIER_MATRIX", implementation, data.rows)
                .put("correctness", true)
                .put("tableCount", tableCount)
                .put("tableTypedFilter", typedFilter)
                .put("tableCallbackFilter", callbackFilter)
                .put("fieldSum", fieldSum)
                .put("fieldFiltered", fieldFiltered)
                .put("keyLookup10k", keyLookup)
                .put("indexCount", indexCount)
                .put("indexResidual", indexResidual)
                .put("mappedPrimitive", mappedPrimitive)
                .put("fieldMaterialize", fieldMaterialize)
                .put("typedFilterMaterialize", typedFilterMaterialize)
                .put("mappedReference", mappedReference);
        if (!streams) {
            result.put("directChunkFieldSum", directChunkFieldSum);
        }
        if (streams) {
            result.put("parallelTableTypedFilter", parallelTypedFilter)
                    .put("parallelTableCallbackFilter", parallelCallbackFilter)
                    .put("parallelFieldSum", parallelFieldSum)
                    .put("parallelMappedPrimitive", parallelMappedPrimitive)
                    .put("parallelFieldMaterialize", parallelFieldMaterialize)
                    .put("parallelTypedFilterMaterialize",
                            parallelTypedFilterMaterialize);
        }
        result.put("sharedFingerprint", fingerprint)
                .put("fingerprint", fingerprint).print();
    }

    private static void runStateful(FrontierData data, String implementation) {
        boolean streams = "java-stream".equals(implementation);
        LongMeasurement fieldDistinct = BenchmarkSupport.measure(() ->
                FrontierData.hashLongs(streams
                        ? Arrays.stream(data.amounts).distinct().sorted().toArray()
                        : distinctSorted(data.amounts)));
        LongMeasurement fieldSortLimit = BenchmarkSupport.measure(() ->
                FrontierData.hashLongs(streams
                        ? Arrays.stream(data.amounts).sorted()
                                .limit(FrontierData.RESULT_LIMIT).toArray()
                        : sortedLimit(data.amounts, FrontierData.RESULT_LIMIT)));
        LongMeasurement fieldTop = BenchmarkSupport.measure(() ->
                FrontierData.hashLongs(streams
                        ? Arrays.stream(data.amounts).sorted()
                                .limit(FrontierData.RESULT_LIMIT).toArray()
                        : sortedLimit(data.amounts, FrontierData.RESULT_LIMIT)));
        LongMeasurement parallelFieldTop = streams
                ? BenchmarkSupport.measure(() -> FrontierData.hashLongs(
                        Arrays.stream(data.amounts).parallel().sorted()
                                .limit(FrontierData.RESULT_LIMIT).toArray()))
                : null;
        LongMeasurement tableTop = BenchmarkSupport.measure(() ->
                FrontierData.hashLongs(tableTopIds(data)));
        LongMeasurement parallelTableTop = streams
                ? BenchmarkSupport.measure(
                        () -> FrontierData.hashLongs(tableTopIds(data)))
                : null;
        LongMeasurement tableSlice = BenchmarkSupport.measure(() ->
                FrontierData.hashLongs(tableSliceIds(data)));
        LongMeasurement referenceDistinct = BenchmarkSupport.measure(() ->
                FrontierData.hashStrings(streams
                        ? Arrays.stream(data.labels).distinct()
                                .sorted(NULLABLE_STRING_ORDER).limit(64)
                                .toArray(String[]::new)
                        : manualDistinctLabels(data)));
        LongMeasurement parallelReferenceDistinct = streams
                ? BenchmarkSupport.measure(() -> FrontierData.hashStrings(
                        Arrays.stream(data.labels).parallel().distinct()
                                .sorted(NULLABLE_STRING_ORDER).limit(64)
                                .toArray(String[]::new)))
                : null;

        require(fieldDistinct, data.distinctFingerprint, "baseline Field distinct");
        require(fieldSortLimit, data.fieldTopFingerprint, "baseline Field sort/limit");
        require(fieldTop, data.fieldTopFingerprint, "baseline Field top");
        if (streams) {
            require(parallelFieldTop, data.fieldTopFingerprint,
                    "baseline parallel Field top");
        }
        require(tableTop, data.tableTopFingerprint, "baseline Table top");
        if (streams) {
            require(parallelTableTop, data.tableTopFingerprint,
                    "baseline parallel Table top");
        }
        require(tableSlice, data.skipLimitFingerprint, "baseline Table slice");
        require(referenceDistinct, data.mappedReferenceFingerprint,
                "baseline reference distinct");
        if (streams) {
            require(parallelReferenceDistinct, data.mappedReferenceFingerprint,
                    "baseline parallel reference distinct");
        }

        long fingerprint = FrontierData.hash(
                fieldDistinct.value(), fieldSortLimit.value(), fieldTop.value(),
                tableTop.value(), tableSlice.value(), referenceDistinct.value());
        BenchmarkResult result = new BenchmarkResult(
                "frontier-stateful", "FRONTIER_MATRIX", implementation, data.rows)
                .put("correctness", true)
                .put("fieldDistinct", fieldDistinct)
                .put("fieldSortLimit", fieldSortLimit)
                .put("fieldTop", fieldTop)
                .put("tableTop", tableTop)
                .put("tableSlice", tableSlice)
                .put("referenceDistinct", referenceDistinct);
        if (streams) {
            result.put("parallelFieldTop", parallelFieldTop)
                    .put("parallelTableTop", parallelTableTop)
                    .put("parallelReferenceDistinct", parallelReferenceDistinct);
        }
        result.put("sharedFingerprint", fingerprint)
                .put("fingerprint", fingerprint).print();
    }

    private static long manualDenseSum(FrontierData data) {
        long sum = 0L;
        for (int index = 0; index < data.rows; index++) {
            if (data.quantities[index] >= 500) sum += data.amounts[index];
        }
        return sum;
    }

    private static long streamDenseSum(FrontierData data, boolean parallel) {
        IntStream stream = IntStream.range(0, data.rows);
        if (parallel) stream = stream.parallel();
        return stream.filter(index -> data.quantities[index] >= 500)
                .mapToLong(index -> data.amounts[index])
                .sum();
    }

    private static long manualAmountSum(FrontierData data) {
        long sum = 0L;
        for (long value : data.amounts) sum += value;
        return sum;
    }

    private static long manualEvenAmountSum(FrontierData data) {
        long sum = 0L;
        for (long value : data.amounts) if ((value & 1L) == 0L) sum += value;
        return sum;
    }

    private static long manualKeyLookup(FrontierData data) {
        long sum = 0L;
        int probes = Math.min(10_000, data.rows);
        for (int probe = 0; probe < probes; probe++) {
            int index = (int) ((probe * 7_919L) % data.rows);
            sum += data.amounts[index];
        }
        return sum;
    }

    private static long streamKeyLookup(FrontierData data) {
        int probes = Math.min(10_000, data.rows);
        return IntStream.range(0, probes)
                .mapToLong(probe -> data.amounts[(int) ((probe * 7_919L) % data.rows)])
                .sum();
    }

    private static long manualIndexResidual(FrontierData data) {
        long sum = 0L;
        for (int offset = 0; offset < data.tenantProbeSize; offset++) {
            int index = data.tenantProbeLocators[offset];
            if (data.quantities[index] >= 500) sum += data.amounts[index];
        }
        return sum;
    }

    private static long manualMappedPrimitive(FrontierData data) {
        long sum = 0L;
        for (int index = 0; index < data.rows; index++) {
            sum += data.amounts[index] + data.quantities[index];
        }
        return sum;
    }

    private static long[] manualTypedFilterMaterialize(FrontierData data) {
        long[] result = new long[data.rows];
        int size = 0;
        for (int index = 0; index < data.rows; index++) {
            if (data.quantities[index] >= 500) {
                result[size++] = data.amounts[index];
            }
        }
        return Arrays.copyOf(result, size);
    }

    private static String[] manualDistinctLabels(FrontierData data) {
        TreeSet<String> values = new TreeSet<String>(NULLABLE_STRING_ORDER);
        values.addAll(Arrays.asList(data.labels));
        String[] result = new String[Math.min(64, values.size())];
        int position = 0;
        for (String value : values) {
            if (position == result.length) break;
            result[position++] = value;
        }
        return result;
    }

    private static long[] distinctSorted(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        if (sorted.length == 0) return sorted;
        int size = 1;
        for (int index = 1; index < sorted.length; index++) {
            if (sorted[index] != sorted[size - 1]) sorted[size++] = sorted[index];
        }
        return Arrays.copyOf(sorted, size);
    }

    private static long[] sortedLimit(long[] values, int limit) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return Arrays.copyOf(sorted, Math.min(limit, sorted.length));
    }

    private static long[] tableTopIds(FrontierData data) {
        int limit = Math.min(FrontierData.RESULT_LIMIT, data.rows);
        long[] heap = new long[limit];
        int size = 0;
        for (int index = 0; index < data.rows; index++) {
            long id = index + 1L;
            if (size < limit) {
                heap[size] = id;
                siftUpWorst(heap, size++, data);
            } else if (better(id, heap[0], data)) {
                heap[0] = id;
                siftDownWorst(heap, size, data);
            }
        }
        for (int left = 1; left < size; left++) {
            long value = heap[left];
            int position = left;
            while (position > 0 && better(value, heap[position - 1], data)) {
                heap[position] = heap[position - 1];
                position--;
            }
            heap[position] = value;
        }
        return heap;
    }

    private static void siftUpWorst(long[] heap, int index, FrontierData data) {
        long value = heap[index];
        while (index > 0) {
            int parent = (index - 1) >>> 1;
            if (!worse(value, heap[parent], data)) break;
            heap[index] = heap[parent];
            index = parent;
        }
        heap[index] = value;
    }

    private static void siftDownWorst(long[] heap, int size, FrontierData data) {
        int index = 0;
        long value = heap[0];
        while (true) {
            int child = (index << 1) + 1;
            if (child >= size) break;
            if (child + 1 < size && worse(heap[child + 1], heap[child], data)) child++;
            if (!worse(heap[child], value, data)) break;
            heap[index] = heap[child];
            index = child;
        }
        heap[index] = value;
    }

    private static boolean better(long leftId, long rightId, FrontierData data) {
        int leftQuantity = data.quantities[(int) leftId - 1];
        int rightQuantity = data.quantities[(int) rightId - 1];
        return leftQuantity > rightQuantity
                || leftQuantity == rightQuantity && leftId < rightId;
    }

    private static boolean worse(long leftId, long rightId, FrontierData data) {
        return better(rightId, leftId, data);
    }

    private static long[] tableSliceIds(FrontierData data) {
        int start = data.rows / 2;
        int length = Math.min(FrontierData.RESULT_LIMIT, data.rows - start);
        long[] result = new long[length];
        for (int index = 0; index < length; index++) result[index] = start + index + 1L;
        return result;
    }

    private static void require(LongMeasurement measurement, long expected, String subject) {
        BenchmarkSupport.require(measurement.value(), expected, subject);
    }
}
