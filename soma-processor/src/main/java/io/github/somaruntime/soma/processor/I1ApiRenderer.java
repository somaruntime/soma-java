package io.github.somaruntime.soma.processor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Renders the deliberately narrow I1 primitive keyed vertical slice. */
final class I1ApiRenderer {
    private I1ApiRenderer() {
    }

    static List<SchemaModel.GeneratedFile> render(SchemaModel.Composition composition) {
        SchemaModel.Type table = composition.tables.get(0);
        SchemaModel.Field key = null;
        SchemaModel.Field payload = null;
        for (SchemaModel.Field field : table.fields) {
            if (field.role == SchemaModel.FieldRole.KEY) {
                key = field;
            } else {
                payload = field;
            }
        }
        String namespace = composition.generatedNamespace;
        String tableName = table.simpleName;
        String accessor = lowerFirst(tableName) + "Table";
        String tableType = tableName + "Table";
        String objectType = tableName;

        List<SchemaModel.GeneratedFile> files =
                new ArrayList<SchemaModel.GeneratedFile>();
        add(files, namespace + ".Soma", renderSoma(namespace, tableType, accessor));
        add(files, namespace + ".SomaGroup",
                renderGroup(namespace, tableType, accessor));
        add(files, namespace + "." + objectType,
                renderObject(namespace, objectType, key.name, payload.name));
        add(files, namespace + "." + tableType,
                renderTable(namespace, objectType, tableType, accessor,
                        key.name, payload.name, table.defaultCapacity));
        return Collections.unmodifiableList(files);
    }

    private static void add(
            List<SchemaModel.GeneratedFile> files,
            String fqn,
            String source) {
        files.add(new SchemaModel.GeneratedFile(fqn, source, sha256(source)));
    }

    private static String renderSoma(
            String namespace,
            String tableType,
            String accessor) {
        StringBuilder source = new StringBuilder(header(namespace));
        source.append("public final class Soma {\n");
        source.append("    static {\n");
        source.append("        ").append(namespace)
                .append(".internal.SomaGeneratedComposition.verifyRuntime();\n");
        source.append("    }\n");
        source.append("    private static final Token TOKEN = new Token();\n");
        source.append("    private static volatile SomaGroup DEFAULT_GROUP;\n");
        source.append("\n");
        source.append("    private Soma() {\n");
        source.append("    }\n\n");
        source.append("    public static SomaGroup defaultGroup() {\n");
        source.append("        SomaGroup group = DEFAULT_GROUP;\n");
        source.append("        if (group == null) {\n");
        source.append("            synchronized (Soma.class) {\n");
        source.append("                group = DEFAULT_GROUP;\n");
        source.append("                if (group == null) {\n");
        source.append("                    group = SomaGroup.create(TOKEN);\n");
        source.append("                    DEFAULT_GROUP = group;\n");
        source.append("                }\n");
        source.append("            }\n");
        source.append("        }\n");
        source.append("        return group;\n");
        source.append("    }\n\n");
        source.append("    public static SomaGroup createGroup() {\n");
        source.append("        return SomaGroup.create(TOKEN);\n");
        source.append("    }\n\n");
        source.append("    public static ").append(tableType).append(' ')
                .append(accessor).append("() {\n");
        source.append("        return defaultGroup().").append(accessor).append("();\n");
        source.append("    }\n\n");
        source.append("    public static void configure(\n");
        source.append("            io.github.somaruntime.soma.SomaConfiguration configuration) {\n");
        source.append("        io.github.somaruntime.soma.internal.SomaRuntimeAccess.configure(configuration);\n");
        source.append("    }\n");
        source.append("\n    static final class Token {\n");
        source.append("        private Token() {\n        }\n    }\n");
        source.append("}\n");
        return source.toString();
    }

