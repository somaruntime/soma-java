package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaDoubleConsumer;
import io.github.somaruntime.soma.SomaDoublePredicate;
import io.github.somaruntime.soma.SomaDoubleStream;
import io.github.somaruntime.soma.SomaDoubleSummary;
import io.github.somaruntime.soma.SomaDoubleToIntFunction;
import io.github.somaruntime.soma.SomaDoubleToLongFunction;
import io.github.somaruntime.soma.SomaDoubleUnaryOperator;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaIntConsumer;
import io.github.somaruntime.soma.SomaIntPredicate;
import io.github.somaruntime.soma.SomaIntStream;
import io.github.somaruntime.soma.SomaIntToDoubleFunction;
import io.github.somaruntime.soma.SomaIntToLongFunction;
import io.github.somaruntime.soma.SomaIntUnaryOperator;
import io.github.somaruntime.soma.SomaLongConsumer;
import io.github.somaruntime.soma.SomaLongPredicate;
import io.github.somaruntime.soma.SomaLongStream;
import io.github.somaruntime.soma.SomaLongSummary;
import io.github.somaruntime.soma.SomaLongToDoubleFunction;
import io.github.somaruntime.soma.SomaLongToIntFunction;
import io.github.somaruntime.soma.SomaLongUnaryOperator;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaToDoubleFunction;
import io.github.somaruntime.soma.SomaToIntFunction;
import io.github.somaruntime.soma.SomaToLongFunction;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

/** One-shot public primitive stream carriers backed by one data-only plan. */
final class GeneratedPrimitivePipeline {

    private GeneratedPrimitivePipeline() {
    }

    static SomaIntStream fromRowInt(
            LogicalRowPlan rows,
            GeneratedCallbacks.RowToIntMapper mapper) {
        return new IntPipeline(PrimitivePlan.row(
                rows, PrimitivePlan.ValueKind.INT, mapper, true));
    }

    static SomaLongStream fromRowLong(
            LogicalRowPlan rows,
            GeneratedCallbacks.RowToLongMapper mapper) {
        return new LongPipeline(PrimitivePlan.row(
                rows, PrimitivePlan.ValueKind.LONG, mapper, true));
    }

    static SomaDoubleStream fromRowDouble(
            LogicalRowPlan rows,
            GeneratedCallbacks.RowToDoubleMapper mapper) {
        return new DoublePipeline(PrimitivePlan.row(
                rows, PrimitivePlan.ValueKind.DOUBLE, mapper, true));
    }

    static <R> SomaIntStream fromMappedInt(
            MappedPlan<R> mapped,
            SomaToIntFunction<? super R> mapper) {
        return new IntPipeline(PrimitivePlan.mapped(
                mapped, PrimitivePlan.ValueKind.INT, mapper));
    }

    static <R> SomaLongStream fromMappedLong(
            MappedPlan<R> mapped,
            SomaToLongFunction<? super R> mapper) {
        return new LongPipeline(PrimitivePlan.mapped(
                mapped, PrimitivePlan.ValueKind.LONG, mapper));
    }

    static <R> SomaDoubleStream fromMappedDouble(
            MappedPlan<R> mapped,
            SomaToDoubleFunction<? super R> mapper) {
        return new DoublePipeline(PrimitivePlan.mapped(
                mapped, PrimitivePlan.ValueKind.DOUBLE, mapper));
    }

    static SomaIntStream fromPlanInt(PrimitivePlan plan) {
        return new IntPipeline(plan);
    }

    static SomaLongStream fromPlanLong(PrimitivePlan plan) {
        return new LongPipeline(plan);
    }

    static SomaDoubleStream fromPlanDouble(PrimitivePlan plan) {
        return new DoublePipeline(plan);
    }

    private abstract static class Pipeline {
        final PrimitivePlan plan;
        private final AtomicBoolean consumed = new AtomicBoolean();

        Pipeline(PrimitivePlan plan) {
            this.plan = plan;
        }

