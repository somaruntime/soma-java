package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaOperationException;

final class SomaFailures {

    private SomaFailures() {
    }

    static SomaOperationException failure(
            SomaFailureCode code,
            SomaOperation operation,
            String context,
            Object provenance) {
        return create(code, operation, context, null, provenance);
    }

    static SomaOperationException failure(
            SomaFailureCode code,
            SomaOperation operation,
            String context,
            Throwable cause,
            Object provenance) {
        return create(code, operation, context, cause, provenance);
    }

    static SomaOperationException invalid(
            SomaOperation operation,
            String context) {
        return failure(
                SomaFailureCode.INVALID_ARGUMENT,
                operation,
                context,
                new Object());
    }

    static SomaOperationException callbackFailure(
            SomaOperation operation,
            Throwable cause,
            Object operationProvenance) {
        if (cause instanceof SomaOperationException
                && owns((SomaOperationException) cause, operationProvenance)) {
            return (SomaOperationException) cause;
        }
        return failure(
                SomaFailureCode.CALLBACK_FAILED,
                operation,
                "application callback failed",
                cause,
                operationProvenance);
    }

    static boolean owns(
            SomaOperationException failure,
            Object operationProvenance) {
        return SomaSharedSecrets.failureAccess().owns(failure, operationProvenance);
    }

    private static SomaOperationException create(
            SomaFailureCode code,
            SomaOperation operation,
            String context,
            Throwable cause,
            Object provenance) {
        if (code == null || operation == null || context == null || provenance == null) {
            throw new AssertionError("invalid trusted SOMA failure");
        }
        return SomaSharedSecrets.failureAccess().create(
                code, operation, context, cause, provenance);
    }
}
