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
    enum Algorithm { NESTED_CROSS, RIGHT_INDEX_LOOKUP, RIGHT_HASH }
    enum Access { SCAN, INDEX }
    enum BuildSide { NONE, RIGHT }

    final NormalizedRelationOperation normalized;
    final Algorithm algorithm;
    final Access access;
    final BuildSide buildSide;
    final int pushedFilterCount;
    final long temporaryBytes;

    PhysicalRelationPlan(
            NormalizedRelationOperation normalized,
            Algorithm algorithm,
            Access access,
            BuildSide buildSide,
            long temporaryBytes) {
        this.normalized = normalized;
        this.algorithm = algorithm;
        this.access = access;
        this.buildSide = buildSide;
        this.pushedFilterCount = normalized.pushedFilterCount;
        this.temporaryBytes = temporaryBytes;
    }

    BoundCanonicalRelationOperation bound() {
        return normalized.bound;
    }
}

final class CanonicalRelationExecutionFrame {
    final PhysicalRelationPlan plan;
    final CanonicalRelationRightHash rightHash;
    final boolean[] matchedRight;
    final IdentityHashIndex.Cursor rightCursor;

    CanonicalRelationExecutionFrame(PhysicalRelationPlan plan) {
        this.plan = plan;
        BoundCanonicalRelationOperation bound = plan.bound();
        this.rightHash = plan.algorithm == PhysicalRelationPlan.Algorithm.RIGHT_HASH
                ? new CanonicalRelationRightHash(
                        bound.rightRoot.size, bound.provenance)
                : null;
        this.matchedRight = bound.canonical.kind
                == CanonicalRelationOperation.Kind.FULL
                ? new boolean[RowExecutionSupport.arrayLength(
                        bound.rightRoot.size, bound.provenance)]
                : null;
        this.rightCursor = plan.algorithm
                == PhysicalRelationPlan.Algorithm.RIGHT_INDEX_LOOKUP
                ? new IdentityHashIndex.Cursor()
                : null;
    }

    BoundCanonicalRelationOperation bound() {
        return plan.bound();
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
        NormalizedRelationOperation normalized = normalize(bound);
        PhysicalRelationPlan.Algorithm algorithm;
        PhysicalRelationPlan.Access access;
        PhysicalRelationPlan.BuildSide buildSide;
        if (bound.canonical.kind == CanonicalRelationOperation.Kind.CROSS) {
            algorithm = PhysicalRelationPlan.Algorithm.NESTED_CROSS;
            access = PhysicalRelationPlan.Access.SCAN;
            buildSide = PhysicalRelationPlan.BuildSide.NONE;
        } else {
            boolean index = rightLookup(bound) != null;
            algorithm = index
                    ? PhysicalRelationPlan.Algorithm.RIGHT_INDEX_LOOKUP
                    : PhysicalRelationPlan.Algorithm.RIGHT_HASH;
            access = index
                    ? PhysicalRelationPlan.Access.INDEX
                    : PhysicalRelationPlan.Access.SCAN;
            buildSide = index
                    ? PhysicalRelationPlan.BuildSide.NONE
                    : PhysicalRelationPlan.BuildSide.RIGHT;
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
        return new PhysicalRelationPlan(
                normalized,
                algorithm,
                access,
                buildSide,
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
                    bound, outputBytesPerElement);
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
            CanonicalQueryOperation.FrameWork<T> work) {
        return executeLeftInternal(
                runtime, left, right, rowOperation, extra, null, work, null);
    }

    static <T> T executeLeftReference(
            GeneratedRelation runtime,
            GeneratedTable left,
            GeneratedTable right,
            CanonicalRowOperation rowOperation,
            CanonicalQueryOperation.ReferenceExtraScratch extra,
            CanonicalQueryOperation.ReferenceSourceWork<T> work) {
        return executeLeftInternal(
                runtime, left, right, rowOperation, null, extra, null, work);
    }

    private static <T> T executeLeftInternal(
            final GeneratedRelation runtime,
            GeneratedTable left,
            GeneratedTable right,
            CanonicalRowOperation rowOperation,
            CanonicalQueryOperation.ExtraScratch extra,
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
            CanonicalRowPhysicalPlan rowPlan =
                    CanonicalRowPlanner.plan(normalized);
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
            if (extra != null) {
                temporaryBytes = CheckedLong.add(
                        temporaryBytes,
                        extra.bytes(rowBound),
                        SomaOperation.QUERY,
                        provenance);
            }
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
