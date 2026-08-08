package io.github.somaruntime.soma.processor;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;
import io.github.somaruntime.soma.SomaValue;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/** Builds the one authoritative semantic/type/leaf model for a composition. */
final class CompositionModelBuilder {

    private final Elements elements;
    private final Types types;
    private final Messager messager;
    private final SourceShapeInspector sourceShapeInspector;
    private PackageElement schemaPackage;
    private String generatedPackage;
    private final Map<String, TypeElement> valueElements =
            new LinkedHashMap<String, TypeElement>();
    private final Map<String, CompositionModel.ValueModel> valueModels =
            new LinkedHashMap<String, CompositionModel.ValueModel>();
    private final Set<String> valuesBeingBuilt = new LinkedHashSet<String>();
    private boolean valid;

    CompositionModelBuilder(
            Elements elements,
            Types types,
            Messager messager,
            SourceShapeInspector sourceShapeInspector) {
        this.elements = elements;
        this.types = types;
        this.messager = messager;
        this.sourceShapeInspector = sourceShapeInspector;
    }

    CompositionModel build(PackageElement packageElement, List<TypeElement> tables) {
        this.schemaPackage = packageElement;
        this.valid = true;
        valueElements.clear();
        valueModels.clear();
        valuesBeingBuilt.clear();

        String schemaName = packageElement.getQualifiedName().toString();
        if (!schemaName.endsWith(".schema") || schemaName.length() == ".schema".length()) {
            error(packageElement, "[SOMA-1002] @SomaSchema package must end with .schema.");
            return null;
        }
        generatedPackage = schemaName.substring(0, schemaName.length() - ".schema".length());
        if (isReservedGeneratedPackage(generatedPackage)) {
            error(packageElement, "[SOMA-1006] Generated package is reserved: "
                    + generatedPackage + ".");
            return null;
        }
        if (!validQualifiedName(schemaName) || !validQualifiedName(generatedPackage)) {
            error(packageElement, "[SOMA-1024] Schema package contains an unsafe identifier.");
            return null;
        }
        if (tables.isEmpty()) {
            error(packageElement, "[SOMA-1003] Composition must contain at least one @SomaTable.");
            return null;
        }

        List<TypeElement> sortedTables = sortedTypes(tables);
        discoverReachableValues(sortedTables);
        List<TypeElement> sortedValues = sortedTypes(
                new ArrayList<TypeElement>(valueElements.values()));

        for (TypeElement table : sortedTables) {
            validateSchemaType(table, "@SomaTable", true);
        }
        for (TypeElement value : sortedValues) {
            validateSchemaType(value, "@SomaValue", false);
        }
        if (!valid) {
            return null;
        }

        List<CompositionModel.ValueModel> values =
                new ArrayList<CompositionModel.ValueModel>();
        for (TypeElement value : sortedValues) {
            CompositionModel.ValueModel model = buildValue(value);
            if (model != null) {
                values.add(model);
            }
        }

        List<CompositionModel.TableModel> tableModels =
                new ArrayList<CompositionModel.TableModel>();
        for (TypeElement table : sortedTables) {
            CompositionModel.TableModel model = buildTable(table);
            if (model != null) {
                tableModels.add(model);
            }
        }
        if (!valid) {
            return null;
        }

        List<String> lines = schemaLines(schemaName, tableModels, values);
        List<Element> origins = new ArrayList<Element>();
        origins.add(packageElement);
        origins.addAll(sortedTables);
        origins.addAll(sortedValues);
        CompositionModel model = new CompositionModel(
                schemaName,
                generatedPackage,
                ProcessorBuildInfo.artifactVersion(),
                ProcessorBuildInfo.contractVersion(),
                ProcessorBuildInfo.runtimeBuildIdentity(),
                sha256(lines),
                lines,
                values,
                tableModels,
                origins);
        if (!new GeneratedSymbolTable(elements, messager, packageElement).validate(model)) {
            return null;
        }
        return model;
    }

    private void discoverReachableValues(List<TypeElement> roots) {
        for (TypeElement root : roots) {
            for (VariableElement field : directFields(root)) {
                discoverValue(field.asType(), new LinkedHashSet<String>());
            }
        }
    }

