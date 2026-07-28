package com.hgtech.soma.runtime.metadata;

/** Detached topology snapshot for one Table storage publication unit。 */
public interface SomaSegmentMetadata {
    int ordinal();
    SomaSegmentKind kind();
    int startRowInclusive();
    int endRowExclusive();
    int capacityRows();
    int liveRows();
}