    private static String renderGroup(
            String namespace,
            String tableType,
            String accessor) {
        StringBuilder source = new StringBuilder(header(namespace));
        source.append("public final class SomaGroup {\n");
        source.append("    private final io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.GroupRuntime runtime;\n");
        source.append("    private final ").append(tableType).append(" table;\n\n");
        source.append("    private SomaGroup(Soma.Token token) {\n");
        source.append("        io.github.somaruntime.soma.internal.SomaRuntimeAccess.freezeForRuntimeAccess();\n");
        source.append("        runtime = new io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.GroupRuntime();\n");
        source.append("        table = ").append(tableType).append(".create(token, runtime);\n");
        source.append("    }\n\n");
        source.append("    static SomaGroup create(Soma.Token token) {\n");
        source.append("        if (token == null) {\n");
        source.append("            throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
        source.append("                    io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT,\n");
        source.append("                    io.github.somaruntime.soma.SomaOperation.CONFIGURE,\n");
        source.append("                    \"composition capability is required\", null);\n");
        source.append("        }\n");
        source.append("        return new SomaGroup(token);\n");
        source.append("    }\n\n");
        source.append("    public ").append(tableType).append(' ').append(accessor)
                .append("() {\n");
        source.append("        return table;\n");
        source.append("    }\n");
        source.append("}\n");
        return source.toString();
    }

    private static String renderObject(
            String namespace,
            String objectType,
            String keyName,
            String payloadName) {
        StringBuilder source = new StringBuilder(header(namespace));
        source.append("public final class ").append(objectType).append(" {\n");
        source.append("    private long ").append(keyName).append(';').append('\n');
        source.append("    private long ").append(payloadName).append(';').append('\n');
        source.append("\n");
        source.append("    public ").append(objectType).append("() {\n");
        source.append("    }\n\n");
        source.append("    public ").append(objectType).append("(long ")
                .append(keyName).append(", long ").append(payloadName).append(") {\n");
        source.append("        this.").append(keyName).append(" = ").append(keyName).append(";\n");
        source.append("        this.").append(payloadName).append(" = ").append(payloadName).append(";\n");
        source.append("    }\n\n");
        source.append("    public long ").append(keyName).append("() {\n");
        source.append("        return ").append(keyName).append(";\n");
        source.append("    }\n\n");
        source.append("    public void ").append(keyName).append("(long value) {\n");
        source.append("        this.").append(keyName).append(" = value;\n");
        source.append("    }\n\n");
        source.append("    public long ").append(payloadName).append("() {\n");
        source.append("        return ").append(payloadName).append(";\n");
        source.append("    }\n\n");
        source.append("    public void ").append(payloadName).append("(long value) {\n");
        source.append("        this.").append(payloadName).append(" = value;\n");
        source.append("    }\n");
        source.append("}\n");
        return source.toString();
    }

    private static String renderTable(
            String namespace,
            String objectType,
            String tableType,
            String accessor,
            String keyName,
            String payloadName,
            long defaultCapacity) {
        StringBuilder source = new StringBuilder(header(namespace));
        source.append("import java.util.Optional;\n");
        source.append("import java.util.concurrent.atomic.AtomicBoolean;\n");
        source.append("import java.util.function.Consumer;\n\n");
        source.append("public final class ").append(tableType).append(" {\n");
        source.append("    public final ").append(capital(keyName)).append("Field ")
                .append(keyName).append(" = new ").append(capital(keyName))
                .append("Field();\n");
        source.append("    public final ").append(capital(payloadName)).append("Field ")
                .append(payloadName).append(" = new ").append(capital(payloadName))
                .append("Field();\n\n");
        source.append("    private final io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime runtime;\n\n");
        source.append("    private final Object expressionOwner = new Object();\n\n");
        source.append("    private ").append(tableType).append("(\n");
        source.append("            io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.GroupRuntime group) {\n");
        source.append("        runtime = new io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime(group, ")
                .append(defaultCapacity).append("L);\n");
        source.append("    }\n\n");
        source.append("    static ").append(tableType).append(" create(Soma.Token token,\n");
        source.append("            io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.GroupRuntime group) {\n");
        source.append("        if (token == null || group == null) {\n");
        source.append("            throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
        source.append("                    io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT,\n");
        source.append("                    io.github.somaruntime.soma.SomaOperation.CONFIGURE,\n");
        source.append("                    \"composition capability and Group are required\", null);\n");
        source.append("        }\n");
        source.append("        return new ").append(tableType).append("(group);\n");
        source.append("    }\n\n");
        source.append("    public long size() {\n        return runtime.size();\n    }\n\n");
        source.append("    public long capacity() {\n        return runtime.capacity();\n    }\n\n");
        source.append("    public void reserve(long expectedRows) {\n        runtime.reserve(expectedRows);\n    }\n\n");
        source.append("    public void add(").append(objectType).append(" value) {\n");
        source.append("        if (value == null) {\n");
        source.append("            throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
        source.append("                    io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT,\n");
        source.append("                    io.github.somaruntime.soma.SomaOperation.ADD,\n");
        source.append("                    \"value must not be null\", null);\n");
        source.append("        }\n");
        source.append("        runtime.add(value.").append(keyName).append("(), value.")
                .append(payloadName).append("());\n");
        source.append("    }\n\n");
        source.append("    public Optional<").append(objectType).append("> find(long key) {\n");
        source.append("        io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.Query query = runtime.beginQuery(\n");
        source.append("                io.github.somaruntime.soma.SomaOperation.FIND);\n");
        source.append("        long locator = query.findLocator(key);\n");
        source.append("        if (locator < 0L) {\n            query.close();\n            return Optional.empty();\n        }\n");
        source.append("        try {\n");
        source.append("            return Optional.of(new ").append(objectType).append("(\n");
        source.append("                    query.keyAt(locator), query.payloadAt(locator)));\n");
        source.append("        } finally {\n            query.close();\n        }\n");
        source.append("    }\n\n");
        source.append("    public ").append(objectType).append(" get(long key) {\n");
        source.append("        Optional<").append(objectType).append("> found = find(key);\n");
        source.append("        if (!found.isPresent()) {\n");
        source.append("            throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
        source.append("                    io.github.somaruntime.soma.SomaFailureCode.MISSING_KEY,\n");
        source.append("                    io.github.somaruntime.soma.SomaOperation.GET,\n");
        source.append("                    \"key is not present\", null);\n");
        source.append("        }\n");
        source.append("        return found.get();\n");
        source.append("    }\n\n");
        source.append("    public io.github.somaruntime.soma.UpdateResult update(\n");
        source.append("            long key, Consumer<? super Editor> updater) {\n");
        source.append("        if (updater == null) {\n");
        source.append("            throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
        source.append("                    io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT,\n");
        source.append("                    io.github.somaruntime.soma.SomaOperation.UPDATE,\n");
        source.append("                    \"updater must not be null\", null);\n");
        source.append("        }\n");
        source.append("        io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.PointUpdate state = runtime.beginUpdate(key);\n");
        source.append("        if (!state.matched()) {\n");
        source.append("            return io.github.somaruntime.soma.internal.SomaRuntimeAccess.updateResult(0L, 0L);\n");
        source.append("        }\n");
        source.append("        Editor editor = new Editor(state);\n");
        source.append("        long before = state.payload();\n");
        source.append("        try {\n");
        source.append("            updater.accept(editor);\n");
        source.append("            long after = state.payload();\n");
        source.append("            state.prepareInPlace();\n");
        source.append("            state.commitPrevalidated();\n");
        source.append("            return io.github.somaruntime.soma.internal.SomaRuntimeAccess.updateResult(\n");
        source.append("                    1L, before == after ? 0L : 1L);\n");
        source.append("        } catch (io.github.somaruntime.soma.SomaOperationException exception) {\n");
        source.append("            state.abort();\n");
        source.append("            throw exception;\n");
        source.append("        } catch (Throwable exception) {\n");
        source.append("            state.abort();\n");
        source.append("            if (exception instanceof Error) { throw (Error) exception; }\n");
        source.append("            throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
        source.append("                    io.github.somaruntime.soma.SomaFailureCode.CALLBACK_FAILED,\n");
        source.append("                    io.github.somaruntime.soma.SomaOperation.UPDATE,\n");
        source.append("                    \"update callback failed\", exception);\n");
        source.append("        }\n");
        source.append("    }\n\n");
        source.append("    public Selection selectAll() {\n        return new Selection(null, null, null);\n    }\n\n");
        source.append("    public Selection filter(io.github.somaruntime.soma.SomaExpression<View> expression) {\n");
        source.append("        return selectAll().filter(expression);\n    }\n\n");
        source.append("    public Selection filter(io.github.somaruntime.soma.SomaPredicate<? super View> callback) {\n");
        source.append("        return selectAll().filter(callback);\n    }\n\n");
        source.append("    public static class View {\n");
        source.append("        private final io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.Query query;\n");
        source.append("        private final io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.PointUpdate update;\n");
        source.append("        private long locator;\n");
        source.append("        private View(io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.Query query, long locator) {\n");
        source.append("            this.query = query; this.locator = locator; this.update = null;\n        }\n");
        source.append("        private View(io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.PointUpdate update) {\n");
        source.append("            this.query = null; this.locator = -1L; this.update = update;\n        }\n");
        source.append("        public long ").append(keyName).append("() { return update == null ? query.keyAt(locator) : update.key(); }\n");
        source.append("        public long ").append(payloadName).append("() { return update == null ? query.payloadAt(locator) : update.payload(); }\n");
        source.append("        public ").append(objectType).append(" fetch() {\n");
        source.append("            return new ").append(objectType).append('(').append(keyName)
                .append("(), ").append(payloadName).append("());\n        }\n");
        source.append("        protected final void set").append(capital(payloadName)).append("(long value) { update.payload(value); }\n");
        source.append("    }\n\n");
        source.append("    public static final class Editor extends View {\n");
        source.append("        private Editor(io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.PointUpdate state) { super(state); }\n");
        source.append("        public void ").append(payloadName).append("(long value) { set")
                .append(capital(payloadName)).append("(value); }\n");
        source.append("    }\n\n");
        source.append("    public final class Selection {\n");
        source.append("        private final Selection parent;\n");
        source.append("        private final io.github.somaruntime.soma.SomaExpression<View> expression;\n");
        source.append("        private final io.github.somaruntime.soma.SomaPredicate<? super View> callback;\n");
        source.append("        private final AtomicBoolean claimed = new AtomicBoolean();\n");
        source.append("        private Selection(Selection parent, io.github.somaruntime.soma.SomaExpression<View> expression,\n");
        source.append("                io.github.somaruntime.soma.SomaPredicate<? super View> callback) {\n");
        source.append("            this.parent = parent; this.expression = expression; this.callback = callback;\n");
        source.append("        }\n");
        source.append("        public Selection filter(io.github.somaruntime.soma.SomaExpression<View> value) {\n");
        source.append("            if (value == null) { throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
        source.append("                    io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT,\n");
        source.append("                    io.github.somaruntime.soma.SomaOperation.QUERY, \"expression must not be null\", null); }\n");
        source.append("            io.github.somaruntime.soma.internal.SomaRuntimeAccess.validate(value, expressionOwner);\n");
        source.append("            claim();\n");
        source.append("            return new Selection(this, value, null);\n");
        source.append("        }\n");
        source.append("        public Selection filter(io.github.somaruntime.soma.SomaPredicate<? super View> value) {\n");
        source.append("            if (value == null) { throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
        source.append("                    io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT,\n");
        source.append("                    io.github.somaruntime.soma.SomaOperation.QUERY, \"callback must not be null\", null); }\n");
        source.append("            claim();\n");
        source.append("            return new Selection(this, null, value);\n");
        source.append("        }\n");
        source.append("        public long count() {\n");
        source.append("            claim();\n");
        source.append("            io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.Query query = runtime.beginQuery();\n");
        source.append("            View view = new View(query, 0L);\n");
        source.append("            long count = 0L;\n");
        source.append("            try {\n");
        source.append("                for (long locator = 0L; locator < query.size(); locator++) {\n");
        source.append("                    view.locator = locator;\n");
        source.append("                    if (matches(view)) { count = Math.addExact(count, 1L); }\n");
        source.append("                }\n");
        source.append("                return count;\n");
        source.append("            } catch (ArithmeticException exception) {\n");
        source.append("                throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
        source.append("                        io.github.somaruntime.soma.SomaFailureCode.ARITHMETIC_OVERFLOW,\n");
        source.append("                        io.github.somaruntime.soma.SomaOperation.QUERY, \"checked count overflow\", exception);\n");
        source.append("            } catch (io.github.somaruntime.soma.SomaOperationException exception) {\n");
        source.append("                throw exception;\n");
        source.append("            } catch (RuntimeException exception) {\n");
        source.append("                throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
        source.append("                        io.github.somaruntime.soma.SomaFailureCode.CALLBACK_FAILED,\n");
        source.append("                        io.github.somaruntime.soma.SomaOperation.QUERY, \"query callback failed\", exception);\n");
        source.append("            } finally {\n");
        source.append("                query.close();\n");
        source.append("            }\n");
        source.append("        }\n");
        source.append("        private boolean matches(View value) {\n");
        source.append("            if (parent != null && !parent.matches(value)) { return false; }\n");
        source.append("            if (expression != null && !io.github.somaruntime.soma.internal.SomaRuntimeAccess.evaluate(expression, value, expressionOwner)) { return false; }\n");
        source.append("            return callback == null || callback.test(value);\n");
        source.append("        }\n");
        source.append("        private void claim() {\n");
        source.append("            if (!claimed.compareAndSet(false, true)) {\n");
        source.append("                throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
        source.append("                        io.github.somaruntime.soma.SomaFailureCode.PIPELINE_ALREADY_CONSUMED,\n");
        source.append("                        io.github.somaruntime.soma.SomaOperation.QUERY, \"selection already consumed\", null);\n");
        source.append("            }\n");
        source.append("        }\n");
        source.append("    }\n\n");
        renderField(source, tableType, keyName, true);
        renderField(source, tableType, payloadName, false);
        source.append("}\n");
        return source.toString();
    }

    private static void renderField(
            StringBuilder source,
            String tableType,
            String name,
            boolean key) {
        String className = capital(name) + "Field";
        source.append("    public final class ").append(className)
                .append(" implements io.github.somaruntime.soma.")
                .append(key ? "SomaKeyableField" : "SomaFieldEndpoint")
                .append("<View, Long> {\n");
        source.append("        private ").append(className).append("() {\n        }\n\n");
        String[] operations = {"eq", "ne", "lt", "le", "gt", "ge"};
        for (String operation : operations) {
            source.append("        public io.github.somaruntime.soma.SomaExpression<View> ")
                    .append(operation).append("(final long value) {\n");
            source.append("            return io.github.somaruntime.soma.internal.SomaRuntimeAccess.expression(expressionOwner,\n");
            source.append("                    new io.github.somaruntime.soma.internal.SomaExpressionNode.Evaluator<View>() {\n");
            source.append("                        @Override public boolean test(View row) {\n");
            source.append("                            return row.").append(name).append("() ")
                    .append(operator(operation)).append(" value;\n");
            source.append("                        }\n                    });\n        }\n\n");
        }
        source.append("    }\n\n");
    }

    private static String operator(String operation) {
        if ("eq".equals(operation)) {
            return "==";
        }
        if ("ne".equals(operation)) {
            return "!=";
        }
        if ("lt".equals(operation)) {
            return "<";
        }
        if ("le".equals(operation)) {
            return "<=";
        }
        if ("gt".equals(operation)) {
            return ">";
        }
        return ">=";
    }

    private static String header(String namespace) {
        return new StringBuilder()
                .append("package ").append(namespace).append(";\n\n")
                .append("// Generated by SOMA; do not edit.\n")
                .toString();
    }

    private static String lowerFirst(String value) {
        int codePoint = value.codePointAt(0);
        int lower = Character.toLowerCase(codePoint);
        return new String(Character.toChars(lower)) + value.substring(
                Character.charCount(codePoint));
    }

    private static String capital(String value) {
        int codePoint = value.codePointAt(0);
        int upper = Character.toUpperCase(codePoint);
        return new String(Character.toChars(upper)) + value.substring(
                Character.charCount(codePoint));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexadecimal = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                hexadecimal.append(String.format("%02x", Integer.valueOf(item & 0xff)));
            }
            return hexadecimal.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 must be available", exception);
        }
    }
}
