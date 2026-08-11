package io.github.somaruntime.soma;

import io.github.somaruntime.soma.internal.SomaSharedSecrets;

/** Detached immutable outcome of a Table update. */
public final class UpdateResult {

    private static final UpdateResult MISSING = new UpdateResult(0, 0);
    private static final UpdateResult UNCHANGED = new UpdateResult(1, 0);
    private static final UpdateResult CHANGED = new UpdateResult(1, 1);

    static {
        SomaSharedSecrets.setUpdateResultAccess(
                new SomaSharedSecrets.UpdateResultAccess() {
                    @Override
                    public UpdateResult create(int matched, int changed) {
                        return UpdateResult.createTrusted(matched, changed);
                    }
                });
    }

    private final int matched;
    private final int changed;

    private UpdateResult(int matched, int changed) {
        if (matched < 0 || changed < 0 || changed > matched) {
            throw new AssertionError("invalid trusted UpdateResult");
        }
        this.matched = matched;
        this.changed = changed;
    }

    public int matched() {
        return matched;
    }

    public int changed() {
        return changed;
    }

    private static UpdateResult createTrusted(int matched, int changed) {
        if (matched == 0 && changed == 0) return MISSING;
        if (matched == 1 && changed == 0) return UNCHANGED;
        if (matched == 1 && changed == 1) return CHANGED;
        return new UpdateResult(matched, changed);
    }
}
