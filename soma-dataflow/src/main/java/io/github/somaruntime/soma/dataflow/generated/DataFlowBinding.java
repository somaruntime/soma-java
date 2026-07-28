package io.github.somaruntime.soma.dataflow.generated;

import io.github.somaruntime.soma.runtime.IndexSnapshot;

/**
 * Narrow generated-to-dataflow protocol.
 *
 * <p>This is not an application SPI. Implementations are schema-specific generated
 * companions and must not expose backing arrays.</p>
 */
public interface DataFlowBinding {
    long aggregateInstanceId();

    Object physicalIdentity();

    String schemaIdentity();

    String tableIdentity();

    String generatedProtocol();

    String transformationProtocol();

    String kernelProtocol();

    long structuralEpoch();

    int packedSize();

    default boolean segmentedStorage() {
        return false;
    }

    default int flatHeadRows() {
        return 0;
    }

    default int segmentRows() {
        return 0;
    }

    boolean isPresent(int columnOrdinal, int index);

    boolean booleanValue(int columnOrdinal, int index);

    long longValue(int columnOrdinal, int index);

    double doubleValue(int columnOrdinal, int index);

    String stringValue(int columnOrdinal, int index);

    IndexSnapshot indexSnapshot(int[] indexes, int length);

    void acquire(String operation);

    void release(
            String operation,
            boolean success,
            long scanned,
            long matched,
            String failureCode);
}
