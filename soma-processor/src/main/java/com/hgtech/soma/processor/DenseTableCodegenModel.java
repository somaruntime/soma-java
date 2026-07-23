package com.hgtech.soma.processor;

import javax.lang.model.element.Element;
import java.util.ArrayList;
import java.util.List;

import static com.hgtech.soma.processor.DenseSourceNames.cap;
import static com.hgtech.soma.processor.DenseSourceNames.q;

/** Stable internal model consumed by the dense artifact emitters. */
final class DenseTableCodegenModel {
    private DenseTableCodegenModel() {
    }

    static TableSpec requireTable(List<TableSpec> tables, String logicalName) {
        for (TableSpec table : tables) {
            if (table.logicalName.equals(logicalName)) {
                return table;
            }
        }
        throw new IllegalStateException("missing schema table: " + logicalName);
    }

    static final class TableSpec {
        final Element origin;
        final String carrierType;
        final String carrierSimpleName;
        final String logicalName;
        final int defaultCapacity;
        final List<FieldSpec> fields;
        final List<ChildSpec> children;
        final List<SelectorSpec> selectors;

        TableSpec(Element origin, String carrierType, String carrierSimpleName,
                  String logicalName, int defaultCapacity, List<FieldSpec> fields,
                  List<ChildSpec> children, List<SelectorSpec> selectors) {
            this.origin = origin;
            this.carrierType = carrierType;
            this.carrierSimpleName = carrierSimpleName;
            this.logicalName = logicalName;
            this.defaultCapacity = defaultCapacity;
            this.fields = new ArrayList<FieldSpec>(fields);
            this.children = new ArrayList<ChildSpec>(children);
            this.selectors = new ArrayList<SelectorSpec>(selectors);
        }

        String name(String suffix) { return carrierSimpleName + suffix; }

        boolean keyed() { return keyField() != null; }

        FieldSpec keyField() {
            for (FieldSpec field : fields) {
                if (field.key) {
                    return field;
                }
            }
            return null;
        }

        int directSlots() {
            int slots = 1;
            for (FieldSpec field : fields) {
                if (field.optional) slots++;
                slots += field.jvmSlots();
            }
            return slots;
        }

        int updateWidth() {
            int result = 0;
            for (FieldSpec field : fields) {
                if (field.key) {
                    continue;
                }
                if (field.flattenedValueStorage()) {
                    for (ValueLeafSpec leaf : field.valueLeaves) result += leaf.bytes();
                } else {
                    result += field.bytes();
                }
                if (field.optional) result++;
            }
            return result;
        }
    }

    static final class ChildSpec {
        final String javaName;
        final String logicalName;
        final String container;
        final String rowJavaType;
        final String rowSimpleName;
        final String tableLogicalName;
        final String keyMaterializedType;
        final String keyJavaName;
        final String materializedType;
        final int initialCapacity;
        final boolean optional;

        ChildSpec(
                String javaName,
                String logicalName,
                String container,
                String rowJavaType,
                String rowSimpleName,
                String tableLogicalName,
                String keyMaterializedType,
                String keyJavaName,
                String materializedType,
                int initialCapacity,
                boolean optional) {
            this.javaName = javaName;
            this.logicalName = logicalName;
            this.container = container;
            this.rowJavaType = rowJavaType;
            this.rowSimpleName = rowSimpleName;
            this.tableLogicalName = tableLogicalName;
            this.keyMaterializedType = keyMaterializedType;
            this.keyJavaName = keyJavaName;
            this.materializedType = materializedType;
            this.initialCapacity = initialCapacity;
            this.optional = optional;
        }

        String tableType() { return rowSimpleName + "Table"; }
        String batchType() { return rowSimpleName + "Batch"; }
        boolean keyed() { return "map".equals(container); }
    }

    static final class SelectorSpec {
        final String kind;
        final String name;
        final List<SelectorLeafSpec> leaves;

        SelectorSpec(String kind, String name, List<SelectorLeafSpec> leaves) {
            this.kind = kind;
            this.name = name;
            this.leaves = new ArrayList<SelectorLeafSpec>(leaves);
        }
    }

