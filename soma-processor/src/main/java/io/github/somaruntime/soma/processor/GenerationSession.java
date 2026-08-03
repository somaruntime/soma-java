package io.github.somaruntime.soma.processor;

import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

final class GenerationSession {
    private final ProcessingEnvironment processingEnvironment;
    private final Messager messager;
    private final Elements elements;
    private final Types types;
    private final Filer filer;
    private final Trees trees;
    private boolean failed;

    GenerationSession(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
        this.messager = processingEnvironment.getMessager();
        this.elements = processingEnvironment.getElementUtils();
        this.types = processingEnvironment.getTypeUtils();
        this.filer = processingEnvironment.getFiler();
        Trees candidate;
        try {
            candidate = Trees.instance(processingEnvironment);
        } catch (IllegalArgumentException exception) {
            candidate = null;
        }
        this.trees = candidate;
    }

    boolean failed() {
        return failed;
    }

    boolean validateFullSourceSetOption() {
        String option = processingEnvironment.getOptions().get(
                ProcessorContract.FULL_SOURCE_SET_OPTION);
        if (!"true".equals(option)) {
            error("0001", "qualified build must assert -Asoma.fullSourceSet=true");
            return false;
        }
        return true;
    }

    boolean verifyRuntimeContract() {
        TypeElement contract = elements.getTypeElement(ProcessorContract.RUNTIME_CONTRACT_TYPE);
        if (contract == null) {
            error("0101", "matching soma-runtime is missing from the application classpath");
            return false;
        }
        String artifactVersion = constantString(contract, "ARTIFACT_VERSION");
        String contractVersion = constantString(contract, "CONTRACT_VERSION");
        if (!ProcessorContract.ARTIFACT_VERSION.equals(artifactVersion)
                || !ProcessorContract.RUNTIME_CONTRACT_VERSION.equals(contractVersion)) {
            error("0102", "soma-processor and soma-runtime versions must match exactly", contract);
            return false;
        }
        return true;
    }

    boolean hasSomaAnnotation(Set<? extends TypeElement> annotations) {
        for (TypeElement annotation : annotations) {
            if (ProcessorContract.SUPPORTED_ANNOTATIONS.contains(
                    annotation.getQualifiedName().toString())) {
                return true;
            }
        }
        return false;
    }