    private void discoverValue(TypeMirror mirror, Set<String> visiting) {
        if (mirror.getKind() != TypeKind.DECLARED) {
            return;
        }
        Element element = ((DeclaredType) mirror).asElement();
        if (!(element instanceof TypeElement)) {
            return;
        }
        TypeElement type = (TypeElement) element;
        if (type.getAnnotation(SomaValue.class) == null) {
            return;
        }
        String identity = type.getQualifiedName().toString();
        if (!visiting.add(identity)) {
            error(type, "[SOMA-1014] @SomaValue graph must be acyclic.");
            return;
        }
        valueElements.put(identity, type);
        for (VariableElement field : directFields(type)) {
            discoverValue(field.asType(), visiting);
        }
        visiting.remove(identity);
    }

    private void validateSchemaType(
            TypeElement type,
            String annotationName,
            boolean table) {
        if (type.getEnclosingElement().getKind() != ElementKind.PACKAGE
                || !elements.getPackageOf(type).getQualifiedName().contentEquals(
                        schemaPackage.getQualifiedName())
                || type.getKind() != ElementKind.CLASS
                || !type.getModifiers().contains(Modifier.FINAL)
                || type.getModifiers().contains(Modifier.PUBLIC)
                || type.getModifiers().contains(Modifier.PROTECTED)
                || type.getModifiers().contains(Modifier.PRIVATE)) {
            error(type, "[SOMA-1004] " + annotationName
                    + " must be a package-private top-level final class in the schema package.");
        }
        if (!validIdentifier(type.getSimpleName().toString(), false)) {
            error(type, "[SOMA-1024] Schema type name is not a stable visible identifier.");
        }
        if (!type.getTypeParameters().isEmpty()
                || !type.getInterfaces().isEmpty()
                || !(type.getSuperclass() instanceof NoType)
                && !"java.lang.Object".equals(type.getSuperclass().toString())) {
            error(type, "[SOMA-1017] SOMA schema declarations cannot inherit or declare type parameters.");
        }

        SourceShapeInspector.Result sourceShape = sourceShapeInspector.inspect(type);
        if (sourceShape == SourceShapeInspector.Result.DECLARATION_MEMBER) {
            error(type, "[SOMA-1017] SOMA schema declarations contain only fields and annotations.");
        } else if (sourceShape == SourceShapeInspector.Result.FIELD_INITIALIZER) {
            error(type, "[SOMA-1018] SOMA fields cannot declare an initializer.");
        } else if (sourceShape == SourceShapeInspector.Result.UNSAFE_SOURCE) {
            error(type, "[SOMA-1024] Schema source contains an unsafe hidden code point.");
        } else if (sourceShape == SourceShapeInspector.Result.UNAVAILABLE) {
            error(type, "[SOMA-1025] Compiler cannot prove the full SOMA source declaration shape.");
        }

        for (Element member : type.getEnclosedElements()) {
            if (member.getKind() == ElementKind.CLASS
                    || member.getKind() == ElementKind.ENUM
                    || member.getKind() == ElementKind.INTERFACE) {
                error(type, "[SOMA-1017] SOMA schema declarations contain only fields and annotations.");
            }
        }

        List<VariableElement> fields = directFields(type);
        if (fields.isEmpty()) {
            error(type, "[SOMA-1005] SOMA schema type must declare at least one field.");
            return;
        }
        int keys = 0;
        for (VariableElement field : fields) {
            if (!validIdentifier(field.getSimpleName().toString(), true)) {
                error(field, "[SOMA-1024] Field name is not a stable visible identifier.");
            }
            if (field.getModifiers().contains(Modifier.STATIC)
                    || field.getModifiers().contains(Modifier.FINAL)
                    || field.getModifiers().contains(Modifier.TRANSIENT)
                    || field.getModifiers().contains(Modifier.VOLATILE)) {
                error(field, "[SOMA-1018] SOMA fields must be mutable instance fields without modifiers.");
            }
            int roles = roleCount(field);
            if (roles != 1) {
                error(field, "[SOMA-1008] Each schema field must declare exactly one SOMA role.");
            }
            if (!table && (field.getAnnotation(SomaKey.class) != null
                    || field.getAnnotation(SomaIndex.class) != null)) {
                error(field, "[SOMA-1009] @SomaValue fields only support @SomaField.");
            }
            if (table && field.getAnnotation(SomaKey.class) != null) {
                keys++;
            }
        }
        if (table && keys > 1) {
            error(type, "[SOMA-1016] @SomaTable supports at most one @SomaKey Field.");
        }
        if (table) {
            SomaTable annotation = type.getAnnotation(SomaTable.class);
            if (annotation != null && annotation.defaultCapacity() < 0L) {
                error(type, "[SOMA-1010] @SomaTable defaultCapacity must be non-negative.");
            }
        }
    }

