package io.github.somaruntime.soma.processor;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public final class I0ProcessorHarness {
    private final Path runtimeClasses;
    private final JavaCompiler compiler;

    private I0ProcessorHarness(Path runtimeClasses) {
        this.runtimeClasses = runtimeClasses;
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new AssertionError("qualification requires a full JDK");
        }
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new AssertionError("runtime classes path is required");
        }
        I0ProcessorHarness harness = new I0ProcessorHarness(
                new File(arguments[0]).toPath().toAbsolutePath().normalize());
        harness.positiveAndDeterministic();
        harness.coexistsWithUnrelatedProcessor();
        harness.missingHandshake();
        harness.emptyComposition();
        harness.emptyTable();
        harness.explicitSchemaConstructor();
        harness.invalidFieldRole();
        harness.negativeCapacity();
        harness.reservedNamespace();
        harness.generatedFqnCollision();
        harness.versionMismatch();
        harness.lateRound();
        System.out.println("I0 processor harness: PASS");
    }

    private void positiveAndDeterministic() throws Exception {
        List<Source> sources = validSources();
        Compilation first = compile("positive-forward", sources, true,
                Collections.<AbstractProcessor>singletonList(new SomaProcessor()),
                runtimeClasses);
        expectSuccess(first);
        Path firstSource = first.generated.resolve(
                "com/example/scheduler/soma/internal/SomaGeneratedComposition.java");
        Path firstManifest = first.classes.resolve(
                "META-INF/soma/composition-manifest.properties");
        assertExists(firstSource);
        assertExists(firstManifest);

        List<Source> reversed = new ArrayList<Source>(sources);
        Collections.reverse(reversed);
        Compilation second = compile("positive-reversed", reversed, true,
                Collections.<AbstractProcessor>singletonList(new SomaProcessor()),
                runtimeClasses);
        expectSuccess(second);
        Path secondSource = second.generated.resolve(
                "com/example/scheduler/soma/internal/SomaGeneratedComposition.java");
        Path secondManifest = second.classes.resolve(
                "META-INF/soma/composition-manifest.properties");
        assertBytesEqual(firstSource, secondSource, "generated source depends on input order");
        assertBytesEqual(firstManifest, secondManifest, "manifest depends on input order");
        String source = read(firstSource);
        if (source.indexOf("public final class SomaGeneratedComposition") < 0
                || source.indexOf("private SomaGeneratedComposition()") < 0
                || source.indexOf(System.getProperty("user.dir")) >= 0) {
            throw new AssertionError("generated carrier shape or sanitization is invalid");
        }
    }

    private void coexistsWithUnrelatedProcessor() throws Exception {
        List<Source> sources = new ArrayList<Source>(validSources());
        sources.add(source(
                "com/example/other/Other.java",
                "package com.example.other;\n"
                + "import java.lang.annotation.*;\n"
                + "@Retention(RetentionPolicy.SOURCE) @Target(ElementType.TYPE)\n"
                + "public @interface Other {}\n"));
        sources.add(source(
                "com/example/other/OtherUse.java",
                "package com.example.other;\n"
                + "@Other final class OtherUse {}\n"));
        Compilation compilation = compile(
                "processor-coexistence",
                sources,
                true,
                Arrays.<AbstractProcessor>asList(
                        new SomaProcessor(), new UnrelatedProcessor()),
                runtimeClasses);
        expectSuccess(compilation);
        assertExists(compilation.generated.resolve(
                "com/example/other/OtherGenerated.java"));
        assertExists(compilation.generated.resolve(
                "com/example/scheduler/soma/internal/SomaGeneratedComposition.java"));
    }

    private void missingHandshake() throws Exception {
        Compilation compilation = compile("missing-handshake", validSources(), false,
                Collections.<AbstractProcessor>singletonList(new SomaProcessor()),
                runtimeClasses);
        expectFailure(compilation, "[SOMA-0001]");
        assertNoValidComposition(compilation);
    }

    private void emptyComposition() throws Exception {
        Compilation compilation = compile(
                "empty-composition",
                Arrays.asList(
                        source("com/example/empty/schema/package-info.java",
                                "@io.github.somaruntime.soma.SomaSchema\n"
                                + "package com.example.empty.schema;\n"),
                        source("com/example/empty/schema/Anchor.java",
                                "package com.example.empty.schema;\n"
                                + "final class Anchor {}\n")),
                true,
                Collections.<AbstractProcessor>singletonList(new SomaProcessor()),
                runtimeClasses);
        expectFailure(compilation, "[SOMA-0202]");
        assertNoValidComposition(compilation);
    }

    private void emptyTable() throws Exception {
        Compilation compilation = compile(
                "empty-table",
                Arrays.asList(
                        packageInfo("com.example.emptytable.schema"),
                        source("com/example/emptytable/schema/EmptyTable.java",
                                "package com.example.emptytable.schema;\n"
                                + "@io.github.somaruntime.soma.SomaTable\n"
                                + "final class EmptyTable {}\n")),
                true,
                Collections.<AbstractProcessor>singletonList(new SomaProcessor()),
                runtimeClasses);
        expectFailure(compilation, "[SOMA-0204]");
        assertNoValidComposition(compilation);
    }

    private void explicitSchemaConstructor() throws Exception {
        Compilation compilation = compile(
                "explicit-constructor",
                Arrays.asList(
                        packageInfo("com.example.constructor.schema"),
                        source("com/example/constructor/schema/ConstructorTable.java",
                                "package com.example.constructor.schema;\n"
                                + "@io.github.somaruntime.soma.SomaTable\n"
                                + "final class ConstructorTable {\n"
                                + "  ConstructorTable() {}\n"
                                + "  @io.github.somaruntime.soma.SomaField long value;\n"
                                + "}\n")),
                true,
                Collections.<AbstractProcessor>singletonList(new SomaProcessor()),
                runtimeClasses);
        expectFailure(compilation, "[SOMA-0205]");
        assertNoValidComposition(compilation);
    }

    private void invalidFieldRole() throws Exception {
        Compilation compilation = compile(
                "invalid-field-role",
                Arrays.asList(
                        packageInfo("com.example.roles.schema"),
                        source("com/example/roles/schema/RoleTable.java",
                                "package com.example.roles.schema;\n"
                                + "@io.github.somaruntime.soma.SomaTable\n"
                                + "final class RoleTable {\n"
                                + "  @io.github.somaruntime.soma.SomaField\n"
                                + "  @io.github.somaruntime.soma.SomaKey long id;\n"
                                + "}\n")),
                true,
                Collections.<AbstractProcessor>singletonList(new SomaProcessor()),
                runtimeClasses);
        expectFailure(compilation, "[SOMA-0206]");
        assertNoValidComposition(compilation);
    }

    private void negativeCapacity() throws Exception {
        Compilation compilation = compile(
                "negative-capacity",
                Arrays.asList(
                        packageInfo("com.example.capacity.schema"),
                        source("com/example/capacity/schema/CapacityTable.java",
                                "package com.example.capacity.schema;\n"
                                + "@io.github.somaruntime.soma.SomaTable(defaultCapacity=-1L)\n"
                                + "final class CapacityTable {\n"
                                + "  @io.github.somaruntime.soma.SomaField long value;\n"
                                + "}\n")),
                true,
                Collections.<AbstractProcessor>singletonList(new SomaProcessor()),
                runtimeClasses);
        expectFailure(compilation, "[SOMA-0207]");
        assertNoValidComposition(compilation);
    }

    private void reservedNamespace() throws Exception {
        Compilation compilation = compile(
                "reserved-namespace",
                Arrays.asList(
                        packageInfo("io.github.somaruntime.soma.client.schema"),
                        source("io/github/somaruntime/soma/client/schema/BadTable.java",
                                "package io.github.somaruntime.soma.client.schema;\n"
                                + "@io.github.somaruntime.soma.SomaTable\n"
                                + "final class BadTable {\n"
                                + "  @io.github.somaruntime.soma.SomaField long value;\n"
                                + "}\n")),
                true,
                Collections.<AbstractProcessor>singletonList(new SomaProcessor()),
                runtimeClasses);
        expectFailure(compilation, "[SOMA-0201]");
        assertNoValidComposition(compilation);
    }

    private void generatedFqnCollision() throws Exception {
        List<Source> sources = new ArrayList<Source>(validSources());
        sources.add(source(
                "com/example/scheduler/soma/internal/SomaGeneratedComposition.java",
                "package com.example.scheduler.soma.internal;\n"
                + "public final class SomaGeneratedComposition {}\n"));
        Compilation compilation = compile("generated-fqn-collision", sources, true,
                Collections.<AbstractProcessor>singletonList(new SomaProcessor()),
                runtimeClasses);
        expectFailure(compilation, "[SOMA-0301]");
        assertNoValidComposition(compilation);
    }

    private void versionMismatch() throws Exception {
        List<Source> sources = fakeRuntimeSources("0.0.0-mismatch");
        sources.add(packageInfo("com.example.mismatch.schema"));
        sources.add(source("com/example/mismatch/schema/MismatchTable.java",
                "package com.example.mismatch.schema;\n"
                + "@io.github.somaruntime.soma.SomaTable\n"
                + "final class MismatchTable {\n"
                + "  @io.github.somaruntime.soma.SomaField long value;\n"
                + "}\n"));
        Compilation compilation = compile("version-mismatch", sources, true,
                Collections.<AbstractProcessor>singletonList(new SomaProcessor()),
                null);
        expectFailure(compilation, "[SOMA-0102]");
        assertNoValidComposition(compilation);
    }

    private void lateRound() throws Exception {
        List<Source> sources = new ArrayList<Source>(validSources());
        sources.add(source(
                "com/example/late/Anchor.java",
                "package com.example.late; public final class Anchor {}\n"));
        Compilation compilation = compile(
                "late-round",
                sources,
                true,
                Arrays.<AbstractProcessor>asList(new SomaProcessor(), new LateTableProcessor()),
                runtimeClasses);
        expectFailure(compilation, "[SOMA-0103]");
        assertNoSuccessManifest(compilation);
    }

    private Compilation compile(
            String name,
            List<Source> sources,
            boolean fullSourceSet,
            List<AbstractProcessor> processors,
            Path classpath) throws Exception {
        Path root = Files.createTempDirectory("soma-i0-" + name + "-");
        Path sourceRoot = Files.createDirectories(root.resolve("src"));
        Path classes = Files.createDirectories(root.resolve("classes"));
        Path generated = Files.createDirectories(root.resolve("generated"));
        List<File> files = new ArrayList<File>();
        for (Source source : sources) {
            Path file = sourceRoot.resolve(source.relativePath);
            Files.createDirectories(file.getParent());
            Files.write(file, source.content.getBytes(StandardCharsets.UTF_8));
            files.add(file.toFile());
        }
        DiagnosticCollector<JavaFileObject> diagnostics =
                new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager manager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8);
        try {
            Iterable<? extends JavaFileObject> units = manager.getJavaFileObjectsFromFiles(files);
            List<String> options = new ArrayList<String>();
            options.addAll(Arrays.asList(
                    "-source", "8",
                    "-target", "8",
                    "-Xlint:all",
                    "-Werror",
                    "-d", classes.toString(),
                    "-s", generated.toString()));
            if (classpath != null) {
                options.add("-classpath");
                options.add(classpath.toString());
            }
            if (fullSourceSet) {
                options.add("-Asoma.fullSourceSet=true");
            }
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, manager, diagnostics, options, null, units);
            task.setProcessors(processors);
            boolean success = Boolean.TRUE.equals(task.call());
            return new Compilation(root, classes, generated, success, diagnostics.getDiagnostics());
        } finally {
            manager.close();
        }
    }

    private List<Source> validSources() {
        return Arrays.asList(
                packageInfo("com.example.scheduler.soma.schema"),
                source("com/example/scheduler/soma/schema/MachineId.java",
                        "package com.example.scheduler.soma.schema;\n"
                        + "@io.github.somaruntime.soma.SomaValue\n"
                        + "final class MachineId {\n"
                        + "  @io.github.somaruntime.soma.SomaField long value;\n"
                        + "}\n"),
                source("com/example/scheduler/soma/schema/TransportTime.java",
                        "package com.example.scheduler.soma.schema;\n"
                        + "@io.github.somaruntime.soma.SomaTable(defaultCapacity=4096L)\n"
                        + "final class TransportTime {\n"
                        + "  @io.github.somaruntime.soma.SomaKey long routeId;\n"
                        + "  @io.github.somaruntime.soma.SomaIndex MachineId machineId;\n"
                        + "  @io.github.somaruntime.soma.SomaField long minutes;\n"
                        + "}\n"));
    }

    private List<Source> fakeRuntimeSources(String artifactVersion) {
        List<Source> sources = new ArrayList<Source>();
        sources.add(annotationSource("SomaSchema", "ElementType.PACKAGE", ""));
        sources.add(annotationSource("SomaTable", "ElementType.TYPE",
                " long defaultCapacity() default 16L;"));
        sources.add(annotationSource("SomaValue", "ElementType.TYPE", ""));
        sources.add(annotationSource("SomaField", "ElementType.FIELD", ""));
        sources.add(annotationSource("SomaKey", "ElementType.FIELD", ""));
        sources.add(annotationSource("SomaIndex", "ElementType.FIELD", ""));
        sources.add(source(
                "io/github/somaruntime/soma/internal/SomaRuntimeContract.java",
                "package io.github.somaruntime.soma.internal;\n"
                + "public final class SomaRuntimeContract {\n"
                + " public static final String ARTIFACT_VERSION=\"" + artifactVersion + "\";\n"
                + " public static final String CONTRACT_VERSION=\"1\";\n"
                + " private SomaRuntimeContract() {}\n"
                + "}\n"));
        return sources;
    }

    private Source annotationSource(String simpleName, String target, String member) {
        return source(
                "io/github/somaruntime/soma/" + simpleName + ".java",
                "package io.github.somaruntime.soma;\n"
                + "import java.lang.annotation.*;\n"
                + "@Retention(RetentionPolicy.CLASS) @Target(" + target + ")\n"
                + "public @interface " + simpleName + " {" + member + "}\n");
    }

    private Source packageInfo(String packageName) {
        return source(packageName.replace('.', '/') + "/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\npackage " + packageName + ";\n");
    }

    private Source source(String relativePath, String content) {
        return new Source(relativePath, content);
    }

    private void expectSuccess(Compilation compilation) {
        if (!compilation.success) {
            throw new AssertionError("expected success:\n" + compilation.messages());
        }
    }

    private void expectFailure(Compilation compilation, String diagnosticCode) {
        if (compilation.success) {
            throw new AssertionError("expected failure " + diagnosticCode);
        }
        String messages = compilation.messages();
        if (messages.indexOf(diagnosticCode) < 0) {
            throw new AssertionError(
                    "missing diagnostic " + diagnosticCode + ":\n" + messages);
        }
        if (messages.indexOf(compilation.root.toString()) >= 0) {
            throw new AssertionError("diagnostic leaked an absolute path:\n" + messages);
        }
    }

    private void assertNoValidComposition(Compilation compilation) {
        assertNoSuccessManifest(compilation);
        Path carrier = compilation.generated.resolve(
                "com/example/scheduler/soma/internal/SomaGeneratedComposition.java");
        if (Files.exists(carrier)) {
            throw new AssertionError("validation failure wrote a composition carrier");
        }
    }

    private void assertNoSuccessManifest(Compilation compilation) {
        Path manifest = compilation.classes.resolve(
                "META-INF/soma/composition-manifest.properties");
        if (Files.exists(manifest)) {
            throw new AssertionError("failed compilation published a success manifest");
        }
    }

    private void assertExists(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new AssertionError("missing expected file: " + path.getFileName());
        }
    }

    private void assertBytesEqual(Path left, Path right, String message) throws IOException {
        if (!Arrays.equals(Files.readAllBytes(left), Files.readAllBytes(right))) {
            throw new AssertionError(message);
        }
    }

    private String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static final class Source {
        private final String relativePath;
        private final String content;

        private Source(String relativePath, String content) {
            this.relativePath = relativePath;
            this.content = content;
        }
    }

    private static final class Compilation {
        private final Path root;
        private final Path classes;
        private final Path generated;
        private final boolean success;
        private final List<Diagnostic<? extends JavaFileObject>> diagnostics;

        private Compilation(
                Path root,
                Path classes,
                Path generated,
                boolean success,
                List<Diagnostic<? extends JavaFileObject>> diagnostics) {
            this.root = root;
            this.classes = classes;
            this.generated = generated;
            this.success = success;
            this.diagnostics = diagnostics;
        }

        private String messages() {
            StringBuilder result = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
                result.append(diagnostic.getKind()).append(':')
                        .append(diagnostic.getMessage(Locale.ROOT)).append('\n');
            }
            return result.toString();
        }
    }

    public static final class LateTableProcessor extends AbstractProcessor {
        private boolean generated;

        public LateTableProcessor() {
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Collections.singleton("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.RELEASE_8;
        }

        @Override
        public boolean process(
                Set<? extends TypeElement> annotations,
                RoundEnvironment roundEnvironment) {
            if (!generated && !roundEnvironment.processingOver()) {
                generated = true;
                Filer filer = processingEnv.getFiler();
                try {
                    JavaFileObject file = filer.createSourceFile("com.example.late.schema.LateTable");
                    Writer writer = file.openWriter();
                    try {
                        writer.write("package com.example.late.schema;\n"
                                + "@io.github.somaruntime.soma.SomaTable\n"
                                + "final class LateTable {\n"
                                + " @io.github.somaruntime.soma.SomaField long value;\n"
                                + "}\n");
                    } finally {
                        writer.close();
                    }
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            }
            return false;
        }
    }

    public static final class UnrelatedProcessor extends AbstractProcessor {
        private boolean generated;

        public UnrelatedProcessor() {
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Collections.singleton("com.example.other.Other");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.RELEASE_8;
        }

        @Override
        public boolean process(
                Set<? extends TypeElement> annotations,
                RoundEnvironment roundEnvironment) {
            if (!generated && !roundEnvironment.processingOver()) {
                generated = true;
                try {
                    JavaFileObject file = processingEnv.getFiler().createSourceFile(
                            "com.example.other.OtherGenerated");
                    Writer writer = file.openWriter();
                    try {
                        writer.write("package com.example.other;\n"
                                + "public final class OtherGenerated {}\n");
                    } finally {
                        writer.close();
                    }
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            }
            return true;
        }
    }
}
