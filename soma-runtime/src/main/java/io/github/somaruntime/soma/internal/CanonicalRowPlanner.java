package io.github.somaruntime.soma.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fixed S1 normalization, exact lookup substitution and resource estimate. */
final class CanonicalRowPlanner {

    private CanonicalRowPlanner() {
    }

    static NormalizedCanonicalRow normalize(BoundCanonicalRowOperation bound) {
        ArrayList<CanonicalRowStage> stages = new ArrayList<CanonicalRowStage>();
        PredicateIr pending = null;
        for (CanonicalRowStage stage : bound.canonical.stages) {
            if (stage.kind == CanonicalRowStage.Kind.TYPED_FILTER) {
                PredicateIr filter = normalizePredicate(
                        stage.predicate, bound.layout);
                pending = pending == null
                        ? filter
                        : normalizeBinary(PredicateIr.Kind.AND, pending, filter);
                continue;
            }
            if (pending != null) {
                stages.add(CanonicalRowStage.typedFilter(pending));
                pending = null;
            }
            stages.add(stage);
        }
        if (pending != null) stages.add(CanonicalRowStage.typedFilter(pending));
        return new NormalizedCanonicalRow(bound, stages);
    }

    private static PredicateIr normalizePredicate(
            PredicateIr predicate,
            GeneratedTableLayout layout) {
        switch (predicate.kind) {
            case AND:
            case OR:
                return normalizeBinary(
                        predicate.kind,
                        normalizePredicate(predicate.left, layout),
                        normalizePredicate(predicate.right, layout));
            case NOT:
                PredicateIr source = normalizePredicate(predicate.left, layout);
                if (source.kind == PredicateIr.Kind.CONSTANT) {
                    return PredicateIr.constant(
                            source.tableIdentity, !source.constant);
                }
                if (source.kind == PredicateIr.Kind.NOT) return source.left;
                return PredicateIr.not(source);
            case IS_NULL:
                return layout.fieldNullable(predicate.fieldIndex)
                        ? predicate
                        : PredicateIr.constant(predicate.tableIdentity, false);
            case IS_NOT_NULL:
                return layout.fieldNullable(predicate.fieldIndex)
                        ? predicate
                        : PredicateIr.constant(predicate.tableIdentity, true);
            case IN:
                if (predicate.literals.length == 0) {
                    return PredicateIr.constant(predicate.tableIdentity, false);
                }
                if (predicate.literals.length == 1) {
                    return PredicateIr.compare(
                            PredicateIr.Kind.EQ,
                            predicate.tableIdentity,
                            predicate.fieldIndex,
                            predicate.literals[0]);
                }
                return predicate;
            default:
                return predicate;
        }
    }

    private static PredicateIr normalizeBinary(
            PredicateIr.Kind kind,
            PredicateIr left,
            PredicateIr right) {
        if (kind == PredicateIr.Kind.AND) {
            if (isConstant(left, false) || isConstant(right, false)) {
                return PredicateIr.constant(left.tableIdentity, false);
            }
            if (isConstant(left, true)) return right;
            if (isConstant(right, true)) return left;
        } else if (kind == PredicateIr.Kind.OR) {
            if (isConstant(left, true) || isConstant(right, true)) {
                return PredicateIr.constant(left.tableIdentity, true);
            }
            if (isConstant(left, false)) return right;
            if (isConstant(right, false)) return left;
        } else {
            throw new AssertionError("non-boolean Canonical binary node");
        }
        return PredicateIr.binary(kind, left, right);
    }

    private static boolean isConstant(PredicateIr predicate, boolean value) {
        return predicate.kind == PredicateIr.Kind.CONSTANT
                && predicate.constant == value;
    }

    static CanonicalRowPhysicalPlan plan(NormalizedCanonicalRow normalized) {
        BoundCanonicalRowOperation bound = normalized.bound;
        CanonicalRowPhysicalPlan.AccessPath access;
        int indexOrdinal = -1;
        TypedLiteral literal = null;
        if (bound.canonical.sourceKind
                == CanonicalRowOperation.SourceKind.INDEX_SELECTION) {
            access = CanonicalRowPhysicalPlan.AccessPath.INDEX_SELECTION;
            indexOrdinal = bound.canonical.indexOrdinal;
            literal = bound.canonical.sourceLiteral;
        } else {
            LookupCandidate candidate = lookupCandidate(
                    bound, normalized.stages);
            if (candidate == null) {
                access = CanonicalRowPhysicalPlan.AccessPath.TABLE_SCAN;
            } else {
                access = candidate.key
                        ? CanonicalRowPhysicalPlan.AccessPath.KEY_LOOKUP
                        : CanonicalRowPhysicalPlan.AccessPath.INDEX_LOOKUP;
                indexOrdinal = candidate.indexOrdinal;
                literal = candidate.literal;
            }
        }
        long temporaryBytes = CheckedLong.multiply(
                bound.canonical.inLiteralCount(),
                256L,
                bound.operation,
                bound.provenance);
        if (bound.canonical.hasStatefulStage()) {
            temporaryBytes = CheckedLong.add(
                    temporaryBytes,
                    RowExecutionSupport.arrayBytes(
                            bound.root.size, 96L, bound.provenance),
                    bound.operation,
                    bound.provenance);
        }
        if (bound.canonical.terminal == CanonicalRowOperation.TerminalKind.LOCATORS_TEST
                || bound.canonical.terminal == CanonicalRowOperation.TerminalKind.UPDATE
                || bound.canonical.terminal == CanonicalRowOperation.TerminalKind.REMOVE) {
            temporaryBytes = CheckedLong.add(
                    temporaryBytes,
                    RowExecutionSupport.arrayBytes(
                            bound.root.size, 24L, bound.provenance),
                    bound.operation,
                    bound.provenance);
        }
        if (bound.canonical.request.mode == ExecutionRequest.Mode.PARALLEL
                && bound.canonical.beginsWithTypedFilter()
                && access == CanonicalRowPhysicalPlan.AccessPath.TABLE_SCAN) {
            temporaryBytes = CheckedLong.add(
                    temporaryBytes,
                    RowExecutionSupport.arrayBytes(
                            bound.root.size, 24L, bound.provenance),
                    bound.operation,
                    bound.provenance);
        }
        int parallelPrefix = 0;
        int partitions = 1;
        if (bound.canonical.request.mode == ExecutionRequest.Mode.PARALLEL
                && access == CanonicalRowPhysicalPlan.AccessPath.TABLE_SCAN) {
            while (parallelPrefix < normalized.stages.size()
                    && normalized.stages.get(parallelPrefix).kind
                            == CanonicalRowStage.Kind.TYPED_FILTER) {
                parallelPrefix++;
            }
            int chunks = CheckedStructural.ceilChunks(
                    bound.root.size, bound.root.directory.chunkRows());
            partitions = Math.min(
                    Math.max(1, bound.table.parallelExecutor().getParallelism()),
                    Math.max(1, chunks));
            if (parallelPrefix == 0) partitions = 1;
        }
        return new CanonicalRowPhysicalPlan(
                normalized,
                access,
                indexOrdinal,
                literal,
                parallelPrefix,
                partitions,
                new ResourceEstimate(temporaryBytes));
    }

