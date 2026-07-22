package com.hgtech.soma.processor;

import javax.lang.model.element.Element;
import java.util.ArrayList;
import java.util.List;

/** Deterministic emitter for the V1 schema-specific columnar facade. */
final class DenseTableSourceGenerator {
    private final String generatedPackage;
    private final String schemaHash;
    private final List<TableSpec> schemaTables;

    DenseTableSourceGenerator(
            String generatedPackage,
            String schemaHash,
            List<TableSpec> schemaTables) {
        this.generatedPackage = generatedPackage;
        this.schemaHash = schemaHash;
        this.schemaTables = new ArrayList<TableSpec>(schemaTables);
    }

    List<GeneratedSourceOutput> render(TableSpec table) {
        List<GeneratedSourceOutput> result = new ArrayList<GeneratedSourceOutput>();
        add(result, table.name("Cursor"), cursorSource(table), table.origin);
        add(result, table.name("UpdateCursor"), updateCursorSource(table), table.origin);
        add(result, table.name("Batch"), batchSource(table), table.origin);
        add(result, table.name("Mutator"), mutatorSource(table), table.origin);
        add(result, table.name("Scan"), scanSource(table), table.origin);
        if (table.keyed()) {
            add(result, table.name("KeyTraversal"), keyTraversalSource(table), table.origin);
        }
        add(result, table.name("Table"), tableSource(table), table.origin);
        return result;
    }

    private void add(
            List<GeneratedSourceOutput> result,
            String simpleName,
            String source,
            Element origin) {
        result.add(new GeneratedSourceOutput(
                generatedPackage + "." + simpleName, source, origin));
    }

    private String header() {
        return "// SOMA-GENERATED: soma-processor-v1\npackage "
                + generatedPackage + ";\n\n";
    }

