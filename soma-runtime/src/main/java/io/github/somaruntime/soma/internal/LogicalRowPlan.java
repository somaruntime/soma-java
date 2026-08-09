package io.github.somaruntime.soma.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable linked logical row plan; it never binds a TableStateRoot. */
final class LogicalRowPlan {

    interface CardinalityStatistics {
        long distinctUpperBound(int fieldIndex);
    }

    enum SourceKind {
        TABLE_SCAN,
        INDEX_SELECTION,
        RELATION_LEFT
    }

    enum StageKind {
        TYPED_FILTER,
        CALLBACK_FILTER,
        TYPED_ORDER,
        CALLBACK_ORDER,
        DISTINCT_FIELD,
        FIELD_PROJECT,
        SKIP,
        LIMIT
    }

    static final class Stage {
        final StageKind kind;
        final PredicateIr predicate;
        final GeneratedCallbacks.RowPredicate callbackPredicate;
        final GeneratedOrder<?> order;
        final GeneratedCallbacks.RowComparator comparator;
        final long count;

        private Stage(
                StageKind kind,
                PredicateIr predicate,
                GeneratedCallbacks.RowPredicate callbackPredicate,
                GeneratedOrder<?> order,
                GeneratedCallbacks.RowComparator comparator,
                long count) {
            this.kind = kind;
            this.predicate = predicate;
            this.callbackPredicate = callbackPredicate;
            this.order = order;
            this.comparator = comparator;
            this.count = count;
        }

        static Stage typedFilter(PredicateIr predicate) {
            return new Stage(
                    StageKind.TYPED_FILTER, predicate, null, null, null, 0L);
        }

        static Stage callbackFilter(GeneratedCallbacks.RowPredicate predicate) {
            return new Stage(
                    StageKind.CALLBACK_FILTER, null, predicate, null, null, 0L);
        }

        static Stage typedOrder(GeneratedOrder<?> order) {
            return new Stage(StageKind.TYPED_ORDER, null, null, order, null, 0L);
        }

        static Stage callbackOrder(GeneratedCallbacks.RowComparator comparator) {
            return new Stage(
                    StageKind.CALLBACK_ORDER, null, null, null, comparator, 0L);
        }

        static Stage distinctField(int fieldIndex) {
            return new Stage(
                    StageKind.DISTINCT_FIELD,
                    null,
                    null,
                    null,
                    null,
                    fieldIndex);
        }

        static Stage fieldProject(int fieldIndex) {
            return new Stage(
                    StageKind.FIELD_PROJECT,
                    null,
                    null,
                    null,
                    null,
                    fieldIndex);
        }

        static Stage slice(StageKind kind, long count) {
            return new Stage(kind, null, null, null, null, count);
        }
    }

    private final GeneratedTable owner;
    private final SourceKind sourceKind;
    private final int indexOrdinal;
    private final GeneratedProbe indexProbe;
    private final GeneratedRelation relation;
    private final LogicalRowPlan parent;
    private final Stage stage;
    private final boolean parallel;

    private LogicalRowPlan(
            GeneratedTable owner,
            SourceKind sourceKind,
            int indexOrdinal,
            GeneratedProbe indexProbe,
            GeneratedRelation relation,
            LogicalRowPlan parent,
            Stage stage,
            boolean parallel) {
        this.owner = owner;
        this.sourceKind = sourceKind;
        this.indexOrdinal = indexOrdinal;
        this.indexProbe = indexProbe;
        this.relation = relation;
        this.parent = parent;
        this.stage = stage;
        this.parallel = parallel;
    }

    static LogicalRowPlan tableScan(GeneratedTable owner) {
        return new LogicalRowPlan(
                owner, SourceKind.TABLE_SCAN, -1, null, null, null, null, false);
    }

    static LogicalRowPlan indexSelection(
            GeneratedTable owner,
            int indexOrdinal,
            GeneratedProbe probe) {
        return new LogicalRowPlan(
                owner, SourceKind.INDEX_SELECTION, indexOrdinal, probe,
                null, null, null, false);
    }

