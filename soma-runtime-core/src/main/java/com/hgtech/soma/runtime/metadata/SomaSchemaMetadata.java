package com.hgtech.soma.runtime.metadata;

import java.util.List;

/** Immutable schema descriptor selected by one generated SchemaMetadata companion。 */
public interface SomaSchemaMetadata {
    String logicalName();
    String sourcePackage();
    String generatedPackage();
    String version();
    String schemaHash();
    List<SomaTableMetadata> tables();
    SomaTableMetadata requireTable(String logicalName);
}
