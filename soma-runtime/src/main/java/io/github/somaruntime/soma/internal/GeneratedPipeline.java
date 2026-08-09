package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaOrder;
import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.UpdateResult;
import java.util.List;
import java.util.Optional;
import io.github.somaruntime.soma.SomaDoubleStream;
import io.github.somaruntime.soma.SomaIntStream;
import io.github.somaruntime.soma.SomaLongStream;
import java.util.concurrent.atomic.AtomicBoolean;

/** Linked lazy one-shot carrier for one immutable logical row plan. */
public final class GeneratedPipeline {

    private final GeneratedTable owner;
    private final LogicalRowPlan plan;
    private final AtomicBoolean consumed = new AtomicBoolean();

    GeneratedPipeline(GeneratedTable owner, LogicalRowPlan plan) {
        this.owner = owner;
        this.plan = plan;
    }

    public GeneratedPipeline filter(SomaExpression<?> expression) {
        PredicateIr next = owner.requireOwnedExpression(expression);
        claim();
        return new GeneratedPipeline(owner, plan.typedFilter(next));
    }

    public GeneratedPipeline filter(GeneratedCallbacks.RowPredicate predicate) {
        requireArgument(predicate, "predicate");
        claim();
        return new GeneratedPipeline(owner, plan.callbackFilter(predicate));
    }

    public GeneratedFieldPipeline projectField(int fieldIndex) {
        owner.layout().fieldStart(fieldIndex);
        claim();
        return new GeneratedFieldPipeline(
                owner,
                fieldIndex,
                new GeneratedPipeline(owner, plan.fieldProjection(fieldIndex)));
    }

    public GeneratedGrouping groupBy(
            int fieldIndex,
            int keyKind,
            GeneratedCallbacks.RowMapper<?> keyMaterializer) {
        owner.layout().fieldStart(fieldIndex);
        requireArgument(keyMaterializer, "group key materializer");
        claim();
        return new GeneratedGrouping(
                plan, fieldIndex, keyKind, keyMaterializer);
    }

    public <R> GeneratedMappedPipeline<R> map(
            GeneratedCallbacks.RowMapper<R> mapper) {
        requireArgument(mapper, "mapper");
        claim();
        return new GeneratedMappedPipeline<R>(MappedPlan.root(plan, mapper));
    }

    public SomaIntStream mapToInt(GeneratedCallbacks.RowToIntMapper mapper) {
        requireArgument(mapper, "mapper");
        claim();
        return GeneratedPrimitivePipeline.fromRowInt(plan, mapper);
    }

    public SomaLongStream mapToLong(GeneratedCallbacks.RowToLongMapper mapper) {
        requireArgument(mapper, "mapper");
        claim();
        return GeneratedPrimitivePipeline.fromRowLong(plan, mapper);
    }

    public SomaDoubleStream mapToDouble(
            GeneratedCallbacks.RowToDoubleMapper mapper) {
        requireArgument(mapper, "mapper");
        claim();
        return GeneratedPrimitivePipeline.fromRowDouble(plan, mapper);
    }

    GeneratedPrimitiveValuePipeline primitiveBoolean(
            GeneratedCallbacks.RowToBooleanMapper mapper) {
        requireArgument(mapper, "mapper"); claim();
        return new GeneratedPrimitiveValuePipeline(PrimitivePlan.row(
                plan, PrimitivePlan.ValueKind.BOOLEAN, mapper, false));
    }

    GeneratedPrimitiveValuePipeline primitiveByte(
            GeneratedCallbacks.RowToByteMapper mapper) {
        requireArgument(mapper, "mapper"); claim();
        return new GeneratedPrimitiveValuePipeline(PrimitivePlan.row(
                plan, PrimitivePlan.ValueKind.BYTE, mapper, false));
    }

    GeneratedPrimitiveValuePipeline primitiveShort(
            GeneratedCallbacks.RowToShortMapper mapper) {
        requireArgument(mapper, "mapper"); claim();
        return new GeneratedPrimitiveValuePipeline(PrimitivePlan.row(
                plan, PrimitivePlan.ValueKind.SHORT, mapper, false));
    }

    GeneratedPrimitiveValuePipeline primitiveChar(
            GeneratedCallbacks.RowToCharMapper mapper) {
        requireArgument(mapper, "mapper"); claim();
        return new GeneratedPrimitiveValuePipeline(PrimitivePlan.row(
                plan, PrimitivePlan.ValueKind.CHAR, mapper, false));
    }

    GeneratedPrimitiveValuePipeline primitiveInt(
            GeneratedCallbacks.RowToIntMapper mapper) {
        requireArgument(mapper, "mapper"); claim();
        return new GeneratedPrimitiveValuePipeline(PrimitivePlan.row(
                plan, PrimitivePlan.ValueKind.INT, mapper, false));
    }

    GeneratedPrimitiveValuePipeline primitiveLong(
            GeneratedCallbacks.RowToLongMapper mapper) {
        requireArgument(mapper, "mapper"); claim();
        return new GeneratedPrimitiveValuePipeline(PrimitivePlan.row(
                plan, PrimitivePlan.ValueKind.LONG, mapper, false));
    }