    boolean hasLateSomaElement(RoundEnvironment roundEnvironment) {
        for (String annotationName : ProcessorContract.SUPPORTED_ANNOTATIONS) {
            TypeElement annotation = elements.getTypeElement(annotationName);
            if (annotation != null
                    && !roundEnvironment.getElementsAnnotatedWith(annotation).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    List<SchemaModel.GeneratedOutput> buildAndWrite(RoundEnvironment roundEnvironment) {
        List<SchemaModel.Composition> compositions = buildCompositions(roundEnvironment);
        if (failed) {
            return Collections.emptyList();
        }
        List<SchemaModel.GeneratedOutput> outputs =
                new ArrayList<SchemaModel.GeneratedOutput>();
        for (SchemaModel.Composition composition : compositions) {
            String source = render(composition);
            List<SchemaModel.GeneratedFile> files =
                    new ArrayList<SchemaModel.GeneratedFile>();
            files.add(new SchemaModel.GeneratedFile(
                    composition.generatedFqn, source, sha256(source)));
            if (composition.i1ApiEligible) {
                files.addAll(I1ApiRenderer.render(composition));
            }
            for (SchemaModel.GeneratedFile file : files) {
                TypeElement occupied = elements.getTypeElement(file.generatedFqn);
                if (occupied != null && !file.generatedFqn.equals(composition.generatedFqn)) {
                    error("0301", "generated API FQN is already occupied", occupied);
                }
            }
            outputs.add(new SchemaModel.GeneratedOutput(
                    composition, source, sha256(source), files));
        }
        if (failed) {
            return Collections.emptyList();
        }
        for (SchemaModel.GeneratedOutput output : outputs) {
            for (SchemaModel.GeneratedFile generatedFile : output.files) {
                try {
                    JavaFileObject file = filer.createSourceFile(
                            generatedFile.generatedFqn,
                            output.composition.originatingElements());
                    Writer writer = file.openWriter();
                    try {
                        writer.write(generatedFile.source);
                    } finally {
                        writer.close();
                    }
                } catch (IOException exception) {
                    error("0105", "generated source output could not be committed");
                    return Collections.emptyList();
                }
            }
        }
        return Collections.unmodifiableList(outputs);
    }

    void writeManifest(List<SchemaModel.GeneratedOutput> outputs) {
        if (failed || outputs.isEmpty()) {
            return;
        }
        String manifest = renderManifest(outputs);
        try {
            FileObject resource = filer.createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    ProcessorContract.MANIFEST_PATH);
            Writer writer = resource.openWriter();
            try {
                writer.write(manifest);
            } finally {
                writer.close();
            }
        } catch (IOException exception) {
            error("0106", "composition manifest could not be committed; clean output is required");
        }
    }

    private List<SchemaModel.Composition> buildCompositions(RoundEnvironment roundEnvironment) {
        TypeElement schemaAnnotation = elements.getTypeElement(ProcessorContract.SOMA_SCHEMA);
        TypeElement tableAnnotation = elements.getTypeElement(ProcessorContract.SOMA_TABLE);
        TypeElement valueAnnotation = elements.getTypeElement(ProcessorContract.SOMA_VALUE);
        if (schemaAnnotation == null || tableAnnotation == null || valueAnnotation == null) {
            error("0101", "SOMA annotation types are missing from the application classpath");
            return Collections.emptyList();
        }

        Map<String, PackageElement> schemaPackages = new LinkedHashMap<String, PackageElement>();
        for (Element element : roundEnvironment.getElementsAnnotatedWith(schemaAnnotation)) {
            if (element.getKind() != ElementKind.PACKAGE) {
                error("0201", "@SomaSchema must annotate a package", element);
                continue;
            }
            PackageElement packageElement = (PackageElement) element;
            String packageName = packageElement.getQualifiedName().toString();
            if (!validateSchemaPackage(packageName, packageElement)) {
                continue;
            }
            schemaPackages.put(packageName, packageElement);
        }

        Map<String, List<TypeElement>> tablesByPackage = new HashMap<String, List<TypeElement>>();
        for (Element element : roundEnvironment.getElementsAnnotatedWith(tableAnnotation)) {
            if (!(element instanceof TypeElement)) {
                error("0205", "@SomaTable must annotate a top-level class", element);
                continue;
            }
            TypeElement type = (TypeElement) element;
            String packageName = elements.getPackageOf(type).getQualifiedName().toString();
            if (!schemaPackages.containsKey(packageName)) {
                error("0203", "@SomaTable must belong to an exact @SomaSchema package", type);
                continue;
            }
            list(tablesByPackage, packageName).add(type);
        }

        Map<String, List<TypeElement>> valuesByPackage = new HashMap<String, List<TypeElement>>();
        for (Element element : roundEnvironment.getElementsAnnotatedWith(valueAnnotation)) {
            if (!(element instanceof TypeElement)) {
                error("0205", "@SomaValue must annotate a top-level class", element);
                continue;
            }
            TypeElement type = (TypeElement) element;
            String packageName = elements.getPackageOf(type).getQualifiedName().toString();
            if (!schemaPackages.containsKey(packageName)) {
                error("0203", "@SomaValue must belong to an exact @SomaSchema package", type);
                continue;
            }
            list(valuesByPackage, packageName).add(type);
        }

        List<String> packageNames = new ArrayList<String>(schemaPackages.keySet());
        Collections.sort(packageNames);
        Set<String> generatedNamespaces = new HashSet<String>();
        List<SchemaModel.Composition> compositions = new ArrayList<SchemaModel.Composition>();
        for (String packageName : packageNames) {
            List<TypeElement> tableElements = tablesByPackage.get(packageName);
            if (tableElements == null || tableElements.isEmpty()) {
                error("0202", "composition must contain at least one @SomaTable",
                        schemaPackages.get(packageName));
                continue;
            }
            List<SchemaModel.Type> tables = new ArrayList<SchemaModel.Type>();
            for (TypeElement table : tableElements) {
                SchemaModel.Type model = validateType(table, SchemaModel.TypeKind.TABLE);
                if (model != null) {
                    tables.add(model);
                }
            }
            List<SchemaModel.Type> allValues = new ArrayList<SchemaModel.Type>();
            List<TypeElement> valueElements = valuesByPackage.get(packageName);
            if (valueElements != null) {
                for (TypeElement value : valueElements) {
                    SchemaModel.Type model = validateType(value, SchemaModel.TypeKind.VALUE);
                    if (model != null) {
                        allValues.add(model);
                    }
                }
            }
            if (failed) {
                continue;
            }
            Collections.sort(tables, TYPE_NAME_ORDER);
            Collections.sort(allValues, TYPE_NAME_ORDER);
            List<SchemaModel.Type> reachableValues = reachableValues(
                    packageName, tables, allValues);
            if (failed) {
                continue;
            }
            String generatedNamespace = packageName.substring(
                    0, packageName.length() - ".schema".length());
            if (!generatedNamespaces.add(generatedNamespace)) {
                error("0302", "multiple compositions resolve to the same generated namespace",
                        schemaPackages.get(packageName));
                continue;
            }
            validateGeneratedSymbols(generatedNamespace, tables);
            if (failed) {
                continue;
            }
            String fingerprint = fingerprint(
                    packageName, generatedNamespace, tables, reachableValues);
            SchemaModel.Composition composition = new SchemaModel.Composition(
                    schemaPackages.get(packageName),
                    packageName,
                    generatedNamespace,
                    tables,
                    reachableValues,
                    fingerprint,
                    isI1ApiEligible(tables));
            TypeElement occupied = elements.getTypeElement(composition.generatedFqn);
            if (occupied != null) {
                error("0301", "generated composition carrier FQN is already occupied", occupied);
                continue;
            }
            compositions.add(composition);
        }
        return compositions;
    }

    private boolean isI1ApiEligible(List<SchemaModel.Type> tables) {
        if (tables.size() != 1) {
            return false;
        }
        SchemaModel.Type table = tables.get(0);
        SchemaModel.Field key = null;
        SchemaModel.Field payload = null;
        for (SchemaModel.Field field : table.fields) {
            if (field.role == SchemaModel.FieldRole.KEY) {
                key = field;
            } else if (field.role == SchemaModel.FieldRole.FIELD) {
                if (payload != null) {
                    return false;
                }
                payload = field;
            } else {
                return false;
            }
        }
        return key != null
                && payload != null
                && "long".equals(key.type)
                && "long".equals(payload.type);
    }

    /** Rejects source names that would collide with the first generated API surface. */
    private void validateGeneratedSymbols(
            String generatedNamespace,
            List<SchemaModel.Type> tables) {
        Set<String> topLevel = new HashSet<String>();
        topLevel.add("Soma");
        topLevel.add("SomaGroup");
        for (SchemaModel.Type table : tables) {
            String objectName = table.simpleName;
            String tableName = objectName + "Table";
            if (!topLevel.add(objectName) || !topLevel.add(tableName)) {
                error("0303", "schema type collides with generated SOMA top-level API", null);
            }
            if (table.fields.size() == 0) {
                continue;
            }
            Set<String> endpointNames = new HashSet<String>();
            for (SchemaModel.Field field : table.fields) {
                String endpoint = upperFirstCodePoint(field.name) + "Field";
                if (!endpointNames.add(endpoint)) {
                    error("0303", "schema fields collide after generated endpoint naming", null);
                }
                if ("runtime".equals(field.name)
                        || "expressionOwner".equals(field.name)
                        || "selectAll".equals(field.name)
                        || "filter".equals(field.name)
                        || "size".equals(field.name)
                        || "capacity".equals(field.name)
                        || "reserve".equals(field.name)
                        || "add".equals(field.name)
                        || "find".equals(field.name)
                        || "get".equals(field.name)
                        || "update".equals(field.name)) {
                    error("0303", "schema field collides with generated Table member", null);
                }
            }
        }
    }

    private static String upperFirstCodePoint(String value) {
        int codePoint = value.codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(codePoint)))
                + value.substring(Character.charCount(codePoint));
    }