    private CompositionModel.ValueModel buildValue(TypeElement element) {
        String identity = element.getQualifiedName().toString();
        CompositionModel.ValueModel existing = valueModels.get(identity);
        if (existing != null) {
            return existing;
        }
        if (!valuesBeingBuilt.add(identity)) {
            error(element, "[SOMA-1014] @SomaValue graph must be acyclic.");
            return null;
        }
        List<CompositionModel.FieldModel> fields =
                new ArrayList<CompositionModel.FieldModel>();
        List<CompositionModel.LeafModel> leaves =
                new ArrayList<CompositionModel.LeafModel>();
        boolean keyable = true;
        for (VariableElement field : directFields(element)) {
            CompositionModel.TypeModel type = resolveType(field, true);
            if (type == null) {
                continue;
            }
            fields.add(new CompositionModel.FieldModel(
                    field.getSimpleName().toString(),
                    type,
                    CompositionModel.FieldRole.FIELD,
                    leaves.size()));
            leaves.addAll(type.leaves());
            keyable &= type.keyable();
        }
        valuesBeingBuilt.remove(identity);
        if (!valid) {
            return null;
        }
        CompositionModel.ValueModel result = new CompositionModel.ValueModel(
                identity,
                element.getSimpleName().toString(),
                generatedPackage + "." + element.getSimpleName(),
                fields,
                leaves,
                keyable);
        valueModels.put(identity, result);
        return result;
    }

    private CompositionModel.TableModel buildTable(TypeElement element) {
        List<CompositionModel.FieldModel> fields =
                new ArrayList<CompositionModel.FieldModel>();
        List<CompositionModel.LeafModel> leaves =
                new ArrayList<CompositionModel.LeafModel>();
        for (VariableElement field : directFields(element)) {
            CompositionModel.TypeModel type = resolveType(field, false);
            if (type == null) {
                continue;
            }
            CompositionModel.FieldRole role = fieldRole(field);
            if ((role == CompositionModel.FieldRole.KEY
                    || role == CompositionModel.FieldRole.INDEX)
                    && !type.keyable()) {
                error(field, "[SOMA-1022] @SomaKey/@SomaIndex requires a recursively keyable Field.");
            }
            fields.add(new CompositionModel.FieldModel(
                    field.getSimpleName().toString(), type, role, leaves.size()));
            leaves.addAll(type.leaves());
        }
        if (!valid) {
            return null;
        }
        SomaTable annotation = element.getAnnotation(SomaTable.class);
        return new CompositionModel.TableModel(
                element.getSimpleName().toString(),
                annotation == null ? 16L : annotation.defaultCapacity(),
                fields,
                leaves);
    }

