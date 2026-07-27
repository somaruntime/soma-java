package com.hgtech.soma.runtime.metadata;

import java.util.List;

/** Immutable table descriptor；不包含 live rows、capacity 或 runtime counters。 */
public interface SomaTableMetadata {
    String logicalName();
    String carrierType();
    SomaTableKind kind();
    int defaultCapacity();
    List<SomaColumnMetadata> columns();
    SomaColumnMetadata requireColumn(String logicalPath);
    SomaKeyMetadata key();
    List<SomaUniqueMetadata> uniques();
    SomaUniqueMetadata requireUnique(String name);
    List<SomaIndexMetadata> indexes();
    SomaIndexMetadata requireIndex(String name);
    List<SomaOwnershipMetadata> ownership();
    SomaOwnershipMetadata requireOwnership(String logicalPath);
}
