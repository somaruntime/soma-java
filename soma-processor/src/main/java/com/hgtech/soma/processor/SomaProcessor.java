package com.hgtech.soma.processor;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIgnore;
import com.hgtech.soma.annotation.SomaSchema;
import com.hgtech.soma.annotation.SomaSemantic;
import com.hgtech.soma.annotation.SomaValue;
import com.hgtech.soma.processor.internal.CompilerProtocol;

import com.sun.tools.javac.processing.JavacProcessingEnvironment;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * SOMA Java 8 schema collector、validator、normalizer 与 schema hash processor。
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({
        "com.hgtech.soma.annotation.SomaSchema",
        "com.hgtech.soma.annotation.SomaValue"
})
public final class SomaProcessor extends AbstractProcessor {
    private static final char[] LOWER_HEX = "0123456789abcdef".toCharArray();
    private static final String GENERATED_TARGET = "java8-columnar";
    private static final String SCHEMA_HASH_PREFIX = "soma-java:v1:schema\n";

    private final Map<String, TypeElement> values = new LinkedHashMap<String, TypeElement>();
    private final Map<String, PackageElement> schemaPackages =
            new LinkedHashMap<String, PackageElement>();
    private boolean finished;
    private boolean hasErrors;
    private boolean activationValid;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        if (!"1.8".equals(System.getProperty("java.specification.version"))) {
            error(null, "SOMA-COMP-002", "SOMA processor requires full JDK 8 javac");
            return;
        }
        if (!(processingEnvironment instanceof JavacProcessingEnvironment)) {
            error(null, "SOMA-COMP-002",
                    "unsupported processing environment: "
                            + processingEnvironment.getClass().getName());
            return;
        }

        CompilerProtocol.Session compilerSession = CompilerProtocol.instance(
                ((JavacProcessingEnvironment) processingEnvironment).getContext());
        String pluginIdentity = compilerSession.getPluginIdentity();
        if (pluginIdentity == null) {
            error(null, "SOMA-COMP-001",
                    "SOMA processor requires javac plugin -Xplugin:SomaValue");
            return;
        }
        if (!CompilerProtocol.LOWERING_IDENTITY.equals(pluginIdentity)) {
            error(null, "SOMA-COMP-003",
                    "compiler lowering identity mismatch: " + pluginIdentity);
            return;
        }
        compilerSession.activateProcessor(CompilerProtocol.PROCESSOR_IDENTITY);
        activationValid = true;
        for (String diagnostic : compilerSession.getPluginDiagnostics()) {
            errorRaw(diagnostic);
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(SomaValue.class)) {
            if (element instanceof TypeElement) {
                TypeElement type = (TypeElement) element;
                values.put(type.getQualifiedName().toString(), type);
            }
        }
        for (Element element : roundEnvironment.getElementsAnnotatedWith(SomaSchema.class)) {
            if (element instanceof PackageElement) {
                PackageElement packageElement = (PackageElement) element;
                schemaPackages.put(packageElement.getQualifiedName().toString(), packageElement);
            }
        }

