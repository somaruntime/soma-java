package io.github.somaruntime.soma.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
final class LateTableProcessor extends AbstractProcessor {

    private boolean generated;

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnvironment) {
        if (generated || roundEnvironment.processingOver()) {
            return false;
        }
        generated = true;
        try {
            JavaFileObject source = processingEnv.getFiler().createSourceFile(
                    "example.late.schema.GeneratedTable");
            try (Writer writer = source.openWriter()) {
                writer.write("package example.late.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class GeneratedTable {\n"
                        + "  @SomaField long generatedValue;\n"
                        + "}\n");
            }
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
        return false;
    }
}
