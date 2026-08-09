package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** Shared generic implementation behind all generated binary-relation entrypoints. */
public final class GeneratedJoinCarriers {

    private GeneratedJoinCarriers() {}

    public static <L, R, LS> SomaJoinOnBuilder<L, R, LS> equality(
            GeneratedRelationAdapter<L, LS> left,
            GeneratedRelationAdapter<R, ?> right) {
        GeneratedRelation relation = GeneratedRelation.equality(
                left.table(), right.table());
        Pair<L, R> pair = new Pair<L, R>(left, right, relation.pairAccess());
        return new On<L, R, LS>(left, right, relation, pair);
    }

    public static <L, R, LS> SomaMatchedJoinStream<L, R> cross(
            GeneratedRelationAdapter<L, LS> left,
            GeneratedRelationAdapter<R, ?> right,
            long maxOutputRows) {
        GeneratedRelation relation = GeneratedRelation.cross(
                left.table(), right.table(), maxOutputRows);
        Pair<L, R> pair = new Pair<L, R>(left, right, relation.pairAccess());
        return new Matched<L, R, LS>(left, right, relation, pair);
    }

    private static final class Pair<L, R> implements JoinPair<L, R> {
        private final GeneratedRelationAdapter<L, ?> left;
        private final GeneratedRelationAdapter<R, ?> right;
        private final GeneratedPairAccess access;
        Pair(
                GeneratedRelationAdapter<L, ?> left,
                GeneratedRelationAdapter<R, ?> right,
                GeneratedPairAccess access) {
            this.left = left;
            this.right = right;
            this.access = access;
            access.register(this);
        }
        @Override public boolean hasLeft() { return access.hasLeft(); }
        @Override public boolean hasRight() { return access.hasRight(); }
        @Override public L left() { access.requireLeft(); return left.view(); }
        @Override public R right() { access.requireRight(); return right.view(); }
    }

    private static final class On<L, R, LS>
            implements SomaJoinOnBuilder<L, R, LS> {
        private final GeneratedRelationAdapter<L, LS> left;
        private final GeneratedRelationAdapter<R, ?> right;
        private final GeneratedRelation relation;
        private final Pair<L, R> pair;
        On(GeneratedRelationAdapter<L, LS> left,
                GeneratedRelationAdapter<R, ?> right,
                GeneratedRelation relation,
                Pair<L, R> pair) {
            this.left = left; this.right = right;
            this.relation = relation; this.pair = pair;
        }
        @Override public <V> SomaJoinCondition<L, R, LS> on(
                SomaKeyableField<L, V> leftField,
                SomaKeyableField<R, V> rightField) {
            return new Condition<L, R, LS>(left, right,
                    relation.on(
                            left.keyableFieldIndex(leftField),
                            right.keyableFieldIndex(rightField)),
                    pair);
        }
    }

    private abstract static class Base<L, R, LS>
            implements SomaPairStream<L, R> {
        final GeneratedRelationAdapter<L, LS> left;
        final GeneratedRelationAdapter<R, ?> right;
        final GeneratedRelation relation;
        final Pair<L, R> pair;
        Base(GeneratedRelationAdapter<L, LS> left,
                GeneratedRelationAdapter<R, ?> right,
                GeneratedRelation relation,
                Pair<L, R> pair) {
            this.left = left; this.right = right;
            this.relation = relation; this.pair = pair;
        }
        abstract SomaPairStream<L, R> filtered(GeneratedRelation next);
        @Override public SomaPairStream<L, R> parallel() {
            return filtered(relation.parallel());
        }
        @Override public SomaPairStream<L, R> filter(SomaRelationExpression expression) {
            return filtered(relation.filter(expression));
        }
        @Override public SomaPairStream<L, R> filter(
                SomaPredicate<? super JoinPair<L, R>> predicate) {
            require(predicate, "predicate");
            return filtered(relation.filter(() -> predicate.test(pair)));
        }
        @Override public <T> MappedStream<T> map(
                Function<? super JoinPair<L, R>, ? extends T> mapper) {
            require(mapper, "mapper");
            return relation.map(() -> mapper.apply(pair));
        }
        @Override public SomaIntStream mapToInt(
                SomaToIntFunction<? super JoinPair<L, R>> mapper) {
            require(mapper, "mapper");
            return relation.mapToInt(() -> mapper.applyAsInt(pair));
        }
        @Override public SomaLongStream mapToLong(
                SomaToLongFunction<? super JoinPair<L, R>> mapper) {
            require(mapper, "mapper");
            return relation.mapToLong(() -> mapper.applyAsLong(pair));
        }
        @Override public SomaDoubleStream mapToDouble(
                SomaToDoubleFunction<? super JoinPair<L, R>> mapper) {
            require(mapper, "mapper");
            return relation.mapToDouble(() -> mapper.applyAsDouble(pair));
        }
        @Override public long count() { return relation.count(); }
        @Override public boolean anyMatch(SomaPredicate<? super JoinPair<L, R>> p) {
            require(p, "predicate"); return relation.anyMatch(() -> p.test(pair));
        }
        @Override public boolean allMatch(SomaPredicate<? super JoinPair<L, R>> p) {
            require(p, "predicate"); return relation.allMatch(() -> p.test(pair));
        }
        @Override public boolean noneMatch(SomaPredicate<? super JoinPair<L, R>> p) {
            require(p, "predicate"); return relation.noneMatch(() -> p.test(pair));
        }
        @Override public void forEach(Consumer<? super JoinPair<L, R>> action) {
            require(action, "action"); relation.forEach(() -> action.accept(pair));
        }
        @Override public void forEachOrdered(Consumer<? super JoinPair<L, R>> action) {
            forEach(action);
        }
        @Override public String _explain() { return relation.explain(); }
    }

