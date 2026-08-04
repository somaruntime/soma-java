package io.github.somaruntime.soma.processor;

import io.github.somaruntime.soma.internal.SomaRuntimeLinkage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class ProcessorBuildInfo {

    private static final String RESOURCE = "META-INF/soma/linkage.properties";
    private static final Properties PROPERTIES = load();

    private ProcessorBuildInfo() {
    }

    static String artifactVersion() {
        return required("artifact.version");
    }

    static String contractVersion() {
        return required("contract.version");
    }

    static String runtimeBuildIdentity() {
        return SomaRuntimeLinkage.runtimeBuildIdentity();
    }

    private static String required(String key) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(
                    "[SOMA-1090] Processor build metadata is incomplete.");
        }
        return value;
    }

    private static Properties load() {
        Path location = codeSource();
        try {
            if (Files.isDirectory(location)) {
                try (InputStream input = Files.newInputStream(location.resolve(RESOURCE))) {
                    return load(input);
                }
            }
            try (JarFile jar = new JarFile(location.toFile())) {
                JarEntry entry = jar.getJarEntry(RESOURCE);
                if (entry == null) {
                    throw new IllegalStateException(
                            "[SOMA-1090] Processor build metadata is missing.");
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    return load(input);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "[SOMA-1090] Processor build metadata cannot be read.", exception);
        }
    }

    private static Properties load(InputStream input) throws IOException {
        Properties properties = new Properties();
        properties.load(input);
        return properties;
    }

    private static Path codeSource() {
        CodeSource source = ProcessorBuildInfo.class.getProtectionDomain().getCodeSource();
        URL location = source == null ? null : source.getLocation();
        if (location == null || !"file".equalsIgnoreCase(location.getProtocol())) {
            throw new IllegalStateException(
                    "[SOMA-1090] Processor code source is unavailable.");
        }
        try {
            return Paths.get(location.toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(
                    "[SOMA-1090] Processor code source is invalid.", exception);
        }
    }
}
