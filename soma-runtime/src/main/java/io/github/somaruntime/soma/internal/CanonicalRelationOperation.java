package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable data-only semantic operation for one bounded binary relation. */
final class CanonicalRelationOperation {
    enum Kind { INNER, LEFT, FULL, SEMI, ANTI, CROSS }
    enum TerminalKind {
        COUNT, MATCH, FOR_EACH, MAP_SOURCE, PRIMITIVE_SOURCE,
        LEFT_SOURCE, EXPLAIN, TEST
    }

    final CanonicalTableIdentity leftIdentity;
    final CanonicalTableIdentity rightIdentity;
    final int[] leftFields;
    final int[] rightFields;
    final Kind kind;
    final long maxOutputRows;
    final List<CanonicalRelationFilter> filters;
    final ExecutionRequest request;
    final TerminalKind terminal;
    final RelationCallbackHandle terminalCallback;

    CanonicalRelationOperation(
            CanonicalTableIdentity leftIdentity,
            CanonicalTableIdentity rightIdentity,
            int[] leftFields,
            int[] rightFields,
            Kind kind,
            long maxOutputRows,
            List<CanonicalRelationFilter> filters,
            ExecutionRequest request,
            TerminalKind terminal,
            RelationCallbackHandle terminalCallback) {
        if (leftIdentity == null || rightIdentity == null
                || leftIdentity.sameTable(rightIdentity)
                || leftFields == null || rightFields == null
                || leftFields.length != rightFields.length
                || kind == null || filters == null || request == null
                || terminal == null || maxOutputRows < 0L
                || kind == Kind.CROSS && leftFields.length != 0
                || kind != Kind.CROSS && leftFields.length == 0) {
            throw new AssertionError("invalid Canonical relation operation");
        }
        this.leftIdentity = leftIdentity;
        this.rightIdentity = rightIdentity;
        this.leftFields = leftFields.clone();
        this.rightFields = rightFields.clone();
        this.kind = kind;
        this.maxOutputRows = maxOutputRows;
        this.filters = Collections.unmodifiableList(
                new ArrayList<CanonicalRelationFilter>(filters));
        this.request = request;
        this.terminal = terminal;
        this.terminalCallback = terminalCallback;
    }

    int pushableFilterCount() {
        if (kind != Kind.INNER) return 0;
        int count = 0;
        while (count < filters.size() && filters.get(count).predicate != null) {
            count++;
        }
        return count;
    }

    boolean hasCallbackFilter() {
        for (CanonicalRelationFilter filter : filters) {
            if (filter.callback != null) return true;
        }
        return false;
    }
}

final class CanonicalRelationFilter {
    enum Owner { LEFT, RIGHT, CALLBACK }

    final Owner owner;
    final PredicateIr predicate;
    final RelationCallbackHandle callback;

    CanonicalRelationFilter(
            Owner owner,
            PredicateIr predicate,
            RelationCallbackHandle callback) {
        if (owner == null
                || owner == Owner.CALLBACK && callback == null
                || owner != Owner.CALLBACK && predicate == null) {
            throw new AssertionError("invalid Canonical relation filter");
        }
        this.owner = owner;
        this.predicate = predicate;
        this.callback = callback;
    }
}

/** Opaque callback requiring both relation endpoints. */
final class RelationCallbackHandle {
    enum Kind { PREDICATE, ACTION, MAPPER, PRIMITIVE_MAPPER }

    final CanonicalTableIdentity leftIdentity;
    final CanonicalTableIdentity rightIdentity;
    final Kind kind;
    final Object callback;

    RelationCallbackHandle(
            CanonicalTableIdentity leftIdentity,
            CanonicalTableIdentity rightIdentity,
            Kind kind,
            Object callback) {
        if (leftIdentity == null || rightIdentity == null
                || kind == null || callback == null) {
            throw new AssertionError("invalid relation callback handle");
        }
        this.leftIdentity = leftIdentity;
        this.rightIdentity = rightIdentity;
        this.kind = kind;
        this.callback = callback;
    }
}

