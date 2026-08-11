package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.*;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** One-shot exact primitive Field stream carrier for all eight Java primitives. */
public final class GeneratedPrimitiveValuePipeline {

    private final PrimitivePipelineCapture plan;
    private final AtomicBoolean consumed = new AtomicBoolean();

    GeneratedPrimitiveValuePipeline(PrimitivePipelineCapture plan) {
        this.plan = plan;
    }

    public GeneratedPrimitiveValuePipeline parallel() {
        claim();
        return next(plan.parallel());
    }

    public GeneratedPrimitiveValuePipeline filterBoolean(SomaBooleanPredicate callback) { return filter(PrimitiveValueKind.BOOLEAN, callback); }
    public GeneratedPrimitiveValuePipeline filterByte(SomaBytePredicate callback) { return filter(PrimitiveValueKind.BYTE, callback); }
    public GeneratedPrimitiveValuePipeline filterShort(SomaShortPredicate callback) { return filter(PrimitiveValueKind.SHORT, callback); }
    public GeneratedPrimitiveValuePipeline filterChar(SomaCharPredicate callback) { return filter(PrimitiveValueKind.CHAR, callback); }
    public GeneratedPrimitiveValuePipeline filterInt(SomaIntPredicate callback) { return filter(PrimitiveValueKind.INT, callback); }
    public GeneratedPrimitiveValuePipeline filterLong(SomaLongPredicate callback) { return filter(PrimitiveValueKind.LONG, callback); }
    public GeneratedPrimitiveValuePipeline filterFloat(SomaFloatPredicate callback) { return filter(PrimitiveValueKind.FLOAT, callback); }
    public GeneratedPrimitiveValuePipeline filterDouble(SomaDoublePredicate callback) { return filter(PrimitiveValueKind.DOUBLE, callback); }

    public GeneratedPrimitiveValuePipeline mapBoolean(SomaBooleanUnaryOperator callback) { return map(PrimitiveValueKind.BOOLEAN, callback); }
    public GeneratedPrimitiveValuePipeline mapByte(SomaByteUnaryOperator callback) { return map(PrimitiveValueKind.BYTE, callback); }
    public GeneratedPrimitiveValuePipeline mapShort(SomaShortUnaryOperator callback) { return map(PrimitiveValueKind.SHORT, callback); }
    public GeneratedPrimitiveValuePipeline mapChar(SomaCharUnaryOperator callback) { return map(PrimitiveValueKind.CHAR, callback); }
    public GeneratedPrimitiveValuePipeline mapInt(SomaIntUnaryOperator callback) { return map(PrimitiveValueKind.INT, callback); }
    public GeneratedPrimitiveValuePipeline mapLong(SomaLongUnaryOperator callback) { return map(PrimitiveValueKind.LONG, callback); }
    public GeneratedPrimitiveValuePipeline mapFloat(SomaFloatUnaryOperator callback) { return map(PrimitiveValueKind.FLOAT, callback); }
    public GeneratedPrimitiveValuePipeline mapDouble(SomaDoubleUnaryOperator callback) { return map(PrimitiveValueKind.DOUBLE, callback); }

