package io.github.somaruntime.soma.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fixed S1 normalization, exact lookup substitution and resource estimate. */
final class CanonicalRowPlanner {

    private CanonicalRowPlanner() {
    }

    static NormalizedCanonicalRow normalize(BoundCanonicalRowOperation bound) {
        PredicateIr combined = null;
        for (PredicateIr filter : bound.canonical.filters) {
            filter = normalizePredicate(filter, bound.layout);
            combined = combined == null
                    ? filter
                    : normalizeBinary(PredicateIr.Kind.AND, combined, filter);
        }
        List<PredicateIr> normalized = combined == null
                ? Collections.<PredicateIr>emptyList()
                : Collections.singletonList(combined);
        return new NormalizedCanonicalRow(bound, normalized);
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
                    bound, normalized.filters);
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
        return new CanonicalRowPhysicalPlan(
                normalized,
                access,
                indexOrdinal,
                literal,
                new ResourceEstimate(temporaryBytes));
    }

    private static LookupCandidate lookupCandidate(
            BoundCanonicalRowOperation bound,
            List<PredicateIr> filters) {
        for (PredicateIr filter : filters) {
            LookupCandidate candidate = lookupCandidate(bound, filter);
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
    final List<PredicateIr> filters;

    NormalizedCanonicalRow(
            BoundCanonicalRowOperation bound,
            List<PredicateIr> filters) {
        this.bound = bound;
        this.filters = Collections.unmodifiableList(
                new ArrayList<PredicateIr>(filters));
    }
}

final class CanonicalRowPhysicalPlan {
    enum AccessPath { TABLE_SCAN, INDEX_SELECTION, KEY_LOOKUP, INDEX_LOOKUP }

    final NormalizedCanonicalRow normalized;
    final AccessPath accessPath;
    final int indexOrdinal;
    final TypedLiteral literal;
    final ResourceEstimate resources;

    CanonicalRowPhysicalPlan(
            NormalizedCanonicalRow normalized,
            AccessPath accessPath,
            int indexOrdinal,
            TypedLiteral literal,
            ResourceEstimate resources) {
        this.normalized = normalized;
        this.accessPath = accessPath;
        this.indexOrdinal = indexOrdinal;
        this.literal = literal;
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
