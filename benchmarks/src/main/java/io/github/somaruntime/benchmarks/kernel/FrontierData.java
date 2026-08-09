package io.github.somaruntime.benchmarks.kernel;

import io.github.somaruntime.benchmarks.BenchmarkSupport;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeSet;

/** Deterministic frontier workload data and implementation-independent expected results. */
final class FrontierData {
    static final long HASH_SEED = 0xcbf29ce484222325L;
    static final int RESULT_LIMIT = 1_024;
    static final String PROBE_TENANT = "TENANT-7";

    final int rows;
    final int routeCount;
    final int[] quantities;
    final long[] amounts;
    final byte[] codes;
    final int[] routeIndexes;
    final String[] labels;
    final String[] tenants;
    final int[] tenantProbeLocators;
    final String[] tenantValues;
    final String[] labelValues;

    long tableDenseSum;
    long amountSum;
    long evenAmountSum;
    long tenantProbeCount;
    long tenantProbeDenseSum;
    long mappedPrimitiveSum;
    long keyLookupSum;
    long enabledJoinSum;
    long enabledJoinCount;
    long[] lowGroupSums = new long[16];
    long[] highGroupSums = new long[8_194];
    boolean[] highGroupPresent = new boolean[8_194];
    int[] highGroupOrder = new int[8_194];
    int highGroupSize;
    int tenantProbeSize;

    long distinctFingerprint;
    long fieldTopFingerprint;
    long tableTopFingerprint;
    long skipLimitFingerprint;
    long materializedAmountFingerprint;
    long mappedReferenceFingerprint;
    long lowGroupFingerprint;
    long highGroupFingerprint;
    long sourceFingerprint;
    long statefulFingerprint;
    long relationFingerprint;

    private FrontierData(int rows, boolean retainColumns) {
        this.rows = rows;
        this.routeCount = Math.min(262_144, Math.max(1_024, rows / 4));
        this.quantities = retainColumns ? new int[rows] : null;
        this.amounts = retainColumns ? new long[rows] : null;
        this.codes = retainColumns ? new byte[rows] : null;
        this.routeIndexes = retainColumns ? new int[rows] : null;
        this.labels = retainColumns ? new String[rows] : null;
        this.tenants = retainColumns ? new String[rows] : null;
        this.tenantProbeLocators = retainColumns ? new int[rows / 32 + 32] : null;
        this.tenantValues = values("TENANT-", 64);
        this.labelValues = values("LABEL-", 8_192);
    }

    static FrontierData create(int rows, boolean retainColumns) {
        FrontierData result = new FrontierData(rows, retainColumns);
        for (int index = 0; index < rows; index++) result.accept(index);
        result.finish();
        return result;
    }

    int quantity(int index) { return index % 1_000; }
    long amount(int index) { return 1L + index % 100; }
    byte code(int index) { return (byte) (index % 16 - 8); }
    int routeIndex(int index) { return index % routeCount; }

    String tenant(int index) {
        return index % 97 == 0 ? null : tenantValues[index % tenantValues.length];
    }

    String label(int index) {
        if (index % 211 == 0) return null;
        if (index % 5 == 0) return "HOT";
        return labelValues[index % labelValues.length];
    }

    private void accept(int index) {
        int quantity = quantity(index);
        long amount = amount(index);
        byte code = code(index);
        int routeIndex = routeIndex(index);
        String tenant = tenant(index);
        String label = label(index);

        if (quantities != null) {
            quantities[index] = quantity;
            amounts[index] = amount;
            codes[index] = code;
            routeIndexes[index] = routeIndex;
            tenants[index] = tenant;
            labels[index] = label;
            if (PROBE_TENANT.equals(tenant)) {
                tenantProbeLocators[tenantProbeSize++] = index;
            }
        }

        if (quantity >= 500) tableDenseSum += amount;
        amountSum += amount;
        if ((amount & 1L) == 0L) evenAmountSum += amount;
        if (PROBE_TENANT.equals(tenant)) {
            tenantProbeCount++;
            if (quantity >= 500) tenantProbeDenseSum += amount;
        }
        mappedPrimitiveSum += amount + quantity;
        lowGroupSums[code + 8] += amount;

        int labelSlot = labelSlot(index);
        if (!highGroupPresent[labelSlot]) {
            highGroupPresent[labelSlot] = true;
            highGroupOrder[highGroupSize++] = labelSlot;
        }
        highGroupSums[labelSlot] += amount;

        enabledJoinCount++;
        enabledJoinSum += amount + 1L;
        if ((routeIndex & 7) != 0) {
            enabledJoinCount++;
            enabledJoinSum += amount + 2L;
        }
    }

