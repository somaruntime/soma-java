package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;

/** Callback-scoped presence and provenance for a generated JoinPair. */
final class GeneratedPairCursor implements GeneratedPairAccess {

    private Object pair;
    private Object provenance;
    private Thread participant;
    private boolean active;
    private boolean left;
    private boolean right;

    @Override
    public void register(Object value) {
        if (value == null || pair != null) {
            throw new AssertionError("invalid generated JoinPair registration");
        }
        pair = value;
    }

    void begin(boolean hasLeft, boolean hasRight, Object operationProvenance) {
        if (active || operationProvenance == null || !hasLeft && !hasRight) {
            throw new AssertionError("invalid JoinPair callback scope");
        }
        provenance = operationProvenance;
        participant = Thread.currentThread();
        left = hasLeft;
        right = hasRight;
        active = true;
    }

    void end() {
        active = false;
        left = false;
        right = false;
        provenance = null;
        participant = null;
    }

    boolean owns(Object value) {
        return value == pair;
    }

    @Override public boolean hasLeft() { requireActive(); return left; }
    @Override public boolean hasRight() { requireActive(); return right; }

    @Override
    public void requireLeft() {
        requireActive();
        if (!left) throw missing("left");
    }

    @Override
    public void requireRight() {
        requireActive();
        if (!right) throw missing("right");
    }

    private void requireActive() {
        if (!active || participant != Thread.currentThread()) {
            throw SomaFailures.failure(
                    SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                    SomaOperation.QUERY,
                    "borrowed JoinPair is outside its callback scope",
                    provenance == null ? new Object() : provenance);
        }
    }

    private RuntimeException missing(String side) {
        return SomaFailures.failure(
                SomaFailureCode.MISSING_RELATION_SIDE,
                SomaOperation.QUERY,
                "JoinPair " + side + " side is missing",
                provenance);
    }
}
