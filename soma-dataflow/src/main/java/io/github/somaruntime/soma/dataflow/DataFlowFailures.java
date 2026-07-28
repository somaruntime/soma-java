package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.runtime.SomaErrorCategory;
import io.github.somaruntime.soma.runtime.SomaRuntimeException;

import java.util.Map;
import java.util.TreeMap;

final class DataFlowFailures {
    private DataFlowFailures() {
    }

    static SomaRuntimeException invalidInput(String code, String path, String operation) {
        return create(SomaErrorCategory.INVALID_INPUT, code, operation, path, empty(), null);
    }

    static SomaRuntimeException lifecycle(
            String code, String path, String operation, String state) {
        return create(SomaErrorCategory.LIFECYCLE, code, operation, path,
                context("state", state), null);
    }

    static SomaRuntimeException compatibility(
            String code, String path, String operation, String expected, String actual) {
        Map<String, String> context = context("expected", expected);
        context.put("actual", actual);
        return create(SomaErrorCategory.COMPATIBILITY, code, operation, path, context, null);
    }

    static SomaRuntimeException conflict(
            String code, String path, String operation, String detail) {
        return create(SomaErrorCategory.CONFLICT, code, operation, path,
                context("detail", detail), null);
    }

    static SomaRuntimeException resource(
            String code, String path, String operation, String detail) {
        return create(SomaErrorCategory.RESOURCE, code, operation, path,
                context("detail", detail), null);
    }

    static SomaRuntimeException callback(
            String code, String path, String operation, Throwable cause) {
        return create(SomaErrorCategory.CALLBACK, code, operation, path, empty(), cause);
    }

    static SomaRuntimeException internal(
            String code, String path, String operation, String detail) {
        return create(SomaErrorCategory.INTERNAL, code, operation, path,
                context("detail", detail), null);
    }

    private static SomaRuntimeException create(
            SomaErrorCategory category,
            String code,
            String operation,
            String path,
            Map<String, String> context,
            Throwable cause) {
        return SomaRuntimeException.create(
                category, code, operation, path, context, cause);
    }

    private static Map<String, String> empty() {
        return new TreeMap<String, String>();
    }

    private static Map<String, String> context(String name, String value) {
        Map<String, String> result = empty();
        result.put(name, value == null ? "" : value);
        return result;
    }
}
