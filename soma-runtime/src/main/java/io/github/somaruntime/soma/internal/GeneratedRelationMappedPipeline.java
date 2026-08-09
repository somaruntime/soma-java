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
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/** Detached mapped relation pipeline; Pair never leaves its root callback. */
public final class GeneratedRelationMappedPipeline<R> implements MappedStream<R> {

    private final Plan plan;
    private final AtomicBoolean consumed = new AtomicBoolean();

    private GeneratedRelationMappedPipeline(Plan plan) {
        this.plan = plan;
    }

    static <R> GeneratedRelationMappedPipeline<R> create(
            GeneratedRelation relation,
            GeneratedCallbacks.RowMapper<R> mapper) {
        return new GeneratedRelationMappedPipeline<R>(
                new Plan(relation, mapper, Collections.<Stage>emptyList()));
    }

    @Override public MappedStream<R> parallel() {
        claim();
        return new GeneratedRelationMappedPipeline<R>(plan.parallel());
    }

    @Override public MappedStream<R> filter(SomaPredicate<? super R> predicate) {
        require(predicate, "predicate"); claim();
        return next(Stage.filter(castPredicate(predicate)));
    }

    @Override public <U> MappedStream<U> map(Function<? super R, ? extends U> mapper) {
        require(mapper, "mapper"); claim();
        return new GeneratedRelationMappedPipeline<U>(plan.append(Stage.map(castMapper(mapper))));
    }

    @Override public SomaIntStream mapToInt(SomaToIntFunction<? super R> mapper) {
        require(mapper, "mapper"); claim();
        return GeneratedRelationPrimitivePipeline.intStream(plan, castToInt(mapper));
    }

    @Override public SomaLongStream mapToLong(SomaToLongFunction<? super R> mapper) {
        require(mapper, "mapper"); claim();
        return GeneratedRelationPrimitivePipeline.longStream(plan, castToLong(mapper));
    }

    @Override public SomaDoubleStream mapToDouble(SomaToDoubleFunction<? super R> mapper) {
        require(mapper, "mapper"); claim();
        return GeneratedRelationPrimitivePipeline.doubleStream(plan, castToDouble(mapper));
    }

    @Override public MappedStream<R> distinct() { claim(); return next(Stage.distinct()); }
    @Override public MappedStream<R> sorted(Comparator<? super R> comparator) {
        require(comparator, "comparator"); claim(); return next(Stage.sorted(castComparator(comparator)));
    }
    @Override public MappedStream<R> top(long count, Comparator<? super R> comparator) {
        requireCount(count, "top"); require(comparator, "comparator"); claim();
        return new GeneratedRelationMappedPipeline<R>(
                plan.append(Stage.sorted(castComparator(comparator))).append(Stage.limit(count)));
    }
    @Override public MappedStream<R> skip(long count) {
        requireCount(count, "skip"); claim(); return next(Stage.skip(count));
    }
    @Override public MappedStream<R> limit(long count) {
        requireCount(count, "limit"); claim(); return next(Stage.limit(count));
    }

    @Override public long count() { claim(); return values().size(); }
    @Override public boolean anyMatch(SomaPredicate<? super R> predicate) {
        require(predicate, "predicate"); claim();
        for (Object value : values()) if (test(castPredicate(predicate), value)) return true;
        return false;
    }
    @Override public boolean allMatch(SomaPredicate<? super R> predicate) {
        require(predicate, "predicate"); claim();
        for (Object value : values()) if (!test(castPredicate(predicate), value)) return false;
        return true;
    }
    @Override public boolean noneMatch(SomaPredicate<? super R> predicate) {
        require(predicate, "predicate"); claim();
        for (Object value : values()) if (test(castPredicate(predicate), value)) return false;
        return true;
    }
    @Override public Optional<R> findFirst() {
        claim(); List<Object> values = values();
        if (values.isEmpty()) return Optional.empty();
        @SuppressWarnings("unchecked") R value = (R) values.get(0);
        if (value == null) throw SomaFailures.failure(
                SomaFailureCode.NULL_VALUE_UNSUPPORTED, SomaOperation.QUERY,
                "selected null cannot be represented by Java Optional", new Object());
        return Optional.of(value);
    }
    @Override public Optional<R> min(Comparator<? super R> comparator) {
        require(comparator, "comparator"); claim(); return extremum(castComparator(comparator), false);
    }
    @Override public Optional<R> max(Comparator<? super R> comparator) {
        require(comparator, "comparator"); claim(); return extremum(castComparator(comparator), true);
    }
    @Override public void forEach(Consumer<? super R> action) {
        require(action, "action"); claim();
        for (Object value : values()) accept(castConsumer(action), value);
    }
    @Override public void forEachOrdered(Consumer<? super R> action) { forEach(action); }
    @Override public List<R> toList() {
        claim();
        @SuppressWarnings("unchecked") List<R> result = (List<R>) (List<?>) values();
        return result;
    }
    @Override public <A> A[] toArray(Class<A> componentType) {
        require(componentType, "componentType");
        if (componentType.isPrimitive()) throw SomaFailures.invalid(
                SomaOperation.QUERY, "mapped array componentType is primitive");
        claim(); List<Object> values = values();
        @SuppressWarnings("unchecked") A[] result =
                (A[]) Array.newInstance(componentType, values.size());
        for (int i = 0; i < values.size(); i++) {
            try { result[i] = componentType.cast(values.get(i)); }
            catch (ClassCastException failure) {
                throw SomaFailures.invalid(SomaOperation.QUERY,
                        "mapped value is incompatible with componentType");
            }
        }
        return result;
    }
    @Override public String _explain() {
        claim(); return "SOMA relation-mapped stages=" + plan.stages.size();
    }

