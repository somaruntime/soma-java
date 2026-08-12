package io.github.somaruntime.soma.internal;

import java.util.List;

/** Finite typed Chunk kernels selected beneath one admitted Canonical PhysicalPlan. */
final class CanonicalPrimitiveVectorKernel {

    private CanonicalPrimitiveVectorKernel() {
    }

    static Decision planCount(CanonicalRowPhysicalPlan physical) {
        KernelPlan kernel = compileCount(physical);
        return kernel == null ? null
                : decision(physical, Operation.COUNT, kernel);
    }

    static boolean isCount(CanonicalRowPhysicalPlan physical) {
        return decision(physical, Operation.COUNT) != null;
    }

    static long count(final CanonicalRowExecutionFrame frame) {
        final Decision decision = requireDecision(frame.plan, Operation.COUNT);
        final KernelPlan plan = decision.kernel;
        final BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        if (decision.managesParallel) {
            CanonicalParallelWorkScheduler.validate(bound);
        }
        if (plan.predicate == null) {
            return bound.root.size;
        }
        final int chunks = logicalChunkCount(bound);
        if (!isParallel(bound) || chunks < 2) {
            long result = 0L;
            for (int ordinal = 0; ordinal < chunks; ordinal++) {
                result = CheckedLong.add(
                        result,
                        countChunk(frame, plan, ordinal),
                        bound.operation,
                        bound.provenance);
            }
            return result;
        }
        final long[] partials = new long[chunks];
        CanonicalParallelWorkScheduler.execute(
                bound, chunks, new CanonicalParallelWorkScheduler.Work() {
            @Override public void run(int ordinal, java.util.concurrent.atomic.AtomicBoolean cancelled) {
                partials[ordinal] = countChunk(frame, plan, ordinal);
            }
        });
        long result = 0L;
        for (long partial : partials) {
            result = CheckedLong.add(
                    result, partial, bound.operation, bound.provenance);
        }
        return result;
    }

    static Decision planIntegralSum(
            CanonicalRowPhysicalPlan physical,
            CanonicalPrimitiveOperation operation) {
        KernelPlan kernel = compilePrimitive(physical, operation, false);
        return kernel == null ? null
                : decision(physical, Operation.INTEGRAL_SUM, kernel);
    }

    static boolean isIntegralSum(CanonicalRowPhysicalPlan physical) {
        return decision(physical, Operation.INTEGRAL_SUM) != null;
    }

    static long sumIntegral(
            final CanonicalRowExecutionFrame frame) {
        final Decision decision = requireDecision(
                frame.plan, Operation.INTEGRAL_SUM);
        final KernelPlan plan = decision.kernel;
        final BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        if (decision.managesParallel) {
            CanonicalParallelWorkScheduler.validate(bound);
        }
        final int chunks = logicalChunkCount(bound);
        if (!isParallel(bound) || chunks < 2) {
            Signed128Accumulator result = new Signed128Accumulator();
            for (int ordinal = 0; ordinal < chunks; ordinal++) {
                sumChunk(frame, plan, ordinal, result);
            }
            return result.longValue(bound.provenance);
        }
        final Signed128Accumulator[] partials =
                new Signed128Accumulator[chunks];
        CanonicalParallelWorkScheduler.execute(
                bound, chunks, new CanonicalParallelWorkScheduler.Work() {
            @Override public void run(int ordinal, java.util.concurrent.atomic.AtomicBoolean cancelled) {
                Signed128Accumulator partial = new Signed128Accumulator();
                sumChunk(frame, plan, ordinal, partial);
                partials[ordinal] = partial;
            }
        });
        Signed128Accumulator result = new Signed128Accumulator();
        for (Signed128Accumulator partial : partials) result.add(partial);
        return result.longValue(bound.provenance);
    }

    static Decision planLongMaterialization(
            CanonicalRowPhysicalPlan physical,
            CanonicalPrimitiveOperation operation) {
        KernelPlan kernel = compilePrimitive(physical, operation, true);
        return kernel == null
                || kernel.projectionKind != GeneratedTableLayout.LONG
                ? null
                : decision(
                        physical,
                        Operation.LONG_MATERIALIZATION,
                        kernel);
    }

