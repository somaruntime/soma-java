package io.github.somaruntime.soma.processor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Renders the bounded I2 scalar-field breadth slice. */
final class I2ApiRenderer {
    private I2ApiRenderer() {
    }

    static List<SchemaModel.GeneratedFile> render(SchemaModel.Composition composition) {
        SchemaModel.Type table = composition.tables.get(0);
        String namespace = composition.generatedNamespace;
        String tableName = table.simpleName;
        String tableType = tableName + "Table";
        String accessor = lowerFirst(tableName) + "Table";
        String objectType = tableName;
        List<SchemaModel.GeneratedFile> files = new ArrayList<SchemaModel.GeneratedFile>();
        add(files, namespace + ".Soma", renderSoma(namespace, tableType, accessor));
        add(files, namespace + ".SomaGroup", renderGroup(namespace, tableType, accessor));
        add(files, namespace + "." + objectType, renderObject(namespace, objectType, table));
        add(files, namespace + "." + tableType,
                renderTable(namespace, objectType, tableType, table));
        return Collections.unmodifiableList(files);
    }

    private static void add(List<SchemaModel.GeneratedFile> files, String fqn, String source) {
        files.add(new SchemaModel.GeneratedFile(fqn, source, sha256(source)));
    }

    private static String renderSoma(String namespace, String tableType, String accessor) {
        StringBuilder source = new StringBuilder(header(namespace));
        source.append("public final class Soma {\n");
        source.append("    static { ").append(namespace)
                .append(".internal.SomaGeneratedComposition.verifyRuntime(); }\n");
        source.append("    private static final Token TOKEN = new Token();\n");
        source.append("    private static volatile SomaGroup DEFAULT_GROUP;\n\n");
        source.append("    private Soma() { }\n\n");
        source.append("    public static SomaGroup defaultGroup() {\n");
        source.append("        SomaGroup group = DEFAULT_GROUP;\n");
        source.append("        if (group == null) { synchronized (Soma.class) {\n");
        source.append("            group = DEFAULT_GROUP;\n");
        source.append("            if (group == null) { group = SomaGroup.create(TOKEN, true); DEFAULT_GROUP = group; }\n");
        source.append("        } }\n        return group;\n    }\n\n");
        source.append("    public static SomaGroup createGroup() { return SomaGroup.create(TOKEN, false); }\n\n");
        source.append("    public static io.github.somaruntime.soma.SomaMetadata _metadata() {\n");
        source.append("        return io.github.somaruntime.soma.internal.SomaRuntimeAccess.somaMetadata();\n");
        source.append("    }\n\n");
        source.append("    public static ").append(tableType).append(' ').append(accessor)
                .append("() { return defaultGroup().").append(accessor).append("(); }\n\n");
        source.append("    public static void configure(io.github.somaruntime.soma.SomaConfiguration configuration) {\n");
        source.append("        io.github.somaruntime.soma.internal.SomaRuntimeAccess.configure(configuration);\n    }\n\n");
        source.append("    static final class Token { private Token() { } }\n");
        source.append("}\n");
        return source.toString();
    }

