package io.github.somaruntime.soma.processor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.lang.model.element.Element;

/** Immutable, fully validated schema model consumed by every generation phase. */
final class CompositionModel {

    private final String schemaPackage;
    private final String generatedPackage;
    private final String processorVersion;
    private final String contractVersion;
    private final String runtimeBuildIdentity;
    private final String fingerprint;
    private final List<String> schemaLines;
    private final List<ValueModel> values;
    private final List<TableModel> tables;
    private final List<Element> originatingElements;

    CompositionModel(
            String schemaPackage,
            String generatedPackage,
            String processorVersion,
            String contractVersion,
            String runtimeBuildIdentity,
            String fingerprint,
            List<String> schemaLines,
            List<ValueModel> values,
            List<TableModel> tables,
            List<? extends Element> originatingElements) {
        this.schemaPackage = schemaPackage;
        this.generatedPackage = generatedPackage;
        this.processorVersion = processorVersion;
        this.contractVersion = contractVersion;
        this.runtimeBuildIdentity = runtimeBuildIdentity;
        this.fingerprint = fingerprint;
        this.schemaLines = immutable(schemaLines);
        this.values = immutable(values);
        this.tables = immutable(tables);
        this.originatingElements = immutableElements(originatingElements);
    }

    String schemaPackage() {
        return schemaPackage;
    }

    String generatedPackage() {
        return generatedPackage;
    }

    String processorVersion() {
        return processorVersion;
    }

    String contractVersion() {
        return contractVersion;
    }

    String runtimeBuildIdentity() {
        return runtimeBuildIdentity;
    }

    String fingerprint() {
        return fingerprint;
    }

    List<String> schemaLines() {
        return schemaLines;
    }

    List<ValueModel> values() {
        return values;
    }

    List<TableModel> tables() {
        return tables;
    }

    List<Element> originatingElements() {
        return originatingElements;
    }

    String generatedTypeName() {
        return generatedPackage + ".SomaCompositionLinkage";
    }

    String manifestPath() {
        return "META-INF/soma/" + schemaPackage + ".properties";
    }

    static final class TableModel {

        private final String simpleName;
        private final long defaultCapacity;
        private final List<FieldModel> fields;
        private final List<LeafModel> leaves;

        TableModel(
                String simpleName,
                long defaultCapacity,
                List<FieldModel> fields,
                List<LeafModel> leaves) {
            this.simpleName = simpleName;
            this.defaultCapacity = defaultCapacity;
            this.fields = immutable(fields);
            this.leaves = immutable(leaves);
        }

        TableModel(String simpleName, long defaultCapacity, List<FieldModel> fields) {
            this(simpleName, defaultCapacity, fields, collectLeaves(fields));
        }

        String simpleName() {
            return simpleName;
        }

        long defaultCapacity() {
            return defaultCapacity;
        }

        List<FieldModel> fields() {
            return fields;
        }

        List<LeafModel> leaves() {
            return leaves;
        }

        int keyFieldIndex() {
            for (int index = 0; index < fields.size(); index++) {
                if (fields.get(index).role() == FieldRole.KEY) {
                    return index;
                }
            }
            return -1;
        }

        List<Integer> indexFieldIndexes() {
            List<Integer> result = new ArrayList<Integer>();
            for (int index = 0; index < fields.size(); index++) {
                if (fields.get(index).role() == FieldRole.INDEX) {
                    result.add(Integer.valueOf(index));
                }
            }
            return Collections.unmodifiableList(result);
        }

        private static List<LeafModel> collectLeaves(List<FieldModel> fields) {
            List<LeafModel> result = new ArrayList<LeafModel>();
            for (FieldModel field : fields) {
                result.addAll(field.type().leaves());
            }
            return result;
        }
    }

    static final class ValueModel {

        private final String schemaQualifiedName;
        private final String simpleName;
        private final String publicTypeName;
        private final List<FieldModel> fields;
        private final List<LeafModel> leaves;
        private final boolean keyable;

        ValueModel(
                String schemaQualifiedName,
                String simpleName,
                String publicTypeName,
                List<FieldModel> fields,
                List<LeafModel> leaves,
                boolean keyable) {
            this.schemaQualifiedName = schemaQualifiedName;
            this.simpleName = simpleName;
            this.publicTypeName = publicTypeName;
            this.fields = immutable(fields);
            this.leaves = immutable(leaves);
            this.keyable = keyable;
        }

