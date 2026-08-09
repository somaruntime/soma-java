package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.MappedStream;
import io.github.somaruntime.soma.SomaDoubleStream;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaIntStream;
import io.github.somaruntime.soma.SomaLongStream;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaPredicate;
import io.github.somaruntime.soma.SomaToDoubleFunction;
import io.github.somaruntime.soma.SomaToIntFunction;
import io.github.somaruntime.soma.SomaToLongFunction;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/** Internal one-shot implementation of arbitrary reference MappedStream. */
public final class GeneratedMappedPipeline<R> implements MappedStream<R> {

    private final MappedPlan<R> plan;
    private final AtomicBoolean consumed = new AtomicBoolean();

    GeneratedMappedPipeline(MappedPlan<R> plan) { this.plan = plan; }

    @Override public MappedStream<R> parallel() {
        claim();
        return new GeneratedMappedPipeline<R>(plan.parallel());
    }

    @Override public MappedStream<R> filter(SomaPredicate<? super R> predicate) {
        require(predicate, "predicate"); claim(); return new GeneratedMappedPipeline<R>(plan.filter(predicate));
    }
    @Override public <U> MappedStream<U> map(Function<? super R, ? extends U> mapper) {
        require(mapper, "mapper"); claim(); return new GeneratedMappedPipeline<U>(plan.map(mapper));
    }
    @Override public SomaIntStream mapToInt(SomaToIntFunction<? super R> mapper) {
        require(mapper, "mapper"); claim(); return GeneratedPrimitivePipeline.fromMappedInt(plan, mapper);
    }
    @Override public SomaLongStream mapToLong(SomaToLongFunction<? super R> mapper) {
        require(mapper, "mapper"); claim(); return GeneratedPrimitivePipeline.fromMappedLong(plan, mapper);
    }
    @Override public SomaDoubleStream mapToDouble(SomaToDoubleFunction<? super R> mapper) {
        require(mapper, "mapper"); claim(); return GeneratedPrimitivePipeline.fromMappedDouble(plan, mapper);
    }
    @Override public MappedStream<R> distinct() { claim(); return new GeneratedMappedPipeline<R>(plan.distinct()); }
    @Override public MappedStream<R> sorted(Comparator<? super R> comparator) {
        require(comparator, "comparator"); claim(); return new GeneratedMappedPipeline<R>(plan.sorted(comparator));
    }
    @Override public MappedStream<R> top(long count, Comparator<? super R> comparator) {
        requireCount(count, "top"); require(comparator, "comparator"); claim();
        return new GeneratedMappedPipeline<R>(plan.sorted(comparator).limit(count));
    }
    @Override public MappedStream<R> skip(long count) { requireCount(count, "skip"); claim(); return new GeneratedMappedPipeline<R>(plan.skip(count)); }
    @Override public MappedStream<R> limit(long count) { requireCount(count, "limit"); claim(); return new GeneratedMappedPipeline<R>(plan.limit(count)); }
    @Override public long count() { claim(); return MappedQueryOperation.count(plan); }
    @Override public boolean anyMatch(SomaPredicate<? super R> predicate) {
        require(predicate, "predicate"); claim(); return MappedQueryOperation.anyMatch(plan, castPredicate(predicate));
    }
    @Override public boolean allMatch(SomaPredicate<? super R> predicate) {
        require(predicate, "predicate"); claim(); return MappedQueryOperation.allMatch(plan, castPredicate(predicate));
    }
    @Override public boolean noneMatch(SomaPredicate<? super R> predicate) {
        require(predicate, "predicate"); claim(); return !MappedQueryOperation.anyMatch(plan, castPredicate(predicate));
    }
    @Override public Optional<R> findFirst() { claim(); return castOptional(MappedQueryOperation.findFirst(plan)); }
    @Override public Optional<R> min(Comparator<? super R> comparator) {
        require(comparator, "comparator"); claim(); return castOptional(MappedQueryOperation.extremum(plan, castComparator(comparator), false));
    }
    @Override public Optional<R> max(Comparator<? super R> comparator) {
        require(comparator, "comparator"); claim(); return castOptional(MappedQueryOperation.extremum(plan, castComparator(comparator), true));
    }
    @Override public void forEach(Consumer<? super R> action) {
        require(action, "action"); claim(); MappedQueryOperation.forEach(plan, castConsumer(action));
    }
    @Override public void forEachOrdered(Consumer<? super R> action) { forEach(action); }
    @Override public List<R> toList() { claim(); return castList(MappedQueryOperation.toList(plan)); }
    @Override public <A> A[] toArray(Class<A> componentType) {
        require(componentType, "componentType");
        if (componentType.isPrimitive()) throw SomaFailures.invalid(
                SomaOperation.QUERY, "mapped array componentType is primitive");
        claim();
        @SuppressWarnings("unchecked") A[] result = (A[]) MappedQueryOperation.toArray(plan, componentType);
        return result;
    }
    @Override public String _explain() { claim(); return MappedQueryOperation.explain(plan); }

    List<Object> referenceListForTesting() {
        claim();
        return ReferenceMappedInterpreter.toListForTesting(plan);
    }

    private void claim() {
        if (!consumed.compareAndSet(false, true)) throw SomaFailures.failure(
                SomaFailureCode.PIPELINE_ALREADY_CONSUMED, SomaOperation.QUERY,
                "linked pipeline has already been consumed", new Object());
    }
    private static void require(Object value, String category) {
        if (value == null) throw SomaFailures.invalid(SomaOperation.QUERY, category + " is null");
    }
    private static void requireCount(long value, String category) {
        if (value < 0L) throw SomaFailures.invalid(SomaOperation.QUERY, category + " is negative");
    }
    @SuppressWarnings("unchecked") private static SomaPredicate<Object> castPredicate(SomaPredicate<?> value) { return (SomaPredicate<Object>) value; }
    @SuppressWarnings("unchecked") private static Comparator<Object> castComparator(Comparator<?> value) { return (Comparator<Object>) value; }
    @SuppressWarnings("unchecked") private static Consumer<Object> castConsumer(Consumer<?> value) { return (Consumer<Object>) value; }
    @SuppressWarnings("unchecked") private static <T> Optional<T> castOptional(Optional<Object> value) { return (Optional<T>) (Optional<?>) value; }
    @SuppressWarnings("unchecked") private static <T> List<T> castList(List<Object> value) { return (List<T>) (List<?>) value; }
}
