package io.github.somaruntime.soma.processor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class I2GeneratedSurfaceScaleTest {

    private static final int TABLE_COUNT = 112;

    @Test
    void oneCompositionWithMoreThanOneHundredTablesCompilesAsOneSurface() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("example/i2scale/schema/package-info.java",
                "@io.github.somaruntime.soma.SomaSchema\n"
                        + "package example.i2scale.schema;\n");
        for (int ordinal = 0; ordinal < TABLE_COUNT; ordinal++) {
            String simpleName = String.format(Locale.ROOT, "Entity%03d", ordinal);
            sources.put("example/i2scale/schema/" + simpleName + ".java",
                    "package example.i2scale.schema;\n"
                            + "import io.github.somaruntime.soma.*;\n"
                            + "@SomaTable(defaultCapacity = 0) final class "
                            + simpleName + " {\n"
                            + "  @SomaKey long id;\n"
                            + "  @SomaIndex long groupId;\n"
                            + "  @SomaIndex String label;\n"
                            + "  @SomaField int value;\n"
                            + "}\n");
        }

        long started = System.nanoTime();
        try (CompilerTestSupport.Compilation compilation = CompilerTestSupport.compile(
                sources, true, new SomaProcessor())) {
            long elapsed = System.nanoTime() - started;
            assertTrue(compilation.success(), compilation.diagnostics().toString());
            assertTrue(compilation.generatedSourceExists("example/i2scale/Entity000.java"));
            assertTrue(compilation.generatedSourceExists("example/i2scale/Entity000Table.java"));
            assertTrue(compilation.generatedSourceExists("example/i2scale/Entity111.java"));
            assertTrue(compilation.generatedSourceExists("example/i2scale/Entity111Table.java"));
            String manifest = compilation.classOutput(
                    "META-INF/soma/example.i2scale.schema.properties");
            assertTrue(manifest.contains("example.i2scale.Entity000Table"));
            assertTrue(manifest.contains("example.i2scale.Entity111Table"));

            long sourceBytes = compilation.generatedSourceBytes();
            List<byte[]> classFiles = compilation.classFilesUnder("example/i2scale");
            long classBytes = 0L;
            int maxMethods = 0;
            int maxConstantPoolEntries = 0;
            for (byte[] classFile : classFiles) {
                ClassFileMetrics metrics = ClassFileMetrics.read(classFile);
                classBytes += classFile.length;
                maxMethods = Math.max(maxMethods, metrics.methods);
                maxConstantPoolEntries = Math.max(
                        maxConstantPoolEntries, metrics.constantPoolEntries);
            }
            assertTrue(sourceBytes > 0L);
            assertTrue(classBytes > 0L);
            assertTrue(classFiles.size() > TABLE_COUNT * 2);
            assertTrue(maxMethods < 32768,
                    "generated class consumed at least half the JVM method-count domain");
            assertTrue(maxConstantPoolEntries < 32768,
                    "generated class consumed at least half the JVM constant-pool domain");
            System.out.println("i2-generated-surface-profile: tables=" + TABLE_COUNT
                    + " fields=" + (TABLE_COUNT * 4)
                    + " indexes=" + (TABLE_COUNT * 2)
                    + " elapsedMillis=" + (elapsed / 1_000_000L)
                    + " generatedSourceBytes=" + sourceBytes
                    + " classFiles=" + classFiles.size()
                    + " classBytes=" + classBytes
                    + " maxMethods=" + maxMethods
                    + " maxConstantPoolEntries=" + maxConstantPoolEntries);
        }
    }

    private static final class ClassFileMetrics {
        private final int constantPoolEntries;
        private final int methods;

        private ClassFileMetrics(int constantPoolEntries, int methods) {
            this.constantPoolEntries = constantPoolEntries;
            this.methods = methods;
        }

        private static ClassFileMetrics read(byte[] bytes) {
            Cursor cursor = new Cursor(bytes);
            if (cursor.u4() != 0xcafebabeL) {
                throw new AssertionError("invalid generated class file");
            }
            cursor.skip(4);
            int constantPoolCount = cursor.u2();
            for (int entry = 1; entry < constantPoolCount; entry++) {
                int tag = cursor.u1();
                switch (tag) {
                    case 1: cursor.skip(cursor.u2()); break;
                    case 3:
                    case 4: cursor.skip(4); break;
                    case 5:
                    case 6: cursor.skip(8); entry++; break;
                    case 7:
                    case 8:
                    case 16:
                    case 19:
                    case 20: cursor.skip(2); break;
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 17:
                    case 18: cursor.skip(4); break;
                    case 15: cursor.skip(3); break;
                    default: throw new AssertionError("unknown constant-pool tag " + tag);
                }
            }
            cursor.skip(6);
            cursor.skip(2 * cursor.u2());
            int fields = cursor.u2();
            for (int field = 0; field < fields; field++) cursor.skipMember();
            int methods = cursor.u2();
            return new ClassFileMetrics(constantPoolCount - 1, methods);
        }
    }

    private static final class Cursor {
        private final byte[] bytes;
        private int offset;

        private Cursor(byte[] bytes) {
            this.bytes = bytes;
        }

        private int u1() {
            require(1);
            return bytes[offset++] & 0xff;
        }

        private int u2() {
            return (u1() << 8) | u1();
        }

        private long u4() {
            return ((long) u2() << 16) | u2();
        }

        private void skip(long count) {
            if (count < 0L || count > Integer.MAX_VALUE) {
                throw new AssertionError("invalid class-file span");
            }
            require((int) count);
            offset += (int) count;
        }

        private void skipMember() {
            skip(6);
            int attributes = u2();
            for (int attribute = 0; attribute < attributes; attribute++) {
                skip(2);
                skip(u4());
            }
        }

        private void require(int count) {
            if (count < 0 || offset > bytes.length - count) {
                throw new AssertionError("truncated generated class file");
            }
        }
    }
}