        if (roundEnvironment.processingOver() && !finished) {
            finished = true;
            finishProcessing();
        }
        return false;
    }

    private void finishProcessing() {
        if (!activationValid || hasErrors) {
            return;
        }

        Map<String, SchemaModel> schemas = new TreeMap<String, SchemaModel>();
        for (TypeElement value : values.values()) {
            ValueModel model = validateValue(value);
            if (model == null) {
                continue;
            }
            PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(value);
            String packageName = packageElement.getQualifiedName().toString();
            PackageElement declaredSchema = schemaPackages.get(packageName);
            if (declaredSchema == null) {
                error(value, "SOMA-SCHEMA-001",
                        "package must declare @SomaSchema in package-info.java");
                continue;
            }
            SchemaModel schema = schemas.get(packageName);
            if (schema == null) {
                schema = validateSchema(declaredSchema);
                if (schema == null) {
                    continue;
                }
                schemas.put(packageName, schema);
            }
            schema.addValue(model);
        }

        validateSchemaNames(schemas);
        for (SchemaModel schema : schemas.values()) {
            validateValueGraph(schema);
        }
        if (hasErrors) {
            return;
        }
        for (SchemaModel schema : schemas.values()) {
            writeSchemaResources(schema);
        }
    }

    private void validateSchemaNames(Map<String, SchemaModel> schemas) {
        Map<String, SchemaModel> byName = new TreeMap<String, SchemaModel>();
        for (SchemaModel schema : schemas.values()) {
            SchemaModel previous = byName.put(schema.name, schema);
            if (previous != null) {
                error(previous.origin, "SOMA-SCHEMA-005",
                        "duplicate schema logical name: " + schema.name);
                error(schema.origin, "SOMA-SCHEMA-005",
                        "duplicate schema logical name: " + schema.name);
            }
        }
    }

    private void validateValueGraph(SchemaModel schema) {
        Set<String> visiting = new HashSet<String>();
        Set<String> visited = new HashSet<String>();
        for (ValueModel value : schema.values.values()) {
            validateValueGraph(schema, value, visiting, visited);
        }
    }

    private void validateValueGraph(
            SchemaModel schema,
            ValueModel value,
            Set<String> visiting,
            Set<String> visited) {
        if (visited.contains(value.javaType)) {
            return;
        }
        if (!visiting.add(value.javaType)) {
            error(schema.origin, "SOMA-VALUE-007",
                    "cyclic nested value declaration: " + value.javaType);
            return;
        }
        for (FieldModel field : value.fields) {
            if (field.type.valueReference == null) {
                continue;
            }
            ValueModel referenced = schema.values.get(field.type.valueReference);
            if (referenced == null) {
                error(schema.origin, "SOMA-VALUE-008",
                        "nested value must belong to the same schema compilation: "
                                + field.type.valueReference);
                continue;
            }
            validateValueGraph(schema, referenced, visiting, visited);
        }
        visiting.remove(value.javaType);
        visited.add(value.javaType);
    }

    private SchemaModel validateSchema(PackageElement packageElement) {
        SomaSchema annotation = packageElement.getAnnotation(SomaSchema.class);
        if (annotation == null) {
            error(packageElement, "SOMA-SCHEMA-001", "missing @SomaSchema");
            return null;
        }
        String name = annotation.name();
        String generatedPackage = annotation.generatedPackage();
        String version = annotation.version();
        boolean valid = true;
        if (name.length() > 128 || !name.matches("[A-Za-z][A-Za-z0-9_]*")) {
            error(packageElement, "SOMA-SCHEMA-002", "invalid schema name: " + name);
            valid = false;
        }
        if (generatedPackage.length() > 255 || !SourceVersion.isName(generatedPackage)) {
            error(packageElement, "SOMA-SCHEMA-003",
                    "invalid generated package: " + generatedPackage);
            valid = false;
        }
        if (!validSchemaVersion(version)) {
            error(packageElement, "SOMA-SCHEMA-004", "invalid schema version label");
            valid = false;
        }
        return valid ? new SchemaModel(
                packageElement,
                packageElement.getQualifiedName().toString(),
                name,
                generatedPackage,
                version) : null;
    }

    private ValueModel validateValue(TypeElement type) {
        boolean valid = true;
        Set<Modifier> modifiers = type.getModifiers();
        if (type.getKind() != ElementKind.CLASS
                || type.getNestingKind() != NestingKind.TOP_LEVEL
                || !modifiers.contains(Modifier.PUBLIC)
                || !modifiers.contains(Modifier.FINAL)
                || !type.getTypeParameters().isEmpty()) {
            error(type, "SOMA-VALUE-001",
                    "lowered @SomaValue must be a public final non-generic top-level class");
            valid = false;
        }

        List<FieldModel> fields = new ArrayList<FieldModel>();
        Set<String> logicalNames = new LinkedHashSet<String>();
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) {
                continue;
            }
            VariableElement field = (VariableElement) enclosed;
            SomaField fieldAnnotation = field.getAnnotation(SomaField.class);
            SomaIgnore ignoreAnnotation = field.getAnnotation(SomaIgnore.class);
            if (field.getModifiers().contains(Modifier.STATIC)) {
                if (fieldAnnotation != null || ignoreAnnotation != null) {
                    error(field, "SOMA-VALUE-003",
                            "static field cannot declare SOMA field annotations");
                    valid = false;
                }
                continue;
            }
            if (fieldAnnotation != null && ignoreAnnotation != null) {
                error(field, "SOMA-VALUE-003",
                        "field cannot be both @SomaField and @SomaIgnore");
                valid = false;
                continue;
            }
            if (ignoreAnnotation != null) {
                error(field, "SOMA-VALUE-003",
                        "@SomaValue cannot declare non-static @SomaIgnore state");
                valid = false;
                continue;
            }
            if (fieldAnnotation == null) {
                error(field, "SOMA-VALUE-003",
                        "value instance field must declare @SomaField");
                valid = false;
                continue;
            }
            if (!field.getModifiers().contains(Modifier.PUBLIC)
                    || !field.getModifiers().contains(Modifier.FINAL)) {
                error(field, "SOMA-VALUE-001",
                        "lowered value field must be public final");
                valid = false;
            }

            String logicalName = fieldAnnotation.name();
            if (logicalName.isEmpty()) {
                logicalName = field.getSimpleName().toString();
            }
            if (logicalName.length() > 128
                    || !SourceVersion.isIdentifier(logicalName)
                    || SourceVersion.isKeyword(logicalName)
                    || !logicalNames.add(logicalName)) {
                error(field, "SOMA-VALUE-004",
                        "invalid or duplicate logical field name: " + logicalName);
                valid = false;
            }
            NormalizedType normalizedType = normalizedType(field.asType());
            if (normalizedType == null) {
                error(field, "SOMA-VALUE-005", "unsupported value field type: " + field.asType());
                valid = false;
                continue;
            }
            if (!validSemantic(fieldAnnotation.semantic(), field.asType())) {
                error(field, "SOMA-VALUE-006",
                        "semantic " + fieldAnnotation.semantic() + " is incompatible with " + field.asType());
                valid = false;
            }
            fields.add(new FieldModel(
                    field.getSimpleName().toString(), logicalName,
                    fieldAnnotation.semantic().name(), normalizedType));
        }

        if (fields.isEmpty()) {
            error(type, "SOMA-VALUE-001", "@SomaValue requires at least one schema field");
            valid = false;
        }
        if (!hasCanonicalConstructor(type, fields)
                || !hasMethod(type, "equals", 1)
                || !hasMethod(type, "hashCode", 0)
                || !hasMethod(type, "toString", 0)) {
            error(type, "SOMA-COMP-004",
                    "processor element model does not expose the complete lowered value shape");
            valid = false;
        }
        return valid ? new ValueModel(
                type.getQualifiedName().toString(), type.getSimpleName().toString(), fields) : null;
    }

    private boolean hasCanonicalConstructor(TypeElement type, List<FieldModel> fields) {
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            ExecutableElement constructor = (ExecutableElement) enclosed;
            if (!constructor.getModifiers().contains(Modifier.PUBLIC)
                    || constructor.getParameters().size() != fields.size()) {
                continue;
            }
            boolean match = true;
            for (int i = 0; i < fields.size(); i++) {
                NormalizedType actual = normalizedType(
                        constructor.getParameters().get(i).asType());
                if (actual == null || !fields.get(i).type.text.equals(actual.text)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMethod(TypeElement type, String name, int parameterCount) {
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD
                    && enclosed.getSimpleName().contentEquals(name)
                    && ((ExecutableElement) enclosed).getParameters().size() == parameterCount) {
                return true;
            }
        }
        return false;
    }

    private NormalizedType normalizedType(TypeMirror mirror) {
        TypeKind kind = mirror.getKind();
        switch (kind) {
            case BOOLEAN:
                return new NormalizedType("boolean", null, null);
            case BYTE:
                return new NormalizedType("byte", null, null);
            case SHORT:
                return new NormalizedType("short", null, null);
            case INT:
                return new NormalizedType("int", null, null);
            case LONG:
                return new NormalizedType("long", null, null);
            case FLOAT:
                return new NormalizedType("float", null, null);
            case DOUBLE:
                return new NormalizedType("double", null, null);
            case DECLARED:
                Element element = ((DeclaredType) mirror).asElement();
                if (!(element instanceof TypeElement)) {
                    return null;
                }
                TypeElement type = (TypeElement) element;
                if (type.getQualifiedName().contentEquals("java.lang.String")) {
                    return new NormalizedType("string", null, null);
                }
                if (type.getKind() == ElementKind.ENUM) {
                    return new NormalizedType(
                            "enum:" + type.getQualifiedName(), enumModel(type), null);
                }
                if (type.getAnnotation(SomaValue.class) != null) {
                    String qualifiedName = type.getQualifiedName().toString();
                    return new NormalizedType(
                            "value:" + qualifiedName, null, qualifiedName);
                }
                return null;
            default:
                return null;
        }
    }

    private EnumModel enumModel(TypeElement type) {
        List<String> members = new ArrayList<String>();
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.ENUM_CONSTANT) {
                members.add(enclosed.getSimpleName().toString());
            }
        }
        return new EnumModel(
                type.getQualifiedName().toString(),
                type.getSimpleName().toString(),
                members);
    }

    private boolean validSchemaVersion(String version) {
        if (version.isEmpty() || version.length() > 128) {
            return false;
        }
        if (isBoundaryWhitespace(version.charAt(0))
                || isBoundaryWhitespace(version.charAt(version.length() - 1))) {
            return false;
        }
        for (int i = 0; i < version.length(); i++) {
            if (Character.isISOControl(version.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isBoundaryWhitespace(char character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character);
    }

    private boolean validSemantic(SomaSemantic semantic, TypeMirror mirror) {
        if (semantic == SomaSemantic.NONE) {
            return true;
        }
        if (semantic == SomaSemantic.DATE) {
            return mirror.getKind() == TypeKind.INT;
        }
        return mirror.getKind() == TypeKind.LONG;
    }

    private void writeSchemaResources(SchemaModel schema) {
        String json = schema.toCanonicalJson();
        String hash = sha256(SCHEMA_HASH_PREFIX + json);
        String basePath = "META-INF/soma/" + schema.sourcePackage;
        try {
            writeResource(basePath + ".schema.json", json + "\n", schema.origin);
            writeResource(basePath + ".schema.sha256", hash + "\n", schema.origin);
        } catch (IOException exception) {
            error(schema.origin, "SOMA-OUTPUT-001",
                    "failed to write deterministic schema resources: "
                            + exception.getClass().getSimpleName());
        }
    }

    private void writeResource(String path, String content, Element origin) throws IOException {
        Filer filer = processingEnv.getFiler();
        FileObject resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", path, origin);
        Writer writer = new OutputStreamWriter(resource.openOutputStream(), StandardCharsets.UTF_8);
        try {
            writer.write(content);
        } finally {
            writer.close();
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                int unsigned = value & 0xff;
                result.append(LOWER_HEX[unsigned >>> 4]);
                result.append(LOWER_HEX[unsigned & 0x0f]);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by Java 8", impossible);
        }
    }

    private void error(Element element, String code, String message) {
        hasErrors = true;
        if (element == null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR, "[" + code + "] " + message);
        } else {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR, "[" + code + "] " + message, element);
        }
    }

    private void errorRaw(String diagnostic) {
        hasErrors = true;
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, diagnostic);
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2);
        result.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"':
                    result.append("\\\"");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                case '\b':
                    result.append("\\b");
                    break;
                case '\f':
                    result.append("\\f");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        result.append("\\u");
                        result.append(LOWER_HEX[(character >>> 12) & 0x0f]);
                        result.append(LOWER_HEX[(character >>> 8) & 0x0f]);
                        result.append(LOWER_HEX[(character >>> 4) & 0x0f]);
                        result.append(LOWER_HEX[character & 0x0f]);
                    } else {
                        result.append(character);
                    }
            }
        }
        return result.append('"').toString();
    }

    private static final class SchemaModel {
        private final PackageElement origin;
        private final String sourcePackage;
        private final String name;
        private final String generatedPackage;
        private final String version;
        private final Map<String, EnumModel> enums = new TreeMap<String, EnumModel>();
        private final Map<String, ValueModel> values = new TreeMap<String, ValueModel>();

        private SchemaModel(
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

        private void addValue(ValueModel value) {
            values.put(value.javaType, value);
            for (FieldModel field : value.fields) {
                if (field.type.enumModel != null) {
                    enums.put(field.type.enumModel.javaType, field.type.enumModel);
                }
            }
        }

        private String toCanonicalJson() {
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
            json.append("\"tables\":[],");
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

    private static final class EnumModel {
        private final String javaType;
        private final String logicalName;
        private final List<String> members;

        private EnumModel(String javaType, String logicalName, List<String> members) {
            this.javaType = javaType;
            this.logicalName = logicalName;
            this.members = members;
        }

        private void appendJson(StringBuilder json) {
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

    private static final class ValueModel {
        private final String javaType;
        private final String logicalName;
        private final List<FieldModel> fields;

        private ValueModel(String javaType, String logicalName, List<FieldModel> fields) {
            this.javaType = javaType;
            this.logicalName = logicalName;
            this.fields = fields;
        }

        private void appendJson(StringBuilder json) {
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

    private static final class FieldModel {
        private final String javaName;
        private final String logicalName;
        private final String semantic;
        private final NormalizedType type;

        private FieldModel(
                String javaName,
                String logicalName,
                String semantic,
                NormalizedType type) {
            this.javaName = javaName;
            this.logicalName = logicalName;
            this.semantic = semantic;
            this.type = type;
        }

        private void appendJson(StringBuilder json) {
            json.append('{');
            json.append("\"javaName\":").append(quote(javaName)).append(',');
            json.append("\"logicalName\":").append(quote(logicalName)).append(',');
            json.append("\"semantic\":").append(quote(semantic)).append(',');
            json.append("\"type\":").append(quote(type.text));
            json.append('}');
        }
    }

    private static final class NormalizedType {
        private final String text;
        private final EnumModel enumModel;
        private final String valueReference;

        private NormalizedType(
                String text, EnumModel enumModel, String valueReference) {
            this.text = text;
            this.enumModel = enumModel;
            this.valueReference = valueReference;
        }
    }
}
