package io.github.somaruntime.soma.processor;

import java.util.ArrayList;
import java.util.List;

import static io.github.somaruntime.soma.processor.SomaSchemaModel.ChildFieldType;
import static io.github.somaruntime.soma.processor.SomaSchemaModel.DefaultModel;
import static io.github.somaruntime.soma.processor.SomaSchemaModel.SchemaModel;
import static io.github.somaruntime.soma.processor.SomaSchemaModel.SchemaStorageKind;
import static io.github.somaruntime.soma.processor.SomaSchemaModel.SelectorModel;
import static io.github.somaruntime.soma.processor.SomaSchemaModel.TableFieldModel;
import static io.github.somaruntime.soma.processor.SomaSchemaModel.TableModel;
import static io.github.somaruntime.soma.processor.SomaSchemaModel.ValueLeafType;

/** Emits the one schema-scoped immutable Descriptor Metadata and plan entry。 */
final class DenseMetadataSourceEmitter {
    private final SchemaModel schema;
    private final String schemaHash;

    DenseMetadataSourceEmitter(SchemaModel schema, String schemaHash) {
        this.schema = schema;
        this.schemaHash = schemaHash;
    }

    String source() {
        SourceBuilder out = new SourceBuilder(
                "// SOMA-GENERATED: soma-processor-v1\npackage "
                        + schema.generatedPackage + ";\n\n");
        out.append("import io.github.somaruntime.soma.runtime.*;\n")
                .append("import io.github.somaruntime.soma.runtime.generated.*;\n")
                .append("import io.github.somaruntime.soma.runtime.metadata.*;\n")
                .append("import java.util.*;\n\n")
                .append("public final class SchemaMetadata {\n")
                .append("  private static final GeneratedMetadata GENERATED=new GeneratedMetadata(")
                .append(q(schemaHash))
                .append(",RuntimeCompatibility.GENERATED_TARGET,RuntimeCompatibility.COMPILER_IDENTITY,")
                .append("RuntimeCompatibility.GENERATED_PROTOCOL,RuntimeCompatibility.RUNTIME_COMPATIBILITY,")
                .append("RuntimeCompatibility.PLAN_PROTOCOL,RuntimeCompatibility.DENSE_ALGORITHM,")
                .append("RuntimeCompatibility.ALLOCATION_ESTIMATOR);\n")
                .append("  private static final SomaMetadata METADATA=createMetadata();\n");
        if (!schema.tables.isEmpty()) {
            out.append("  private static final RuntimePlan DEFAULT_RUNTIME_PLAN=createDefaultRuntimePlan();\n");
        }
        out
                .append("  private SchemaMetadata(){}\n")
                .append("  static GeneratedMetadata generatedMetadata(){return GENERATED;}\n")
                .append("  public static SomaMetadata metadata(){return METADATA;}\n")
                .append("  public static SomaSchemaMetadata schema(){return METADATA.descriptor().schema();}\n");
        if (!schema.tables.isEmpty()) {
            out.append("  public static RuntimePlan defaultRuntimePlan(){return DEFAULT_RUNTIME_PLAN;}\n")
                    .append("  public static RuntimePlan.Builder newPlan(){return DEFAULT_RUNTIME_PLAN.toBuilder();}\n");
        }
        appendMetadata(out);
        if (!schema.tables.isEmpty()) {
            appendDefaultPlan(out);
        }
        appendMetadataImplementations(out);
        return out.append("}\n").toString();
    }

    private void appendMetadata(SourceBuilder out) {
        out.append("  private static SomaMetadata createMetadata(){return metadata(")
                .append(q(schema.name)).append(',')
                .append(q(schema.sourcePackage)).append(',')
                .append(q(schema.generatedPackage)).append(',')
                .append(q(schema.version));
        for (TableModel table : schema.tables.values()) {
            out.append(",table").append(table.simpleName).append("()");
        }
        out.append(");}\n");
        for (TableModel table : schema.tables.values()) {
            appendTableMetadata(out, table);
        }
    }

