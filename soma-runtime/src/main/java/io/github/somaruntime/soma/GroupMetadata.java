package io.github.somaruntime.soma;

import io.github.somaruntime.soma.internal.SomaSharedSecrets;

/** Detached immutable snapshot of one SOMA Group. */
public final class GroupMetadata {

    static {
        SomaSharedSecrets.setGroupMetadataAccess(
                new SomaSharedSecrets.GroupMetadataAccess() {
                    @Override
                    public GroupMetadata create(
                            boolean defaultGroup,
                            long retained,
                            long globalRetained,
                            long globalTemporary,
                            long effectiveBudget,
                            SomaCompression compression) {
                        return new GroupMetadata(
                                defaultGroup, retained, globalRetained,
                                globalTemporary, effectiveBudget, compression);
                    }
                });
    }

    private final boolean defaultGroup;
    private final long retainedBytes;
    private final long globalRetainedBytes;
    private final long globalTemporaryBytes;
    private final long effectiveMemoryBudgetBytes;
    private final SomaCompression compression;

    private GroupMetadata(
            boolean defaultGroup,
            long retainedBytes,
            long globalRetainedBytes,
            long globalTemporaryBytes,
            long effectiveMemoryBudgetBytes,
            SomaCompression compression) {
        this.defaultGroup = defaultGroup;
        this.retainedBytes = retainedBytes;
        this.globalRetainedBytes = globalRetainedBytes;
        this.globalTemporaryBytes = globalTemporaryBytes;
        this.effectiveMemoryBudgetBytes = effectiveMemoryBudgetBytes;
        this.compression = compression;
    }

    public boolean defaultGroup() { return defaultGroup; }
    public long retainedBytes() { return retainedBytes; }
    public long globalRetainedBytes() { return globalRetainedBytes; }
    public long globalTemporaryBytes() { return globalTemporaryBytes; }
    public long effectiveMemoryBudgetBytes() { return effectiveMemoryBudgetBytes; }
    public SomaCompression compression() { return compression; }
}
