package com.hgtech.soma.processor;

import com.hgtech.soma.processor.internal.CompilerProtocol;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.hgtech.soma.processor.SomaSchemaJson.GENERATED_TARGET;
import static com.hgtech.soma.processor.SomaSchemaJson.quote;

/** Normalized schema and artifact-plan model produced before code generation. */
final class SomaSchemaModel {
    /** Closed compiler classification; no OTHER/object fallback exists. */
    enum SchemaStorageKind {
        PRIMITIVE_BACKED_SCALAR,
        REFERENCE_BACKED_IMMUTABLE_SCALAR,
        COMPILER_FLATTENED_VALUE,
        OWNED_STRUCTURED_STATE;

        String metadataConstant() {
            return "SomaTypeKind." + name();
        }
    }

    private SomaSchemaModel() {
    }

    static final class SchemaArtifactPlan {
        final PackageElement origin;
        final String basePath;
        final String schemaJson;
        final String schemaHash;
        final List<GeneratedSourceOutput> sources;

        SchemaArtifactPlan(
                PackageElement origin,
                String basePath,
                String schemaJson,
                String schemaHash,
                List<GeneratedSourceOutput> sources) {
            this.origin = origin;
            this.basePath = basePath;
            this.schemaJson = schemaJson;
            this.schemaHash = schemaHash;
            this.sources = Collections.unmodifiableList(
                    new ArrayList<GeneratedSourceOutput>(sources));
        }
    }

    static final class SchemaModel {
        final PackageElement origin;
        final String sourcePackage;
        final String name;
        final String generatedPackage;
        final String version;
        final Map<String, EnumModel> enums = new TreeMap<String, EnumModel>(
                UnicodeCodePointOrder.INSTANCE);
        final Map<String, ValueModel> values = new TreeMap<String, ValueModel>(
                UnicodeCodePointOrder.INSTANCE);
        final Map<String, TableModel> tables = new TreeMap<String, TableModel>(
                UnicodeCodePointOrder.INSTANCE);

        SchemaModel(
                PackageElement origin,
                String sourcePackage,
                String name,
                String generatedPackage,
                String version) {
            this.origin = origin;
            this.sourcePackage = sourcePackage;
            this.name = name;
            this.generatedPackage = generatedPackage;
            this.version = version;
        }

        void addValue(ValueModel value) {
            values.put(value.javaType, value);
            for (FieldModel field : value.fields) {
                if (field.type.enumModel != null) {
                    enums.put(field.type.enumModel.javaType, field.type.enumModel);
                }
            }
        }

        void addTable(TableModel table) {
            tables.put(table.javaType, table);
            for (TableFieldModel field : table.fields) {
                if (field.type != null && field.type.enumModel != null) {
                    enums.put(field.type.enumModel.javaType, field.type.enumModel);
                }
            }
        }

        TableModel tableByLogicalName(String logicalName) {
            for (TableModel table : tables.values()) {
                if (table.logicalName.equals(logicalName)) {
                    return table;
                }
            }
            return null;
        }

        String toCanonicalJson() {
            StringBuilder json = new StringBuilder();
            json.append('{');
            json.append("\"enums\":[");
            int enumIndex = 0;
            for (EnumModel enumModel : enums.values()) {
                if (enumIndex > 0) {
                    json.append(',');
                }
                enumModel.appendJson(json);
                enumIndex++;
            }
            json.append("],");
            json.append("\"generatedPackage\":").append(quote(generatedPackage)).append(',');
            json.append("\"generatedTarget\":").append(quote(GENERATED_TARGET)).append(',');
            json.append("\"schemaName\":").append(quote(name)).append(',');
            json.append("\"schemaPackage\":").append(quote(sourcePackage)).append(',');
            json.append("\"schemaVersion\":").append(quote(version)).append(',');
            json.append("\"tables\":[");
            int tableIndex = 0;
            for (TableModel table : tables.values()) {
                if (tableIndex > 0) {
                    json.append(',');
                }
                table.appendJson(json);
                tableIndex++;
            }
            json.append("],");
            json.append("\"values\":[");
            int valueIndex = 0;
            for (ValueModel value : values.values()) {
                if (valueIndex > 0) {
                    json.append(',');
                }
                value.appendJson(json);
                valueIndex++;
            }
            return json.append("]}").toString();
        }
    }

    static final class EnumModel {
        final String javaType;
        final String logicalName;
        final List<String> members;

