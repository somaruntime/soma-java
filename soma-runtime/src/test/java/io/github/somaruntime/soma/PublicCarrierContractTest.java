package io.github.somaruntime.soma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;

class PublicCarrierContractTest {

    @Test
    void configurationAndResultCarriersHaveNoApplicationConstructor() {
        assertAllConstructorsPrivate(SomaConfiguration.class);
        assertAllConstructorsPrivate(SomaConfiguration.Builder.class);
        assertAllConstructorsPrivate(UpdateResult.class);
        assertAllConstructorsPrivate(RemoveResult.class);
        assertAllConstructorsPrivate(SomaOperationException.class);

        SomaConfiguration automatic = SomaConfiguration.builder().build();
        assertNotNull(automatic);

        SomaConfiguration explicit = SomaConfiguration.builder()
                .memoryBudgetBytes(4096L)
                .build();
        assertNotNull(explicit);
        assertThrows(IllegalArgumentException.class,
                () -> SomaConfiguration.builder().memoryBudgetBytes(0L));
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            assertNotNull(SomaConfiguration.builder()
                    .parallelExecutor(pool)
                    .build());
        } finally {
            pool.shutdownNow();
        }
        assertThrows(IllegalArgumentException.class,
                () -> SomaConfiguration.builder().parallelExecutor(null));
    }

    private static void assertAllConstructorsPrivate(Class<?> type) {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertTrue(constructors.length > 0);
        for (Constructor<?> constructor : constructors) {
            assertTrue(Modifier.isPrivate(constructor.getModifiers()), constructor.toString());
        }
    }
}
