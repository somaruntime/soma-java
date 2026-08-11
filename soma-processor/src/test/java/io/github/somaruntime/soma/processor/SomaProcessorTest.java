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
    void longKeyedCompositionGeneratesAndCompilesTypedVerticalSlice() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("example/entity/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package example.entity.schema;\n");
        sources.put("example/entity/schema/Entity.java",
                "package example.entity.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable(defaultCapacity = 4) final class Entity {\n"
                        + "  @SomaKey long id;\n"
                        + "  @SomaField long value;\n"
                        + "}\n");
        sources.put("example/entity/Consumer.java",
                "package example.entity;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "final class Consumer {\n"
                        + "  static long use() {\n"
                        + "    EntityTable table = Soma.entityTable();\n"
                        + "    table.add(new Entity(1L, 10L));\n"
                        + "    Entity value = table.get(1L);\n"
                        + "    table.update(1L, editor -> editor.value(11L));\n"
                        + "    return table.filter(table.value.ge(value.value())).count();\n"
                        + "  }\n"
                        + "}\n");

        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                sources, true, new SomaProcessor())) {
            assertTrue(compilation.success(), compilation.diagnostics().toString());
            String soma = compilation.generatedSource("example/entity/Soma.java");
            String group = compilation.generatedSource("example/entity/SomaGroup.java");
            String object = compilation.generatedSource("example/entity/Entity.java");
            String table = compilation.generatedSource("example/entity/EntityTable.java");
            assertTrue(soma.contains("public static EntityTable entityTable()"));
            assertTrue(group.contains("public EntityTable entityTable()"));
            assertTrue(object.contains("public Entity(long id, long value)"));
            assertTrue(table.contains(
                    "implements io.github.somaruntime.soma.SomaKeyableField<"
                            + "View, java.lang.Long>"));
            assertTrue(table.contains(
                    "public io.github.somaruntime.soma.UpdateResult update("));
            assertTrue(table.contains(
                    "public io.github.somaruntime.soma.RemoveResult remove(long key)"));
            assertTrue(table.contains("public Stream parallel()"));
            assertFalse(table.contains("filter(SomaPredicate"));

            String manifest = compilation.classOutput(
                    "META-INF/soma/example.entity.schema.properties");
            assertTrue(manifest.contains("example.entity.Soma"));
            assertTrue(manifest.contains("example.entity.EntityTable"));
        }
    }

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
            assertTrue(firstSource.contains(
                    "static java.lang.String schemaPackage()"));
            assertTrue(firstSource.contains(
                    "private static final java.lang.String SCHEMA_PACKAGE = "
                            + "\"example.order.schema\""));

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
    void multipleKeysFailSchemaValidationBeforeAnyCompositionOutput() throws Exception {
        Map<String, String> sources = i1Sources(
                "example.multikey",
                "Invalid",
                "  @SomaKey long first;\n"
                        + "  @SomaKey long second;\n"
                        + "  @SomaField long value;\n");
        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                sources, true, new SomaProcessor())) {
            assertFalse(compilation.success());
            assertDiagnostic(compilation, "[SOMA-1016]");
            assertNoPublishedComposition(compilation, "example/multikey");
        }
    }

    @Test
    void generatedSymbolCollisionsFailBeforeAnyCompositionOutput() throws Exception {
        Map<String, String> somaType = i1Sources(
                "example.collision.soma",
                "Soma",
                "  @SomaKey long id;\n  @SomaField long value;\n");

        Map<String, String> tableType = i1Sources(
                "example.collision.table",
                "Order",
                "  @SomaKey long id;\n  @SomaField long value;\n");
        tableType.put(
                "example/collision/table/schema/OrderTable.java",
                schemaType(
                        "example.collision.table",
                        "OrderTable",
                        "  @SomaKey long id;\n  @SomaField long value;\n"));

        Map<String, String> endpointType = i1Sources(
                "example.collision.endpoint",
                "Entity",
                "  @SomaKey long id;\n"
                        + "  @SomaField long Id;\n"
                        + "  @SomaField long value;\n");

        Map<String, String> directMember = i1Sources(
                "example.collision.member",
                "Entity",
                "  @SomaKey long id;\n"
                        + "  @SomaField long runtime;\n");

        assertSymbolCollision(somaType, "example/collision/soma");
        assertSymbolCollision(tableType, "example/collision/table");
        assertSymbolCollision(endpointType, "example/collision/endpoint");
        assertSymbolCollision(directMember, "example/collision/member");

        Map<String, String> indexAccessor = i1Sources(
                "example.collision.indexaccessor",
                "Entity",
                "  @SomaIndex long machineId;\n"
                        + "  @SomaField long byMachineId;\n");

        Map<String, String> valueView = new LinkedHashMap<String, String>();
        valueView.put(
                "example/collision/valueview/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package example.collision.valueview.schema;\n");
        valueView.put(
                "example/collision/valueview/schema/BadValue.java",
                "package example.collision.valueview.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaValue final class BadValue { @SomaField long fetch; }\n");
        valueView.put(
                "example/collision/valueview/schema/Entity.java",
                schemaType(
                        "example.collision.valueview",
                        "Entity",
                        "  @SomaField BadValue value;\n"));

        assertSymbolCollision(indexAccessor, "example/collision/indexaccessor");
        assertSymbolCollision(valueView, "example/collision/valueview");
    }

    @Test
    void acronymAndTableSuffixNamesUseOnlyTheFirstUnicodeCodePoint() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("example/mechanical/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package example.mechanical.schema;\n");
        sources.put("example/mechanical/schema/URL.java",
                schemaType(
                        "example.mechanical",
                        "URL",
                        "  @SomaIndex long uRL;\n"));
        sources.put("example/mechanical/schema/AuditTable.java",
                schemaType(
                        "example.mechanical",
                        "AuditTable",
                        "  @SomaField long value;\n"));

        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                sources, true, new SomaProcessor())) {
            assertTrue(compilation.success(), compilation.diagnostics().toString());
            String soma = compilation.generatedSource("example/mechanical/Soma.java");
            String url = compilation.generatedSource("example/mechanical/URLTable.java");
            assertTrue(soma.contains("URLTable uRLTable()"));
            assertTrue(soma.contains("AuditTableTable auditTableTable()"));
            assertTrue(url.contains("IndexSelection byURL(long value)"));
            assertTrue(compilation.generatedSourceExists(
                    "example/mechanical/AuditTableTable.java"));
        }
    }

    @Test
    void infrastructureAndJavaLangSimpleNamesCannotShadowGeneratedDependencies()
            throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        String generatedPackage = "example.shadow";
        String packagePath = generatedPackage.replace('.', '/');
        sources.put(
                packagePath + "/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package " + generatedPackage + ".schema;\n");
        for (String tableName : new String[] {
                "Optional", "GeneratedLong", "Object", "String",
                "Long", "View", "Consumer", "Override"
        }) {
            sources.put(
                    packagePath + "/schema/" + tableName + ".java",
                    schemaType(
                            generatedPackage,
                            tableName,
                            "  @SomaKey long id;\n  @SomaField long value;\n"));
        }

        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                sources, true, new SomaProcessor())) {
            assertTrue(compilation.success(), compilation.diagnostics().toString());
            String soma = compilation.generatedSource("example/shadow/Soma.java");
            String optionalTable = compilation.generatedSource(
                    "example/shadow/OptionalTable.java");
            String generatedLongTable = compilation.generatedSource(
                    "example/shadow/GeneratedLongTable.java");
            String viewTable = compilation.generatedSource(
                    "example/shadow/ViewTable.java");
            assertTrue(soma.contains("java.lang.Object CAPABILITY"));
            assertTrue(optionalTable.contains(
                    "java.util.Optional<example.shadow.Optional>"));
            assertTrue(generatedLongTable.contains(
                    "io.github.somaruntime.soma.internal.GeneratedTable runtime"));
            assertTrue(viewTable.contains("example.shadow.View fetch()"));
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
                + "@SomaTable(defaultCapacity = 32) final class Beta {\n"
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

    private static Map<String, String> i1Sources(
            String generatedPackage,
            String tableName,
            String fields) {
        LinkedHashMap<String, String> sources = new LinkedHashMap<String, String>();
        String packagePath = generatedPackage.replace('.', '/');
        sources.put(
                packagePath + "/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package " + generatedPackage + ".schema;\n");
        sources.put(
                packagePath + "/schema/" + tableName + ".java",
                schemaType(generatedPackage, tableName, fields));
        return sources;
    }

    private static String schemaType(
            String generatedPackage,
            String tableName,
            String fields) {
        return "package " + generatedPackage + ".schema;\n"
                + "import io.github.somaruntime.soma.*;\n"
                + "@SomaTable final class " + tableName + " {\n"
                + fields
                + "}\n";
    }

    private static void assertSymbolCollision(
            Map<String, String> sources,
            String generatedPackagePath) throws Exception {
        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                sources, true, new SomaProcessor())) {
            assertFalse(compilation.success());
            assertDiagnostic(compilation, "[SOMA-1015]");
            assertNoPublishedComposition(compilation, generatedPackagePath);
        }
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
