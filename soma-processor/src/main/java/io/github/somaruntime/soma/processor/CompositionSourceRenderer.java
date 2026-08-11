package io.github.somaruntime.soma.processor;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Pure deterministic Java 8 source renderer over the validated composition model. */
final class CompositionSourceRenderer {

    private CompositionSourceRenderer() {
    }

    static String linkage(CompositionModel model) {
        StringBuilder source = header(model, 1600);
        source.append("final class SomaCompositionLinkage {\n")
                .append("    private static final java.lang.String SCHEMA_PACKAGE = \"")
                .append(model.schemaPackage()).append("\";\n")
                .append("    private static final java.lang.String PROCESSOR_VERSION = \"")
                .append(model.processorVersion()).append("\";\n")
                .append("    private static final java.lang.String CONTRACT_VERSION = \"")
                .append(model.contractVersion()).append("\";\n")
                .append("    private static final java.lang.String RUNTIME_BUILD_IDENTITY = \"")
                .append(model.runtimeBuildIdentity()).append("\";\n")
                .append("    private static final java.lang.String FINGERPRINT = \"")
                .append(model.fingerprint()).append("\";\n\n")
                .append("    static {\n")
                .append("        io.github.somaruntime.soma.internal.SomaRuntimeLinkage")
                .append(".requireCompatible(PROCESSOR_VERSION, CONTRACT_VERSION, ")
                .append("RUNTIME_BUILD_IDENTITY);\n")
                .append("    }\n\n")
                .append("    private SomaCompositionLinkage() {\n    }\n\n")
                .append("    static void requireLinked() {\n    }\n\n")
                .append("    static java.lang.String schemaPackage() {\n")
                .append("        return SCHEMA_PACKAGE;\n    }\n\n")
                .append("    static java.lang.String fingerprint() {\n")
                .append("        return FINGERPRINT;\n    }\n")
                .append("}\n");
        return source.toString();
    }

    static String soma(CompositionModel model) {
        StringBuilder source = header(model, 3600);
        source.append("public final class Soma {\n\n")
                .append("    private static final java.lang.Object CAPABILITY = ")
                .append("new java.lang.Object();\n\n")
                .append("    static {\n        SomaCompositionLinkage.requireLinked();\n    }\n\n")
                .append("    private Soma() {\n    }\n\n")
                .append("    public static void configure(\n")
                .append("            io.github.somaruntime.soma.SomaConfiguration configuration) {\n")
                .append("        io.github.somaruntime.soma.internal.GeneratedRuntime.configure(\n")
                .append("                java.lang.invoke.MethodHandles.lookup(), CAPABILITY, configuration);\n")
                .append("    }\n\n")
                .append("    public static SomaGroup defaultGroup() {\n")
                .append("        io.github.somaruntime.soma.internal.GeneratedRuntime.freezeConfiguration(\n")
                .append("                java.lang.invoke.MethodHandles.lookup(), CAPABILITY);\n")
                .append("        return DefaultGroupHolder.INSTANCE;\n    }\n\n")
                .append("    public static SomaGroup createGroup() {\n")
                .append("        io.github.somaruntime.soma.internal.GeneratedRuntime.freezeConfiguration(\n")
                .append("                java.lang.invoke.MethodHandles.lookup(), CAPABILITY);\n")
                .append("        return newGroup(false);\n    }\n\n")
                .append("    public static io.github.somaruntime.soma.SomaMetadata _metadata() {\n")
                .append("        return io.github.somaruntime.soma.internal.GeneratedRuntime.metadata(\n")
                .append("                java.lang.invoke.MethodHandles.lookup(), CAPABILITY);\n")
                .append("    }\n\n");
        for (CompositionModel.TableModel table : model.tables()) {
            String tableType = GeneratedNames.tableType(table.simpleName());
            String accessor = GeneratedNames.tableAccessor(table.simpleName());
            source.append("    public static ").append(tableType).append(' ')
                    .append(accessor).append("() {\n")
                    .append("        return defaultGroup().").append(accessor).append("();\n")
                    .append("    }\n\n");
        }
        source.append("    static boolean accepts(java.lang.Object candidate) {\n")
                .append("        return candidate == CAPABILITY;\n    }\n\n")
                .append("    private static SomaGroup newGroup(boolean defaultGroup) {\n")
                .append("        io.github.somaruntime.soma.internal.GeneratedGroup runtime = ")
                .append("io.github.somaruntime.soma.internal.GeneratedRuntime.createGroup(\n")
                .append("                java.lang.invoke.MethodHandles.lookup(), CAPABILITY);\n")
                .append("        return SomaGroup.create(CAPABILITY, runtime, defaultGroup);\n    }\n\n")
                .append("    private static final class DefaultGroupHolder {\n")
                .append("        private static final SomaGroup INSTANCE = newGroup(true);\n")
                .append("    }\n")
                .append("}\n");
        return source.toString();
    }

    static String group(CompositionModel model) {
        StringBuilder source = header(model, 4800);
        source.append("public final class SomaGroup {\n\n")
                .append("    private final java.lang.Object capability;\n")
                .append("    private final io.github.somaruntime.soma.internal.GeneratedGroup runtime;\n")
                .append("    private final boolean defaultGroup;\n");
        for (CompositionModel.TableModel table : model.tables()) {
            source.append("    private volatile ")
                    .append(GeneratedNames.tableType(table.simpleName())).append(' ')
                    .append(GeneratedNames.tableAccessor(table.simpleName())).append(";\n");
        }
        source.append("\n    private SomaGroup(\n")
                .append("            java.lang.Object capability,\n")
                .append("            io.github.somaruntime.soma.internal.GeneratedGroup runtime,\n")
                .append("            boolean defaultGroup) {\n")
                .append("        if (runtime == null) throw new java.lang.AssertionError(")
                .append("\"generated runtime Group is missing\");\n")
                .append("        runtime.requireCapability(capability);\n")
                .append("        this.capability = capability;\n")
                .append("        this.runtime = runtime;\n")
                .append("        this.defaultGroup = defaultGroup;\n    }\n\n")
                .append("    static SomaGroup create(\n")
                .append("            java.lang.Object capability,\n")
                .append("            io.github.somaruntime.soma.internal.GeneratedGroup runtime,\n")
                .append("            boolean defaultGroup) {\n")
                .append("        return new SomaGroup(capability, runtime, defaultGroup);\n    }\n\n")
                .append("    public io.github.somaruntime.soma.GroupMetadata _metadata() {\n")
                .append("        return runtime.metadata(defaultGroup);\n")
                .append("    }\n\n");
        for (CompositionModel.TableModel table : model.tables()) {
            String tableType = GeneratedNames.tableType(table.simpleName());
            String accessor = GeneratedNames.tableAccessor(table.simpleName());
            source.append("    public ").append(tableType).append(' ').append(accessor)
                    .append("() {\n")
                    .append("        ").append(tableType).append(" value = ").append(accessor)
                    .append(";\n")
                    .append("        if (value != null) return value;\n")
                    .append("        synchronized (this) {\n")
                    .append("            value = ").append(accessor).append(";\n")
                    .append("            if (value == null) {\n")
                    .append("                value = ").append(tableType)
                    .append(".create(capability, runtime);\n")
                    .append("                ").append(accessor).append(" = value;\n")
                    .append("            }\n")
                    .append("            return value;\n        }\n    }\n\n");
        }
        source.append("}\n");
        return source.toString();
    }

    static String value(CompositionModel model, CompositionModel.ValueModel value) {
        StringBuilder source = header(model, 8000);
        source.append("public final class ").append(value.simpleName()).append(" {\n\n");
        for (CompositionModel.FieldModel field : value.fields()) {
            source.append("    private final ").append(field.typeName()).append(' ')
                    .append(field.name()).append(";\n");
        }
        source.append("\n    public ").append(value.simpleName()).append('(');
        appendParameters(source, value.fields());
        source.append(") {\n");
        for (CompositionModel.FieldModel field : value.fields()) {
            if (field.type().kind() == CompositionModel.LogicalKind.VALUE) {
                source.append("        if (").append(field.name()).append(" == null) ")
                        .append("throw new java.lang.IllegalArgumentException(")
                        .append('"').append(field.name()).append(" is null\");\n");
            }
            source.append("        this.").append(field.name()).append(" = ")
                    .append(field.name()).append(";\n");
        }
        source.append("    }\n\n");
        for (CompositionModel.FieldModel field : value.fields()) {
            source.append("    public ").append(field.typeName()).append(' ')
                    .append(field.name()).append("() {\n")
                    .append("        return ").append(field.name()).append(";\n    }\n\n");
        }
        appendValueEqualsHash(source, value);
        appendValueView(source, value);
        source.append("}\n");
        return source.toString();
    }

    static String tableObject(CompositionModel model, CompositionModel.TableModel table) {
        StringBuilder source = header(model, 5200);
        source.append("public final class ").append(table.simpleName()).append(" {\n\n");
        for (CompositionModel.FieldModel field : table.fields()) {
            source.append("    private ").append(field.typeName()).append(' ')
                    .append(field.name()).append(";\n");
        }
        source.append("\n    public ").append(table.simpleName()).append("() {\n    }\n\n")
                .append("    public ").append(table.simpleName()).append('(');
        appendParameters(source, table.fields());
        source.append(") {\n");
        for (CompositionModel.FieldModel field : table.fields()) {
            source.append("        this.").append(field.name()).append(" = ")
                    .append(field.name()).append(";\n");
        }
        source.append("    }\n\n");
        for (CompositionModel.FieldModel field : table.fields()) {
            source.append("    public ").append(field.typeName()).append(' ')
                    .append(field.name()).append("() {\n")
                    .append("        return ").append(field.name()).append(";\n    }\n\n")
                    .append("    public void ").append(field.name()).append('(')
                    .append(field.typeName()).append(" value) {\n")
                    .append("        this.").append(field.name()).append(" = value;\n    }\n\n");
        }
        source.append("}\n");
        return source.toString();
    }

    static String table(CompositionModel model, CompositionModel.TableModel table) {
        TableShape shape = new TableShape(table);
        String tableType = GeneratedNames.tableType(table.simpleName());
        String objectType = model.generatedPackage() + "." + table.simpleName();
        StringBuilder source = header(model, 32000);
        source.append("public final class ").append(tableType).append(" {\n\n")
                .append("    private static final io.github.somaruntime.soma.internal")
                .append(".GeneratedTableLayout LAYOUT = createLayout();\n\n")
                .append("    private final java.lang.Object capability;\n")
                .append("    private final io.github.somaruntime.soma.internal.GeneratedTable runtime;\n")
                .append("    private final View borrowedView;\n")
                .append("    private final View borrowedCompareView;\n")
                .append("    private final Editor borrowedEditor;\n")
                .append("    private final Editor borrowedSelectionEditor;\n");
        source.append("    private final RelationAdapter relationAdapter;\n");
        for (EndpointPlan endpoint : shape.roots) {
            source.append("    public final ").append(endpoint.typeName()).append(' ')
                    .append(endpoint.field.name()).append(";\n");
        }
        source.append("\n    private ").append(tableType).append("(\n")
                .append("            java.lang.Object capability,\n")
                .append("            io.github.somaruntime.soma.internal.GeneratedGroup group) {\n")
                .append("        if (group == null) throw new java.lang.AssertionError(")
                .append("\"generated Group is missing\");\n")
                .append("        this.capability = capability;\n")
                .append("        this.runtime = group.createTable(\n")
                .append("                java.lang.invoke.MethodHandles.lookup(), capability, \"")
                .append(table.simpleName()).append("\", ")
                .append(model.tables().indexOf(table)).append(", LAYOUT);\n")
                .append("        this.borrowedView = new View(capability, runtime.queryCursor());\n")
                .append("        this.borrowedCompareView = new View(capability, ")
                .append("runtime.secondaryQueryCursor());\n")
                .append("        this.borrowedEditor = new Editor(capability, runtime.borrowedRow());\n");
        source.append("        this.borrowedSelectionEditor = new Editor(capability, ")
                .append("runtime.borrowedSelectionEditor());\n");
        source.append("        this.relationAdapter = new RelationAdapter();\n");
        for (EndpointPlan endpoint : shape.roots) {
            source.append("        this.").append(endpoint.field.name()).append(" = new ")
                    .append(endpoint.typeName()).append("();\n");
        }
        source.append("    }\n\n")
                .append("    static ").append(tableType).append(" create(\n")
                .append("            java.lang.Object capability,\n")
                .append("            io.github.somaruntime.soma.internal.GeneratedGroup group) {\n")
                .append("        return new ").append(tableType).append("(capability, group);\n")
                .append("    }\n\n");
        appendLayout(source, table, shape);
        appendTableOperations(source, table, shape, objectType);
        appendRelationOperations(source, model, table, shape, tableType);
        appendTableViewEditor(source, table, objectType);
        appendGroupBuilders(source, tableType, shape);
        appendStreamType(source, tableType, objectType, shape);
        appendSelectionTypes(source, tableType, objectType, shape);
        for (EndpointPlan endpoint : shape.roots) {
            appendEndpoint(source, endpoint, "    ");
        }
        source.append("}\n");
        return source.toString();
    }

