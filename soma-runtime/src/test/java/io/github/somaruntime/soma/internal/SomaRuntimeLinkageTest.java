package io.github.somaruntime.soma.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SomaRuntimeLinkageTest {

    @Test
    void exactVersionIsAcceptedWithoutFreezingConfiguration() {
        boolean frozenBefore =
                SomaRuntimeLinkage.configurationFrozenForInternalObservation();

        assertDoesNotThrow(() -> SomaRuntimeLinkage.requireCompatible(
                SomaRuntimeLinkage.runtimeArtifactVersion(),
                SomaRuntimeLinkage.runtimeContractVersion(),
                SomaRuntimeLinkage.runtimeBuildIdentity()));

        assertTrue(frozenBefore
                == SomaRuntimeLinkage.configurationFrozenForInternalObservation());
    }

    @Test
    void mismatchedProcessorVersionFailsClosed() {
        LinkageError error = assertThrows(LinkageError.class,
                () -> SomaRuntimeLinkage.requireCompatible(
                        "different-version",
                        SomaRuntimeLinkage.runtimeContractVersion(),
                        SomaRuntimeLinkage.runtimeBuildIdentity()));
        assertTrue(error.getMessage().startsWith("[SOMA-0001]"));
        assertTrue(error.getMessage().contains("Full regeneration is required"));
    }

    @Test
    void sameVersionWithDifferentRuntimeBuildIdentityFailsClosed() {
        LinkageError error = assertThrows(LinkageError.class,
                () -> SomaRuntimeLinkage.requireCompatible(
                        SomaRuntimeLinkage.runtimeArtifactVersion(),
                        SomaRuntimeLinkage.runtimeContractVersion(),
                        "0000000000000000000000000000000000000000000000000000000000000000"));
        assertTrue(error.getMessage().startsWith("[SOMA-0001]"));
    }

    @Test
    void classpathResourceCannotForgeDefiningArtifactMetadata() {
        assertFalse("forged-test-resource".equals(
                SomaRuntimeLinkage.runtimeArtifactVersion()));
        assertTrue(SomaRuntimeLinkage.runtimeBuildIdentity().matches("[0-9a-f]{64}"));
    }
}
