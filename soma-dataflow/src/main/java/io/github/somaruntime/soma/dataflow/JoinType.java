package io.github.somaruntime.soma.dataflow;

/** Supported left-driven equi Join preservation semantics. */
public enum JoinType {
    INNER,
    LEFT_OUTER,
    LEFT_SEMI,
    LEFT_ANTI
}