    private CompositionModel.TypeModel resolveType(
            VariableElement field,
            boolean insideValue) {
        TypeMirror mirror = field.asType();
        CompositionModel.TypeModel result = primitiveType(mirror);
        if (result != null) {
            return result;
        }
        if (mirror.getKind() == TypeKind.DECLARED) {
            TypeElement type = (TypeElement) ((DeclaredType) mirror).asElement();
            String qualified = type.getQualifiedName().toString();
            if (type.getAnnotation(SomaValue.class) != null) {
                CompositionModel.ValueModel value = buildValue(type);
                if (value == null) {
                    return null;
                }
                return new CompositionModel.TypeModel(
                        CompositionModel.LogicalKind.VALUE,
                        value.publicTypeName(),
                        value.publicTypeName(),
                        value.leaves(),
                        value,
                        value.keyable(),
                        false,
                        true,
                        false);
            }
            if ("java.lang.String".equals(qualified)) {
                return referenceType(
                        CompositionModel.LogicalKind.STRING,
                        mirror.toString(),
                        CompositionModel.EqualityKind.STRING_CONTENT,
                        true,
                        true,
                        true);
            }
            if (type.getKind() == ElementKind.ENUM) {
                if (!sourceAccessible(mirror)) {
                    inaccessible(field, mirror);
                    return null;
                }
                return referenceType(
                        CompositionModel.LogicalKind.ENUM,
                        mirror.toString(),
                        CompositionModel.EqualityKind.ENUM_IDENTITY,
                        true,
                        true,
                        true);
            }
            if (!sourceAccessible(mirror)) {
                inaccessible(field, mirror);
                return null;
            }
            if (insideValue) {
                error(field, "[SOMA-1023] @SomaValue cannot contain an ordinary Object Field.");
                return null;
            }
            return referenceType(
                    CompositionModel.LogicalKind.OBJECT,
                    mirror.toString(),
                    CompositionModel.EqualityKind.OBJECT_IDENTITY,
                    false,
                    false,
                    false);
        }
        if (mirror.getKind() == TypeKind.ARRAY) {
            if (!sourceAccessible(mirror)) {
                inaccessible(field, mirror);
                return null;
            }
            if (insideValue) {
                error(field, "[SOMA-1023] @SomaValue cannot contain an ordinary Object Field.");
                return null;
            }
            return referenceType(
                    CompositionModel.LogicalKind.OBJECT,
                    mirror.toString(),
                    CompositionModel.EqualityKind.OBJECT_IDENTITY,
                    false,
                    false,
                    false);
        }
        error(field, "[SOMA-1020] Unsupported SOMA Field type: " + mirror + ".");
        return null;
    }

    private CompositionModel.TypeModel primitiveType(TypeMirror mirror) {
        if (!mirror.getKind().isPrimitive()) {
            return null;
        }
        TypeKind kind = mirror.getKind();
        CompositionModel.LogicalKind logical;
        CompositionModel.LeafKind leaf;
        CompositionModel.EqualityKind equality;
        String boxed;
        boolean keyable = true;
        boolean ordered = true;
        switch (kind) {
            case BOOLEAN:
                logical = CompositionModel.LogicalKind.BOOLEAN;
                leaf = CompositionModel.LeafKind.BOOLEAN;
                equality = CompositionModel.EqualityKind.BOOLEAN;
                boxed = "java.lang.Boolean";
                ordered = false;
                break;
            case BYTE:
                logical = CompositionModel.LogicalKind.BYTE;
                leaf = CompositionModel.LeafKind.BYTE;
                equality = CompositionModel.EqualityKind.BYTE;
                boxed = "java.lang.Byte";
                break;
            case SHORT:
                logical = CompositionModel.LogicalKind.SHORT;
                leaf = CompositionModel.LeafKind.SHORT;
                equality = CompositionModel.EqualityKind.SHORT;
                boxed = "java.lang.Short";
                break;
            case CHAR:
                logical = CompositionModel.LogicalKind.CHAR;
                leaf = CompositionModel.LeafKind.CHAR;
                equality = CompositionModel.EqualityKind.CHAR;
                boxed = "java.lang.Character";
                break;
            case INT:
                logical = CompositionModel.LogicalKind.INT;
                leaf = CompositionModel.LeafKind.INT;
                equality = CompositionModel.EqualityKind.INT;
                boxed = "java.lang.Integer";
                break;
            case LONG:
                logical = CompositionModel.LogicalKind.LONG;
                leaf = CompositionModel.LeafKind.LONG;
                equality = CompositionModel.EqualityKind.LONG;
                boxed = "java.lang.Long";
                break;
            case FLOAT:
                logical = CompositionModel.LogicalKind.FLOAT;
                leaf = CompositionModel.LeafKind.FLOAT;
                equality = CompositionModel.EqualityKind.FLOAT_CANONICAL;
                boxed = "java.lang.Float";
                keyable = false;
                break;
            case DOUBLE:
                logical = CompositionModel.LogicalKind.DOUBLE;
                leaf = CompositionModel.LeafKind.DOUBLE;
                equality = CompositionModel.EqualityKind.DOUBLE_CANONICAL;
                boxed = "java.lang.Double";
                keyable = false;
                break;
            default:
                return null;
        }
        List<CompositionModel.LeafModel> leaves =
                Collections.singletonList(new CompositionModel.LeafModel(
                        leaf, equality, mirror.toString()));
        return new CompositionModel.TypeModel(
                logical,
                mirror.toString(),
                boxed,
                leaves,
                null,
                keyable,
                ordered,
                true,
                false);
    }

