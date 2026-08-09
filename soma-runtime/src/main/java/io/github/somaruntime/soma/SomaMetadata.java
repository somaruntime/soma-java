package io.github.somaruntime.soma;

import io.github.somaruntime.soma.internal.SomaSharedSecrets;
import java.util.Optional;
import java.util.OptionalLong;

/** Detached immutable snapshot of process-wide SOMA state. */
public final class SomaMetadata {

    public enum ConfigurationState { UNFROZEN, FROZEN }

    static {
        SomaSharedSecrets.setSomaMetadataAccess(
                new SomaSharedSecrets.SomaMetadataAccess() {
                    @Override
                    public SomaMetadata create(
                            String composition,
                            boolean frozen,
                            long effectiveBudget,
                            SomaCompression compression,
                            long retained,
                            long temporary) {
                        return new SomaMetadata(
                                composition, frozen, effectiveBudget,
                                compression, retained, temporary);
                    }
                });
    }

    private final String composition;
    private final ConfigurationState configurationState;
    private final OptionalLong effectiveMemoryBudgetBytes;
    private final Optional<SomaCompression> compression;
    private final long globalRetainedBytes;
    private final long globalTemporaryBytes;

    private SomaMetadata(
            String composition,
            boolean frozen,
            long effectiveBudget,
            SomaCompression compression,
            long retained,
            long temporary) {
        this.composition = composition;
        this.configurationState = frozen
                ? ConfigurationState.FROZEN : ConfigurationState.UNFROZEN;
        this.effectiveMemoryBudgetBytes = frozen
                ? OptionalLong.of(effectiveBudget) : OptionalLong.empty();
        this.compression = Optional.ofNullable(compression);
        this.globalRetainedBytes = retained;
        this.globalTemporaryBytes = temporary;
    }

    public String composition() { return composition; }
    public ConfigurationState configurationState() { return configurationState; }
    public OptionalLong effectiveMemoryBudgetBytes() {
        return effectiveMemoryBudgetBytes;
    }
    public Optional<SomaCompression> compression() { return compression; }
    public long globalRetainedBytes() { return globalRetainedBytes; }
    public long globalTemporaryBytes() { return globalTemporaryBytes; }
}
