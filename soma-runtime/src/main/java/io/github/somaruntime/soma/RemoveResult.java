package io.github.somaruntime.soma;

import io.github.somaruntime.soma.internal.SomaSharedSecrets;

/** Detached immutable outcome of a Table remove. */
public final class RemoveResult {

    static {
        SomaSharedSecrets.setRemoveResultAccess(
                new SomaSharedSecrets.RemoveResultAccess() {
                    @Override
                    public RemoveResult create(long removed) {
                        return RemoveResult.createTrusted(removed);
                    }
                });
    }

    private final long removed;

    private RemoveResult(long removed) {
        if (removed < 0L) {
            throw new AssertionError("invalid trusted RemoveResult");
        }
        this.removed = removed;
    }

    public long removed() {
        return removed;
    }

    private static RemoveResult createTrusted(long removed) {
        return new RemoveResult(removed);
    }
}