    private static void appendLayout(
            StringBuilder source,
            CompositionModel.TableModel table,
            TableShape shape) {
        source.append("    private static io.github.somaruntime.soma.internal")
                .append(".GeneratedTableLayout createLayout() {\n")
                .append("        return io.github.somaruntime.soma.internal")
                .append(".GeneratedTableLayout.create(\n")
                .append("                \"").append(table.simpleName()).append("\", ")
                .append(table.defaultCapacity()).append(",\n")
                .append("                new byte[] {");
        for (int index = 0; index < table.leaves().size(); index++) {
            if (index != 0) source.append(", ");
            source.append("io.github.somaruntime.soma.internal.GeneratedTableLayout.")
                    .append(leafConstant(table.leaves().get(index).kind()));
        }
        source.append("},\n                new byte[] {");
        for (int index = 0; index < table.leaves().size(); index++) {
            if (index != 0) source.append(", ");
            source.append("io.github.somaruntime.soma.internal.GeneratedTableLayout.")
                    .append(equalityConstant(table.leaves().get(index).equality()));
        }
        source.append("},\n                new int[] {");
        for (int index = 0; index < shape.all.size(); index++) {
            if (index != 0) source.append(", ");
            source.append(shape.all.get(index).leafStart);
        }
        source.append("},\n                new int[] {");
        for (int index = 0; index < shape.all.size(); index++) {
            if (index != 0) source.append(", ");
            source.append(shape.all.get(index).field.leafCount());
        }
        source.append("},\n                new boolean[] {");
        for (int index = 0; index < shape.all.size(); index++) {
            if (index != 0) source.append(", ");
            source.append(shape.all.get(index).field.type().nullable());
        }
        source.append("},\n                ")
                .append(shape.key == null ? -1 : shape.key.planIndex)
                .append(",\n                new int[] {");
        for (int index = 0; index < shape.indexes.size(); index++) {
            if (index != 0) source.append(", ");
            source.append(shape.indexes.get(index).planIndex);
        }
        source.append("});\n    }\n\n");
    }

    private static void appendTableOperations(
            StringBuilder source,
            CompositionModel.TableModel table,
            TableShape shape,
            String objectType) {
        source.append("    public int size() { return runtime.size(); }\n\n")
                .append("    public int capacity() { return runtime.capacity(); }\n\n")
                .append("    public void reserve(int expectedRows) { runtime.reserve(expectedRows); }\n\n")
                .append("    public void add(").append(objectType).append(" value) {\n")
                .append("        runtime.requireArgument(value, ")
                .append("io.github.somaruntime.soma.SomaOperation.ADD, \"value\");\n")
                .append("        try (io.github.somaruntime.soma.internal.GeneratedRow row = ")
                .append("runtime.beginAdd()) {\n");
        Counter counter = new Counter();
        for (CompositionModel.FieldModel field : table.fields()) {
            if (field.role() == CompositionModel.FieldRole.KEY
                    && field.type().kind() != CompositionModel.LogicalKind.VALUE) {
                appendRequired(
                        source,
                        field.type(),
                        "value." + field.name() + "()",
                        "            ",
                        "ADD",
                        "Key");
            }
            appendWrite(
                    source,
                    field.type(),
                    "value." + field.name() + "()",
                    field.firstLeaf(),
                    "row",
                    "put",
                    "            ",
                    counter,
                    "io.github.somaruntime.soma.SomaOperation.ADD");
        }
        source.append("            row.add();\n        }\n    }\n\n");

        if (shape.key != null) {
            CompositionModel.FieldModel key = shape.key.field;
            appendKeyMethod(source, table, shape, key, objectType, "find");
            appendKeyMethod(source, table, shape, key, objectType, "get");
            appendKeyUpdate(source, shape, key);
            appendKeyRemove(source, shape, key);
        }

        source.append("    public long count() { return runtime.count(); }\n\n")
                .append("    public Stream parallel() {\n")
                .append("        return new Stream(this, runtime.parallel());\n    }\n\n")
                .append("    public Selection selectAll() {\n")
                .append("        return new Selection(this, runtime.selectAll());\n    }\n\n")
                .append("    public Selection filter(")
                .append("io.github.somaruntime.soma.SomaExpression<View> expression) {\n")
                .append("        return new Selection(this, runtime.filter(expression));\n    }\n\n")
                .append("    public Selection filter(\n")
                .append("            io.github.somaruntime.soma.SomaPredicate<? super View> predicate) {\n")
                .append("        runtime.requireArgument(predicate, ")
                .append("io.github.somaruntime.soma.SomaOperation.QUERY, \"predicate\");\n")
                .append("        return new Selection(this, runtime.filter(() -> ")
                .append("predicate.test(borrowedView)));\n    }\n\n")
                .append("    public Selection sorted(\n")
                .append("            java.util.Comparator<? super View> comparator) {\n")
                .append("        runtime.requireArgument(comparator, ")
                .append("io.github.somaruntime.soma.SomaOperation.QUERY, \"comparator\");\n")
                .append("        return new Selection(this, runtime.sorted(() -> comparator.compare(")
                .append("borrowedView, borrowedCompareView)));\n    }\n\n")
                .append("    public Selection sortedBy(")
                .append("io.github.somaruntime.soma.SomaOrder<View> order) {\n")
                .append("        return new Selection(this, runtime.sortedBy(order));\n    }\n\n")
                .append("    public Selection skip(long count) {\n")
                .append("        return new Selection(this, runtime.skip(count));\n    }\n\n")
                .append("    public Selection limit(long count) {\n")
                .append("        return new Selection(this, runtime.limit(count));\n    }\n\n")
                .append("    public Selection top(long count, ")
                .append("io.github.somaruntime.soma.SomaOrder<View> order) {\n")
                .append("        return new Selection(this, runtime.top(count, order));\n    }\n\n")
                .append("    public boolean anyMatch(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super View> predicate) {\n")
                .append("        return selectAll().anyMatch(predicate);\n    }\n\n")
                .append("    public boolean allMatch(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super View> predicate) {\n")
                .append("        return selectAll().allMatch(predicate);\n    }\n\n")
                .append("    public boolean noneMatch(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super View> predicate) {\n")
                .append("        return selectAll().noneMatch(predicate);\n    }\n\n")
                .append("    public java.util.Optional<").append(objectType)
                .append("> findFirst() { return selectAll().findFirst(); }\n\n")
                .append("    public void forEach(java.util.function.Consumer<? super View> action) {\n")
                .append("        selectAll().forEach(action);\n    }\n\n")
                .append("    public void forEachOrdered(")
                .append("java.util.function.Consumer<? super View> action) {\n")
                .append("        selectAll().forEachOrdered(action);\n    }\n\n")
                .append("    public java.util.List<").append(objectType)
                .append("> toList() { return selectAll().toList(); }\n\n")
                .append("    public ").append(objectType)
                .append("[] toArray() { return selectAll().toArray(); }\n\n")
                .append("    public java.lang.String _explain() {\n")
                .append("        return runtime.selectAll().explain();\n    }\n\n")
                .append("    public io.github.somaruntime.soma.TableMetadata _metadata() {\n")
                .append("        return runtime.metadata();\n    }\n\n");

        appendRowProjectionMethods(
                source,
                shape,
                "    ",
                "this",
                "runtime.selectAll()",
                "borrowedView",
                "runtime");
        appendGroupByMethods(
                source, shape, "    ", "this", "runtime", "runtime");

        for (int ordinal = 0; ordinal < shape.indexes.size(); ordinal++) {
            EndpointPlan endpoint = shape.indexes.get(ordinal);
            CompositionModel.FieldModel field = endpoint.field;
            String accessor = GeneratedNames.indexAccessor(field.name());
            source.append("    public IndexSelection ").append(accessor).append('(')
                    .append(field.typeName()).append(" value) {\n")
                    .append("        io.github.somaruntime.soma.internal.GeneratedProbe probe = ")
                    .append("runtime.newProbe(").append(endpoint.planIndex).append(");\n");
            appendWrite(
                    source,
                    field.type(),
                    "value",
                    endpoint.leafStart,
                    "probe",
                    "put",
                    "        ",
                    new Counter(),
                    "io.github.somaruntime.soma.SomaOperation.QUERY");
            source.append("        return new IndexSelection(this, runtime.indexSelection(")
                    .append(ordinal).append(", probe.seal()));\n    }\n\n");
        }

        source.append("    private static ").append(objectType)
                .append(" materialize(io.github.somaruntime.soma.internal.GeneratedRow row) {\n")
                .append("        return new ").append(objectType).append('(');
        for (int index = 0; index < table.fields().size(); index++) {
            if (index != 0) source.append(", ");
            CompositionModel.FieldModel field = table.fields().get(index);
            source.append(materialize(field.type(), "row", "read", field.firstLeaf()));
        }
        source.append(");\n    }\n\n");
    }

    private static void appendRelationOperations(
            StringBuilder source,
            CompositionModel model,
            CompositionModel.TableModel table,
            TableShape shape,
            String tableType) {
        source.append("    io.github.somaruntime.soma.internal.GeneratedTable ")
                .append("relationRuntime(java.lang.Object candidate) {\n")
                .append("        if (!Soma.accepts(candidate)) throw runtime.invalidQuery(\"relation capability\");\n")
                .append("        return runtime;\n    }\n\n")
                .append("    View relationView(java.lang.Object candidate) {\n")
                .append("        if (!Soma.accepts(candidate)) throw runtime.invalidQuery(\"relation capability\");\n")
                .append("        return borrowedView;\n    }\n\n")
                .append("    int relationKeyableFieldIndex(java.lang.Object candidate, ")
                .append("io.github.somaruntime.soma.SomaKeyableField<View, ?> field) {\n")
                .append("        if (!Soma.accepts(candidate)) throw runtime.invalidQuery(\"relation capability\");\n");
        for (EndpointPlan endpoint : shape.all) {
            if (endpoint.field.type().keyable()) {
                source.append("        if (field == ")
                        .append(endpoint.memberExpression("this"))
                        .append(") return ").append(endpoint.planIndex).append(";\n");
            }
        }
        source.append("        throw runtime.invalidQuery(\"relation Field\");\n")
                .append("    }\n\n")
                .append("    int relationFieldIndex(java.lang.Object candidate, ")
                .append("io.github.somaruntime.soma.SomaFieldEndpoint<View, ?> field) {\n")
                .append("        if (!Soma.accepts(candidate)) throw runtime.invalidQuery(\"relation capability\");\n");
        for (EndpointPlan endpoint : shape.all) {
            source.append("        if (field == ")
                    .append(endpoint.memberExpression("this"))
                    .append(") return ").append(endpoint.planIndex).append(";\n");
        }
        source.append("        throw runtime.invalidQuery(\"relation Field\");\n")
                .append("    }\n\n")
                .append("    java.lang.Object relationFieldValue(int field) {\n")
                .append("        switch (field) {\n");
        for (EndpointPlan endpoint : shape.all) {
            source.append("            case ").append(endpoint.planIndex).append(": return ")
                    .append(detachedEndpointValue(endpoint)).append(";\n");
        }
        source.append("            default: throw new java.lang.AssertionError(\"unknown relation Field\");\n")
                .append("        }\n    }\n\n");

        source.append("    io.github.somaruntime.soma.internal.GeneratedRelationAdapter<View, ReadStream> ")
                .append("relationAdapter(java.lang.Object candidate) {\n")
                .append("        if (!Soma.accepts(candidate)) throw runtime.invalidQuery(\"relation capability\");\n")
                .append("        return relationAdapter;\n    }\n\n")
                .append("    private final class RelationAdapter implements ")
                .append("io.github.somaruntime.soma.internal.GeneratedRelationAdapter<View, ReadStream> {\n")
                .append("        public io.github.somaruntime.soma.internal.GeneratedTable table() { return runtime; }\n")
                .append("        public View view() { return borrowedView; }\n")
                .append("        public int keyableFieldIndex(io.github.somaruntime.soma.SomaKeyableField<View, ?> field) { return relationKeyableFieldIndex(capability, field); }\n")
                .append("        public int fieldIndex(io.github.somaruntime.soma.SomaFieldEndpoint<View, ?> field) { return relationFieldIndex(capability, field); }\n")
                .append("        public java.lang.Object fieldValue(int field) { return relationFieldValue(field); }\n")
                .append("        public ReadStream readStream(io.github.somaruntime.soma.internal.GeneratedRelation relation) { return new ReadStream(relation); }\n")
                .append("    }\n\n");

        appendReadStream(source, table, shape, tableType, model.generatedPackage());

        for (CompositionModel.TableModel other : model.tables()) {
            if (other == table) continue;
            appendJoinToTable(
                    source,
                    GeneratedNames.tableType(other.simpleName()));
        }
    }

    private static void appendReadStream(
            StringBuilder source,
            CompositionModel.TableModel table,
            TableShape shape,
            String tableType,
            String generatedPackage) {
        appendPipelineReadStream(
                source, table, shape, tableType, generatedPackage);
    }

