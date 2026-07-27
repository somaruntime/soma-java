package com.hgtech.soma.runtime.metadata;

/** Immutable descriptor projection of one authoritative physical leaf column。 */
public interface SomaColumnMetadata {
    String javaPath();
    String logicalPath();
    SomaTypeMetadata type();
    boolean optional();
    boolean hasDefault();
    /** Canonical normalized schema default, or {@code null} when absent. */
    String normalizedDefault();
    SomaColumnRole role();
}
