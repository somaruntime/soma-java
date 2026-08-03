package com.example.scheduler;

import com.example.scheduler.soma.internal.SomaGeneratedComposition;
import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.internal.SomaRuntimeAccess;

public final class Application {
    private Application() {
    }

    public static void main(String[] arguments) {
        if (!"com.example.scheduler.soma.schema".equals(
                SomaGeneratedComposition.SCHEMA_PACKAGE)) {
            throw new AssertionError("unexpected schema package");
        }
        if (SomaGeneratedComposition.SCHEMA_FINGERPRINT.length() != 64) {
            throw new AssertionError("unexpected schema fingerprint");
        }
        SomaGeneratedComposition.verifyRuntime();

        if (!"UNFROZEN".equals(SomaRuntimeAccess.configurationState())) {
            throw new AssertionError("class linkage must not freeze configuration");
        }
        SomaConfiguration configuration = SomaConfiguration.builder()
                .memoryBudgetBytes(1024L * 1024L)
                .compression(SomaCompression.OFF)
                .build();
        if (!"UNFROZEN".equals(SomaRuntimeAccess.configurationState())) {
            throw new AssertionError("building configuration must not freeze runtime");
        }
        SomaRuntimeAccess.configure(configuration);
        if (!"EXPLICIT".equals(SomaRuntimeAccess.configurationState())) {
            throw new AssertionError("explicit configuration was not frozen");
        }
    }
}
