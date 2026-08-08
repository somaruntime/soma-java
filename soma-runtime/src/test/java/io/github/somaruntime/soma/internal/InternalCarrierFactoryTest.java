package io.github.somaruntime.soma.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.UpdateResult;
import org.junit.jupiter.api.Test;

class InternalCarrierFactoryTest {

    @Test
    void internalFactoriesObserveAndCreateValidatedCarriers() {
        SomaSharedSecrets.ConfigurationAccess access =
                SomaSharedSecrets.configurationAccess();
        SomaConfiguration automatic = SomaConfiguration.builder().build();
        assertFalse(access.hasMemoryBudget(automatic));

        SomaConfiguration explicit = SomaConfiguration.builder()
                .memoryBudgetBytes(4096L)
                .build();
        assertTrue(access.hasMemoryBudget(explicit));
        assertEquals(4096L, access.memoryBudgetBytes(explicit));

        UpdateResult result = SomaSharedSecrets.updateResultAccess().create(3L, 2L);
        assertEquals(3L, result.matched());
        assertEquals(2L, result.changed());
        assertThrows(AssertionError.class,
                () -> SomaSharedSecrets.updateResultAccess().create(1L, 2L));

        RemoveResult removed = SomaSharedSecrets.removeResultAccess().create(3L);
        assertEquals(3L, removed.removed());
        assertThrows(AssertionError.class,
                () -> SomaSharedSecrets.removeResultAccess().create(-1L));
    }
}