    int labelSlot(int index) {
        if (index % 211 == 0) return 8_193;
        if (index % 5 == 0) return 8_192;
        return index % 8_192;
    }

    String labelForSlot(int slot) {
        if (slot == 8_193) return null;
        if (slot == 8_192) return "HOT";
        return labelValues[slot];
    }

    private void finish() {
        int keyProbes = Math.min(10_000, rows);
        for (int probe = 0; probe < keyProbes; probe++) {
            int index = (int) ((probe * 7_919L) % rows);
            keyLookupSum += amount(index);
        }
        distinctFingerprint = expectedDistinctFingerprint();
        fieldTopFingerprint = expectedFieldTopFingerprint();
        tableTopFingerprint = expectedTableTopFingerprint();
        skipLimitFingerprint = expectedSkipLimitFingerprint();
        materializedAmountFingerprint = expectedMaterializedAmountFingerprint();
        mappedReferenceFingerprint = expectedMappedReferenceFingerprint();
        lowGroupFingerprint = expectedLowGroupFingerprint();
        highGroupFingerprint = expectedHighGroupFingerprint();
        sourceFingerprint = hash(
                rows, tableDenseSum, amountSum, evenAmountSum,
                tenantProbeCount, tenantProbeDenseSum, mappedPrimitiveSum,
                keyLookupSum, materializedAmountFingerprint, mappedReferenceFingerprint);
        statefulFingerprint = hash(
                distinctFingerprint, fieldTopFingerprint, tableTopFingerprint,
                skipLimitFingerprint, mappedReferenceFingerprint);
        relationFingerprint = hash(
                lowGroupFingerprint, highGroupFingerprint,
                rows * 2L, enabledJoinCount, enabledJoinSum, rows, 0L);
    }

    private long expectedDistinctFingerprint() {
        long hash = BenchmarkSupport.mix(HASH_SEED, 100L);
        for (long value = 1L; value <= 100L; value++) {
            hash = BenchmarkSupport.mix(hash, value);
        }
        return hash;
    }

    private long expectedFieldTopFingerprint() {
        int length = Math.min(RESULT_LIMIT, rows);
        long hash = BenchmarkSupport.mix(HASH_SEED, length);
        int remaining = length;
        for (long value = 1L; value <= 100L && remaining > 0; value++) {
            int occurrences = (rows + 100 - (int) value) / 100;
            int emitted = Math.min(occurrences, remaining);
            for (int index = 0; index < emitted; index++) {
                hash = BenchmarkSupport.mix(hash, value);
            }
            remaining -= emitted;
        }
        return hash;
    }

    private long expectedTableTopFingerprint() {
        int length = Math.min(RESULT_LIMIT, rows);
        long hash = BenchmarkSupport.mix(HASH_SEED, length);
        int emitted = 0;
        for (int quantity = 999; quantity >= 0 && emitted < length; quantity--) {
            for (long id = quantity + 1L; id <= rows && emitted < length; id += 1_000L) {
                hash = BenchmarkSupport.mix(hash, id);
                emitted++;
            }
        }
        return hash;
    }