    private void appendTableMetadata(SourceBuilder out, TableModel table) {
        out.append("  private static SomaTableMetadata table")
                .append(table.simpleName).append("(){return table(")
                .append(q(table.logicalName)).append(',')
                .append(q(table.javaType)).append(',')
                .append(table.hasKey()
                        ? "SomaTableKind.KEYED" : "SomaTableKind.DENSE")
                .append(',').append(table.defaultCapacity < 0
                        ? 16 : table.defaultCapacity)
                .append(",new SomaColumnMetadata[]{");
        boolean first = true;
        for (TableFieldModel field : table.fields) {
            if (field.child != null) {
                continue;
            }
            if (field.type.valueJavaType == null) {
                if (!first) out.append(',');
                appendColumn(
                        out,
                        field.javaName,
                        field.logicalName,
                        field.type.logicalType,
                        field.type.publicType,
                        field.type.storagePrimitiveName,
                        field.semantic,
                        field.type.storageKind().metadataConstant(),
                        field.optional,
                        field.defaultValue,
                        field.key);
                first = false;
            } else {
                for (ValueLeafType leaf : field.type.valueLeaves) {
                    if (!first) out.append(',');
                    appendColumn(
                            out,
                            field.javaName + "." + leaf.javaName,
                            field.logicalName + "." + leaf.logicalName,
                            field.type.logicalType,
                            leaf.publicPrimitiveName,
                            leaf.storagePrimitiveName,
                            leaf.semantic,
                            SchemaStorageKind.COMPILER_FLATTENED_VALUE
                                    .metadataConstant(),
                            field.optional,
                            leaf.defaultValue,
                            field.key);
                    first = false;
                }
            }
        }
        out.append("},");
        appendKey(out, table);
        out.append(",new SomaUniqueMetadata[]{");
        appendSelectors(out, table.selectors, "unique");
        out.append("},new SomaIndexMetadata[]{");
        appendSelectors(out, table.selectors, "index");
        out.append("},new SomaOwnershipMetadata[]{");
        first = true;
        for (TableFieldModel field : table.fields) {
            if (field.child == null) continue;
            if (!first) out.append(',');
            ChildFieldType child = field.child;
            out.append("ownership(")
                    .append(q(field.javaName)).append(',')
                    .append(q(field.logicalName)).append(',')
                    .append(q(child.container)).append(',')
                    .append(q(child.tableLogicalName)).append(',')
                    .append("type(")
                    .append(SchemaStorageKind.OWNED_STRUCTURED_STATE
                            .metadataConstant()).append(',')
                    .append(q("owned:" + child.container)).append(',')
                    .append(q(child.materializedType)).append(',')
                    .append(q("owned-table-handle")).append(',')
                    .append(q("NONE")).append("),")
                    .append(field.optional).append(')');
            first = false;
        }
        out.append("});}\n");
    }

    private void appendColumn(
            SourceBuilder out,
            String javaPath,
            String logicalPath,
            String logicalType,
            String javaType,
            String storageType,
            String semantic,
            String kind,
            boolean optional,
            DefaultModel defaultValue,
            boolean key) {
        out.append("column(")
                .append(q(javaPath)).append(',')
                .append(q(logicalPath)).append(',')
                .append("type(").append(kind).append(',')
                .append(q(logicalType)).append(',')
                .append(q(javaType)).append(',')
                .append(q(storageType)).append(',')
                .append(q(semantic)).append("),")
                .append(optional).append(',')
                .append(defaultValue == null
                        ? "null" : q(defaultValue.normalized))
                .append(',')
                .append(key
                        ? "SomaColumnRole.KEY" : "SomaColumnRole.FIELD")
                .append(')');
    }