    public SomaIntStream mapBooleanToInt(SomaBooleanToIntFunction callback) { return toInt(PrimitiveValueKind.BOOLEAN, callback); }
    public SomaLongStream mapBooleanToLong(SomaBooleanToLongFunction callback) { return toLong(PrimitiveValueKind.BOOLEAN, callback); }
    public SomaDoubleStream mapBooleanToDouble(SomaBooleanToDoubleFunction callback) { return toDouble(PrimitiveValueKind.BOOLEAN, callback); }
    public SomaIntStream mapByteToInt(SomaByteToIntFunction callback) { return toInt(PrimitiveValueKind.BYTE, callback); }
    public SomaLongStream mapByteToLong(SomaByteToLongFunction callback) { return toLong(PrimitiveValueKind.BYTE, callback); }
    public SomaDoubleStream mapByteToDouble(SomaByteToDoubleFunction callback) { return toDouble(PrimitiveValueKind.BYTE, callback); }
    public SomaIntStream mapShortToInt(SomaShortToIntFunction callback) { return toInt(PrimitiveValueKind.SHORT, callback); }
    public SomaLongStream mapShortToLong(SomaShortToLongFunction callback) { return toLong(PrimitiveValueKind.SHORT, callback); }
    public SomaDoubleStream mapShortToDouble(SomaShortToDoubleFunction callback) { return toDouble(PrimitiveValueKind.SHORT, callback); }
    public SomaIntStream mapCharToInt(SomaCharToIntFunction callback) { return toInt(PrimitiveValueKind.CHAR, callback); }
    public SomaLongStream mapCharToLong(SomaCharToLongFunction callback) { return toLong(PrimitiveValueKind.CHAR, callback); }
    public SomaDoubleStream mapCharToDouble(SomaCharToDoubleFunction callback) { return toDouble(PrimitiveValueKind.CHAR, callback); }
    public SomaLongStream mapIntToLong(SomaIntToLongFunction callback) { return toLong(PrimitiveValueKind.INT, callback); }
    public SomaDoubleStream mapIntToDouble(SomaIntToDoubleFunction callback) { return toDouble(PrimitiveValueKind.INT, callback); }
    public SomaIntStream mapLongToInt(SomaLongToIntFunction callback) { return toInt(PrimitiveValueKind.LONG, callback); }
    public SomaDoubleStream mapLongToDouble(SomaLongToDoubleFunction callback) { return toDouble(PrimitiveValueKind.LONG, callback); }
    public SomaIntStream mapFloatToInt(SomaFloatToIntFunction callback) { return toInt(PrimitiveValueKind.FLOAT, callback); }
    public SomaLongStream mapFloatToLong(SomaFloatToLongFunction callback) { return toLong(PrimitiveValueKind.FLOAT, callback); }
    public SomaDoubleStream mapFloatToDouble(SomaFloatToDoubleFunction callback) { return toDouble(PrimitiveValueKind.FLOAT, callback); }
    public SomaIntStream mapDoubleToInt(SomaDoubleToIntFunction callback) { return toInt(PrimitiveValueKind.DOUBLE, callback); }
    public SomaLongStream mapDoubleToLong(SomaDoubleToLongFunction callback) { return toLong(PrimitiveValueKind.DOUBLE, callback); }

    public GeneratedPrimitiveValuePipeline distinct() { claim(); return next(plan.distinct()); }
    public GeneratedPrimitiveValuePipeline sorted() { claim(); return next(plan.sorted()); }
    public GeneratedPrimitiveValuePipeline top(long count) { requireCount(count, "top"); claim(); return next(plan.sorted().limit(count)); }
    public GeneratedPrimitiveValuePipeline skip(long count) { requireCount(count, "skip"); claim(); return next(plan.skip(count)); }
    public GeneratedPrimitiveValuePipeline limit(long count) { requireCount(count, "limit"); claim(); return next(plan.limit(count)); }

