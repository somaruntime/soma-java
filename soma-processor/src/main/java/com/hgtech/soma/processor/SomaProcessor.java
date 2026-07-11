package com.hgtech.soma.processor;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaIgnore;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaIndexes;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaOrders;
import com.hgtech.soma.annotation.SomaSchema;
import com.hgtech.soma.annotation.SomaSemantic;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;
import com.hgtech.soma.annotation.SomaUnique;
import com.hgtech.soma.annotation.SomaUniques;
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
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
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
        "com.hgtech.soma.annotation.SomaValue",
        "com.hgtech.soma.annotation.SomaTable",
        "com.hgtech.soma.annotation.SomaChild",
        "com.hgtech.soma.annotation.SomaDefault",
        "com.hgtech.soma.annotation.SomaKey",
        "com.hgtech.soma.annotation.SomaOptional",
        "com.hgtech.soma.annotation.SomaIndex",
        "com.hgtech.soma.annotation.SomaIndexes",
        "com.hgtech.soma.annotation.SomaUnique",
        "com.hgtech.soma.annotation.SomaUniques",
        "com.hgtech.soma.annotation.SomaOrder",
        "com.hgtech.soma.annotation.SomaOrders"
})
public final class SomaProcessor extends AbstractProcessor {
    private static final char[] LOWER_HEX = "0123456789abcdef".toCharArray();
    private static final String GENERATED_TARGET = "java8-columnar";
    private static final String SCHEMA_HASH_PREFIX = "soma-java:v1:schema\n";

    private final Map<String, TypeElement> values = new LinkedHashMap<String, TypeElement>();
    private final Map<String, TypeElement> tables = new LinkedHashMap<String, TypeElement>();
    private final Map<String, PackageElement> schemaPackages =
            new LinkedHashMap<String, PackageElement>();
    private final Set<Element> invalidSelectorOwners = new HashSet<Element>();
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
        validateChildPlacement(roundEnvironment.getElementsAnnotatedWith(SomaChild.class));
        validateSelectorPlacement(roundEnvironment.getElementsAnnotatedWith(SomaIndex.class));
        validateSelectorPlacement(roundEnvironment.getElementsAnnotatedWith(SomaIndexes.class));
        validateSelectorPlacement(roundEnvironment.getElementsAnnotatedWith(SomaUnique.class));
        validateSelectorPlacement(roundEnvironment.getElementsAnnotatedWith(SomaUniques.class));
        validateSelectorPlacement(roundEnvironment.getElementsAnnotatedWith(SomaOrder.class));
        validateSelectorPlacement(roundEnvironment.getElementsAnnotatedWith(SomaOrders.class));
        for (Element element : roundEnvironment.getElementsAnnotatedWith(SomaValue.class)) {
            if (element instanceof TypeElement) {
                TypeElement type = (TypeElement) element;
                values.put(type.getQualifiedName().toString(), type);
            }
        }
        for (Element element : roundEnvironment.getElementsAnnotatedWith(SomaTable.class)) {
            if (element instanceof TypeElement) {
                TypeElement type = (TypeElement) element;
                tables.put(type.getQualifiedName().toString(), type);
            }
        }
        for (Element element : roundEnvironment.getElementsAnnotatedWith(SomaSchema.class)) {
            if (element instanceof PackageElement) {
                PackageElement packageElement = (PackageElement) element;
                schemaPackages.put(packageElement.getQualifiedName().toString(), packageElement);
            }
        }