    private static void appendPipelineReadStream(
            StringBuilder source,
            CompositionModel.TableModel table,
            TableShape shape,
            String tableType,
            String generatedPackage) {
        String objectType = generatedPackage + "." + table.simpleName();
        source.append("    public final class ReadStream {\n")
                .append("        private final io.github.somaruntime.soma.internal.GeneratedPipeline pipeline;\n\n")
                .append("        private ReadStream(io.github.somaruntime.soma.internal.GeneratedRelation relation) { this(relation.leftPipeline()); }\n")
                .append("        private ReadStream(io.github.somaruntime.soma.internal.GeneratedPipeline pipeline) { this.pipeline = pipeline; }\n\n");
        appendRowProjectionMethods(
                source,
                shape,
                "        ",
                tableType + ".this",
                "pipeline",
                "borrowedView",
                "runtime");
        source.append("        public ReadStream parallel() { return new ReadStream(pipeline.parallel()); }\n\n")
                .append("        public ReadStream filter(io.github.somaruntime.soma.SomaExpression<View> expression) { return new ReadStream(pipeline.filter(expression)); }\n\n")
                .append("        public ReadStream filter(io.github.somaruntime.soma.SomaPredicate<? super View> predicate) { runtime.requireArgument(predicate,io.github.somaruntime.soma.SomaOperation.QUERY,\"predicate\");return new ReadStream(pipeline.filter(() -> predicate.test(borrowedView))); }\n\n")
                .append("        public ReadStream sorted(java.util.Comparator<? super View> comparator) { runtime.requireArgument(comparator,io.github.somaruntime.soma.SomaOperation.QUERY,\"comparator\");return new ReadStream(pipeline.sorted(() -> comparator.compare(borrowedView,borrowedCompareView))); }\n\n")
                .append("        public ReadStream sortedBy(io.github.somaruntime.soma.SomaOrder<View> order) { return new ReadStream(pipeline.sortedBy(order)); }\n\n")
                .append("        public ReadStream skip(long count) { return new ReadStream(pipeline.skip(count)); }\n\n")
                .append("        public ReadStream limit(long count) { return new ReadStream(pipeline.limit(count)); }\n\n")
                .append("        public ReadStream top(long count,io.github.somaruntime.soma.SomaOrder<View> order) { return new ReadStream(pipeline.top(count,order)); }\n\n")
                .append("        public long count() { return pipeline.count(); }\n\n")
                .append("        public boolean anyMatch(io.github.somaruntime.soma.SomaPredicate<? super View> predicate) { runtime.requireArgument(predicate,io.github.somaruntime.soma.SomaOperation.QUERY,\"predicate\");return pipeline.anyMatch(() -> predicate.test(borrowedView)); }\n\n")
                .append("        public boolean allMatch(io.github.somaruntime.soma.SomaPredicate<? super View> predicate) { runtime.requireArgument(predicate,io.github.somaruntime.soma.SomaOperation.QUERY,\"predicate\");return pipeline.allMatch(() -> predicate.test(borrowedView)); }\n\n")
                .append("        public boolean noneMatch(io.github.somaruntime.soma.SomaPredicate<? super View> predicate) { runtime.requireArgument(predicate,io.github.somaruntime.soma.SomaOperation.QUERY,\"predicate\");return pipeline.noneMatch(() -> predicate.test(borrowedView)); }\n\n")
                .append("        public java.util.Optional<").append(objectType).append("> findFirst() { return pipeline.findFirst(() -> borrowedView.fetch()); }\n\n")
                .append("        public void forEach(java.util.function.Consumer<? super View> action) { runtime.requireArgument(action,io.github.somaruntime.soma.SomaOperation.QUERY,\"action\");pipeline.forEach(() -> action.accept(borrowedView)); }\n\n")
                .append("        public void forEachOrdered(java.util.function.Consumer<? super View> action) { forEach(action); }\n\n")
                .append("        public java.util.List<").append(objectType).append("> toList() { return pipeline.toList(() -> borrowedView.fetch()); }\n\n")
                .append("        public ").append(objectType).append("[] toArray() { return pipeline.toArray(() -> borrowedView.fetch(),").append(objectType).append(".class); }\n\n")
                .append("        public java.lang.String _explain() { return pipeline.explain(); }\n")
                .append("    }\n\n");
    }

    private static void appendJoinToTable(
            StringBuilder source,
            String rightType) {
        appendSharedJoinToTable(source, rightType);
    }

    private static void appendSharedJoinToTable(
            StringBuilder source,
            String rightType) {
        source.append("    public io.github.somaruntime.soma.SomaJoinOnBuilder<")
                .append("View, ").append(rightType).append(".View, ReadStream> join(")
                .append(rightType).append(" other) {\n")
                .append("        runtime.requireArgument(other, io.github.somaruntime.soma.SomaOperation.QUERY, \"Join Table\");\n")
                .append("        return io.github.somaruntime.soma.internal.GeneratedJoinCarriers")
                .append(".equality(relationAdapter, other.relationAdapter(capability));\n")
                .append("    }\n\n")
                .append("    public io.github.somaruntime.soma.SomaMatchedJoinStream<")
                .append("View, ").append(rightType).append(".View> crossJoin(")
                .append(rightType).append(" other, long maxOutputRows) {\n")
                .append("        runtime.requireArgument(other, io.github.somaruntime.soma.SomaOperation.QUERY, \"Cross Join Table\");\n")
                .append("        return io.github.somaruntime.soma.internal.GeneratedJoinCarriers")
                .append(".cross(relationAdapter, other.relationAdapter(capability), maxOutputRows);\n")
                .append("    }\n\n");
    }

    private static String detachedEndpointValue(EndpointPlan endpoint) {
        return endpoint.field.type().kind() == CompositionModel.LogicalKind.VALUE
                ? endpoint.valueExpression + ".fetch()"
                : endpoint.valueExpression;
    }

    private static void appendKeyMethod(
            StringBuilder source,
            CompositionModel.TableModel table,
            TableShape shape,
            CompositionModel.FieldModel key,
            String objectType,
            String operation) {
        boolean find = "find".equals(operation);
        source.append("    public ")
                .append(find ? "java.util.Optional<" + objectType + ">" : objectType)
                .append(' ').append(operation).append('(')
                .append(key.typeName()).append(" key) {\n");
        appendRequired(source, key.type(), "key", "        ",
                find ? "FIND" : "GET", "Key");
        source.append("        try (io.github.somaruntime.soma.internal.GeneratedRow row = runtime.")
                .append(find ? "beginFind" : "beginGet").append("()) {\n");
        appendWrite(
                source,
                key.type(),
                "key",
                shape.key.leafStart,
                "row",
                "put",
                "            ",
                new Counter(),
                find ? "io.github.somaruntime.soma.SomaOperation.FIND"
                        : "io.github.somaruntime.soma.SomaOperation.GET");
        if (find) {
            source.append("            if (!row.find()) return java.util.Optional.empty();\n")
                    .append("            return java.util.Optional.of(materialize(row));\n");
        } else {
            source.append("            row.get();\n            return materialize(row);\n");
        }
        source.append("        }\n    }\n\n");
    }

    private static void appendKeyUpdate(
            StringBuilder source,
            TableShape shape,
            CompositionModel.FieldModel key) {
        source.append("    public io.github.somaruntime.soma.UpdateResult update(\n")
                .append("            ").append(key.typeName()).append(" key,\n")
                .append("            java.util.function.Consumer<? super Editor> updater) {\n");
        appendRequired(source, key.type(), "key", "        ", "UPDATE", "Key");
        source.append("        runtime.requireArgument(updater, ")
                .append("io.github.somaruntime.soma.SomaOperation.UPDATE, \"updater\");\n")
                .append("        try (io.github.somaruntime.soma.internal.GeneratedRow row = ")
                .append("runtime.beginUpdate()) {\n");
        appendWrite(source, key.type(), "key", shape.key.leafStart, "row", "put",
                "            ", new Counter(), "io.github.somaruntime.soma.SomaOperation.UPDATE");
        source.append("            if (!row.locateForUpdate()) return runtime.missingUpdate();\n")
                .append("            row.beginEditorCallback();\n")
                .append("            try {\n")
                .append("                updater.accept(borrowedEditor);\n")
                .append("            } catch (java.lang.Exception failure) {\n")
                .append("                throw row.callbackFailure(failure);\n")
                .append("            } finally {\n")
                .append("                row.endEditorCallback();\n")
                .append("            }\n")
                .append("            return row.finishUpdate();\n")
                .append("        }\n    }\n\n");
    }

    private static void appendKeyRemove(
            StringBuilder source,
            TableShape shape,
            CompositionModel.FieldModel key) {
        source.append("    public io.github.somaruntime.soma.RemoveResult remove(")
                .append(key.typeName()).append(" key) {\n");
        appendRequired(source, key.type(), "key", "        ", "REMOVE", "Key");
        source.append("        try (io.github.somaruntime.soma.internal.GeneratedRow row = ")
                .append("runtime.beginRemove()) {\n");
        appendWrite(source, key.type(), "key", shape.key.leafStart, "row", "put",
                "            ", new Counter(), "io.github.somaruntime.soma.SomaOperation.REMOVE");
        source.append("            return row.remove();\n        }\n    }\n\n");
    }

    private static void appendTableViewEditor(
            StringBuilder source,
            CompositionModel.TableModel table,
            String objectType) {
        source.append("    public static class View {\n")
                .append("        private final io.github.somaruntime.soma.internal.GeneratedRowAccess row;\n");
        for (CompositionModel.FieldModel field : table.fields()) {
            if (field.type().kind() == CompositionModel.LogicalKind.VALUE) {
                source.append("        private final ").append(field.typeName()).append(".View ")
                        .append(field.name()).append("View;\n");
            }
        }
        source.append("\n        private View(java.lang.Object capability, ")
                .append("io.github.somaruntime.soma.internal.GeneratedRowAccess row) {\n")
                .append("            this.row = row;\n")
                .append("            row.registerBorrowedView(this);\n");
        for (CompositionModel.FieldModel field : table.fields()) {
            if (field.type().kind() == CompositionModel.LogicalKind.VALUE) {
                source.append("            this.").append(field.name()).append("View = ")
                        .append(field.typeName()).append(".View.create(capability, row, ")
                        .append(field.firstLeaf()).append(");\n");
            }
        }
        source.append("        }\n\n");
        for (CompositionModel.FieldModel field : table.fields()) {
            source.append("        public ")
                    .append(field.type().kind() == CompositionModel.LogicalKind.VALUE
                            ? field.typeName() + ".View"
                            : field.typeName())
                    .append(' ')
                    .append(field.name()).append("() {\n            return ");
            if (field.type().kind() == CompositionModel.LogicalKind.VALUE) {
                source.append(field.name()).append("View");
            } else {
                source.append(materialize(field.type(), "row", "view", field.firstLeaf()));
            }
            source.append(";\n        }\n\n");
        }
        source.append("        public ").append(objectType).append(" fetch() {\n")
                .append("            return new ").append(objectType).append('(');
        for (int index = 0; index < table.fields().size(); index++) {
            if (index != 0) source.append(", ");
            source.append(table.fields().get(index).name()).append("()");
            if (table.fields().get(index).type().kind() == CompositionModel.LogicalKind.VALUE) {
                source.append(".fetch()");
            }
        }
        source.append(");\n        }\n    }\n\n")
                .append("    public static final class Editor extends View {\n")
                .append("        private final io.github.somaruntime.soma.internal.GeneratedEditorAccess row;\n\n")
                .append("        private Editor(java.lang.Object capability, ")
                .append("io.github.somaruntime.soma.internal.GeneratedEditorAccess row) {\n")
                .append("            super(capability, row);\n            this.row = row;\n        }\n\n");
        for (CompositionModel.FieldModel field : table.fields()) {
            if (field.role() == CompositionModel.FieldRole.KEY) continue;
            source.append("        public void ").append(field.name()).append('(')
                    .append(field.typeName()).append(" value) {\n");
            appendWrite(source, field.type(), "value", field.firstLeaf(), "row", "edit",
                    "            ", new Counter(), "io.github.somaruntime.soma.SomaOperation.UPDATE");
            source.append("        }\n\n");
        }
        source.append("        @java.lang.Override\n")
                .append("        public ").append(objectType).append(" fetch() {\n")
                .append("            return super.fetch();\n        }\n")
                .append("    }\n\n");
    }

    private static void appendRowProjectionMethods(
            StringBuilder source,
            TableShape shape,
            String indent,
            String owner,
            String pipeline,
            String view,
            String runtime) {
        for (EndpointPlan endpoint : shape.all) {
            String endpointType = endpoint.qualifiedTypeName();
            source.append(indent).append("public ").append(endpointType)
                    .append(".Stream ")
                    .append(rowProjectionMethod(endpoint.field.type().kind()))
                    .append('(').append(endpointType).append(" field) {\n")
                    .append(indent).append("    if (field != ")
                    .append(endpoint.memberExpression(owner)).append(") throw ")
                    .append(runtime).append(".invalidQuery(\"field\");\n")
                    .append(indent).append("    return field.project(")
                    .append(pipeline).append(".projectField(")
                    .append(endpoint.planIndex).append("));\n")
                    .append(indent).append("}\n\n");
        }
        source.append(indent).append("public <R> io.github.somaruntime.soma.MappedStream<R> map(\n")
                .append(indent).append("        java.util.function.Function<? super View, ? extends R> mapper) {\n")
                .append(indent).append("    if (mapper == null) throw ")
                .append(runtime).append(".invalidQuery(\"mapper\");\n")
                .append(indent).append("    return ").append(pipeline)
                .append(".map(() -> mapper.apply(").append(view).append("));\n")
                .append(indent).append("}\n\n")
                .append(indent).append("public io.github.somaruntime.soma.SomaIntStream mapToInt(\n")
                .append(indent).append("        io.github.somaruntime.soma.SomaToIntFunction<? super View> mapper) {\n")
                .append(indent).append("    if (mapper == null) throw ")
                .append(runtime).append(".invalidQuery(\"mapper\");\n")
                .append(indent).append("    return ").append(pipeline)
                .append(".mapToInt(() -> mapper.applyAsInt(").append(view).append("));\n")
                .append(indent).append("}\n\n")
                .append(indent).append("public io.github.somaruntime.soma.SomaLongStream mapToLong(\n")
                .append(indent).append("        io.github.somaruntime.soma.SomaToLongFunction<? super View> mapper) {\n")
                .append(indent).append("    if (mapper == null) throw ")
                .append(runtime).append(".invalidQuery(\"mapper\");\n")
                .append(indent).append("    return ").append(pipeline)
                .append(".mapToLong(() -> mapper.applyAsLong(").append(view).append("));\n")
                .append(indent).append("}\n\n")
                .append(indent).append("public io.github.somaruntime.soma.SomaDoubleStream mapToDouble(\n")
                .append(indent).append("        io.github.somaruntime.soma.SomaToDoubleFunction<? super View> mapper) {\n")
                .append(indent).append("    if (mapper == null) throw ")
                .append(runtime).append(".invalidQuery(\"mapper\");\n")
                .append(indent).append("    return ").append(pipeline)
                .append(".mapToDouble(() -> mapper.applyAsDouble(").append(view).append("));\n")
                .append(indent).append("}\n\n");
    }

