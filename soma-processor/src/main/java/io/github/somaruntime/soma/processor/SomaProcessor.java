package io.github.somaruntime.soma.processor;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

/** 面向完整 SOMA schema composition 的 JSR 269 aggregating processor。 */
public final class SomaProcessor extends AbstractProcessor {
    private boolean initialRoundObserved;
    private boolean failed;
    private boolean manifestAttempted;
    private GenerationSession session;
    private List<SchemaModel.GeneratedOutput> outputs = Collections.emptyList();

    /** 为 JSR 269 host 创建尚未初始化的 processor instance。 */
    public SomaProcessor() {
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return ProcessorContract.SUPPORTED_ANNOTATIONS;
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Collections.singleton(ProcessorContract.FULL_SOURCE_SET_OPTION);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_8;
    }

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnvironment) {
        if (session == null) {
            session = new GenerationSession(processingEnv);
        }
        boolean claimsSoma = session.hasSomaAnnotation(annotations);
        if (roundEnvironment.processingOver()) {
            if (!manifestAttempted) {
                manifestAttempted = true;
                if (!failed && !session.failed() && !roundEnvironment.errorRaised()) {
                    session.writeManifest(outputs);
                    failed = session.failed();
                }
            }
            return claimsSoma;
        }

        if (!initialRoundObserved) {
            initialRoundObserved = true;
            if (!session.validateFullSourceSetOption()
                    || !session.verifyRuntimeContract()) {
                failed = true;
                return claimsSoma;
            }
            outputs = session.buildAndWrite(roundEnvironment);
            failed = session.failed();
            return claimsSoma;
        }

        if (session.hasLateSomaElement(roundEnvironment)) {
            SomaDiagnostic.error(
                    processingEnv.getMessager(),
                    "0103",
                    "SOMA schema declarations must all be present in the initial full-source round");
            failed = true;
        }
        return claimsSoma;
    }
}