        if (!roundEnvironment.processingOver() && !finished
                && (!values.isEmpty() || !tables.isEmpty())) {
            finished = true;
            finishProcessing();
        }
        return false;
    }

    private void validateChildPlacement(Set<? extends Element> elements) {
        for (Element element : elements) {
            Element owner = element.getEnclosingElement();
            if (element.getKind() == ElementKind.FIELD
                    && owner != null
                    && owner.getAnnotation(SomaTable.class) != null) {
                continue;
            }
            error(element, "SOMA-TABLE-010",
                    "@SomaChild is only valid on direct @SomaTable fields");
        }
    }

    private void validateSelectorPlacement(Set<? extends Element> elements) {
        for (Element element : elements) {
            if (element.getAnnotation(SomaTable.class) != null
                    || !invalidSelectorOwners.add(element)) continue;
            error(element, "SOMA-TABLE-009",
                    "selector annotations are only valid on @SomaTable types");
        }
    }

    private void finishProcessing() {
        if (!activationValid || hasErrors) {
            return;
        }

        Map<String, SchemaModel> schemas = new TreeMap<String, SchemaModel>();
        Map<String, ValueModel> validatedValues = new LinkedHashMap<String, ValueModel>();
        for (TypeElement value : values.values()) {
            ValueModel model = validateValue(value);
            if (model == null) {
                continue;
            }
            validatedValues.put(model.javaType, model);
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

        for (TypeElement table : tables.values()) {
            TableModel model = validateTable(table, validatedValues);
            if (model == null) {
                continue;
            }
            PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(table);
            String packageName = packageElement.getQualifiedName().toString();
            PackageElement declaredSchema = schemaPackages.get(packageName);
            if (declaredSchema == null) {
                error(table, "SOMA-SCHEMA-001",
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
            TableModel duplicate = schema.tableByLogicalName(model.logicalName);
            if (duplicate != null) {
                error(duplicate.origin, "SOMA-TABLE-002",
                        "duplicate table logical name: " + model.logicalName);
                error(model.origin, "SOMA-TABLE-002",
                        "duplicate table logical name: " + model.logicalName);
            } else {
                schema.addTable(model);
            }
        }

        validateSchemaNames(schemas);
        for (SchemaModel schema : schemas.values()) {
            validateValueGraph(schema);
            validateOwnershipGraph(schema);
        }
        if (hasErrors) {
            return;
        }
        for (SchemaModel schema : schemas.values()) {
            writeSchemaArtifacts(schema);
        }
    }

    private TableModel validateTable(TypeElement type, Map<String, ValueModel> validatedValues) {
        boolean valid = true;
        Set<Modifier> modifiers = type.getModifiers();
        if (type.getKind() != ElementKind.CLASS
                || type.getNestingKind() != NestingKind.TOP_LEVEL
                || !modifiers.contains(Modifier.PUBLIC)
                || modifiers.contains(Modifier.ABSTRACT)
                || !type.getTypeParameters().isEmpty()) {
            error(type, "SOMA-TABLE-001",
                    "@SomaTable must be a public non-abstract non-generic top-level class");
            valid = false;
        }
        SomaTable annotation = type.getAnnotation(SomaTable.class);
        String logicalName = annotation.name().isEmpty()
                ? type.getSimpleName().toString() : annotation.name();
        if (logicalName.length() > 128
                || !logicalName.matches("[A-Za-z][A-Za-z0-9_]*")) {
            error(type, "SOMA-TABLE-002", "invalid table logical name: " + logicalName);
            valid = false;
        }
        int defaultCapacity = annotation.defaultCapacity();
        if (defaultCapacity == 0 || defaultCapacity < -1) {
            error(type, "SOMA-TABLE-007", "invalid defaultCapacity: " + defaultCapacity);
            valid = false;
        }
        if (!hasPublicNoArgConstructor(type)) {
            error(type, "SOMA-TABLE-006",
                    "table carrier requires a public no-arg constructor without checked exceptions");
            valid = false;
        }

        List<TableFieldModel> fields = new ArrayList<TableFieldModel>();
        Set<String> logicalNames = new LinkedHashSet<String>();
        Set<String> generatedAccessNames = new LinkedHashSet<String>();
        Set<String> strictSelectorPaths = selectorPaths(type);
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) {
                continue;
            }
            VariableElement field = (VariableElement) enclosed;
            SomaField fieldAnnotation = field.getAnnotation(SomaField.class);
            SomaKey keyAnnotation = field.getAnnotation(SomaKey.class);
            SomaChild childAnnotation = field.getAnnotation(SomaChild.class);
            SomaIgnore ignore = field.getAnnotation(SomaIgnore.class);
            SomaOptional optional = field.getAnnotation(SomaOptional.class);
            SomaDefault defaultAnnotation = field.getAnnotation(SomaDefault.class);
            if (field.getModifiers().contains(Modifier.STATIC)) {
                if (fieldAnnotation != null || keyAnnotation != null || childAnnotation != null
                        || ignore != null || optional != null || defaultAnnotation != null) {
                    error(field, "SOMA-TABLE-003",
                            "static field cannot declare SOMA table annotations");
                    valid = false;
                }
                continue;
            }
            int primaryRoles = (fieldAnnotation == null ? 0 : 1)
                    + (keyAnnotation == null ? 0 : 1)
                    + (childAnnotation == null ? 0 : 1)
                    + (ignore == null ? 0 : 1);
            if (primaryRoles > 1) {
                error(field, "SOMA-TABLE-003",
                        "@SomaField, @SomaKey, @SomaChild and @SomaIgnore are mutually exclusive");
                valid = false;
                continue;
            }
            if (ignore != null) {
                if (optional != null || defaultAnnotation != null) {
                    error(field, "SOMA-TABLE-003",
                            "field modifier requires @SomaField");
                    valid = false;
                }
                continue;
            }
            if (fieldAnnotation == null && keyAnnotation == null && childAnnotation == null) {
                error(field, "SOMA-TABLE-003",
                        "table instance field must declare @SomaField, @SomaKey, @SomaChild or @SomaIgnore");
                valid = false;
                continue;
            }
            if (!field.getModifiers().contains(Modifier.PUBLIC)
                    || field.getModifiers().contains(Modifier.FINAL)) {
                error(field, "SOMA-TABLE-006",
                        "table schema field must be public and mutable");
                valid = false;
            }

            boolean key = keyAnnotation != null;
            boolean child = childAnnotation != null;
            if (defaultAnnotation != null
                    && (fieldAnnotation == null || optional != null)) {
                error(field, "SOMA-TABLE-005",
                        "@SomaDefault is allowed only on required @SomaField");
                valid = false;
            }
            String annotationName = key ? keyAnnotation.name()
                    : child ? childAnnotation.name() : fieldAnnotation.name();
            SomaSemantic annotationSemantic = child ? SomaSemantic.NONE
                    : key ? keyAnnotation.semantic() : fieldAnnotation.semantic();
            String fieldLogicalName = annotationName.isEmpty()
                    ? field.getSimpleName().toString() : annotationName;
            if (fieldLogicalName.length() > 128
                    || !SourceVersion.isIdentifier(fieldLogicalName)
                    || SourceVersion.isKeyword(fieldLogicalName)
                    || !logicalNames.add(fieldLogicalName)) {
                error(field, "SOMA-TABLE-004",
                        "invalid or duplicate logical field name: " + fieldLogicalName);
                valid = false;
            }

            ChildFieldType childType = child
                    ? childFieldType(type, field, childAnnotation) : null;
            TableFieldType tableType = child ? null : tableFieldType(
                    field.asType(), optional != null, key, validatedValues);
            if (child && childType == null) {
                valid = false;
                continue;
            }
            if (tableType == null) {
                if (child) {
                    fields.add(new TableFieldModel(
                            field.getSimpleName().toString(), fieldLogicalName,
                            SomaSemantic.NONE.name(), null, childType,
                            optional != null, false, null));
                    if (!registerGeneratedChildAccessNames(
                            generatedAccessNames, field.getSimpleName().toString(),
                            optional != null)) {
                        error(field, "SOMA-GEN-001",
                                "generated child access name collision for field: "
                                        + field.getSimpleName());
                        valid = false;
                    }
                    continue;
                }
                error(field, "SOMA-TABLE-005",
                        "unsupported SOMA table field type or optional materialized shape: "
                                + field.asType());
                valid = false;
                continue;
            }
            if (key && optional != null) {
                error(field, "SOMA-TABLE-008",
                        "current keyed table slice requires a required scalar or enum @SomaKey: "
                                + field.asType());
                valid = false;
            }
            if (key && tableType.valueJavaType != null
                    && valueHasDefault(tableType.valueJavaType, validatedValues,
                            new HashSet<String>())) {
                error(field, "SOMA-TABLE-008",
                        "value key path cannot depend on @SomaDefault");
                valid = false;
            }
            if (tableType.valueJavaType != null
                    && !validateStrictValueDefaults(
                            field, fieldLogicalName, tableType.valueJavaType,
                            validatedValues, strictSelectorPaths,
                            new HashSet<String>())) {
                valid = false;
            }
            if (!validTableSemantic(annotationSemantic, tableType.primitiveKind)) {
                error(field, "SOMA-TABLE-005",
                        "semantic " + annotationSemantic
                                + " is incompatible with " + field.asType());
                valid = false;
            }
            if (!registerGeneratedAccessNames(
                    generatedAccessNames,
                    field.getSimpleName().toString(),
                    optional != null)) {
                error(field, "SOMA-GEN-001",
                        "generated access name collision for field: "
                                + field.getSimpleName());
                valid = false;
            }
            DefaultModel defaultValue = null;
            if (defaultAnnotation != null && fieldAnnotation != null && optional == null) {
                defaultValue = normalizeTableDefault(
                        field, tableType, annotationSemantic, defaultAnnotation.value(),
                        strictSelectorPaths.contains(fieldLogicalName));
                if (defaultValue == null) valid = false;
            }
            fields.add(new TableFieldModel(
                    field.getSimpleName().toString(), fieldLogicalName,
                    annotationSemantic.name(), tableType, null, optional != null, key,
                    defaultValue));
        }
        if (fields.isEmpty()) {
            error(type, "SOMA-TABLE-001", "@SomaTable requires at least one schema field");
            valid = false;
        }
        int keyCount = 0;
        for (TableFieldModel field : fields) {
            if (field.key) {
                keyCount++;
            }
        }
        if (keyCount > 1) {
            error(type, "SOMA-TABLE-008", "@SomaTable permits exactly one @SomaKey field");
            valid = false;
        }
        List<SelectorModel> selectors = validateSelectors(type, fields);
        if (selectors == null) {
            valid = false;
            selectors = new ArrayList<SelectorModel>();
        }
        for (SelectorModel selector : selectors) {
            if (!generatedAccessNames.add(selector.generatedMethodName())) {
                error(type, "SOMA-GEN-001",
                        "generated selector access name collision: "
                                + selector.generatedMethodName());
                valid = false;
            }
        }
        return valid ? new TableModel(type, type.getQualifiedName().toString(),
                type.getSimpleName().toString(), logicalName, defaultCapacity,
                fields, selectors) : null;
    }

    private List<SelectorModel> validateSelectors(
            TypeElement table, List<TableFieldModel> fields) {
        List<SelectorModel> result = new ArrayList<SelectorModel>();
        Set<String> names = new LinkedHashSet<String>();
        boolean valid = true;
        for (SomaIndex index : table.getAnnotationsByType(SomaIndex.class)) {
            SelectorModel selector = selector(table, "index", index.value(),
                    index.name(), index.fields(), null, fields, names);
            if (selector == null) valid = false; else result.add(selector);
        }
        for (SomaUnique unique : table.getAnnotationsByType(SomaUnique.class)) {
            SelectorModel selector = selector(table, "unique", unique.value(),
                    unique.name(), unique.fields(), null, fields, names);
            if (selector == null) valid = false; else result.add(selector);
        }
        for (SomaOrder order : table.getAnnotationsByType(SomaOrder.class)) {
            SomaSort[] by = order.by();
            String[] paths = new String[by.length];
            String[] directions = new String[by.length];
            for (int i = 0; i < by.length; i++) {
                paths[i] = by[i].value();
                directions[i] = by[i].direction().name();
            }
            SelectorModel selector = selector(table, "order", order.value(),
                    order.name(), paths, directions, fields, names);
            if (selector == null) valid = false; else result.add(selector);
        }
        return valid ? result : null;
    }

    private Set<String> selectorPaths(TypeElement table) {
        Set<String> result = new HashSet<String>();
        for (SomaIndex index : table.getAnnotationsByType(SomaIndex.class)) {
            for (String path : index.fields()) result.add(path);
        }
        for (SomaUnique unique : table.getAnnotationsByType(SomaUnique.class)) {
            for (String path : unique.fields()) result.add(path);
        }
        for (SomaOrder order : table.getAnnotationsByType(SomaOrder.class)) {
            for (SomaSort sort : order.by()) result.add(sort.value());
        }
        return result;
    }

    private SelectorModel selector(
            TypeElement table, String kind, String alias, String declaredName,
            String[] paths, String[] directions, List<TableFieldModel> fields,
            Set<String> names) {
        String name = declaredName.isEmpty() ? alias : declaredName;
        boolean valid = true;
        if (!alias.isEmpty() && !declaredName.isEmpty() && !alias.equals(declaredName)) {
            error(table, "SOMA-TABLE-009", "selector value/name alias mismatch");
            valid = false;
        }
        if (name.isEmpty() || name.length() > 128
                || !name.matches("[A-Za-z][A-Za-z0-9_]*") || !names.add(name)) {
            error(table, "SOMA-TABLE-009",
                    "invalid or duplicate selector name: " + name);
            valid = false;
        }
        if (paths.length == 0) {
            error(table, "SOMA-TABLE-009", "selector requires at least one leaf path: " + name);
            valid = false;
        }
        List<SelectorLeafModel> leaves = new ArrayList<SelectorLeafModel>();
        Set<String> seenPaths = new LinkedHashSet<String>();
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            SelectorLeafModel leaf = resolveSelectorLeaf(fields, path,
                    directions == null ? "ASC" : directions[i]);
            if (leaf == null) {
                error(table, "SOMA-TABLE-009", selectorLeafFailure(fields, path));
                valid = false;
            } else if (!seenPaths.add(path)) {
                error(table, "SOMA-TABLE-009", "duplicate selector leaf path: " + path);
                valid = false;
            } else {
                leaves.add(leaf);
            }
        }
        return valid ? new SelectorModel(kind, name, leaves) : null;
    }

    private SelectorLeafModel resolveSelectorLeaf(
            List<TableFieldModel> fields, String path, String direction) {
        for (TableFieldModel field : fields) {
            if (field.child != null) continue;
            if (field.optional) continue;
            if (field.type.valueJavaType == null) {
                if (field.logicalName.equals(path)
                        && !"java.lang.String".equals(field.type.storagePrimitiveName)) {
                    return new SelectorLeafModel(path, direction,
                            field.type.publicType, field.type.storagePrimitiveName,
                            field.type.enumJavaType);
                }
                continue;
            }
            for (ValueLeafType leaf : field.type.valueLeaves) {
                if ((field.logicalName + "." + leaf.logicalName).equals(path)
                        && !"java.lang.String".equals(leaf.storagePrimitiveName)) {
                    return new SelectorLeafModel(path, direction,
                            leaf.publicPrimitiveName, leaf.storagePrimitiveName,
                            leaf.enumJavaType);
                }
            }
        }
        return null;
    }

    private String selectorLeafFailure(
            List<TableFieldModel> fields, String path) {
        List<String> candidates = new ArrayList<String>();
        for (TableFieldModel field : fields) {
            if (field.child != null) continue;
            candidates.add(field.logicalName);
            if (field.logicalName.equals(path) && field.optional) {
                return "selector path is optional and cannot be indexed: " + path;
            }
            if (field.logicalName.equals(path)
                    && "java.lang.String".equals(field.type.storagePrimitiveName)) {
                return "selector path resolves to unsupported string leaf: " + path;
            }
            for (ValueLeafType leaf : field.type.valueLeaves) {
                String candidate = field.logicalName + "." + leaf.logicalName;
                candidates.add(candidate);
                if (candidate.equals(path)
                        && "java.lang.String".equals(leaf.storagePrimitiveName)) {
                    return "selector path resolves to unsupported string leaf: " + path;
                }
            }
        }
        return "invalid selector leaf path: " + path
                + "; candidates=" + candidates;
    }

    private boolean registerGeneratedAccessNames(
            Set<String> names, String javaName, boolean optional) {
        String capitalized = Character.toUpperCase(javaName.charAt(0))
                + javaName.substring(1);
        List<String> derived = new ArrayList<String>();
        derived.add(javaName);
        derived.add("set" + capitalized);
        if (optional) {
            derived.add(javaName + "Present");
            derived.add(javaName + "Absent");
            derived.add(javaName + "Or");
            derived.add("clear" + capitalized);
        }
        boolean unique = true;
        for (String name : derived) {
            if (!names.add(name)) {
                unique = false;
            }
        }
        return unique;
    }

    private boolean registerGeneratedChildAccessNames(
            Set<String> names, String javaName, boolean optional) {
        String capitalized = Character.toUpperCase(javaName.charAt(0))
                + javaName.substring(1);
        List<String> derived = new ArrayList<String>();
        derived.add(javaName);
        derived.add("replace" + capitalized);
        if (optional) {
            derived.add(javaName + "Present");
            derived.add(javaName + "OrThrow");
            derived.add("ensure" + capitalized);
            derived.add("unset" + capitalized);
        }
        boolean unique = true;
        for (String name : derived) {
            if (!names.add(name)) unique = false;
        }
        return unique;
    }

    private ChildFieldType childFieldType(
            TypeElement owner,
            VariableElement field,
            SomaChild annotation) {
        int initialCapacity = annotation.initialCapacity();
        if (initialCapacity == 0 || initialCapacity < -1) {
            error(field, "SOMA-TABLE-007",
                    "invalid child initialCapacity: " + initialCapacity);
            return null;
        }
        TypeMirror mirror = field.asType();
        if (mirror.getKind() != TypeKind.DECLARED) {
            error(field, "SOMA-TABLE-010",
                    "child field must be exact java.util.List<R> or java.util.Map<K,R>");
            return null;
        }
        DeclaredType declared = (DeclaredType) mirror;
        Element rawElement = declared.asElement();
        if (!(rawElement instanceof TypeElement)) return invalidChild(field, "invalid child container");
        String raw = ((TypeElement) rawElement).getQualifiedName().toString();
        List<? extends TypeMirror> arguments = declared.getTypeArguments();
        boolean list = "java.util.List".equals(raw);
        boolean map = "java.util.Map".equals(raw);
        if ((!list && !map) || arguments.size() != (list ? 1 : 2)) {
            return invalidChild(field,
                    "child field must be exact parameterized java.util.List or java.util.Map");
        }
        TypeMirror rowMirror = arguments.get(list ? 0 : 1);
        if (rowMirror.getKind() != TypeKind.DECLARED
                || !((DeclaredType) rowMirror).getTypeArguments().isEmpty()) {
            return invalidChild(field, "child row type must be a non-generic @SomaTable class");
        }
        Element rowElement = ((DeclaredType) rowMirror).asElement();
        if (!(rowElement instanceof TypeElement)
                || rowElement.getAnnotation(SomaTable.class) == null) {
            return invalidChild(field, "child row type must declare @SomaTable");
        }
        TypeElement rowType = (TypeElement) rowElement;
        String ownerPackage = processingEnv.getElementUtils().getPackageOf(owner)
                .getQualifiedName().toString();
        String rowPackage = processingEnv.getElementUtils().getPackageOf(rowType)
                .getQualifiedName().toString();
        if (!ownerPackage.equals(rowPackage)) {
            return invalidChild(field, "child ownership must remain in one @SomaSchema package");
        }
        List<VariableElement> keys = new ArrayList<VariableElement>();
        for (Element enclosed : rowType.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD
                    && enclosed.getAnnotation(SomaKey.class) != null) {
                keys.add((VariableElement) enclosed);
            }
        }
        if (list && !keys.isEmpty()) {
            return invalidChild(field, "List child row must be dense and declare no @SomaKey");
        }
        String keyMaterializedType = null;
        String keyJavaName = null;
        if (map) {
            if (keys.size() != 1) {
                return invalidChild(field, "Map child row must declare exactly one @SomaKey");
            }
            TypeMirror expected = materializedKeyType(keys.get(0).asType());
            if (expected == null || !processingEnv.getTypeUtils().isSameType(
                    arguments.get(0), expected)) {
                return invalidChild(field,
                        "Map key type must equal child materialized key type");
            }
            keyMaterializedType = expected.toString();
            keyJavaName = keys.get(0).getSimpleName().toString();
        }
        SomaTable childTable = rowType.getAnnotation(SomaTable.class);
        String childLogicalName = childTable.name().isEmpty()
                ? rowType.getSimpleName().toString() : childTable.name();
        return new ChildFieldType(
                list ? "list" : "map",
                rowType.getQualifiedName().toString(),
                rowType.getSimpleName().toString(),
                childLogicalName,
                keyMaterializedType,
                keyJavaName,
                mirror.toString(),
                initialCapacity);
    }

    private TypeMirror materializedKeyType(TypeMirror key) {
        if (key.getKind().isPrimitive()) {
            return processingEnv.getTypeUtils().boxedClass((PrimitiveType) key).asType();
        }
        return key.getKind() == TypeKind.DECLARED ? key : null;
    }

    private ChildFieldType invalidChild(VariableElement field, String message) {
        error(field, "SOMA-TABLE-010", message + ": " + field.asType());
        return null;
    }

    private boolean hasPublicNoArgConstructor(TypeElement type) {
        Types types = processingEnv.getTypeUtils();
        TypeMirror runtimeException = processingEnv.getElementUtils()
                .getTypeElement("java.lang.RuntimeException").asType();
        TypeMirror errorType = processingEnv.getElementUtils()
                .getTypeElement("java.lang.Error").asType();
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            ExecutableElement constructor = (ExecutableElement) enclosed;
            if (!constructor.getModifiers().contains(Modifier.PUBLIC)
                    || !constructor.getParameters().isEmpty()) {
                continue;
            }
            boolean checked = false;
            for (TypeMirror thrown : constructor.getThrownTypes()) {
                if (!types.isSubtype(thrown, runtimeException)
                        && !types.isSubtype(thrown, errorType)) {
                    checked = true;
                    break;
                }
            }
            if (!checked) {
                return true;
            }
        }
        return false;
    }

    private TableFieldType tableFieldType(
            TypeMirror mirror,
            boolean optional,
            boolean key,
            Map<String, ValueModel> validatedValues) {
        if (!optional && mirror.getKind().isPrimitive()) {
            return TableFieldType.forKind(mirror.getKind());
        }
        if (mirror.getKind() != TypeKind.DECLARED) {
            return null;
        }
        Element element = ((DeclaredType) mirror).asElement();
        if (!(element instanceof TypeElement)) {
            return null;
        }
        TypeElement type = (TypeElement) element;
        if (type.getQualifiedName().contentEquals("java.lang.String")) {
            return TableFieldType.forString();
        }
        if (type.getKind() == ElementKind.ENUM) {
            return TableFieldType.forEnum(type, enumModel(type));
        }
        if (type.getAnnotation(SomaValue.class) != null) {
            ValueModel value = validatedValues.get(type.getQualifiedName().toString());
            return value == null ? null
                    : TableFieldType.forFlattenedValue(value, validatedValues);
        }
        if (!optional) {
            return null;
        }
        return TableFieldType.forBoxed(type.getQualifiedName().toString());
    }

    private DefaultModel normalizeTableDefault(
            VariableElement field,
            TableFieldType type,
            SomaSemantic semantic,
            String literal,
            boolean strictFloating) {
        if (type.valueJavaType != null) {
            error(field, "SOMA-TABLE-005",
                    "@SomaDefault does not support an outer value field; declare leaf defaults on @SomaValue");
            return null;
        }
        try {
            if ("java.lang.String".equals(type.storagePrimitiveName)) {
                return new DefaultModel(literal, literal, quote(literal));
            }
            if (type.enumModel != null) {
                if (!type.enumModel.members.contains(literal)) {
                    throw new IllegalArgumentException("unknown enum member");
                }
                return new DefaultModel(literal, literal,
                        type.enumJavaType + "." + literal);
            }
            return normalizePrimitiveDefault(
                    literal, type.primitiveKind, semantic, strictFloating);
        } catch (RuntimeException invalid) {
            error(field, "SOMA-TABLE-005",
                    "invalid schema default for " + field.getSimpleName() + ": " + literal);
            return null;
        }
    }

    private DefaultModel normalizeValueDefault(
            VariableElement field,
            NormalizedType type,
            SomaSemantic semantic,
            String literal) {
        try {
            if (type.valueReference != null) {
                throw new IllegalArgumentException("nested value is not a leaf");
            }
            if ("string".equals(type.text)) {
                return new DefaultModel(literal, literal, quote(literal));
            }
            if (type.enumModel != null) {
                if (!type.enumModel.members.contains(literal)) {
                    throw new IllegalArgumentException("unknown enum member");
                }
                return new DefaultModel(literal, literal,
                        type.enumModel.javaType + "." + literal);
            }
            TableFieldType primitive = TableFieldType.primitiveType(type.text);
            if (primitive == null) throw new IllegalArgumentException("unsupported leaf");
            return normalizePrimitiveDefault(
                    literal, primitive.primitiveKind, semantic, false);
        } catch (RuntimeException invalid) {
            error(field, "SOMA-VALUE-005",
                    "invalid value leaf default for " + field.getSimpleName() + ": " + literal);
            return null;
        }
    }

    private DefaultModel normalizePrimitiveDefault(
            String literal,
            TypeKind kind,
            SomaSemantic semantic,
            boolean strictFloating) {
        if (semantic == SomaSemantic.DATE) {
            long epochDay = java.time.LocalDate.parse(literal).toEpochDay();
            if (epochDay < Integer.MIN_VALUE || epochDay > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("date outside int epoch-day range");
            }
            String value = Integer.toString((int) epochDay);
            return new DefaultModel(literal, value, value);
        }
        if (semantic == SomaSemantic.TIME) {
            String value = Long.toString(java.time.LocalTime.parse(literal).toNanoOfDay());
            return new DefaultModel(literal, value, value + "L");
        }
        if (semantic == SomaSemantic.DATE_TIME) {
            long epochMillis;
            try {
                epochMillis = java.time.Instant.parse(literal).toEpochMilli();
            } catch (java.time.format.DateTimeParseException notInstant) {
                epochMillis = java.time.OffsetDateTime.parse(literal).toInstant().toEpochMilli();
            }
            String value = Long.toString(epochMillis);
            return new DefaultModel(literal, value, value + "L");
        }
        switch (kind) {
            case BOOLEAN:
                if (!"true".equals(literal) && !"false".equals(literal)) {
                    throw new IllegalArgumentException("invalid boolean");
                }
                return new DefaultModel(literal, literal, literal);
            case BYTE: {
                long value = Long.parseLong(literal);
                if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) throw new NumberFormatException();
                String normalized = Long.toString(value);
                return new DefaultModel(literal, normalized, "(byte)" + normalized);
            }
            case SHORT: {
                long value = Long.parseLong(literal);
                if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) throw new NumberFormatException();
                String normalized = Long.toString(value);
                return new DefaultModel(literal, normalized, "(short)" + normalized);
            }
            case INT: {
                int value = Integer.parseInt(literal);
                String normalized = Integer.toString(value);
                return new DefaultModel(literal, normalized, normalized);
            }
            case LONG: {
                long value = Long.parseLong(literal);
                String normalized = Long.toString(value);
                return new DefaultModel(literal, normalized, normalized + "L");
            }
            case FLOAT:
                return normalizeFloatDefault(literal, strictFloating);
            case DOUBLE:
                return normalizeDoubleDefault(literal, strictFloating);
            default:
                throw new IllegalArgumentException("unsupported primitive default");
        }
    }

    private DefaultModel normalizeFloatDefault(String literal, boolean strict) {
        float value = parseFloatDefault(literal);
        if (strict && (Float.isNaN(value) || Float.isInfinite(value))) {
            throw new IllegalArgumentException("non-finite strict float");
        }
        if (strict && value == 0.0f) value = 0.0f;
        String normalized = Float.isNaN(value) ? "NaN"
                : value == Float.POSITIVE_INFINITY ? "Infinity"
                : value == Float.NEGATIVE_INFINITY ? "-Infinity"
                : Float.toString(value);
        String expression = "NaN".equals(normalized) ? "Float.NaN"
                : "Infinity".equals(normalized) ? "Float.POSITIVE_INFINITY"
                : "-Infinity".equals(normalized) ? "Float.NEGATIVE_INFINITY"
                : normalized + "f";
        return new DefaultModel(literal, normalized, expression);
    }

    private DefaultModel normalizeDoubleDefault(String literal, boolean strict) {
        double value = parseDoubleDefault(literal);
        if (strict && (Double.isNaN(value) || Double.isInfinite(value))) {
            throw new IllegalArgumentException("non-finite strict double");
        }
        if (strict && value == 0.0d) value = 0.0d;
        String normalized = Double.isNaN(value) ? "NaN"
                : value == Double.POSITIVE_INFINITY ? "Infinity"
                : value == Double.NEGATIVE_INFINITY ? "-Infinity"
                : Double.toString(value);
        String expression = "NaN".equals(normalized) ? "Double.NaN"
                : "Infinity".equals(normalized) ? "Double.POSITIVE_INFINITY"
                : "-Infinity".equals(normalized) ? "Double.NEGATIVE_INFINITY"
                : normalized + "d";
        return new DefaultModel(literal, normalized, expression);
    }

    private float parseFloatDefault(String literal) {
        if ("NaN".equals(literal)) return Float.NaN;
        if ("Infinity".equals(literal)) return Float.POSITIVE_INFINITY;
        if ("-Infinity".equals(literal)) return Float.NEGATIVE_INFINITY;
        return Float.parseFloat(literal);
    }

    private double parseDoubleDefault(String literal) {
        if ("NaN".equals(literal)) return Double.NaN;
        if ("Infinity".equals(literal)) return Double.POSITIVE_INFINITY;
        if ("-Infinity".equals(literal)) return Double.NEGATIVE_INFINITY;
        return Double.parseDouble(literal);
    }

    private boolean validTableSemantic(SomaSemantic semantic, TypeKind primitiveKind) {
        if (semantic == SomaSemantic.NONE) {
            return true;
        }
        if (semantic == SomaSemantic.DATE) {
            return primitiveKind == TypeKind.INT;
        }
        return primitiveKind == TypeKind.LONG;
    }

    private boolean valueHasDefault(
            String javaType,
            Map<String, ValueModel> values,
            Set<String> visiting) {
        if (!visiting.add(javaType)) return false;
        ValueModel value = values.get(javaType);
        if (value == null) return false;
        for (FieldModel field : value.fields) {
            if (field.defaultValue != null) return true;
            if (field.type.valueReference != null
                    && valueHasDefault(field.type.valueReference, values, visiting)) {
                return true;
            }
        }
        visiting.remove(javaType);
        return false;
    }

    private boolean validateStrictValueDefaults(
            VariableElement tableField,
            String logicalPrefix,
            String valueJavaType,
            Map<String, ValueModel> values,
            Set<String> strictSelectorPaths,
            Set<String> visiting) {
        if (!visiting.add(valueJavaType)) return true;
        ValueModel value = values.get(valueJavaType);
        if (value == null) return false;
        boolean valid = true;
        for (FieldModel leaf : value.fields) {
            String path = logicalPrefix + "." + leaf.logicalName;
            if (leaf.type.valueReference != null) {
                if (!validateStrictValueDefaults(
                        tableField, path, leaf.type.valueReference, values,
                        strictSelectorPaths, visiting)) {
                    valid = false;
                }
            } else if (leaf.defaultValue != null && strictSelectorPaths.contains(path)
                    && ("float".equals(leaf.type.text)
                    || "double".equals(leaf.type.text))) {
                try {
                    if ("float".equals(leaf.type.text)) {
                        normalizeFloatDefault(leaf.defaultValue.literal, true);
                    } else {
                        normalizeDoubleDefault(leaf.defaultValue.literal, true);
                    }
                } catch (RuntimeException invalid) {
                    error(tableField, "SOMA-TABLE-005",
                            "non-finite value default on strict selector path: " + path);
                    valid = false;
                }
            }
        }
        visiting.remove(valueJavaType);
        return valid;
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

    private void validateOwnershipGraph(SchemaModel schema) {
        Set<String> visited = new HashSet<String>();
        Set<String> visiting = new LinkedHashSet<String>();
        List<String> path = new ArrayList<String>();
        for (TableModel table : schema.tables.values()) {
            validateOwnershipGraph(schema, table, visiting, visited, path);
        }
    }

    private void validateOwnershipGraph(
            SchemaModel schema,
            TableModel table,
            Set<String> visiting,
            Set<String> visited,
            List<String> path) {
        if (visited.contains(table.javaType)) return;
        if (!visiting.add(table.javaType)) {
            path.add(table.logicalName);
            error(table.origin, "SOMA-TABLE-011",
                    "cyclic child ownership declaration: " + path);
            path.remove(path.size() - 1);
            return;
        }
        path.add(table.logicalName);
        for (TableFieldModel field : table.fields) {
            if (field.child == null) continue;
            TableModel child = schema.tables.get(field.child.rowJavaType);
            if (child == null) {
                error(table.origin, "SOMA-TABLE-010",
                        "child table must belong to the same schema compilation: "
                                + field.child.rowJavaType);
                continue;
            }
            path.add(field.logicalName);
            if (visiting.contains(child.javaType)) {
                List<String> cycle = new ArrayList<String>(path);
                cycle.add(child.logicalName);
                error(table.origin, "SOMA-TABLE-011",
                        "cyclic child ownership declaration: " + cycle);
            } else {
                validateOwnershipGraph(schema, child, visiting, visited, path);
            }
            path.remove(path.size() - 1);
        }
        path.remove(path.size() - 1);
        visiting.remove(table.javaType);
        visited.add(table.javaType);
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
        if (type.getAnnotationsByType(SomaIndex.class).length != 0
                || type.getAnnotationsByType(SomaUnique.class).length != 0
                || type.getAnnotationsByType(SomaOrder.class).length != 0) {
            error(type, "SOMA-VALUE-003",
                    "@SomaValue cannot declare table access selectors");
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
            SomaKey keyAnnotation = field.getAnnotation(SomaKey.class);
            SomaChild childAnnotation = field.getAnnotation(SomaChild.class);
            SomaOptional optionalAnnotation = field.getAnnotation(SomaOptional.class);
            SomaDefault defaultAnnotation = field.getAnnotation(SomaDefault.class);
            if (field.getModifiers().contains(Modifier.STATIC)) {
                if (fieldAnnotation != null || ignoreAnnotation != null
                        || keyAnnotation != null || childAnnotation != null
                        || optionalAnnotation != null || defaultAnnotation != null) {
                    error(field, "SOMA-VALUE-003",
                            "static field cannot declare SOMA field annotations");
                    valid = false;
                }
                continue;
            }
            if (keyAnnotation != null || childAnnotation != null
                    || optionalAnnotation != null) {
                error(field, "SOMA-VALUE-003",
                        "@SomaValue field cannot declare key, child or optional modifiers");
                valid = false;
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
            DefaultModel defaultValue = defaultAnnotation == null ? null
                    : normalizeValueDefault(field, normalizedType,
                            fieldAnnotation.semantic(), defaultAnnotation.value());
            if (defaultAnnotation != null && defaultValue == null) valid = false;
            fields.add(new FieldModel(
                    field.getSimpleName().toString(), logicalName,
                    fieldAnnotation.semantic().name(), normalizedType, defaultValue));
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

    private void writeSchemaArtifacts(SchemaModel schema) {
        String json = schema.toCanonicalJson();
        String hash = sha256(SCHEMA_HASH_PREFIX + json);
        String basePath = "META-INF/soma/" + schema.sourcePackage;
        try {
            writeResource(basePath + ".schema.json", json + "\n", schema.origin);
            writeResource(basePath + ".schema.sha256", hash + "\n", schema.origin);
            List<DenseTableSourceGenerator.TableSpec> generatedTables =
                    new ArrayList<DenseTableSourceGenerator.TableSpec>();
            for (TableModel table : schema.tables.values()) {
                generatedTables.add(table.toGeneratorSpec());
            }
            DenseTableSourceGenerator generator = new DenseTableSourceGenerator(
                    processingEnv.getFiler(), schema.generatedPackage, hash,
                    generatedTables);
            for (TableModel table : schema.tables.values()) {
                generator.generate(table.toGeneratorSpec());
            }
        } catch (IOException exception) {
            error(schema.origin, "SOMA-OUTPUT-001",
                    "failed to write deterministic schema artifacts: "
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
        private final Map<String, TableModel> tables = new TreeMap<String, TableModel>();

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

        private void addTable(TableModel table) {
            tables.put(table.javaType, table);
            for (TableFieldModel field : table.fields) {
                if (field.type != null && field.type.enumModel != null) {
                    enums.put(field.type.enumModel.javaType, field.type.enumModel);
                }
            }
        }

        private TableModel tableByLogicalName(String logicalName) {
            for (TableModel table : tables.values()) {
                if (table.logicalName.equals(logicalName)) {
                    return table;
                }
            }
            return null;
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
        private final DefaultModel defaultValue;

        private FieldModel(
                String javaName,
                String logicalName,
                String semantic,
                NormalizedType type,
                DefaultModel defaultValue) {
            this.javaName = javaName;
            this.logicalName = logicalName;
            this.semantic = semantic;
            this.type = type;
            this.defaultValue = defaultValue;
        }

        private void appendJson(StringBuilder json) {
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

    private static final class DefaultModel {
        private final String literal;
        private final String normalized;
        private final String javaExpression;

        private DefaultModel(String literal, String normalized, String javaExpression) {
            this.literal = literal;
            this.normalized = normalized;
            this.javaExpression = javaExpression;
        }

        private void appendJson(StringBuilder json) {
            json.append('{').append("\"literal\":").append(quote(literal)).append(',')
                    .append("\"normalized\":").append(quote(normalized)).append('}');
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

    private static final class TableModel {
        private final TypeElement origin;
        private final String javaType;
        private final String simpleName;
        private final String logicalName;
        private final int defaultCapacity;
        private final List<TableFieldModel> fields;
        private final List<SelectorModel> selectors;

        private TableModel(TypeElement origin, String javaType, String simpleName,
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

        private void appendJson(StringBuilder json) {
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

        private DenseTableSourceGenerator.TableSpec toGeneratorSpec() {
            List<DenseTableSourceGenerator.FieldSpec> result =
                    new ArrayList<DenseTableSourceGenerator.FieldSpec>();
            List<DenseTableSourceGenerator.ChildSpec> childResult =
                    new ArrayList<DenseTableSourceGenerator.ChildSpec>();
            for (TableFieldModel field : fields) {
                if (field.child == null) result.add(field.toGeneratorSpec());
                else childResult.add(field.toGeneratorChildSpec());
            }
            List<DenseTableSourceGenerator.SelectorSpec> generatedSelectors =
                    new ArrayList<DenseTableSourceGenerator.SelectorSpec>();
            for (SelectorModel selector : selectors) {
                generatedSelectors.add(selector.toGeneratorSpec());
            }
            return new DenseTableSourceGenerator.TableSpec(
                    origin, javaType, simpleName, logicalName,
                    defaultCapacity < 0 ? 16 : defaultCapacity,
                    result, childResult, generatedSelectors);
        }

        private boolean hasKey() {
            for (TableFieldModel field : fields) {
                if (field.key) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class SelectorModel {
        private final String kind;
        private final String name;
        private final List<SelectorLeafModel> leaves;

        private SelectorModel(
                String kind, String name, List<SelectorLeafModel> leaves) {
            this.kind = kind;
            this.name = name;
            this.leaves = leaves;
        }

        private void appendJson(StringBuilder json) {
            json.append('{');
            json.append("\"kind\":").append(quote(kind)).append(',');
            json.append("\"leaves\":[");
            for (int i = 0; i < leaves.size(); i++) {
                if (i > 0) json.append(',');
                leaves.get(i).appendJson(json);
            }
            json.append("],\"name\":").append(quote(name)).append('}');
        }

        private DenseTableSourceGenerator.SelectorSpec toGeneratorSpec() {
            List<DenseTableSourceGenerator.SelectorLeafSpec> result =
                    new ArrayList<DenseTableSourceGenerator.SelectorLeafSpec>();
            for (SelectorLeafModel leaf : leaves) {
                result.add(new DenseTableSourceGenerator.SelectorLeafSpec(
                        leaf.path, leaf.direction, leaf.publicType,
                        leaf.storageType, leaf.enumType));
            }
            return new DenseTableSourceGenerator.SelectorSpec(kind, name, result);
        }

        private String generatedMethodName() {
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
            return ("order".equals(kind) ? "by" : "findBy") + suffix;
        }
    }

    private static final class SelectorLeafModel {
        private final String path;
        private final String direction;
        private final String publicType;
        private final String storageType;
        private final String enumType;

        private SelectorLeafModel(
                String path, String direction, String publicType,
                String storageType, String enumType) {
            this.path = path;
            this.direction = direction;
            this.publicType = publicType;
            this.storageType = storageType;
            this.enumType = enumType;
        }

        private void appendJson(StringBuilder json) {
            json.append('{');
            json.append("\"direction\":").append(quote(direction)).append(',');
            json.append("\"path\":").append(quote(path)).append(',');
            json.append("\"storageType\":").append(quote(storageType));
            json.append('}');
        }
    }

    private static final class TableFieldModel {
        private final String javaName;
        private final String logicalName;
        private final String semantic;
        private final TableFieldType type;
        private final ChildFieldType child;
        private final boolean optional;
        private final boolean key;
        private DefaultModel defaultValue;

        private TableFieldModel(String javaName, String logicalName, String semantic,
                                TableFieldType type, ChildFieldType child,
                                boolean optional, boolean key,
                                DefaultModel defaultValue) {
            this.javaName = javaName;
            this.logicalName = logicalName;
            this.semantic = semantic;
            this.type = type;
            this.child = child;
            this.optional = optional;
            this.key = key;
            this.defaultValue = defaultValue;
        }

        private void appendJson(StringBuilder json) {
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

        private DenseTableSourceGenerator.FieldSpec toGeneratorSpec() {
            List<DenseTableSourceGenerator.ValueLeafSpec> leaves =
                    new ArrayList<DenseTableSourceGenerator.ValueLeafSpec>();
            for (ValueLeafType leaf : type.valueLeaves) {
                leaves.add(new DenseTableSourceGenerator.ValueLeafSpec(
                        leaf.javaName, leaf.storageName, leaf.logicalName, leaf.semantic,
                        leaf.publicPrimitiveName, leaf.storagePrimitiveName,
                        leaf.columnType, leaf.enumJavaType));
            }
            List<DenseTableSourceGenerator.ValueGroupSpec> groups =
                    new ArrayList<DenseTableSourceGenerator.ValueGroupSpec>();
            for (ValueGroupType group : type.valueGroups) {
                groups.add(new DenseTableSourceGenerator.ValueGroupSpec(
                        group.javaPath, group.logicalPath, group.javaType,
                        group.firstLeaf, group.leafCount, group.directFieldCount));
            }
            return new DenseTableSourceGenerator.FieldSpec(
                    javaName, logicalName, type.publicType,
                    type.boxedName, type.storagePrimitiveName, type.columnType,
                    type.enumJavaType, type.valueJavaType, type.valueLeafJavaName,
                    type.valueConstructionTemplate, leaves, groups, optional, key,
                    defaultValue != null ? defaultValue.javaExpression
                            : type.valueDefaultExpression);
        }

        private DenseTableSourceGenerator.ChildSpec toGeneratorChildSpec() {
            return new DenseTableSourceGenerator.ChildSpec(
                    javaName, logicalName, child.container, child.rowJavaType,
                    child.rowSimpleName, child.tableLogicalName,
                    child.keyMaterializedType, child.keyJavaName, child.materializedType,
                    child.initialCapacity, optional);
        }

        private static void appendLeafJson(
                StringBuilder json, String path, String semantic, String storageType) {
            json.append('{');
            json.append("\"leafPath\":").append(quote(path)).append(',');
            json.append("\"semantic\":").append(quote(semantic)).append(',');
            json.append("\"storageType\":").append(quote(storageType));
            json.append('}');
        }
    }

    private static final class ChildFieldType {
        private final String container;
        private final String rowJavaType;
        private final String rowSimpleName;
        private final String tableLogicalName;
        private final String keyMaterializedType;
        private final String keyJavaName;
        private final String materializedType;
        private final int initialCapacity;

        private ChildFieldType(
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

    private static final class TableFieldType {
        private final TypeKind primitiveKind;
        private final String logicalType;
        private final String publicType;
        private final String boxedName;
        private final String materializedType;
        private final String storagePrimitiveName;
        private final String columnType;
        private final String enumJavaType;
        private final EnumModel enumModel;
        private final String valueJavaType;
        private final String valueLeafJavaName;
        private final String valueLeafLogicalName;
        private final String valueLeafSemantic;
        private final String valueConstructionTemplate;
        private final String valueDefaultExpression;
        private final List<ValueLeafType> valueLeaves;
        private final List<ValueGroupType> valueGroups;

        private TableFieldType(
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

        private static TableFieldType forKind(TypeKind kind) {
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

        private static TableFieldType forBoxed(String javaType) {
            if ("java.lang.Boolean".equals(javaType)) return forKind(TypeKind.BOOLEAN);
            if ("java.lang.Byte".equals(javaType)) return forKind(TypeKind.BYTE);
            if ("java.lang.Short".equals(javaType)) return forKind(TypeKind.SHORT);
            if ("java.lang.Integer".equals(javaType)) return forKind(TypeKind.INT);
            if ("java.lang.Long".equals(javaType)) return forKind(TypeKind.LONG);
            if ("java.lang.Float".equals(javaType)) return forKind(TypeKind.FLOAT);
            if ("java.lang.Double".equals(javaType)) return forKind(TypeKind.DOUBLE);
            return null;
        }

        private static TableFieldType forString() {
            return new TableFieldType(
                    null, "string", "java.lang.String", "java.lang.String",
                    "java.lang.String", "java.lang.String",
                    "ObjectColumn<java.lang.String>", null, null,
                    null, null, null, null, null, null,
                    new ArrayList<ValueLeafType>(), new ArrayList<ValueGroupType>());
        }

        private static TableFieldType forEnum(TypeElement type, EnumModel enumModel) {
            String javaType = type.getQualifiedName().toString();
            return new TableFieldType(
                    null, "enum:" + javaType, javaType, javaType, javaType,
                    "int", "IntColumn", javaType, enumModel,
                    null, null, null, null, null, null,
                    new ArrayList<ValueLeafType>(), new ArrayList<ValueGroupType>());
        }

        private static TableFieldType forFlattenedValue(
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

        private static String valueDefaultExpression(
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

        private static String flattenValue(
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

        private static ValueLeafType valueLeaf(
                FieldModel field, String javaPath, String logicalPath,
                String storageName) {
            TableFieldType primitive = primitiveType(field.type.text);
            if (primitive != null) {
                return new ValueLeafType(javaPath, storageName, logicalPath,
                        field.semantic, primitive.publicType,
                        primitive.storagePrimitiveName, primitive.columnType, null);
            }
            if ("string".equals(field.type.text)) {
                return new ValueLeafType(javaPath, storageName, logicalPath,
                        field.semantic, "java.lang.String", "java.lang.String",
                        "ObjectColumn<java.lang.String>", null);
            }
            if (field.type.enumModel != null) {
                String enumType = field.type.enumModel.javaType;
                return new ValueLeafType(javaPath, storageName, logicalPath,
                        field.semantic, enumType, "int", "IntColumn", enumType);
            }
            return null;
        }

        private static TypeKind primitiveKind(String storageType) {
            TableFieldType primitive = primitiveType(storageType);
            return primitive == null ? null : primitive.primitiveKind;
        }

        private static String capitalize(String value) {
            return Character.toUpperCase(value.charAt(0)) + value.substring(1);
        }

        private static TableFieldType primitiveType(String text) {
            if ("boolean".equals(text)) return forKind(TypeKind.BOOLEAN);
            if ("byte".equals(text)) return forKind(TypeKind.BYTE);
            if ("short".equals(text)) return forKind(TypeKind.SHORT);
            if ("int".equals(text)) return forKind(TypeKind.INT);
            if ("long".equals(text)) return forKind(TypeKind.LONG);
            if ("float".equals(text)) return forKind(TypeKind.FLOAT);
            if ("double".equals(text)) return forKind(TypeKind.DOUBLE);
            return null;
        }

        private static TableFieldType type(TypeKind kind, String primitive,
                                           String boxed, String column) {
            return new TableFieldType(
                    kind, primitive, primitive, boxed, primitive, primitive,
                    column, null, null, null, null, null, null,
                    null, null,
                    new ArrayList<ValueLeafType>(), new ArrayList<ValueGroupType>());
        }
    }

    private static final class ValueLeafType {
        private final String javaName;
        private final String storageName;
        private final String logicalName;
        private final String semantic;
        private final String publicPrimitiveName;
        private final String storagePrimitiveName;
        private final String columnType;
        private final String enumJavaType;

        private ValueLeafType(
                String javaName, String storageName, String logicalName, String semantic,
                String publicPrimitiveName, String storagePrimitiveName,
                String columnType, String enumJavaType) {
            this.javaName = javaName;
            this.storageName = storageName;
            this.logicalName = logicalName;
            this.semantic = semantic;
            this.publicPrimitiveName = publicPrimitiveName;
            this.storagePrimitiveName = storagePrimitiveName;
            this.columnType = columnType;
            this.enumJavaType = enumJavaType;
        }
    }

    private static final class ValueGroupType {
        private final String javaPath;
        private final String logicalPath;
        private final String javaType;
        private final int firstLeaf;
        private final int leafCount;
        private final int directFieldCount;

        private ValueGroupType(
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
}
