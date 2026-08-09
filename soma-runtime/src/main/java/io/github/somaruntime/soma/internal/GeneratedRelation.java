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
    private final AtomicBoolean consumed = new AtomicBoolean();

    private GeneratedRelation(
            GeneratedTable left,
            GeneratedTable right,
            int[] leftFields,
            int[] rightFields,
            int kind,
            long maxOutputRows,
            List<FilterStage> filters,
            GeneratedPairCursor pairCursor) {
        this.left = left;
        this.right = right;
        this.leftFields = leftFields;
        this.rightFields = rightFields;
        this.kind = kind;
        this.maxOutputRows = maxOutputRows;
        this.filters = filters;
        this.pairCursor = pairCursor;
    }

    public static GeneratedRelation equality(
            GeneratedTable left,
            GeneratedTable right) {
        requireTables(left, right);
        return new GeneratedRelation(
                left, right, new int[0], new int[0], INNER, Long.MAX_VALUE,
                Collections.<FilterStage>emptyList(), new GeneratedPairCursor());
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
                Collections.<FilterStage>emptyList(), new GeneratedPairCursor());
    }

    public GeneratedPairAccess pairAccess() {
        return pairCursor;
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
        return execute(false, new PairWork<Long>() {
            @Override public Long run(RelationBinding binding) {
                final long[] count = new long[1];
                visit(binding, false, new PairVisitor() {
                    @Override public boolean visit(long left, long right) {
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
        return execute(true, new PairWork<Boolean>() {
            @Override public Boolean run(final RelationBinding binding) {
                final boolean[] matched = new boolean[1];
                visit(binding, true, new PairVisitor() {
                    @Override public boolean visit(long left, long right) {
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
        return execute(true, new PairWork<Boolean>() {
            @Override public Boolean run(final RelationBinding binding) {
                final boolean[] result = new boolean[] {true};
                visit(binding, true, new PairVisitor() {
                    @Override public boolean visit(long left, long right) {
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
        return execute(true, new PairWork<Boolean>() {
            @Override public Boolean run(final RelationBinding binding) {
                final boolean[] matched = new boolean[1];
                visit(binding, true, new PairVisitor() {
                    @Override public boolean visit(long left, long right) {
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
        execute(true, new PairWork<Object>() {
            @Override public Object run(final RelationBinding binding) {
                visit(binding, true, new PairVisitor() {
                    @Override public boolean visit(long left, long right) {
                        try {
                            action.accept();
                        } catch (Exception failure) {
                            throw SomaFailures.callbackFailure(
                                    SomaOperation.QUERY, failure, binding.provenance);
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
        return execute(false, new PairWork<String>() {
            @Override public String run(RelationBinding binding) {
                return "SOMA relation=" + kindName(kind)
                        + " conditions=" + leftFields.length
                        + " physical=" + physicalName()
                        + " predicatePushdown=" + pushableFilterCount()
                        + " filters=" + filters.size()
                        + " order=left-then-right";
            }
        });
    }

    <T> T terminal(
            PairWork<T> work,
            boolean callbackScope,
            long outputBytesPerElement) {
        return execute(callbackScope, outputBytesPerElement, work);
    }

    <T> T executeLeft(
            LogicalRowPlan logical,
            QueryOperation.BoundWork<T> work) {
        if (!left.sharesGroup(right) || logical.owner() != left) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY, "invalid relation-derived left source");
        }
        try (GroupOperationGuard.Lease operation = left.acquireQuery()) {
            TableStateRoot leftRoot = left.currentRoot();
            TableStateRoot rightRoot = right.currentRoot();
            Object provenance = operation.provenance();
            BoundRowPlan provisional = new BoundRowPlan(
                    logical, leftRoot, SomaOperation.QUERY, provenance);
            long scratch = QueryOperation.addScratch(
                    scratchBytes(rightRoot.size, provenance),
                    RowExecutionSupport.arrayBytes(
                            leftRoot.size, 16L, provenance),
                    provenance);
            scratch = QueryOperation.addScratch(
                    scratch, work.scratchBytes(provisional), provenance);
            try (GlobalMemoryManager.TemporaryLease ignored =
                         left.leaseQueryTemporary(scratch, provenance)) {
                left.queryCursor().begin(leftRoot, SomaOperation.QUERY, provenance);
                try {
                    left.secondaryQueryCursor().begin(
                            leftRoot, SomaOperation.QUERY, provenance);
                    try {
                        right.queryCursor().begin(
                                rightRoot, SomaOperation.QUERY, provenance);
                        try {
                            final LongLocatorBuffer source = new LongLocatorBuffer(
                                    leftRoot.size, SomaOperation.QUERY, provenance);
                            RelationBinding binding = new RelationBinding(
                                    leftRoot, rightRoot, provenance);
                            visit(binding, false, new PairVisitor() {
                                @Override public boolean visit(
                                        long leftLocator, long rightLocator) {
                                    source.add(leftLocator);
                                    return true;
                                }
                            });
                            return work.run(new BoundRowPlan(
                                    logical, leftRoot, SomaOperation.QUERY,
                                    provenance, source));
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

    private <T> T execute(boolean callbackScope, PairWork<T> work) {
        return execute(callbackScope, 0L, work);
    }

    private <T> T execute(
            boolean callbackScope,
            long outputBytesPerElement,
            PairWork<T> work) {
        if (!left.sharesGroup(right)) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY, "Join Tables belong to different SomaGroup instances");
        }
        try (GroupOperationGuard.Lease operation = left.acquireQuery()) {
            TableStateRoot leftRoot = left.currentRoot();
            TableStateRoot rightRoot = right.currentRoot();
            Object provenance = operation.provenance();
            long scratch = scratchBytes(rightRoot.size, provenance);
            if (outputBytesPerElement != 0L) {
                scratch = QueryOperation.addScratch(
                        scratch,
                        RowExecutionSupport.arrayBytes(
                                outputUpperBound(leftRoot, rightRoot, provenance),
                                outputBytesPerElement,
                                provenance),
                        provenance);
            }
            try (GlobalMemoryManager.TemporaryLease ignored =
                         left.leaseQueryTemporary(scratch, provenance)) {
                left.queryCursor().begin(leftRoot, SomaOperation.QUERY, provenance);
                try {
                    right.queryCursor().begin(rightRoot, SomaOperation.QUERY, provenance);
                    try {
                        RelationBinding binding = new RelationBinding(
                                leftRoot, rightRoot, provenance);
                        return work.run(binding);
                    } finally {
                        right.queryCursor().end();
                    }
                } finally {
                    left.queryCursor().end();
                }
            }
        }
    }

    long outputUpperBound(RelationBinding binding) {
        return outputUpperBound(binding.left, binding.right, binding.provenance);
    }

    boolean isBorrowed(Object value) {
        return pairCursor.owns(value)
                || left.isBorrowedQueryView(value)
                || right.isBorrowedQueryView(value);
    }

    private long outputUpperBound(
            TableStateRoot leftRoot,
            TableStateRoot rightRoot,
            Object provenance) {
        if (kind == SEMI || kind == ANTI) return leftRoot.size;
        long product = CheckedLong.multiply(
                leftRoot.size, rightRoot.size, SomaOperation.QUERY, provenance);
        if (kind == INNER || kind == CROSS) return product;
        long result = CheckedLong.add(
                product, leftRoot.size, SomaOperation.QUERY, provenance);
        return kind == FULL
                ? CheckedLong.add(result, rightRoot.size, SomaOperation.QUERY, provenance)
                : result;
    }

    private long scratchBytes(long rightRows, Object provenance) {
        long bytes = RowExecutionSupport.arrayBytes(
                rightRows, kind == FULL ? 32L : 24L, provenance);
        return bytes;
    }

    private void visit(
            RelationBinding binding,
            boolean terminalCallback,
            PairVisitor visitor) {
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
            for (long leftLocator = 0L; leftLocator < binding.left.size; leftLocator++) {
                for (long rightLocator = 0L; rightLocator < binding.right.size; rightLocator++) {
                    if (!emit(binding, leftLocator, rightLocator, terminalCallback, visitor)) return;
                }
            }
            return;
        }
        if (leftFields.length == 0) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "Equality Join condition is missing");
        }
        IdentityHashIndex rightLookup = rightLookup(binding.right);
        RightHash hash = rightLookup == null
                ? new RightHash(binding.right.size, binding.provenance) : null;
        if (hash != null) {
            for (long locator = 0L; locator < binding.right.size; locator++) {
                if (!hasNull(binding.right, right.layout(), rightFields, locator)
                        && matchesPushedFilters(binding, right, locator)) {
                    hash.add(joinHash(
                            binding.right, right.layout(), rightFields, locator),
                            locator);
                }
            }
        }
        boolean[] matchedRight = kind == FULL
                ? new boolean[RowExecutionSupport.arrayLength(
                        binding.right.size, binding.provenance)] : null;
        for (long leftLocator = 0L; leftLocator < binding.left.size; leftLocator++) {
            boolean matched = false;
            if (!hasNull(binding.left, left.layout(), leftFields, leftLocator)
                    && matchesPushedFilters(binding, left, leftLocator)) {
                long rightLocator = rightLookup == null
                        ? -1L
                        : rightLookup.firstJoin(
                                binding.right.directory,
                                left.layout(),
                                binding.left.directory,
                                leftLocator,
                                leftFields[0]);
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
                    if (rightLookup != null) rightLocator = rightLookup.next(rightLocator);
                    else link = hash.next(link);
                }
            }
            if (kind == SEMI && matched) {
                if (!emit(binding, leftLocator, -1L, terminalCallback, visitor)) return;
            } else if (kind == ANTI && !matched) {
                if (!emit(binding, leftLocator, -1L, terminalCallback, visitor)) return;
            } else if ((kind == LEFT || kind == FULL) && !matched) {
                if (!emit(binding, leftLocator, -1L, terminalCallback, visitor)) return;
            }
        }
        if (kind == FULL) {
            for (long rightLocator = 0L; rightLocator < binding.right.size; rightLocator++) {
                if (!matchedRight[(int) rightLocator]
                        && !emit(binding, -1L, rightLocator,
                        terminalCallback, visitor)) return;
            }
        }
    }

    /** Correctness-first nested-loop oracle; never used as a production fallback. */
    private void visitReference(
            RelationBinding binding,
            boolean terminalCallback,
            PairVisitor visitor) {
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
            for (long leftLocator = 0L;
                    leftLocator < binding.left.size;
                    leftLocator++) {
                for (long rightLocator = 0L;
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
        for (long leftLocator = 0L;
                leftLocator < binding.left.size;
                leftLocator++) {
            boolean matched = false;
            if (!hasNull(binding.left, left.layout(), leftFields, leftLocator)) {
                for (long rightLocator = 0L;
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
                if (!emitReference(binding, leftLocator, -1L,
                        terminalCallback, visitor)) return;
            } else if (kind == ANTI && !matched) {
                if (!emitReference(binding, leftLocator, -1L,
                        terminalCallback, visitor)) return;
            } else if ((kind == LEFT || kind == FULL) && !matched) {
                if (!emitReference(binding, leftLocator, -1L,
                        terminalCallback, visitor)) return;
            }
        }
        if (kind == FULL) {
            for (long rightLocator = 0L;
                    rightLocator < binding.right.size;
                    rightLocator++) {
                if (!matchedRight[(int) rightLocator]
                        && !emitReference(binding, -1L, rightLocator,
                        terminalCallback, visitor)) return;
            }
        }
    }

    private boolean emit(
            RelationBinding binding,
            long leftLocator,
            long rightLocator,
            boolean terminalCallback,
            PairVisitor visitor) {
        return emit(
                binding, leftLocator, rightLocator, terminalCallback, visitor, true);
    }

    private boolean emitReference(
            RelationBinding binding,
            long leftLocator,
            long rightLocator,
            boolean terminalCallback,
            PairVisitor visitor) {
        return emit(
                binding, leftLocator, rightLocator, terminalCallback, visitor, false);
    }

    private boolean emit(
            RelationBinding binding,
            long leftLocator,
            long rightLocator,
            boolean terminalCallback,
            PairVisitor visitor,
            boolean optimized) {
        boolean pairScope = terminalCallback || hasCallbackFilter();
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
            long leftLocator,
            long rightLocator,
            boolean optimized) {
        int pushed = optimized ? pushableFilterCount() : 0;
        for (int index = 0; index < filters.size(); index++) {
            if (index < pushed) continue;
            FilterStage filter = filters.get(index);
            if (filter.callback != null) {
                if (!invokePredicate(filter.callback, binding)) return false;
                continue;
            }
            if (filter.owner == left) {
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
            long locator) {
        int pushed = pushableFilterCount();
        for (int index = 0; index < pushed; index++) {
            FilterStage filter = filters.get(index);
            if (filter.owner == owner && !PredicateEvaluator.matches(
                    owner.layout(),
                    filter.predicate,
                    owner == left ? binding.left : binding.right,
                    locator)) return false;
        }
        return true;
    }

    private int pushableFilterCount() {
        if (kind != INNER) return 0;
        int count = 0;
        while (count < filters.size() && filters.get(count).callback == null) {
            count++;
        }
        return count;
    }

    private IdentityHashIndex rightLookup(TableStateRoot rightRoot) {
        if (leftFields.length != 1) return null;
        int field = rightFields[0];
        if (right.layout().keyFieldIndex() == field) return rightRoot.key;
        int ordinal = right.layout().indexOrdinalForField(field);
        return ordinal < 0 ? null : rightRoot.indexes[ordinal];
    }

    private boolean usesRightIndex() {
        if (leftFields.length != 1) return false;
        int field = rightFields[0];
        return right.layout().keyFieldIndex() == field
                || right.layout().indexOrdinalForField(field) >= 0;
    }

    private String physicalName() {
        if (kind == CROSS) return "NESTED_CROSS";
        return usesRightIndex() ? "RIGHT_INDEX_LOOKUP" : "RIGHT_HASH";
    }

    private boolean invokePredicate(
            GeneratedCallbacks.RowPredicate predicate,
            RelationBinding binding) {
        try {
            return predicate.test();
        } catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    SomaOperation.QUERY, failure, binding.provenance);
        }
    }

    private void enterPair(
            RelationBinding binding,
            long leftLocator,
            long rightLocator) {
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

    private void leavePair(long leftLocator, long rightLocator) {
        pairCursor.end();
        if (rightLocator >= 0L) right.queryCursor().leave();
        if (leftLocator >= 0L) left.queryCursor().leave();
    }

    private boolean conditionsEqual(
            RelationBinding binding,
            long leftLocator,
            long rightLocator) {
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
            long locator) {
        for (int field : fields) {
            if (layout.storedFieldIsNull(root.directory, locator, field)) return true;
        }
        return false;
    }

    private static long joinHash(
            TableStateRoot root,
            GeneratedTableLayout layout,
            int[] fields,
            long locator) {
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
                maxOutputRows, nextFilters, pairCursor);
    }

    private boolean hasCallbackFilter() {
        for (FilterStage filter : filters) if (filter.callback != null) return true;
        return false;
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
        RelationBinding(TableStateRoot left, TableStateRoot right, Object provenance) {
            this.left = left;
            this.right = right;
            this.provenance = provenance;
        }
    }

    interface PairWork<T> {
        T run(RelationBinding binding);
    }

    interface PairVisitor {
        boolean visit(long left, long right);
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

    private static final class RightHash {
        private final int[] heads;
        private final int[] tails;
        private final int[] next;
        private final long[] locators;
        private int size;

        RightHash(long rows, Object provenance) {
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
            locators = new long[length];
        }

        void add(long hash, long locator) {
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
        long locator(int link) { return locators[link - 1]; }

        private static long mix(long value) {
            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdL;
            value ^= value >>> 33;
            value *= 0xc4ceb9fe1a85ec53L;
            return value ^ value >>> 33;
        }
    }
}
