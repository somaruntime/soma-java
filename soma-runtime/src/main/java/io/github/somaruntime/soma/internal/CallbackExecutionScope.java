package io.github.somaruntime.soma.internal;

/** Calling-thread marker used only to reject nested parallel terminals. */
final class CallbackExecutionScope {

    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<Integer>();

    private CallbackExecutionScope() {
    }

    static void enter() {
        Integer depth = DEPTH.get();
        DEPTH.set(depth == null ? 1 : depth + 1);
    }

    static void exit() {
        Integer depth = DEPTH.get();
        if (depth == null || depth <= 0) {
            throw new AssertionError("callback scope underflow");
        }
        if (depth == 1) DEPTH.remove();
        else DEPTH.set(depth - 1);
    }

    static boolean isActive() {
        Integer depth = DEPTH.get();
        return depth != null && depth > 0;
    }
}
