package io.github.somaruntime.soma.processor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

final class ProcessorContract {
    static final String SOMA_SCHEMA = "io.github.somaruntime.soma.SomaSchema";
    static final String SOMA_TABLE = "io.github.somaruntime.soma.SomaTable";
    static final String SOMA_VALUE = "io.github.somaruntime.soma.SomaValue";
    static final String SOMA_FIELD = "io.github.somaruntime.soma.SomaField";
    static final String SOMA_KEY = "io.github.somaruntime.soma.SomaKey";
    static final String SOMA_INDEX = "io.github.somaruntime.soma.SomaIndex";
    static final Set<String> SUPPORTED_ANNOTATIONS;
    static final String ARTIFACT_VERSION = "1.0.0-SNAPSHOT";
    static final String RUNTIME_CONTRACT_VERSION = "1";
    static final String FULL_SOURCE_SET_OPTION = "soma.fullSourceSet";
    static final String RUNTIME_CONTRACT_TYPE =
            "io.github.somaruntime.soma.internal.SomaRuntimeContract";
    static final String GENERATED_CARRIER_SIMPLE_NAME = "SomaGeneratedComposition";
    static final String MANIFEST_PATH = "META-INF/soma/composition-manifest.properties";

    static {
        Set<String> annotations = new LinkedHashSet<String>();
        annotations.add(SOMA_SCHEMA);
        annotations.add(SOMA_TABLE);
        annotations.add(SOMA_VALUE);
        annotations.add(SOMA_FIELD);
        annotations.add(SOMA_KEY);
        annotations.add(SOMA_INDEX);
        SUPPORTED_ANNOTATIONS = Collections.unmodifiableSet(annotations);
    }

    private ProcessorContract() {
    }
}
