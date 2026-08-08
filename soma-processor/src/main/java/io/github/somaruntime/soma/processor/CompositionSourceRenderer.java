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
                .append("        return newGroup();\n    }\n\n");
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
                .append("    private static SomaGroup newGroup() {\n")
                .append("        io.github.somaruntime.soma.internal.GeneratedGroup runtime = ")
                .append("io.github.somaruntime.soma.internal.GeneratedRuntime.createGroup(\n")
                .append("                java.lang.invoke.MethodHandles.lookup(), CAPABILITY);\n")
                .append("        return SomaGroup.create(CAPABILITY, runtime);\n    }\n\n")
                .append("    private static final class DefaultGroupHolder {\n")
                .append("        private static final SomaGroup INSTANCE = newGroup();\n")
                .append("    }\n")
                .append("}\n");
        return source.toString();
    }

    static String group(CompositionModel model) {
        StringBuilder source = header(model, 4800);
        source.append("public final class SomaGroup {\n\n")
                .append("    private final java.lang.Object capability;\n")
                .append("    private final io.github.somaruntime.soma.internal.GeneratedGroup runtime;\n");
        for (CompositionModel.TableModel table : model.tables()) {
            source.append("    private volatile ")
                    .append(GeneratedNames.tableType(table.simpleName())).append(' ')
                    .append(GeneratedNames.tableAccessor(table.simpleName())).append(";\n");
        }
        source.append("\n    private SomaGroup(\n")
                .append("            java.lang.Object capability,\n")
                .append("            io.github.somaruntime.soma.internal.GeneratedGroup runtime) {\n")
                .append("        if (runtime == null) throw new java.lang.AssertionError(")
                .append("\"generated runtime Group is missing\");\n")
                .append("        runtime.requireCapability(capability);\n")
                .append("        this.capability = capability;\n")
                .append("        this.runtime = runtime;\n    }\n\n")
                .append("    static SomaGroup create(\n")
                .append("            java.lang.Object capability,\n")
                .append("            io.github.somaruntime.soma.internal.GeneratedGroup runtime) {\n")
                .append("        return new SomaGroup(capability, runtime);\n    }\n\n");
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
                .append("    private final io.github.somaruntime.soma.internal.GeneratedTable runtime;\n")
                .append("    private final View borrowedView;\n")
                .append("    private final Editor borrowedEditor;\n");
        for (EndpointPlan endpoint : shape.roots) {
            source.append("    public final ").append(endpoint.typeName()).append(' ')
                    .append(endpoint.field.name()).append(";\n");
        }
        source.append("\n    private ").append(tableType).append("(\n")
                .append("            java.lang.Object capability,\n")
                .append("            io.github.somaruntime.soma.internal.GeneratedGroup group) {\n")
                .append("        if (group == null) throw new java.lang.AssertionError(")
                .append("\"generated Group is missing\");\n")
                .append("        this.runtime = group.createTable(\n")
                .append("                java.lang.invoke.MethodHandles.lookup(), capability, \"")
                .append(table.simpleName()).append("\", LAYOUT);\n")
                .append("        io.github.somaruntime.soma.internal.GeneratedRow row = ")
                .append("runtime.borrowedRow();\n")
                .append("        this.borrowedView = new View(capability, row);\n")
                .append("        this.borrowedEditor = new Editor(capability, row);\n");
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
        appendTableViewEditor(source, table, objectType);
        appendSelectionTypes(source);
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
                .append(table.defaultCapacity()).append("L,\n")
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
        source.append("    public long size() { return runtime.size(); }\n\n")
                .append("    public long capacity() { return runtime.capacity(); }\n\n")
                .append("    public void reserve(long expectedRows) { runtime.reserve(expectedRows); }\n\n")
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
                .append("    public Selection selectAll() {\n")
                .append("        return new Selection(runtime.selectAll());\n    }\n\n")
                .append("    public Selection filter(")
                .append("io.github.somaruntime.soma.SomaExpression<View> expression) {\n")
                .append("        return new Selection(runtime.filter(expression));\n    }\n\n");

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
            source.append("        return new IndexSelection(runtime.indexSelection(")
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
                .append("        private final io.github.somaruntime.soma.internal.GeneratedRow row;\n");
        for (CompositionModel.FieldModel field : table.fields()) {
            if (field.type().kind() == CompositionModel.LogicalKind.VALUE) {
                source.append("        private final ").append(field.typeName()).append(".View ")
                        .append(field.name()).append("View;\n");
            }
        }
        source.append("\n        private View(java.lang.Object capability, ")
                .append("io.github.somaruntime.soma.internal.GeneratedRow row) {\n")
                .append("            this.row = row;\n");
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
                .append("        private final io.github.somaruntime.soma.internal.GeneratedRow row;\n\n")
                .append("        private Editor(java.lang.Object capability, ")
                .append("io.github.somaruntime.soma.internal.GeneratedRow row) {\n")
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

    private static void appendSelectionTypes(StringBuilder source) {
        source.append("    public static final class Selection {\n")
                .append("        private final io.github.somaruntime.soma.internal.GeneratedPipeline pipeline;\n\n")
                .append("        private Selection(")
                .append("io.github.somaruntime.soma.internal.GeneratedPipeline pipeline) {\n")
                .append("            this.pipeline = pipeline;\n        }\n\n")
                .append("        public Selection filter(")
                .append("io.github.somaruntime.soma.SomaExpression<View> expression) {\n")
                .append("            return new Selection(pipeline.filter(expression));\n        }\n\n")
                .append("        public long count() { return pipeline.count(); }\n")
                .append("    }\n\n")
                .append("    public static final class IndexSelection {\n")
                .append("        private final io.github.somaruntime.soma.internal")
                .append(".GeneratedIndexSelection selection;\n\n")
                .append("        private IndexSelection(io.github.somaruntime.soma.internal")
                .append(".GeneratedIndexSelection selection) {\n")
                .append("            this.selection = selection;\n        }\n\n")
                .append("        public long count() { return selection.count(); }\n")
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
        source.append(indent).append("    }\n\n");
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
        }
        if (type.nullable()) {
            source.append(indent).append("public io.github.somaruntime.soma.SomaExpression<View> ")
                    .append("isNull() { return runtime.isNull(")
                    .append(endpoint.planIndex).append("); }\n\n")
                    .append(indent).append("public io.github.somaruntime.soma.SomaExpression<View> ")
                    .append("isNotNull() { return runtime.isNotNull(")
                    .append(endpoint.planIndex).append("); }\n\n");
        }
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
                .append(indent).append("    io.github.somaruntime.soma.internal.GeneratedProbe[] probes = ")
                .append("new io.github.somaruntime.soma.internal.GeneratedProbe[values.length];\n")
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
                .append("        private final io.github.somaruntime.soma.internal.GeneratedRow row;\n")
                .append("        private final int base;\n");
        for (CompositionModel.FieldModel field : value.fields()) {
            if (field.type().kind() == CompositionModel.LogicalKind.VALUE) {
                source.append("        private final ").append(field.typeName()).append(".View ")
                        .append(field.name()).append("View;\n");
            }
        }
        source.append("\n        private View(java.lang.Object capability, ")
                .append("io.github.somaruntime.soma.internal.GeneratedRow row, int base) {\n")
                .append("            if (!Soma.accepts(capability) || row == null || base < 0) ")
                .append("throw new java.lang.AssertionError(\"invalid Value View capability\");\n")
                .append("            this.row = row;\n            this.base = base;\n");
        for (CompositionModel.FieldModel field : value.fields()) {
            if (field.type().kind() == CompositionModel.LogicalKind.VALUE) {
                source.append("            this.").append(field.name()).append("View = ")
                        .append(field.typeName()).append(".View.create(capability, row, base + ")
                        .append(field.firstLeaf()).append(");\n");
            }
        }
        source.append("        }\n\n")
                .append("        static View create(java.lang.Object capability, ")
                .append("io.github.somaruntime.soma.internal.GeneratedRow row, int base) {\n")
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
                EndpointPlan root = add(field, field.firstLeaf(), all);
                roots.add(root);
                if (field.role() == CompositionModel.FieldRole.KEY) key = root;
                if (field.role() == CompositionModel.FieldRole.INDEX) indexes.add(root);
            }
        }

        private static EndpointPlan add(
                CompositionModel.FieldModel field,
                int leafStart,
                List<EndpointPlan> all) {
            EndpointPlan result = new EndpointPlan(field, leafStart, all.size());
            all.add(result);
            if (field.type().kind() == CompositionModel.LogicalKind.VALUE) {
                for (CompositionModel.FieldModel child : field.type().value().fields()) {
                    result.children.add(add(
                            child, leafStart + child.firstLeaf(), all));
                }
            }
            return result;
        }
    }

    private static final class EndpointPlan {
        private final CompositionModel.FieldModel field;
        private final int leafStart;
        private final int planIndex;
        private final List<EndpointPlan> children = new ArrayList<EndpointPlan>();

        private EndpointPlan(
                CompositionModel.FieldModel field,
                int leafStart,
                int planIndex) {
            this.field = field;
            this.leafStart = leafStart;
            this.planIndex = planIndex;
        }

        private String typeName() {
            return GeneratedNames.fieldEndpointType(field.name());
        }
    }
}