        String schemaQualifiedName() {
            return schemaQualifiedName;
        }

        String simpleName() {
            return simpleName;
        }

        String publicTypeName() {
            return publicTypeName;
        }

        List<FieldModel> fields() {
            return fields;
        }

        List<LeafModel> leaves() {
            return leaves;
        }

        boolean keyable() {
            return keyable;
        }
    }

    static final class FieldModel {

        private final String name;
        private final TypeModel type;
        private final FieldRole role;
        private final int firstLeaf;

        FieldModel(String name, TypeModel type, FieldRole role, int firstLeaf) {
            this.name = name;
            this.type = type;
            this.role = role;
            this.firstLeaf = firstLeaf;
        }

        String name() {
            return name;
        }

        TypeModel type() {
            return type;
        }

        String typeName() {
            return type.publicTypeName();
        }

        FieldRole role() {
            return role;
        }

        int firstLeaf() {
            return firstLeaf;
        }

        int leafCount() {
            return type.leaves().size();
        }

    }

    static final class TypeModel {

        private final LogicalKind kind;
        private final String publicTypeName;
        private final String boxedTypeName;
        private final List<LeafModel> leaves;
        private final ValueModel value;
        private final boolean keyable;
        private final boolean naturalOrder;
        private final boolean intrinsicEquality;
        private final boolean nullable;

        TypeModel(
                LogicalKind kind,
                String publicTypeName,
                String boxedTypeName,
                List<LeafModel> leaves,
                ValueModel value,
                boolean keyable,
                boolean naturalOrder,
                boolean intrinsicEquality,
                boolean nullable) {
            this.kind = kind;
            this.publicTypeName = publicTypeName;
            this.boxedTypeName = boxedTypeName;
            this.leaves = immutable(leaves);
            this.value = value;
            this.keyable = keyable;
            this.naturalOrder = naturalOrder;
            this.intrinsicEquality = intrinsicEquality;
            this.nullable = nullable;
        }

        LogicalKind kind() {
            return kind;
        }

        String publicTypeName() {
            return publicTypeName;
        }

        String boxedTypeName() {
            return boxedTypeName;
        }

        List<LeafModel> leaves() {
            return leaves;
        }

        ValueModel value() {
            return value;
        }

        boolean keyable() {
            return keyable;
        }

        boolean naturalOrder() {
            return naturalOrder;
        }

        boolean intrinsicEquality() {
            return intrinsicEquality;
        }

        boolean nullable() {
            return nullable;
        }

        boolean primitive() {
            return kind.ordinal() <= LogicalKind.DOUBLE.ordinal();
        }
    }

    static final class LeafModel {

        private final LeafKind kind;
        private final EqualityKind equality;
        private final String publicTypeName;

        LeafModel(LeafKind kind, EqualityKind equality, String publicTypeName) {
            this.kind = kind;
            this.equality = equality;
            this.publicTypeName = publicTypeName;
        }

        LeafKind kind() {
            return kind;
        }

        EqualityKind equality() {
            return equality;
        }

        String publicTypeName() {
            return publicTypeName;
        }
    }

    enum FieldRole {
        KEY,
        INDEX,
        FIELD
    }

    enum LogicalKind {
        BOOLEAN,
        BYTE,
        SHORT,
        CHAR,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        STRING,
        ENUM,
        VALUE,
        OBJECT
    }

    enum LeafKind {
        BOOLEAN,
        BYTE,
        SHORT,
        CHAR,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        REFERENCE
    }

    enum EqualityKind {
        BOOLEAN,
        BYTE,
        SHORT,
        CHAR,
        INT,
        LONG,
        FLOAT_CANONICAL,
        DOUBLE_CANONICAL,
        STRING_CONTENT,
        ENUM_IDENTITY,
        OBJECT_IDENTITY
    }

    private static <T> List<T> immutable(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }

    private static List<Element> immutableElements(List<? extends Element> source) {
        return Collections.unmodifiableList(new ArrayList<Element>(source));
    }
}
