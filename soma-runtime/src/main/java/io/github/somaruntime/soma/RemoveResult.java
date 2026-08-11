package io.github.somaruntime.soma;

import io.github.somaruntime.soma.internal.SomaSharedSecrets;

/** Detached immutable outcome of a Table remove. */
public final class RemoveResult {

    private static final RemoveResult MISSING = new RemoveResult(0);
    private static final RemoveResult REMOVED = new RemoveResult(1);

    static {
        SomaSharedSecrets.setRemoveResultAccess(
                new SomaSharedSecrets.RemoveResultAccess() {
                    @Override
                    public RemoveResult create(int removed) {
                        return RemoveResult.createTrusted(removed);
                    }
                });
    }

    private final int removed;

    private RemoveResult(int removed) {
        if (removed < 0) {
            throw new AssertionError("invalid trusted RemoveResult");
        }
        this.removed = removed;
    }

    public int removed() {
        return removed;
    }

    private static RemoveResult createTrusted(int removed) {
        if (removed == 0) return MISSING;
        if (removed == 1) return REMOVED;
        return new RemoveResult(removed);
    }
}