    private void appendKey(SourceBuilder out, TableModel table) {
        if (!table.hasKey()) {
            out.append("null");
            return;
        }
        out.append("key(");
        TableFieldModel key = null;
        for (TableFieldModel field : table.fields) {
            if (field.key) {
                key = field;
                break;
            }
        }
        if (key.type.valueJavaType == null) {
            out.append(q(key.logicalName));
        } else {
            for (int index = 0; index < key.type.valueLeaves.size(); index++) {
                if (index > 0) out.append(',');
                out.append(q(key.logicalName + "."
                        + key.type.valueLeaves.get(index).logicalName));
            }
        }
        out.append(')');
    }

    private void appendSelectors(
            SourceBuilder out, List<SelectorModel> selectors, String kind) {
        boolean first = true;
        for (SelectorModel selector : selectors) {
            if (!kind.equals(selector.kind)) continue;
            if (!first) out.append(',');
            out.append(kind).append('(')
                    .append(q(selector.name));
            for (SomaSchemaModel.SelectorLeafModel leaf : selector.leaves) {
                out.append(',').append(q(leaf.path));
            }
            out.append(')');
            first = false;
        }
    }

    private void appendDefaultPlan(SourceBuilder out) {
        out.append("  private static RuntimePlan createDefaultRuntimePlan(){")
                .append("RuntimePlan.Builder builder=GeneratedRuntimePlan.builder(")
                .append(q(schemaHash))
                .append(",RuntimeCompatibility.RUNTIME_COMPATIBILITY,")
                .append("RuntimeCompatibility.GENERATED_PROTOCOL,")
                .append("RuntimeCompatibility.PLAN_PROTOCOL,")
                .append("RuntimeCompatibility.ALLOCATION_ESTIMATOR);");
        for (TableModel table : schema.tables.values()) {
            DenseTableCodegenModel.TableSpec spec = table.toGeneratorSpec();
            int initialCapacity = spec.defaultCapacity < 0
                    ? 16 : spec.defaultCapacity;
            out.append("GeneratedRuntimePlan.addTable(builder,GeneratedRuntimePlan.table(")
                    .append(q(spec.logicalName))
                    .append(",RuntimeCompatibility.DENSE_ALGORITHM,")
                    .append(initialCapacity).append(',')
                    .append(initialCapacity).append(',')
                    .append(defaultMaximumRows(spec))
                    .append(",3,2,268435456L,268435456L,268435456L,268435456L,")
                    .append(q(spec.keyed()
                            ? spec.keyField().keySpaceImplementation() : "none"))
                    .append(',')
                    .append("RuntimeCompatibility.PRIMARY_LOCATOR_LAYOUT_FORMULA,")
                    .append(spec.selectors.isEmpty()
                            ? q("none")
                            : "RuntimeCompatibility.PRIMITIVE_EXACT_HASH")
                    .append(',')
                    .append("RuntimeCompatibility.STORAGE_LAYOUT_FORMULA,")
                    .append(structuralBytesPerRow(spec)).append(',')
                    .append(hasString(spec))
                    .append(",StringResourceProfile.unprofiled()));");
        }
        for (TableModel owner : schema.tables.values()) {
            for (TableFieldModel field : owner.fields) {
                if (field.child == null) continue;
                TableModel child = schema.tableByLogicalName(
                        field.child.tableLogicalName);
                int capacity = field.child.initialCapacity > 0
                        ? field.child.initialCapacity
                        : (child.defaultCapacity < 0 ? 16 : child.defaultCapacity);
                out.append("GeneratedRuntimePlan.addChild(builder,ChildPlan.create(")
                        .append(q(owner.logicalName)).append(',')
                        .append(q(field.logicalName)).append(',')
                        .append(q(field.child.tableLogicalName)).append(',')
                        .append(capacity).append("));");
            }
        }
        out.append("return builder.build();}\n");
    }