    static boolean isLongMaterialization(
            CanonicalRowPhysicalPlan physical) {
        return decision(
                physical, Operation.LONG_MATERIALIZATION) != null;
    }

    static int writeLongs(
            CanonicalRowExecutionFrame frame,
            long[] output) {
        KernelPlan plan = requireDecision(
                frame.plan, Operation.LONG_MATERIALIZATION).kernel;
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        int size = 0;
        int chunks = logicalChunkCount(bound);
        for (int ordinal = 0; ordinal < chunks; ordinal++) {
            size = writeLongChunk(frame, plan, ordinal, output, size);
        }
        return size;
    }

    private static KernelPlan compileCount(
            CanonicalRowPhysicalPlan physical) {
        BoundCanonicalRowOperation bound = physical.normalized.bound;
        if (bound.canonical.terminal != CanonicalRowOperation.TerminalKind.COUNT
                || bound.canonical.sourceKind != CanonicalRowOperation.SourceKind.TABLE
                || physical.accessPath != CanonicalRowPhysicalPlan.AccessPath.TABLE_SCAN
                || bound.canonical.hasStatefulStage()) return null;
        PredicateKernel predicate = null;
        for (CanonicalRowStage stage : physical.normalized.stages) {
            if (stage.kind != CanonicalRowStage.Kind.TYPED_FILTER
                    || predicate != null) return null;
            predicate = PredicateKernel.compile(bound.layout, stage.predicate);
            if (predicate == null) return null;
        }
        return new KernelPlan(-1, (byte) -1, predicate);
    }

    private static KernelPlan compilePrimitive(
            CanonicalRowPhysicalPlan physical,
            CanonicalPrimitiveOperation operation,
            boolean materialization) {
        BoundCanonicalRowOperation bound = physical.normalized.bound;
        if (operation.rootKind != CanonicalPrimitiveOperation.RootKind.ROW
                || operation.mapped != null
                || operation.rootApplicationCallback
                || operation.rootFieldIndex < 0
                || operation.rootValueKind != operation.valueKind
                || !operation.stages.isEmpty()
                || operation.source.sourceKind
                        != CanonicalRowOperation.SourceKind.TABLE
                || physical.accessPath
                        != CanonicalRowPhysicalPlan.AccessPath.TABLE_SCAN
                || operation.source.hasStatefulStage()
                || materialization && isParallel(bound)) return null;
        GeneratedTableLayout layout = bound.layout;
        int field = operation.rootFieldIndex;
        int leaf = layout.fieldStart(field);
        if (layout.fieldLeafCount(field) != 1
                || !integral(layout.leafKind(leaf))) return null;
        List<CanonicalRowStage> stages = physical.normalized.stages;
        PredicateKernel predicate = null;
        boolean projected = false;
        for (CanonicalRowStage stage : stages) {
            if (stage.kind == CanonicalRowStage.Kind.TYPED_FILTER
                    && predicate == null && !projected) {
                predicate = PredicateKernel.compile(layout, stage.predicate);
                if (predicate == null) return null;
            } else if (stage.kind == CanonicalRowStage.Kind.FIELD_PROJECT
                    && stage.fieldIndex == field && !projected) {
                projected = true;
            } else {
                return null;
            }
        }
        if (!projected) return null;
        return new KernelPlan(
                layout.leafSlot(leaf), layout.leafKind(leaf), predicate);
    }

    private static Decision decision(
            CanonicalRowPhysicalPlan physical,
            Operation operation,
            KernelPlan kernel) {
        BoundCanonicalRowOperation bound = physical.normalized.bound;
        boolean managesParallel = isParallel(bound);
        long temporaryBytes = 0L;
        if (managesParallel && bound.root.size != 0) {
            int chunks = logicalChunkCount(bound);
            if (operation == Operation.COUNT && kernel.predicate != null) {
                temporaryBytes = RowExecutionSupport.arrayBytes(
                        chunks, 16L, bound.provenance);
            } else if (operation == Operation.INTEGRAL_SUM) {
                temporaryBytes = RowExecutionSupport.arrayBytes(
                        chunks, 64L, bound.provenance);
            }
        }
        return new Decision(
                operation, kernel, managesParallel, temporaryBytes);
    }