    private static String renderGroup(String namespace, String tableType, String accessor) {
        StringBuilder source = new StringBuilder(header(namespace));
        source.append("public final class SomaGroup {\n");
        source.append("    private final io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.GroupRuntime runtime;\n");
        source.append("    private final ").append(tableType).append(" table;\n");
        source.append("    private final boolean defaultGroup;\n\n");
        source.append("    private SomaGroup(Soma.Token token, boolean defaultGroup) {\n");
        source.append("        io.github.somaruntime.soma.internal.SomaRuntimeAccess.freezeForRuntimeAccess();\n");
        source.append("        this.defaultGroup = defaultGroup;\n");
        source.append("        runtime = new io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.GroupRuntime();\n");
        source.append("        table = ").append(tableType).append(".create(token, runtime);\n    }\n\n");
        source.append("    static SomaGroup create(Soma.Token token, boolean defaultGroup) {\n");
        source.append("        if (token == null) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
        source.append("            io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.CONFIGURE,\n");
        source.append("            \"composition capability is required\", null);\n");
        source.append("        return new SomaGroup(token, defaultGroup);\n    }\n\n");
        source.append("    public ").append(tableType).append(' ').append(accessor)
                .append("() { return table; }\n");
        source.append("    public io.github.somaruntime.soma.GroupMetadata _metadata() {\n");
        source.append("        return io.github.somaruntime.soma.internal.SomaRuntimeAccess.groupMetadata(defaultGroup, 1L);\n");
        source.append("    }\n");
        source.append("}\n");
        return source.toString();
    }

    private static String renderObject(String namespace, String objectType, SchemaModel.Type table) {
        StringBuilder source = new StringBuilder(header(namespace));
        source.append("public final class ").append(objectType).append(" {\n");
        for (SchemaModel.Field field : table.fields) {
            source.append("    private ").append(typeName(field)).append(' ')
                    .append(field.name).append(';').append('\n');
        }
        source.append('\n').append("    public ").append(objectType).append("() { }\n\n");
        source.append("    public ").append(objectType).append('(');
        for (int i = 0; i < table.fields.size(); i++) {
            if (i > 0) source.append(", ");
            SchemaModel.Field field = table.fields.get(i);
            source.append(typeName(field)).append(' ').append(field.name);
        }
        source.append(") {\n");
        for (SchemaModel.Field field : table.fields) {
            source.append("        this.").append(field.name).append(" = ").append(field.name).append(';').append('\n');
        }
        source.append("    }\n\n");
        for (SchemaModel.Field field : table.fields) {
            source.append("    public ").append(typeName(field)).append(' ').append(field.name)
                    .append("() { return ").append(field.name).append("; }\n");
            source.append("    public void ").append(field.name).append('(').append(typeName(field))
                    .append(" value) { this.").append(field.name).append(" = value; }\n\n");
        }
        source.append("}\n");
        return source.toString();
    }

    private static String renderTable(
            String namespace, String objectType, String tableType, SchemaModel.Type table) {
        StringBuilder source = new StringBuilder(header(namespace));
        source.append("import java.util.ArrayList;\nimport java.util.Arrays;\nimport java.util.List;\nimport java.util.Objects;\nimport java.util.Optional;\n");
        source.append("import java.util.concurrent.atomic.AtomicBoolean;\nimport java.util.function.Consumer;\nimport java.util.function.Function;\nimport java.util.function.ToLongFunction;\n\n");
        source.append("public final class ").append(tableType).append(" {\n");
        for (SchemaModel.Field field : table.fields) {
            source.append("    public final ").append(capital(field.name)).append("Field ")
                    .append(field.name).append(" = new ").append(capital(field.name)).append("Field();\n");
        }
        source.append('\n');
        source.append("    private static final io.github.somaruntime.soma.internal.ScalarTableRuntime.FieldSpec[] SPECS = new io.github.somaruntime.soma.internal.ScalarTableRuntime.FieldSpec[] {\n");
        for (SchemaModel.Field field : table.fields) {
            source.append("        new io.github.somaruntime.soma.internal.ScalarTableRuntime.FieldSpec(")
                    .append("io.github.somaruntime.soma.internal.ScalarTableRuntime.FieldKind.")
                    .append(field.storageKind.name()).append(", ")
                    .append(field.role == SchemaModel.FieldRole.KEY ? "true" : "false").append(", ")
                    .append(field.role == SchemaModel.FieldRole.INDEX ? "true" : "false").append(", ")
                    .append(isString(field) ? "true" : "false").append("),\n");
        }
        source.append("    };\n");
        source.append("    private static final int KEY_INDEX = ").append(keyIndex(table)).append(";\n");
        source.append("    private final io.github.somaruntime.soma.internal.ScalarTableRuntime runtime;\n");
        source.append("    private final Object expressionOwner = new Object();\n\n");
        source.append("    private ").append(tableType).append("(io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.GroupRuntime group) {\n");
        source.append("        runtime = new io.github.somaruntime.soma.internal.ScalarTableRuntime(group, ")
                .append(table.defaultCapacity).append("L, SPECS, KEY_INDEX);\n    }\n\n");
        source.append("    static ").append(tableType).append(" create(Soma.Token token, io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.GroupRuntime group) {\n");
        source.append("        if (token == null || group == null) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
        source.append("            io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.CONFIGURE,\n");
        source.append("            \"composition capability and Group are required\", null);\n");
        source.append("        return new ").append(tableType).append("(group);\n    }\n\n");
        source.append("    public long size() { return runtime.size(); }\n");
        source.append("    public long capacity() { return runtime.capacity(); }\n");
        source.append("    public void reserve(long expectedRows) { runtime.reserve(expectedRows); }\n\n");
        source.append("    public io.github.somaruntime.soma.TableMetadata _metadata() {\n");
        source.append("        return runtime.metadata(\"").append(table.simpleName).append("\");\n");
        source.append("    }\n\n");
        source.append("    public void add(").append(objectType).append(" value) {\n");
        source.append("        if (value == null) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
        source.append("            io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.ADD,\n");
        source.append("            \"value must not be null\", null);\n");
        source.append("        io.github.somaruntime.soma.internal.ScalarTableRuntime.Append append = runtime.beginAppend();\n");
        source.append("        try {\n");
        for (int i = 0; i < table.fields.size(); i++) {
            SchemaModel.Field field = table.fields.get(i);
            source.append("            append.").append(setter(field)).append('(').append(i).append(", value.")
                    .append(field.name).append("());\n");
        }
        source.append("            append.commit();\n        } catch (Throwable failure) { append.abort(); if (failure instanceof Error) throw (Error) failure; if (failure instanceof RuntimeException) throw (RuntimeException) failure; throw new IllegalStateException(failure); }\n");
        source.append("    }\n\n");
        if (keyIndex(table) >= 0) {
            SchemaModel.Field key = table.fields.get(keyIndex(table));
            source.append("    public Optional<").append(objectType).append("> find(").append(typeName(key)).append(" key) {\n");
            source.append("        io.github.somaruntime.soma.internal.ScalarTableRuntime.Query query = runtime.beginQuery(io.github.somaruntime.soma.SomaOperation.FIND);\n");
            source.append("        long locator = query.findLocator(key);\n        if (locator < 0L) { query.close(); return Optional.empty(); }\n");
            source.append("        try { return Optional.of(new ").append(objectType).append('(');
            appendQueryConstructor(source, table);
            source.append(")); } finally { query.close(); }\n    }\n\n");
            source.append("    public ").append(objectType).append(" get(").append(typeName(key)).append(" key) {\n");
            source.append("        Optional<").append(objectType).append("> value = find(key);\n        if (!value.isPresent()) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
            source.append("            io.github.somaruntime.soma.SomaFailureCode.MISSING_KEY, io.github.somaruntime.soma.SomaOperation.GET, \"key is not present\", null);\n");
            source.append("        return value.get();\n    }\n\n");
            source.append("    public io.github.somaruntime.soma.UpdateResult update(").append(typeName(key)).append(" key, Consumer<? super Editor> updater) {\n");
            source.append("        if (updater == null) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
            source.append("            io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.UPDATE, \"updater must not be null\", null);\n");
            source.append("        io.github.somaruntime.soma.internal.ScalarTableRuntime.PointUpdate state = runtime.beginUpdate(key);\n");
            source.append("        if (!state.matched()) return io.github.somaruntime.soma.internal.SomaRuntimeAccess.updateResult(0L, 0L);\n");
            source.append("        Editor editor = new Editor(state);\n        try { updater.accept(editor); state.prepare(); state.commit(); return io.github.somaruntime.soma.internal.SomaRuntimeAccess.updateResult(1L, state.changed() ? 1L : 0L);\n");
            source.append("        } catch (io.github.somaruntime.soma.SomaOperationException failure) { state.abort(); throw failure; } catch (Throwable failure) { state.abort(); if (failure instanceof Error) throw (Error) failure; throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.CALLBACK_FAILED, io.github.somaruntime.soma.SomaOperation.UPDATE, \"update callback failed\", failure); }\n    }\n\n");
            source.append("    public io.github.somaruntime.soma.RemoveResult remove(").append(typeName(key)).append(" key) { return runtime.remove(key); }\n\n");
        }
        source.append("    public Selection selectAll() { return new Selection(null, null, null, -1, null); }\n");
        source.append("    public Selection filter(io.github.somaruntime.soma.SomaExpression<View> expression) { return selectAll().filter(expression); }\n");
        source.append("    public Selection filter(io.github.somaruntime.soma.SomaPredicate<? super View> callback) { return selectAll().filter(callback); }\n\n");
        for (SchemaModel.Field field : table.fields) {
            if (field.role == SchemaModel.FieldRole.INDEX) {
                source.append("    public IndexSelection by").append(capital(field.name)).append('(')
                        .append(typeName(field)).append(" value) { return new IndexSelection(")
                        .append(findFieldIndex(table, field.name)).append(", value); }\n");
            }
        }
        source.append('\n');
        renderView(source, objectType, table);
        renderEditor(source, table);
        renderSelection(source, table);
        renderIndexSelection(source);
        renderGroupBy(source, table);
        for (int i = 0; i < table.fields.size(); i++) {
            renderField(source, table, i);
        }
        source.append("}\n");
        return source.toString();
    }

    private static void renderView(StringBuilder source, String objectType, SchemaModel.Type table) {
        source.append("    public static class View {\n");
        source.append("        protected final io.github.somaruntime.soma.internal.ScalarTableRuntime.Query query;\n");
        source.append("        protected final io.github.somaruntime.soma.internal.ScalarTableRuntime.PointUpdate update;\n");
        source.append("        private long locator;\n");
        source.append("        private View(io.github.somaruntime.soma.internal.ScalarTableRuntime.Query query, long locator) { this.query = query; this.locator = locator; this.update = null; }\n");
        source.append("        private View(io.github.somaruntime.soma.internal.ScalarTableRuntime.PointUpdate update) { this.query = null; this.locator = -1L; this.update = update; }\n");
        for (int i = 0; i < table.fields.size(); i++) {
            SchemaModel.Field field = table.fields.get(i);
            source.append("        public ").append(typeName(field)).append(' ').append(field.name).append("() { return ")
                    .append(readExpr(field, i)).append("; }\n");
        }
        source.append("        public ").append(objectType).append(" fetch() { return new ").append(objectType).append('(');
        for (int i = 0; i < table.fields.size(); i++) { if (i > 0) source.append(", "); source.append(table.fields.get(i).name).append("()"); }
        source.append("); }\n    }\n\n");
    }

    private static void renderEditor(StringBuilder source, SchemaModel.Type table) {
        source.append("    public static final class Editor extends View {\n");
        source.append("        private Editor(io.github.somaruntime.soma.internal.ScalarTableRuntime.PointUpdate update) { super(update); }\n");
        for (int i = 0; i < table.fields.size(); i++) {
            SchemaModel.Field field = table.fields.get(i);
            if (field.role == SchemaModel.FieldRole.KEY) continue;
            source.append("        public void ").append(field.name).append('(').append(typeName(field)).append(" value) { update().")
                    .append(setter(field)).append('(').append(i).append(", value); }\n");
        }
        source.append("        private io.github.somaruntime.soma.internal.ScalarTableRuntime.PointUpdate update() { return super.update; }\n");
        source.append("    }\n\n");
    }

    private static void renderSelection(StringBuilder source, SchemaModel.Type table) {
        source.append("    public final class Selection {\n");
        source.append("        private final Selection parent;\n");
        source.append("        private final io.github.somaruntime.soma.SomaExpression<View> expression;\n");
        source.append("        private final io.github.somaruntime.soma.SomaPredicate<? super View> callback;\n");
        source.append("        private final int indexField; private final Object indexValue;\n");
        source.append("        private final boolean parallel;\n");
        source.append("        private final AtomicBoolean claimed = new AtomicBoolean();\n");
        source.append("        private Selection(Selection parent, io.github.somaruntime.soma.SomaExpression<View> expression, io.github.somaruntime.soma.SomaPredicate<? super View> callback, int indexField, Object indexValue) { this(parent, expression, callback, indexField, indexValue, false); }\n");
        source.append("        private Selection(Selection parent, io.github.somaruntime.soma.SomaExpression<View> expression, io.github.somaruntime.soma.SomaPredicate<? super View> callback, int indexField, Object indexValue, boolean parallel) { this.parent = parent; this.expression = expression; this.callback = callback; this.indexField = indexField; this.indexValue = indexValue; this.parallel = parallel; }\n");
        source.append("        public Selection filter(io.github.somaruntime.soma.SomaExpression<View> value) { if (value == null) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.QUERY, \"expression must not be null\", null); io.github.somaruntime.soma.internal.SomaRuntimeAccess.validate(value, expressionOwner); claim(); return new Selection(this, value, null, indexField, indexValue, parallel); }\n");
        source.append("        public Selection filter(io.github.somaruntime.soma.SomaPredicate<? super View> value) { if (value == null) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.QUERY, \"callback must not be null\", null); claim(); return new Selection(this, null, value, indexField, indexValue, parallel); }\n");
        source.append("        public Selection parallel() { claim(); return new Selection(this, null, null, -1, null, true); }\n");
        source.append("        public <R> MappedStream<R> map(Function<? super View, ? extends R> mapper) { if (parallel) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.QUERY, \"bounded parallel slice supports count only\", null); if (mapper == null) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.QUERY, \"mapper must not be null\", null); claim(); return new MappedStream<R>(this, mapper); }\n");
        source.append("        public LongMappedStream mapToLong(ToLongFunction<? super View> mapper) { if (parallel) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.QUERY, \"bounded parallel slice supports count only\", null); if (mapper == null) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.QUERY, \"mapper must not be null\", null); claim(); return new LongMappedStream(this, mapper); }\n");
        source.append("        public long count() { claim(); return countInternal(); }\n");
        source.append("        private long countInternal() { io.github.somaruntime.soma.internal.ScalarTableRuntime.Query query = runtime.beginQuery(io.github.somaruntime.soma.SomaOperation.QUERY); try { if (parallel && !hasOpaqueCallback()) { final java.lang.ThreadLocal<View> views = new java.lang.ThreadLocal<View>() { @Override protected View initialValue() { return new View(query, 0L); } }; return runtime.parallelCount(query.size(), new java.util.function.LongPredicate() { @Override public boolean test(long locator) { View view = views.get(); view.locator = locator; io.github.somaruntime.soma.internal.ScalarTableRuntime.enterParallelRead(query); try { return matchesParallel(view, query, locator); } finally { io.github.somaruntime.soma.internal.ScalarTableRuntime.exitParallelRead(query); } } }, new Runnable() { @Override public void run() { views.remove(); } }); } View view = new View(query, 0L); long count = 0L; for (long locator = 0L; locator < query.size(); locator++) { view.locator = locator; if (matchesParallel(view, query, locator)) count = Math.addExact(count, 1L); } return count; } catch (ArithmeticException failure) { throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.ARITHMETIC_OVERFLOW, io.github.somaruntime.soma.SomaOperation.QUERY, \"checked count overflow\", failure); } catch (io.github.somaruntime.soma.SomaOperationException failure) { throw failure; } catch (RuntimeException failure) { throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.CALLBACK_FAILED, io.github.somaruntime.soma.SomaOperation.QUERY, \"query callback failed\", failure); } finally { query.close(); } }\n");
        source.append("        public Optional<").append(table.simpleName).append("> findFirst() { if (parallel) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.FIND, \"bounded parallel slice supports count only\", null); claim(); io.github.somaruntime.soma.internal.ScalarTableRuntime.Query query = runtime.beginQuery(io.github.somaruntime.soma.SomaOperation.FIND); View view = new View(query, 0L); try { for (long locator = 0L; locator < query.size(); locator++) { view.locator = locator; if (matches(view, query, locator)) return Optional.of(view.fetch()); } return Optional.empty(); } catch (io.github.somaruntime.soma.SomaOperationException failure) { throw failure; } catch (RuntimeException failure) { throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.CALLBACK_FAILED, io.github.somaruntime.soma.SomaOperation.FIND, \"query callback failed\", failure); } finally { query.close(); } }\n");
        source.append("        public boolean anyMatch(io.github.somaruntime.soma.SomaPredicate<? super View> matcher) { return matchTerminal(matcher, 0); }\n");
        source.append("        public boolean allMatch(io.github.somaruntime.soma.SomaPredicate<? super View> matcher) { return matchTerminal(matcher, 1); }\n");
        source.append("        public boolean noneMatch(io.github.somaruntime.soma.SomaPredicate<? super View> matcher) { return matchTerminal(matcher, 2); }\n");
        source.append("        private boolean matchTerminal(io.github.somaruntime.soma.SomaPredicate<? super View> matcher, int mode) { if (matcher == null) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.QUERY, \"matcher must not be null\", null); if (parallel) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.QUERY, \"bounded parallel slice supports count only\", null); claim(); io.github.somaruntime.soma.internal.ScalarTableRuntime.Query query = runtime.beginQuery(io.github.somaruntime.soma.SomaOperation.QUERY); View view = new View(query, 0L); try { for (long locator = 0L; locator < query.size(); locator++) { view.locator = locator; if (matches(view, query, locator)) { boolean value = matcher.test(view); if ((mode == 0 && value) || (mode == 1 && !value) || (mode == 2 && value)) return mode == 0; } } return mode == 1 || mode == 2; } catch (io.github.somaruntime.soma.SomaOperationException failure) { throw failure; } catch (RuntimeException failure) { throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.CALLBACK_FAILED, io.github.somaruntime.soma.SomaOperation.QUERY, \"match callback failed\", failure); } finally { query.close(); } }\n");
        source.append("        public List<").append(table.simpleName).append("> toList() { if (parallel) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.QUERY, \"bounded parallel slice supports count only\", null); claim(); io.github.somaruntime.soma.internal.ScalarTableRuntime.Query query = runtime.beginQuery(io.github.somaruntime.soma.SomaOperation.QUERY); View view = new View(query, 0L); List<").append(table.simpleName).append("> values = new ArrayList<").append(table.simpleName).append(">(); try { for (long locator = 0L; locator < query.size(); locator++) { view.locator = locator; if (matches(view, query, locator)) values.add(view.fetch()); } return values; } catch (io.github.somaruntime.soma.SomaOperationException failure) { throw failure; } catch (RuntimeException failure) { throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.CALLBACK_FAILED, io.github.somaruntime.soma.SomaOperation.QUERY, \"query callback failed\", failure); } finally { query.close(); } }\n");
        source.append("        public ").append(table.simpleName).append("[] toArray() { return toList().toArray(new ").append(table.simpleName).append("[0]); }\n");
        source.append("        private boolean hasOpaqueCallback() { return callback != null || parent != null && parent.hasOpaqueCallback(); }\n");
        source.append("        private boolean matchesParallel(View view, io.github.somaruntime.soma.internal.ScalarTableRuntime.Query query, long locator) { return matches(view, query, locator, true); }\n");
        source.append("        private boolean matches(View view, io.github.somaruntime.soma.internal.ScalarTableRuntime.Query query, long locator) { return matches(view, query, locator, false); }\n");
        source.append("        private boolean matches(View view, io.github.somaruntime.soma.internal.ScalarTableRuntime.Query query, long locator, boolean parallelAllowed) { if (parallel && !parallelAllowed) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.QUERY, \"bounded parallel slice supports count only\", null); if (parent != null && !parent.matches(view, query, locator, parallelAllowed)) return false; if (indexField >= 0 && !query.matches(indexField, locator, indexValue)) return false; if (expression != null && !io.github.somaruntime.soma.internal.SomaRuntimeAccess.evaluate(expression, view, expressionOwner)) return false; return callback == null || callback.test(view); }\n");
        source.append("        private void forEachMatched(io.github.somaruntime.soma.internal.ScalarTableRuntime.Query query, View view, RowConsumer consumer) { for (long locator = 0L; locator < query.size(); locator++) { view.locator = locator; if (matches(view, query, locator)) consumer.accept(view); } }\n");
        source.append("        private void claim() { if (!claimed.compareAndSet(false, true)) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.PIPELINE_ALREADY_CONSUMED, io.github.somaruntime.soma.SomaOperation.QUERY, \"selection already consumed\", null); }\n");
        source.append("    }\n\n");
        source.append("    private interface RowConsumer { void accept(View value); }\n\n");
        source.append("    public final class MappedStream<R> { private final Selection source; private final Function<? super View, ? extends R> mapper; private final AtomicBoolean claimed = new AtomicBoolean(); private MappedStream(Selection source, Function<? super View, ? extends R> mapper) { this.source = source; this.mapper = mapper; } public List<R> toList() { claim(); io.github.somaruntime.soma.internal.ScalarTableRuntime.Query query = runtime.beginQuery(io.github.somaruntime.soma.SomaOperation.QUERY); View view = new View(query, 0L); List<R> values = new ArrayList<R>(); try { source.forEachMatched(query, view, new RowConsumer() { public void accept(View row) { R value = mapper.apply(row); if (value instanceof View) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.CALLBACK_SCOPE_VIOLATION, io.github.somaruntime.soma.SomaOperation.QUERY, \"borrowed View cannot escape map\", null); values.add(value); } }); return values; } catch (io.github.somaruntime.soma.SomaOperationException failure) { throw failure; } catch (RuntimeException failure) { throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.CALLBACK_FAILED, io.github.somaruntime.soma.SomaOperation.QUERY, \"map callback failed\", failure); } finally { query.close(); } } public <A> A[] toArray(Class<A> componentType) { if (componentType == null || componentType.isPrimitive()) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.QUERY, \"reference component type is required\", null); List<R> values = toList(); Object array = java.lang.reflect.Array.newInstance(componentType, values.size()); try { for (int i = 0; i < values.size(); i++) java.lang.reflect.Array.set(array, i, values.get(i)); @SuppressWarnings(\"unchecked\") A[] result = (A[]) array; return result; } catch (IllegalArgumentException failure) { throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.QUERY, \"mapped value is incompatible with component type\", failure); } } private void claim() { if (!claimed.compareAndSet(false, true)) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.PIPELINE_ALREADY_CONSUMED, io.github.somaruntime.soma.SomaOperation.QUERY, \"mapped stream already consumed\", null); } }\n\n");
        source.append("    public final class LongMappedStream {\n");
        source.append("        private final Selection source; private final ToLongFunction<? super View> mapper; private final AtomicBoolean claimed = new AtomicBoolean();\n");
        source.append("        private LongMappedStream(Selection source, ToLongFunction<? super View> mapper) { this.source = source; this.mapper = mapper; }\n");
        source.append("        public long sum() { claim(); io.github.somaruntime.soma.internal.ScalarTableRuntime.Query query = runtime.beginQuery(io.github.somaruntime.soma.SomaOperation.QUERY); View view = new View(query, 0L); final io.github.somaruntime.soma.internal.ScalarTableRuntime.CheckedLongAccumulator sum = new io.github.somaruntime.soma.internal.ScalarTableRuntime.CheckedLongAccumulator(); try { source.forEachMatched(query, view, new RowConsumer() { public void accept(View row) { sum.add(mapper.applyAsLong(row)); } }); if (!sum.fitsLong()) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.ARITHMETIC_OVERFLOW, io.github.somaruntime.soma.SomaOperation.QUERY, \"checked long sum overflow\", null); return sum.value(); } catch (io.github.somaruntime.soma.SomaOperationException failure) { throw failure; } catch (RuntimeException failure) { throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.CALLBACK_FAILED, io.github.somaruntime.soma.SomaOperation.QUERY, \"mapToLong callback failed\", failure); } finally { query.close(); } }\n");
        source.append("        public long[] toArray() { claim(); io.github.somaruntime.soma.internal.ScalarTableRuntime.Query query = runtime.beginQuery(io.github.somaruntime.soma.SomaOperation.QUERY); View view = new View(query, 0L); final long[][] values = new long[][] { new long[16] }; final int[] size = new int[1]; try { source.forEachMatched(query, view, new RowConsumer() { public void accept(View row) { if (size[0] == values[0].length) values[0] = Arrays.copyOf(values[0], Math.multiplyExact(values[0].length, 2)); values[0][size[0]++] = mapper.applyAsLong(row); } }); return Arrays.copyOf(values[0], size[0]); } catch (ArithmeticException failure) { throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.ARITHMETIC_OVERFLOW, io.github.somaruntime.soma.SomaOperation.QUERY, \"checked mapped array growth overflow\", failure); } catch (io.github.somaruntime.soma.SomaOperationException failure) { throw failure; } catch (RuntimeException failure) { throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.CALLBACK_FAILED, io.github.somaruntime.soma.SomaOperation.QUERY, \"mapToLong callback failed\", failure); } finally { query.close(); } }\n");
        source.append("        private void claim() { if (!claimed.compareAndSet(false, true)) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.PIPELINE_ALREADY_CONSUMED, io.github.somaruntime.soma.SomaOperation.QUERY, \"mapped stream already consumed\", null); }\n");
        source.append("    }\n\n");
    }

    private static void renderIndexSelection(StringBuilder source) {
        source.append("    public final class IndexSelection { private final int field; private final Object value; private IndexSelection(int field, Object value) { this.field = field; this.value = value; } public long count() { return new Selection(null, null, null, field, value).count(); } public Selection filter(io.github.somaruntime.soma.SomaExpression<View> expression) { return new Selection(null, null, null, field, value).filter(expression); } public Selection filter(io.github.somaruntime.soma.SomaPredicate<? super View> callback) { return new Selection(null, null, null, field, value).filter(callback); } }\n\n");
    }

    private static void renderGroupBy(StringBuilder source, SchemaModel.Type table) {
        boolean hasInt = false;
        for (SchemaModel.Field field : table.fields) {
            if (field.storageKind == SchemaModel.StorageKind.INT && isKeyable(field)) {
                hasInt = true;
                source.append("    public IntGroupBy groupBy(").append(capital(field.name))
                        .append("Field field) { if (field == null || field != ").append(field.name)
                        .append(") throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.QUERY, \"GroupBy key field has a foreign owner\", null); return new IntGroupBy(")
                        .append(findFieldIndex(table, field.name)).append("); }\n");
            }
        }
        if (!hasInt) {
            return;
        }
        source.append("    public final class IntGroupBy { private final int keyField; private final AtomicBoolean claimed = new AtomicBoolean(); private IntGroupBy(int keyField) { this.keyField = keyField; }\n");
        source.append("        public io.github.somaruntime.soma.IntGroupedLongResult count() { claim(); return runtime.groupIntLong(keyField, -1, false); }\n");
        for (SchemaModel.Field field : table.fields) {
            if (field.storageKind == SchemaModel.StorageKind.INT) {
                source.append("        public io.github.somaruntime.soma.IntGroupedLongResult sum(")
                        .append(capital(field.name)).append("Field field) { if (field == null || field != ")
                        .append(field.name).append(") throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.QUERY, \"GroupBy sum field belongs to another endpoint\", null); claim(); return runtime.groupIntLong(keyField, ")
                        .append(findFieldIndex(table, field.name)).append(", true); }\n");
            }
        }
        source.append("        private void claim() { if (!claimed.compareAndSet(false, true)) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.PIPELINE_ALREADY_CONSUMED, io.github.somaruntime.soma.SomaOperation.QUERY, \"GroupBy builder already consumed\", null); }\n");
        source.append("    }\n\n");
    }

    private static void renderField(StringBuilder source, SchemaModel.Type table, int index) {
        SchemaModel.Field field = table.fields.get(index);
        String className = capital(field.name) + "Field";
        source.append("    public final class ").append(className).append(" implements io.github.somaruntime.soma.")
                .append(isKeyable(field) ? "SomaKeyableField" : "SomaFieldEndpoint")
                .append("<View, ").append(boxedType(field)).append("> {\n");
        source.append("        private ").append(className).append("() { }\n");
        source.append("        public io.github.somaruntime.soma.FieldMetadata _metadata() {\n");
        source.append("            return runtime.fieldMetadata(").append(index).append(", \"")
                .append(javaString(field.name)).append("\", \"")
                .append(javaString(field.qualifiedType)).append("\", ")
                .append(field.storageKind == SchemaModel.StorageKind.REFERENCE ? "true" : "false")
                .append(");\n");
        source.append("        }\n");
        String[] ops = {"eq", "ne", "lt", "le", "gt", "ge"};
        for (String op : ops) {
            if (field.storageKind == SchemaModel.StorageKind.BOOLEAN
                    && !"eq".equals(op) && !"ne".equals(op)) continue;
            if (field.storageKind == SchemaModel.StorageKind.REFERENCE && ("lt".equals(op) || "le".equals(op) || "gt".equals(op) || "ge".equals(op))) continue;
            source.append("        public io.github.somaruntime.soma.SomaExpression<View> ").append(op).append('(').append(typeName(field)).append(" value) { return io.github.somaruntime.soma.internal.SomaRuntimeAccess.expression(expressionOwner, new io.github.somaruntime.soma.internal.SomaExpressionNode.Evaluator<View>() { @Override public boolean test(View row) { return ")
                    .append(compareExpr(field, index, op)).append("; } }); }\n");
        }
        if (field.storageKind == SchemaModel.StorageKind.REFERENCE) {
            source.append("        public io.github.somaruntime.soma.SomaExpression<View> isNull() { return io.github.somaruntime.soma.internal.SomaRuntimeAccess.expression(expressionOwner, new io.github.somaruntime.soma.internal.SomaExpressionNode.Evaluator<View>() { @Override public boolean test(View row) { return row.").append(field.name).append("() == null; } }); }\n");
            source.append("        public io.github.somaruntime.soma.SomaExpression<View> isNotNull() { return io.github.somaruntime.soma.internal.SomaRuntimeAccess.expression(expressionOwner, new io.github.somaruntime.soma.internal.SomaExpressionNode.Evaluator<View>() { @Override public boolean test(View row) { return row.").append(field.name).append("() != null; } }); }\n");
        }
        source.append("    }\n\n");
    }

    private static String compareExpr(SchemaModel.Field field, int index, String op) {
        String value = "row." + field.name + "()";
        if (field.storageKind == SchemaModel.StorageKind.REFERENCE) {
            if ("eq".equals(op)) return "Objects.equals(" + value + ", value)";
            if ("ne".equals(op)) return "!Objects.equals(" + value + ", value)";
        }
        if (field.storageKind == SchemaModel.StorageKind.FLOAT) return "Float.compare(" + value + ", value) " + operator(op) + " 0";
        if (field.storageKind == SchemaModel.StorageKind.DOUBLE) return "Double.compare(" + value + ", value) " + operator(op) + " 0";
        return value + " " + operator(op) + " value";
    }

    private static String operator(String operation) {
        if ("eq".equals(operation)) return "==";
        if ("ne".equals(operation)) return "!=";
        if ("lt".equals(operation)) return "<";
        if ("le".equals(operation)) return "<=";
        if ("gt".equals(operation)) return ">";
        return ">=";
    }

    private static void appendQueryConstructor(StringBuilder source, SchemaModel.Type table) {
        for (int i = 0; i < table.fields.size(); i++) {
            if (i > 0) source.append(", ");
            SchemaModel.Field field = table.fields.get(i);
            source.append(queryReadExpr(field, i));
        }
    }

    private static String readExpr(SchemaModel.Field field, int index) {
        String q = "query";
        String u = "update";
        String suffix = field.storageKind.name().substring(0, 1) + field.storageKind.name().substring(1).toLowerCase();
        String query = q + " == null ? " + u + "." + lower(suffix) + "At(" + index + ") : "
                + q + "." + lower(suffix) + "At(" + index + ", locator)";
        if (field.storageKind == SchemaModel.StorageKind.REFERENCE) {
            return "(" + typeName(field) + ") (" + query + ")";
        }
        return query;
    }

    private static String queryReadExpr(SchemaModel.Field field, int index) {
        String suffix = field.storageKind.name().substring(0, 1)
                + field.storageKind.name().substring(1).toLowerCase();
        String result = "query." + lower(suffix) + "At(" + index + ", locator)";
        return field.storageKind == SchemaModel.StorageKind.REFERENCE
                ? "(" + typeName(field) + ") " + result : result;
    }

    private static String setter(SchemaModel.Field field) {
        String suffix = field.storageKind.name().substring(0, 1) + field.storageKind.name().substring(1).toLowerCase();
        return "set" + suffix;
    }

    private static String lower(String value) { return Character.toLowerCase(value.charAt(0)) + value.substring(1); }

    private static String typeName(SchemaModel.Field field) { return field.qualifiedType; }
    private static boolean isString(SchemaModel.Field field) {
        return field.storageKind == SchemaModel.StorageKind.REFERENCE
                && "java.lang.String".equals(field.qualifiedType);
    }
    private static String boxedType(SchemaModel.Field field) {
        switch (field.storageKind) {
            case BOOLEAN: return "java.lang.Boolean";
            case BYTE: return "java.lang.Byte";
            case SHORT: return "java.lang.Short";
            case CHAR: return "java.lang.Character";
            case INT: return "java.lang.Integer";
            case LONG: return "java.lang.Long";
            case FLOAT: return "java.lang.Float";
            case DOUBLE: return "java.lang.Double";
            default: return typeName(field);
        }
    }
    private static int keyIndex(SchemaModel.Type table) { return findRoleIndex(table, SchemaModel.FieldRole.KEY); }
    private static boolean isKeyable(SchemaModel.Field field) {
        switch (field.storageKind) {
            case FLOAT:
            case DOUBLE:
            case VALUE:
            case UNSUPPORTED:
                return false;
            case REFERENCE:
                return isString(field) || field.enumType;
            default:
                return true;
        }
    }
    private static int findFieldIndex(SchemaModel.Type table, String name) { for (int i = 0; i < table.fields.size(); i++) if (table.fields.get(i).name.equals(name)) return i; return -1; }
    private static int findRoleIndex(SchemaModel.Type table, SchemaModel.FieldRole role) { for (int i = 0; i < table.fields.size(); i++) if (table.fields.get(i).role == role) return i; return -1; }

    private static String header(String namespace) { return "package " + namespace + ";\n\n// Generated by SOMA; do not edit.\n"; }
    private static String javaString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    private static String lowerFirst(String value) { int cp = value.codePointAt(0); return new String(Character.toChars(Character.toLowerCase(cp))) + value.substring(Character.charCount(cp)); }
    private static String capital(String value) { int cp = value.codePointAt(0); return new String(Character.toChars(Character.toUpperCase(cp))) + value.substring(Character.charCount(cp)); }
    private static String sha256(String value) { try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder result = new StringBuilder(); for (byte item : bytes) result.append(String.format("%02x", Integer.valueOf(item & 0xff))); return result.toString(); } catch (NoSuchAlgorithmException exception) { throw new AssertionError(exception); } }
}
