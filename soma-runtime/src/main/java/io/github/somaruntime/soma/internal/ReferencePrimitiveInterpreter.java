package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.*;
import java.util.ArrayList;
import java.util.List;

/** Slow boxed primitive-plan oracle with independent traversal and algorithms. */
final class ReferencePrimitiveInterpreter {

    private ReferencePrimitiveInterpreter() {
    }

    static long[] valuesForTesting(final PrimitivePlan plan) {
        return QueryOperation.execute(plan.rows, new QueryOperation.BoundWork<long[]>() {
            @Override public long scratchBytes(BoundRowPlan bound) {
                return RowExecutionSupport.arrayBytes(
                        bound.root.size, 64L, bound.provenance);
            }

            @Override public long[] run(BoundRowPlan bound) {
                ArrayList<Long> values = roots(bound, plan);
                for (PrimitivePlan.Stage stage : plan.stages) {
                    switch (stage.kind) {
                        case FILTER: filter(bound, values, stage); break;
                        case MAP:
                        case CONVERT: map(bound, values, stage); break;
                        case DISTINCT: distinct(values, stage.input); break;
                        case SORTED: insertionSort(values, stage.input); break;
                        case SKIP: skip(values, stage.count); break;
                        case LIMIT: limit(values, stage.count); break;
                        default: throw new AssertionError("unknown primitive stage");
                    }
                }
                long[] result = new long[values.size()];
                for (int index = 0; index < result.length; index++) {
                    result[index] = values.get(index).longValue();
                }
                return result;
            }
        });
    }

    static long sumIntegralForTesting(PrimitivePlan plan) {
        long[] values = valuesForTesting(plan);
        Signed128Accumulator result = new Signed128Accumulator();
        for (long value : values) result.add(integralValue(plan.valueKind, value));
        return result.longValue(plan);
    }

    static double sumFloatingForTesting(PrimitivePlan plan) {
        long[] raw = valuesForTesting(plan);
        double[] values = new double[raw.length];
        for (int index = 0; index < raw.length; index++) {
            values[index] = floatingValue(plan.valueKind, raw[index]);
        }
        int blocks = 0;
        for (int start = 0; start < values.length;) {
            int length = Math.min(1024, values.length - start);
            values[blocks++] = referencePairwise(values, start, length);
            start += length;
        }
        return referencePairwise(values, 0, blocks);
    }

    private static double referencePairwise(
            double[] values,
            int start,
            int length) {
        if (length == 0) return 0.0d;
        int width = 1;
        while (width < length) {
            int offset = 0;
            while (offset + width < length) {
                values[start + offset] = values[start + offset]
                        + values[start + offset + width];
                offset += width << 1;
            }
            if (width > Integer.MAX_VALUE / 2) break;
            width <<= 1;
        }
        return values[start];
    }

    private static long integralValue(
            PrimitivePlan.ValueKind kind,
            long raw) {
        switch (kind) {
            case BYTE: return (byte) raw;
            case SHORT: return (short) raw;
            case CHAR: return (char) raw;
            case INT: return (int) raw;
            case LONG: return raw;
            default: throw new AssertionError("not an integral primitive kind");
        }
    }

    private static double floatingValue(
            PrimitivePlan.ValueKind kind,
            long raw) {
        if (kind == PrimitivePlan.ValueKind.FLOAT) {
            return Float.intBitsToFloat((int) raw);
        }
        if (kind == PrimitivePlan.ValueKind.DOUBLE) {
            return Double.longBitsToDouble(raw);
        }
        throw new AssertionError("not a floating primitive kind");
    }

    private static ArrayList<Long> roots(
            final BoundRowPlan bound,
            final PrimitivePlan plan) {
        final ArrayList<Long> result = new ArrayList<Long>();
        if (plan.rootKind == PrimitivePlan.RootKind.ROW) {
            ReferenceRowInterpreter.visit(bound,
                    locator -> { result.add(rowRoot(bound, plan, locator)); return true; });
        } else {
            for (Object value : ReferenceMappedInterpreter.valuesBound(
                    bound, plan.mapped)) {
                result.add(mappedRoot(bound, plan, value));
            }
        }
        return result;
    }