    private static void appendGroupByMethods(
            StringBuilder source,
            TableShape shape,
            String indent,
            String owner,
            String receiver,
            String runtime) {
        for (EndpointPlan key : shape.all) {
            if (!key.field.type().keyable()) continue;
            source.append(indent).append("public ").append(groupBuilderName(key))
                    .append(" groupBy(").append(key.qualifiedTypeName()).append(" field) {\n")
                    .append(indent).append("    if (field != ")
                    .append(key.memberExpression(owner)).append(") throw ")
                    .append(runtime).append(".invalidQuery(\"group key field\");\n")
                    .append(indent).append("    return ");
            if ("owner".equals(owner)) source.append("owner.new ");
            else source.append("new ");
            source.append(groupBuilderName(key)).append('(')
                    .append(receiver).append(".groupBy(")
                    .append(key.planIndex).append(", ")
                    .append("io.github.somaruntime.soma.internal.GeneratedGrouping.")
                    .append(groupKeyConstant(key.field.type().kind())).append(", () -> ")
                    .append(groupKeyMaterializer(key, owner)).append("));\n")
                    .append(indent).append("}\n\n");
        }
    }

    private static void appendGroupBuilders(
            StringBuilder source,
            String tableType,
            TableShape shape) {
        for (EndpointPlan key : shape.all) {
            if (!key.field.type().keyable()) continue;
            source.append("    public final class ").append(groupBuilderName(key)).append(" {\n")
                    .append("        private final io.github.somaruntime.soma.internal")
                    .append(".GeneratedGrouping grouping;\n\n")
                    .append("        private ").append(groupBuilderName(key)).append('(')
                    .append("io.github.somaruntime.soma.internal.GeneratedGrouping grouping) {\n")
                    .append("            this.grouping = grouping;\n")
                    .append("        }\n\n")
                    .append("        public ").append(groupResultType(key, "Long"))
                    .append(" count() {\n")
                    .append("            return (").append(groupResultType(key, "Long"))
                    .append(") grouping.count();\n")
                    .append("        }\n\n");
            for (EndpointPlan value : shape.all) {
                if (!isNumeric(value.field.type().kind())) continue;
                appendGroupAggregate(source, tableType, key, value, "sum");
                appendGroupAggregate(source, tableType, key, value, "min");
                appendGroupAggregate(source, tableType, key, value, "max");
                appendGroupAggregate(source, tableType, key, value, "average");
                appendGroupAggregate(
                        source, tableType, key, value, "summaryStatistics");
            }
            source.append("    }\n\n");
        }
    }

    private static void appendGroupAggregate(
            StringBuilder source,
            String tableType,
            EndpointPlan key,
            EndpointPlan value,
            String method) {
        CompositionModel.LogicalKind kind = value.field.type().kind();
        boolean floating = kind == CompositionModel.LogicalKind.FLOAT
                || kind == CompositionModel.LogicalKind.DOUBLE;
        String valueToken;
        String aggregateConstant;
        if ("sum".equals(method)) {
            valueToken = floating ? "Double" : "Long";
            aggregateConstant = "SUM";
        } else if ("min".equals(method) || "max".equals(method)) {
            valueToken = floating ? "Double"
                    : kind == CompositionModel.LogicalKind.LONG ? "Long" : "Int";
            aggregateConstant = method.toUpperCase();
        } else if ("average".equals(method)) {
            valueToken = "Double";
            aggregateConstant = "AVERAGE";
        } else {
            valueToken = floating ? "DoubleSummary" : "LongSummary";
            aggregateConstant = "SUMMARY";
        }
        String resultType = groupResultType(key, valueToken);
        source.append("        public ").append(resultType).append(' ')
                .append(method).append('(').append(value.qualifiedTypeName())
                .append(" field) {\n")
                .append("            if (field != ")
                .append(value.memberExpression(tableType + ".this"))
                .append(") throw runtime.invalidQuery(\"aggregate field\");\n")
                .append("            return (").append(resultType).append(") grouping.")
                .append(floating ? "aggregateDouble" : "aggregateLong")
                .append("(io.github.somaruntime.soma.internal.GeneratedGrouping.")
                .append(aggregateConstant).append(", ")
                .append("io.github.somaruntime.soma.internal.GeneratedGrouping.VALUE_")
                .append(camelConstant(valueToken)).append(", ")
                .append(value.planIndex).append(", () -> ")
                .append(value.valueExpression).append(");\n")
                .append("        }\n\n");
    }

    private static String groupBuilderName(EndpointPlan endpoint) {
        return endpoint.qualifiedTypeName().replace(".", "") + "Group";
    }

    private static String groupResultType(EndpointPlan key, String valueToken) {
        String prefix = primitiveGroupKeyToken(key.field.type().kind());
        if (prefix != null) {
            return "io.github.somaruntime.soma." + prefix + "Grouped"
                    + valueToken + "Result";
        }
        return "io.github.somaruntime.soma.Grouped" + valueToken
                + "Result<" + key.field.type().boxedTypeName() + ">";
    }

    private static String groupKeyMaterializer(
            EndpointPlan key,
            String owner) {
        String expression = key.valueExpression.replace(
                "borrowedView", "owner".equals(owner)
                        ? "owner.borrowedView" : "borrowedView");
        return key.field.type().kind() == CompositionModel.LogicalKind.VALUE
                ? expression + ".fetch()"
                : expression;
    }

    private static String groupKeyConstant(CompositionModel.LogicalKind kind) {
        String token = primitiveGroupKeyToken(kind);
        return token == null ? "KEY_REFERENCE" : "KEY_" + token.toUpperCase();
    }

    private static String primitiveGroupKeyToken(CompositionModel.LogicalKind kind) {
        switch (kind) {
            case BOOLEAN: return "Boolean";
            case BYTE: return "Byte";
            case SHORT: return "Short";
            case CHAR: return "Char";
            case INT: return "Int";
            case LONG: return "Long";
            default: return null;
        }
    }

    private static boolean isNumeric(CompositionModel.LogicalKind kind) {
        return kind == CompositionModel.LogicalKind.BYTE
                || kind == CompositionModel.LogicalKind.SHORT
                || kind == CompositionModel.LogicalKind.CHAR
                || kind == CompositionModel.LogicalKind.INT
                || kind == CompositionModel.LogicalKind.LONG
                || kind == CompositionModel.LogicalKind.FLOAT
                || kind == CompositionModel.LogicalKind.DOUBLE;
    }

    private static String camelConstant(String token) {
        StringBuilder result = new StringBuilder(token.length() + 4);
        for (int index = 0; index < token.length(); index++) {
            char current = token.charAt(index);
            if (index != 0 && Character.isUpperCase(current)) result.append('_');
            result.append(Character.toUpperCase(current));
        }
        return result.toString();
    }

    private static String rowProjectionMethod(CompositionModel.LogicalKind kind) {
        switch (kind) {
            case BYTE:
            case SHORT:
            case CHAR:
            case INT:
                return "mapToInt";
            case LONG:
                return "mapToLong";
            case FLOAT:
            case DOUBLE:
                return "mapToDouble";
            default:
                return "map";
        }
    }

    private static void appendStreamType(
            StringBuilder source,
            String tableType,
            String objectType,
            TableShape shape) {
        source.append("    public static final class Stream {\n")
                .append("        private final ").append(tableType).append(" owner;\n")
                .append("        private final io.github.somaruntime.soma.internal.GeneratedPipeline pipeline;\n\n")
                .append("        private Stream(").append(tableType).append(" owner, ")
                .append("io.github.somaruntime.soma.internal.GeneratedPipeline pipeline) {\n")
                .append("            this.owner = owner;\n")
                .append("            this.pipeline = pipeline;\n        }\n\n");
        appendRowProjectionMethods(
                source,
                shape,
                "        ",
                "owner",
                "pipeline",
                "owner.borrowedView",
                "owner.runtime");
        appendGroupByMethods(
                source, shape, "        ", "owner", "pipeline", "owner.runtime");
        source.append("        public Stream parallel() {\n")
                .append("            return new Stream(owner, pipeline.parallel());\n        }\n\n")
                .append("        public Selection filter(io.github.somaruntime.soma.SomaExpression<View> expression) {\n")
                .append("            return new Selection(owner, pipeline.filter(expression));\n        }\n\n")
                .append("        public Selection filter(io.github.somaruntime.soma.SomaPredicate<? super View> predicate) {\n")
                .append("            owner.runtime.requireArgument(predicate, io.github.somaruntime.soma.SomaOperation.QUERY, \"predicate\");\n")
                .append("            return new Selection(owner, pipeline.filter(() -> predicate.test(owner.borrowedView)));\n        }\n\n")
                .append("        public Selection sorted(java.util.Comparator<? super View> comparator) {\n")
                .append("            owner.runtime.requireArgument(comparator, io.github.somaruntime.soma.SomaOperation.QUERY, \"comparator\");\n")
                .append("            return new Selection(owner, pipeline.sorted(() -> comparator.compare(owner.borrowedView, owner.borrowedCompareView)));\n        }\n\n")
                .append("        public Selection sortedBy(io.github.somaruntime.soma.SomaOrder<View> order) {\n")
                .append("            return new Selection(owner, pipeline.sortedBy(order));\n        }\n\n")
                .append("        public Selection skip(long count) { return new Selection(owner, pipeline.skip(count)); }\n\n")
                .append("        public Selection limit(long count) { return new Selection(owner, pipeline.limit(count)); }\n\n")
                .append("        public Selection top(long count, io.github.somaruntime.soma.SomaOrder<View> order) {\n")
                .append("            return new Selection(owner, pipeline.top(count, order));\n        }\n\n")
                .append("        public long count() { return pipeline.count(); }\n\n")
                .append("        public boolean anyMatch(io.github.somaruntime.soma.SomaPredicate<? super View> predicate) {\n")
                .append("            owner.runtime.requireArgument(predicate, io.github.somaruntime.soma.SomaOperation.QUERY, \"predicate\");\n")
                .append("            return pipeline.anyMatch(() -> predicate.test(owner.borrowedView));\n        }\n\n")
                .append("        public boolean allMatch(io.github.somaruntime.soma.SomaPredicate<? super View> predicate) {\n")
                .append("            owner.runtime.requireArgument(predicate, io.github.somaruntime.soma.SomaOperation.QUERY, \"predicate\");\n")
                .append("            return pipeline.allMatch(() -> predicate.test(owner.borrowedView));\n        }\n\n")
                .append("        public boolean noneMatch(io.github.somaruntime.soma.SomaPredicate<? super View> predicate) {\n")
                .append("            owner.runtime.requireArgument(predicate, io.github.somaruntime.soma.SomaOperation.QUERY, \"predicate\");\n")
                .append("            return pipeline.noneMatch(() -> predicate.test(owner.borrowedView));\n        }\n\n")
                .append("        public java.util.Optional<").append(objectType).append("> findFirst() {\n")
                .append("            return pipeline.findFirst(() -> owner.borrowedView.fetch());\n        }\n\n")
                .append("        public void forEach(java.util.function.Consumer<? super View> action) {\n")
                .append("            owner.runtime.requireArgument(action, io.github.somaruntime.soma.SomaOperation.QUERY, \"action\");\n")
                .append("            pipeline.forEach(() -> action.accept(owner.borrowedView));\n        }\n\n")
                .append("        public void forEachOrdered(java.util.function.Consumer<? super View> action) { forEach(action); }\n\n")
                .append("        public java.util.List<").append(objectType).append("> toList() {\n")
                .append("            return pipeline.toList(() -> owner.borrowedView.fetch());\n        }\n\n")
                .append("        public ").append(objectType).append("[] toArray() {\n")
                .append("            return pipeline.toArray(() -> owner.borrowedView.fetch(), ")
                .append(objectType).append(".class);\n        }\n\n")
                .append("        public java.lang.String _explain() { return pipeline.explain(); }\n")
                .append("    }\n\n");
    }

