package io.github.somaruntime.soma.internal;

import java.util.Arrays;
import java.util.List;

/** Finite typed Chunk kernels selected beneath one admitted Canonical PhysicalPlan. */
final class CanonicalPrimitiveVectorKernel {

    private CanonicalPrimitiveVectorKernel() {
    }

    static Decision plan(
            NormalizedCanonicalRow normalized,
            CanonicalRowPhysicalPlan.AccessPath accessPath,
            CanonicalPrimitiveOperation primitive) {
        if (primitive == null) {
            if (normalized.bound.canonical.terminal
                    != CanonicalRowOperation.TerminalKind.COUNT) {
                return null;
            }
            KernelPlan count = compileCount(normalized, accessPath);
            return count == null ? null
                    : decision(normalized.bound, Operation.COUNT, count);
        }
        if (primitive.terminal
                == CanonicalPrimitiveOperation.TerminalKind.SUM) {
            KernelPlan sum = compilePrimitive(
                    normalized, accessPath, primitive);
            return sum == null ? null
                    : decision(normalized.bound, Operation.INTEGRAL_SUM, sum);
        }
        if (primitive.terminal
                        == CanonicalPrimitiveOperation.TerminalKind.MATERIALIZE
                && primitive.valueKind == PrimitiveValueKind.LONG) {
            KernelPlan materialization = compilePrimitive(
                    normalized, accessPath, primitive);
            return materialization == null
                    || materialization.projectionKind
                            != GeneratedTableLayout.LONG
                    ? null
                    : decision(
                            normalized.bound,
                            Operation.LONG_MATERIALIZATION,
                            materialization);
        }
        return null;
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
                        countChunk(frame, decision, ordinal),
                        bound.operation,
                        bound.provenance);
            }
            return result;
        }
        final long[] partials = new long[chunks];
        CanonicalParallelWorkScheduler.execute(
                bound, chunks, new CanonicalParallelWorkScheduler.Work() {
            @Override public void run(int ordinal, java.util.concurrent.atomic.AtomicBoolean cancelled) {
                partials[ordinal] = countChunk(frame, decision, ordinal);
            }
        });
        long result = 0L;
        for (long partial : partials) {
            result = CheckedLong.add(
                    result, partial, bound.operation, bound.provenance);
        }
        return result;
    }

    static boolean isIntegralSum(CanonicalRowPhysicalPlan physical) {
        return decision(physical, Operation.INTEGRAL_SUM) != null;
    }

    static long sumIntegral(
            final CanonicalRowExecutionFrame frame) {
        final Decision decision = requireDecision(
                frame.plan, Operation.INTEGRAL_SUM);
        final BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        if (decision.managesParallel) {
            CanonicalParallelWorkScheduler.validate(bound);
        }
        final int chunks = logicalChunkCount(bound);
        if (!isParallel(bound) || chunks < 2) {
            Signed128Accumulator result = new Signed128Accumulator();
            for (int ordinal = 0; ordinal < chunks; ordinal++) {
                sumChunk(frame, decision, ordinal, result);
            }
            return result.longValue(bound.provenance);
        }
        final Signed128Accumulator[] partials =
                new Signed128Accumulator[chunks];
        CanonicalParallelWorkScheduler.execute(
                bound, chunks, new CanonicalParallelWorkScheduler.Work() {
            @Override public void run(int ordinal, java.util.concurrent.atomic.AtomicBoolean cancelled) {
                Signed128Accumulator partial = new Signed128Accumulator();
                sumChunk(frame, decision, ordinal, partial);
                partials[ordinal] = partial;
            }
        });
        Signed128Accumulator result = new Signed128Accumulator();
        for (Signed128Accumulator partial : partials) result.add(partial);
        return result.longValue(bound.provenance);
    }

    static boolean isLongMaterialization(
            CanonicalRowPhysicalPlan physical) {
        return decision(
                physical, Operation.LONG_MATERIALIZATION) != null;
    }

    static long[] materializeLongs(
            final CanonicalRowExecutionFrame frame) {
        final Decision decision = requireDecision(
                frame.plan, Operation.LONG_MATERIALIZATION);
        final KernelPlan plan = decision.kernel;
        final BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        if (decision.managesParallel) {
            CanonicalParallelWorkScheduler.validate(bound);
        }
        final int chunks = logicalChunkCount(bound);
        if (plan.predicate == null) {
            final long[] output = new long[bound.root.size];
            if (!isParallel(bound) || chunks < 2) {
                for (int ordinal = 0; ordinal < chunks; ordinal++) {
                    int start = ordinal * bound.root.directory.chunkRows();
                    int end = writeLongChunk(
                            frame, decision, ordinal, output, start);
                    if (end != start + logicalRows(bound, ordinal)) {
                        throw new AssertionError("long materialization range drift");
                    }
                }
                return output;
            }
            CanonicalParallelWorkScheduler.execute(
                    bound, chunks, new CanonicalParallelWorkScheduler.Work() {
                @Override public void run(
                        int ordinal,
                        java.util.concurrent.atomic.AtomicBoolean cancelled) {
                    int start = ordinal * bound.root.directory.chunkRows();
                    int end = writeLongChunk(
                            frame, decision, ordinal, output, start);
                    if (end != start + logicalRows(bound, ordinal)) {
                        throw new AssertionError("parallel long range drift");
                    }
                }
            });
            return output;
        }

        if (!isParallel(bound)) {
            long[] staging = new long[bound.root.size];
            int size = 0;
            for (int ordinal = 0; ordinal < chunks; ordinal++) {
                size = writeLongChunk(
                        frame, decision, ordinal, staging, size);
            }
            if (size == staging.length) return staging;
            long[] result = new long[size];
            System.arraycopy(staging, 0, result, 0, size);
            return result;
        }

        final int[] counts = new int[chunks];
        final int[] offsets = new int[chunks];
        if (chunks < 2) {
            for (int ordinal = 0; ordinal < chunks; ordinal++) {
                counts[ordinal] = countLongChunk(frame, decision, ordinal);
            }
        } else {
            CanonicalParallelWorkScheduler.execute(
                    bound, chunks, new CanonicalParallelWorkScheduler.Work() {
                @Override public void run(
                        int ordinal,
                        java.util.concurrent.atomic.AtomicBoolean cancelled) {
                    counts[ordinal] = countLongChunk(frame, decision, ordinal);
                }
            });
        }
        long total = 0L;
        for (int ordinal = 0; ordinal < chunks; ordinal++) {
            offsets[ordinal] = CheckedStructural.fromLong(
                    total, bound.operation, bound.provenance);
            total = CheckedLong.add(
                    total,
                    counts[ordinal],
                    bound.operation,
                    bound.provenance);
        }
        final long[] output = new long[CheckedStructural.fromLong(
                total, bound.operation, bound.provenance)];
        if (chunks < 2) {
            for (int ordinal = 0; ordinal < chunks; ordinal++) {
                int end = writeLongChunk(
                        frame, decision, ordinal, output, offsets[ordinal]);
                if (end != offsets[ordinal] + counts[ordinal]) {
                    throw new AssertionError("long materialization count drift");
                }
            }
            return output;
        }
        CanonicalParallelWorkScheduler.execute(
                bound, chunks, new CanonicalParallelWorkScheduler.Work() {
            @Override public void run(
                    int ordinal,
                    java.util.concurrent.atomic.AtomicBoolean cancelled) {
                int end = writeLongChunk(
                        frame, decision, ordinal, output, offsets[ordinal]);
                if (end != offsets[ordinal] + counts[ordinal]) {
                    throw new AssertionError("parallel long count drift");
                }
            }
        });
        return output;
    }

    private static KernelPlan compileCount(
            NormalizedCanonicalRow normalized,
            CanonicalRowPhysicalPlan.AccessPath accessPath) {
        BoundCanonicalRowOperation bound = normalized.bound;
        if (bound.canonical.terminal != CanonicalRowOperation.TerminalKind.COUNT
                || bound.canonical.sourceKind != CanonicalRowOperation.SourceKind.TABLE
                || accessPath != CanonicalRowPhysicalPlan.AccessPath.TABLE_SCAN
                || bound.canonical.hasStatefulStage()) return null;
        PredicateKernel predicate = null;
        for (CanonicalRowStage stage : normalized.stages) {
            if (stage.kind != CanonicalRowStage.Kind.TYPED_FILTER
                    || predicate != null) return null;
            predicate = PredicateKernel.compile(bound.layout, stage.predicate);
            if (predicate == null) return null;
        }
        return new KernelPlan(-1, -1, (byte) -1, predicate);
    }

    private static KernelPlan compilePrimitive(
            NormalizedCanonicalRow normalized,
            CanonicalRowPhysicalPlan.AccessPath accessPath,
            CanonicalPrimitiveOperation operation) {
        BoundCanonicalRowOperation bound = normalized.bound;
        if (operation.rootKind != CanonicalPrimitiveOperation.RootKind.ROW
                || operation.mapped != null
                || operation.rootApplicationCallback
                || operation.rootFieldIndex < 0
                || operation.rootValueKind != operation.valueKind
                || !operation.stages.isEmpty()
                || operation.source.sourceKind
                        != CanonicalRowOperation.SourceKind.TABLE
                || accessPath != CanonicalRowPhysicalPlan.AccessPath.TABLE_SCAN
                || operation.source.hasStatefulStage()) return null;
        GeneratedTableLayout layout = bound.layout;
        int field = operation.rootFieldIndex;
        int leaf = layout.fieldStart(field);
        if (layout.fieldLeafCount(field) != 1
                || !integral(layout.leafKind(leaf))) return null;
        List<CanonicalRowStage> stages = normalized.stages;
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
                leaf,
                layout.leafSlot(leaf),
                layout.leafKind(leaf),
                predicate);
    }

    private static Decision decision(
            BoundCanonicalRowOperation bound,
            Operation operation,
            KernelPlan kernel) {
        boolean managesParallel = isParallel(bound);
        long temporaryBytes = 0L;
        boolean ownsTerminalScratch =
                operation == Operation.LONG_MATERIALIZATION;
        int chunks = logicalChunkCount(bound);
        if (ownsTerminalScratch) {
            temporaryBytes = RowExecutionSupport.arrayBytes(
                    bound.root.size, 8L, bound.provenance);
            if (kernel.predicate != null && !managesParallel) {
                temporaryBytes = RowExecutionSupport.arrayBytes(
                        bound.root.size, 16L, bound.provenance);
            } else if (kernel.predicate != null) {
                temporaryBytes = CheckedLong.add(
                        temporaryBytes,
                        RowExecutionSupport.arrayBytes(
                                chunks, 4L, bound.provenance),
                        bound.operation,
                        bound.provenance);
                temporaryBytes = CheckedLong.add(
                        temporaryBytes,
                        RowExecutionSupport.arrayBytes(
                                chunks, 4L, bound.provenance),
                        bound.operation,
                        bound.provenance);
            }
            if (managesParallel && bound.root.size != 0) {
                temporaryBytes = CheckedLong.add(
                        temporaryBytes,
                        RowExecutionSupport.arrayBytes(
                                chunks, 16L, bound.provenance),
                        bound.operation,
                        bound.provenance);
            }
        } else if (managesParallel && bound.root.size != 0) {
            if (operation == Operation.COUNT && kernel.predicate != null) {
                temporaryBytes = RowExecutionSupport.arrayBytes(
                        chunks, 16L, bound.provenance);
            } else if (operation == Operation.INTEGRAL_SUM) {
                temporaryBytes = RowExecutionSupport.arrayBytes(
                        chunks, 64L, bound.provenance);
            }
        }
        return new Decision(
                operation,
                kernel,
                managesParallel,
                ownsTerminalScratch,
                temporaryBytes);
    }

    private static Decision decision(
            CanonicalRowPhysicalPlan physical,
            Operation operation) {
        Decision decision = physical.pipeline.terminalSegment().chunkKernel;
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
            Decision decision,
            int ordinal) {
        KernelPlan plan = decision.kernel;
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        TableChunkDirectory directory = bound.root.directory;
        TableChunk chunk = directory.chunk(ordinal);
        int rows = logicalRows(bound, ordinal);
        long result = 0L;
        RepresentationHandler handler = decision.handler(chunk);
        if (handler == RepresentationHandler.PLAIN_DIRECT) {
            for (int offset = 0; offset < rows; offset++) {
                if (plan.predicate.matches((PlainChunk) chunk, offset)) result++;
            }
            return result;
        }
        if (handler == RepresentationHandler.ENCODED_NATIVE) {
            EncodedChunk encoded = (EncodedChunk) chunk;
            if (plan.singleRequiredLeaf == NO_REQUIRED_LEAF) {
                return (plan.predicate == null
                        || plan.predicate.matchesSingle(0L, NO_REQUIRED_LEAF))
                        ? rows : 0L;
            }
            if (plan.singleRequiredLeaf >= 0) {
                int leaf = plan.singleRequiredLeaf;
                IntegralChunkAccess access = encoded.borrowIntegral(
                        bound.layout.leafKind(leaf),
                        bound.layout.leafSlot(leaf));
                if (access != null && access.runEncoded()) {
                    int start = 0;
                    for (int run = 0;
                            run < access.runCount() && start < rows;
                            run++) {
                        int end = Math.min(access.runEnd(run), rows);
                        if (plan.predicate.matchesSingle(
                                access.runValue(run), leaf)) {
                            result = CheckedLong.add(
                                    result,
                                    end - start,
                                    bound.operation,
                                    bound.provenance);
                        }
                        start = end;
                    }
                    return result;
                }
                if (access != null) {
                    byte kind = bound.layout.leafKind(leaf);
                    for (int offset = 0; offset < rows; offset++) {
                        if (plan.predicate.matchesSingle(
                                plainValue(access, kind, offset), leaf)) {
                            result++;
                        }
                    }
                    return result;
                }
            } else if (plan.predicate.allPlainEncoded(encoded)) {
                for (int offset = 0; offset < rows; offset++) {
                    if (plan.predicate.matchesEncodedPlain(encoded, offset)) {
                        result++;
                    }
                }
                return result;
            }
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
            Decision decision,
            int ordinal,
            final Signed128Accumulator result) {
        KernelPlan plan = decision.kernel;
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        TableChunkDirectory directory = bound.root.directory;
        TableChunk chunk = directory.chunk(ordinal);
        int rows = logicalRows(bound, ordinal);
        RepresentationHandler handler = decision.handler(chunk);
        if (handler == RepresentationHandler.PLAIN_DIRECT) {
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
        if (handler == RepresentationHandler.ENCODED_NATIVE
                && sumEncoded(
                        bound,
                        plan,
                        (EncodedChunk) chunk,
                        rows,
                        result)) {
            return;
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

    private static boolean sumEncoded(
            BoundCanonicalRowOperation bound,
            KernelPlan plan,
            EncodedChunk chunk,
            int rows,
            Signed128Accumulator result) {
        IntegralChunkAccess projection = chunk.borrowIntegral(
                plan.projectionKind, plan.projectionSlot);
        if (projection == null) return false;
        if (plan.singleRequiredLeaf >= 0 && projection.runEncoded()) {
            if (plan.singleRequiredLeaf != plan.projectionLeaf) return false;
            int start = 0;
            for (int run = 0;
                    run < projection.runCount() && start < rows;
                    run++) {
                int end = Math.min(projection.runEnd(run), rows);
                long raw = projection.runValue(run);
                if (plan.predicate == null
                        || plan.predicate.matchesSingle(
                                raw, plan.singleRequiredLeaf)) {
                    result.addRepeated(raw, end - start);
                }
                start = end;
            }
            return true;
        }
        if (projection.runEncoded()
                || plan.predicate != null
                        && !plan.predicate.allPlainEncoded(chunk)) {
            return false;
        }
        if (plan.predicate == null) {
            addPlainIntegral(
                    result, projection, plan.projectionKind, rows);
            return true;
        }
        int predicateLeaf = plan.predicate.singleRequiredLeaf();
        if (predicateLeaf == NO_REQUIRED_LEAF) {
            if (plan.predicate.matchesSingle(0L, NO_REQUIRED_LEAF)) {
                addPlainIntegral(
                        result, projection, plan.projectionKind, rows);
            }
            return true;
        }
        if (predicateLeaf >= 0) {
            IntegralChunkAccess predicate = chunk.borrowIntegral(
                    bound.layout.leafKind(predicateLeaf),
                    bound.layout.leafSlot(predicateLeaf));
            if (predicate == null || predicate.runEncoded()) return false;
            byte predicateKind = bound.layout.leafKind(predicateLeaf);
            for (int offset = 0; offset < rows; offset++) {
                long tested = plainValue(predicate, predicateKind, offset);
                if (plan.predicate.matchesSingle(tested, predicateLeaf)) {
                    result.add(plainValue(
                            projection, plan.projectionKind, offset));
                }
            }
            return true;
        }
        for (int offset = 0; offset < rows; offset++) {
            if (plan.predicate.matchesEncodedPlain(chunk, offset)) {
                result.add(plainValue(
                        projection, plan.projectionKind, offset));
            }
        }
        return true;
    }

    private static void addPlainIntegral(
            Signed128Accumulator result,
            IntegralChunkAccess access,
            byte kind,
            int rows) {
        Object values = access.plainValues();
        switch (kind) {
            case GeneratedTableLayout.BYTE:
                result.addBytes((byte[]) values, rows);
                return;
            case GeneratedTableLayout.SHORT:
                result.addShorts((short[]) values, rows);
                return;
            case GeneratedTableLayout.CHAR:
                result.addChars((char[]) values, rows);
                return;
            case GeneratedTableLayout.INT:
                result.addInts((int[]) values, rows);
                return;
            case GeneratedTableLayout.LONG:
                result.addLongs((long[]) values, rows);
                return;
            default:
                throw new AssertionError("non-integral encoded values");
        }
    }

    private static long plainValue(
            IntegralChunkAccess access,
            byte kind,
            int offset) {
        if (access == null || access.runEncoded()) {
            throw new AssertionError("direct encoded integral values are missing");
        }
        Object values = access.plainValues();
        switch (kind) {
            case GeneratedTableLayout.BYTE: return ((byte[]) values)[offset];
            case GeneratedTableLayout.SHORT: return ((short[]) values)[offset];
            case GeneratedTableLayout.CHAR: return ((char[]) values)[offset];
            case GeneratedTableLayout.INT: return ((int[]) values)[offset];
            case GeneratedTableLayout.LONG: return ((long[]) values)[offset];
            default: throw new AssertionError("non-integral encoded values");
        }
    }

    private static int countLongChunk(
            CanonicalRowExecutionFrame frame,
            Decision decision,
            int ordinal) {
        long count = countChunk(frame, decision, ordinal);
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        return CheckedStructural.fromLong(
                count, bound.operation, bound.provenance);
    }

    private static int writeLongChunk(
            CanonicalRowExecutionFrame frame,
            Decision decision,
            int ordinal,
            long[] output,
            int position) {
        KernelPlan plan = decision.kernel;
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        TableChunkDirectory directory = bound.root.directory;
        TableChunk chunk = directory.chunk(ordinal);
        int rows = logicalRows(bound, ordinal);
        RepresentationHandler handler = decision.handler(chunk);
        if (handler == RepresentationHandler.PLAIN_DIRECT) {
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
        if (handler == RepresentationHandler.ENCODED_NATIVE) {
            return writeEncodedLongs(
                    plan, (EncodedChunk) chunk, rows, output, position);
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

    private static int writeEncodedLongs(
            KernelPlan plan,
            EncodedChunk chunk,
            int rows,
            long[] output,
            int position) {
        IntegralChunkAccess projection = chunk.borrowIntegral(
                GeneratedTableLayout.LONG, plan.projectionSlot);
        if (projection == null) {
            throw new AssertionError("encoded long projection is missing");
        }
        if (projection.runEncoded()) {
            int start = 0;
            for (int run = 0;
                    run < projection.runCount() && start < rows;
                    run++) {
                int end = Math.min(projection.runEnd(run), rows);
                long raw = projection.runValue(run);
                if (plan.predicate == null
                        || plan.predicate.matchesSingle(
                                raw, plan.singleRequiredLeaf)) {
                    Arrays.fill(output, position, position + end - start, raw);
                    position += end - start;
                }
                start = end;
            }
            return position;
        }
        long[] values = (long[]) projection.plainValues();
        if (plan.predicate == null) {
            System.arraycopy(values, 0, output, position, rows);
            return position + rows;
        }
        int predicateLeaf = plan.predicate.singleRequiredLeaf();
        if (predicateLeaf == NO_REQUIRED_LEAF) {
            if (plan.predicate.matchesSingle(0L, NO_REQUIRED_LEAF)) {
                System.arraycopy(values, 0, output, position, rows);
                return position + rows;
            }
            return position;
        }
        if (predicateLeaf >= 0) {
            IntegralChunkAccess predicate = chunk.borrowIntegral(
                    plan.predicate.kindForLeaf(predicateLeaf),
                    plan.predicate.slotForLeaf(predicateLeaf));
            if (predicate == null || predicate.runEncoded()) {
                throw new AssertionError("encoded predicate handler drift");
            }
            byte predicateKind = plan.predicate.kindForLeaf(predicateLeaf);
            for (int offset = 0; offset < rows; offset++) {
                if (plan.predicate.matchesSingle(
                        plainValue(predicate, predicateKind, offset),
                        predicateLeaf)) {
                    output[position++] = values[offset];
                }
            }
            return position;
        }
        for (int offset = 0; offset < rows; offset++) {
            if (plan.predicate.matchesEncodedPlain(chunk, offset)) {
                output[position++] = values[offset];
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

    enum RepresentationHandler {
        PLAIN_DIRECT,
        ENCODED_NATIVE,
        ENCODED_SCALAR,
        OVERLAY_SCALAR,
        UNAVAILABLE
    }

    /** Immutable physical decision; execution consumes it without re-planning. */
    static final class Decision {
        final Operation operation;
        final KernelPlan kernel;
        final boolean managesParallel;
        final boolean ownsTerminalScratch;
        final long temporaryBytes;

        Decision(
                Operation operation,
                KernelPlan kernel,
                boolean managesParallel,
                boolean ownsTerminalScratch,
                long temporaryBytes) {
            this.operation = operation;
            this.kernel = kernel;
            this.managesParallel = managesParallel;
            this.ownsTerminalScratch = ownsTerminalScratch;
            this.temporaryBytes = temporaryBytes;
        }

        RepresentationHandler handler(TableChunk chunk) {
            if (chunk instanceof PlainChunk) {
                return RepresentationHandler.PLAIN_DIRECT;
            }
            if (chunk instanceof EncodedChunk) {
                return kernel.encodedNative((EncodedChunk) chunk)
                        ? RepresentationHandler.ENCODED_NATIVE
                        : RepresentationHandler.ENCODED_SCALAR;
            }
            if (chunk instanceof OverlayChunk) {
                return RepresentationHandler.OVERLAY_SCALAR;
            }
            return RepresentationHandler.UNAVAILABLE;
        }
    }

    private static final class KernelPlan {
        final int projectionLeaf;
        final int projectionSlot;
        final byte projectionKind;
        final PredicateKernel predicate;
        final int singleRequiredLeaf;
        final int singleRequiredSlot;
        final byte singleRequiredKind;

        KernelPlan(
                int projectionLeaf,
                int projectionSlot,
                byte projectionKind,
                PredicateKernel predicate) {
            this.projectionLeaf = projectionLeaf;
            this.projectionSlot = projectionSlot;
            this.projectionKind = projectionKind;
            this.predicate = predicate;
            int predicateLeaf = predicate == null
                    ? NO_REQUIRED_LEAF : predicate.singleRequiredLeaf();
            this.singleRequiredLeaf = combineRequiredLeaves(
                    projectionLeaf, predicateLeaf);
            if (singleRequiredLeaf >= 0) {
                this.singleRequiredSlot = projectionLeaf == singleRequiredLeaf
                        ? projectionSlot
                        : predicate.slotForLeaf(singleRequiredLeaf);
                this.singleRequiredKind = projectionLeaf == singleRequiredLeaf
                        ? projectionKind
                        : predicate.kindForLeaf(singleRequiredLeaf);
            } else {
                this.singleRequiredSlot = -1;
                this.singleRequiredKind = (byte) -1;
            }
        }

        boolean matches(PlainChunk chunk, int offset) {
            return predicate == null || predicate.matches(chunk, offset);
        }

        boolean encodedNative(EncodedChunk chunk) {
            if (singleRequiredLeaf == NO_REQUIRED_LEAF) return true;
            if (singleRequiredLeaf >= 0) {
                IntegralChunkAccess access = chunk.borrowIntegral(
                        singleRequiredKind, singleRequiredSlot);
                return access != null;
            }
            if (projectionLeaf >= 0) {
                IntegralChunkAccess projection = chunk.borrowIntegral(
                        projectionKind, projectionSlot);
                if (projection == null || projection.runEncoded()) return false;
            }
            return predicate == null || predicate.allPlainEncoded(chunk);
        }

        private static int combineRequiredLeaves(int left, int right) {
            if (left == MULTIPLE_REQUIRED_LEAVES
                    || right == MULTIPLE_REQUIRED_LEAVES) {
                return MULTIPLE_REQUIRED_LEAVES;
            }
            if (left == NO_REQUIRED_LEAF) return right;
            if (right == NO_REQUIRED_LEAF || left == right) return left;
            return MULTIPLE_REQUIRED_LEAVES;
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

        int singleRequiredLeaf() {
            switch (kind) {
                case CONSTANT: return NO_REQUIRED_LEAF;
                case AND:
                case OR:
                    return KernelPlan.combineRequiredLeaves(
                            left.singleRequiredLeaf(),
                            right.singleRequiredLeaf());
                case NOT: return left.singleRequiredLeaf();
                default: return leaf;
            }
        }

        int slotForLeaf(int requiredLeaf) {
            if (leaf == requiredLeaf) return slot;
            if (left != null) {
                int found = left.slotForLeaf(requiredLeaf);
                if (found >= 0) return found;
            }
            return right == null ? -1 : right.slotForLeaf(requiredLeaf);
        }

        byte kindForLeaf(int requiredLeaf) {
            if (leaf == requiredLeaf) return leafKind;
            if (left != null) {
                byte found = left.kindForLeaf(requiredLeaf);
                if (found >= 0) return found;
            }
            return right == null ? (byte) -1
                    : right.kindForLeaf(requiredLeaf);
        }

        boolean allPlainEncoded(EncodedChunk chunk) {
            switch (kind) {
                case CONSTANT: return true;
                case AND:
                case OR:
                    return left.allPlainEncoded(chunk)
                            && right.allPlainEncoded(chunk);
                case NOT: return left.allPlainEncoded(chunk);
                default:
                    IntegralChunkAccess access = chunk.borrowIntegral(
                            leafKind, slot);
                    return access != null && !access.runEncoded();
            }
        }

        boolean matchesEncodedPlain(EncodedChunk chunk, int offset) {
            switch (kind) {
                case CONSTANT: return source.constant;
                case AND: return left.matchesEncodedPlain(chunk, offset)
                        && right.matchesEncodedPlain(chunk, offset);
                case OR: return left.matchesEncodedPlain(chunk, offset)
                        || right.matchesEncodedPlain(chunk, offset);
                case NOT: return !left.matchesEncodedPlain(chunk, offset);
                default:
                    return matchesSingle(
                            plainValue(
                                    chunk.borrowIntegral(leafKind, slot),
                                    leafKind,
                                    offset),
                            leaf);
            }
        }

        boolean matchesSingle(long raw, int requiredLeaf) {
            switch (kind) {
                case CONSTANT: return source.constant;
                case AND: return left.matchesSingle(raw, requiredLeaf)
                        && right.matchesSingle(raw, requiredLeaf);
                case OR: return left.matchesSingle(raw, requiredLeaf)
                        || right.matchesSingle(raw, requiredLeaf);
                case NOT: return !left.matchesSingle(raw, requiredLeaf);
                case EQ: return compare(raw, requiredLeaf, source.lower) == 0;
                case NE: return compare(raw, requiredLeaf, source.lower) != 0;
                case LT: return compare(raw, requiredLeaf, source.lower) < 0;
                case LE: return compare(raw, requiredLeaf, source.lower) <= 0;
                case GT: return compare(raw, requiredLeaf, source.lower) > 0;
                case GE: return compare(raw, requiredLeaf, source.lower) >= 0;
                case BETWEEN:
                    return compare(raw, requiredLeaf, source.lower) >= 0
                            && compare(raw, requiredLeaf, source.upper) <= 0;
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

        private int compare(
                long raw,
                int requiredLeaf,
                TypedLiteral literal) {
            if (leaf != requiredLeaf) {
                throw new AssertionError("single-leaf predicate binding mismatch");
            }
            long expected;
            switch (leafKind) {
                case GeneratedTableLayout.BYTE:
                    expected = literal.byteValue(leaf);
                    break;
                case GeneratedTableLayout.SHORT:
                    expected = literal.shortValue(leaf);
                    break;
                case GeneratedTableLayout.CHAR:
                    expected = literal.charValue(leaf);
                    break;
                case GeneratedTableLayout.INT:
                    expected = literal.intValue(leaf);
                    break;
                case GeneratedTableLayout.LONG:
                    expected = literal.longValue(leaf);
                    break;
                default:
                    throw new AssertionError("non-integral vector predicate");
            }
            return Long.compare(raw, expected);
        }
    }

    private static final int NO_REQUIRED_LEAF = -1;
    private static final int MULTIPLE_REQUIRED_LEAVES = -2;
}