    private boolean validateSchemaPackage(String packageName, PackageElement element) {
        if (!packageName.endsWith(".schema")
                || packageName.length() == ".schema".length()) {
            error("0201", "@SomaSchema package must end with .schema", element);
            return false;
        }
        String generatedNamespace = packageName.substring(
                0, packageName.length() - ".schema".length());
        if (isReservedNamespace(generatedNamespace)) {
            error("0201", "generated namespace is reserved", element);
            return false;
        }
        String[] segments = packageName.split("\\.");
        for (String segment : segments) {
            if (!validIdentifier(segment, false)) {
                error("0208", "schema package contains an unsafe identifier", element);
                return false;
            }
        }
        return true;
    }

    private SchemaModel.Type validateType(
            TypeElement type,
            SchemaModel.TypeKind typeKind) {
        if (trees == null) {
            error("0205", "qualified javac source model is unavailable", type);
            return null;
        }
        Set<Modifier> modifiers = type.getModifiers();
        if (type.getKind() != ElementKind.CLASS
                || type.getEnclosingElement().getKind() != ElementKind.PACKAGE
                || !modifiers.contains(Modifier.FINAL)
                || modifiers.contains(Modifier.PUBLIC)
                || modifiers.contains(Modifier.PROTECTED)
                || modifiers.contains(Modifier.PRIVATE)
                || modifiers.contains(Modifier.ABSTRACT)
                || !type.getTypeParameters().isEmpty()
                || !type.getInterfaces().isEmpty()
                || !isObjectSuperclass(type.getSuperclass())) {
            error("0205", "schema declaration must be a package-private top-level final class", type);
            return null;
        }
        if (!validIdentifier(type.getSimpleName().toString(), false)) {
            error("0208", "schema type contains an unsafe identifier", type);
            return null;
        }
        if (hasExplicitNonFieldMember(type)) {
            error("0205", "schema declaration may contain only annotated instance fields", type);
            return null;
        }

        List<VariableElement> fields = new ArrayList<VariableElement>(
                ElementFilter.fieldsIn(type.getEnclosedElements()));
        Collections.sort(fields, new Comparator<VariableElement>() {
            @Override
            public int compare(VariableElement left, VariableElement right) {
                long leftPosition = sourcePosition(left);
                long rightPosition = sourcePosition(right);
                return leftPosition < rightPosition ? -1 : leftPosition == rightPosition ? 0 : 1;
            }
        });
        List<SchemaModel.Field> fieldModels = new ArrayList<SchemaModel.Field>();
        int keyCount = 0;
        for (VariableElement field : fields) {
            long position = sourcePosition(field);
            if (position < 0L) {
                error("0205", "stable source declaration order is unavailable", field);
                continue;
            }
            SchemaModel.FieldRole role = validateField(field, typeKind);
            if (role == null) {
                continue;
            }
            if (role == SchemaModel.FieldRole.KEY) {
                keyCount++;
            }
            fieldModels.add(new SchemaModel.Field(
                    field.getSimpleName().toString(),
                    field.asType().toString(),
                    role,
                    position));
        }
        if (fieldModels.isEmpty()) {
            error("0204", "schema declaration must contain at least one SOMA field", type);
        }
        if (keyCount > 1) {
            error("0206", "a Table may declare at most one @SomaKey field", type);
        }
        long defaultCapacity = typeKind == SchemaModel.TypeKind.TABLE
                ? tableDefaultCapacity(type)
                : 0L;
        if (defaultCapacity < 0L) {
            error("0207", "@SomaTable defaultCapacity must not be negative", type);
        }
        if (failed) {
            return null;
        }
        return new SchemaModel.Type(type, typeKind, defaultCapacity, fieldModels);
    }