    private static void appendSelectionTypes(
            StringBuilder source,
            String tableType,
            String objectType,
            TableShape shape) {
        source.append("    public static final class Selection {\n")
                .append("        private final ").append(tableType).append(" owner;\n")
                .append("        private final io.github.somaruntime.soma.internal.GeneratedPipeline pipeline;\n\n")
                .append("        private Selection(").append(tableType).append(" owner, ")
                .append("io.github.somaruntime.soma.internal.GeneratedPipeline pipeline) {\n")
                .append("            this.owner = owner;\n")
                .append("            this.pipeline = pipeline;\n        }\n\n");
        appendRowProjectionMethods(
                source,
                shape,
                "        ",
                "owner",
                "pipeline",
                "owner.borrowedView",
                "owner.runtime");
        appendGroupByMethods(
                source, shape, "        ", "owner", "pipeline", "owner.runtime");
        source
                .append("        public Selection parallel() {\n")
                .append("            return new Selection(owner, pipeline.parallel());\n        }\n\n")
                .append("        public Selection filter(")
                .append("io.github.somaruntime.soma.SomaExpression<View> expression) {\n")
                .append("            return new Selection(owner, pipeline.filter(expression));\n        }\n\n")
                .append("        public Selection filter(\n")
                .append("                io.github.somaruntime.soma.SomaPredicate<? super View> predicate) {\n")
                .append("            if (predicate == null) throw owner.runtime.invalidQuery(\"predicate\");\n")
                .append("            return new Selection(owner, pipeline.filter(() -> ")
                .append("predicate.test(owner.borrowedView)));\n        }\n\n")
                .append("        public Selection sorted(\n")
                .append("                java.util.Comparator<? super View> comparator) {\n")
                .append("            if (comparator == null) throw owner.runtime.invalidQuery(\"comparator\");\n")
                .append("            return new Selection(owner, pipeline.sorted(() -> comparator.compare(")
                .append("owner.borrowedView, owner.borrowedCompareView)));\n        }\n\n")
                .append("        public Selection sortedBy(")
                .append("io.github.somaruntime.soma.SomaOrder<View> order) {\n")
                .append("            return new Selection(owner, pipeline.sortedBy(order));\n        }\n\n")
                .append("        public Selection skip(long count) {\n")
                .append("            return new Selection(owner, pipeline.skip(count));\n        }\n\n")
                .append("        public Selection limit(long count) {\n")
                .append("            return new Selection(owner, pipeline.limit(count));\n        }\n\n")
                .append("        public Selection top(long count, ")
                .append("io.github.somaruntime.soma.SomaOrder<View> order) {\n")
                .append("            return new Selection(owner, pipeline.top(count, order));\n        }\n\n")
                .append("        public long count() { return pipeline.count(); }\n\n")
                .append("        public boolean anyMatch(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super View> predicate) {\n")
                .append("            if (predicate == null) throw owner.runtime.invalidQuery(\"predicate\");\n")
                .append("            return pipeline.anyMatch(() -> predicate.test(owner.borrowedView));\n        }\n\n")
                .append("        public boolean allMatch(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super View> predicate) {\n")
                .append("            if (predicate == null) throw owner.runtime.invalidQuery(\"predicate\");\n")
                .append("            return pipeline.allMatch(() -> predicate.test(owner.borrowedView));\n        }\n\n")
                .append("        public boolean noneMatch(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super View> predicate) {\n")
                .append("            if (predicate == null) throw owner.runtime.invalidQuery(\"predicate\");\n")
                .append("            return pipeline.noneMatch(() -> predicate.test(owner.borrowedView));\n        }\n\n")
                .append("        public java.util.Optional<").append(objectType).append("> findFirst() {\n")
                .append("            return pipeline.findFirst(() -> owner.borrowedView.fetch());\n        }\n\n")
                .append("        public void forEach(java.util.function.Consumer<? super View> action) {\n")
                .append("            if (action == null) throw owner.runtime.invalidQuery(\"action\");\n")
                .append("            pipeline.forEach(() -> action.accept(owner.borrowedView));\n        }\n\n")
                .append("        public void forEachOrdered(")
                .append("java.util.function.Consumer<? super View> action) { forEach(action); }\n\n")
                .append("        public java.util.List<").append(objectType).append("> toList() {\n")
                .append("            return pipeline.toList(() -> owner.borrowedView.fetch());\n        }\n\n")
                .append("        public ").append(objectType).append("[] toArray() {\n")
                .append("            return pipeline.toArray(() -> owner.borrowedView.fetch(), ")
                .append(objectType).append(".class);\n        }\n\n")
                .append("        public java.lang.String _explain() { return pipeline.explain(); }\n")
                .append("\n        public io.github.somaruntime.soma.UpdateResult update(\n")
                .append("                java.util.function.Consumer<? super Editor> updater) {\n")
                .append("            owner.runtime.requireArgument(updater, ")
                .append("io.github.somaruntime.soma.SomaOperation.UPDATE, \"updater\");\n")
                .append("            return pipeline.update(() -> ")
                .append("updater.accept(owner.borrowedSelectionEditor));\n        }\n\n")
                .append("        public io.github.somaruntime.soma.RemoveResult remove() {\n")
                .append("            return pipeline.remove();\n        }\n")
                .append("    }\n\n")
                .append("    public static final class IndexSelection {\n")
                .append("        private final ").append(tableType).append(" owner;\n")
                .append("        private final io.github.somaruntime.soma.internal")
                .append(".GeneratedIndexSelection selection;\n\n")
                .append("        private IndexSelection(").append(tableType)
                .append(" owner, io.github.somaruntime.soma.internal")
                .append(".GeneratedIndexSelection selection) {\n")
                .append("            this.owner = owner;\n")
                .append("            this.selection = selection;\n        }\n\n");
        appendRowProjectionMethods(
                source,
                shape,
                "        ",
                "owner",
                "selection",
                "owner.borrowedView",
                "owner.runtime");
        source
                .append("        public Selection parallel() {\n")
                .append("            return new Selection(owner, selection.parallel());\n        }\n\n")
                .append("        public Selection filter(")
                .append("io.github.somaruntime.soma.SomaExpression<View> expression) {\n")
                .append("            return new Selection(owner, selection.filter(expression));\n        }\n\n")
                .append("        public Selection filter(\n")
                .append("                io.github.somaruntime.soma.SomaPredicate<? super View> predicate) {\n")
                .append("            if (predicate == null) throw owner.runtime.invalidQuery(\"predicate\");\n")
                .append("            return new Selection(owner, selection.filter(() -> ")
                .append("predicate.test(owner.borrowedView)));\n        }\n\n")
                .append("        public Selection sorted(\n")
                .append("                java.util.Comparator<? super View> comparator) {\n")
                .append("            if (comparator == null) throw owner.runtime.invalidQuery(\"comparator\");\n")
                .append("            return new Selection(owner, selection.sorted(() -> comparator.compare(")
                .append("owner.borrowedView, owner.borrowedCompareView)));\n        }\n\n")
                .append("        public Selection sortedBy(")
                .append("io.github.somaruntime.soma.SomaOrder<View> order) {\n")
                .append("            return new Selection(owner, selection.sortedBy(order));\n        }\n\n")
                .append("        public Selection skip(long count) {\n")
                .append("            return new Selection(owner, selection.skip(count));\n        }\n\n")
                .append("        public Selection limit(long count) {\n")
                .append("            return new Selection(owner, selection.limit(count));\n        }\n\n")
                .append("        public Selection top(long count, ")
                .append("io.github.somaruntime.soma.SomaOrder<View> order) {\n")
                .append("            return new Selection(owner, selection.top(count, order));\n        }\n\n")
                .append("        public long count() { return selection.count(); }\n\n")
                .append("        public boolean anyMatch(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super View> predicate) {\n")
                .append("            if (predicate == null) throw owner.runtime.invalidQuery(\"predicate\");\n")
                .append("            return selection.anyMatch(() -> predicate.test(owner.borrowedView));\n        }\n\n")
                .append("        public boolean allMatch(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super View> predicate) {\n")
                .append("            if (predicate == null) throw owner.runtime.invalidQuery(\"predicate\");\n")
                .append("            return selection.allMatch(() -> predicate.test(owner.borrowedView));\n        }\n\n")
                .append("        public boolean noneMatch(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super View> predicate) {\n")
                .append("            if (predicate == null) throw owner.runtime.invalidQuery(\"predicate\");\n")
                .append("            return selection.noneMatch(() -> predicate.test(owner.borrowedView));\n        }\n\n")
                .append("        public java.util.Optional<").append(objectType).append("> findFirst() {\n")
                .append("            return selection.findFirst(() -> owner.borrowedView.fetch());\n        }\n\n")
                .append("        public void forEach(java.util.function.Consumer<? super View> action) {\n")
                .append("            if (action == null) throw owner.runtime.invalidQuery(\"action\");\n")
                .append("            selection.forEach(() -> action.accept(owner.borrowedView));\n        }\n\n")
                .append("        public void forEachOrdered(")
                .append("java.util.function.Consumer<? super View> action) { forEach(action); }\n\n")
                .append("        public java.util.List<").append(objectType).append("> toList() {\n")
                .append("            return selection.toList(() -> owner.borrowedView.fetch());\n        }\n\n")
                .append("        public ").append(objectType).append("[] toArray() {\n")
                .append("            return selection.toArray(() -> owner.borrowedView.fetch(), ")
                .append(objectType).append(".class);\n        }\n\n")
                .append("        public java.lang.String _explain() { return selection.explain(); }\n")
                .append("\n        public io.github.somaruntime.soma.UpdateResult update(\n")
                .append("                java.util.function.Consumer<? super Editor> updater) {\n")
                .append("            owner.runtime.requireArgument(updater, ")
                .append("io.github.somaruntime.soma.SomaOperation.UPDATE, \"updater\");\n")
                .append("            return selection.update(() -> ")
                .append("updater.accept(owner.borrowedSelectionEditor));\n        }\n\n")
                .append("        public io.github.somaruntime.soma.RemoveResult remove() {\n")
                .append("            return selection.remove();\n        }\n")
                .append("    }\n\n");
    }

    private static void appendEndpoint(
            StringBuilder source,
            EndpointPlan endpoint,
            String indent) {
        String marker = endpoint.field.type().keyable()
                ? "io.github.somaruntime.soma.SomaKeyableField"
                : "io.github.somaruntime.soma.SomaFieldEndpoint";
        source.append(indent).append("public final class ").append(endpoint.typeName())
                .append(" implements ").append(marker).append("<View, ")
                .append(endpoint.field.type().boxedTypeName()).append("> {\n");
        for (EndpointPlan child : endpoint.children) {
            source.append(indent).append("    public final ").append(child.typeName()).append(' ')
                    .append(child.field.name()).append(";\n");
        }
        source.append('\n').append(indent).append("    private ").append(endpoint.typeName())
                .append("() {\n");
        for (EndpointPlan child : endpoint.children) {
            source.append(indent).append("        this.").append(child.field.name())
                    .append(" = new ").append(child.typeName()).append("();\n");
        }
        source.append(indent).append("    }\n\n")
                .append(indent).append("    private Stream project(\n")
                .append(indent).append("            io.github.somaruntime.soma.internal")
                .append(".GeneratedFieldPipeline pipeline) {\n")
                .append(indent).append("        return new Stream(");
        if (isPrimitive(endpoint.field.type().kind())) {
            source.append("pipeline.primitive")
                    .append(primitiveToken(endpoint.field.type().kind()))
                    .append("(() -> ").append(endpoint.valueExpression).append("));\n");
        } else {
            source.append("pipeline);\n");
        }
        source
                .append(indent).append("    }\n\n");
        appendEndpointMethods(source, endpoint, indent + "    ");
        for (EndpointPlan child : endpoint.children) {
            appendEndpoint(source, child, indent + "    ");
        }
        source.append(indent).append("}\n\n");
    }

    private static void appendEndpointMethods(
            StringBuilder source,
            EndpointPlan endpoint,
            String indent) {
        CompositionModel.TypeModel type = endpoint.field.type();
        source.append(indent)
                .append("public io.github.somaruntime.soma.FieldMetadata _metadata() {\n")
                .append(indent).append("    return runtime.fieldMetadata(")
                .append(endpoint.planIndex).append(", \"")
                .append(endpoint.logicalPath()).append("\", \"")
                .append(endpoint.field.typeName()).append("\", ")
                .append(type.intrinsicEquality()).append(", ")
                .append(type.naturalOrder()).append(");\n")
                .append(indent).append("}\n\n");
        if (type.intrinsicEquality()) {
            appendComparisonMethod(source, endpoint, indent, "eq");
            appendComparisonMethod(source, endpoint, indent, "ne");
            appendInMethod(source, endpoint, indent);
        }
        if (type.naturalOrder()) {
            appendComparisonMethod(source, endpoint, indent, "lt");
            appendComparisonMethod(source, endpoint, indent, "le");
            appendComparisonMethod(source, endpoint, indent, "gt");
            appendComparisonMethod(source, endpoint, indent, "ge");
            appendBetweenMethod(source, endpoint, indent);
            source.append(indent).append("public io.github.somaruntime.soma.SomaOrder<View> asc() { ")
                    .append("return runtime.asc(").append(endpoint.planIndex)
                    .append("); }\n\n")
                    .append(indent).append("public io.github.somaruntime.soma.SomaOrder<View> desc() { ")
                    .append("return runtime.desc(").append(endpoint.planIndex)
                    .append("); }\n\n");
        }
        if (type.nullable()) {
            source.append(indent).append("public io.github.somaruntime.soma.SomaExpression<View> ")
                    .append("isNull() { return runtime.isNull(")
                    .append(endpoint.planIndex).append("); }\n\n")
                    .append(indent).append("public io.github.somaruntime.soma.SomaExpression<View> ")
                    .append("isNotNull() { return runtime.isNotNull(")
                    .append(endpoint.planIndex).append("); }\n\n");
        }
        if (isPrimitive(type.kind())) {
            appendPrimitiveFieldSource(source, endpoint, indent);
        } else {
            appendReferenceFieldSource(source, endpoint, indent);
        }
    }