    static LogicalRowPlan relationLeft(
            GeneratedTable owner,
            GeneratedRelation relation) {
        if (owner == null || relation == null) {
            throw new AssertionError("invalid relation row source");
        }
        return new LogicalRowPlan(
                owner, SourceKind.RELATION_LEFT, -1, null, relation,
                null, null, relation.isParallel());
    }

    LogicalRowPlan typedFilter(PredicateIr predicate) {
        return append(Stage.typedFilter(predicate));
    }

    LogicalRowPlan callbackFilter(GeneratedCallbacks.RowPredicate predicate) {
        return append(Stage.callbackFilter(predicate));
    }

    LogicalRowPlan sortedBy(GeneratedOrder<?> order) {
        return append(Stage.typedOrder(order));
    }

    LogicalRowPlan sorted(GeneratedCallbacks.RowComparator comparator) {
        return append(Stage.callbackOrder(comparator));
    }

    LogicalRowPlan distinctField(int fieldIndex) {
        return append(Stage.distinctField(fieldIndex));
    }

    LogicalRowPlan fieldProjection(int fieldIndex) {
        owner.layout().fieldStart(fieldIndex);
        return append(Stage.fieldProject(fieldIndex));
    }

    LogicalRowPlan skip(long count) {
        return append(Stage.slice(StageKind.SKIP, count));
    }

    LogicalRowPlan limit(long count) {
        return append(Stage.slice(StageKind.LIMIT, count));
    }

    LogicalRowPlan top(long count, GeneratedOrder<?> order) {
        return sortedBy(order).limit(count);
    }

    LogicalRowPlan parallel() {
        if (parallel) return this;
        return new LogicalRowPlan(
                owner,
                sourceKind,
                indexOrdinal,
                indexProbe,
                relation,
                parent,
                stage,
                true);
    }

    private LogicalRowPlan append(Stage next) {
        return new LogicalRowPlan(
                owner,
                sourceKind,
                indexOrdinal,
                indexProbe,
                relation,
                this,
                next,
                parallel);
    }

    GeneratedTable owner() {
        return owner;
    }

    SourceKind sourceKind() {
        return sourceKind;
    }

    int indexOrdinal() {
        return indexOrdinal;
    }

    GeneratedProbe indexProbe() {
        return indexProbe;
    }

    GeneratedRelation relation() {
        return relation;
    }

    boolean isParallel() {
        return parallel;
    }

    List<Stage> stages() {
        ArrayList<Stage> reversed = new ArrayList<Stage>();
        for (LogicalRowPlan cursor = this; cursor != null; cursor = cursor.parent) {
            if (cursor.stage != null) reversed.add(cursor.stage);
        }
        Collections.reverse(reversed);
        return Collections.unmodifiableList(reversed);
    }

    boolean hasStatefulStage() {
        for (Stage current : stages()) {
            if (current.kind == StageKind.TYPED_ORDER
                    || current.kind == StageKind.CALLBACK_ORDER
                    || current.kind == StageKind.DISTINCT_FIELD) {
                return true;
            }
        }
        return false;
    }

    long inLiteralCount() {
        long result = 0L;
        for (Stage current : stages()) {
            if (current.kind == StageKind.TYPED_FILTER) {
                result = Math.addExact(
                        result, current.predicate.inLiteralCount());
            }
        }
        return result;
    }

    long outputUpperBound(long sourceUpperBound) {
        return outputUpperBound(sourceUpperBound, null);
    }

    long outputUpperBound(
            long sourceUpperBound,
            CardinalityStatistics statistics) {
        long result = sourceUpperBound;
        for (Stage current : stages()) {
            if (current.kind == StageKind.SKIP) {
                result = current.count >= result ? 0L : result - current.count;
            } else if (current.kind == StageKind.LIMIT && current.count < result) {
                result = current.count;
            } else if (current.kind == StageKind.DISTINCT_FIELD
                    && statistics != null) {
                long distinct = statistics.distinctUpperBound((int) current.count);
                if (distinct >= 0L && distinct < result) result = distinct;
            }
        }
        return result;
    }
}
