package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.SomaCompression;
import java.util.concurrent.ForkJoinPool;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.UpdateResult;
import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.SomaLongSummary;
import io.github.somaruntime.soma.SomaDoubleSummary;
import io.github.somaruntime.soma.SomaTuple2;
import io.github.somaruntime.soma.SomaMetadata;
import io.github.somaruntime.soma.GroupMetadata;
import io.github.somaruntime.soma.TableMetadata;
import io.github.somaruntime.soma.FieldMetadata;
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
    private static final AtomicReference<RemoveResultAccess> REMOVE_RESULT =
            new AtomicReference<RemoveResultAccess>();
    private static final AtomicReference<FailureAccess> FAILURE =
            new AtomicReference<FailureAccess>();
    private static final AtomicReference<LongSummaryAccess> LONG_SUMMARY =
            new AtomicReference<LongSummaryAccess>();
    private static final AtomicReference<DoubleSummaryAccess> DOUBLE_SUMMARY =
            new AtomicReference<DoubleSummaryAccess>();
    private static final AtomicReference<Tuple2Access> TUPLE2 =
            new AtomicReference<Tuple2Access>();
    private static final AtomicReference<SomaMetadataAccess> SOMA_METADATA =
            new AtomicReference<SomaMetadataAccess>();
    private static final AtomicReference<GroupMetadataAccess> GROUP_METADATA =
            new AtomicReference<GroupMetadataAccess>();
    private static final AtomicReference<TableMetadataAccess> TABLE_METADATA =
            new AtomicReference<TableMetadataAccess>();
    private static final AtomicReference<FieldMetadataAccess> FIELD_METADATA =
            new AtomicReference<FieldMetadataAccess>();

    private SomaSharedSecrets() {
    }

    public static void setConfigurationAccess(ConfigurationAccess access) {
        install(CONFIGURATION, access, SomaConfiguration.class);
    }

    static ConfigurationAccess configurationAccess() {
        ConfigurationAccess access = CONFIGURATION.get();
        if (access == null) {
            initialize(SomaConfiguration.class);
            access = CONFIGURATION.get();
        }
        return required(access, "SomaConfiguration");
    }

    public static void setUpdateResultAccess(UpdateResultAccess access) {
        install(UPDATE_RESULT, access, UpdateResult.class);
    }

    static UpdateResultAccess updateResultAccess() {
        UpdateResultAccess access = UPDATE_RESULT.get();
        if (access == null) {
            initialize(UpdateResult.class);
            access = UPDATE_RESULT.get();
        }
        return required(access, "UpdateResult");
    }

    public static void setRemoveResultAccess(RemoveResultAccess access) {
        install(REMOVE_RESULT, access, RemoveResult.class);
    }

    static RemoveResultAccess removeResultAccess() {
        RemoveResultAccess access = REMOVE_RESULT.get();
        if (access == null) {
            initialize(RemoveResult.class);
            access = REMOVE_RESULT.get();
        }
        return required(access, "RemoveResult");
    }

    public static void setFailureAccess(FailureAccess access) {
        install(FAILURE, access, SomaOperationException.class);
    }

    static FailureAccess failureAccess() {
        FailureAccess access = FAILURE.get();
        if (access == null) {
            initialize(SomaOperationException.class);
            access = FAILURE.get();
        }
        return required(access, "SomaOperationException");
    }

    public static void setLongSummaryAccess(LongSummaryAccess access) {
        install(LONG_SUMMARY, access, SomaLongSummary.class);
    }

    static LongSummaryAccess longSummaryAccess() {
        LongSummaryAccess access = LONG_SUMMARY.get();
        if (access == null) {
            initialize(SomaLongSummary.class);
            access = LONG_SUMMARY.get();
        }
        return required(access, "SomaLongSummary");
    }

    public static void setDoubleSummaryAccess(DoubleSummaryAccess access) {
        install(DOUBLE_SUMMARY, access, SomaDoubleSummary.class);
    }

    static DoubleSummaryAccess doubleSummaryAccess() {
        DoubleSummaryAccess access = DOUBLE_SUMMARY.get();
        if (access == null) {
            initialize(SomaDoubleSummary.class);
            access = DOUBLE_SUMMARY.get();
        }
        return required(access, "SomaDoubleSummary");
    }

    public static void setTuple2Access(Tuple2Access access) {
        install(TUPLE2, access, SomaTuple2.class);
    }

    static Tuple2Access tuple2Access() {
        Tuple2Access access = TUPLE2.get();
        if (access == null) {
            initialize(SomaTuple2.class);
            access = TUPLE2.get();
        }
        return required(access, "SomaTuple2");
    }

    public static void setSomaMetadataAccess(SomaMetadataAccess access) {
        install(SOMA_METADATA, access, SomaMetadata.class);
    }

    static SomaMetadataAccess somaMetadataAccess() {
        SomaMetadataAccess access = SOMA_METADATA.get();
        if (access == null) {
            initialize(SomaMetadata.class);
            access = SOMA_METADATA.get();
        }
        return required(access, "SomaMetadata");
    }

    public static void setGroupMetadataAccess(GroupMetadataAccess access) {
        install(GROUP_METADATA, access, GroupMetadata.class);
    }

    static GroupMetadataAccess groupMetadataAccess() {
        GroupMetadataAccess access = GROUP_METADATA.get();
        if (access == null) {
            initialize(GroupMetadata.class);
            access = GROUP_METADATA.get();
        }
        return required(access, "GroupMetadata");
    }

    public static void setTableMetadataAccess(TableMetadataAccess access) {
        install(TABLE_METADATA, access, TableMetadata.class);
    }

    static TableMetadataAccess tableMetadataAccess() {
        TableMetadataAccess access = TABLE_METADATA.get();
        if (access == null) {
            initialize(TableMetadata.class);
            access = TABLE_METADATA.get();
        }
        return required(access, "TableMetadata");
    }

    public static void setFieldMetadataAccess(FieldMetadataAccess access) {
        install(FIELD_METADATA, access, FieldMetadata.class);
    }

    static FieldMetadataAccess fieldMetadataAccess() {
        FieldMetadataAccess access = FIELD_METADATA.get();
        if (access == null) {
            initialize(FieldMetadata.class);
            access = FIELD_METADATA.get();
        }
        return required(access, "FieldMetadata");
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

        ForkJoinPool parallelExecutor(SomaConfiguration configuration);

        SomaCompression compression(SomaConfiguration configuration);
    }

    public interface UpdateResultAccess {
        UpdateResult create(int matched, int changed);
    }

    public interface RemoveResultAccess {
        RemoveResult create(int removed);
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

    /** Internal zero-allocation operation identity used only by failure provenance. */
    public interface OperationProvenance {
        Object owner();

        long generation();
    }

    public interface LongSummaryAccess {
        SomaLongSummary create(long count, long min, long max, long sum, double average);
    }

    public interface DoubleSummaryAccess {
        SomaDoubleSummary create(
                long count, double min, double max, double sum, double average);
    }

    public interface Tuple2Access {
        <A, B> SomaTuple2<A, B> create(A first, B second);
    }

    public interface SomaMetadataAccess {
        SomaMetadata create(
                String composition,
                boolean frozen,
                long effectiveBudget,
                SomaCompression compression,
                long retained,
                long temporary);
    }

    public interface GroupMetadataAccess {
        GroupMetadata create(
                boolean defaultGroup,
                long retained,
                long globalRetained,
                long globalTemporary,
                long effectiveBudget,
                SomaCompression compression);
    }

    public interface TableMetadataAccess {
        TableMetadata create(
                String logicalName,
                int size,
                int capacity,
                long managed,
                long plainEquivalent,
                long representation,
                long encodedChunks);
    }

    public interface FieldMetadataAccess {
        FieldMetadata create(
                String path,
                String type,
                boolean nullable,
                boolean key,
                boolean indexed,
                boolean equality,
                boolean ordered,
                long plainEquivalent,
                long representation,
                boolean encoded);
    }
}
