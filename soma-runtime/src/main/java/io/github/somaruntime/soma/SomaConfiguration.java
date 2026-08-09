package io.github.somaruntime.soma;

import io.github.somaruntime.soma.internal.SomaSharedSecrets;
import java.util.concurrent.ForkJoinPool;

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

                    @Override
                    public ForkJoinPool parallelExecutor(
                            SomaConfiguration configuration) {
                        return configuration.parallelExecutor;
                    }

                    @Override
                    public SomaCompression compression(
                            SomaConfiguration configuration) {
                        return configuration.compression;
                    }
                });
    }

    private final boolean hasMemoryBudget;
    private final long memoryBudgetBytes;
    private final ForkJoinPool parallelExecutor;
    private final SomaCompression compression;

    private SomaConfiguration(Builder builder) {
        this.hasMemoryBudget = builder.hasMemoryBudget;
        this.memoryBudgetBytes = builder.memoryBudgetBytes;
        this.parallelExecutor = builder.parallelExecutor;
        this.compression = builder.compression;
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
        private ForkJoinPool parallelExecutor;
        private SomaCompression compression = SomaCompression.AUTO;

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

        public Builder parallelExecutor(ForkJoinPool executor) {
            if (executor == null) {
                throw new IllegalArgumentException("parallelExecutor is null");
            }
            this.parallelExecutor = executor;
            return this;
        }

        public Builder compression(SomaCompression compression) {
            if (compression == null) {
                throw new IllegalArgumentException("compression is null");
            }
            this.compression = compression;
            return this;
        }

        public SomaConfiguration build() {
            return SomaConfiguration.create(this);
        }
    }
}
