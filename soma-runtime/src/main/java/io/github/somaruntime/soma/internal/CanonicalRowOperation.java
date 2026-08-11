package io.github.somaruntime.soma.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Data-only Canonical operation for the S1 Row filter/count closure. */
final class CanonicalRowOperation {

    enum SourceKind { TABLE, INDEX_SELECTION }
    enum TerminalKind { COUNT }

    final CanonicalTableIdentity tableIdentity;
    final SourceKind sourceKind;
    final int indexOrdinal;
    final TypedLiteral sourceLiteral;
    final List<PredicateIr> filters;
    final TerminalKind terminal;

    CanonicalRowOperation(
            CanonicalTableIdentity tableIdentity,
            SourceKind sourceKind,
            int indexOrdinal,
            TypedLiteral sourceLiteral,
            List<PredicateIr> filters,
            TerminalKind terminal) {
        if (tableIdentity == null || sourceKind == null || filters == null
                || terminal == null) {
            throw new AssertionError("invalid canonical Row operation");
        }
        if (sourceKind == SourceKind.TABLE) {
            if (indexOrdinal != -1 || sourceLiteral != null) {
                throw new AssertionError("Table source contains physical lookup state");
            }
        } else if (indexOrdinal < 0
                || sourceLiteral == null
                || !sourceLiteral.tableIdentity().sameTable(tableIdentity)
                || tableIdentity.descriptor().indexFieldIndex(indexOrdinal)
                        != sourceLiteral.fieldIndex()) {
            throw new AssertionError("invalid canonical Index source");
        }
        for (PredicateIr filter : filters) {
            if (filter == null
                    || !filter.tableIdentity.sameTable(tableIdentity)) {
                throw new AssertionError("canonical predicate owner drift");
            }
        }
        this.tableIdentity = tableIdentity;
        this.sourceKind = sourceKind;
        this.indexOrdinal = indexOrdinal;
        this.sourceLiteral = sourceLiteral;
        this.filters = Collections.unmodifiableList(
                new ArrayList<PredicateIr>(filters));
        this.terminal = terminal;
    }

    long inLiteralCount() {
        long result = 0L;
        for (PredicateIr filter : filters) {
            result = Math.addExact(result, filter.inLiteralCount());
        }
        return result;
    }
}

/** Java facade lowering for the capabilities admitted by S1. */
final class CanonicalRowLowering {

    private CanonicalRowLowering() {
    }

    static CanonicalRowOperation count(
            GeneratedTable table,
            LogicalRowPlan frontend) {
        if (table == null || frontend == null || frontend.owner() != table
                || frontend.isParallel()
                || frontend.sourceKind() == LogicalRowPlan.SourceKind.RELATION_LEFT) {
            return null;
        }
        ArrayList<PredicateIr> filters = new ArrayList<PredicateIr>();
        for (LogicalRowPlan.Stage stage : frontend.stages()) {
            if (stage.kind != LogicalRowPlan.StageKind.TYPED_FILTER) return null;
            filters.add(stage.predicate);
        }
        CanonicalTableIdentity identity = table.logicalIdentity();
        if (frontend.sourceKind() == LogicalRowPlan.SourceKind.TABLE_SCAN) {
            return new CanonicalRowOperation(
                    identity,
                    CanonicalRowOperation.SourceKind.TABLE,
                    -1,
                    null,
                    filters,
                    CanonicalRowOperation.TerminalKind.COUNT);
        }
        TypedLiteral literal = frontend.indexProbe();
        if (literal == null || literal.tableIdentity() != identity) {
            throw new AssertionError("frontend Index literal identity drift");
        }
        return new CanonicalRowOperation(
                identity,
                CanonicalRowOperation.SourceKind.INDEX_SELECTION,
                frontend.indexOrdinal(),
                literal,
                filters,
                CanonicalRowOperation.TerminalKind.COUNT);
    }
}
