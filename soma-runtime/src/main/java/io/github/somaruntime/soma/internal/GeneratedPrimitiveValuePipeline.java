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

    private final PrimitivePlan plan;
    private final AtomicBoolean consumed = new AtomicBoolean();

    GeneratedPrimitiveValuePipeline(PrimitivePlan plan) {
        this.plan = plan;
    }

    public GeneratedPrimitiveValuePipeline parallel() {
        claim();
        return next(plan.parallel());
    }

    public GeneratedPrimitiveValuePipeline filterBoolean(SomaBooleanPredicate callback) { return filter(PrimitivePlan.ValueKind.BOOLEAN, callback); }
    public GeneratedPrimitiveValuePipeline filterByte(SomaBytePredicate callback) { return filter(PrimitivePlan.ValueKind.BYTE, callback); }
    public GeneratedPrimitiveValuePipeline filterShort(SomaShortPredicate callback) { return filter(PrimitivePlan.ValueKind.SHORT, callback); }
    public GeneratedPrimitiveValuePipeline filterChar(SomaCharPredicate callback) { return filter(PrimitivePlan.ValueKind.CHAR, callback); }
    public GeneratedPrimitiveValuePipeline filterInt(SomaIntPredicate callback) { return filter(PrimitivePlan.ValueKind.INT, callback); }
    public GeneratedPrimitiveValuePipeline filterLong(SomaLongPredicate callback) { return filter(PrimitivePlan.ValueKind.LONG, callback); }
    public GeneratedPrimitiveValuePipeline filterFloat(SomaFloatPredicate callback) { return filter(PrimitivePlan.ValueKind.FLOAT, callback); }
    public GeneratedPrimitiveValuePipeline filterDouble(SomaDoublePredicate callback) { return filter(PrimitivePlan.ValueKind.DOUBLE, callback); }

    public GeneratedPrimitiveValuePipeline mapBoolean(SomaBooleanUnaryOperator callback) { return map(PrimitivePlan.ValueKind.BOOLEAN, callback); }
    public GeneratedPrimitiveValuePipeline mapByte(SomaByteUnaryOperator callback) { return map(PrimitivePlan.ValueKind.BYTE, callback); }
    public GeneratedPrimitiveValuePipeline mapShort(SomaShortUnaryOperator callback) { return map(PrimitivePlan.ValueKind.SHORT, callback); }
    public GeneratedPrimitiveValuePipeline mapChar(SomaCharUnaryOperator callback) { return map(PrimitivePlan.ValueKind.CHAR, callback); }
    public GeneratedPrimitiveValuePipeline mapInt(SomaIntUnaryOperator callback) { return map(PrimitivePlan.ValueKind.INT, callback); }
    public GeneratedPrimitiveValuePipeline mapLong(SomaLongUnaryOperator callback) { return map(PrimitivePlan.ValueKind.LONG, callback); }
    public GeneratedPrimitiveValuePipeline mapFloat(SomaFloatUnaryOperator callback) { return map(PrimitivePlan.ValueKind.FLOAT, callback); }
    public GeneratedPrimitiveValuePipeline mapDouble(SomaDoubleUnaryOperator callback) { return map(PrimitivePlan.ValueKind.DOUBLE, callback); }

    public SomaIntStream mapBooleanToInt(SomaBooleanToIntFunction callback) { return toInt(PrimitivePlan.ValueKind.BOOLEAN, callback); }
    public SomaLongStream mapBooleanToLong(SomaBooleanToLongFunction callback) { return toLong(PrimitivePlan.ValueKind.BOOLEAN, callback); }
    public SomaDoubleStream mapBooleanToDouble(SomaBooleanToDoubleFunction callback) { return toDouble(PrimitivePlan.ValueKind.BOOLEAN, callback); }
    public SomaIntStream mapByteToInt(SomaByteToIntFunction callback) { return toInt(PrimitivePlan.ValueKind.BYTE, callback); }
    public SomaLongStream mapByteToLong(SomaByteToLongFunction callback) { return toLong(PrimitivePlan.ValueKind.BYTE, callback); }
    public SomaDoubleStream mapByteToDouble(SomaByteToDoubleFunction callback) { return toDouble(PrimitivePlan.ValueKind.BYTE, callback); }
    public SomaIntStream mapShortToInt(SomaShortToIntFunction callback) { return toInt(PrimitivePlan.ValueKind.SHORT, callback); }
    public SomaLongStream mapShortToLong(SomaShortToLongFunction callback) { return toLong(PrimitivePlan.ValueKind.SHORT, callback); }
    public SomaDoubleStream mapShortToDouble(SomaShortToDoubleFunction callback) { return toDouble(PrimitivePlan.ValueKind.SHORT, callback); }
    public SomaIntStream mapCharToInt(SomaCharToIntFunction callback) { return toInt(PrimitivePlan.ValueKind.CHAR, callback); }
    public SomaLongStream mapCharToLong(SomaCharToLongFunction callback) { return toLong(PrimitivePlan.ValueKind.CHAR, callback); }
    public SomaDoubleStream mapCharToDouble(SomaCharToDoubleFunction callback) { return toDouble(PrimitivePlan.ValueKind.CHAR, callback); }
    public SomaLongStream mapIntToLong(SomaIntToLongFunction callback) { return toLong(PrimitivePlan.ValueKind.INT, callback); }
    public SomaDoubleStream mapIntToDouble(SomaIntToDoubleFunction callback) { return toDouble(PrimitivePlan.ValueKind.INT, callback); }
    public SomaIntStream mapLongToInt(SomaLongToIntFunction callback) { return toInt(PrimitivePlan.ValueKind.LONG, callback); }
    public SomaDoubleStream mapLongToDouble(SomaLongToDoubleFunction callback) { return toDouble(PrimitivePlan.ValueKind.LONG, callback); }
    public SomaIntStream mapFloatToInt(SomaFloatToIntFunction callback) { return toInt(PrimitivePlan.ValueKind.FLOAT, callback); }
    public SomaLongStream mapFloatToLong(SomaFloatToLongFunction callback) { return toLong(PrimitivePlan.ValueKind.FLOAT, callback); }
    public SomaDoubleStream mapFloatToDouble(SomaFloatToDoubleFunction callback) { return toDouble(PrimitivePlan.ValueKind.FLOAT, callback); }
    public SomaIntStream mapDoubleToInt(SomaDoubleToIntFunction callback) { return toInt(PrimitivePlan.ValueKind.DOUBLE, callback); }
    public SomaLongStream mapDoubleToLong(SomaDoubleToLongFunction callback) { return toLong(PrimitivePlan.ValueKind.DOUBLE, callback); }

    public GeneratedPrimitiveValuePipeline distinct() { claim(); return next(plan.distinct()); }
    public GeneratedPrimitiveValuePipeline sorted() { claim(); return next(plan.sorted()); }
    public GeneratedPrimitiveValuePipeline top(long count) { requireCount(count, "top"); claim(); return next(plan.sorted().limit(count)); }
    public GeneratedPrimitiveValuePipeline skip(long count) { requireCount(count, "skip"); claim(); return next(plan.skip(count)); }
    public GeneratedPrimitiveValuePipeline limit(long count) { requireCount(count, "limit"); claim(); return next(plan.limit(count)); }

    public long count() { claim(); return PrimitivePlanOperation.count(plan); }
    public boolean anyMatchBoolean(SomaBooleanPredicate callback) { return match(PrimitivePlan.ValueKind.BOOLEAN, callback, 0); }
    public boolean allMatchBoolean(SomaBooleanPredicate callback) { return match(PrimitivePlan.ValueKind.BOOLEAN, callback, 1); }
    public boolean noneMatchBoolean(SomaBooleanPredicate callback) { return match(PrimitivePlan.ValueKind.BOOLEAN, callback, 2); }
    public boolean anyMatchByte(SomaBytePredicate callback) { return match(PrimitivePlan.ValueKind.BYTE, callback, 0); }
    public boolean allMatchByte(SomaBytePredicate callback) { return match(PrimitivePlan.ValueKind.BYTE, callback, 1); }
    public boolean noneMatchByte(SomaBytePredicate callback) { return match(PrimitivePlan.ValueKind.BYTE, callback, 2); }
    public boolean anyMatchShort(SomaShortPredicate callback) { return match(PrimitivePlan.ValueKind.SHORT, callback, 0); }
    public boolean allMatchShort(SomaShortPredicate callback) { return match(PrimitivePlan.ValueKind.SHORT, callback, 1); }
    public boolean noneMatchShort(SomaShortPredicate callback) { return match(PrimitivePlan.ValueKind.SHORT, callback, 2); }
    public boolean anyMatchChar(SomaCharPredicate callback) { return match(PrimitivePlan.ValueKind.CHAR, callback, 0); }
    public boolean allMatchChar(SomaCharPredicate callback) { return match(PrimitivePlan.ValueKind.CHAR, callback, 1); }
    public boolean noneMatchChar(SomaCharPredicate callback) { return match(PrimitivePlan.ValueKind.CHAR, callback, 2); }
    public boolean anyMatchInt(SomaIntPredicate callback) { return match(PrimitivePlan.ValueKind.INT, callback, 0); }
    public boolean allMatchInt(SomaIntPredicate callback) { return match(PrimitivePlan.ValueKind.INT, callback, 1); }
    public boolean noneMatchInt(SomaIntPredicate callback) { return match(PrimitivePlan.ValueKind.INT, callback, 2); }
    public boolean anyMatchLong(SomaLongPredicate callback) { return match(PrimitivePlan.ValueKind.LONG, callback, 0); }
    public boolean allMatchLong(SomaLongPredicate callback) { return match(PrimitivePlan.ValueKind.LONG, callback, 1); }
    public boolean noneMatchLong(SomaLongPredicate callback) { return match(PrimitivePlan.ValueKind.LONG, callback, 2); }
    public boolean anyMatchFloat(SomaFloatPredicate callback) { return match(PrimitivePlan.ValueKind.FLOAT, callback, 0); }
    public boolean allMatchFloat(SomaFloatPredicate callback) { return match(PrimitivePlan.ValueKind.FLOAT, callback, 1); }
    public boolean noneMatchFloat(SomaFloatPredicate callback) { return match(PrimitivePlan.ValueKind.FLOAT, callback, 2); }
    public boolean anyMatchDouble(SomaDoublePredicate callback) { return match(PrimitivePlan.ValueKind.DOUBLE, callback, 0); }
    public boolean allMatchDouble(SomaDoublePredicate callback) { return match(PrimitivePlan.ValueKind.DOUBLE, callback, 1); }
    public boolean noneMatchDouble(SomaDoublePredicate callback) { return match(PrimitivePlan.ValueKind.DOUBLE, callback, 2); }

    public Optional<Boolean> findFirstBoolean() { requireKind(PrimitivePlan.ValueKind.BOOLEAN); claim(); return PrimitivePlanOperation.findBoolean(plan); }
    public OptionalInt findFirstInt() { requireIntegralIntKind(); claim(); return PrimitivePlanOperation.findInt(plan); }
    public OptionalLong findFirstLong() { requireKind(PrimitivePlan.ValueKind.LONG); claim(); return PrimitivePlanOperation.findLong(plan); }
    public OptionalDouble findFirstFloating() { requireFloatingKind(); claim(); return PrimitivePlanOperation.findDouble(plan); }
    public OptionalInt minInt(boolean maximum) { requireIntegralIntKind(); claim(); return PrimitivePlanOperation.extremumInt(plan, maximum); }
    public OptionalLong minLong(boolean maximum) { requireKind(PrimitivePlan.ValueKind.LONG); claim(); return PrimitivePlanOperation.extremumLong(plan, maximum); }
    public OptionalDouble minFloating(boolean maximum) { requireFloatingKind(); claim(); return PrimitivePlanOperation.extremumDouble(plan, maximum); }
    public long sumIntegral() { requireIntegralKind(); claim(); return PrimitivePlanOperation.sumIntegral(plan); }
    public double sumFloating() { requireFloatingKind(); claim(); return PrimitivePlanOperation.sumDouble(plan); }
    public OptionalDouble averageIntegral() { requireIntegralKind(); claim(); return PrimitivePlanOperation.averageIntegral(plan); }
    public OptionalDouble averageFloating() { requireFloatingKind(); claim(); return PrimitivePlanOperation.averageDouble(plan); }
    public SomaLongSummary summaryIntegral() { requireIntegralKind(); claim(); return PrimitivePlanOperation.summaryIntegral(plan); }
    public SomaDoubleSummary summaryFloating() { requireFloatingKind(); claim(); return PrimitivePlanOperation.summaryDouble(plan); }

    public void forEachBoolean(SomaBooleanConsumer callback) { forEach(PrimitivePlan.ValueKind.BOOLEAN, callback); }
    public void forEachByte(SomaByteConsumer callback) { forEach(PrimitivePlan.ValueKind.BYTE, callback); }
    public void forEachShort(SomaShortConsumer callback) { forEach(PrimitivePlan.ValueKind.SHORT, callback); }
    public void forEachChar(SomaCharConsumer callback) { forEach(PrimitivePlan.ValueKind.CHAR, callback); }
    public void forEachInt(SomaIntConsumer callback) { forEach(PrimitivePlan.ValueKind.INT, callback); }
    public void forEachLong(SomaLongConsumer callback) { forEach(PrimitivePlan.ValueKind.LONG, callback); }
    public void forEachFloat(SomaFloatConsumer callback) { forEach(PrimitivePlan.ValueKind.FLOAT, callback); }
    public void forEachDouble(SomaDoubleConsumer callback) { forEach(PrimitivePlan.ValueKind.DOUBLE, callback); }

    public List<Boolean> toBooleanList() { requireKind(PrimitivePlan.ValueKind.BOOLEAN); return list(); }
    public List<Byte> toByteList() { requireKind(PrimitivePlan.ValueKind.BYTE); return list(); }
    public List<Short> toShortList() { requireKind(PrimitivePlan.ValueKind.SHORT); return list(); }
    public List<Character> toCharList() { requireKind(PrimitivePlan.ValueKind.CHAR); return list(); }
    public List<Integer> toIntList() { requireKind(PrimitivePlan.ValueKind.INT); return list(); }
    public List<Long> toLongList() { requireKind(PrimitivePlan.ValueKind.LONG); return list(); }
    public List<Float> toFloatList() { requireKind(PrimitivePlan.ValueKind.FLOAT); return list(); }
    public List<Double> toDoubleList() { requireKind(PrimitivePlan.ValueKind.DOUBLE); return list(); }

    public boolean[] toBooleanArray() { requireKind(PrimitivePlan.ValueKind.BOOLEAN); claim(); return PrimitivePlanOperation.toBooleanArray(plan); }
    public byte[] toByteArray() { requireKind(PrimitivePlan.ValueKind.BYTE); claim(); return PrimitivePlanOperation.toByteArray(plan); }
    public short[] toShortArray() { requireKind(PrimitivePlan.ValueKind.SHORT); claim(); return PrimitivePlanOperation.toShortArray(plan); }
    public char[] toCharArray() { requireKind(PrimitivePlan.ValueKind.CHAR); claim(); return PrimitivePlanOperation.toCharArray(plan); }
    public int[] toIntArray() { requireKind(PrimitivePlan.ValueKind.INT); claim(); return PrimitivePlanOperation.toIntArray(plan); }
    public long[] toLongArray() { requireKind(PrimitivePlan.ValueKind.LONG); claim(); return PrimitivePlanOperation.toLongArray(plan); }
    public float[] toFloatArray() { requireKind(PrimitivePlan.ValueKind.FLOAT); claim(); return PrimitivePlanOperation.toFloatArray(plan); }
    public double[] toDoubleArray() { requireKind(PrimitivePlan.ValueKind.DOUBLE); claim(); return PrimitivePlanOperation.toDoubleArray(plan); }
    public String explain() { claim(); return PrimitivePlanOperation.explain(plan); }

    private GeneratedPrimitiveValuePipeline filter(PrimitivePlan.ValueKind kind, Object callback) { requireKind(kind); require(callback, "predicate"); claim(); return next(plan.filter(callback)); }
    private GeneratedPrimitiveValuePipeline map(PrimitivePlan.ValueKind kind, Object callback) { requireKind(kind); require(callback, "mapper"); claim(); return next(plan.map(callback)); }
    private SomaIntStream toInt(PrimitivePlan.ValueKind kind, Object callback) { requireKind(kind); require(callback, "mapper"); claim(); return newInt(plan.convert(PrimitivePlan.ValueKind.INT, callback)); }
    private SomaLongStream toLong(PrimitivePlan.ValueKind kind, Object callback) { requireKind(kind); require(callback, "mapper"); claim(); return newLong(plan.convert(PrimitivePlan.ValueKind.LONG, callback)); }
    private SomaDoubleStream toDouble(PrimitivePlan.ValueKind kind, Object callback) { requireKind(kind); require(callback, "mapper"); claim(); return newDouble(plan.convert(PrimitivePlan.ValueKind.DOUBLE, callback)); }
    private boolean match(PrimitivePlan.ValueKind kind, Object callback, int mode) { requireKind(kind); require(callback, "predicate"); claim(); return PrimitivePlanOperation.match(plan, callback, mode); }
    private void forEach(PrimitivePlan.ValueKind kind, Object callback) { requireKind(kind); require(callback, "action"); claim(); PrimitivePlanOperation.forEach(plan, callback); }
    private GeneratedPrimitiveValuePipeline next(PrimitivePlan next) { return new GeneratedPrimitiveValuePipeline(next); }

    private static SomaIntStream newInt(PrimitivePlan plan) { return GeneratedPrimitivePipeline.fromPlanInt(plan); }
    private static SomaLongStream newLong(PrimitivePlan plan) { return GeneratedPrimitivePipeline.fromPlanLong(plan); }
    private static SomaDoubleStream newDouble(PrimitivePlan plan) { return GeneratedPrimitivePipeline.fromPlanDouble(plan); }

    @SuppressWarnings("unchecked")
    private <T> List<T> list() {
        claim();
        return (List<T>) (List<?>) PrimitivePlanOperation.toBoxedList(plan);
    }

    private void requireKind(PrimitivePlan.ValueKind expected) { if (plan.valueKind != expected) throw new AssertionError("primitive stream kind mismatch"); }
    private void requireIntegralIntKind() { if (plan.valueKind != PrimitivePlan.ValueKind.BYTE && plan.valueKind != PrimitivePlan.ValueKind.SHORT && plan.valueKind != PrimitivePlan.ValueKind.CHAR && plan.valueKind != PrimitivePlan.ValueKind.INT) throw new AssertionError("primitive stream kind mismatch"); }
    private void requireIntegralKind() { requireIntegralIntOrLong(); }
    private void requireIntegralIntOrLong() { if (plan.valueKind != PrimitivePlan.ValueKind.BYTE && plan.valueKind != PrimitivePlan.ValueKind.SHORT && plan.valueKind != PrimitivePlan.ValueKind.CHAR && plan.valueKind != PrimitivePlan.ValueKind.INT && plan.valueKind != PrimitivePlan.ValueKind.LONG) throw new AssertionError("primitive stream kind mismatch"); }
    private void requireFloatingKind() { if (plan.valueKind != PrimitivePlan.ValueKind.FLOAT && plan.valueKind != PrimitivePlan.ValueKind.DOUBLE) throw new AssertionError("primitive stream kind mismatch"); }
    private static void require(Object value, String category) { if (value == null) throw SomaFailures.invalid(SomaOperation.QUERY, category + " is null"); }
    private static void requireCount(long value, String category) { if (value < 0L) throw SomaFailures.invalid(SomaOperation.QUERY, category + " is negative"); }
    private void claim() { if (!consumed.compareAndSet(false, true)) throw SomaFailures.failure(SomaFailureCode.PIPELINE_ALREADY_CONSUMED, SomaOperation.QUERY, "linked pipeline has already been consumed", new Object()); }
}