final class BoundCanonicalRelationOperation {
    final CanonicalRelationOperation canonical;
    final GeneratedTable leftTable;
    final GeneratedTable rightTable;
    final TableStateRoot leftRoot;
    final TableStateRoot rightRoot;
    final GeneratedTableLayout leftLayout;
    final GeneratedTableLayout rightLayout;
    final Object provenance;

    BoundCanonicalRelationOperation(
            CanonicalRelationOperation canonical,
            GeneratedTable leftTable,
            GeneratedTable rightTable,
            TableStateRoot leftRoot,
            TableStateRoot rightRoot,
            Object provenance) {
        this.canonical = canonical;
        this.leftTable = leftTable;
        this.rightTable = rightTable;
        this.leftRoot = leftRoot;
        this.rightRoot = rightRoot;
        this.leftLayout = leftTable.layout();
        this.rightLayout = rightTable.layout();
        this.provenance = provenance;
    }

    long outputUpperBound() {
        return CanonicalRelationPlanner.outputUpperBound(this);
    }
}

/** Proven relation rewrites only; no cursor, Index container or scratch state. */
final class NormalizedRelationOperation {
    final BoundCanonicalRelationOperation bound;
    final int pushedFilterCount;
    final List<CanonicalRelationFilter> residualFilters;

    NormalizedRelationOperation(
            BoundCanonicalRelationOperation bound,
            int pushedFilterCount) {
        this.bound = bound;
        this.pushedFilterCount = pushedFilterCount;
        this.residualFilters = Collections.unmodifiableList(
                new ArrayList<CanonicalRelationFilter>(
                        bound.canonical.filters.subList(
                                pushedFilterCount,
                                bound.canonical.filters.size())));
    }
}

final class PhysicalRelationPlan {
    final NormalizedRelationOperation normalized;
    final CanonicalBinaryPhysicalPipeline pipeline;
    final int pushedFilterCount;
    final long temporaryBytes;

    PhysicalRelationPlan(
            NormalizedRelationOperation normalized,
            CanonicalBinaryPhysicalPipeline pipeline,
            long temporaryBytes) {
        this.normalized = normalized;
        if (pipeline == null) {
            throw new AssertionError("binary physical pipeline is missing");
        }
        this.pipeline = pipeline;
        this.pushedFilterCount = normalized.pushedFilterCount;
        this.temporaryBytes = temporaryBytes;
    }

    BoundCanonicalRelationOperation bound() {
        return normalized.bound;
    }
}

/** Closed physical topology for one bounded binary Relation. */
final class CanonicalBinaryPhysicalPipeline {
    enum Source { LEFT_SCAN, RIGHT_SCAN, RIGHT_INDEX }
    enum Kernel { NESTED_CROSS, RIGHT_INDEX_LOOKUP, RIGHT_HASH_BUILD_PROBE }
    enum OutputShape { RELATION_PAIR, LEFT_LOCATOR, MAPPED_REFERENCE, PRIMITIVE }

    final Source leftSource;
    final Source rightSource;
    final Kernel kernel;
    final OutputShape outputShape;
    final CanonicalPhysicalMorsel leftMorsel;
    final CanonicalPhysicalMorsel rightMorsel;
    final int residualFilterCount;
    final boolean callbackBarrier;
    final long outputUpperBound;
    final CanonicalRelationPhysicalDownstream downstream;

    CanonicalBinaryPhysicalPipeline(
            Source leftSource,
            Source rightSource,
            Kernel kernel,
            OutputShape outputShape,
            int residualFilterCount,
            boolean callbackBarrier,
            long outputUpperBound,
            CanonicalRelationPhysicalDownstream downstream) {
        if (leftSource == null || rightSource == null || kernel == null
                || outputShape == null || residualFilterCount < 0
                || outputUpperBound < 0L || downstream == null
                || outputShape != downstream.outputShape) {
            throw new AssertionError("invalid binary physical pipeline");
        }
        this.leftSource = leftSource;
        this.rightSource = rightSource;
        this.kernel = kernel;
        this.outputShape = outputShape;
        this.leftMorsel = CanonicalPhysicalMorsel.caller();
        this.rightMorsel = CanonicalPhysicalMorsel.caller();
        this.residualFilterCount = residualFilterCount;
        this.callbackBarrier = callbackBarrier;
        this.outputUpperBound = outputUpperBound;
        this.downstream = downstream;
    }
}

