package io.github.somaruntime.soma.processor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.lang.model.element.Element;

final class CompositionModel {

    private final String schemaPackage;
    private final String generatedPackage;
    private final String processorVersion;
    private final String contractVersion;
    private final String runtimeBuildIdentity;
    private final String fingerprint;
    private final List<String> schemaLines;
    private final List<Element> originatingElements;

    CompositionModel(
            String schemaPackage,
            String generatedPackage,
            String processorVersion,
            String contractVersion,
            String runtimeBuildIdentity,
            String fingerprint,
            List<String> schemaLines,
            List<? extends Element> originatingElements) {
        this.schemaPackage = schemaPackage;
        this.generatedPackage = generatedPackage;
        this.processorVersion = processorVersion;
        this.contractVersion = contractVersion;
        this.runtimeBuildIdentity = runtimeBuildIdentity;
        this.fingerprint = fingerprint;
        this.schemaLines = Collections.unmodifiableList(new ArrayList<String>(schemaLines));
        this.originatingElements = Collections.unmodifiableList(
                new ArrayList<Element>(originatingElements));
    }

    String schemaPackage() {
        return schemaPackage;
    }

    String generatedPackage() {
        return generatedPackage;
    }

    String processorVersion() {
        return processorVersion;
    }

    String contractVersion() {
        return contractVersion;
    }

    String runtimeBuildIdentity() {
        return runtimeBuildIdentity;
    }

    String fingerprint() {
        return fingerprint;
    }

    List<String> schemaLines() {
        return schemaLines;
    }

    List<Element> originatingElements() {
        return originatingElements;
    }

    String generatedTypeName() {
        return generatedPackage + ".SomaCompositionLinkage";
    }

    String manifestPath() {
        return "META-INF/soma/" + schemaPackage + ".properties";
    }
}