    static final class SelectorLeafSpec {
        final String path;
        final String direction;
        final String publicType;
        final String storageType;
        final String enumType;

        SelectorLeafSpec(
                String path, String direction, String publicType,
                String storageType, String enumType) {
            this.path = path;
            this.direction = direction;
            this.publicType = publicType;
            this.storageType = storageType;
            this.enumType = enumType;
        }
    }

    static final class ValueGroupSpec {
        final String javaPath;
        final String logicalPath;
        final String javaType;
        final int firstLeaf;
        final int leafCount;
        final int directFieldCount;

        ValueGroupSpec(
                String javaPath, String logicalPath, String javaType,
                int firstLeaf, int leafCount, int directFieldCount) {
            this.javaPath = javaPath;
            this.logicalPath = logicalPath;
            this.javaType = javaType;
            this.firstLeaf = firstLeaf;
            this.leafCount = leafCount;
            this.directFieldCount = directFieldCount;
        }
    }

    static final class FieldSpec {
        final String javaName;
        final String logicalName;
        /** public/generated Java type；primitive 保持 primitive，enum 保持 FQN。 */
        final String primitive;
        final String boxed;
        /** packed physical column 的 primitive type。 */
        final String storagePrimitive;
        final String columnType;
        final String enumType;
        final String valueType;
        final String valueLeafJavaName;
        final String valueConstructionTemplate;
        final List<ValueLeafSpec> valueLeaves;
        final List<ValueGroupSpec> valueGroups;
        final boolean optional;
        final boolean key;
        final String defaultExpression;

        FieldSpec(String javaName, String logicalName, String primitive,
                  String boxed, String storagePrimitive, String columnType,
                  String enumType, String valueType, String valueLeafJavaName,
                  String valueConstructionTemplate, List<ValueLeafSpec> valueLeaves,
                  List<ValueGroupSpec> valueGroups,
                  boolean optional, boolean key, String defaultExpression) {
            this.javaName = javaName;
            this.logicalName = logicalName;
            this.primitive = primitive;
            this.boxed = boxed;
            this.storagePrimitive = storagePrimitive;
            this.columnType = columnType;
            this.enumType = enumType;
            this.valueType = valueType;
            this.valueLeafJavaName = valueLeafJavaName;
            this.valueConstructionTemplate = valueConstructionTemplate;
            this.valueLeaves = new ArrayList<ValueLeafSpec>(valueLeaves);
            this.valueGroups = new ArrayList<ValueGroupSpec>(valueGroups);
            this.optional = optional;
            this.key = key;
            this.defaultExpression = defaultExpression;
        }

        boolean hasDefault() { return defaultExpression != null; }

        String zero() {
            if ("boolean".equals(primitive)) return "false";
            if ("byte".equals(primitive)) return "(byte)0";
            if ("short".equals(primitive)) return "(short)0";
            if ("long".equals(primitive)) return "0L";
            if ("float".equals(primitive)) return "0.0f";
            if ("double".equals(primitive)) return "0.0d";
            if (enumType != null || valueType != null
                    || "java.lang.String".equals(primitive)) return "null";
            return "0";
        }

        String storageZero() {
            if ("boolean".equals(storagePrimitive)) return "false";
            if ("byte".equals(storagePrimitive)) return "(byte)0";
            if ("short".equals(storagePrimitive)) return "(short)0";
            if ("long".equals(storagePrimitive)) return "0L";
            if ("float".equals(storagePrimitive)) return "0.0f";
            if ("double".equals(storagePrimitive)) return "0.0d";
            if ("java.lang.String".equals(storagePrimitive)) return "null";
            return "0";
        }

        String unboxMethod() {
            if ("boolean".equals(primitive)) return "booleanValue";
            if ("byte".equals(primitive)) return "byteValue";
            if ("short".equals(primitive)) return "shortValue";
            if ("int".equals(primitive)) return "intValue";
            if ("long".equals(primitive)) return "longValue";
            if ("float".equals(primitive)) return "floatValue";
            return "doubleValue";
        }