    public long count() { claim(); return PrimitivePlanOperation.count(plan); }
    public boolean anyMatchBoolean(SomaBooleanPredicate callback) { return match(PrimitiveValueKind.BOOLEAN, callback, 0); }
    public boolean allMatchBoolean(SomaBooleanPredicate callback) { return match(PrimitiveValueKind.BOOLEAN, callback, 1); }
    public boolean noneMatchBoolean(SomaBooleanPredicate callback) { return match(PrimitiveValueKind.BOOLEAN, callback, 2); }
    public boolean anyMatchByte(SomaBytePredicate callback) { return match(PrimitiveValueKind.BYTE, callback, 0); }
    public boolean allMatchByte(SomaBytePredicate callback) { return match(PrimitiveValueKind.BYTE, callback, 1); }
    public boolean noneMatchByte(SomaBytePredicate callback) { return match(PrimitiveValueKind.BYTE, callback, 2); }
    public boolean anyMatchShort(SomaShortPredicate callback) { return match(PrimitiveValueKind.SHORT, callback, 0); }
    public boolean allMatchShort(SomaShortPredicate callback) { return match(PrimitiveValueKind.SHORT, callback, 1); }
    public boolean noneMatchShort(SomaShortPredicate callback) { return match(PrimitiveValueKind.SHORT, callback, 2); }
    public boolean anyMatchChar(SomaCharPredicate callback) { return match(PrimitiveValueKind.CHAR, callback, 0); }
    public boolean allMatchChar(SomaCharPredicate callback) { return match(PrimitiveValueKind.CHAR, callback, 1); }
    public boolean noneMatchChar(SomaCharPredicate callback) { return match(PrimitiveValueKind.CHAR, callback, 2); }
    public boolean anyMatchInt(SomaIntPredicate callback) { return match(PrimitiveValueKind.INT, callback, 0); }
    public boolean allMatchInt(SomaIntPredicate callback) { return match(PrimitiveValueKind.INT, callback, 1); }
    public boolean noneMatchInt(SomaIntPredicate callback) { return match(PrimitiveValueKind.INT, callback, 2); }
    public boolean anyMatchLong(SomaLongPredicate callback) { return match(PrimitiveValueKind.LONG, callback, 0); }
    public boolean allMatchLong(SomaLongPredicate callback) { return match(PrimitiveValueKind.LONG, callback, 1); }
    public boolean noneMatchLong(SomaLongPredicate callback) { return match(PrimitiveValueKind.LONG, callback, 2); }
    public boolean anyMatchFloat(SomaFloatPredicate callback) { return match(PrimitiveValueKind.FLOAT, callback, 0); }
    public boolean allMatchFloat(SomaFloatPredicate callback) { return match(PrimitiveValueKind.FLOAT, callback, 1); }
    public boolean noneMatchFloat(SomaFloatPredicate callback) { return match(PrimitiveValueKind.FLOAT, callback, 2); }
    public boolean anyMatchDouble(SomaDoublePredicate callback) { return match(PrimitiveValueKind.DOUBLE, callback, 0); }
    public boolean allMatchDouble(SomaDoublePredicate callback) { return match(PrimitiveValueKind.DOUBLE, callback, 1); }
    public boolean noneMatchDouble(SomaDoublePredicate callback) { return match(PrimitiveValueKind.DOUBLE, callback, 2); }

    public Optional<Boolean> findFirstBoolean() { requireKind(PrimitiveValueKind.BOOLEAN); claim(); return PrimitivePlanOperation.findBoolean(plan); }
    public OptionalInt findFirstInt() { requireIntegralIntKind(); claim(); return PrimitivePlanOperation.findInt(plan); }
    public OptionalLong findFirstLong() { requireKind(PrimitiveValueKind.LONG); claim(); return PrimitivePlanOperation.findLong(plan); }
    public OptionalDouble findFirstFloating() { requireFloatingKind(); claim(); return PrimitivePlanOperation.findDouble(plan); }
    public OptionalInt minInt(boolean maximum) { requireIntegralIntKind(); claim(); return PrimitivePlanOperation.extremumInt(plan, maximum); }
    public OptionalLong minLong(boolean maximum) { requireKind(PrimitiveValueKind.LONG); claim(); return PrimitivePlanOperation.extremumLong(plan, maximum); }
    public OptionalDouble minFloating(boolean maximum) { requireFloatingKind(); claim(); return PrimitivePlanOperation.extremumDouble(plan, maximum); }
    public long sumIntegral() { requireIntegralKind(); claim(); return PrimitivePlanOperation.sumIntegral(plan); }
    public double sumFloating() { requireFloatingKind(); claim(); return PrimitivePlanOperation.sumDouble(plan); }
    public OptionalDouble averageIntegral() { requireIntegralKind(); claim(); return PrimitivePlanOperation.averageIntegral(plan); }
    public OptionalDouble averageFloating() { requireFloatingKind(); claim(); return PrimitivePlanOperation.averageDouble(plan); }
    public SomaLongSummary summaryIntegral() { requireIntegralKind(); claim(); return PrimitivePlanOperation.summaryIntegral(plan); }
    public SomaDoubleSummary summaryFloating() { requireFloatingKind(); claim(); return PrimitivePlanOperation.summaryDouble(plan); }

