package io.github.somaruntime.soma.processor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class I6ParallelSurfaceTest {

    @Test
    void generatedJava8SurfaceCarriesParallelModeAcrossCapabilities()
            throws Exception {
        String base = "example.i6";
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("example/i6/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\npackage "
                        + base + ".schema;\n");
        sources.put("example/i6/schema/Event.java",
                "package " + base + ".schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class Event {\n"
                        + "  @SomaKey long id;\n"
                        + "  @SomaIndex long machineId;\n"
                        + "  @SomaField long amount;\n"
                        + "  @SomaIndex String label;\n"
                        + "}\n");
        sources.put("example/i6/schema/State.java",
                "package " + base + ".schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class State {\n"
                        + "  @SomaKey long machineId;\n"
                        + "  @SomaField boolean enabled;\n"
                        + "}\n");
        sources.put("example/i6/Consumer.java",
                "package " + base + ";\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "import java.util.concurrent.ForkJoinPool;\n"
                        + "final class Consumer {\n"
                        + "  void configure(ForkJoinPool pool) {\n"
                        + "    Soma.configure(SomaConfiguration.builder()"
                        + ".parallelExecutor(pool).build());\n"
                        + "  }\n"
                        + "  long run() {\n"
                        + "    EventTable events = Soma.eventTable();\n"
                        + "    StateTable states = Soma.stateTable();\n"
                        + "    EventTable.Stream stream = events.parallel().parallel();\n"
                        + "    EventTable.Selection selection = events.selectAll().parallel();\n"
                        + "    EventTable.ReadStream read = events.join(states)"
                        + ".on(events.machineId, states.machineId).parallel().semi();\n"
                        + "    MappedStream<String> mapped = events.map(v -> v.label()).parallel();\n"
                        + "    EventTable.AmountField.Stream primitive = events.amount.parallel().parallel();\n"
                        + "    long indexed = events.byLabel(\"hot\").parallel().count();\n"
                        + "    long joined = events.join(states)"
                        + ".on(events.machineId, states.machineId).inner().parallel().count();\n"
                        + "    return stream.count() + selection.count() + read.count()"
                        + " + mapped.count() + primitive.count() + indexed + joined;\n"
                        + "  }\n"
                        + "}\n");

        try (CompilerTestSupport.Compilation compilation =
                     CompilerTestSupport.compile(
                             sources, true, new SomaProcessor())) {
            assertTrue(compilation.success(), compilation.diagnostics().toString());
            String table = compilation.generatedSource(
                    "example/i6/EventTable.java");
            assertTrue(table.contains("public Stream parallel()"));
            assertTrue(table.contains("public Selection parallel()"));
            assertTrue(table.contains("public ReadStream parallel()"));
        }
    }
}
