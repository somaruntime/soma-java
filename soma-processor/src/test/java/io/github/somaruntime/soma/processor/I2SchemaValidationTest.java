package io.github.somaruntime.soma.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class I2SchemaValidationTest {

    @Test
    void explicitConstructorMethodInitializerAndFieldInitializerFailClosed() throws Exception {
        assertInvalid("constructor", "[SOMA-1017]",
                "  Invalid() {}\n  @SomaField long value;\n");
        assertInvalid("method", "[SOMA-1017]",
                "  @SomaField long value;\n  long value() { return value; }\n");
        assertInvalid("block", "[SOMA-1017]",
                "  { }\n  @SomaField long value;\n");
        assertInvalid("fieldinit", "[SOMA-1018]",
                "  @SomaField long value = 1L;\n");
    }

    @Test
    void keyIndexAndValueTypeBoundariesFailBeforeGeneration() throws Exception {
        assertInvalid("floatkey", "[SOMA-1022]",
                "  @SomaKey float key;\n  @SomaField long value;\n");
        assertInvalid("doubleindex", "[SOMA-1022]",
                "  @SomaIndex double indexed;\n");
        assertInvalidWithSupport(
                "objectindex",
                "[SOMA-1022]",
                "package example.objectindex; public final class Payload {}\n",
                "  @SomaIndex example.objectindex.Payload payload;\n");
        assertInvalidWithSupport(
                "objectkey",
                "[SOMA-1022]",
                "package example.objectkey; public final class Payload {}\n",
                "  @SomaKey example.objectkey.Payload payload;\n");

        Map<String, String> valueObject = base("valueobject");
        valueObject.put("example/valueobject/Payload.java",
                "package example.valueobject; public final class Payload {}\n");
        valueObject.put("example/valueobject/schema/InvalidValue.java",
                "package example.valueobject.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaValue final class InvalidValue {\n"
                        + "  @SomaField example.valueobject.Payload payload;\n"
                        + "}\n");
        valueObject.put("example/valueobject/schema/Invalid.java",
                table("example.valueobject", "  @SomaField InvalidValue value;\n"));
        assertInvalid(valueObject, "example/valueobject", "[SOMA-1023]");

        Map<String, String> valueFloatKey = base("valuefloat");
        valueFloatKey.put("example/valuefloat/schema/FloatValue.java",
                "package example.valuefloat.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaValue final class FloatValue {\n"
                        + "  @SomaField float value;\n"
                        + "}\n");
        valueFloatKey.put("example/valuefloat/schema/Invalid.java",
                table("example.valuefloat", "  @SomaKey FloatValue key;\n"));
        assertInvalid(valueFloatKey, "example/valuefloat", "[SOMA-1022]");
    }

    @Test
    void inaccessibleParameterizedAndArrayComponentsFailClosed() throws Exception {
        Map<String, String> generic = base("inaccessiblegeneric");
        generic.put("example/inaccessiblegeneric/schema/Hidden.java",
                "package example.inaccessiblegeneric.schema;\n"
                        + "final class Hidden {}\n");
        generic.put("example/inaccessiblegeneric/schema/Invalid.java",
                table("example.inaccessiblegeneric",
                        "  @SomaField java.util.List<Hidden> values;\n"));
        assertInvalid(generic, "example/inaccessiblegeneric", "[SOMA-1021]");

        Map<String, String> array = base("inaccessiblearray");
        array.put("example/inaccessiblearray/schema/Hidden.java",
                "package example.inaccessiblearray.schema;\n"
                        + "final class Hidden {}\n");
        array.put("example/inaccessiblearray/schema/Invalid.java",
                table("example.inaccessiblearray", "  @SomaField Hidden[] values;\n"));
        assertInvalid(array, "example/inaccessiblearray", "[SOMA-1021]");
    }

    @Test
    void cyclicValueGraphAndRoleErrorsFailWithoutPartialComposition() throws Exception {
        Map<String, String> cycle = base("cycle");
        cycle.put("example/cycle/schema/Left.java",
                "package example.cycle.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaValue final class Left { @SomaField Right right; }\n");
        cycle.put("example/cycle/schema/Right.java",
                "package example.cycle.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaValue final class Right { @SomaField Left left; }\n");
        cycle.put("example/cycle/schema/Invalid.java",
                table("example.cycle", "  @SomaField Left value;\n"));
        assertInvalid(cycle, "example/cycle", "[SOMA-1014]");

        assertInvalid("norole", "[SOMA-1008]", "  long value;\n");
        assertInvalid("doublerole", "[SOMA-1008]",
                "  @SomaField @SomaIndex long value;\n");
    }

    @Test
    void fieldRolesRequireASchemaDeclarationOwner() throws Exception {
        Map<String, String> sources = base("roleowner");
        sources.put("example/roleowner/schema/Valid.java",
                table("example.roleowner", "  @SomaField long value;\n")
                        .replace("class Invalid", "class Valid"));
        sources.put("example/roleowner/Ordinary.java",
                "package example.roleowner;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "final class Ordinary {\n"
                        + "  @SomaField long field;\n"
                        + "  @SomaKey long key;\n"
                        + "  @SomaIndex long index;\n"
                        + "}\n");
        assertInvalid(sources, "example/roleowner", "[SOMA-1013]");
    }

    @Test
    void ordinaryObjectEqualityAndKeyEditorMutationAreAbsentAtCompileTime() throws Exception {
        Map<String, String> objectEquality = capabilitySources(
                "objectcapability",
                "Soma.entityTable().payload.eq(new Payload());");
        assertConsumerCompileFailure(objectEquality, "eq");

        Map<String, String> keyMutation = capabilitySources(
                "keymutation",
                "Soma.entityTable().update(1L, editor -> editor.id(2L));");
        assertConsumerCompileFailure(keyMutation, "id");
    }

    @Test
    void declarationCapacityRoleAndIdentifierDiagnosticsCoverTheSchemaBoundary()
            throws Exception {
        assertInvalid("emptytable", "[SOMA-1005]", "");
        assertInvalid("negativecapacity", "[SOMA-1010]",
                "  @SomaField long value;\n", -1);
        assertInvalid("staticfield", "[SOMA-1018]",
                "  @SomaField static long value;\n");
        assertInvalid("finalfield", "[SOMA-1018]",
                "  @SomaField final long value;\n");
        assertInvalid("unsafeidentifier", "[SOMA-1024]",
                "  @SomaField long bad$name;\n");
        assertInvalid("hiddenfield", "[SOMA-1024]",
                "  @SomaField long _value;\n");
        assertInvalid("nonnfcfield", "[SOMA-1024]",
                "  @SomaField long e" + '\u0301' + ";\n");
        assertInvalid("bidifield", "[SOMA-1024]",
                "  @SomaField long bad" + '\u202e' + "name;\n");
        assertInvalid("escapedbidifield", "[SOMA-1024]",
                "  @SomaField long bad" + '\\' + "u202ename;\n");

        Map<String, String> generic = base("genericdeclaration");
        generic.put("example/genericdeclaration/schema/Invalid.java",
                "package example.genericdeclaration.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class Invalid<T> {\n"
                        + "  @SomaField long value;\n"
                        + "}\n");
        assertInvalid(generic, "example/genericdeclaration", "[SOMA-1017]");

        Map<String, String> valueRole = base("valuerole");
        valueRole.put("example/valuerole/schema/BadValue.java",
                "package example.valuerole.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaValue final class BadValue { @SomaKey long value; }\n");
        valueRole.put("example/valuerole/schema/Invalid.java",
                table("example.valuerole", "  @SomaField BadValue value;\n"));
        assertInvalid(valueRole, "example/valuerole", "[SOMA-1013]");

        Map<String, String> emptyValue = base("emptyvalue");
        emptyValue.put("example/emptyvalue/schema/EmptyValue.java",
                "package example.emptyvalue.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaValue final class EmptyValue {}\n");
        emptyValue.put("example/emptyvalue/schema/Invalid.java",
                table("example.emptyvalue", "  @SomaField EmptyValue value;\n"));
        assertInvalid(emptyValue, "example/emptyvalue", "[SOMA-1005]");
    }

    @Test
    void packageCompositionDiagnosticsRejectWrongAndReservedOwners() throws Exception {
        Map<String, String> wrongSuffix = new LinkedHashMap<String, String>();
        wrongSuffix.put("example/wrongsuffix/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package example.wrongsuffix;\n");
        wrongSuffix.put("example/wrongsuffix/Invalid.java",
                "package example.wrongsuffix;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class Invalid { @SomaField long value; }\n");
        assertInvalid(wrongSuffix, "example", "[SOMA-1002]");

        Map<String, String> reserved = new LinkedHashMap<String, String>();
        reserved.put("io/github/somaruntime/soma/bad/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package io.github.somaruntime.soma.bad.schema;\n");
        reserved.put("io/github/somaruntime/soma/bad/schema/Invalid.java",
                "package io.github.somaruntime.soma.bad.schema;\n"
                        + "@io.github.somaruntime.soma.SomaTable final class Invalid {\n"
                        + "  @io.github.somaruntime.soma.SomaField long value;\n"
                        + "}\n");
        assertInvalid(reserved, "io/github/somaruntime/soma/bad", "[SOMA-1006]");

        Map<String, String> outside = base("outside");
        outside.put("example/outside/schema/Valid.java",
                table("example.outside", "  @SomaField long value;\n")
                        .replace("class Invalid", "class Valid"));
        outside.put("example/foreign/Outside.java",
                "package example.foreign;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class Outside { @SomaField long value; }\n");
        assertInvalid(outside, "example/outside", "[SOMA-1012]");
    }

    @Test
    void nestedEndpointAndCrossCompositionNamespacesArePreflightedGlobally()
            throws Exception {
        Map<String, String> nested = base("nestedcollision");
        nested.put("example/nestedcollision/schema/Nested.java",
                "package example.nestedcollision.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaValue final class Nested { @SomaField long runtime; }\n");
        nested.put("example/nestedcollision/schema/Invalid.java",
                table("example.nestedcollision", "  @SomaField Nested value;\n"));
        assertInvalid(nested, "example/nestedcollision", "[SOMA-1015]");

        Map<String, String> cross = new LinkedHashMap<String, String>();
        cross.put("example/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\npackage example.schema;\n");
        cross.put("example/schema/audit.java",
                "package example.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class audit { @SomaField long value; }\n");
        cross.put("example/audit/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package example.audit.schema;\n");
        cross.put("example/audit/schema/Other.java",
                "package example.audit.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class Other { @SomaField long value; }\n");
        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                cross, true, new SomaProcessor())) {
            assertFalse(compilation.success(), compilation.diagnostics().toString());
            assertTrue(compilation.diagnostics().stream()
                    .anyMatch(message -> message.contains("[SOMA-1015]")),
                    compilation.diagnostics().toString());
            assertFalse(compilation.generatedSourceExists("example/Soma.java"));
            assertFalse(compilation.generatedSourceExists("example/audit/Soma.java"));
            assertFalse(compilation.classOutputExists(
                    "META-INF/soma/example.schema.properties"));
            assertFalse(compilation.classOutputExists(
                    "META-INF/soma/example.audit.schema.properties"));
        }
    }

    @Test
    void escapedBackslashTextInCommentIsNotAnUnsafeUnicodeEscape() throws Exception {
        Map<String, String> sources = base("safecomment");
        sources.put("example/safecomment/schema/Valid.java",
                "package example.safecomment.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "// A JLS Unicode escape remains comment text: "
                        + '\\' + "u202e\n"
                        + "@SomaTable final class Valid { @SomaField long value; }\n");
        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                sources, true, new SomaProcessor())) {
            assertTrue(compilation.success(), compilation.diagnostics().toString());
        }
    }

    @Test
    void stableDiagnosticTextIsFrozenForRoleAndTypeFailures() throws Exception {
        Map<String, String> owner = base("diagnosticowner");
        owner.put("example/diagnosticowner/schema/Valid.java",
                table("example.diagnosticowner", "  @SomaField long value;\n")
                        .replace("class Invalid", "class Valid"));
        owner.put("example/diagnosticowner/Ordinary.java",
                "package example.diagnosticowner;\n"
                        + "final class Ordinary {\n"
                        + "  @io.github.somaruntime.soma.SomaKey long key;\n"
                        + "}\n");
        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                owner, true, new SomaProcessor())) {
            assertFalse(compilation.success());
            long count = compilation.diagnostics().stream()
                    .filter(message -> message.contains("[SOMA-1013]"))
                    .count();
            assertEquals(1L, count);
            assertTrue(compilation.diagnostics().stream().anyMatch(message -> message.endsWith(
                    "[SOMA-1013] @SomaKey is only valid on a direct @SomaTable field.")),
                    compilation.diagnostics().toString());
        }

        Map<String, String> objectKey = base("diagnosticobject");
        objectKey.put("example/diagnosticobject/Payload.java",
                "package example.diagnosticobject; public final class Payload {}\n");
        objectKey.put("example/diagnosticobject/schema/Invalid.java",
                table("example.diagnosticobject",
                        "  @SomaKey example.diagnosticobject.Payload key;\n"));
        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                objectKey, true, new SomaProcessor())) {
            assertFalse(compilation.success());
            assertTrue(compilation.diagnostics().stream().anyMatch(message -> message.endsWith(
                    "[SOMA-1022] @SomaKey/@SomaIndex requires a recursively keyable Field.")),
                    compilation.diagnostics().toString());
        }
    }

    private static void assertInvalid(
            String identity,
            String code,
            String fields) throws Exception {
        Map<String, String> sources = base(identity);
        sources.put("example/" + identity + "/schema/Invalid.java",
                table("example." + identity, fields));
        assertInvalid(sources, "example/" + identity, code);
    }

    private static void assertInvalid(
            String identity,
            String code,
            String fields,
            int defaultCapacity) throws Exception {
        Map<String, String> sources = base(identity);
        sources.put("example/" + identity + "/schema/Invalid.java",
                "package example." + identity + ".schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable(defaultCapacity = " + defaultCapacity + ") "
                        + "final class Invalid {\n"
                        + fields
                        + "}\n");
        assertInvalid(sources, "example/" + identity, code);
    }

    private static void assertInvalidWithSupport(
            String identity,
            String code,
            String support,
            String fields) throws Exception {
        Map<String, String> sources = base(identity);
        sources.put("example/" + identity + "/Payload.java", support);
        sources.put("example/" + identity + "/schema/Invalid.java",
                table("example." + identity, fields));
        assertInvalid(sources, "example/" + identity, code);
    }

    private static void assertInvalid(
            Map<String, String> sources,
            String generatedPath,
            String code) throws Exception {
        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                sources, true, new SomaProcessor())) {
            assertFalse(compilation.success(), compilation.diagnostics().toString());
            assertTrue(compilation.diagnostics().stream()
                    .anyMatch(message -> message.contains(code)),
                    compilation.diagnostics().toString());
            assertFalse(compilation.generatedSourceExists(generatedPath + "/Soma.java"));
            assertFalse(compilation.classOutputExists(
                    "META-INF/soma/" + generatedPath.replace('/', '.') + ".schema.properties"));
        }
    }

    private static Map<String, String> capabilitySources(
            String identity,
            String statement) {
        Map<String, String> sources = base(identity);
        String generatedPackage = "example." + identity;
        String path = "example/" + identity;
        sources.put(path + "/Payload.java",
                "package " + generatedPackage + ";\n"
                        + "public final class Payload {}\n");
        sources.put(path + "/schema/Entity.java",
                "package " + generatedPackage + ".schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class Entity {\n"
                        + "  @SomaKey long id;\n"
                        + "  @SomaField " + generatedPackage + ".Payload payload;\n"
                        + "}\n");
        sources.put(path + "/Consumer.java",
                "package " + generatedPackage + ";\n"
                        + "final class Consumer {\n"
                        + "  static void use() { " + statement + " }\n"
                        + "}\n");
        return sources;
    }

    private static void assertConsumerCompileFailure(
            Map<String, String> sources,
            String expectedMember) throws Exception {
        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                sources, true, new SomaProcessor())) {
            assertFalse(compilation.success());
            assertTrue(compilation.diagnostics().stream()
                    .anyMatch(message -> message.contains(expectedMember)),
                    compilation.diagnostics().toString());
        }
    }

    private static Map<String, String> base(String identity) {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("example/" + identity + "/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package example." + identity + ".schema;\n");
        return sources;
    }

    private static String table(String generatedPackage, String fields) {
        return "package " + generatedPackage + ".schema;\n"
                + "import io.github.somaruntime.soma.*;\n"
                + "@SomaTable final class Invalid {\n"
                + fields
                + "}\n";
    }
}
