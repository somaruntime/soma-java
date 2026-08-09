package io.github.somaruntime.soma.processor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class I3QueryGenerationTest {

    @Test
    void generatedSequentialQueryGrammarCompilesAndRunsOnJava8Shape()
            throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("example/i3/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package example.i3.schema;\n");
        sources.put("example/i3/schema/Event.java",
                "package example.i3.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class Event {\n"
                        + "  @SomaKey long id;\n"
                        + "  @SomaIndex String kind;\n"
                        + "  @SomaField boolean enabled;\n"
                        + "  @SomaField byte code;\n"
                        + "  @SomaField short shortValue;\n"
                        + "  @SomaField char letter;\n"
                        + "  @SomaField int priority;\n"
                        + "  @SomaField long amount;\n"
                        + "  @SomaField float ratio;\n"
                        + "  @SomaField double score;\n"
                        + "  @SomaField String label;\n"
                        + "}\n");
        sources.put("example/i3/Consumer.java", consumer());
        try (CompilerTestSupport.Compilation compilation =
                     CompilerTestSupport.compile(sources, true, new SomaProcessor())) {
            assertTrue(compilation.success(), compilation.diagnostics().toString());
            String table = compilation.generatedSource("example/i3/EventTable.java");
            assertTrue(table.contains("MappedStream<R> map("));
            assertTrue(table.contains("SomaLongStream mapToLong("));
            assertTrue(table.contains("class AmountField"));
            invoke(compilation.loadClass("example.i3.Consumer")
                    .getDeclaredMethod("run"));
        }
    }

    @Test
    void generatedNestedValueEndpointsRemainLogicalTypedSources()
            throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("example/i3value/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package example.i3value.schema;\n");
        sources.put("example/i3value/schema/MachineId.java",
                "package example.i3value.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaValue final class MachineId {\n"
                        + "  @SomaField long value;\n"
                        + "}\n");
        sources.put("example/i3value/schema/MachinePair.java",
                "package example.i3value.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaValue final class MachinePair {\n"
                        + "  @SomaField MachineId fromMachine;\n"
                        + "  @SomaField MachineId toMachine;\n"
                        + "}\n");
        sources.put("example/i3value/schema/Transfer.java",
                "package example.i3value.schema;\n"
                        + "import io.github.somaruntime.soma.*;\n"
                        + "@SomaTable final class Transfer {\n"
                        + "  @SomaKey long id;\n"
                        + "  @SomaField MachinePair machinePair;\n"
                        + "}\n");
        sources.put("example/i3value/Consumer.java",
                "package example.i3value;\n"
                        + "final class Consumer { static void run() {\n"
                        + "  TransferTable table = Soma.transferTable();\n"
                        + "  table.add(new Transfer(1L, new MachinePair(new MachineId(7L), new MachineId(8L))));\n"
                        + "  MachinePair[] pairs = table.machinePair.toArray();\n"
                        + "  MachineId[] from = table.machinePair.fromMachine.toArray();\n"
                        + "  long[] values = table.machinePair.fromMachine.value.toArray();\n"
                        + "  MachineId[] projected = table.filter(table.id.eq(1L)).map(table.machinePair.fromMachine).toArray();\n"
                        + "  if (pairs.length != 1 || pairs[0].fromMachine().value() != 7L\n"
                        + "      || from.length != 1 || from[0].value() != 7L\n"
                        + "      || values.length != 1 || values[0] != 7L\n"
                        + "      || projected.length != 1 || projected[0].value() != 7L)\n"
                        + "    throw new AssertionError(\"nested logical Field\");\n"
                        + "} }\n");
        try (CompilerTestSupport.Compilation compilation =
                     CompilerTestSupport.compile(
                             sources, true, new SomaProcessor())) {
            assertTrue(compilation.success(), compilation.diagnostics().toString());
            invoke(compilation.loadClass("example.i3value.Consumer")
                    .getDeclaredMethod("run"));
        }
    }

    private static String consumer() {
        return "package example.i3;\n"
                + "import io.github.somaruntime.soma.*;\n"
                + "import java.util.*;\n"
                + "final class Consumer {\n"
                + "  static void run() {\n"
                + "    EventTable table = Soma.eventTable();\n"
                + "    table.add(new Event(1L, \"a\", true, (byte) 2, (short) 3, 'c', 4, 10L, 1.5f, 4.0d, \"x\"));\n"
                + "    table.add(new Event(2L, \"b\", false, (byte) 3, (short) 4, 'a', 1, 20L, -0.0f, 2.0d, null));\n"
                + "    table.add(new Event(3L, \"a\", true, (byte) 2, (short) 5, 'b', 3, 30L, Float.NaN, Double.NaN, \"x\"));\n"
                + "    if (table.filter(table.enabled.eq(true)).filter(v -> v.priority() > 2).count() != 2L) throw new AssertionError(\"filter\");\n"
                + "    SomaExpression<EventTable.View> combined = table.kind.eq(\"a\").and(table.enabled.eq(true));\n"
                + "    if (table.filter(combined).count() != 2L || table.filter(combined.not()).count() != 1L) throw new AssertionError(\"boolean expression\");\n"
                + "    if (table.filter(table.id.in()).count() != 0L || table.filter(table.id.in(1L, 1L, 3L)).count() != 2L) throw new AssertionError(\"in set\");\n"
                + "    try { table.kind.in(\"a\", null); throw new AssertionError(\"null in\"); } catch (SomaOperationException expected) { if (expected.code() != SomaFailureCode.INVALID_ARGUMENT) throw expected; }\n"
                + "    try { table.id.between(3L, 1L); throw new AssertionError(\"inverse between\"); } catch (SomaOperationException expected) { if (expected.code() != SomaFailureCode.INVALID_ARGUMENT) throw expected; }\n"
                + "    long[] projected = table.filter(table.kind.eq(\"a\")).mapToLong(table.amount).map(v -> v * 2L).toArray();\n"
                + "    if (!Arrays.equals(projected, new long[] {20L, 60L})) throw new AssertionError(\"projection\");\n"
                + "    if (!Arrays.equals(table.enabled.toArray(), new boolean[] {true, false, true})) throw new AssertionError(\"boolean exact\");\n"
                + "    if (!Arrays.equals(table.shortValue.toArray(), new short[] {3, 4, 5})) throw new AssertionError(\"short exact\");\n"
                + "    if (!Arrays.equals(table.priority.toArray(), new int[] {4, 1, 3})) throw new AssertionError(\"int exact\");\n"
                + "    if (!Arrays.equals(table.amount.toArray(), new long[] {10L, 20L, 30L})) throw new AssertionError(\"long exact\");\n"
                + "    if (!Arrays.equals(table.score.toArray(), new double[] {4.0d, 2.0d, Double.NaN})) throw new AssertionError(\"double exact\");\n"
                + "    if (table.byKind(\"a\").mapToLong(table.amount).sum() != 40L) throw new AssertionError(\"index projection\");\n"
                + "    String[] labels = table.map(v -> v.label()).distinct().sorted(Comparator.nullsFirst(String::compareTo)).toArray(String.class);\n"
                + "    if (labels.length != 2 || labels[0] != null || !\"x\".equals(labels[1])) throw new AssertionError(\"mapped reference\");\n"
                + "    CharSequence[] superLabels = table.map(v -> v.label()).toArray(CharSequence.class);\n"
                + "    if (superLabels.getClass() != CharSequence[].class || superLabels.length != 3) throw new AssertionError(\"mapped parent array\");\n"
                + "    Object[] objectLabels = table.map(v -> v.label()).toArray(Object.class);\n"
                + "    if (objectLabels.getClass() != Object[].class || objectLabels.length != 3) throw new AssertionError(\"mapped object array\");\n"
                + "    String[] allNull = table.map(v -> (String) null).toArray(String.class);\n"
                + "    if (allNull.getClass() != String[].class || allNull.length != 3 || allNull[0] != null) throw new AssertionError(\"mapped all-null array\");\n"
                + "    if (table.mapToInt(v -> v.priority()).filter(v -> v > 1).sum() != 7L) throw new AssertionError(\"mapped int\");\n"
                + "    byte[] bytes = table.code.map(v -> (byte) (v + 1)).distinct().sorted().toArray();\n"
                + "    if (!Arrays.equals(bytes, new byte[] {3, 4})) throw new AssertionError(\"byte exact\");\n"
                + "    char[] chars = table.letter.map(v -> (char) (v + 1)).sorted().toArray();\n"
                + "    if (!Arrays.equals(chars, new char[] {'b', 'c', 'd'})) throw new AssertionError(\"char exact\");\n"
                + "    float[] floats = table.ratio.map(v -> v * 2.0f).toArray();\n"
                + "    if (floats.length != 3 || floats[0] != 3.0f || Float.floatToIntBits(floats[1]) != Float.floatToIntBits(-0.0f) || !Float.isNaN(floats[2])) throw new AssertionError(\"float exact\");\n"
                + "    if (table.code.mapToLong(v -> v).sum() != 7L) throw new AssertionError(\"byte to long\");\n"
                + "    if (!table.label.map(v -> v == null ? \"null\" : v).toList().add(\"detached\")) throw new AssertionError(\"modifiable list\");\n"
                + "    Event[] nullableAscending = table.sortedBy(table.label.asc()).toArray();\n"
                + "    if (nullableAscending[0].id() != 2L || nullableAscending[1].id() != 1L || nullableAscending[2].id() != 3L) throw new AssertionError(\"nullable ascending\");\n"
                + "    Event[] nullableDescending = table.sortedBy(table.label.desc()).toArray();\n"
                + "    if (nullableDescending[2].id() != 2L) throw new AssertionError(\"nullable descending\");\n"
                + "    Event[] tied = table.sortedBy(table.kind.asc().then(table.priority.desc())).toArray();\n"
                + "    if (tied[0].id() != 1L || tied[1].id() != 3L || tied[2].id() != 2L) throw new AssertionError(\"lexicographic order\");\n"
                + "    Event[] top = table.top(2L, table.priority.desc()).toArray();\n"
                + "    Event[] sortedLimited = table.sortedBy(table.priority.desc()).limit(2L).toArray();\n"
                + "    if (top.length != sortedLimited.length || top[0].id() != sortedLimited[0].id() || top[1].id() != sortedLimited[1].id()) throw new AssertionError(\"top equivalence\");\n"
                + "    EventTable empty = Soma.createGroup().eventTable();\n"
                + "    if (empty.anyMatch(v -> true) || !empty.allMatch(v -> false) || !empty.noneMatch(v -> true)) throw new AssertionError(\"empty matches\");\n"
                + "    if (empty.map(v -> v.label()).toArray(String.class).getClass() != String[].class) throw new AssertionError(\"empty mapped type\");\n"
                + "    SomaLongSummary emptyLong = empty.amount.summaryStatistics();\n"
                + "    SomaDoubleSummary emptyDouble = empty.score.summaryStatistics();\n"
                + "    if (emptyLong.count() != 0L || emptyLong.min() != 0L || emptyLong.max() != 0L || emptyLong.sum() != 0L || emptyLong.average() != 0.0d) throw new AssertionError(\"empty long summary\");\n"
                + "    if (emptyDouble.count() != 0L || emptyDouble.min() != 0.0d || emptyDouble.max() != 0.0d || emptyDouble.sum() != 0.0d || emptyDouble.average() != 0.0d) throw new AssertionError(\"empty double summary\");\n"
                + "    try { table.label.sorted().findFirst(); throw new AssertionError(\"nullable optional\"); } catch (SomaOperationException expected) { if (expected.code() != SomaFailureCode.NULL_VALUE_UNSUPPORTED) throw expected; }\n"
                + "    MappedStream<String> unclaimed = table.map(v -> v.label());\n"
                + "    try { unclaimed.toArray(null); throw new AssertionError(\"null component\"); } catch (SomaOperationException expected) { if (expected.code() != SomaFailureCode.INVALID_ARGUMENT) throw expected; }\n"
                + "    if (unclaimed.count() != 3L) throw new AssertionError(\"validation claimed pipeline\");\n"
                + "    try { table.map(v -> v.label()).toArray(int.class); throw new AssertionError(\"primitive component\"); } catch (SomaOperationException expected) { if (expected.code() != SomaFailureCode.INVALID_ARGUMENT) throw expected; }\n"
                + "    try { table.map(v -> v.label()).toArray(Integer.class); throw new AssertionError(\"incompatible component\"); } catch (SomaOperationException expected) { if (expected.code() != SomaFailureCode.INVALID_ARGUMENT) throw expected; }\n"
                + "    if (!table.filter(table.id.gt(1L))._explain().contains(\"physicalSource\")) throw new AssertionError(\"explain\");\n"
                + "    final int[] callbackCalls = new int[1];\n"
                + "    String rowExplain = table.filter(v -> { callbackCalls[0]++; return true; })._explain();\n"
                + "    if (callbackCalls[0] != 0 || !rowExplain.contains(\"callbackBarrier=true\")) throw new AssertionError(\"row explain callback\");\n"
                + "    table.map(v -> { callbackCalls[0]++; return v.label(); }).distinct()._explain();\n"
                + "    if (callbackCalls[0] != 0) throw new AssertionError(\"mapped explain callback\");\n"
                + "    table.mapToInt(v -> { callbackCalls[0]++; return v.priority(); }).sorted()._explain();\n"
                + "    if (callbackCalls[0] != 0) throw new AssertionError(\"primitive explain callback\");\n"
                + "    table.filter(v -> { callbackCalls[0]++; return true; }).limit(1L).sortedBy(table.id.asc()).count();\n"
                + "    if (callbackCalls[0] != 1) throw new AssertionError(\"row short circuit\");\n"
                + "    callbackCalls[0] = 0;\n"
                + "    table.mapToInt(v -> { callbackCalls[0]++; return v.priority(); }).limit(1L).sorted().toArray();\n"
                + "    if (callbackCalls[0] != 1) throw new AssertionError(\"primitive short circuit\");\n"
                + "    callbackCalls[0] = 0;\n"
                + "    table.map(v -> { callbackCalls[0]++; return v.label(); }).limit(1L).sorted(Comparator.nullsFirst(String::compareTo)).toList();\n"
                + "    if (callbackCalls[0] != 1) throw new AssertionError(\"mapped short circuit\");\n"
                + "    callbackCalls[0] = 0;\n"
                + "    table.mapToInt(v -> { callbackCalls[0]++; return v.priority(); }).limit(0L).count();\n"
                + "    if (callbackCalls[0] != 0) throw new AssertionError(\"zero limit\");\n"
                + "    EventTable wideAverage = Soma.createGroup().eventTable();\n"
                + "    wideAverage.add(new Event(1L, null, true, (byte) 0, (short) 0, 'a', 0, Long.MAX_VALUE, 0.0f, 1.0d, null));\n"
                + "    wideAverage.add(new Event(2L, null, true, (byte) 0, (short) 0, 'a', 0, Long.MAX_VALUE, 0.0f, 2.0d, null));\n"
                + "    if (wideAverage.amount.average().getAsDouble() != (double) Long.MAX_VALUE) throw new AssertionError(\"wide average\");\n"
                + "    try { wideAverage.amount.sum(); throw new AssertionError(\"sum overflow\"); }\n"
                + "    catch (SomaOperationException expected) { if (expected.code() != SomaFailureCode.ARITHMETIC_OVERFLOW) throw expected; }\n"
                + "    if (Double.doubleToLongBits(wideAverage.score.sum()) != Double.doubleToLongBits(wideAverage.mapToDouble(v -> v.score()).sum())) throw new AssertionError(\"floating canonical drift\");\n"
                + "    EventTable cancelling = Soma.createGroup().eventTable();\n"
                + "    cancelling.add(new Event(1L, null, true, (byte) 0, (short) 0, 'a', 0, Long.MAX_VALUE, 0.0f, 0.0d, null));\n"
                + "    cancelling.add(new Event(2L, null, true, (byte) 0, (short) 0, 'a', 0, Long.MAX_VALUE, 0.0f, 0.0d, null));\n"
                + "    cancelling.add(new Event(3L, null, true, (byte) 0, (short) 0, 'a', 0, -Long.MAX_VALUE, 0.0f, 0.0d, null));\n"
                + "    cancelling.add(new Event(4L, null, true, (byte) 0, (short) 0, 'a', 0, -Long.MAX_VALUE, 0.0f, 0.0d, null));\n"
                + "    if (cancelling.amount.sum() != 0L) throw new AssertionError(\"signed128 final range\");\n"
                + "    if (table.score.min().getAsDouble() != 2.0d || !Double.isNaN(table.score.max().getAsDouble())) throw new AssertionError(\"floating extrema\");\n"
                + "    try { table.map(v -> v).count(); throw new AssertionError(\"borrowed view escaped\"); }\n"
                + "    catch (SomaOperationException expected) { if (expected.code() != SomaFailureCode.CALLBACK_SCOPE_VIOLATION) throw expected; }\n"
                + "  }\n"
                + "}\n";
    }

    private static void invoke(Method method) throws Exception {
        method.setAccessible(true);
        try {
            method.invoke(null);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new AssertionError(cause);
        }
    }
}
