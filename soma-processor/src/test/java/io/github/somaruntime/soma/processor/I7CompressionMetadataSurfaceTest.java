package io.github.somaruntime.soma.processor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class I7CompressionMetadataSurfaceTest {

    @Test
    void generatedJava8SurfaceExposesFourLevelDetachedMetadata()
            throws Exception {
        String base = "example.i7";
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("example/i7/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\npackage "
                        + base + ".schema;\n");
        sources.put("example/i7/schema/MachineId.java",
                "package " + base + ".schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaValue final class MachineId { @SomaField long value; }\n");
        sources.put("example/i7/schema/Event.java",
                "package " + base + ".schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class Event {\n"
                        + "  @SomaKey long id;\n"
                        + "  @SomaIndex MachineId machineId;\n"
                        + "  @SomaField String category;\n"
                        + "  @SomaField boolean enabled;\n"
                        + "}\n");
        sources.put("example/i7/Consumer.java",
                "package " + base + ";\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "final class Consumer {\n"
                        + "  void configure() { Soma.configure(SomaConfiguration.builder()"
                        + ".compression(SomaCompression.AUTO).build()); }\n"
                        + "  long inspect() {\n"
                        + "    SomaMetadata soma = Soma._metadata();\n"
                        + "    SomaGroup group = Soma.defaultGroup();\n"
                        + "    GroupMetadata groupMetadata = group._metadata();\n"
                        + "    EventTable table = group.eventTable();\n"
                        + "    TableMetadata tableMetadata = table._metadata();\n"
                        + "    FieldMetadata field = table.machineId.value._metadata();\n"
                        + "    String explain = table._explain();\n"
                        + "    if (!field.logicalPath().equals(\"machineId.value\"))"
                        + " throw new AssertionError();\n"
                        + "    return tableMetadata.size() + groupMetadata.retainedBytes()"
                        + " + soma.globalRetainedBytes() + explain.length();\n"
                        + "  }\n"
                        + "}\n");

        try (CompilerTestSupport.Compilation compilation =
                     CompilerTestSupport.compile(
                             sources, true, new SomaProcessor())) {
            assertTrue(compilation.success(), compilation.diagnostics().toString());
            assertTrue(compilation.generatedSource("example/i7/Soma.java")
                    .contains("SomaMetadata _metadata()"));
            assertTrue(compilation.generatedSource("example/i7/SomaGroup.java")
                    .contains("GroupMetadata _metadata()"));
            String table = compilation.generatedSource("example/i7/EventTable.java");
            assertTrue(table.contains("TableMetadata _metadata()"));
            assertTrue(table.contains("FieldMetadata _metadata()"));
        }
    }
}
