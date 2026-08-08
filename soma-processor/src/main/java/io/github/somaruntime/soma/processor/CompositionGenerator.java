package io.github.somaruntime.soma.processor;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

/** Transaction-shaped composition renderer: all sources precede the success manifest. */
final class CompositionGenerator {

    private final Filer filer;

    CompositionGenerator(Filer filer) {
        this.filer = filer;
    }

    List<RenderedComposition> renderAll(List<CompositionModel> models) {
        List<RenderedComposition> rendered =
                new ArrayList<RenderedComposition>(models.size());
        for (CompositionModel model : models) {
            List<RenderedSource> sources = renderSources(model);
            rendered.add(new RenderedComposition(
                    model, sources, renderManifest(model, sources)));
        }
        return Collections.unmodifiableList(rendered);
    }

    void generateAll(List<RenderedComposition> compositions) throws IOException {
        for (RenderedComposition composition : compositions) {
            Element[] origins = origins(composition.model);
            for (RenderedSource rendered : composition.sources) {
                JavaFileObject sourceFile = filer.createSourceFile(rendered.typeName, origins);
                try (Writer writer = sourceFile.openWriter()) {
                    writer.write(rendered.source);
                }
            }
        }
        for (RenderedComposition composition : compositions) {
            CompositionModel model = composition.model;
            FileObject manifest = filer.createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    model.manifestPath(),
                    origins(model));
            try (OutputStream output = manifest.openOutputStream();
                 Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                writer.write(composition.manifest);
            }
        }
    }

    private static List<RenderedSource> renderSources(CompositionModel model) {
        List<RenderedSource> result = new ArrayList<RenderedSource>();
        result.add(source(model.generatedTypeName(), CompositionSourceRenderer.linkage(model)));
        result.add(source(model.generatedPackage() + ".Soma", CompositionSourceRenderer.soma(model)));
        result.add(source(
                model.generatedPackage() + ".SomaGroup",
                CompositionSourceRenderer.group(model)));
        for (CompositionModel.ValueModel value : model.values()) {
            result.add(source(value.publicTypeName(), CompositionSourceRenderer.value(model, value)));
        }
        for (CompositionModel.TableModel table : model.tables()) {
            result.add(source(
                    model.generatedPackage() + "." + table.simpleName(),
                    CompositionSourceRenderer.tableObject(model, table)));
            result.add(source(
                    model.generatedPackage() + "." + GeneratedNames.tableType(table.simpleName()),
                    CompositionSourceRenderer.table(model, table)));
        }
        return Collections.unmodifiableList(result);
    }

    private static RenderedSource source(String typeName, String source) {
        return new RenderedSource(typeName, source);
    }

    private static Element[] origins(CompositionModel model) {
        return model.originatingElements().toArray(
                new Element[model.originatingElements().size()]);
    }

    private static String renderManifest(
            CompositionModel model,
            List<RenderedSource> sources) {
        StringBuilder manifest = new StringBuilder(1600);
        appendProperty(manifest, "format", "1");
        appendProperty(manifest, "schema.package", model.schemaPackage());
        appendProperty(manifest, "generated.package", model.generatedPackage());
        appendProperty(manifest, "processor.version", model.processorVersion());
        appendProperty(manifest, "contract.version", model.contractVersion());
        appendProperty(manifest, "runtime.build.sha256", model.runtimeBuildIdentity());
        appendProperty(manifest, "fingerprint.sha256", model.fingerprint());
        StringBuilder generatedFiles = new StringBuilder();
        for (RenderedSource source : sources) {
            if (generatedFiles.length() != 0) generatedFiles.append(',');
            generatedFiles.append(source.typeName);
        }
        appendProperty(manifest, "generated.files", generatedFiles.toString());
        appendProperty(manifest, "schema.line.count",
                Integer.toString(model.schemaLines().size()));
        for (int index = 0; index < model.schemaLines().size(); index++) {
            appendProperty(
                    manifest,
                    "schema.line." + index,
                    model.schemaLines().get(index));
        }
        return manifest.toString();
    }

    private static void appendProperty(StringBuilder target, String key, String value) {
        target.append(key).append('=');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\': target.append("\\\\"); break;
                case '\t': target.append("\\t"); break;
                case '\n': target.append("\\n"); break;
                case '\r': target.append("\\r"); break;
                case '\f': target.append("\\f"); break;
                default:
                    if (character < 0x20 || character > 0x7e) {
                        target.append("\\u");
                        String hex = Integer.toHexString(character);
                        for (int padding = hex.length(); padding < 4; padding++) target.append('0');
                        target.append(hex);
                    } else {
                        target.append(character);
                    }
                    break;
            }
        }
        target.append('\n');
    }

    static final class RenderedComposition {
        private final CompositionModel model;
        private final List<RenderedSource> sources;
        private final String manifest;

        private RenderedComposition(
                CompositionModel model,
                List<RenderedSource> sources,
                String manifest) {
            this.model = model;
            this.sources = sources;
            this.manifest = manifest;
        }
    }

    private static final class RenderedSource {
        private final String typeName;
        private final String source;

        private RenderedSource(String typeName, String source) {
            this.typeName = typeName;
            this.source = source;
        }
    }
}
