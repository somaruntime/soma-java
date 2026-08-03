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
        source.append("            if (group == null) { group = SomaGroup.create(TOKEN); DEFAULT_GROUP = group; }\n");
        source.append("        } }\n        return group;\n    }\n\n");
        source.append("    public static SomaGroup createGroup() { return SomaGroup.create(TOKEN); }\n\n");
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
        source.append("    private final ").append(tableType).append(" table;\n\n");
        source.append("    private SomaGroup(Soma.Token token) {\n");
        source.append("        io.github.somaruntime.soma.internal.SomaRuntimeAccess.freezeForRuntimeAccess();\n");
        source.append("        runtime = new io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime.GroupRuntime();\n");
        source.append("        table = ").append(tableType).append(".create(token, runtime);\n    }\n\n");
        source.append("    static SomaGroup create(Soma.Token token) {\n");
        source.append("        if (token == null) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(\n");
        source.append("            io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.CONFIGURE,\n");
        source.append("            \"composition capability is required\", null);\n");
        source.append("        return new SomaGroup(token);\n    }\n\n");
        source.append("    public ").append(tableType).append(' ').append(accessor)
                .append("() { return table; }\n");
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
        source.append("import java.util.Objects;\nimport java.util.Optional;\n");
        source.append("import java.util.concurrent.atomic.AtomicBoolean;\nimport java.util.function.Consumer;\n\n");
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
        source.append("        private final Selection parent; private final io.github.somaruntime.soma.SomaExpression<View> expression;\n");
        source.append("        private final io.github.somaruntime.soma.SomaPredicate<? super View> callback; private final int indexField; private final Object indexValue;\n");
        source.append("        private final AtomicBoolean claimed = new AtomicBoolean();\n");
        source.append("        private Selection(Selection parent, io.github.somaruntime.soma.SomaExpression<View> expression, io.github.somaruntime.soma.SomaPredicate<? super View> callback, int indexField, Object indexValue) { this.parent = parent; this.expression = expression; this.callback = callback; this.indexField = indexField; this.indexValue = indexValue; }\n");
        source.append("        public Selection filter(io.github.somaruntime.soma.SomaExpression<View> value) { if (value == null) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.QUERY, \"expression must not be null\", null); io.github.somaruntime.soma.internal.SomaRuntimeAccess.validate(value, expressionOwner); claim(); return new Selection(this, value, null, indexField, indexValue); }\n");
        source.append("        public Selection filter(io.github.somaruntime.soma.SomaPredicate<? super View> value) { if (value == null) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.INVALID_ARGUMENT, io.github.somaruntime.soma.SomaOperation.QUERY, \"callback must not be null\", null); claim(); return new Selection(this, null, value, indexField, indexValue); }\n");
        source.append("        public long count() { claim(); io.github.somaruntime.soma.internal.ScalarTableRuntime.Query query = runtime.beginQuery(io.github.somaruntime.soma.SomaOperation.QUERY); View view = new View(query, 0L); long count = 0L; try { for (long locator = 0L; locator < query.size(); locator++) { view.locator = locator; if (matches(view, query, locator)) count = Math.addExact(count, 1L); } return count; } catch (ArithmeticException failure) { throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.ARITHMETIC_OVERFLOW, io.github.somaruntime.soma.SomaOperation.QUERY, \"checked count overflow\", failure); } catch (io.github.somaruntime.soma.SomaOperationException failure) { throw failure; } catch (RuntimeException failure) { throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.CALLBACK_FAILED, io.github.somaruntime.soma.SomaOperation.QUERY, \"query callback failed\", failure); } finally { query.close(); } }\n");
        source.append("        private boolean matches(View view, io.github.somaruntime.soma.internal.ScalarTableRuntime.Query query, long locator) { if (parent != null && !parent.matches(view, query, locator)) return false; if (indexField >= 0 && !query.matches(indexField, locator, indexValue)) return false; if (expression != null && !io.github.somaruntime.soma.internal.SomaRuntimeAccess.evaluate(expression, view, expressionOwner)) return false; return callback == null || callback.test(view); }\n");
        source.append("        private void claim() { if (!claimed.compareAndSet(false, true)) throw io.github.somaruntime.soma.internal.SomaRuntimeAccess.failure(io.github.somaruntime.soma.SomaFailureCode.PIPELINE_ALREADY_CONSUMED, io.github.somaruntime.soma.SomaOperation.QUERY, \"selection already consumed\", null); }\n");
        source.append("    }\n\n");
    }

    private static void renderIndexSelection(StringBuilder source) {
        source.append("    public final class IndexSelection { private final int field; private final Object value; private IndexSelection(int field, Object value) { this.field = field; this.value = value; } public long count() { return new Selection(null, null, null, field, value).count(); } public Selection filter(io.github.somaruntime.soma.SomaExpression<View> expression) { return new Selection(null, null, null, field, value).filter(expression); } public Selection filter(io.github.somaruntime.soma.SomaPredicate<? super View> callback) { return new Selection(null, null, null, field, value).filter(callback); } }\n\n");
    }

    private static void renderField(StringBuilder source, SchemaModel.Type table, int index) {
        SchemaModel.Field field = table.fields.get(index);
        String className = capital(field.name) + "Field";
        source.append("    public final class ").append(className).append(" implements io.github.somaruntime.soma.")
                .append(field.role == SchemaModel.FieldRole.KEY ? "SomaKeyableField" : "SomaFieldEndpoint")
                .append("<View, ").append(boxedType(field)).append("> {\n");
        source.append("        private ").append(className).append("() { }\n");
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
    private static int findFieldIndex(SchemaModel.Type table, String name) { for (int i = 0; i < table.fields.size(); i++) if (table.fields.get(i).name.equals(name)) return i; return -1; }
    private static int findRoleIndex(SchemaModel.Type table, SchemaModel.FieldRole role) { for (int i = 0; i < table.fields.size(); i++) if (table.fields.get(i).role == role) return i; return -1; }

    private static String header(String namespace) { return "package " + namespace + ";\n\n// Generated by SOMA; do not edit.\n"; }
    private static String lowerFirst(String value) { int cp = value.codePointAt(0); return new String(Character.toChars(Character.toLowerCase(cp))) + value.substring(Character.charCount(cp)); }
    private static String capital(String value) { int cp = value.codePointAt(0); return new String(Character.toChars(Character.toUpperCase(cp))) + value.substring(Character.charCount(cp)); }
    private static String sha256(String value) { try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder result = new StringBuilder(); for (byte item : bytes) result.append(String.format("%02x", Integer.valueOf(item & 0xff))); return result.toString(); } catch (NoSuchAlgorithmException exception) { throw new AssertionError(exception); } }
}
