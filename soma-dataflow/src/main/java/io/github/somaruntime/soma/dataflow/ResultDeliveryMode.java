package io.github.somaruntime.soma.dataflow;

/** Closed result-delivery capability selected by a DataFlow terminal. */
public enum ResultDeliveryMode {
    EAGER_DETACHED,
    CALLBACK_SCOPED
}
