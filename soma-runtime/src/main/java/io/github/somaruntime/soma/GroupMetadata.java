package io.github.somaruntime.soma;

/** Immutable Group metadata snapshot. */
public final class GroupMetadata {
    private final boolean defaultGroup;
    private final long tableCount;

    private GroupMetadata(boolean defaultGroup, long tableCount) {
        this.defaultGroup = defaultGroup;
        this.tableCount = tableCount;
    }

    static GroupMetadata trustedCreate(boolean defaultGroup, long tableCount) {
        return new GroupMetadata(defaultGroup, tableCount);
    }

    public boolean defaultGroup() {
        return defaultGroup;
    }

    public long tableCount() {
        return tableCount;
    }
}
