package io.github.somaruntime.soma.internal;

/** Calling-thread marker used only to reject nested parallel terminals. */
final class CallbackExecutionScope {

    private static final ThreadLocal<Depth> DEPTH = new ThreadLocal<Depth>();

    private CallbackExecutionScope() {
    }

    static void enter() {
        Depth depth = DEPTH.get();
        if (depth == null) {
            depth = new Depth();
            DEPTH.set(depth);
        }
        depth.value++;
    }

    static void exit() {
        Depth depth = DEPTH.get();
        if (depth == null || depth.value <= 0) {
            throw new AssertionError("callback scope underflow");
        }
        depth.value--;
    }

    static boolean isActive() {
        Depth depth = DEPTH.get();
        return depth != null && depth.value > 0;
    }

    /** Release the reusable marker once the outer SOMA operation has quiesced. */
    static void clearIfInactive() {
        Depth depth = DEPTH.get();
        if (depth != null && depth.value == 0) DEPTH.remove();
    }

    private static final class Depth {
        private int value;
    }
}
