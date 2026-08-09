package io.github.somaruntime.soma.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fixed, conservative I3 row normalization and exact-lookup substitution. */
final class RowOptimizer {

    private RowOptimizer() {
    }

    static NormalizedRowPlan optimize(BoundRowPlan bound) {
        return optimize(
                bound.logical, bound.operation, bound.provenance, true);
    }

    static NormalizedRowPlan describe(LogicalRowPlan logical) {
        return optimize(
                logical,
                io.github.somaruntime.soma.SomaOperation.QUERY,
                new Object(),
                false);
    }

    private static NormalizedRowPlan optimize(
            LogicalRowPlan logical,
            io.github.somaruntime.soma.SomaOperation operation,
            Object provenance,
            boolean prepareMembership) {
        List<LogicalRowPlan.Stage> normalized = normalizeAdjacentTypedFilters(logical.stages());
        PredicateMembership membership = prepareMembership
                && logical.inLiteralCount() != 0L
                ? PredicateMembership.prepare(
                        logical.owner().layout(), normalized, operation, provenance)
                : null;
        if (logical.sourceKind() == LogicalRowPlan.SourceKind.INDEX_SELECTION) {
            return new NormalizedRowPlan(
                    normalized,
                    NormalizedRowPlan.SourceKind.INDEX_SELECTION,
                    logical.indexOrdinal(),
                    logical.indexProbe(),
                    membership);
        }
        if (logical.sourceKind() == LogicalRowPlan.SourceKind.RELATION_LEFT) {
            return new NormalizedRowPlan(
                    normalized,
                    NormalizedRowPlan.SourceKind.RELATION_LEFT,
                    -1,
                    null,
                    membership);
        }

        GeneratedTable table = logical.owner();
        for (LogicalRowPlan.Stage stage : normalized) {
            if (stage.kind == LogicalRowPlan.StageKind.CALLBACK_FILTER
                    || stage.kind == LogicalRowPlan.StageKind.CALLBACK_ORDER
                    || stage.kind == LogicalRowPlan.StageKind.TYPED_ORDER
                    || stage.kind == LogicalRowPlan.StageKind.DISTINCT_FIELD
                    || stage.kind == LogicalRowPlan.StageKind.FIELD_PROJECT
                    || stage.kind == LogicalRowPlan.StageKind.SKIP
                    || stage.kind == LogicalRowPlan.StageKind.LIMIT) {
                break;
            }
            if (stage.kind != LogicalRowPlan.StageKind.TYPED_FILTER) continue;
            LookupCandidate candidate = lookupCandidate(table, stage.predicate);
            if (candidate != null) {
                return new NormalizedRowPlan(
                        normalized,
                        candidate.unique
                                ? NormalizedRowPlan.SourceKind.KEY_LOOKUP
                                : NormalizedRowPlan.SourceKind.INDEX_LOOKUP,
                        candidate.indexOrdinal,
                        candidate.probe,
                        membership);
            }
        }
        return new NormalizedRowPlan(
                normalized, NormalizedRowPlan.SourceKind.TABLE_SCAN, -1, null,
                membership);
    }

    private static List<LogicalRowPlan.Stage> normalizeAdjacentTypedFilters(
            List<LogicalRowPlan.Stage> source) {
        ArrayList<LogicalRowPlan.Stage> result = new ArrayList<LogicalRowPlan.Stage>();
        PredicateIr pending = null;
        for (LogicalRowPlan.Stage stage : source) {
            if (stage.kind == LogicalRowPlan.StageKind.TYPED_FILTER) {
                pending = pending == null
                        ? stage.predicate
                        : PredicateIr.binary(PredicateIr.Kind.AND, pending, stage.predicate);
            } else {
                if (pending != null) {
                    result.add(LogicalRowPlan.Stage.typedFilter(pending));
                    pending = null;
                }
                result.add(stage);
            }
        }
        if (pending != null) result.add(LogicalRowPlan.Stage.typedFilter(pending));
        return Collections.unmodifiableList(result);
    }

    private static LookupCandidate lookupCandidate(
            GeneratedTable table,
            PredicateIr predicate) {
        if (predicate.kind == PredicateIr.Kind.AND) {
            LookupCandidate left = lookupCandidate(table, predicate.left);
            return left != null ? left : lookupCandidate(table, predicate.right);
        }
        if (predicate.kind != PredicateIr.Kind.EQ
                && predicate.kind != PredicateIr.Kind.IS_NULL) {
            return null;
        }
        int field = predicate.fieldIndex;
        GeneratedProbe probe;
        if (predicate.kind == PredicateIr.Kind.EQ) {
            probe = predicate.lower;
        } else {
            int index = table.layout().indexOrdinalForField(field);
            if (index < 0) return null;
            probe = table.newProbe(field);
            probe.putReference(table.layout().fieldStart(field), null);
            probe.seal();
        }
        if (table.layout().keyFieldIndex() == field) {
            return new LookupCandidate(true, -1, probe);
        }
        int index = table.layout().indexOrdinalForField(field);
        return index < 0 ? null : new LookupCandidate(false, index, probe);
    }

    private static final class LookupCandidate {
        final boolean unique;
        final int indexOrdinal;
        final GeneratedProbe probe;

        LookupCandidate(boolean unique, int indexOrdinal, GeneratedProbe probe) {
            this.unique = unique;
            this.indexOrdinal = indexOrdinal;
            this.probe = probe;
        }
    }
}
