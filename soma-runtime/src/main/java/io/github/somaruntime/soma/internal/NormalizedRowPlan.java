package io.github.somaruntime.soma.internal;

import java.util.List;

/** Immutable output of the fixed I3 row normalization and source-selection phases. */
final class NormalizedRowPlan {

    enum SourceKind {
        TABLE_SCAN,
        RELATION_LEFT,
        INDEX_SELECTION,
        KEY_LOOKUP,
        INDEX_LOOKUP
    }

    final List<LogicalRowPlan.Stage> stages;
    final SourceKind sourceKind;
    final int indexOrdinal;
    final TypedLiteral probe;
    final PredicateMembership membership;

    NormalizedRowPlan(
            List<LogicalRowPlan.Stage> stages,
            SourceKind sourceKind,
            int indexOrdinal,
            TypedLiteral probe,
            PredicateMembership membership) {
        this.stages = stages;
        this.sourceKind = sourceKind;
        this.indexOrdinal = indexOrdinal;
        this.probe = probe;
        this.membership = membership;
    }
}
