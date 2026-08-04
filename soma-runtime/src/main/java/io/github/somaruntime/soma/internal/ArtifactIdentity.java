package io.github.somaruntime.soma.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class ArtifactIdentity {

    private ArtifactIdentity() {
    }

    static Properties loadProperties(Class<?> owner, String resourcePath) {
        Path location = codeSource(owner);
        try {
            if (Files.isDirectory(location)) {
                Path resource = location.resolve(resourcePath);
                try (InputStream input = Files.newInputStream(resource)) {
                    return load(input);
                }
            }
            try (JarFile jar = new JarFile(location.toFile())) {
                JarEntry entry = jar.getJarEntry(resourcePath);
                if (entry == null) {
                    throw new IllegalStateException(
                            "[SOMA-0002] Artifact build metadata is missing.");
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    return load(input);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "[SOMA-0002] Artifact build metadata cannot be read.", exception);
        }
    }

    static String sha256(Class<?> owner) {
        Path location = codeSource(owner);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (Files.isDirectory(location)) {
                updateDirectoryDigest(digest, location);
            } else {
                try (InputStream input = Files.newInputStream(location)) {
                    updateDigest(digest, input);
                }
            }
            return hex(digest.digest());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "[SOMA-0002] Artifact identity cannot be read.", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required by Java 8", exception);
        }
    }

    private static Properties load(InputStream input) throws IOException {
        Properties properties = new Properties();
        properties.load(input);
        return properties;
    }

    private static Path codeSource(Class<?> owner) {
        CodeSource source = owner.getProtectionDomain().getCodeSource();
        URL location = source == null ? null : source.getLocation();
        if (location == null || !"file".equalsIgnoreCase(location.getProtocol())) {
            throw new IllegalStateException(
                    "[SOMA-0002] Artifact code source is unavailable.");
        }
        try {
            return Paths.get(location.toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(
                    "[SOMA-0002] Artifact code source is invalid.", exception);
        }
    }

    private static void updateDirectoryDigest(
            final MessageDigest digest,
            final Path root) throws IOException {
        final List<Path> files = new ArrayList<Path>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile()) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(files, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                return relativeName(root, left).compareTo(relativeName(root, right));
            }
        });
        for (Path file : files) {
            digest.update(relativeName(root, file).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (InputStream input = Files.newInputStream(file)) {
                updateDigest(digest, input);
            }
            digest.update((byte) 0);
        }
    }

    private static String relativeName(Path root, Path file) {
        return root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
    }

    private static void updateDigest(MessageDigest digest, InputStream input) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            int unsigned = item & 0xff;
            if (unsigned < 0x10) {
                result.append('0');
            }
            result.append(Integer.toHexString(unsigned));
        }
        return result.toString();
    }
}
