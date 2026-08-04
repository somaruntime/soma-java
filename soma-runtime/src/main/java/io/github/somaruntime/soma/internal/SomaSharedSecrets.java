package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.UpdateResult;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Java 8 friend-access bridge for private public-carrier constructors/state.
 * This class is internal and is not an application SPI.
 */
public final class SomaSharedSecrets {

    private static final AtomicReference<ConfigurationAccess> CONFIGURATION =
            new AtomicReference<ConfigurationAccess>();
    private static final AtomicReference<UpdateResultAccess> UPDATE_RESULT =
            new AtomicReference<UpdateResultAccess>();
    private static final AtomicReference<FailureAccess> FAILURE =
            new AtomicReference<FailureAccess>();

    private SomaSharedSecrets() {
    }

    public static void setConfigurationAccess(ConfigurationAccess access) {
        install(CONFIGURATION, access, SomaConfiguration.class);
    }

    static ConfigurationAccess configurationAccess() {
        initialize(SomaConfiguration.class);
        return required(CONFIGURATION.get(), "SomaConfiguration");
    }

    public static void setUpdateResultAccess(UpdateResultAccess access) {
        install(UPDATE_RESULT, access, UpdateResult.class);
    }

    static UpdateResultAccess updateResultAccess() {
        initialize(UpdateResult.class);
        return required(UPDATE_RESULT.get(), "UpdateResult");
    }

    public static void setFailureAccess(FailureAccess access) {
        install(FAILURE, access, SomaOperationException.class);
    }

    static FailureAccess failureAccess() {
        initialize(SomaOperationException.class);
        return required(FAILURE.get(), "SomaOperationException");
    }

    private static <T> void install(
            AtomicReference<T> destination,
            T access,
            Class<?> owner) {
        if (access == null || access.getClass().getEnclosingClass() != owner) {
            throw new SecurityException("invalid SOMA shared-secret owner");
        }
        if (!destination.compareAndSet(null, access)) {
            throw new IllegalStateException("SOMA shared secret already installed");
        }
    }

    private static void initialize(Class<?> owner) {
        try {
            Class.forName(owner.getName(), true, owner.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("SOMA public carrier is unavailable", exception);
        }
    }

    private static <T> T required(T access, String owner) {
        if (access == null) {
            throw new AssertionError(owner + " shared secret was not installed");
        }
        return access;
    }

    public interface ConfigurationAccess {
        boolean hasMemoryBudget(SomaConfiguration configuration);

        long memoryBudgetBytes(SomaConfiguration configuration);
    }

    public interface UpdateResultAccess {
        UpdateResult create(long matched, long changed);
    }

    public interface FailureAccess {
        SomaOperationException create(
                SomaFailureCode code,
                SomaOperation operation,
                String context,
                Throwable cause,
                Object provenance);

        boolean owns(SomaOperationException failure, Object provenance);
    }
}