        final void claim() {
            if (!consumed.compareAndSet(false, true)) {
                throw SomaFailures.failure(
                        SomaFailureCode.PIPELINE_ALREADY_CONSUMED,
                        SomaOperation.QUERY,
                        "linked pipeline has already been consumed",
                        new Object());
            }
        }

        final void require(Object value, String category) {
            if (value == null) {
                throw SomaFailures.invalid(
                        SomaOperation.QUERY, category + " is null");
            }
        }

        final void requireCount(long value, String category) {
            if (value < 0L) {
                throw SomaFailures.invalid(
                        SomaOperation.QUERY, category + " is negative");
            }
        }
    }

    private static final class IntPipeline extends Pipeline
            implements SomaIntStream {

        IntPipeline(PrimitivePlan plan) {
            super(plan);
        }

        @Override public SomaIntStream parallel() {
            claim();
            return new IntPipeline(plan.parallel());
        }

        @Override public SomaIntStream filter(SomaIntPredicate predicate) {
            require(predicate, "predicate"); claim();
            return new IntPipeline(plan.filter(predicate));
        }

        @Override public SomaIntStream map(SomaIntUnaryOperator mapper) {
            require(mapper, "mapper"); claim();
            return new IntPipeline(plan.map(mapper));
        }

        @Override public SomaLongStream mapToLong(SomaIntToLongFunction mapper) {
            require(mapper, "mapper"); claim();
            return new LongPipeline(plan.convert(
                    PrimitivePlan.ValueKind.LONG, mapper));
        }

        @Override public SomaDoubleStream mapToDouble(
                SomaIntToDoubleFunction mapper) {
            require(mapper, "mapper"); claim();
            return new DoublePipeline(plan.convert(
                    PrimitivePlan.ValueKind.DOUBLE, mapper));
        }

        @Override public SomaIntStream distinct() {
            claim(); return new IntPipeline(plan.distinct());
        }

        @Override public SomaIntStream sorted() {
            claim(); return new IntPipeline(plan.sorted());
        }

        @Override public SomaIntStream top(long count) {
            requireCount(count, "top"); claim();
            return new IntPipeline(plan.sorted().limit(count));
        }

        @Override public SomaIntStream skip(long count) {
            requireCount(count, "skip"); claim();
            return new IntPipeline(plan.skip(count));
        }

        @Override public SomaIntStream limit(long count) {
            requireCount(count, "limit"); claim();
            return new IntPipeline(plan.limit(count));
        }

        @Override public long count() {
            claim(); return PrimitivePlanOperation.count(plan);
        }

        @Override public boolean anyMatch(SomaIntPredicate predicate) {
            require(predicate, "predicate"); claim();
            return PrimitivePlanOperation.match(plan, predicate, 0);
        }

        @Override public boolean allMatch(SomaIntPredicate predicate) {
            require(predicate, "predicate"); claim();
            return PrimitivePlanOperation.match(plan, predicate, 1);
        }

        @Override public boolean noneMatch(SomaIntPredicate predicate) {
            require(predicate, "predicate"); claim();
            return PrimitivePlanOperation.match(plan, predicate, 2);
        }

        @Override public OptionalInt findFirst() {
            claim(); return PrimitivePlanOperation.findInt(plan);
        }

        @Override public OptionalInt min() {
            claim(); return PrimitivePlanOperation.extremumInt(plan, false);
        }

        @Override public OptionalInt max() {
            claim(); return PrimitivePlanOperation.extremumInt(plan, true);
        }

        @Override public long sum() {
            claim(); return PrimitivePlanOperation.sumIntegral(plan);
        }

        @Override public OptionalDouble average() {
            claim(); return PrimitivePlanOperation.averageIntegral(plan);
        }

        @Override public SomaLongSummary summaryStatistics() {
            claim(); return PrimitivePlanOperation.summaryIntegral(plan);
        }

        @Override public void forEach(SomaIntConsumer action) {
            require(action, "action"); claim();
            PrimitivePlanOperation.forEach(plan, action);
        }

        @Override public void forEachOrdered(SomaIntConsumer action) {
            forEach(action);
        }

        @Override public int[] toArray() {
            claim(); return PrimitivePlanOperation.toIntArray(plan);
        }

        @Override public String _explain() {
            claim(); return PrimitivePlanOperation.explain(plan);
        }
    }