        String boxValue(String expression) {
            if (enumType != null || valueType != null
                    || "java.lang.String".equals(primitive)) {
                return expression;
            }
            return boxed + ".valueOf(" + expression + ")";
        }

        boolean boxedPrimitive() {
            return optional && enumType == null && valueType == null
                    && !"java.lang.String".equals(primitive);
        }

        String storageValue(String expression, String operation) {
            if (valueType != null) {
                String leaf = "RuntimeFailures.requiredValue(TABLE," + q(logicalName) + ","
                        + expression + "," + q(operation) + ")." + valueLeafJavaName;
                if (key && "float".equals(storagePrimitive)) {
                    return "KeyCanonicalization.strictFloatStorage(TABLE,"
                            + q(logicalName) + "," + leaf + "," + q(operation) + ")";
                }
                if (key && "double".equals(storagePrimitive)) {
                    return "KeyCanonicalization.strictDoubleStorage(TABLE,"
                            + q(logicalName) + "," + leaf + "," + q(operation) + ")";
                }
                return leaf;
            }
            if (enumType != null) {
                return "RuntimeFailures.requiredEnumValue(TABLE," + q(logicalName) + ","
                        + expression + "," + q(operation) + ").ordinal()";
            }
            if ("java.lang.String".equals(storagePrimitive)) {
                return "RuntimeFailures.requiredValue(TABLE," + q(logicalName) + ","
                        + expression + "," + q(operation) + ")";
            }
            if ("float".equals(primitive)) {
                return key ? "KeyCanonicalization.strictFloatStorage(TABLE,"
                        + q(logicalName) + "," + expression + "," + q(operation) + ")" : expression;
            }
            if ("double".equals(primitive)) {
                return key ? "KeyCanonicalization.strictDoubleStorage(TABLE,"
                        + q(logicalName) + "," + expression + "," + q(operation) + ")" : expression;
            }
            return expression;
        }

        String publicValue(String expression) {
            if (valueType != null) {
                return "new " + valueType + "(" + expression + ")";
            }
            return enumType == null ? expression : enumConstantsName() + "[" + expression + "]";
        }

        String enumConstantsName() {
            return javaName.toUpperCase(java.util.Locale.ROOT) + "_ENUM_VALUES";
        }

        String keySpaceType() {
            if (compositeKey()) {
                return "HashCompositeKeySpace";
            }
            if ("long".equals(storagePrimitive) || "double".equals(storagePrimitive)) {
                return "HashLongKeySpace";
            }
            return "HashIntKeySpace";
        }

        String keySpaceConstruction(String plan, String expectedSize) {
            return "new " + keySpaceType() + "(" + expectedSize + ")";
        }

        String appendValidationKeySpaceType() {
            return keySpaceType();
        }

        String appendValidationKeySpaceConstruction(String expectedSize) {
            return "new " + appendValidationKeySpaceType() + "(" + expectedSize + ")";
        }

        String appendValidationKeySpaceImplementation() {
            if ("HashCompositeKeySpace".equals(appendValidationKeySpaceType())) {
                return "hash-composite-v2";
            }
            if ("HashLongKeySpace".equals(appendValidationKeySpaceType())) {
                return "hash-long-v2";
            }
            return "hash-int-v2";
        }

        String requireInsertKey(String space, String key, String operation) {
            return "";
        }

        String keySpaceImplementation() {
            if (compositeKey()) return "hash-composite-v2";
            if ("long".equals(storagePrimitive) || "double".equals(storagePrimitive)) {
                return "hash-long-v2";
            }
            return "hash-int-v2";
        }

        String keySpaceValue(String expression, String operation) {
            return keySpaceValueExpression(expression, q(operation));
        }

        String keySpaceValueExpression(String expression, String operationExpression) {
            if (valueType != null) {
                return keySpaceValueFromStorageExpression(
                        "RuntimeFailures.requiredValue(TABLE," + q(logicalName) + ","
                                + expression + "," + operationExpression + ")." + valueLeafJavaName,
                        operationExpression);
            }
            return keySpaceValueFromStorageExpression(expression, operationExpression);
        }