    private SchemaModel.FieldRole validateField(
            VariableElement field,
            SchemaModel.TypeKind typeKind) {
        Set<Modifier> modifiers = field.getModifiers();
        if (modifiers.contains(Modifier.STATIC)
                || modifiers.contains(Modifier.FINAL)
                || modifiers.contains(Modifier.TRANSIENT)
                || modifiers.contains(Modifier.VOLATILE)) {
            error("0206", "schema field must be a mutable instance declaration", field);
            return null;
        }
        Tree tree = trees.getTree(field);
        if (!(tree instanceof VariableTree)
                || ((VariableTree) tree).getInitializer() != null) {
            error("0206", "schema field must not declare an initializer", field);
            return null;
        }
        String name = field.getSimpleName().toString();
        if (!validIdentifier(name, true)) {
            error("0208", "schema field contains an unsafe identifier", field);
            return null;
        }
        boolean ordinary = hasAnnotation(field, ProcessorContract.SOMA_FIELD);
        boolean key = hasAnnotation(field, ProcessorContract.SOMA_KEY);
        boolean index = hasAnnotation(field, ProcessorContract.SOMA_INDEX);
        int roles = (ordinary ? 1 : 0) + (key ? 1 : 0) + (index ? 1 : 0);
        if (roles != 1) {
            error("0206", "schema field must declare exactly one SOMA role", field);
            return null;
        }
        if (typeKind == SchemaModel.TypeKind.VALUE && !ordinary) {
            error("0206", "@SomaValue fields may only use @SomaField", field);
            return null;
        }
        return key
                ? SchemaModel.FieldRole.KEY
                : index ? SchemaModel.FieldRole.INDEX : SchemaModel.FieldRole.FIELD;
    }

