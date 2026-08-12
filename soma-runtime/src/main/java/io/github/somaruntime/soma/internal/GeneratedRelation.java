package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaRelationExpression;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Immutable binary-relation plan and one-shot sequential execution carrier. */
public final class GeneratedRelation {

    public static final int INNER = 1;
    public static final int LEFT = 2;
    public static final int FULL = 3;
    public static final int SEMI = 4;
    public static final int ANTI = 5;
    public static final int CROSS = 6;

    private final GeneratedTable left;
    private final GeneratedTable right;
    private final int[] leftFields;
    private final int[] rightFields;
    private final int kind;
    private final long maxOutputRows;
    private final List<FilterStage> filters;
    private final GeneratedPairCursor pairCursor;
    private final boolean parallel;
    private final AtomicBoolean consumed = new AtomicBoolean();

    private GeneratedRelation(
            GeneratedTable left,
            GeneratedTable right,
            int[] leftFields,
            int[] rightFields,
            int kind,
            long maxOutputRows,
            List<FilterStage> filters,
            GeneratedPairCursor pairCursor,
            boolean parallel) {
        this.left = left;
        this.right = right;
        this.leftFields = leftFields;
        this.rightFields = rightFields;
        this.kind = kind;
        this.maxOutputRows = maxOutputRows;
        this.filters = filters;
        this.pairCursor = pairCursor;
        this.parallel = parallel;
    }

    public static GeneratedRelation equality(
            GeneratedTable left,
            GeneratedTable right) {
        requireTables(left, right);
        return new GeneratedRelation(
                left, right, new int[0], new int[0], INNER, Long.MAX_VALUE,
                Collections.<FilterStage>emptyList(), new GeneratedPairCursor(), false);
    }