        String keySpaceValueFromStorage(String expression, String operation) {
            return keySpaceValueFromStorageExpression(expression, q(operation));
        }

        String keySpaceValueFromStorageExpression(String expression, String operationExpression) {
            if (enumType != null) {
                return "RuntimeFailures.requiredEnumValue(TABLE," + q(logicalName) + ","
                        + expression + "," + operationExpression + ").ordinal()";
            }
            if ("boolean".equals(storagePrimitive)) {
                return "(" + expression + "?1:0)";
            }
            if ("byte".equals(storagePrimitive) || "short".equals(storagePrimitive)
                    || "int".equals(storagePrimitive) || "long".equals(storagePrimitive)) {
                return expression;
            }
            if ("float".equals(storagePrimitive)) {
                return "KeyCanonicalization.strictFloatKeyBits(TABLE,"
                        + q(logicalName) + "," + expression + "," + operationExpression + ")";
            }
            if ("double".equals(storagePrimitive)) {
                return "KeyCanonicalization.strictDoubleKeyBits(TABLE,"
                        + q(logicalName) + "," + expression + "," + operationExpression + ")";
            }
            throw new IllegalStateException("unsupported primitive key: " + primitive);
        }

        boolean valueBacked() {
            return valueType != null;
        }

        boolean compositeValueKey() {
            return key && flattenedValueStorage();
        }

        boolean stringKey() {
            return key && "java.lang.String".equals(primitive);
        }

        boolean compositeKey() {
            return compositeValueKey() || stringKey();
        }

        boolean flattenedValueStorage() {
            if (valueType == null || valueLeaves.isEmpty()) {
                return false;
            }
            if (!key) {
                return true;
            }
            ValueLeafSpec first = valueLeaves.get(0);
            String scalarTemplate = "new " + valueType + "(@{0}@)";
            return valueLeaves.size() > 1
                    || first.enumType != null
                    || "java.lang.String".equals(first.storagePrimitive)
                    || !scalarTemplate.equals(valueConstructionTemplate);
        }

        String keySpaceValueType() {
            return ("long".equals(storagePrimitive) || "double".equals(storagePrimitive))
                    ? "long" : "int";
        }

        String different(String left, String right) {
            if ("java.lang.String".equals(primitive)) {
                return "!" + left + ".equals(" + right + ")";
            }
            if ("float".equals(primitive)) {
                return "Float.floatToIntBits(" + left + ")!=Float.floatToIntBits(" + right + ")";
            }
            if ("double".equals(primitive)) {
                return "Double.doubleToLongBits(" + left + ")!=Double.doubleToLongBits(" + right + ")";
            }
            return left + "!=" + right;
        }

        int jvmSlots() { return "long".equals(primitive) || "double".equals(primitive) ? 2 : 1; }

        int bytes() {
            if ("boolean".equals(storagePrimitive) || "byte".equals(storagePrimitive)) return 1;
            if ("short".equals(storagePrimitive)) return 2;
            if ("int".equals(storagePrimitive) || "float".equals(storagePrimitive)) return 4;
            return 8;
        }

        boolean supportsColumnAccess() {
            return valueType == null && !"java.lang.String".equals(primitive);
        }

        String columnTraversalType() {
            return enumType == null ? cap(primitive) + "ColumnTraversal"
                    : "EnumColumnTraversal<" + enumType + ">";
        }

        String columnViewType() {
            return enumType == null ? cap(primitive) + "ColumnView"
                    : "EnumColumnView<" + enumType + ">";
        }

        String columnTraversalConstruction(String presence) {
            String operation = logicalName + ".values";
            if (enumType == null) {
                return "GeneratedColumnAccess." + primitive + "Traversal(state,"
                        + javaName + "Column," + presence
                        + ",TABLE," + q(operation) + ","
                        + q(operation + ".consumer") + ")";
            }
            return "GeneratedColumnAccess.enumTraversal(state," + javaName + "Column,"
                    + presence + ",TABLE," + q(operation) + ","
                    + q(operation + ".consumer") + "," + enumConstantsName() + ")";
        }

