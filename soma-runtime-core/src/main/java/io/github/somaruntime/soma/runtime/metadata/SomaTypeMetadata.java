package io.github.somaruntime.soma.runtime.metadata;

/** Immutable descriptor projection of one schema type/leaf representation。 */
public interface SomaTypeMetadata {
    SomaTypeKind kind();
    String logicalType();
    String javaType();
    String storageType();
    String semantic();
}