    private List<SchemaModel.Type> reachableValues(
            String schemaPackage,
            List<SchemaModel.Type> tables,
            List<SchemaModel.Type> allValues) {
        Map<String, SchemaModel.Type> valuesByName = new HashMap<String, SchemaModel.Type>();
        for (SchemaModel.Type value : allValues) {
            valuesByName.put(value.qualifiedName, value);
        }
        Set<String> reachable = new LinkedHashSet<String>();
        Set<String> visiting = new HashSet<String>();
        for (SchemaModel.Type table : tables) {
            collectValueDependencies(
                    schemaPackage, table, valuesByName, reachable, visiting);
        }
        List<SchemaModel.Type> result = new ArrayList<SchemaModel.Type>();
        for (String name : reachable) {
            result.add(valuesByName.get(name));
        }
        Collections.sort(result, TYPE_NAME_ORDER);
        return result;
    }

    private void collectValueDependencies(
            String schemaPackage,
            SchemaModel.Type owner,
            Map<String, SchemaModel.Type> valuesByName,
            Set<String> reachable,
            Set<String> visiting) {
        for (SchemaModel.Field field : owner.fields) {
            SchemaModel.Type dependency = valuesByName.get(field.type);
            if (dependency == null) {
                TypeElement declared = declaredTypeElement(owner.element, field.name);
                if (declared != null
                        && hasAnnotation(declared, ProcessorContract.SOMA_VALUE)) {
                    String dependencyPackage = elements.getPackageOf(declared)
                            .getQualifiedName().toString();
                    if (!schemaPackage.equals(dependencyPackage)) {
                        error("0209", "@SomaValue cannot cross a composition boundary", declared);
                    }
                }
                continue;
            }
            if (visiting.contains(dependency.qualifiedName)) {
                error("0209", "cyclic @SomaValue graph is not supported", dependency.element);
                continue;
            }
            if (reachable.add(dependency.qualifiedName)) {
                visiting.add(dependency.qualifiedName);
                collectValueDependencies(
                        schemaPackage, dependency, valuesByName, reachable, visiting);
                visiting.remove(dependency.qualifiedName);
            }
        }
    }

