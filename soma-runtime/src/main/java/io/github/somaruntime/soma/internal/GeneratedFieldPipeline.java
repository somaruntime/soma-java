package io.github.somaruntime.soma.internal;

import java.util.List;
import java.util.Optional;
import io.github.somaruntime.soma.SomaDoubleStream;
import io.github.somaruntime.soma.SomaIntStream;
import io.github.somaruntime.soma.SomaLongStream;

/** One-shot schema-known Field projection over an existing row lineage. */
public final class GeneratedFieldPipeline {

    private final GeneratedTable owner;
    private final int fieldIndex;
    private final GeneratedPipeline rows;

    GeneratedFieldPipeline(
            GeneratedTable owner,
            int fieldIndex,
            GeneratedPipeline rows) {
        this.owner = owner;
        this.fieldIndex = fieldIndex;
        this.rows = rows;
    }

    public GeneratedFieldPipeline filter(GeneratedCallbacks.RowPredicate predicate) {
        return new GeneratedFieldPipeline(owner, fieldIndex, rows.filter(predicate));
    }

    public <R> GeneratedMappedPipeline<R> map(
            GeneratedCallbacks.RowMapper<R> mapper) {
        return rows.map(mapper);
    }

    public SomaIntStream mapToInt(GeneratedCallbacks.RowToIntMapper mapper) {
        return rows.mapToInt(mapper);
    }

    public SomaLongStream mapToLong(GeneratedCallbacks.RowToLongMapper mapper) {
        return rows.mapToLong(mapper);
    }

    public SomaDoubleStream mapToDouble(
            GeneratedCallbacks.RowToDoubleMapper mapper) {
        return rows.mapToDouble(mapper);
    }

    public GeneratedPrimitiveValuePipeline primitiveBoolean(
            GeneratedCallbacks.RowToBooleanMapper mapper) {
        return rows.primitiveBoolean(mapper);
    }
    public GeneratedPrimitiveValuePipeline primitiveByte(
            GeneratedCallbacks.RowToByteMapper mapper) {
        return rows.primitiveByte(mapper);
    }
    public GeneratedPrimitiveValuePipeline primitiveShort(
            GeneratedCallbacks.RowToShortMapper mapper) {
        return rows.primitiveShort(mapper);
    }
    public GeneratedPrimitiveValuePipeline primitiveChar(
            GeneratedCallbacks.RowToCharMapper mapper) {
        return rows.primitiveChar(mapper);
    }
    public GeneratedPrimitiveValuePipeline primitiveInt(
            GeneratedCallbacks.RowToIntMapper mapper) {
        return rows.primitiveInt(mapper);
    }
    public GeneratedPrimitiveValuePipeline primitiveLong(
            GeneratedCallbacks.RowToLongMapper mapper) {
        return rows.primitiveLong(mapper);
    }
    public GeneratedPrimitiveValuePipeline primitiveFloat(
            GeneratedCallbacks.RowToFloatMapper mapper) {
        return rows.primitiveFloat(mapper);
    }
    public GeneratedPrimitiveValuePipeline primitiveDouble(
            GeneratedCallbacks.RowToDoubleMapper mapper) {
        return rows.primitiveDouble(mapper);
    }

    public GeneratedFieldPipeline distinct() {
        return new GeneratedFieldPipeline(
                owner, fieldIndex, rows.distinctField(fieldIndex));
    }

    public GeneratedFieldPipeline sortedNatural() {
        return sortedNatural(false);
    }

    public GeneratedFieldPipeline sortedNatural(boolean descending) {
        return new GeneratedFieldPipeline(
                owner,
                fieldIndex,
                rows.sortedBy(descending
                        ? owner.desc(fieldIndex)
                        : owner.asc(fieldIndex)));
    }

    public GeneratedFieldPipeline sorted(GeneratedCallbacks.RowComparator comparator) {
        return new GeneratedFieldPipeline(
                owner, fieldIndex, rows.sorted(comparator));
    }

    public GeneratedFieldPipeline skip(long count) {
        return new GeneratedFieldPipeline(owner, fieldIndex, rows.skip(count));
    }

    public GeneratedFieldPipeline limit(long count) {
        return new GeneratedFieldPipeline(owner, fieldIndex, rows.limit(count));
    }

    public GeneratedFieldPipeline topNatural(long count) {
        return new GeneratedFieldPipeline(
                owner, fieldIndex, rows.top(count, owner.asc(fieldIndex)));
    }

    public GeneratedFieldPipeline topNatural(long count, boolean descending) {
        return new GeneratedFieldPipeline(
                owner,
                fieldIndex,
                rows.top(
                        count,
                        descending ? owner.desc(fieldIndex) : owner.asc(fieldIndex)));
    }

    public GeneratedFieldPipeline top(
            long count,
            GeneratedCallbacks.RowComparator comparator) {
        rows.requireNonNegativeCount(count, "top");
        return sorted(comparator).limit(count);
    }

    public long count() {
        return rows.count();
    }

    public boolean anyMatch(GeneratedCallbacks.RowPredicate predicate) {
        return rows.anyMatch(predicate);
    }

    public boolean allMatch(GeneratedCallbacks.RowPredicate predicate) {
        return rows.allMatch(predicate);
    }

    public boolean noneMatch(GeneratedCallbacks.RowPredicate predicate) {
        return rows.noneMatch(predicate);
    }

    public <R> Optional<R> findFirst(GeneratedCallbacks.RowMapper<R> materializer) {
        return rows.findFirst(materializer);
    }

    public void forEach(GeneratedCallbacks.RowAction action) {
        rows.forEach(action);
    }

    public <R> List<R> toList(GeneratedCallbacks.RowMapper<R> materializer) {
        return rows.toList(materializer);
    }

    public <R> R[] toArray(
            GeneratedCallbacks.RowMapper<R> materializer,
            Class<R> componentType) {
        return rows.toArray(materializer, componentType);
    }

    public String explain() {
        return rows.explain();
    }

}