    private static Decision decision(
            CanonicalRowPhysicalPlan physical,
            Operation operation) {
        Decision decision = physical.vectorDecision;
        return decision != null && decision.operation == operation
                ? decision : null;
    }

    private static Decision requireDecision(
            CanonicalRowPhysicalPlan physical,
            Operation operation) {
        Decision decision = decision(physical, operation);
        if (decision == null) {
            throw new AssertionError(
                    "vector kernel was not selected by PhysicalPlan");
        }
        return decision;
    }

    private static long countChunk(
            CanonicalRowExecutionFrame frame,
            KernelPlan plan,
            int ordinal) {
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        TableChunkDirectory directory = bound.root.directory;
        TableChunk chunk = directory.chunk(ordinal);
        int rows = logicalRows(bound, ordinal);
        long result = 0L;
        if (chunk instanceof PlainChunk) {
            for (int offset = 0; offset < rows; offset++) {
                if (plan.predicate.matches((PlainChunk) chunk, offset)) result++;
            }
            return result;
        }
        int first = ordinal * directory.chunkRows();
        for (int offset = 0; offset < rows; offset++) {
            if (OptimizedPredicateEvaluator.matches(
                    bound.layout,
                    plan.predicate.source,
                    bound.root,
                    first + offset,
                    frame.membership)) result++;
        }
        return result;
    }

    private static void sumChunk(
            CanonicalRowExecutionFrame frame,
            KernelPlan plan,
            int ordinal,
            final Signed128Accumulator result) {
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        TableChunkDirectory directory = bound.root.directory;
        TableChunk chunk = directory.chunk(ordinal);
        int rows = logicalRows(bound, ordinal);
        if (chunk instanceof PlainChunk) {
            PlainChunk plain = (PlainChunk) chunk;
            switch (plan.projectionKind) {
                case GeneratedTableLayout.BYTE:
                    byte[] bytes = plain.bytes(plan.projectionSlot);
                    if (plan.predicate == null) {
                        result.addBytes(bytes, rows);
                        return;
                    }
                    for (int offset = 0; offset < rows; offset++) {
                        if (plan.matches(plain, offset)) result.add(bytes[offset]);
                    }
                    return;
                case GeneratedTableLayout.SHORT:
                    short[] shorts = plain.shorts(plan.projectionSlot);
                    if (plan.predicate == null) {
                        result.addShorts(shorts, rows);
                        return;
                    }
                    for (int offset = 0; offset < rows; offset++) {
                        if (plan.matches(plain, offset)) result.add(shorts[offset]);
                    }
                    return;
                case GeneratedTableLayout.CHAR:
                    char[] chars = plain.chars(plan.projectionSlot);
                    if (plan.predicate == null) {
                        result.addChars(chars, rows);
                        return;
                    }
                    for (int offset = 0; offset < rows; offset++) {
                        if (plan.matches(plain, offset)) result.add(chars[offset]);
                    }
                    return;
                case GeneratedTableLayout.INT:
                    int[] ints = plain.ints(plan.projectionSlot);
                    if (plan.predicate == null) {
                        result.addInts(ints, rows);
                        return;
                    }
                    for (int offset = 0; offset < rows; offset++) {
                        if (plan.matches(plain, offset)) result.add(ints[offset]);
                    }
                    return;
                case GeneratedTableLayout.LONG:
                    long[] longs = plain.longs(plan.projectionSlot);
                    if (plan.predicate == null) {
                        result.addLongs(longs, rows);
                        return;
                    }
                    for (int offset = 0; offset < rows; offset++) {
                        if (plan.matches(plain, offset)) result.add(longs[offset]);
                    }
                    return;
                default:
                    throw new AssertionError("non-integral vector projection");
            }
        }
        int first = ordinal * directory.chunkRows();
        for (int offset = 0; offset < rows; offset++) {
            int locator = first + offset;
            if (plan.predicate != null && !OptimizedPredicateEvaluator.matches(
                    bound.layout,
                    plan.predicate.source,
                    bound.root,
                    locator,
                    frame.membership)) continue;
            result.add(integralValue(
                    chunk, plan.projectionKind, plan.projectionSlot, offset));
        }
    }

