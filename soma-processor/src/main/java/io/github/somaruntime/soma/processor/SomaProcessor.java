package io.github.somaruntime.soma.processor;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaSchema;
import io.github.somaruntime.soma.SomaTable;
import io.github.somaruntime.soma.SomaValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/** Aggregating JSR 269 processor for complete SOMA schema compositions. */
@SupportedAnnotationTypes({
        "io.github.somaruntime.soma.SomaSchema",
        "io.github.somaruntime.soma.SomaTable",
        "io.github.somaruntime.soma.SomaValue",
        "io.github.somaruntime.soma.SomaField",
        "io.github.somaruntime.soma.SomaKey",
        "io.github.somaruntime.soma.SomaIndex"
})
@SupportedOptions(SomaProcessor.FULL_SOURCE_SET_OPTION)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public final class SomaProcessor extends AbstractProcessor {

    static final String FULL_SOURCE_SET_OPTION = "soma.fullSourceSet";

    private final Map<String, PackageElement> schemaPackages =
            new LinkedHashMap<String, PackageElement>();
    private final Map<String, TypeElement> tables =
            new LinkedHashMap<String, TypeElement>();
    private boolean failed;
    private boolean generated;
    private SourceShapeInspector sourceShapeInspector;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        sourceShapeInspector = SourceShapeInspector.create(processingEnvironment);
    }

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnvironment) {
        collect(roundEnvironment);
        failed |= roundEnvironment.errorRaised();

        if (!roundEnvironment.processingOver() || generated) {
            return false;
        }
        generated = true;

        if (schemaPackages.isEmpty() && tables.isEmpty()) {
            return false;
        }
        if (!"true".equals(processingEnv.getOptions().get(FULL_SOURCE_SET_OPTION))) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "[SOMA-1001] Full source-set handshake is required: "
                            + "-Asoma.fullSourceSet=true.");
            return false;
        }
        if (failed) {
            return false;
        }

        List<CompositionModel> models = buildModels();
        if (models == null) {
            return false;
        }
        if (!GeneratedSymbolTable.validateAcrossCompositions(
                models, processingEnv.getMessager())) {
            return false;
        }

        CompositionGenerator generator = new CompositionGenerator(processingEnv.getFiler());
        try {
            List<CompositionGenerator.RenderedComposition> rendered =
                    generator.renderAll(models);
            generator.generateAll(rendered);
        } catch (IOException exception) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "[SOMA-1091] Composition generation failed; no valid manifest was published.");
        }
        return false;
    }

    private void collect(RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(SomaSchema.class)) {
            if (element instanceof PackageElement) {
                PackageElement packageElement = (PackageElement) element;
                schemaPackages.put(packageElement.getQualifiedName().toString(), packageElement);
            } else {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "[SOMA-1011] @SomaSchema is only valid on a package.",
                        element);
                failed = true;
            }
        }
        collectTypes(roundEnvironment.getElementsAnnotatedWith(SomaTable.class), tables);
        for (Element element : roundEnvironment.getElementsAnnotatedWith(SomaValue.class)) {
            if (element instanceof TypeElement) {
                sourceShapeInspector.capture((TypeElement) element);
            }
        }
        validateFieldRoleOwners(
                roundEnvironment.getElementsAnnotatedWith(SomaField.class), "@SomaField", true);
        validateFieldRoleOwners(
                roundEnvironment.getElementsAnnotatedWith(SomaKey.class), "@SomaKey", false);
        validateFieldRoleOwners(
                roundEnvironment.getElementsAnnotatedWith(SomaIndex.class), "@SomaIndex", false);
    }

    private void validateFieldRoleOwners(
            Set<? extends Element> elements,
            String annotationName,
            boolean valueOwnerAllowed) {
        List<Element> ordered = new ArrayList<Element>(elements);
        Collections.sort(ordered, new Comparator<Element>() {
            @Override
            public int compare(Element left, Element right) {
                return stableElementName(left).compareTo(stableElementName(right));
            }
        });
        for (Element field : ordered) {
            Element owner = field.getEnclosingElement();
            boolean tableOwner = owner instanceof TypeElement
                    && ((TypeElement) owner).getAnnotation(SomaTable.class) != null;
            boolean valueOwner = valueOwnerAllowed
                    && owner instanceof TypeElement
                    && ((TypeElement) owner).getAnnotation(SomaValue.class) != null;
            if (!tableOwner && !valueOwner) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "[SOMA-1013] " + annotationName
                                + " is only valid on a direct "
                                + (valueOwnerAllowed
                                        ? "@SomaTable/@SomaValue" : "@SomaTable")
                                + " field.",
                        field);
                failed = true;
            }
        }
    }

    private static String stableElementName(Element element) {
        Element owner = element.getEnclosingElement();
        return (owner == null ? "" : owner.toString())
                + "#" + element.getSimpleName();
    }

    private void collectTypes(
            Set<? extends Element> elements,
            Map<String, TypeElement> destination) {
        for (Element element : elements) {
            if (element instanceof TypeElement) {
                TypeElement type = (TypeElement) element;
                sourceShapeInspector.capture(type);
                destination.put(type.getQualifiedName().toString(), type);
            }
        }
    }

    private List<CompositionModel> buildModels() {
        List<String> packageNames = new ArrayList<String>(schemaPackages.keySet());
        Collections.sort(packageNames);
        Map<String, List<TypeElement>> tablesByPackage = groupByPackage(tables);

        boolean valid = true;
        for (String packageName : tablesByPackage.keySet()) {
            if (!schemaPackages.containsKey(packageName)) {
                TypeElement table = tablesByPackage.get(packageName).get(0);
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "[SOMA-1012] @SomaTable must be in an @SomaSchema package.",
                        table);
                valid = false;
            }
        }
        if (!valid) {
            return null;
        }

        CompositionModelBuilder builder = new CompositionModelBuilder(
                processingEnv.getElementUtils(),
                processingEnv.getTypeUtils(),
                processingEnv.getMessager(),
                sourceShapeInspector);
        List<CompositionModel> models = new ArrayList<CompositionModel>();
        for (String packageName : packageNames) {
            List<TypeElement> packageTables = tablesByPackage.get(packageName);
            CompositionModel model = builder.build(
                    schemaPackages.get(packageName),
                    packageTables == null
                            ? Collections.<TypeElement>emptyList()
                            : packageTables);
            if (model == null) {
                valid = false;
            } else {
                models.add(model);
            }
        }
        return valid ? models : null;
    }

    private Map<String, List<TypeElement>> groupByPackage(
            Map<String, TypeElement> source) {
        Map<String, List<TypeElement>> result =
                new LinkedHashMap<String, List<TypeElement>>();
        for (TypeElement type : source.values()) {
            String packageName = processingEnv.getElementUtils()
                    .getPackageOf(type).getQualifiedName().toString();
            List<TypeElement> packageTypes = result.get(packageName);
            if (packageTypes == null) {
                packageTypes = new ArrayList<TypeElement>();
                result.put(packageName, packageTypes);
            }
            packageTypes.add(type);
        }
        return result;
    }
}
