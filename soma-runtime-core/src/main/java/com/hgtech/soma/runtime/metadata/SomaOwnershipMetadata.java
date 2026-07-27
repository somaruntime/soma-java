package com.hgtech.soma.runtime.metadata;

/** Immutable parent-owned child edge descriptor。 */
public interface SomaOwnershipMetadata {
    String javaPath();
    String logicalPath();
    String containerKind();
    String childTable();
    SomaTypeMetadata type();
    boolean optional();
}
