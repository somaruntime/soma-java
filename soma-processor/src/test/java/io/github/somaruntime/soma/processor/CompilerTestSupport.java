package io.github.somaruntime.soma.processor;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

final class CompilerTestSupport {

    private CompilerTestSupport() {
    }

    static Compilation compile(
            Map<String, String> sources,
            boolean fullSourceSet,
            Processor... processors) throws IOException {
        Path root = Files.createTempDirectory("soma-compiler-test-");
        Path sourceRoot = Files.createDirectories(root.resolve("src"));
        Path classRoot = Files.createDirectories(root.resolve("classes"));
        Path generatedRoot = Files.createDirectories(root.resolve("generated"));
        List<File> files = new ArrayList<File>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path path = sourceRoot.resolve(source.getKey());
            Files.createDirectories(path.getParent());
            Files.write(path, source.getValue().getBytes(StandardCharsets.UTF_8));
            files.add(path.toFile());
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            deleteRecursively(root);
            throw new IllegalStateException("Tests require a full JDK");
        }
        DiagnosticCollector<JavaFileObject> diagnostics =
                new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8);
        try {
            Iterable<? extends JavaFileObject> units =
                    fileManager.getJavaFileObjectsFromFiles(files);
            List<String> options = new ArrayList<String>(Arrays.asList(
                    "-source", "8",
                    "-target", "8",
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classRoot.toString(),
                    "-s", generatedRoot.toString()));
            if (fullSourceSet) {
                options.add("-Asoma.fullSourceSet=true");
            }
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, options, null, units);
            task.setProcessors(processors.length == 0
                    ? Collections.<Processor>singletonList(new SomaProcessor())
                    : Arrays.asList(processors));
            boolean success = Boolean.TRUE.equals(task.call());
            List<String> messages = new ArrayList<String>();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                String sourceName = diagnostic.getSource() == null
                        ? "<no-source>"
                        : diagnostic.getSource().getName();
                messages.add(diagnostic.getKind() + ":" + sourceName + ":"
                        + diagnostic.getLineNumber() + ":" + diagnostic.getMessage(null));
            }
            return new Compilation(root, classRoot, generatedRoot, success, messages);
        } finally {
            fileManager.close();
        }
    }

    static final class Compilation implements AutoCloseable {

        private final Path root;
        private final Path classRoot;
        private final Path generatedRoot;
        private final boolean success;
        private final List<String> diagnostics;
        private URLClassLoader classLoader;

        private Compilation(
                Path root,
                Path classRoot,
                Path generatedRoot,
                boolean success,
                List<String> diagnostics) {
            this.root = root;
            this.classRoot = classRoot;
            this.generatedRoot = generatedRoot;
            this.success = success;
            this.diagnostics = Collections.unmodifiableList(
                    new ArrayList<String>(diagnostics));
        }

        boolean success() {
            return success;
        }

        List<String> diagnostics() {
            return diagnostics;
        }

        String generatedSource(String relativePath) throws IOException {
            return new String(
                    Files.readAllBytes(generatedRoot.resolve(relativePath)),
                    StandardCharsets.UTF_8);
        }

        boolean generatedSourceExists(String relativePath) {
            return Files.exists(generatedRoot.resolve(relativePath));
        }

        String classOutput(String relativePath) throws IOException {
            return new String(
                    Files.readAllBytes(classRoot.resolve(relativePath)),
                    StandardCharsets.UTF_8);
        }

        boolean classOutputExists(String relativePath) {
            return Files.exists(classRoot.resolve(relativePath));
        }

        List<byte[]> classFilesUnder(String relativeDirectory) throws IOException {
            Path directory = classRoot.resolve(relativeDirectory);
            final List<byte[]> result = new ArrayList<byte[]>();
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    if (file.getFileName().toString().endsWith(".class")) {
                        result.add(Files.readAllBytes(file));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return result;
        }

        long generatedSourceBytes() throws IOException {
            final long[] result = {0L};
            Files.walkFileTree(generatedRoot, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().endsWith(".java")) {
                        result[0] += attrs.size();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return result[0];
        }

        synchronized Class<?> loadClass(String qualifiedName)
                throws IOException, ClassNotFoundException {
            if (!success) {
                throw new IllegalStateException("cannot load a failed compilation");
            }
            if (classLoader == null) {
                classLoader = new URLClassLoader(
                        new URL[] {classRoot.toUri().toURL()},
                        CompilerTestSupport.class.getClassLoader());
            }
            return classLoader.loadClass(qualifiedName);
        }

        @Override
        public void close() throws IOException {
            if (classLoader != null) {
                classLoader.close();
            }
            deleteRecursively(root);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception)
                    throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