    private static final class LongPipeline extends Pipeline
            implements SomaLongStream {

        LongPipeline(PrimitivePlan plan) {
            super(plan);
        }

        @Override public SomaLongStream parallel() {
            claim();
            return new LongPipeline(plan.parallel());
        }

        @Override public SomaLongStream filter(SomaLongPredicate predicate) {
            require(predicate, "predicate"); claim();
            return new LongPipeline(plan.filter(predicate));
        }

        @Override public SomaLongStream map(SomaLongUnaryOperator mapper) {
            require(mapper, "mapper"); claim();
            return new LongPipeline(plan.map(mapper));
        }

        @Override public SomaIntStream mapToInt(SomaLongToIntFunction mapper) {
            require(mapper, "mapper"); claim();
            return new IntPipeline(plan.convert(
                    PrimitivePlan.ValueKind.INT, mapper));
        }

        @Override public SomaDoubleStream mapToDouble(
                SomaLongToDoubleFunction mapper) {
            require(mapper, "mapper"); claim();
            return new DoublePipeline(plan.convert(
                    PrimitivePlan.ValueKind.DOUBLE, mapper));
        }

        @Override public SomaLongStream distinct() {
            claim(); return new LongPipeline(plan.distinct());
        }

        @Override public SomaLongStream sorted() {
            claim(); return new LongPipeline(plan.sorted());
        }

        @Override public SomaLongStream top(long count) {
            requireCount(count, "top"); claim();
            return new LongPipeline(plan.sorted().limit(count));
        }

        @Override public SomaLongStream skip(long count) {
            requireCount(count, "skip"); claim();
            return new LongPipeline(plan.skip(count));
        }

        @Override public SomaLongStream limit(long count) {
            requireCount(count, "limit"); claim();
            return new LongPipeline(plan.limit(count));
        }

        @Override public long count() {
            claim(); return PrimitivePlanOperation.count(plan);
        }

        @Override public boolean anyMatch(SomaLongPredicate predicate) {
            require(predicate, "predicate"); claim();
            return PrimitivePlanOperation.match(plan, predicate, 0);
        }

        @Override public boolean allMatch(SomaLongPredicate predicate) {
            require(predicate, "predicate"); claim();
            return PrimitivePlanOperation.match(plan, predicate, 1);
        }

        @Override public boolean noneMatch(SomaLongPredicate predicate) {
            require(predicate, "predicate"); claim();
            return PrimitivePlanOperation.match(plan, predicate, 2);
        }

        @Override public OptionalLong findFirst() {
            claim(); return PrimitivePlanOperation.findLong(plan);
        }

        @Override public OptionalLong min() {
            claim(); return PrimitivePlanOperation.extremumLong(plan, false);
        }

        @Override public OptionalLong max() {
            claim(); return PrimitivePlanOperation.extremumLong(plan, true);
        }

        @Override public long sum() {
            claim(); return PrimitivePlanOperation.sumIntegral(plan);
        }

        @Override public OptionalDouble average() {
            claim(); return PrimitivePlanOperation.averageIntegral(plan);
        }

        @Override public SomaLongSummary summaryStatistics() {
            claim(); return PrimitivePlanOperation.summaryIntegral(plan);
        }

        @Override public void forEach(SomaLongConsumer action) {
            require(action, "action"); claim();
            PrimitivePlanOperation.forEach(plan, action);
        }

        @Override public void forEachOrdered(SomaLongConsumer action) {
            forEach(action);
        }

        @Override public long[] toArray() {
            claim(); return PrimitivePlanOperation.toLongArray(plan);
        }

        @Override public String _explain() {
            claim(); return PrimitivePlanOperation.explain(plan);
        }
    }

