package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOrder;
import io.github.somaruntime.soma.SomaDoubleStream;
import io.github.somaruntime.soma.SomaIntStream;
import io.github.somaruntime.soma.SomaLongStream;
import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.UpdateResult;
import java.util.List;
import java.util.Optional;

/** Reusable exact-Index source that creates one fresh I3 row lineage per operation. */
public final class GeneratedIndexSelection {

    private final GeneratedTable owner;
    private final int indexOrdinal;
    private final TypedLiteral probe;

    GeneratedIndexSelection(
            GeneratedTable owner,
            int indexOrdinal,
            TypedLiteral probe) {
        this.owner = owner;
        this.indexOrdinal = indexOrdinal;
        this.probe = probe;
    }

    public long count() {
        return QueryOperation.optimizedCount(
                LogicalRowPlan.indexSelection(owner, indexOrdinal, probe));
    }

    public GeneratedPipeline parallel() {
        return pipeline().parallel();
    }

    public GeneratedPipeline filter(io.github.somaruntime.soma.SomaExpression<?> expression) {
        return pipeline().filter(expression);
    }

    public GeneratedPipeline filter(GeneratedCallbacks.RowPredicate predicate) {
        return pipeline().filter(predicate);
    }

    public GeneratedFieldPipeline projectField(int fieldIndex) {
        return pipeline().projectField(fieldIndex);
    }

    public <R> GeneratedMappedPipeline<R> map(
            GeneratedCallbacks.RowMapper<R> mapper) {
        return pipeline().map(mapper);
    }

    public SomaIntStream mapToInt(GeneratedCallbacks.RowToIntMapper mapper) {
        return pipeline().mapToInt(mapper);
    }

    public SomaLongStream mapToLong(GeneratedCallbacks.RowToLongMapper mapper) {
        return pipeline().mapToLong(mapper);
    }

    public SomaDoubleStream mapToDouble(
            GeneratedCallbacks.RowToDoubleMapper mapper) {
        return pipeline().mapToDouble(mapper);
    }

    public GeneratedPipeline sorted(GeneratedCallbacks.RowComparator comparator) {
        return pipeline().sorted(comparator);
    }

    public GeneratedPipeline sortedBy(SomaOrder<?> order) {
        return pipeline().sortedBy(order);
    }

    public GeneratedPipeline skip(long count) {
        return pipeline().skip(count);
    }

    public GeneratedPipeline limit(long count) {
        return pipeline().limit(count);
    }

    public GeneratedPipeline top(long count, SomaOrder<?> order) {
        return pipeline().top(count, order);
    }

    public boolean anyMatch(GeneratedCallbacks.RowPredicate predicate) {
        return pipeline().anyMatch(predicate);
    }

    public boolean allMatch(GeneratedCallbacks.RowPredicate predicate) {
        return pipeline().allMatch(predicate);
    }

    public boolean noneMatch(GeneratedCallbacks.RowPredicate predicate) {
        return pipeline().noneMatch(predicate);
    }

    public <R> Optional<R> findFirst(GeneratedCallbacks.RowMapper<R> materializer) {
        return pipeline().findFirst(materializer);
    }

    public void forEach(GeneratedCallbacks.RowAction action) {
        pipeline().forEach(action);
    }

    public <R> List<R> toList(GeneratedCallbacks.RowMapper<R> materializer) {
        return pipeline().toList(materializer);
    }

    public <R> R[] toArray(
            GeneratedCallbacks.RowMapper<R> materializer,
            Class<R> componentType) {
        return pipeline().toArray(materializer, componentType);
    }

    public String explain() {
        return pipeline().explain();
    }

    public UpdateResult update(GeneratedCallbacks.EditorAction updater) {
        if (updater == null) {
            throw SomaFailures.invalid(
                    io.github.somaruntime.soma.SomaOperation.UPDATE,
                    "updater is null");
        }
        return pipeline().update(updater);
    }

    public RemoveResult remove() {
        return pipeline().remove();
    }

    private GeneratedPipeline pipeline() {
        return owner.indexPipeline(indexOrdinal, probe);
    }
}
