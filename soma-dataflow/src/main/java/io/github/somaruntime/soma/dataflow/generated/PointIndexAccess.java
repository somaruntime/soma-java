package io.github.somaruntime.soma.dataflow.generated;

/** Generated zero-or-one locator used only while an Invocation owns the guard. */
public interface PointIndexAccess<B extends DataFlowBinding> {
    int index(B binding);

    String identity();
}