        String columnViewConstruction(String presence) {
            String path = logicalName + ".column";
            String getter = enumType == null ? "get" + cap(primitive) : "get";
            if (enumType == null) {
                return "GeneratedColumnAccess." + primitive + "View(state,"
                        + javaName + "Column," + presence
                        + ",TABLE," + q(logicalName) + "," + q(path) + ","
                        + q(path + ".isPresent") + "," + q(path + "." + getter) + ")";
            }
            return "GeneratedColumnAccess.enumView(state," + javaName + "Column,"
                    + presence + ",TABLE," + q(logicalName) + "," + q(path) + ","
                    + q(path + ".isPresent") + "," + q(path + "." + getter) + ","
                    + enumConstantsName() + ")";
        }
    }

    static final class ValueLeafSpec {
        final String javaName;
        final String storageName;
        final String logicalName;
        final String semantic;
        final String primitive;
        final String storagePrimitive;
        final String columnType;
        final String enumType;

        ValueLeafSpec(
                String javaName, String storageName, String logicalName, String semantic,
                String primitive, String storagePrimitive, String columnType,
                String enumType) {
            this.javaName = javaName;
            this.storageName = storageName;
            this.logicalName = logicalName;
            this.semantic = semantic;
            this.primitive = primitive;
            this.storagePrimitive = storagePrimitive;
            this.columnType = columnType;
            this.enumType = enumType;
        }

        String physicalName(FieldSpec owner) {
            int index = owner.valueLeaves.indexOf(this);
            if (index < 0) {
                throw new IllegalStateException("value leaf is not owned by field");
            }
            return owner.javaName + "Leaf" + index;
        }

        String stem(FieldSpec owner) {
            String suffix = cap(storageName);
            if (!suffix.endsWith("Value")) {
                suffix += "Value";
            }
            return owner.javaName + suffix;
        }

        String columnName(FieldSpec owner) {
            return owner.flattenedValueStorage()
                    ? physicalName(owner) + "Column" : owner.javaName + "Column";
        }

        String updateValue(FieldSpec owner, int fieldIndex, String row) {
            return updateMethod(fieldIndex) + "(" + row + ")";
        }

        String updateMethod(int fieldIndex) {
            return "updateLeaf" + fieldIndex + "_" + storageName + "Value";
        }

        String setUpdateMethod(int fieldIndex) {
            return "setUpdateLeaf" + fieldIndex + "_" + storageName;
        }

        boolean supportsColumnAccess() {
            return !"java.lang.String".equals(storagePrimitive);
        }

        String columnTraversalType() {
            return enumType == null ? cap(primitive) + "ColumnTraversal"
                    : "EnumColumnTraversal<" + enumType + ">";
        }

        String columnViewType() {
            return enumType == null ? cap(primitive) + "ColumnView"
                    : "EnumColumnView<" + enumType + ">";
        }

        String columnTraversalConstruction(FieldSpec owner, String presence) {
            String logicalPath = owner.logicalName + "." + logicalName;
            String operation = logicalPath + ".values";
            if (enumType == null) {
                return "GeneratedColumnAccess." + primitive + "Traversal(state,"
                        + columnName(owner) + "," + presence + ",TABLE,"
                        + q(operation) + "," + q(operation + ".consumer") + ")";
            }
            return "GeneratedColumnAccess.enumTraversal(state," + columnName(owner) + ","
                    + presence + ",TABLE," + q(operation) + ","
                    + q(operation + ".consumer") + "," + enumConstantsName(owner) + ")";
        }

        String columnViewConstruction(FieldSpec owner, String presence) {
            String logicalPath = owner.logicalName + "." + logicalName;
            String path = logicalPath + ".column";
            String getter = enumType == null ? "get" + cap(primitive) : "get";
            if (enumType == null) {
                return "GeneratedColumnAccess." + primitive + "View(state,"
                        + columnName(owner) + "," + presence + ",TABLE,"
                        + q(logicalPath) + "," + q(path) + ","
                        + q(path + ".isPresent") + "," + q(path + "." + getter) + ")";
            }
            return "GeneratedColumnAccess.enumView(state," + columnName(owner) + ","
                    + presence + ",TABLE," + q(logicalPath) + "," + q(path) + ","
                    + q(path + ".isPresent") + "," + q(path + "." + getter) + ","
                    + enumConstantsName(owner) + ")";
        }