/** Finite post-Relation linear topology; no generic tuple or operator DAG. */
final class CanonicalRelationPhysicalDownstream {
    enum Kernel {
        PAIR_EMIT,
        MAPPED_FILTER,
        MAPPED_MAP,
        MAPPED_HASH,
        MAPPED_STABLE_SORT,
        MAPPED_SLICE,
        PRIMITIVE_FILTER,
        PRIMITIVE_MAP,
        PRIMITIVE_HASH,
        PRIMITIVE_STABLE_SORT,
        PRIMITIVE_SLICE
    }

    final CanonicalBinaryPhysicalPipeline.OutputShape outputShape;
    final Kernel[] kernels;
    final int breakerCount;
    final boolean callbackBarrier;

    private CanonicalRelationPhysicalDownstream(
            CanonicalBinaryPhysicalPipeline.OutputShape outputShape,
            Kernel[] kernels,
            int breakerCount,
            boolean callbackBarrier) {
        if (outputShape == null || kernels == null || kernels.length == 0
                || breakerCount < 0 || breakerCount > kernels.length) {
            throw new AssertionError("invalid Relation downstream topology");
        }
        this.outputShape = outputShape;
        this.kernels = kernels.clone();
        this.breakerCount = breakerCount;
        this.callbackBarrier = callbackBarrier;
    }

    static CanonicalRelationPhysicalDownstream pair(
            CanonicalRelationOperation.Kind kind) {
        return new CanonicalRelationPhysicalDownstream(
                kind == CanonicalRelationOperation.Kind.SEMI
                                || kind == CanonicalRelationOperation.Kind.ANTI
                        ? CanonicalBinaryPhysicalPipeline.OutputShape.LEFT_LOCATOR
                        : CanonicalBinaryPhysicalPipeline.OutputShape.RELATION_PAIR,
                new Kernel[] {Kernel.PAIR_EMIT}, 0, false);
    }

    static CanonicalRelationPhysicalDownstream mapped(
            Kernel[] kernels,
            int breakerCount) {
        return new CanonicalRelationPhysicalDownstream(
                CanonicalBinaryPhysicalPipeline.OutputShape.MAPPED_REFERENCE,
                kernels, breakerCount, true);
    }

    static CanonicalRelationPhysicalDownstream primitive(
            Kernel[] kernels,
            int breakerCount) {
        return new CanonicalRelationPhysicalDownstream(
                CanonicalBinaryPhysicalPipeline.OutputShape.PRIMITIVE,
                kernels, breakerCount, true);
    }
}

final class CanonicalRelationExecutionFrame {
    final PhysicalRelationPlan plan;
    final CanonicalRelationRightHash rightHash;
    final boolean[] matchedRight;

    CanonicalRelationExecutionFrame(PhysicalRelationPlan plan) {
        this.plan = plan;
        BoundCanonicalRelationOperation bound = plan.bound();
        this.rightHash = plan.pipeline.kernel
                        == CanonicalBinaryPhysicalPipeline.Kernel.RIGHT_HASH_BUILD_PROBE
                ? new CanonicalRelationRightHash(
                        bound.rightRoot.size, bound.provenance)
                : null;
        this.matchedRight = bound.canonical.kind
                == CanonicalRelationOperation.Kind.FULL
                ? new boolean[RowExecutionSupport.arrayLength(
                        bound.rightRoot.size, bound.provenance)]
                : null;
    }

    BoundCanonicalRelationOperation bound() {
        return plan.bound();
    }

    /**
     * Creates one admitted operator-local cursor. Keeping the cursor local to
     * the hot Join loop lets HotSpot scalar-replace it; the Frame remains the
     * lifecycle and admission owner without retaining physical cursor state
     * beyond this execution.
     */
    IdentityHashIndex.Cursor openRightCursor() {
        if (plan.pipeline.kernel
                != CanonicalBinaryPhysicalPipeline.Kernel.RIGHT_INDEX_LOOKUP) {
            throw new AssertionError("right Index cursor is not admitted");
        }
        return new IdentityHashIndex.Cursor();
    }
}

