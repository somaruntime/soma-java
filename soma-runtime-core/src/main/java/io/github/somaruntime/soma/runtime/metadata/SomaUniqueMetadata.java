package io.github.somaruntime.soma.runtime.metadata;

import java.util.List;

/** Immutable secondary-unique descriptor。 */
public interface SomaUniqueMetadata {
    String name();
    List<String> leafPaths();
}
