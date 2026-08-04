package io.github.somaruntime.soma.internal;

import java.util.Properties;

final class RuntimeBuildInfo {

    private static final String RESOURCE = "META-INF/soma/linkage.properties";
    private static final Properties PROPERTIES =
            ArtifactIdentity.loadProperties(RuntimeBuildInfo.class, RESOURCE);
    private static final String BUILD_IDENTITY =
            ArtifactIdentity.sha256(SomaRuntimeLinkage.class);

    private RuntimeBuildInfo() {
    }

    static String artifactVersion() {
        return required("artifact.version");
    }

    static String contractVersion() {
        return required("contract.version");
    }

    static String buildIdentity() {
        return BUILD_IDENTITY;
    }

    private static String required(String key) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new ExceptionInInitializerError(
                    "[SOMA-0002] Runtime build metadata is incomplete.");
        }
        return value;
    }

}
