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
                        return failure.provenance == provenance;
                    }
                });
    }

    private final SomaFailureCode code;
    private final SomaOperation operation;
    private final String context;
    private final transient Object provenance;

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
        this.provenance = provenance;
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
}
