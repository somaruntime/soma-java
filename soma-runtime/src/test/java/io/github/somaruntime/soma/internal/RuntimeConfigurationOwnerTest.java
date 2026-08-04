package io.github.somaruntime.soma.internal;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RuntimeConfigurationOwnerTest {

    @Test
    void productionOwnerPublishesOneSharedConfigureFirstSnapshot() {
        RuntimeConfigurationState.Snapshot configured =
                new RuntimeConfigurationState.Snapshot(8192L, "explicit-owner-test");
        assertSame(configured, RuntimeConfigurationOwner.configure(configured));
        assertSame(configured, RuntimeConfigurationOwner.freezeDefault(() ->
                new RuntimeConfigurationState.Snapshot(4096L, "unreachable-default")));
        assertTrue(RuntimeConfigurationOwner.observe().isFrozen());
        assertSame(configured, RuntimeConfigurationOwner.observe().snapshot());
    }
}