    public void forEachBoolean(SomaBooleanConsumer callback) { forEach(PrimitiveValueKind.BOOLEAN, callback); }
    public void forEachByte(SomaByteConsumer callback) { forEach(PrimitiveValueKind.BYTE, callback); }
    public void forEachShort(SomaShortConsumer callback) { forEach(PrimitiveValueKind.SHORT, callback); }
    public void forEachChar(SomaCharConsumer callback) { forEach(PrimitiveValueKind.CHAR, callback); }
    public void forEachInt(SomaIntConsumer callback) { forEach(PrimitiveValueKind.INT, callback); }
    public void forEachLong(SomaLongConsumer callback) { forEach(PrimitiveValueKind.LONG, callback); }
    public void forEachFloat(SomaFloatConsumer callback) { forEach(PrimitiveValueKind.FLOAT, callback); }
    public void forEachDouble(SomaDoubleConsumer callback) { forEach(PrimitiveValueKind.DOUBLE, callback); }

    public List<Boolean> toBooleanList() { requireKind(PrimitiveValueKind.BOOLEAN); return list(); }
    public List<Byte> toByteList() { requireKind(PrimitiveValueKind.BYTE); return list(); }
    public List<Short> toShortList() { requireKind(PrimitiveValueKind.SHORT); return list(); }
    public List<Character> toCharList() { requireKind(PrimitiveValueKind.CHAR); return list(); }
    public List<Integer> toIntList() { requireKind(PrimitiveValueKind.INT); return list(); }
    public List<Long> toLongList() { requireKind(PrimitiveValueKind.LONG); return list(); }
    public List<Float> toFloatList() { requireKind(PrimitiveValueKind.FLOAT); return list(); }
    public List<Double> toDoubleList() { requireKind(PrimitiveValueKind.DOUBLE); return list(); }

    public boolean[] toBooleanArray() { requireKind(PrimitiveValueKind.BOOLEAN); claim(); return PrimitivePlanOperation.toBooleanArray(plan); }
    public byte[] toByteArray() { requireKind(PrimitiveValueKind.BYTE); claim(); return PrimitivePlanOperation.toByteArray(plan); }
    public short[] toShortArray() { requireKind(PrimitiveValueKind.SHORT); claim(); return PrimitivePlanOperation.toShortArray(plan); }
    public char[] toCharArray() { requireKind(PrimitiveValueKind.CHAR); claim(); return PrimitivePlanOperation.toCharArray(plan); }
    public int[] toIntArray() { requireKind(PrimitiveValueKind.INT); claim(); return PrimitivePlanOperation.toIntArray(plan); }
    public long[] toLongArray() { requireKind(PrimitiveValueKind.LONG); claim(); return PrimitivePlanOperation.toLongArray(plan); }
    public float[] toFloatArray() { requireKind(PrimitiveValueKind.FLOAT); claim(); return PrimitivePlanOperation.toFloatArray(plan); }
    public double[] toDoubleArray() { requireKind(PrimitiveValueKind.DOUBLE); claim(); return PrimitivePlanOperation.toDoubleArray(plan); }
    public String explain() { claim(); return PrimitivePlanOperation.explain(plan); }