    private static Long rowRoot(
            BoundRowPlan bound, PrimitivePlan plan, long locator) {
        switch (plan.rootValueKind) {
            case BOOLEAN: return RowExecutionSupport.callbackMapBoolean(bound, locator, (GeneratedCallbacks.RowToBooleanMapper) plan.rootMapper, plan.rootApplicationCallback) ? 1L : 0L;
            case BYTE: return (long) RowExecutionSupport.callbackMapByte(bound, locator, (GeneratedCallbacks.RowToByteMapper) plan.rootMapper, plan.rootApplicationCallback);
            case SHORT: return (long) RowExecutionSupport.callbackMapShort(bound, locator, (GeneratedCallbacks.RowToShortMapper) plan.rootMapper, plan.rootApplicationCallback);
            case CHAR: return (long) RowExecutionSupport.callbackMapChar(bound, locator, (GeneratedCallbacks.RowToCharMapper) plan.rootMapper, plan.rootApplicationCallback);
            case INT: return (long) RowExecutionSupport.callbackMapInt(bound, locator, (GeneratedCallbacks.RowToIntMapper) plan.rootMapper, plan.rootApplicationCallback);
            case LONG: return RowExecutionSupport.callbackMapLong(bound, locator, (GeneratedCallbacks.RowToLongMapper) plan.rootMapper, plan.rootApplicationCallback);
            case FLOAT: return (long) Float.floatToIntBits(RowExecutionSupport.callbackMapFloat(bound, locator, (GeneratedCallbacks.RowToFloatMapper) plan.rootMapper, plan.rootApplicationCallback));
            case DOUBLE: return Double.doubleToLongBits(RowExecutionSupport.callbackMapDouble(bound, locator, (GeneratedCallbacks.RowToDoubleMapper) plan.rootMapper, plan.rootApplicationCallback));
            default: throw new AssertionError();
        }
    }

