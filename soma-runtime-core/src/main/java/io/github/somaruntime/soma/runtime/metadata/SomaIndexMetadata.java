package io.github.somaruntime.soma.runtime.metadata;

import java.util.List;

/** Immutable secondary non-unique exact-index descriptor。 */
public interface SomaIndexMetadata {
    String name();
    List<String> leafPaths();
}