    private static final class DoublePipeline extends Pipeline
            implements SomaDoubleStream {

        DoublePipeline(PrimitivePlan plan) {
            super(plan);
        }

        @Override public SomaDoubleStream parallel() {
            claim();
            return new DoublePipeline(plan.parallel());
        }

        @Override public SomaDoubleStream filter(SomaDoublePredicate predicate) {
            require(predicate, "predicate"); claim();
            return new DoublePipeline(plan.filter(predicate));
        }

        @Override public SomaDoubleStream map(SomaDoubleUnaryOperator mapper) {
            require(mapper, "mapper"); claim();
            return new DoublePipeline(plan.map(mapper));
        }

        @Override public SomaIntStream mapToInt(SomaDoubleToIntFunction mapper) {
            require(mapper, "mapper"); claim();
            return new IntPipeline(plan.convert(
                    PrimitivePlan.ValueKind.INT, mapper));
        }

        @Override public SomaLongStream mapToLong(SomaDoubleToLongFunction mapper) {
            require(mapper, "mapper"); claim();
            return new LongPipeline(plan.convert(
                    PrimitivePlan.ValueKind.LONG, mapper));
        }

        @Override public SomaDoubleStream distinct() {
            claim(); return new DoublePipeline(plan.distinct());
        }

        @Override public SomaDoubleStream sorted() {
            claim(); return new DoublePipeline(plan.sorted());
        }

        @Override public SomaDoubleStream top(long count) {
            requireCount(count, "top"); claim();
            return new DoublePipeline(plan.sorted().limit(count));
        }

        @Override public SomaDoubleStream skip(long count) {
            requireCount(count, "skip"); claim();
            return new DoublePipeline(plan.skip(count));
        }

        @Override public SomaDoubleStream limit(long count) {
            requireCount(count, "limit"); claim();
            return new DoublePipeline(plan.limit(count));
        }

        @Override public long count() {
            claim(); return PrimitivePlanOperation.count(plan);
        }

        @Override public boolean anyMatch(SomaDoublePredicate predicate) {
            require(predicate, "predicate"); claim();
            return PrimitivePlanOperation.match(plan, predicate, 0);
        }

        @Override public boolean allMatch(SomaDoublePredicate predicate) {
            require(predicate, "predicate"); claim();
            return PrimitivePlanOperation.match(plan, predicate, 1);
        }

        @Override public boolean noneMatch(SomaDoublePredicate predicate) {
            require(predicate, "predicate"); claim();
            return PrimitivePlanOperation.match(plan, predicate, 2);
        }

        @Override public OptionalDouble findFirst() {
            claim(); return PrimitivePlanOperation.findDouble(plan);
        }

        @Override public OptionalDouble min() {
            claim(); return PrimitivePlanOperation.extremumDouble(plan, false);
        }

        @Override public OptionalDouble max() {
            claim(); return PrimitivePlanOperation.extremumDouble(plan, true);
        }

        @Override public double sum() {
            claim(); return PrimitivePlanOperation.sumDouble(plan);
        }

        @Override public OptionalDouble average() {
            claim(); return PrimitivePlanOperation.averageDouble(plan);
        }

        @Override public SomaDoubleSummary summaryStatistics() {
            claim(); return PrimitivePlanOperation.summaryDouble(plan);
        }

        @Override public void forEach(SomaDoubleConsumer action) {
            require(action, "action"); claim();
            PrimitivePlanOperation.forEach(plan, action);
        }

        @Override public void forEachOrdered(SomaDoubleConsumer action) {
            forEach(action);
        }

        @Override public double[] toArray() {
            claim(); return PrimitivePlanOperation.toDoubleArray(plan);
        }

        @Override public String _explain() {
            claim(); return PrimitivePlanOperation.explain(plan);
        }
    }
}
