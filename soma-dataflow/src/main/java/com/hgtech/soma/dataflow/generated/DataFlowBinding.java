package com.hgtech.soma.dataflow.generated;

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

    void acquire(String operation);

    void release(
            String operation,
            boolean success,
            long scanned,
            long matched,
            String failureCode);
}
