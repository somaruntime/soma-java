package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.UpdateResult;
import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.IntGroupedLongResult;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;

/** Generated code 使用的内部 linkage；该类型不是 application compatibility contract。 */
public final class SomaRuntimeAccess {
    private static final Map<SomaConfiguration, ConfigurationValues> CONFIGURATIONS =
            Collections.synchronizedMap(
                    new WeakHashMap<SomaConfiguration, ConfigurationValues>());
    private static final RuntimeState UNFROZEN = RuntimeState.unfrozen();
    private static final AtomicReference<RuntimeState> STATE =
            new AtomicReference<RuntimeState>(UNFROZEN);
    private static volatile FailureFactory failureFactory;
    private static volatile UpdateResultFactory updateResultFactory;
    private static volatile RemoveResultFactory removeResultFactory;
    private static volatile IntGroupedLongResultFactory intGroupedLongResultFactory;

    private SomaRuntimeAccess() {
    }

    /** 由 shared failure carrier package 安装的内部 factory。 */
    public interface FailureFactory {
        SomaOperationException create(
                SomaFailureCode code,
                SomaOperation operation,
                String context,
                Throwable cause);
    }

    /** Trusted factory installed by the result carrier's class initializer. */
    public interface UpdateResultFactory {
        UpdateResult create(long matched, long changed);
    }

    public interface RemoveResultFactory {
        RemoveResult create(long removed);
    }

    public interface IntGroupedLongResultFactory {
        IntGroupedLongResult create(int[] keys, long[] values);
    }