    public static GeneratedRelation cross(
            GeneratedTable left,
            GeneratedTable right,
            long maxOutputRows) {
        requireTables(left, right);
        if (maxOutputRows < 0L) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY, "Cross Join maxOutputRows is negative");
        }
        return new GeneratedRelation(
                left, right, new int[0], new int[0], CROSS, maxOutputRows,
                Collections.<FilterStage>emptyList(), new GeneratedPairCursor(), false);
    }

    public GeneratedPairAccess pairAccess() {
        return pairCursor;
    }

    public GeneratedRelation parallel() {
        claim();
        return parallelCopy();
    }

    GeneratedRelation parallelCopy() {
        return copy(leftFields, rightFields, kind, filters, true);
    }

    boolean isParallel() {
        return parallel;
    }

    public GeneratedPipeline leftPipeline() {
        if (kind != SEMI && kind != ANTI) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY, "only Semi/Anti Join has a left ReadStream");
        }
        claim();
        return new GeneratedPipeline(
                left, LogicalRowPlan.relationLeft(left, this));
    }

    public GeneratedRelation on(int leftField, int rightField) {
        if (leftFields.length != 0 || kind == CROSS) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "Join condition already started");
        }
        validateCondition(leftField, rightField);
        claim();
        return copy(new int[] {leftField}, new int[] {rightField}, kind, filters);
    }

    public GeneratedRelation and(int leftField, int rightField) {
        if (leftFields.length == 0 || kind == CROSS) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "Join condition is missing");
        }
        validateCondition(leftField, rightField);
        int[] nextLeft = new int[leftFields.length + 1];
        int[] nextRight = new int[rightFields.length + 1];
        System.arraycopy(leftFields, 0, nextLeft, 0, leftFields.length);
        System.arraycopy(rightFields, 0, nextRight, 0, rightFields.length);
        nextLeft[leftFields.length] = leftField;
        nextRight[rightFields.length] = rightField;
        claim();
        return copy(nextLeft, nextRight, kind, filters);
    }

    public GeneratedRelation kind(int nextKind) {
        if (leftFields.length == 0 || nextKind < INNER || nextKind > ANTI) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "invalid Equality Join kind");
        }
        claim();
        return copy(leftFields, rightFields, nextKind, filters);
    }

    public GeneratedRelation filter(SomaRelationExpression expression) {
        FilterStage stage = typedFilter(expression);
        claim();
        return append(stage);
    }

    public GeneratedRelation filter(GeneratedCallbacks.RowPredicate callback) {
        if (callback == null) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "Join predicate is null");
        }
        claim();
        return append(FilterStage.callback(callback));
    }

    public long count() {
        claim();
        return execute(
                CanonicalRelationOperation.TerminalKind.COUNT,
                false,
                0L,
                new PairWork<Long>() {
            @Override public Long run(RelationBinding binding) {
                final long[] count = new long[1];
                visit(binding, false, new PairVisitor() {
                    @Override public boolean visit(int left, int right) {
                        count[0] = CheckedLong.increment(
                                count[0], SomaOperation.QUERY, binding.provenance);
                        return true;
                    }
                });
                return count[0];
            }
        });
    }

    public boolean anyMatch(final GeneratedCallbacks.RowPredicate predicate) {
        requireCallback(predicate, "predicate");
        claim();
        return execute(
                CanonicalRelationOperation.TerminalKind.MATCH,
                true,
                0L,
                new PairWork<Boolean>() {
            @Override public Boolean run(final RelationBinding binding) {
                final boolean[] matched = new boolean[1];
                visit(binding, true, new PairVisitor() {
                    @Override public boolean visit(int left, int right) {
                        if (invokePredicate(predicate, binding)) {
                            matched[0] = true;
                            return false;
                        }
                        return true;
                    }
                });
                return matched[0];
            }
        });
    }

    public boolean allMatch(final GeneratedCallbacks.RowPredicate predicate) {
        requireCallback(predicate, "predicate");
        claim();
        return execute(
                CanonicalRelationOperation.TerminalKind.MATCH,
                true,
                0L,
                new PairWork<Boolean>() {
            @Override public Boolean run(final RelationBinding binding) {
                final boolean[] result = new boolean[] {true};
                visit(binding, true, new PairVisitor() {
                    @Override public boolean visit(int left, int right) {
                        if (!invokePredicate(predicate, binding)) {
                            result[0] = false;
                            return false;
                        }
                        return true;
                    }
                });
                return result[0];
            }
        });
    }

    public boolean noneMatch(GeneratedCallbacks.RowPredicate predicate) {
        requireCallback(predicate, "predicate");
        claim();
        return execute(
                CanonicalRelationOperation.TerminalKind.MATCH,
                true,
                0L,
                new PairWork<Boolean>() {
            @Override public Boolean run(final RelationBinding binding) {
                final boolean[] matched = new boolean[1];
                visit(binding, true, new PairVisitor() {
                    @Override public boolean visit(int left, int right) {
                        if (invokePredicate(predicate, binding)) {
                            matched[0] = true;
                            return false;
                        }
                        return true;
                    }
                });
                return !matched[0];
            }
        });
    }

    public void forEach(final GeneratedCallbacks.RowAction action) {
        requireCallback(action, "action");
        claim();
        execute(
                CanonicalRelationOperation.TerminalKind.FOR_EACH,
                true,
                0L,
                new PairWork<Object>() {
            @Override public Object run(final RelationBinding binding) {
                visit(binding, true, new PairVisitor() {
                    @Override public boolean visit(int left, int right) {
                        try {
                            CallbackExecutionScope.enter();
                            action.accept();
                        } catch (Exception failure) {
                            throw SomaFailures.callbackFailure(
                                    SomaOperation.QUERY, failure, binding.provenance);
                        } finally {
                            CallbackExecutionScope.exit();
                        }
                        return true;
                    }
                });
                return null;
            }
        });
    }

    public <R> GeneratedRelationMappedPipeline<R> map(
            GeneratedCallbacks.RowMapper<R> mapper) {
        requireCallback(mapper, "mapper");
        claim();
        return GeneratedRelationMappedPipeline.create(this, mapper);
    }

    public io.github.somaruntime.soma.SomaIntStream mapToInt(
            GeneratedCallbacks.RowToIntMapper mapper) {
        requireCallback(mapper, "mapper");
        claim();
        return GeneratedRelationPrimitivePipeline.intStream(this, mapper);
    }

    public io.github.somaruntime.soma.SomaLongStream mapToLong(
            GeneratedCallbacks.RowToLongMapper mapper) {
        requireCallback(mapper, "mapper");
        claim();
        return GeneratedRelationPrimitivePipeline.longStream(this, mapper);
    }

    public io.github.somaruntime.soma.SomaDoubleStream mapToDouble(
            GeneratedCallbacks.RowToDoubleMapper mapper) {
        requireCallback(mapper, "mapper");
        claim();
        return GeneratedRelationPrimitivePipeline.doubleStream(this, mapper);
    }

    public String explain() {
        claim();
        return execute(
                CanonicalRelationOperation.TerminalKind.EXPLAIN,
                false,
                0L,
                new PairWork<String>() {
            @Override public String run(RelationBinding binding) {
                return "SOMA relation=" + kindName(kind)
                        + " conditions=" + leftFields.length
                        + " physical=" + binding.frame.plan.pipeline.kernel
                        + " binaryOutput="
                        + binding.frame.plan.pipeline.outputShape
                        + " predicatePushdown="
                        + binding.frame.plan.pushedFilterCount
                        + " filters=" + filters.size()
                        + " mode=" + (parallel ? "PARALLEL" : "SEQUENTIAL")
                        + " order=left-then-right";
            }
        });
    }

    <T> T terminal(
            CanonicalRelationOperation.TerminalKind terminal,
            PairWork<T> work,
            boolean callbackScope,
            long outputBytesPerElement) {
        return execute(terminal, callbackScope, outputBytesPerElement, work);
    }

    <T> T terminal(
            CanonicalRelationOperation.TerminalKind terminal,
            PairWork<T> work,
            boolean callbackScope,
            long outputBytesPerElement,
            CanonicalRelationPhysicalDownstream downstream) {
        return execute(
                terminal, callbackScope, outputBytesPerElement,
                downstream, work);
    }

    /** Test-only reference hook retained until the S5 adapter-removal slice. */
    <T> T terminal(
            PairWork<T> work,
            boolean callbackScope,
            long outputBytesPerElement) {
        return execute(
                CanonicalRelationOperation.TerminalKind.TEST,
                callbackScope,
                outputBytesPerElement,
                work);
    }

    <T> T executeLeftCanonical(
            CanonicalRowOperation rowOperation,
            CanonicalQueryOperation.ExtraScratch extra,
            CanonicalMappedOperation mapped,
            CanonicalPrimitiveOperation primitive,
            CanonicalGroupOperation group,
            CanonicalQueryOperation.FrameWork<T> work) {
        return CanonicalRelationQueryOperation.executeLeft(
                this, left, right, rowOperation, extra,
                mapped, primitive, group, work);
    }

    <T> T executeLeftCanonicalReference(
            CanonicalRowOperation rowOperation,
            CanonicalQueryOperation.ReferenceExtraScratch extra,
            CanonicalQueryOperation.ReferenceSourceWork<T> work) {
        return CanonicalRelationQueryOperation.executeLeftReference(
                this, left, right, rowOperation, extra, work);
    }

    void visitBound(
            RelationBinding binding,
            boolean callbackScope,
            PairVisitor visitor) {
        visit(binding, callbackScope, visitor);
    }

    void visitBoundReference(
            RelationBinding binding,
            boolean callbackScope,
            PairVisitor visitor) {
        visitReference(binding, callbackScope, visitor);
    }

    private <T> T execute(
            CanonicalRelationOperation.TerminalKind terminal,
            boolean callbackScope,
            long outputBytesPerElement,
            final PairWork<T> work) {
        return execute(
                terminal, callbackScope, outputBytesPerElement,
                CanonicalRelationPhysicalDownstream.pair(
                        CanonicalRelationOperation.Kind.values()[kind - 1]),
                work);
    }

    private <T> T execute(
            CanonicalRelationOperation.TerminalKind terminal,
            boolean callbackScope,
            long outputBytesPerElement,
            CanonicalRelationPhysicalDownstream downstream,
            final PairWork<T> work) {
        final CanonicalRelationOperation canonical = lower(terminal);
        return CanonicalRelationQueryOperation.execute(
                left,
                right,
                canonical,
                outputBytesPerElement,
                downstream,
                new CanonicalRelationQueryOperation.FrameWork<T>() {
            @Override public T run(CanonicalRelationExecutionFrame frame) {
                return work.run(new RelationBinding(frame));
            }
        });
    }

    CanonicalRelationOperation lower(
            CanonicalRelationOperation.TerminalKind terminal) {
        CanonicalTableIdentity leftIdentity = left.logicalIdentity();
        CanonicalTableIdentity rightIdentity = right.logicalIdentity();
        ArrayList<CanonicalRelationFilter> canonicalFilters =
                new ArrayList<CanonicalRelationFilter>(filters.size());
        for (FilterStage filter : filters) {
            if (filter.callback != null) {
                canonicalFilters.add(new CanonicalRelationFilter(
                        CanonicalRelationFilter.Owner.CALLBACK,
                        null,
                        new RelationCallbackHandle(
                                leftIdentity,
                                rightIdentity,
                                RelationCallbackHandle.Kind.PREDICATE,
                                filter.callback)));
            } else {
                canonicalFilters.add(new CanonicalRelationFilter(
                        filter.owner == left
                                ? CanonicalRelationFilter.Owner.LEFT
                                : CanonicalRelationFilter.Owner.RIGHT,
                        filter.predicate,
                        null));
            }
        }
        return new CanonicalRelationOperation(
                leftIdentity,
                rightIdentity,
                leftFields,
                rightFields,
                CanonicalRelationOperation.Kind.values()[kind - 1],
                maxOutputRows,
                canonicalFilters,
                parallel ? ExecutionRequest.PARALLEL
                        : ExecutionRequest.SEQUENTIAL,
                terminal,
                null);
    }

    long outputUpperBound(RelationBinding binding) {
        return binding.frame.bound().outputUpperBound();
    }

    boolean isBorrowed(Object value) {
        return pairCursor.owns(value)
                || left.isBorrowedQueryView(value)
                || right.isBorrowedQueryView(value);
    }

    long outputUpperBoundForTesting(long leftRows, long rightRows) {
        return CanonicalRelationPlanner.outputUpperBoundForTesting(
                left.layout(), right.layout(),
                lower(CanonicalRelationOperation.TerminalKind.TEST),
                leftRows, rightRows);
    }

    private void visit(
            RelationBinding binding,
            boolean terminalCallback,
            PairVisitor visitor) {
        int kind = binding.canonical.kind.ordinal() + 1;
        int[] leftFields = binding.canonical.leftFields;
        int[] rightFields = binding.canonical.rightFields;
        long maxOutputRows = binding.canonical.maxOutputRows;
        if (kind == CROSS) {
            long product = CheckedLong.multiply(
                    binding.left.size,
                    binding.right.size,
                    SomaOperation.QUERY,
                    binding.provenance);
            if (product > maxOutputRows) {
                throw SomaFailures.failure(
                        SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                        SomaOperation.QUERY,
                        "Cross Join output exceeds maxOutputRows",
                        binding.provenance);
            }
            for (int leftLocator = 0; leftLocator < binding.left.size; leftLocator++) {
                for (int rightLocator = 0; rightLocator < binding.right.size; rightLocator++) {
                    if (!emit(binding, leftLocator, rightLocator, terminalCallback, visitor)) return;
                }
            }
            return;
        }
        if (leftFields.length == 0) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "Equality Join condition is missing");
        }
        IdentityHashIndex rightLookup = rightLookup(binding);
        CanonicalRelationRightHash hash = binding.frame.rightHash;
        if (hash != null) {
            for (int locator = 0; locator < binding.right.size; locator++) {
                if (!hasNull(binding.right, right.layout(), rightFields, locator)
                        && matchesPushedFilters(binding, right, locator)) {
                    hash.add(joinHash(
                            binding.right, right.layout(), rightFields, locator),
                            locator);
                }
            }
        }
        boolean[] matchedRight = binding.frame.matchedRight;
        IdentityHashIndex.Cursor rightCursor = rightLookup == null
                ? null : binding.frame.openRightCursor();
        for (int leftLocator = 0; leftLocator < binding.left.size; leftLocator++) {
            boolean matched = false;
            if (!hasNull(binding.left, left.layout(), leftFields, leftLocator)
                    && matchesPushedFilters(binding, left, leftLocator)) {
                int rightLocator = rightLookup == null
                        ? -1
                        : rightLookup.firstJoin(
                                binding.right.directory,
                                left.layout(),
                                binding.left.directory,
                                leftLocator,
                                leftFields[0],
                                rightCursor);
                int link = rightLookup == null
                        ? hash.head(joinHash(
                                binding.left,
                                left.layout(),
                                leftFields,
                                leftLocator))
                        : 0;
                while (rightLookup != null ? rightLocator >= 0L : link != 0) {
                    if (rightLookup == null) rightLocator = hash.locator(link);
                    boolean candidate = conditionsEqual(
                            binding, leftLocator, rightLocator)
                            && matchesPushedFilters(binding, right, rightLocator);
                    if (candidate) {
                        matched = true;
                        if (matchedRight != null) {
                            matchedRight[(int) rightLocator] = true;
                        }
                        if (kind != SEMI && kind != ANTI
                                && !emit(binding, leftLocator, rightLocator,
                                terminalCallback, visitor)) return;
                        if (kind == SEMI) break;
                    }
                    if (rightLookup != null) rightLocator = rightLookup.next(rightCursor);
                    else link = hash.next(link);
                }
            }
            if (kind == SEMI && matched) {
                if (!emit(binding, leftLocator, -1, terminalCallback, visitor)) return;
            } else if (kind == ANTI && !matched) {
                if (!emit(binding, leftLocator, -1, terminalCallback, visitor)) return;
            } else if ((kind == LEFT || kind == FULL) && !matched) {
                if (!emit(binding, leftLocator, -1, terminalCallback, visitor)) return;
            }
        }
        if (kind == FULL) {
            for (int rightLocator = 0; rightLocator < binding.right.size; rightLocator++) {
                if (!matchedRight[(int) rightLocator]
                        && !emit(binding, -1, rightLocator,
                        terminalCallback, visitor)) return;
            }
        }
    }

    /** Correctness-first nested-loop oracle; never used as a production fallback. */
    private void visitReference(
            RelationBinding binding,
            boolean terminalCallback,
            PairVisitor visitor) {
        int kind = binding.canonical.kind.ordinal() + 1;
        int[] leftFields = binding.canonical.leftFields;
        int[] rightFields = binding.canonical.rightFields;
        long maxOutputRows = binding.canonical.maxOutputRows;
        if (kind == CROSS) {
            long product = CheckedLong.multiply(
                    binding.left.size,
                    binding.right.size,
                    SomaOperation.QUERY,
                    binding.provenance);
            if (product > maxOutputRows) {
                throw SomaFailures.failure(
                        SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                        SomaOperation.QUERY,
                        "Cross Join output exceeds maxOutputRows",
                        binding.provenance);
            }
            for (int leftLocator = 0;
                    leftLocator < binding.left.size;
                    leftLocator++) {
                for (int rightLocator = 0;
                        rightLocator < binding.right.size;
                        rightLocator++) {
                    if (!emitReference(binding, leftLocator, rightLocator,
                            terminalCallback, visitor)) return;
                }
            }
            return;
        }
        if (leftFields.length == 0) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY, "Equality Join condition is missing");
        }
        boolean[] matchedRight = kind == FULL
                ? new boolean[RowExecutionSupport.arrayLength(
                        binding.right.size, binding.provenance)] : null;
        for (int leftLocator = 0;
                leftLocator < binding.left.size;
                leftLocator++) {
            boolean matched = false;
            if (!hasNull(binding.left, left.layout(), leftFields, leftLocator)) {
                for (int rightLocator = 0;
                        rightLocator < binding.right.size;
                        rightLocator++) {
                    if (hasNull(binding.right, right.layout(), rightFields,
                            rightLocator)
                            || !conditionsEqual(
                            binding, leftLocator, rightLocator)) continue;
                    matched = true;
                    if (matchedRight != null) {
                        matchedRight[(int) rightLocator] = true;
                    }
                    if (kind != SEMI && kind != ANTI
                                && !emitReference(binding, leftLocator, rightLocator,
                                terminalCallback, visitor)) return;
                    if (kind == SEMI) break;
                }
            }
            if (kind == SEMI && matched) {
                if (!emitReference(binding, leftLocator, -1,
                        terminalCallback, visitor)) return;
            } else if (kind == ANTI && !matched) {
                if (!emitReference(binding, leftLocator, -1,
                        terminalCallback, visitor)) return;
            } else if ((kind == LEFT || kind == FULL) && !matched) {
                if (!emitReference(binding, leftLocator, -1,
                        terminalCallback, visitor)) return;
            }
        }
        if (kind == FULL) {
            for (int rightLocator = 0;
                    rightLocator < binding.right.size;
                    rightLocator++) {
                if (!matchedRight[(int) rightLocator]
                        && !emitReference(binding, -1, rightLocator,
                        terminalCallback, visitor)) return;
            }
        }
    }

    private boolean emit(
            RelationBinding binding,
            int leftLocator,
            int rightLocator,
            boolean terminalCallback,
            PairVisitor visitor) {
        return emit(
                binding, leftLocator, rightLocator, terminalCallback, visitor, true);
    }

    private boolean emitReference(
            RelationBinding binding,
            int leftLocator,
            int rightLocator,
            boolean terminalCallback,
            PairVisitor visitor) {
        return emit(
                binding, leftLocator, rightLocator, terminalCallback, visitor, false);
    }

    private boolean emit(
            RelationBinding binding,
            int leftLocator,
            int rightLocator,
            boolean terminalCallback,
            PairVisitor visitor,
            boolean optimized) {
        boolean pairScope = terminalCallback
                || binding.canonical.hasCallbackFilter();
        if (pairScope) enterPair(binding, leftLocator, rightLocator);
        try {
            if (!matchesFilters(
                    binding, leftLocator, rightLocator, optimized)) return true;
            return visitor.visit(leftLocator, rightLocator);
        } finally {
            if (pairScope) leavePair(leftLocator, rightLocator);
        }
    }

    private boolean matchesFilters(
            RelationBinding binding,
            int leftLocator,
            int rightLocator,
            boolean optimized) {
        int pushed = optimized ? pushedFilterCount(binding) : 0;
        for (int index = 0;
                index < binding.canonical.filters.size(); index++) {
            if (index < pushed) continue;
            CanonicalRelationFilter filter =
                    binding.canonical.filters.get(index);
            if (filter.callback != null) {
                if (!invokePredicate(
                        (GeneratedCallbacks.RowPredicate)
                                filter.callback.callback,
                        binding)) return false;
                continue;
            }
            if (filter.owner == CanonicalRelationFilter.Owner.LEFT) {
                if (leftLocator < 0L || !PredicateEvaluator.matches(
                        left.layout(), filter.predicate, binding.left, leftLocator)) return false;
            } else if (rightLocator < 0L || !PredicateEvaluator.matches(
                    right.layout(), filter.predicate, binding.right, rightLocator)) return false;
        }
        return true;
    }

    private boolean matchesPushedFilters(
            RelationBinding binding,
            GeneratedTable owner,
            int locator) {
        int pushed = pushedFilterCount(binding);
        for (int index = 0; index < pushed; index++) {
            CanonicalRelationFilter filter =
                    binding.canonical.filters.get(index);
            boolean sameOwner = owner == left
                    ? filter.owner == CanonicalRelationFilter.Owner.LEFT
                    : filter.owner == CanonicalRelationFilter.Owner.RIGHT;
            if (sameOwner && !PredicateEvaluator.matches(
                    owner.layout(),
                    filter.predicate,
                    owner == left ? binding.left : binding.right,
                    locator)) return false;
        }
        return true;
    }

    private static int pushedFilterCount(RelationBinding binding) {
        return binding.frame.plan.pushedFilterCount;
    }

    private IdentityHashIndex rightLookup(RelationBinding binding) {
        if (binding.frame.plan.pipeline.kernel
                != CanonicalBinaryPhysicalPipeline.Kernel.RIGHT_INDEX_LOOKUP) {
            return null;
        }
        int[] leftFields = binding.canonical.leftFields;
        int[] rightFields = binding.canonical.rightFields;
        if (leftFields.length != 1) return null;
        int field = rightFields[0];
        if (right.layout().keyFieldIndex() == field) return binding.right.key;
        int ordinal = right.layout().indexOrdinalForField(field);
        return ordinal < 0 ? null : binding.right.indexes[ordinal];
    }

    private boolean invokePredicate(
            GeneratedCallbacks.RowPredicate predicate,
            RelationBinding binding) {
        try {
            CallbackExecutionScope.enter();
            return predicate.test();
        } catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    SomaOperation.QUERY, failure, binding.provenance);
        } finally {
            CallbackExecutionScope.exit();
        }
    }

    private void enterPair(
            RelationBinding binding,
            int leftLocator,
            int rightLocator) {
        if (leftLocator >= 0L) left.queryCursor().enter(leftLocator);
        try {
            if (rightLocator >= 0L) right.queryCursor().enter(rightLocator);
            try {
                pairCursor.begin(
                        leftLocator >= 0L, rightLocator >= 0L, binding.provenance);
            } catch (RuntimeException failure) {
                if (rightLocator >= 0L) right.queryCursor().leave();
                throw failure;
            }
        } catch (RuntimeException failure) {
            if (leftLocator >= 0L) left.queryCursor().leave();
            throw failure;
        }
    }

    private void leavePair(int leftLocator, int rightLocator) {
        pairCursor.end();
        if (rightLocator >= 0L) right.queryCursor().leave();
        if (leftLocator >= 0L) left.queryCursor().leave();
    }

    private boolean conditionsEqual(
            RelationBinding binding,
            int leftLocator,
            int rightLocator) {
        int[] leftFields = binding.canonical.leftFields;
        int[] rightFields = binding.canonical.rightFields;
        for (int index = 0; index < leftFields.length; index++) {
            if (!left.layout().joinFieldEquals(
                    binding.left.directory,
                    leftLocator,
                    leftFields[index],
                    right.layout(),
                    binding.right.directory,
                    rightLocator,
                    rightFields[index])) return false;
        }
        return true;
    }

    private static boolean hasNull(
            TableStateRoot root,
            GeneratedTableLayout layout,
            int[] fields,
            int locator) {
        for (int field : fields) {
            if (layout.storedFieldIsNull(root.directory, locator, field)) return true;
        }
        return false;
    }

    private static long joinHash(
            TableStateRoot root,
            GeneratedTableLayout layout,
            int[] fields,
            int locator) {
        long result = 1L;
        for (int field : fields) {
            long part = layout.hashField(root.directory, locator, field);
            result = result * 31L + part;
        }
        return result;
    }

    private FilterStage typedFilter(SomaRelationExpression expression) {
        if (!(expression instanceof SomaExpression)
                || !(expression instanceof GeneratedExpression)) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "relation expression is not issued by SOMA");
        }
        GeneratedExpression<?> generated = (GeneratedExpression<?>) expression;
        if (generated.owner() != left && generated.owner() != right) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "relation expression belongs to another Table");
        }
        return FilterStage.typed(generated.owner(), generated.predicate());
    }

    private void validateCondition(int leftField, int rightField) {
        if (!left.layout().joinCompatible(leftField, right.layout(), rightField)) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "Join Fields have incompatible types");
        }
    }

    private GeneratedRelation append(FilterStage stage) {
        ArrayList<FilterStage> next = new ArrayList<FilterStage>(filters.size() + 1);
        next.addAll(filters);
        next.add(stage);
        return copy(leftFields, rightFields, kind, Collections.unmodifiableList(next));
    }

    private GeneratedRelation copy(
            int[] nextLeft,
            int[] nextRight,
            int nextKind,
            List<FilterStage> nextFilters) {
        return new GeneratedRelation(
                left, right, nextLeft.clone(), nextRight.clone(), nextKind,
                maxOutputRows, nextFilters, pairCursor, parallel);
    }

    private GeneratedRelation copy(
            int[] nextLeft,
            int[] nextRight,
            int nextKind,
            List<FilterStage> nextFilters,
            boolean nextParallel) {
        return new GeneratedRelation(
                left, right, nextLeft.clone(), nextRight.clone(), nextKind,
                maxOutputRows, nextFilters, pairCursor, nextParallel);
    }

    private void claim() {
        if (!consumed.compareAndSet(false, true)) {
            throw SomaFailures.failure(
                    SomaFailureCode.PIPELINE_ALREADY_CONSUMED,
                    SomaOperation.QUERY,
                    "linked relation pipeline has already been consumed",
                    new Object());
        }
    }

    private static void requireTables(GeneratedTable left, GeneratedTable right) {
        if (left == null || right == null || left == right) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "invalid binary relation Tables");
        }
    }

    private static void requireCallback(Object value, String category) {
        if (value == null) throw SomaFailures.invalid(
                SomaOperation.QUERY, "Join " + category + " is null");
    }

    private static String kindName(int kind) {
        switch (kind) {
            case INNER: return "INNER";
            case LEFT: return "LEFT";
            case FULL: return "FULL";
            case SEMI: return "SEMI";
            case ANTI: return "ANTI";
            case CROSS: return "CROSS";
            default: throw new AssertionError("unknown Join kind");
        }
    }

    static final class RelationBinding {
        final TableStateRoot left;
        final TableStateRoot right;
        final Object provenance;
        final CanonicalRelationExecutionFrame frame;
        final CanonicalRelationOperation canonical;
        RelationBinding(CanonicalRelationExecutionFrame frame) {
            this.frame = frame;
            this.canonical = frame.bound().canonical;
            this.left = frame.bound().leftRoot;
            this.right = frame.bound().rightRoot;
            this.provenance = frame.bound().provenance;
        }
    }

    interface PairWork<T> {
        T run(RelationBinding binding);
    }

    interface PairVisitor {
        boolean visit(int left, int right);
    }

    private static final class FilterStage {
        final GeneratedTable owner;
        final PredicateIr predicate;
        final GeneratedCallbacks.RowPredicate callback;
        private FilterStage(
                GeneratedTable owner,
                PredicateIr predicate,
                GeneratedCallbacks.RowPredicate callback) {
            this.owner = owner;
            this.predicate = predicate;
            this.callback = callback;
        }
        static FilterStage typed(GeneratedTable owner, PredicateIr predicate) {
            return new FilterStage(owner, predicate, null);
        }
        static FilterStage callback(GeneratedCallbacks.RowPredicate callback) {
            return new FilterStage(null, null, callback);
        }
    }

}