    private TypeElement declaredTypeElement(TypeElement owner, String fieldName) {
        for (VariableElement field : ElementFilter.fieldsIn(owner.getEnclosedElements())) {
            if (!field.getSimpleName().contentEquals(fieldName)) {
                continue;
            }
            TypeMirror mirror = field.asType();
            if (mirror.getKind() == TypeKind.DECLARED) {
                Element element = ((DeclaredType) mirror).asElement();
                return element instanceof TypeElement ? (TypeElement) element : null;
            }
        }
        return null;
    }

    private boolean hasExplicitNonFieldMember(TypeElement type) {
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                continue;
            }
            long enclosedPosition = sourcePosition(enclosed);
            if (enclosed.getKind() == ElementKind.CONSTRUCTOR
                    && sourceEndPosition(enclosed) < 0L) {
                continue;
            }
            if (enclosedPosition >= 0L) {
                return true;
            }
        }
        return false;
    }

    private long sourcePosition(Element element) {
        TreePath path = trees.getPath(element);
        if (path == null) {
            return -1L;
        }
        return trees.getSourcePositions().getStartPosition(
                path.getCompilationUnit(), path.getLeaf());
    }

    private long sourceEndPosition(Element element) {
        TreePath path = trees.getPath(element);
        if (path == null) {
            return -1L;
        }
        return trees.getSourcePositions().getEndPosition(
                path.getCompilationUnit(), path.getLeaf());
    }

    private boolean isObjectSuperclass(TypeMirror superclass) {
        if (superclass.getKind() == TypeKind.NONE) {
            return false;
        }
        TypeElement objectType = elements.getTypeElement("java.lang.Object");
        return objectType != null
                && types.isSameType(types.erasure(superclass), types.erasure(objectType.asType()));
    }

    private long tableDefaultCapacity(TypeElement type) {
        AnnotationMirror annotation = annotation(type, ProcessorContract.SOMA_TABLE);
        if (annotation == null) {
            return -1L;
        }
        Map<? extends javax.lang.model.element.ExecutableElement, ? extends AnnotationValue> values =
                elements.getElementValuesWithDefaults(annotation);
        for (Map.Entry<? extends javax.lang.model.element.ExecutableElement,
                ? extends AnnotationValue> entry : values.entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals("defaultCapacity")) {
                Object value = entry.getValue().getValue();
                return value instanceof Long ? ((Long) value).longValue() : -1L;
            }
        }
        return -1L;
    }

    private String constantString(TypeElement type, String fieldName) {
        for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            if (field.getSimpleName().contentEquals(fieldName)) {
                Object value = field.getConstantValue();
                return value instanceof String ? (String) value : null;
            }
        }
        return null;
    }

    private boolean hasAnnotation(Element element, String annotationName) {
        return annotation(element, annotationName) != null;
    }

    private AnnotationMirror annotation(Element element, String annotationName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            Element annotationElement = mirror.getAnnotationType().asElement();
            if (annotationElement instanceof TypeElement
                    && ((TypeElement) annotationElement).getQualifiedName()
                    .contentEquals(annotationName)) {
                return mirror;
            }
        }
        return null;
    }

    private String fingerprint(
            String schemaPackage,
            String generatedNamespace,
            List<SchemaModel.Type> tables,
            List<SchemaModel.Type> values) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("schemaPackage=").append(schemaPackage).append('\n');
        canonical.append("generatedNamespace=").append(generatedNamespace).append('\n');
        for (SchemaModel.Type table : tables) {
            appendType(canonical, table);
        }
        for (SchemaModel.Type value : values) {
            appendType(canonical, value);
        }
        return sha256(canonical.toString());
    }

    private void appendType(StringBuilder canonical, SchemaModel.Type type) {
        canonical.append(type.kind.name()).append('|')
                .append(type.qualifiedName).append('|')
                .append(type.defaultCapacity).append('\n');
        for (SchemaModel.Field field : type.fields) {
            canonical.append("FIELD|")
                    .append(field.name).append('|')
                    .append(field.role.name()).append('|')
                    .append(field.type).append('\n');
        }
    }

    private String render(SchemaModel.Composition composition) {
        String packageName = composition.generatedNamespace + ".internal";
        StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n");
        source.append("/** Build-time composition carrier；不是 application API。 */\n");
        source.append("public final class ")
                .append(ProcessorContract.GENERATED_CARRIER_SIMPLE_NAME)
                .append(" {\n");
        source.append("    public static final String SCHEMA_PACKAGE = \"")
                .append(composition.schemaPackage).append("\";\n");
        source.append("    public static final String SCHEMA_FINGERPRINT = \"")
                .append(composition.fingerprint).append("\";\n");
        source.append("    public static final String TABLE_DECLARATIONS = \"")
                .append(tableDeclarations(composition.tables)).append("\";\n");
        source.append("    public static final String PROCESSOR_VERSION = \"")
                .append(ProcessorContract.ARTIFACT_VERSION).append("\";\n\n");
        source.append("    static {\n");
        source.append("        io.github.somaruntime.soma.internal.SomaRuntimeAccess")
                .append(".verifyGeneratedArtifact(\n");
        source.append("                PROCESSOR_VERSION,\n");
        source.append("                \"")
                .append(ProcessorContract.RUNTIME_CONTRACT_VERSION).append("\",\n");
        source.append("                SCHEMA_FINGERPRINT);\n");
        source.append("    }\n\n");
        source.append("    private ")
                .append(ProcessorContract.GENERATED_CARRIER_SIMPLE_NAME)
                .append("() {\n");
        source.append("    }\n\n");
        source.append("    public static void verifyRuntime() {\n");
        source.append("        // Class initialization 已执行精确 linkage check。\n");
        source.append("    }\n");
        source.append("}\n");
        return source.toString();
    }

    private String renderManifest(List<SchemaModel.GeneratedOutput> outputs) {
        List<SchemaModel.GeneratedOutput> sorted =
                new ArrayList<SchemaModel.GeneratedOutput>(outputs);
        Collections.sort(sorted, new Comparator<SchemaModel.GeneratedOutput>() {
            @Override
            public int compare(
                    SchemaModel.GeneratedOutput left,
                    SchemaModel.GeneratedOutput right) {
                return left.composition.schemaPackage.compareTo(
                        right.composition.schemaPackage);
            }
        });
        StringBuilder moduleCanonical = new StringBuilder();
        for (SchemaModel.GeneratedOutput output : sorted) {
            moduleCanonical.append(output.composition.schemaPackage).append('|')
                    .append(output.composition.fingerprint).append('\n');
            for (SchemaModel.GeneratedFile file : output.files) {
                moduleCanonical.append(file.generatedFqn).append('|')
                        .append(file.sourceSha256).append('\n');
            }
        }
        StringBuilder manifest = new StringBuilder();
        property(manifest, "manifestFormatVersion", "1");
        property(manifest, "processorArtifactVersion", ProcessorContract.ARTIFACT_VERSION);
        property(manifest, "runtimeArtifactVersion", ProcessorContract.ARTIFACT_VERSION);
        property(manifest, "runtimeContractVersion", ProcessorContract.RUNTIME_CONTRACT_VERSION);
        property(manifest, "moduleSchemaModelSha256", sha256(moduleCanonical.toString()));
        property(manifest, "composition.count", Integer.toString(sorted.size()));
        for (int index = 0; index < sorted.size(); index++) {
            SchemaModel.GeneratedOutput output = sorted.get(index);
            String prefix = "composition." + index + ".";
            property(manifest, prefix + "schemaPackage", output.composition.schemaPackage);
            property(manifest, prefix + "generatedNamespace",
                    output.composition.generatedNamespace);
            property(manifest, prefix + "schemaModelSha256",
                    output.composition.fingerprint);
            property(manifest, prefix + "generatedFqn", output.composition.generatedFqn);
            property(manifest, prefix + "generatedSourceSha256", output.sourceSha256);
            property(manifest, prefix + "generatedFile.count",
                    Integer.toString(output.files.size()));
            for (int fileIndex = 0; fileIndex < output.files.size(); fileIndex++) {
                SchemaModel.GeneratedFile file = output.files.get(fileIndex);
                property(manifest, prefix + "generatedFile." + fileIndex + ".fqn",
                        file.generatedFqn);
                property(manifest, prefix + "generatedFile." + fileIndex + ".sha256",
                        file.sourceSha256);
            }
            property(manifest, prefix + "table.count",
                    Integer.toString(output.composition.tables.size()));
            for (int tableIndex = 0;
                    tableIndex < output.composition.tables.size();
                    tableIndex++) {
                property(manifest,
                        prefix + "table." + tableIndex + ".qualifiedName",
                        output.composition.tables.get(tableIndex).qualifiedName);
            }
        }
        return manifest.toString();
    }

    private String tableDeclarations(List<SchemaModel.Type> tables) {
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < tables.size(); index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append(tables.get(index).qualifiedName);
        }
        return value.toString();
    }

    private void property(StringBuilder target, String key, String value) {
        target.append(escapeProperty(key)).append('=')
                .append(escapeProperty(value)).append('\n');
    }

    private String escapeProperty(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case ' ':
                    escaped.append("\\ ");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '=':
                case ':':
                case '#':
                case '!':
                    escaped.append('\\').append(character);
                    break;
                default:
                    if (character < 0x20 || character > 0x7e) {
                        escaped.append("\\u");
                        String hexadecimal = Integer.toHexString(character);
                        for (int padding = hexadecimal.length(); padding < 4; padding++) {
                            escaped.append('0');
                        }
                        escaped.append(hexadecimal);
                    } else {
                        escaped.append(character);
                    }
                    break;
            }
        }
        return escaped.toString();
    }

    private boolean validIdentifier(String value, boolean rejectLeadingUnderscore) {
        if (value.length() == 0
                || value.indexOf('$') >= 0
                || (rejectLeadingUnderscore && value.charAt(0) == '_')
                || !Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
            return false;
        }
        int first = value.codePointAt(0);
        if (!Character.isJavaIdentifierStart(first)) {
            return false;
        }
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isJavaIdentifierPart(codePoint)
                    || Character.isISOControl(codePoint)
                    || Character.getType(codePoint) == Character.FORMAT
                    || Character.isIdentifierIgnorable(codePoint)
                    || isBidiControl(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private boolean isBidiControl(int codePoint) {
        return codePoint == 0x061c
                || codePoint == 0x200e
                || codePoint == 0x200f
                || (codePoint >= 0x202a && codePoint <= 0x202e)
                || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }

    private boolean isReservedNamespace(String namespace) {
        return namespace.equals("java") || namespace.startsWith("java.")
                || namespace.equals("javax") || namespace.startsWith("javax.")
                || namespace.equals("jdk") || namespace.startsWith("jdk.")
                || namespace.equals("sun") || namespace.startsWith("sun.")
                || namespace.equals("io.github.somaruntime.soma")
                || namespace.startsWith("io.github.somaruntime.soma.");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexadecimal = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                hexadecimal.append(String.format("%02x", Integer.valueOf(item & 0xff)));
            }
            return hexadecimal.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 must be available", exception);
        }
    }

    private static <K, V> List<V> list(Map<K, List<V>> map, K key) {
        List<V> values = map.get(key);
        if (values == null) {
            values = new ArrayList<V>();
            map.put(key, values);
        }
        return values;
    }

    private void error(String code, String message) {
        failed = true;
        SomaDiagnostic.error(messager, code, message);
    }

    private void error(String code, String message, Element element) {
        failed = true;
        SomaDiagnostic.error(messager, code, message, element);
    }

    private static final Comparator<SchemaModel.Type> TYPE_NAME_ORDER =
            new Comparator<SchemaModel.Type>() {
                @Override
                public int compare(SchemaModel.Type left, SchemaModel.Type right) {
                    return left.qualifiedName.compareTo(right.qualifiedName);
                }
            };
}