        EnumModel(String javaType, String logicalName, List<String> members) {
            this.javaType = javaType;
            this.logicalName = logicalName;
            this.members = members;
        }

        void appendJson(StringBuilder json) {
            json.append('{');
            json.append("\"javaType\":").append(quote(javaType)).append(',');
            json.append("\"logicalName\":").append(quote(logicalName)).append(',');
            json.append("\"members\":[");
            for (int i = 0; i < members.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append(quote(members.get(i)));
            }
            json.append("]}");
        }
    }

    static final class ValueModel {
        final TypeElement origin;
        final String javaType;
        final String logicalName;
        final List<FieldModel> fields;

        ValueModel(
                TypeElement origin,
                String javaType,
                String logicalName,
                List<FieldModel> fields) {
            this.origin = origin;
            this.javaType = javaType;
            this.logicalName = logicalName;
            this.fields = fields;
        }

        void appendJson(StringBuilder json) {
            json.append('{');
            json.append("\"effectiveShape\":{");
            json.append("\"classFinal\":true,");
            json.append("\"constructor\":\"canonical-all-fields\",");
            json.append("\"fieldsPublicFinal\":true,");
            json.append("\"loweringIdentity\":")
                    .append(quote(CompilerProtocol.LOWERING_IDENTITY)).append("},");
            json.append("\"fields\":[");
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                fields.get(i).appendJson(json);
            }
            json.append("],");
            json.append("\"javaType\":").append(quote(javaType)).append(',');
            json.append("\"logicalName\":").append(quote(logicalName));
            json.append('}');
        }
    }

    static final class FieldModel {
        final VariableElement origin;
        final String javaName;
        final String logicalName;
        final String semantic;
        final NormalizedType type;
        final DefaultModel defaultValue;

        FieldModel(
                VariableElement origin,
                String javaName,
                String logicalName,
                String semantic,
                NormalizedType type,
                DefaultModel defaultValue) {
            this.origin = origin;
            this.javaName = javaName;
            this.logicalName = logicalName;
            this.semantic = semantic;
            this.type = type;
            this.defaultValue = defaultValue;
        }

        void appendJson(StringBuilder json) {
            json.append('{');
            if (defaultValue != null) {
                json.append("\"default\":");
                defaultValue.appendJson(json);
                json.append(',');
            }
            json.append("\"javaName\":").append(quote(javaName)).append(',');
            json.append("\"logicalName\":").append(quote(logicalName)).append(',');
            json.append("\"semantic\":").append(quote(semantic)).append(',');
            json.append("\"type\":").append(quote(type.text));
            json.append('}');
        }
    }

    static final class DefaultModel {
        final String literal;
        final String normalized;
        final String javaExpression;

        DefaultModel(String literal, String normalized, String javaExpression) {
            this.literal = literal;
            this.normalized = normalized;
            this.javaExpression = javaExpression;
        }

        void appendJson(StringBuilder json) {
            json.append('{').append("\"literal\":").append(quote(literal)).append(',')
                    .append("\"normalized\":").append(quote(normalized)).append('}');
        }
    }

    static final class NormalizedType {
        final String text;
        final EnumModel enumModel;
        final String valueReference;

        NormalizedType(
                String text, EnumModel enumModel, String valueReference) {
            this.text = text;
            this.enumModel = enumModel;
            this.valueReference = valueReference;
        }
    }

    static final class TableModel {
        final TypeElement origin;
        final String javaType;
        final String simpleName;
        final String logicalName;
        final int defaultCapacity;
        final List<TableFieldModel> fields;
        final List<SelectorModel> selectors;

        TableModel(TypeElement origin, String javaType, String simpleName,
                           String logicalName, int defaultCapacity,
                           List<TableFieldModel> fields,
                           List<SelectorModel> selectors) {
            this.origin = origin;
            this.javaType = javaType;
            this.simpleName = simpleName;
            this.logicalName = logicalName;
            this.defaultCapacity = defaultCapacity;
            this.fields = fields;
            this.selectors = selectors;
        }

        void appendJson(StringBuilder json) {
            json.append('{');
            json.append("\"fields\":[");
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                fields.get(i).appendJson(json);
            }
            json.append("],");
            json.append("\"javaType\":").append(quote(javaType)).append(',');
            json.append("\"kind\":").append(hasKey() ? "\"keyed\"" : "\"dense\"").append(',');
            json.append("\"logicalName\":").append(quote(logicalName)).append(',');
            json.append("\"materializedType\":").append(quote(javaType));
            if (!selectors.isEmpty()) {
                json.append(',').append("\"selectors\":[");
                for (int i = 0; i < selectors.size(); i++) {
                    if (i > 0) json.append(',');
                    selectors.get(i).appendJson(json);
                }
                json.append(']');
            }
            json.append('}');
        }

        DenseTableCodegenModel.TableSpec toGeneratorSpec() {
            List<DenseTableCodegenModel.FieldSpec> result =
                    new ArrayList<DenseTableCodegenModel.FieldSpec>();
            List<DenseTableCodegenModel.ChildSpec> childResult =
                    new ArrayList<DenseTableCodegenModel.ChildSpec>();
            for (TableFieldModel field : fields) {
                if (field.child == null) result.add(field.toGeneratorSpec());
                else childResult.add(field.toGeneratorChildSpec());
            }
            List<DenseTableCodegenModel.SelectorSpec> generatedSelectors =
                    new ArrayList<DenseTableCodegenModel.SelectorSpec>();
            for (SelectorModel selector : selectors) {
                generatedSelectors.add(selector.toGeneratorSpec());
            }
            return new DenseTableCodegenModel.TableSpec(
                    origin, javaType, simpleName, logicalName,
                    defaultCapacity < 0 ? 16 : defaultCapacity,
                    result, childResult, generatedSelectors);
        }

        boolean hasKey() {
            for (TableFieldModel field : fields) {
                if (field.key) {
                    return true;
                }
            }
            return false;
        }
    }

    static final class SelectorModel {
        final String kind;
        final String name;
        final List<SelectorLeafModel> leaves;

        SelectorModel(
                String kind, String name, List<SelectorLeafModel> leaves) {
            this.kind = kind;
            this.name = name;
            this.leaves = leaves;
        }

        void appendJson(StringBuilder json) {
            json.append('{');
            json.append("\"kind\":").append(quote(kind)).append(',');
            json.append("\"leaves\":[");
            for (int i = 0; i < leaves.size(); i++) {
                if (i > 0) json.append(',');
                leaves.get(i).appendJson(json);
            }
            json.append("],\"name\":").append(quote(name)).append('}');
        }

        DenseTableCodegenModel.SelectorSpec toGeneratorSpec() {
            List<DenseTableCodegenModel.SelectorLeafSpec> result =
                    new ArrayList<DenseTableCodegenModel.SelectorLeafSpec>();
            for (SelectorLeafModel leaf : leaves) {
                result.add(new DenseTableCodegenModel.SelectorLeafSpec(
                        leaf.path, "ASC", leaf.publicType,
                        leaf.storageType, leaf.enumType));
            }
            return new DenseTableCodegenModel.SelectorSpec(kind, name, result);
        }

        String generatedSuffix() {
            String source = name.startsWith("by_") ? name.substring(3) : name;
            StringBuilder suffix = new StringBuilder();
            boolean upper = true;
            for (int i = 0; i < source.length(); i++) {
                char value = source.charAt(i);
                if (value == '_') {
                    upper = true;
                } else {
                    suffix.append(upper ? Character.toUpperCase(value) : value);
                    upper = false;
                }
            }
            return suffix.toString();
        }

        String generatedMethodName() {
            return "scanBy" + generatedSuffix();
        }
    }

    static final class SelectorLeafModel {
        final String path;
        final String publicType;
        final String storageType;
        final String enumType;

        SelectorLeafModel(
                String path, String publicType,
                String storageType, String enumType) {
            this.path = path;
            this.publicType = publicType;
            this.storageType = storageType;
            this.enumType = enumType;
        }

        void appendJson(StringBuilder json) {
            json.append('{');
            json.append("\"path\":").append(quote(path)).append(',');
            json.append("\"storageType\":").append(quote(storageType));
            json.append('}');
        }
    }

    static final class TableFieldModel {
        final VariableElement origin;
        final String javaName;
        final String logicalName;
        final String semantic;
        final TableFieldType type;
        final ChildFieldType child;
        final boolean optional;
        final boolean key;
        DefaultModel defaultValue;

        TableFieldModel(VariableElement origin,
                                String javaName, String logicalName, String semantic,
                                TableFieldType type, ChildFieldType child,
                                boolean optional, boolean key,
                                DefaultModel defaultValue) {
            this.origin = origin;
            this.javaName = javaName;
            this.logicalName = logicalName;
            this.semantic = semantic;
            this.type = type;
            this.child = child;
            this.optional = optional;
            this.key = key;
            this.defaultValue = defaultValue;
        }

        void appendJson(StringBuilder json) {
            json.append('{');
            if (child != null) {
                json.append("\"child\":{")
                        .append("\"container\":").append(quote(child.container));
                if (child.keyMaterializedType != null) {
                    json.append(',').append("\"keyMaterializedType\":")
                            .append(quote(child.keyMaterializedType));
                }
                json.append(',').append("\"rowJavaType\":")
                        .append(quote(child.rowJavaType)).append(',')
                        .append("\"tableLogicalName\":")
                        .append(quote(child.tableLogicalName)).append("},");
            }
            if (defaultValue != null) {
                json.append("\"default\":");
                defaultValue.appendJson(json);
                json.append(',');
            }
            json.append("\"javaName\":").append(quote(javaName)).append(',');
            json.append("\"leaves\":[");
            if (child != null) {
                // ownership fields never flatten into parent storage leaves
            } else if (type.valueJavaType == null) {
                appendLeafJson(json, logicalName, semantic, type.storagePrimitiveName);
            } else {
                for (int i = 0; i < type.valueLeaves.size(); i++) {
                    if (i > 0) {
                        json.append(',');
                    }
                    ValueLeafType leaf = type.valueLeaves.get(i);
                    appendLeafJson(json, logicalName + "." + leaf.logicalName,
                            leaf.semantic, leaf.storagePrimitiveName);
                }
            }
            json.append("],");
            json.append("\"logicalName\":").append(quote(logicalName)).append(',');
            json.append("\"materializedType\":")
                    .append(quote(child != null ? child.materializedType
                            : optional ? type.boxedName : type.materializedType)).append(',');
            json.append("\"optional\":").append(optional).append(',');
            json.append("\"role\":").append(child != null ? "\"child\""
                    : key ? "\"key\"" : "\"field\"").append(',');
            json.append("\"type\":").append(quote(child != null ? "child" : type.logicalType));
            json.append('}');
        }

        DenseTableCodegenModel.FieldSpec toGeneratorSpec() {
            List<DenseTableCodegenModel.ValueLeafSpec> leaves =
                    new ArrayList<DenseTableCodegenModel.ValueLeafSpec>();
            for (ValueLeafType leaf : type.valueLeaves) {
                leaves.add(new DenseTableCodegenModel.ValueLeafSpec(
                        leaf.javaName, leaf.storageName, leaf.logicalName, leaf.semantic,
                        leaf.publicPrimitiveName, leaf.storagePrimitiveName,
                        leaf.columnType, leaf.enumJavaType));
            }
            List<DenseTableCodegenModel.ValueGroupSpec> groups =
                    new ArrayList<DenseTableCodegenModel.ValueGroupSpec>();
            for (ValueGroupType group : type.valueGroups) {
                groups.add(new DenseTableCodegenModel.ValueGroupSpec(
                        group.javaPath, group.logicalPath, group.javaType,
                        group.firstLeaf, group.leafCount, group.directFieldCount));
            }
            return new DenseTableCodegenModel.FieldSpec(
                    javaName, logicalName, type.publicType,
                    type.boxedName, type.storagePrimitiveName, type.columnType,
                    type.enumJavaType, type.valueJavaType, type.valueLeafJavaName,
                    type.valueConstructionTemplate, leaves, groups, optional, key,
                    defaultValue != null ? defaultValue.javaExpression
                            : type.valueDefaultExpression);
        }

        DenseTableCodegenModel.ChildSpec toGeneratorChildSpec() {
            return new DenseTableCodegenModel.ChildSpec(
                    javaName, logicalName, child.container, child.rowJavaType,
                    child.rowSimpleName, child.tableLogicalName,
                    child.keyMaterializedType, child.keyJavaName, child.materializedType,
                    child.initialCapacity, optional);
        }

        static void appendLeafJson(
                StringBuilder json, String path, String semantic, String storageType) {
            json.append('{');
            json.append("\"leafPath\":").append(quote(path)).append(',');
            json.append("\"semantic\":").append(quote(semantic)).append(',');
            json.append("\"storageType\":").append(quote(storageType));
            json.append('}');
        }
    }

    static final class ChildFieldType {
        final String container;
        final String rowJavaType;
        final String rowSimpleName;
        final String tableLogicalName;
        final String keyMaterializedType;
        final String keyJavaName;
        final String materializedType;
        final int initialCapacity;

        ChildFieldType(
                String container,
                String rowJavaType,
                String rowSimpleName,
                String tableLogicalName,
                String keyMaterializedType,
                String keyJavaName,
                String materializedType,
                int initialCapacity) {
            this.container = container;
            this.rowJavaType = rowJavaType;
            this.rowSimpleName = rowSimpleName;
            this.tableLogicalName = tableLogicalName;
            this.keyMaterializedType = keyMaterializedType;
            this.keyJavaName = keyJavaName;
            this.materializedType = materializedType;
            this.initialCapacity = initialCapacity;
        }
    }

    static final class TableFieldType {
        final TypeKind primitiveKind;
        final String logicalType;
        final String publicType;
        final String boxedName;
        final String materializedType;
        final String storagePrimitiveName;
        final String columnType;
        final String enumJavaType;
        final EnumModel enumModel;
        final String valueJavaType;
        final String valueLeafJavaName;
        final String valueLeafLogicalName;
        final String valueLeafSemantic;
        final String valueConstructionTemplate;
        final String valueDefaultExpression;
        final List<ValueLeafType> valueLeaves;
        final List<ValueGroupType> valueGroups;

        SchemaStorageKind storageKind() {
            if (valueJavaType != null) {
                return SchemaStorageKind.COMPILER_FLATTENED_VALUE;
            }
            if ("java.lang.String".equals(storagePrimitiveName)) {
                return SchemaStorageKind.REFERENCE_BACKED_IMMUTABLE_SCALAR;
            }
            return SchemaStorageKind.PRIMITIVE_BACKED_SCALAR;
        }

        TableFieldType(
                TypeKind primitiveKind,
                String logicalType,
                String publicType,
                String boxedName,
                String materializedType,
                String storagePrimitiveName,
                String columnType,
                String enumJavaType,
                EnumModel enumModel,
                String valueJavaType,
                String valueLeafJavaName,
                String valueLeafLogicalName,
                String valueLeafSemantic,
                String valueConstructionTemplate,
                String valueDefaultExpression,
                List<ValueLeafType> valueLeaves,
                List<ValueGroupType> valueGroups) {
            this.primitiveKind = primitiveKind;
            this.logicalType = logicalType;
            this.publicType = publicType;
            this.boxedName = boxedName;
            this.materializedType = materializedType;
            this.storagePrimitiveName = storagePrimitiveName;
            this.columnType = columnType;
            this.enumJavaType = enumJavaType;
            this.enumModel = enumModel;
            this.valueJavaType = valueJavaType;
            this.valueLeafJavaName = valueLeafJavaName;
            this.valueLeafLogicalName = valueLeafLogicalName;
            this.valueLeafSemantic = valueLeafSemantic;
            this.valueConstructionTemplate = valueConstructionTemplate;
            this.valueDefaultExpression = valueDefaultExpression;
            this.valueLeaves = valueLeaves;
            this.valueGroups = valueGroups;
        }

        static TableFieldType forKind(TypeKind kind) {
            switch (kind) {
                case BOOLEAN: return type(kind, "boolean", "java.lang.Boolean", "BooleanColumn");
                case BYTE: return type(kind, "byte", "java.lang.Byte", "ByteColumn");
                case SHORT: return type(kind, "short", "java.lang.Short", "ShortColumn");
                case INT: return type(kind, "int", "java.lang.Integer", "IntColumn");
                case LONG: return type(kind, "long", "java.lang.Long", "LongColumn");
                case FLOAT: return type(kind, "float", "java.lang.Float", "FloatColumn");
                case DOUBLE: return type(kind, "double", "java.lang.Double", "DoubleColumn");
                default: return null;
            }
        }

        static TableFieldType forBoxed(String javaType) {
            if ("java.lang.Boolean".equals(javaType)) return forKind(TypeKind.BOOLEAN);
            if ("java.lang.Byte".equals(javaType)) return forKind(TypeKind.BYTE);
            if ("java.lang.Short".equals(javaType)) return forKind(TypeKind.SHORT);
            if ("java.lang.Integer".equals(javaType)) return forKind(TypeKind.INT);
            if ("java.lang.Long".equals(javaType)) return forKind(TypeKind.LONG);
            if ("java.lang.Float".equals(javaType)) return forKind(TypeKind.FLOAT);
            if ("java.lang.Double".equals(javaType)) return forKind(TypeKind.DOUBLE);
            return null;
        }

        static TableFieldType forString() {
            return new TableFieldType(
                    null, "string", "java.lang.String", "java.lang.String",
                    "java.lang.String", "java.lang.String",
                    "StringColumn", null, null,
                    null, null, null, null, null, null,
                    new ArrayList<ValueLeafType>(), new ArrayList<ValueGroupType>());
        }

        static TableFieldType forEnum(TypeElement type, EnumModel enumModel) {
            String javaType = type.getQualifiedName().toString();
            return new TableFieldType(
                    null, "enum:" + javaType, javaType, javaType, javaType,
                    "int", "IntColumn", javaType, enumModel,
                    null, null, null, null, null, null,
                    new ArrayList<ValueLeafType>(), new ArrayList<ValueGroupType>());
        }

        static TableFieldType forFlattenedValue(
                ValueModel value, Map<String, ValueModel> values) {
            if (value.fields.isEmpty()) {
                return null;
            }
            List<ValueLeafType> leaves = new ArrayList<ValueLeafType>();
            List<ValueGroupType> groups = new ArrayList<ValueGroupType>();
            String construction = flattenValue(
                    value, values, "", "", "", leaves, groups, new HashSet<String>());
            if (construction == null || leaves.isEmpty()) return null;
            String defaultExpression = valueDefaultExpression(
                    value, values, new HashSet<String>());
            ValueLeafType first = leaves.get(0);
            return new TableFieldType(
                    primitiveKind(first.storagePrimitiveName),
                    "value:" + value.javaType,
                    value.javaType,
                    value.javaType,
                    value.javaType,
                    first.storagePrimitiveName,
                    first.columnType,
                    null,
                    null,
                    value.javaType,
                    first.javaName,
                    first.logicalName,
                    first.semantic,
                    construction,
                    defaultExpression,
                    leaves, groups);
        }

        static String valueDefaultExpression(
                ValueModel value,
                Map<String, ValueModel> values,
                Set<String> visiting) {
            if (!visiting.add(value.javaType)) return null;
            StringBuilder expression = new StringBuilder("new ")
                    .append(value.javaType).append('(');
            for (int index = 0; index < value.fields.size(); index++) {
                if (index > 0) expression.append(',');
                FieldModel field = value.fields.get(index);
                if (field.type.valueReference != null) {
                    ValueModel nested = values.get(field.type.valueReference);
                    if (nested == null) return null;
                    String nestedDefault = valueDefaultExpression(nested, values, visiting);
                    if (nestedDefault == null) return null;
                    expression.append(nestedDefault);
                } else {
                    if (field.defaultValue == null) return null;
                    expression.append(field.defaultValue.javaExpression);
                }
            }
            visiting.remove(value.javaType);
            return expression.append(')').toString();
        }

        static String flattenValue(
                ValueModel value,
                Map<String, ValueModel> values,
                String javaPrefix,
                String logicalPrefix,
                String storagePrefix,
                List<ValueLeafType> leaves,
                List<ValueGroupType> groups,
                Set<String> visiting) {
            if (!visiting.add(value.javaType)) return null;
            int firstLeaf = leaves.size();
            StringBuilder result = new StringBuilder("new ")
                    .append(value.javaType).append('(');
            for (int index = 0; index < value.fields.size(); index++) {
                if (index > 0) result.append(',');
                FieldModel field = value.fields.get(index);
                String javaPath = javaPrefix + field.javaName;
                String logicalPath = logicalPrefix + field.logicalName;
                String storageName = storagePrefix + (storagePrefix.isEmpty()
                        ? field.javaName : capitalize(field.javaName));
                if (field.type.valueReference != null) {
                    ValueModel nested = values.get(field.type.valueReference);
                    if (nested == null) return null;
                    String nestedExpression = flattenValue(
                            nested, values, javaPath + ".", logicalPath + ".",
                            storageName, leaves, groups, visiting);
                    if (nestedExpression == null) return null;
                    result.append(nestedExpression);
                } else {
                    ValueLeafType leaf = valueLeaf(
                            field, javaPath, logicalPath, storageName);
                    if (leaf == null) return null;
                    int leafIndex = leaves.size();
                    leaves.add(leaf);
                    result.append("@{").append(leafIndex).append("}@");
                }
            }
            visiting.remove(value.javaType);
            String groupJavaPath = javaPrefix.isEmpty()
                    ? "" : javaPrefix.substring(0, javaPrefix.length() - 1);
            String groupLogicalPath = logicalPrefix.isEmpty()
                    ? "" : logicalPrefix.substring(0, logicalPrefix.length() - 1);
            groups.add(new ValueGroupType(groupJavaPath, groupLogicalPath,
                    value.javaType, firstLeaf, leaves.size() - firstLeaf,
                    value.fields.size()));
            return result.append(')').toString();
        }

        static ValueLeafType valueLeaf(
                FieldModel field, String javaPath, String logicalPath,
                String storageName) {
            TableFieldType primitive = primitiveType(field.type.text);
            if (primitive != null) {
                return new ValueLeafType(javaPath, storageName, logicalPath,
                        field.semantic, primitive.publicType,
                        primitive.storagePrimitiveName, primitive.columnType, null,
                        field.defaultValue);
            }
            if ("string".equals(field.type.text)) {
                return new ValueLeafType(javaPath, storageName, logicalPath,
                        field.semantic, "java.lang.String", "java.lang.String",
                        "StringColumn", null, field.defaultValue);
            }
            if (field.type.enumModel != null) {
                String enumType = field.type.enumModel.javaType;
                return new ValueLeafType(javaPath, storageName, logicalPath,
                        field.semantic, enumType, "int", "IntColumn", enumType,
                        field.defaultValue);
            }
            return null;
        }

        static TypeKind primitiveKind(String storageType) {
            TableFieldType primitive = primitiveType(storageType);
            return primitive == null ? null : primitive.primitiveKind;
        }

        static String capitalize(String value) {
            return Character.toUpperCase(value.charAt(0)) + value.substring(1);
        }

        static TableFieldType primitiveType(String text) {
            if ("boolean".equals(text)) return forKind(TypeKind.BOOLEAN);
            if ("byte".equals(text)) return forKind(TypeKind.BYTE);
            if ("short".equals(text)) return forKind(TypeKind.SHORT);
            if ("int".equals(text)) return forKind(TypeKind.INT);
            if ("long".equals(text)) return forKind(TypeKind.LONG);
            if ("float".equals(text)) return forKind(TypeKind.FLOAT);
            if ("double".equals(text)) return forKind(TypeKind.DOUBLE);
            return null;
        }

        static TableFieldType type(TypeKind kind, String primitive,
                                           String boxed, String column) {
            return new TableFieldType(
                    kind, primitive, primitive, boxed, primitive, primitive,
                    column, null, null, null, null, null, null,
                    null, null,
                    new ArrayList<ValueLeafType>(), new ArrayList<ValueGroupType>());
        }
    }

    static final class ValueLeafType {
        final String javaName;
        final String storageName;
        final String logicalName;
        final String semantic;
        final String publicPrimitiveName;
        final String storagePrimitiveName;
        final String columnType;
        final String enumJavaType;
        final DefaultModel defaultValue;

        ValueLeafType(
                String javaName, String storageName, String logicalName, String semantic,
                String publicPrimitiveName, String storagePrimitiveName,
                String columnType, String enumJavaType,
                DefaultModel defaultValue) {
            this.javaName = javaName;
            this.storageName = storageName;
            this.logicalName = logicalName;
            this.semantic = semantic;
            this.publicPrimitiveName = publicPrimitiveName;
            this.storagePrimitiveName = storagePrimitiveName;
            this.columnType = columnType;
            this.enumJavaType = enumJavaType;
            this.defaultValue = defaultValue;
        }
    }

    static final class ValueGroupType {
        final String javaPath;
        final String logicalPath;
        final String javaType;
        final int firstLeaf;
        final int leafCount;
        final int directFieldCount;

        ValueGroupType(
                String javaPath, String logicalPath, String javaType,
                int firstLeaf, int leafCount, int directFieldCount) {
            this.javaPath = javaPath;
            this.logicalPath = logicalPath;
            this.javaType = javaType;
            this.firstLeaf = firstLeaf;
            this.leafCount = leafCount;
            this.directFieldCount = directFieldCount;
        }
    }}