    private GeneratedPrimitiveValuePipeline filter(PrimitiveValueKind kind, Object callback) { requireKind(kind); require(callback, "predicate"); claim(); return next(plan.filter(callback)); }
    private GeneratedPrimitiveValuePipeline map(PrimitiveValueKind kind, Object callback) { requireKind(kind); require(callback, "mapper"); claim(); return next(plan.map(callback)); }
    private SomaIntStream toInt(PrimitiveValueKind kind, Object callback) { requireKind(kind); require(callback, "mapper"); claim(); return newInt(plan.convert(PrimitiveValueKind.INT, callback)); }
    private SomaLongStream toLong(PrimitiveValueKind kind, Object callback) { requireKind(kind); require(callback, "mapper"); claim(); return newLong(plan.convert(PrimitiveValueKind.LONG, callback)); }
    private SomaDoubleStream toDouble(PrimitiveValueKind kind, Object callback) { requireKind(kind); require(callback, "mapper"); claim(); return newDouble(plan.convert(PrimitiveValueKind.DOUBLE, callback)); }
    private boolean match(PrimitiveValueKind kind, Object callback, int mode) { requireKind(kind); require(callback, "predicate"); claim(); return PrimitivePlanOperation.match(plan, callback, mode); }
    private void forEach(PrimitiveValueKind kind, Object callback) { requireKind(kind); require(callback, "action"); claim(); PrimitivePlanOperation.forEach(plan, callback); }
    private GeneratedPrimitiveValuePipeline next(PrimitivePipelineCapture next) { return new GeneratedPrimitiveValuePipeline(next); }

    private static SomaIntStream newInt(PrimitivePipelineCapture plan) { return GeneratedPrimitivePipeline.fromPlanInt(plan); }
    private static SomaLongStream newLong(PrimitivePipelineCapture plan) { return GeneratedPrimitivePipeline.fromPlanLong(plan); }
    private static SomaDoubleStream newDouble(PrimitivePipelineCapture plan) { return GeneratedPrimitivePipeline.fromPlanDouble(plan); }

    @SuppressWarnings("unchecked")
    private <T> List<T> list() {
        claim();
        return (List<T>) (List<?>) PrimitivePlanOperation.toBoxedList(plan);
    }

    private void requireKind(PrimitiveValueKind expected) { if (plan.valueKind != expected) throw new AssertionError("primitive stream kind mismatch"); }
    private void requireIntegralIntKind() { if (plan.valueKind != PrimitiveValueKind.BYTE && plan.valueKind != PrimitiveValueKind.SHORT && plan.valueKind != PrimitiveValueKind.CHAR && plan.valueKind != PrimitiveValueKind.INT) throw new AssertionError("primitive stream kind mismatch"); }
    private void requireIntegralKind() { requireIntegralIntOrLong(); }
    private void requireIntegralIntOrLong() { if (plan.valueKind != PrimitiveValueKind.BYTE && plan.valueKind != PrimitiveValueKind.SHORT && plan.valueKind != PrimitiveValueKind.CHAR && plan.valueKind != PrimitiveValueKind.INT && plan.valueKind != PrimitiveValueKind.LONG) throw new AssertionError("primitive stream kind mismatch"); }
    private void requireFloatingKind() { if (plan.valueKind != PrimitiveValueKind.FLOAT && plan.valueKind != PrimitiveValueKind.DOUBLE) throw new AssertionError("primitive stream kind mismatch"); }
    private static void require(Object value, String category) { if (value == null) throw SomaFailures.invalid(SomaOperation.QUERY, category + " is null"); }
    private static void requireCount(long value, String category) { if (value < 0L) throw SomaFailures.invalid(SomaOperation.QUERY, category + " is negative"); }
    private void claim() { if (!consumed.compareAndSet(false, true)) throw SomaFailures.failure(SomaFailureCode.PIPELINE_ALREADY_CONSUMED, SomaOperation.QUERY, "linked pipeline has already been consumed", new Object()); }
}
