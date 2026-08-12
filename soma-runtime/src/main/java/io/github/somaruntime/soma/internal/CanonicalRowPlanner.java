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
        return plan(normalized, CanonicalRowPhysicalRequest.row(0L));
    }

    static CanonicalRowPhysicalPlan plan(
            NormalizedCanonicalRow normalized,
            CanonicalRowPhysicalRequest request) {
        BoundCanonicalRowOperation bound = normalized.bound;
        CanonicalRowPhysicalPlan.AccessPath access;
        int indexOrdinal = -1;
        TypedLiteral literal = null;
        if (bound.canonical.sourceKind
                == CanonicalRowOperation.SourceKind.INDEX_SELECTION) {
            access = CanonicalRowPhysicalPlan.AccessPath.INDEX_SELECTION;
            indexOrdinal = bound.canonical.indexOrdinal;
            literal = bound.canonical.sourceLiteral;
        } else if (bound.canonical.sourceKind
                == CanonicalRowOperation.SourceKind.RELATION_LEFT) {
            access = CanonicalRowPhysicalPlan.AccessPath.RELATION_LEFT;
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
        long parallelPrefixTemporaryBytes = 0L;
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
            parallelPrefixTemporaryBytes = RowExecutionSupport.arrayBytes(
                    bound.root.size, 24L, bound.provenance);
            temporaryBytes = CheckedLong.add(
                    temporaryBytes,
                    parallelPrefixTemporaryBytes,
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
        CanonicalPrimitiveVectorKernel.Decision vectorDecision =
                CanonicalPrimitiveVectorKernel.plan(
                        normalized, access, request.primitive);
        if (vectorDecision != null && vectorDecision.managesParallel) {
            int chunks = CheckedStructural.ceilChunks(
                    bound.root.size, bound.root.directory.chunkRows());
            partitions = Math.min(
                    Math.max(1, bound.table.parallelExecutor().getParallelism()),
                    Math.max(1, chunks));
            temporaryBytes = CheckedLong.subtract(
                    temporaryBytes,
                    parallelPrefixTemporaryBytes,
                    bound.operation,
                    bound.provenance);
            parallelPrefixTemporaryBytes = 0L;
        }
        if (vectorDecision != null) {
            temporaryBytes = CheckedLong.add(
                    temporaryBytes,
                    vectorDecision.temporaryBytes,
                    bound.operation,
                    bound.provenance);
        }
        if (vectorDecision == null || !vectorDecision.ownsTerminalScratch) {
            temporaryBytes = CheckedLong.add(
                    temporaryBytes,
                    request.additionalTemporaryBytes,
                    bound.operation,
                    bound.provenance);
        }
        CanonicalPhysicalPipeline pipeline = CanonicalPhysicalPipeline.plan(
                normalized,
                access,
                request,
                parallelPrefix,
                partitions,
                vectorDecision);
        return new CanonicalRowPhysicalPlan(
                normalized,
                access,
                indexOrdinal,
                literal,
                parallelPrefix,
                partitions,
                new ResourceEstimate(
                        temporaryBytes,
                        parallelPrefixTemporaryBytes),
                pipeline);
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

/** Closed terminal requirement consumed by the sole Physical planning owner. */
final class CanonicalRowPhysicalRequest {
    final CanonicalMappedOperation mapped;
    final CanonicalPrimitiveOperation primitive;
    final long additionalTemporaryBytes;

    private CanonicalRowPhysicalRequest(
            CanonicalMappedOperation mapped,
            CanonicalPrimitiveOperation primitive,
            long additionalTemporaryBytes) {
        if (additionalTemporaryBytes < 0L) {
            throw new AssertionError("negative terminal scratch");
        }
        if (mapped != null && primitive != null) {
            throw new AssertionError("multiple value terminal requirements");
        }
        this.mapped = mapped;
        this.primitive = primitive;
        this.additionalTemporaryBytes = additionalTemporaryBytes;
    }

    static CanonicalRowPhysicalRequest row(long additionalTemporaryBytes) {
        return new CanonicalRowPhysicalRequest(
                null, null, additionalTemporaryBytes);
    }

    static CanonicalRowPhysicalRequest mapped(
            CanonicalMappedOperation mapped,
            long additionalTemporaryBytes) {
        if (mapped == null) {
            throw new AssertionError("mapped terminal requirement is missing");
        }
        return new CanonicalRowPhysicalRequest(
                mapped, null, additionalTemporaryBytes);
    }

    static CanonicalRowPhysicalRequest primitive(
            CanonicalPrimitiveOperation primitive,
            long additionalTemporaryBytes) {
        if (primitive == null) {
            throw new AssertionError("primitive terminal requirement is missing");
        }
        return new CanonicalRowPhysicalRequest(
                null, primitive, additionalTemporaryBytes);
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
    enum AccessPath {
        TABLE_SCAN, INDEX_SELECTION, KEY_LOOKUP, INDEX_LOOKUP, RELATION_LEFT
    }

    final NormalizedCanonicalRow normalized;
    final AccessPath accessPath;
    final int indexOrdinal;
    final TypedLiteral literal;
    final int parallelPrefixStages;
    final int partitions;
    final ResourceEstimate resources;
    final CanonicalPhysicalPipeline pipeline;

    CanonicalRowPhysicalPlan(
            NormalizedCanonicalRow normalized,
            AccessPath accessPath,
            int indexOrdinal,
            TypedLiteral literal,
            int parallelPrefixStages,
            int partitions,
            ResourceEstimate resources,
            CanonicalPhysicalPipeline pipeline) {
        this.normalized = normalized;
        this.accessPath = accessPath;
        this.indexOrdinal = indexOrdinal;
        this.literal = literal;
        this.parallelPrefixStages = parallelPrefixStages;
        this.partitions = partitions;
        this.resources = resources;
        if (pipeline == null) {
            throw new AssertionError("physical pipeline is missing");
        }
        this.pipeline = pipeline;
    }

    boolean managesParallelPreparation() {
        CanonicalPrimitiveVectorKernel.Decision kernel =
                pipeline.terminalSegment().chunkKernel;
        return kernel != null && kernel.managesParallel;
    }
}

/** Data-only topology for one admitted Row-family terminal. */
final class CanonicalPhysicalPipeline {
    enum Sink {
        ROW_FAMILY,
        COUNT,
        INTEGRAL_SUM,
        LONG_MATERIALIZATION
    }

    final CanonicalRowPhysicalPlan.AccessPath source;
    final CanonicalPhysicalSegment[] segments;
    final Sink sink;

    private CanonicalPhysicalPipeline(
            CanonicalRowPhysicalPlan.AccessPath source,
            CanonicalPhysicalSegment[] segments,
            Sink sink) {
        if (segments == null || segments.length == 0) {
            throw new AssertionError("physical pipeline has no segment");
        }
        this.source = source;
        this.segments = segments.clone();
        this.sink = sink;
    }

    static CanonicalPhysicalPipeline plan(
            NormalizedCanonicalRow normalized,
            CanonicalRowPhysicalPlan.AccessPath source,
            CanonicalRowPhysicalRequest request,
            int parallelPrefixStages,
            int partitions,
            CanonicalPrimitiveVectorKernel.Decision chunkKernel) {
        int streamingEnd = 0;
        while (streamingEnd < normalized.stages.size()
                && !normalized.stages.get(streamingEnd).isStateful()) {
            streamingEnd++;
        }
        CanonicalPhysicalMorsel morsel = chunkKernel != null
                && chunkKernel.managesParallel
                ? CanonicalPhysicalMorsel.chunk(partitions)
                : parallelPrefixStages > 0 && partitions > 1
                        ? CanonicalPhysicalMorsel.rowRange(partitions)
                        : CanonicalPhysicalMorsel.caller();
        ArrayList<CanonicalPhysicalSegment> segments =
                new ArrayList<CanonicalPhysicalSegment>();
        CanonicalPrimitiveVectorKernel.Decision rowKernel =
                request.primitive == null ? chunkKernel : null;
        segments.add(new CanonicalPhysicalSegment(
                CanonicalPhysicalSegment.Shape.ROW_LOCATOR,
                0,
                streamingEnd,
                rowKernel == null
                        ? CanonicalPhysicalSegment.Kernel.TYPED_SCALAR
                        : CanonicalPhysicalSegment.Kernel.CHUNK_SPECIALIZED,
                hasRowCallback(normalized.stages, 0, streamingEnd),
                rowKernel == null ? CanonicalPhysicalMorsel.caller() : morsel,
                rowKernel));
        if (request.mapped != null) {
            addMappedSegment(segments, request.mapped);
        } else if (request.primitive != null) {
            if (request.primitive.mapped != null) {
                addMappedSegment(segments, request.primitive.mapped);
            }
            int end = firstPrimitiveStateful(request.primitive.stages);
            segments.add(new CanonicalPhysicalSegment(
                    CanonicalPhysicalSegment.Shape.PRIMITIVE,
                    0,
                    end,
                    chunkKernel == null
                            ? CanonicalPhysicalSegment.Kernel.PRIMITIVE_SCALAR
                            : CanonicalPhysicalSegment.Kernel.CHUNK_SPECIALIZED,
                    request.primitive.rootApplicationCallback
                            || hasPrimitiveCallback(
                                    request.primitive.stages, 0, end),
                    chunkKernel == null ? CanonicalPhysicalMorsel.caller() : morsel,
                    chunkKernel));
        }
        return new CanonicalPhysicalPipeline(
                source,
                segments.toArray(new CanonicalPhysicalSegment[segments.size()]),
                sink(normalized, request, chunkKernel));
    }

    CanonicalPhysicalSegment terminalSegment() {
        return segments[segments.length - 1];
    }

    private static void addMappedSegment(
            ArrayList<CanonicalPhysicalSegment> segments,
            CanonicalMappedOperation mapped) {
        int end = firstMappedStateful(mapped.stages);
        segments.add(new CanonicalPhysicalSegment(
                CanonicalPhysicalSegment.Shape.MAPPED_REFERENCE,
                0,
                end,
                CanonicalPhysicalSegment.Kernel.MAPPED_SCALAR,
                true,
                CanonicalPhysicalMorsel.caller(),
                null));
    }

    private static int firstMappedStateful(
            List<CanonicalMappedStage> stages) {
        for (int index = 0; index < stages.size(); index++) {
            if (stages.get(index).isStateful()) return index;
        }
        return stages.size();
    }

    private static int firstPrimitiveStateful(
            List<CanonicalPrimitiveStage> stages) {
        for (int index = 0; index < stages.size(); index++) {
            if (stages.get(index).isStateful()) return index;
        }
        return stages.size();
    }

    private static boolean hasRowCallback(
            List<CanonicalRowStage> stages,
            int from,
            int to) {
        for (int index = from; index < to; index++) {
            if (stages.get(index).callback != null) return true;
        }
        return false;
    }

    private static boolean hasPrimitiveCallback(
            List<CanonicalPrimitiveStage> stages,
            int from,
            int to) {
        for (int index = from; index < to; index++) {
            if (stages.get(index).callback != null) return true;
        }
        return false;
    }

    private static Sink sink(
            NormalizedCanonicalRow normalized,
            CanonicalRowPhysicalRequest request,
            CanonicalPrimitiveVectorKernel.Decision chunkKernel) {
        if (request.primitive == null) {
            return normalized.bound.canonical.terminal
                            == CanonicalRowOperation.TerminalKind.COUNT
                    ? Sink.COUNT : Sink.ROW_FAMILY;
        }
        switch (request.primitive.terminal) {
            case SUM:
                return Sink.INTEGRAL_SUM;
            case MATERIALIZE:
                return request.primitive.valueKind == PrimitiveValueKind.LONG
                        ? Sink.LONG_MATERIALIZATION : Sink.ROW_FAMILY;
            default:
                return Sink.ROW_FAMILY;
        }
    }
}

/** Largest currently admitted stateless typed region. */
final class CanonicalPhysicalSegment {
    enum Shape {
        ROW_LOCATOR,
        MAPPED_REFERENCE,
        PRIMITIVE
    }

    enum Kernel {
        TYPED_SCALAR,
        MAPPED_SCALAR,
        PRIMITIVE_SCALAR,
        CHUNK_SPECIALIZED
    }

    final Shape shape;
    final int fromStage;
    final int toStageExclusive;
    final Kernel kernel;
    final boolean callbackBarrier;
    final CanonicalPhysicalMorsel morsel;
    final CanonicalPrimitiveVectorKernel.Decision chunkKernel;

    CanonicalPhysicalSegment(
            Shape shape,
            int fromStage,
            int toStageExclusive,
            Kernel kernel,
            boolean callbackBarrier,
            CanonicalPhysicalMorsel morsel,
            CanonicalPrimitiveVectorKernel.Decision chunkKernel) {
        if (fromStage < 0 || toStageExclusive < fromStage
                || shape == null || kernel == null || morsel == null) {
            throw new AssertionError("invalid physical segment");
        }
        if ((kernel == Kernel.CHUNK_SPECIALIZED) != (chunkKernel != null)) {
            throw new AssertionError("physical kernel payload drift");
        }
        this.shape = shape;
        this.fromStage = fromStage;
        this.toStageExclusive = toStageExclusive;
        this.kernel = kernel;
        this.callbackBarrier = callbackBarrier;
        this.morsel = morsel;
        this.chunkKernel = chunkKernel;
    }
}

/** Bounded canonical-ordinal work decision consumed by the shared scheduler. */
final class CanonicalPhysicalMorsel {
    enum Kind {
        CALLER_ONLY,
        ROW_RANGE,
        CHUNK_RANGE
    }

    final Kind kind;
    final int partitions;

    private CanonicalPhysicalMorsel(Kind kind, int partitions) {
        if (kind == null || partitions < 1) {
            throw new AssertionError("invalid physical morsel");
        }
        this.kind = kind;
        this.partitions = partitions;
    }

    static CanonicalPhysicalMorsel caller() {
        return new CanonicalPhysicalMorsel(Kind.CALLER_ONLY, 1);
    }

    static CanonicalPhysicalMorsel rowRange(int partitions) {
        return new CanonicalPhysicalMorsel(Kind.ROW_RANGE, partitions);
    }

    static CanonicalPhysicalMorsel chunk(int partitions) {
        return new CanonicalPhysicalMorsel(Kind.CHUNK_RANGE, partitions);
    }
}

final class ResourceEstimate {
    final long temporaryBytes;
    final long parallelPrefixTemporaryBytes;

    ResourceEstimate(
            long temporaryBytes,
            long parallelPrefixTemporaryBytes) {
        if (temporaryBytes < 0L || parallelPrefixTemporaryBytes < 0L
                || parallelPrefixTemporaryBytes > temporaryBytes) {
            throw new AssertionError("invalid resource estimate");
        }
        this.temporaryBytes = temporaryBytes;
        this.parallelPrefixTemporaryBytes = parallelPrefixTemporaryBytes;
    }
}
