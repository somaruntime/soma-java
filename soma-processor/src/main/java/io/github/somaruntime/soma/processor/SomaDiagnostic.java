package io.github.somaruntime.soma.processor;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

final class SomaDiagnostic {
    private SomaDiagnostic() {
    }

    static void error(Messager messager, String code, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, prefix(code, message));
    }

    static void error(
            Messager messager,
            String code,
            String message,
            Element element) {
        messager.printMessage(Diagnostic.Kind.ERROR, prefix(code, message), element);
    }

    private static String prefix(String code, String message) {
        return "[SOMA-" + code + "] " + message;
    }
}
