package io.github.somaruntime.soma.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class I2SchemaGenerationTest {

    @Test
    void fullTypeSystemGeneratesCompilesAndRunsOneUnifiedTablePath() throws Exception {
        Map<String, String> sources = fullTypeSources();
        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                sources, true, new SomaProcessor())) {
            assertTrue(compilation.success(), compilation.diagnostics().toString());

            String value = compilation.generatedSource("example/i2/MachinePair.java");
            assertTrue(value.contains("private final example.i2.MachineId fromMachine;"));
            assertTrue(value.contains("public MachinePair("));
            assertFalse(value.contains("public MachinePair()"));
            assertTrue(value.contains("public static final class View"));

            String object = compilation.generatedSource("example/i2/AllTypes.java");
            assertTrue(object.contains("public AllTypes()"));
            assertTrue(object.contains("public AllTypes("));
            assertTrue(object.contains("public void payload(example.i2.Payload value)"));

            String table = compilation.generatedSource("example/i2/AllTypesTable.java");
            assertTrue(table.contains("GeneratedTable runtime"));
            assertFalse(table.contains("GeneratedLongTable"));
            assertTrue(table.contains(
                    "class KeyField implements io.github.somaruntime.soma.SomaKeyableField"));
            assertTrue(table.contains(
                    "class RatioField implements io.github.somaruntime.soma.SomaFieldEndpoint"));
            assertFalse(table.contains(
                    "class RatioField implements io.github.somaruntime.soma.SomaKeyableField"));
            String payloadEndpoint = table.substring(table.indexOf("class PayloadField"));
            payloadEndpoint = payloadEndpoint.substring(
                    0, payloadEndpoint.indexOf("class BytesField"));
            assertTrue(payloadEndpoint.contains("isNull()"));
            assertFalse(payloadEndpoint.contains("eq(example.i2.Payload value)"));
            assertTrue(table.contains("public IndexSelection byName(java.lang.String value)"));
            assertTrue(table.contains(
                    "public IndexSelection byMachineId(example.i2.MachineId value)"));

            String keyless = compilation.generatedSource("example/i2/LogEntryTable.java");
            String keylessTable = keyless.substring(
                    0, keyless.indexOf("public static class View"));
            assertFalse(keylessTable.contains(
                    "java.util.Optional<example.i2.LogEntry> find("));
            assertFalse(keylessTable.contains("public example.i2.LogEntry get("));
            assertFalse(keylessTable.contains(
                    "public io.github.somaruntime.soma.UpdateResult update("));
            assertFalse(keylessTable.contains(
                    "public io.github.somaruntime.soma.RemoveResult remove("));

            Class<?> consumer = compilation.loadClass("example.i2.Consumer");
            Method run = consumer.getDeclaredMethod("run");
            run.setAccessible(true);
            invoke(run);
        }
    }

    private static void invoke(Method method) throws Exception {
        try {
            method.invoke(null);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new AssertionError(cause);
        }
    }

    private static Map<String, String> fullTypeSources() {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("example/i2/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package example.i2.schema;\n");
        sources.put("example/i2/Status.java",
                "package example.i2;\n"
                        + "public enum Status { NEW, ACTIVE, DONE }\n");
        sources.put("example/i2/Payload.java",
                "package example.i2;\n"
                        + "public final class Payload {\n"
                        + "  private final String value;\n"
                        + "  public Payload(String value) { this.value = value; }\n"
                        + "  public String value() { return value; }\n"
                        + "}\n");
        sources.put("example/i2/schema/MachineId.java",
                "package example.i2.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaValue final class MachineId {\n"
                        + "  @SomaField long value;\n"
                        + "}\n");
        sources.put("example/i2/schema/JobId.java",
                "package example.i2.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaValue final class JobId {\n"
                        + "  @SomaField long value;\n"
                        + "}\n");
        sources.put("example/i2/schema/MachinePair.java",
                "package example.i2.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "import example.i2.Status;\n"
                        + "@SomaValue final class MachinePair {\n"
                        + "  @SomaField MachineId fromMachine;\n"
                        + "  @SomaField MachineId toMachine;\n"
                        + "  @SomaField String label;\n"
                        + "  @SomaField Status status;\n"
                        + "}\n");
        sources.put("example/i2/schema/AllTypes.java",
                "package example.i2.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "import example.i2.*;\n"
                        + "import java.util.List;\n"
                        + "@SomaTable(defaultCapacity = 4) final class AllTypes {\n"
                        + "  @SomaKey MachinePair key;\n"
                        + "  @SomaIndex String name;\n"
                        + "  @SomaIndex MachineId machineId;\n"
                        + "  @SomaField boolean enabled;\n"
                        + "  @SomaField byte byteValue;\n"
                        + "  @SomaField short shortValue;\n"
                        + "  @SomaField char charValue;\n"
                        + "  @SomaField int intValue;\n"
                        + "  @SomaField long longValue;\n"
                        + "  @SomaField float ratio;\n"
                        + "  @SomaField double weight;\n"
                        + "  @SomaField Status status;\n"
                        + "  @SomaField Payload payload;\n"
                        + "  @SomaField byte[] bytes;\n"
                        + "  @SomaField List<String> tags;\n"
                        + "}\n");
        sources.put("example/i2/schema/LogEntry.java",
                "package example.i2.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class LogEntry {\n"
                        + "  @SomaField long minute;\n"
                        + "  @SomaField String message;\n"
                        + "}\n");
        sources.put("example/i2/schema/EligibleMachine.java",
                "package example.i2.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class EligibleMachine {\n"
                        + "  @SomaIndex JobId jobId;\n"
                        + "  @SomaIndex MachineId machineId;\n"
                        + "  @SomaField long processingMinutes;\n"
                        + "}\n");
        sources.put("example/i2/Consumer.java",
                "package example.i2;\n"
                        + "import java.util.*;\n"
                        + "final class Consumer {\n"
                        + "  static void run() {\n"
                        + "    AllTypesTable table = Soma.allTypesTable();\n"
                        + "    if (table != Soma.defaultGroup().allTypesTable()) throw new AssertionError();\n"
                        + "    if (table == Soma.createGroup().allTypesTable()) throw new AssertionError();\n"
                        + "    final SomaGroup concurrentGroup = Soma.createGroup();\n"
                        + "    final AllTypesTable[] firstAccess = new AllTypesTable[16];\n"
                        + "    final java.util.concurrent.atomic.AtomicReference<Throwable> "
                        + "firstFailure = new java.util.concurrent.atomic.AtomicReference<>();\n"
                        + "    Thread[] workers = new Thread[firstAccess.length];\n"
                        + "    for (int i = 0; i < workers.length; i++) {\n"
                        + "      final int slot = i;\n"
                        + "      workers[i] = new Thread(() -> {\n"
                        + "        try { firstAccess[slot] = concurrentGroup.allTypesTable(); }\n"
                        + "        catch (Throwable problem) { firstFailure.compareAndSet(null, problem); }\n"
                        + "      });\n"
                        + "      workers[i].start();\n"
                        + "    }\n"
                        + "    for (Thread worker : workers) {\n"
                        + "      try { worker.join(); } catch (InterruptedException failure) {\n"
                        + "        Thread.currentThread().interrupt(); throw new AssertionError(failure);\n"
                        + "      }\n"
                        + "    }\n"
                        + "    if (firstFailure.get() != null)\n"
                        + "      throw new AssertionError(firstFailure.get());\n"
                        + "    for (AllTypesTable observed : firstAccess)\n"
                        + "      if (observed != firstAccess[0] || observed == table)\n"
                        + "        throw new AssertionError(\"accessor identity drift\");\n"
                        + "    MachinePair firstKey = new MachinePair(\n"
                        + "        new MachineId(1L), new MachineId(2L), null, Status.NEW);\n"
                        + "    MachinePair secondKey = new MachinePair(\n"
                        + "        new MachineId(3L), new MachineId(4L), \"pair\", null);\n"
                        + "    Payload payload = new Payload(\"identity\");\n"
                        + "    byte[] bytes = new byte[] {1, 2};\n"
                        + "    List<String> tags = new ArrayList<String>();\n"
                        + "    tags.add(\"tag\");\n"
                        + "    table.add(new AllTypes(firstKey, \"alpha\", new MachineId(7L),\n"
                        + "        true, (byte) 8, (short) 9, 'x', 10, 11L,\n"
                        + "        Float.NaN, -0.0d, Status.ACTIVE, payload, bytes, tags));\n"
                        + "    table.add(new AllTypes(secondKey, null, new MachineId(7L),\n"
                        + "        false, (byte) 1, (short) 2, 'y', 3, 4L,\n"
                        + "        1.5f, 2.5d, null, null, null, null));\n"
                        + "    AllTypes found = table.get(firstKey);\n"
                        + "    if (found.payload() != payload || found.bytes() != bytes || found.tags() != tags)\n"
                        + "      throw new AssertionError(\"ordinary reference identity drift\");\n"
                        + "    if (!found.key().equals(firstKey) || table.find(firstKey).get().longValue() != 11L)\n"
                        + "      throw new AssertionError(\"Value materialization drift\");\n"
                        + "    if (table.byName(\"alpha\").count() != 1L\n"
                        + "        || table.byName(null).count() != 1L\n"
                        + "        || table.byMachineId(new MachineId(7L)).count() != 2L)\n"
                        + "      throw new AssertionError(\"Index drift\");\n"
                        + "    if (table.filter(table.ratio.eq(Float.NaN)).count() != 1L\n"
                        + "        || table.filter(table.weight.eq(+0.0d)).count() != 0L\n"
                        + "        || table.filter(table.status.eq(Status.ACTIVE)).count() != 1L)\n"
                        + "      throw new AssertionError(\"typed equality drift\");\n"
                        + "    io.github.somaruntime.soma.UpdateResult updated = table.update(firstKey, editor -> {\n"
                        + "      editor.name(null);\n"
                        + "      editor.machineId(new MachineId(8L));\n"
                        + "      editor.longValue(12L);\n"
                        + "    });\n"
                        + "    if (updated.matched() != 1L || updated.changed() != 1L\n"
                        + "        || table.byName(\"alpha\").count() != 0L\n"
                        + "        || table.byName(null).count() != 2L\n"
                        + "        || table.byMachineId(new MachineId(7L)).count() != 1L\n"
                        + "        || table.byMachineId(new MachineId(8L)).count() != 1L)\n"
                        + "      throw new AssertionError(\"Index update drift\");\n"
                        + "    io.github.somaruntime.soma.RemoveResult removed = table.remove(secondKey);\n"
                        + "    if (removed.removed() != 1L || table.size() != 1L\n"
                        + "        || table.byName(null).count() != 1L)\n"
                        + "      throw new AssertionError(\"remove drift\");\n"
                        + "    LogEntryTable log = Soma.logEntryTable();\n"
                        + "    log.add(new LogEntry());\n"
                        + "    if (log.size() != 1L || log.count() != 1L) throw new AssertionError();\n"
                        + "    EligibleMachineTable relations = Soma.eligibleMachineTable();\n"
                        + "    relations.add(new EligibleMachine(new JobId(1L), new MachineId(7L), 10L));\n"
                        + "    relations.add(new EligibleMachine(new JobId(1L), new MachineId(8L), 11L));\n"
                        + "    relations.add(new EligibleMachine(new JobId(2L), new MachineId(7L), 12L));\n"
                        + "    if (relations.byJobId(new JobId(1L)).count() != 2L\n"
                        + "        || relations.byMachineId(new MachineId(7L)).count() != 2L)\n"
                        + "      throw new AssertionError(\"relation Index drift\");\n"
                        + "  }\n"
                        + "}\n");
        return sources;
    }
}
