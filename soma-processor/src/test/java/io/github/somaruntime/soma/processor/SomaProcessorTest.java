package io.github.somaruntime.soma.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class SomaProcessorTest {

    @Test
    void validCompositionProducesDeterministicCarrierAndManifest() throws Exception {
        Map<String, String> firstOrder = validSources(false);
        Map<String, String> reverseOrder = validSources(true);
        try (CompilerTestSupport.Compilation first = CompilerTestSupport.compile(
                     firstOrder, true, new SomaProcessor());
             CompilerTestSupport.Compilation second = CompilerTestSupport.compile(
                     reverseOrder, true, new SomaProcessor())) {
            assertTrue(first.success(), first.diagnostics().toString());
            assertTrue(second.success(), second.diagnostics().toString());

            String firstSource = first.generatedSource(
                    "example/order/SomaCompositionLinkage.java");
            String secondSource = second.generatedSource(
                    "example/order/SomaCompositionLinkage.java");
            assertEquals(firstSource, secondSource);
            assertFalse(firstSource.contains("public class SomaCompositionLinkage"));
            assertTrue(firstSource.contains("final class SomaCompositionLinkage"));

            String firstManifest = first.classOutput(
                    "META-INF/soma/example.order.schema.properties");
            String secondManifest = second.classOutput(
                    "META-INF/soma/example.order.schema.properties");
            assertEquals(firstManifest, secondManifest);
            assertTrue(firstManifest.contains("schema.package=example.order.schema"));
            assertTrue(firstManifest.contains("generated.files=example.order.SomaCompositionLinkage"));
            assertTrue(firstManifest.contains("table=example.order.schema.Alpha"));
            assertTrue(firstManifest.contains("table=example.order.schema.Beta"));
            assertTrue(firstManifest.contains("value=example.order.schema.OrderCode"));
            assertFalse(firstManifest.contains("value=example.order.schema.OrphanValue"));
        }
    }

    @Test
    void missingFullSourceSetHandshakeFailsWithStableDiagnostic() throws Exception {
        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                validSources(false), false, new SomaProcessor())) {
            assertFalse(compilation.success());
            assertDiagnostic(compilation, "[SOMA-1001]");
            assertNoPublishedComposition(compilation, "example/order");
        }
    }

    @Test
    void emptyCompositionAndInvalidTableFailWithoutCarrier() throws Exception {
        Map<String, String> empty = new LinkedHashMap<String, String>();
        empty.put("example/empty/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package example.empty.schema;\n");
        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                empty, true, new SomaProcessor())) {
            assertFalse(compilation.success());
            assertDiagnostic(compilation, "[SOMA-1003]");
            assertNoPublishedComposition(compilation, "example/empty");
        }

        Map<String, String> invalid = new LinkedHashMap<String, String>();
        invalid.put("example/invalid/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package example.invalid.schema;\n");
        invalid.put("example/invalid/schema/PublicTable.java",
                "package example.invalid.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable public final class PublicTable {\n"
                        + "  @SomaField long value;\n"
                        + "}\n");
        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                invalid, true, new SomaProcessor())) {
            assertFalse(compilation.success());
            assertDiagnostic(compilation, "[SOMA-1004]");
            assertNoPublishedComposition(compilation, "example/invalid");
        }
    }

    @Test
    void cleanFullRegenerationDropsDeletedAndRenamedMembers() throws Exception {
        Map<String, String> renamed = validSources(false);
        renamed.remove("example/order/schema/Alpha.java");
        renamed.put("example/order/schema/Gamma.java",
                "package example.order.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class Gamma {\n"
                        + "  @SomaKey long id;\n"
                        + "  @SomaField long value;\n"
                        + "}\n");

        try (CompilerTestSupport.Compilation before = CompilerTestSupport.compile(
                     validSources(false), true, new SomaProcessor());
             CompilerTestSupport.Compilation after = CompilerTestSupport.compile(
                     renamed, true, new SomaProcessor())) {
            assertTrue(before.success(), before.diagnostics().toString());
            assertTrue(after.success(), after.diagnostics().toString());

            String beforeManifest = before.classOutput(
                    "META-INF/soma/example.order.schema.properties");
            String afterManifest = after.classOutput(
                    "META-INF/soma/example.order.schema.properties");
            assertTrue(beforeManifest.contains("table=example.order.schema.Alpha"));
            assertFalse(afterManifest.contains("table=example.order.schema.Alpha"));
            assertTrue(afterManifest.contains("table=example.order.schema.Gamma"));
            assertFalse(beforeManifest.equals(afterManifest));

            String beforeSource = before.generatedSource(
                    "example/order/SomaCompositionLinkage.java");
            String afterSource = after.generatedSource(
                    "example/order/SomaCompositionLinkage.java");
            assertFalse(beforeSource.equals(afterSource));
        }
    }

    @Test
    void generatedCarrierNameCollisionFailsWithoutManifest() throws Exception {
        Map<String, String> sources = validSources(false);
        sources.put("example/order/SomaCompositionLinkage.java",
                "package example.order;\n"
                        + "final class SomaCompositionLinkage { }\n");
        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                sources, true, new SomaProcessor())) {
            assertFalse(compilation.success());
            assertDiagnostic(compilation, "[SOMA-1007]");
            assertFalse(compilation.classOutputExists(
                    "META-INF/soma/example.order.schema.properties"));
        }
    }

    @Test
    void lateRoundTableIsPartOfTheSingleCompositionManifest() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("example/late/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package example.late.schema;\n");
        sources.put("example/late/schema/InitialTable.java",
                "package example.late.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class InitialTable {\n"
                        + "  @SomaField long initialValue;\n"
                        + "}\n");
        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                sources, true, new LateTableProcessor(), new SomaProcessor())) {
            assertTrue(compilation.success(), compilation.diagnostics().toString());
            String manifest = compilation.classOutput(
                    "META-INF/soma/example.late.schema.properties");
            assertTrue(manifest.contains("table=example.late.schema.InitialTable"));
            assertTrue(manifest.contains("table=example.late.schema.GeneratedTable"));
        }
    }

    @Test
    void manifestUsesJava8PropertiesEscapingForUnicodeSchemaIdentity() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("example/订单/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package example.订单.schema;\n");
        sources.put("example/订单/schema/订单.java",
                "package example.订单.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class 订单 {\n"
                        + "  @SomaKey long 编号;\n"
                        + "}\n");
        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                sources, true, new SomaProcessor())) {
            assertTrue(compilation.success(), compilation.diagnostics().toString());
            String manifest = compilation.classOutput(
                    "META-INF/soma/example.订单.schema.properties");
            assertTrue(manifest.contains("\\u8ba2\\u5355"));

            Properties properties = new Properties();
            properties.load(new ByteArrayInputStream(
                    manifest.getBytes(StandardCharsets.ISO_8859_1)));
            assertEquals("example.订单.schema", properties.getProperty("schema.package"));
            assertTrue(properties.values().stream()
                    .map(String::valueOf)
                    .anyMatch(value -> value.contains("example.订单.schema.订单")));
        }
    }

    private static Map<String, String> validSources(boolean reverse) {
        LinkedHashMap<String, String> sources = new LinkedHashMap<String, String>();
        String packageInfo = "@io.github.somaruntime.soma.SomaSchema\n"
                + "package example.order.schema;\n";
        String alpha = "package example.order.schema;\n"
                + "import io.github.somaruntime.soma.*;\n"
                + "@SomaTable final class Alpha {\n"
                + "  @SomaKey long id;\n"
                + "  @SomaField long value;\n"
                + "}\n";
        String beta = "package example.order.schema;\n"
                + "import io.github.somaruntime.soma.*;\n"
                + "@SomaTable(defaultCapacity = 32L) final class Beta {\n"
                + "  @SomaIndex String name;\n"
                + "  @SomaField OrderCode code;\n"
                + "}\n";
        String orderCode = "package example.order.schema;\n"
                + "import io.github.somaruntime.soma.*;\n"
                + "@SomaValue final class OrderCode {\n"
                + "  @SomaField int value;\n"
                + "}\n";
        String orphanValue = "package example.order.schema;\n"
                + "import io.github.somaruntime.soma.*;\n"
                + "@SomaValue final class OrphanValue {\n"
                + "  @SomaField long ignored;\n"
                + "}\n";
        sources.put("example/order/schema/package-info.java", packageInfo);
        if (reverse) {
            sources.put("example/order/schema/OrphanValue.java", orphanValue);
            sources.put("example/order/schema/OrderCode.java", orderCode);
            sources.put("example/order/schema/Beta.java", beta);
            sources.put("example/order/schema/Alpha.java", alpha);
        } else {
            sources.put("example/order/schema/Alpha.java", alpha);
            sources.put("example/order/schema/Beta.java", beta);
            sources.put("example/order/schema/OrderCode.java", orderCode);
            sources.put("example/order/schema/OrphanValue.java", orphanValue);
        }
        return sources;
    }

    private static void assertDiagnostic(
            CompilerTestSupport.Compilation compilation,
            String code) throws IOException {
        assertTrue(compilation.diagnostics().stream().anyMatch(message -> message.contains(code)),
                compilation.diagnostics().toString());
    }

    private static void assertNoPublishedComposition(
            CompilerTestSupport.Compilation compilation,
            String generatedPackagePath) {
        assertFalse(compilation.generatedSourceExists(
                generatedPackagePath + "/SomaCompositionLinkage.java"));
        assertFalse(compilation.classOutputExists(
                "META-INF/soma/" + generatedPackagePath.replace('/', '.')
                        + ".schema.properties"));
    }
}
