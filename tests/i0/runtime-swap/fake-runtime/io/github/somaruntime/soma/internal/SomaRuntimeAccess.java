package io.github.somaruntime.soma.internal;

/** Test-only：模拟较旧且不兼容的 SOMA runtime artifact。 */
public final class SomaRuntimeAccess {
    private static final String ARTIFACT_VERSION = "0.0.0-runtime-swap";
    private static final String CONTRACT_VERSION = "1";

    private SomaRuntimeAccess() {
    }

    public static void verifyGeneratedArtifact(
            String expectedArtifactVersion,
            String expectedContractVersion,
            String schemaFingerprint) {
        if (!ARTIFACT_VERSION.equals(expectedArtifactVersion)
                || !CONTRACT_VERSION.equals(expectedContractVersion)) {
            throw new IllegalStateException(
                    "[SOMA-0102] generated/runtime version mismatch");
        }
        if (schemaFingerprint == null || schemaFingerprint.length() == 0) {
            throw new IllegalStateException(
                    "[SOMA-0102] generated composition fingerprint is missing");
        }
    }
}
