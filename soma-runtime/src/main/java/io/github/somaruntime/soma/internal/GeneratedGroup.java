package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.GroupMetadata;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ForkJoinPool;

/** Internal runtime state owned by one generated SomaGroup. */
public final class GeneratedGroup {

    private final GlobalMemoryManager memoryManager;
    private final GlobalMemoryManager.GroupToken accountingToken;
    private final ForkJoinPool parallelExecutor;
    private final SomaCompression compression;
    private final String generatedPackage;
    private final Object capability;
    private final GroupOperationGuard operationGuard = new GroupOperationGuard();

    private GeneratedGroup(
            GlobalMemoryManager memoryManager,
            ForkJoinPool parallelExecutor,
            SomaCompression compression,
            String generatedPackage,
            Object capability) {
        this.memoryManager = memoryManager;
        this.accountingToken = memoryManager.newGroupToken();
        this.parallelExecutor = parallelExecutor;
        this.compression = compression;
        this.generatedPackage = generatedPackage;
        this.capability = capability;
    }

    private GeneratedGroup(
            GlobalMemoryManager memoryManager,
            String generatedPackage,
            Object capability) {
        this(memoryManager, ForkJoinPool.commonPool(), SomaCompression.AUTO,
                generatedPackage, capability);
    }

    private GeneratedGroup(
            GlobalMemoryManager memoryManager,
            ForkJoinPool parallelExecutor,
            String generatedPackage,
            Object capability) {
        this(memoryManager, parallelExecutor, SomaCompression.AUTO,
                generatedPackage, capability);
    }

    static GeneratedGroup create(
            Object factoryAccess,
            GlobalMemoryManager memoryManager,
            String generatedPackage,
            Object capability) {
        return create(
                factoryAccess,
                memoryManager,
                ForkJoinPool.commonPool(),
                SomaCompression.AUTO,
                generatedPackage,
                capability);
    }

    static GeneratedGroup create(
            Object factoryAccess,
            GlobalMemoryManager memoryManager,
            ForkJoinPool parallelExecutor,
            String generatedPackage,
            Object capability) {
        return create(
                factoryAccess,
                memoryManager,
                parallelExecutor,
                SomaCompression.AUTO,
                generatedPackage,
                capability);
    }

    static GeneratedGroup create(
            Object factoryAccess,
            GlobalMemoryManager memoryManager,
            ForkJoinPool parallelExecutor,
            SomaCompression compression,
            String generatedPackage,
            Object capability) {
        if (!GeneratedRuntime.acceptsGroupFactoryAccess(factoryAccess)
                || memoryManager == null
                || parallelExecutor == null
                || compression == null
                || generatedPackage == null
                || generatedPackage.isEmpty()
                || capability == null) {
            throw SomaFailures.invalid(
                    SomaOperation.CONFIGURE,
                    "generated Group construction capability is invalid");
        }
        GeneratedGroup group = new GeneratedGroup(
                memoryManager, parallelExecutor, compression, generatedPackage, capability);
        memoryManager.registerGroup(group, group.accountingToken);
        return group;
    }

    public GeneratedTable createTable(
            MethodHandles.Lookup caller,
            Object candidateCapability,
            String logicalName,
            GeneratedTableLayout layout) {
        if (caller == null
                || candidateCapability != capability
                || logicalName == null
                || layout == null
                || (caller.lookupModes() & MethodHandles.Lookup.PRIVATE) == 0
                || !caller.lookupClass().getName().equals(
                        generatedPackage + "." + logicalName + "Table")) {
            throw SomaFailures.invalid(
                    SomaOperation.CONFIGURE,
                    "generated Table construction capability is invalid");
        }
        return new GeneratedTable(this, layout);
    }

    public void requireCapability(Object candidateCapability) {
        if (candidateCapability != capability) {
            throw SomaFailures.invalid(
                    SomaOperation.CONFIGURE,
                    "generated Group capability is invalid");
        }
    }

    GroupOperationGuard.Lease acquire(SomaOperation operation) {
        return operationGuard.acquire(operation);
    }

    GlobalMemoryManager memoryManager() {
        return memoryManager;
    }

    ForkJoinPool parallelExecutor() {
        return parallelExecutor;
    }

    SomaCompression compression() {
        return compression;
    }

    public GroupMetadata metadata(boolean defaultGroup) {
        return SomaSharedSecrets.groupMetadataAccess().create(
                defaultGroup,
                memoryManager.retainedBytes(accountingToken),
                memoryManager.retainedBytes(),
                memoryManager.temporaryBytes(),
                memoryManager.budgetBytes(),
                compression);
    }

    GlobalMemoryManager.RetainedReservation reserveRetained(
            long bytes,
            SomaOperation operation,
            Object provenance) {
        return memoryManager.reserveRetained(
                accountingToken, bytes, operation, provenance);
    }

    GlobalMemoryManager.TemporaryLease leaseTemporary(
            long bytes,
            SomaOperation operation,
            Object provenance) {
        return memoryManager.leaseTemporary(bytes, operation, provenance);
    }

    void releasePublished(long bytes) {
        memoryManager.releasePublished(accountingToken, bytes);
    }
}