    GeneratedPrimitiveValuePipeline primitiveFloat(
            GeneratedCallbacks.RowToFloatMapper mapper) {
        requireArgument(mapper, "mapper"); claim();
        return new GeneratedPrimitiveValuePipeline(PrimitivePlan.row(
                plan, PrimitivePlan.ValueKind.FLOAT, mapper, false));
    }

    GeneratedPrimitiveValuePipeline primitiveDouble(
            GeneratedCallbacks.RowToDoubleMapper mapper) {
        requireArgument(mapper, "mapper"); claim();
        return new GeneratedPrimitiveValuePipeline(PrimitivePlan.row(
                plan, PrimitivePlan.ValueKind.DOUBLE, mapper, false));
    }

    public GeneratedPipeline sorted(GeneratedCallbacks.RowComparator comparator) {
        requireArgument(comparator, "comparator");
        claim();
        return new GeneratedPipeline(owner, plan.sorted(comparator));
    }

    public GeneratedPipeline distinctField(int fieldIndex) {
        owner.layout().fieldStart(fieldIndex);
        claim();
        return new GeneratedPipeline(owner, plan.distinctField(fieldIndex));
    }

    public GeneratedPipeline sortedBy(SomaOrder<?> order) {
        GeneratedOrder<?> internal = owner.requireOwnedOrder(order);
        claim();
        return new GeneratedPipeline(owner, plan.sortedBy(internal));
    }

    public GeneratedPipeline skip(long count) {
        requireNonNegative(count, "skip");
        claim();
        return new GeneratedPipeline(owner, plan.skip(count));
    }

    public GeneratedPipeline limit(long count) {
        requireNonNegative(count, "limit");
        claim();
        return new GeneratedPipeline(owner, plan.limit(count));
    }

    public GeneratedPipeline top(long count, SomaOrder<?> order) {
        requireNonNegative(count, "top");
        GeneratedOrder<?> internal = owner.requireOwnedOrder(order);
        claim();
        return new GeneratedPipeline(owner, plan.top(count, internal));
    }

    public long count() {
        claim();
        return QueryOperation.optimizedCount(plan);
    }

    public boolean anyMatch(GeneratedCallbacks.RowPredicate predicate) {
        requireArgument(predicate, "predicate");
        claim();
        return QueryOperation.anyMatch(plan, predicate);
    }

    public boolean allMatch(GeneratedCallbacks.RowPredicate predicate) {
        requireArgument(predicate, "predicate");
        claim();
        return QueryOperation.allMatch(plan, predicate);
    }

    public boolean noneMatch(GeneratedCallbacks.RowPredicate predicate) {
        requireArgument(predicate, "predicate");
        claim();
        return QueryOperation.noneMatch(plan, predicate);
    }

    public <R> Optional<R> findFirst(GeneratedCallbacks.RowMapper<R> materializer) {
        requireArgument(materializer, "materializer");
        claim();
        return QueryOperation.findFirst(plan, materializer);
    }

    public void forEach(GeneratedCallbacks.RowAction action) {
        requireArgument(action, "action");
        claim();
        QueryOperation.forEach(plan, action);
    }

    public <R> List<R> toList(GeneratedCallbacks.RowMapper<R> materializer) {
        requireArgument(materializer, "materializer");
        claim();
        return QueryOperation.toList(plan, materializer);
    }

    public <R> R[] toArray(
            GeneratedCallbacks.RowMapper<R> materializer,
            Class<R> componentType) {
        requireArgument(materializer, "materializer");
        requireArgument(componentType, "componentType");
        claim();
        return QueryOperation.toArray(plan, materializer, componentType);
    }

    public String explain() {
        claim();
        return QueryOperation.explain(plan);
    }

    public UpdateResult update(GeneratedCallbacks.EditorAction updater) {
        requireArgument(updater, SomaOperation.UPDATE, "updater");
        claim(SomaOperation.UPDATE);
        return MutationOperation.update(plan, updater);
    }

    public RemoveResult remove() {
        claim(SomaOperation.REMOVE);
        return MutationOperation.remove(plan);
    }

    long referenceCountForTesting() {
        claim();
        return QueryOperation.referenceCountForTesting(plan);
    }

    void requireNonNegativeCount(long value, String category) {
        requireNonNegative(value, category);
    }

    private void claim() {
        claim(SomaOperation.QUERY);
    }

    private void claim(SomaOperation operation) {
        if (!consumed.compareAndSet(false, true)) {
            throw SomaFailures.failure(
                    SomaFailureCode.PIPELINE_ALREADY_CONSUMED,
                    operation,
                    "linked pipeline has already been consumed",
                    new Object());
        }
    }

    private static void requireArgument(Object value, String category) {
        requireArgument(value, SomaOperation.QUERY, category);
    }

    private static void requireArgument(
            Object value,
            SomaOperation operation,
            String category) {
        if (value == null) {
            throw SomaFailures.invalid(
                    operation, category + " is null");
        }
    }

    private static void requireNonNegative(long value, String category) {
        if (value < 0L) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY, category + " is negative");
        }
    }
}
