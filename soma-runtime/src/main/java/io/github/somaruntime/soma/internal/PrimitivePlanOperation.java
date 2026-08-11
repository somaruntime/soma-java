package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaDoublePredicate;
import io.github.somaruntime.soma.SomaDoubleSummary;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaIntPredicate;
import io.github.somaruntime.soma.SomaLongPredicate;
import io.github.somaruntime.soma.SomaLongSummary;
import io.github.somaruntime.soma.SomaOperation;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Unboxed execution of arbitrary primitive mapper chains. */
strictfp final class PrimitivePlanOperation {

    private PrimitivePlanOperation() {}

    static long count(final PrimitivePlan plan) {
        return terminal(plan, 0L, (bound, ignored) -> {
            final long[] count = new long[1]; visitBound(bound, plan, value -> { count[0]++; return true; });
            return count[0];
        });
    }

    static boolean match(final PrimitivePlan plan, final Object predicate, final int mode) {
        return terminal(plan, 0L, (bound, ignored) -> {
            final boolean[] result = new boolean[] {mode != 0};
            visitBound(bound, plan, value -> {
                boolean matched = test(bound, plan.valueKind, predicate, value);
                if (mode == 0 && matched) { result[0] = true; return false; }
                if (mode == 1 && !matched) { result[0] = false; return false; }
                if (mode == 2 && matched) { result[0] = false; return false; }
                return true;
            });
            return result[0];
        });
    }

    static OptionalInt findInt(final PrimitivePlan plan) {
        final long[] value = first(plan); return value == null ? OptionalInt.empty() : OptionalInt.of((int) value[0]);
    }
    static Optional<Boolean> findBoolean(final PrimitivePlan plan) {
        final long[] value = first(plan);
        return value == null ? Optional.<Boolean>empty()
                : Optional.of(value[0] != 0L);
    }
    static OptionalLong findLong(final PrimitivePlan plan) {
        final long[] value = first(plan); return value == null ? OptionalLong.empty() : OptionalLong.of(value[0]);
    }
    static OptionalDouble findDouble(final PrimitivePlan plan) {
        final long[] value = first(plan); return value == null ? OptionalDouble.empty()
                : OptionalDouble.of(floatingValue(plan.valueKind, value[0]));
    }

    private static long[] first(final PrimitivePlan plan) {
        return terminal(plan, 0L, (bound, ignored) -> {
            final long[] value = new long[1]; final boolean[] present = new boolean[1];
            visitBound(bound, plan, candidate -> { value[0] = candidate; present[0] = true; return false; });
            return present[0] ? value : null;
        });
    }

    static OptionalInt extremumInt(final PrimitivePlan plan, final boolean maximum) {
        Long result = extremum(plan, maximum); return result == null ? OptionalInt.empty() : OptionalInt.of(result.intValue());
    }
    static OptionalLong extremumLong(final PrimitivePlan plan, final boolean maximum) {
        Long result = extremum(plan, maximum); return result == null ? OptionalLong.empty() : OptionalLong.of(result.longValue());
    }
    static OptionalDouble extremumDouble(final PrimitivePlan plan, final boolean maximum) {
        Long result = extremum(plan, maximum); return result == null ? OptionalDouble.empty()
                : OptionalDouble.of(floatingValue(plan.valueKind, result.longValue()));
    }

    private static Long extremum(final PrimitivePlan plan, final boolean maximum) {
        return terminal(plan, 0L, (bound, ignored) -> {
            final long[] result = new long[1]; final boolean[] present = new boolean[1];
            visitBound(bound, plan, value -> {
                if (!present[0] || (maximum
                        ? compare(plan.valueKind, value, result[0]) > 0
                        : compare(plan.valueKind, value, result[0]) < 0)) {
                    result[0] = value; present[0] = true;
                }
                return true;
            });
            return present[0] ? Long.valueOf(result[0]) : null;
        });
    }

    static long sumIntegral(final PrimitivePlan plan) {
        return terminal(plan, 0L, (bound, ignored) -> {
            Signed128Accumulator result = new Signed128Accumulator();
            visitBound(bound, plan, value -> { result.add(integralValue(plan.valueKind, value)); return true; });
            return result.longValue(bound.provenance);
        });
    }

    static double sumDouble(final PrimitivePlan plan) {
        return floatingValues(plan, false).sum;
    }

    static OptionalDouble averageIntegral(final PrimitivePlan plan) {
        return terminal(plan, 0L, (bound, ignored) -> {
            Signed128Accumulator result = new Signed128Accumulator(); final long[] count = new long[1];
            visitBound(bound, plan, value -> { result.add(integralValue(plan.valueKind, value)); count[0]++; return true; });
            return count[0] == 0L ? OptionalDouble.empty()
                    : OptionalDouble.of(result.doubleValue() / (double) count[0]);
        });
    }

    static OptionalDouble averageDouble(final PrimitivePlan plan) {
        FloatingResult result = floatingValues(plan, false);
        return result.count == 0L ? OptionalDouble.empty() : OptionalDouble.of(result.sum / result.count);
    }

    static SomaLongSummary summaryIntegral(final PrimitivePlan plan) {
        return terminal(plan, 0L, (bound, ignored) -> {
            Signed128Accumulator sum = new Signed128Accumulator(); final long[] count = new long[1];
            final long[] min = new long[1]; final long[] max = new long[1];
            visitBound(bound, plan, raw -> {
                long value = integralValue(plan.valueKind, raw);
                if (count[0] == 0L) { min[0] = value; max[0] = value; }
                else { if (value < min[0]) min[0] = value; if (value > max[0]) max[0] = value; }
                sum.add(value); count[0]++; return true;
            });
            long exact = sum.longValue(bound.provenance);
            return SomaSharedSecrets.longSummaryAccess().create(count[0], count[0] == 0 ? 0 : min[0],
                    count[0] == 0 ? 0 : max[0], exact, count[0] == 0 ? 0.0d : exact / (double) count[0]);
        });
    }

    static SomaDoubleSummary summaryDouble(final PrimitivePlan plan) {
        FloatingResult result = floatingValues(plan, true);
        return SomaSharedSecrets.doubleSummaryAccess().create(result.count,
                result.count == 0 ? 0.0d : result.min, result.count == 0 ? 0.0d : result.max,
                result.sum, result.count == 0 ? 0.0d : result.sum / result.count);
    }

    static int[] toIntArray(final PrimitivePlan plan) {
        return terminal(plan, 8L, (bound, ignored) -> {
            int[] staging = new int[materializedLength(bound, plan)];
            final int[] size = new int[1]; visitBound(bound, plan, value -> { staging[size[0]++] = (int) value; return true; });
            int[] result = new int[size[0]]; System.arraycopy(staging, 0, result, 0, size[0]); return result;
        });
    }
    static boolean[] toBooleanArray(final PrimitivePlan plan) {
        return terminal(plan, 8L, (bound, ignored) -> {
            boolean[] staging = new boolean[materializedLength(bound, plan)];
            final int[] size = new int[1]; visitBound(bound, plan, value -> { staging[size[0]++] = value != 0L; return true; });
            boolean[] result = new boolean[size[0]]; System.arraycopy(staging, 0, result, 0, size[0]); return result;
        });
    }
    static byte[] toByteArray(final PrimitivePlan plan) {
        return terminal(plan, 8L, (bound, ignored) -> {
            byte[] staging = new byte[materializedLength(bound, plan)];
            final int[] size = new int[1]; visitBound(bound, plan, value -> { staging[size[0]++] = (byte) value; return true; });
            byte[] result = new byte[size[0]]; System.arraycopy(staging, 0, result, 0, size[0]); return result;
        });
    }
    static short[] toShortArray(final PrimitivePlan plan) {
        return terminal(plan, 8L, (bound, ignored) -> {
            short[] staging = new short[materializedLength(bound, plan)];
            final int[] size = new int[1]; visitBound(bound, plan, value -> { staging[size[0]++] = (short) value; return true; });
            short[] result = new short[size[0]]; System.arraycopy(staging, 0, result, 0, size[0]); return result;
        });
    }
    static char[] toCharArray(final PrimitivePlan plan) {
        return terminal(plan, 8L, (bound, ignored) -> {
            char[] staging = new char[materializedLength(bound, plan)];
            final int[] size = new int[1]; visitBound(bound, plan, value -> { staging[size[0]++] = (char) value; return true; });
            char[] result = new char[size[0]]; System.arraycopy(staging, 0, result, 0, size[0]); return result;
        });
    }
    static long[] toLongArray(final PrimitivePlan plan) {
        return terminal(plan, 16L, (bound, ignored) -> {
            long[] staging = new long[materializedLength(bound, plan)];
            final int[] size = new int[1]; visitBound(bound, plan, value -> { staging[size[0]++] = value; return true; });
            long[] result = new long[size[0]]; System.arraycopy(staging, 0, result, 0, size[0]); return result;
        });
    }
    static double[] toDoubleArray(final PrimitivePlan plan) {
        return terminal(plan, 16L, (bound, ignored) -> {
            double[] staging = new double[materializedLength(bound, plan)];
            final int[] size = new int[1]; visitBound(bound, plan, value -> { staging[size[0]++] = floatingValue(plan.valueKind, value); return true; });
            double[] result = new double[size[0]]; System.arraycopy(staging, 0, result, 0, size[0]); return result;
        });
    }
    static float[] toFloatArray(final PrimitivePlan plan) {
        return terminal(plan, 8L, (bound, ignored) -> {
            float[] staging = new float[materializedLength(bound, plan)];
            final int[] size = new int[1]; visitBound(bound, plan, value -> { staging[size[0]++] = Float.intBitsToFloat((int) value); return true; });
            float[] result = new float[size[0]]; System.arraycopy(staging, 0, result, 0, size[0]); return result;
        });
    }

    static void forEach(final PrimitivePlan plan, final Object action) {
        terminal(plan, 0L, (bound, ignored) -> { visitBound(bound, plan, value -> { accept(bound, plan.valueKind, action, value); return true; }); return null; });
    }

    static List<Object> toBoxedList(final PrimitivePlan plan) {
        return terminal(plan, 48L, (bound, ignored) -> {
            ArrayList<Object> result = new ArrayList<Object>(
                    materializedLength(bound, plan));
            visitBound(bound, plan, value -> {
                result.add(box(plan.valueKind, value)); return true;
            });
            return result;
        });
    }

    static String explain(final PrimitivePlan plan) {
        return QueryOperation.execute(
                plan.rows, new QueryOperation.BoundWork<String>() {
        @Override public long scratchBytes(BoundRowPlan bound) {
            return 0L;
        }
        @Override public String run(BoundRowPlan bound) {
            StringBuilder result = new StringBuilder(
                    QueryOperation.explainBound(plan.rows, bound));
            result.append(" primitiveRoot=").append(plan.rootKind).append(':').append(plan.rootValueKind).append(" stages=[");
            for (int i = 0; i < plan.stages.size(); i++) { if (i != 0) result.append(','); result.append(plan.stages.get(i).kind); }
            return result.append("] output=").append(plan.valueKind)
                    .append(" estimatedPrimitiveTemporaryPeakBytes=")
                    .append(estimatedExecutionScratch(bound, plan, 0L))
                    .toString();
        }});
    }

    static long[] valuesForTesting(final PrimitivePlan plan) {
        return terminal(plan, 16L, (bound, ignored) -> {
            long[] staging = new long[materializedLength(bound, plan)];
            final int[] size = new int[1];
            visitBound(bound, plan, value -> {
                staging[size[0]++] = value; return true;
            });
            long[] result = new long[size[0]];
            System.arraycopy(staging, 0, result, 0, size[0]);
            return result;
        });
    }

    private static FloatingResult floatingValues(final PrimitivePlan plan, final boolean extrema) {
        return terminal(plan, 8L, (bound, ignored) -> {
            double[] values = new double[materializedLength(bound, plan)];
            final int[] size = new int[1]; final double[] min = new double[1]; final double[] max = new double[1];
            visitBound(bound, plan, raw -> {
                double value = floatingValue(plan.valueKind, raw);
                if (size[0] == 0) { min[0] = value; max[0] = value; }
                else if (extrema) { if (Double.compare(value, min[0]) < 0) min[0] = value; if (Double.compare(value, max[0]) > 0) max[0] = value; }
                values[size[0]++] = value; return true;
            });
            return new FloatingResult(size[0], min[0], max[0], canonicalSum(values, size[0]));
        });
    }

    private static <T> T terminal(final PrimitivePlan plan, final long extraPerElement, final Terminal<T> terminal) {
        return QueryOperation.execute(plan.rows, new QueryOperation.BoundWork<T>() {
            @Override public long scratchBytes(BoundRowPlan bound) {
                return estimatedExecutionScratch(
                        bound, plan, extraPerElement);
            }
            @Override public T run(BoundRowPlan bound) { return terminal.run(bound, plan); }
        });
    }

    private static long estimatedExecutionScratch(
            BoundRowPlan bound,
            PrimitivePlan plan,
            long extraPerElement) {
                long result = QueryOperation.rowExecutionScratch(bound);
                if (plan.mapped != null && plan.mapped.hasOwnStatefulStage()) {
                    result = QueryOperation.addScratch(
                            result,
                            MappedQueryOperation.mappedStatefulScratch(bound),
                            bound.provenance);
                }
                if (plan.hasOwnStatefulStage()) {
                    result = QueryOperation.addScratch(
                            result,
                            RowExecutionSupport.arrayBytes(
                                    bound.root.size, 64L, bound.provenance),
                            bound.provenance);
                }
                if (extraPerElement != 0L) {
                    result = QueryOperation.addScratch(
                            result,
                            RowExecutionSupport.arrayBytes(
                                    plan.outputUpperBound(bound.root.size),
                                    extraPerElement,
                                    bound.provenance),
                            bound.provenance);
                }
                return result;
    }

    private static int materializedLength(
            BoundRowPlan bound,
            PrimitivePlan plan) {
        return RowExecutionSupport.arrayLength(
                plan.outputUpperBound(bound.root.size), bound.provenance);
    }

    static void visitBound(BoundRowPlan bound, PrimitivePlan plan, Visitor visitor) {
        if (plan.hasOwnStatefulStage()) {
            LongValueBuffer values = values(bound, plan);
            for (int i = 0; i < values.size(); i++) if (!visitor.visit(values.get(i))) return;
            return;
        }
        final long[] counters = new long[plan.stages.size()];
        if (primitiveLimitReached(plan, counters)) return;
        visitRoot(bound, plan, raw -> {
            if (primitiveLimitReached(plan, counters)) return false;
            long value = raw;
            for (int i = 0; i < plan.stages.size(); i++) {
                PrimitivePlan.Stage stage = plan.stages.get(i);
                switch (stage.kind) {
                    case FILTER: if (!test(bound, stage.input, stage.callback, value)) return !primitiveLimitReached(plan, counters); break;
                    case MAP: case CONVERT: value = apply(bound, stage, value); break;
                    case SKIP: if (counters[i] < stage.count) { counters[i]++; return !primitiveLimitReached(plan, counters); } break;
                    case LIMIT: if (counters[i] >= stage.count) return false; counters[i]++; break;
                    default: throw new AssertionError("stateful primitive stage in streaming path");
                }
            }
            return visitor.visit(value) && !primitiveLimitReached(plan, counters);
        });
    }

    private static boolean primitiveLimitReached(
            PrimitivePlan plan,
            long[] counters) {
        for (int index = 0; index < plan.stages.size(); index++) {
            PrimitivePlan.Stage stage = plan.stages.get(index);
            if (stage.kind == PrimitivePlan.StageKind.LIMIT
                    && counters[index] >= stage.count) return true;
        }
        return false;
    }

    private static LongValueBuffer values(BoundRowPlan bound, PrimitivePlan plan) {
        LongValueBuffer result = new LongValueBuffer(bound.root.size, bound.provenance);
        int firstStateful = primitiveNextStateful(plan.stages, 0);
        collectPrimitiveSegment(bound, plan, 0, firstStateful, result);
        int position = firstStateful;
        while (position < plan.stages.size()) {
            PrimitivePlan.Stage stage = plan.stages.get(position);
            switch (stage.kind) {
                case DISTINCT: distinct(bound, result, stage.input); break;
                case SORTED: sort(result, stage.input); break;
                default: throw new AssertionError("expected stateful primitive stage");
            }
            int next = primitiveNextStateful(plan.stages, position + 1);
            compactPrimitiveSegment(
                    bound, result, plan.stages, position + 1, next);
            position = next;
        }
        return result;
    }

    private static void collectPrimitiveSegment(
            final BoundRowPlan bound,
            final PrimitivePlan plan,
            final int from,
            final int to,
            final LongValueBuffer output) {
        final long[] counters = new long[to - from];
        if (primitiveSegmentLimitReached(plan.stages, from, to, counters)) return;
        visitRoot(bound, plan, raw -> {
            long value = raw;
            for (int position = from; position < to; position++) {
                PrimitivePlan.Stage stage = plan.stages.get(position);
                int counter = position - from;
                switch (stage.kind) {
                    case FILTER:
                        if (!test(bound, stage.input, stage.callback, value)) {
                            return !primitiveSegmentLimitReached(
                                    plan.stages, from, to, counters);
                        }
                        break;
                    case MAP:
                    case CONVERT:
                        value = apply(bound, stage, value);
                        break;
                    case SKIP:
                        if (counters[counter] < stage.count) {
                            counters[counter]++;
                            return !primitiveSegmentLimitReached(
                                    plan.stages, from, to, counters);
                        }
                        break;
                    case LIMIT:
                        if (counters[counter] >= stage.count) return false;
                        counters[counter]++;
                        break;
                    default:
                        throw new AssertionError("stateful primitive stage inside segment");
                }
            }
            output.add(value);
            return !primitiveSegmentLimitReached(
                    plan.stages, from, to, counters);
        });
    }

    private static void compactPrimitiveSegment(
            BoundRowPlan bound,
            LongValueBuffer values,
            java.util.List<PrimitivePlan.Stage> stages,
            int from,
            int to) {
        if (from == to) return;
        long[] counters = new long[to - from];
        int output = 0;
        inputLoop:
        for (int input = 0; input < values.size(); input++) {
            if (primitiveSegmentLimitReached(stages, from, to, counters)) break;
            long value = values.get(input);
            for (int position = from; position < to; position++) {
                PrimitivePlan.Stage stage = stages.get(position);
                int counter = position - from;
                switch (stage.kind) {
                    case FILTER:
                        if (!test(bound, stage.input, stage.callback, value)) {
                            continue inputLoop;
                        }
                        break;
                    case MAP:
                    case CONVERT:
                        value = apply(bound, stage, value);
                        break;
                    case SKIP:
                        if (counters[counter] < stage.count) {
                            counters[counter]++;
                            continue inputLoop;
                        }
                        break;
                    case LIMIT:
                        if (counters[counter] >= stage.count) break inputLoop;
                        counters[counter]++;
                        break;
                    default:
                        throw new AssertionError("stateful primitive stage inside segment");
                }
            }
            values.set(output++, value);
        }
        values.size(output);
    }

    private static boolean primitiveSegmentLimitReached(
            java.util.List<PrimitivePlan.Stage> stages,
            int from,
            int to,
            long[] counters) {
        for (int position = from; position < to; position++) {
            PrimitivePlan.Stage stage = stages.get(position);
            if (stage.kind == PrimitivePlan.StageKind.LIMIT
                    && counters[position - from] >= stage.count) return true;
        }
        return false;
    }

    private static int primitiveNextStateful(
            java.util.List<PrimitivePlan.Stage> stages,
            int from) {
        for (int position = from; position < stages.size(); position++) {
            PrimitivePlan.StageKind kind = stages.get(position).kind;
            if (kind == PrimitivePlan.StageKind.DISTINCT
                    || kind == PrimitivePlan.StageKind.SORTED) return position;
        }
        return stages.size();
    }

    private static void visitRoot(BoundRowPlan bound, PrimitivePlan plan, Visitor visitor) {
        if (plan.rootKind == PrimitivePlan.RootKind.ROW) {
            if (plan.rootFieldIndex >= 0
                    && plan.rows.isDirectFieldProjection(plan.rootFieldIndex)) {
                if (plan.rows.isParallel()) {
                    ParallelRowScheduler.requireAvailable(bound);
                }
                GeneratedTableLayout layout = plan.rows.owner().layout();
                int leaf = layout.fieldStart(plan.rootFieldIndex);
                if (layout.fieldLeafCount(plan.rootFieldIndex) != 1) {
                    throw new AssertionError(
                            "primitive Field must have one physical leaf");
                }
                int slot = layout.leafSlot(leaf);
                byte kind = layout.leafKind(leaf);
                TableChunkDirectory directory = bound.root.directory;
                int chunkRows = directory.chunkRows();
                int remaining = bound.root.size;
                for (int ordinal = 0;
                        ordinal < directory.chunkCount() && remaining > 0;
                        ordinal++) {
                    TableChunk chunk = directory.chunk(ordinal);
                    int logicalRows = Math.min(chunkRows, remaining);
                    if (!chunk.visitPrimitive(
                            kind, slot, logicalRows, visitor)) return;
                    remaining -= logicalRows;
                }
            } else {
                RowExecutor.visit(
                        bound,
                        locator -> visitor.visit(rowRoot(bound, plan, locator)));
            }
        } else {
            MappedQueryOperation.visitBound(bound, plan.mapped, value -> visitor.visit(mappedRoot(bound, plan, value)));
        }
    }

    private static long rowRoot(BoundRowPlan bound, PrimitivePlan plan, int locator) {
        if (plan.rootFieldIndex >= 0) {
            return directFieldRoot(bound, plan, locator);
        }
        switch (plan.rootValueKind) {
            case BOOLEAN: return RowExecutionSupport.callbackMapBoolean(bound, locator, (GeneratedCallbacks.RowToBooleanMapper) plan.rootMapper, plan.rootApplicationCallback) ? 1L : 0L;
            case BYTE: return RowExecutionSupport.callbackMapByte(bound, locator, (GeneratedCallbacks.RowToByteMapper) plan.rootMapper, plan.rootApplicationCallback);
            case SHORT: return RowExecutionSupport.callbackMapShort(bound, locator, (GeneratedCallbacks.RowToShortMapper) plan.rootMapper, plan.rootApplicationCallback);
            case CHAR: return RowExecutionSupport.callbackMapChar(bound, locator, (GeneratedCallbacks.RowToCharMapper) plan.rootMapper, plan.rootApplicationCallback);
            case INT: return RowExecutionSupport.callbackMapInt(bound, locator, (GeneratedCallbacks.RowToIntMapper) plan.rootMapper, plan.rootApplicationCallback);
            case LONG: return RowExecutionSupport.callbackMapLong(bound, locator, (GeneratedCallbacks.RowToLongMapper) plan.rootMapper, plan.rootApplicationCallback);
            case FLOAT: return Float.floatToIntBits(RowExecutionSupport.callbackMapFloat(bound, locator, (GeneratedCallbacks.RowToFloatMapper) plan.rootMapper, plan.rootApplicationCallback));
            case DOUBLE: return Double.doubleToLongBits(RowExecutionSupport.callbackMapDouble(bound, locator, (GeneratedCallbacks.RowToDoubleMapper) plan.rootMapper, plan.rootApplicationCallback));
            default: throw new AssertionError();
        }
    }

    private static long directFieldRoot(
            BoundRowPlan bound,
            PrimitivePlan plan,
            int locator) {
        GeneratedTableLayout layout = plan.rows.owner().layout();
        int leaf = layout.fieldStart(plan.rootFieldIndex);
        if (layout.fieldLeafCount(plan.rootFieldIndex) != 1) {
            throw new AssertionError("primitive Field must have one physical leaf");
        }
        int slot = layout.leafSlot(leaf);
        switch (plan.rootValueKind) {
            case BOOLEAN:
                return bound.root.directory.booleanValue(locator, slot) ? 1L : 0L;
            case BYTE:
                return bound.root.directory.byteValue(locator, slot);
            case SHORT:
                return bound.root.directory.shortValue(locator, slot);
            case CHAR:
                return bound.root.directory.charValue(locator, slot);
            case INT:
                return bound.root.directory.intValue(locator, slot);
            case LONG:
                return bound.root.directory.longValue(locator, slot);
            case FLOAT:
                return Float.floatToIntBits(
                        bound.root.directory.floatValue(locator, slot));
            case DOUBLE:
                return Double.doubleToLongBits(
                        bound.root.directory.doubleValue(locator, slot));
            default:
                throw new AssertionError();
        }
    }

    @SuppressWarnings("unchecked") private static long mappedRoot(BoundRowPlan bound, PrimitivePlan plan, Object value) {
        CallbackExecutionScope.enter();
        try {
            switch (plan.rootValueKind) {
                case INT: return ((io.github.somaruntime.soma.SomaToIntFunction<Object>) plan.rootMapper).applyAsInt(value);
                case LONG: return ((io.github.somaruntime.soma.SomaToLongFunction<Object>) plan.rootMapper).applyAsLong(value);
                case DOUBLE: return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaToDoubleFunction<Object>) plan.rootMapper).applyAsDouble(value));
                default: throw new AssertionError();
            }
        } catch (Exception failure) { throw SomaFailures.callbackFailure(SomaOperation.QUERY, failure, bound.provenance); }
        finally { CallbackExecutionScope.exit(); }
    }

    private static boolean test(BoundRowPlan bound, PrimitivePlan.ValueKind kind, Object callback, long raw) {
        CallbackExecutionScope.enter();
        try {
            switch (kind) {
                case BOOLEAN: return ((io.github.somaruntime.soma.SomaBooleanPredicate) callback).test(raw != 0L);
                case BYTE: return ((io.github.somaruntime.soma.SomaBytePredicate) callback).test((byte) raw);
                case SHORT: return ((io.github.somaruntime.soma.SomaShortPredicate) callback).test((short) raw);
                case CHAR: return ((io.github.somaruntime.soma.SomaCharPredicate) callback).test((char) raw);
                case INT: return ((SomaIntPredicate) callback).test((int) raw);
                case LONG: return ((SomaLongPredicate) callback).test(raw);
                case FLOAT: return ((io.github.somaruntime.soma.SomaFloatPredicate) callback).test(Float.intBitsToFloat((int) raw));
                case DOUBLE: return ((SomaDoublePredicate) callback).test(Double.longBitsToDouble(raw));
                default: throw new AssertionError();
            }
        } catch (Exception failure) { throw SomaFailures.callbackFailure(SomaOperation.QUERY, failure, bound.provenance); }
        finally { CallbackExecutionScope.exit(); }
    }

    private static long apply(BoundRowPlan bound, PrimitivePlan.Stage stage, long raw) {
        CallbackExecutionScope.enter();
        try {
            if (stage.input == PrimitivePlan.ValueKind.BOOLEAN && stage.output == PrimitivePlan.ValueKind.BOOLEAN)
                return ((io.github.somaruntime.soma.SomaBooleanUnaryOperator) stage.callback).applyAsBoolean(raw != 0L) ? 1L : 0L;
            if (stage.input == PrimitivePlan.ValueKind.BYTE && stage.output == PrimitivePlan.ValueKind.BYTE)
                return ((io.github.somaruntime.soma.SomaByteUnaryOperator) stage.callback).applyAsByte((byte) raw);
            if (stage.input == PrimitivePlan.ValueKind.SHORT && stage.output == PrimitivePlan.ValueKind.SHORT)
                return ((io.github.somaruntime.soma.SomaShortUnaryOperator) stage.callback).applyAsShort((short) raw);
            if (stage.input == PrimitivePlan.ValueKind.CHAR && stage.output == PrimitivePlan.ValueKind.CHAR)
                return ((io.github.somaruntime.soma.SomaCharUnaryOperator) stage.callback).applyAsChar((char) raw);
            if (stage.input == PrimitivePlan.ValueKind.INT && stage.output == PrimitivePlan.ValueKind.INT)
                return ((io.github.somaruntime.soma.SomaIntUnaryOperator) stage.callback).applyAsInt((int) raw);
            if (stage.input == PrimitivePlan.ValueKind.LONG && stage.output == PrimitivePlan.ValueKind.LONG)
                return ((io.github.somaruntime.soma.SomaLongUnaryOperator) stage.callback).applyAsLong(raw);
            if (stage.input == PrimitivePlan.ValueKind.FLOAT && stage.output == PrimitivePlan.ValueKind.FLOAT)
                return Float.floatToIntBits(((io.github.somaruntime.soma.SomaFloatUnaryOperator) stage.callback).applyAsFloat(Float.intBitsToFloat((int) raw)));
            if (stage.input == PrimitivePlan.ValueKind.DOUBLE && stage.output == PrimitivePlan.ValueKind.DOUBLE)
                return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaDoubleUnaryOperator) stage.callback).applyAsDouble(Double.longBitsToDouble(raw)));
            if (stage.input == PrimitivePlan.ValueKind.BOOLEAN && stage.output == PrimitivePlan.ValueKind.INT)
                return ((io.github.somaruntime.soma.SomaBooleanToIntFunction) stage.callback).applyAsInt(raw != 0L);
            if (stage.input == PrimitivePlan.ValueKind.BOOLEAN && stage.output == PrimitivePlan.ValueKind.LONG)
                return ((io.github.somaruntime.soma.SomaBooleanToLongFunction) stage.callback).applyAsLong(raw != 0L);
            if (stage.input == PrimitivePlan.ValueKind.BOOLEAN && stage.output == PrimitivePlan.ValueKind.DOUBLE)
                return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaBooleanToDoubleFunction) stage.callback).applyAsDouble(raw != 0L));
            if (stage.input == PrimitivePlan.ValueKind.BYTE && stage.output == PrimitivePlan.ValueKind.INT)
                return ((io.github.somaruntime.soma.SomaByteToIntFunction) stage.callback).applyAsInt((byte) raw);
            if (stage.input == PrimitivePlan.ValueKind.BYTE && stage.output == PrimitivePlan.ValueKind.LONG)
                return ((io.github.somaruntime.soma.SomaByteToLongFunction) stage.callback).applyAsLong((byte) raw);
            if (stage.input == PrimitivePlan.ValueKind.BYTE && stage.output == PrimitivePlan.ValueKind.DOUBLE)
                return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaByteToDoubleFunction) stage.callback).applyAsDouble((byte) raw));
            if (stage.input == PrimitivePlan.ValueKind.SHORT && stage.output == PrimitivePlan.ValueKind.INT)
                return ((io.github.somaruntime.soma.SomaShortToIntFunction) stage.callback).applyAsInt((short) raw);
            if (stage.input == PrimitivePlan.ValueKind.SHORT && stage.output == PrimitivePlan.ValueKind.LONG)
                return ((io.github.somaruntime.soma.SomaShortToLongFunction) stage.callback).applyAsLong((short) raw);
            if (stage.input == PrimitivePlan.ValueKind.SHORT && stage.output == PrimitivePlan.ValueKind.DOUBLE)
                return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaShortToDoubleFunction) stage.callback).applyAsDouble((short) raw));
            if (stage.input == PrimitivePlan.ValueKind.CHAR && stage.output == PrimitivePlan.ValueKind.INT)
                return ((io.github.somaruntime.soma.SomaCharToIntFunction) stage.callback).applyAsInt((char) raw);
            if (stage.input == PrimitivePlan.ValueKind.CHAR && stage.output == PrimitivePlan.ValueKind.LONG)
                return ((io.github.somaruntime.soma.SomaCharToLongFunction) stage.callback).applyAsLong((char) raw);
            if (stage.input == PrimitivePlan.ValueKind.CHAR && stage.output == PrimitivePlan.ValueKind.DOUBLE)
                return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaCharToDoubleFunction) stage.callback).applyAsDouble((char) raw));
            if (stage.input == PrimitivePlan.ValueKind.INT && stage.output == PrimitivePlan.ValueKind.LONG)
                return ((io.github.somaruntime.soma.SomaIntToLongFunction) stage.callback).applyAsLong((int) raw);
            if (stage.input == PrimitivePlan.ValueKind.INT && stage.output == PrimitivePlan.ValueKind.DOUBLE)
                return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaIntToDoubleFunction) stage.callback).applyAsDouble((int) raw));
            if (stage.input == PrimitivePlan.ValueKind.LONG && stage.output == PrimitivePlan.ValueKind.INT)
                return ((io.github.somaruntime.soma.SomaLongToIntFunction) stage.callback).applyAsInt(raw);
            if (stage.input == PrimitivePlan.ValueKind.LONG && stage.output == PrimitivePlan.ValueKind.DOUBLE)
                return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaLongToDoubleFunction) stage.callback).applyAsDouble(raw));
            if (stage.input == PrimitivePlan.ValueKind.FLOAT && stage.output == PrimitivePlan.ValueKind.INT)
                return ((io.github.somaruntime.soma.SomaFloatToIntFunction) stage.callback).applyAsInt(Float.intBitsToFloat((int) raw));
            if (stage.input == PrimitivePlan.ValueKind.FLOAT && stage.output == PrimitivePlan.ValueKind.LONG)
                return ((io.github.somaruntime.soma.SomaFloatToLongFunction) stage.callback).applyAsLong(Float.intBitsToFloat((int) raw));
            if (stage.input == PrimitivePlan.ValueKind.FLOAT && stage.output == PrimitivePlan.ValueKind.DOUBLE)
                return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaFloatToDoubleFunction) stage.callback).applyAsDouble(Float.intBitsToFloat((int) raw)));
            if (stage.input == PrimitivePlan.ValueKind.DOUBLE && stage.output == PrimitivePlan.ValueKind.INT)
                return ((io.github.somaruntime.soma.SomaDoubleToIntFunction) stage.callback).applyAsInt(Double.longBitsToDouble(raw));
            if (stage.input == PrimitivePlan.ValueKind.DOUBLE && stage.output == PrimitivePlan.ValueKind.LONG)
                return ((io.github.somaruntime.soma.SomaDoubleToLongFunction) stage.callback).applyAsLong(Double.longBitsToDouble(raw));
            throw new AssertionError("unknown primitive conversion");
        } catch (Exception failure) { throw SomaFailures.callbackFailure(SomaOperation.QUERY, failure, bound.provenance); }
        finally { CallbackExecutionScope.exit(); }
    }

    private static void accept(BoundRowPlan bound, PrimitivePlan.ValueKind kind, Object action, long raw) {
        CallbackExecutionScope.enter();
        try {
            switch (kind) {
                case BOOLEAN: ((io.github.somaruntime.soma.SomaBooleanConsumer) action).accept(raw != 0L); break;
                case BYTE: ((io.github.somaruntime.soma.SomaByteConsumer) action).accept((byte) raw); break;
                case SHORT: ((io.github.somaruntime.soma.SomaShortConsumer) action).accept((short) raw); break;
                case CHAR: ((io.github.somaruntime.soma.SomaCharConsumer) action).accept((char) raw); break;
                case INT: ((io.github.somaruntime.soma.SomaIntConsumer) action).accept((int) raw); break;
                case LONG: ((io.github.somaruntime.soma.SomaLongConsumer) action).accept(raw); break;
                case FLOAT: ((io.github.somaruntime.soma.SomaFloatConsumer) action).accept(Float.intBitsToFloat((int) raw)); break;
                case DOUBLE: ((io.github.somaruntime.soma.SomaDoubleConsumer) action).accept(Double.longBitsToDouble(raw)); break;
                default: throw new AssertionError();
            }
        } catch (Exception failure) { throw SomaFailures.callbackFailure(SomaOperation.QUERY, failure, bound.provenance); }
        finally { CallbackExecutionScope.exit(); }
    }

    private static void compact(BoundRowPlan bound, LongValueBuffer values, PrimitivePlan.Stage stage) {
        int output = 0; for (int i = 0; i < values.size(); i++) { long value = values.get(i); if (test(bound, stage.input, stage.callback, value)) values.set(output++, value); } values.size(output);
    }
    private static void distinct(
            BoundRowPlan bound,
            LongValueBuffer values,
            PrimitivePlan.ValueKind kind) {
        PrimitiveDistinctSet seen = new PrimitiveDistinctSet(
                values.size(), bound.provenance);
        int output = 0;
        for (int index = 0; index < values.size(); index++) {
            long value = values.get(index);
            if (seen.add(canonicalDistinctKey(kind, value))) {
                values.set(output++, value);
            }
        }
        values.size(output);
    }
    private static void sort(LongValueBuffer values, PrimitivePlan.ValueKind kind) {
        if (values.size() < 2) return;
        if (kind != PrimitivePlan.ValueKind.FLOAT
                && kind != PrimitivePlan.ValueKind.DOUBLE) {
            Arrays.sort(values.backing(), 0, values.size());
            return;
        }
        long[] scratch = new long[values.size()];
        mergeSort(values.backing(), scratch, 0, values.size(), kind);
    }
    private static void skip(LongValueBuffer values, long count) { if (count >= values.size()) { values.size(0); return; } int n = (int) count; int remaining = values.size() - n; System.arraycopy(values.backing(), n, values.backing(), 0, remaining); values.size(remaining); }
    private static boolean equal(PrimitivePlan.ValueKind kind, long a, long b) {
        if (kind == PrimitivePlan.ValueKind.FLOAT) {
            return Float.floatToIntBits(Float.intBitsToFloat((int) a))
                    == Float.floatToIntBits(Float.intBitsToFloat((int) b));
        }
        if (kind == PrimitivePlan.ValueKind.DOUBLE) {
            return Double.doubleToLongBits(Double.longBitsToDouble(a))
                    == Double.doubleToLongBits(Double.longBitsToDouble(b));
        }
        return a == b;
    }

    private static long canonicalDistinctKey(
            PrimitivePlan.ValueKind kind,
            long raw) {
        if (kind == PrimitivePlan.ValueKind.FLOAT) {
            return (long) Float.floatToIntBits(
                    Float.intBitsToFloat((int) raw));
        }
        if (kind == PrimitivePlan.ValueKind.DOUBLE) {
            return Double.doubleToLongBits(Double.longBitsToDouble(raw));
        }
        return raw;
    }

    private static void mergeSort(
            long[] values,
            long[] scratch,
            int from,
            int to,
            PrimitivePlan.ValueKind kind) {
        int length = to - from;
        if (length < 2) return;
        int middle = from + length / 2;
        mergeSort(values, scratch, from, middle, kind);
        mergeSort(values, scratch, middle, to, kind);
        int left = from;
        int right = middle;
        int output = from;
        while (left < middle && right < to) {
            if (compare(kind, values[left], values[right]) <= 0) {
                scratch[output++] = values[left++];
            } else {
                scratch[output++] = values[right++];
            }
        }
        while (left < middle) scratch[output++] = values[left++];
        while (right < to) scratch[output++] = values[right++];
        System.arraycopy(scratch, from, values, from, length);
    }

    /** Unboxed canonical-value set used by primitive distinct. */
    private static final class PrimitiveDistinctSet {
        private final long[] keys;
        private final byte[] occupied;
        private final int mask;

        PrimitiveDistinctSet(int expected, Object provenance) {
            if (expected > (1 << 29)) {
                throw SomaFailures.failure(
                        SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                        SomaOperation.QUERY,
                        "primitive distinct hash table exceeds Java array boundary",
                        provenance);
            }
            int capacity = 2;
            int required = Math.max(2, expected << 1);
            while (capacity < required) capacity <<= 1;
            keys = new long[capacity];
            occupied = new byte[capacity];
            mask = capacity - 1;
        }

        boolean add(long value) {
            long mixed = value;
            mixed ^= mixed >>> 33;
            mixed *= 0xff51afd7ed558ccdL;
            mixed ^= mixed >>> 33;
            mixed *= 0xc4ceb9fe1a85ec53L;
            mixed ^= mixed >>> 33;
            int slot = ((int) mixed) & mask;
            while (occupied[slot] != 0) {
                if (keys[slot] == value) return false;
                slot = (slot + 1) & mask;
            }
            occupied[slot] = 1;
            keys[slot] = value;
            return true;
        }
    }
    private static int compare(PrimitivePlan.ValueKind kind, long a, long b) {
        switch (kind) {
            case BOOLEAN: return Boolean.compare(a != 0L, b != 0L);
            case BYTE: return Byte.compare((byte) a, (byte) b);
            case SHORT: return Short.compare((short) a, (short) b);
            case CHAR: return Character.compare((char) a, (char) b);
            case INT: return Integer.compare((int) a, (int) b);
            case LONG: return Long.compare(a, b);
            case FLOAT: return Float.compare(Float.intBitsToFloat((int) a), Float.intBitsToFloat((int) b));
            case DOUBLE: return Double.compare(Double.longBitsToDouble(a), Double.longBitsToDouble(b));
            default: throw new AssertionError();
        }
    }

    private static long integralValue(PrimitivePlan.ValueKind kind, long raw) {
        switch (kind) {
            case BYTE: return (byte) raw;
            case SHORT: return (short) raw;
            case CHAR: return (char) raw;
            case INT: return (int) raw;
            case LONG: return raw;
            default: throw new AssertionError("not an integral primitive kind");
        }
    }

    private static double floatingValue(PrimitivePlan.ValueKind kind, long raw) {
        if (kind == PrimitivePlan.ValueKind.FLOAT) {
            return Float.intBitsToFloat((int) raw);
        }
        if (kind == PrimitivePlan.ValueKind.DOUBLE) {
            return Double.longBitsToDouble(raw);
        }
        throw new AssertionError("not a floating primitive kind");
    }

    private static Object box(PrimitivePlan.ValueKind kind, long raw) {
        switch (kind) {
            case BOOLEAN: return Boolean.valueOf(raw != 0L);
            case BYTE: return Byte.valueOf((byte) raw);
            case SHORT: return Short.valueOf((short) raw);
            case CHAR: return Character.valueOf((char) raw);
            case INT: return Integer.valueOf((int) raw);
            case LONG: return Long.valueOf(raw);
            case FLOAT: return Float.valueOf(Float.intBitsToFloat((int) raw));
            case DOUBLE: return Double.valueOf(Double.longBitsToDouble(raw));
            default: throw new AssertionError();
        }
    }

    private static double canonicalSum(double[] values, int size) {
        int blocks = 0;
        for (int start = 0; start < size;) {
            int length = Math.min(1024, size - start);
            values[blocks++] = pairwise(
                    values, start, length);
            start += length;
        }
        return pairwise(values, 0, blocks);
    }
    private static double pairwise(double[] values, int start, int length) {
        if (length == 0) return 0.0d;
        for (int width = 1; width < length;) {
            for (long offset = 0L;
                    offset + width < length;
                    offset += (long) width << 1) {
                values[start + (int) offset] +=
                        values[start + (int) offset + width];
            }
            if (width > Integer.MAX_VALUE / 2) break;
            width <<= 1;
        }
        return values[start];
    }

    interface Visitor extends TableChunk.PrimitiveVisitor {}
    interface Terminal<T> { T run(BoundRowPlan bound, PrimitivePlan plan); }
    private static final class FloatingResult { final long count; final double min, max, sum; FloatingResult(long count, double min, double max, double sum) { this.count = count; this.min = min; this.max = max; this.sum = sum; } }
}
