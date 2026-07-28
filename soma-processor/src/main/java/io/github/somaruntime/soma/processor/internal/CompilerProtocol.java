package io.github.somaruntime.soma.processor.internal;

import com.sun.tools.javac.util.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * javac 8 plugin/processor 同一 compilation context 的内部握手协议。
 */
public final class CompilerProtocol {
    public static final String LOWERING_IDENTITY = "soma-value-javac8-v1";
    public static final String PROCESSOR_IDENTITY = "soma-processor-v1";

    private static final Context.Key<Session> SESSION_KEY = new Context.Key<Session>();

    private CompilerProtocol() {
    }

    public static Session instance(Context context) {
        Session session = context.get(SESSION_KEY);
        if (session == null) {
            session = new Session();
            context.put(SESSION_KEY, session);
        }
        return session;
    }

    public static final class Session {
        private String pluginIdentity;
        private String processorIdentity;
        private final List<String> pluginDiagnostics = new ArrayList<String>();

        public String getPluginIdentity() {
            return pluginIdentity;
        }

        public void activatePlugin(String identity) {
            pluginIdentity = identity;
        }

        public String getProcessorIdentity() {
            return processorIdentity;
        }

        public void activateProcessor(String identity) {
            processorIdentity = identity;
        }

        public void reportPluginDiagnostic(String diagnostic) {
            pluginDiagnostics.add(diagnostic);
        }

        public List<String> getPluginDiagnostics() {
            return Collections.unmodifiableList(pluginDiagnostics);
        }
    }
}