    private static void appendPrimitiveFieldSource(
            StringBuilder source,
            EndpointPlan endpoint,
            String indent) {
        CompositionModel.TypeModel type = endpoint.field.type();
        String token = primitiveToken(type.kind());
        String value = endpoint.valueExpression;
        source.append(indent).append("private io.github.somaruntime.soma.internal")
                .append(".GeneratedPrimitiveValuePipeline source() {\n")
                .append(indent).append("    return runtime.fieldSource(")
                .append(endpoint.planIndex).append(").primitive").append(token)
                .append("(() -> ").append(value).append(");\n")
                .append(indent).append("}\n\n")
                .append(indent).append("private void require(java.lang.Object value, ")
                .append("java.lang.String category) { runtime.requireArgument(value, ")
                .append("io.github.somaruntime.soma.SomaOperation.QUERY, category); }\n\n")
                .append(indent).append("public Stream parallel() { return new Stream(source().parallel()); }\n\n")
                .append(indent).append("public Stream filter(")
                .append("io.github.somaruntime.soma.Soma").append(token)
                .append("Predicate predicate) { require(predicate, \"predicate\"); return new Stream(")
                .append("source().filter").append(token).append("(predicate)); }\n\n")
                .append(indent).append("public Stream map(")
                .append("io.github.somaruntime.soma.Soma").append(token)
                .append("UnaryOperator mapper) { require(mapper, \"mapper\"); return new Stream(")
                .append("source().map").append(token).append("(mapper)); }\n\n")
                .append(indent).append("public Stream distinct() { ")
                .append("return new Stream(source()).distinct(); }\n\n");
        appendPrimitiveConversions(source, type.kind(), indent, "source()");
        if (type.naturalOrder()) {
            source.append(indent).append("public Stream sorted() { ")
                    .append("return new Stream(source()).sorted(); }\n\n")
                    .append(indent).append("public Stream top(long count) { ")
                    .append("return new Stream(source()).top(count); }\n\n");
        }
        source.append(indent).append("public Stream skip(long count) { ")
                .append("return new Stream(source()).skip(count); }\n\n")
                .append(indent).append("public Stream limit(long count) { ")
                .append("return new Stream(source()).limit(count); }\n\n")
                .append(indent).append("public long count() { return source().count(); }\n\n")
                .append(indent).append("public boolean anyMatch(")
                .append("io.github.somaruntime.soma.Soma").append(token)
                .append("Predicate predicate) { return new Stream(source()).anyMatch(predicate); }\n\n")
                .append(indent).append("public boolean allMatch(")
                .append("io.github.somaruntime.soma.Soma").append(token)
                .append("Predicate predicate) { return new Stream(source()).allMatch(predicate); }\n\n")
                .append(indent).append("public boolean noneMatch(")
                .append("io.github.somaruntime.soma.Soma").append(token)
                .append("Predicate predicate) { return new Stream(source()).noneMatch(predicate); }\n\n");
        appendExactPrimitiveTerminals(source, type.kind(), indent, "source()");
        source.append(indent).append("public final class Stream {\n")
                .append(indent).append("    private final io.github.somaruntime.soma.internal")
                .append(".GeneratedPrimitiveValuePipeline pipeline;\n\n")
                .append(indent).append("    private Stream(io.github.somaruntime.soma.internal")
                .append(".GeneratedPrimitiveValuePipeline pipeline) { this.pipeline = pipeline; }\n\n")
                .append(indent).append("    public Stream parallel() { return new Stream(pipeline.parallel()); }\n\n")
                .append(indent).append("    public Stream filter(")
                .append("io.github.somaruntime.soma.Soma").append(token)
                .append("Predicate predicate) {\n")
                .append(indent).append("        require(predicate, \"predicate\");\n")
                .append(indent).append("        return new Stream(pipeline.filter")
                .append(token).append("(predicate));\n")
                .append(indent).append("    }\n\n")
                .append(indent).append("    public Stream map(")
                .append("io.github.somaruntime.soma.Soma").append(token)
                .append("UnaryOperator mapper) {\n")
                .append(indent).append("        require(mapper, \"mapper\");\n")
                .append(indent).append("        return new Stream(pipeline.map")
                .append(token).append("(mapper));\n")
                .append(indent).append("    }\n\n")
                .append(indent).append("    public Stream distinct() { ")
                .append("return new Stream(pipeline.distinct()); }\n\n");
        appendPrimitiveConversions(source, type.kind(), indent + "    ", "pipeline");
        if (type.naturalOrder()) {
            source.append(indent).append("    public Stream sorted() { ")
                    .append("return new Stream(pipeline.sorted()); }\n\n")
                    .append(indent).append("    public Stream top(long count) { ")
                    .append("return new Stream(pipeline.top(count)); }\n\n");
        }
        source.append(indent).append("    public Stream skip(long count) { ")
                .append("return new Stream(pipeline.skip(count)); }\n\n")
                .append(indent).append("    public Stream limit(long count) { ")
                .append("return new Stream(pipeline.limit(count)); }\n\n")
                .append(indent).append("    public long count() { return pipeline.count(); }\n\n")
                .append(indent).append("    public boolean anyMatch(")
                .append("io.github.somaruntime.soma.Soma").append(token)
                .append("Predicate predicate) { require(predicate, \"predicate\"); return ")
                .append("pipeline.anyMatch").append(token).append("(predicate); }\n\n")
                .append(indent).append("    public boolean allMatch(")
                .append("io.github.somaruntime.soma.Soma").append(token)
                .append("Predicate predicate) { require(predicate, \"predicate\"); return ")
                .append("pipeline.allMatch").append(token).append("(predicate); }\n\n")
                .append(indent).append("    public boolean noneMatch(")
                .append("io.github.somaruntime.soma.Soma").append(token)
                .append("Predicate predicate) { require(predicate, \"predicate\"); return ")
                .append("pipeline.noneMatch").append(token).append("(predicate); }\n\n");
        appendExactPrimitiveTerminals(
                source, type.kind(), indent + "    ", "pipeline");
        source.append(indent).append("}\n\n");
    }

    private static void appendPrimitiveConversions(
            StringBuilder source,
            CompositionModel.LogicalKind kind,
            String indent,
            String receiver) {
        String token = primitiveToken(kind);
        if (kind != CompositionModel.LogicalKind.INT) {
            source.append(indent).append("public io.github.somaruntime.soma.SomaIntStream mapToInt(")
                    .append("io.github.somaruntime.soma.Soma").append(token)
                    .append("ToIntFunction mapper) { require(mapper, \"mapper\"); return ")
                    .append(receiver).append(".map").append(token)
                    .append("ToInt(mapper); }\n\n");
        }
        if (kind != CompositionModel.LogicalKind.LONG) {
            source.append(indent).append("public io.github.somaruntime.soma.SomaLongStream mapToLong(")
                    .append("io.github.somaruntime.soma.Soma").append(token)
                    .append("ToLongFunction mapper) { require(mapper, \"mapper\"); return ")
                    .append(receiver).append(".map").append(token)
                    .append("ToLong(mapper); }\n\n");
        }
        if (kind != CompositionModel.LogicalKind.DOUBLE) {
            source.append(indent).append("public io.github.somaruntime.soma.SomaDoubleStream mapToDouble(")
                    .append("io.github.somaruntime.soma.Soma").append(token)
                    .append("ToDoubleFunction mapper) { require(mapper, \"mapper\"); return ")
                    .append(receiver).append(".map").append(token)
                    .append("ToDouble(mapper); }\n\n");
        }
    }

    private static void appendExactPrimitiveTerminals(
            StringBuilder source,
            CompositionModel.LogicalKind kind,
            String indent,
            String receiver) {
        String token = primitiveToken(kind);
        if (kind == CompositionModel.LogicalKind.BOOLEAN) {
            source.append(indent).append("public java.util.Optional<java.lang.Boolean> findFirst() { return ")
                    .append(receiver).append(".findFirstBoolean(); }\n\n");
        } else if (kind == CompositionModel.LogicalKind.LONG) {
            source.append(indent).append("public java.util.OptionalLong findFirst() { return ")
                    .append(receiver).append(".findFirstLong(); }\n\n")
                    .append(indent).append("public java.util.OptionalLong min() { return ")
                    .append(receiver).append(".minLong(false); }\n\n")
                    .append(indent).append("public java.util.OptionalLong max() { return ")
                    .append(receiver).append(".minLong(true); }\n\n");
            appendExactIntegralAggregates(source, indent, receiver);
        } else if (kind == CompositionModel.LogicalKind.FLOAT
                || kind == CompositionModel.LogicalKind.DOUBLE) {
            source.append(indent).append("public java.util.OptionalDouble findFirst() { return ")
                    .append(receiver).append(".findFirstFloating(); }\n\n")
                    .append(indent).append("public java.util.OptionalDouble min() { return ")
                    .append(receiver).append(".minFloating(false); }\n\n")
                    .append(indent).append("public java.util.OptionalDouble max() { return ")
                    .append(receiver).append(".minFloating(true); }\n\n")
                    .append(indent).append("public double sum() { return ")
                    .append(receiver).append(".sumFloating(); }\n\n")
                    .append(indent).append("public java.util.OptionalDouble average() { return ")
                    .append(receiver).append(".averageFloating(); }\n\n")
                    .append(indent).append("public io.github.somaruntime.soma.SomaDoubleSummary summaryStatistics() { return ")
                    .append(receiver).append(".summaryFloating(); }\n\n");
        } else {
            source.append(indent).append("public java.util.OptionalInt findFirst() { return ")
                    .append(receiver).append(".findFirstInt(); }\n\n")
                    .append(indent).append("public java.util.OptionalInt min() { return ")
                    .append(receiver).append(".minInt(false); }\n\n")
                    .append(indent).append("public java.util.OptionalInt max() { return ")
                    .append(receiver).append(".minInt(true); }\n\n");
            appendExactIntegralAggregates(source, indent, receiver);
        }
        source.append(indent).append("public void forEach(io.github.somaruntime.soma.Soma")
                .append(token).append("Consumer action) { require(action, \"action\"); ")
                .append(receiver).append(".forEach").append(token).append("(action); }\n\n")
                .append(indent).append("public void forEachOrdered(io.github.somaruntime.soma.Soma")
                .append(token).append("Consumer action) { forEach(action); }\n\n")
                .append(indent).append("public java.util.List<")
                .append(boxedPrimitive(kind)).append("> toList() { return ")
                .append(receiver).append(".to").append(token).append("List(); }\n\n")
                .append(indent).append("public ").append(primitiveName(kind)).append("[] toArray() { return ")
                .append(receiver).append('.').append(primitiveArrayMethod(kind)).append("(); }\n\n")
                .append(indent).append("public java.lang.String _explain() { return ")
                .append(receiver).append(".explain(); }\n\n");
    }

    private static void appendExactIntegralAggregates(
            StringBuilder source,
            String indent,
            String receiver) {
        source.append(indent).append("public long sum() { return ")
                .append(receiver).append(".sumIntegral(); }\n\n")
                .append(indent).append("public java.util.OptionalDouble average() { return ")
                .append(receiver).append(".averageIntegral(); }\n\n")
                .append(indent).append("public io.github.somaruntime.soma.SomaLongSummary summaryStatistics() { return ")
                .append(receiver).append(".summaryIntegral(); }\n\n");
    }

    private static void appendReferenceFieldSource(
            StringBuilder source,
            EndpointPlan endpoint,
            String indent) {
        CompositionModel.TypeModel type = endpoint.field.type();
        String materialType = type.publicTypeName();
        String elementType = type.kind() == CompositionModel.LogicalKind.VALUE
                ? materialType + ".View"
                : materialType;
        String value = endpoint.valueExpression;
        String compared = endpoint.compareValueExpression();
        String materialized = type.kind() == CompositionModel.LogicalKind.VALUE
                ? value + ".fetch()"
                : value;

        source.append(indent).append("private io.github.somaruntime.soma.internal")
                .append(".GeneratedFieldPipeline source() { return runtime.fieldSource(")
                .append(endpoint.planIndex).append("); }\n\n")
                .append(indent).append("private void require(java.lang.Object value, ")
                .append("java.lang.String category) { runtime.requireArgument(value, ")
                .append("io.github.somaruntime.soma.SomaOperation.QUERY, category); }\n\n")
                .append(indent).append("public Stream parallel() { return new Stream(source().parallel()); }\n\n")
                .append(indent).append("public Stream filter(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super ")
                .append(elementType).append("> predicate) {\n")
                .append(indent).append("    require(predicate, \"predicate\");\n")
                .append(indent).append("    return new Stream(source().filter(() -> predicate.test(")
                .append(value).append(")));\n")
                .append(indent).append("}\n\n");
        appendReferenceMapMethods(
                source, indent, "source()", elementType, value);
        if (type.intrinsicEquality()) {
            source.append(indent).append("public Stream distinct() { ")
                    .append("return new Stream(source().distinct()); }\n\n");
        }
        source.append(indent).append("public Stream sorted(")
                .append("java.util.Comparator<? super ").append(elementType)
                .append("> comparator) {\n")
                .append(indent).append("    require(comparator, \"comparator\");\n")
                .append(indent).append("    return new Stream(source().sorted(() -> ")
                .append("comparator.compare(").append(value).append(", ")
                .append(compared).append(")));\n")
                .append(indent).append("}\n\n");
        if (type.naturalOrder()) {
            source.append(indent).append("public Stream sorted() { ")
                    .append("return new Stream(source().sortedNatural()); }\n\n")
                    .append(indent).append("public Stream top(long count) { ")
                    .append("return new Stream(source().topNatural(count)); }\n\n");
        }
        source.append(indent).append("public Stream top(long count, ")
                .append("java.util.Comparator<? super ").append(elementType)
                .append("> comparator) {\n")
                .append(indent).append("    require(comparator, \"comparator\");\n")
                .append(indent).append("    return new Stream(source().top(count, () -> ")
                .append("comparator.compare(").append(value).append(", ")
                .append(compared).append(")));\n")
                .append(indent).append("}\n\n")
                .append(indent).append("public Stream skip(long count) { ")
                .append("return new Stream(source().skip(count)); }\n\n")
                .append(indent).append("public Stream limit(long count) { ")
                .append("return new Stream(source().limit(count)); }\n\n")
                .append(indent).append("public long count() { return source().count(); }\n\n")
                .append(indent).append("public boolean anyMatch(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super ")
                .append(elementType).append("> predicate) {\n")
                .append(indent).append("    require(predicate, \"predicate\");\n")
                .append(indent).append("    return source().anyMatch(() -> predicate.test(")
                .append(value).append("));\n")
                .append(indent).append("}\n\n")
                .append(indent).append("public boolean allMatch(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super ")
                .append(elementType).append("> predicate) {\n")
                .append(indent).append("    require(predicate, \"predicate\");\n")
                .append(indent).append("    return source().allMatch(() -> predicate.test(")
                .append(value).append("));\n")
                .append(indent).append("}\n\n")
                .append(indent).append("public boolean noneMatch(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super ")
                .append(elementType).append("> predicate) {\n")
                .append(indent).append("    require(predicate, \"predicate\");\n")
                .append(indent).append("    return source().noneMatch(() -> predicate.test(")
                .append(value).append("));\n")
                .append(indent).append("}\n\n")
                .append(indent).append("public java.util.Optional<").append(materialType)
                .append("> findFirst() { return source().findFirst(() -> ")
                .append(materialized).append("); }\n\n");
        if (type.naturalOrder()) {
            source.append(indent).append("public java.util.Optional<").append(materialType)
                    .append("> min() { return new Stream(source().sortedNatural()).findFirst(); }\n\n")
                    .append(indent).append("public java.util.Optional<").append(materialType)
                    .append("> max() { return new Stream(source().sortedNatural(true)).findFirst(); }\n\n");
        }
        source.append(indent).append("public java.util.Optional<").append(materialType)
                .append("> min(java.util.Comparator<? super ").append(elementType)
                .append("> comparator) { require(comparator, \"comparator\"); return sorted(comparator).findFirst(); }\n\n")
                .append(indent).append("public java.util.Optional<").append(materialType)
                .append("> max(java.util.Comparator<? super ").append(elementType)
                .append("> comparator) { require(comparator, \"comparator\"); return new Stream(source().sorted(() -> comparator.compare(")
                .append(compared).append(", ").append(value).append("))).findFirst(); }\n\n")
                .append(indent).append("public void forEach(java.util.function.Consumer<? super ")
                .append(elementType).append("> action) {\n")
                .append(indent).append("    require(action, \"action\");\n")
                .append(indent).append("    source().forEach(() -> action.accept(")
                .append(value).append("));\n")
                .append(indent).append("}\n\n")
                .append(indent).append("public void forEachOrdered(")
                .append("java.util.function.Consumer<? super ").append(elementType)
                .append("> action) { forEach(action); }\n\n")
                .append(indent).append("public java.util.List<").append(materialType)
                .append("> toList() { return source().toList(() -> ")
                .append(materialized).append("); }\n\n")
                .append(indent).append("public ").append(materialType)
                .append("[] toArray() { return (").append(materialType)
                .append("[]) source().toArray(() -> ")
                .append(materialized).append(", ")
                .append(componentClassLiteral(materialType)).append("); }\n\n")
                .append(indent).append("public java.lang.String _explain() { ")
                .append("return source().explain(); }\n\n")
                .append(indent).append("public final class Stream {\n")
                .append(indent).append("    private final io.github.somaruntime.soma.internal")
                .append(".GeneratedFieldPipeline pipeline;\n\n")
                .append(indent).append("    private Stream(io.github.somaruntime.soma.internal")
                .append(".GeneratedFieldPipeline pipeline) { this.pipeline = pipeline; }\n\n");
        appendReferenceFieldStreamBody(
                source, endpoint, indent + "    ", elementType, materialType, value, compared,
                materialized);
        source.append(indent).append("}\n\n");
    }