    private static int writeLongChunk(
            CanonicalRowExecutionFrame frame,
            KernelPlan plan,
            int ordinal,
            long[] output,
            int position) {
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        TableChunkDirectory directory = bound.root.directory;
        TableChunk chunk = directory.chunk(ordinal);
        int rows = logicalRows(bound, ordinal);
        if (chunk instanceof PlainChunk) {
            PlainChunk plain = (PlainChunk) chunk;
            long[] values = plain.longs(plan.projectionSlot);
            if (plan.predicate == null) {
                System.arraycopy(values, 0, output, position, rows);
                return position + rows;
            }
            for (int offset = 0; offset < rows; offset++) {
                if (plan.predicate.matches(plain, offset)) {
                    output[position++] = values[offset];
                }
            }
            return position;
        }
        int first = ordinal * directory.chunkRows();
        for (int offset = 0; offset < rows; offset++) {
            int locator = first + offset;
            if (plan.predicate == null || OptimizedPredicateEvaluator.matches(
                    bound.layout,
                    plan.predicate.source,
                    bound.root,
                    locator,
                    frame.membership)) {
                output[position++] = chunk.longValue(
                        plan.projectionSlot, offset);
            }
        }
        return position;
    }

    private static long integralValue(
            TableChunk chunk,
            byte kind,
            int slot,
            int offset) {
        switch (kind) {
            case GeneratedTableLayout.BYTE: return chunk.byteValue(slot, offset);
            case GeneratedTableLayout.SHORT: return chunk.shortValue(slot, offset);
            case GeneratedTableLayout.CHAR: return chunk.charValue(slot, offset);
            case GeneratedTableLayout.INT: return chunk.intValue(slot, offset);
            case GeneratedTableLayout.LONG: return chunk.longValue(slot, offset);
            default: throw new AssertionError("non-integral vector value");
        }
    }

    private static boolean integral(byte kind) {
        return kind == GeneratedTableLayout.BYTE
                || kind == GeneratedTableLayout.SHORT
                || kind == GeneratedTableLayout.CHAR
                || kind == GeneratedTableLayout.INT
                || kind == GeneratedTableLayout.LONG;
    }

    private static boolean isParallel(BoundCanonicalRowOperation bound) {
        return bound.canonical.request.mode == ExecutionRequest.Mode.PARALLEL;
    }

    private static int logicalChunkCount(BoundCanonicalRowOperation bound) {
        return CheckedStructural.ceilChunks(
                bound.root.size, bound.root.directory.chunkRows());
    }

    private static int logicalRows(
            BoundCanonicalRowOperation bound,
            int ordinal) {
        int first = ordinal * bound.root.directory.chunkRows();
        return Math.min(
                bound.root.directory.chunkRows(), bound.root.size - first);
    }

    enum Operation {
        COUNT, INTEGRAL_SUM, LONG_MATERIALIZATION
    }

    /** Immutable physical decision; execution consumes it without re-planning. */
    static final class Decision {
        final Operation operation;
        final KernelPlan kernel;
        final boolean managesParallel;
        final long temporaryBytes;

        Decision(
                Operation operation,
                KernelPlan kernel,
                boolean managesParallel,
                long temporaryBytes) {
            this.operation = operation;
            this.kernel = kernel;
            this.managesParallel = managesParallel;
            this.temporaryBytes = temporaryBytes;
        }
    }

    private static final class KernelPlan {
        final int projectionSlot;
        final byte projectionKind;
        final PredicateKernel predicate;

