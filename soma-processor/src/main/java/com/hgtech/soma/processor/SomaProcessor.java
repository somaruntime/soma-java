package com.hgtech.soma.processor;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaIgnore;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaIndexes;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaSchema;
import com.hgtech.soma.annotation.SomaSemantic;
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
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

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
        "com.hgtech.soma.annotation.SomaUniques"
})
public final class SomaProcessor extends AbstractProcessor {
    private static final char[] LOWER_HEX = "0123456789abcdef".toCharArray();
    private static final char[] UPPER_HEX = "0123456789ABCDEF".toCharArray();
    private static final int MAXIMUM_DEFAULT_LITERAL_LENGTH = 4096;
    private static final Pattern DECIMAL_FLOATING_LITERAL = Pattern.compile(
            "[+-]?(?:(?:[0-9]+(?:\\.[0-9]*)?)|(?:\\.[0-9]+))(?:[eE][+-]?[0-9]+)?");
    private static final String GENERATED_TARGET = "java8-columnar";
    private static final String SCHEMA_HASH_PREFIX = "soma-java:v1:schema\n";

    private final Map<String, TypeElement> values = new LinkedHashMap<String, TypeElement>();
    private final Map<String, TypeElement> tables = new LinkedHashMap<String, TypeElement>();
    private final Map<String, PackageElement> schemaPackages =
            new LinkedHashMap<String, PackageElement>();
    private final Set<Element> invalidSelectorOwners = new HashSet<Element>();
    private boolean initialCollectionClosed;
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
        if (!compilerSession.getPluginDiagnostics().isEmpty()) {
            hasErrors = true;
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        if (roundEnvironment.errorRaised()) hasErrors = true;
        validateChildPlacement(roundEnvironment.getElementsAnnotatedWith(SomaChild.class));
        validateSelectorPlacement(roundEnvironment.getElementsAnnotatedWith(SomaIndex.class));
        validateSelectorPlacement(roundEnvironment.getElementsAnnotatedWith(SomaIndexes.class));
        validateSelectorPlacement(roundEnvironment.getElementsAnnotatedWith(SomaUnique.class));
        validateSelectorPlacement(roundEnvironment.getElementsAnnotatedWith(SomaUniques.class));
        for (Element element : roundEnvironment.getElementsAnnotatedWith(SomaValue.class)) {
            if (element instanceof TypeElement) {
                TypeElement type = (TypeElement) element;
                boolean firstSeen = !values.containsKey(type.getQualifiedName().toString());
                if (initialCollectionClosed && firstSeen) lateDeclaration(type);
                values.put(type.getQualifiedName().toString(), type);
            }
        }
        for (Element element : roundEnvironment.getElementsAnnotatedWith(SomaTable.class)) {
            if (element instanceof TypeElement) {
                TypeElement type = (TypeElement) element;
                boolean firstSeen = !tables.containsKey(type.getQualifiedName().toString());
                if (initialCollectionClosed && firstSeen) lateDeclaration(type);
                tables.put(type.getQualifiedName().toString(), type);
            }
        }
        for (Element element : roundEnvironment.getElementsAnnotatedWith(SomaSchema.class)) {
            if (element instanceof PackageElement) {
                PackageElement packageElement = (PackageElement) element;
                boolean firstSeen = !schemaPackages.containsKey(
                        packageElement.getQualifiedName().toString());
                if (initialCollectionClosed && firstSeen) lateDeclaration(packageElement);
                schemaPackages.put(packageElement.getQualifiedName().toString(), packageElement);
            }
        }

        if (!roundEnvironment.processingOver() && !initialCollectionClosed) {
            initialCollectionClosed = true;
            if (!finished && (!values.isEmpty() || !tables.isEmpty())) {
                finished = true;
                finishProcessing();
            }
        }
        return false;
    }

