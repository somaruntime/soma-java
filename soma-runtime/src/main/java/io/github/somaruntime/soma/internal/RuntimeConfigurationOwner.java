package io.github.somaruntime.soma.internal;

import java.util.function.Supplier;

final class RuntimeConfigurationOwner {

    private static final RuntimeConfigurationState STATE = new RuntimeConfigurationState();

    private RuntimeConfigurationOwner() {
    }

    static boolean isFrozen() {
        return STATE.observe().isFrozen();
    }

    static RuntimeConfigurationState.Observation observe() {
        return STATE.observe();
    }

    static RuntimeConfigurationState.Snapshot configure(
            RuntimeConfigurationState.Snapshot candidate) {
        return STATE.configure(candidate);
    }

    static RuntimeConfigurationState.Snapshot freezeDefault(
            Supplier<RuntimeConfigurationState.Snapshot> policy) {
        return STATE.freezeDefault(policy);
    }
}