    private static LookupCandidate lookupCandidate(
            BoundCanonicalRowOperation bound,
            List<CanonicalRowStage> stages) {
        for (CanonicalRowStage stage : stages) {
            if (stage.kind != CanonicalRowStage.Kind.TYPED_FILTER) break;
            LookupCandidate candidate = lookupCandidate(bound, stage.predicate);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static LookupCandidate lookupCandidate(
            BoundCanonicalRowOperation bound,
            PredicateIr predicate) {
        if (predicate.kind == PredicateIr.Kind.AND) {
            LookupCandidate left = lookupCandidate(bound, predicate.left);
            return left == null ? lookupCandidate(bound, predicate.right) : left;
        }
        if (predicate.kind != PredicateIr.Kind.EQ
                && predicate.kind != PredicateIr.Kind.IS_NULL) return null;
        int field = predicate.fieldIndex;
        GeneratedTableLayout layout = bound.layout;
        int index = layout.indexOrdinalForField(field);
        boolean key = layout.keyFieldIndex() == field;
        if (!key && index < 0) return null;
        TypedLiteral literal = predicate.kind == PredicateIr.Kind.EQ
                ? predicate.lower
                : nullLiteral(bound.canonical.tableIdentity, layout, field);
        return new LookupCandidate(key, index, literal);
    }

    private static TypedLiteral nullLiteral(
            CanonicalTableIdentity identity,
            GeneratedTableLayout layout,
            int fieldIndex) {
        int start = layout.fieldStart(fieldIndex);
        if (layout.fieldLeafCount(fieldIndex) != 1
                || layout.leafKind(start) != GeneratedTableLayout.REFERENCE) {
            throw new AssertionError("IS_NULL lookup is not a nullable reference Field");
        }
        return TypedLiteral.nullReference(layout, identity, fieldIndex);
    }

    private static final class LookupCandidate {
        final boolean key;
        final int indexOrdinal;
        final TypedLiteral literal;

        LookupCandidate(boolean key, int indexOrdinal, TypedLiteral literal) {
            this.key = key;
            this.indexOrdinal = indexOrdinal;
            this.literal = literal;
        }
    }
}

final class NormalizedCanonicalRow {
    final BoundCanonicalRowOperation bound;
    final List<CanonicalRowStage> stages;
    final List<PredicateIr> filters;

    NormalizedCanonicalRow(
            BoundCanonicalRowOperation bound,
            List<CanonicalRowStage> stages) {
        this.bound = bound;
        this.stages = Collections.unmodifiableList(
                new ArrayList<CanonicalRowStage>(stages));
        ArrayList<PredicateIr> predicates = new ArrayList<PredicateIr>();
        for (CanonicalRowStage stage : stages) {
            if (stage.kind == CanonicalRowStage.Kind.TYPED_FILTER) {
                predicates.add(stage.predicate);
            }
        }
        this.filters = Collections.unmodifiableList(predicates);
    }
}

final class CanonicalRowPhysicalPlan {
    enum AccessPath { TABLE_SCAN, INDEX_SELECTION, KEY_LOOKUP, INDEX_LOOKUP }

    final NormalizedCanonicalRow normalized;
    final AccessPath accessPath;
    final int indexOrdinal;
    final TypedLiteral literal;
    final int parallelPrefixStages;
    final int partitions;
    final ResourceEstimate resources;

    CanonicalRowPhysicalPlan(
            NormalizedCanonicalRow normalized,
            AccessPath accessPath,
            int indexOrdinal,
            TypedLiteral literal,
            int parallelPrefixStages,
            int partitions,
            ResourceEstimate resources) {
        this.normalized = normalized;
        this.accessPath = accessPath;
        this.indexOrdinal = indexOrdinal;
        this.literal = literal;
        this.parallelPrefixStages = parallelPrefixStages;
        this.partitions = partitions;
        this.resources = resources;
    }
}

final class ResourceEstimate {
    final long temporaryBytes;

    ResourceEstimate(long temporaryBytes) {
        if (temporaryBytes < 0L) throw new AssertionError("negative resource estimate");
        this.temporaryBytes = temporaryBytes;
    }
}
