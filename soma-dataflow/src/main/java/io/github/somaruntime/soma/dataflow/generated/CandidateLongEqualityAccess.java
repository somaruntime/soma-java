package io.github.somaruntime.soma.dataflow.generated;

/**
 * Narrow generated bridge from a logical long carrier equality to an exact
 * candidate access.
 *
 * <p>Only generated schema companions implement this protocol. Application
 * code receives logical expressions and never handles carrier-bound access
 * objects directly.</p>
 */
public interface CandidateLongEqualityAccess<B extends DataFlowBinding> {
    CandidateIndexAccess<B> equalTo(long value);

    String identity();
}
