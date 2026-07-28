package io.github.somaruntime.soma.runtime.metadata;

/** Runtime physical binding for one declared non-unique exact index。 */
public interface SomaIndexRuntimeMetadata
        extends SomaExactAccessRuntimeMetadata {
    SomaIndexMetadata descriptor();
}