    private void lateDeclaration(Element element) {
        error(element, "SOMA-COMP-007",
                "SOMA schema declaration was generated after the initial source round");
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

        Map<String, SchemaModel> schemas = new TreeMap<String, SchemaModel>(
                UnicodeCodePointOrder.INSTANCE);
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

        for (SchemaModel schema : schemas.values()) {
            validateValueGraph(schema);
        }
        if (hasErrors) return;

        for (TypeElement table : tables.values()) {
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
            TableModel model = validateTable(table, schema.values);
            if (model == null) {
                continue;
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
            validateOwnershipGraph(schema);
        }
        if (hasErrors) {
            return;
        }
        List<SchemaArtifactPlan> compilationPlan = buildCompilationPlan(schemas);
        if (hasErrors) return;
        emitCompilationPlan(compilationPlan);
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
        Set<String> strictSelectorPaths = selectorPaths(type);
        Map<String, Integer> flattenedLeafCounts = new LinkedHashMap<String, Integer>();
        int physicalLeafCount = 0;
        boolean physicalLeafLimitReported = false;
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
            TypeElement declaredValueType = child ? null : somaValueType(field.asType());
            if (declaredValueType != null && !validatedValues.containsKey(
                    declaredValueType.getQualifiedName().toString())) {
                error(field, "SOMA-VALUE-008",
                        "table value reference must belong to the same schema compilation graph: "
                                + declaredValueType.getQualifiedName());
                valid = false;
                continue;
            }
            int fieldLeafCount = child ? 0 : declaredValueType == null ? 1
                    : flattenedValueLeafCount(
                            declaredValueType.getQualifiedName().toString(),
                            validatedValues, flattenedLeafCounts,
                            new HashSet<String>());
            physicalLeafCount = saturatingAdd(
                    physicalLeafCount, fieldLeafCount,
                    CodegenLimits.MAXIMUM_TABLE_PHYSICAL_LEAVES + 1);
            if (physicalLeafCount > CodegenLimits.MAXIMUM_TABLE_PHYSICAL_LEAVES
                    && !physicalLeafLimitReported) {
                codegenAdmissionError(field, "table-physical-leaves",
                        CodegenLimits.MAXIMUM_TABLE_PHYSICAL_LEAVES, physicalLeafCount,
                        type.getQualifiedName().toString());
                physicalLeafLimitReported = true;
                valid = false;
            }
            if (physicalLeafCount > CodegenLimits.MAXIMUM_TABLE_PHYSICAL_LEAVES) {
                continue;
            }
            TableFieldType tableType = child ? null : tableFieldType(
                    field.asType(), optional != null, key, validatedValues);
            if (child && childType == null) {
                valid = false;
                continue;
            }
            if (tableType == null) {
                if (child) {
                    fields.add(new TableFieldModel(
                            field, field.getSimpleName().toString(), fieldLogicalName,
                            SomaSemantic.NONE.name(), null, childType,
                            optional != null, false, null));
                    continue;
                }
                error(field, "SOMA-TABLE-005",
                        "unsupported SOMA table field type or optional materialized shape: "
                                + field.asType());
                valid = false;
                continue;
            }
            if (tableType.valueJavaType != null
                    && !valueBelongsToTableSchema(type, tableType.valueJavaType)) {
                error(field, "SOMA-VALUE-008",
                        "table value reference must belong to the same schema compilation: "
                                + tableType.valueJavaType);
                valid = false;
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
            if (tableType.valueJavaType != null
                    && annotationSemantic != SomaSemantic.NONE) {
                error(field, "SOMA-TABLE-005",
                        "outer value field semantic must be NONE; declare semantic on scalar leaves");
                valid = false;
            } else if (!validTableSemantic(annotationSemantic, tableType.primitiveKind)) {
                error(field, "SOMA-TABLE-005",
                        "semantic " + annotationSemantic
                                + " is incompatible with " + field.asType());
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
                    field, field.getSimpleName().toString(), fieldLogicalName,
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
        TableModel result = new TableModel(type, type.getQualifiedName().toString(),
                type.getSimpleName().toString(), logicalName, defaultCapacity,
                fields, selectors);
        if (!validateGeneratedSignatures(result)) valid = false;
        return valid ? result : null;
    }

    private int flattenedValueLeafCount(
            String valueJavaType,
            Map<String, ValueModel> values,
            Map<String, Integer> knownCounts,
            Set<String> visiting) {
        Integer known = knownCounts.get(valueJavaType);
        if (known != null) return known.intValue();
        if (!visiting.add(valueJavaType)) {
            return CodegenLimits.MAXIMUM_TABLE_PHYSICAL_LEAVES + 1;
        }
        ValueModel value = values.get(valueJavaType);
        if (value == null) return CodegenLimits.MAXIMUM_TABLE_PHYSICAL_LEAVES + 1;
        int count = 0;
        for (FieldModel field : value.fields) {
            int fieldCount = field.type.valueReference == null ? 1
                    : flattenedValueLeafCount(
                            field.type.valueReference, values, knownCounts, visiting);
            count = saturatingAdd(count, fieldCount,
                    CodegenLimits.MAXIMUM_TABLE_PHYSICAL_LEAVES + 1);
            if (count > CodegenLimits.MAXIMUM_TABLE_PHYSICAL_LEAVES) break;
        }
        visiting.remove(valueJavaType);
        knownCounts.put(valueJavaType, Integer.valueOf(count));
        return count;
    }

    private int saturatingAdd(int left, int right, int maximum) {
        if (left >= maximum || right >= maximum || left > maximum - right) {
            return maximum;
        }
        return left + right;
    }

    private boolean validateGeneratedSignatures(TableModel model) {
        DenseTableCodegenModel.TableSpec table = model.toGeneratorSpec();
        Map<String, Element> signatures = new LinkedHashMap<String, Element>();
        Map<String, Element> fields = new LinkedHashMap<String, Element>();
        boolean valid = true;

        String rowScope = model.javaType + "#row-family";
        valid &= generatedSignature(signatures, rowScope, "getClass", model.origin);
        valid &= generatedSignature(signatures, rowScope, "hashCode", model.origin);
        valid &= generatedSignature(signatures, rowScope, "toString", model.origin);
        valid &= generatedSignature(signatures, rowScope, "clone", model.origin);
        valid &= generatedSignature(signatures, rowScope, "finalize", model.origin);
        valid &= generatedSignature(signatures, rowScope, "notify", model.origin);
        valid &= generatedSignature(signatures, rowScope, "notifyAll", model.origin);
        valid &= generatedSignature(signatures, rowScope, "wait", model.origin);
        valid &= generatedSignature(signatures, rowScope, "wait", model.origin, "long");
        valid &= generatedSignature(
                signatures, rowScope, "wait", model.origin, "long", "int");

        Map<String, TableFieldModel> origins = new LinkedHashMap<String, TableFieldModel>();
        for (TableFieldModel field : model.fields) origins.put(field.javaName, field);
        for (DenseTableCodegenModel.FieldSpec field : table.fields) {
            TableFieldModel source = origins.get(field.javaName);
            valid &= generatedSignature(
                    signatures, rowScope, field.javaName, source.origin);
            if (field.optional) {
                valid &= generatedSignature(
                        signatures, rowScope, field.javaName + "Present", source.origin);
                valid &= generatedSignature(
                        signatures, rowScope, field.javaName + "Absent", source.origin);
                valid &= generatedSignature(signatures, rowScope,
                        field.javaName + "Or", source.origin, field.primitive);
            }
            if (field.valueBacked()) {
                for (DenseTableCodegenModel.ValueLeafSpec leaf : field.valueLeaves) {
                    valid &= generatedSignature(
                            signatures, rowScope, leaf.stem(field), source.origin);
                }
            }
            if (!field.key) {
                valid &= generatedSignature(signatures, rowScope,
                        "set" + capitalize(field.javaName), source.origin, field.primitive);
                if (field.valueBacked()) {
                    for (DenseTableCodegenModel.ValueLeafSpec leaf : field.valueLeaves) {
                        valid &= generatedSignature(signatures, rowScope,
                                "set" + capitalize(leaf.stem(field)),
                                source.origin, leaf.primitive);
                    }
                }
                if (field.optional) {
                    valid &= generatedSignature(signatures, rowScope,
                            "clear" + capitalize(field.javaName), source.origin);
                }
            }
        }

        String builderScope = model.javaType + "#batch-row-builder";
        String mutatorScope = model.javaType + "#mutator";
        for (DenseTableCodegenModel.FieldSpec field : table.fields) {
            TableFieldModel source = origins.get(field.javaName);
            String capitalized = capitalize(field.javaName);
            valid &= generatedSignature(signatures, builderScope,
                    "set" + capitalized, source.origin, field.primitive);
            if (field.optional) {
                valid &= generatedSignature(signatures, builderScope,
                        "clear" + capitalized, source.origin);
            }
            if (!field.key) {
                valid &= generatedSignature(signatures, mutatorScope,
                        "set" + capitalized, source.origin, field.primitive);
                if (field.optional) {
                    valid &= generatedSignature(signatures, mutatorScope,
                            "clear" + capitalized, source.origin);
                }
            }
        }
        valid &= generatedSignature(signatures, mutatorScope, "commit", model.origin);
        for (DenseTableCodegenModel.ChildSpec child : table.children) {
            TableFieldModel source = origins.get(child.javaName);
            valid &= generatedSignature(signatures, builderScope,
                    "set" + capitalize(child.javaName), source.origin, child.batchType());
            if (child.optional) {
                valid &= generatedSignature(signatures, builderScope,
                        "clear" + capitalize(child.javaName), source.origin);
            }
        }

        String tableScope = model.javaType + "#table";
        valid &= registerFixedTableSignatures(signatures, tableScope, table, model.origin);
        for (DenseTableCodegenModel.FieldSpec field : table.fields) {
            TableFieldModel source = origins.get(field.javaName);
            if (field.supportsColumnAccess()) {
                valid &= generatedSignature(signatures, tableScope,
                        field.javaName + "Values", source.origin);
                valid &= generatedSignature(signatures, tableScope,
                        field.javaName + "Column", source.origin);
            }
            if (field.valueBacked()) {
                for (DenseTableCodegenModel.ValueLeafSpec leaf : field.valueLeaves) {
                    if (!leaf.supportsColumnAccess()) continue;
                    valid &= generatedSignature(signatures, tableScope,
                            leaf.stem(field) + "s", source.origin);
                    valid &= generatedSignature(signatures, tableScope,
                            leaf.stem(field) + "Column", source.origin);
                }
            }
        }
        String locatorType = table.keyed() ? table.keyField().primitive : "int";
        for (DenseTableCodegenModel.ChildSpec child : table.children) {
            TableFieldModel source = origins.get(child.javaName);
            String capitalized = capitalize(child.javaName);
            if (child.optional) {
                valid &= generatedSignature(signatures, tableScope,
                        child.javaName + "Present", source.origin, locatorType);
                valid &= generatedSignature(signatures, tableScope,
                        child.javaName + "OrThrow", source.origin, locatorType);
                valid &= generatedSignature(signatures, tableScope,
                        "ensure" + capitalized, source.origin, locatorType);
                valid &= generatedSignature(signatures, tableScope,
                        "unset" + capitalized, source.origin, locatorType);
            } else {
                valid &= generatedSignature(signatures, tableScope,
                        child.javaName, source.origin, locatorType);
            }
            valid &= generatedSignature(signatures, tableScope,
                    "replace" + capitalized, source.origin,
                    locatorType, child.batchType());
        }
        for (int i = 0; i < model.selectors.size(); i++) {
            SelectorModel selector = model.selectors.get(i);
            DenseTableCodegenModel.SelectorSpec generated = table.selectors.get(i);
            List<String> parameters =
                    DenseTableSourceGenerator.selectorPublicParameterTypes(table, generated);
            valid &= generatedSignature(signatures, tableScope,
                    selector.generatedMethodName(), model.origin,
                    parameters.toArray(new String[parameters.size()]));
            if ("unique".equals(selector.kind)) {
                String suffix = selector.generatedSuffix();
                String[] pointParameters = parameters.toArray(new String[parameters.size()]);
                valid &= generatedSignature(signatures, tableScope,
                        "containsBy" + suffix, model.origin, pointParameters);
                valid &= generatedSignature(signatures, tableScope,
                        "findIndexBy" + suffix, model.origin, pointParameters);
                valid &= generatedSignature(signatures, tableScope,
                        "requireIndexBy" + suffix, model.origin, pointParameters);
                valid &= generatedSignature(signatures, tableScope,
                        "findBy" + suffix, model.origin, pointParameters);
                valid &= generatedSignature(signatures, tableScope,
                        "fetchBy" + suffix, model.origin, pointParameters);
                valid &= generatedSignature(signatures, tableScope,
                        "mutateBy" + suffix, model.origin, pointParameters);
                valid &= generatedSignature(signatures, tableScope,
                        "deleteBy" + suffix, model.origin, pointParameters);
                List<String> budgetParameters = new ArrayList<String>(parameters);
                budgetParameters.add("com.hgtech.soma.runtime.MaterializationBudget");
                String[] withBudget = budgetParameters.toArray(
                        new String[budgetParameters.size()]);
                valid &= generatedSignature(signatures, tableScope,
                        "findBy" + suffix, model.origin, withBudget);
                valid &= generatedSignature(signatures, tableScope,
                        "fetchBy" + suffix, model.origin, withBudget);
            }
        }

        String batchFieldScope = model.javaType + "#batch-fields";
        String tableFieldScope = model.javaType + "#table-fields";
        if (!table.children.isEmpty()) {
            valid &= generatedField(fields, tableFieldScope,
                    "ownerTokenColumn", model.origin);
        }
        for (DenseTableCodegenModel.FieldSpec field : table.fields) {
            TableFieldModel source = origins.get(field.javaName);
            if (field.flattenedValueStorage()) {
                for (int i = 0; i < field.valueLeaves.size(); i++) {
                    DenseTableCodegenModel.ValueLeafSpec leaf = field.valueLeaves.get(i);
                    String physical = field.javaName + "Leaf" + i;
                    valid &= generatedField(fields, batchFieldScope,
                            physical + "Values", source.origin);
                    valid &= generatedField(fields, tableFieldScope,
                            physical + "Column", source.origin);
                    if (leaf.enumType != null) {
                        valid &= generatedField(fields, batchFieldScope,
                                physical.toUpperCase(java.util.Locale.ROOT)
                                        + "_ENUM_VALUES", source.origin);
                        valid &= generatedField(fields, tableFieldScope,
                                physical.toUpperCase(java.util.Locale.ROOT)
                                        + "_ENUM_VALUES", source.origin);
                    }
                }
            } else {
                valid &= generatedField(fields, batchFieldScope,
                        field.javaName + "Values", source.origin);
                valid &= generatedField(fields, tableFieldScope,
                        field.javaName + "Column", source.origin);
                if (field.enumType != null) {
                    valid &= generatedField(fields, batchFieldScope,
                            field.javaName.toUpperCase(java.util.Locale.ROOT)
                                    + "_ENUM_VALUES", source.origin);
                    valid &= generatedField(fields, tableFieldScope,
                            field.javaName.toUpperCase(java.util.Locale.ROOT)
                                    + "_ENUM_VALUES", source.origin);
                }
            }
            if (field.optional) {
                valid &= generatedField(fields, batchFieldScope,
                        field.javaName + "Presence", source.origin);
                valid &= generatedField(fields, tableFieldScope,
                        field.javaName + "Presence", source.origin);
            }
        }
        for (DenseTableCodegenModel.ChildSpec child : table.children) {
            TableFieldModel source = origins.get(child.javaName);
            valid &= generatedField(fields, batchFieldScope,
                    child.javaName + "Values", source.origin);
            valid &= generatedField(fields, batchFieldScope,
                    child.javaName + "Present", source.origin);
            valid &= generatedField(fields, tableFieldScope,
                    child.javaName + "HandleColumn", source.origin);
            if (child.optional) {
                valid &= generatedField(fields, tableFieldScope,
                        child.javaName + "ChildPresence", source.origin);
            }
        }
        return valid;
    }

    private boolean registerFixedTableSignatures(
            Map<String, Element> signatures,
            String scope,
            DenseTableCodegenModel.TableSpec table,
            Element origin) {
        boolean valid = true;
        valid &= generatedSignature(signatures, scope, "create", origin);
        valid &= generatedSignature(signatures, scope, "create", origin,
                "com.hgtech.soma.runtime.RuntimePlan");
        valid &= generatedSignature(signatures, scope, "defaultRuntimePlan", origin);
        valid &= generatedSignature(signatures, scope, "runtimePlan", origin);
        valid &= generatedSignature(signatures, scope, "size", origin);
        valid &= generatedSignature(signatures, scope, "capacity", origin);
        valid &= generatedSignature(signatures, scope, "structuralEpoch", origin);
        valid &= generatedSignature(signatures, scope, "isReleased", origin);
        valid &= generatedSignature(signatures, scope, "reserve", origin, "int");
        valid &= generatedSignature(signatures, scope, "addBatch", origin,
                table.name("Batch"));
        valid &= generatedSignature(signatures, scope, "replaceAll", origin,
                table.name("Batch"));
        valid &= generatedSignature(signatures, scope, "clear", origin);
        valid &= generatedSignature(signatures, scope, "release", origin);
        valid &= generatedSignature(signatures, scope, "mutateAt", origin, "int");
        valid &= generatedSignature(signatures, scope, "filter", origin,
                table.name("Scan") + ".Predicate");
        valid &= generatedSignature(signatures, scope, "skip", origin, "long");
        valid &= generatedSignature(signatures, scope, "limit", origin, "long");
        valid &= generatedSignature(signatures, scope, "sorted", origin,
                table.name("Scan") + ".Comparator");
        valid &= generatedSignature(signatures, scope, "count", origin);
        valid &= generatedSignature(signatures, scope, "anyMatch", origin,
                table.name("Scan") + ".Predicate");
        valid &= generatedSignature(signatures, scope, "noneMatch", origin,
                table.name("Scan") + ".Predicate");
        valid &= generatedSignature(signatures, scope, "forEach", origin,
                table.name("Scan") + ".Consumer");
        valid &= generatedSignature(signatures, scope, "findIndex", origin);
        valid &= generatedSignature(signatures, scope, "requireIndex", origin);
        valid &= generatedSignature(signatures, scope, "findFirst", origin);
        valid &= generatedSignature(signatures, scope, "findFirst", origin,
                "com.hgtech.soma.runtime.MaterializationBudget");
        valid &= generatedSignature(signatures, scope, "firstOrThrow", origin);
        valid &= generatedSignature(signatures, scope, "firstOrThrow", origin,
                "com.hgtech.soma.runtime.MaterializationBudget");
        valid &= generatedSignature(signatures, scope, "fetchAll", origin);
        valid &= generatedSignature(signatures, scope, "fetchAll", origin,
                "com.hgtech.soma.runtime.MaterializationBudget");
        valid &= generatedSignature(signatures, scope, "indexSnapshot", origin);
        valid &= generatedSignature(signatures, scope, "update", origin,
                table.name("Scan") + ".Updater");
        valid &= generatedSignature(signatures, scope, "remove", origin);
        valid &= generatedSignature(signatures, scope, "statsSnapshot", origin);
        valid &= generatedSignature(signatures, scope, "resetStats", origin);
        if (table.keyed()) {
            DenseTableCodegenModel.FieldSpec key = table.keyField();
            valid &= generatedSignature(signatures, scope, "containsKey", origin,
                    key.primitive);
            valid &= generatedSignature(signatures, scope, "findIndex", origin,
                    key.primitive);
            valid &= generatedSignature(signatures, scope, "requireIndex", origin,
                    key.primitive);
            valid &= generatedSignature(signatures, scope, "find", origin,
                    key.primitive);
            valid &= generatedSignature(signatures, scope, "find", origin,
                    key.primitive, "com.hgtech.soma.runtime.MaterializationBudget");
            valid &= generatedSignature(signatures, scope, "fetch", origin,
                    key.primitive);
            valid &= generatedSignature(signatures, scope, "fetch", origin,
                    key.primitive, "com.hgtech.soma.runtime.MaterializationBudget");
            valid &= generatedSignature(signatures, scope, "mutate", origin,
                    key.primitive);
            valid &= generatedSignature(signatures, scope, "delete", origin,
                    key.primitive);
            valid &= generatedSignature(signatures, scope, "keys", origin);
            if (key.valueBacked()) {
                List<String> leafTypes = new ArrayList<String>();
                for (DenseTableCodegenModel.ValueLeafSpec leaf : key.valueLeaves) {
                    leafTypes.add(leaf.primitive);
                }
                valid &= generatedSignature(signatures, scope, "findIndex", origin,
                        leafTypes.toArray(new String[leafTypes.size()]));
                valid &= generatedSignature(signatures, scope, "requireIndex", origin,
                        leafTypes.toArray(new String[leafTypes.size()]));
            }
        }
        return valid;
    }

    private boolean generatedSignature(
            Map<String, Element> signatures,
            String scope,
            String name,
            Element origin,
            String... parameterTypes) {
        StringBuilder key = new StringBuilder(scope).append('#').append(name).append('(');
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) key.append(',');
            key.append(erasedType(parameterTypes[i]));
        }
        key.append(')');
        Element previous = signatures.put(key.toString(), origin);
        if (previous == null) return true;
        error(previous, "SOMA-GEN-001", "generated method signature collision: " + key);
        if (previous != origin) {
            error(origin, "SOMA-GEN-001", "generated method signature collision: " + key);
        }
        return false;
    }

    private boolean generatedField(
            Map<String, Element> fields,
            String scope,
            String name,
            Element origin) {
        String key = scope + '#' + name;
        Element previous = fields.put(key, origin);
        if (previous == null) return true;
        error(previous, "SOMA-GEN-001", "generated storage symbol collision: " + key);
        if (previous != origin) {
            error(origin, "SOMA-GEN-001", "generated storage symbol collision: " + key);
        }
        return false;
    }

    private String erasedType(String type) {
        StringBuilder result = new StringBuilder(type.length());
        int genericDepth = 0;
        for (int i = 0; i < type.length(); i++) {
            char value = type.charAt(i);
            if (value == '<') {
                genericDepth++;
            } else if (value == '>') {
                genericDepth--;
            } else if (genericDepth == 0 && !Character.isWhitespace(value)) {
                result.append(value);
            }
        }
        return result.toString();
    }

    private List<SelectorModel> validateSelectors(
            TypeElement table, List<TableFieldModel> fields) {
        List<SelectorModel> result = new ArrayList<SelectorModel>();
        Set<String> names = new LinkedHashSet<String>();
        boolean valid = true;
        for (SomaIndex index : table.getAnnotationsByType(SomaIndex.class)) {
            SelectorModel selector = selector(table, "index", index.value(),
                    index.name(), index.fields(), fields, names);
            if (selector == null) valid = false; else result.add(selector);
        }
        for (SomaUnique unique : table.getAnnotationsByType(SomaUnique.class)) {
            SelectorModel selector = selector(table, "unique", unique.value(),
                    unique.name(), unique.fields(), fields, names);
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
        return result;
    }

    private SelectorModel selector(
            TypeElement table, String kind, String alias, String declaredName,
            String[] paths, List<TableFieldModel> fields,
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
            SelectorLeafModel leaf = resolveSelectorLeaf(fields, path);
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
            List<TableFieldModel> fields, String path) {
        for (TableFieldModel field : fields) {
            if (field.child != null) continue;
            if (field.optional) continue;
            if (field.type.valueJavaType == null) {
                if (field.logicalName.equals(path)
                        && !"java.lang.String".equals(field.type.storagePrimitiveName)) {
                    return new SelectorLeafModel(path,
                            field.type.publicType, field.type.storagePrimitiveName,
                            field.type.enumJavaType);
                }
                continue;
            }
            for (ValueLeafType leaf : field.type.valueLeaves) {
                if ((field.logicalName + "." + leaf.logicalName).equals(path)
                        && !"java.lang.String".equals(leaf.storagePrimitiveName)) {
                    return new SelectorLeafModel(path,
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

    private String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private TypeElement somaValueType(TypeMirror mirror) {
        if (mirror.getKind() != TypeKind.DECLARED) return null;
        Element element = ((DeclaredType) mirror).asElement();
        if (!(element instanceof TypeElement)) return null;
        TypeElement type = (TypeElement) element;
        return type.getAnnotation(SomaValue.class) == null ? null : type;
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

    private boolean valueBelongsToTableSchema(TypeElement table, String valueJavaType) {
        TypeElement value = processingEnv.getElementUtils().getTypeElement(valueJavaType);
        return value != null && processingEnv.getElementUtils().getPackageOf(table)
                .getQualifiedName().contentEquals(
                        processingEnv.getElementUtils().getPackageOf(value)
                                .getQualifiedName());
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
            requireDefaultLiteral(literal);
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
                    "invalid schema default for " + field.getSimpleName()
                            + "; literalLength=" + literal.length());
            return null;
        }
    }

    private DefaultModel normalizeValueDefault(
            VariableElement field,
            NormalizedType type,
            SomaSemantic semantic,
            String literal) {
        try {
            requireDefaultLiteral(literal);
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
                    "invalid value leaf default for " + field.getSimpleName()
                            + "; literalLength=" + literal.length());
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
        if (!DECIMAL_FLOATING_LITERAL.matcher(literal).matches()) {
            throw new IllegalArgumentException("invalid decimal float literal");
        }
        float value = Float.parseFloat(literal);
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("non-finite decimal float literal");
        }
        return value;
    }

    private double parseDoubleDefault(String literal) {
        if ("NaN".equals(literal)) return Double.NaN;
        if ("Infinity".equals(literal)) return Double.POSITIVE_INFINITY;
        if ("-Infinity".equals(literal)) return Double.NEGATIVE_INFINITY;
        if (!DECIMAL_FLOATING_LITERAL.matcher(literal).matches()) {
            throw new IllegalArgumentException("invalid decimal double literal");
        }
        double value = Double.parseDouble(literal);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("non-finite decimal double literal");
        }
        return value;
    }

    private static void requireDefaultLiteral(String literal) {
        if (literal == null || literal.length() > MAXIMUM_DEFAULT_LITERAL_LENGTH) {
            throw new IllegalArgumentException("default literal length exceeds limit");
        }
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
        Map<String, SchemaModel> byName = new TreeMap<String, SchemaModel>(
                UnicodeCodePointOrder.INSTANCE);
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
            validateValueGraph(schema, value, visiting, visited, 1);
        }
        if (hasErrors) return;
        Map<String, Integer> depthByValue = new LinkedHashMap<String, Integer>();
        for (ValueModel value : schema.values.values()) {
            int depth = maximumValueDepth(
                    schema, value, depthByValue, new HashSet<String>());
            if (depth > CodegenLimits.MAXIMUM_VALUE_DEPTH) {
                codegenAdmissionError(value.origin, "value-depth",
                        CodegenLimits.MAXIMUM_VALUE_DEPTH, depth, value.javaType);
            }
        }
    }

    private void validateValueGraph(
            SchemaModel schema,
            ValueModel value,
            Set<String> visiting,
            Set<String> visited,
            int depth) {
        if (depth > CodegenLimits.MAXIMUM_VALUE_DEPTH) {
            codegenAdmissionError(value.origin, "value-depth",
                    CodegenLimits.MAXIMUM_VALUE_DEPTH, depth, value.javaType);
            return;
        }
        if (visited.contains(value.javaType)) {
            return;
        }
        if (!visiting.add(value.javaType)) {
            error(value.origin, "SOMA-VALUE-007",
                    "cyclic nested value declaration: " + value.javaType);
            return;
        }
        for (FieldModel field : value.fields) {
            if (field.type.valueReference == null) {
                continue;
            }
            ValueModel referenced = schema.values.get(field.type.valueReference);
            if (referenced == null) {
                error(field.origin, "SOMA-VALUE-008",
                        "nested value must belong to the same schema compilation: "
                                + field.type.valueReference);
                continue;
            }
            validateValueGraph(schema, referenced, visiting, visited, depth + 1);
        }
        visiting.remove(value.javaType);
        visited.add(value.javaType);
    }

    private int maximumValueDepth(
            SchemaModel schema,
            ValueModel value,
            Map<String, Integer> depthByValue,
            Set<String> visiting) {
        Integer known = depthByValue.get(value.javaType);
        if (known != null) return known.intValue();
        if (!visiting.add(value.javaType)) return 1;
        int depth = 1;
        for (FieldModel field : value.fields) {
            if (field.type.valueReference == null) continue;
            ValueModel referenced = schema.values.get(field.type.valueReference);
            if (referenced != null) {
                if (visiting.size() >= CodegenLimits.MAXIMUM_VALUE_DEPTH) {
                    depth = CodegenLimits.MAXIMUM_VALUE_DEPTH + 1;
                    break;
                }
                depth = Math.max(depth, 1 + maximumValueDepth(
                        schema, referenced, depthByValue, visiting));
            }
        }
        visiting.remove(value.javaType);
        depthByValue.put(value.javaType, Integer.valueOf(depth));
        return depth;
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
                || type.getAnnotationsByType(SomaUnique.class).length != 0) {
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
                    field, field.getSimpleName().toString(), logicalName,
                    fieldAnnotation.semantic().name(), normalizedType, defaultValue));
        }

        if (fields.isEmpty()) {
            error(type, "SOMA-VALUE-001", "@SomaValue requires at least one schema field");
            valid = false;
        }
        int constructorSlots = 1;
        for (FieldModel field : fields) {
            TypeKind kind = field.origin.asType().getKind();
            constructorSlots += kind == TypeKind.LONG || kind == TypeKind.DOUBLE ? 2 : 1;
        }
        if (constructorSlots > CodegenLimits.MAXIMUM_JVM_PARAMETER_SLOTS) {
            codegenAdmissionError(type, "value-constructor-slots",
                    CodegenLimits.MAXIMUM_JVM_PARAMETER_SLOTS, constructorSlots,
                    type.getQualifiedName().toString());
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
                type, type.getQualifiedName().toString(),
                type.getSimpleName().toString(), fields) : null;
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

    private List<SchemaArtifactPlan> buildCompilationPlan(
            Map<String, SchemaModel> schemas) {
        List<SchemaArtifactPlan> result = new ArrayList<SchemaArtifactPlan>();
        Map<String, Element> generatedTypes = new TreeMap<String, Element>(
                UnicodeCodePointOrder.INSTANCE);
        Map<String, List<DenseTableCodegenModel.TableSpec>> specsBySchema =
                new TreeMap<String, List<DenseTableCodegenModel.TableSpec>>(
                        UnicodeCodePointOrder.INSTANCE);
        for (SchemaModel schema : schemas.values()) {
            if (schema.tables.size() > CodegenLimits.MAXIMUM_SCHEMA_TABLES) {
                codegenAdmissionError(schema.origin, "schema-tables",
                        CodegenLimits.MAXIMUM_SCHEMA_TABLES, schema.tables.size(),
                        schema.sourcePackage);
            }
            List<DenseTableCodegenModel.TableSpec> tableSpecs =
                    new ArrayList<DenseTableCodegenModel.TableSpec>();
            for (TableModel table : schema.tables.values()) {
                int leafCount = normalizedPhysicalLeafCount(table);
                if (leafCount > CodegenLimits.MAXIMUM_TABLE_PHYSICAL_LEAVES) {
                    codegenAdmissionError(table.origin, "table-physical-leaves",
                            CodegenLimits.MAXIMUM_TABLE_PHYSICAL_LEAVES,
                            leafCount, table.javaType);
                }
                DenseTableCodegenModel.TableSpec tableSpec = table.toGeneratorSpec();
                tableSpecs.add(tableSpec);
                for (String generatedName : generatedTypeNames(tableSpec)) {
                    String qualifiedName = schema.generatedPackage + "." + generatedName;
                    Element previous = generatedTypes.put(qualifiedName, table.origin);
                    if (previous != null) {
                        error(previous, "SOMA-GEN-001",
                                "generated top-level type collision: " + qualifiedName);
                        error(table.origin, "SOMA-GEN-001",
                                "generated top-level type collision: " + qualifiedName);
                    } else if (processingEnv.getElementUtils().getTypeElement(
                            qualifiedName) != null
                            && !isOwnedGeneratedSource(qualifiedName)) {
                        error(table.origin, "SOMA-GEN-001",
                                "generated top-level type conflicts with existing type: "
                                        + qualifiedName);
                    }
                }
            }
            specsBySchema.put(schema.sourcePackage,
                    Collections.unmodifiableList(tableSpecs));
        }
        if (hasErrors) return Collections.emptyList();

        for (SchemaModel schema : schemas.values()) {
            String json = schema.toCanonicalJson();
            String hash = sha256(SCHEMA_HASH_PREFIX + json);
            List<DenseTableCodegenModel.TableSpec> tableSpecs =
                    specsBySchema.get(schema.sourcePackage);
            DenseTableSourceGenerator generator = new DenseTableSourceGenerator(
                    schema.generatedPackage, hash, tableSpecs);
            List<GeneratedSourceOutput> sources =
                    new ArrayList<GeneratedSourceOutput>();
            long totalLength = 0L;
            for (DenseTableCodegenModel.TableSpec tableSpec : tableSpecs) {
                List<GeneratedSourceOutput> rendered;
                try {
                    rendered = generator.render(tableSpec);
                } catch (SourceLimitExceeded exceeded) {
                    codegenAdmissionError(tableSpec.origin, "generated-source-utf16",
                            CodegenLimits.MAXIMUM_GENERATED_SOURCE_LENGTH, exceeded.proposed,
                            tableSpec.carrierType);
                    return Collections.emptyList();
                }
                for (GeneratedSourceOutput source : rendered) {
                    int length = source.source.length();
                    if (length > CodegenLimits.MAXIMUM_GENERATED_SOURCE_LENGTH) {
                        codegenAdmissionError(source.origin, "generated-source-utf16",
                                CodegenLimits.MAXIMUM_GENERATED_SOURCE_LENGTH, length,
                                source.qualifiedName);
                    }
                    totalLength += length;
                    sources.add(source);
                }
                if (hasErrors) return Collections.emptyList();
            }
            if (totalLength > CodegenLimits.MAXIMUM_SCHEMA_GENERATED_SOURCE_LENGTH) {
                codegenAdmissionError(schema.origin, "schema-generated-source-utf16",
                        CodegenLimits.MAXIMUM_SCHEMA_GENERATED_SOURCE_LENGTH, totalLength,
                        schema.sourcePackage);
            }
            result.add(new SchemaArtifactPlan(
                    schema.origin,
                    "META-INF/soma/" + schema.sourcePackage,
                    json + "\n",
                    hash + "\n",
                    sources));
        }
        return Collections.unmodifiableList(result);
    }

    private boolean isOwnedGeneratedSource(String qualifiedName) {
        int separator = qualifiedName.lastIndexOf('.');
        String packageName = separator < 0 ? "" : qualifiedName.substring(0, separator);
        String simpleName = separator < 0 ? qualifiedName
                : qualifiedName.substring(separator + 1);
        InputStream input = null;
        try {
            FileObject resource = processingEnv.getFiler().getResource(
                    StandardLocation.SOURCE_OUTPUT, packageName, simpleName + ".java");
            input = resource.openInputStream();
            byte[] prefix = new byte[64];
            int length = input.read(prefix);
            return length > 0 && new String(prefix, 0, length, StandardCharsets.UTF_8)
                    .startsWith("// SOMA-GENERATED: soma-processor-v1\n");
        } catch (IOException missingOrUnreadable) {
            return false;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                    // A failed close does not turn an untrusted source into an owned artifact.
                }
            }
        }
    }

    private List<String> generatedTypeNames(
            DenseTableCodegenModel.TableSpec table) {
        List<String> result = new ArrayList<String>();
        result.add(table.name("Cursor"));
        result.add(table.name("UpdateCursor"));
        result.add(table.name("Batch"));
        result.add(table.name("Mutator"));
        result.add(table.name("Scan"));
        if (table.keyed()) result.add(table.name("KeyTraversal"));
        result.add(table.name("Table"));
        return result;
    }

    private int normalizedPhysicalLeafCount(TableModel table) {
        int count = 0;
        for (TableFieldModel field : table.fields) {
            if (field.type != null && field.type.valueJavaType != null) {
                count += field.type.valueLeaves.size();
            } else if (field.child == null) {
                count++;
            }
        }
        return count;
    }

    private void codegenAdmissionError(
            Element origin,
            String dimension,
            long limit,
            long proposed,
            String symbol) {
        error(origin, "SOMA-GEN-003",
                "codegen admission exceeded: dimension=" + dimension
                        + " limit=" + limit
                        + " proposed=" + proposed
                        + " symbol=" + symbol);
    }

    private void emitCompilationPlan(List<SchemaArtifactPlan> plans) {
        for (SchemaArtifactPlan plan : plans) {
            try {
                writeResource(plan.basePath + ".schema.json",
                        plan.schemaJson, plan.origin);
                writeResource(plan.basePath + ".schema.sha256",
                        plan.schemaHash, plan.origin);
            } catch (IOException exception) {
                error(plan.origin, "SOMA-OUTPUT-001",
                        "failed to write deterministic schema artifacts: "
                                + exception.getClass().getSimpleName());
                return;
            }
        }
        for (SchemaArtifactPlan plan : plans) {
            for (GeneratedSourceOutput source : plan.sources) {
                try {
                    writeSource(source);
                } catch (IOException exception) {
                    error(source.origin, "SOMA-GEN-002",
                            "failed to emit deterministic generated source: "
                                    + source.qualifiedName + "; cause="
                                    + exception.getClass().getSimpleName());
                    return;
                }
            }
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

    private void writeSource(GeneratedSourceOutput source)
            throws IOException {
        JavaFileObject file = processingEnv.getFiler().createSourceFile(
                source.qualifiedName, source.origin);
        Writer writer = new OutputStreamWriter(
                file.openOutputStream(), StandardCharsets.UTF_8);
        try {
            writer.write(source.source);
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
                    if (Character.isSurrogate(character)) {
                        result.append("\\u");
                        result.append(UPPER_HEX[(character >>> 12) & 0x0f]);
                        result.append(UPPER_HEX[(character >>> 8) & 0x0f]);
                        result.append(UPPER_HEX[(character >>> 4) & 0x0f]);
                        result.append(UPPER_HEX[character & 0x0f]);
                    } else if (character < 0x20) {
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

    private static final class SchemaArtifactPlan {
        private final PackageElement origin;
        private final String basePath;
        private final String schemaJson;
        private final String schemaHash;
        private final List<GeneratedSourceOutput> sources;

        private SchemaArtifactPlan(
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

    private static final class SchemaModel {
        private final PackageElement origin;
        private final String sourcePackage;
        private final String name;
        private final String generatedPackage;
        private final String version;
        private final Map<String, EnumModel> enums = new TreeMap<String, EnumModel>(
                UnicodeCodePointOrder.INSTANCE);
        private final Map<String, ValueModel> values = new TreeMap<String, ValueModel>(
                UnicodeCodePointOrder.INSTANCE);
        private final Map<String, TableModel> tables = new TreeMap<String, TableModel>(
                UnicodeCodePointOrder.INSTANCE);

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
        private final TypeElement origin;
        private final String javaType;
        private final String logicalName;
        private final List<FieldModel> fields;

        private ValueModel(
                TypeElement origin,
                String javaType,
                String logicalName,
                List<FieldModel> fields) {
            this.origin = origin;
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
        private final VariableElement origin;
        private final String javaName;
        private final String logicalName;
        private final String semantic;
        private final NormalizedType type;
        private final DefaultModel defaultValue;

        private FieldModel(
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

        private DenseTableCodegenModel.TableSpec toGeneratorSpec() {
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

        private DenseTableCodegenModel.SelectorSpec toGeneratorSpec() {
            List<DenseTableCodegenModel.SelectorLeafSpec> result =
                    new ArrayList<DenseTableCodegenModel.SelectorLeafSpec>();
            for (SelectorLeafModel leaf : leaves) {
                result.add(new DenseTableCodegenModel.SelectorLeafSpec(
                        leaf.path, "ASC", leaf.publicType,
                        leaf.storageType, leaf.enumType));
            }
            return new DenseTableCodegenModel.SelectorSpec(kind, name, result);
        }

        private String generatedSuffix() {
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

        private String generatedMethodName() {
            return "scanBy" + generatedSuffix();
        }
    }

    private static final class SelectorLeafModel {
        private final String path;
        private final String publicType;
        private final String storageType;
        private final String enumType;

        private SelectorLeafModel(
                String path, String publicType,
                String storageType, String enumType) {
            this.path = path;
            this.publicType = publicType;
            this.storageType = storageType;
            this.enumType = enumType;
        }

        private void appendJson(StringBuilder json) {
            json.append('{');
            json.append("\"path\":").append(quote(path)).append(',');
            json.append("\"storageType\":").append(quote(storageType));
            json.append('}');
        }
    }

    private static final class TableFieldModel {
        private final VariableElement origin;
        private final String javaName;
        private final String logicalName;
        private final String semantic;
        private final TableFieldType type;
        private final ChildFieldType child;
        private final boolean optional;
        private final boolean key;
        private DefaultModel defaultValue;

        private TableFieldModel(VariableElement origin,
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

        private DenseTableCodegenModel.FieldSpec toGeneratorSpec() {
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

        private DenseTableCodegenModel.ChildSpec toGeneratorChildSpec() {
            return new DenseTableCodegenModel.ChildSpec(
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
