package io.github.somaruntime.soma.runtime.metadata;

/** V1 封闭 schema/storage kind。 */
public enum SomaTypeKind {
    PRIMITIVE_BACKED_SCALAR,
    REFERENCE_BACKED_IMMUTABLE_SCALAR,
    COMPILER_FLATTENED_VALUE,
    OWNED_STRUCTURED_STATE
}