/** Lease-owned right build state for the production hash Join operator. */
final class CanonicalRelationRightHash {
    private final int[] heads;
    private final int[] tails;
    private final int[] next;
    private final int[] locators;
    private int size;

    CanonicalRelationRightHash(long rows, Object provenance) {
        int length = RowExecutionSupport.arrayLength(rows, provenance);
        int buckets = 1;
        while (buckets < length && buckets < (1 << 30)) buckets <<= 1;
        if (buckets < length) {
            throw SomaFailures.failure(
                    SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                    SomaOperation.QUERY,
                    "Join hash table exceeds Java array boundary",
                    provenance);
        }
        heads = new int[buckets];
        tails = new int[buckets];
        next = new int[length];
        locators = new int[length];
    }

    void add(long hash, int locator) {
        int bucket = ((int) mix(hash)) & (heads.length - 1);
        int entry = size++;
        locators[entry] = locator;
        if (heads[bucket] == 0) heads[bucket] = entry + 1;
        else next[tails[bucket] - 1] = entry + 1;
        tails[bucket] = entry + 1;
    }

    int head(long hash) {
        return heads[((int) mix(hash)) & (heads.length - 1)];
    }

    int next(int link) { return next[link - 1]; }
    int locator(int link) { return locators[link - 1]; }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }
}

final class CanonicalRelationPlanner {
    private CanonicalRelationPlanner() {
    }

    static PhysicalRelationPlan plan(
            BoundCanonicalRelationOperation bound,
            long outputBytesPerElement) {
        return plan(
                bound,
                outputBytesPerElement,
                CanonicalRelationPhysicalDownstream.pair(bound.canonical.kind));
    }

    static PhysicalRelationPlan plan(
            BoundCanonicalRelationOperation bound,
            long outputBytesPerElement,
            CanonicalRelationPhysicalDownstream downstream) {
        NormalizedRelationOperation normalized = normalize(bound);
        CanonicalBinaryPhysicalPipeline.Kernel kernel;
        CanonicalBinaryPhysicalPipeline.Source rightSource;
        if (bound.canonical.kind == CanonicalRelationOperation.Kind.CROSS) {
            kernel = CanonicalBinaryPhysicalPipeline.Kernel.NESTED_CROSS;
            rightSource = CanonicalBinaryPhysicalPipeline.Source.RIGHT_SCAN;
        } else {
            boolean index = rightLookup(bound) != null;
            kernel = index
                    ? CanonicalBinaryPhysicalPipeline.Kernel.RIGHT_INDEX_LOOKUP
                    : CanonicalBinaryPhysicalPipeline.Kernel.RIGHT_HASH_BUILD_PROBE;
            rightSource = index
                    ? CanonicalBinaryPhysicalPipeline.Source.RIGHT_INDEX
                    : CanonicalBinaryPhysicalPipeline.Source.RIGHT_SCAN;
        }
        long scratch = RowExecutionSupport.arrayBytes(
                bound.rightRoot.size,
                bound.canonical.kind == CanonicalRelationOperation.Kind.FULL
                        ? 32L : 24L,
                bound.provenance);
        if (outputBytesPerElement != 0L) {
            scratch = CheckedLong.add(
                    scratch,
                    RowExecutionSupport.arrayBytes(
                            outputUpperBound(bound),
                            outputBytesPerElement,
                            bound.provenance),
                    SomaOperation.QUERY,
                    bound.provenance);
        }
        if (downstream.breakerCount != 0) {
            scratch = CheckedLong.add(
                    scratch,
                    RowExecutionSupport.arrayBytes(
                            outputUpperBound(bound),
                            24L,
                            bound.provenance),
                    SomaOperation.QUERY,
                    bound.provenance);
        }
        return new PhysicalRelationPlan(
                normalized,
                new CanonicalBinaryPhysicalPipeline(
                        CanonicalBinaryPhysicalPipeline.Source.LEFT_SCAN,
                        rightSource,
                        kernel,
                        downstream.outputShape,
                        normalized.residualFilters.size(),
                        bound.canonical.hasCallbackFilter()
                                || downstream.callbackBarrier,
                        outputUpperBound(bound),
                        downstream),
                scratch);
    }

