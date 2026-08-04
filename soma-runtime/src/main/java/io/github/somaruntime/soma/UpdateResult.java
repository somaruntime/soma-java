package io.github.somaruntime.soma;

import io.github.somaruntime.soma.internal.SomaSharedSecrets;

/** Detached immutable outcome of a Table update. */
public final class UpdateResult {

    static {
        SomaSharedSecrets.setUpdateResultAccess(
                new SomaSharedSecrets.UpdateResultAccess() {
                    @Override
                    public UpdateResult create(long matched, long changed) {
                        return UpdateResult.createTrusted(matched, changed);
                    }
                });
    }

    private final long matched;
    private final long changed;

    private UpdateResult(long matched, long changed) {
        if (matched < 0L || changed < 0L || changed > matched) {
            throw new AssertionError("invalid trusted UpdateResult");
        }
        this.matched = matched;
        this.changed = changed;
    }

    public long matched() {
        return matched;
    }

    public long changed() {
        return changed;
    }

    private static UpdateResult createTrusted(long matched, long changed) {
        return new UpdateResult(matched, changed);
    }
}
