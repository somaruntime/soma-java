package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicReference;

/** Internal entry used only by generated composition roots. */
public final class GeneratedRuntime {

    private static final AtomicReference<Environment> ENVIRONMENT =
            new AtomicReference<Environment>();
    private static final Object GROUP_FACTORY_ACCESS = new GroupFactoryAccess();

    private GeneratedRuntime() {
    }

    public static <A, B> io.github.somaruntime.soma.SomaTuple2<A, B> tuple(
            A first,
            B second) {
        return SomaSharedSecrets.tuple2Access().create(first, second);
    }

    public static void configure(
            MethodHandles.Lookup caller,
            Object capability,
            SomaConfiguration configuration) {
        if (configuration == null) {
            throw SomaFailures.invalid(SomaOperation.CONFIGURE, "configuration is null");
        }
        requireGeneratedSoma(caller, capability);
        SomaSharedSecrets.ConfigurationAccess access =
                SomaSharedSecrets.configurationAccess();
        long budget = access.hasMemoryBudget(configuration)
                ? access.memoryBudgetBytes(configuration)
                : automaticBudget();
        RuntimeConfigurationState.Snapshot candidate =
                new RuntimeConfigurationState.Snapshot(
                        budget,
                        access.hasMemoryBudget(configuration)
                                ? "application-v1"
                                : "automatic-v1");
        RuntimeConfigurationState.Snapshot configured;
        try {
            configured = RuntimeConfigurationOwner.configure(candidate);
        } catch (IllegalStateException exception) {
            throw SomaFailures.failure(
                    SomaFailureCode.CONFIGURATION_FROZEN,
                    SomaOperation.CONFIGURE,
                    "SOMA runtime configuration is already frozen",
                    new Object());
        }
        environment(configured);
    }

    public static void freezeConfiguration(
            MethodHandles.Lookup caller,
            Object capability) {
        requireGeneratedSoma(caller, capability);
        environment(RuntimeConfigurationOwner.freezeDefault(
                () -> new RuntimeConfigurationState.Snapshot(
                        automaticBudget(), "automatic-v1")));
    }

    public static GeneratedGroup createGroup(
            MethodHandles.Lookup caller,
            Object capability) {
        String generatedPackage = requireGeneratedSoma(caller, capability);
        RuntimeConfigurationState.Snapshot snapshot =
                RuntimeConfigurationOwner.freezeDefault(
                        () -> new RuntimeConfigurationState.Snapshot(
                                automaticBudget(), "automatic-v1"));
        return GeneratedGroup.create(
                GROUP_FACTORY_ACCESS,
                environment(snapshot).memoryManager,
                generatedPackage,
                capability);
    }

    static boolean acceptsGroupFactoryAccess(Object candidate) {
        return candidate == GROUP_FACTORY_ACCESS;
    }

    private static Environment environment(
            RuntimeConfigurationState.Snapshot snapshot) {
        Environment existing = ENVIRONMENT.get();
        if (existing != null) {
            return existing;
        }
        Environment candidate = new Environment(
                new GlobalMemoryManager(snapshot.memoryBudgetBytes()));
        if (ENVIRONMENT.compareAndSet(null, candidate)) {
            return candidate;
        }
        return ENVIRONMENT.get();
    }

    private static long automaticBudget() {
        long maximum = Runtime.getRuntime().maxMemory();
        if (maximum <= 1L) {
            return 1L;
        }
        return Math.max(1L, maximum / 2L);
    }

    private static String requireGeneratedSoma(
            MethodHandles.Lookup caller,
            Object capability) {
        if (caller == null
                || capability == null
                || (caller.lookupModes() & MethodHandles.Lookup.PRIVATE) == 0) {
            throw invalidConstruction();
        }
        Class<?> owner = caller.lookupClass();
        String ownerName = owner.getName();
        if (!ownerName.endsWith(".Soma")) {
            throw invalidConstruction();
        }
        return ownerName.substring(0, ownerName.length() - ".Soma".length());
    }

    private static RuntimeException invalidConstruction() {
        return SomaFailures.invalid(
                SomaOperation.CONFIGURE,
                "generated composition construction capability is invalid");
    }

    private static final class Environment {

        private final GlobalMemoryManager memoryManager;

        private Environment(GlobalMemoryManager memoryManager) {
            this.memoryManager = memoryManager;
        }
    }

    private static final class GroupFactoryAccess {
    }
}
