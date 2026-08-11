package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.*;
import java.util.ArrayList;
import java.util.List;

/** Slow boxed primitive-plan oracle with independent traversal and algorithms. */
final class ReferencePrimitiveInterpreter {

    private ReferencePrimitiveInterpreter() {
    }

    static long[] valuesForTesting(final PrimitivePipelineCapture frontend) {
        final CanonicalPrimitiveOperation plan =
                CanonicalPrimitiveLowering.operation(
                        frontend,
                        CanonicalPrimitiveOperation.TerminalKind.TEST,
                        null);
        return CanonicalQueryOperation.executeReferenceFamily(
                frontend.rows.owner(),
                plan.source,
                new CanonicalQueryOperation.ReferenceExtraScratch() {
            @Override public long bytes(BoundCanonicalRowOperation bound) {
                return RowExecutionSupport.arrayBytes(
                        bound.root.size, 64L, bound.provenance);
            }
        }, new CanonicalQueryOperation.ReferenceWork<long[]>() {
            @Override public long[] run(BoundCanonicalRowOperation bound) {
                ArrayList<Long> values = roots(bound, plan);
                for (CanonicalPrimitiveStage stage : plan.stages) {
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

    static long sumIntegralForTesting(PrimitivePipelineCapture frontend) {
        long[] values = valuesForTesting(frontend);
        Signed128Accumulator result = new Signed128Accumulator();
        for (long value : values) {
            result.add(integralValue(frontend.valueKind, value));
        }
        return result.longValue(frontend);
    }

    static double sumFloatingForTesting(PrimitivePipelineCapture frontend) {
        long[] raw = valuesForTesting(frontend);
        double[] values = new double[raw.length];
        for (int index = 0; index < raw.length; index++) {
            values[index] = floatingValue(frontend.valueKind, raw[index]);
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
            PrimitiveValueKind kind,
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
            PrimitiveValueKind kind,
            long raw) {
        if (kind == PrimitiveValueKind.FLOAT) {
            return Float.intBitsToFloat((int) raw);
        }
        if (kind == PrimitiveValueKind.DOUBLE) {
            return Double.longBitsToDouble(raw);
        }
        throw new AssertionError("not a floating primitive kind");
    }

    private static ArrayList<Long> roots(
            final BoundCanonicalRowOperation bound,
            final CanonicalPrimitiveOperation plan) {
        final ArrayList<Long> result = new ArrayList<Long>();
        if (plan.rootKind == CanonicalPrimitiveOperation.RootKind.ROW) {
            ReferenceCanonicalRowInterpreter.visit(bound,
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
            BoundCanonicalRowOperation bound, CanonicalPrimitiveOperation plan, int locator) {
        GeneratedQueryCursor cursor = bound.table.queryCursor();
        cursor.enter(locator);
        CallbackExecutionScope.enter();
        try {
            Object mapper = plan.rootMapper.callback;
            switch (plan.rootValueKind) {
                case BOOLEAN: return ((GeneratedCallbacks.RowToBooleanMapper) mapper).applyAsBoolean() ? 1L : 0L;
                case BYTE: return (long) ((GeneratedCallbacks.RowToByteMapper) mapper).applyAsByte();
                case SHORT: return (long) ((GeneratedCallbacks.RowToShortMapper) mapper).applyAsShort();
                case CHAR: return (long) ((GeneratedCallbacks.RowToCharMapper) mapper).applyAsChar();
                case INT: return (long) ((GeneratedCallbacks.RowToIntMapper) mapper).applyAsInt();
                case LONG: return ((GeneratedCallbacks.RowToLongMapper) mapper).applyAsLong();
                case FLOAT: return (long) Float.floatToIntBits(((GeneratedCallbacks.RowToFloatMapper) mapper).applyAsFloat());
                case DOUBLE: return Double.doubleToLongBits(((GeneratedCallbacks.RowToDoubleMapper) mapper).applyAsDouble());
                default: throw new AssertionError();
            }
        } catch (Exception failure) {
            if (!plan.rootApplicationCallback && failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            if (!plan.rootApplicationCallback) {
                throw new AssertionError(
                        "generated primitive materializer threw checked failure",
                        failure);
            }
            throw SomaFailures.callbackFailure(
                    bound.operation, failure, bound.provenance);
        } finally {
            CallbackExecutionScope.exit();
            cursor.leave();
        }
    }

    @SuppressWarnings("unchecked")
    private static Long mappedRoot(
            BoundCanonicalRowOperation bound, CanonicalPrimitiveOperation plan, Object value) {
        try {
            switch (plan.rootValueKind) {
                case INT: return (long) ((SomaToIntFunction<Object>) plan.rootMapper.callback).applyAsInt(value);
                case LONG: return ((SomaToLongFunction<Object>) plan.rootMapper.callback).applyAsLong(value);
                case DOUBLE: return Double.doubleToLongBits(((SomaToDoubleFunction<Object>) plan.rootMapper.callback).applyAsDouble(value));
                default: throw new AssertionError("invalid mapped primitive root");
            }
        } catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    SomaOperation.QUERY, failure, bound.provenance);
        }
    }

    private static void filter(
            BoundCanonicalRowOperation bound,
            ArrayList<Long> values,
            CanonicalPrimitiveStage stage) {
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
            BoundCanonicalRowOperation bound,
            ArrayList<Long> values,
            CanonicalPrimitiveStage stage) {
        for (int index = 0; index < values.size(); index++) {
            values.set(index, Long.valueOf(apply(
                    bound, stage, values.get(index).longValue())));
        }
    }

    private static void distinct(
            ArrayList<Long> values,
            PrimitiveValueKind kind) {
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
            PrimitiveValueKind kind) {
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
            BoundCanonicalRowOperation bound,
            PrimitiveValueKind kind,
            HostCallbackHandle handle,
            long raw) {
        if (handle == null
                || handle.kind != HostCallbackHandle.Kind.PRIMITIVE_PREDICATE) {
            throw new AssertionError("invalid primitive predicate handle");
        }
        Object callback = handle.callback;
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
            BoundCanonicalRowOperation bound,
            CanonicalPrimitiveStage stage,
            long raw) {
        if (stage.callback == null
                || stage.callback.kind != HostCallbackHandle.Kind.PRIMITIVE_MAPPER) {
            throw new AssertionError("invalid primitive mapper handle");
        }
        Object callback = stage.callback.callback;
        try {
            switch (stage.input) {
                case BOOLEAN:
                    if (stage.output == PrimitiveValueKind.BOOLEAN) return ((SomaBooleanUnaryOperator) callback).applyAsBoolean(raw != 0L) ? 1L : 0L;
                    if (stage.output == PrimitiveValueKind.INT) return ((SomaBooleanToIntFunction) callback).applyAsInt(raw != 0L);
                    if (stage.output == PrimitiveValueKind.LONG) return ((SomaBooleanToLongFunction) callback).applyAsLong(raw != 0L);
                    return Double.doubleToLongBits(((SomaBooleanToDoubleFunction) callback).applyAsDouble(raw != 0L));
                case BYTE:
                    if (stage.output == PrimitiveValueKind.BYTE) return ((SomaByteUnaryOperator) callback).applyAsByte((byte) raw);
                    if (stage.output == PrimitiveValueKind.INT) return ((SomaByteToIntFunction) callback).applyAsInt((byte) raw);
                    if (stage.output == PrimitiveValueKind.LONG) return ((SomaByteToLongFunction) callback).applyAsLong((byte) raw);
                    return Double.doubleToLongBits(((SomaByteToDoubleFunction) callback).applyAsDouble((byte) raw));
                case SHORT:
                    if (stage.output == PrimitiveValueKind.SHORT) return ((SomaShortUnaryOperator) callback).applyAsShort((short) raw);
                    if (stage.output == PrimitiveValueKind.INT) return ((SomaShortToIntFunction) callback).applyAsInt((short) raw);
                    if (stage.output == PrimitiveValueKind.LONG) return ((SomaShortToLongFunction) callback).applyAsLong((short) raw);
                    return Double.doubleToLongBits(((SomaShortToDoubleFunction) callback).applyAsDouble((short) raw));
                case CHAR:
                    if (stage.output == PrimitiveValueKind.CHAR) return ((SomaCharUnaryOperator) callback).applyAsChar((char) raw);
                    if (stage.output == PrimitiveValueKind.INT) return ((SomaCharToIntFunction) callback).applyAsInt((char) raw);
                    if (stage.output == PrimitiveValueKind.LONG) return ((SomaCharToLongFunction) callback).applyAsLong((char) raw);
                    return Double.doubleToLongBits(((SomaCharToDoubleFunction) callback).applyAsDouble((char) raw));
                case INT:
                    if (stage.output == PrimitiveValueKind.INT) return ((SomaIntUnaryOperator) callback).applyAsInt((int) raw);
                    if (stage.output == PrimitiveValueKind.LONG) return ((SomaIntToLongFunction) callback).applyAsLong((int) raw);
                    return Double.doubleToLongBits(((SomaIntToDoubleFunction) callback).applyAsDouble((int) raw));
                case LONG:
                    if (stage.output == PrimitiveValueKind.LONG) return ((SomaLongUnaryOperator) callback).applyAsLong(raw);
                    if (stage.output == PrimitiveValueKind.INT) return ((SomaLongToIntFunction) callback).applyAsInt(raw);
                    return Double.doubleToLongBits(((SomaLongToDoubleFunction) callback).applyAsDouble(raw));
                case FLOAT:
                    float floating = Float.intBitsToFloat((int) raw);
                    if (stage.output == PrimitiveValueKind.FLOAT) return Float.floatToIntBits(((SomaFloatUnaryOperator) callback).applyAsFloat(floating));
                    if (stage.output == PrimitiveValueKind.INT) return ((SomaFloatToIntFunction) callback).applyAsInt(floating);
                    if (stage.output == PrimitiveValueKind.LONG) return ((SomaFloatToLongFunction) callback).applyAsLong(floating);
                    return Double.doubleToLongBits(((SomaFloatToDoubleFunction) callback).applyAsDouble(floating));
                case DOUBLE:
                    double decimal = Double.longBitsToDouble(raw);
                    if (stage.output == PrimitiveValueKind.DOUBLE) return Double.doubleToLongBits(((SomaDoubleUnaryOperator) callback).applyAsDouble(decimal));
                    if (stage.output == PrimitiveValueKind.INT) return ((SomaDoubleToIntFunction) callback).applyAsInt(decimal);
                    return ((SomaDoubleToLongFunction) callback).applyAsLong(decimal);
                default: throw new AssertionError();
            }
        } catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    SomaOperation.QUERY, failure, bound.provenance);
        }
    }

    private static boolean equal(
            PrimitiveValueKind kind, long left, long right) {
        if (kind == PrimitiveValueKind.FLOAT) {
            return Float.floatToIntBits(Float.intBitsToFloat((int) left))
                    == Float.floatToIntBits(Float.intBitsToFloat((int) right));
        }
        if (kind == PrimitiveValueKind.DOUBLE) {
            return Double.doubleToLongBits(Double.longBitsToDouble(left))
                    == Double.doubleToLongBits(Double.longBitsToDouble(right));
        }
        return left == right;
    }

    private static int compare(
            PrimitiveValueKind kind, long left, long right) {
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
