package io.github.somaruntime.soma.runtime.metadata;

/** Runtime physical binding for one declared unique exact access path。 */
public interface SomaUniqueRuntimeMetadata
        extends SomaExactAccessRuntimeMetadata {
    SomaUniqueMetadata descriptor();
}