    private static class Matched<L, R, LS> extends Base<L, R, LS>
            implements SomaMatchedJoinStream<L, R> {
        Matched(GeneratedRelationAdapter<L, LS> left,
                GeneratedRelationAdapter<R, ?> right,
                GeneratedRelation relation,
                Pair<L, R> pair) {
            super(left, right, relation, pair);
        }
        @Override SomaPairStream<L, R> filtered(GeneratedRelation next) {
            return new Matched<L, R, LS>(left, right, next, pair);
        }
        @Override public SomaMatchedJoinStream<L, R> parallel() {
            return new Matched<L, R, LS>(
                    left, right, relation.parallel(), pair);
        }
        @Override public SomaMatchedJoinStream<L, R> filter(
                SomaRelationExpression expression) {
            return new Matched<L, R, LS>(
                    left, right, relation.filter(expression), pair);
        }
        @Override public SomaMatchedJoinStream<L, R> filter(
                SomaPredicate<? super JoinPair<L, R>> predicate) {
            require(predicate, "predicate");
            return new Matched<L, R, LS>(left, right,
                    relation.filter(() -> predicate.test(pair)), pair);
        }
        @Override public <A, B> MappedStream<SomaTuple2<A, B>> select(
                SomaFieldEndpoint<L, A> leftField,
                SomaFieldEndpoint<R, B> rightField) {
            final int leftIndex = left.fieldIndex(leftField);
            final int rightIndex = right.fieldIndex(rightField);
            return relation.map(() -> tuple(leftIndex, rightIndex));
        }
        @SuppressWarnings("unchecked")
        private <A, B> SomaTuple2<A, B> tuple(int leftIndex, int rightIndex) {
            return (SomaTuple2<A, B>) GeneratedRuntime.tuple(
                    left.fieldValue(leftIndex), right.fieldValue(rightIndex));
        }
    }

    private static final class Condition<L, R, LS> extends Matched<L, R, LS>
            implements SomaJoinCondition<L, R, LS> {
        Condition(GeneratedRelationAdapter<L, LS> left,
                GeneratedRelationAdapter<R, ?> right,
                GeneratedRelation relation,
                Pair<L, R> pair) {
            super(left, right, relation, pair);
        }
        @Override public <V> SomaJoinCondition<L, R, LS> and(
                SomaKeyableField<L, V> leftField,
                SomaKeyableField<R, V> rightField) {
            return new Condition<L, R, LS>(left, right,
                    relation.and(
                            left.keyableFieldIndex(leftField),
                            right.keyableFieldIndex(rightField)),
                    pair);
        }
        @Override public SomaJoinCondition<L, R, LS> parallel() {
            return new Condition<L, R, LS>(
                    left, right, relation.parallel(), pair);
        }
        @Override public SomaMatchedJoinStream<L, R> inner() {
            return new Matched<L, R, LS>(left, right,
                    relation.kind(GeneratedRelation.INNER), pair);
        }
        @Override public SomaOuterJoinStream<L, R> left() {
            return new Outer<L, R, LS>(left, right,
                    relation.kind(GeneratedRelation.LEFT), pair);
        }
        @Override public SomaOuterJoinStream<L, R> full() {
            return new Outer<L, R, LS>(left, right,
                    relation.kind(GeneratedRelation.FULL), pair);
        }
        @Override public LS semi() {
            return left.readStream(relation.kind(GeneratedRelation.SEMI));
        }
        @Override public LS anti() {
            return left.readStream(relation.kind(GeneratedRelation.ANTI));
        }
    }

    private static final class Outer<L, R, LS> extends Base<L, R, LS>
            implements SomaOuterJoinStream<L, R> {
        Outer(GeneratedRelationAdapter<L, LS> left,
                GeneratedRelationAdapter<R, ?> right,
                GeneratedRelation relation,
                Pair<L, R> pair) {
            super(left, right, relation, pair);
        }
        @Override SomaPairStream<L, R> filtered(GeneratedRelation next) {
            return new Outer<L, R, LS>(left, right, next, pair);
        }
        @Override public SomaOuterJoinStream<L, R> parallel() {
            return new Outer<L, R, LS>(
                    left, right, relation.parallel(), pair);
        }
        @Override public SomaOuterJoinStream<L, R> filter(
                SomaRelationExpression expression) {
            return new Outer<L, R, LS>(
                    left, right, relation.filter(expression), pair);
        }
        @Override public SomaOuterJoinStream<L, R> filter(
                SomaPredicate<? super JoinPair<L, R>> predicate) {
            require(predicate, "predicate");
            return new Outer<L, R, LS>(left, right,
                    relation.filter(() -> predicate.test(pair)), pair);
        }
    }

    private static void require(Object value, String category) {
        if (value == null) throw SomaFailures.invalid(
                SomaOperation.QUERY, "Join " + category + " is null");
    }
}