    private static void appendReferenceFieldStreamBody(
            StringBuilder source,
            EndpointPlan endpoint,
            String indent,
            String elementType,
            String materialType,
            String value,
            String compared,
            String materialized) {
        CompositionModel.TypeModel type = endpoint.field.type();
        source.append(indent).append("public Stream parallel() { return new Stream(pipeline.parallel()); }\n\n")
                .append(indent).append("public Stream filter(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super ")
                .append(elementType).append("> predicate) {\n")
                .append(indent).append("    require(predicate, \"predicate\");\n")
                .append(indent).append("    return new Stream(pipeline.filter(() -> ")
                .append("predicate.test(").append(value).append(")));\n")
                .append(indent).append("}\n\n");
        appendReferenceMapMethods(
                source, indent, "pipeline", elementType, value);
        if (type.intrinsicEquality()) {
            source.append(indent).append("public Stream distinct() { ")
                    .append("return new Stream(pipeline.distinct()); }\n\n");
        }
        source.append(indent).append("public Stream sorted(")
                .append("java.util.Comparator<? super ").append(elementType)
                .append("> comparator) {\n")
                .append(indent).append("    require(comparator, \"comparator\");\n")
                .append(indent).append("    return new Stream(pipeline.sorted(() -> ")
                .append("comparator.compare(").append(value).append(", ")
                .append(compared).append(")));\n")
                .append(indent).append("}\n\n");
        if (type.naturalOrder()) {
            source.append(indent).append("public Stream sorted() { ")
                    .append("return new Stream(pipeline.sortedNatural()); }\n\n")
                    .append(indent).append("public Stream top(long count) { ")
                    .append("return new Stream(pipeline.topNatural(count)); }\n\n");
        }
        source.append(indent).append("public Stream top(long count, ")
                .append("java.util.Comparator<? super ").append(elementType)
                .append("> comparator) {\n")
                .append(indent).append("    require(comparator, \"comparator\");\n")
                .append(indent).append("    return new Stream(pipeline.top(count, () -> ")
                .append("comparator.compare(").append(value).append(", ")
                .append(compared).append(")));\n")
                .append(indent).append("}\n\n")
                .append(indent).append("public Stream skip(long count) { ")
                .append("return new Stream(pipeline.skip(count)); }\n\n")
                .append(indent).append("public Stream limit(long count) { ")
                .append("return new Stream(pipeline.limit(count)); }\n\n")
                .append(indent).append("public long count() { return pipeline.count(); }\n\n")
                .append(indent).append("public boolean anyMatch(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super ")
                .append(elementType).append("> predicate) { require(predicate, \"predicate\"); ")
                .append("return pipeline.anyMatch(() -> predicate.test(").append(value)
                .append(")); }\n\n")
                .append(indent).append("public boolean allMatch(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super ")
                .append(elementType).append("> predicate) { require(predicate, \"predicate\"); ")
                .append("return pipeline.allMatch(() -> predicate.test(").append(value)
                .append(")); }\n\n")
                .append(indent).append("public boolean noneMatch(")
                .append("io.github.somaruntime.soma.SomaPredicate<? super ")
                .append(elementType).append("> predicate) { require(predicate, \"predicate\"); ")
                .append("return pipeline.noneMatch(() -> predicate.test(").append(value)
                .append(")); }\n\n")
                .append(indent).append("public java.util.Optional<").append(materialType)
                .append("> findFirst() { return pipeline.findFirst(() -> ")
                .append(materialized).append("); }\n\n");
        if (type.naturalOrder()) {
            source.append(indent).append("public java.util.Optional<").append(materialType)
                    .append("> min() { return new Stream(pipeline.sortedNatural()).findFirst(); }\n\n")
                    .append(indent).append("public java.util.Optional<").append(materialType)
                    .append("> max() { return new Stream(pipeline.sortedNatural(true)).findFirst(); }\n\n");
        }
        source.append(indent).append("public java.util.Optional<").append(materialType)
                .append("> min(java.util.Comparator<? super ").append(elementType)
                .append("> comparator) { require(comparator, \"comparator\"); return sorted(comparator).findFirst(); }\n\n")
                .append(indent).append("public java.util.Optional<").append(materialType)
                .append("> max(java.util.Comparator<? super ").append(elementType)
                .append("> comparator) { require(comparator, \"comparator\"); return new Stream(pipeline.sorted(() -> comparator.compare(")
                .append(compared).append(", ").append(value).append("))).findFirst(); }\n\n")
                .append(indent).append("public void forEach(java.util.function.Consumer<? super ")
                .append(elementType).append("> action) { require(action, \"action\"); ")
                .append("pipeline.forEach(() -> action.accept(").append(value).append(")); }\n\n")
                .append(indent).append("public void forEachOrdered(")
                .append("java.util.function.Consumer<? super ").append(elementType)
                .append("> action) { forEach(action); }\n\n")
                .append(indent).append("public java.util.List<").append(materialType)
                .append("> toList() { return pipeline.toList(() -> ")
                .append(materialized).append("); }\n\n")
                .append(indent).append("public ").append(materialType)
                .append("[] toArray() { return (").append(materialType)
                .append("[]) pipeline.toArray(() -> ")
                .append(materialized).append(", ")
                .append(componentClassLiteral(materialType)).append("); }\n\n")
                .append(indent).append("public java.lang.String _explain() { ")
                .append("return pipeline.explain(); }\n\n");
    }

    private static void appendReferenceMapMethods(
            StringBuilder source,
            String indent,
            String receiver,
            String elementType,
            String value) {
        source.append(indent).append("public <R> io.github.somaruntime.soma.MappedStream<R> map(\n")
                .append(indent).append("        java.util.function.Function<? super ")
                .append(elementType).append(", ? extends R> mapper) {\n")
                .append(indent).append("    require(mapper, \"mapper\");\n")
                .append(indent).append("    return ").append(receiver)
                .append(".map(() -> mapper.apply(").append(value).append("));\n")
                .append(indent).append("}\n\n")
                .append(indent).append("public io.github.somaruntime.soma.SomaIntStream mapToInt(\n")
                .append(indent).append("        io.github.somaruntime.soma.SomaToIntFunction<? super ")
                .append(elementType).append("> mapper) {\n")
                .append(indent).append("    require(mapper, \"mapper\");\n")
                .append(indent).append("    return ").append(receiver)
                .append(".mapToInt(() -> mapper.applyAsInt(").append(value).append("));\n")
                .append(indent).append("}\n\n")
                .append(indent).append("public io.github.somaruntime.soma.SomaLongStream mapToLong(\n")
                .append(indent).append("        io.github.somaruntime.soma.SomaToLongFunction<? super ")
                .append(elementType).append("> mapper) {\n")
                .append(indent).append("    require(mapper, \"mapper\");\n")
                .append(indent).append("    return ").append(receiver)
                .append(".mapToLong(() -> mapper.applyAsLong(").append(value).append("));\n")
                .append(indent).append("}\n\n")
                .append(indent).append("public io.github.somaruntime.soma.SomaDoubleStream mapToDouble(\n")
                .append(indent).append("        io.github.somaruntime.soma.SomaToDoubleFunction<? super ")
                .append(elementType).append("> mapper) {\n")
                .append(indent).append("    require(mapper, \"mapper\");\n")
                .append(indent).append("    return ").append(receiver)
                .append(".mapToDouble(() -> mapper.applyAsDouble(").append(value).append("));\n")
                .append(indent).append("}\n\n");
    }

    private static void appendComparisonMethod(
            StringBuilder source,
            EndpointPlan endpoint,
            String indent,
            String operation) {
        CompositionModel.TypeModel type = endpoint.field.type();
        source.append(indent).append("public io.github.somaruntime.soma.SomaExpression<View> ")
                .append(operation).append('(').append(type.publicTypeName()).append(" value) {\n")
                .append(indent).append("    io.github.somaruntime.soma.internal.GeneratedProbe probe = ")
                .append("runtime.newProbe(").append(endpoint.planIndex).append(");\n");
        appendWrite(source, type, "value", endpoint.leafStart, "probe", "put",
                indent + "    ", new Counter(), "io.github.somaruntime.soma.SomaOperation.QUERY");
        source.append(indent).append("    return runtime.").append(operation)
                .append("(probe.seal());\n").append(indent).append("}\n\n");
    }

    private static void appendBetweenMethod(
            StringBuilder source,
            EndpointPlan endpoint,
            String indent) {
        CompositionModel.TypeModel type = endpoint.field.type();
        source.append(indent).append("public io.github.somaruntime.soma.SomaExpression<View> between(\n")
                .append(indent).append("        ").append(type.publicTypeName()).append(" lowerInclusive,\n")
                .append(indent).append("        ").append(type.publicTypeName()).append(" upperInclusive) {\n")
                .append(indent).append("    io.github.somaruntime.soma.internal.GeneratedProbe lower = ")
                .append("runtime.newProbe(").append(endpoint.planIndex).append(");\n");
        appendWrite(source, type, "lowerInclusive", endpoint.leafStart, "lower", "put",
                indent + "    ", new Counter(), "io.github.somaruntime.soma.SomaOperation.QUERY");
        source.append(indent).append("    io.github.somaruntime.soma.internal.GeneratedProbe upper = ")
                .append("runtime.newProbe(").append(endpoint.planIndex).append(");\n");
        appendWrite(source, type, "upperInclusive", endpoint.leafStart, "upper", "put",
                indent + "    ", new Counter(), "io.github.somaruntime.soma.SomaOperation.QUERY");
        source.append(indent).append("    return runtime.between(lower.seal(), upper.seal());\n")
                .append(indent).append("}\n\n");
    }

    private static void appendInMethod(
            StringBuilder source,
            EndpointPlan endpoint,
            String indent) {
        CompositionModel.TypeModel type = endpoint.field.type();
        source.append(indent).append("public io.github.somaruntime.soma.SomaExpression<View> in(")
                .append(type.publicTypeName()).append("... values) {\n")
                .append(indent).append("    if (values == null) return runtime.in(null);\n")
                .append(indent).append("    int literalCount = ")
                .append("runtime.requireInLiteralCapacity(values.length);\n")
                .append(indent).append("    io.github.somaruntime.soma.internal.GeneratedProbe[] probes = ")
                .append("new io.github.somaruntime.soma.internal.GeneratedProbe[literalCount];\n")
                .append(indent).append("    for (int index = 0; index < values.length; index++) {\n")
                .append(indent).append("        io.github.somaruntime.soma.internal.GeneratedProbe probe = ")
                .append("runtime.newProbe(").append(endpoint.planIndex).append(");\n");
        appendWrite(source, type, "values[index]", endpoint.leafStart, "probe", "put",
                indent + "        ", new Counter(), "io.github.somaruntime.soma.SomaOperation.QUERY");
        source.append(indent).append("        probes[index] = probe.seal();\n")
                .append(indent).append("    }\n")
                .append(indent).append("    return runtime.in(probes);\n")
                .append(indent).append("}\n\n");
    }

