package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaDoubleSummary;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaLongSummary;
import io.github.somaruntime.soma.SomaOperation;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** One-shot typed GroupBy carrier over a row logical plan. */
public final class GeneratedGrouping {

    public static final int KEY_BOOLEAN = 1;
    public static final int KEY_BYTE = 2;
    public static final int KEY_SHORT = 3;
    public static final int KEY_CHAR = 4;
    public static final int KEY_INT = 5;
    public static final int KEY_LONG = 6;
    public static final int KEY_REFERENCE = 7;

    public static final int VALUE_INT = 1;
    public static final int VALUE_LONG = 2;
    public static final int VALUE_DOUBLE = 3;
    public static final int VALUE_LONG_SUMMARY = 4;
    public static final int VALUE_DOUBLE_SUMMARY = 5;

    public static final int SUM = 1;
    public static final int MIN = 2;
    public static final int MAX = 3;
    public static final int AVERAGE = 4;
    public static final int SUMMARY = 5;

    private final LogicalRowPlan plan;
    private final int keyFieldIndex;
    private final int keyKind;
    private final GeneratedCallbacks.RowMapper<?> keyMaterializer;
    private final AtomicBoolean consumed = new AtomicBoolean();

    GeneratedGrouping(
            LogicalRowPlan plan,
            int keyFieldIndex,
            int keyKind,
            GeneratedCallbacks.RowMapper<?> keyMaterializer) {
        this.plan = plan;
        this.keyFieldIndex = keyFieldIndex;
        this.keyKind = keyKind;
        this.keyMaterializer = keyMaterializer;
    }

    public Object count() {
        claim();
        return execute(new AggregateSpec(VALUE_LONG, 0, -1, null, null), false);
    }

    Object countReferenceForTesting() {
        claim();
        return execute(new AggregateSpec(VALUE_LONG, 0, -1, null, null), true);
    }

    public Object aggregateLong(
            int aggregate,
            int valueKind,
            int fieldIndex,
            GeneratedCallbacks.RowToLongMapper mapper) {
        requireAggregate(aggregate, valueKind, fieldIndex, mapper);
        claim();
        return execute(new AggregateSpec(
                valueKind, aggregate, fieldIndex, mapper, null), false);
    }

    public Object aggregateDouble(
            int aggregate,
            int valueKind,
            int fieldIndex,
            GeneratedCallbacks.RowToDoubleMapper mapper) {
        requireAggregate(aggregate, valueKind, fieldIndex, mapper);
        claim();
        return execute(new AggregateSpec(
                valueKind, aggregate, fieldIndex, null, mapper), false);
    }

    private Object execute(
            final AggregateSpec aggregate,
            final boolean reference) {
        return QueryOperation.execute(plan, new QueryOperation.BoundWork<Object>() {
            @Override
            public long scratchBytes(BoundRowPlan bound) {
                long rows = bound.outputUpperBound();
                long perRow = aggregate.needsFloatingSequence() ? 176L : 144L;
                return QueryOperation.addScratch(
                        QueryOperation.rowExecutionScratch(bound),
                        RowExecutionSupport.arrayBytes(rows, perRow, bound.provenance),
                        bound.provenance);
            }

            @Override
            public Object run(BoundRowPlan bound) {
                return group(bound, aggregate, reference);
            }
        });
    }

