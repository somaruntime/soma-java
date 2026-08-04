package io.github.somaruntime.soma.internal;

/**
 * Internal linkage used by generated composition carriers.
 *
 * <p>This package is intentionally outside the application compatibility contract.</p>
 */
public final class SomaRuntimeLinkage {

    private SomaRuntimeLinkage() {
    }

    /** Verifies that generated code and runtime come from the same exact build contract. */
    public static void requireCompatible(
            String generatedArtifactVersion,
            String generatedContractVersion,
            String expectedRuntimeBuildIdentity) {
        String runtimeArtifactVersion = RuntimeBuildInfo.artifactVersion();
        String runtimeContractVersion = RuntimeBuildInfo.contractVersion();
        String runtimeBuildIdentity = RuntimeBuildInfo.buildIdentity();
        if (!runtimeArtifactVersion.equals(generatedArtifactVersion)
                || !runtimeContractVersion.equals(generatedContractVersion)
                || !runtimeBuildIdentity.equals(expectedRuntimeBuildIdentity)) {
            throw new LinkageError(
                    "[SOMA-0001] Processor/runtime version mismatch: generated="
                            + generatedArtifactVersion
                            + "/"
                            + generatedContractVersion
                            + "/"
                            + expectedRuntimeBuildIdentity
                            + ", runtime="
                            + runtimeArtifactVersion
                            + "/"
                            + runtimeContractVersion
                            + "/"
                            + runtimeBuildIdentity
                            + ". Full regeneration is required.");
        }
    }

    static String runtimeArtifactVersion() {
        return RuntimeBuildInfo.artifactVersion();
    }

    static String runtimeContractVersion() {
        return RuntimeBuildInfo.contractVersion();
    }

    /** Returns the SHA-256 identity of the defining runtime artifact or class directory. */
    public static String runtimeBuildIdentity() {
        return RuntimeBuildInfo.buildIdentity();
    }

    static boolean configurationFrozenForInternalObservation() {
        return RuntimeConfigurationOwner.isFrozen();
    }
}
