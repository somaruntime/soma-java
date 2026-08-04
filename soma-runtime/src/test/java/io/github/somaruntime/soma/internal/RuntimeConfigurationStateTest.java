package io.github.somaruntime.soma.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RuntimeConfigurationStateTest {

    @Test
    void observationDoesNotFreezeAndExplicitConfigurationWinsOnce() {
        RuntimeConfigurationState state = new RuntimeConfigurationState();
        assertFalse(state.observe().isFrozen());

        RuntimeConfigurationState.Snapshot configured =
                new RuntimeConfigurationState.Snapshot(1024L, "explicit");
        assertSame(configured, state.configure(configured));
        assertTrue(state.observe().isFrozen());
        assertSame(configured, state.observe().snapshot());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> state.configure(
                        new RuntimeConfigurationState.Snapshot(2048L, "second")));
        assertTrue(error.getMessage().startsWith("[SOMA-0003]"));
        assertSame(configured, state.observe().snapshot());
    }

    @Test
    void concurrentDefaultFreezePublishesOneStableSnapshot() throws Exception {
        RuntimeConfigurationState state = new RuntimeConfigurationState();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger policies = new AtomicInteger();
        AtomicReference<RuntimeConfigurationState.Snapshot> left = new AtomicReference<>();
        AtomicReference<RuntimeConfigurationState.Snapshot> right = new AtomicReference<>();

        Thread first = freezeThread(state, start, policies, left);
        Thread second = freezeThread(state, start, policies, right);
        first.start();
        second.start();
        start.countDown();
        first.join();
        second.join();

        assertTrue(state.observe().isFrozen());
        assertSame(state.observe().snapshot(), left.get());
        assertSame(left.get(), right.get());
        assertTrue(policies.get() >= 1);
        assertTrue(policies.get() <= 2);
        assertEquals(4096L, left.get().memoryBudgetBytes());
        assertEquals("default", left.get().policyIdentity());
    }

    private static Thread freezeThread(
            RuntimeConfigurationState state,
            CountDownLatch start,
            AtomicInteger policies,
            AtomicReference<RuntimeConfigurationState.Snapshot> result) {
        return new Thread(() -> {
            try {
                start.await();
                result.set(state.freezeDefault(() -> {
                    policies.incrementAndGet();
                    return new RuntimeConfigurationState.Snapshot(4096L, "default");
                }));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        });
    }
}