    private static void appendValueEqualsHash(
            StringBuilder source,
            CompositionModel.ValueModel value) {
        source.append("    @java.lang.Override\n")
                .append("    public boolean equals(java.lang.Object other) {\n")
                .append("        if (this == other) return true;\n")
                .append("        if (!(other instanceof ").append(value.simpleName())
                .append(")) return false;\n")
                .append("        ").append(value.simpleName()).append(" that = (")
                .append(value.simpleName()).append(") other;\n        return ");
        for (int index = 0; index < value.fields().size(); index++) {
            if (index != 0) source.append("\n                && ");
            CompositionModel.FieldModel field = value.fields().get(index);
            source.append(equalsExpression(field.type(), "this." + field.name(), "that." + field.name()));
        }
        source.append(";\n    }\n\n")
                .append("    @java.lang.Override\n")
                .append("    public int hashCode() {\n")
                .append("        int result = 1;\n");
        for (CompositionModel.FieldModel field : value.fields()) {
            source.append("        result = 31 * result + ")
                    .append(hashExpression(field.type(), "this." + field.name())).append(";\n");
        }
        source.append("        return result;\n    }\n\n");
    }

    private static void appendValueView(
            StringBuilder source,
            CompositionModel.ValueModel value) {
        source.append("    public static final class View {\n")
                .append("        private final io.github.somaruntime.soma.internal.GeneratedRowAccess row;\n")
                .append("        private final int base;\n");
        for (CompositionModel.FieldModel field : value.fields()) {
            if (field.type().kind() == CompositionModel.LogicalKind.VALUE) {
                source.append("        private final ").append(field.typeName()).append(".View ")
                        .append(field.name()).append("View;\n");
            }
        }
        source.append("\n        private View(java.lang.Object capability, ")
                .append("io.github.somaruntime.soma.internal.GeneratedRowAccess row, int base) {\n")
                .append("            if (!Soma.accepts(capability) || row == null || base < 0) ")
                .append("throw new java.lang.AssertionError(\"invalid Value View capability\");\n")
                .append("            this.row = row;\n            this.base = base;\n")
                .append("            row.registerBorrowedView(this);\n");
        for (CompositionModel.FieldModel field : value.fields()) {
            if (field.type().kind() == CompositionModel.LogicalKind.VALUE) {
                source.append("            this.").append(field.name()).append("View = ")
                        .append(field.typeName()).append(".View.create(capability, row, base + ")
                        .append(field.firstLeaf()).append(");\n");
            }
        }
        source.append("        }\n\n")
                .append("        static View create(java.lang.Object capability, ")
                .append("io.github.somaruntime.soma.internal.GeneratedRowAccess row, int base) {\n")
                .append("            return new View(capability, row, base);\n        }\n\n");
        for (CompositionModel.FieldModel field : value.fields()) {
            source.append("        public ")
                    .append(field.type().kind() == CompositionModel.LogicalKind.VALUE
                            ? field.typeName() + ".View"
                            : field.typeName())
                    .append(' ')
                    .append(field.name()).append("() {\n            return ");
            if (field.type().kind() == CompositionModel.LogicalKind.VALUE) {
                source.append(field.name()).append("View");
            } else {
                source.append(materializeDynamic(
                        field.type(), "row", "view", "base", field.firstLeaf()));
            }
            source.append(";\n        }\n\n");
        }
        source.append("        public ").append(value.simpleName()).append(" fetch() {\n")
                .append("            return new ").append(value.simpleName()).append('(');
        for (int index = 0; index < value.fields().size(); index++) {
            if (index != 0) source.append(", ");
            CompositionModel.FieldModel field = value.fields().get(index);
            source.append(field.name()).append("()");
            if (field.type().kind() == CompositionModel.LogicalKind.VALUE) source.append(".fetch()");
        }
        source.append(");\n        }\n    }\n\n");
    }

    private static void appendWrite(
            StringBuilder source,
            CompositionModel.TypeModel type,
            String expression,
            int leafStart,
            String target,
            String prefix,
            String indent,
            Counter counter,
            String operation) {
        if (type.kind() == CompositionModel.LogicalKind.VALUE) {
            String local = "_value" + counter.next();
            source.append(indent).append(type.publicTypeName()).append(' ').append(local)
                    .append(" = ").append(expression).append(";\n")
                    .append(indent).append(target).append(".requireArgument(")
                    .append(local).append(", ")
                    .append(operation).append(", \"Value\");\n");
            for (CompositionModel.FieldModel child : type.value().fields()) {
                appendWrite(
                        source,
                        child.type(),
                        local + "." + child.name() + "()",
                        leafStart + child.firstLeaf(),
                        target,
                        prefix,
                        indent,
                        counter,
                        operation);
            }
            return;
        }
        source.append(indent).append(target).append('.').append(prefix)
                .append(accessorSuffix(type.kind())).append('(')
                .append(leafStart).append(", ").append(expression).append(");\n");
    }

    private static void appendRequired(
            StringBuilder source,
            CompositionModel.TypeModel type,
            String expression,
            String indent,
            String operation,
            String category) {
        if (!type.primitive()
                && (type.kind() == CompositionModel.LogicalKind.VALUE
                || type.kind() == CompositionModel.LogicalKind.STRING
                || type.kind() == CompositionModel.LogicalKind.ENUM)) {
            source.append(indent).append("runtime.requireArgument(").append(expression)
                    .append(", io.github.somaruntime.soma.SomaOperation.")
                    .append(operation).append(", \"").append(category).append("\");\n");
        }
    }

    private static String materialize(
            CompositionModel.TypeModel type,
            String row,
            String prefix,
            int leafStart) {
        if (type.kind() == CompositionModel.LogicalKind.VALUE) {
            StringBuilder result = new StringBuilder("new ")
                    .append(type.publicTypeName()).append('(');
            for (int index = 0; index < type.value().fields().size(); index++) {
                if (index != 0) result.append(", ");
                CompositionModel.FieldModel child = type.value().fields().get(index);
                result.append(materialize(
                        child.type(), row, prefix, leafStart + child.firstLeaf()));
            }
            return result.append(')').toString();
        }
        String call = row + "." + prefix + accessorSuffix(type.kind())
                + "(" + leafStart + ")";
        return type.primitive() ? call : "(" + type.publicTypeName() + ") " + call;
    }

    private static String materializeDynamic(
            CompositionModel.TypeModel type,
            String row,
            String prefix,
            String base,
            int relativeLeaf) {
        String call = row + "." + prefix + accessorSuffix(type.kind())
                + "(" + base + " + " + relativeLeaf + ")";
        return type.primitive() ? call : "(" + type.publicTypeName() + ") " + call;
    }

    private static String equalsExpression(
            CompositionModel.TypeModel type,
            String left,
            String right) {
        switch (type.kind()) {
            case FLOAT:
                return "java.lang.Float.floatToIntBits(" + left + ") == "
                        + "java.lang.Float.floatToIntBits(" + right + ")";
            case DOUBLE:
                return "java.lang.Double.doubleToLongBits(" + left + ") == "
                        + "java.lang.Double.doubleToLongBits(" + right + ")";
            case STRING:
                return "java.util.Objects.equals(" + left + ", " + right + ")";
            case ENUM:
                return left + " == " + right;
            case VALUE:
                return left + ".equals(" + right + ")";
            default:
                return left + " == " + right;
        }
    }

    private static String hashExpression(CompositionModel.TypeModel type, String value) {
        switch (type.kind()) {
            case BOOLEAN:
                return value + " ? 1231 : 1237";
            case BYTE:
            case SHORT:
            case CHAR:
            case INT:
                return "(int) " + value;
            case LONG:
                return "(int) (" + value + " ^ (" + value + " >>> 32))";
            case FLOAT:
                return "java.lang.Float.floatToIntBits(" + value + ")";
            case DOUBLE:
                return "(int) (java.lang.Double.doubleToLongBits(" + value + ") ^ "
                        + "(java.lang.Double.doubleToLongBits(" + value + ") >>> 32))";
            case STRING:
            case VALUE:
                return "(" + value + " == null ? 0 : " + value + ".hashCode())";
            case ENUM:
                return "(" + value + " == null ? 0 : java.lang.System.identityHashCode("
                        + value + "))";
            default:
                throw new AssertionError("ordinary Object cannot be a Value leaf");
        }
    }

    private static String accessorSuffix(CompositionModel.LogicalKind kind) {
        switch (kind) {
            case BOOLEAN: return "Boolean";
            case BYTE: return "Byte";
            case SHORT: return "Short";
            case CHAR: return "Char";
            case INT: return "Int";
            case LONG: return "Long";
            case FLOAT: return "Float";
            case DOUBLE: return "Double";
            case STRING:
            case ENUM:
            case OBJECT: return "Reference";
            default: throw new AssertionError("Value is recursively lowered");
        }
    }

    private static String leafConstant(CompositionModel.LeafKind kind) {
        return kind == CompositionModel.LeafKind.REFERENCE ? "REFERENCE" : kind.name();
    }

    private static String equalityConstant(CompositionModel.EqualityKind kind) {
        return "EQ_" + kind.name();
    }

    private static boolean isPrimitive(CompositionModel.LogicalKind kind) {
        return kind == CompositionModel.LogicalKind.BOOLEAN
                || kind == CompositionModel.LogicalKind.BYTE
                || kind == CompositionModel.LogicalKind.SHORT
                || kind == CompositionModel.LogicalKind.CHAR
                || kind == CompositionModel.LogicalKind.INT
                || kind == CompositionModel.LogicalKind.LONG
                || kind == CompositionModel.LogicalKind.FLOAT
                || kind == CompositionModel.LogicalKind.DOUBLE;
    }

    private static String componentClassLiteral(String typeName) {
        int arguments = typeName.indexOf('<');
        return (arguments < 0 ? typeName : typeName.substring(0, arguments)) + ".class";
    }

    private static String primitiveToken(CompositionModel.LogicalKind kind) {
        String value = kind.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String primitiveName(CompositionModel.LogicalKind kind) {
        return kind.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String boxedPrimitive(CompositionModel.LogicalKind kind) {
        switch (kind) {
            case BOOLEAN: return "java.lang.Boolean";
            case BYTE: return "java.lang.Byte";
            case SHORT: return "java.lang.Short";
            case CHAR: return "java.lang.Character";
            case INT: return "java.lang.Integer";
            case LONG: return "java.lang.Long";
            case FLOAT: return "java.lang.Float";
            case DOUBLE: return "java.lang.Double";
            default: throw new AssertionError("not a primitive Field");
        }
    }

    private static String primitiveArrayMethod(CompositionModel.LogicalKind kind) {
        return "to" + primitiveToken(kind) + "Array";
    }

    private static void appendParameters(
            StringBuilder source,
            List<CompositionModel.FieldModel> fields) {
        for (int index = 0; index < fields.size(); index++) {
            if (index != 0) source.append(", ");
            CompositionModel.FieldModel field = fields.get(index);
            source.append(field.typeName()).append(' ').append(field.name());
        }
    }

    private static StringBuilder header(CompositionModel model, int capacity) {
        return new StringBuilder(capacity)
                .append("// Generated by SOMA. Do not edit.\n")
                .append("package ").append(model.generatedPackage()).append(";\n\n");
    }

    private static final class Counter {
        private int value;
        int next() { return value++; }
    }

    private static final class TableShape {
        private final List<EndpointPlan> roots = new ArrayList<EndpointPlan>();
        private final List<EndpointPlan> all = new ArrayList<EndpointPlan>();
        private final List<EndpointPlan> indexes = new ArrayList<EndpointPlan>();
        private EndpointPlan key;

        private TableShape(CompositionModel.TableModel table) {
            for (CompositionModel.FieldModel field : table.fields()) {
                EndpointPlan root = add(
                        field,
                        field.firstLeaf(),
                        "borrowedView." + field.name() + "()",
                        null,
                        all);
                roots.add(root);
                if (field.role() == CompositionModel.FieldRole.KEY) key = root;
                if (field.role() == CompositionModel.FieldRole.INDEX) indexes.add(root);
            }
        }

        private static EndpointPlan add(
                CompositionModel.FieldModel field,
                int leafStart,
                String valueExpression,
                EndpointPlan parent,
                List<EndpointPlan> all) {
            EndpointPlan result = new EndpointPlan(
                    field, leafStart, all.size(), valueExpression, parent);
            all.add(result);
            if (field.type().kind() == CompositionModel.LogicalKind.VALUE) {
                for (CompositionModel.FieldModel child : field.type().value().fields()) {
                    result.children.add(add(
                            child,
                            leafStart + child.firstLeaf(),
                            valueExpression + "." + child.name() + "()",
                            result,
                            all));
                }
            }
            return result;
        }
    }

    private static final class EndpointPlan {
        private final CompositionModel.FieldModel field;
        private final int leafStart;
        private final int planIndex;
        private final String valueExpression;
        private final EndpointPlan parent;
        private final List<EndpointPlan> children = new ArrayList<EndpointPlan>();

        private EndpointPlan(
                CompositionModel.FieldModel field,
                int leafStart,
                int planIndex,
                String valueExpression,
                EndpointPlan parent) {
            this.field = field;
            this.leafStart = leafStart;
            this.planIndex = planIndex;
            this.valueExpression = valueExpression;
            this.parent = parent;
        }

        private String typeName() {
            return GeneratedNames.fieldEndpointType(field.name());
        }

        private String qualifiedTypeName() {
            return parent == null
                    ? typeName()
                    : parent.qualifiedTypeName() + "." + typeName();
        }

        private String memberExpression(String owner) {
            return valueExpression
                    .replace("borrowedView", owner)
                    .replace("()", "");
        }

        private String logicalPath() {
            return parent == null
                    ? field.name()
                    : parent.logicalPath() + "." + field.name();
        }

        private String compareValueExpression() {
            return valueExpression.replace("borrowedView", "borrowedCompareView");
        }
    }
}