    private Object group(
            final BoundRowPlan bound,
            final AggregateSpec aggregate,
            final boolean reference) {
        final int upper = RowExecutionSupport.arrayLength(
                bound.outputUpperBound(), bound.provenance);
        final int expectedGroups = expectedGroups(bound, upper);
        final GroupState state = new GroupState(
                upper, expectedGroups, keyKind, aggregate, bound.provenance);
        visitRows(
                bound,
                new OptimizedSequentialRowExecutor.LocatorVisitor() {
                    @Override
                    public boolean visit(long locator) {
                        int group = reference
                                ? state.groupForReference(
                                        bound.root.directory,
                                        plan.owner().layout(),
                                        keyFieldIndex,
                                        locator)
                                : state.groupFor(
                                        bound.root.directory,
                                        plan.owner().layout(),
                                        keyFieldIndex,
                                        locator);
                        state.add(bound, group, locator, aggregate);
                        return true;
                    }
                }, reference);
        Object keys = materializeKeys(bound, state);
        Object values = state.finish(aggregate, bound.provenance);
        return GeneratedGroupedResults.create(
                keyKind, aggregate.valueKind, keys, values, state.size);
    }

    private int expectedGroups(BoundRowPlan bound, int upper) {
        if (upper == 0) return 0;
        GeneratedTableLayout layout = plan.owner().layout();
        if (layout.keyFieldIndex() == keyFieldIndex) return upper;
        int index = layout.indexOrdinalForField(keyFieldIndex);
        if (index < 0) return Math.min(upper, GroupState.INITIAL_CAPACITY);
        long distinct = bound.root.indexes[index].distinctCount();
        return (int) Math.min((long) upper, distinct);
    }

    private static void visitRows(
            BoundRowPlan bound,
            OptimizedSequentialRowExecutor.LocatorVisitor visitor,
            boolean reference) {
        if (reference) ReferenceRowInterpreter.visit(bound, visitor);
        else RowExecutor.visit(bound, visitor);
    }

    private Object materializeKeys(BoundRowPlan bound, GroupState state) {
        Object result = keyArray(keyKind, state.size);
        for (int index = 0; index < state.size; index++) {
            Object value = RowExecutionSupport.callbackMap(
                    bound, state.representatives[index], keyMaterializer, false);
            putKey(result, index, value);
        }
        return result;
    }

    private static Object keyArray(int kind, int size) {
        switch (kind) {
            case KEY_BOOLEAN: return new boolean[size];
            case KEY_BYTE: return new byte[size];
            case KEY_SHORT: return new short[size];
            case KEY_CHAR: return new char[size];
            case KEY_INT: return new int[size];
            case KEY_LONG: return new long[size];
            case KEY_REFERENCE: return new Object[size];
            default: throw new AssertionError("unknown grouped key kind");
        }
    }

    private void putKey(Object array, int index, Object value) {
        switch (keyKind) {
            case KEY_BOOLEAN: ((boolean[]) array)[index] = ((Boolean) value).booleanValue(); break;
            case KEY_BYTE: ((byte[]) array)[index] = ((Byte) value).byteValue(); break;
            case KEY_SHORT: ((short[]) array)[index] = ((Short) value).shortValue(); break;
            case KEY_CHAR: ((char[]) array)[index] = ((Character) value).charValue(); break;
            case KEY_INT: ((int[]) array)[index] = ((Integer) value).intValue(); break;
            case KEY_LONG: ((long[]) array)[index] = ((Long) value).longValue(); break;
            case KEY_REFERENCE: ((Object[]) array)[index] = value; break;
            default: throw new AssertionError("unknown grouped key kind");
        }
    }