    private String cursorSource(TableSpec table) {
        SourceBuilder out = new SourceBuilder(header());
        out.append("public interface ").append(table.name("Cursor")).append(" {\n");
        for (FieldSpec field : table.fields) {
            if (field.optional) {
                out.append("  boolean ").append(field.javaName).append("Present();\n")
                        .append("  boolean ").append(field.javaName).append("Absent();\n");
            }
            out.append("  ").append(field.primitive).append(' ')
                    .append(field.javaName).append("();\n");
            if (field.valueBacked()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    out.append("  ").append(leaf.primitive).append(' ')
                            .append(leaf.stem(field)).append("();\n");
                }
            }
            if (field.optional) {
                out.append("  ").append(field.primitive).append(' ')
                        .append(field.javaName).append("Or(")
                        .append(field.primitive).append(" defaultValue);\n");
            }
        }
        return out.append("}\n").toString();
    }

    private String updateCursorSource(TableSpec table) {
        SourceBuilder out = new SourceBuilder(header());
        out.append("public interface ").append(table.name("UpdateCursor"))
                .append(" extends ").append(table.name("Cursor")).append(" {\n");
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            out.append("  void set").append(cap(field.javaName)).append('(')
                    .append(field.primitive).append(" value);\n");
            if (field.valueBacked()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    out.append("  void set").append(cap(leaf.stem(field))).append('(')
                            .append(leaf.primitive).append(" value);\n");
                }
            }
            if (field.optional) {
                out.append("  void clear").append(cap(field.javaName)).append("();\n");
            }
        }
        return out.append("}\n").toString();
    }

    private String batchSource(TableSpec table) {
        String batch = table.name("Batch");
        SourceBuilder out = new SourceBuilder(header());
        out.append("import com.hgtech.soma.runtime.generated.RuntimeFailures;\n")
                .append("import com.hgtech.soma.runtime.generated.KeyCanonicalization;\n")
                .append("import java.util.Arrays;\n\n")
                .append("public final class ").append(batch).append(" {\n")
                .append("  private static final String TABLE = ").append(q(table.logicalName)).append(";\n")
                .append("  private int size;\n  private int capacity;\n");
        for (FieldSpec field : table.fields) {
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    out.append("  private ").append(leaf.storagePrimitive).append("[] ")
                            .append(leaf.physicalName(field)).append("Values;\n");
                }
            } else {
                out.append("  private ").append(field.storagePrimitive).append("[] ")
                        .append(field.javaName).append("Values;\n");
            }
            if (field.optional) {
                out.append("  private long[] ").append(field.javaName).append("Presence;\n");
            }
        }
        for (ChildSpec child : table.children) {
            out.append("  private ").append(child.batchType()).append("[] ")
                    .append(child.javaName).append("Values;\n")
                    .append("  private boolean[] ").append(child.javaName)
                    .append("Present;\n");
        }
        for (FieldSpec field : table.fields) {
            if (field.enumType != null) {
                out.append("  private static final ").append(field.enumType).append("[] ")
                        .append(field.enumConstantsName()).append('=')
                        .append(field.enumType).append(".values();\n");
            }
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    if (leaf.enumType != null) {
                        out.append("  private static final ").append(leaf.enumType).append("[] ")
                                .append(leaf.enumConstantsName(field)).append('=')
                                .append(leaf.enumType).append(".values();\n");
                    }
                }
            }
        }
        out.append("\n  public ").append(batch).append("() { this(")
                .append(table.defaultCapacity).append("); }\n")
                .append("  public ").append(batch).append("(int initialCapacity) {\n")
                .append("    if (initialCapacity < 0) throw new IllegalArgumentException(\"initialCapacity must be non-negative\");\n")
                .append("    capacity = initialCapacity;\n");
        for (FieldSpec field : table.fields) {
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    out.append("    ").append(leaf.physicalName(field)).append("Values = new ")
                            .append(leaf.storagePrimitive).append("[initialCapacity];\n");
                }
            } else {
                out.append("    ").append(field.javaName).append("Values = new ")
                        .append(field.storagePrimitive).append("[initialCapacity];\n");
            }
            if (field.optional) {
                out.append("    ").append(field.javaName)
                        .append("Presence = new long[(initialCapacity + 63) >>> 6];\n");
            }
        }
        for (ChildSpec child : table.children) {
            out.append("    ").append(child.javaName).append("Values = new ")
                    .append(child.batchType()).append("[initialCapacity];\n")
                    .append("    ").append(child.javaName)
                    .append("Present = new boolean[initialCapacity];\n");
        }
        out.append("  }\n\n  public int size() { return size; }\n")
                .append("  public int capacity() { return capacity; }\n")
                .append("  public boolean isEmpty() { return size == 0; }\n\n")
                .append("  public ").append(batch).append(" add(").append(table.carrierType)
                .append(" detachedRow) {\n")
                .append("    if (detachedRow == null) throw new NullPointerException(\"detachedRow\");\n");
        for (FieldSpec field : table.fields) {
            String source = "detachedRow." + field.javaName;
            String value = field.optional && field.boxedPrimitive()
                    ? source + "." + field.unboxMethod() + "()" : source;
            appendBatchFieldStaging(out, field,
                    field.optional ? source + " != null" : null,
                    value, "batch.add");
        }
        for (ChildSpec child : table.children) {
            out.append("    ").append(child.batchType()).append(" staged")
                    .append(cap(child.javaName)).append("=snapshot")
                    .append(cap(child.javaName)).append("(detachedRow.")
                    .append(child.javaName).append(");\n");
        }
        out.append("    ensureOne();\n");
        appendBatchFieldPublish(out, table);
        appendBatchChildPublish(out, table);
        out.append("    size++;\n    return this;\n  }\n\n")
                .append("  public ").append(batch).append(" addValues(Writer writer) {\n")
                .append("    if (writer == null) throw new NullPointerException(\"writer\");\n")
                .append("    BuilderImpl row = new BuilderImpl();\n")
                .append("    try { writer.write(row); } catch (Error error) { row.close(); throw error; }")
                .append(" catch (RuntimeException failure) { row.close(); throw RuntimeFailures.callbackFailed(TABLE, \"batch.addValues\", \"writer\", failure); }\n")
                .append("    row.close();\n");
        for (FieldSpec field : table.fields) {
            if (!field.optional) {
                out.append("    if (!row.").append(field.javaName).append("Assigned) {");
                if (field.hasDefault()) {
                    out.append("row.").append(field.javaName).append("Value=")
                            .append(field.defaultExpression).append(";row.")
                            .append(field.javaName).append("Assigned=true;");
                } else {
                    out.append("throw RuntimeFailures.missingRequiredField(TABLE,")
                            .append(q(field.logicalName)).append(",\"batch.addValues\");");
                }
                out.append("}\n");
            }
        }
        for (ChildSpec child : table.children) {
            if (!child.optional) {
                out.append("    if (!row.").append(child.javaName)
                        .append("Assigned) throw RuntimeFailures.missingRequiredField(TABLE,")
                        .append(q(child.logicalName)).append(",\"batch.addValues\");\n");
            }
        }
        for (FieldSpec field : table.fields) {
            appendBatchFieldStaging(out, field,
                    field.optional ? "row." + field.javaName + "Present" : null,
                    "row." + field.javaName + "Value", "batch.addValues");
        }
        for (ChildSpec child : table.children) {
            out.append("    ").append(child.batchType()).append(" staged")
                    .append(cap(child.javaName)).append("=row.")
                    .append(child.javaName).append(child.optional ? "Present" : "Assigned")
                    .append("?row.").append(child.javaName).append("Value:null;\n");
        }
        out.append("    ensureOne();\n");
        appendBatchFieldPublish(out, table);
        appendBatchChildPublish(out, table);
        out.append("    size++;\n    return this;\n  }\n\n");

        if (table.directSlots() + table.children.size() <= 255) {
            out.append("  public ").append(batch).append(" addValues(");
            appendDirectParameters(out, table);
            out.append(") {\n");
            for (FieldSpec field : table.fields) {
                appendBatchFieldStaging(out, field,
                        field.optional ? field.javaName + "Present" : null,
                        field.optional ? field.javaName + "Value" : field.javaName,
                        "batch.addValues");
            }
            for (ChildSpec child : table.children) {
                if (!child.optional) {
                    out.append("    if(").append(child.javaName)
                            .append("==null)throw RuntimeFailures.invalidNullValue(TABLE,")
                            .append(q(child.logicalName)).append(",\"batch.addValues\");\n");
                }
                out.append("    ").append(child.batchType()).append(" staged")
                        .append(cap(child.javaName)).append('=').append(child.javaName)
                        .append("==null?null:").append(child.javaName).append(".copy();\n");
            }
            out.append("    ensureOne();\n");
            appendBatchFieldPublish(out, table);
            appendBatchChildPublish(out, table);
            out.append("    size++;\n    return this;\n  }\n\n");
        }

        out.append("  public void clear() { int previous = size; size = 0;\n");
        for (FieldSpec field : table.fields) {
            if (field.optional) {
                out.append("    Arrays.fill(").append(field.javaName).append("Presence, 0L);\n");
            }
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    if ("java.lang.String".equals(leaf.storagePrimitive)) {
                        out.append("    Arrays.fill(").append(leaf.physicalName(field))
                                .append("Values, 0, previous, null);\n");
                    }
                }
            } else if ("java.lang.String".equals(field.storagePrimitive)) {
                out.append("    Arrays.fill(").append(field.javaName)
                        .append("Values, 0, previous, null);\n");
            }
        }
        for (ChildSpec child : table.children) {
            out.append("    Arrays.fill(").append(child.javaName)
                    .append("Values,0,previous,null);Arrays.fill(")
                    .append(child.javaName).append("Present,false);\n");
        }
        out.append("  }\n\n  private void ensureOne() {\n")
                .append("    if (size == Integer.MAX_VALUE) throw new IllegalStateException(\"batch size overflow\");\n")
                .append("    ensureCapacity(size + 1);\n  }\n\n")
                .append("  private void ensureCapacity(int required) {\n")
                .append("    if (required <= capacity) return;\n")
                .append("    long grown = Math.max((long) required, Math.max(1L, ((long) capacity * 3L + 1L) / 2L));\n")
                .append("    if (grown > Integer.MAX_VALUE) grown = Integer.MAX_VALUE;\n")
                .append("    int next = (int) grown;\n");
        for (FieldSpec field : table.fields) {
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    String physical = leaf.physicalName(field);
                    out.append("    ").append(leaf.storagePrimitive).append("[] new")
                            .append(cap(physical)).append("Values = Arrays.copyOf(")
                            .append(physical).append("Values, next);\n");
                }
            } else {
                out.append("    ").append(field.storagePrimitive).append("[] new")
                        .append(cap(field.javaName)).append("Values = Arrays.copyOf(")
                        .append(field.javaName).append("Values, next);\n");
            }
            if (field.optional) {
                out.append("    long[] new").append(cap(field.javaName))
                        .append("Presence = Arrays.copyOf(").append(field.javaName)
                        .append("Presence, (next + 63) >>> 6);\n");
            }
        }
        for (ChildSpec child : table.children) {
            String c = cap(child.javaName);
            out.append("    ").append(child.batchType()).append("[] new")
                    .append(c).append("Values=Arrays.copyOf(")
                    .append(child.javaName).append("Values,next);\n")
                    .append("    boolean[] new").append(c)
                    .append("Present=Arrays.copyOf(").append(child.javaName)
                    .append("Present,next);\n");
        }
        for (FieldSpec field : table.fields) {
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    String physical = leaf.physicalName(field);
                    out.append("    ").append(physical).append("Values = new")
                            .append(cap(physical)).append("Values;\n");
                }
            } else {
                out.append("    ").append(field.javaName).append("Values = new")
                        .append(cap(field.javaName)).append("Values;\n");
            }
            if (field.optional) {
                out.append("    ").append(field.javaName).append("Presence = new")
                        .append(cap(field.javaName)).append("Presence;\n");
            }
        }
        for (ChildSpec child : table.children) {
            String c = cap(child.javaName);
            out.append("    ").append(child.javaName).append("Values=new")
                    .append(c).append("Values;")
                    .append(child.javaName).append("Present=new")
                    .append(c).append("Present;\n");
        }
        out.append("    capacity = next;\n  }\n\n");
        for (FieldSpec field : table.fields) {
            String c = cap(field.javaName);
            if (field.flattenedValueStorage()) {
                out.append("  private void set").append(c).append("(int row, ")
                        .append(field.primitive).append(" value) { ")
                        .append(field.primitive).append(" required=RuntimeFailures.requiredValue(TABLE,")
                        .append(q(field.logicalName)).append(",value,\"batch.write\");");
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    out.append(leaf.physicalName(field)).append("Values[row]=")
                            .append(leaf.storageValue(field, "required." + leaf.javaName, "batch.write"))
                            .append(';');
                }
            } else {
                out.append("  private void set").append(c).append("(int row, ")
                        .append(field.primitive).append(" value) { ").append(field.javaName)
                        .append("Values[row] = ").append(field.storageValue("value", "batch.write")).append(";");
            }
            if (field.optional) {
                out.append(" setPresent(").append(field.javaName).append("Presence, row, true);");
            }
            out.append(" }\n");
            if (field.flattenedValueStorage()) {
                out.append("  ").append(field.primitive).append(" ").append(field.javaName)
                        .append("Value(int row) { return ")
                        .append(compositeValueExpression(field, "row", true))
                        .append("; }\n");
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    out.append("  ").append(leaf.storagePrimitive).append(' ')
                            .append(leaf.physicalName(field)).append("StorageValue(int row) { return ")
                            .append(leaf.physicalName(field)).append("Values[row]; }\n");
                }
            } else {
                out.append("  ").append(field.primitive).append(" ").append(field.javaName)
                        .append("Value(int row) { return ")
                        .append(field.publicValue(field.javaName + "Values[row]")).append("; }\n")
                        .append("  ").append(field.storagePrimitive).append(" ").append(field.javaName)
                        .append("StorageValue(int row) { return ").append(field.javaName)
                        .append("Values[row]; }\n");
            }
            if (field.optional) {
                out.append("  private void set").append(c).append("Absent(int row) { ");
                if (field.flattenedValueStorage()) {
                    for (ValueLeafSpec leaf : field.valueLeaves) {
                        out.append(leaf.physicalName(field)).append("Values[row]=")
                                .append(leaf.storageZero()).append(';');
                    }
                } else {
                    out.append(field.javaName).append("Values[row] = ")
                            .append(field.storageZero()).append(';');
                }
                out.append(" setPresent(").append(field.javaName)
                        .append("Presence, row, false); }\n")
                        .append("  boolean ").append(field.javaName).append("Present(int row) { return present(")
                        .append(field.javaName).append("Presence, row); }\n");
            }
        }
        for (ChildSpec child : table.children) {
            String c = cap(child.javaName);
            out.append("  private void set").append(c).append("(int row,")
                    .append(child.batchType()).append(" value){");
            if (!child.optional) {
                out.append("if(value==null)throw RuntimeFailures.invalidNullValue(TABLE,")
                        .append(q(child.logicalName)).append(",\"batch.write\");");
            }
            out.append(child.javaName).append("Values[row]=value==null?null:value.copy();")
                    .append(child.javaName).append("Present[row]=value!=null;}\n")
                    .append("  ").append(child.batchType()).append(' ')
                    .append(child.javaName).append("Batch(int row){return ")
                    .append(child.javaName).append("Values[row];}\n")
                    .append("  boolean ").append(child.javaName)
                    .append("Present(int row){return ").append(child.javaName)
                    .append("Present[row];}\n")
                    .append("  private ").append(child.batchType()).append(" snapshot")
                    .append(c).append('(').append(child.materializedType).append(" value){");
            if (!child.optional) {
                out.append("if(value==null)throw RuntimeFailures.invalidNullValue(TABLE,")
                        .append(q(child.logicalName)).append(",\"batch.add\");");
            } else {
                out.append("if(value==null)return null;");
            }
            out.append(child.batchType()).append(" result=new ")
                    .append(child.batchType()).append("();");
            if (child.keyed()) {
                out.append("for(java.util.Map.Entry<").append(child.keyMaterializedType)
                        .append(',').append(child.rowJavaType)
                        .append("> entry:value.entrySet()){if(entry.getKey()==null||entry.getValue()==null)throw RuntimeFailures.invalidNullValue(TABLE,")
                        .append(q(child.logicalName)).append(",\"batch.add\");if(!(")
                        .append(childKeyEquality(child, "entry.getKey()", "entry.getValue()." + child.keyJavaName))
                        .append("))throw RuntimeFailures.childKeyMismatch(TABLE+\".")
                        .append(child.logicalName).append("\",\"batch.add\");result.add(entry.getValue());}");
            } else {
                out.append("for(").append(child.rowJavaType)
                        .append(" element:value){if(element==null)throw RuntimeFailures.invalidNullValue(TABLE,")
                        .append(q(child.logicalName)).append(",\"batch.add\");result.add(element);}");
            }
            out.append("return result;}\n");
        }
        out.append("  ").append(batch).append(" copy(){")
                .append(batch).append(" copy=new ").append(batch).append("(size);copy.size=size;");
        for (FieldSpec field : table.fields) {
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    String physical = leaf.physicalName(field);
                    out.append("System.arraycopy(").append(physical)
                            .append("Values,0,copy.").append(physical)
                            .append("Values,0,size);");
                }
            } else {
                out.append("System.arraycopy(").append(field.javaName)
                        .append("Values,0,copy.").append(field.javaName)
                        .append("Values,0,size);");
            }
            if (field.optional) {
                out.append("System.arraycopy(").append(field.javaName)
                        .append("Presence,0,copy.").append(field.javaName)
                        .append("Presence,0,").append(field.javaName).append("Presence.length);");
            }
        }
        for (ChildSpec child : table.children) {
            out.append("for(int i=0;i<size;i++){copy.").append(child.javaName)
                    .append("Present[i]=").append(child.javaName)
                    .append("Present[i];if(").append(child.javaName)
                    .append("Values[i]!=null)copy.").append(child.javaName)
                    .append("Values[i]=").append(child.javaName).append("Values[i].copy();}");
        }
        out.append("return copy;}\n");
        out.append("\n  private static boolean present(long[] words, int row) { return (words[row >>> 6] & (1L << (row & 63))) != 0L; }\n")
                .append("  private static void setPresent(long[] words, int row, boolean present) { int word = row >>> 6; long mask = 1L << (row & 63); if (present) words[word] |= mask; else words[word] &= ~mask; }\n\n")
                .append("  public interface Writer { void write(RowBuilder row); }\n")
                .append("  public interface RowBuilder {\n");
        for (FieldSpec field : table.fields) {
            out.append("    void set").append(cap(field.javaName)).append('(')
                    .append(field.primitive).append(" value);\n");
            if (field.optional) {
                out.append("    void clear").append(cap(field.javaName)).append("();\n");
            }
        }
        for (ChildSpec child : table.children) {
            out.append("    void set").append(cap(child.javaName)).append('(')
                    .append(child.batchType()).append(" value);\n");
            if (child.optional) {
                out.append("    void clear").append(cap(child.javaName)).append("();\n");
            }
        }
        out.append("  }\n\n  private static final class BuilderImpl implements RowBuilder {\n")
                .append("    private boolean active = true;\n");
        for (FieldSpec field : table.fields) {
            out.append("    private ").append(field.primitive).append(' ')
                    .append(field.javaName).append("Value;\n")
                    .append("    private boolean ").append(field.javaName)
                    .append(field.optional ? "Present;\n" : "Assigned;\n");
        }
        for (ChildSpec child : table.children) {
            out.append("    private ").append(child.batchType()).append(' ')
                    .append(child.javaName).append("Value;\n")
                    .append("    private boolean ").append(child.javaName)
                    .append(child.optional ? "Present;\n" : "Assigned;\n");
        }
        for (FieldSpec field : table.fields) {
            out.append("    public void set").append(cap(field.javaName)).append('(')
                    .append(field.primitive).append(" value) { check(); ")
                    .append(field.javaName).append("Value = value; ")
                    .append(field.javaName).append(field.optional ? "Present = true;" : "Assigned = true;")
                    .append(" }\n");
            if (field.optional) {
                out.append("    public void clear").append(cap(field.javaName))
                        .append("() { check(); ").append(field.javaName).append("Value = ")
                        .append(field.zero()).append("; ").append(field.javaName)
                        .append("Present = false; }\n");
            }
        }
        for (ChildSpec child : table.children) {
            String c = cap(child.javaName);
            out.append("    public void set").append(c).append('(')
                    .append(child.batchType()).append(" value){check();if(value==null)throw RuntimeFailures.invalidNullValue(TABLE,")
                    .append(q(child.logicalName)).append(",\"batch.addValues\");")
                    .append(child.javaName).append("Value=value.copy();")
                    .append(child.javaName)
                    .append(child.optional ? "Present=true;" : "Assigned=true;")
                    .append("}\n");
            if (child.optional) {
                out.append("    public void clear").append(c).append("(){check();")
                        .append(child.javaName).append("Value=null;")
                        .append(child.javaName).append("Present=false;}\n");
            }
        }
        return out.append("    private void close() { active = false; }\n")
                .append("    private void check() { if (!active) throw RuntimeFailures.internalInvariant(\"escaped_batch_row_builder\", TABLE, \"batch.addValues\"); }\n")
                .append("  }\n}\n").toString();
    }

    private String mutatorSource(TableSpec table) {
        String name = table.name("Mutator");
        SourceBuilder out = new SourceBuilder(header());
        out.append("import com.hgtech.soma.runtime.generated.RuntimeFailures;\n\n")
                .append("public final class ").append(name).append(" {\n")
                .append("  private final ").append(table.name("Table")).append(" table;\n")
                .append("  private final int index;\n  private final long epoch;\n  private boolean consumed;\n");
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            out.append("  private ").append(field.primitive).append(' ')
                    .append(field.javaName).append("Value;\n");
            out.append("  private boolean ").append(field.javaName).append("Touched;\n");
            if (field.optional) out.append("  private boolean ").append(field.javaName).append("Present;\n");
        }
        out.append("\n  ").append(name).append('(').append(table.name("Table"))
                .append(" table, int index, long epoch) {\n    this.table = table; this.index = index; this.epoch = epoch;\n");
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            out.append("    this.").append(field.javaName).append("Value = ");
            if (field.optional) {
                out.append("table.").append(field.javaName).append("Present(index)?table.")
                        .append(field.javaName).append("Value(index):")
                        .append(field.zero());
            } else {
                out.append("table.").append(field.javaName).append("Value(index)");
            }
            out.append(";\n");
            if (field.optional) out.append("    this.").append(field.javaName).append("Present = table.").append(field.javaName).append("Present(index);\n");
        }
        out.append("  }\n\n");
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            String c = cap(field.javaName);
            out.append("  public ").append(name).append(" set").append(c).append('(')
                    .append(field.primitive).append(" value) { check(); ")
                    .append(field.javaName).append("Value = value; ")
                    .append(field.javaName).append("Touched = true;");
            if (field.optional) out.append(' ').append(field.javaName).append("Present = true;");
            out.append(" return this; }\n");
            if (field.optional) {
                out.append("  public ").append(name).append(" clear").append(c)
                        .append("() { check(); ").append(field.javaName).append("Value = ")
                        .append(field.zero()).append("; ").append(field.javaName)
                        .append("Present = false; ").append(field.javaName)
                        .append("Touched = true; return this; }\n");
            }
            out.append("  ").append(field.primitive).append(' ').append(field.javaName)
                    .append("Value() { return ").append(field.javaName).append("Value; }\n");
            out.append("  boolean ").append(field.javaName).append("Touched() { return ")
                    .append(field.javaName).append("Touched; }\n");
            if (field.optional) out.append("  boolean ").append(field.javaName).append("Present() { return ").append(field.javaName).append("Present; }\n");
        }
        return out.append("\n  public void commit() { check(); consumed = true; table.commitMutator(index, epoch, this); }\n")
                .append("  private void check() { if (consumed) throw RuntimeFailures.mutationConsumed(")
                .append(q(table.logicalName)).append(", \"mutator\"); }\n}\n").toString();
    }

    private String keyTraversalSource(TableSpec table) {
        FieldSpec key = table.keyField();
        String name = table.name("KeyTraversal");
        String tableName = table.name("Table");
        SourceBuilder out = new SourceBuilder(header());
        out.append("import com.hgtech.soma.runtime.MaterializationBudget;\n")
                .append("import com.hgtech.soma.runtime.SomaRuntimeException;\n")
                .append("import com.hgtech.soma.runtime.generated.RuntimeFailures;\n")
                .append("import java.util.ArrayList;\nimport java.util.List;\nimport java.util.Optional;\n")
                .append("import java.util.function.Consumer;\n\n")
                .append("public final class ").append(name).append(" {\n")
                .append("  private final ").append(tableName).append(" table;private boolean consumed;\n")
                .append("  ").append(name).append('(').append(tableName).append(" table){this.table=table;}\n")
                .append("  public void forEach(Consumer<").append(key.boxed).append("> consumer){if(consumer==null)throw new NullPointerException(\"consumer\");start(\"keys.forEach\");long scanned=0L;try{int size=table.size();for(int row=0;row<size;row++){scanned++;table.beginCallback(\"keys.forEach.consumer\");try{consumer.accept(").append(key.boxValue("table." + key.javaName + "Value(row)")).append(");}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"keys.forEach\",\"consumer\",callback);}finally{table.endCallback(\"keys.forEach.consumer\");}}table.endSuccess(\"keys.forEach\",scanned,scanned,0L);}catch(SomaRuntimeException failure){table.endFailure(\"keys.forEach\",scanned,scanned,failure.code());throw failure;}catch(Error failure){table.endFailure(\"keys.forEach\",scanned,scanned,\"callback_failed\");throw failure;}}\n")
                .append("  public List<").append(key.boxed).append("> fetchAll(){start(\"keys.fetchAll\");return fetchAllStarted(null);}\n")
                .append("  public List<").append(key.boxed).append("> fetchAll(MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");start(\"keys.fetchAll\");return fetchAllStarted(budget);}\n")
                .append("  private List<").append(key.boxed).append("> fetchAllStarted(MaterializationBudget budget){try{budget=materializationBudget(budget);List<").append(key.boxed).append("> result=table.materializeKeys(budget,\"keys.fetchAll\",true);long count=result.size();table.endSuccess(\"keys.fetchAll\",count,count,0L);return result;}catch(SomaRuntimeException failure){table.endFailure(\"keys.fetchAll\",0L,0L,failure.code());throw failure;}catch(RuntimeException failure){table.abort(\"keys.fetchAll\");throw failure;}catch(Error failure){table.abort(\"keys.fetchAll\");throw failure;}}\n")
                .append("  public Optional<").append(key.boxed).append("> findFirst(){start(\"keys.findFirst\");return findFirstStarted(null);}\n")
                .append("  public Optional<").append(key.boxed).append("> findFirst(MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");start(\"keys.findFirst\");return findFirstStarted(budget);}\n")
                .append("  private Optional<").append(key.boxed).append("> findFirstStarted(MaterializationBudget budget){try{budget=materializationBudget(budget);Optional<").append(key.boxed).append("> result=table.materializeOptionalKey(budget,\"keys.findFirst\",true);long count=result.isPresent()?1L:0L;table.endSuccess(\"keys.findFirst\",count,count,0L);return result;}catch(SomaRuntimeException failure){table.endFailure(\"keys.findFirst\",0L,0L,failure.code());throw failure;}catch(RuntimeException failure){table.abort(\"keys.findFirst\");throw failure;}catch(Error failure){table.abort(\"keys.findFirst\");throw failure;}}\n")
                .append("  public ").append(key.boxed).append(" firstOrThrow(){start(\"keys.firstOrThrow\");return firstOrThrowStarted(null);}\n")
                .append("  public ").append(key.boxed).append(" firstOrThrow(MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");start(\"keys.firstOrThrow\");return firstOrThrowStarted(budget);}\n")
                .append("  private ").append(key.boxed).append(" firstOrThrowStarted(MaterializationBudget budget){try{budget=materializationBudget(budget);").append(key.boxed).append(" result=table.materializeRequiredKey(budget,\"keys.firstOrThrow\",true);table.endSuccess(\"keys.firstOrThrow\",1L,1L,0L);return result;}catch(SomaRuntimeException failure){table.endFailure(\"keys.firstOrThrow\",0L,0L,failure.code());throw failure;}catch(RuntimeException failure){table.abort(\"keys.firstOrThrow\");throw failure;}catch(Error failure){table.abort(\"keys.firstOrThrow\");throw failure;}}\n")
                .append("  private MaterializationBudget materializationBudget(MaterializationBudget budget){return budget==null?table.runtimePlan().defaultMaterializationBudget():budget;}\n")
                .append("  private void start(String operation){check(operation);consumed=true;table.begin(operation);}\n")
                .append("  private void check(String operation){if(consumed)throw RuntimeFailures.traversalConsumed(").append(q(table.logicalName)).append(",operation);}\n")
                .append("}\n");
        return out.toString();
    }

    private String scanSource(TableSpec table) {
        String rows = table.name("Scan");
        String row = table.name("Cursor");
        String mutable = table.name("UpdateCursor");
        SourceBuilder out = new SourceBuilder(header());
        out.append("import com.hgtech.soma.runtime.RemoveResult;\n")
                .append("import com.hgtech.soma.runtime.IndexSnapshot;\n")
                .append("import com.hgtech.soma.runtime.MaterializationBudget;\n")
                .append("import com.hgtech.soma.runtime.SomaRuntimeException;\n")
                .append("import com.hgtech.soma.runtime.UpdateResult;\n")
                .append("import com.hgtech.soma.runtime.generated.GeneratedScanEvaluation;\n")
                .append("import com.hgtech.soma.runtime.generated.GeneratedScanPlan;\n")
                .append("import com.hgtech.soma.runtime.generated.RuntimeFailures;\n")
                .append("import java.util.List;\nimport java.util.Optional;\n\n")
                .append("public final class ").append(rows).append(" {\n")
                .append("  private static final byte FILTER=1,SKIP=2,LIMIT=3,SORT=4;\n")
                .append("  private final Source plan;private final int generation;\n")
                .append("  ").append(rows).append("(Source plan){this(plan,0);}\n")
                .append("  private ").append(rows).append("(Source plan,int generation){this.plan=plan;this.generation=generation;}\n")
                .append("  private static ").append(rows).append(" packed(").append(table.name("Table")).append(" table,byte kind,Object callback,long argument){Source plan=new Source(table,").append(q(table.logicalName)).append(");plan.append(kind,callback,argument,1);return new ").append(rows).append("(plan,1);}\n")
                .append("  static ").append(rows).append(" packedFilter(").append(table.name("Table")).append(" table,Predicate value){if(value==null)throw new NullPointerException(\"predicate\");return packed(table,FILTER,value,0L);}\n")
                .append("  static ").append(rows).append(" packedSkip(").append(table.name("Table")).append(" table,long value){if(value<0L)throw new IllegalArgumentException(\"count must be non-negative\");return packed(table,SKIP,null,value);}\n")
                .append("  static ").append(rows).append(" packedLimit(").append(table.name("Table")).append(" table,long value){if(value<0L)throw new IllegalArgumentException(\"count must be non-negative\");return packed(table,LIMIT,null,value);}\n")
                .append("  static ").append(rows).append(" packedSorted(").append(table.name("Table")).append(" table,Comparator value){if(value==null)throw new NullPointerException(\"comparator\");return packed(table,SORT,value,0L);}\n");
        out
                .append("  public ").append(rows).append(" filter(Predicate value){if(value==null)throw new NullPointerException(\"predicate\");return append(FILTER,value,null,0L);}\n")
                .append("  public ").append(rows).append(" skip(long value){if(value<0L)throw new IllegalArgumentException(\"count must be non-negative\");return append(SKIP,null,null,value);}\n")
                .append("  public ").append(rows).append(" limit(long value){if(value<0L)throw new IllegalArgumentException(\"count must be non-negative\");return append(LIMIT,null,null,value);}\n")
                .append("  public ").append(rows).append(" sorted(Comparator value){if(value==null)throw new NullPointerException(\"comparator\");return append(SORT,null,value,0L);}\n")
                .append("  private ").append(rows).append(" append(byte kind,Predicate predicate,Comparator comparator,long count){check(\"intermediate\");int nextGeneration=generation+1;if(nextGeneration<0)throw new OutOfMemoryError(\"pipeline generation\");").append(rows).append(" next=new ").append(rows).append("(plan,nextGeneration);plan.append(kind,predicate!=null?predicate:comparator,count,nextGeneration);return next;}\n\n")
                .append("  public long count(){start(\"scan.count\");long scanned=0L,reached=0L;GeneratedScanEvaluation e=null;try{if(plan.stageCount()==0){int cardinality=plan.size();table().endSuccess(\"scan.count\",cardinality,cardinality,0L);return cardinality;}if(!hasSort()){Cursor c=hasFilter()?new Cursor(table()):null;int initial=plan.size();for(int position=0;position<initial&&!limitReached(0,plan.stageCount());position++){int index=plan.rowAt(position);scanned++;if(matches(index,c,0,plan.stageCount()))reached++;}table().endSuccess(\"scan.count\",scanned,reached,0L);return reached;}e=new GeneratedScanEvaluation();select(e,terminalMaximum());table().endSuccess(\"scan.count\",e.selectionScanned,e.length,0L);return e.length;}catch(SomaRuntimeException f){table().endFailure(\"scan.count\",e==null?scanned:e.attemptedScanned,e==null?reached:e.attemptedMatched,f.code());throw f;}catch(RuntimeException f){table().abort(\"scan.count\");throw f;}catch(Error f){table().abort(\"scan.count\");throw f;}finally{finish();}}\n")
                .append("  public boolean anyMatch(Predicate value){return matchTerminal(value,true);}\n")
                .append("  public boolean noneMatch(Predicate value){return !matchTerminal(value,false);}\n")
                .append("  private boolean matchTerminal(Predicate value,boolean any){if(value==null)throw new NullPointerException(\"predicate\");String op=any?\"scan.anyMatch\":\"scan.noneMatch\",callback=any?\"scan.anyMatch.predicate\":\"scan.noneMatch.predicate\";start(op);long reached=0L,scanned=0L;GeneratedScanEvaluation e=null;try{Cursor c=new Cursor(table());if(!hasSort()){int initial=plan.size();for(int position=0;position<initial&&!limitReached(0,plan.stageCount());position++){int index=plan.rowAt(position);scanned++;if(matches(index,c,0,plan.stageCount())){reached++;if(test(value,c,index,op,callback)){table().endSuccess(op,scanned,reached,0L);return true;}}}table().endSuccess(op,scanned,reached,0L);return false;}e=new GeneratedScanEvaluation();select(e,terminalMaximum());scanned=e.selectionScanned;for(int i=0;i<e.length;i++){reached++;if(test(value,c,e.indexes[i],op,callback)){table().endSuccess(op,scanned,reached,0L);return true;}}table().endSuccess(op,scanned,reached,0L);return false;}catch(SomaRuntimeException f){table().endFailure(op,e==null?scanned:e.attemptedScanned,e==null?reached:e.attemptedMatched,f.code());throw f;}catch(RuntimeException f){table().abort(op);throw f;}catch(Error f){table().abort(op);throw f;}finally{finish();}}\n")
                .append("  public void forEach(Consumer value){if(value==null)throw new NullPointerException(\"consumer\");start(\"scan.forEach\");long reached=0L,scanned=0L;GeneratedScanEvaluation e=null;try{Cursor c=new Cursor(table());if(!hasSort()){int initial=plan.size();for(int position=0;position<initial&&!limitReached(0,plan.stageCount());position++){int index=plan.rowAt(position);scanned++;if(!matches(index,c,0,plan.stageCount()))continue;reached++;c.open(index);table().beginCallback(\"scan.forEach.consumer\");try{value.accept(c);}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"scan.forEach\",\"consumer\",callback);}finally{table().endCallback(\"scan.forEach.consumer\");c.close();}}table().endSuccess(\"scan.forEach\",scanned,reached,0L);return;}e=new GeneratedScanEvaluation();select(e,terminalMaximum());scanned=e.selectionScanned;for(int i=0;i<e.length;i++){reached++;c.open(e.indexes[i]);table().beginCallback(\"scan.forEach.consumer\");try{value.accept(c);}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"scan.forEach\",\"consumer\",callback);}finally{table().endCallback(\"scan.forEach.consumer\");c.close();}}table().endSuccess(\"scan.forEach\",scanned,reached,0L);}catch(SomaRuntimeException f){table().endFailure(\"scan.forEach\",e==null?scanned:e.attemptedScanned,e==null?reached:e.attemptedMatched,f.code());throw f;}catch(RuntimeException f){table().abort(\"scan.forEach\");throw f;}catch(Error f){table().abort(\"scan.forEach\");throw f;}finally{finish();}}\n")
                .append("  public int findIndex(){start(\"scan.findIndex\");GeneratedScanEvaluation e=new GeneratedScanEvaluation();try{select(e,1);int result=e.length==0?-1:e.indexes[0];table().endSuccess(\"scan.findIndex\",e.selectionScanned,e.length,0L);return result;}catch(SomaRuntimeException f){table().endFailure(\"scan.findIndex\",e.attemptedScanned,e.attemptedMatched,f.code());throw f;}catch(RuntimeException f){table().abort(\"scan.findIndex\");throw f;}catch(Error f){table().abort(\"scan.findIndex\");throw f;}finally{finish();}}\n")
                .append("  public int requireIndex(){start(\"scan.requireIndex\");GeneratedScanEvaluation e=new GeneratedScanEvaluation();try{select(e,1);if(e.length==0)throw RuntimeFailures.emptyResult(sourcePath(),\"scan.requireIndex\");int result=e.indexes[0];table().endSuccess(\"scan.requireIndex\",e.selectionScanned,1L,0L);return result;}catch(SomaRuntimeException f){table().endFailure(\"scan.requireIndex\",e.attemptedScanned,e.attemptedMatched,f.code());throw f;}catch(RuntimeException f){table().abort(\"scan.requireIndex\");throw f;}catch(Error f){table().abort(\"scan.requireIndex\");throw f;}finally{finish();}}\n")
                .append("  public Optional<").append(table.carrierType).append("> findFirst(){start(\"scan.findFirst\");return findFirstStarted(null);}\n")
                .append("  public Optional<").append(table.carrierType).append("> findFirst(MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");start(\"scan.findFirst\");return findFirstStarted(budget);}\n")
                .append("  private Optional<").append(table.carrierType).append("> findFirstStarted(MaterializationBudget budget){GeneratedScanEvaluation e=new GeneratedScanEvaluation();try{budget=materializationBudget(budget);select(e,1);Optional<").append(table.carrierType).append("> result=table().materializeOptionalRow(e.length==0?-1:e.indexes[0],budget,\"scan.findFirst\",true);table().endSuccess(\"scan.findFirst\",e.selectionScanned,e.length,0L);return result;}catch(SomaRuntimeException f){table().endFailure(\"scan.findFirst\",e.attemptedScanned,e.attemptedMatched,f.code());throw f;}catch(RuntimeException f){table().abort(\"scan.findFirst\");throw f;}catch(Error f){table().abort(\"scan.findFirst\");throw f;}finally{finish();}}\n")
                .append("  public ").append(table.carrierType).append(" firstOrThrow(){start(\"scan.firstOrThrow\");return firstOrThrowStarted(null);}\n")
                .append("  public ").append(table.carrierType).append(" firstOrThrow(MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");start(\"scan.firstOrThrow\");return firstOrThrowStarted(budget);}\n")
                .append("  private ").append(table.carrierType).append(" firstOrThrowStarted(MaterializationBudget budget){GeneratedScanEvaluation e=new GeneratedScanEvaluation();try{budget=materializationBudget(budget);select(e,1);").append(table.carrierType).append(" result=table().materializeRequiredRow(e.length==0?-1:e.indexes[0],budget,\"scan.firstOrThrow\",true,sourcePath());table().endSuccess(\"scan.firstOrThrow\",e.selectionScanned,1L,0L);return result;}catch(SomaRuntimeException f){table().endFailure(\"scan.firstOrThrow\",e.attemptedScanned,e.attemptedMatched,f.code());throw f;}catch(RuntimeException f){table().abort(\"scan.firstOrThrow\");throw f;}catch(Error f){table().abort(\"scan.firstOrThrow\");throw f;}finally{finish();}}\n")
                .append("  public List<").append(table.carrierType).append("> fetchAll(){start(\"scan.fetchAll\");return fetchAllStarted(null);}\n")
                .append("  public List<").append(table.carrierType).append("> fetchAll(MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");start(\"scan.fetchAll\");return fetchAllStarted(budget);}\n")
                .append("  private List<").append(table.carrierType).append("> fetchAllStarted(MaterializationBudget budget){GeneratedScanEvaluation e=new GeneratedScanEvaluation();try{budget=materializationBudget(budget);select(e,terminalMaximum());List<").append(table.carrierType).append("> result=table().materializeRows(e.indexes,e.length,budget,\"scan.fetchAll\",true);table().endSuccess(\"scan.fetchAll\",e.selectionScanned,e.length,0L);return result;}catch(SomaRuntimeException f){table().endFailure(\"scan.fetchAll\",e.attemptedScanned,e.attemptedMatched,f.code());throw f;}catch(RuntimeException f){table().abort(\"scan.fetchAll\");throw f;}catch(Error f){table().abort(\"scan.fetchAll\");throw f;}finally{finish();}}\n")
                .append("  private MaterializationBudget materializationBudget(MaterializationBudget budget){return budget==null?table().runtimePlan().defaultMaterializationBudget():budget;}\n")
                .append("  public IndexSnapshot indexSnapshot(){start(\"scan.indexSnapshot\");GeneratedScanEvaluation e=new GeneratedScanEvaluation();try{select(e,terminalMaximum());IndexSnapshot result=table().indexSnapshot(e.indexes,e.length);table().endSuccess(\"scan.indexSnapshot\",e.selectionScanned,e.length,0L);return result;}catch(SomaRuntimeException f){table().endFailure(\"scan.indexSnapshot\",e.attemptedScanned,e.attemptedMatched,f.code());throw f;}catch(RuntimeException f){table().abort(\"scan.indexSnapshot\");throw f;}catch(Error f){table().abort(\"scan.indexSnapshot\");throw f;}finally{finish();}}\n")
                .append("  public UpdateResult update(Updater value){if(value==null)throw new NullPointerException(\"updater\");startMutation(\"scan.update\");long reached=0L;boolean selected=false;GeneratedScanEvaluation e=new GeneratedScanEvaluation();try{select(e,terminalMaximum());selected=true;table().prepareUpdateScratch(e.length);table().loadUpdateScratch(e.indexes,e.length);MutableCursor c=new MutableCursor(table());for(int i=0;i<e.length;i++){reached++;c.open(i,e.indexes[i]);table().beginCallback(\"scan.update.updater\");try{value.update(c);}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"scan.update\",\"updater\",callback);}finally{table().endCallback(\"scan.update.updater\");c.close();}}long changed=table().publishUpdate(e.indexes,e.length);table().endSuccess(\"scan.update\",e.selectionScanned,e.length,changed);return table().updateResult(e.selectionScanned,e.length,changed);}catch(SomaRuntimeException f){table().endFailure(\"scan.update\",selected?e.selectionScanned:e.attemptedScanned,reached==0L&&!selected?e.attemptedMatched:reached,f.code());throw f;}catch(RuntimeException f){table().abort(\"scan.update\");throw f;}catch(Error f){table().abort(\"scan.update\");throw f;}finally{if(selected)table().clearUpdateScratch(e.length);finish();}}\n\n")
                .append("  public RemoveResult remove(){startMutation(\"scan.remove\");boolean selected=false;GeneratedScanEvaluation e=new GeneratedScanEvaluation();try{select(e,terminalMaximum());selected=true;RemoveResult result=table().removeSelected(e.indexes,e.length,e.selectionScanned,\"scan.remove\");table().endSuccess(\"scan.remove\",e.selectionScanned,e.length,e.length);return result;}catch(SomaRuntimeException f){table().endFailure(\"scan.remove\",selected?e.selectionScanned:e.attemptedScanned,selected?e.length:e.attemptedMatched,f.code());throw f;}catch(RuntimeException f){table().abort(\"scan.remove\");throw f;}catch(Error f){table().abort(\"scan.remove\");throw f;}finally{finish();}}\n\n")
                .append("  private void select(GeneratedScanEvaluation e,int maximum){int initial=plan.size();int firstSort=firstSort();if(maximum==1&&stableArgMinEligible(firstSort)){selectStableArgMin(e,initial,firstSort);return;}int required=firstSort<plan.stageCount()?initial:Math.min(initial,maximum);int[] values=table().preparePipelineScratch(required);Cursor cursor=hasFilter()?new Cursor(table()):null;int length=0,scanned=0;for(int position=0;position<initial&&!limitReached(0,firstSort);position++){int index=plan.rowAt(position);scanned++;e.attemptedScanned=scanned;if(matches(index,cursor,0,firstSort)){values[length++]=index;e.attemptedMatched=length;}if(firstSort==plan.stageCount()&&length>=maximum)break;}for(int stage=firstSort;stage<plan.stageCount();stage++){byte kind=plan.kind(stage);if(kind==SORT){stableSort(values,length,(Comparator)plan.callback(stage));}else if(kind==FILTER){int write=0;Predicate predicate=(Predicate)plan.callback(stage);for(int i=0;i<length;i++)if(test(predicate,cursor,values[i],\"scan.filter\",\"scan.filter.predicate\"))values[write++]=values[i];length=write;e.attemptedMatched=length;}else if(kind==SKIP){int remove=(int)Math.min((long)length,plan.argument(stage));System.arraycopy(values,remove,values,0,length-remove);length-=remove;e.attemptedMatched=length;}else{length=(int)Math.min((long)length,plan.argument(stage));e.attemptedMatched=length;}}if(length>maximum)length=maximum;e.attemptedMatched=length;e.indexes=values;e.length=length;e.selectionScanned=scanned;}\n")
                .append("  private boolean stableArgMinEligible(int firstSort){if(firstSort>=plan.stageCount())return false;int sorts=0;for(int i=0;i<plan.stageCount();i++){byte kind=plan.kind(i);if(kind==SORT)sorts++;if(i>firstSort&&kind!=LIMIT)return false;}return sorts==1;}\n")
                .append("  private void selectStableArgMin(GeneratedScanEvaluation e,int initial,int sortStage){int[] values=table().preparePipelineScratch(Math.min(initial,1));Cursor left=new Cursor(table()),right=new Cursor(table()),cursor=hasFilter()?left:null;int best=-1,scanned=0,candidates=0;for(int position=0;position<initial&&!limitReached(0,sortStage);position++){int index=plan.rowAt(position);scanned++;e.attemptedScanned=scanned;if(!matches(index,cursor,0,sortStage))continue;candidates++;e.attemptedMatched=candidates;if(best<0||compare((Comparator)plan.callback(sortStage),left,right,index,best)<0)best=index;}int length=best<0?0:1;for(int stage=sortStage+1;stage<plan.stageCount();stage++)length=(int)Math.min((long)length,plan.argument(stage));if(length!=0)values[0]=best;e.attemptedMatched=length;e.indexes=values;e.length=length;e.selectionScanned=scanned;}\n")
                .append("  private int terminalMaximum(){int first=firstSort(),maximum=Integer.MAX_VALUE;if(first==plan.stageCount()){for(int i=0;i<plan.stageCount();i++)if(plan.kind(i)==LIMIT)maximum=(int)Math.min((long)maximum,plan.argument(i));return maximum;}for(int i=first+1;i<plan.stageCount();i++){if(plan.kind(i)!=LIMIT)return Integer.MAX_VALUE;maximum=(int)Math.min((long)maximum,plan.argument(i));}return maximum;}\n")
                .append("  private int firstSort(){for(int i=0;i<plan.stageCount();i++)if(plan.kind(i)==SORT)return i;return plan.stageCount();}\n")
                .append("  private boolean hasSort(){return firstSort()!=plan.stageCount();}\n")
                .append("  private boolean hasFilter(){for(int i=0;i<plan.stageCount();i++)if(plan.kind(i)==FILTER)return true;return false;}\n")
                .append("  private boolean limitReached(int from,int to){for(int i=from;i<to;i++)if(plan.kind(i)==LIMIT&&plan.argument(i)<=0L)return true;return false;}\n")
                .append("  private boolean matches(int index,Cursor cursor,int from,int to){for(int i=from;i<to;i++){byte kind=plan.kind(i);if(kind==FILTER){if(!test((Predicate)plan.callback(i),cursor,index,\"scan.filter\",\"scan.filter.predicate\"))return false;}else if(kind==SKIP){long remaining=plan.argument(i);if(remaining>0L){plan.argument(i,remaining-1L);return false;}}else if(kind==LIMIT){long remaining=plan.argument(i);if(remaining<=0L)return false;plan.argument(i,remaining-1L);}}return true;}\n")
                .append("  private boolean test(Predicate value,Cursor cursor,int index,String operation,String callbackOperation){cursor.open(index);table().beginCallback(callbackOperation);try{return value.test(cursor);}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",operation,\"predicate\",callback);}finally{table().endCallback(callbackOperation);cursor.close();}}\n")
                .append("  private void stableSort(int[] values,int length,Comparator comparator){int[] auxiliary=table().prepareSortScratch(length);Cursor left=new Cursor(table()),right=new Cursor(table());for(int width=1;width<length;width=width>length/2?length:width*2){for(int start=0;start<length;start+=width*2){int middle=Math.min(start+width,length),end=Math.min(start+width*2,length),a=start,b=middle,w=start;while(a<middle||b<end){if(b>=end||(a<middle&&compare(comparator,left,right,values[a],values[b])<=0))auxiliary[w++]=values[a++];else auxiliary[w++]=values[b++];}System.arraycopy(auxiliary,start,values,start,end-start);}}}\n")
                .append("  private int compare(Comparator value,Cursor left,Cursor right,int a,int b){left.open(a);right.open(b);table().beginCallback(\"scan.sorted.comparator\");try{return value.compare(left,right);}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"scan.sorted\",\"comparator\",callback);}finally{table().endCallback(\"scan.sorted.comparator\");left.close();right.close();}}\n")
                .append("  private void start(String operation){consume(operation);try{table().begin(operation);}catch(RuntimeException failure){finish();throw failure;}catch(Error failure){finish();throw failure;}}\n  private void startMutation(String operation){consume(operation);try{table().preflightMutation(operation);table().begin(operation);}catch(RuntimeException failure){finish();throw failure;}catch(Error failure){finish();throw failure;}}\n  private void consume(String operation){check(operation);plan.consume();}\n  private void check(String operation){if(!plan.isCurrent(generation))throw RuntimeFailures.pipelineConsumed(sourcePath(),operation);}\n")
                .append("  private ").append(table.name("Table")).append(" table(){return plan.table();}\n")
                .append("  private void finish(){plan.clear();}\n")
                .append("  private String sourcePath(){return plan.sourcePath();}\n")
                .append("  static class Source extends GeneratedScanPlan{private ").append(table.name("Table")).append(" table;Source(").append(table.name("Table")).append(" table,String sourcePath){super(sourcePath);this.table=table;}final ").append(table.name("Table")).append(" table(){return table;}int size(){return table.size();}int rowAt(int position){return position;}protected void clearSource(){table=null;}}\n");
        appendScanSources(out, table);
        out.append("  public interface Predicate{boolean test(").append(row).append(" candidate);}\n  public interface Consumer{void accept(").append(row).append(" candidate);}\n  public interface Updater{void update(").append(mutable).append(" candidate);}\n  public interface Comparator{int compare(").append(row).append(" left,").append(row).append(" right);}\n")
                .append("  static final class Cursor implements ").append(row).append(" {\n    protected final ").append(table.name("Table")).append(" table;protected int row;protected boolean active;Cursor(").append(table.name("Table")).append(" table){this.table=table;}void open(int row){this.row=row;active=true;}void close(){active=false;}void valid(){if(!active)throw RuntimeFailures.internalInvariant(\"escaped_cursor\",").append(q(table.logicalName)).append(",\"cursor\");}\n");
        appendCursorMethods(out, table, false);
        out.append("  }\n\n  static final class MutableCursor implements ").append(mutable).append(" {\n    private final ").append(table.name("Table")).append(" table; private int scratch,row; private boolean active; MutableCursor(").append(table.name("Table")).append(" table){this.table=table;} void open(int scratch,int row){this.scratch=scratch;this.row=row;active=true;} void close(){active=false;} void valid(){if(!active)throw RuntimeFailures.internalInvariant(\"escaped_update_cursor\",").append(q(table.logicalName)).append(",\"cursor\");}\n");
        appendCursorMethods(out, table, true);
        for (int fieldIndex = 0; fieldIndex < table.fields.size(); fieldIndex++) {
            FieldSpec field = table.fields.get(fieldIndex);
            if (field.key) {
                continue;
            }
            out.append("    public void set").append(cap(field.javaName)).append('(')
                    .append(field.primitive).append(" value){valid();table.")
                    .append(setUpdateMethod(fieldIndex)).append("(scratch,value);}\n");
            if (field.valueBacked()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    out.append("    public void set").append(cap(leaf.stem(field))).append('(')
                            .append(leaf.primitive).append(" value){valid();table.")
                            .append(leaf.setUpdateMethod(fieldIndex))
                            .append("(scratch,value);}\n");
                }
            }
            if (field.optional) out.append("    public void clear")
                    .append(cap(field.javaName)).append("(){valid();table.")
                    .append(clearUpdateMethod(fieldIndex)).append("(scratch);}\n");
        }
        return out.append("  }\n}\n").toString();
    }

    private void appendPackedTableTerminalExecutors(SourceBuilder out, TableSpec table) {
        String tableType = table.name("Table");
        String carrierType = table.carrierType;
        String scanType = table.name("Scan");
        out.append("  private static boolean packedAnyMatch(").append(tableType).append(" table,").append(scanType).append(".Predicate value){return packedMatch(table,value,true);}\n")
                .append("  private static boolean packedNoneMatch(").append(tableType).append(" table,").append(scanType).append(".Predicate value){return !packedMatch(table,value,false);}\n")
                .append("  private static boolean packedMatch(").append(tableType).append(" table,").append(scanType).append(".Predicate value,boolean any){if(value==null)throw new NullPointerException(\"predicate\");String op=any?\"scan.anyMatch\":\"scan.noneMatch\",callback=any?\"scan.anyMatch.predicate\":\"scan.noneMatch.predicate\";table.begin(op);long scanned=0L,reached=0L;try{").append(scanType).append(".Cursor c=new ").append(scanType).append(".Cursor(table);int size=table.size();for(int row=0;row<size;row++){scanned++;reached++;c.open(row);table.beginCallback(callback);boolean matched;try{matched=value.test(c);}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException failure){throw RuntimeFailures.callbackFailed(")
                .append(q(table.logicalName)).append(",op,\"predicate\",failure);}finally{table.endCallback(callback);c.close();}if(matched){table.endSuccess(op,scanned,reached,0L);return true;}}table.endSuccess(op,scanned,reached,0L);return false;}catch(SomaRuntimeException failure){table.endFailure(op,scanned,reached,failure.code());throw failure;}catch(RuntimeException failure){table.abort(op);throw failure;}catch(Error failure){table.abort(op);throw failure;}}\n")
                .append("  private static void packedForEach(").append(tableType).append(" table,").append(scanType).append(".Consumer value){if(value==null)throw new NullPointerException(\"consumer\");table.begin(\"scan.forEach\");long scanned=0L;try{").append(scanType).append(".Cursor c=new ").append(scanType).append(".Cursor(table);int size=table.size();for(int row=0;row<size;row++){scanned++;c.open(row);table.beginCallback(\"scan.forEach.consumer\");try{value.accept(c);}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException failure){throw RuntimeFailures.callbackFailed(")
                .append(q(table.logicalName)).append(",\"scan.forEach\",\"consumer\",failure);}finally{table.endCallback(\"scan.forEach.consumer\");c.close();}}table.endSuccess(\"scan.forEach\",scanned,scanned,0L);}catch(SomaRuntimeException failure){table.endFailure(\"scan.forEach\",scanned,scanned,failure.code());throw failure;}catch(RuntimeException failure){table.abort(\"scan.forEach\");throw failure;}catch(Error failure){table.abort(\"scan.forEach\");throw failure;}}\n")
                .append("  private static Optional<").append(carrierType).append("> packedFindFirst(").append(tableType).append(" table,MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");table.begin(\"scan.findFirst\");long reached=0L;try{int row=table.size()==0?-1:0;reached=row<0?0L:1L;Optional<").append(carrierType).append("> result=table.materializeOptionalRow(row,budget,\"scan.findFirst\",true);table.endSuccess(\"scan.findFirst\",reached,reached,0L);return result;}catch(SomaRuntimeException failure){table.endFailure(\"scan.findFirst\",reached,reached,failure.code());throw failure;}catch(RuntimeException failure){table.abort(\"scan.findFirst\");throw failure;}catch(Error failure){table.abort(\"scan.findFirst\");throw failure;}}\n")
                .append("  private static ").append(carrierType).append(" packedFirstOrThrow(").append(tableType).append(" table,MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");table.begin(\"scan.firstOrThrow\");long reached=0L;try{int row=table.size()==0?-1:0;reached=row<0?0L:1L;").append(carrierType).append(" result=table.materializeRequiredRow(row,budget,\"scan.firstOrThrow\",true,")
                .append(q(table.logicalName)).append(");table.endSuccess(\"scan.firstOrThrow\",1L,1L,0L);return result;}catch(SomaRuntimeException failure){table.endFailure(\"scan.firstOrThrow\",reached,reached,failure.code());throw failure;}catch(RuntimeException failure){table.abort(\"scan.firstOrThrow\");throw failure;}catch(Error failure){table.abort(\"scan.firstOrThrow\");throw failure;}}\n")
                .append("  private static List<").append(carrierType).append("> packedFetchAll(").append(tableType).append(" table,MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");table.begin(\"scan.fetchAll\");long reached=0L;try{int count=table.size();int[] rows=table.preparePipelineScratch(count);for(int i=0;i<count;i++)rows[i]=i;reached=count;List<").append(carrierType).append("> result=table.materializeRows(rows,count,budget,\"scan.fetchAll\",true);table.endSuccess(\"scan.fetchAll\",reached,reached,0L);return result;}catch(SomaRuntimeException failure){table.endFailure(\"scan.fetchAll\",reached,reached,failure.code());throw failure;}catch(RuntimeException failure){table.abort(\"scan.fetchAll\");throw failure;}catch(Error failure){table.abort(\"scan.fetchAll\");throw failure;}}\n")
                .append("  private static IndexSnapshot packedIndexSnapshot(").append(tableType).append(" table){table.begin(\"scan.indexSnapshot\");long reached=0L;try{int count=table.size();int[] rows=table.preparePipelineScratch(count);for(int i=0;i<count;i++)rows[i]=i;reached=count;IndexSnapshot result=table.indexSnapshot(rows,count);table.endSuccess(\"scan.indexSnapshot\",reached,reached,0L);return result;}catch(SomaRuntimeException failure){table.endFailure(\"scan.indexSnapshot\",reached,reached,failure.code());throw failure;}catch(RuntimeException failure){table.abort(\"scan.indexSnapshot\");throw failure;}catch(Error failure){table.abort(\"scan.indexSnapshot\");throw failure;}}\n")
                .append("  private static UpdateResult packedUpdate(").append(tableType).append(" table,").append(scanType).append(".Updater value){if(value==null)throw new NullPointerException(\"updater\");table.preflightMutation(\"scan.update\");table.begin(\"scan.update\");long reached=0L,scanned=0L;boolean selected=false;int count=0;try{count=table.size();int[] rows=table.preparePipelineScratch(count);for(int i=0;i<count;i++)rows[i]=i;scanned=count;selected=true;table.prepareUpdateScratch(count);table.loadUpdateScratch(rows,count);").append(scanType).append(".MutableCursor c=new ").append(scanType).append(".MutableCursor(table);for(int i=0;i<count;i++){reached++;c.open(i,rows[i]);table.beginCallback(\"scan.update.updater\");try{value.update(c);}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException failure){throw RuntimeFailures.callbackFailed(")
                .append(q(table.logicalName)).append(",\"scan.update\",\"updater\",failure);}finally{table.endCallback(\"scan.update.updater\");c.close();}}long changed=table.publishUpdate(rows,count);table.endSuccess(\"scan.update\",scanned,count,changed);return table.updateResult(scanned,count,changed);}catch(SomaRuntimeException failure){table.endFailure(\"scan.update\",scanned,reached,failure.code());throw failure;}catch(RuntimeException failure){table.abort(\"scan.update\");throw failure;}catch(Error failure){table.abort(\"scan.update\");throw failure;}finally{if(selected)table.clearUpdateScratch(count);}}\n")
                .append("  private static RemoveResult packedRemove(").append(tableType).append(" table){table.preflightMutation(\"scan.remove\");table.begin(\"scan.remove\");long scanned=0L;try{int count=table.size();int[] rows=table.preparePipelineScratch(count);for(int i=0;i<count;i++)rows[i]=i;scanned=count;RemoveResult result=table.removeSelected(rows,count,scanned,\"scan.remove\");table.endSuccess(\"scan.remove\",scanned,count,count);return result;}catch(SomaRuntimeException failure){table.endFailure(\"scan.remove\",scanned,scanned,failure.code());throw failure;}catch(RuntimeException failure){table.abort(\"scan.remove\");throw failure;}catch(Error failure){table.abort(\"scan.remove\");throw failure;}}\n");
    }

    private void appendScanSources(SourceBuilder out, TableSpec table) {
        String scan = table.name("Scan");
        String tableType = table.name("Table");
        for (int selectorIndex = 0; selectorIndex < table.selectors.size(); selectorIndex++) {
            SelectorSpec selector = table.selectors.get(selectorIndex);
            String sourceType = "ExactSource" + selectorIndex;
            String method = selectorMethodName(selector);
            out.append("  static ").append(scan).append(" exact")
                    .append(selectorIndex).append('(').append(tableType)
                    .append(" table,long hash");
            for (int leafIndex = 0; leafIndex < selector.leaves.size(); leafIndex++) {
                SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
                out.append(',').append(leaf.storageType).append(" sourceLeaf")
                        .append(leafIndex);
            }
            out.append("){return new ").append(scan).append("(new ")
                    .append(sourceType).append("(table,hash");
            appendSourceLeafArguments(out, selector.leaves.size());
            out.append("));}\n  static final class ").append(sourceType)
                    .append(" extends Source{private final long hash;");
            for (int leafIndex = 0; leafIndex < selector.leaves.size(); leafIndex++) {
                SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
                out.append("private ").append(leaf.storageType).append(" sourceLeaf")
                        .append(leafIndex).append(';');
            }
            out.append("private int group,row,nextPosition;").append(sourceType)
                    .append('(').append(tableType).append(" table,long hash");
            for (int leafIndex = 0; leafIndex < selector.leaves.size(); leafIndex++) {
                SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
                out.append(',').append(leaf.storageType).append(" sourceLeaf")
                        .append(leafIndex);
            }
            out.append("){super(table,").append(q(table.logicalName + "." + method))
                    .append(");this.hash=hash;");
            for (int leafIndex = 0; leafIndex < selector.leaves.size(); leafIndex++) {
                out.append("this.sourceLeaf").append(leafIndex)
                        .append("=sourceLeaf").append(leafIndex).append(';');
            }
            out.append("}int size(){").append(tableType)
                    .append(" table=table();group=table.selector").append(selectorIndex)
                    .append("SourceGroup(hash");
            appendSourceLeafArguments(out, selector.leaves.size());
            out.append(");row=group<0?-1:table.selector").append(selectorIndex)
                    .append("SourceFirst(group);nextPosition=0;return group<0?0:table.selector")
                    .append(selectorIndex).append("SourceSize(group);}int rowAt(")
                    .append("int position){").append(tableType)
                    .append(" table=table();if(position!=nextPosition||row<0)throw RuntimeFailures.internalInvariant(\"exact_index_source_sequence\",")
                    .append(q(table.logicalName)).append(',').append(q(method))
                    .append(");int result=row;row=table.selector").append(selectorIndex)
                    .append("SourceNext(row);nextPosition++;return result;}protected void clearSource(){");
            for (int leafIndex = 0; leafIndex < selector.leaves.size(); leafIndex++) {
                SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
                if (!primitiveJavaType(leaf.storageType)) {
                    out.append("sourceLeaf").append(leafIndex).append("=null;");
                }
            }
            out.append("super.clearSource();}}\n");
        }
    }

    private static void appendSourceLeafArguments(SourceBuilder out, int leafCount) {
        for (int leafIndex = 0; leafIndex < leafCount; leafIndex++) {
            out.append(",sourceLeaf").append(leafIndex);
        }
    }

    private static boolean primitiveJavaType(String type) {
        return "boolean".equals(type) || "byte".equals(type)
                || "short".equals(type) || "int".equals(type)
                || "long".equals(type) || "float".equals(type)
                || "double".equals(type) || "char".equals(type);
    }

    private void appendCursorMethods(SourceBuilder out, TableSpec table, boolean scratch) {
        for (int fieldIndex = 0; fieldIndex < table.fields.size(); fieldIndex++) {
            FieldSpec field = table.fields.get(fieldIndex);
            String index = scratch && !field.key ? "scratch" : "row";
            String value = scratch && !field.key
                    ? updateValueMethod(fieldIndex) + "(" + index + ")"
                    : field.javaName + "Value(" + index + ")";
            String present = scratch && !field.key
                    ? updatePresentMethod(fieldIndex) + "(" + index + ")"
                    : field.javaName + "Present(" + index + ")";
            if (field.optional) {
                out.append("    public boolean ").append(field.javaName).append("Present(){valid();return table.").append(present).append(";}\n")
                        .append("    public boolean ").append(field.javaName).append("Absent(){return !").append(field.javaName).append("Present();}\n")
                        .append("    public ").append(field.primitive).append(' ').append(field.javaName).append("(){valid();if(!table.").append(present).append(")throw RuntimeFailures.optionalAbsent(").append(q(table.logicalName)).append(',').append(q(field.logicalName)).append(",\"cursor.get\");return table.").append(value).append(";}\n")
                        .append("    public ").append(field.primitive).append(' ').append(field.javaName).append("Or(").append(field.primitive).append(" defaultValue){valid();return table.").append(present).append("?table.").append(value).append(":defaultValue;}\n");
            } else {
                out.append("    public ").append(field.primitive).append(' ').append(field.javaName).append("(){valid();return table.").append(value).append(";}\n");
            }
            if (field.valueBacked()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    String leafValue = scratch && !field.key
                            ? leaf.updateValue(field, fieldIndex, index)
                            : leaf.stem(field) + "Value(" + index + ")";
                    out.append("    public ").append(leaf.primitive).append(' ')
                            .append(leaf.stem(field)).append("(){valid();");
                    if (field.optional) {
                        out.append("if(!table.").append(present)
                                .append(")throw RuntimeFailures.optionalAbsent(")
                                .append(q(table.logicalName)).append(',')
                                .append(q(field.logicalName)).append(",\"cursor.get\");");
                    }
                    out.append("return table.").append(leafValue).append(";}\n");
                }
            }
        }
    }

    private String tableSource(TableSpec table) {
        String name = table.name("Table");
        SourceBuilder out = new SourceBuilder(header());
        out.append("import com.hgtech.soma.runtime.*;\n")
                .append("import com.hgtech.soma.runtime.generated.*;\n")
                .append("import java.util.ArrayList;\nimport java.util.Arrays;\nimport java.util.List;\nimport java.util.Optional;\n\n")
                .append("public final class ").append(name).append(" {\n")
                .append("  private static final String TABLE=").append(q(table.logicalName)).append(";\n")
                .append("  private static final GeneratedMetadata METADATA=new GeneratedMetadata(")
                .append(q(schemaHash)).append(",RuntimeCompatibility.GENERATED_TARGET,RuntimeCompatibility.COMPILER_IDENTITY,RuntimeCompatibility.GENERATED_PROTOCOL,RuntimeCompatibility.RUNTIME_COMPATIBILITY,RuntimeCompatibility.PLAN_PROTOCOL,RuntimeCompatibility.DENSE_ALGORITHM,RuntimeCompatibility.ALLOCATION_ESTIMATOR);\n")
                .append("  private static final RuntimePlan DEFAULT_RUNTIME_PLAN=createDefaultRuntimePlan();\n");
        for (FieldSpec field : table.fields) {
            if (field.enumType != null) {
                out.append("  private static final ").append(field.enumType).append("[] ")
                        .append(field.enumConstantsName()).append('=')
                        .append(field.enumType).append(".values();\n");
            }
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    if (leaf.enumType != null) {
                        out.append("  private static final ").append(leaf.enumType).append("[] ")
                                .append(leaf.enumConstantsName(field)).append('=')
                                .append(leaf.enumType).append(".values();\n");
                    }
                }
            }
        }
        for (FieldSpec field : table.fields) {
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    String physical = leaf.physicalName(field);
                    out.append("  private final ").append(leaf.columnType).append(' ')
                            .append(physical).append("Column=new ")
                            .append(leaf.columnType).append("();\n");
                }
            } else {
                out.append("  private final ").append(field.columnType).append(' ').append(field.javaName)
                        .append("Column=new ").append(field.columnType).append("();\n");
            }
            if (field.optional) out.append("  private final PresenceBitmap ").append(field.javaName).append("Presence=new PresenceBitmap();\n");
        }
        if (!table.children.isEmpty()) {
            out.append("  private final LongColumn ownerTokenColumn=new LongColumn();\n");
            for (ChildSpec child : table.children) {
                out.append("  private final LongColumn ").append(child.javaName)
                        .append("HandleColumn=new LongColumn();\n");
                out.append("  private final TablePlan ").append(child.javaName)
                        .append("TablePlan;\n");
                if (child.optional) {
                    out.append("  private final PresenceBitmap ").append(child.javaName)
                            .append("ChildPresence=new PresenceBitmap();\n");
                }
            }
        }
        if (table.keyed()) {
            out.append("  private ").append(table.keyField().keySpaceType())
                    .append(" keySpace;\n");
        }
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("  private GroupedExactIndex selector")
                    .append(i).append("Index;\n");
        }
        out.append("  private final DenseTableState state;\n")
                .append("  private final ChildOwnershipRegistry ownership;\n")
                .append("  private final boolean owned;\n")
                .append("  private final Object indexSnapshotOwner=new Object();\n")
                .append("  private final IndexBuffer candidateScratch=new IndexBuffer(),pipelineScratch=new IndexBuffer(),sortScratch=new IndexBuffer();private int updateScratchCapacity;\n");
        for (int fieldIndex = 0; fieldIndex < table.fields.size(); fieldIndex++) {
            FieldSpec field = table.fields.get(fieldIndex);
            if (field.key) {
                continue;
            }
            if (field.flattenedValueStorage()) {
                for (int leafIndex = 0; leafIndex < field.valueLeaves.size(); leafIndex++) {
                    ValueLeafSpec leaf = field.valueLeaves.get(leafIndex);
                    out.append("  private ").append(leaf.storagePrimitive)
                            .append("[] ").append(updateScratch(fieldIndex, leafIndex))
                            .append("=new ").append(leaf.storagePrimitive).append("[0];\n");
                }
            } else {
                out.append("  private ").append(field.storagePrimitive).append("[] ")
                        .append(updateScratch(fieldIndex)).append("=new ")
                        .append(field.storagePrimitive).append("[0];\n");
            }
            if (field.optional) out.append("  private boolean[] ")
                    .append(updatePresenceScratch(fieldIndex)).append("=new boolean[0];\n");
        }
        out.append("\n  private ").append(name).append("(RuntimePlan plan,TablePlan tablePlan,ChildOwnershipRegistry ownership,boolean owned,String ownershipPath){\n")
                .append("    this.ownership=ownership;this.owned=owned;\n");
        for (ChildSpec child : table.children) {
            out.append("    this.").append(child.javaName)
                    .append("TablePlan=effectiveChildTablePlan(plan,")
                    .append(q(child.logicalName)).append(");\n");
        }
        out.append("    ColumnGroup columns=new ColumnGroup(TABLE,tablePlan,ownership,tablePlan.initialCapacity()");
        for (FieldSpec field : table.fields) {
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    out.append(',').append(leaf.physicalName(field)).append("Column");
                }
            } else {
                out.append(',').append(field.javaName).append("Column");
            }
            if (field.optional) out.append(',').append(field.javaName).append("Presence");
        }
        if (!table.children.isEmpty()) {
            out.append(",ownerTokenColumn");
            for (ChildSpec child : table.children) {
                out.append(',').append(child.javaName).append("HandleColumn");
                if (child.optional) out.append(',').append(child.javaName).append("ChildPresence");
            }
        }
        out.append(");\n    state=new DenseTableState(TABLE,plan,tablePlan,columns);\n");
        out.append("    if(owned)state.markOwned(ownershipPath);\n");
        if (table.keyed()) {
            out.append("    try{keySpace=newKeySpace(tablePlan.initialCapacity(),\"table.create\");")
                    .append("state.releaseBulkScratch(keySpace.retainedBytes(),\"table.create\");state.commitKeySpaceStorage(0L,keySpace.retainedBytes(),\"table.create\");}")
                    .append("catch(RuntimeException failure){if(keySpace!=null)keySpace.releaseStorage();state.abortConstruction();throw failure;}")
                    .append("catch(Error failure){if(keySpace!=null)keySpace.releaseStorage();state.abortConstruction();throw failure;}\n");
        }
        if (!table.selectors.isEmpty()) {
            out.append("    long estimatedExactIndexBytes=0L;");
            for (int i = 0; i < table.selectors.size(); i++) {
                out.append("estimatedExactIndexBytes=addExactMetric(estimatedExactIndexBytes,GroupedExactIndex.estimatedRetainedBytes(tablePlan.initialCapacity(),0));");
            }
            out.append("state.preflightExactIndexStorage(estimatedExactIndexBytes,\"table.create\");try{");
            for (int i = 0; i < table.selectors.size(); i++) {
                out.append("selector").append(i)
                        .append("Index=new GroupedExactIndex(tablePlan.initialCapacity(),0);");
            }
            out.append("long actualExactIndexBytes=exactIndexRetainedBytes();if(actualExactIndexBytes!=estimatedExactIndexBytes)throw RuntimeFailures.internalInvariant(\"exact_index_estimator\",TABLE,\"table.create\");state.commitExactIndexStorage(0L,actualExactIndexBytes,\"table.create\");}catch(RuntimeException failure){");
            for (int i = 0; i < table.selectors.size(); i++) {
                out.append("if(selector").append(i)
                        .append("Index!=null)selector").append(i).append("Index.release();");
            }
            if (table.keyed()) out.append("if(keySpace!=null)keySpace.releaseStorage();");
            out.append("state.abortConstruction();throw failure;}catch(Error failure){");
            for (int i = 0; i < table.selectors.size(); i++) {
                out.append("if(selector").append(i)
                        .append("Index!=null)selector").append(i).append("Index.release();");
            }
            if (table.keyed()) out.append("if(keySpace!=null)keySpace.releaseStorage();");
            out.append("state.abortConstruction();throw failure;}\n");
        }
        out.append("  }\n\n");
        if (!table.children.isEmpty()) {
            out.append("  private static TablePlan effectiveChildTablePlan(RuntimePlan plan,String childField){ChildPlan child=plan.requireChild(TABLE,childField);TablePlan base=plan.requireTable(child.childTable());return base.initialCapacity()==child.initialCapacity()?base:base.toBuilder().initialCapacity(child.initialCapacity()).build();}\n");
        }
        out.append("  public static ").append(name).append(" create(){return create(defaultRuntimePlan());}\n")
                .append("  public static ").append(name).append(" create(RuntimePlan plan){if(plan==null)throw new NullPointerException(\"plan\");verifySchemaPlan(plan);TablePlan tablePlan=RuntimeCompatibility.verifyAccess(RuntimeCompatibility.verify(METADATA,plan,TABLE),")
                .append(table.selectors.isEmpty() ? "false" : "true")
                .append(");return new ").append(name).append("(plan,tablePlan,new ChildOwnershipRegistry(plan.maximumAggregateStorageBytes(),plan.maximumOwnershipTableInstances()),false,\"\");}\n")
                .append("  static ").append(name).append(" createOwned(RuntimePlan plan,ChildOwnershipRegistry ownership,TablePlan tablePlan,String path){if(tablePlan==null)throw new NullPointerException(\"tablePlan\");return new ")
                .append(name).append("(plan,tablePlan,ownership,true,path);}\n")
                .append("  public static RuntimePlan defaultRuntimePlan(){return DEFAULT_RUNTIME_PLAN;}\n");
        appendSchemaPlanRuntime(out);
        out.append("  public RuntimePlan runtimePlan(){state.checkCallbackAccess(\"runtimePlan\");return state.runtimePlan();}\n  public int size(){state.checkActive(\"size\");return state.size();}\n  public int capacity(){state.checkActive(\"capacity\");return state.capacity();}\n  public long structuralEpoch(){state.checkCallbackAccess(\"structuralEpoch\");return state.structuralEpoch();}\n  public boolean isReleased(){state.checkCallbackAccess(\"isReleased\");return state.isReleased();}\n  public void reserve(int expectedCapacity){ownership.preflightMutation(\"reserve\");if(expectedCapacity<0)throw new IllegalArgumentException(\"expectedCapacity must be non-negative\");int required=Math.max(size(),expectedCapacity),additional=required-size();long proposedKeySpace=")
                .append(table.keyed()
                        ? "keySpace.retainedBytesAfterEnsureAdditional(additional)"
                        : "0L")
                .append(",proposedExactIndexes=exactIndexRetainedBytesAfterEnsure(required,0);state.preflightReserve(expectedCapacity,proposedKeySpace,proposedExactIndexes);")
                .append(table.keyed()
                        ? "ensureAppendKeyCapacity(additional,\"reserve\");"
                        : "")
                .append("ensureExactIndexCapacity(required,0,\"reserve\");state.commitReserve(expectedCapacity);}\n\n");
        if (table.children.isEmpty() && table.keyed()) {
            String keySpaceType = table.keyField().keySpaceType();
            out.append("  public void addBatch(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");ownership.preflightMutation(\"addBatch\");state.prepareAppend(0);int count=batch.size();if(count==0)return;")
                    .append("validateSelectorAppend(batch);validateUniqueAppend(batch);validateAppendKeys(batch);ensureAppendKeyCapacity(count,\"addBatch\");ensureExactIndexAppendCapacity(size()+count,batch,\"addBatch\");int start=state.prepareAppend(count);boolean keysAppended=false;try{copyBatch(batch,0,start,count);appendKeys(batch,start);keysAppended=true;linkExactIndexRows(start,count);state.commitAppend(start,count);}catch(RuntimeException failure){if(keysAppended)rollbackAppendKeys(batch,start);clearColumns(start,start+count);throw failure;}catch(Error failure){if(keysAppended)rollbackAppendKeys(batch,start);clearColumns(start,start+count);throw failure;}}\n")
                    .append("  public void replaceAll(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");ownership.preflightMutation(\"replaceAll\");state.prepareReplace(0);")
                    .append("validateSelectorReplacement(batch);validateUniqueReplacement(batch);").append(keySpaceType).append(" staged=stageReplacementKeys(batch);staged.addMetrics(keySpace.probeCount(),keySpace.collisionCount(),keySpace.rehashCount());ExactIndexStage stagedIndexes=stageExactIndexes(batch,\"replaceAll\");int count=batch.size();try{state.preflightReplaceStorage(count,staged.retainedBytes(),stagedIndexes.retainedBytes(),\"replaceAll\");int previous=state.prepareReplace(count);copyBatch(batch,0,0,count);if(previous>count)clearColumns(count,previous);publishKeySpace(staged,\"replaceAll\");staged=null;stagedIndexes.publish(\"replaceAll\");stagedIndexes=null;state.commitReplace(previous,count);}catch(RuntimeException failure){discardKeySpace(staged,\"replaceAll\");if(stagedIndexes!=null)stagedIndexes.discard(\"replaceAll\");throw failure;}catch(Error failure){discardKeySpace(staged,\"replaceAll\");if(stagedIndexes!=null)stagedIndexes.discard(\"replaceAll\");throw failure;}}\n")
                    .append("  public void clear(){ownership.preflightMutation(\"clear\");int previous=state.prepareClear();clearColumns(0,previous);keySpace.clear();clearExactIndexes();state.commitClear(previous);}\n")
                    .append("  public void release(){state.rejectOwnedRelease(\"release\");ownership.preflightMutation(\"release\");int previous=state.prepareRelease();if(previous>=0){clearColumns(0,previous);keySpace.releaseStorage();releaseExactIndexes(\"release\");releaseRetainedScratch();state.commitRelease(previous);ownership.releaseStorage();}}\n\n");
        } else if (table.children.isEmpty()) {
            out.append("  public void addBatch(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");ownership.preflightMutation(\"addBatch\");state.prepareAppend(0);int count=batch.size();if(count==0)return;validateSelectorAppend(batch);validateUniqueAppend(batch);ensureExactIndexAppendCapacity(size()+count,batch,\"addBatch\");int start=state.prepareAppend(count);copyBatch(batch,0,start,count);linkExactIndexRows(start,count);state.commitAppend(start,count);}\n")
                    .append("  public void replaceAll(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");ownership.preflightMutation(\"replaceAll\");state.prepareReplace(0);int count=batch.size();validateSelectorReplacement(batch);validateUniqueReplacement(batch);ExactIndexStage stagedIndexes=stageExactIndexes(batch,\"replaceAll\");try{state.preflightReplaceStorage(count,0L,stagedIndexes.retainedBytes(),\"replaceAll\");int previous=state.prepareReplace(count);copyBatch(batch,0,0,count);if(previous>count)clearColumns(count,previous);stagedIndexes.publish(\"replaceAll\");stagedIndexes=null;state.commitReplace(previous,count);}catch(RuntimeException failure){if(stagedIndexes!=null)stagedIndexes.discard(\"replaceAll\");throw failure;}catch(Error failure){if(stagedIndexes!=null)stagedIndexes.discard(\"replaceAll\");throw failure;}}\n")
                    .append("  public void clear(){ownership.preflightMutation(\"clear\");int previous=state.prepareClear();clearColumns(0,previous);clearExactIndexes();state.commitClear(previous);}\n")
                    .append("  public void release(){state.rejectOwnedRelease(\"release\");ownership.preflightMutation(\"release\");int previous=state.prepareRelease();if(previous>=0){clearColumns(0,previous);releaseExactIndexes(\"release\");releaseRetainedScratch();state.commitRelease(previous);ownership.releaseStorage();}}\n\n");
        } else {
            appendChildStructuralMethods(out, table);
        }
        out.append("  private void copyBatch(").append(table.name("Batch")).append(" batch,int source,int target,int count){for(int i=0;i<count;i++){int s=source+i,t=target+i;\n");
        for (FieldSpec field : table.fields) {
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    String physical = leaf.physicalName(field);
                    String stored = "batch." + physical + "StorageValue(s)";
                    SelectorLeafSpec accessLeaf = selectorLeaf(
                            table, field.logicalName + "." + leaf.logicalName);
                    if (accessLeaf != null) {
                        stored = canonicalAccessStorage(
                                accessLeaf, stored, "table.import");
                    }
                    out.append("    ").append(physical).append("Column.set(t,")
                            .append(stored).append(");\n");
                }
            } else {
                String stored = "batch." + field.javaName + "StorageValue(s)";
                SelectorLeafSpec accessLeaf = selectorLeaf(table, field.logicalName);
                if (accessLeaf != null) {
                    stored = canonicalAccessStorage(accessLeaf, stored, "table.import");
                }
                out.append("    ").append(field.javaName).append("Column.set(t,")
                        .append(stored).append(");\n");
            }
            if (field.optional) out.append("    if(batch.").append(field.javaName).append("Present(s))").append(field.javaName).append("Presence.setPresent(t);else ").append(field.javaName).append("Presence.clearPresent(t);\n");
        }
        out.append("  }}\n  private void clearColumns(int from,int to){\n");
        for (FieldSpec field : table.fields) {
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    out.append("    ").append(leaf.physicalName(field))
                            .append("Column.clearRange(from,to);\n");
                }
            } else {
                out.append("    ").append(field.javaName).append("Column.clearRange(from,to);\n");
            }
            if (field.optional) out.append("    ").append(field.javaName).append("Presence.clearRange(from,to);\n");
        }
        if (!table.children.isEmpty()) {
            out.append("    ownerTokenColumn.clearRange(from,to);\n");
            for (ChildSpec child : table.children) {
                out.append("    ").append(child.javaName)
                        .append("HandleColumn.clearRange(from,to);\n");
                if (child.optional) out.append("    ").append(child.javaName)
                        .append("ChildPresence.clearRange(from,to);\n");
            }
        }
        out.append("  }\n\n");
        if (!table.children.isEmpty()) {
            appendChildOwnershipRuntime(out, table);
        }
        if (table.keyed()) {
            FieldSpec key = table.keyField();
            out.append("  private int appendKeyCapacity(int batchSize){long total=(long)size()+(long)batchSize;if(total>Integer.MAX_VALUE)throw RuntimeFailures.memoryLimitExceeded(TABLE,\"addBatch\",Integer.MAX_VALUE,total);return(int)total;}\n");
            if (key.compositeKey()) {
                appendCompositeKeyRuntime(out, table, key);
            } else {
                out.append("  private void validateAppendKeys(")
                        .append(table.name("Batch")).append(" batch){if(batch.size()==1){")
                        .append(key.valueBacked() ? key.storagePrimitive : key.primitive).append(" key=batch.")
                        .append(key.javaName).append(key.valueBacked() ? "StorageValue(0);" : "Value(0);")
                        .append(key.keySpaceValueType()).append(" keySlot=")
                        .append(key.valueBacked() ? key.keySpaceValueFromStorage("key", "addBatch") : key.keySpaceValue("key", "addBatch"))
                        .append(';').append(key.requireInsertKey("keySpace", "keySlot", "addBatch"))
                        .append("if(keySpace.contains(keySlot))throw ")
                        .append(key.valueBacked() ? "RuntimeFailures.duplicateValueKey(TABLE," + q(key.logicalName) + ",\"addBatch\")" : "RuntimeFailures.duplicateKey(TABLE,key,\"addBatch\")")
                        .append(";return;}")
                        .append(key.appendValidationKeySpaceType())
                        .append(" staged=newAppendValidationKeySpace(batch.size(),\"addBatch\");try{for(int row=0;row<batch.size();row++){")
                        .append(key.valueBacked() ? key.storagePrimitive : key.primitive).append(" key=batch.")
                        .append(key.javaName).append(key.valueBacked() ? "StorageValue(row);" : "Value(row);")
                        .append(key.keySpaceValueType()).append(" keySlot=")
                        .append(key.valueBacked() ? key.keySpaceValueFromStorage("key", "addBatch") : key.keySpaceValue("key", "addBatch"))
                        .append(';').append(key.requireInsertKey("keySpace", "keySlot", "addBatch"))
                        .append("if(keySpace.contains(keySlot)||staged.contains(keySlot))throw ")
                        .append(key.valueBacked() ? "RuntimeFailures.duplicateValueKey(TABLE," + q(key.logicalName) + ",\"addBatch\")" : "RuntimeFailures.duplicateKey(TABLE,key,\"addBatch\")")
                        .append(";staged.put(keySlot,row);}}finally{discardAppendValidationKeySpace(staged,\"addBatch\");}}\n")
                        .append("  private void appendKeys(").append(table.name("Batch"))
                        .append(" batch,int start){for(int row=0;row<batch.size();row++){")
                        .append(key.valueBacked() ? key.storagePrimitive : key.primitive).append(" key=batch.")
                        .append(key.javaName).append(key.valueBacked() ? "StorageValue(row);" : "Value(row);")
                        .append("keySpace.put(")
                        .append(key.valueBacked() ? key.keySpaceValueFromStorage("key", "addBatch") : key.keySpaceValue("key", "addBatch"))
                        .append(",start+row);}}\n")
                        .append("  private void rollbackAppendKeys(").append(table.name("Batch"))
                        .append(" batch,int start){for(int row=batch.size()-1;row>=0;row--){")
                        .append(key.valueBacked() ? key.storagePrimitive : key.primitive).append(" key=batch.")
                        .append(key.javaName).append(key.valueBacked() ? "StorageValue(row);" : "Value(row);")
                        .append("keySpace.remove(")
                        .append(key.valueBacked() ? key.keySpaceValueFromStorage("key", "addBatch.rollback") : key.keySpaceValue("key", "addBatch.rollback"))
                        .append(");}}\n")
                        .append("  private ").append(key.keySpaceType()).append(" stageReplacementKeys(")
                        .append(table.name("Batch")).append(" batch){")
                        .append(key.keySpaceType()).append(" staged=newKeySpace(")
                        .append("batch.size(),\"replaceAll\")")
                        .append(";for(int row=0;row<batch.size();row++){")
                        .append(key.valueBacked() ? key.storagePrimitive : key.primitive).append(" key=batch.")
                        .append(key.javaName).append(key.valueBacked() ? "StorageValue(row);" : "Value(row);")
                        .append(key.keySpaceValueType()).append(" keySlot=")
                        .append(key.valueBacked() ? key.keySpaceValueFromStorage("key", "replaceAll") : key.keySpaceValue("key", "replaceAll"))
                        .append(';').append(key.requireInsertKey("staged", "keySlot", "replaceAll"))
                        .append("if(staged.contains(keySlot))throw ")
                        .append(key.valueBacked() ? "RuntimeFailures.duplicateValueKey(TABLE," + q(key.logicalName) + ",\"replaceAll\")" : "RuntimeFailures.duplicateKey(TABLE,key,\"replaceAll\")")
                        .append(";staged.put(keySlot,row);}return staged;}\n")
                        .append("  private int keyRow(").append(key.primitive).append(" key,String operation){int row=keySpace.rowOf(")
                        .append(key.keySpaceValueExpression("key", "operation"))
                        .append(");if(row<0)throw ")
                        .append(key.valueBacked() ? "RuntimeFailures.missingValueKey(TABLE," + q(key.logicalName) + ",operation)" : "RuntimeFailures.missingKey(TABLE,key,operation)")
                        .append(";return row;}\n\n");
            }
            appendKeySpaceLifecycleRuntime(out, key);
        }
        appendMaterializationRuntime(out, table);
        if (table.keyed()) {
            FieldSpec key = table.keyField();
            if (key.compositeKey()) {
                out.append("  public boolean containsKey(").append(key.primitive).append(" key){state.checkActive(\"containsKey\");return compositeLookup(key,\"containsKey\")>=0;}\n")
                        .append("  public int findIndex(").append(key.primitive).append(" key){state.checkActive(\"findIndex\");return compositeLookup(key,\"findIndex\");}\n")
                        .append("  public int requireIndex(").append(key.primitive).append(" key){state.checkActive(\"requireIndex\");return keyRow(key,\"requireIndex\");}\n")
                        .append("  public java.util.Optional<").append(table.carrierType).append("> find(").append(key.primitive).append(" key){return find(key,runtimePlan().defaultMaterializationBudget());}\n")
                        .append("  public java.util.Optional<").append(table.carrierType).append("> find(").append(key.primitive).append(" key,MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");state.checkActive(\"find\");int row=compositeLookup(key,\"find\");return materializeOptionalRow(row,budget,\"find\",false);}\n")
                        .append("  public ").append(table.carrierType).append(" fetch(").append(key.primitive).append(" key){return materializeRequiredRow(keyRow(key,\"fetch\"),runtimePlan().defaultMaterializationBudget(),\"fetch\",false,TABLE);}\n")
                        .append("  public ").append(table.carrierType).append(" fetch(").append(key.primitive).append(" key,MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");return materializeRequiredRow(keyRow(key,\"fetch\"),budget,\"fetch\",false,TABLE);}\n")
                        .append("  public ").append(table.name("Mutator")).append(" mutate(").append(key.primitive).append(" key){return mutateAt(keyRow(key,\"mutate\"));}\n")
                        .append("  public void delete(").append(key.primitive).append(" key){ownership.preflightMutation(\"delete\");state.beginOperation(\"delete\");long scanned=0L;try{int row=keyRow(key,\"delete\");scanned=1L;int[] selected=preparePipelineScratch(1);selected[0]=row;removeSelected(selected,1,1L,\"delete\");state.endOperationSuccess(\"delete\",1L,1L,1L);}catch(SomaRuntimeException failure){state.endOperationFailure(\"delete\",scanned,0L,failure.code());throw failure;}catch(RuntimeException failure){state.abortOperation(\"delete\");throw failure;}catch(Error failure){state.abortOperation(\"delete\");throw failure;}}\n")
                        .append("  public ").append(table.name("KeyTraversal")).append(" keys(){state.checkActive(\"keys\");return new ").append(table.name("KeyTraversal")).append("(this);}\n");
            } else {
                out.append("  public boolean containsKey(").append(key.primitive).append(" key){state.checkActive(\"containsKey\");return keySpace.contains(")
                        .append(key.keySpaceValue("key", "containsKey"))
                        .append(");}\n")
                        .append("  public int findIndex(").append(key.primitive).append(" key){state.checkActive(\"findIndex\");return keySpace.rowOf(")
                        .append(key.keySpaceValue("key", "findIndex"))
                        .append(");}\n")
                        .append("  public int requireIndex(").append(key.primitive).append(" key){state.checkActive(\"requireIndex\");return keyRow(key,\"requireIndex\");}\n")
                        .append("  public java.util.Optional<").append(table.carrierType).append("> find(").append(key.primitive).append(" key){state.checkActive(\"find\");int row=keySpace.rowOf(")
                        .append(key.keySpaceValue("key", "find"))
                        .append(");return materializeOptionalRow(row,runtimePlan().defaultMaterializationBudget(),\"find\",false);}\n")
                        .append("  public java.util.Optional<").append(table.carrierType).append("> find(").append(key.primitive).append(" key,MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");state.checkActive(\"find\");int row=keySpace.rowOf(")
                        .append(key.keySpaceValue("key", "find"))
                        .append(");return materializeOptionalRow(row,budget,\"find\",false);}\n")
                        .append("  public ").append(table.carrierType).append(" fetch(").append(key.primitive).append(" key){return materializeRequiredRow(keyRow(key,\"fetch\"),runtimePlan().defaultMaterializationBudget(),\"fetch\",false,TABLE);}\n")
                        .append("  public ").append(table.carrierType).append(" fetch(").append(key.primitive).append(" key,MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");return materializeRequiredRow(keyRow(key,\"fetch\"),budget,\"fetch\",false,TABLE);}\n")
                        .append("  public ").append(table.name("Mutator")).append(" mutate(").append(key.primitive).append(" key){return mutateAt(keyRow(key,\"mutate\"));}\n")
                        .append("  public void delete(").append(key.primitive).append(" key){ownership.preflightMutation(\"delete\");state.beginOperation(\"delete\");long scanned=0L;try{int row=keyRow(key,\"delete\");scanned=1L;int[] selected=preparePipelineScratch(1);selected[0]=row;removeSelected(selected,1,1L,\"delete\");state.endOperationSuccess(\"delete\",1L,1L,1L);}catch(SomaRuntimeException failure){state.endOperationFailure(\"delete\",scanned,0L,failure.code());throw failure;}catch(RuntimeException failure){state.abortOperation(\"delete\");throw failure;}catch(Error failure){state.abortOperation(\"delete\");throw failure;}}\n")
                        .append("  public ").append(table.name("KeyTraversal")).append(" keys(){state.checkActive(\"keys\");return new ").append(table.name("KeyTraversal")).append("(this);}\n");
            }
            appendValueKeyLeafLocators(out, key);
        }
        appendPackedTableTerminalExecutors(out, table);
        out.append("  public ").append(table.name("Mutator")).append(" mutateAt(int index){int current=state.checkRowIndex(index,\"mutateAt\");return new ").append(table.name("Mutator")).append("(this,current,structuralEpoch());}\n")
                .append("  public ").append(table.name("Scan")).append(" filter(").append(table.name("Scan")).append(".Predicate predicate){return ").append(table.name("Scan")).append(".packedFilter(this,predicate);}\n")
                .append("  public ").append(table.name("Scan")).append(" skip(long count){return ").append(table.name("Scan")).append(".packedSkip(this,count);}\n")
                .append("  public ").append(table.name("Scan")).append(" limit(long count){return ").append(table.name("Scan")).append(".packedLimit(this,count);}\n")
                .append("  public ").append(table.name("Scan")).append(" sorted(").append(table.name("Scan")).append(".Comparator comparator){return ").append(table.name("Scan")).append(".packedSorted(this,comparator);}\n")
                .append("  public long count(){state.beginOperation(\"scan.count\");try{long result=state.size();state.endOperationSuccess(\"scan.count\",result,result,0L);return result;}catch(SomaRuntimeException failure){state.endOperationFailure(\"scan.count\",0L,0L,failure.code());throw failure;}catch(RuntimeException failure){state.abortOperation(\"scan.count\");throw failure;}catch(Error failure){state.abortOperation(\"scan.count\");throw failure;}}\n")
                .append("  public boolean anyMatch(").append(table.name("Scan")).append(".Predicate predicate){return packedAnyMatch(this,predicate);}\n")
                .append("  public boolean noneMatch(").append(table.name("Scan")).append(".Predicate predicate){return packedNoneMatch(this,predicate);}\n")
                .append("  public void forEach(").append(table.name("Scan")).append(".Consumer consumer){packedForEach(this,consumer);}\n")
                .append("  public int findIndex(){state.beginOperation(\"scan.findIndex\");try{int result=state.size()==0?-1:0;long matched=result<0?0L:1L;state.endOperationSuccess(\"scan.findIndex\",matched,matched,0L);return result;}catch(SomaRuntimeException failure){state.endOperationFailure(\"scan.findIndex\",0L,0L,failure.code());throw failure;}catch(RuntimeException failure){state.abortOperation(\"scan.findIndex\");throw failure;}catch(Error failure){state.abortOperation(\"scan.findIndex\");throw failure;}}\n")
                .append("  public int requireIndex(){state.beginOperation(\"scan.requireIndex\");try{if(state.size()==0)throw RuntimeFailures.emptyResult(TABLE,\"scan.requireIndex\");state.endOperationSuccess(\"scan.requireIndex\",1L,1L,0L);return 0;}catch(SomaRuntimeException failure){state.endOperationFailure(\"scan.requireIndex\",0L,0L,failure.code());throw failure;}catch(RuntimeException failure){state.abortOperation(\"scan.requireIndex\");throw failure;}catch(Error failure){state.abortOperation(\"scan.requireIndex\");throw failure;}}\n")
                .append("  public java.util.Optional<").append(table.carrierType).append("> findFirst(){return packedFindFirst(this,runtimePlan().defaultMaterializationBudget());}\n")
                .append("  public java.util.Optional<").append(table.carrierType).append("> findFirst(MaterializationBudget budget){return packedFindFirst(this,budget);}\n")
                .append("  public ").append(table.carrierType).append(" firstOrThrow(){return packedFirstOrThrow(this,runtimePlan().defaultMaterializationBudget());}\n")
                .append("  public ").append(table.carrierType).append(" firstOrThrow(MaterializationBudget budget){return packedFirstOrThrow(this,budget);}\n")
                .append("  public java.util.List<").append(table.carrierType).append("> fetchAll(){return packedFetchAll(this,runtimePlan().defaultMaterializationBudget());}\n")
                .append("  public java.util.List<").append(table.carrierType).append("> fetchAll(MaterializationBudget budget){return packedFetchAll(this,budget);}\n")
                .append("  public IndexSnapshot indexSnapshot(){return packedIndexSnapshot(this);}\n")
                .append("  public void requireCurrent(IndexSnapshot snapshot){state.checkActive(\"requireCurrent\");if(snapshot==null)throw new NullPointerException(\"snapshot\");if(!IndexSnapshots.isOwnedBy(snapshot,indexSnapshotOwner))throw RuntimeFailures.indexSnapshotWrongTable(TABLE,\"requireCurrent\");long current=structuralEpoch();if(snapshot.structuralEpoch()!=current)throw RuntimeFailures.staleIndexSnapshot(TABLE,snapshot.structuralEpoch(),current,\"requireCurrent\");for(int i=0;i<snapshot.size();i++)state.checkRowIndex(snapshot.indexAt(i),\"requireCurrent\");}\n")
                .append("  public UpdateResult update(").append(table.name("Scan")).append(".Updater updater){return packedUpdate(this,updater);}\n")
                .append("  public RemoveResult remove(){return packedRemove(this);}\n");
        appendSelectorSources(out, table);
        for (FieldSpec field : table.fields) {
            String presence = field.optional ? field.javaName + "Presence" : "null";
            if (field.supportsColumnAccess()) {
                out.append("  public ").append(field.columnTraversalType()).append(' ')
                        .append(field.javaName).append("Values(){state.checkActive(")
                        .append(q(field.logicalName + ".values"))
                        .append(");return ")
                        .append(field.columnTraversalConstruction(presence)).append(";}\n")
                        .append("  public ").append(field.columnViewType()).append(' ')
                        .append(field.javaName).append("Column(){return ")
                        .append(field.columnViewConstruction(presence)).append(";}\n");
            }
            if (field.valueBacked()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    if (!leaf.supportsColumnAccess()) continue;
                    out.append("  public ").append(leaf.columnTraversalType()).append(' ')
                            .append(leaf.stem(field)).append("s(){state.checkActive(")
                            .append(q(field.logicalName + "." + leaf.logicalName + ".values"))
                            .append(");return ")
                            .append(leaf.columnTraversalConstruction(field, presence))
                            .append(";}\n")
                            .append("  public ").append(leaf.columnViewType()).append(' ')
                            .append(leaf.stem(field)).append("Column(){return ")
                            .append(leaf.columnViewConstruction(field, presence))
                            .append(";}\n");
                }
            }
        }
        if (table.keyed()) {
            out.append("  public TableStats statsSnapshot(){state.checkCallbackAccess(\"statsSnapshot\");TableStats base=TableStats.withKeySpace(state.statsSnapshot(subtreeChildInstanceCount(),subtreeDescendantRowCount()-state.size()),")
                    .append(q(table.keyField().keySpaceImplementation()))
                    .append(",keySpace.capacity(),keySpace.used(),keySpace.probeCount(),keySpace.collisionCount(),keySpace.rehashCount());return exactIndexStats(base);}\n")
                    .append("  public void resetStats(){ownership.preflightMutation(\"resetStats\");state.resetStats();keySpace.resetMetrics();resetExactIndexMetrics();}\n\n");
        } else {
            out.append("  public TableStats statsSnapshot(){state.checkCallbackAccess(\"statsSnapshot\");return exactIndexStats(state.statsSnapshot(subtreeChildInstanceCount(),subtreeDescendantRowCount()-state.size()));}\n  public void resetStats(){ownership.preflightMutation(\"resetStats\");state.resetStats();resetExactIndexMetrics();}\n\n");
        }
        out
                .append("  void begin(String operation){state.beginOperation(operation);}\n  void beginCallback(String callback){state.beginCallback(callback);}\n  void endCallback(String callback){state.endCallback(callback);}\n  void endSuccess(String operation,long scanned,long matched,long changed){state.endOperationSuccess(operation,scanned,matched,changed);}\n  void endFailure(String operation,long scanned,long matched,String code){state.endOperationFailure(operation,scanned,matched,code);}\n  void abort(String operation){state.abortOperation(operation);}\n  UpdateResult updateResult(long scanned,long matched,long changed){return state.updateResult(scanned,matched,changed);}\n  IndexSnapshot indexSnapshot(int[] indexes,int count){return IndexSnapshots.copyOf(indexSnapshotOwner,structuralEpoch(),indexes,count);}\n")
                .append("  void beginMaterialization(String operation,boolean nested){ownership.beginMaterialization(operation);try{if(nested)state.beginOperationMaterialization(operation);else state.beginMaterialization(operation);}catch(RuntimeException failure){ownership.endMaterialization();throw failure;}catch(Error failure){ownership.endMaterialization();throw failure;}}\n")
                .append("  void endMaterializationSuccess(MaterializationTracker tracker){try{state.endMaterializationSuccess(tracker);}finally{ownership.endMaterialization();}}\n")
                .append("  void endMaterializationFailure(MaterializationTracker tracker){try{state.endMaterializationFailure(tracker);}finally{ownership.endMaterialization();}}\n")
                .append("  void preflightMutation(String operation){ownership.preflightMutation(operation);}\n")
                .append("  private long operationScratchBytes(long pipeline,long sort){return pipeline>Long.MAX_VALUE-sort?Long.MAX_VALUE:pipeline+sort;}\n")
                .append("  private void requireOperationScratch(long pipeline,long sort,String operation){long bytes=operationScratchBytes(pipeline,sort),limit=runtimePlan().requireTable(TABLE).maximumOperationScratchBytes();if(bytes>limit)throw RuntimeFailures.memoryLimitExceeded(TABLE,operation,limit,bytes);state.preflightOperationScratch(bytes,operation);}\n")
                .append("  private void recordOperationScratch(){state.operationScratch(operationScratchBytes(pipelineScratch.retainedBytes(),sortScratch.retainedBytes()));}\n")
                .append("  int[] preparePipelineScratch(int required){if(required<0||required>size())throw RuntimeFailures.internalInvariant(\"pipeline_scratch_size\",TABLE,\"rows\");if(pipelineScratch.capacity()<required){requireOperationScratch(pipelineScratch.retainedBytesAfterEnsure(required),sortScratch.retainedBytes(),\"rows\");pipelineScratch.ensureCapacity(required);recordOperationScratch();}return pipelineScratch.prepare(required);}\n")
                .append("  int[] prepareSortScratch(int required){if(required<0||required>size())throw RuntimeFailures.internalInvariant(\"sort_scratch_size\",TABLE,\"scan.sort\");if(sortScratch.capacity()<required){requireOperationScratch(pipelineScratch.retainedBytes(),sortScratch.retainedBytesAfterEnsure(required),\"scan.sort\");sortScratch.ensureCapacity(required);recordOperationScratch();}return sortScratch.prepare(required);}\n")
                .append("  ").append(table.carrierType).append(" materializeRow(int row){return materializeRow(row,runtimePlan().defaultMaterializationBudget());}\n")
                .append("  ").append(table.carrierType).append(" materializeRow(int row,MaterializationBudget budget){return materializeRequiredRow(row,budget,\"fetchAt\",false,TABLE);}\n")
                .append("  ").append(table.carrierType).append(" materializeRequiredRow(int row,MaterializationBudget budget,String operation,boolean nested,String sourcePath){beginMaterialization(operation,nested);MaterializationTracker tracker=null;try{tracker=new MaterializationTracker(budget,TABLE);tracker.enterOwnership(this,TABLE);tracker.checkOwnershipDepth(0,TABLE);tracker.addTableInstances(1L,TABLE);if(row<0)throw RuntimeFailures.emptyResult(sourcePath,operation);tracker.addRows(1L,TABLE);accountRowRecursive(tracker,row,0,TABLE);tracker.exitOwnership(this,TABLE);MaterializationAllocation.preflight(operation,tracker.estimatedBytes(),TABLE);").append(table.carrierType).append(" result=carrierRecursive(row,0,TABLE);endMaterializationSuccess(tracker);return result;}catch(RuntimeException failure){endMaterializationFailure(tracker);throw failure;}catch(Error failure){endMaterializationFailure(tracker);throw failure;}}\n")
                .append("  java.util.Optional<").append(table.carrierType).append("> materializeOptionalRow(int row,MaterializationBudget budget,String operation,boolean nested){beginMaterialization(operation,nested);MaterializationTracker tracker=null;try{tracker=new MaterializationTracker(budget,TABLE);tracker.enterOwnership(this,TABLE);tracker.checkOwnershipDepth(0,TABLE);tracker.addTableInstances(1L,TABLE);if(row<0){tracker.addOptionalAllocation(false,TABLE);tracker.exitOwnership(this,TABLE);MaterializationAllocation.preflight(operation,tracker.estimatedBytes(),TABLE);java.util.Optional<").append(table.carrierType).append("> empty=java.util.Optional.empty();endMaterializationSuccess(tracker);return empty;}tracker.addRows(1L,TABLE);tracker.addOptionalAllocation(true,TABLE);accountRowRecursive(tracker,row,0,TABLE);tracker.exitOwnership(this,TABLE);MaterializationAllocation.preflight(operation,tracker.estimatedBytes(),TABLE);java.util.Optional<").append(table.carrierType).append("> result=java.util.Optional.of(carrierRecursive(row,0,TABLE));endMaterializationSuccess(tracker);return result;}catch(RuntimeException failure){endMaterializationFailure(tracker);throw failure;}catch(Error failure){endMaterializationFailure(tracker);throw failure;}}\n")
                .append("  List<").append(table.carrierType).append("> materializeRows(int[] rows,int count){return materializeRows(rows,count,runtimePlan().defaultMaterializationBudget());}\n")
                .append("  List<").append(table.carrierType).append("> materializeRows(int[] rows,int count,MaterializationBudget budget){return materializeRows(rows,count,budget,\"scan.fetchAll\",true);}\n")
                .append("  List<").append(table.carrierType).append("> materializeRows(int[] rows,int count,MaterializationBudget budget,String operation,boolean nested){beginMaterialization(operation,nested);MaterializationTracker tracker=null;try{tracker=new MaterializationTracker(budget,TABLE);tracker.enterOwnership(this,TABLE);tracker.checkOwnershipDepth(0,TABLE);tracker.addTableInstances(1L,TABLE);tracker.addRows(count,TABLE);tracker.addListAllocation(count,TABLE);for(int i=0;i<count;i++)accountRowRecursive(tracker,rows[i],0,TABLE);tracker.exitOwnership(this,TABLE);MaterializationAllocation.preflight(operation,tracker.estimatedBytes(),TABLE);List<").append(table.carrierType).append("> result=new ArrayList<").append(table.carrierType).append(">(count);for(int i=0;i<count;i++)result.add(carrierRecursive(rows[i],0,TABLE));endMaterializationSuccess(tracker);return result;}catch(RuntimeException failure){endMaterializationFailure(tracker);throw failure;}catch(Error failure){endMaterializationFailure(tracker);throw failure;}}\n");
        appendTableFieldAccess(out, table);
        appendMutatorCommit(out, table);
        appendUpdateScratch(out, table);
        appendRemove(out, table);
        DenseExactIndexSourceEmitter.appendRuntime(out, table);
        appendOwnedLifecycle(out, table);
        return out.append("}\n").toString();
    }

    private String childKeyEquality(ChildSpec child, String left, String right) {
        FieldSpec key = schemaTable(child.tableLogicalName).keyField();
        if (key.valueBacked()) {
            StringBuilder equality = new StringBuilder();
            for (int i = 0; i < key.valueLeaves.size(); i++) {
                if (i > 0) equality.append("&&");
                ValueLeafSpec leaf = key.valueLeaves.get(i);
                String leftLeaf = leaf.keyInputStorage(
                        key, left + "." + leaf.javaName, q("batch.add"));
                String rightLeaf = leaf.keyInputStorage(
                        key, right + "." + leaf.javaName, q("batch.add"));
                equality.append(leaf.keyEqual(key, leftLeaf, rightLeaf, q("batch.add")));
            }
            return equality.toString();
        }
        if (key.enumType != null) return left + "==" + right;
        if ("java.lang.String".equals(key.primitive)) return left + ".equals(" + right + ")";
        String unboxed = left + "." + key.unboxMethod() + "()";
        if ("float".equals(key.primitive)) {
            return "KeyCanonicalization.strictFloatKeyBits(TABLE," + q(key.logicalName)
                    + "," + unboxed + ",\"batch.add\")==KeyCanonicalization.strictFloatKeyBits(TABLE,"
                    + q(key.logicalName) + "," + right + ",\"batch.add\")";
        }
        if ("double".equals(key.primitive)) {
            return "KeyCanonicalization.strictDoubleKeyBits(TABLE," + q(key.logicalName)
                    + "," + unboxed + ",\"batch.add\")==KeyCanonicalization.strictDoubleKeyBits(TABLE,"
                    + q(key.logicalName) + "," + right + ",\"batch.add\")";
        }
        return unboxed + "==" + right;
    }

    private void appendSchemaPlanRuntime(SourceBuilder out) {
        out.append("  private static RuntimePlan createDefaultRuntimePlan(){RuntimePlan.Builder builder=RuntimePlan.builder(")
                .append(q(schemaHash))
                .append(",RuntimeCompatibility.RUNTIME_COMPATIBILITY,RuntimeCompatibility.GENERATED_PROTOCOL,RuntimeCompatibility.PLAN_PROTOCOL,RuntimeCompatibility.ALLOCATION_ESTIMATOR);");
        for (TableSpec candidate : schemaTables) {
            out.append("builder.addTable(TablePlan.builder(")
                    .append(q(candidate.logicalName))
                    .append(",RuntimeCompatibility.DENSE_ALGORITHM).initialCapacity(")
                    .append(candidate.defaultCapacity)
                    .append(").growthRatio(3,2).maximumUpdateScratchBytes(268435456L)")
                    .append(".keySpaceStrategy(")
                    .append(q(candidate.keyed()
                            ? candidate.keyField().keySpaceImplementation() : "none"))
                    .append(')');
            if (!candidate.selectors.isEmpty()) {
                out.append(".accessStrategy(RuntimeCompatibility.PRIMITIVE_EXACT_HASH)");
            }
            out.append(".build());");
        }
        for (TableSpec owner : schemaTables) {
            for (ChildSpec child : owner.children) {
                TableSpec childTable = schemaTable(child.tableLogicalName);
                int capacity = child.initialCapacity > 0
                        ? child.initialCapacity : childTable.defaultCapacity;
                out.append("builder.addChild(ChildPlan.create(")
                        .append(q(owner.logicalName)).append(',')
                        .append(q(child.logicalName)).append(',')
                        .append(q(child.tableLogicalName)).append(',')
                        .append(capacity).append("));");
            }
        }
        int childCount = 0;
        for (TableSpec owner : schemaTables) childCount += owner.children.size();
        out.append("return builder.build();}\n")
                .append("  private static void verifySchemaPlan(RuntimePlan plan){if(plan.tables().size()!=")
                .append(schemaTables.size()).append("||plan.children().size()!=")
                .append(childCount)
                .append(")throw RuntimeFailures.invalidRuntimePlan(TABLE,\"schema aggregate table/child plan completeness\");");
        for (TableSpec candidate : schemaTables) {
            out.append("RuntimeCompatibility.verifyAccess(plan.requireTable(")
                    .append(q(candidate.logicalName)).append("),")
                    .append(candidate.selectors.isEmpty() ? "false" : "true")
                    .append(");");
            out.append("RuntimeCompatibility.verifyKeySpace(plan.requireTable(")
                    .append(q(candidate.logicalName)).append("),")
                    .append(q(candidate.keyed()
                            ? candidate.keyField().keySpaceImplementation() : "none"))
                    .append(");");
        }
        for (TableSpec owner : schemaTables) {
            for (ChildSpec child : owner.children) {
                out.append("if(!plan.requireChild(")
                        .append(q(owner.logicalName)).append(',')
                        .append(q(child.logicalName)).append(").childTable().equals(")
                        .append(q(child.tableLogicalName))
                        .append("))throw RuntimeFailures.invalidRuntimePlan(TABLE,\"child table identity mismatch\");");
            }
        }
        out.append("}\n");
    }

    private void appendChildStructuralMethods(SourceBuilder out, TableSpec table) {
        String batch = table.name("Batch");
        String validateAppendKeys = table.keyed() ? "validateAppendKeys(batch);" : "";
        String prepareAppendKeys = table.keyed()
                ? "ensureAppendKeyCapacity(count,\"addBatch\");" : "";
        String appendKeys = table.keyed()
                ? "appendKeys(batch,start);keysAppended=true;" : "";
        String rollbackAppendKeys = table.keyed()
                ? "if(keysAppended)rollbackAppendKeys(batch,start);" : "";
        String replacementKeys = table.keyed()
                ? table.keyField().keySpaceType() + " stagedKeys=stageReplacementKeys(batch);"
                    + "stagedKeys.addMetrics(keySpace.probeCount(),keySpace.collisionCount(),keySpace.rehashCount());"
                : "";
        String publishReplacementKeys = table.keyed()
                ? "publishKeySpace(stagedKeys,\"replaceAll\");stagedKeys=null;" : "";
        String replacementKeyBytes = table.keyed()
                ? "stagedKeys.retainedBytes()" : "0L";
        String discardReplacementKeys = table.keyed()
                ? "discardKeySpace(stagedKeys,\"replaceAll\");" : "";
        String clearKeys = table.keyed() ? "keySpace.clear();" : "";
        String releaseKeys = table.keyed() ? "keySpace.releaseStorage();" : "";
        out.append("  public void addBatch(").append(batch)
                .append(" batch){if(batch==null)throw new NullPointerException(\"batch\");ownership.preflightMutation(\"addBatch\");state.prepareAppend(0);int count=batch.size();if(count==0)return;validateSelectorAppend(batch);validateUniqueAppend(batch);")
                .append(validateAppendKeys)
                .append("ChildStage staged=stageChildren(batch,\"addBatch\");int start=-1;boolean keysAppended=false;try{")
                .append(prepareAppendKeys)
                .append("ensureExactIndexAppendCapacity(size()+count,batch,\"addBatch\");start=state.prepareAppend(count);copyBatch(batch,0,start,count);copyChildStage(batch,staged,start,count);")
                .append(appendKeys)
                .append("linkExactIndexRows(start,count);state.commitAppend(start,count);staged.publish();}catch(RuntimeException failure){")
                .append(rollbackAppendKeys)
                .append("if(start>=0)clearColumns(start,start+count);staged.discard();throw failure;}catch(Error failure){")
                .append(rollbackAppendKeys)
                .append("if(start>=0)clearColumns(start,start+count);staged.discard();throw failure;}}\n")
                .append("  public void replaceAll(").append(batch)
                .append(" batch){if(batch==null)throw new NullPointerException(\"batch\");ownership.preflightMutation(\"replaceAll\");state.prepareReplace(0);int count=batch.size();validateSelectorReplacement(batch);validateUniqueReplacement(batch);")
                .append(replacementKeys)
                .append("ExactIndexStage stagedIndexes=stageExactIndexes(batch,\"replaceAll\");ChildStage staged=stageChildren(batch,\"replaceAll\");try{")
                .append("state.preflightReplaceStorage(count,").append(replacementKeyBytes)
                .append(",stagedIndexes.retainedBytes(),\"replaceAll\");")
                .append("int previous=state.prepareReplace(count);")
                .append("beginRetireRows(0,previous,\"replaceAll\",true);releaseRetired(false,\"replaceAll\");copyBatch(batch,0,0,count);copyChildStage(batch,staged,0,count);if(previous>count)clearColumns(count,previous);")
                .append(publishReplacementKeys)
                .append("stagedIndexes.publish(\"replaceAll\");stagedIndexes=null;staged.publish();state.commitReplace(previous,count);}catch(RuntimeException failure){")
                .append(discardReplacementKeys)
                .append("if(stagedIndexes!=null)stagedIndexes.discard(\"replaceAll\");staged.discard();throw failure;}catch(Error failure){")
                .append(discardReplacementKeys)
                .append("if(stagedIndexes!=null)stagedIndexes.discard(\"replaceAll\");staged.discard();throw failure;}}\n")
                .append("  public void clear(){ownership.preflightMutation(\"clear\");int previous=state.prepareClear();beginRetireRows(0,previous,\"clear\",true);releaseRetired(false,\"clear\");clearColumns(0,previous);")
                .append(clearKeys)
                .append("clearExactIndexes();state.commitClear(previous);}\n")
                .append("  public void release(){state.rejectOwnedRelease(\"release\");ownership.preflightMutation(\"release\");int previous=state.prepareRelease();if(previous>=0){beginRetireRows(0,previous,\"release\",false);releaseRetired(true,\"release\");clearColumns(0,previous);")
                .append(releaseKeys)
                .append("releaseExactIndexes(\"release\");releaseRetainedScratch();state.commitRelease(previous);ownership.releaseStorage();}}\n\n");
    }

    private void appendChildOwnershipRuntime(SourceBuilder out, TableSpec table) {
        String batch = table.name("Batch");
        out.append("  private ChildStage stageChildren(").append(batch)
                .append(" batch,String operation){return new ChildStage(batch,operation);}\n")
                .append("  private void copyChildStage(").append(batch)
                .append(" batch,ChildStage staged,int target,int count){for(int i=0;i<count;i++){int row=target+i;ownerTokenColumn.set(row,staged.ownerTokens[i]);");
        for (ChildSpec child : table.children) {
            out.append(child.javaName).append("HandleColumn.set(row,staged.")
                    .append(child.javaName).append("Handles[i]);");
            if (child.optional) {
                out.append("if(batch.").append(child.javaName)
                        .append("Present(i))").append(child.javaName)
                        .append("ChildPresence.setPresent(row);else ")
                        .append(child.javaName).append("ChildPresence.clearPresent(row);");
            }
        }
        out.append("}}\n")
                .append("  private long childStageScratchBytes(int count,String operation){long widths=")
                .append(1 + table.children.size())
                .append("L;if(count<0||count>Long.MAX_VALUE/8L/widths)throw RuntimeFailures.memoryLimitExceeded(TABLE,operation,runtimePlan().requireTable(TABLE).maximumBulkScratchBytes(),Long.MAX_VALUE);return 8L*widths*(long)count;}\n")
                .append("  private final class ChildStage{long[] ownerTokens=new long[0];");
        for (ChildSpec child : table.children) {
            out.append("long[] ").append(child.javaName).append("Handles=new long[0];");
        }
        out.append("final String operation;final long scratchBytes;boolean finished,scratchReserved;ChildStage(").append(batch)
                .append(" batch,String operation){this.operation=operation;int count=batch.size();scratchBytes=childStageScratchBytes(count,operation);state.reserveBulkScratch(scratchBytes,operation);scratchReserved=true;try{ownerTokens=new long[count];");
        for (ChildSpec child : table.children) {
            out.append(child.javaName).append("Handles=new long[count];");
        }
        out.append("for(int row=0;row<count;row++){long owner=ownership.newOwnerToken();ownerTokens[row]=owner;");
        for (ChildSpec child : table.children) {
            out.append("if(batch.").append(child.javaName).append("Present(row)&&batch.")
                    .append(child.javaName).append("Batch(row).size()>0){")
                    .append(child.tableType()).append(" instance=")
                    .append(child.tableType()).append(".createOwned(runtimePlan(),ownership,")
                    .append(child.javaName).append("TablePlan,TABLE+\".\"+")
                    .append(q(child.logicalName)).append(");try{instance.addBatch(batch.")
                    .append(child.javaName).append("Batch(row));instance.preflightOwnedRelease(false);")
                    .append(child.javaName).append("Handles[row]=ownership.stage(owner,")
                    .append(q(child.logicalName)).append(",TABLE+\".\"+")
                    .append(q(child.logicalName)).append(",instance,instance.ownedLifecycle());}")
                    .append("catch(RuntimeException failure){instance.releaseOwnedSubtree(false);throw failure;}catch(Error failure){instance.releaseOwnedSubtree(false);throw failure;}}");
        }
        out.append("}}catch(RuntimeException failure){discard();throw failure;}catch(Error failure){discard();throw failure;}}")
                .append("void publish(){if(finished)return;");
        for (ChildSpec child : table.children) {
            out.append("for(int i=0;i<").append(child.javaName)
                    .append("Handles.length;i++)if(").append(child.javaName)
                    .append("Handles[i]!=0L)ownership.publish(").append(child.javaName)
                    .append("Handles[i],ownerTokens[i],").append(q(child.logicalName)).append(");");
        }
        out.append("finished=true;finishScratch();}void discard(){if(finished)return;try{");
        for (ChildSpec child : table.children) {
            out.append("for(int i=0;i<").append(child.javaName)
                    .append("Handles.length;i++)if(").append(child.javaName)
                    .append("Handles[i]!=0L)ownership.discardStaged(")
                    .append(child.javaName).append("Handles[i],ownerTokens[i],")
                    .append(q(child.logicalName)).append(");");
        }
        out.append("}finally{finished=true;finishScratch();}}void finishScratch(){if(scratchReserved){state.releaseBulkScratch(scratchBytes,operation);scratchReserved=false;}}}\n")
                .append("  private void beginRetireRows(int from,int to,String operation,boolean pin){long expected=(long)(to-from)*")
                .append(table.children.size())
                .append("L;ownership.beginCascade(expected,runtimePlan().requireTable(TABLE).maximumBulkScratchBytes(),TABLE,operation);try{for(int row=from;row<to;row++){long owner=ownerTokenColumn.get(row);");
        for (ChildSpec child : table.children) {
            out.append("ownership.collectCascade(").append(child.javaName)
                    .append("HandleColumn.get(row),owner,")
                    .append(q(child.logicalName)).append(",pin,operation);");
        }
        out.append("}}catch(RuntimeException failure){ownership.cancelCascade(operation);throw failure;}catch(Error failure){ownership.cancelCascade(operation);throw failure;}}\n")
                .append("  private void beginRetireSelection(int[] selected,int count,String operation,boolean pin){long expected=(long)count*")
                .append(table.children.size())
                .append("L;ownership.beginCascade(expected,runtimePlan().requireTable(TABLE).maximumBulkScratchBytes(),TABLE,operation);try{for(int i=0;i<count;i++){int row=selected[i];long owner=ownerTokenColumn.get(row);");
        for (ChildSpec child : table.children) {
            out.append("ownership.collectCascade(").append(child.javaName)
                    .append("HandleColumn.get(row),owner,")
                    .append(q(child.logicalName)).append(",pin,operation);");
        }
        out.append("}}catch(RuntimeException failure){ownership.cancelCascade(operation);throw failure;}catch(Error failure){ownership.cancelCascade(operation);throw failure;}}\n")
                .append("  private void releaseRetired(boolean aggregateRelease,String operation){ownership.commitCascade(aggregateRelease,operation);}\n");

        for (ChildSpec child : table.children) {
            appendChildFacadeRuntime(out, table, child);
        }
    }

    private void appendChildFacadeRuntime(
            SourceBuilder out, TableSpec table, ChildSpec child) {
        String locatorType;
        String locatorName;
        String rowExpression;
        if (table.keyed()) {
            locatorType = table.keyField().primitive;
            locatorName = "parentKey";
            rowExpression = "keyRow(parentKey,operation)";
        } else {
            locatorType = "int";
            locatorName = "index";
            rowExpression = "state.checkRowIndex(index,operation)";
        }
        String c = cap(child.javaName);
        out.append("  private ").append(child.tableType()).append(' ')
                .append(child.javaName).append("AtRow(int row,boolean create,String operation){state.checkRowIndex(row,operation);");
        if (child.optional) {
            out.append("if(!").append(child.javaName)
                    .append("ChildPresence.isPresent(row))throw RuntimeFailures.optionalAbsent(TABLE,")
                    .append(q(child.logicalName)).append(",operation);");
        }
        out.append("long owner=ownerTokenColumn.get(row),handle=")
                .append(child.javaName).append("HandleColumn.get(row);if(handle==0L&&create){ownership.preflightMutation(operation);")
                .append(child.tableType()).append(" instance=")
                .append(child.tableType()).append(".createOwned(runtimePlan(),ownership,")
                .append(child.javaName).append("TablePlan,TABLE+\".\"+")
                .append(q(child.logicalName)).append(");try{handle=ownership.stage(owner,")
                .append(q(child.logicalName)).append(",TABLE+\".\"+")
                .append(q(child.logicalName)).append(",instance,instance.ownedLifecycle());}catch(RuntimeException failure){instance.releaseOwnedSubtree(false);throw failure;}catch(Error failure){instance.releaseOwnedSubtree(false);throw failure;}ownership.publish(handle,owner,")
                .append(q(child.logicalName)).append(");")
                .append(child.javaName).append("HandleColumn.set(row,handle);}return(")
                .append(child.tableType()).append(")ownership.resolve(handle,owner,")
                .append(q(child.logicalName)).append(",operation);}\n");
        if (!child.optional) {
            out.append("  public ").append(child.tableType()).append(' ')
                    .append(child.javaName).append('(').append(locatorType).append(' ')
                    .append(locatorName).append("){String operation=")
                    .append(q(child.javaName)).append(";int row=").append(rowExpression)
                    .append(";return ").append(child.javaName)
                    .append("AtRow(row,true,operation);}\n");
        } else {
            out.append("  public boolean ").append(child.javaName).append("Present(")
                    .append(locatorType).append(' ').append(locatorName)
                    .append("){String operation=").append(q(child.javaName + "Present"))
                    .append(";int row=").append(rowExpression).append(";return ")
                    .append(child.javaName).append("ChildPresence.isPresent(row);}\n")
                    .append("  public ").append(child.tableType()).append(' ')
                    .append(child.javaName).append("OrThrow(").append(locatorType).append(' ')
                    .append(locatorName).append("){String operation=")
                    .append(q(child.javaName + "OrThrow")).append(";int row=")
                    .append(rowExpression).append(";return ").append(child.javaName)
                    .append("AtRow(row,true,operation);}\n")
                    .append("  public ").append(child.tableType()).append(" ensure")
                    .append(c).append('(').append(locatorType).append(' ')
                    .append(locatorName).append("){String operation=")
                    .append(q("ensure" + c)).append(";int row=").append(rowExpression)
                    .append(";if(!").append(child.javaName)
                    .append("ChildPresence.isPresent(row)){ownership.preflightMutation(operation);state.prepareChildChange(operation);long owner=ownerTokenColumn.get(row);")
                    .append(child.tableType()).append(" instance=").append(child.tableType())
                    .append(".createOwned(runtimePlan(),ownership,")
                    .append(child.javaName).append("TablePlan,TABLE+\".\"+")
                    .append(q(child.logicalName)).append(");long staged=0L;try{staged=ownership.stage(owner,")
                    .append(q(child.logicalName)).append(",TABLE+\".\"+")
                    .append(q(child.logicalName)).append(",instance,instance.ownedLifecycle());")
                    .append(child.javaName).append("HandleColumn.set(row,staged);")
                    .append(child.javaName).append("ChildPresence.setPresent(row);ownership.publish(staged,owner,")
                    .append(q(child.logicalName)).append(");state.commitChildChange(operation);return instance;}catch(RuntimeException failure){if(staged!=0L)ownership.discardStaged(staged,owner,")
                    .append(q(child.logicalName)).append(");else instance.releaseOwnedSubtree(false);throw failure;}catch(Error failure){if(staged!=0L)ownership.discardStaged(staged,owner,")
                    .append(q(child.logicalName)).append(");else instance.releaseOwnedSubtree(false);throw failure;}}return ")
                    .append(child.javaName).append("AtRow(row,true,operation);}\n");
        }
        out.append("  public void replace").append(c).append('(')
                .append(locatorType).append(' ').append(locatorName).append(',')
                .append(child.batchType()).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");String operation=")
                .append(q("replace" + c)).append(";int row=").append(rowExpression)
                .append(";ownership.preflightMutation(operation);state.prepareChildChange(operation);long owner=ownerTokenColumn.get(row),old=")
                .append(child.javaName).append("HandleColumn.get(row);ownership.preflightPinned(old,owner,")
                .append(q(child.logicalName)).append(",operation);")
                .append(child.tableType()).append(" instance=").append(child.tableType())
                .append(".createOwned(runtimePlan(),ownership,")
                .append(child.javaName).append("TablePlan,TABLE+\".\"+")
                .append(q(child.logicalName)).append(");long staged=0L;boolean switched=false;try{instance.addBatch(batch.copy());instance.preflightOwnedRelease(false);staged=ownership.stage(owner,")
                .append(q(child.logicalName)).append(",TABLE+\".\"+")
                .append(q(child.logicalName)).append(",instance,instance.ownedLifecycle());")
                .append("ownership.preflightRelease(old,owner,").append(q(child.logicalName))
                .append(",operation,false);")
                .append(child.javaName).append("HandleColumn.set(row,staged);");
        if (child.optional) out.append(child.javaName).append("ChildPresence.setPresent(row);");
        out.append("ownership.publish(staged,owner,").append(q(child.logicalName))
                .append(");switched=true;ownership.releasePreflighted(old,owner,")
                .append(q(child.logicalName)).append(",operation,false);state.commitChildChange(operation);}catch(RuntimeException failure){if(!switched){if(staged!=0L)ownership.discardStaged(staged,owner,")
                .append(q(child.logicalName)).append(");else instance.releaseOwnedSubtree(false);}throw failure;}catch(Error failure){if(!switched){if(staged!=0L)ownership.discardStaged(staged,owner,")
                .append(q(child.logicalName)).append(");else instance.releaseOwnedSubtree(false);}throw failure;}}\n");
        if (child.optional) {
            out.append("  public void unset").append(c).append('(')
                    .append(locatorType).append(' ').append(locatorName)
                    .append("){String operation=").append(q("unset" + c))
                    .append(";int row=").append(rowExpression).append(";ownership.preflightMutation(operation);state.prepareChildChange(operation);if(!")
                    .append(child.javaName).append("ChildPresence.isPresent(row))return;long owner=ownerTokenColumn.get(row),old=")
                    .append(child.javaName).append("HandleColumn.get(row);ownership.preflightPinned(old,owner,")
                    .append(q(child.logicalName)).append(",operation);")
                    .append("ownership.release(old,owner,").append(q(child.logicalName)).append(",operation,false);")
                    .append(child.javaName).append("HandleColumn.set(row,0L);")
                    .append(child.javaName).append("ChildPresence.clearPresent(row);state.commitChildChange(operation);}\n");
        }
    }

    private void appendOwnedLifecycle(SourceBuilder out, TableSpec table) {
        out.append("  boolean hasPinnedSubtree(){if(state.hasPinnedBorrow())return true;");
        if (!table.children.isEmpty()) {
            out.append("for(int row=0;row<state.size();row++){long owner=ownerTokenColumn.get(row);");
            for (ChildSpec child : table.children) {
                out.append("long ").append(child.javaName).append("Handle=")
                        .append(child.javaName).append("HandleColumn.get(row);if(")
                        .append(child.javaName).append("Handle!=0L&&ownership.hasPinned(")
                        .append(child.javaName).append("Handle,owner,")
                        .append(q(child.logicalName)).append(",\"ownership.pin\"))return true;");
            }
            out.append("}");
        }
        out.append("return false;}\n")
                .append("  void preflightOwnedRelease(boolean aggregateRelease){if(!owned)throw RuntimeFailures.internalInvariant(\"root_owned_release\",TABLE,\"ownership.release\");if(state.isReleased())return;state.preflightOwnedRelease(\"ownership.release\");int previous=state.size();");
        if (!table.children.isEmpty()) {
            out.append("beginRetireRows(0,previous,\"ownership.release\",false);ownership.preflightCascade(aggregateRelease,\"ownership.release\");");
        }
        out.append("}\n")
                .append("  void releaseOwnedSubtree(boolean aggregateRelease){if(!owned)throw RuntimeFailures.internalInvariant(\"root_owned_release\",TABLE,\"ownership.release\");if(state.isReleased())return;int previous=state.size();");
        if (!table.children.isEmpty()) {
            out.append("beginRetireRows(0,previous,\"ownership.release\",false);releaseRetired(aggregateRelease,\"ownership.release\");");
        }
        out.append("clearColumns(0,previous);");
        if (table.keyed()) out.append("keySpace.releaseStorage();");
        out.append("releaseExactIndexes(\"ownership.release\");releaseRetainedScratch();state.commitOwnedRelease(aggregateRelease);");
        out.append("}\n")
                .append("  long subtreeChildInstanceCount(){long result=0L;");
        if (!table.children.isEmpty()) {
            out.append("for(int row=0;row<state.size();row++){long owner=ownerTokenColumn.get(row);");
            for (ChildSpec child : table.children) {
                out.append("result+=ownership.childInstanceCount(")
                        .append(child.javaName).append("HandleColumn.get(row),owner,")
                        .append(q(child.logicalName)).append(");");
            }
            out.append("}");
        }
        out.append("return result;}\n")
                .append("  long subtreeDescendantRowCount(){long result=state.size();");
        if (!table.children.isEmpty()) {
            out.append("for(int row=0;row<state.size();row++){long owner=ownerTokenColumn.get(row);");
            for (ChildSpec child : table.children) {
                out.append("result+=ownership.descendantRowCount(")
                        .append(child.javaName).append("HandleColumn.get(row),owner,")
                        .append(q(child.logicalName)).append(");");
            }
            out.append("}");
        }
        out.append("return result;}\n")
                .append("  OwnedChildTable ownedLifecycle(){final ").append(table.name("Table"))
                .append(" self=this;return new OwnedChildTable(){public boolean hasPinnedSubtree(){return self.hasPinnedSubtree();}public void preflightOwnedRelease(boolean aggregateRelease){self.preflightOwnedRelease(aggregateRelease);}public void releaseOwnedSubtree(boolean aggregateRelease){self.releaseOwnedSubtree(aggregateRelease);}public long subtreeChildInstanceCount(){return self.subtreeChildInstanceCount();}public long subtreeDescendantRowCount(){return self.subtreeDescendantRowCount();}};}\n");
    }

    private void appendMaterializationRuntime(SourceBuilder out, TableSpec table) {
        String carrier = table.carrierType;
        out.append("\n  public ").append(carrier)
                .append(" fetchAt(int index){return fetchAt(index,runtimePlan().defaultMaterializationBudget());}\n")
                .append("  public ").append(carrier)
                .append(" fetchAt(int index,MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");int row=state.checkRowIndex(index,\"fetchAt\");return materializeRequiredRow(row,budget,\"fetchAt\",false,TABLE);}\n");
        if (table.keyed()) {
            FieldSpec key = table.keyField();
            out.append("  public java.util.Map<").append(key.boxed).append(',')
                    .append(carrier).append("> materialize(){return materialize(runtimePlan().defaultMaterializationBudget());}\n")
                    .append("  public java.util.Map<").append(key.boxed).append(',')
                    .append(carrier).append("> materialize(MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");beginMaterialization(\"materialize\",false);MaterializationTracker tracker=null;try{tracker=new MaterializationTracker(budget,TABLE);accountOwnedAll(tracker,0,TABLE);MaterializationAllocation.preflight(\"materialize\",tracker.estimatedBytes(),TABLE);java.util.Map<").append(key.boxed).append(',').append(carrier).append("> result=materializeOwnedMap(0,TABLE);endMaterializationSuccess(tracker);return result;}catch(RuntimeException failure){endMaterializationFailure(tracker);throw failure;}catch(Error failure){endMaterializationFailure(tracker);throw failure;}}\n")
                    .append("  void accountOwnedAll(MaterializationTracker tracker,int depth,String path){tracker.enterOwnership(this,path);tracker.checkOwnershipDepth(depth,path);tracker.addTableInstances(1L,path);int count=size();tracker.addRows(count,path);tracker.addMapAllocation(count,")
                    .append(materializedKeyBoxes(key) ? "true" : "false")
                    .append(",path);for(int row=0;row<count;row++)accountRowRecursive(tracker,row,depth,path);tracker.exitOwnership(this,path);}\n")
                    .append("  java.util.Map<").append(key.boxed).append(',').append(carrier)
                    .append("> materializeOwnedMap(int depth,String path){int count=size();java.util.Map<")
                    .append(key.boxed).append(',').append(carrier)
                    .append("> result=new java.util.HashMap<").append(key.boxed).append(',')
                    .append(carrier).append(">(count>=805306368?Integer.MAX_VALUE:Math.max(1,(int)(((long)count*4L)/3L+1L)));for(int row=0;row<count;row++){")
                    .append(carrier).append(" value=carrierRecursive(row,depth,path);result.put(value.")
                    .append(key.javaName).append(",value);}return result;}\n")
                    .append("  java.util.List<").append(key.boxed).append("> materializeKeys(MaterializationBudget budget,String operation,boolean nested){beginMaterialization(operation,nested);MaterializationTracker tracker=null;try{tracker=new MaterializationTracker(budget,TABLE);tracker.enterOwnership(this,TABLE);tracker.checkOwnershipDepth(0,TABLE);tracker.addTableInstances(1L,TABLE);int count=size();tracker.addRows(count,TABLE);tracker.addListAllocation(count,TABLE);for(int row=0;row<count;row++){tracker.addLeafValues(")
                    .append(materializedKeyLeafCount(key)).append("L,TABLE);");
            long keyBytes = materializedKeyEstimatedBytes(key);
            if (keyBytes != 0L) {
                out.append("tracker.addEstimatedBytes(").append(keyBytes)
                        .append("L,TABLE);");
            }
            out.append("}tracker.exitOwnership(this,TABLE);MaterializationAllocation.preflight(operation,tracker.estimatedBytes(),TABLE);java.util.List<")
                    .append(key.boxed).append("> result=new java.util.ArrayList<")
                    .append(key.boxed).append(">(count);for(int row=0;row<count;row++)result.add(")
                    .append(key.boxValue(key.javaName + "Value(row)"))
                    .append(");endMaterializationSuccess(tracker);return result;}catch(RuntimeException failure){endMaterializationFailure(tracker);throw failure;}catch(Error failure){endMaterializationFailure(tracker);throw failure;}}\n")
                    .append("  java.util.Optional<").append(key.boxed).append("> materializeOptionalKey(MaterializationBudget budget,String operation,boolean nested){beginMaterialization(operation,nested);MaterializationTracker tracker=null;try{tracker=new MaterializationTracker(budget,TABLE);tracker.enterOwnership(this,TABLE);tracker.checkOwnershipDepth(0,TABLE);tracker.addTableInstances(1L,TABLE);if(size()==0){tracker.addOptionalAllocation(false,TABLE);tracker.exitOwnership(this,TABLE);MaterializationAllocation.preflight(operation,tracker.estimatedBytes(),TABLE);java.util.Optional<")
                    .append(key.boxed).append("> empty=java.util.Optional.empty();endMaterializationSuccess(tracker);return empty;}tracker.addRows(1L,TABLE);tracker.addOptionalAllocation(true,TABLE);tracker.addLeafValues(")
                    .append(materializedKeyLeafCount(key)).append("L,TABLE);");
            if (keyBytes != 0L) {
                out.append("tracker.addEstimatedBytes(").append(keyBytes)
                        .append("L,TABLE);");
            }
            out.append("tracker.exitOwnership(this,TABLE);MaterializationAllocation.preflight(operation,tracker.estimatedBytes(),TABLE);java.util.Optional<")
                    .append(key.boxed).append("> result=java.util.Optional.of(")
                    .append(key.boxValue(key.javaName + "Value(0)"))
                    .append(");endMaterializationSuccess(tracker);return result;}catch(RuntimeException failure){endMaterializationFailure(tracker);throw failure;}catch(Error failure){endMaterializationFailure(tracker);throw failure;}}\n")
                    .append("  ").append(key.boxed).append(" materializeRequiredKey(MaterializationBudget budget,String operation,boolean nested){beginMaterialization(operation,nested);MaterializationTracker tracker=null;try{tracker=new MaterializationTracker(budget,TABLE);tracker.enterOwnership(this,TABLE);tracker.checkOwnershipDepth(0,TABLE);tracker.addTableInstances(1L,TABLE);if(size()==0)throw RuntimeFailures.emptyResult(TABLE,operation);tracker.addRows(1L,TABLE);tracker.addLeafValues(")
                    .append(materializedKeyLeafCount(key)).append("L,TABLE);");
            if (keyBytes != 0L) {
                out.append("tracker.addEstimatedBytes(").append(keyBytes)
                        .append("L,TABLE);");
            }
            out.append("tracker.exitOwnership(this,TABLE);MaterializationAllocation.preflight(operation,tracker.estimatedBytes(),TABLE);")
                    .append(key.boxed).append(" result=")
                    .append(key.boxValue(key.javaName + "Value(0)"))
                    .append(";endMaterializationSuccess(tracker);return result;}catch(RuntimeException failure){endMaterializationFailure(tracker);throw failure;}catch(Error failure){endMaterializationFailure(tracker);throw failure;}}\n");
        } else {
            out.append("  public java.util.List<").append(carrier)
                    .append("> materialize(){return materialize(runtimePlan().defaultMaterializationBudget());}\n")
                    .append("  public java.util.List<").append(carrier)
                    .append("> materialize(MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");beginMaterialization(\"materialize\",false);MaterializationTracker tracker=null;try{tracker=new MaterializationTracker(budget,TABLE);accountOwnedAll(tracker,0,TABLE);MaterializationAllocation.preflight(\"materialize\",tracker.estimatedBytes(),TABLE);java.util.List<").append(carrier).append("> result=materializeOwnedList(0,TABLE);endMaterializationSuccess(tracker);return result;}catch(RuntimeException failure){endMaterializationFailure(tracker);throw failure;}catch(Error failure){endMaterializationFailure(tracker);throw failure;}}\n")
                    .append("  void accountOwnedAll(MaterializationTracker tracker,int depth,String path){tracker.enterOwnership(this,path);tracker.checkOwnershipDepth(depth,path);tracker.addTableInstances(1L,path);int count=size();tracker.addRows(count,path);tracker.addListAllocation(count,path);for(int row=0;row<count;row++)accountRowRecursive(tracker,row,depth,path);tracker.exitOwnership(this,path);}\n")
                    .append("  java.util.List<").append(carrier)
                    .append("> materializeOwnedList(int depth,String path){int count=size();java.util.List<")
                    .append(carrier).append("> result=new java.util.ArrayList<")
                    .append(carrier).append(">(count);for(int row=0;row<count;row++)result.add(carrierRecursive(row,depth,path));return result;}\n");
        }
        out.append("  private void accountRowRecursive(MaterializationTracker tracker,int row,int depth,String path){long leaves=0L,bytes=")
                .append(16L + 8L * (table.fields.size() + table.children.size())).append("L;");
        for (FieldSpec field : table.fields) {
            if (field.optional) {
                out.append("if(").append(field.javaName)
                        .append("Present(row)){leaves+=")
                        .append(field.flattenedValueStorage()
                                ? field.valueLeaves.size() : 1)
                        .append("L;bytes+=")
                        .append(field.flattenedValueStorage()
                                ? valueObjectEstimatedBytes(field)
                                : optionalMaterializedAllocation(field))
                        .append("L;}");
            } else if (field.flattenedValueStorage()) {
                out.append("leaves+=").append(field.valueLeaves.size()).append("L;bytes+=")
                        .append(valueObjectEstimatedBytes(field)).append("L;");
            } else {
                out.append("leaves++;");
            }
        }
        out.append("tracker.addLeafValues(leaves,path);tracker.addEstimatedBytes(bytes,path);");
        for (ChildSpec child : table.children) {
            String present = child.optional
                    ? child.javaName + "ChildPresence.isPresent(row)" : "true";
            out.append("if(").append(present).append("){String childPath=path+\"[].\"+")
                    .append(q(child.logicalName)).append(";long owner=ownerTokenColumn.get(row),handle=")
                    .append(child.javaName).append("HandleColumn.get(row);if(handle==0L){tracker.checkOwnershipDepth(depth+1,childPath);tracker.addTableInstances(1L,childPath);");
            if (child.keyed()) out.append("tracker.addMapAllocation(0,false,childPath);");
            else out.append("tracker.addListAllocation(0,childPath);");
            out.append("}else{").append(child.tableType()).append(" child=(")
                    .append(child.tableType()).append(")ownership.resolve(handle,owner,")
                    .append(q(child.logicalName)).append(",\"materialize\");child.accountOwnedAll(tracker,depth+1,childPath);}} ");
        }
        out.append("}\n  private ").append(carrier).append(" carrierRecursive(int row,int depth,String path){")
                .append(carrier).append(" value=new ").append(carrier).append("();");
        for (FieldSpec field : table.fields) {
            if (field.optional) {
                out.append("value.").append(field.javaName).append('=')
                        .append(field.javaName).append("Present(row)?")
                        .append(field.boxValue(field.javaName + "Value(row)"))
                        .append(":null;");
            } else {
                out.append("value.").append(field.javaName).append('=')
                        .append(field.javaName).append("Value(row);");
            }
        }
        for (ChildSpec child : table.children) {
            String present = child.optional
                    ? child.javaName + "ChildPresence.isPresent(row)" : "true";
            out.append("if(!(").append(present).append(")){value.")
                    .append(child.javaName).append("=null;}else{String childPath=path+\"[].\"+")
                    .append(q(child.logicalName)).append(";long owner=ownerTokenColumn.get(row),handle=")
                    .append(child.javaName).append("HandleColumn.get(row);if(handle==0L)value.")
                    .append(child.javaName).append("=new ");
            if (child.keyed()) {
                out.append("java.util.HashMap<").append(child.keyMaterializedType)
                        .append(',').append(child.rowJavaType).append(">()");
            } else {
                out.append("java.util.ArrayList<").append(child.rowJavaType).append(">()");
            }
            out.append(";else{").append(child.tableType()).append(" child=(")
                    .append(child.tableType()).append(")ownership.resolve(handle,owner,")
                    .append(q(child.logicalName)).append(",\"materialize\");value.")
                    .append(child.javaName).append("=child.")
                    .append(child.keyed() ? "materializeOwnedMap" : "materializeOwnedList")
                    .append("(depth+1,childPath);}}");
        }
        out.append("return value;}\n\n");
    }

    private static boolean materializedKeyBoxes(FieldSpec key) {
        return key.enumType == null && key.valueType == null
                && !"java.lang.String".equals(key.primitive);
    }

    private static int materializedKeyLeafCount(FieldSpec key) {
        return key.valueType == null ? 1 : key.valueLeaves.size();
    }

    private static long materializedKeyEstimatedBytes(FieldSpec key) {
        if (key.valueType != null) return valueObjectEstimatedBytes(key);
        return materializedKeyBoxes(key) ? 16L : 0L;
    }

    private static long valueObjectEstimatedBytes(FieldSpec field) {
        long result = 0L;
        for (ValueGroupSpec group : field.valueGroups) {
            long raw = 16L + 8L * group.directFieldCount;
            result += (raw + 7L) & ~7L;
        }
        return result;
    }

    private static long optionalMaterializedAllocation(FieldSpec field) {
        return field.enumType == null && field.valueType == null
                && !"java.lang.String".equals(field.primitive) ? 16L : 0L;
    }

    private TableSpec schemaTable(String logicalName) {
        for (TableSpec table : schemaTables) {
            if (table.logicalName.equals(logicalName)) return table;
        }
        throw new IllegalStateException("missing generated child table: " + logicalName);
    }

    private void appendSelectorSources(SourceBuilder out, TableSpec table) {
        String scan = table.name("Scan");
        for (int i = 0; i < table.selectors.size(); i++) {
            SelectorSpec selector = table.selectors.get(i);
            List<SelectorParameter> parameters = selectorParameters(table, selector);
            String suffix = selectorSuffix(selector);
            String method = "scanBy" + suffix;
            out.append("  public ").append(scan).append(' ').append(method).append('(');
            appendSelectorParameters(out, parameters);
            out.append("){state.checkActive(").append(q(method)).append(");");
            for (int leafIndex = 0; leafIndex < selector.leaves.size(); leafIndex++) {
                SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
                out.append(leaf.storageType).append(" sourceLeaf").append(leafIndex)
                        .append('=').append(selectorParameterArgument(
                                parameters, leafIndex, leaf, method)).append(';');
            }
            out.append("long hash=1469598103934665603L;");
            for (int leafIndex = 0; leafIndex < selector.leaves.size(); leafIndex++) {
                SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
                out.append("hash=(hash^").append(selectorHashBits(
                        leaf.storageType, "sourceLeaf" + leafIndex))
                        .append(")*1099511628211L;");
            }
            out.append("return ").append(scan).append(".exact").append(i)
                    .append("(this,hash");
            appendSourceLeafArguments(out, selector.leaves.size());
            out.append(");}\n");

            if ("unique".equals(selector.kind)) {
                appendUniquePointMethods(out, table, selector, i, parameters, suffix);
            }
            appendExactSourceTableMethods(out, table, selector, i);
        }
    }

    private void appendExactSourceTableMethods(
            SourceBuilder out, TableSpec table, SelectorSpec selector, int index) {
        out.append("  int selector").append(index)
                .append("SourceGroup(long hash");
        for (int leafIndex = 0; leafIndex < selector.leaves.size(); leafIndex++) {
            out.append(',').append(selector.leaves.get(leafIndex).storageType)
                    .append(" sourceLeaf").append(leafIndex);
        }
        out.append("){int group=selector").append(index)
                .append("Index.firstGroup(hash);while(group>=0&&!selector")
                .append(index).append("SourceEqual(selector").append(index)
                .append("Index.representativeRow(group)");
        appendSourceLeafArguments(out, selector.leaves.size());
        out.append(")){selector").append(index)
                .append("Index.recordCollision();group=selector").append(index)
                .append("Index.nextHashGroup(group);}return group;}\n  private boolean selector")
                .append(index).append("SourceEqual(int row");
        for (int leafIndex = 0; leafIndex < selector.leaves.size(); leafIndex++) {
            out.append(',').append(selector.leaves.get(leafIndex).storageType)
                    .append(" sourceLeaf").append(leafIndex);
        }
        out.append("){return ");
        for (int leafIndex = 0; leafIndex < selector.leaves.size(); leafIndex++) {
            if (leafIndex > 0) out.append("&&");
            SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
            SelectorBinding binding = selectorBinding(table, leaf);
            out.append('(').append(compareExpression(
                    leaf.storageType,
                    binding.columnExpression("row"),
                    "sourceLeaf" + leafIndex)).append(")==0");
        }
        out.append(";}\n  int selector").append(index)
                .append("SourceFirst(int group){return selector").append(index)
                .append("Index.firstRow(group);}\n  int selector").append(index)
                .append("SourceNext(int row){return selector").append(index)
                .append("Index.nextRow(row);}\n  int selector").append(index)
                .append("SourceSize(int group){return selector").append(index)
                .append("Index.groupSize(group);}\n");
    }

    private void appendUniquePointMethods(
            SourceBuilder out,
            TableSpec table,
            SelectorSpec selector,
            int index,
            List<SelectorParameter> parameters,
            String suffix) {
        String contains = "containsBy" + suffix;
        String findIndex = "findIndexBy" + suffix;
        String requireIndex = "requireIndexBy" + suffix;
        String find = "findBy" + suffix;
        String fetch = "fetchBy" + suffix;
        String mutate = "mutateBy" + suffix;
        String delete = "deleteBy" + suffix;

        out.append("  public boolean ").append(contains).append('(');
        appendSelectorParameters(out, parameters);
        out.append("){state.checkActive(").append(q(contains)).append(");return selector")
                .append(index).append("Group(");
        appendSelectorValueArguments(out, parameters.size());
        if (!parameters.isEmpty()) out.append(',');
        out.append(q(contains)).append(")>=0;}\n");

        out.append("  public int ").append(findIndex).append('(');
        appendSelectorParameters(out, parameters);
        out.append("){state.checkActive(").append(q(findIndex)).append(");int group=selector")
                .append(index).append("Group(");
        appendSelectorValueArguments(out, parameters.size());
        if (!parameters.isEmpty()) out.append(',');
        out.append(q(findIndex)).append(");return group<0?-1:selector")
                .append(index).append("Index.firstRow(group);}\n");

        out.append("  public int ").append(requireIndex).append('(');
        appendSelectorParameters(out, parameters);
        out.append("){state.checkActive(").append(q(requireIndex)).append(");int group=selector")
                .append(index).append("Group(");
        appendSelectorValueArguments(out, parameters.size());
        if (!parameters.isEmpty()) out.append(',');
        out.append(q(requireIndex)).append(");if(group<0)throw RuntimeFailures.emptyResult(TABLE+")
                .append(q("." + selector.name)).append(',').append(q(requireIndex))
                .append(");return selector").append(index).append("Index.firstRow(group);}\n");

        out.append("  public java.util.Optional<").append(table.carrierType).append("> ")
                .append(find).append('(');
        appendSelectorParameters(out, parameters);
        out.append("){return ").append(find).append('(');
        appendSelectorValueArguments(out, parameters.size());
        if (!parameters.isEmpty()) out.append(',');
        out.append("runtimePlan().defaultMaterializationBudget());}\n  public java.util.Optional<")
                .append(table.carrierType).append("> ").append(find).append('(');
        appendSelectorParameters(out, parameters);
        if (!parameters.isEmpty()) out.append(',');
        out.append("MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");int row=")
                .append(findIndex).append('(');
        appendSelectorValueArguments(out, parameters.size());
        out.append(");return materializeOptionalRow(row,budget,").append(q(find))
                .append(",false);}\n");

        out.append("  public ").append(table.carrierType).append(' ').append(fetch).append('(');
        appendSelectorParameters(out, parameters);
        out.append("){return ").append(fetch).append('(');
        appendSelectorValueArguments(out, parameters.size());
        if (!parameters.isEmpty()) out.append(',');
        out.append("runtimePlan().defaultMaterializationBudget());}\n  public ")
                .append(table.carrierType).append(' ').append(fetch).append('(');
        appendSelectorParameters(out, parameters);
        if (!parameters.isEmpty()) out.append(',');
        out.append("MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");int row=")
                .append(requireIndex).append('(');
        appendSelectorValueArguments(out, parameters.size());
        out.append(");return materializeRequiredRow(row,budget,").append(q(fetch))
                .append(",false,TABLE+").append(q("." + selector.name)).append(");}\n");

        out.append("  public ").append(table.name("Mutator")).append(' ').append(mutate).append('(');
        appendSelectorParameters(out, parameters);
        out.append("){return mutateAt(").append(requireIndex).append('(');
        appendSelectorValueArguments(out, parameters.size());
        out.append("));}\n");

        out.append("  public void ").append(delete).append('(');
        appendSelectorParameters(out, parameters);
        out.append("){ownership.preflightMutation(").append(q(delete))
                .append(");state.beginOperation(").append(q(delete))
                .append(");long scanned=0L;try{int group=selector").append(index).append("Group(");
        appendSelectorValueArguments(out, parameters.size());
        if (!parameters.isEmpty()) out.append(',');
        out.append(q(delete)).append(");if(group<0)throw RuntimeFailures.emptyResult(TABLE+")
                .append(q("." + selector.name)).append(',').append(q(delete))
                .append(");int row=selector").append(index)
                .append("Index.firstRow(group);scanned=1L;int[] selected=preparePipelineScratch(1);selected[0]=row;removeSelected(selected,1,1L,")
                .append(q(delete)).append(");state.endOperationSuccess(").append(q(delete))
                .append(",1L,1L,1L);}catch(SomaRuntimeException failure){state.endOperationFailure(")
                .append(q(delete)).append(",scanned,0L,failure.code());throw failure;}catch(RuntimeException failure){state.abortOperation(")
                .append(q(delete)).append(");throw failure;}catch(Error failure){state.abortOperation(")
                .append(q(delete)).append(");throw failure;}}\n");
    }


    static boolean hasMutableSelectors(TableSpec table) {
        for (SelectorSpec selector : table.selectors) {
            if (selectorCanChange(table, selector)) return true;
        }
        return false;
    }

    static boolean selectorCanChange(TableSpec table, SelectorSpec selector) {
        for (SelectorLeafSpec leaf : selector.leaves) {
            if (!selectorBinding(table, leaf).field.key) return true;
        }
        return false;
    }

    static void appendNoMutableSelectorChange(
            SourceBuilder out, TableSpec table, String prefix) {
        boolean emitted = false;
        for (int i = 0; i < table.selectors.size(); i++) {
            if (!selectorCanChange(table, table.selectors.get(i))) continue;
            if (emitted) out.append("&&");
            emitted = true;
            out.append('!').append(prefix).append(i);
        }
        if (!emitted) out.append("true");
    }

    static void appendSelectorHash(
            SourceBuilder out,
            TableSpec table,
            SelectorSpec selector,
            String row,
            String batch,
            List<SelectorParameter> parameters,
            String operation) {
        for (int leafIndex = 0; leafIndex < selector.leaves.size(); leafIndex++) {
            SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
            SelectorBinding binding = selectorBinding(table, leaf);
            String value;
            if (batch != null) {
                value = selectorBatchValue(binding, leaf, batch, row, operation);
            } else if (parameters != null) {
                value = selectorParameterArgument(
                        parameters, leafIndex, leaf, operation);
            } else {
                value = selectorStorageValue(
                        leaf, binding.columnExpression(row), operation);
            }
            out.append("hash=(hash^").append(selectorHashBits(leaf.storageType, value))
                    .append(")*1099511628211L;");
        }
    }

    static void appendSelectorBatchEquality(
            SourceBuilder out,
            TableSpec table,
            SelectorSpec selector,
            String batch,
            String left,
            String right,
            String operation) {
        for (int leafIndex = 0; leafIndex < selector.leaves.size(); leafIndex++) {
            if (leafIndex > 0) out.append("&&");
            SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
            SelectorBinding binding = selectorBinding(table, leaf);
            String leftValue = selectorBatchValue(
                    binding, leaf, batch, left, operation);
            String rightValue = selectorBatchValue(
                    binding, leaf, batch, right, operation);
            out.append('(').append(compareExpression(
                    leaf.storageType, leftValue, rightValue)).append(")==0");
        }
    }

    static void appendSelectorRowBatchEquality(
            SourceBuilder out,
            TableSpec table,
            SelectorSpec selector,
            String row,
            String batch,
            String batchRow,
            String operation) {
        for (int leafIndex = 0; leafIndex < selector.leaves.size(); leafIndex++) {
            if (leafIndex > 0) out.append("&&");
            SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
            SelectorBinding binding = selectorBinding(table, leaf);
            String rowValue = selectorStorageValue(
                    leaf, binding.columnExpression(row), operation);
            String batchValue = selectorBatchValue(
                    binding, leaf, batch, batchRow, operation);
            out.append('(').append(compareExpression(
                    leaf.storageType, rowValue, batchValue)).append(")==0");
        }
    }

    static void appendSelectorValueArguments(
            SourceBuilder out, int parameterCount) {
        for (int index = 0; index < parameterCount; index++) {
            if (index > 0) out.append(',');
            out.append("value").append(index);
        }
    }

    static void appendExactIndexReplacementRuntime(
            SourceBuilder out, TableSpec table) {
        if (table.selectors.isEmpty()) {
            out.append("  private ExactIndexStage stageExactIndexes(")
                    .append(table.name("Batch"))
                    .append(" batch,String operation){return new ExactIndexStage();}\n")
                    .append("  private final class ExactIndexStage{long retainedBytes(){return 0L;}void publish(String operation){}void discard(String operation){}}\n");
            return;
        }
        out.append("  private ExactIndexStage stageExactIndexes(")
                .append(table.name("Batch"))
                .append(" batch,String operation){long scratch=0L;");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("scratch=addExactMetric(scratch,GroupedExactIndex.estimatedRetainedBytes(batch.size(),batch.size()));");
        }
        out.append("state.reserveBulkScratch(scratch,operation);ExactIndexStage stage=new ExactIndexStage(scratch);try{");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("stage.selector").append(i).append("=buildSelector")
                    .append(i).append("Index(batch,operation);");
        }
        out.append("if(stage.retainedBytes()>scratch)throw RuntimeFailures.internalInvariant(\"exact_index_estimator\",TABLE,operation);return stage;}catch(RuntimeException failure){stage.discard(operation);throw failure;}catch(Error failure){stage.discard(operation);throw failure;}}\n")
                .append("  private final class ExactIndexStage{private long scratchBytes;private boolean consumed;");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("private GroupedExactIndex selector").append(i).append(';');
        }
        out.append("ExactIndexStage(long scratchBytes){this.scratchBytes=scratchBytes;}long retainedBytes(){long value=0L;");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("if(selector").append(i)
                    .append("!=null)value=addExactMetric(value,selector")
                    .append(i).append(".retainedBytes());");
        }
        out.append("return value;}void publish(String operation){if(consumed)throw RuntimeFailures.internalInvariant(\"exact_index_stage_consumed\",TABLE,operation);long previous=exactIndexRetainedBytes(),proposed=retainedBytes();state.preflightExactIndexStorage(proposed,operation);state.releaseBulkScratch(scratchBytes,operation);scratchBytes=0L;state.commitExactIndexStorage(previous,proposed,operation);");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("GroupedExactIndex previous").append(i).append("=selector")
                    .append(i).append("Index;selector").append(i)
                    .append("Index=selector").append(i).append(";selector")
                    .append(i).append("=null;previous").append(i).append(".release();");
        }
        out.append("consumed=true;}void discard(String operation){if(consumed)return;");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("if(selector").append(i).append("!=null){selector")
                    .append(i).append(".release();selector").append(i).append("=null;}");
        }
        out.append("if(scratchBytes!=0L){state.releaseBulkScratch(scratchBytes,operation);scratchBytes=0L;}consumed=true;}}\n");
    }

    static void appendSelectorChangeRuntime(
            SourceBuilder out, TableSpec table, SelectorSpec selector, int index) {
        String mutator = table.name("Mutator");
        out.append("  private boolean selector").append(index)
                .append("ChangedByMutator(int target,").append(mutator)
                .append(" mutation){return ");
        appendSelectorChangedExpression(
                out, table, selector, "target", "mutation", null, "mutator.commit");
        out.append(";}\n  private boolean selector").append(index)
                .append("ChangedByUpdateRow(int target,int scratch){return ");
        appendSelectorChangedExpression(
                out, table, selector, "target", null, "scratch", "scan.update");
        out.append(";}\n  private boolean selector").append(index)
                .append("ChangedByUpdate(int[] selected,int count){for(int scratch=0;scratch<count;scratch++){int target=selected[scratch];if(selector")
                .append(index)
                .append("ChangedByUpdateRow(target,scratch))return true;}return false;}\n");
        if (!"unique".equals(selector.kind)) return;
        out.append("  private int compareSelector").append(index)
                .append("RowToMutator(int row,int target,").append(mutator)
                .append(" mutation){");
        for (int leafIndex = 0; leafIndex < selector.leaves.size(); leafIndex++) {
            SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
            SelectorBinding binding = selectorBinding(table, leaf);
            String left = selectorStorageValue(
                    leaf, binding.columnExpression("row"), "mutator.commit");
            String right = binding.field.key
                    ? selectorStorageValue(
                            leaf, binding.columnExpression("target"), "mutator.commit")
                    : selectorMutatorValueOrLive(
                            binding, leaf, "mutation", "target", "mutator.commit");
            out.append("int compare").append(leafIndex).append('=')
                    .append(compareExpression(leaf.storageType, left, right)).append(';')
                    .append("if(compare").append(leafIndex).append("!=0)return ")
                    .append("DESC".equals(leaf.direction) ? "-compare" : "compare")
                    .append(leafIndex).append(';');
        }
        out.append("return 0;}\n");
    }

    private static void appendSelectorChangedExpression(
            SourceBuilder out, TableSpec table, SelectorSpec selector,
            String target, String mutation, String scratch, String operation) {
        boolean emitted = false;
        for (SelectorLeafSpec leaf : selector.leaves) {
            SelectorBinding binding = selectorBinding(table, leaf);
            if (binding.field.key) continue;
            if (emitted) out.append("||");
            emitted = true;
            String live = selectorStorageValue(
                    leaf, binding.columnExpression(target), operation);
            String candidate = mutation != null
                    ? selectorMutatorValueOrLive(
                            binding, leaf, mutation, target, operation)
                    : selectorUpdateValue(table, binding, leaf, scratch, operation);
            out.append('(').append(compareExpression(
                    leaf.storageType, live, candidate)).append(")!=0");
        }
        if (!emitted) out.append("false");
    }

    static void appendUniqueValidationRuntime(SourceBuilder out, TableSpec table) {
        String batch = table.name("Batch");
        String mutator = table.name("Mutator");
        out.append("  private void validateSelectorAppend(").append(batch).append(" batch){for(int row=0;row<batch.size();row++){");
        appendSelectorStrictValidation(out, table, "Append", "row", "addBatch");
        out.append("}}\n  private void validateSelectorReplacement(").append(batch)
                .append(" batch){for(int row=0;row<batch.size();row++){");
        appendSelectorStrictValidation(out, table, "Replacement", "row", "replaceAll");
        out.append("}}\n  private void validateSelectorMutator(").append(mutator).append(" mutation){");
        appendSelectorStrictValidation(out, table, "MutatorOnly", "0", "mutator.commit");
        out.append("}\n  private void validateSelectorUpdate(int count){for(int row=0;row<count;row++){");
        appendSelectorStrictValidation(out, table, "UpdateOnly", "row", "scan.update");
        out.append("}}\n  private void validateUniqueAppend(").append(batch).append(" batch){");
        for (int i = 0; i < table.selectors.size(); i++) {
            if (!"unique".equals(table.selectors.get(i).kind)) continue;
            appendUniqueProbeLoop(out, table.selectors.get(i), i, "Append",
                    "size()+batch.size()", "candidate", "batch", "addBatch");
        }
        out.append("}\n  private void validateUniqueReplacement(").append(batch).append(" batch){");
        for (int i = 0; i < table.selectors.size(); i++) {
            if (!"unique".equals(table.selectors.get(i).kind)) continue;
            appendUniqueProbeLoop(out, table.selectors.get(i), i, "Replacement",
                    "batch.size()", "candidate", "batch", "replaceAll");
        }
        out.append("}\n  private void validateUniqueMutator(int target,").append(mutator)
                .append(" mutation){");
        for (int i = 0; i < table.selectors.size(); i++) {
            if (!"unique".equals(table.selectors.get(i).kind)) continue;
            SelectorSpec selector = table.selectors.get(i);
            out.append("if(selector").append(i)
                    .append("ChangedByMutator(target,mutation)){long hash=selector")
                    .append(i).append("HashMutator(target,mutation);int group=selector")
                    .append(i).append("Index.firstGroup(hash);while(group>=0&&compareSelector")
                    .append(i).append("RowToMutator(selector").append(i)
                    .append("Index.representativeRow(group),target,mutation)!=0){selector")
                    .append(i).append("Index.recordCollision();group=selector")
                    .append(i).append("Index.nextHashGroup(group);}if(group>=0){int conflict=selector")
                    .append(i).append("Index.firstRow(group);if(conflict!=target)throw RuntimeFailures.uniqueConstraintViolation(TABLE,")
                    .append(q(selector.name)).append(",conflict,target,\"mutator.commit\");}}");
        }
        out.append("}\n  private void validateUniqueUpdate(int[] selected,int selectedCount){");
        boolean unique = false;
        for (SelectorSpec selector : table.selectors) {
            if ("unique".equals(selector.kind)) unique = true;
        }
        if (unique) {
            out.append("int changedUniqueCount=0;");
            for (int i = 0; i < table.selectors.size(); i++) {
                if (!"unique".equals(table.selectors.get(i).kind)) continue;
                out.append("boolean unique").append(i)
                        .append("Changed=selector").append(i)
                        .append("ChangedByUpdate(selected,selectedCount);if(unique")
                        .append(i).append("Changed)changedUniqueCount++;");
            }
            out.append("if(changedUniqueCount==0)return;int[] lookup=prepareCandidateScratch();long retained=candidateScratch.retainedBytes()+updateBytes(updateScratchCapacity);long unit=HashCompositeKeySpace.estimatedPeakBytes(size());long uniqueBytes=unit>Long.MAX_VALUE/(long)changedUniqueCount?Long.MAX_VALUE:unit*(long)changedUniqueCount;long required=uniqueBytes==Long.MAX_VALUE||retained>Long.MAX_VALUE-uniqueBytes?Long.MAX_VALUE:retained+uniqueBytes;long limit=runtimePlan().requireTable(TABLE).maximumUpdateScratchBytes();if(required>limit)throw RuntimeFailures.memoryLimitExceeded(TABLE,\"scan.update\",limit,required);state.updateScratch(required,required);try{Arrays.fill(lookup,0,size(),-1);for(int index=0;index<selectedCount;index++)lookup[selected[index]]=index;");
            for (int i = 0; i < table.selectors.size(); i++) {
                if (!"unique".equals(table.selectors.get(i).kind)) continue;
                out.append("if(unique").append(i).append("Changed)");
                appendUniqueProbeLoop(out, table.selectors.get(i), i, "Update",
                        "size()", "candidate", "lookup", "scan.update");
            }
            out.append("}finally{state.updateScratch(retained,required);}");
        }
        out.append("}\n");

        for (int i = 0; i < table.selectors.size(); i++) {
            SelectorSpec selector = table.selectors.get(i);
            if (!"unique".equals(selector.kind)) continue;
            appendUniqueModeMethods(out, table, selector, i, "Append", batch + " batch", "addBatch");
            appendUniqueModeMethods(out, table, selector, i, "Replacement", batch + " batch", "replaceAll");
            appendUniqueModeMethods(out, table, selector, i, "Update", "int[] lookup", "scan.update");
        }
    }

    private static void appendSelectorStrictValidation(
            SourceBuilder out, TableSpec table, String mode,
            String row, String operation) {
        java.util.Set<String> emitted = new java.util.LinkedHashSet<String>();
        for (SelectorSpec selector : table.selectors) {
            for (SelectorLeafSpec leaf : selector.leaves) {
                if (!("float".equals(leaf.storageType) || "double".equals(leaf.storageType))
                        || !emitted.add(leaf.path)) continue;
                SelectorBinding binding = selectorBinding(table, leaf);
                String value;
                String guard = null;
                if ("Append".equals(mode) || "Replacement".equals(mode)) {
                    value = selectorBatchValue(binding, leaf, "batch", row, operation);
                } else if ("MutatorOnly".equals(mode)) {
                    if (binding.field.key) continue;
                    guard = "mutation." + binding.field.javaName + "Touched()";
                    value = selectorMutatorValue(binding, leaf, "mutation", operation);
                } else {
                    if (binding.field.key) continue;
                    value = selectorUpdateValue(table, binding, leaf, row, operation);
                }
                if (guard != null) out.append("if(").append(guard).append("){\n");
                out.append(value).append(';');
                if (guard != null) out.append('}');
            }
        }
    }

    private static void appendUniqueProbeLoop(
            SourceBuilder out, SelectorSpec selector, int index, String mode,
            String count, String candidate, String extraArguments, String operation) {
        String suffix = index + mode;
        boolean bulkAdmission = "Append".equals(mode) || "Replacement".equals(mode);
        out.append("{int uniqueCount=").append(count).append(';');
        if (bulkAdmission) {
            out.append("long uniqueScratch=HashCompositeKeySpace.estimatedPeakBytes(uniqueCount);state.reserveBulkScratch(uniqueScratch,")
                    .append(q(operation))
                    .append(");HashCompositeKeySpace uniqueSpace=null;try{uniqueSpace=new HashCompositeKeySpace(uniqueCount);");
        } else {
            out.append("HashCompositeKeySpace uniqueSpace=new HashCompositeKeySpace(uniqueCount);");
        }
        out.append("for(int ")
                .append(candidate).append("=0;").append(candidate).append("<uniqueCount;")
                .append(candidate).append("++){uniqueSpace.ensureInsertCapacity();long uniqueHash=uniqueHash")
                .append(suffix).append('(').append(candidate);
        if (!extraArguments.isEmpty()) out.append(',').append(extraArguments);
        out.append(");int uniqueSlot=uniqueSpace.firstSlot(uniqueHash);while(!uniqueSpace.isEmpty(uniqueSlot)){if(uniqueSpace.isLive(uniqueSlot)&&uniqueSpace.hashAt(uniqueSlot)==uniqueHash&&uniqueEqual")
                .append(suffix).append("(uniqueSpace.rowAt(uniqueSlot),").append(candidate);
        if (!extraArguments.isEmpty()) out.append(',').append(extraArguments);
        out.append("))throw RuntimeFailures.uniqueConstraintViolation(TABLE,")
                .append(q(selector.name)).append(",uniqueSpace.rowAt(uniqueSlot),")
                .append(candidate).append(',').append(q(operation))
                .append(");uniqueSlot=uniqueSpace.nextSlot(uniqueSlot);}uniqueSpace.putAt(uniqueSlot,uniqueHash,")
                .append(candidate).append(");}");
        if (bulkAdmission) {
            out.append("}finally{if(uniqueSpace!=null)uniqueSpace.releaseStorage();state.releaseBulkScratch(uniqueScratch,")
                    .append(q(operation)).append(");}} ");
        } else {
            out.append("} ");
        }
    }

    private static void appendUniqueModeMethods(
            SourceBuilder out, TableSpec table, SelectorSpec selector, int index,
            String mode, String extraParameters, String operation) {
        String suffix = index + mode;
        out.append("  private long uniqueHash").append(suffix).append("(int row");
        if (!extraParameters.isEmpty()) out.append(',').append(extraParameters);
        out.append("){long hash=1469598103934665603L;");
        for (SelectorLeafSpec leaf : selector.leaves) {
            String value = uniqueModeValue(table, leaf, mode, "row", operation);
            out.append("hash=(hash^").append(selectorHashBits(leaf.storageType, value))
                    .append(")*1099511628211L;");
        }
        out.append("return hash;}\n  private boolean uniqueEqual").append(suffix)
                .append("(int left,int right");
        if (!extraParameters.isEmpty()) out.append(',').append(extraParameters);
        out.append("){return ");
        for (int leafIndex = 0; leafIndex < selector.leaves.size(); leafIndex++) {
            if (leafIndex > 0) out.append("&&");
            SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
            String left = uniqueModeValue(table, leaf, mode, "left", operation);
            String right = uniqueModeValue(table, leaf, mode, "right", operation);
            out.append('(').append(compareExpression(leaf.storageType, left, right)).append(")==0");
        }
        out.append(";}\n");
    }

    private static String uniqueModeValue(
            TableSpec table, SelectorLeafSpec leaf, String mode,
            String row, String operation) {
        SelectorBinding binding = selectorBinding(table, leaf);
        String live = selectorStorageValue(leaf, binding.columnExpression(row), operation);
        if ("Append".equals(mode)) {
            String fromBatch = selectorBatchValue(binding, leaf, "batch", row + "-size()", operation);
            return "(" + row + "<size()?" + live + ":" + fromBatch + ")";
        }
        if ("Replacement".equals(mode)) {
            return selectorBatchValue(binding, leaf, "batch", row, operation);
        }
        if ("Mutator".equals(mode)) {
            if (binding.field.key) return live;
            String mutated = selectorMutatorValueOrLive(
                    binding, leaf, "mutation", row, operation);
            return "(" + row + "==target?" + mutated + ":" + live + ")";
        }
        if ("Update".equals(mode)) {
            if (binding.field.key) return live;
            String updated = selectorUpdateValue(
                    table, binding, leaf, "lookup[" + row + "]", operation);
            return "(lookup[" + row + "]>=0?" + updated + ":" + live + ")";
        }
        throw new IllegalStateException("unsupported unique validation mode: " + mode);
    }

    private static String selectorBatchValue(
            SelectorBinding binding, SelectorLeafSpec leaf, String batch,
            String row, String operation) {
        String value = binding.valueLeaf == null
                ? batch + "." + binding.field.javaName + "StorageValue(" + row + ")"
                : batch + "." + binding.valueLeaf.physicalName(binding.field)
                        + "StorageValue(" + row + ")";
        return selectorStorageValue(leaf, value, operation);
    }

    private static String selectorMutatorValue(
            SelectorBinding binding, SelectorLeafSpec leaf,
            String mutation, String operation) {
        String value = mutation + "." + binding.field.javaName + "Value()";
        if (binding.valueLeaf != null) value += "." + binding.valueLeaf.javaName;
        if (binding.valueLeaf != null) {
            value = binding.valueLeaf.storageValue(binding.field, value, operation);
        } else {
            value = binding.field.storageValue(value, operation);
        }
        return selectorStorageValue(leaf, value, operation);
    }

    static String selectorMutatorValueOrLive(
            SelectorBinding binding, SelectorLeafSpec leaf,
            String mutation, String target, String operation) {
        String live = selectorStorageValue(
                leaf, binding.columnExpression(target), operation);
        String staged = selectorMutatorValue(binding, leaf, mutation, operation);
        return "(" + mutation + "." + binding.field.javaName + "Touched()?"
                + staged + ":" + live + ")";
    }

    private static String selectorUpdateValue(
            TableSpec table, SelectorBinding binding, SelectorLeafSpec leaf,
            String scratch, String operation) {
        int fieldIndex = fieldIndex(table, binding.field);
        if (binding.valueLeaf != null && binding.field.flattenedValueStorage()) {
            return selectorStorageValue(leaf,
                    updateScratch(fieldIndex, leafIndex(binding.field, binding.valueLeaf))
                            + "[" + scratch + "]",
                    operation);
        }
        String value = updateScratch(fieldIndex) + "[" + scratch + "]";
        if (binding.valueLeaf != null) value += "." + binding.valueLeaf.javaName;
        if (binding.valueLeaf != null) {
            value = binding.valueLeaf.storageValue(binding.field, value, operation);
        } else if (binding.field.enumType == null) {
            value = binding.field.storageValue(value, operation);
        }
        return selectorStorageValue(leaf, value, operation);
    }

    static String selectorStorageValue(
            SelectorLeafSpec leaf, String value, String operation) {
        if ("float".equals(leaf.storageType)) {
            return "KeyCanonicalization.strictFloatStorage(TABLE," + q(leaf.path) + ","
                    + value + "," + operationExpression(operation) + ")";
        }
        if ("double".equals(leaf.storageType)) {
            return "KeyCanonicalization.strictDoubleStorage(TABLE," + q(leaf.path) + ","
                    + value + "," + operationExpression(operation) + ")";
        }
        return value;
    }

    private static SelectorLeafSpec selectorLeaf(TableSpec table, String path) {
        for (SelectorSpec selector : table.selectors) {
            for (SelectorLeafSpec leaf : selector.leaves) {
                if (leaf.path.equals(path)) return leaf;
            }
        }
        return null;
    }

    private static String canonicalAccessStorage(
            SelectorLeafSpec leaf, String value, String operation) {
        return selectorStorageValue(leaf, value, operation);
    }

    static String selectorHashBits(String type, String value) {
        if ("boolean".equals(type)) return "(" + value + "?1L:0L)";
        if ("java.lang.String".equals(type)) return "(long)" + value + ".hashCode()";
        if ("float".equals(type)) return "(long)Float.floatToIntBits(" + value + ')';
        if ("double".equals(type)) return "Double.doubleToLongBits(" + value + ')';
        return "(long)(" + value + ')';
    }

    static void appendSelectorArguments(SourceBuilder out, int parameters) {
        for (int i = 0; i < parameters; i++) out.append(",value").append(i);
    }

    static void appendSelectorParameters(
            SourceBuilder out, List<SelectorParameter> parameters) {
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) out.append(',');
            out.append(parameters.get(i).publicType).append(" value").append(i);
        }
    }

    static int selectorParameterLeafCount(SelectorSpec selector) {
        return selector.leaves.size();
    }

    static List<SelectorParameter> selectorParameters(
            TableSpec table, SelectorSpec selector) {
        int leafCount = selectorParameterLeafCount(selector);
        List<SelectorParameter> result = new ArrayList<SelectorParameter>();
        int leafIndex = 0;
        while (leafIndex < leafCount) {
            SelectorGroupBinding grouped = groupedValueGroup(
                    table, selector, leafIndex, leafCount);
            if (grouped != null) {
                result.add(new SelectorParameter(
                        leafIndex, grouped.group.leafCount,
                        grouped.group.javaType, grouped));
                leafIndex += grouped.group.leafCount;
            } else {
                SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
                result.add(new SelectorParameter(leafIndex, 1, leaf.publicType, null));
                leafIndex++;
            }
        }
        return result;
    }

    static List<String> selectorPublicParameterTypes(
            TableSpec table, SelectorSpec selector) {
        List<String> result = new ArrayList<String>();
        for (SelectorParameter parameter : selectorParameters(table, selector)) {
            result.add(parameter.publicType);
        }
        return result;
    }

    private static SelectorGroupBinding groupedValueGroup(
            TableSpec table, SelectorSpec selector, int start, int limit) {
        SelectorGroupBinding best = null;
        for (FieldSpec field : table.fields) {
            for (ValueGroupSpec group : field.valueGroups) {
                if (group.leafCount == 0 || start + group.leafCount > limit) continue;
                boolean matches = true;
                for (int i = 0; i < group.leafCount; i++) {
                    ValueLeafSpec leaf = field.valueLeaves.get(group.firstLeaf + i);
                    String path = field.logicalName + "." + leaf.logicalName;
                    if (!path.equals(selector.leaves.get(start + i).path)) {
                        matches = false;
                        break;
                    }
                }
                if (matches && (best == null
                        || group.leafCount > best.group.leafCount)) {
                    best = new SelectorGroupBinding(field, group);
                }
            }
        }
        return best;
    }

    static String selectorSuffix(SelectorSpec selector) {
        String source = selector.name.startsWith("by_")
                ? selector.name.substring(3) : selector.name;
        StringBuilder suffix = new StringBuilder();
        boolean upper = true;
        for (int i = 0; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value == '_') {
                upper = true;
            } else {
                suffix.append(upper ? Character.toUpperCase(value) : value);
                upper = false;
            }
        }
        return suffix.toString();
    }

    static String selectorMethodName(SelectorSpec selector) {
        return "scanBy" + selectorSuffix(selector);
    }

    static void appendSelectorComparison(
            SourceBuilder out, TableSpec table, SelectorSpec selector,
            String leftRow, String rightRow, int leaves, boolean rowToRow,
            String operation) {
        appendSelectorComparison(out, table, selector, leftRow, rightRow,
                leaves, rowToRow, operation, null);
    }

    static void appendSelectorComparison(
            SourceBuilder out, TableSpec table, SelectorSpec selector,
            String leftRow, String rightRow, int leaves, boolean rowToRow,
            String operation, List<SelectorParameter> parameters) {
        for (int i = 0; i < leaves; i++) {
            SelectorLeafSpec leaf = selector.leaves.get(i);
            SelectorBinding binding = selectorBinding(table, leaf);
            String left = selectorStorageValue(
                    leaf, binding.columnExpression(leftRow), operation);
            String right = rowToRow ? selectorStorageValue(
                    leaf, binding.columnExpression(rightRow), operation)
                    : selectorParameterArgument(parameters, i, leaf, operation);
            out.append("int compare").append(i).append('=')
                    .append(compareExpression(leaf.storageType, left, right)).append(';')
                    .append("if(compare").append(i).append("!=0)return ")
                    .append("DESC".equals(leaf.direction) ? "-compare" : "compare")
                    .append(i).append(';');
        }
    }

    private static String selectorParameterArgument(
            List<SelectorParameter> parameters, int leafIndex,
            SelectorLeafSpec leaf, String operation) {
        for (int parameterIndex = 0; parameterIndex < parameters.size(); parameterIndex++) {
            SelectorParameter parameter = parameters.get(parameterIndex);
            if (leafIndex < parameter.firstLeaf
                    || leafIndex >= parameter.firstLeaf + parameter.leafCount) continue;
            String value = "value" + parameterIndex;
            if (parameter.grouped != null) {
                FieldSpec groupedField = parameter.grouped.field;
                ValueGroupSpec group = parameter.grouped.group;
                ValueLeafSpec groupedLeaf = groupedField.valueLeaves.get(
                        group.firstLeaf + leafIndex - parameter.firstLeaf);
                value = "RuntimeFailures.requiredValue(TABLE,"
                        + q(groupedField.logicalName
                                + (group.logicalPath.isEmpty() ? "" : "." + group.logicalPath))
                        + "," + value + "," + operationExpression(operation) + ")."
                        + relativeJavaPath(group.javaPath, groupedLeaf.javaName);
                if (groupedLeaf.enumType != null) {
                    value = "RuntimeFailures.requiredEnumValue(TABLE," + q(leaf.path) + ","
                            + value + "," + operationExpression(operation) + ").ordinal()";
                }
                return selectorStorageValue(leaf, value, operation);
            }
            return selectorArgument(leaf, value, operation);
        }
        throw new IllegalStateException("selector parameter leaf is not bound: " + leaf.path);
    }

    private static String relativeJavaPath(String groupPath, String leafPath) {
        return groupPath.isEmpty() ? leafPath : leafPath.substring(groupPath.length() + 1);
    }

    private static String selectorArgument(
            SelectorLeafSpec leaf, String value, String operation) {
        if (leaf.enumType != null) {
            return "RuntimeFailures.requiredEnumValue(TABLE," + q(leaf.path) + ","
                    + value + "," + operationExpression(operation) + ").ordinal()";
        }
        if ("java.lang.String".equals(leaf.publicType)) {
            return "RuntimeFailures.requiredValue(TABLE," + q(leaf.path) + ","
                    + value + "," + operationExpression(operation) + ")";
        }
        if ("float".equals(leaf.storageType)) {
            return "KeyCanonicalization.strictFloatStorage(TABLE," + q(leaf.path) + ","
                    + value + "," + operationExpression(operation) + ")";
        }
        if ("double".equals(leaf.storageType)) {
            return "KeyCanonicalization.strictDoubleStorage(TABLE," + q(leaf.path) + ","
                    + value + "," + operationExpression(operation) + ")";
        }
        return value;
    }

    private static String operationExpression(String operation) {
        return "$operation".equals(operation) ? "operation" : q(operation);
    }

    private static String compareExpression(String type, String left, String right) {
        if ("boolean".equals(type)) {
            return left + "==" + right + "?0:(" + left + "?1:-1)";
        }
        if ("java.lang.String".equals(type)) return left + ".compareTo(" + right + ')';
        if ("float".equals(type)) return "Float.compare(" + left + ',' + right + ')';
        if ("double".equals(type)) return "Double.compare(" + left + ',' + right + ')';
        return left + "<" + right + "?-1:" + left + ">" + right + "?1:0";
    }

    private static String flattenedValueDifferent(
            TableSpec table, FieldSpec field, String row,
            String publicValue, String operation) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < field.valueLeaves.size(); i++) {
            if (i > 0) result.append("||");
            ValueLeafSpec leaf = field.valueLeaves.get(i);
            String left = leaf.physicalName(field) + "Column.get(" + row + ")";
            String right = leaf.storageValue(
                    field, publicValue + "." + leaf.javaName, operation);
            SelectorLeafSpec accessLeaf = selectorLeaf(
                    table, field.logicalName + "." + leaf.logicalName);
            if (accessLeaf != null) {
                left = canonicalAccessStorage(accessLeaf, left, operation);
                right = canonicalAccessStorage(accessLeaf, right, operation);
            }
            if ("java.lang.String".equals(leaf.storagePrimitive)) {
                result.append('!').append(left).append(".equals(").append(right).append(')');
            } else if ("float".equals(leaf.storagePrimitive)) {
                result.append("Float.floatToIntBits(").append(left)
                        .append(")!=Float.floatToIntBits(").append(right).append(')');
            } else if ("double".equals(leaf.storagePrimitive)) {
                result.append("Double.doubleToLongBits(").append(left)
                        .append(")!=Double.doubleToLongBits(").append(right).append(')');
            } else {
                result.append(left).append("!=").append(right);
            }
        }
        return result.toString();
    }

    private static String flattenedScratchDifferent(
            TableSpec table, FieldSpec field, String row,
            String scratch, String operation) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < field.valueLeaves.size(); i++) {
            if (i > 0) result.append("||");
            ValueLeafSpec leaf = field.valueLeaves.get(i);
            String left = leaf.physicalName(field) + "Column.get(" + row + ")";
            String right = updateScratch(fieldIndex(table, field), i)
                    + "[" + scratch + "]";
            SelectorLeafSpec accessLeaf = selectorLeaf(
                    table, field.logicalName + "." + leaf.logicalName);
            if (accessLeaf != null) {
                left = canonicalAccessStorage(accessLeaf, left, operation);
                right = canonicalAccessStorage(accessLeaf, right, operation);
            }
            if ("java.lang.String".equals(leaf.storagePrimitive)) {
                result.append('!').append(left).append(".equals(").append(right).append(')');
            } else if ("float".equals(leaf.storagePrimitive)) {
                result.append("Float.floatToIntBits(").append(left)
                        .append(")!=Float.floatToIntBits(").append(right).append(')');
            } else if ("double".equals(leaf.storagePrimitive)) {
                result.append("Double.doubleToLongBits(").append(left)
                        .append(")!=Double.doubleToLongBits(").append(right).append(')');
            } else {
                result.append(left).append("!=").append(right);
            }
        }
        return result.toString();
    }

    private static String accessAwareDifferent(
            TableSpec table, FieldSpec field, String left,
            String right, String operation) {
        SelectorLeafSpec accessLeaf = selectorLeaf(table, field.logicalName);
        if (accessLeaf == null) return field.different(left, right);
        String canonicalLeft = accessLeaf.enumType == null
                ? canonicalAccessStorage(accessLeaf, left, operation)
                : selectorArgument(accessLeaf, left, operation);
        String canonicalRight = accessLeaf.enumType == null
                ? canonicalAccessStorage(accessLeaf, right, operation)
                : selectorArgument(accessLeaf, right, operation);
        return "(" + compareExpression(
                accessLeaf.storageType, canonicalLeft, canonicalRight) + ")!=0";
    }

    private static String scalarValueDifferent(
            TableSpec table, FieldSpec field, String leftStorage,
            String rightStorage, String operation) {
        ValueLeafSpec leaf = field.valueLeaves.get(0);
        SelectorLeafSpec accessLeaf = selectorLeaf(
                table, field.logicalName + "." + leaf.logicalName);
        if (accessLeaf != null) {
            leftStorage = canonicalAccessStorage(accessLeaf, leftStorage, operation);
            rightStorage = canonicalAccessStorage(accessLeaf, rightStorage, operation);
        }
        if ("java.lang.String".equals(leaf.storagePrimitive)) {
            return "!" + leftStorage + ".equals(" + rightStorage + ")";
        }
        if ("float".equals(leaf.storagePrimitive)) {
            return "Float.floatToIntBits(" + leftStorage + ")!=Float.floatToIntBits("
                    + rightStorage + ")";
        }
        if ("double".equals(leaf.storagePrimitive)) {
            return "Double.doubleToLongBits(" + leftStorage + ")!=Double.doubleToLongBits("
                    + rightStorage + ")";
        }
        return leftStorage + "!=" + rightStorage;
    }

    static SelectorBinding selectorBinding(TableSpec table, SelectorLeafSpec selector) {
        for (FieldSpec field : table.fields) {
            if (field.logicalName.equals(selector.path)) {
                return new SelectorBinding(field, field.javaName + "Column", null);
            }
            String prefix = field.logicalName + ".";
            if (!selector.path.startsWith(prefix)) continue;
            String path = selector.path.substring(prefix.length());
            for (ValueLeafSpec leaf : field.valueLeaves) {
                if (leaf.logicalName.equals(path)) {
                    return field.flattenedValueStorage()
                            ? new SelectorBinding(field, leaf.physicalName(field) + "Column", leaf)
                            : new SelectorBinding(field, field.javaName + "Column", leaf);
                }
            }
        }
        throw new IllegalStateException("selector leaf is not bound: " + selector.path);
    }

    private void appendStringKeyRuntime(
            SourceBuilder out, TableSpec table, FieldSpec key) {
        String batch = table.name("Batch");
        String column = key.javaName + "Column";
        String duplicateAppend = "RuntimeFailures.duplicateValueKey(TABLE,"
                + q(key.logicalName) + ",\"addBatch\")";
        String duplicateReplace = "RuntimeFailures.duplicateValueKey(TABLE,"
                + q(key.logicalName) + ",\"replaceAll\")";
        out.append("  private void validateAppendKeys(")
                .append(batch).append(" batch){if(batch.size()==1){String value=batch.")
                .append(key.javaName).append("Value(0);if(compositeLookup(value,\"addBatch\")>=0)throw ")
                .append(duplicateAppend)
                .append(";return;}HashCompositeKeySpace staged=newAppendValidationKeySpace(batch.size(),\"addBatch\");try{for(int row=0;row<batch.size();row++){String value=batch.")
                .append(key.javaName).append("Value(row);long hash=stringKeyHash(value);if(compositeLookup(value,\"addBatch\")>=0||compositeBatchSlot(staged,hash,batch,row)>=0)throw ")
                .append(duplicateAppend)
                .append(";staged.putAt(compositeInsertionSlot(staged,hash),hash,row);}}finally{discardAppendValidationKeySpace(staged,\"addBatch\");}}\n")
                .append("  private void appendKeys(").append(batch)
                .append(" batch,int start){for(int row=0;row<batch.size();row++){String value=batch.")
                .append(key.javaName).append("Value(row);long hash=stringKeyHash(value);keySpace.putAt(compositeInsertionSlot(keySpace,hash),hash,start+row);}}\n")
                .append("  private void rollbackAppendKeys(").append(batch)
                .append(" batch,int start){for(int row=batch.size()-1;row>=0;row--){int slot=compositeStoredSlot(start+row,\"addBatch.rollback\");if(slot<0)throw RuntimeFailures.internalInvariant(\"append_key_rollback\",TABLE,\"addBatch.rollback\");keySpace.removeAt(slot);}}\n")
                .append("  private HashCompositeKeySpace stageReplacementKeys(")
                .append(batch).append(" batch){HashCompositeKeySpace staged=newKeySpace(batch.size(),\"replaceAll\");for(int row=0;row<batch.size();row++){staged.ensureInsertCapacity();String value=batch.")
                .append(key.javaName).append("Value(row);long hash=stringKeyHash(value);if(compositeBatchSlot(staged,hash,batch,row)>=0)throw ")
                .append(duplicateReplace)
                .append(";staged.putAt(compositeInsertionSlot(staged,hash),hash,row);}return staged;}\n")
                .append("  private int compositeInsertionSlot(HashCompositeKeySpace space,long hash){int slot=space.firstSlot(hash),deleted=-1;while(!space.isEmpty(slot)){if(!space.isLive(slot)&&deleted<0)deleted=slot;slot=space.nextSlot(slot);}return deleted>=0?deleted:slot;}\n")
                .append("  private int compositeBatchSlot(HashCompositeKeySpace space,long hash,")
                .append(batch).append(" batch,int batchRow){String value=batch.")
                .append(key.javaName).append("Value(batchRow);int slot=space.firstSlot(hash);while(!space.isEmpty(slot)){if(space.isLive(slot)&&space.hashAt(slot)==hash&&value.equals(batch.")
                .append(key.javaName).append("Value(space.rowAt(slot))))return slot;slot=space.nextSlot(slot);}return -1;}\n")
                .append("  private int compositeStoredSlot(int row,String operation){String value=")
                .append(column).append(".get(row);long hash=stringKeyHash(value);int slot=keySpace.firstSlot(hash);while(!keySpace.isEmpty(slot)){if(keySpace.isLive(slot)&&keySpace.hashAt(slot)==hash&&value.equals(")
                .append(column).append(".get(keySpace.rowAt(slot))))return slot;slot=keySpace.nextSlot(slot);}return -1;}\n")
                .append("  private int compositeLookup(String key,String operation){String required=RuntimeFailures.requiredValue(TABLE,")
                .append(q(key.logicalName)).append(",key,operation);long hash=stringKeyHash(required);int slot=keySpace.firstSlot(hash);while(!keySpace.isEmpty(slot)){if(keySpace.isLive(slot)&&keySpace.hashAt(slot)==hash&&required.equals(")
                .append(column).append(".get(keySpace.rowAt(slot))))return keySpace.rowAt(slot);slot=keySpace.nextSlot(slot);}return -1;}\n")
                .append("  private long stringKeyHash(String value){return(long)value.hashCode();}\n")
                .append("  private int keyRow(String key,String operation){int row=compositeLookup(key,operation);if(row<0)throw RuntimeFailures.missingValueKey(TABLE,")
                .append(q(key.logicalName)).append(",operation);return row;}\n");
    }

    private void appendCompositeKeyRuntime(
            SourceBuilder out, TableSpec table, FieldSpec key) {
        if (key.stringKey()) {
            appendStringKeyRuntime(out, table, key);
            return;
        }
        String batch = table.name("Batch");
        String duplicateAppend = "RuntimeFailures.duplicateValueKey(TABLE,"
                + q(key.logicalName) + ",\"addBatch\")";
        String duplicateReplace = "RuntimeFailures.duplicateValueKey(TABLE,"
                + q(key.logicalName) + ",\"replaceAll\")";

        out.append("  private void validateAppendKeys(").append(batch)
                .append(" batch){if(batch.size()==1){long hash=");
        appendCompositeBatchHash(out, key, "batch", "0", "\"addBatch\"");
        out.append(";if(compositeBatchTableSlot(hash,batch,0,\"addBatch\")>=0)throw ")
                .append(duplicateAppend)
                .append(";return;}HashCompositeKeySpace staged=newAppendValidationKeySpace(batch.size(),\"addBatch\");try{for(int row=0;row<batch.size();row++){long hash=");
        appendCompositeBatchHash(out, key, "batch", "row", "\"addBatch\"");
        out.append(";if(compositeBatchTableSlot(hash,batch,row,\"addBatch\")>=0||compositeBatchSlot(staged,hash,batch,row,\"addBatch\")>=0)throw ")
                .append(duplicateAppend)
                .append(";staged.putAt(compositeInsertionSlot(staged,hash),hash,row);}}finally{discardAppendValidationKeySpace(staged,\"addBatch\");}}\n")
                .append("  private void appendKeys(").append(batch)
                .append(" batch,int start){for(int row=0;row<batch.size();row++){long hash=");
        appendCompositeBatchHash(out, key, "batch", "row", "\"addBatch\"");
        out.append(";keySpace.putAt(compositeInsertionSlot(keySpace,hash),hash,start+row);}}\n");
        out.append("  private void rollbackAppendKeys(").append(batch)
                .append(" batch,int start){for(int row=batch.size()-1;row>=0;row--){int slot=compositeStoredSlot(start+row,\"addBatch.rollback\");if(slot<0)throw RuntimeFailures.internalInvariant(\"append_key_rollback\",TABLE,\"addBatch.rollback\");keySpace.removeAt(slot);}}\n");

        out.append("  private HashCompositeKeySpace stageReplacementKeys(").append(batch)
                .append(" batch){HashCompositeKeySpace staged=newKeySpace(batch.size(),\"replaceAll\");for(int row=0;row<batch.size();row++){staged.ensureInsertCapacity();long hash=");
        appendCompositeBatchHash(out, key, "batch", "row", "\"replaceAll\"");
        out.append(";if(compositeBatchSlot(staged,hash,batch,row,\"replaceAll\")>=0)throw ")
                .append(duplicateReplace)
                .append(";staged.putAt(compositeInsertionSlot(staged,hash),hash,row);}return staged;}\n");

        out.append("  private int compositeInsertionSlot(HashCompositeKeySpace space,long hash){int slot=space.firstSlot(hash),deleted=-1;while(!space.isEmpty(slot)){if(!space.isLive(slot)&&deleted<0)deleted=slot;slot=space.nextSlot(slot);}return deleted>=0?deleted:slot;}\n")
                .append("  private int compositeBatchTableSlot(long hash,").append(batch)
                .append(" batch,int batchRow,String operation){int slot=keySpace.firstSlot(hash);while(!keySpace.isEmpty(slot)){if(keySpace.isLive(slot)&&keySpace.hashAt(slot)==hash&&compositeBatchTableEquals(batch,batchRow,keySpace.rowAt(slot),operation))return slot;slot=keySpace.nextSlot(slot);}return -1;}\n")
                .append("  private int compositeBatchSlot(HashCompositeKeySpace space,long hash,").append(batch)
                .append(" batch,int batchRow,String operation){int slot=space.firstSlot(hash);while(!space.isEmpty(slot)){if(space.isLive(slot)&&space.hashAt(slot)==hash&&compositeBatchEquals(batch,batchRow,space.rowAt(slot),operation))return slot;slot=space.nextSlot(slot);}return -1;}\n")
                .append("  private int compositeStoredSlot(int row,String operation){long hash=");
        appendCompositeColumnHash(out, key, "row", "operation");
        out.append(";int slot=keySpace.firstSlot(hash);while(!keySpace.isEmpty(slot)){if(keySpace.isLive(slot)&&keySpace.hashAt(slot)==hash&&compositeStoredEquals(row,keySpace.rowAt(slot),operation))return slot;slot=keySpace.nextSlot(slot);}return -1;}\n")
                .append("  private int compositeLookup(").append(key.primitive)
                .append(" key,String operation){").append(key.primitive)
                .append(" required=RuntimeFailures.requiredValue(TABLE,")
                .append(q(key.logicalName)).append(",key,operation);long hash=");
        appendCompositeValueHash(out, key, "required", "operation");
        out.append(";int slot=keySpace.firstSlot(hash);while(!keySpace.isEmpty(slot)){if(keySpace.isLive(slot)&&keySpace.hashAt(slot)==hash&&compositeValueEquals(required,keySpace.rowAt(slot),operation))return keySpace.rowAt(slot);slot=keySpace.nextSlot(slot);}return -1;}\n")
                .append("  private int keyRow(").append(key.primitive)
                .append(" key,String operation){int row=compositeLookup(key,operation);if(row<0)throw RuntimeFailures.missingValueKey(TABLE,")
                .append(q(key.logicalName)).append(",operation);return row;}\n");

        appendCompositeLeafLookupRuntime(out, key);

        out.append("  private long compositeHash(");
        for (int i = 0; i < key.valueLeaves.size(); i++) {
            if (i > 0) out.append(',');
            ValueLeafSpec leaf = key.valueLeaves.get(i);
            out.append(leaf.storagePrimitive).append(" leaf").append(i);
        }
        out.append(",String operation){long hash=0xcbf29ce484222325L;");
        for (int i = 0; i < key.valueLeaves.size(); i++) {
            ValueLeafSpec leaf = key.valueLeaves.get(i);
            out.append("hash^=").append(leaf.hashBits(key, "leaf" + i, "operation"))
                    .append(";hash*=0x100000001b3L;");
        }
        out.append("return hash;}\n");

        out.append("  private boolean compositeBatchTableEquals(").append(batch)
                .append(" batch,int batchRow,int tableRow,String operation){return ");
        appendCompositeBatchTableEquality(out, key, "batch", "batchRow", "tableRow", "operation");
        out.append(";}\n  private boolean compositeBatchEquals(").append(batch)
                .append(" batch,int left,int right,String operation){return ");
        appendCompositeBatchEquality(out, key, "batch", "left", "right", "operation");
        out.append(";}\n  private boolean compositeStoredEquals(int left,int right,String operation){return ");
        appendCompositeStoredEquality(out, key, "left", "right", "operation");
        out.append(";}\n  private boolean compositeValueEquals(").append(key.primitive)
                .append(" value,int row,String operation){return ");
        appendCompositeValueEquality(out, key, "value", "row", "operation");
        out.append(";}\n\n");
    }

    private static void appendCompositeBatchHash(
            SourceBuilder out, FieldSpec key, String batch, String row, String operation) {
        out.append("compositeHash(");
        for (int i = 0; i < key.valueLeaves.size(); i++) {
            if (i > 0) out.append(',');
            out.append(batch).append('.').append(key.valueLeaves.get(i).physicalName(key))
                    .append("StorageValue(").append(row).append(')');
        }
        out.append(',').append(operation).append(')');
    }

    private static void appendCompositeLeafLookupRuntime(
            SourceBuilder out, FieldSpec key) {
        out.append("  private int compositeLookupLeaves(");
        appendValueLeafParameters(out, key);
        out.append(",String operation){long hash=compositeHash(");
        for (int i = 0; i < key.valueLeaves.size(); i++) {
            if (i > 0) out.append(',');
            ValueLeafSpec leaf = key.valueLeaves.get(i);
            out.append(leaf.keyInputStorage(key, leaf.stem(key), "operation"));
        }
        out.append(",operation);int slot=keySpace.firstSlot(hash);while(!keySpace.isEmpty(slot)){if(keySpace.isLive(slot)&&keySpace.hashAt(slot)==hash&&compositeLeavesEqual(");
        appendValueLeafArguments(out, key);
        out.append(",keySpace.rowAt(slot),operation))return keySpace.rowAt(slot);slot=keySpace.nextSlot(slot);}return -1;}\n")
                .append("  private int keyRowLeaves(");
        appendValueLeafParameters(out, key);
        out.append(",String operation){int row=compositeLookupLeaves(");
        appendValueLeafArguments(out, key);
        out.append(",operation);if(row<0)throw RuntimeFailures.missingValueKey(TABLE,")
                .append(q(key.logicalName)).append(",operation);return row;}\n")
                .append("  private boolean compositeLeavesEqual(");
        appendValueLeafParameters(out, key);
        out.append(",int row,String operation){return ");
        for (int i = 0; i < key.valueLeaves.size(); i++) {
            if (i > 0) out.append("&&");
            ValueLeafSpec leaf = key.valueLeaves.get(i);
            out.append(leaf.keyEqual(key,
                    leaf.keyInputStorage(key, leaf.stem(key), "operation"),
                    leaf.physicalName(key) + "Column.get(row)", "operation"));
        }
        out.append(";}\n");
    }

    private static void appendCompositeColumnHash(
            SourceBuilder out, FieldSpec key, String row, String operation) {
        out.append("compositeHash(");
        for (int i = 0; i < key.valueLeaves.size(); i++) {
            if (i > 0) out.append(',');
            out.append(key.valueLeaves.get(i).physicalName(key)).append("Column.get(")
                    .append(row).append(')');
        }
        out.append(',').append(operation).append(')');
    }

    private static void appendCompositeValueHash(
            SourceBuilder out, FieldSpec key, String value, String operation) {
        out.append("compositeHash(");
        for (int i = 0; i < key.valueLeaves.size(); i++) {
            if (i > 0) out.append(',');
            ValueLeafSpec leaf = key.valueLeaves.get(i);
            out.append(leaf.keyInputStorage(key,
                    value + "." + leaf.javaName, operation));
        }
        out.append(',').append(operation).append(')');
    }

    private static void appendCompositeBatchTableEquality(
            SourceBuilder out, FieldSpec key, String batch, String batchRow,
            String tableRow, String operation) {
        for (int i = 0; i < key.valueLeaves.size(); i++) {
            if (i > 0) out.append("&&");
            ValueLeafSpec leaf = key.valueLeaves.get(i);
            String left = batch + "." + leaf.physicalName(key)
                    + "StorageValue(" + batchRow + ")";
            String right = leaf.physicalName(key) + "Column.get(" + tableRow + ")";
            out.append(leaf.keyEqual(key, left, right, operation));
        }
    }

    private static void appendCompositeBatchEquality(
            SourceBuilder out, FieldSpec key, String batch, String leftRow,
            String rightRow, String operation) {
        for (int i = 0; i < key.valueLeaves.size(); i++) {
            if (i > 0) out.append("&&");
            ValueLeafSpec leaf = key.valueLeaves.get(i);
            String left = batch + "." + leaf.physicalName(key)
                    + "StorageValue(" + leftRow + ")";
            String right = batch + "." + leaf.physicalName(key)
                    + "StorageValue(" + rightRow + ")";
            out.append(leaf.keyEqual(key, left, right, operation));
        }
    }

    private static void appendCompositeStoredEquality(
            SourceBuilder out, FieldSpec key, String leftRow,
            String rightRow, String operation) {
        for (int i = 0; i < key.valueLeaves.size(); i++) {
            if (i > 0) out.append("&&");
            ValueLeafSpec leaf = key.valueLeaves.get(i);
            String column = leaf.physicalName(key) + "Column.get(";
            out.append(leaf.keyEqual(key, column + leftRow + ")",
                    column + rightRow + ")", operation));
        }
    }

    private static void appendCompositeValueEquality(
            SourceBuilder out, FieldSpec key, String value, String row, String operation) {
        for (int i = 0; i < key.valueLeaves.size(); i++) {
            if (i > 0) out.append("&&");
            ValueLeafSpec leaf = key.valueLeaves.get(i);
            out.append(leaf.keyEqual(key,
                    leaf.keyInputStorage(key, value + "." + leaf.javaName, operation),
                    leaf.physicalName(key) + "Column.get(" + row + ")", operation));
        }
    }

    private static void appendValueKeyLeafLocators(SourceBuilder out, FieldSpec key) {
        if (!key.valueBacked()) return;
        if (key.compositeKey()) {
            out.append("  public int findIndex(");
            appendValueLeafParameters(out, key);
            out.append("){state.checkActive(\"findIndex\");return compositeLookupLeaves(");
            appendValueLeafArguments(out, key);
            out.append(",\"findIndex\");}\n  public int requireIndex(");
            appendValueLeafParameters(out, key);
            out.append("){state.checkActive(\"requireIndex\");return keyRowLeaves(");
            appendValueLeafArguments(out, key);
            out.append(",\"requireIndex\");}\n");
            return;
        }
        ValueLeafSpec leaf = key.valueLeaves.get(0);
        String parameter = leaf.stem(key);
        out.append("  public int findIndex(").append(leaf.primitive).append(' ')
                .append(parameter)
                .append("){state.checkActive(\"findIndex\");return keySpace.rowOf(")
                .append(key.keySpaceValueFromStorage(parameter, "findIndex"))
                .append(");}\n  public int requireIndex(").append(leaf.primitive).append(' ')
                .append(parameter)
                .append("){state.checkActive(\"requireIndex\");int row=keySpace.rowOf(")
                .append(key.keySpaceValueFromStorage(parameter, "requireIndex"))
                .append(");if(row<0)throw RuntimeFailures.missingValueKey(TABLE,")
                .append(q(key.logicalName)).append(",\"requireIndex\");return row;}\n");
    }

    private static void appendKeySpaceLifecycleRuntime(
            SourceBuilder out, FieldSpec key) {
        String validationType = key.appendValidationKeySpaceType();
        out.append("  private ").append(key.keySpaceType())
                .append(" newKeySpace(int expected,String operation){long bytes=RuntimeCompatibility.estimatedKeySpaceBytes(runtimePlan().requireTable(TABLE),")
                .append(q(key.keySpaceImplementation()))
                .append(",expected);state.preflightKeySpaceStorage(bytes,operation);state.reserveBulkScratch(bytes,operation);try{")
                .append(key.keySpaceType()).append(" staged=")
                .append(key.keySpaceConstruction(
                        "runtimePlan().requireTable(TABLE)", "expected"))
                .append(";if(staged.retainedBytes()!=bytes){staged.releaseStorage();throw RuntimeFailures.internalInvariant(\"key_space_estimator\",TABLE,operation);}return staged;}catch(RuntimeException failure){state.releaseBulkScratch(bytes,operation);throw failure;}catch(Error failure){state.releaseBulkScratch(bytes,operation);throw failure;}}\n  private void discardKeySpace(")
                .append(key.keySpaceType()).append(" staged,String operation){if(staged==null)return;state.releaseBulkScratch(staged.retainedBytes(),operation);staged.releaseStorage();}\n  private void publishKeySpace(")
                .append(key.keySpaceType())
                .append(" staged,String operation){")
                .append(key.keySpaceType()).append(" previous=keySpace;")
                .append("state.releaseBulkScratch(staged.retainedBytes(),operation);state.commitKeySpaceStorage(previous.retainedBytes(),staged.retainedBytes(),operation);keySpace=staged;previous.releaseStorage();}\n")
                .append("  private ").append(validationType)
                .append(" newAppendValidationKeySpace(int expected,String operation){long bytes=RuntimeCompatibility.estimatedHashKeySpaceBytes(")
                .append(q(key.appendValidationKeySpaceImplementation()))
                .append(",expected);state.reserveBulkScratch(bytes,operation);try{")
                .append(validationType).append(" staged=")
                .append(key.appendValidationKeySpaceConstruction("expected"))
                .append(";if(staged.retainedBytes()!=bytes){staged.releaseStorage();throw RuntimeFailures.internalInvariant(\"append_key_estimator\",TABLE,operation);}return staged;}catch(RuntimeException failure){state.releaseBulkScratch(bytes,operation);throw failure;}catch(Error failure){state.releaseBulkScratch(bytes,operation);throw failure;}}\n")
                .append("  private void discardAppendValidationKeySpace(")
                .append(validationType)
                .append(" staged,String operation){state.releaseBulkScratch(staged.retainedBytes(),operation);staged.releaseStorage();}\n")
                .append("  private void ensureAppendKeyCapacity(int additional,String operation){long previous=keySpace.retainedBytes(),proposed=keySpace.retainedBytesAfterEnsureAdditional(additional),allocation=keySpace.allocationBytesDuringEnsureAdditional(additional);state.preflightAppendStorage(additional,proposed,exactIndexRetainedBytes(),operation);state.preflightKeySpaceStorage(proposed,operation);state.reserveBulkScratch(allocation,operation);try{keySpace.ensureAdditionalCapacity(additional);}catch(RuntimeException failure){state.releaseBulkScratch(allocation,operation);throw failure;}catch(Error failure){state.releaseBulkScratch(allocation,operation);throw failure;}state.releaseBulkScratch(allocation,operation);if(keySpace.retainedBytes()!=proposed)throw RuntimeFailures.internalInvariant(\"append_key_capacity\",TABLE,operation);state.commitKeySpaceStorage(previous,proposed,operation);}\n");
    }

    private static void appendValueLeafParameters(SourceBuilder out, FieldSpec key) {
        for (int i = 0; i < key.valueLeaves.size(); i++) {
            if (i > 0) out.append(',');
            ValueLeafSpec leaf = key.valueLeaves.get(i);
            out.append(leaf.primitive).append(' ').append(leaf.stem(key));
        }
    }

    private static void appendValueLeafArguments(SourceBuilder out, FieldSpec key) {
        for (int i = 0; i < key.valueLeaves.size(); i++) {
            if (i > 0) out.append(',');
            out.append(key.valueLeaves.get(i).stem(key));
        }
    }

    private void appendTableFieldAccess(SourceBuilder out, TableSpec table) {
        for (int fieldIndex = 0; fieldIndex < table.fields.size(); fieldIndex++) {
            FieldSpec field = table.fields.get(fieldIndex);
            if (field.flattenedValueStorage()) {
                out.append("  ").append(field.primitive).append(' ').append(field.javaName)
                        .append("Value(int row){return ")
                        .append(compositeValueExpression(field, "row", false))
                        .append(";}\n");
            } else {
                out.append("  ").append(field.primitive).append(' ').append(field.javaName).append("Value(int row){return ")
                        .append(field.publicValue(field.javaName + "Column.get(row)")).append(";}\n");
            }
            if (field.optional) out.append("  boolean ").append(field.javaName).append("Present(int row){return ").append(field.javaName).append("Presence.isPresent(row);}\n");
            if (field.valueBacked()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    out.append("  ").append(leaf.primitive).append(' ')
                            .append(leaf.stem(field)).append("Value(int row){return ")
                            .append(leaf.publicValue(field,
                                    leaf.columnName(field) + ".get(row)"))
                            .append(";}\n");
                }
            }
            if (field.key) {
                continue;
            }
            if (field.flattenedValueStorage()) {
                out.append("  ").append(field.primitive).append(' ')
                        .append(updateValueMethod(fieldIndex)).append("(int row){return ")
                        .append(compositeUpdateValueExpression(fieldIndex, field, "row"))
                        .append(";}\n")
                        .append("  void ").append(setUpdateMethod(fieldIndex)).append("(int row,")
                        .append(field.primitive).append(" value){")
                        .append(field.primitive).append(" required=RuntimeFailures.requiredValue(TABLE,")
                        .append(q(field.logicalName)).append(",value,\"scan.update\");");
                for (int leafIndex = 0; leafIndex < field.valueLeaves.size(); leafIndex++) {
                    ValueLeafSpec leaf = field.valueLeaves.get(leafIndex);
                    out.append(updateScratch(fieldIndex, leafIndex))
                            .append("[row]=").append(leaf.storageValue(field,
                                    "required." + leaf.javaName, "scan.update"))
                            .append(';');
                }
            } else {
                out.append("  ").append(field.primitive).append(' ')
                        .append(updateValueMethod(fieldIndex)).append("(int row){return ")
                        .append(field.publicValue(updateScratch(fieldIndex) + "[row]"))
                        .append(";}\n")
                        .append("  void ").append(setUpdateMethod(fieldIndex)).append("(int row,")
                        .append(field.primitive)
                        .append(" value){").append(updateScratch(fieldIndex)).append("[row]=")
                        .append(field.storageValue("value", "scan.update")).append(';');
            }
            if (field.optional) out.append(updatePresenceScratch(fieldIndex))
                    .append("[row]=true;");
            out.append("}\n");
            if (field.valueBacked()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    String target = field.flattenedValueStorage()
                            ? updateScratch(fieldIndex, field.valueLeaves.indexOf(leaf))
                            : updateScratch(fieldIndex);
                    out.append("  ").append(leaf.primitive).append(' ')
                            .append(leaf.updateMethod(fieldIndex)).append("(int row){return ")
                            .append(leaf.publicValue(field, target + "[row]"))
                            .append(";}\n");
                    out.append("  void ").append(leaf.setUpdateMethod(fieldIndex))
                            .append("(int row,").append(leaf.primitive)
                            .append(" value){");
                    if (field.optional) {
                        out.append("if(!").append(updatePresenceScratch(fieldIndex))
                                .append("[row])throw RuntimeFailures.optionalAbsent(TABLE,")
                                .append(q(field.logicalName)).append(",\"scan.update.leaf\");");
                    }
                    out.append(target).append("[row]=")
                            .append(leaf.storageValue(field, "value", "scan.update"))
                            .append(";}\n");
                }
            }
            if (field.optional) {
                out.append("  boolean ").append(updatePresentMethod(fieldIndex))
                        .append("(int row){return ")
                        .append(updatePresenceScratch(fieldIndex))
                        .append("[row];}\n  void ").append(clearUpdateMethod(fieldIndex))
                        .append("(int row){");
                if (field.flattenedValueStorage()) {
                    for (int leafIndex = 0; leafIndex < field.valueLeaves.size(); leafIndex++) {
                        ValueLeafSpec leaf = field.valueLeaves.get(leafIndex);
                        out.append(updateScratch(fieldIndex, leafIndex))
                                .append("[row]=").append(leaf.storageZero()).append(';');
                    }
                } else {
                    out.append(updateScratch(fieldIndex)).append("[row]=")
                            .append(field.storageZero()).append(';');
                }
                out.append(updatePresenceScratch(fieldIndex)).append("[row]=false;}\n");
            }
        }
    }

    private void appendMutatorCommit(SourceBuilder out, TableSpec table) {
        String mutator = table.name("Mutator");
        out.append("  private void validateMutatorValues(").append(mutator)
                .append(" mutation){\n");
        for (FieldSpec field : table.fields) {
            if (field.key) continue;
            out.append("    if(mutation.").append(field.javaName).append("Touched()){");
            String guard = field.optional ? "if(mutation." + field.javaName + "Present()){" : "";
            if (!guard.isEmpty()) out.append(guard);
            if (field.flattenedValueStorage()) {
                out.append(field.primitive).append(" checked").append(cap(field.javaName))
                        .append("=RuntimeFailures.requiredValue(TABLE,")
                        .append(q(field.logicalName)).append(",mutation.")
                        .append(field.javaName).append("Value(),\"mutator.commit\");");
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    out.append(leaf.storagePrimitive).append(" checked")
                            .append(cap(leaf.physicalName(field))).append('=')
                            .append(leaf.storageValue(field,
                                    "checked" + cap(field.javaName) + "." + leaf.javaName,
                                    "mutator.commit")).append(';');
                }
            } else if (field.enumType != null || field.valueType != null
                    || "java.lang.String".equals(field.storagePrimitive)) {
                out.append(field.storagePrimitive).append(" checked").append(cap(field.javaName))
                        .append('=').append(field.storageValue(
                                "mutation." + field.javaName + "Value()", "mutator.commit"))
                        .append(';');
            }
            if (!guard.isEmpty()) out.append('}');
            out.append("}\n");
        }
        out.append("  }\n  void commitMutator(int row,long epoch,").append(mutator).append(" mutation){ownership.preflightMutation(\"mutator.commit\");state.checkRowIndex(row,\"mutator.commit\");if(epoch!=structuralEpoch())throw RuntimeFailures.staleMutator(TABLE,epoch,structuralEpoch());state.beginOperation(\"mutator.commit\");try{validateMutatorValues(mutation);validateSelectorMutator(mutation);validateUniqueMutator(row,mutation);prepareMutatorExactIndexes(row,mutation);boolean changed=false;\n");
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            out.append("    if(mutation.").append(field.javaName)
                    .append("Touched()){\n");
            String c = cap(field.javaName);
            String newValue = "mutation." + field.javaName + "Value()";
            String different = field.flattenedValueStorage()
                    ? flattenedValueDifferent(table, field, "row", newValue, "mutator.commit")
                    : field.valueBacked()
                            ? scalarValueDifferent(table, field,
                                    field.javaName + "Column.get(row)",
                                    field.storageValue(newValue, "mutator.commit"),
                                    "mutator.commit")
                    : accessAwareDifferent(table, field,
                            field.javaName + "Value(row)", newValue, "mutator.commit");
            if (field.optional) different = field.javaName + "Present(row)!=mutation."
                    + field.javaName + "Present()||(" + field.javaName
                    + "Present(row)&&(" + different + "))";
            out.append("    if(").append(different).append(")changed=true;\n");
            if (field.optional) {
                out.append("    if(mutation.").append(field.javaName)
                        .append("Present()){");
            }
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    String stored = leaf.storageValue(field,
                            newValue + "." + leaf.javaName, "mutator.commit");
                    SelectorLeafSpec accessLeaf = selectorLeaf(
                            table, field.logicalName + "." + leaf.logicalName);
                    if (accessLeaf != null) {
                        stored = canonicalAccessStorage(
                                accessLeaf, stored, "mutator.commit");
                    }
                    out.append("    ").append(leaf.physicalName(field)).append("Column.set(row,")
                            .append(stored).append(");\n");
                }
            } else {
                String stored = field.storageValue(newValue, "mutator.commit");
                SelectorLeafSpec accessLeaf = selectorLeaf(table, field.logicalName);
                if (accessLeaf != null) {
                    stored = canonicalAccessStorage(accessLeaf, stored, "mutator.commit");
                }
                out.append("    ").append(field.javaName).append("Column.set(row,")
                        .append(stored).append(");\n");
            }
            if (field.optional) {
                out.append(field.javaName).append("Presence.setPresent(row);}else{");
                if (field.flattenedValueStorage()) {
                    for (ValueLeafSpec leaf : field.valueLeaves) {
                        out.append(leaf.physicalName(field))
                                .append("Column.clearRange(row,row+1);");
                    }
                } else {
                    out.append(field.javaName).append("Column.clearRange(row,row+1);");
                }
                out.append(field.javaName).append("Presence.clearPresent(row);}\n");
            }
            out.append("    }\n");
        }
        out.append("    publishMutatorExactIndexes(row,mutation);state.endOperationSuccess(\"mutator.commit\",1L,1L,changed?1L:0L);}catch(SomaRuntimeException failure){state.endOperationFailure(\"mutator.commit\",0L,0L,failure.code());throw failure;}catch(RuntimeException failure){state.abortOperation(\"mutator.commit\");throw failure;}catch(Error failure){state.abortOperation(\"mutator.commit\");throw failure;}}\n");
    }

    private void appendUpdateScratch(SourceBuilder out, TableSpec table) {
        out.append("  int[] prepareCandidateScratch(){int required=size();long bytes=candidateScratch.retainedBytesAfterEnsure(required)+updateBytes(updateScratchCapacity);long limit=runtimePlan().requireTable(TABLE).maximumUpdateScratchBytes();if(bytes>limit)throw RuntimeFailures.memoryLimitExceeded(TABLE,\"scan.update\",limit,bytes);state.preflightUpdateScratch(bytes,\"scan.update\");candidateScratch.ensureCapacity(required);state.updateScratch(candidateScratch.retainedBytes()+updateBytes(updateScratchCapacity),candidateScratch.retainedBytes()+updateBytes(updateScratchCapacity));return candidateScratch.prepare(required);}\n")
                .append("  void prepareUpdateScratch(int required){if(required<=updateScratchCapacity)return;long bytes=candidateScratch.retainedBytes()+updateBytes(required);long limit=runtimePlan().requireTable(TABLE).maximumUpdateScratchBytes();if(bytes>limit)throw RuntimeFailures.memoryLimitExceeded(TABLE,\"scan.update\",limit,bytes);state.preflightUpdateScratch(bytes,\"scan.update\");\n");
        for (int fieldIndex = 0; fieldIndex < table.fields.size(); fieldIndex++) {
            FieldSpec field = table.fields.get(fieldIndex);
            if (field.key) {
                continue;
            }
            if (field.flattenedValueStorage()) {
                for (int leafIndex = 0; leafIndex < field.valueLeaves.size(); leafIndex++) {
                    ValueLeafSpec leaf = field.valueLeaves.get(leafIndex);
                    out.append("    ").append(leaf.storagePrimitive).append("[] newField")
                            .append(fieldIndex).append("Leaf").append(leafIndex)
                            .append("=Arrays.copyOf(").append(updateScratch(fieldIndex, leafIndex))
                            .append(",required);\n");
                }
            } else {
                out.append("    ").append(field.storagePrimitive).append("[] newField")
                        .append(fieldIndex).append("=Arrays.copyOf(")
                        .append(updateScratch(fieldIndex)).append(",required);\n");
            }
            if (field.optional) out.append("    boolean[] newField").append(fieldIndex)
                    .append("Present=Arrays.copyOf(").append(updatePresenceScratch(fieldIndex))
                    .append(",required);\n");
        }
        for (int fieldIndex = 0; fieldIndex < table.fields.size(); fieldIndex++) {
            FieldSpec field = table.fields.get(fieldIndex);
            if (field.key) {
                continue;
            }
            if (field.flattenedValueStorage()) {
                for (int leafIndex = 0; leafIndex < field.valueLeaves.size(); leafIndex++) {
                    out.append("    ").append(updateScratch(fieldIndex, leafIndex))
                            .append("=newField").append(fieldIndex).append("Leaf")
                            .append(leafIndex).append(";\n");
                }
            } else {
                out.append("    ").append(updateScratch(fieldIndex)).append("=newField")
                        .append(fieldIndex).append(";\n");
            }
            if (field.optional) out.append("    ").append(updatePresenceScratch(fieldIndex))
                    .append("=newField").append(fieldIndex).append("Present;\n");
        }
        out.append("    updateScratchCapacity=required;state.updateScratch(bytes,bytes);}\n")
                .append("  private long updateBytes(int scratchLength){return (long)scratchLength*").append(table.updateWidth()).append("L;}\n")
                .append("  void loadUpdateScratch(int[] rows,int count){for(int i=0;i<count;i++){int row=rows[i];\n");
        for (int fieldIndex = 0; fieldIndex < table.fields.size(); fieldIndex++) {
            FieldSpec field = table.fields.get(fieldIndex);
            if (field.key) {
                continue;
            }
            if (field.flattenedValueStorage()) {
                for (int leafIndex = 0; leafIndex < field.valueLeaves.size(); leafIndex++) {
                    ValueLeafSpec leaf = field.valueLeaves.get(leafIndex);
                    String physical = leaf.physicalName(field);
                    out.append("    ").append(updateScratch(fieldIndex, leafIndex)).append("[i]=")
                            .append(physical).append("Column.get(row);\n");
                }
            } else {
                out.append("    ").append(updateScratch(fieldIndex)).append("[i]=")
                        .append(field.javaName).append("Column.get(row);\n");
            }
            if (field.optional) out.append("    ").append(updatePresenceScratch(fieldIndex))
                    .append("[i]=").append(field.javaName).append("Present(row);\n");
        }
        out.append("  }}\n  void clearUpdateScratch(int count){if(count<0||count>updateScratchCapacity)throw RuntimeFailures.internalInvariant(\"update_scratch_clear\",TABLE,\"scan.update\");");
        for (int fieldIndex = 0; fieldIndex < table.fields.size(); fieldIndex++) {
            FieldSpec field = table.fields.get(fieldIndex);
            if (field.key) continue;
            if (field.flattenedValueStorage()) {
                for (int leafIndex = 0; leafIndex < field.valueLeaves.size(); leafIndex++) {
                    ValueLeafSpec leaf = field.valueLeaves.get(leafIndex);
                    if ("java.lang.String".equals(leaf.storagePrimitive)) {
                        out.append("Arrays.fill(")
                                .append(updateScratch(fieldIndex, leafIndex))
                                .append(",0,count,null);");
                    }
                }
            } else if ("java.lang.String".equals(field.storagePrimitive)) {
                out.append("Arrays.fill(").append(updateScratch(fieldIndex))
                        .append(",0,count,null);");
            }
        }
        out.append("}\n  long publishUpdate(int[] rows,int count){validateSelectorUpdate(count);validateUniqueUpdate(rows,count);prepareUpdateExactIndexes(rows,count);long changed=0L;for(int i=0;i<count;i++){int row=rows[i];boolean rowChanged=false;\n");
        for (int fieldIndex = 0; fieldIndex < table.fields.size(); fieldIndex++) {
            FieldSpec field = table.fields.get(fieldIndex);
            if (field.key) {
                continue;
            }
            String different = field.flattenedValueStorage()
                    ? flattenedScratchDifferent(table, field, "row", "i", "scan.update")
                    : field.valueBacked()
                            ? scalarValueDifferent(table, field,
                                    field.javaName + "Column.get(row)",
                                    updateScratch(fieldIndex) + "[i]", "scan.update")
                    : field.enumType != null
                            ? field.javaName + "Column.get(row)!="
                                    + updateScratch(fieldIndex) + "[i]"
                            : accessAwareDifferent(table, field, field.javaName + "Value(row)",
                                    updateScratch(fieldIndex) + "[i]", "scan.update");
            if (field.optional) different = field.javaName + "Present(row)!="
                    + updatePresenceScratch(fieldIndex) + "[i]||(" + field.javaName
                    + "Present(row)&&(" + different + "))";
            out.append("    if(").append(different).append(")rowChanged=true;\n");
            if (field.optional) {
                out.append("    if(").append(updatePresenceScratch(fieldIndex)).append("[i]){");
            }
            if (field.flattenedValueStorage()) {
                for (int leafIndex = 0; leafIndex < field.valueLeaves.size(); leafIndex++) {
                    ValueLeafSpec leaf = field.valueLeaves.get(leafIndex);
                    String stored = updateScratch(fieldIndex, leafIndex) + "[i]";
                    SelectorLeafSpec accessLeaf = selectorLeaf(
                            table, field.logicalName + "." + leaf.logicalName);
                    if (accessLeaf != null) {
                        stored = canonicalAccessStorage(accessLeaf, stored, "scan.update");
                    }
                    out.append("    ").append(leaf.physicalName(field)).append("Column.set(row,")
                            .append(stored).append(");\n");
                }
            } else {
                String stored = field.enumType == null
                        ? field.storageValue(updateScratch(fieldIndex) + "[i]", "scan.update")
                        : updateScratch(fieldIndex) + "[i]";
                SelectorLeafSpec accessLeaf = selectorLeaf(table, field.logicalName);
                if (accessLeaf != null) {
                    stored = canonicalAccessStorage(accessLeaf, stored, "scan.update");
                }
                out.append("    ").append(field.javaName).append("Column.set(row,")
                        .append(stored).append(");\n");
            }
            if (field.optional) {
                out.append(field.javaName).append("Presence.setPresent(row);}else{");
                if (field.flattenedValueStorage()) {
                    for (ValueLeafSpec leaf : field.valueLeaves) {
                        out.append(leaf.physicalName(field))
                                .append("Column.clearRange(row,row+1);");
                    }
                } else {
                    out.append(field.javaName).append("Column.clearRange(row,row+1);");
                }
                out.append(field.javaName).append("Presence.clearPresent(row);}\n");
            }
        }
        out.append("    if(rowChanged)changed++;}publishUpdateExactIndexes(rows,count);return changed;}\n")
                .append("  private void releaseRetainedScratch(){candidateScratch.release();pipelineScratch.release();sortScratch.release();updateScratchCapacity=0;");
        for (int fieldIndex = 0; fieldIndex < table.fields.size(); fieldIndex++) {
            FieldSpec field = table.fields.get(fieldIndex);
            if (field.key) continue;
            if (field.flattenedValueStorage()) {
                for (int leafIndex = 0; leafIndex < field.valueLeaves.size(); leafIndex++) {
                    ValueLeafSpec leaf = field.valueLeaves.get(leafIndex);
                    out.append(updateScratch(fieldIndex, leafIndex)).append("=new ")
                            .append(leaf.storagePrimitive).append("[0];");
                }
            } else {
                out.append(updateScratch(fieldIndex)).append("=new ")
                        .append(field.storagePrimitive).append("[0];");
            }
            if (field.optional) {
                out.append(updatePresenceScratch(fieldIndex)).append("=new boolean[0];");
            }
        }
        out.append("}\n");
    }

    private void appendRemove(SourceBuilder out, TableSpec table) {
        out.append("  RemoveResult removeSelected(int[] selected,int count,long scanned,String operation){int previous=size();if(count<0||count>previous)throw RuntimeFailures.internalInvariant(\"remove_selection_count\",TABLE,operation);state.preflightStructuralOperation(operation,count!=0);Arrays.sort(selected,0,count);for(int index=0;index<count;index++){int row=selected[index];if(row<0||row>=previous||(index!=0&&selected[index-1]==row))throw RuntimeFailures.internalInvariant(\"remove_selection_identity\",TABLE,operation);}");
        if (!table.children.isEmpty()) {
            out.append("beginRetireSelection(selected,count,operation,true);releaseRetired(false,operation);");
        }
        if (table.keyed()) {
            FieldSpec key = table.keyField();
            if (key.compositeKey()) {
                out.append("    for(int index=0;index<count;index++){int row=selected[index],slot=compositeStoredSlot(row,operation);if(slot<0)throw RuntimeFailures.internalInvariant(\"composite_key_missing\",TABLE,operation);keySpace.removeAt(slot);}\n");
            } else {
                out.append("    for(int index=0;index<count;index++){int row=selected[index];keySpace.remove(")
                        .append(key.valueBacked()
                                ? key.keySpaceValueFromStorage(key.javaName + "Column.get(row)", "scan.remove")
                                : key.keySpaceValue(key.javaName + "Value(row)", "scan.remove"))
                        .append(");}\n");
            }
        }
        out.append("unlinkExactIndexRows(selected,count);int newSize=previous-count,tail=previous-1,selectedTail=count-1;long compacted=0L;for(int holeIndex=0;holeIndex<count&&selected[holeIndex]<newSize;holeIndex++){int write=selected[holeIndex];while(tail>=newSize){while(selectedTail>=0&&selected[selectedTail]>tail)selectedTail--;if(selectedTail>=0&&selected[selectedTail]==tail){tail--;selectedTail--;continue;}break;}if(tail<newSize)throw RuntimeFailures.internalInvariant(\"swap_remove_tail\",TABLE,operation);int read=tail--;\n");
        for (FieldSpec field : table.fields) {
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    String physical = leaf.physicalName(field);
                    out.append("    ").append(physical).append("Column.set(write,")
                            .append(physical).append("Column.get(read));\n");
                }
            } else {
                out.append("    ").append(field.javaName).append("Column.set(write,")
                        .append(field.javaName).append("Column.get(read));\n");
            }
            if (field.optional) {
                out.append("    if(").append(field.javaName).append("Presence.isPresent(read))")
                        .append(field.javaName).append("Presence.setPresent(write);else ")
                        .append(field.javaName).append("Presence.clearPresent(write);\n");
            }
        }
        if (!table.children.isEmpty()) {
            out.append("    ownerTokenColumn.set(write,ownerTokenColumn.get(read));\n");
            for (ChildSpec child : table.children) {
                out.append("    ").append(child.javaName)
                        .append("HandleColumn.set(write,").append(child.javaName)
                        .append("HandleColumn.get(read));\n");
                if (child.optional) {
                    out.append("    if(").append(child.javaName)
                            .append("ChildPresence.isPresent(read))")
                            .append(child.javaName)
                            .append("ChildPresence.setPresent(write);else ")
                            .append(child.javaName)
                            .append("ChildPresence.clearPresent(write);\n");
                }
            }
        }
        if (table.keyed()) {
            FieldSpec key = table.keyField();
            if (key.compositeKey()) {
                out.append("    int keySlot=compositeStoredSlot(read,operation);if(keySlot<0)throw RuntimeFailures.internalInvariant(\"composite_key_missing\",TABLE,operation);keySpace.updateRowAt(keySlot,write);\n");
            } else {
                out.append("    keySpace.updateRow(")
                        .append(key.valueBacked()
                                ? key.keySpaceValueFromStorage(key.javaName + "Column.get(read)", "scan.remove")
                                : key.keySpaceValue(key.javaName + "Value(read)", "scan.remove"))
                        .append(",write);\n");
            }
        }
        out.append("    relocateExactIndexRows(read,write);compacted++;}clearColumns(newSize,previous);state.commitStructuralRemove(previous,newSize,operation);return state.removeResult(scanned,count,count,compacted);}\n");
    }

    private static void appendBatchFieldStaging(
            SourceBuilder out,
            FieldSpec field,
            String presenceExpression,
            String valueExpression,
            String operation) {
        String fieldCap = cap(field.javaName);
        String stagedPresent = "staged" + fieldCap + "Present";
        if (field.optional) {
            out.append("    boolean ").append(stagedPresent).append('=')
                    .append(presenceExpression).append(";\n");
        }
        if (field.flattenedValueStorage()) {
            String required = "staged" + fieldCap + "Required";
            out.append("    ").append(field.primitive).append(' ').append(required).append('=');
            if (field.optional) out.append(stagedPresent).append('?');
            out.append("RuntimeFailures.requiredValue(TABLE,")
                    .append(q(field.logicalName)).append(',').append(valueExpression)
                    .append(',').append(q(operation)).append(')');
            if (field.optional) out.append(":null");
            out.append(";\n");
            for (ValueLeafSpec leaf : field.valueLeaves) {
                out.append("    ").append(leaf.storagePrimitive).append(" staged")
                        .append(cap(leaf.physicalName(field))).append("Value=");
                if (field.optional) out.append(stagedPresent).append('?');
                out.append(leaf.storageValue(
                        field, required + "." + leaf.javaName, operation));
                if (field.optional) out.append(':').append(leaf.storageZero());
                out.append(";\n");
            }
        } else {
            out.append("    ").append(field.storagePrimitive).append(" staged")
                    .append(fieldCap).append("Value=");
            if (field.optional) out.append(stagedPresent).append('?');
            out.append(field.storageValue(valueExpression, operation));
            if (field.optional) out.append(':').append(field.storageZero());
            out.append(";\n");
        }
    }

    private static void appendBatchFieldPublish(SourceBuilder out, TableSpec table) {
        for (FieldSpec field : table.fields) {
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    out.append("    ").append(leaf.physicalName(field))
                            .append("Values[size]=staged")
                            .append(cap(leaf.physicalName(field))).append("Value;\n");
                }
            } else {
                out.append("    ").append(field.javaName)
                        .append("Values[size]=staged")
                        .append(cap(field.javaName)).append("Value;\n");
            }
            if (field.optional) {
                out.append("    setPresent(").append(field.javaName)
                        .append("Presence,size,staged").append(cap(field.javaName))
                        .append("Present);\n");
            }
        }
    }

    private static void appendBatchChildPublish(SourceBuilder out, TableSpec table) {
        for (ChildSpec child : table.children) {
            String c = cap(child.javaName);
            out.append("    ").append(child.javaName).append("Values[size]=staged")
                    .append(c).append(';').append(child.javaName)
                    .append("Present[size]=staged").append(c).append("!=null;\n");
        }
    }

    private static void appendDirectParameters(SourceBuilder out, TableSpec table) {
        for (int i = 0; i < table.fields.size(); i++) {
            if (i > 0) out.append(',');
            FieldSpec field = table.fields.get(i);
            if (field.optional) {
                out.append("boolean ").append(field.javaName).append("Present,")
                        .append(field.primitive).append(' ').append(field.javaName).append("Value");
            } else {
                out.append(field.primitive).append(' ').append(field.javaName);
            }
        }
        for (ChildSpec child : table.children) {
            if (!table.fields.isEmpty() || table.children.indexOf(child) > 0) out.append(',');
            out.append(child.batchType()).append(' ').append(child.javaName);
        }
    }

    private static String compositeValueExpression(
            FieldSpec field, String row, boolean batch) {
        String result = field.valueConstructionTemplate;
        for (int i = 0; i < field.valueLeaves.size(); i++) {
            ValueLeafSpec leaf = field.valueLeaves.get(i);
            String storage = batch
                    ? leaf.physicalName(field) + "Values[" + row + "]"
                    : leaf.physicalName(field) + "Column.get(" + row + ")";
            result = result.replace("@{" + i + "}@", leaf.publicValue(field, storage));
        }
        return result;
    }

    private static String compositeUpdateValueExpression(
            int fieldIndex, FieldSpec field, String row) {
        String result = field.valueConstructionTemplate;
        for (int i = 0; i < field.valueLeaves.size(); i++) {
            ValueLeafSpec leaf = field.valueLeaves.get(i);
            String storage = updateScratch(fieldIndex, i) + "[" + row + "]";
            result = result.replace("@{" + i + "}@", leaf.publicValue(field, storage));
        }
        return result;
    }

    private static String cap(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /** Internal update buffers use normalized numeric slots, never schema-derived identifiers. */
    private static String updateScratch(int fieldIndex) {
        return "updateField" + fieldIndex;
    }

    private static String updateScratch(int fieldIndex, int leafIndex) {
        return "updateField" + fieldIndex + "Leaf" + leafIndex;
    }

    private static String updatePresenceScratch(int fieldIndex) {
        return "updateField" + fieldIndex + "Present";
    }

    private static String updateValueMethod(int fieldIndex) {
        return "updateField" + fieldIndex + "Value";
    }

    private static String setUpdateMethod(int fieldIndex) {
        return "setUpdateField" + fieldIndex;
    }

    private static String updatePresentMethod(int fieldIndex) {
        return "updateField" + fieldIndex + "IsPresent";
    }

    private static String clearUpdateMethod(int fieldIndex) {
        return "clearUpdateField" + fieldIndex;
    }

    private static int fieldIndex(TableSpec table, FieldSpec field) {
        for (int index = 0; index < table.fields.size(); index++) {
            if (table.fields.get(index) == field) return index;
        }
        throw new IllegalStateException("field is not owned by table: " + field.javaName);
    }

    private static int leafIndex(FieldSpec field, ValueLeafSpec leaf) {
        for (int index = 0; index < field.valueLeaves.size(); index++) {
            if (field.valueLeaves.get(index) == leaf) return index;
        }
        throw new IllegalStateException("leaf is not owned by field: " + leaf.javaName);
    }

    static String q(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') out.append('\\');
            out.append(c);
        }
        return out.append('"').toString();
    }

    static final class TableSpec {
        final Element origin;
        final String carrierType;
        final String carrierSimpleName;
        final String logicalName;
        final int defaultCapacity;
        final List<FieldSpec> fields;
        final List<ChildSpec> children;
        final List<SelectorSpec> selectors;

        TableSpec(Element origin, String carrierType, String carrierSimpleName,
                  String logicalName, int defaultCapacity, List<FieldSpec> fields,
                  List<ChildSpec> children, List<SelectorSpec> selectors) {
            this.origin = origin;
            this.carrierType = carrierType;
            this.carrierSimpleName = carrierSimpleName;
            this.logicalName = logicalName;
            this.defaultCapacity = defaultCapacity;
            this.fields = new ArrayList<FieldSpec>(fields);
            this.children = new ArrayList<ChildSpec>(children);
            this.selectors = new ArrayList<SelectorSpec>(selectors);
        }

        String name(String suffix) { return carrierSimpleName + suffix; }

        boolean keyed() { return keyField() != null; }

        FieldSpec keyField() {
            for (FieldSpec field : fields) {
                if (field.key) {
                    return field;
                }
            }
            return null;
        }

        int directSlots() {
            int slots = 1;
            for (FieldSpec field : fields) {
                if (field.optional) slots++;
                slots += field.jvmSlots();
            }
            return slots;
        }

        int updateWidth() {
            int result = 0;
            for (FieldSpec field : fields) {
                if (field.key) {
                    continue;
                }
                if (field.flattenedValueStorage()) {
                    for (ValueLeafSpec leaf : field.valueLeaves) result += leaf.bytes();
                } else {
                    result += field.bytes();
                }
                if (field.optional) result++;
            }
            return result;
        }
    }

    static final class ChildSpec {
        final String javaName;
        final String logicalName;
        final String container;
        final String rowJavaType;
        final String rowSimpleName;
        final String tableLogicalName;
        final String keyMaterializedType;
        final String keyJavaName;
        final String materializedType;
        final int initialCapacity;
        final boolean optional;

        ChildSpec(
                String javaName,
                String logicalName,
                String container,
                String rowJavaType,
                String rowSimpleName,
                String tableLogicalName,
                String keyMaterializedType,
                String keyJavaName,
                String materializedType,
                int initialCapacity,
                boolean optional) {
            this.javaName = javaName;
            this.logicalName = logicalName;
            this.container = container;
            this.rowJavaType = rowJavaType;
            this.rowSimpleName = rowSimpleName;
            this.tableLogicalName = tableLogicalName;
            this.keyMaterializedType = keyMaterializedType;
            this.keyJavaName = keyJavaName;
            this.materializedType = materializedType;
            this.initialCapacity = initialCapacity;
            this.optional = optional;
        }

        String tableType() { return rowSimpleName + "Table"; }
        String batchType() { return rowSimpleName + "Batch"; }
        boolean keyed() { return "map".equals(container); }
    }

    static final class SelectorSpec {
        final String kind;
        final String name;
        final List<SelectorLeafSpec> leaves;

        SelectorSpec(String kind, String name, List<SelectorLeafSpec> leaves) {
            this.kind = kind;
            this.name = name;
            this.leaves = new ArrayList<SelectorLeafSpec>(leaves);
        }
    }

    static final class SelectorLeafSpec {
        final String path;
        final String direction;
        final String publicType;
        final String storageType;
        final String enumType;

        SelectorLeafSpec(
                String path, String direction, String publicType,
                String storageType, String enumType) {
            this.path = path;
            this.direction = direction;
            this.publicType = publicType;
            this.storageType = storageType;
            this.enumType = enumType;
        }
    }

    static final class ValueGroupSpec {
        final String javaPath;
        final String logicalPath;
        final String javaType;
        final int firstLeaf;
        final int leafCount;
        final int directFieldCount;

        ValueGroupSpec(
                String javaPath, String logicalPath, String javaType,
                int firstLeaf, int leafCount, int directFieldCount) {
            this.javaPath = javaPath;
            this.logicalPath = logicalPath;
            this.javaType = javaType;
            this.firstLeaf = firstLeaf;
            this.leafCount = leafCount;
            this.directFieldCount = directFieldCount;
        }
    }

    static final class SelectorBinding {
        final FieldSpec field;
        final String column;
        final ValueLeafSpec valueLeaf;

        SelectorBinding(FieldSpec field, String column, ValueLeafSpec valueLeaf) {
            this.field = field;
            this.column = column;
            this.valueLeaf = valueLeaf;
        }

        String columnExpression(String row) {
            return column + ".get(" + row + ")";
        }
    }

    static final class SelectorParameter {
        final int firstLeaf;
        final int leafCount;
        final String publicType;
        final SelectorGroupBinding grouped;

        SelectorParameter(
                int firstLeaf, int leafCount, String publicType,
                SelectorGroupBinding grouped) {
            this.firstLeaf = firstLeaf;
            this.leafCount = leafCount;
            this.publicType = publicType;
            this.grouped = grouped;
        }
    }

    private static final class SelectorGroupBinding {
        final FieldSpec field;
        final ValueGroupSpec group;

        SelectorGroupBinding(FieldSpec field, ValueGroupSpec group) {
            this.field = field;
            this.group = group;
        }
    }

    static final class FieldSpec {
        final String javaName;
        final String logicalName;
        /** public/generated Java type；primitive 保持 primitive，enum 保持 FQN。 */
        final String primitive;
        final String boxed;
        /** packed physical column 的 primitive type。 */
        final String storagePrimitive;
        final String columnType;
        final String enumType;
        final String valueType;
        final String valueLeafJavaName;
        final String valueConstructionTemplate;
        final List<ValueLeafSpec> valueLeaves;
        final List<ValueGroupSpec> valueGroups;
        final boolean optional;
        final boolean key;
        final String defaultExpression;

        FieldSpec(String javaName, String logicalName, String primitive,
                  String boxed, String storagePrimitive, String columnType,
                  String enumType, String valueType, String valueLeafJavaName,
                  String valueConstructionTemplate, List<ValueLeafSpec> valueLeaves,
                  List<ValueGroupSpec> valueGroups,
                  boolean optional, boolean key, String defaultExpression) {
            this.javaName = javaName;
            this.logicalName = logicalName;
            this.primitive = primitive;
            this.boxed = boxed;
            this.storagePrimitive = storagePrimitive;
            this.columnType = columnType;
            this.enumType = enumType;
            this.valueType = valueType;
            this.valueLeafJavaName = valueLeafJavaName;
            this.valueConstructionTemplate = valueConstructionTemplate;
            this.valueLeaves = new ArrayList<ValueLeafSpec>(valueLeaves);
            this.valueGroups = new ArrayList<ValueGroupSpec>(valueGroups);
            this.optional = optional;
            this.key = key;
            this.defaultExpression = defaultExpression;
        }

        boolean hasDefault() { return defaultExpression != null; }

        String zero() {
            if ("boolean".equals(primitive)) return "false";
            if ("byte".equals(primitive)) return "(byte)0";
            if ("short".equals(primitive)) return "(short)0";
            if ("long".equals(primitive)) return "0L";
            if ("float".equals(primitive)) return "0.0f";
            if ("double".equals(primitive)) return "0.0d";
            if (enumType != null || valueType != null
                    || "java.lang.String".equals(primitive)) return "null";
            return "0";
        }

        String storageZero() {
            if ("boolean".equals(storagePrimitive)) return "false";
            if ("byte".equals(storagePrimitive)) return "(byte)0";
            if ("short".equals(storagePrimitive)) return "(short)0";
            if ("long".equals(storagePrimitive)) return "0L";
            if ("float".equals(storagePrimitive)) return "0.0f";
            if ("double".equals(storagePrimitive)) return "0.0d";
            if ("java.lang.String".equals(storagePrimitive)) return "null";
            return "0";
        }

        String unboxMethod() {
            if ("boolean".equals(primitive)) return "booleanValue";
            if ("byte".equals(primitive)) return "byteValue";
            if ("short".equals(primitive)) return "shortValue";
            if ("int".equals(primitive)) return "intValue";
            if ("long".equals(primitive)) return "longValue";
            if ("float".equals(primitive)) return "floatValue";
            return "doubleValue";
        }

        String boxValue(String expression) {
            if (enumType != null || valueType != null
                    || "java.lang.String".equals(primitive)) {
                return expression;
            }
            return boxed + ".valueOf(" + expression + ")";
        }

        boolean boxedPrimitive() {
            return optional && enumType == null && valueType == null
                    && !"java.lang.String".equals(primitive);
        }

        String storageValue(String expression, String operation) {
            if (valueType != null) {
                String leaf = "RuntimeFailures.requiredValue(TABLE," + q(logicalName) + ","
                        + expression + "," + q(operation) + ")." + valueLeafJavaName;
                if (key && "float".equals(storagePrimitive)) {
                    return "KeyCanonicalization.strictFloatStorage(TABLE,"
                            + q(logicalName) + "," + leaf + "," + q(operation) + ")";
                }
                if (key && "double".equals(storagePrimitive)) {
                    return "KeyCanonicalization.strictDoubleStorage(TABLE,"
                            + q(logicalName) + "," + leaf + "," + q(operation) + ")";
                }
                return leaf;
            }
            if (enumType != null) {
                return "RuntimeFailures.requiredEnumValue(TABLE," + q(logicalName) + ","
                        + expression + "," + q(operation) + ").ordinal()";
            }
            if ("java.lang.String".equals(storagePrimitive)) {
                return "RuntimeFailures.requiredValue(TABLE," + q(logicalName) + ","
                        + expression + "," + q(operation) + ")";
            }
            if ("float".equals(primitive)) {
                return key ? "KeyCanonicalization.strictFloatStorage(TABLE,"
                        + q(logicalName) + "," + expression + "," + q(operation) + ")" : expression;
            }
            if ("double".equals(primitive)) {
                return key ? "KeyCanonicalization.strictDoubleStorage(TABLE,"
                        + q(logicalName) + "," + expression + "," + q(operation) + ")" : expression;
            }
            return expression;
        }

        String publicValue(String expression) {
            if (valueType != null) {
                return "new " + valueType + "(" + expression + ")";
            }
            return enumType == null ? expression : enumConstantsName() + "[" + expression + "]";
        }

        String enumConstantsName() {
            return javaName.toUpperCase(java.util.Locale.ROOT) + "_ENUM_VALUES";
        }

        String keySpaceType() {
            if (compositeKey()) {
                return "HashCompositeKeySpace";
            }
            if ("long".equals(storagePrimitive) || "double".equals(storagePrimitive)) {
                return "HashLongKeySpace";
            }
            return "HashIntKeySpace";
        }

        String keySpaceConstruction(String plan, String expectedSize) {
            return "new " + keySpaceType() + "(" + expectedSize + ")";
        }

        String appendValidationKeySpaceType() {
            return keySpaceType();
        }

        String appendValidationKeySpaceConstruction(String expectedSize) {
            return "new " + appendValidationKeySpaceType() + "(" + expectedSize + ")";
        }

        String appendValidationKeySpaceImplementation() {
            if ("HashCompositeKeySpace".equals(appendValidationKeySpaceType())) {
                return "hash-composite-v2";
            }
            if ("HashLongKeySpace".equals(appendValidationKeySpaceType())) {
                return "hash-long-v2";
            }
            return "hash-int-v2";
        }

        String requireInsertKey(String space, String key, String operation) {
            return "";
        }

        String keySpaceImplementation() {
            if (compositeKey()) return "hash-composite-v2";
            if ("long".equals(storagePrimitive) || "double".equals(storagePrimitive)) {
                return "hash-long-v2";
            }
            return "hash-int-v2";
        }

        String keySpaceValue(String expression, String operation) {
            return keySpaceValueExpression(expression, q(operation));
        }

        String keySpaceValueExpression(String expression, String operationExpression) {
            if (valueType != null) {
                return keySpaceValueFromStorageExpression(
                        "RuntimeFailures.requiredValue(TABLE," + q(logicalName) + ","
                                + expression + "," + operationExpression + ")." + valueLeafJavaName,
                        operationExpression);
            }
            return keySpaceValueFromStorageExpression(expression, operationExpression);
        }

        String keySpaceValueFromStorage(String expression, String operation) {
            return keySpaceValueFromStorageExpression(expression, q(operation));
        }

        String keySpaceValueFromStorageExpression(String expression, String operationExpression) {
            if (enumType != null) {
                return "RuntimeFailures.requiredEnumValue(TABLE," + q(logicalName) + ","
                        + expression + "," + operationExpression + ").ordinal()";
            }
            if ("boolean".equals(storagePrimitive)) {
                return "(" + expression + "?1:0)";
            }
            if ("byte".equals(storagePrimitive) || "short".equals(storagePrimitive)
                    || "int".equals(storagePrimitive) || "long".equals(storagePrimitive)) {
                return expression;
            }
            if ("float".equals(storagePrimitive)) {
                return "KeyCanonicalization.strictFloatKeyBits(TABLE,"
                        + q(logicalName) + "," + expression + "," + operationExpression + ")";
            }
            if ("double".equals(storagePrimitive)) {
                return "KeyCanonicalization.strictDoubleKeyBits(TABLE,"
                        + q(logicalName) + "," + expression + "," + operationExpression + ")";
            }
            throw new IllegalStateException("unsupported primitive key: " + primitive);
        }

        boolean valueBacked() {
            return valueType != null;
        }

        boolean compositeValueKey() {
            return key && flattenedValueStorage();
        }

        boolean stringKey() {
            return key && "java.lang.String".equals(primitive);
        }

        boolean compositeKey() {
            return compositeValueKey() || stringKey();
        }

        boolean flattenedValueStorage() {
            if (valueType == null || valueLeaves.isEmpty()) {
                return false;
            }
            if (!key) {
                return true;
            }
            ValueLeafSpec first = valueLeaves.get(0);
            String scalarTemplate = "new " + valueType + "(@{0}@)";
            return valueLeaves.size() > 1
                    || first.enumType != null
                    || "java.lang.String".equals(first.storagePrimitive)
                    || !scalarTemplate.equals(valueConstructionTemplate);
        }

        String keySpaceValueType() {
            return ("long".equals(storagePrimitive) || "double".equals(storagePrimitive))
                    ? "long" : "int";
        }

        String different(String left, String right) {
            if ("java.lang.String".equals(primitive)) {
                return "!" + left + ".equals(" + right + ")";
            }
            if ("float".equals(primitive)) {
                return "Float.floatToIntBits(" + left + ")!=Float.floatToIntBits(" + right + ")";
            }
            if ("double".equals(primitive)) {
                return "Double.doubleToLongBits(" + left + ")!=Double.doubleToLongBits(" + right + ")";
            }
            return left + "!=" + right;
        }

        int jvmSlots() { return "long".equals(primitive) || "double".equals(primitive) ? 2 : 1; }

        int bytes() {
            if ("boolean".equals(storagePrimitive) || "byte".equals(storagePrimitive)) return 1;
            if ("short".equals(storagePrimitive)) return 2;
            if ("int".equals(storagePrimitive) || "float".equals(storagePrimitive)) return 4;
            return 8;
        }

        boolean supportsColumnAccess() {
            return valueType == null && !"java.lang.String".equals(primitive);
        }

        String columnTraversalType() {
            return enumType == null ? cap(primitive) + "ColumnTraversal"
                    : "EnumColumnTraversal<" + enumType + ">";
        }

        String columnViewType() {
            return enumType == null ? cap(primitive) + "ColumnView"
                    : "EnumColumnView<" + enumType + ">";
        }

        String columnTraversalConstruction(String presence) {
            String operation = logicalName + ".values";
            if (enumType == null) {
                return "GeneratedColumnAccess." + primitive + "Traversal(state,"
                        + javaName + "Column," + presence
                        + ",TABLE," + q(operation) + ","
                        + q(operation + ".consumer") + ")";
            }
            return "GeneratedColumnAccess.enumTraversal(state," + javaName + "Column,"
                    + presence + ",TABLE," + q(operation) + ","
                    + q(operation + ".consumer") + "," + enumConstantsName() + ")";
        }

        String columnViewConstruction(String presence) {
            String path = logicalName + ".column";
            String getter = enumType == null ? "get" + cap(primitive) : "get";
            if (enumType == null) {
                return "GeneratedColumnAccess." + primitive + "View(state,"
                        + javaName + "Column," + presence
                        + ",TABLE," + q(logicalName) + "," + q(path) + ","
                        + q(path + ".isPresent") + "," + q(path + "." + getter) + ")";
            }
            return "GeneratedColumnAccess.enumView(state," + javaName + "Column,"
                    + presence + ",TABLE," + q(logicalName) + "," + q(path) + ","
                    + q(path + ".isPresent") + "," + q(path + "." + getter) + ","
                    + enumConstantsName() + ")";
        }
    }

    static final class ValueLeafSpec {
        final String javaName;
        final String storageName;
        final String logicalName;
        final String semantic;
        final String primitive;
        final String storagePrimitive;
        final String columnType;
        final String enumType;

        ValueLeafSpec(
                String javaName, String storageName, String logicalName, String semantic,
                String primitive, String storagePrimitive, String columnType,
                String enumType) {
            this.javaName = javaName;
            this.storageName = storageName;
            this.logicalName = logicalName;
            this.semantic = semantic;
            this.primitive = primitive;
            this.storagePrimitive = storagePrimitive;
            this.columnType = columnType;
            this.enumType = enumType;
        }

        String physicalName(FieldSpec owner) {
            int index = owner.valueLeaves.indexOf(this);
            if (index < 0) {
                throw new IllegalStateException("value leaf is not owned by field");
            }
            return owner.javaName + "Leaf" + index;
        }

        String stem(FieldSpec owner) {
            String suffix = cap(storageName);
            if (!suffix.endsWith("Value")) {
                suffix += "Value";
            }
            return owner.javaName + suffix;
        }

        String columnName(FieldSpec owner) {
            return owner.flattenedValueStorage()
                    ? physicalName(owner) + "Column" : owner.javaName + "Column";
        }

        String updateValue(FieldSpec owner, int fieldIndex, String row) {
            return updateMethod(fieldIndex) + "(" + row + ")";
        }

        String updateMethod(int fieldIndex) {
            return "updateLeaf" + fieldIndex + "_" + storageName + "Value";
        }

        String setUpdateMethod(int fieldIndex) {
            return "setUpdateLeaf" + fieldIndex + "_" + storageName;
        }

        boolean supportsColumnAccess() {
            return !"java.lang.String".equals(storagePrimitive);
        }

        String columnTraversalType() {
            return enumType == null ? cap(primitive) + "ColumnTraversal"
                    : "EnumColumnTraversal<" + enumType + ">";
        }

        String columnViewType() {
            return enumType == null ? cap(primitive) + "ColumnView"
                    : "EnumColumnView<" + enumType + ">";
        }

        String columnTraversalConstruction(FieldSpec owner, String presence) {
            String logicalPath = owner.logicalName + "." + logicalName;
            String operation = logicalPath + ".values";
            if (enumType == null) {
                return "GeneratedColumnAccess." + primitive + "Traversal(state,"
                        + columnName(owner) + "," + presence + ",TABLE,"
                        + q(operation) + "," + q(operation + ".consumer") + ")";
            }
            return "GeneratedColumnAccess.enumTraversal(state," + columnName(owner) + ","
                    + presence + ",TABLE," + q(operation) + ","
                    + q(operation + ".consumer") + "," + enumConstantsName(owner) + ")";
        }

        String columnViewConstruction(FieldSpec owner, String presence) {
            String logicalPath = owner.logicalName + "." + logicalName;
            String path = logicalPath + ".column";
            String getter = enumType == null ? "get" + cap(primitive) : "get";
            if (enumType == null) {
                return "GeneratedColumnAccess." + primitive + "View(state,"
                        + columnName(owner) + "," + presence + ",TABLE,"
                        + q(logicalPath) + "," + q(path) + ","
                        + q(path + ".isPresent") + "," + q(path + "." + getter) + ")";
            }
            return "GeneratedColumnAccess.enumView(state," + columnName(owner) + ","
                    + presence + ",TABLE," + q(logicalPath) + "," + q(path) + ","
                    + q(path + ".isPresent") + "," + q(path + "." + getter) + ","
                    + enumConstantsName(owner) + ")";
        }

        String storageZero() {
            if ("boolean".equals(storagePrimitive)) return "false";
            if ("byte".equals(storagePrimitive)) return "(byte)0";
            if ("short".equals(storagePrimitive)) return "(short)0";
            if ("long".equals(storagePrimitive)) return "0L";
            if ("float".equals(storagePrimitive)) return "0.0f";
            if ("double".equals(storagePrimitive)) return "0.0d";
            if ("java.lang.String".equals(storagePrimitive)) return "null";
            return "0";
        }

        String storageValue(
                FieldSpec owner, String expression, String operation) {
            if (owner.key && "float".equals(storagePrimitive)) {
                return "KeyCanonicalization.strictFloatStorage(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + q(operation) + ")";
            }
            if (owner.key && "double".equals(storagePrimitive)) {
                return "KeyCanonicalization.strictDoubleStorage(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + q(operation) + ")";
            }
            if (enumType != null) {
                return "RuntimeFailures.requiredEnumValue(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + q(operation) + ").ordinal()";
            }
            if ("java.lang.String".equals(storagePrimitive)) {
                return "RuntimeFailures.requiredValue(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + q(operation) + ")";
            }
            return expression;
        }

        String hashBits(FieldSpec owner, String expression, String operationExpression) {
            if ("boolean".equals(storagePrimitive)) {
                return "(" + expression + "?1L:0L)";
            }
            if ("byte".equals(storagePrimitive) || "short".equals(storagePrimitive)
                    || "int".equals(storagePrimitive) || "long".equals(storagePrimitive)) {
                return "(long)(" + expression + ")";
            }
            if ("float".equals(storagePrimitive)) {
                return "(long)KeyCanonicalization.strictFloatKeyBits(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + operationExpression + ")";
            }
            if ("double".equals(storagePrimitive)) {
                return "KeyCanonicalization.strictDoubleKeyBits(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + operationExpression + ")";
            }
            if ("java.lang.String".equals(storagePrimitive)) {
                return "(long)(" + expression + ").hashCode()";
            }
            throw new IllegalStateException("unsupported composite key leaf: " + primitive);
        }

        String keyEqual(
                FieldSpec owner, String left, String right, String operationExpression) {
            if ("float".equals(storagePrimitive)) {
                return hashBits(owner, left, operationExpression) + "=="
                        + hashBits(owner, right, operationExpression);
            }
            if ("double".equals(storagePrimitive)) {
                return hashBits(owner, left, operationExpression) + "=="
                        + hashBits(owner, right, operationExpression);
            }
            if ("java.lang.String".equals(storagePrimitive)) {
                return "(" + left + ").equals(" + right + ")";
            }
            return left + "==" + right;
        }

        String publicValue(FieldSpec owner, String storageExpression) {
            if (enumType != null) {
                return enumConstantsName(owner) + "[" + storageExpression + "]";
            }
            return storageExpression;
        }

        String keyInputStorage(
                FieldSpec owner, String expression, String operationExpression) {
            if (enumType != null) {
                return "RuntimeFailures.requiredEnumValue(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + operationExpression + ").ordinal()";
            }
            if ("java.lang.String".equals(storagePrimitive)) {
                return "RuntimeFailures.requiredValue(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + operationExpression + ")";
            }
            return expression;
        }

        int bytes() {
            if ("boolean".equals(storagePrimitive) || "byte".equals(storagePrimitive)) return 1;
            if ("short".equals(storagePrimitive)) return 2;
            if ("int".equals(storagePrimitive) || "float".equals(storagePrimitive)) return 4;
            return 8;
        }

        String enumConstantsName(FieldSpec owner) {
            return physicalName(owner).toUpperCase(java.util.Locale.ROOT)
                    + "_ENUM_VALUES";
        }
    }
}
