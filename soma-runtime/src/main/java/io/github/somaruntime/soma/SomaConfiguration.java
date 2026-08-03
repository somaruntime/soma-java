package io.github.somaruntime.soma;

import io.github.somaruntime.soma.internal.SomaRuntimeAccess;
import java.util.concurrent.ForkJoinPool;

/** 不可变的 library-wide SOMA configuration。 */
public final class SomaConfiguration {
    static {
        SomaRuntimeAccess.installFailureFactory(new SomaRuntimeAccess.FailureFactory() {
            @Override
            public SomaOperationException create(
                    SomaFailureCode code,
                    SomaOperation operation,
                    String context,
                    Throwable cause) {
                return SomaOperationException.create(code, operation, context, cause);
            }
        });
    }

    private SomaConfiguration(
            ForkJoinPool parallelExecutor,
            Long memoryBudgetBytes,
            SomaCompression compression) {
        SomaRuntimeAccess.registerConfiguration(
                this, parallelExecutor, memoryBudgetBytes, compression);
    }

    /** 创建一个相互独立的 configuration builder。 */
    public static Builder builder() {
        return new Builder();
    }

    /** 构建一份不可变 configuration value。 */
    public static final class Builder {
        private ForkJoinPool parallelExecutor;
        private Long memoryBudgetBytes;
        private SomaCompression compression = SomaCompression.AUTO;

        private Builder() {
        }

        /** 为显式 parallel terminal 使用 application-owned ForkJoinPool。 */
        public Builder parallelExecutor(ForkJoinPool executor) {
            if (executor == null) {
                throw new IllegalArgumentException("parallelExecutor must not be null");
            }
            this.parallelExecutor = executor;
            return this;
        }

        /** 设置正数的全局 managed-memory budget。 */
        public Builder memoryBudgetBytes(long bytes) {
            if (bytes <= 0L) {
                throw new IllegalArgumentException("memoryBudgetBytes must be positive");
            }
            this.memoryBudgetBytes = Long.valueOf(bytes);
            return this;
        }

        /** 选择 library-wide compression policy。 */
        public Builder compression(SomaCompression value) {
            if (value == null) {
                throw new IllegalArgumentException("compression must not be null");
            }
            this.compression = value;
            return this;
        }

        /** 创建不可变 configuration，但不冻结 runtime。 */
        public SomaConfiguration build() {
            return new SomaConfiguration(parallelExecutor, memoryBudgetBytes, compression);
        }
    }
}