    private static void requireAggregate(
            int aggregate,
            int valueKind,
            int fieldIndex,
            Object mapper) {
        if (aggregate < SUM || aggregate > SUMMARY
                || valueKind < VALUE_INT || valueKind > VALUE_DOUBLE_SUMMARY
                || fieldIndex < 0 || mapper == null) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "invalid GroupBy aggregate");
        }
    }

    private void claim() {
        if (!consumed.compareAndSet(false, true)) {
            throw SomaFailures.failure(
                    SomaFailureCode.PIPELINE_ALREADY_CONSUMED,
                    SomaOperation.QUERY,
                    "GroupBy builder has already been consumed",
                    new Object());
        }
    }

    private static final class AggregateSpec {
        final int valueKind;
        final int aggregate;
        final int fieldIndex;
        final GeneratedCallbacks.RowToLongMapper longMapper;
        final GeneratedCallbacks.RowToDoubleMapper doubleMapper;

        AggregateSpec(
                int valueKind,
                int aggregate,
                int fieldIndex,
                GeneratedCallbacks.RowToLongMapper longMapper,
                GeneratedCallbacks.RowToDoubleMapper doubleMapper) {
            this.valueKind = valueKind;
            this.aggregate = aggregate;
            this.fieldIndex = fieldIndex;
            this.longMapper = longMapper;
            this.doubleMapper = doubleMapper;
        }

        boolean floating() {
            return doubleMapper != null;
        }

        boolean needsFloatingSequence() {
            return floating()
                    && (aggregate == SUM
                    || aggregate == AVERAGE
                    || aggregate == SUMMARY);
        }
    }

    private static final class GroupState {
        private static final int INITIAL_CAPACITY = 1024;

        long[] representatives;
        long[] primitiveKeys;
        int[] buckets;
        int[] hashNext;
        long[] counts;
        long[] lows;
        long[] highs;
        long[] integralMins;
        long[] integralMaxs;
        double[] floatingExtrema;
        int[] valueHeads;
        int[] valueTails;
        int[] valueNext;
        double[] floatingValues;
        final int upper;
        final Object provenance;
        int size;
        int valueSize;

        GroupState(
                int upper,
                int expectedGroups,
                int keyKind,
                AggregateSpec aggregate,
                Object provenance) {
            this.upper = upper;
            this.provenance = provenance;
            int initial = Math.min(upper, expectedGroups);
            representatives = new long[initial];
            primitiveKeys = keyKind == KEY_REFERENCE ? null : new long[initial];
            hashNext = new int[initial];
            counts = new long[initial];
            int bucketCount = bucketCount(initial, provenance);
            buckets = new int[bucketCount];
            lows = aggregate.longMapper == null ? null : new long[initial];
            highs = aggregate.longMapper == null ? null : new long[initial];
            integralMins = aggregate.longMapper == null ? null : new long[initial];
            integralMaxs = aggregate.longMapper == null ? null : new long[initial];
            floatingExtrema = aggregate.doubleMapper == null
                    ? null : new double[initial];
            if (aggregate.needsFloatingSequence()) {
                valueHeads = new int[initial];
                valueTails = new int[initial];
                valueNext = new int[initial];
                floatingValues = new double[initial];
            } else {
                valueHeads = null;
                valueTails = null;
                valueNext = null;
                floatingValues = null;
            }
        }

        int groupFor(
                TableChunkDirectory directory,
                GeneratedTableLayout layout,
                int fieldIndex,
                long locator) {
            if (primitiveKeys != null) {
                return groupForPrimitive(directory, layout, fieldIndex, locator);
            }
            int bucket = ((int) mix(layout.hashField(
                    directory, locator, fieldIndex))) & (buckets.length - 1);
            for (int link = buckets[bucket]; link != 0; link = hashNext[link - 1]) {
                int candidate = link - 1;
                if (layout.fieldEquals(
                        directory,
                        representatives[candidate],
                        locator,
                        fieldIndex)) {
                    return candidate;
                }
            }
            ensureGroupCapacity(
                    directory, layout, fieldIndex, true);
            bucket = ((int) mix(layout.hashField(
                    directory, locator, fieldIndex))) & (buckets.length - 1);
            int created = size++;
            representatives[created] = locator;
            hashNext[created] = buckets[bucket];
            buckets[bucket] = created + 1;
            return created;
        }

        private int groupForPrimitive(
                TableChunkDirectory directory,
                GeneratedTableLayout layout,
                int fieldIndex,
                long locator) {
            long key = primitiveKey(directory, layout, fieldIndex, locator);
            int bucket = ((int) mix(key)) & (buckets.length - 1);
            for (int link = buckets[bucket]; link != 0; link = hashNext[link - 1]) {
                int candidate = link - 1;
                if (primitiveKeys[candidate] == key) return candidate;
            }
            ensureGroupCapacity(directory, layout, fieldIndex, true);
            bucket = ((int) mix(key)) & (buckets.length - 1);
            int created = size++;
            representatives[created] = locator;
            primitiveKeys[created] = key;
            hashNext[created] = buckets[bucket];
            buckets[bucket] = created + 1;
            return created;
        }

        int groupForReference(
                TableChunkDirectory directory,
                GeneratedTableLayout layout,
                int fieldIndex,
                long locator) {
            for (int candidate = 0; candidate < size; candidate++) {
                if (layout.fieldEquals(
                        directory,
                        representatives[candidate],
                        locator,
                        fieldIndex)) return candidate;
            }
            ensureGroupCapacity(
                    directory, layout, fieldIndex, false);
            int created = size++;
            representatives[created] = locator;
            return created;
        }

        void add(
                BoundRowPlan bound,
                int group,
                long locator,
                AggregateSpec aggregate) {
            long previous = counts[group]++;
            if (aggregate.aggregate == 0) return;
            if (aggregate.longMapper != null) {
                long value = RowExecutionSupport.callbackMapLong(
                        bound, locator, aggregate.longMapper, false);
                if (previous == 0L) {
                    integralMins[group] = value;
                    integralMaxs[group] = value;
                } else {
                    if (value < integralMins[group]) integralMins[group] = value;
                    if (value > integralMaxs[group]) integralMaxs[group] = value;
                }
                if (aggregate.aggregate == SUM
                        || aggregate.aggregate == AVERAGE
                        || aggregate.aggregate == SUMMARY) {
                    add128(group, value);
                }
                return;
            }
            double value = RowExecutionSupport.callbackMapDouble(
                    bound, locator, aggregate.doubleMapper, false);
            if (previous == 0L) floatingExtrema[group] = value;
            else if ((aggregate.aggregate == MIN || aggregate.aggregate == SUMMARY)
                    && Double.compare(value, floatingExtrema[group]) < 0) {
                floatingExtrema[group] = value;
            } else if (aggregate.aggregate == MAX
                    && Double.compare(value, floatingExtrema[group]) > 0) {
                floatingExtrema[group] = value;
            }
            if (aggregate.aggregate == SUMMARY && previous != 0L
                    && Double.compare(value, floatingExtrema[group]) > 0) {
                // summary max is completed in a separate pass below
            }
            if (aggregate.needsFloatingSequence()) appendFloating(group, value);
        }

        Object finish(AggregateSpec aggregate, Object provenance) {
            if (aggregate.aggregate == 0) {
                return Arrays.copyOf(counts, size);
            }
            if (aggregate.longMapper != null) {
                if (aggregate.aggregate == MIN || aggregate.aggregate == MAX) {
                    long[] extrema = aggregate.aggregate == MIN
                            ? integralMins : integralMaxs;
                    if (aggregate.valueKind == VALUE_INT) {
                        int[] result = new int[size];
                        for (int i = 0; i < size; i++) result[i] = (int) extrema[i];
                        return result;
                    }
                    return Arrays.copyOf(extrema, size);
                }
                if (aggregate.aggregate == SUMMARY) {
                    SomaLongSummary[] result = new SomaLongSummary[size];
                    for (int i = 0; i < size; i++) {
                        long sum = longValue(i, provenance);
                        result[i] = SomaSharedSecrets.longSummaryAccess().create(
                                counts[i], integralMins[i], integralMaxs[i],
                                sum, ((double) sum) / counts[i]);
                    }
                    return result;
                }
                if (aggregate.aggregate == AVERAGE) {
                    double[] result = new double[size];
                    for (int i = 0; i < size; i++) {
                        result[i] = doubleValue(i) / counts[i];
                    }
                    return result;
                }
                long[] result = new long[size];
                for (int i = 0; i < size; i++) result[i] = longValue(i, provenance);
                return result;
            }
            if (aggregate.aggregate == MIN || aggregate.aggregate == MAX) {
                return Arrays.copyOf(floatingExtrema, size);
            }
            double[] sums = floatingSums();
            if (aggregate.aggregate == SUM) return sums;
            if (aggregate.aggregate == AVERAGE) {
                for (int i = 0; i < size; i++) sums[i] /= counts[i];
                return sums;
            }
            SomaDoubleSummary[] result = new SomaDoubleSummary[size];
            for (int group = 0; group < size; group++) {
                double min = 0.0d;
                double max = 0.0d;
                boolean first = true;
                for (int link = valueHeads[group]; link != 0; link = valueNext[link - 1]) {
                    double value = floatingValues[link - 1];
                    if (first) { min = value; max = value; first = false; }
                    else {
                        if (Double.compare(value, min) < 0) min = value;
                        if (Double.compare(value, max) > 0) max = value;
                    }
                }
                result[group] = SomaSharedSecrets.doubleSummaryAccess().create(
                        counts[group], min, max, sums[group], sums[group] / counts[group]);
            }
            return result;
        }

        private void add128(int group, long value) {
            long before = lows[group];
            lows[group] += value;
            highs[group] += value < 0L ? -1L : 0L;
            if (Long.compareUnsigned(lows[group], before) < 0) highs[group]++;
        }

        private long longValue(int group, Object provenance) {
            long high = highs[group];
            long low = lows[group];
            if ((high == 0L && low >= 0L) || (high == -1L && low < 0L)) return low;
            throw SomaFailures.failure(
                    SomaFailureCode.ARITHMETIC_OVERFLOW,
                    SomaOperation.QUERY,
                    "grouped integer aggregate exceeds signed long range",
                    provenance);
        }

        private double doubleValue(int group) {
            double unsignedLow = (double) (lows[group] & Long.MAX_VALUE);
            if (lows[group] < 0L) unsignedLow += 0x1.0p63;
            return (double) highs[group] * 0x1.0p64 + unsignedLow;
        }

        private void appendFloating(int group, double value) {
            ensureValueCapacity();
            int entry = valueSize++;
            floatingValues[entry] = value;
            if (valueHeads[group] == 0) valueHeads[group] = entry + 1;
            else valueNext[valueTails[group] - 1] = entry + 1;
            valueTails[group] = entry + 1;
        }

        private void ensureGroupCapacity(
                TableChunkDirectory directory,
                GeneratedTableLayout layout,
                int fieldIndex,
                boolean hashed) {
            if (size < representatives.length) return;
            int next = nextCapacity(representatives.length, upper, provenance);
            representatives = Arrays.copyOf(representatives, next);
            if (primitiveKeys != null) {
                primitiveKeys = Arrays.copyOf(primitiveKeys, next);
            }
            hashNext = Arrays.copyOf(hashNext, next);
            counts = Arrays.copyOf(counts, next);
            if (lows != null) {
                lows = Arrays.copyOf(lows, next);
                highs = Arrays.copyOf(highs, next);
                integralMins = Arrays.copyOf(integralMins, next);
                integralMaxs = Arrays.copyOf(integralMaxs, next);
            }
            if (floatingExtrema != null) {
                floatingExtrema = Arrays.copyOf(floatingExtrema, next);
            }
            if (valueHeads != null) {
                valueHeads = Arrays.copyOf(valueHeads, next);
                valueTails = Arrays.copyOf(valueTails, next);
            }
            if (!hashed) return;
            int[] nextBuckets = new int[bucketCount(next, provenance)];
            for (int group = 0; group < size; group++) {
                long hash = primitiveKeys == null
                        ? layout.hashField(
                                directory,
                                representatives[group],
                                fieldIndex)
                        : primitiveKeys[group];
                int bucket = ((int) mix(hash)) & (nextBuckets.length - 1);
                hashNext[group] = nextBuckets[bucket];
                nextBuckets[bucket] = group + 1;
            }
            buckets = nextBuckets;
        }

        private static long primitiveKey(
                TableChunkDirectory directory,
                GeneratedTableLayout layout,
                int fieldIndex,
                long locator) {
            if (layout.fieldLeafCount(fieldIndex) != 1) {
                throw new AssertionError("primitive GroupBy Field is not scalar");
            }
            int leaf = layout.fieldStart(fieldIndex);
            int slot = layout.leafSlot(leaf);
            switch (layout.leafKind(leaf)) {
                case GeneratedTableLayout.BOOLEAN:
                    return directory.booleanValue(locator, slot) ? 1L : 0L;
                case GeneratedTableLayout.BYTE:
                    return directory.byteValue(locator, slot);
                case GeneratedTableLayout.SHORT:
                    return directory.shortValue(locator, slot);
                case GeneratedTableLayout.CHAR:
                    return directory.charValue(locator, slot);
                case GeneratedTableLayout.INT:
                    return directory.intValue(locator, slot);
                case GeneratedTableLayout.LONG:
                    return directory.longValue(locator, slot);
                default:
                    throw new AssertionError("unsupported primitive GroupBy Field");
            }
        }

        private void ensureValueCapacity() {
            if (valueSize < floatingValues.length) return;
            int next = nextCapacity(floatingValues.length, upper, provenance);
            floatingValues = Arrays.copyOf(floatingValues, next);
            valueNext = Arrays.copyOf(valueNext, next);
        }

        private static int nextCapacity(
                int current,
                int limit,
                Object provenance) {
            if (current >= limit) {
                throw SomaFailures.failure(
                        SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                        SomaOperation.QUERY,
                        "GroupBy cardinality exceeds bound input cardinality",
                        provenance);
            }
            if (current == 0) return Math.min(limit, INITIAL_CAPACITY);
            long doubled = (long) current << 1;
            return (int) Math.min((long) limit, doubled);
        }

        private double[] floatingSums() {
            double[] result = new double[size];
            int maximumGroupSize = 0;
            for (int group = 0; group < size; group++) {
                maximumGroupSize = Math.max(
                        maximumGroupSize, (int) counts[group]);
            }
            double[] scratch = new double[maximumGroupSize];
            for (int group = 0; group < size; group++) {
                int count = 0;
                for (int link = valueHeads[group]; link != 0; link = valueNext[link - 1]) {
                    scratch[count++] = floatingValues[link - 1];
                }
                result[group] = canonicalSum(scratch, count);
            }
            return result;
        }

        private static int bucketCount(int expected, Object provenance) {
            if (expected <= 1) return 1;
            if (expected > (1 << 30)) {
                throw SomaFailures.failure(
                        SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                        SomaOperation.QUERY,
                        "GroupBy hash table exceeds Java array boundary",
                        provenance);
            }
            int result = 1;
            while (result < expected) result <<= 1;
            return result;
        }

        private static long mix(long value) {
            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdL;
            value ^= value >>> 33;
            value *= 0xc4ceb9fe1a85ec53L;
            return value ^ (value >>> 33);
        }

        private static double canonicalSum(double[] values, int size) {
            int blocks = 0;
            for (int start = 0; start < size;) {
                int length = Math.min(1024, size - start);
                values[blocks++] = pairwise(values, start, length);
                start += length;
            }
            return pairwise(values, 0, blocks);
        }

        private static double pairwise(double[] values, int start, int length) {
            if (length == 0) return 0.0d;
            for (int width = 1; width < length;) {
                int step = width << 1;
                for (int index = 0; index + width < length; index += step) {
                    values[start + index] = values[start + index]
                            + values[start + index + width];
                }
                width = step;
            }
            return values[start];
        }

    }
}
