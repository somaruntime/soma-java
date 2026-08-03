package com.example.scheduler;

import com.example.scheduler.soma.internal.SomaGeneratedComposition;

/** 证明 generated composition 面对不兼容 runtime 时 fail closed。 */
public final class RuntimeSwapProbe {
    private RuntimeSwapProbe() {
    }

    public static void main(String[] arguments) {
        try {
            SomaGeneratedComposition.verifyRuntime();
            throw new AssertionError("incompatible runtime was accepted");
        } catch (ExceptionInInitializerError failure) {
            Throwable cause = failure.getCause();
            if (!(cause instanceof IllegalStateException)
                    || cause.getMessage() == null
                    || cause.getMessage().indexOf("[SOMA-0102]") < 0) {
                throw new AssertionError("unexpected runtime-swap failure", failure);
            }
        }
    }
}