        String storageZero() {
            if ("boolean".equals(storagePrimitive)) return "false";
            if ("byte".equals(storagePrimitive)) return "(byte)0";
            if ("short".equals(storagePrimitive)) return "(short)0";
            if ("long".equals(storagePrimitive)) return "0L";
            if ("float".equals(storagePrimitive)) return "0.0f";
            if ("double".equals(storagePrimitive)) return "0.0d";
            if ("java.lang.String".equals(storagePrimitive)) return "null";
            return "0";
        }

        String storageValue(
                FieldSpec owner, String expression, String operation) {
            if (owner.key && "float".equals(storagePrimitive)) {
                return "KeyCanonicalization.strictFloatStorage(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + q(operation) + ")";
            }
            if (owner.key && "double".equals(storagePrimitive)) {
                return "KeyCanonicalization.strictDoubleStorage(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + q(operation) + ")";
            }
            if (enumType != null) {
                return "RuntimeFailures.requiredEnumValue(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + q(operation) + ").ordinal()";
            }
            if ("java.lang.String".equals(storagePrimitive)) {
                return "RuntimeFailures.requiredValue(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + q(operation) + ")";
            }
            return expression;
        }

        String hashBits(FieldSpec owner, String expression, String operationExpression) {
            if ("boolean".equals(storagePrimitive)) {
                return "(" + expression + "?1L:0L)";
            }
            if ("byte".equals(storagePrimitive) || "short".equals(storagePrimitive)
                    || "int".equals(storagePrimitive) || "long".equals(storagePrimitive)) {
                return "(long)(" + expression + ")";
            }
            if ("float".equals(storagePrimitive)) {
                return "(long)KeyCanonicalization.strictFloatKeyBits(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + operationExpression + ")";
            }
            if ("double".equals(storagePrimitive)) {
                return "KeyCanonicalization.strictDoubleKeyBits(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + operationExpression + ")";
            }
            if ("java.lang.String".equals(storagePrimitive)) {
                return "(long)(" + expression + ").hashCode()";
            }
            throw new IllegalStateException("unsupported composite key leaf: " + primitive);
        }

        String keyEqual(
                FieldSpec owner, String left, String right, String operationExpression) {
            if ("float".equals(storagePrimitive)) {
                return hashBits(owner, left, operationExpression) + "=="
                        + hashBits(owner, right, operationExpression);
            }
            if ("double".equals(storagePrimitive)) {
                return hashBits(owner, left, operationExpression) + "=="
                        + hashBits(owner, right, operationExpression);
            }
            if ("java.lang.String".equals(storagePrimitive)) {
                return "(" + left + ").equals(" + right + ")";
            }
            return left + "==" + right;
        }

        String publicValue(FieldSpec owner, String storageExpression) {
            if (enumType != null) {
                return enumConstantsName(owner) + "[" + storageExpression + "]";
            }
            return storageExpression;
        }

        String keyInputStorage(
                FieldSpec owner, String expression, String operationExpression) {
            if (enumType != null) {
                return "RuntimeFailures.requiredEnumValue(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + operationExpression + ").ordinal()";
            }
            if ("java.lang.String".equals(storagePrimitive)) {
                return "RuntimeFailures.requiredValue(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + operationExpression + ")";
            }
            return expression;
        }

        int bytes() {
            if ("boolean".equals(storagePrimitive) || "byte".equals(storagePrimitive)) return 1;
            if ("short".equals(storagePrimitive)) return 2;
            if ("int".equals(storagePrimitive) || "float".equals(storagePrimitive)) return 4;
            return 8;
        }

        String enumConstantsName(FieldSpec owner) {
            return physicalName(owner).toUpperCase(java.util.Locale.ROOT)
                    + "_ENUM_VALUES";
        }
    }}
