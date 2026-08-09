package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaPredicate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Immutable data-only arbitrary-reference logical projection. */
final class MappedPlan<R> {

    enum Kind { FILTER, MAP, DISTINCT, SORTED, SKIP, LIMIT }

    static final class Stage {
        final Kind kind;
        final SomaPredicate<Object> predicate;
        final Function<Object, Object> mapper;
        final Comparator<Object> comparator;
        final long count;

        private Stage(
                Kind kind,
                SomaPredicate<Object> predicate,
                Function<Object, Object> mapper,
                Comparator<Object> comparator,
                long count) {
            this.kind = kind;
            this.predicate = predicate;
            this.mapper = mapper;
            this.comparator = comparator;
            this.count = count;
        }
    }

    final LogicalRowPlan rows;
    final GeneratedCallbacks.RowMapper<?> rootMapper;
    final List<Stage> stages;

    MappedPlan(
            LogicalRowPlan rows,
            GeneratedCallbacks.RowMapper<?> rootMapper,
            List<Stage> stages) {
        this.rows = rows;
        this.rootMapper = rootMapper;
        this.stages = stages;
    }

    static <R> MappedPlan<R> root(
            LogicalRowPlan rows,
            GeneratedCallbacks.RowMapper<R> mapper) {
        return new MappedPlan<R>(rows, mapper, Collections.<Stage>emptyList());
    }

    MappedPlan<R> filter(SomaPredicate<? super R> predicate) {
        @SuppressWarnings("unchecked") SomaPredicate<Object> cast =
                (SomaPredicate<Object>) predicate;
        return append(new Stage(Kind.FILTER, cast, null, null, 0L));
    }

    <U> MappedPlan<U> map(Function<? super R, ? extends U> mapper) {
        @SuppressWarnings("unchecked") Function<Object, Object> cast =
                (Function<Object, Object>) mapper;
        return appendTyped(new Stage(Kind.MAP, null, cast, null, 0L));
    }

    MappedPlan<R> distinct() {
        return append(new Stage(Kind.DISTINCT, null, null, null, 0L));
    }

    MappedPlan<R> sorted(Comparator<? super R> comparator) {
        @SuppressWarnings("unchecked") Comparator<Object> cast =
                (Comparator<Object>) comparator;
        return append(new Stage(Kind.SORTED, null, null, cast, 0L));
    }

    MappedPlan<R> skip(long count) {
        return append(new Stage(Kind.SKIP, null, null, null, count));
    }

    MappedPlan<R> limit(long count) {
        return append(new Stage(Kind.LIMIT, null, null, null, count));
    }

    boolean hasStatefulStage() {
        if (rows.hasStatefulStage()) return true;
        return hasOwnStatefulStage();
    }

    boolean hasOwnStatefulStage() {
        for (Stage stage : stages) {
            if (stage.kind == Kind.DISTINCT || stage.kind == Kind.SORTED) return true;
        }
        return false;
    }

    long outputUpperBound(long sourceUpperBound) {
        long result = rows.outputUpperBound(sourceUpperBound);
        for (Stage stage : stages) {
            if (stage.kind == Kind.SKIP) {
                result = stage.count >= result ? 0L : result - stage.count;
            } else if (stage.kind == Kind.LIMIT && stage.count < result) {
                result = stage.count;
            }
        }
        return result;
    }

    private MappedPlan<R> append(Stage stage) {
        return appendTyped(stage);
    }

    private <U> MappedPlan<U> appendTyped(Stage stage) {
        ArrayList<Stage> next = new ArrayList<Stage>(stages.size() + 1);
        next.addAll(stages); next.add(stage);
        return new MappedPlan<U>(
                rows, rootMapper, Collections.unmodifiableList(next));
    }
}
