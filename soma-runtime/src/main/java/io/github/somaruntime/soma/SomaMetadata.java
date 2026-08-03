package io.github.somaruntime.soma;

/**
 * Immutable library-wide metadata snapshot.
 *
 * <p>Before runtime configuration is frozen, {@link #configurationState()} is {@code "UNFROZEN"},
 * {@link #effectiveMemoryBudgetBytes()} is {@code -1}, and {@link #compression()} is {@code null}.
 * Reading this snapshot never freezes configuration.</p>
 */
public final class SomaMetadata {
    private final String configurationState;
    private final long effectiveMemoryBudgetBytes;
    private final SomaCompression compression;

    private SomaMetadata(
            String configurationState,
            long effectiveMemoryBudgetBytes,
            SomaCompression compression) {
        this.configurationState = configurationState;
        this.effectiveMemoryBudgetBytes = effectiveMemoryBudgetBytes;
        this.compression = compression;
    }

    static SomaMetadata trustedCreate(
            String configurationState,
            long effectiveMemoryBudgetBytes,
            SomaCompression compression) {
        return new SomaMetadata(configurationState, effectiveMemoryBudgetBytes, compression);
    }

    public String configurationState() {
        return configurationState;
    }

    public long effectiveMemoryBudgetBytes() {
        return effectiveMemoryBudgetBytes;
    }

    /** Returns the effective policy, or {@code null} while configuration is {@code UNFROZEN}. */
    public SomaCompression compression() {
        return compression;
    }
}