        KernelPlan(
                int projectionSlot,
                byte projectionKind,
                PredicateKernel predicate) {
            this.projectionSlot = projectionSlot;
            this.projectionKind = projectionKind;
            this.predicate = predicate;
        }

        boolean matches(PlainChunk chunk, int offset) {
            return predicate == null || predicate.matches(chunk, offset);
        }
    }

    /** Closed compiled predicate tree; no application callback or reflective dispatch. */
    private static final class PredicateKernel {
        final PredicateIr source;
        final PredicateIr.Kind kind;
        final int leaf;
        final int slot;
        final byte leafKind;
        final PredicateKernel left;
        final PredicateKernel right;

        private PredicateKernel(
                PredicateIr source,
                int leaf,
                int slot,
                byte leafKind,
                PredicateKernel left,
                PredicateKernel right) {
            this.source = source;
            this.kind = source.kind;
            this.leaf = leaf;
            this.slot = slot;
            this.leafKind = leafKind;
            this.left = left;
            this.right = right;
        }

        static PredicateKernel compile(
                GeneratedTableLayout layout,
                PredicateIr source) {
            switch (source.kind) {
                case CONSTANT:
                    return new PredicateKernel(
                            source, -1, -1, (byte) -1, null, null);
                case AND:
                case OR:
                    PredicateKernel left = compile(layout, source.left);
                    PredicateKernel right = compile(layout, source.right);
                    return left == null || right == null ? null
                            : new PredicateKernel(
                                    source, -1, -1, (byte) -1, left, right);
                case NOT:
                    PredicateKernel child = compile(layout, source.left);
                    return child == null ? null
                            : new PredicateKernel(
                                    source, -1, -1, (byte) -1, child, null);
                case EQ:
                case NE:
                case LT:
                case LE:
                case GT:
                case GE:
                case BETWEEN:
                    int field = source.fieldIndex;
                    int leaf = layout.fieldStart(field);
                    if (layout.fieldLeafCount(field) != 1
                            || !integral(layout.leafKind(leaf))) return null;
                    return new PredicateKernel(
                            source,
                            leaf,
                            layout.leafSlot(leaf),
                            layout.leafKind(leaf),
                            null,
                            null);
                default:
                    return null;
            }
        }

        boolean matches(PlainChunk chunk, int offset) {
            switch (kind) {
                case CONSTANT: return source.constant;
                case AND: return left.matches(chunk, offset)
                        && right.matches(chunk, offset);
                case OR: return left.matches(chunk, offset)
                        || right.matches(chunk, offset);
                case NOT: return !left.matches(chunk, offset);
                case EQ: return compare(chunk, offset, source.lower) == 0;
                case NE: return compare(chunk, offset, source.lower) != 0;
                case LT: return compare(chunk, offset, source.lower) < 0;
                case LE: return compare(chunk, offset, source.lower) <= 0;
                case GT: return compare(chunk, offset, source.lower) > 0;
                case GE: return compare(chunk, offset, source.lower) >= 0;
                case BETWEEN: return compare(chunk, offset, source.lower) >= 0
                        && compare(chunk, offset, source.upper) <= 0;
                default: throw new AssertionError("unsupported vector predicate");
            }
        }

        private int compare(
                PlainChunk chunk,
                int offset,
                TypedLiteral literal) {
            switch (leafKind) {
                case GeneratedTableLayout.BYTE:
                    return Byte.compare(
                            chunk.bytes(slot)[offset], literal.byteValue(leaf));
                case GeneratedTableLayout.SHORT:
                    return Short.compare(
                            chunk.shorts(slot)[offset], literal.shortValue(leaf));
                case GeneratedTableLayout.CHAR:
                    return Character.compare(
                            chunk.chars(slot)[offset], literal.charValue(leaf));
                case GeneratedTableLayout.INT:
                    return Integer.compare(
                            chunk.ints(slot)[offset], literal.intValue(leaf));
                case GeneratedTableLayout.LONG:
                    return Long.compare(
                            chunk.longs(slot)[offset], literal.longValue(leaf));
                default:
                    throw new AssertionError("non-integral vector predicate");
            }
        }
    }
}
