package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;

/** Canonical Row semantics bound once to one immutable published generation. */
final class BoundCanonicalRowOperation {

    final CanonicalRowOperation canonical;
    final GeneratedTable table;
    final GeneratedTableLayout layout;
    final TableStateRoot root;
    final SomaOperation operation;
    final Object provenance;

    BoundCanonicalRowOperation(
            CanonicalRowOperation canonical,
            GeneratedTable table,
            GeneratedTableLayout layout,
            TableStateRoot root,
            SomaOperation operation,
            Object provenance) {
        if (canonical == null || table == null || layout == null || root == null
                || operation == null || provenance == null
                || canonical.tableIdentity.descriptor() != layout
                || !canonical.tableIdentity.sameTable(table.logicalIdentity())) {
            throw new AssertionError("invalid bound Canonical Row operation");
        }
        this.canonical = canonical;
        this.table = table;
        this.layout = layout;
        this.root = root;
        this.operation = operation;
        this.provenance = provenance;
    }

    long outputUpperBound() {
        long result = root.size;
        if (canonical.sourceKind == CanonicalRowOperation.SourceKind.INDEX_SELECTION) {
            result = root.indexes[canonical.indexOrdinal].count(
                    root.directory, canonical.sourceLiteral);
        }
        for (CanonicalRowStage stage : canonical.stages) {
            if (stage.kind == CanonicalRowStage.Kind.SKIP) {
                result = stage.count >= result ? 0L : result - stage.count;
            } else if (stage.kind == CanonicalRowStage.Kind.LIMIT
                    && stage.count < result) {
                result = stage.count;
            } else if (stage.kind == CanonicalRowStage.Kind.DISTINCT_FIELD) {
                int index = layout.indexOrdinalForField(stage.fieldIndex);
                long distinct = index >= 0
                        ? root.indexes[index].distinctCount()
                        : layout.fieldKey(stage.fieldIndex) ? root.size : -1L;
                if (distinct >= 0L && distinct < result) result = distinct;
            }
        }
        return result;
    }
}
