package io.github.somaruntime.soma.runtime.metadata;

/** Descriptor 是完整 Metadata control plane 的 immutable schema 子项。 */
public interface SomaDescriptor {
    SomaSchemaMetadata schema();
}
