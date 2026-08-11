package io.github.somaruntime.soma.internal;

/** Compact logical identity shared by Canonical nodes for one generated Table type. */
final class CanonicalTableIdentity {

    private final Object compositionCapability;
    private final int tableOrdinal;
    private final GeneratedTableLayout descriptor;

    CanonicalTableIdentity(
            Object compositionCapability,
            int tableOrdinal,
            GeneratedTableLayout descriptor) {
        if (compositionCapability == null || tableOrdinal < 0 || descriptor == null) {
            throw new AssertionError("invalid canonical Table identity");
        }
        this.compositionCapability = compositionCapability;
        this.tableOrdinal = tableOrdinal;
        this.descriptor = descriptor;
    }

    int tableOrdinal() {
        return tableOrdinal;
    }

    GeneratedTableLayout descriptor() {
        return descriptor;
    }

    boolean sameComposition(CanonicalTableIdentity other) {
        return other != null && compositionCapability == other.compositionCapability;
    }

    boolean sameTable(CanonicalTableIdentity other) {
        return sameComposition(other)
                && tableOrdinal == other.tableOrdinal
                && descriptor == other.descriptor;
    }
}
