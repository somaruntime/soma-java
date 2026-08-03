package com.example.soma.i1;

import com.example.soma.i1.soma.Soma;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.internal.SomaRuntimeAccess;

public final class ConfigureFirst {
    private ConfigureFirst() {
    }

    public static void main(String[] arguments) {
        if (!"UNFROZEN".equals(SomaRuntimeAccess.configurationState())) {
            throw new AssertionError("runtime unexpectedly frozen before configure");
        }
        Soma.configure(SomaConfiguration.builder().memoryBudgetBytes(1024L).build());
        if (!"EXPLICIT".equals(SomaRuntimeAccess.configurationState())) {
            throw new AssertionError("configure-first did not freeze explicit policy");
        }
        Soma.defaultGroup().workItemTable();
    }
}