    private static CompositionModel.TypeModel referenceType(
            CompositionModel.LogicalKind logical,
            String typeName,
            CompositionModel.EqualityKind equality,
            boolean keyable,
            boolean ordered,
            boolean intrinsicEquality) {
        List<CompositionModel.LeafModel> leaves =
                Collections.singletonList(new CompositionModel.LeafModel(
                        CompositionModel.LeafKind.REFERENCE,
                        equality,
                        typeName));
        return new CompositionModel.TypeModel(
                logical,
                typeName,
                typeName,
                leaves,
                null,
                keyable,
                ordered,
                intrinsicEquality,
                true);
    }

    private boolean sourceAccessible(TypeMirror mirror) {
        switch (mirror.getKind()) {
            case ARRAY:
                return sourceAccessible(((ArrayType) mirror).getComponentType());
            case DECLARED:
                DeclaredType declared = (DeclaredType) mirror;
                if (!typeAccessible((TypeElement) declared.asElement())) {
                    return false;
                }
                TypeMirror enclosing = declared.getEnclosingType();
                if (enclosing != null && enclosing.getKind() != TypeKind.NONE
                        && !sourceAccessible(enclosing)) {
                    return false;
                }
                for (TypeMirror argument : declared.getTypeArguments()) {
                    if (!sourceAccessible(argument)) {
                        return false;
                    }
                }
                return true;
            case WILDCARD:
                WildcardType wildcard = (WildcardType) mirror;
                return (wildcard.getExtendsBound() == null
                        || sourceAccessible(wildcard.getExtendsBound()))
                        && (wildcard.getSuperBound() == null
                        || sourceAccessible(wildcard.getSuperBound()));
            case TYPEVAR:
                TypeVariable variable = (TypeVariable) mirror;
                return sourceAccessible(variable.getUpperBound())
                        && sourceAccessible(variable.getLowerBound());
            case NULL:
            case NONE:
                return true;
            default:
                return mirror.getKind().isPrimitive();
        }
    }

    private boolean typeAccessible(TypeElement type) {
        Element current = type;
        while (current instanceof TypeElement) {
            TypeElement currentType = (TypeElement) current;
            String ownerPackage = elements.getPackageOf(currentType)
                    .getQualifiedName().toString();
            boolean samePackage = generatedPackage.equals(ownerPackage);
            Set<Modifier> modifiers = currentType.getModifiers();
            if (currentType.getNestingKind() == NestingKind.TOP_LEVEL) {
                if (!modifiers.contains(Modifier.PUBLIC) && !samePackage) {
                    return false;
                }
            } else if (modifiers.contains(Modifier.PRIVATE)
                    || !modifiers.contains(Modifier.PUBLIC) && !samePackage) {
                return false;
            }
            current = currentType.getEnclosingElement();
        }
        return true;
    }

    private void inaccessible(VariableElement field, TypeMirror mirror) {
        error(field, "[SOMA-1021] Field type is not accessible from generated package: "
                + mirror + ".");
    }

