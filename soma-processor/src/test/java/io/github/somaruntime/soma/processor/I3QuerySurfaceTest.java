package io.github.somaruntime.soma.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class I3QuerySurfaceTest {

    @Test
    void invalidCapabilityTransitionsAreAbsentFromGeneratedJavaSurface()
            throws Exception {
        assertRejected("tableDistinct", "table.distinct();");
        assertRejected("mappedNoArgArray", "table.map(v -> v.label()).toArray();");
        assertRejected("booleanSum", "table.enabled.sum();");
        assertRejected("fieldMutation", "table.amount.remove();");
        assertRejected("mappedMutation", "table.map(v -> v.label()).remove();");
        assertRejected("parallelBeforeI6", "table.parallel();");
        assertRejected("sequential", "table.map(v -> v.label()).sequential();");
        assertRejected("mappedArrayFactory", "table.map(v -> v.label()).toArray(String[]::new);");
        assertRejected("flatMap", "table.map(v -> v.label()).flatMap(v -> java.util.stream.Stream.of(v));");
        assertRejected("objectEquality", "table.payload.eq(new Object());");
        assertRejected("objectDistinct", "table.payload.distinct();");
        assertRejected("objectOrder", "table.payload.sorted();");
    }

    private static void assertRejected(String suffix, String statement)
            throws Exception {
        String base = "example.i3negative" + suffix;
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put(path(base, "schema/package-info.java"),
                "@io.github.somaruntime.soma.SomaSchema\npackage "
                        + base + ".schema;\n");
        sources.put(path(base, "schema/Entry.java"),
                "package " + base + ".schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class Entry {\n"
                        + "  @SomaKey long id;\n"
                        + "  @SomaField boolean enabled;\n"
                        + "  @SomaField long amount;\n"
                        + "  @SomaField String label;\n"
                        + "  @SomaField Object payload;\n"
                        + "}\n");
        sources.put(path(base, "Consumer.java"),
                "package " + base + ";\n"
                        + "final class Consumer { void run() {\n"
                        + "  EntryTable table = Soma.entryTable();\n"
                        + "  " + statement + "\n"
                        + "} }\n");
        try (CompilerTestSupport.Compilation compilation =
                     CompilerTestSupport.compile(sources, true, new SomaProcessor())) {
            assertFalse(compilation.success(), suffix);
            assertTrue(compilation.diagnostics().toString().contains("cannot find symbol")
                    || compilation.diagnostics().toString().contains("cannot be applied"),
                    compilation.diagnostics().toString());
        }
    }

    private static String path(String packageName, String file) {
        return packageName.replace('.', '/') + "/" + file;
    }
}
