package com.hgtech.soma.runtime.metadata;

import java.util.List;

/** Immutable primary-key descriptor。 */
public interface SomaKeyMetadata {
    List<String> leafPaths();
}