    private static NormalizedRelationOperation normalize(
            BoundCanonicalRelationOperation bound) {
        return new NormalizedRelationOperation(
                bound, bound.canonical.pushableFilterCount());
    }

    static long outputUpperBound(BoundCanonicalRelationOperation bound) {
        return outputUpperBound(
                bound.leftLayout,
                bound.rightLayout,
                bound.canonical,
                bound.leftRoot.size,
                bound.rightRoot.size,
                bound.provenance);
    }

    static long outputUpperBoundForTesting(
            GeneratedTableLayout leftLayout,
            GeneratedTableLayout rightLayout,
            CanonicalRelationOperation canonical,
            long leftRows,
            long rightRows) {
        return outputUpperBound(
                leftLayout,
                rightLayout,
                canonical,
                leftRows,
                rightRows,
                new Object());
    }

    private static long outputUpperBound(
            GeneratedTableLayout leftLayout,
            GeneratedTableLayout rightLayout,
            CanonicalRelationOperation canonical,
            long leftRows,
            long rightRows,
            Object provenance) {
        CanonicalRelationOperation.Kind kind = canonical.kind;
        if (kind == CanonicalRelationOperation.Kind.SEMI
                || kind == CanonicalRelationOperation.Kind.ANTI) return leftRows;
        if (kind != CanonicalRelationOperation.Kind.CROSS) {
            boolean leftUnique = joinsKey(
                    leftLayout, canonical.leftFields);
            boolean rightUnique = joinsKey(
                    rightLayout, canonical.rightFields);
            if (kind == CanonicalRelationOperation.Kind.INNER) {
                if (leftUnique && rightUnique) {
                    return Math.min(leftRows, rightRows);
                }
                if (rightUnique) return leftRows;
                if (leftUnique) return rightRows;
            } else if (rightUnique
                    && kind == CanonicalRelationOperation.Kind.LEFT) {
                return leftRows;
            } else if (leftUnique || rightUnique) {
                return CheckedLong.add(
                        leftRows, rightRows,
                        SomaOperation.QUERY, provenance);
            }
        }
        long product = CheckedLong.multiply(
                leftRows, rightRows,
                SomaOperation.QUERY, provenance);
        if (kind == CanonicalRelationOperation.Kind.INNER
                || kind == CanonicalRelationOperation.Kind.CROSS) return product;
        long result = CheckedLong.add(
                product, leftRows,
                SomaOperation.QUERY, provenance);
        return kind == CanonicalRelationOperation.Kind.FULL
                ? CheckedLong.add(
                        result, rightRows,
                        SomaOperation.QUERY, provenance)
                : result;
    }

    static IdentityHashIndex rightLookup(
            BoundCanonicalRelationOperation bound) {
        if (bound.canonical.leftFields.length != 1) return null;
        int field = bound.canonical.rightFields[0];
        if (bound.rightLayout.keyFieldIndex() == field) {
            return bound.rightRoot.key;
        }
        int ordinal = bound.rightLayout.indexOrdinalForField(field);
        return ordinal < 0 ? null : bound.rightRoot.indexes[ordinal];
    }

    private static boolean joinsKey(
            GeneratedTableLayout layout,
            int[] fields) {
        int key = layout.keyFieldIndex();
        if (key < 0) return false;
        for (int field : fields) if (field == key) return true;
        return false;
    }
}

/** Single two-root admission coordinator for relation specialized kernels. */
final class CanonicalRelationQueryOperation {
    interface FrameWork<T> {
        T run(CanonicalRelationExecutionFrame frame);
    }

    private CanonicalRelationQueryOperation() {
    }

    static <T> T execute(
            GeneratedTable left,
            GeneratedTable right,
            CanonicalRelationOperation operation,
            long outputBytesPerElement,
            FrameWork<T> work) {
        return execute(
                left, right, operation, outputBytesPerElement,
                CanonicalRelationPhysicalDownstream.pair(operation.kind), work);
    }