    private static int defaultMaximumRows(
            DenseTableCodegenModel.TableSpec table) {
        long bytesPerRow = 0L;
        for (DenseTableCodegenModel.FieldSpec field : table.fields) {
            if (field.flattenedValueStorage()) {
                for (DenseTableCodegenModel.ValueLeafSpec leaf
                        : field.valueLeaves) {
                    bytesPerRow += leaf.bytes();
                }
            } else {
                bytesPerRow += field.bytes();
            }
            if (field.optional) bytesPerRow++;
        }
        if (table.keyed()) {
            bytesPerRow += "hash-int-v2".equals(
                    table.keyField().keySpaceImplementation()) ? 18L : 26L;
        }
        bytesPerRow += 32L * (long) table.selectors.size();
        bytesPerRow = Math.max(1L, bytesPerRow);
        long rows = 268435456L / bytesPerRow;
        rows = Math.max(
                table.defaultCapacity < 0
                        ? 16L : (long) table.defaultCapacity,
                rows);
        return rows > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rows;
    }

    private static boolean hasString(
            DenseTableCodegenModel.TableSpec table) {
        for (DenseTableCodegenModel.FieldSpec field : table.fields) {
            if ("java.lang.String".equals(field.storagePrimitive)) {
                return true;
            }
            for (DenseTableCodegenModel.ValueLeafSpec leaf
                    : field.valueLeaves) {
                if ("java.lang.String".equals(leaf.storagePrimitive)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int structuralBytesPerRow(
            DenseTableCodegenModel.TableSpec table) {
        long bytes = 0L;
        for (DenseTableCodegenModel.FieldSpec field : table.fields) {
            if (field.flattenedValueStorage()) {
                for (DenseTableCodegenModel.ValueLeafSpec leaf
                        : field.valueLeaves) {
                    bytes += leaf.bytes();
                }
            } else {
                bytes += field.bytes();
            }
            if (field.optional) bytes++;
        }
        if (!table.children.isEmpty()) {
            bytes += 8L;
            for (DenseTableCodegenModel.ChildSpec child : table.children) {
                bytes += 8L;
                if (child.optional) bytes++;
            }
        }
        if (bytes <= 0L || bytes > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "invalid generated structural row width");
        }
        return (int) bytes;
    }

    private void appendMetadataImplementations(SourceBuilder out) {
        out.append("  private static SomaTypeMetadata type(SomaTypeKind kind,String logicalType,String javaType,String storageType,String semantic){return new TypeMetadata(kind,logicalType,javaType,storageType,semantic);}\n")
                .append("  private static SomaColumnMetadata column(String javaPath,String logicalPath,SomaTypeMetadata type,boolean optional,String normalizedDefault,SomaColumnRole role){return new ColumnMetadata(javaPath,logicalPath,type,optional,normalizedDefault,role);}\n")
                .append("  private static SomaKeyMetadata key(String... leafPaths){return new KeyMetadata(strings(leafPaths));}\n")
                .append("  private static SomaUniqueMetadata unique(String name,String... leafPaths){return new UniqueMetadata(name,strings(leafPaths));}\n")
                .append("  private static SomaIndexMetadata index(String name,String... leafPaths){return new IndexMetadata(name,strings(leafPaths));}\n")
                .append("  private static SomaOwnershipMetadata ownership(String javaPath,String logicalPath,String containerKind,String childTable,SomaTypeMetadata type,boolean optional){return new OwnershipMetadata(javaPath,logicalPath,containerKind,childTable,type,optional);}\n")
                .append("  private static SomaTableMetadata table(String logicalName,String carrierType,SomaTableKind kind,int defaultCapacity,SomaColumnMetadata[] columns,SomaKeyMetadata key,SomaUniqueMetadata[] uniques,SomaIndexMetadata[] indexes,SomaOwnershipMetadata[] ownership){return new TableMetadata(logicalName,carrierType,kind,defaultCapacity,immutable(columns),key,immutable(uniques),immutable(indexes),immutable(ownership));}\n")
                .append("  private static SomaMetadata metadata(String logicalName,String sourcePackage,String generatedPackage,String version,SomaTableMetadata... tables){return new RootMetadata(new DescriptorMetadata(new SchemaDescriptor(logicalName,sourcePackage,generatedPackage,version,GENERATED.schemaHash(),immutable(tables))));}\n")
                .append("  private static <T> List<T> immutable(T[] values){return Collections.unmodifiableList(new ArrayList<T>(Arrays.asList(values)));}\n")
                .append("  private static List<String> strings(String[] values){return immutable(values);}\n")
                .append("  private static final class TypeMetadata implements SomaTypeMetadata{private final SomaTypeKind kind;private final String logicalType;private final String javaType;private final String storageType;private final String semantic;private TypeMetadata(SomaTypeKind kind,String logicalType,String javaType,String storageType,String semantic){this.kind=kind;this.logicalType=logicalType;this.javaType=javaType;this.storageType=storageType;this.semantic=semantic;}public SomaTypeKind kind(){return kind;}public String logicalType(){return logicalType;}public String javaType(){return javaType;}public String storageType(){return storageType;}public String semantic(){return semantic;}}\n")
                .append("  private static final class ColumnMetadata implements SomaColumnMetadata{private final String javaPath;private final String logicalPath;private final SomaTypeMetadata type;private final boolean optional;private final boolean hasDefault;private final String normalizedDefault;private final SomaColumnRole role;private ColumnMetadata(String javaPath,String logicalPath,SomaTypeMetadata type,boolean optional,String normalizedDefault,SomaColumnRole role){this.javaPath=javaPath;this.logicalPath=logicalPath;this.type=type;this.optional=optional;this.hasDefault=normalizedDefault!=null;this.normalizedDefault=normalizedDefault;this.role=role;}public String javaPath(){return javaPath;}public String logicalPath(){return logicalPath;}public SomaTypeMetadata type(){return type;}public boolean optional(){return optional;}public boolean hasDefault(){return hasDefault;}public String normalizedDefault(){return normalizedDefault;}public SomaColumnRole role(){return role;}}\n")
                .append("  private static final class KeyMetadata implements SomaKeyMetadata{private final List<String> leafPaths;private KeyMetadata(List<String> leafPaths){this.leafPaths=leafPaths;}public List<String> leafPaths(){return leafPaths;}}\n")
                .append("  private static final class UniqueMetadata implements SomaUniqueMetadata{private final String name;private final List<String> leafPaths;private UniqueMetadata(String name,List<String> leafPaths){this.name=name;this.leafPaths=leafPaths;}public String name(){return name;}public List<String> leafPaths(){return leafPaths;}}\n")
                .append("  private static final class IndexMetadata implements SomaIndexMetadata{private final String name;private final List<String> leafPaths;private IndexMetadata(String name,List<String> leafPaths){this.name=name;this.leafPaths=leafPaths;}public String name(){return name;}public List<String> leafPaths(){return leafPaths;}}\n")
                .append("  private static final class OwnershipMetadata implements SomaOwnershipMetadata{private final String javaPath;private final String logicalPath;private final String containerKind;private final String childTable;private final SomaTypeMetadata type;private final boolean optional;private OwnershipMetadata(String javaPath,String logicalPath,String containerKind,String childTable,SomaTypeMetadata type,boolean optional){this.javaPath=javaPath;this.logicalPath=logicalPath;this.containerKind=containerKind;this.childTable=childTable;this.type=type;this.optional=optional;}public String javaPath(){return javaPath;}public String logicalPath(){return logicalPath;}public String containerKind(){return containerKind;}public String childTable(){return childTable;}public SomaTypeMetadata type(){return type;}public boolean optional(){return optional;}}\n")
                .append("  private static final class TableMetadata implements SomaTableMetadata{private final String logicalName;private final String carrierType;private final SomaTableKind kind;private final int defaultCapacity;private final List<SomaColumnMetadata> columns;private final SomaKeyMetadata key;private final List<SomaUniqueMetadata> uniques;private final List<SomaIndexMetadata> indexes;private final List<SomaOwnershipMetadata> ownership;private TableMetadata(String logicalName,String carrierType,SomaTableKind kind,int defaultCapacity,List<SomaColumnMetadata> columns,SomaKeyMetadata key,List<SomaUniqueMetadata> uniques,List<SomaIndexMetadata> indexes,List<SomaOwnershipMetadata> ownership){this.logicalName=logicalName;this.carrierType=carrierType;this.kind=kind;this.defaultCapacity=defaultCapacity;this.columns=columns;this.key=key;this.uniques=uniques;this.indexes=indexes;this.ownership=ownership;}public String logicalName(){return logicalName;}public String carrierType(){return carrierType;}public SomaTableKind kind(){return kind;}public int defaultCapacity(){return defaultCapacity;}public List<SomaColumnMetadata> columns(){return columns;}public SomaColumnMetadata requireColumn(String logicalPath){for(SomaColumnMetadata value:columns){if(value.logicalPath().equals(logicalPath))return value;}throw missing(\"column\",logicalPath);}public SomaKeyMetadata key(){return key;}public List<SomaUniqueMetadata> uniques(){return uniques;}public SomaUniqueMetadata requireUnique(String name){for(SomaUniqueMetadata value:uniques){if(value.name().equals(name))return value;}throw missing(\"unique\",name);}public List<SomaIndexMetadata> indexes(){return indexes;}public SomaIndexMetadata requireIndex(String name){for(SomaIndexMetadata value:indexes){if(value.name().equals(name))return value;}throw missing(\"index\",name);}public List<SomaOwnershipMetadata> ownership(){return ownership;}public SomaOwnershipMetadata requireOwnership(String logicalPath){for(SomaOwnershipMetadata value:ownership){if(value.logicalPath().equals(logicalPath))return value;}throw missing(\"ownership\",logicalPath);}private IllegalArgumentException missing(String kind,String name){return new IllegalArgumentException(\"Unknown \"+kind+\" '\"+name+\"' in SOMA table '\"+logicalName+\"'\");}}\n")
                .append("  private static final class SchemaDescriptor implements SomaSchemaMetadata{private final String logicalName;private final String sourcePackage;private final String generatedPackage;private final String version;private final String schemaHash;private final List<SomaTableMetadata> tables;private SchemaDescriptor(String logicalName,String sourcePackage,String generatedPackage,String version,String schemaHash,List<SomaTableMetadata> tables){this.logicalName=logicalName;this.sourcePackage=sourcePackage;this.generatedPackage=generatedPackage;this.version=version;this.schemaHash=schemaHash;this.tables=tables;}public String logicalName(){return logicalName;}public String sourcePackage(){return sourcePackage;}public String generatedPackage(){return generatedPackage;}public String version(){return version;}public String schemaHash(){return schemaHash;}public List<SomaTableMetadata> tables(){return tables;}public SomaTableMetadata requireTable(String logicalName){for(SomaTableMetadata value:tables){if(value.logicalName().equals(logicalName))return value;}throw new IllegalArgumentException(\"Unknown SOMA table '\"+logicalName+\"' in schema '\"+this.logicalName+\"'\");}}\n")
                .append("  private static final class DescriptorMetadata implements SomaDescriptor{private final SomaSchemaMetadata schema;private DescriptorMetadata(SomaSchemaMetadata schema){this.schema=schema;}public SomaSchemaMetadata schema(){return schema;}}\n")
                .append("  private static final class RootMetadata implements SomaMetadata{private final SomaDescriptor descriptor;private RootMetadata(SomaDescriptor descriptor){this.descriptor=descriptor;}public SomaDescriptor descriptor(){return descriptor;}}\n");
    }

    private static String q(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\\' || current == '"') {
                result.append('\\');
            }
            result.append(current);
        }
        return result.append('"').toString();
    }
}