    private List<String> schemaLines(
            String schemaName,
            List<CompositionModel.TableModel> tables,
            List<CompositionModel.ValueModel> values) {
        List<String> lines = new ArrayList<String>();
        lines.add("schema=" + schemaName);
        lines.add("generated=" + generatedPackage);
        lines.add("processor=" + ProcessorBuildInfo.artifactVersion());
        lines.add("contract=" + ProcessorBuildInfo.contractVersion());
        lines.add("runtime.build.sha256=" + ProcessorBuildInfo.runtimeBuildIdentity());
        for (CompositionModel.TableModel table : tables) {
            lines.add("table=" + schemaName + "." + table.simpleName()
                    + "|defaultCapacity=" + table.defaultCapacity());
            appendFieldLines(lines, "table", table.simpleName(), table.fields());
        }
        for (CompositionModel.ValueModel value : values) {
            lines.add("value=" + value.schemaQualifiedName());
            appendFieldLines(lines, "value", value.simpleName(), value.fields());
        }
        return lines;
    }

    private static void appendFieldLines(
            List<String> lines,
            String ownerKind,
            String owner,
            List<CompositionModel.FieldModel> fields) {
        for (CompositionModel.FieldModel field : fields) {
            lines.add("field=" + ownerKind + ":" + owner + "#" + field.name()
                    + "|role=" + field.role()
                    + "|type=" + field.typeName()
                    + "|leafStart=" + field.firstLeaf()
                    + "|leafCount=" + field.leafCount());
        }
    }

    private static List<VariableElement> directFields(TypeElement type) {
        return ElementFilter.fieldsIn(type.getEnclosedElements());
    }

    private static CompositionModel.FieldRole fieldRole(VariableElement field) {
        if (field.getAnnotation(SomaKey.class) != null) {
            return CompositionModel.FieldRole.KEY;
        }
        if (field.getAnnotation(SomaIndex.class) != null) {
            return CompositionModel.FieldRole.INDEX;
        }
        return CompositionModel.FieldRole.FIELD;
    }

    private static int roleCount(VariableElement field) {
        int count = 0;
        count += field.getAnnotation(SomaField.class) == null ? 0 : 1;
        count += field.getAnnotation(SomaKey.class) == null ? 0 : 1;
        count += field.getAnnotation(SomaIndex.class) == null ? 0 : 1;
        return count;
    }

    private static List<TypeElement> sortedTypes(List<TypeElement> source) {
        List<TypeElement> result = new ArrayList<TypeElement>(source);
        Collections.sort(result, new Comparator<TypeElement>() {
            @Override
            public int compare(TypeElement left, TypeElement right) {
                return left.getQualifiedName().toString()
                        .compareTo(right.getQualifiedName().toString());
            }
        });
        return result;
    }

    private static boolean validQualifiedName(String value) {
        int start = 0;
        while (start < value.length()) {
            int end = value.indexOf('.', start);
            if (end < 0) {
                end = value.length();
            }
            if (!validIdentifier(value.substring(start, end), false)) {
                return false;
            }
            start = end + 1;
        }
        return true;
    }

    private static boolean validIdentifier(String value, boolean field) {
        if (value.isEmpty()
                || value.indexOf('$') >= 0
                || field && value.charAt(0) == '_'
                || !Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
            return false;
        }
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (Character.isIdentifierIgnorable(codePoint)
                    || Character.getType(codePoint) == Character.FORMAT
                    || Character.isISOControl(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean isReservedGeneratedPackage(String packageName) {
        return packageName.equals("io.github.somaruntime.soma")
                || packageName.startsWith("io.github.somaruntime.soma.")
                || packageName.equals("java")
                || packageName.startsWith("java.")
                || packageName.equals("javax")
                || packageName.startsWith("javax.")
                || packageName.equals("jdk")
                || packageName.startsWith("jdk.")
                || packageName.equals("sun")
                || packageName.startsWith("sun.");
    }

    private static String sha256(List<String> lines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String line : lines) {
                digest.update(line.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            byte[] value = digest.digest();
            StringBuilder result = new StringBuilder(value.length * 2);
            for (byte item : value) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required by Java 8", exception);
        }
    }

    private void error(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
        valid = false;
    }
}