    /** 安装唯一的 ClassLoader-local structured-failure factory。 */
    public static void installFailureFactory(FailureFactory factory) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        synchronized (SomaRuntimeAccess.class) {
            if (failureFactory != null && failureFactory != factory) {
                throw new IllegalStateException("SOMA failure factory is already installed");
            }
            failureFactory = factory;
        }
    }

    /** Installs the one class-local factory used by generated mutation code. */
    public static void installUpdateResultFactory(UpdateResultFactory factory) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        synchronized (SomaRuntimeAccess.class) {
            if (updateResultFactory != null && updateResultFactory != factory) {
                throw new IllegalStateException("SOMA update-result factory is already installed");
            }
            updateResultFactory = factory;
        }
    }

    public static void installRemoveResultFactory(RemoveResultFactory factory) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        synchronized (SomaRuntimeAccess.class) {
            if (removeResultFactory != null && removeResultFactory != factory) {
                throw new IllegalStateException("SOMA remove-result factory is already installed");
            }
            removeResultFactory = factory;
        }
    }

    public static void installIntGroupedLongResultFactory(IntGroupedLongResultFactory factory) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        synchronized (SomaRuntimeAccess.class) {
            if (intGroupedLongResultFactory != null && intGroupedLongResultFactory != factory) {
                throw new IllegalStateException("SOMA grouped-result factory is already installed");
            }
            intGroupedLongResultFactory = factory;
        }
    }

    /** Creates a structured result through the trusted result carrier. */
    public static UpdateResult updateResult(long matched, long changed) {
        UpdateResultFactory factory = updateResultFactory;
        if (factory == null) {
            SomaConfiguration.builder();
            factory = updateResultFactory;
        }
        if (factory == null) {
            throw new IllegalStateException("SOMA update-result factory is unavailable");
        }
        return factory.create(matched, changed);
    }

    public static RemoveResult removeResult(long removed) {
        RemoveResultFactory factory = removeResultFactory;
        if (factory == null) {
            SomaConfiguration.builder();
            factory = removeResultFactory;
        }
        if (factory == null) {
            throw new IllegalStateException("SOMA remove-result factory is unavailable");
        }
        return factory.create(removed);
    }

    public static IntGroupedLongResult intGroupedLongResult(int[] keys, long[] values) {
        IntGroupedLongResultFactory factory = intGroupedLongResultFactory;
        if (factory == null) {
            SomaConfiguration.builder();
            factory = intGroupedLongResultFactory;
        }
        if (factory == null) {
            throw new IllegalStateException("SOMA grouped-result factory is unavailable");
        }
        return factory.create(keys, values);
    }

    /** Creates a stable structured operation failure for internal owners. */
    public static SomaOperationException failure(
            SomaFailureCode code,
            SomaOperation operation,
            String context,
            Throwable cause) {
        FailureFactory factory = failureFactory;
        if (factory == null) {
            SomaConfiguration.builder();
            factory = failureFactory;
        }
        if (factory == null) {
            throw new IllegalStateException("SOMA failure factory is unavailable");
        }
        return factory.create(code, operation, context, cause);
    }

    /** Creates a typed expression node owned by generated source. */
    public static <T> SomaExpression<T> expression(
            io.github.somaruntime.soma.internal.SomaExpressionNode.Evaluator<T> evaluator) {
        return new SomaExpressionNode<T>(evaluator, null);
    }

    /** Creates a generated expression bound to one composition/table owner. */
    public static <T> SomaExpression<T> expression(
            Object owner,
            io.github.somaruntime.soma.internal.SomaExpressionNode.Evaluator<T> evaluator) {
        if (owner == null) {
            throw invalidExpression();
        }
        return new SomaExpressionNode<T>(evaluator, owner);
    }

    /** Evaluates a processor-issued expression without exposing its evaluator contract. */
    public static <T> boolean evaluate(SomaExpression<T> expression, T value) {
        return evaluate(expression, value, null);
    }

    /** Evaluates an expression only when its generated owner matches the receiver. */
    public static <T> boolean evaluate(SomaExpression<T> expression, T value, Object owner) {
        if (!(expression instanceof SomaExpressionNode)) {
            throw invalidExpression();
        }
        @SuppressWarnings("unchecked")
        SomaExpressionNode<T> node = (SomaExpressionNode<T>) expression;
        if (!node.ownedBy(owner)) {
            throw invalidExpression();
        }
        return node.evaluate(value);
    }

    /** Validates expression provenance before a linked pipeline receiver is claimed. */
    public static void validate(SomaExpression<?> expression, Object owner) {
        if (!(expression instanceof SomaExpressionNode)
                || !((SomaExpressionNode<?>) expression).ownedBy(owner)) {
            throw invalidExpression();
        }
    }

    /** Internal stable failure for an application-supplied foreign expression. */
    public static SomaOperationException invalidExpression() {
        return failure(
                SomaFailureCode.INVALID_ARGUMENT,
                SomaOperation.QUERY,
                "foreign or replayed SOMA expression",
                null);
    }

    /** 注册一份不可变 configuration instance 的 opaque values。 */
    public static void registerConfiguration(
            SomaConfiguration configuration,
            ForkJoinPool executor,
            Long memoryBudgetBytes,
            SomaCompression compression) {
        if (configuration == null || compression == null) {
            throw new NullPointerException("configuration");
        }
        CONFIGURATIONS.put(
                configuration,
                new ConfigurationValues(executor, memoryBudgetBytes, compression));
    }

    /** 以 exactly-once 方式冻结显式 configuration。 */
    public static void configure(SomaConfiguration configuration) {
        if (configuration == null) {
            throw failure(
                    SomaFailureCode.INVALID_ARGUMENT,
                    "configuration must not be null");
        }
        ConfigurationValues values = CONFIGURATIONS.get(configuration);
        if (values == null) {
            throw failure(
                    SomaFailureCode.INVALID_ARGUMENT,
                    "configuration is not a SOMA builder result");
        }
        RuntimeState next = RuntimeState.explicit(values);
        if (!STATE.compareAndSet(UNFROZEN, next)) {
            throw failure(
                    SomaFailureCode.CONFIGURATION_FROZEN,
                    "SOMA runtime configuration is already frozen");
        }
    }

    /** 在第一次真实 runtime access 时冻结 automatic default。 */
    public static void freezeForRuntimeAccess() {
        RuntimeState current = STATE.get();
        if (current.frozen) {
            return;
        }
        RuntimeState automatic = RuntimeState.automatic();
        STATE.compareAndSet(UNFROZEN, automatic);
    }

    /** 读取内部 configuration state，但不触发冻结。 */
    public static String configurationState() {
        RuntimeState state = STATE.get();
        if (!state.frozen) {
            return "UNFROZEN";
        }
        return state.explicit ? "EXPLICIT" : "DEFAULT";
    }

    /** 返回 effective budget；冻结前返回 {@code -1}。 */
    public static long effectiveMemoryBudgetBytes() {
        return STATE.get().effectiveMemoryBudgetBytes;
    }

    /** 返回 effective compression policy；冻结前返回 {@code null}。 */
    public static SomaCompression effectiveCompression() {
        return STATE.get().compression;
    }

    /** 在使用 generated capability 前验证精确的 processor/runtime linkage。 */
    public static void verifyGeneratedArtifact(
            String expectedArtifactVersion,
            String expectedContractVersion,
            String schemaFingerprint) {
        if (!SomaRuntimeContract.ARTIFACT_VERSION.equals(expectedArtifactVersion)
                || !SomaRuntimeContract.CONTRACT_VERSION.equals(expectedContractVersion)) {
            throw new IllegalStateException(
                    "[SOMA-0102] generated/runtime version mismatch");
        }
        if (schemaFingerprint == null || schemaFingerprint.length() == 0) {
            throw new IllegalStateException(
                    "[SOMA-0102] generated composition fingerprint is missing");
        }
    }

    private static SomaOperationException failure(SomaFailureCode code, String context) {
        FailureFactory factory = failureFactory;
        if (factory == null) {
            SomaConfiguration.builder();
            factory = failureFactory;
        }
        if (factory == null) {
            throw new IllegalStateException("SOMA failure factory is unavailable");
        }
        return factory.create(code, SomaOperation.CONFIGURE, context, null);
    }

    private static long automaticBudget() {
        long maximum = Runtime.getRuntime().maxMemory();
        if (maximum <= 1L) {
            return 1L;
        }
        return maximum / 2L;
    }

    private static final class ConfigurationValues {
        private final ForkJoinPool executor;
        private final Long memoryBudgetBytes;
        private final SomaCompression compression;

        private ConfigurationValues(
                ForkJoinPool executor,
                Long memoryBudgetBytes,
                SomaCompression compression) {
            this.executor = executor;
            this.memoryBudgetBytes = memoryBudgetBytes;
            this.compression = compression;
        }
    }

    private static final class RuntimeState {
        private final boolean frozen;
        private final boolean explicit;
        private final ForkJoinPool executor;
        private final long effectiveMemoryBudgetBytes;
        private final SomaCompression compression;

        private RuntimeState(
                boolean frozen,
                boolean explicit,
                ForkJoinPool executor,
                long effectiveMemoryBudgetBytes,
                SomaCompression compression) {
            this.frozen = frozen;
            this.explicit = explicit;
            this.executor = executor;
            this.effectiveMemoryBudgetBytes = effectiveMemoryBudgetBytes;
            this.compression = compression;
        }

        private static RuntimeState unfrozen() {
            return new RuntimeState(false, false, null, -1L, null);
        }

        private static RuntimeState explicit(ConfigurationValues values) {
            long budget = values.memoryBudgetBytes == null
                    ? automaticBudget()
                    : values.memoryBudgetBytes.longValue();
            ForkJoinPool pool = values.executor == null
                    ? ForkJoinPool.commonPool()
                    : values.executor;
            return new RuntimeState(true, true, pool, budget, values.compression);
        }

        private static RuntimeState automatic() {
            return new RuntimeState(
                    true,
                    false,
                    ForkJoinPool.commonPool(),
                    automaticBudget(),
                    SomaCompression.AUTO);
        }
    }
}
