package io.github.somaruntime.soma.internal;

import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded caller-participating refinement of one admitted Canonical PhysicalPlan. */
final class CanonicalParallelRowScheduler {

    private CanonicalParallelRowScheduler() {
    }

    static void prepare(CanonicalRowExecutionFrame frame) {
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        if (bound.canonical.request.mode != ExecutionRequest.Mode.PARALLEL) return;
        if (frame.plan.partitions <= 1
                || frame.plan.parallelPrefixStages == 0
                || bound.root.size == 0) {
            CanonicalParallelWorkScheduler.validate(bound);
            return;
        }

        final Range[] work = ranges(frame, frame.plan.partitions);
        CanonicalParallelWorkScheduler.execute(
                bound, work.length, new CanonicalParallelWorkScheduler.Work() {
            @Override public void run(
                    int ordinal,
                    AtomicBoolean cancelled) {
                work[ordinal].run(cancelled);
            }
        });
        frame.parallelSource = merge(work, bound);
    }

    private static Range[] ranges(
            CanonicalRowExecutionFrame frame,
            int count) {
        Range[] result = new Range[count];
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        int chunkRows = bound.root.directory.chunkRows();
        int chunks = CheckedStructural.ceilChunks(bound.root.size, chunkRows);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            int firstChunk = (int) ((long) chunks * ordinal / count);
            int nextChunk = (int) ((long) chunks * (ordinal + 1) / count);
            int from = firstChunk * chunkRows;
            int to = (int) Math.min(
                    (long) bound.root.size, (long) nextChunk * chunkRows);
            result[ordinal] = new Range(frame, from, to);
        }
        return result;
    }

    private static IntLocatorBuffer merge(
            Range[] work,
            BoundCanonicalRowOperation bound) {
        IntLocatorBuffer result = new IntLocatorBuffer(
                bound.root.size, bound.operation, bound.provenance);
        for (Range range : work) {
            for (int index = 0; index < range.output.size(); index++) {
                result.add(range.output.get(index));
            }
        }
        return result;
    }

    private static final class Range {
        private final CanonicalRowExecutionFrame frame;
        private final int from;
        private final int to;
        private final IntLocatorBuffer output;

        Range(CanonicalRowExecutionFrame frame, int from, int to) {
            this.frame = frame;
            this.from = from;
            this.to = to;
            BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
            this.output = new IntLocatorBuffer(
                    to - from, bound.operation, bound.provenance);
        }

        void run(AtomicBoolean cancelled) {
            for (int locator = from; locator < to && !cancelled.get(); locator++) {
                if (matchesPrefix(locator)) output.add(locator);
            }
        }

        private boolean matchesPrefix(int locator) {
            BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
            for (int index = 0; index < frame.plan.parallelPrefixStages; index++) {
                CanonicalRowStage stage = frame.plan.normalized.stages.get(index);
                if (!OptimizedPredicateEvaluator.matches(
                        bound.layout,
                        stage.predicate,
                        bound.root,
                        locator,
                        frame.membership)) return false;
            }
            return true;
        }
    }
}
