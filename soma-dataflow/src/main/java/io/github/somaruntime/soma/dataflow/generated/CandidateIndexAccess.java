package io.github.somaruntime.soma.dataflow.generated;

/**
 * Narrow generated exact-candidate access protocol.
 *
 * <p>The logical access object is immutable and detached from a live Table.
 * Generated bindings resolve the current exact group only while an invocation
 * owns the source aggregate guard. This is not an application SPI.</p>
 */
public interface CandidateIndexAccess<B extends DataFlowBinding> {
    int group(B binding);

    int size(B binding, int group);

    int first(B binding, int group);

    int next(B binding, int currentIndex);

    default boolean bitmap(B binding, int group) {
        return false;
    }

    default int bitmapWordCount(B binding, int group) {
        return 0;
    }

    default long bitmapWord(B binding, int group, int word) {
        throw new IllegalStateException(
                "candidate exact access is not bitmap-backed");
    }

    String identity();
}
