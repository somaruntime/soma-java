package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

/**
 * Invocation-local primitive build-side filter.
 *
 * <p>False means the probe cannot match. True is only a candidate and always
 * flows through the authoritative hash chain and full key equality.</p>
 */
abstract class JoinRuntimeFilter {
    private static final String NONE = "none";
    private static final String MINMAX = "minmax";
    private static final String BLOOM = "bloom";

    static JoinRuntimeFilter build(
            ExecutionFrame frame,
            CandidateSelection probe,
            CandidateSelection build,
            KeyExpression<?> buildKey,
            DataFlowBinding buildBinding) {
        if (!buildKey.singleLongCarrier()
                || build.size == 0
                || probe.size < 4096
                || (long) build.size * 4L
                >= (long) probe.size * 3L) {
            return null;
        }

        long minimum = Long.MAX_VALUE;
        long maximum = Long.MIN_VALUE;
        for (int position = 0; position < build.size; position++) {
            long value = buildKey.singleLongValue(
                    frame, buildBinding, build.indexAt(position));
            if (value < minimum) minimum = value;
            if (value > maximum) maximum = value;
        }

        String strategy = strategyFor(
                probe.size, build.size, minimum, maximum);
        if (MINMAX.equals(strategy)) {
            return new MinMaxJoinRuntimeFilter(minimum, maximum);
        }
        if (!BLOOM.equals(strategy)) {
            return null;
        }

        int bitCapacity = bloomBitCapacity(build.size);
        long[] words = frame.newScratchLongs(
                bitCapacity >>> 6, "dataflow.join.runtime-filter");
        BloomJoinRuntimeFilter filter =
                new BloomJoinRuntimeFilter(words, bitCapacity - 1);
        for (int position = 0; position < build.size; position++) {
            filter.add(buildKey.singleLongValue(
                    frame, buildBinding, build.indexAt(position)));
        }
        return filter;
    }

    static String strategyFor(
            int probeSize,
            int buildSize,
            long minimum,
            long maximum) {
        if (probeSize < 4096 || buildSize <= 0) {
            return NONE;
        }
        long span = maximum >= minimum
                && maximum - minimum >= 0L
                ? maximum - minimum : Long.MAX_VALUE;
        long narrowBound = (long) buildSize * 8L;
        if ((long) buildSize * 4L < (long) probeSize * 3L
                && span <= narrowBound) {
            return MINMAX;
        }

        if ((long) buildSize * 4L >= (long) probeSize
                || buildSize < 64) {
            return NONE;
        }
        return BLOOM;
    }

    abstract boolean mightContain(long value);

    private static int bloomBitCapacity(int entries) {
        long requested = Math.max(1024L, (long) entries * 8L);
        int capacity = 1024;
        while ((long) capacity < requested && capacity < (1 << 30)) {
            capacity <<= 1;
        }
        return capacity;
    }
}

final class MinMaxJoinRuntimeFilter extends JoinRuntimeFilter {
    private final long minimum;
    private final long maximum;

    MinMaxJoinRuntimeFilter(long minimum, long maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    @Override
    boolean mightContain(long value) {
        return value >= minimum && value <= maximum;
    }

}

final class BloomJoinRuntimeFilter extends JoinRuntimeFilter {
    private final long[] words;
    private final int mask;

    BloomJoinRuntimeFilter(long[] words, int mask) {
        this.words = words;
        this.mask = mask;
    }

    void add(long value) {
        long mixed = mix64(value);
        set((int) mixed & mask);
        set((int) (mixed >>> 32) & mask);
    }

    @Override
    boolean mightContain(long value) {
        long mixed = mix64(value);
        return get((int) mixed & mask)
                && get((int) (mixed >>> 32) & mask);
    }

    private void set(int bit) {
        words[bit >>> 6] |= 1L << (bit & 63);
    }

    private boolean get(int bit) {
        return (words[bit >>> 6] & (1L << (bit & 63))) != 0L;
    }

    private static long mix64(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        return value ^ (value >>> 33);
    }
}
