package io.github.somaruntime.soma;

import io.github.somaruntime.soma.internal.SomaSharedSecrets;

/** Stable structured failure raised by the SOMA runtime. */
public final class SomaOperationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    static {
        SomaSharedSecrets.setFailureAccess(
                new SomaSharedSecrets.FailureAccess() {
                    @Override
                    public SomaOperationException create(
                            SomaFailureCode code,
                            SomaOperation operation,
                            String context,
                            Throwable cause,
                            Object provenance) {
                        return SomaOperationException.createTrusted(
                                code, operation, context, cause, provenance);
                    }

                    @Override
                    public boolean owns(
                            SomaOperationException failure,
                            Object provenance) {
                        return failure.owns(provenance);
                    }
                });
    }

    private final SomaFailureCode code;
    private final SomaOperation operation;
    private final String context;
    private final transient Object provenance;
    private final long provenanceGeneration;

    private SomaOperationException(
            SomaFailureCode code,
            SomaOperation operation,
            String context,
            Throwable cause,
            Object provenance) {
        super(message(code, operation, context), cause);
        this.code = code;
        this.operation = operation;
        this.context = context;
        if (provenance instanceof SomaSharedSecrets.OperationProvenance) {
            SomaSharedSecrets.OperationProvenance operationProvenance =
                    (SomaSharedSecrets.OperationProvenance) provenance;
            this.provenance = operationProvenance.owner();
            this.provenanceGeneration = operationProvenance.generation();
        } else {
            this.provenance = provenance;
            this.provenanceGeneration = 0L;
        }
    }

    public SomaFailureCode code() {
        return code;
    }

    public SomaOperation operation() {
        return operation;
    }

    public String context() {
        return context;
    }

    @Override
    public synchronized Throwable getCause() {
        return super.getCause();
    }

    private static String message(
            SomaFailureCode code,
            SomaOperation operation,
            String context) {
        return "[SOMA] " + code + " during " + operation + ": " + context;
    }

    private static SomaOperationException createTrusted(
            SomaFailureCode code,
            SomaOperation operation,
            String context,
            Throwable cause,
            Object provenance) {
        return new SomaOperationException(code, operation, context, cause, provenance);
    }

    private boolean owns(Object candidate) {
        if (candidate instanceof SomaSharedSecrets.OperationProvenance) {
            SomaSharedSecrets.OperationProvenance operationProvenance =
                    (SomaSharedSecrets.OperationProvenance) candidate;
            return provenance == operationProvenance.owner()
                    && provenanceGeneration == operationProvenance.generation();
        }
        return provenance == candidate && provenanceGeneration == 0L;
    }
}
