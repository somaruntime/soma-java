package io.github.somaruntime.soma;

import io.github.somaruntime.soma.internal.SomaSharedSecrets;

/** Immutable process-wide SOMA configuration. */
public final class SomaConfiguration {

    static {
        SomaSharedSecrets.setConfigurationAccess(
                new SomaSharedSecrets.ConfigurationAccess() {
                    @Override
                    public boolean hasMemoryBudget(SomaConfiguration configuration) {
                        return configuration.hasMemoryBudget;
                    }

                    @Override
                    public long memoryBudgetBytes(SomaConfiguration configuration) {
                        return configuration.memoryBudgetBytes;
                    }
                });
    }

    private final boolean hasMemoryBudget;
    private final long memoryBudgetBytes;

    private SomaConfiguration(Builder builder) {
        this.hasMemoryBudget = builder.hasMemoryBudget;
        this.memoryBudgetBytes = builder.memoryBudgetBytes;
    }

    public static Builder builder() {
        return Builder.create();
    }

    private static SomaConfiguration create(Builder builder) {
        return new SomaConfiguration(builder);
    }

    /** Builder validation is local and therefore uses ordinary Java exceptions. */
    public static final class Builder {

        private boolean hasMemoryBudget;
        private long memoryBudgetBytes;

        private Builder() {
        }

        private static Builder create() {
            return new Builder();
        }

        public Builder memoryBudgetBytes(long bytes) {
            if (bytes <= 0L) {
                throw new IllegalArgumentException("memoryBudgetBytes must be positive");
            }
            this.hasMemoryBudget = true;
            this.memoryBudgetBytes = bytes;
            return this;
        }

        public SomaConfiguration build() {
            return SomaConfiguration.create(this);
        }
    }
}