    private GeneratedRelationMappedPipeline<R> next(Stage stage) {
        return new GeneratedRelationMappedPipeline<R>(plan.append(stage));
    }

    private List<Object> values() {
        return materialize(plan);
    }

    static List<Object> materialize(final Plan plan) {
        return plan.relation.terminal(
                new GeneratedRelation.PairWork<List<Object>>() {
                    @Override public List<Object> run(
                            final GeneratedRelation.RelationBinding binding) {
                        int upper = RowExecutionSupport.arrayLength(
                                plan.relation.outputUpperBound(binding), binding.provenance);
                        final ArrayList<Object> values = new ArrayList<Object>(upper);
                        plan.relation.visitBound(binding, true, new GeneratedRelation.PairVisitor() {
                            @Override public boolean visit(long left, long right) {
                                Object value;
                                CallbackExecutionScope.enter();
                                try { value = plan.mapper.apply(); }
                                catch (Exception failure) {
                                    throw SomaFailures.callbackFailure(
                                            SomaOperation.QUERY, failure, binding.provenance);
                                } finally { CallbackExecutionScope.exit(); }
                                if (plan.relation.isBorrowed(value)) {
                                    throw SomaFailures.failure(
                                            SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                                            SomaOperation.QUERY,
                                            "borrowed relation value escaped mapper callback",
                                            binding.provenance);
                                }
                                values.add(value);
                                return true;
                            }
                        });
                        applyStages(values, plan.stages, binding.provenance);
                        return values;
                    }
                }, true, 72L);
    }

