package io.github.somaruntime.soma.processor;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;
import io.github.somaruntime.soma.SomaValue;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

final class CompositionModelBuilder {

    private static final String GENERATED_TYPE = "SomaCompositionLinkage";

    private final Elements elements;
    private final Messager messager;

    CompositionModelBuilder(Elements elements, Messager messager) {
        this.elements = elements;
        this.messager = messager;
    }

    CompositionModel build(
            PackageElement schemaPackage,
            List<TypeElement> tables) {
        String schemaName = schemaPackage.getQualifiedName().toString();
        if (!schemaName.endsWith(".schema") || schemaName.length() == ".schema".length()) {
            error(schemaPackage, "[SOMA-1002] @SomaSchema package must end with .schema.");
            return null;
        }

        String generatedPackage = schemaName.substring(0, schemaName.length() - ".schema".length());
        if (isReservedGeneratedPackage(generatedPackage)) {
            error(schemaPackage, "[SOMA-1006] Generated package is reserved: " + generatedPackage + ".");
            return null;
        }
        if (tables.isEmpty()) {
            error(schemaPackage, "[SOMA-1003] Composition must contain at least one @SomaTable.");
            return null;
        }

        List<TypeElement> sortedTables = sortedTypes(tables);
        Map<String, TypeElement> reachableValues =
                new LinkedHashMap<String, TypeElement>();
        boolean valueGraphValid = discoverReachableValues(
                sortedTables, reachableValues, new LinkedHashSet<String>());
        List<TypeElement> sortedValues = sortedTypes(
                new ArrayList<TypeElement>(reachableValues.values()));
        List<String> lines = new ArrayList<String>();
        List<Element> origins = new ArrayList<Element>();
        origins.add(schemaPackage);
        lines.add("schema=" + schemaName);
        lines.add("generated=" + generatedPackage);
        lines.add("processor=" + ProcessorBuildInfo.artifactVersion());
        lines.add("contract=" + ProcessorBuildInfo.contractVersion());
        lines.add("runtime.build.sha256=" + ProcessorBuildInfo.runtimeBuildIdentity());

        boolean valid = valueGraphValid;
        for (TypeElement table : sortedTables) {
            valid &= validateType(table, schemaPackage, "@SomaTable");
            SomaTable annotation = table.getAnnotation(SomaTable.class);
            if (annotation != null && annotation.defaultCapacity() < 0L) {
                error(table, "[SOMA-1010] @SomaTable defaultCapacity must be non-negative.");
                valid = false;
            }
            valid &= appendFields(lines, table, true);
            lines.add("table=" + table.getQualifiedName()
                    + "|defaultCapacity="
                    + (annotation == null ? "?" : Long.toString(annotation.defaultCapacity())));
            origins.add(table);
        }
        for (TypeElement value : sortedValues) {
            valid &= validateType(value, schemaPackage, "@SomaValue");
            valid &= appendFields(lines, value, false);
            lines.add("value=" + value.getQualifiedName());
            origins.add(value);
        }

        String generatedTypeName = generatedPackage + "." + GENERATED_TYPE;
        TypeElement collision = elements.getTypeElement(generatedTypeName);
        if (collision != null) {
            error(collision, "[SOMA-1007] Generated type already exists: " + generatedTypeName + ".");
            valid = false;
        }
        if (!valid) {
            return null;
        }

        return new CompositionModel(
                schemaName,
                generatedPackage,
                ProcessorBuildInfo.artifactVersion(),
                ProcessorBuildInfo.contractVersion(),
                ProcessorBuildInfo.runtimeBuildIdentity(),
                sha256(lines),
                lines,
                origins);
    }

    private boolean discoverReachableValues(
            List<TypeElement> roots,
            Map<String, TypeElement> discovered,
            Set<String> visiting) {
        boolean valid = true;
        for (TypeElement root : roots) {
            for (VariableElement field : ElementFilter.fieldsIn(root.getEnclosedElements())) {
                valid &= discoverReachableValue(field.asType(), discovered, visiting);
            }
        }
        return valid;
    }

    private boolean discoverReachableValue(
            TypeMirror mirror,
            Map<String, TypeElement> discovered,
            Set<String> visiting) {
        if (!(mirror instanceof DeclaredType)) {
            return true;
        }
        Element element = ((DeclaredType) mirror).asElement();
        if (!(element instanceof TypeElement)) {
            return true;
        }
        TypeElement type = (TypeElement) element;
        if (type.getAnnotation(SomaValue.class) == null) {
            return true;
        }

        String identity = type.getQualifiedName().toString();
        if (visiting.contains(identity)) {
            error(type, "[SOMA-1014] @SomaValue graph must be acyclic.");
            return false;
        }
        if (discovered.containsKey(identity)) {
            return true;
        }

        discovered.put(identity, type);
        visiting.add(identity);
        boolean valid = true;
        for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            valid &= discoverReachableValue(field.asType(), discovered, visiting);
        }
        visiting.remove(identity);
        return valid;
    }

    private boolean validateType(
            TypeElement type,
            PackageElement schemaPackage,
            String annotationName) {
        boolean valid = true;
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
            valid = false;
        }
        return valid;
    }

    private boolean appendFields(List<String> lines, TypeElement type, boolean table) {
        List<VariableElement> fields = ElementFilter.fieldsIn(type.getEnclosedElements());
        if (fields.isEmpty()) {
            error(type, "[SOMA-1005] SOMA schema type must declare at least one field.");
            return false;
        }

        boolean valid = true;
        int roleFields = 0;
        for (VariableElement field : fields) {
            int roles = roleCount(field);
            if (roles != 1) {
                error(field, "[SOMA-1008] Each schema field must declare exactly one SOMA role.");
                valid = false;
                continue;
            }
            if (!table && (field.getAnnotation(SomaKey.class) != null
                    || field.getAnnotation(SomaIndex.class) != null)) {
                error(field, "[SOMA-1009] @SomaValue fields only support @SomaField.");
                valid = false;
            }
            roleFields++;
            lines.add("field=" + type.getQualifiedName()
                    + "#" + field.getSimpleName()
                    + "|role=" + role(field)
                    + "|type=" + field.asType());
        }
        if (roleFields == 0) {
            error(type, "[SOMA-1005] SOMA schema type must declare at least one SOMA field.");
            valid = false;
        }
        return valid;
    }

    private static int roleCount(VariableElement field) {
        int count = 0;
        count += field.getAnnotation(SomaField.class) == null ? 0 : 1;
        count += field.getAnnotation(SomaKey.class) == null ? 0 : 1;
        count += field.getAnnotation(SomaIndex.class) == null ? 0 : 1;
        return count;
    }

    private static String role(VariableElement field) {
        if (field.getAnnotation(SomaKey.class) != null) {
            return "KEY";
        }
        if (field.getAnnotation(SomaIndex.class) != null) {
            return "INDEX";
        }
        return "FIELD";
    }

    private static List<TypeElement> sortedTypes(List<TypeElement> types) {
        List<TypeElement> sorted = new ArrayList<TypeElement>(types);
        Collections.sort(sorted, new Comparator<TypeElement>() {
            @Override
            public int compare(TypeElement left, TypeElement right) {
                return left.getQualifiedName().toString()
                        .compareTo(right.getQualifiedName().toString());
            }
        });
        return sorted;
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
    }
}