    static <T> T execute(
            GeneratedTable left,
            GeneratedTable right,
            CanonicalRelationOperation operation,
            long outputBytesPerElement,
            CanonicalRelationPhysicalDownstream downstream,
            FrameWork<T> work) {
        if (!left.sharesGroup(right)) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY,
                    "Join Tables belong to different SomaGroup instances");
        }
        try (GroupOperationGuard.Lease lease = left.acquireQuery()) {
            Object provenance = lease.provenance();
            requireParallelAvailable(left, operation, provenance);
            BoundCanonicalRelationOperation bound =
                    new BoundCanonicalRelationOperation(
                            operation,
                            left,
                            right,
                            left.currentRoot(),
                            right.currentRoot(),
                            provenance);
            PhysicalRelationPlan physical = CanonicalRelationPlanner.plan(
                    bound, outputBytesPerElement, downstream);
            try (GlobalMemoryManager.TemporaryLease ignored =
                         left.leaseQueryTemporary(
                                 physical.temporaryBytes, provenance)) {
                left.queryCursor().begin(
                        bound.leftRoot, SomaOperation.QUERY, provenance);
                try {
                    right.queryCursor().begin(
                            bound.rightRoot, SomaOperation.QUERY, provenance);
                    try {
                        return work.run(
                                new CanonicalRelationExecutionFrame(physical));
                    } finally {
                        right.queryCursor().end();
                    }
                } finally {
                    left.queryCursor().end();
                }
            }
        }
    }

    static <T> T executeLeft(
            GeneratedRelation runtime,
            GeneratedTable left,
            GeneratedTable right,
            CanonicalRowOperation rowOperation,
            CanonicalQueryOperation.ExtraScratch extra,
            CanonicalMappedOperation mapped,
            CanonicalPrimitiveOperation primitive,
            CanonicalGroupOperation group,
            CanonicalQueryOperation.FrameWork<T> work) {
        return executeLeftInternal(
                runtime, left, right, rowOperation, extra,
                mapped, primitive, group, null, work, null);
    }

    static <T> T executeLeftReference(
            GeneratedRelation runtime,
            GeneratedTable left,
            GeneratedTable right,
            CanonicalRowOperation rowOperation,
            CanonicalQueryOperation.ReferenceExtraScratch extra,
            CanonicalQueryOperation.ReferenceSourceWork<T> work) {
        return executeLeftInternal(
                runtime, left, right, rowOperation, null,
                null, null, null, extra, null, work);
    }

    private static <T> T executeLeftInternal(
            final GeneratedRelation runtime,
            GeneratedTable left,
            GeneratedTable right,
            CanonicalRowOperation rowOperation,
            CanonicalQueryOperation.ExtraScratch extra,
            CanonicalMappedOperation mapped,
            CanonicalPrimitiveOperation primitive,
            CanonicalGroupOperation group,
            CanonicalQueryOperation.ReferenceExtraScratch referenceExtra,
            CanonicalQueryOperation.FrameWork<T> optimizedWork,
            CanonicalQueryOperation.ReferenceSourceWork<T> referenceWork) {
        if (rowOperation.sourceKind
                != CanonicalRowOperation.SourceKind.RELATION_LEFT
                || rowOperation.relationSource == null
                || !left.sharesGroup(right)) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY, "invalid relation-derived left source");
        }
        try (GroupOperationGuard.Lease lease = left.acquireQuery()) {
            Object provenance = lease.provenance();
            requireParallelAvailable(left, rowOperation.relationSource, provenance);
            TableStateRoot leftRoot = left.currentRoot();
            TableStateRoot rightRoot = right.currentRoot();
            BoundCanonicalRelationOperation relationBound =
                    new BoundCanonicalRelationOperation(
                            rowOperation.relationSource,
                            left,
                            right,
                            leftRoot,
                            rightRoot,
                            provenance);
            BoundCanonicalRowOperation rowBound =
                    new BoundCanonicalRowOperation(
                            rowOperation,
                            left,
                            left.layout(),
                            leftRoot,
                            SomaOperation.QUERY,
                            provenance);
            PhysicalRelationPlan relationPlan = CanonicalRelationPlanner.plan(
                    relationBound, 0L);
            NormalizedCanonicalRow normalized =
                    CanonicalRowPlanner.normalize(rowBound);
            long additionalTemporaryBytes = extra == null
                    ? 0L : extra.bytes(rowBound);
            CanonicalRowPhysicalRequest request = group != null
                    ? CanonicalRowPhysicalRequest.group(
                            group, additionalTemporaryBytes)
                    : mapped != null
                            ? CanonicalRowPhysicalRequest.mapped(
                                    mapped, additionalTemporaryBytes)
                            : primitive != null
                                    ? CanonicalRowPhysicalRequest.primitive(
                                            primitive, additionalTemporaryBytes)
                                    : CanonicalRowPhysicalRequest.row(
                                            additionalTemporaryBytes);
            CanonicalRowPhysicalPlan rowPlan =
                    CanonicalRowPlanner.plan(normalized, request);
            long temporaryBytes = CheckedLong.add(
                    relationPlan.temporaryBytes,
                    RowExecutionSupport.arrayBytes(
                            leftRoot.size, 24L, provenance),
                    SomaOperation.QUERY,
                    provenance);
            temporaryBytes = CheckedLong.add(
                    temporaryBytes,
                    referenceWork == null
                            ? rowPlan.resources.temporaryBytes
                            : CanonicalQueryOperation.referenceTemporaryBytes(
                                    rowBound),
                    SomaOperation.QUERY,
                    provenance);
            if (referenceExtra != null) {
                temporaryBytes = CheckedLong.add(
                        temporaryBytes,
                        referenceExtra.bytes(rowBound),
                        SomaOperation.QUERY,
                        provenance);
            }
            try (GlobalMemoryManager.TemporaryLease ignored =
                         left.leaseQueryTemporary(temporaryBytes, provenance)) {
                left.queryCursor().begin(
                        leftRoot, SomaOperation.QUERY, provenance);
                try {
                    left.secondaryQueryCursor().begin(
                            leftRoot, SomaOperation.QUERY, provenance);
                    try {
                        right.queryCursor().begin(
                                rightRoot, SomaOperation.QUERY, provenance);
                        try {
                            final IntLocatorBuffer source = new IntLocatorBuffer(
                                    leftRoot.size,
                                    SomaOperation.QUERY,
                                    provenance);
                            CanonicalRelationExecutionFrame relationFrame =
                                    new CanonicalRelationExecutionFrame(
                                            relationPlan);
                            GeneratedRelation.RelationBinding binding =
                                    new GeneratedRelation.RelationBinding(
                                            relationFrame);
                            GeneratedRelation.PairVisitor collector =
                                    new GeneratedRelation.PairVisitor() {
                                @Override public boolean visit(
                                        int leftLocator,
                                        int rightLocator) {
                                    source.add(leftLocator);
                                    return true;
                                }
                            };
                            if (referenceWork == null) {
                                runtime.visitBound(binding, false, collector);
                                CanonicalRowExecutionFrame rowFrame =
                                        new CanonicalRowExecutionFrame(rowPlan);
                                rowFrame.sourceOverride = source;
                                CanonicalParallelRowScheduler.prepare(rowFrame);
                                return optimizedWork.run(rowFrame);
                            }
                            runtime.visitBoundReference(
                                    binding, false, collector);
                            return referenceWork.run(rowBound, source);
                        } finally {
                            right.queryCursor().end();
                        }
                    } finally {
                        left.secondaryQueryCursor().end();
                    }
                } finally {
                    left.queryCursor().end();
                }
            }
        }
    }

    private static void requireParallelAvailable(
            GeneratedTable left,
            CanonicalRelationOperation operation,
            Object provenance) {
        if (operation.request.mode != ExecutionRequest.Mode.PARALLEL) return;
        if (CallbackExecutionScope.isActive()) {
            throw SomaFailures.failure(
                    SomaFailureCode.NESTED_PARALLEL_OPERATION,
                    SomaOperation.QUERY,
                    "parallel terminal started inside a SOMA callback",
                    provenance);
        }
        if (left.parallelExecutor().isShutdown()
                || left.parallelExecutor().isTerminated()) {
            throw SomaFailures.failure(
                    SomaFailureCode.PARALLEL_EXECUTOR_UNAVAILABLE,
                    SomaOperation.QUERY,
                    "parallel ForkJoinPool is unavailable",
                    provenance);
        }
    }
}