    private Optional<R> extremum(Comparator<Object> comparator, boolean maximum) {
        List<Object> values = values();
        if (values.isEmpty()) return Optional.empty();
        Object best = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            int compared = compare(comparator, values.get(i), best);
            if (maximum ? compared > 0 : compared < 0) best = values.get(i);
        }
        if (best == null) throw SomaFailures.failure(
                SomaFailureCode.NULL_VALUE_UNSUPPORTED, SomaOperation.QUERY,
                "selected null cannot be represented by Java Optional", new Object());
        @SuppressWarnings("unchecked") R result = (R) best;
        return Optional.of(result);
    }

    private static void applyStages(
            ArrayList<Object> values,
            List<Stage> stages,
            Object provenance) {
        for (Stage stage : stages) {
            switch (stage.kind) {
                case FILTER:
                    for (int i = values.size() - 1; i >= 0; i--) {
                        if (!test(stage.predicate, values.get(i))) values.remove(i);
                    }
                    break;
                case MAP:
                    for (int i = 0; i < values.size(); i++) {
                        values.set(i, apply(stage.mapper, values.get(i)));
                    }
                    break;
                case DISTINCT:
                    try {
                        LinkedHashSet<Object> unique = new LinkedHashSet<Object>(values);
                        values.clear(); values.addAll(unique);
                    } catch (Exception failure) {
                        throw SomaFailures.callbackFailure(
                                SomaOperation.QUERY, failure, provenance);
                    }
                    break;
                case SORTED:
                    try { Collections.sort(values, stage.comparator); }
                    catch (Exception failure) {
                        throw SomaFailures.callbackFailure(
                                SomaOperation.QUERY, failure, provenance);
                    }
                    break;
                case SKIP:
                    int skip = (int) Math.min((long) values.size(), stage.count);
                    if (skip != 0) values.subList(0, skip).clear();
                    break;
                case LIMIT:
                    if (stage.count < values.size()) {
                        values.subList((int) stage.count, values.size()).clear();
                    }
                    break;
                default: throw new AssertionError("unknown relation mapped stage");
            }
        }
    }

    private void claim() {
        if (!consumed.compareAndSet(false, true)) throw SomaFailures.failure(
                SomaFailureCode.PIPELINE_ALREADY_CONSUMED, SomaOperation.QUERY,
                "linked mapped relation pipeline has already been consumed", new Object());
    }
    private static void require(Object value, String category) {
        if (value == null) throw SomaFailures.invalid(SomaOperation.QUERY, category + " is null");
    }
    private static void requireCount(long value, String category) {
        if (value < 0L) throw SomaFailures.invalid(SomaOperation.QUERY, category + " is negative");
    }
    private static boolean test(SomaPredicate<Object> predicate, Object value) {
        CallbackExecutionScope.enter();
        try { return predicate.test(value); }
        catch (Exception failure) { throw SomaFailures.callbackFailure(SomaOperation.QUERY, failure, new Object()); }
        finally { CallbackExecutionScope.exit(); }
    }
    private static Object apply(Function<Object, Object> mapper, Object value) {
        CallbackExecutionScope.enter();
        try { return mapper.apply(value); }
        catch (Exception failure) { throw SomaFailures.callbackFailure(SomaOperation.QUERY, failure, new Object()); }
        finally { CallbackExecutionScope.exit(); }
    }
    private static int compare(Comparator<Object> comparator, Object left, Object right) {
        CallbackExecutionScope.enter();
        try { return comparator.compare(left, right); }
        catch (Exception failure) { throw SomaFailures.callbackFailure(SomaOperation.QUERY, failure, new Object()); }
        finally { CallbackExecutionScope.exit(); }
    }
    private static void accept(Consumer<Object> consumer, Object value) {
        CallbackExecutionScope.enter();
        try { consumer.accept(value); }
        catch (Exception failure) { throw SomaFailures.callbackFailure(SomaOperation.QUERY, failure, new Object()); }
        finally { CallbackExecutionScope.exit(); }
    }

    static final class Plan {
        final GeneratedRelation relation;
        final GeneratedCallbacks.RowMapper<?> mapper;
        final List<Stage> stages;
        Plan(GeneratedRelation relation, GeneratedCallbacks.RowMapper<?> mapper, List<Stage> stages) {
            this.relation = relation; this.mapper = mapper; this.stages = stages;
        }
        Plan append(Stage stage) {
            ArrayList<Stage> next = new ArrayList<Stage>(stages.size() + 1);
            next.addAll(stages); next.add(stage);
            return new Plan(relation, mapper, Collections.unmodifiableList(next));
        }
        Plan parallel() {
            return new Plan(relation.parallelCopy(), mapper, stages);
        }
    }

    private enum Kind { FILTER, MAP, DISTINCT, SORTED, SKIP, LIMIT }
    static final class Stage {
        final Kind kind;
        final SomaPredicate<Object> predicate;
        final Function<Object, Object> mapper;
        final Comparator<Object> comparator;
        final long count;
        private Stage(Kind kind, SomaPredicate<Object> predicate,
                Function<Object, Object> mapper, Comparator<Object> comparator, long count) {
            this.kind = kind; this.predicate = predicate; this.mapper = mapper;
            this.comparator = comparator; this.count = count;
        }
        static Stage filter(SomaPredicate<Object> value) { return new Stage(Kind.FILTER, value, null, null, 0L); }
        static Stage map(Function<Object, Object> value) { return new Stage(Kind.MAP, null, value, null, 0L); }
        static Stage distinct() { return new Stage(Kind.DISTINCT, null, null, null, 0L); }
        static Stage sorted(Comparator<Object> value) { return new Stage(Kind.SORTED, null, null, value, 0L); }
        static Stage skip(long value) { return new Stage(Kind.SKIP, null, null, null, value); }
        static Stage limit(long value) { return new Stage(Kind.LIMIT, null, null, null, value); }
    }

    @SuppressWarnings("unchecked") private static SomaPredicate<Object> castPredicate(SomaPredicate<?> v) { return (SomaPredicate<Object>) v; }
    @SuppressWarnings("unchecked") private static Function<Object, Object> castMapper(Function<?, ?> v) { return (Function<Object, Object>) v; }
    @SuppressWarnings("unchecked") private static Comparator<Object> castComparator(Comparator<?> v) { return (Comparator<Object>) v; }
    @SuppressWarnings("unchecked") private static Consumer<Object> castConsumer(Consumer<?> v) { return (Consumer<Object>) v; }
    @SuppressWarnings("unchecked") private static SomaToIntFunction<Object> castToInt(SomaToIntFunction<?> v) { return (SomaToIntFunction<Object>) v; }
    @SuppressWarnings("unchecked") private static SomaToLongFunction<Object> castToLong(SomaToLongFunction<?> v) { return (SomaToLongFunction<Object>) v; }
    @SuppressWarnings("unchecked") private static SomaToDoubleFunction<Object> castToDouble(SomaToDoubleFunction<?> v) { return (SomaToDoubleFunction<Object>) v; }
}
