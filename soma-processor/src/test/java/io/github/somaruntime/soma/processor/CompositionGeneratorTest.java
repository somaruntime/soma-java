package io.github.somaruntime.soma.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import org.junit.jupiter.api.Test;

class CompositionGeneratorTest {

    @Test
    void allSourcesAreWrittenBeforeAnyManifest() throws Exception {
        RecordingFiler filer = new RecordingFiler(0, 0);
        CompositionGenerator generator = new CompositionGenerator(filer);
        generator.generateAll(generator.renderAll(Arrays.asList(
                model("example.alpha"),
                model("example.beta"))));

        assertEquals(Arrays.asList(
                "source:example.alpha.SomaCompositionLinkage",
                "source:example.alpha.Soma",
                "source:example.alpha.SomaGroup",
                "source:example.alpha.Entity",
                "source:example.alpha.EntityTable",
                "source:example.beta.SomaCompositionLinkage",
                "source:example.beta.Soma",
                "source:example.beta.SomaGroup",
                "source:example.beta.Entity",
                "source:example.beta.EntityTable",
                "resource:META-INF/soma/example.alpha.schema.properties",
                "resource:META-INF/soma/example.beta.schema.properties"),
                filer.events);
    }

    @Test
    void sourceFailureCannotStartManifestPublication() {
        RecordingFiler filer = new RecordingFiler(2, 0);
        CompositionGenerator generator = new CompositionGenerator(filer);

        assertThrows(IOException.class, () -> generator.generateAll(
                generator.renderAll(Arrays.asList(
                        model("example.alpha"),
                        model("example.beta")))));
        assertFalse(filer.events.stream().anyMatch(event -> event.startsWith("resource:")));
    }

    @Test
    void anyPublicSourceFailureCannotStartManifestPublication() {
        for (int failedSource = 1; failedSource <= 5; failedSource++) {
            RecordingFiler filer = new RecordingFiler(failedSource, 0);
            CompositionGenerator generator = new CompositionGenerator(filer);

            assertThrows(IOException.class, () -> generator.generateAll(
                    generator.renderAll(Collections.singletonList(
                            model("example.failure")))));
            assertFalse(filer.events.stream()
                    .anyMatch(event -> event.startsWith("resource:")));
        }
    }

    @Test
    void manifestFailurePropagatesToTheCompilationBoundary() {
        RecordingFiler filer = new RecordingFiler(0, 1);
        CompositionGenerator generator = new CompositionGenerator(filer);

        assertThrows(IOException.class, () -> generator.generateAll(
                generator.renderAll(Collections.singletonList(model("example.alpha")))));
        assertEquals(Arrays.asList(
                "source:example.alpha.SomaCompositionLinkage",
                "source:example.alpha.Soma",
                "source:example.alpha.SomaGroup",
                "source:example.alpha.Entity",
                "source:example.alpha.EntityTable",
                "resource:META-INF/soma/example.alpha.schema.properties"),
                filer.events);
    }

    private static CompositionModel model(String generatedPackage) {
        CompositionModel.TypeModel longType = new CompositionModel.TypeModel(
                CompositionModel.LogicalKind.LONG,
                "long",
                "java.lang.Long",
                Collections.singletonList(new CompositionModel.LeafModel(
                        CompositionModel.LeafKind.LONG,
                        CompositionModel.EqualityKind.LONG,
                        "long")),
                null,
                true,
                true,
                true,
                false);
        CompositionModel.TableModel table = new CompositionModel.TableModel(
                "Entity",
                4L,
                Arrays.asList(
                        new CompositionModel.FieldModel(
                                "id", longType, CompositionModel.FieldRole.KEY, 0),
                        new CompositionModel.FieldModel(
                                "value", longType, CompositionModel.FieldRole.FIELD, 1)));
        return new CompositionModel(
                generatedPackage + ".schema",
                generatedPackage,
                "1.0.0-SNAPSHOT",
                "1",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                Collections.singletonList("schema=" + generatedPackage + ".schema"),
                Collections.<CompositionModel.ValueModel>emptyList(),
                Collections.singletonList(table),
                Collections.<Element>emptyList());
    }

    private static final class RecordingFiler implements Filer {

        private final int failOnSource;
        private final int failOnResource;
        private final List<String> events = new ArrayList<String>();
        private int sources;
        private int resources;

        private RecordingFiler(int failOnSource, int failOnResource) {
            this.failOnSource = failOnSource;
            this.failOnResource = failOnResource;
        }

        @Override
        public JavaFileObject createSourceFile(
                CharSequence name,
                Element... originatingElements) throws IOException {
            sources++;
            events.add("source:" + name);
            if (sources == failOnSource) {
                throw new IOException("injected source failure");
            }
            return memoryFile("/" + name.toString().replace('.', '/') + ".java");
        }

        @Override
        public JavaFileObject createClassFile(
                CharSequence name,
                Element... originatingElements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileObject createResource(
                javax.tools.JavaFileManager.Location location,
                CharSequence packageName,
                CharSequence relativeName,
                Element... originatingElements) throws IOException {
            resources++;
            events.add("resource:" + relativeName);
            if (resources == failOnResource) {
                throw new IOException("injected manifest failure");
            }
            return memoryFile("/" + relativeName);
        }

        @Override
        public FileObject getResource(
                javax.tools.JavaFileManager.Location location,
                CharSequence packageName,
                CharSequence relativeName) {
            throw new UnsupportedOperationException();
        }

        private static JavaFileObject memoryFile(String path) {
            return new SimpleJavaFileObject(
                    URI.create("mem://" + path), JavaFileObject.Kind.OTHER) {
                @Override
                public Writer openWriter() {
                    return new StringWriter();
                }

                @Override
                public OutputStream openOutputStream() {
                    return new ByteArrayOutputStream();
                }
            };
        }
    }
}