    @SuppressWarnings("unchecked")
    private static Long mappedRoot(
            BoundRowPlan bound, PrimitivePlan plan, Object value) {
        try {
            switch (plan.rootValueKind) {
                case INT: return (long) ((SomaToIntFunction<Object>) plan.rootMapper).applyAsInt(value);
                case LONG: return ((SomaToLongFunction<Object>) plan.rootMapper).applyAsLong(value);
                case DOUBLE: return Double.doubleToLongBits(((SomaToDoubleFunction<Object>) plan.rootMapper).applyAsDouble(value));
                default: throw new AssertionError("invalid mapped primitive root");
            }
        } catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    SomaOperation.QUERY, failure, bound.provenance);
        }
    }

    private static void filter(
            BoundRowPlan bound,
            ArrayList<Long> values,
            PrimitivePlan.Stage stage) {
        int output = 0;
        for (int input = 0; input < values.size(); input++) {
            long value = values.get(input).longValue();
            if (test(bound, stage.input, stage.callback, value)) {
                values.set(output++, Long.valueOf(value));
            }
        }
        trim(values, output);
    }

    private static void map(
            BoundRowPlan bound,
            ArrayList<Long> values,
            PrimitivePlan.Stage stage) {
        for (int index = 0; index < values.size(); index++) {
            values.set(index, Long.valueOf(apply(
                    bound, stage, values.get(index).longValue())));
        }
    }

    private static void distinct(
            ArrayList<Long> values,
            PrimitivePlan.ValueKind kind) {
        int output = 0;
        for (int input = 0; input < values.size(); input++) {
            long candidate = values.get(input).longValue();
            boolean seen = false;
            for (int prior = 0; prior < output; prior++) {
                if (equal(kind, values.get(prior).longValue(), candidate)) {
                    seen = true; break;
                }
            }
            if (!seen) values.set(output++, Long.valueOf(candidate));
        }
        trim(values, output);
    }

    private static void insertionSort(
            ArrayList<Long> values,
            PrimitivePlan.ValueKind kind) {
        for (int index = 1; index < values.size(); index++) {
            Long candidate = values.get(index);
            int position = index;
            while (position > 0 && compare(
                    kind, values.get(position - 1).longValue(),
                    candidate.longValue()) > 0) {
                values.set(position, values.get(position - 1));
                position--;
            }
            values.set(position, candidate);
        }
    }

    private static void skip(ArrayList<Long> values, long count) {
        int skipped = (int) Math.min((long) values.size(), count);
        int output = 0;
        for (int input = skipped; input < values.size(); input++) {
            values.set(output++, values.get(input));
        }
        trim(values, output);
    }

    private static void limit(ArrayList<Long> values, long count) {
        if (count < values.size()) trim(values, (int) count);
    }

    private static boolean test(
            BoundRowPlan bound,
            PrimitivePlan.ValueKind kind,
            Object callback,
            long raw) {
        try {
            switch (kind) {
                case BOOLEAN: return ((SomaBooleanPredicate) callback).test(raw != 0L);
                case BYTE: return ((SomaBytePredicate) callback).test((byte) raw);
                case SHORT: return ((SomaShortPredicate) callback).test((short) raw);
                case CHAR: return ((SomaCharPredicate) callback).test((char) raw);
                case INT: return ((SomaIntPredicate) callback).test((int) raw);
                case LONG: return ((SomaLongPredicate) callback).test(raw);
                case FLOAT: return ((SomaFloatPredicate) callback).test(Float.intBitsToFloat((int) raw));
                case DOUBLE: return ((SomaDoublePredicate) callback).test(Double.longBitsToDouble(raw));
                default: throw new AssertionError();
            }
        } catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    SomaOperation.QUERY, failure, bound.provenance);
        }
    }

    private static long apply(
            BoundRowPlan bound,
            PrimitivePlan.Stage stage,
            long raw) {
        try {
            switch (stage.input) {
                case BOOLEAN:
                    if (stage.output == PrimitivePlan.ValueKind.BOOLEAN) return ((SomaBooleanUnaryOperator) stage.callback).applyAsBoolean(raw != 0L) ? 1L : 0L;
                    if (stage.output == PrimitivePlan.ValueKind.INT) return ((SomaBooleanToIntFunction) stage.callback).applyAsInt(raw != 0L);
                    if (stage.output == PrimitivePlan.ValueKind.LONG) return ((SomaBooleanToLongFunction) stage.callback).applyAsLong(raw != 0L);
                    return Double.doubleToLongBits(((SomaBooleanToDoubleFunction) stage.callback).applyAsDouble(raw != 0L));
                case BYTE:
                    if (stage.output == PrimitivePlan.ValueKind.BYTE) return ((SomaByteUnaryOperator) stage.callback).applyAsByte((byte) raw);
                    if (stage.output == PrimitivePlan.ValueKind.INT) return ((SomaByteToIntFunction) stage.callback).applyAsInt((byte) raw);
                    if (stage.output == PrimitivePlan.ValueKind.LONG) return ((SomaByteToLongFunction) stage.callback).applyAsLong((byte) raw);
                    return Double.doubleToLongBits(((SomaByteToDoubleFunction) stage.callback).applyAsDouble((byte) raw));
                case SHORT:
                    if (stage.output == PrimitivePlan.ValueKind.SHORT) return ((SomaShortUnaryOperator) stage.callback).applyAsShort((short) raw);
                    if (stage.output == PrimitivePlan.ValueKind.INT) return ((SomaShortToIntFunction) stage.callback).applyAsInt((short) raw);
                    if (stage.output == PrimitivePlan.ValueKind.LONG) return ((SomaShortToLongFunction) stage.callback).applyAsLong((short) raw);
                    return Double.doubleToLongBits(((SomaShortToDoubleFunction) stage.callback).applyAsDouble((short) raw));
                case CHAR:
                    if (stage.output == PrimitivePlan.ValueKind.CHAR) return ((SomaCharUnaryOperator) stage.callback).applyAsChar((char) raw);
                    if (stage.output == PrimitivePlan.ValueKind.INT) return ((SomaCharToIntFunction) stage.callback).applyAsInt((char) raw);
                    if (stage.output == PrimitivePlan.ValueKind.LONG) return ((SomaCharToLongFunction) stage.callback).applyAsLong((char) raw);
                    return Double.doubleToLongBits(((SomaCharToDoubleFunction) stage.callback).applyAsDouble((char) raw));
                case INT:
                    if (stage.output == PrimitivePlan.ValueKind.INT) return ((SomaIntUnaryOperator) stage.callback).applyAsInt((int) raw);
                    if (stage.output == PrimitivePlan.ValueKind.LONG) return ((SomaIntToLongFunction) stage.callback).applyAsLong((int) raw);
                    return Double.doubleToLongBits(((SomaIntToDoubleFunction) stage.callback).applyAsDouble((int) raw));
                case LONG:
                    if (stage.output == PrimitivePlan.ValueKind.LONG) return ((SomaLongUnaryOperator) stage.callback).applyAsLong(raw);
                    if (stage.output == PrimitivePlan.ValueKind.INT) return ((SomaLongToIntFunction) stage.callback).applyAsInt(raw);
                    return Double.doubleToLongBits(((SomaLongToDoubleFunction) stage.callback).applyAsDouble(raw));
                case FLOAT:
                    float floating = Float.intBitsToFloat((int) raw);
                    if (stage.output == PrimitivePlan.ValueKind.FLOAT) return Float.floatToIntBits(((SomaFloatUnaryOperator) stage.callback).applyAsFloat(floating));
                    if (stage.output == PrimitivePlan.ValueKind.INT) return ((SomaFloatToIntFunction) stage.callback).applyAsInt(floating);
                    if (stage.output == PrimitivePlan.ValueKind.LONG) return ((SomaFloatToLongFunction) stage.callback).applyAsLong(floating);
                    return Double.doubleToLongBits(((SomaFloatToDoubleFunction) stage.callback).applyAsDouble(floating));
                case DOUBLE:
                    double decimal = Double.longBitsToDouble(raw);
                    if (stage.output == PrimitivePlan.ValueKind.DOUBLE) return Double.doubleToLongBits(((SomaDoubleUnaryOperator) stage.callback).applyAsDouble(decimal));
                    if (stage.output == PrimitivePlan.ValueKind.INT) return ((SomaDoubleToIntFunction) stage.callback).applyAsInt(decimal);
                    return ((SomaDoubleToLongFunction) stage.callback).applyAsLong(decimal);
                default: throw new AssertionError();
            }
        } catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    SomaOperation.QUERY, failure, bound.provenance);
        }
    }

    private static boolean equal(
            PrimitivePlan.ValueKind kind, long left, long right) {
        if (kind == PrimitivePlan.ValueKind.FLOAT) {
            return Float.floatToIntBits(Float.intBitsToFloat((int) left))
                    == Float.floatToIntBits(Float.intBitsToFloat((int) right));
        }
        if (kind == PrimitivePlan.ValueKind.DOUBLE) {
            return Double.doubleToLongBits(Double.longBitsToDouble(left))
                    == Double.doubleToLongBits(Double.longBitsToDouble(right));
        }
        return left == right;
    }

    private static int compare(
            PrimitivePlan.ValueKind kind, long left, long right) {
        switch (kind) {
            case BOOLEAN: return Boolean.compare(left != 0L, right != 0L);
            case BYTE: return Byte.compare((byte) left, (byte) right);
            case SHORT: return Short.compare((short) left, (short) right);
            case CHAR: return Character.compare((char) left, (char) right);
            case INT: return Integer.compare((int) left, (int) right);
            case LONG: return Long.compare(left, right);
            case FLOAT: return Float.compare(Float.intBitsToFloat((int) left), Float.intBitsToFloat((int) right));
            case DOUBLE: return Double.compare(Double.longBitsToDouble(left), Double.longBitsToDouble(right));
            default: throw new AssertionError();
        }
    }

    private static void trim(ArrayList<Long> values, int size) {
        while (values.size() > size) values.remove(values.size() - 1);
    }
}