    private long expectedSkipLimitFingerprint() {
        int start = rows / 2;
        int length = Math.min(RESULT_LIMIT, rows - start);
        long hash = BenchmarkSupport.mix(HASH_SEED, length);
        for (int index = 0; index < length; index++) {
            hash = BenchmarkSupport.mix(hash, start + index + 1L);
        }
        return hash;
    }

    private long expectedMaterializedAmountFingerprint() {
        long hash = BenchmarkSupport.mix(HASH_SEED, rows);
        int samples = Math.min(1_024, rows);
        for (int sample = 0; sample < samples; sample++) {
            int index = (int) ((long) sample * rows / samples);
            hash = BenchmarkSupport.mix(hash, amount(index));
        }
        return hash;
    }

    private long expectedMappedReferenceFingerprint() {
        TreeSet<String> values = new TreeSet<String>(Comparator.nullsFirst(String::compareTo));
        for (int index = 0; index < rows; index++) values.add(label(index));
        int length = Math.min(64, values.size());
        long hash = BenchmarkSupport.mix(HASH_SEED, length);
        int emitted = 0;
        for (String value : values) {
            if (emitted++ == length) break;
            hash = mixNullable(hash, value);
        }
        return hash;
    }

    private long expectedLowGroupFingerprint() {
        long hash = BenchmarkSupport.mix(HASH_SEED, 16L);
        for (int slot = 0; slot < lowGroupSums.length; slot++) {
            hash = BenchmarkSupport.mix(hash, slot - 8L);
            hash = BenchmarkSupport.mix(hash, lowGroupSums[slot]);
        }
        return hash;
    }

    private long expectedHighGroupFingerprint() {
        long hash = BenchmarkSupport.mix(HASH_SEED, highGroupSize);
        for (int index = 0; index < highGroupSize; index++) {
            int slot = highGroupOrder[index];
            hash = mixNullable(hash, labelForSlot(slot));
            hash = BenchmarkSupport.mix(hash, highGroupSums[slot]);
        }
        return hash;
    }

    long[] expectedFieldTopValues() {
        int length = Math.min(RESULT_LIMIT, rows);
        long[] result = new long[length];
        int position = 0;
        for (long value = 1L; value <= 100L && position < length; value++) {
            int occurrences = (rows + 100 - (int) value) / 100;
            int emitted = Math.min(occurrences, length - position);
            Arrays.fill(result, position, position + emitted, value);
            position += emitted;
        }
        return result;
    }

    long[] expectedTableTopIds() {
        int length = Math.min(RESULT_LIMIT, rows);
        long[] result = new long[length];
        int position = 0;
        for (int quantity = 999; quantity >= 0 && position < length; quantity--) {
            for (long id = quantity + 1L; id <= rows && position < length; id += 1_000L) {
                result[position++] = id;
            }
        }
        return result;
    }

    static long hash(long... values) {
        long hash = HASH_SEED;
        for (long value : values) hash = BenchmarkSupport.mix(hash, value);
        return hash;
    }

    static long hashLongs(long[] values) {
        long hash = BenchmarkSupport.mix(HASH_SEED, values.length);
        for (long value : values) hash = BenchmarkSupport.mix(hash, value);
        return hash;
    }

    static long sampledLongs(long[] values) {
        long hash = BenchmarkSupport.mix(HASH_SEED, values.length);
        int samples = Math.min(1_024, values.length);
        for (int sample = 0; sample < samples; sample++) {
            int index = (int) ((long) sample * values.length / samples);
            hash = BenchmarkSupport.mix(hash, values[index]);
        }
        return hash;
    }

    static long hashStrings(String[] values) {
        long hash = BenchmarkSupport.mix(HASH_SEED, values.length);
        for (String value : values) hash = mixNullable(hash, value);
        return hash;
    }

    static long mixNullable(long hash, String value) {
        return BenchmarkSupport.mix(hash, value == null ? -1L : value.hashCode());
    }

    private static String[] values(String prefix, int count) {
        String[] result = new String[count];
        for (int index = 0; index < count; index++) result[index] = prefix + index;
        return result;
    }
}
