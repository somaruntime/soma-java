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
        add(result, table.name("Row"), rowSource(table), table.origin);
        add(result, table.name("MutableRow"), mutableRowSource(table), table.origin);
        add(result, table.name("Batch"), batchSource(table), table.origin);
        add(result, table.name("Mutator"), mutatorSource(table), table.origin);
        add(result, table.name("Rows"), rowsSource(table), table.origin);
        if (table.keyed()) {
            add(result, table.name("Keys"), keysSource(table), table.origin);
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

    private String rowSource(TableSpec table) {
        SourceBuilder out = new SourceBuilder(header());
        out.append("public interface ").append(table.name("Row")).append(" {\n");
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

    private String mutableRowSource(TableSpec table) {
        SourceBuilder out = new SourceBuilder(header());
        out.append("public interface ").append(table.name("MutableRow"))
                .append(" extends ").append(table.name("Row")).append(" {\n");
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
                .append("  private final int rowIndex;\n  private final long epoch;\n  private boolean consumed;\n");
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
                .append(" table, int rowIndex, long epoch) {\n    this.table = table; this.rowIndex = rowIndex; this.epoch = epoch;\n");
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            out.append("    this.").append(field.javaName).append("Value = ");
            if (field.optional) {
                out.append("table.").append(field.javaName).append("Present(rowIndex)?table.")
                        .append(field.javaName).append("Value(rowIndex):")
                        .append(field.zero());
            } else {
                out.append("table.").append(field.javaName).append("Value(rowIndex)");
            }
            out.append(";\n");
            if (field.optional) out.append("    this.").append(field.javaName).append("Present = table.").append(field.javaName).append("Present(rowIndex);\n");
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
        return out.append("\n  public void commit() { check(); consumed = true; table.commitMutator(rowIndex, epoch, this); }\n")
                .append("  private void check() { if (consumed) throw RuntimeFailures.mutationConsumed(")
                .append(q(table.logicalName)).append(", \"mutator\"); }\n}\n").toString();
    }

    private String keysSource(TableSpec table) {
        FieldSpec key = table.keyField();
        String name = table.name("Keys");
        String tableName = table.name("Table");
        SourceBuilder out = new SourceBuilder(header());
        out.append("import com.hgtech.soma.runtime.MaterializationBudget;\n")
                .append("import com.hgtech.soma.runtime.SomaRuntimeException;\n")
                .append("import com.hgtech.soma.runtime.generated.RuntimeFailures;\n")
                .append("import java.util.ArrayList;\nimport java.util.List;\nimport java.util.Optional;\n")
                .append("import java.util.function.Consumer;\n\n")
                .append("public final class ").append(name).append(" {\n")
                .append("  private final ").append(tableName).append(" table;\n")
                .append("  ").append(name).append('(').append(tableName).append(" table){this.table=table;}\n")
                .append("  public void forEach(Consumer<").append(key.boxed).append("> consumer){if(consumer==null)throw new NullPointerException(\"consumer\");table.begin(\"keys.forEach\");long scanned=0L;try{int size=table.size();for(int row=0;row<size;row++){scanned++;table.beginCallback(\"keys.forEach.consumer\");try{consumer.accept(").append(key.boxValue("table." + key.javaName + "Value(row)")).append(");}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"keys.forEach\",\"consumer\",callback);}finally{table.endCallback(\"keys.forEach.consumer\");}}table.endSuccess(\"keys.forEach\",scanned,scanned,0L);}catch(SomaRuntimeException failure){table.endFailure(\"keys.forEach\",scanned,scanned,failure.code());throw failure;}catch(Error failure){table.endFailure(\"keys.forEach\",scanned,scanned,\"callback_failed\");throw failure;}}\n")
                .append("  public List<").append(key.boxed).append("> fetchAll(){return fetchAll(table.runtimePlan().defaultMaterializationBudget());}\n")
                .append("  public List<").append(key.boxed).append("> fetchAll(MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");table.begin(\"keys.fetchAll\");try{List<").append(key.boxed).append("> result=table.materializeKeys(budget,\"keys.fetchAll\",true);long count=result.size();table.endSuccess(\"keys.fetchAll\",count,count,0L);return result;}catch(SomaRuntimeException failure){table.endFailure(\"keys.fetchAll\",0L,0L,failure.code());throw failure;}catch(RuntimeException failure){table.abort(\"keys.fetchAll\");throw failure;}catch(Error failure){table.abort(\"keys.fetchAll\");throw failure;}}\n")
                .append("  public Optional<").append(key.boxed).append("> findFirst(){return findFirst(table.runtimePlan().defaultMaterializationBudget());}\n")
                .append("  public Optional<").append(key.boxed).append("> findFirst(MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");table.begin(\"keys.findFirst\");try{Optional<").append(key.boxed).append("> result=table.materializeOptionalKey(budget,\"keys.findFirst\",true);long count=result.isPresent()?1L:0L;table.endSuccess(\"keys.findFirst\",count,count,0L);return result;}catch(SomaRuntimeException failure){table.endFailure(\"keys.findFirst\",0L,0L,failure.code());throw failure;}catch(RuntimeException failure){table.abort(\"keys.findFirst\");throw failure;}catch(Error failure){table.abort(\"keys.findFirst\");throw failure;}}\n")
                .append("  public ").append(key.boxed).append(" firstOrThrow(){return firstOrThrow(table.runtimePlan().defaultMaterializationBudget());}\n")
                .append("  public ").append(key.boxed).append(" firstOrThrow(MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");table.begin(\"keys.firstOrThrow\");try{").append(key.boxed).append(" result=table.materializeRequiredKey(budget,\"keys.firstOrThrow\",true);table.endSuccess(\"keys.firstOrThrow\",1L,1L,0L);return result;}catch(SomaRuntimeException failure){table.endFailure(\"keys.firstOrThrow\",0L,0L,failure.code());throw failure;}catch(RuntimeException failure){table.abort(\"keys.firstOrThrow\");throw failure;}catch(Error failure){table.abort(\"keys.firstOrThrow\");throw failure;}}\n")
                .append("}\n");
        return out.toString();
    }

    private String rowsSource(TableSpec table) {
        String rows = table.name("Rows");
        String row = table.name("Row");
        String mutable = table.name("MutableRow");
        SourceBuilder out = new SourceBuilder(header());
        out.append("import com.hgtech.soma.runtime.RemoveResult;\n")
                .append("import com.hgtech.soma.runtime.MaterializationBudget;\n")
                .append("import com.hgtech.soma.runtime.SomaRuntimeException;\n")
                .append("import com.hgtech.soma.runtime.UpdateResult;\n")
                .append("import com.hgtech.soma.runtime.generated.RuntimeFailures;\n")
                .append("import java.util.Arrays;\nimport java.util.List;\nimport java.util.Optional;\n\n")
                .append("public final class ").append(rows).append(" {\n")
                .append("  private static final byte FILTER=1,SKIP=2,LIMIT=3,SORT=4;\n")
                .append("  private final ").append(table.name("Table")).append(" table;private final Source source;\n")
                .append("  private final byte[] kinds;private final Predicate[] predicates;private final Comparator[] comparators;private final long[] counts;private boolean consumed;private long attemptedScanned,attemptedMatched,sidecarRebuildBaseline;\n")
                .append("  ").append(rows).append('(').append(table.name("Table")).append(" table){this(table,null,new byte[0],new Predicate[0],new Comparator[0],new long[0]);}\n")
                .append("  ").append(rows).append('(').append(table.name("Table")).append(" table,Source source){this(table,source,new byte[0],new Predicate[0],new Comparator[0],new long[0]);}\n")
                .append("  private ").append(rows).append('(').append(table.name("Table")).append(" table,Source source,byte[] kinds,Predicate[] predicates,Comparator[] comparators,long[] counts){this.table=table;this.source=source;this.kinds=kinds;this.predicates=predicates;this.comparators=comparators;this.counts=counts;}\n")
                .append("  public ").append(rows).append(" filter(Predicate value){if(value==null)throw new NullPointerException(\"predicate\");return append(FILTER,value,null,0L);}\n")
                .append("  public ").append(rows).append(" skip(long value){if(value<0L)throw new IllegalArgumentException(\"count must be non-negative\");return append(SKIP,null,null,value);}\n")
                .append("  public ").append(rows).append(" limit(long value){if(value<0L)throw new IllegalArgumentException(\"count must be non-negative\");return append(LIMIT,null,null,value);}\n")
                .append("  public ").append(rows).append(" sorted(Comparator value){if(value==null)throw new NullPointerException(\"comparator\");return append(SORT,null,value,0L);}\n")
                .append("  private ").append(rows).append(" append(byte kind,Predicate predicate,Comparator comparator,long count){check(\"intermediate\");int n=kinds.length;byte[] nk=Arrays.copyOf(kinds,n+1);Predicate[] np=Arrays.copyOf(predicates,n+1);Comparator[] nc=Arrays.copyOf(comparators,n+1);long[] nn=Arrays.copyOf(counts,n+1);nk[n]=kind;np[n]=predicate;nc[n]=comparator;nn[n]=count;consumed=true;return new ").append(rows).append("(table,source,nk,np,nc,nn);}\n\n")
                .append("  public long count(){start(\"rows.count\");long scanned=0L,reached=0L;try{if(!hasSort()){Cursor c=new Cursor(table);int initial=source==null?table.size():source.size();long[] seen=new long[kinds.length];for(int position=0;position<initial&&!limitReached(seen,0,kinds.length);position++){int rowIndex=source==null?position:source.rowAt(position);scanned++;if(matches(rowIndex,c,seen,0,kinds.length))reached++;}table.endSuccess(\"rows.count\",scanned,reached,0L);return reached;}Selection s=select(Integer.MAX_VALUE);table.endSuccess(\"rows.count\",s.scanned,s.length,0L);return s.length;}catch(SomaRuntimeException f){table.endFailure(\"rows.count\",scanned==0L?attemptedScanned:scanned,reached==0L?attemptedMatched:reached,f.code());throw f;}catch(RuntimeException f){table.abort(\"rows.count\");throw f;}catch(Error f){table.abort(\"rows.count\");throw f;}}\n")
                .append("  public boolean anyMatch(Predicate value){return matchTerminal(value,true);}\n")
                .append("  public boolean noneMatch(Predicate value){return !matchTerminal(value,false);}\n")
                .append("  private boolean matchTerminal(Predicate value,boolean any){if(value==null)throw new NullPointerException(\"predicate\");String op=any?\"rows.anyMatch\":\"rows.noneMatch\";start(op);long reached=0L,scanned=0L;try{Cursor c=new Cursor(table);if(!hasSort()){int initial=source==null?table.size():source.size();long[] seen=new long[kinds.length];for(int position=0;position<initial&&!limitReached(seen,0,kinds.length);position++){int rowIndex=source==null?position:source.rowAt(position);scanned++;if(matches(rowIndex,c,seen,0,kinds.length)){reached++;if(test(value,c,rowIndex,op)){table.endSuccess(op,scanned,reached,0L);return true;}}}table.endSuccess(op,scanned,reached,0L);return false;}Selection s=select(Integer.MAX_VALUE);scanned=s.scanned;for(int i=0;i<s.length;i++){reached++;if(test(value,c,s.rows[i],op)){table.endSuccess(op,scanned,reached,0L);return true;}}table.endSuccess(op,scanned,reached,0L);return false;}catch(SomaRuntimeException f){table.endFailure(op,scanned==0L?attemptedScanned:scanned,reached==0L?attemptedMatched:reached,f.code());throw f;}catch(RuntimeException f){table.abort(op);throw f;}catch(Error f){table.abort(op);throw f;}}\n")
                .append("  public void forEach(Consumer value){if(value==null)throw new NullPointerException(\"consumer\");start(\"rows.forEach\");long reached=0L,scanned=0L;Selection s=null;try{Cursor c=new Cursor(table);if(!hasSort()){int initial=source==null?table.size():source.size();long[] seen=new long[kinds.length];for(int position=0;position<initial&&!limitReached(seen,0,kinds.length);position++){int rowIndex=source==null?position:source.rowAt(position);scanned++;if(!matches(rowIndex,c,seen,0,kinds.length))continue;reached++;c.open(rowIndex);table.beginCallback(\"rows.forEach.consumer\");try{value.accept(c);}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"rows.forEach\",\"consumer\",callback);}finally{table.endCallback(\"rows.forEach.consumer\");c.close();}}table.endSuccess(\"rows.forEach\",scanned,reached,0L);return;}s=select(Integer.MAX_VALUE);scanned=s.scanned;for(int i=0;i<s.length;i++){reached++;c.open(s.rows[i]);table.beginCallback(\"rows.forEach.consumer\");try{value.accept(c);}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"rows.forEach\",\"consumer\",callback);}finally{table.endCallback(\"rows.forEach.consumer\");c.close();}}table.endSuccess(\"rows.forEach\",scanned,reached,0L);}catch(SomaRuntimeException f){table.endFailure(\"rows.forEach\",scanned==0L?attemptedScanned:scanned,reached==0L?attemptedMatched:reached,f.code());throw f;}catch(RuntimeException f){table.abort(\"rows.forEach\");throw f;}catch(Error f){table.abort(\"rows.forEach\");throw f;}}\n")
                .append("  public Optional<").append(table.carrierType).append("> findFirst(){return findFirst(table.runtimePlan().defaultMaterializationBudget());}\n")
                .append("  public Optional<").append(table.carrierType).append("> findFirst(MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");start(\"rows.findFirst\");try{Selection s=select(1);Optional<").append(table.carrierType).append("> result=table.materializeOptionalRow(s.length==0?-1:s.rows[0],budget,\"rows.findFirst\",true);table.endSuccess(\"rows.findFirst\",s.scanned,s.length,0L);return result;}catch(SomaRuntimeException f){table.endFailure(\"rows.findFirst\",attemptedScanned,attemptedMatched,f.code());throw f;}catch(RuntimeException f){table.abort(\"rows.findFirst\");throw f;}catch(Error f){table.abort(\"rows.findFirst\");throw f;}}\n")
                .append("  public ").append(table.carrierType).append(" firstOrThrow(){return firstOrThrow(table.runtimePlan().defaultMaterializationBudget());}\n")
                .append("  public ").append(table.carrierType).append(" firstOrThrow(MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");start(\"rows.firstOrThrow\");try{Selection s=select(1);").append(table.carrierType).append(" result=table.materializeRequiredRow(s.length==0?-1:s.rows[0],budget,\"rows.firstOrThrow\",true,sourcePath());table.endSuccess(\"rows.firstOrThrow\",s.scanned,1L,0L);return result;}catch(SomaRuntimeException f){table.endFailure(\"rows.firstOrThrow\",attemptedScanned,attemptedMatched,f.code());throw f;}catch(RuntimeException f){table.abort(\"rows.firstOrThrow\");throw f;}catch(Error f){table.abort(\"rows.firstOrThrow\");throw f;}}\n")
                .append("  public List<").append(table.carrierType).append("> fetchAll(){return fetchAll(table.runtimePlan().defaultMaterializationBudget());}\n")
                .append("  public List<").append(table.carrierType).append("> fetchAll(MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");start(\"rows.fetchAll\");try{Selection s=select(Integer.MAX_VALUE);List<").append(table.carrierType).append("> result=table.materializeRows(s.rows,s.length,budget,\"rows.fetchAll\",true);table.endSuccess(\"rows.fetchAll\",s.scanned,s.length,0L);return result;}catch(SomaRuntimeException f){table.endFailure(\"rows.fetchAll\",attemptedScanned,attemptedMatched,f.code());throw f;}catch(RuntimeException f){table.abort(\"rows.fetchAll\");throw f;}catch(Error f){table.abort(\"rows.fetchAll\");throw f;}}\n")
                .append("  public int[] rowIndexes(){start(\"rows.rowIndexes\");try{Selection s=select(Integer.MAX_VALUE);int[] result=Arrays.copyOf(s.rows,s.length);table.endSuccess(\"rows.rowIndexes\",s.scanned,s.length,0L);return result;}catch(SomaRuntimeException f){table.endFailure(\"rows.rowIndexes\",attemptedScanned,attemptedMatched,f.code());throw f;}catch(RuntimeException f){table.abort(\"rows.rowIndexes\");throw f;}catch(Error f){table.abort(\"rows.rowIndexes\");throw f;}}\n")
                .append("  public UpdateResult update(Updater value){if(value==null)throw new NullPointerException(\"updater\");table.preflightMutation(\"rows.update\");start(\"rows.update\");long reached=0L;Selection s=null;try{s=select(Integer.MAX_VALUE);table.prepareUpdateScratch(s.length);table.loadUpdateScratch(s.rows,s.length);MutableCursor c=new MutableCursor(table);for(int i=0;i<s.length;i++){reached++;c.open(i,s.rows[i]);table.beginCallback(\"rows.update.updater\");try{value.update(c);}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"rows.update\",\"updater\",callback);}finally{table.endCallback(\"rows.update.updater\");c.close();}}long changed=table.publishUpdate(s.rows,s.length);table.endSuccess(\"rows.update\",s.scanned,s.length,changed);return table.updateResult(s.scanned,s.length,changed,0L,rebuiltSidecars());}catch(SomaRuntimeException f){table.endFailure(\"rows.update\",s==null?attemptedScanned:s.scanned,reached==0L&&s==null?attemptedMatched:reached,f.code());throw f;}catch(RuntimeException f){table.abort(\"rows.update\");throw f;}catch(Error f){table.abort(\"rows.update\");throw f;}}\n\n")
                .append("  public RemoveResult remove(){table.preflightMutation(\"rows.remove\");start(\"rows.remove\");Selection s=null;try{s=select(Integer.MAX_VALUE);RemoveResult result=table.removeSelected(s.rows,s.length,s.scanned,0L,rebuiltSidecars(),\"rows.remove\");table.endSuccess(\"rows.remove\",s.scanned,s.length,s.length);return result;}catch(SomaRuntimeException f){table.endFailure(\"rows.remove\",s==null?attemptedScanned:s.scanned,s==null?attemptedMatched:s.length,f.code());throw f;}catch(RuntimeException f){table.abort(\"rows.remove\");throw f;}catch(Error f){table.abort(\"rows.remove\");throw f;}}\n\n")
                .append("  private Selection select(int maximum){attemptedScanned=0L;attemptedMatched=0L;int initial=source==null?table.size():source.size();int firstSort=firstSort();if(maximum==1&&stableArgMinEligible(firstSort))return selectStableArgMin(initial,firstSort);int required=firstSort<kinds.length?initial:Math.min(initial,maximum);int[] values=table.preparePipelineScratch(required);long[] seen=new long[kinds.length];Cursor cursor=new Cursor(table);int length=0;long scanned=0L;for(int position=0;position<initial&&!limitReached(seen,0,firstSort);position++){int rowIndex=source==null?position:source.rowAt(position);scanned++;attemptedScanned=scanned;if(matches(rowIndex,cursor,seen,0,firstSort)){values[length++]=rowIndex;attemptedMatched=length;}if(firstSort==kinds.length&&length>=maximum)break;}for(int stage=firstSort;stage<kinds.length;stage++){if(kinds[stage]==SORT){stableSort(values,length,comparators[stage]);}else if(kinds[stage]==FILTER){int write=0;for(int i=0;i<length;i++)if(test(predicates[stage],cursor,values[i],\"rows.filter\"))values[write++]=values[i];length=write;attemptedMatched=length;}else if(kinds[stage]==SKIP){int remove=(int)Math.min((long)length,counts[stage]);System.arraycopy(values,remove,values,0,length-remove);length-=remove;attemptedMatched=length;}else{length=(int)Math.min((long)length,counts[stage]);attemptedMatched=length;}}if(length>maximum)length=maximum;attemptedMatched=length;return new Selection(values,length,scanned);}\n")
                .append("  private boolean stableArgMinEligible(int firstSort){if(firstSort>=kinds.length)return false;int sorts=0;for(int i=0;i<kinds.length;i++){if(kinds[i]==SORT)sorts++;if(i>firstSort&&kinds[i]!=LIMIT)return false;}return sorts==1;}\n")
                .append("  private Selection selectStableArgMin(int initial,int sortStage){int[] values=table.preparePipelineScratch(Math.min(initial,1));long[] seen=new long[kinds.length];Cursor cursor=new Cursor(table),left=new Cursor(table),right=new Cursor(table);int best=-1;long scanned=0L,candidates=0L;for(int position=0;position<initial&&!limitReached(seen,0,sortStage);position++){int rowIndex=source==null?position:source.rowAt(position);scanned++;attemptedScanned=scanned;if(!matches(rowIndex,cursor,seen,0,sortStage))continue;candidates++;attemptedMatched=candidates;if(best<0||compare(comparators[sortStage],left,right,rowIndex,best)<0)best=rowIndex;}int length=best<0?0:1;for(int stage=sortStage+1;stage<kinds.length;stage++)length=(int)Math.min((long)length,counts[stage]);if(length!=0)values[0]=best;attemptedMatched=length;return new Selection(values,length,scanned);}\n")
                .append("  private int firstSort(){for(int i=0;i<kinds.length;i++)if(kinds[i]==SORT)return i;return kinds.length;}\n")
                .append("  private boolean hasSort(){return firstSort()!=kinds.length;}\n")
                .append("  private boolean limitReached(long[] seen,int from,int to){for(int i=from;i<to;i++)if(kinds[i]==LIMIT&&seen[i]>=counts[i])return true;return false;}\n")
                .append("  private boolean matches(int rowIndex,Cursor cursor,long[] seen,int from,int to){for(int i=from;i<to;i++){if(kinds[i]==FILTER){if(!test(predicates[i],cursor,rowIndex,\"rows.filter\"))return false;}else if(kinds[i]==SKIP){if(seen[i]<counts[i]){seen[i]++;return false;}}else if(kinds[i]==LIMIT){if(seen[i]>=counts[i])return false;seen[i]++;}}return true;}\n")
                .append("  private boolean test(Predicate value,Cursor cursor,int rowIndex,String operation){cursor.open(rowIndex);table.beginCallback(operation+\".predicate\");try{return value.test(cursor);}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",operation,\"predicate\",callback);}finally{table.endCallback(operation+\".predicate\");cursor.close();}}\n")
                .append("  private void stableSort(int[] values,int length,Comparator comparator){int[] auxiliary=table.prepareSortScratch(length);Cursor left=new Cursor(table),right=new Cursor(table);for(int width=1;width<length;width=width>length/2?length:width*2){for(int start=0;start<length;start+=width*2){int middle=Math.min(start+width,length),end=Math.min(start+width*2,length),a=start,b=middle,w=start;while(a<middle||b<end){if(b>=end||(a<middle&&compare(comparator,left,right,values[a],values[b])<=0))auxiliary[w++]=values[a++];else auxiliary[w++]=values[b++];}System.arraycopy(auxiliary,start,values,start,end-start);}}}\n")
                .append("  private int compare(Comparator value,Cursor left,Cursor right,int a,int b){left.open(a);right.open(b);table.beginCallback(\"rows.sorted.comparator\");try{return value.compare(left,right);}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"rows.sorted\",\"comparator\",callback);}finally{table.endCallback(\"rows.sorted.comparator\");left.close();right.close();}}\n")
                .append("  private void start(String operation){check(operation);consumed=true;attemptedScanned=0L;attemptedMatched=0L;table.begin(operation);sidecarRebuildBaseline=table.sidecarRebuildCount();}\n  private long rebuiltSidecars(){return table.sidecarRebuildCount()-sidecarRebuildBaseline;}\n  private void check(String operation){if(consumed)throw RuntimeFailures.pipelineConsumed(sourcePath(),operation);}\n")
                .append("  private String sourcePath(){return source==null?").append(q(table.logicalName)).append(":").append(q(table.logicalName + ".")).append("+source.name();}\n")
                .append("  static abstract class Source{abstract int size();abstract int rowAt(int position);abstract String name();}\n")
                .append("  public interface Predicate{boolean test(").append(row).append(" row);}\n  public interface Consumer{void accept(").append(row).append(" row);}\n  public interface Updater{void update(").append(mutable).append(" row);}\n  public interface Comparator{int compare(").append(row).append(" left,").append(row).append(" right);}\n")
                .append("  private static final class Selection{final int[] rows;final int length;final long scanned;Selection(int[] rows,int length,long scanned){this.rows=rows;this.length=length;this.scanned=scanned;}}\n")
                .append("  private static final class Cursor implements ").append(row).append(" {\n    protected final ").append(table.name("Table")).append(" table;protected int row;protected boolean active;Cursor(").append(table.name("Table")).append(" table){this.table=table;}void open(int row){this.row=row;active=true;}void close(){active=false;}void valid(){if(!active)throw RuntimeFailures.internalInvariant(\"escaped_row_cursor\",").append(q(table.logicalName)).append(",\"cursor\");}\n");
        appendCursorMethods(out, table, false);
        out.append("  }\n\n  private static final class MutableCursor implements ").append(mutable).append(" {\n    private final ").append(table.name("Table")).append(" table; private int scratch,row; private boolean active; MutableCursor(").append(table.name("Table")).append(" table){this.table=table;} void open(int scratch,int row){this.scratch=scratch;this.row=row;active=true;} void close(){active=false;} void valid(){if(!active)throw RuntimeFailures.internalInvariant(\"escaped_mutable_cursor\",").append(q(table.logicalName)).append(",\"cursor\");}\n");
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
                .append("import java.util.ArrayList;\nimport java.util.Arrays;\nimport java.util.List;\n\n")
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
            out.append("  private final RowPermutationSidecar selector")
                    .append(i).append("Sidecar=new RowPermutationSidecar();\n");
        }
        out.append("  private final DenseTableState state;\n")
                .append("  private final ChildOwnershipRegistry ownership;\n")
                .append("  private final boolean owned;\n")
                .append("  private int[] candidateScratch=new int[0],pipelineScratch=new int[0],sortScratch=new int[0];private boolean[] removeMarks=new boolean[0];private int updateScratchCapacity;\n");
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
                .append("    this.ownership=ownership;this.owned=owned;\n")
                .append("    ColumnGroup columns=new ColumnGroup(TABLE,tablePlan,ownership,tablePlan.initialCapacity()");
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
        out.append("  }\n\n")
                .append("  public static ").append(name).append(" create(){return create(defaultRuntimePlan());}\n")
                .append("  public static ").append(name).append(" create(RuntimePlan plan){if(plan==null)throw new NullPointerException(\"plan\");verifySchemaPlan(plan);TablePlan tablePlan=RuntimeCompatibility.verifyAccess(RuntimeCompatibility.verify(METADATA,plan,TABLE),")
                .append(table.selectors.isEmpty() ? "false" : "true")
                .append(");return new ").append(name).append("(plan,tablePlan,new ChildOwnershipRegistry(plan.maximumAggregateStorageBytes(),plan.maximumOwnershipTableInstances()),false,\"\");}\n")
                .append("  static ").append(name).append(" createOwned(RuntimePlan plan,ChildOwnershipRegistry ownership,int initialCapacity,String path){verifySchemaPlan(plan);TablePlan base=RuntimeCompatibility.verifyAccess(RuntimeCompatibility.verify(METADATA,plan,TABLE),")
                .append(table.selectors.isEmpty() ? "false" : "true")
                .append(");TablePlan effective=base.toBuilder().initialCapacity(initialCapacity).build();return new ")
                .append(name).append("(plan,effective,ownership,true,path);}\n")
                .append("  public static RuntimePlan defaultRuntimePlan(){return DEFAULT_RUNTIME_PLAN;}\n");
        appendSchemaPlanRuntime(out);
        out.append("  public RuntimePlan runtimePlan(){state.checkCallbackAccess(\"runtimePlan\");return state.runtimePlan();}\n  public int size(){state.checkActive(\"size\");return state.size();}\n  public int capacity(){state.checkActive(\"capacity\");return state.capacity();}\n  public long structuralEpoch(){state.checkCallbackAccess(\"structuralEpoch\");return state.structuralEpoch();}\n  public boolean isReleased(){state.checkCallbackAccess(\"isReleased\");return state.isReleased();}\n  public void reserve(int expectedCapacity){ownership.preflightMutation(\"reserve\");state.reserve(expectedCapacity);}\n\n");
        if (table.children.isEmpty() && table.keyed()) {
            String keySpaceType = table.keyField().keySpaceType();
            out.append("  public void addBatch(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");ownership.preflightMutation(\"addBatch\");state.prepareAppend(0);int count=batch.size();if(count==0)return;")
                    .append("validateSelectorAppend(batch);validateUniqueAppend(batch);validateAppendKeys(batch);ensureAppendKeyCapacity(count,\"addBatch\");int start=state.prepareAppend(count);boolean keysAppended=false;try{copyBatch(batch,0,start,count);appendKeys(batch,start);keysAppended=true;state.commitAppend(start,count);markSelectorSidecarsDirty();}catch(RuntimeException failure){if(keysAppended)rollbackAppendKeys(batch,start);clearColumns(start,start+count);throw failure;}catch(Error failure){if(keysAppended)rollbackAppendKeys(batch,start);clearColumns(start,start+count);throw failure;}}\n")
                    .append("  public void replaceAll(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");ownership.preflightMutation(\"replaceAll\");state.prepareReplace(0);")
                    .append("validateSelectorReplacement(batch);validateUniqueReplacement(batch);").append(keySpaceType).append(" staged=stageReplacementKeys(batch);staged.addMetrics(keySpace.probeCount(),keySpace.collisionCount(),keySpace.rehashCount());int count=batch.size();try{state.preflightReplaceStorage(count,staged.retainedBytes(),\"replaceAll\");int previous=state.prepareReplace(count);copyBatch(batch,0,0,count);if(previous>count)clearColumns(count,previous);state.commitReplace(previous,count);publishKeySpace(staged,\"replaceAll\");staged=null;if(previous!=count||count!=0)markSelectorSidecarsDirty();}catch(RuntimeException failure){discardKeySpace(staged,\"replaceAll\");throw failure;}catch(Error failure){discardKeySpace(staged,\"replaceAll\");throw failure;}}\n")
                    .append("  public void clear(){ownership.preflightMutation(\"clear\");int previous=state.prepareClear();clearColumns(0,previous);keySpace.clear();clearSelectorSidecars();state.commitClear(previous);}\n")
                    .append("  public void release(){state.rejectOwnedRelease(\"release\");ownership.preflightMutation(\"release\");int previous=state.prepareRelease();if(previous>=0){clearColumns(0,previous);keySpace.releaseStorage();releaseSelectorSidecars();releaseRetainedScratch();state.commitRelease(previous);ownership.releaseStorage();}}\n\n");
        } else if (table.children.isEmpty()) {
            out.append("  public void addBatch(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");ownership.preflightMutation(\"addBatch\");state.prepareAppend(0);int count=batch.size();if(count==0)return;validateSelectorAppend(batch);validateUniqueAppend(batch);int start=state.prepareAppend(count);copyBatch(batch,0,start,count);state.commitAppend(start,count);markSelectorSidecarsDirty();}\n")
                    .append("  public void replaceAll(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");ownership.preflightMutation(\"replaceAll\");state.prepareReplace(0);int count=batch.size();validateSelectorReplacement(batch);validateUniqueReplacement(batch);int previous=state.prepareReplace(count);copyBatch(batch,0,0,count);if(previous>count)clearColumns(count,previous);state.commitReplace(previous,count);if(previous!=count||count!=0)markSelectorSidecarsDirty();}\n")
                    .append("  public void clear(){ownership.preflightMutation(\"clear\");int previous=state.prepareClear();clearColumns(0,previous);clearSelectorSidecars();state.commitClear(previous);}\n")
                    .append("  public void release(){state.rejectOwnedRelease(\"release\");ownership.preflightMutation(\"release\");int previous=state.prepareRelease();if(previous>=0){clearColumns(0,previous);releaseSelectorSidecars();releaseRetainedScratch();state.commitRelease(previous);ownership.releaseStorage();}}\n\n");
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
                        .append(table.name("Batch")).append(" batch){")
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
                        .append("  public int findRowIndex(").append(key.primitive).append(" key){state.checkActive(\"findRowIndex\");return compositeLookup(key,\"findRowIndex\");}\n")
                        .append("  public int rowIndexOf(").append(key.primitive).append(" key){state.checkActive(\"rowIndexOf\");return keyRow(key,\"rowIndexOf\");}\n")
                        .append("  public java.util.Optional<").append(table.carrierType).append("> find(").append(key.primitive).append(" key){return find(key,runtimePlan().defaultMaterializationBudget());}\n")
                        .append("  public java.util.Optional<").append(table.carrierType).append("> find(").append(key.primitive).append(" key,MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");state.checkActive(\"find\");int row=compositeLookup(key,\"find\");return materializeOptionalRow(row,budget,\"find\",false);}\n")
                        .append("  public ").append(table.carrierType).append(" fetch(").append(key.primitive).append(" key){return materializeRequiredRow(keyRow(key,\"fetch\"),runtimePlan().defaultMaterializationBudget(),\"fetch\",false,TABLE);}\n")
                        .append("  public ").append(table.carrierType).append(" fetch(").append(key.primitive).append(" key,MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");return materializeRequiredRow(keyRow(key,\"fetch\"),budget,\"fetch\",false,TABLE);}\n")
                        .append("  public ").append(table.name("Mutator")).append(" mutate(").append(key.primitive).append(" key){return mutateAt(keyRow(key,\"mutate\"));}\n")
                        .append("  public void delete(").append(key.primitive).append(" key){ownership.preflightMutation(\"delete\");state.beginOperation(\"delete\");long scanned=0L;try{int row=keyRow(key,\"delete\");scanned=1L;int[] selected=preparePipelineScratch(1);selected[0]=row;removeSelected(selected,1,1L,0L,0L,\"delete\");state.endOperationSuccess(\"delete\",1L,1L,1L);}catch(SomaRuntimeException failure){state.endOperationFailure(\"delete\",scanned,0L,failure.code());throw failure;}catch(RuntimeException failure){state.abortOperation(\"delete\");throw failure;}catch(Error failure){state.abortOperation(\"delete\");throw failure;}}\n")
                        .append("  public ").append(table.name("Keys")).append(" keys(){state.checkActive(\"keys\");return new ").append(table.name("Keys")).append("(this);}\n");
            } else {
                out.append("  public boolean containsKey(").append(key.primitive).append(" key){state.checkActive(\"containsKey\");return keySpace.contains(")
                        .append(key.keySpaceValue("key", "containsKey"))
                        .append(");}\n")
                        .append("  public int findRowIndex(").append(key.primitive).append(" key){state.checkActive(\"findRowIndex\");return keySpace.rowOf(")
                        .append(key.keySpaceValue("key", "findRowIndex"))
                        .append(");}\n")
                        .append("  public int rowIndexOf(").append(key.primitive).append(" key){state.checkActive(\"rowIndexOf\");return keyRow(key,\"rowIndexOf\");}\n")
                        .append("  public java.util.Optional<").append(table.carrierType).append("> find(").append(key.primitive).append(" key){state.checkActive(\"find\");int row=keySpace.rowOf(")
                        .append(key.keySpaceValue("key", "find"))
                        .append(");return materializeOptionalRow(row,runtimePlan().defaultMaterializationBudget(),\"find\",false);}\n")
                        .append("  public java.util.Optional<").append(table.carrierType).append("> find(").append(key.primitive).append(" key,MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");state.checkActive(\"find\");int row=keySpace.rowOf(")
                        .append(key.keySpaceValue("key", "find"))
                        .append(");return materializeOptionalRow(row,budget,\"find\",false);}\n")
                        .append("  public ").append(table.carrierType).append(" fetch(").append(key.primitive).append(" key){return materializeRequiredRow(keyRow(key,\"fetch\"),runtimePlan().defaultMaterializationBudget(),\"fetch\",false,TABLE);}\n")
                        .append("  public ").append(table.carrierType).append(" fetch(").append(key.primitive).append(" key,MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");return materializeRequiredRow(keyRow(key,\"fetch\"),budget,\"fetch\",false,TABLE);}\n")
                        .append("  public ").append(table.name("Mutator")).append(" mutate(").append(key.primitive).append(" key){return mutateAt(keyRow(key,\"mutate\"));}\n")
                        .append("  public void delete(").append(key.primitive).append(" key){ownership.preflightMutation(\"delete\");state.beginOperation(\"delete\");long scanned=0L;try{int row=keyRow(key,\"delete\");scanned=1L;int[] selected=preparePipelineScratch(1);selected[0]=row;removeSelected(selected,1,1L,0L,0L,\"delete\");state.endOperationSuccess(\"delete\",1L,1L,1L);}catch(SomaRuntimeException failure){state.endOperationFailure(\"delete\",scanned,0L,failure.code());throw failure;}catch(RuntimeException failure){state.abortOperation(\"delete\");throw failure;}catch(Error failure){state.abortOperation(\"delete\");throw failure;}}\n")
                        .append("  public ").append(table.name("Keys")).append(" keys(){state.checkActive(\"keys\");return new ").append(table.name("Keys")).append("(this);}\n");
            }
            appendValueKeyLeafLocators(out, key);
        }
        out.append("  public ").append(table.name("Mutator")).append(" mutateAt(int rowIndex){int row=state.checkRowIndex(rowIndex,\"mutateAt\");return new ").append(table.name("Mutator")).append("(this,row,structuralEpoch());}\n")
                .append("  public ").append(table.name("Rows")).append(" rows(){state.checkActive(\"rows\");return new ").append(table.name("Rows")).append("(this);}\n")
                .append("  public ").append(table.name("Rows")).append(" filter(").append(table.name("Rows")).append(".Predicate predicate){return rows().filter(predicate);}\n")
                .append("  public ").append(table.name("Rows")).append(" skip(long count){return rows().skip(count);}\n")
                .append("  public ").append(table.name("Rows")).append(" limit(long count){return rows().limit(count);}\n")
                .append("  public ").append(table.name("Rows")).append(" sorted(").append(table.name("Rows")).append(".Comparator comparator){return rows().sorted(comparator);}\n")
                .append("  public long count(){return rows().count();}\n")
                .append("  public boolean anyMatch(").append(table.name("Rows")).append(".Predicate predicate){return rows().anyMatch(predicate);}\n")
                .append("  public boolean noneMatch(").append(table.name("Rows")).append(".Predicate predicate){return rows().noneMatch(predicate);}\n")
                .append("  public void forEach(").append(table.name("Rows")).append(".Consumer consumer){rows().forEach(consumer);}\n")
                .append("  public java.util.Optional<").append(table.carrierType).append("> findFirst(){return rows().findFirst();}\n")
                .append("  public java.util.Optional<").append(table.carrierType).append("> findFirst(MaterializationBudget budget){return rows().findFirst(budget);}\n")
                .append("  public ").append(table.carrierType).append(" firstOrThrow(){return rows().firstOrThrow();}\n")
                .append("  public ").append(table.carrierType).append(" firstOrThrow(MaterializationBudget budget){return rows().firstOrThrow(budget);}\n")
                .append("  public java.util.List<").append(table.carrierType).append("> fetchAll(){return rows().fetchAll();}\n")
                .append("  public java.util.List<").append(table.carrierType).append("> fetchAll(MaterializationBudget budget){return rows().fetchAll(budget);}\n")
                .append("  public int[] rowIndexes(){return rows().rowIndexes();}\n")
                .append("  public UpdateResult update(").append(table.name("Rows")).append(".Updater updater){return rows().update(updater);}\n")
                .append("  public RemoveResult remove(){return rows().remove();}\n");
        appendSelectorSources(out, table);
        for (FieldSpec field : table.fields) {
            String presence = field.optional ? field.javaName + "Presence" : "null";
            if (field.supportsColumnAccess()) {
                out.append("  public ").append(field.columnPipelineType()).append(' ')
                        .append(field.javaName).append("Values(){state.checkActive(")
                        .append(q(field.logicalName + ".values"))
                        .append(");return ")
                        .append(field.columnPipelineConstruction(presence)).append(";}\n")
                        .append("  public ").append(field.columnViewType()).append(' ')
                        .append(field.javaName).append("Column(){return ")
                        .append(field.columnViewConstruction(presence)).append(";}\n");
            }
            if (field.valueBacked()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    if (!leaf.supportsColumnAccess()) continue;
                    out.append("  public ").append(leaf.columnPipelineType()).append(' ')
                            .append(leaf.stem(field)).append("s(){state.checkActive(")
                            .append(q(field.logicalName + "." + leaf.logicalName + ".values"))
                            .append(");return ")
                            .append(leaf.columnPipelineConstruction(field, presence))
                            .append(";}\n")
                            .append("  public ").append(leaf.columnViewType()).append(' ')
                            .append(leaf.stem(field)).append("Column(){return ")
                            .append(leaf.columnViewConstruction(field, presence))
                            .append(";}\n");
                }
            }
        }
        if (table.keyed()) {
            out.append("  public TableStats statsSnapshot(){state.checkCallbackAccess(\"statsSnapshot\");return TableStats.withPhase5KeySpace(state.statsSnapshot(subtreeChildInstanceCount(),subtreeDescendantRowCount()-state.size()),")
                    .append(table.keyField().sparseIntEligible()
                            ? "keySpace.implementation()"
                            : q(table.keyField().keySpaceImplementation()))
                    .append(",keySpace.capacity(),keySpace.used(),keySpace.probeCount(),keySpace.collisionCount(),keySpace.rehashCount());}\n")
                    .append("  public void resetStats(){ownership.preflightMutation(\"resetStats\");state.resetStats();keySpace.resetMetrics();}\n\n");
        } else {
            out.append("  public TableStats statsSnapshot(){state.checkCallbackAccess(\"statsSnapshot\");return state.statsSnapshot(subtreeChildInstanceCount(),subtreeDescendantRowCount()-state.size());}\n  public void resetStats(){ownership.preflightMutation(\"resetStats\");state.resetStats();}\n\n");
        }
        out
                .append("  void begin(String operation){state.beginOperation(operation);}\n  void beginCallback(String callback){state.beginCallback(callback);}\n  void endCallback(String callback){state.endCallback(callback);}\n  void endSuccess(String operation,long scanned,long matched,long changed){state.endOperationSuccess(operation,scanned,matched,changed);}\n  void endFailure(String operation,long scanned,long matched,String code){state.endOperationFailure(operation,scanned,matched,code);}\n  void abort(String operation){state.abortOperation(operation);}\n  long sidecarRebuildCount(){return state.sidecarRebuildCount();}\n  UpdateResult updateResult(long scanned,long matched,long changed,long maintained,long rebuilt){return state.updateResult(scanned,matched,changed,maintained,rebuilt);}\n")
                .append("  void beginMaterialization(String operation,boolean nested){ownership.beginMaterialization(operation);try{if(nested)state.beginOperationMaterialization(operation);else state.beginMaterialization(operation);}catch(RuntimeException failure){ownership.endMaterialization();throw failure;}catch(Error failure){ownership.endMaterialization();throw failure;}}\n")
                .append("  void endMaterializationSuccess(MaterializationTracker tracker){try{state.endMaterializationSuccess(tracker);}finally{ownership.endMaterialization();}}\n")
                .append("  void endMaterializationFailure(MaterializationTracker tracker){try{state.endMaterializationFailure(tracker);}finally{ownership.endMaterialization();}}\n")
                .append("  void preflightMutation(String operation){ownership.preflightMutation(operation);}\n")
                .append("  private long operationScratchBytes(int pipeline,int sort,int remove){return 4L*(long)pipeline+4L*(long)sort+(long)remove;}\n")
                .append("  private void requireOperationScratch(int pipeline,int sort,int remove,String operation){long bytes=operationScratchBytes(pipeline,sort,remove),limit=runtimePlan().requireTable(TABLE).maximumOperationScratchBytes();if(bytes>limit)throw RuntimeFailures.memoryLimitExceeded(TABLE,operation,limit,bytes);state.preflightOperationScratch(bytes,operation);}\n")
                .append("  private void recordOperationScratch(){state.operationScratch(operationScratchBytes(pipelineScratch.length,sortScratch.length,removeMarks.length));}\n")
                .append("  int[] preparePipelineScratch(int required){if(required<0||required>size())throw RuntimeFailures.internalInvariant(\"pipeline_scratch_size\",TABLE,\"rows\");if(pipelineScratch.length<required){requireOperationScratch(required,sortScratch.length,removeMarks.length,\"rows\");pipelineScratch=Arrays.copyOf(pipelineScratch,required);recordOperationScratch();}return pipelineScratch;}\n")
                .append("  int[] prepareSortScratch(int required){if(required<0||required>size())throw RuntimeFailures.internalInvariant(\"sort_scratch_size\",TABLE,\"rows.sort\");if(sortScratch.length<required){requireOperationScratch(pipelineScratch.length,required,removeMarks.length,\"rows.sort\");sortScratch=Arrays.copyOf(sortScratch,required);recordOperationScratch();}return sortScratch;}\n")
                .append("  ").append(table.carrierType).append(" materializeRow(int row){return materializeRow(row,runtimePlan().defaultMaterializationBudget());}\n")
                .append("  ").append(table.carrierType).append(" materializeRow(int row,MaterializationBudget budget){return materializeRequiredRow(row,budget,\"fetchAt\",false,TABLE);}\n")
                .append("  ").append(table.carrierType).append(" materializeRequiredRow(int row,MaterializationBudget budget,String operation,boolean nested,String sourcePath){beginMaterialization(operation,nested);MaterializationTracker tracker=null;try{tracker=new MaterializationTracker(budget,TABLE);tracker.enterOwnership(this,TABLE);tracker.checkOwnershipDepth(0,TABLE);tracker.addTableInstances(1L,TABLE);if(row<0)throw RuntimeFailures.emptyResult(sourcePath,operation);tracker.addRows(1L,TABLE);accountRowRecursive(tracker,row,0,TABLE);tracker.exitOwnership(this,TABLE);MaterializationAllocation.preflight(operation,tracker.estimatedBytes(),TABLE);").append(table.carrierType).append(" result=carrierRecursive(row,0,TABLE);endMaterializationSuccess(tracker);return result;}catch(RuntimeException failure){endMaterializationFailure(tracker);throw failure;}catch(Error failure){endMaterializationFailure(tracker);throw failure;}}\n")
                .append("  java.util.Optional<").append(table.carrierType).append("> materializeOptionalRow(int row,MaterializationBudget budget,String operation,boolean nested){beginMaterialization(operation,nested);MaterializationTracker tracker=null;try{tracker=new MaterializationTracker(budget,TABLE);tracker.enterOwnership(this,TABLE);tracker.checkOwnershipDepth(0,TABLE);tracker.addTableInstances(1L,TABLE);if(row<0){tracker.addOptionalAllocation(false,TABLE);tracker.exitOwnership(this,TABLE);MaterializationAllocation.preflight(operation,tracker.estimatedBytes(),TABLE);java.util.Optional<").append(table.carrierType).append("> empty=java.util.Optional.empty();endMaterializationSuccess(tracker);return empty;}tracker.addRows(1L,TABLE);tracker.addOptionalAllocation(true,TABLE);accountRowRecursive(tracker,row,0,TABLE);tracker.exitOwnership(this,TABLE);MaterializationAllocation.preflight(operation,tracker.estimatedBytes(),TABLE);java.util.Optional<").append(table.carrierType).append("> result=java.util.Optional.of(carrierRecursive(row,0,TABLE));endMaterializationSuccess(tracker);return result;}catch(RuntimeException failure){endMaterializationFailure(tracker);throw failure;}catch(Error failure){endMaterializationFailure(tracker);throw failure;}}\n")
                .append("  List<").append(table.carrierType).append("> materializeRows(int[] rows,int count){return materializeRows(rows,count,runtimePlan().defaultMaterializationBudget());}\n")
                .append("  List<").append(table.carrierType).append("> materializeRows(int[] rows,int count,MaterializationBudget budget){return materializeRows(rows,count,budget,\"rows.fetchAll\",true);}\n")
                .append("  List<").append(table.carrierType).append("> materializeRows(int[] rows,int count,MaterializationBudget budget,String operation,boolean nested){beginMaterialization(operation,nested);MaterializationTracker tracker=null;try{tracker=new MaterializationTracker(budget,TABLE);tracker.enterOwnership(this,TABLE);tracker.checkOwnershipDepth(0,TABLE);tracker.addTableInstances(1L,TABLE);tracker.addRows(count,TABLE);tracker.addListAllocation(count,TABLE);for(int i=0;i<count;i++)accountRowRecursive(tracker,rows[i],0,TABLE);tracker.exitOwnership(this,TABLE);MaterializationAllocation.preflight(operation,tracker.estimatedBytes(),TABLE);List<").append(table.carrierType).append("> result=new ArrayList<").append(table.carrierType).append(">(count);for(int i=0;i<count;i++)result.add(carrierRecursive(rows[i],0,TABLE));endMaterializationSuccess(tracker);return result;}catch(RuntimeException failure){endMaterializationFailure(tracker);throw failure;}catch(Error failure){endMaterializationFailure(tracker);throw failure;}}\n");
        appendTableFieldAccess(out, table);
        appendMutatorCommit(out, table);
        appendUpdateScratch(out, table);
        appendRemove(out, table);
        appendSelectorRuntime(out, table);
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
                out.append(".accessStrategy(RuntimeCompatibility.PRIMITIVE_SORTED_PERMUTATION).sidecarMaintenancePolicy(RuntimeCompatibility.DIRTY_LAZY_REBUILD).maximumSidecarScratchBytes(268435456L)");
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
                    .append(',').append(candidate.keyed()
                            && candidate.keyField().sparseIntEligible() ? "true" : "false")
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
        String preflightReplacementStorage = table.keyed()
                ? "state.preflightReplaceStorage(count,stagedKeys.retainedBytes(),\"replaceAll\");"
                : "";
        String discardReplacementKeys = table.keyed()
                ? "discardKeySpace(stagedKeys,\"replaceAll\");" : "";
        String clearKeys = table.keyed() ? "keySpace.clear();" : "";
        String releaseKeys = table.keyed() ? "keySpace.releaseStorage();" : "";
        out.append("  public void addBatch(").append(batch)
                .append(" batch){if(batch==null)throw new NullPointerException(\"batch\");ownership.preflightMutation(\"addBatch\");state.prepareAppend(0);int count=batch.size();if(count==0)return;validateSelectorAppend(batch);validateUniqueAppend(batch);")
                .append(validateAppendKeys)
                .append("ChildStage staged=stageChildren(batch,\"addBatch\");int start=-1;boolean keysAppended=false;try{")
                .append(prepareAppendKeys)
                .append("start=state.prepareAppend(count);copyBatch(batch,0,start,count);copyChildStage(batch,staged,start,count);")
                .append(appendKeys)
                .append("state.commitAppend(start,count);staged.publish();markSelectorSidecarsDirty();}catch(RuntimeException failure){")
                .append(rollbackAppendKeys)
                .append("if(start>=0)clearColumns(start,start+count);staged.discard();throw failure;}catch(Error failure){")
                .append(rollbackAppendKeys)
                .append("if(start>=0)clearColumns(start,start+count);staged.discard();throw failure;}}\n")
                .append("  public void replaceAll(").append(batch)
                .append(" batch){if(batch==null)throw new NullPointerException(\"batch\");ownership.preflightMutation(\"replaceAll\");state.prepareReplace(0);int count=batch.size();validateSelectorReplacement(batch);validateUniqueReplacement(batch);")
                .append(replacementKeys)
                .append("ChildStage staged=stageChildren(batch,\"replaceAll\");try{")
                .append(preflightReplacementStorage)
                .append("int previous=state.prepareReplace(count);")
                .append("beginRetireRows(0,previous,\"replaceAll\",true);releaseRetired(false,\"replaceAll\");copyBatch(batch,0,0,count);copyChildStage(batch,staged,0,count);if(previous>count)clearColumns(count,previous);state.commitReplace(previous,count);")
                .append(publishReplacementKeys)
                .append("staged.publish();if(previous!=count||count!=0)markSelectorSidecarsDirty();}catch(RuntimeException failure){")
                .append(discardReplacementKeys)
                .append("staged.discard();throw failure;}catch(Error failure){")
                .append(discardReplacementKeys)
                .append("staged.discard();throw failure;}}\n")
                .append("  public void clear(){ownership.preflightMutation(\"clear\");int previous=state.prepareClear();beginRetireRows(0,previous,\"clear\",true);releaseRetired(false,\"clear\");clearColumns(0,previous);")
                .append(clearKeys)
                .append("clearSelectorSidecars();state.commitClear(previous);}\n")
                .append("  public void release(){state.rejectOwnedRelease(\"release\");ownership.preflightMutation(\"release\");int previous=state.prepareRelease();if(previous>=0){beginRetireRows(0,previous,\"release\",false);releaseRetired(true,\"release\");clearColumns(0,previous);")
                .append(releaseKeys)
                .append("releaseSelectorSidecars();releaseRetainedScratch();state.commitRelease(previous);ownership.releaseStorage();}}\n\n");
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
                    .append(child.tableType()).append(".createOwned(runtimePlan(),ownership,runtimePlan().requireChild(TABLE,")
                    .append(q(child.logicalName)).append(").initialCapacity(),TABLE+\".\"+")
                    .append(q(child.logicalName)).append(");try{instance.addBatch(batch.")
                    .append(child.javaName).append("Batch(row));")
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
            locatorName = "rowIndex";
            rowExpression = "state.checkRowIndex(rowIndex,operation)";
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
                .append(child.tableType()).append(".createOwned(runtimePlan(),ownership,runtimePlan().requireChild(TABLE,")
                .append(q(child.logicalName)).append(").initialCapacity(),TABLE+\".\"+")
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
                    .append(".createOwned(runtimePlan(),ownership,runtimePlan().requireChild(TABLE,")
                    .append(q(child.logicalName)).append(").initialCapacity(),TABLE+\".\"+")
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
                .append(".createOwned(runtimePlan(),ownership,runtimePlan().requireChild(TABLE,")
                .append(q(child.logicalName)).append(").initialCapacity(),TABLE+\".\"+")
                .append(q(child.logicalName)).append(");long staged=0L;try{instance.addBatch(batch.copy());staged=ownership.stage(owner,")
                .append(q(child.logicalName)).append(",TABLE+\".\"+")
                .append(q(child.logicalName)).append(",instance,instance.ownedLifecycle());")
                .append("ownership.release(old,owner,").append(q(child.logicalName))
                .append(",operation,false);")
                .append(child.javaName).append("HandleColumn.set(row,staged);");
        if (child.optional) out.append(child.javaName).append("ChildPresence.setPresent(row);");
        out.append("ownership.publish(staged,owner,").append(q(child.logicalName))
                .append(");state.commitChildChange(operation);}catch(RuntimeException failure){if(staged!=0L)ownership.discardStaged(staged,owner,")
                .append(q(child.logicalName)).append(");else instance.releaseOwnedSubtree(false);throw failure;}catch(Error failure){if(staged!=0L)ownership.discardStaged(staged,owner,")
                .append(q(child.logicalName)).append(");else instance.releaseOwnedSubtree(false);throw failure;}}\n");
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
                .append("  void releaseOwnedSubtree(boolean aggregateRelease){if(!owned)throw RuntimeFailures.internalInvariant(\"root_owned_release\",TABLE,\"ownership.release\");if(state.isReleased())return;int previous=state.size();");
        if (!table.children.isEmpty()) {
            out.append("beginRetireRows(0,previous,\"ownership.release\",false);releaseRetired(aggregateRelease,\"ownership.release\");");
        }
        out.append("clearColumns(0,previous);");
        if (table.keyed()) out.append("keySpace.releaseStorage();");
        out.append("releaseSelectorSidecars();releaseRetainedScratch();state.commitOwnedRelease(aggregateRelease);");
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
                .append(" self=this;return new OwnedChildTable(){public boolean hasPinnedSubtree(){return self.hasPinnedSubtree();}public void releaseOwnedSubtree(boolean aggregateRelease){self.releaseOwnedSubtree(aggregateRelease);}public long subtreeChildInstanceCount(){return self.subtreeChildInstanceCount();}public long subtreeDescendantRowCount(){return self.subtreeDescendantRowCount();}};}\n");
    }

    private void appendMaterializationRuntime(SourceBuilder out, TableSpec table) {
        String carrier = table.carrierType;
        out.append("\n  public ").append(carrier)
                .append(" fetchAt(int rowIndex){return fetchAt(rowIndex,runtimePlan().defaultMaterializationBudget());}\n")
                .append("  public ").append(carrier)
                .append(" fetchAt(int rowIndex,MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");int row=state.checkRowIndex(rowIndex,\"fetchAt\");return materializeRequiredRow(row,budget,\"fetchAt\",false,TABLE);}\n");
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
        String rows = table.name("Rows");
        for (int i = 0; i < table.selectors.size(); i++) {
            SelectorSpec selector = table.selectors.get(i);
            List<SelectorParameter> parameters = selectorParameters(table, selector);
            String method = selectorMethodName(selector);
            if ("order".equals(selector.kind) && !parameters.isEmpty()) {
                out.append("  public ").append(rows).append(' ').append(method)
                        .append("(){state.checkActive(").append(q(method))
                        .append(");return new ").append(rows).append("(this,new ")
                        .append(rows).append(".Source(){private int from;int size(){long range=selector")
                        .append(i).append("Range();from=(int)(range>>>32);return (int)range-from;}int rowAt(int position){return selector")
                        .append(i).append("RowAt(from+position);}String name(){return ")
                        .append(q(method)).append(";}});}\n");
            }
            out.append("  public ").append(rows).append(' ').append(method).append('(');
            appendSelectorParameters(out, parameters);
            out.append("){state.checkActive(").append(q(method)).append(");return new ")
                    .append(rows).append("(this,new ").append(rows)
                    .append(".Source(){private int from;int size(){long range=selector")
                    .append(i).append("Range(");
            for (int parameter = 0; parameter < parameters.size(); parameter++) {
                if (parameter > 0) out.append(',');
                out.append("value").append(parameter);
            }
            out.append(");from=(int)(range>>>32);return (int)range-from;}int rowAt(int position){return selector")
                    .append(i).append("RowAt(from+position);}String name(){return ")
                    .append(q(method)).append(";}});}\n");
        }
    }

    private void appendSelectorRuntime(SourceBuilder out, TableSpec table) {
        out.append("  private void markSelectorSidecarsDirty(){long dirtied=0L;");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("if(!selector").append(i).append("Sidecar.isDirty())dirtied++;selector")
                    .append(i).append("Sidecar.markDirty();");
        }
        out.append("state.sidecarsDirtied(dirtied);}\n  private void clearSelectorSidecars(){");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("selector").append(i).append("Sidecar.clear();");
        }
        out.append("}\n  private void releaseSelectorSidecars(){");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("selector").append(i).append("Sidecar.release();");
        }
        out.append("state.sidecarScratch(0L,0L);}\n  private long selectorSidecarRetainedBytes(){long bytes=0L;");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("bytes+=selector").append(i).append("Sidecar.retainedBytes();");
        }
        out.append("return bytes;}\n");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("  private void markSelector").append(i)
                    .append("Dirty(){if(selector").append(i)
                    .append("Sidecar.isDirty())return;selector").append(i)
                    .append("Sidecar.markDirty();state.sidecarsDirtied(1L);}\n");
            appendSelectorChangeRuntime(out, table, table.selectors.get(i), i);
        }

        appendUniqueValidationRuntime(out, table);

        for (int i = 0; i < table.selectors.size(); i++) {
            SelectorSpec selector = table.selectors.get(i);
            List<SelectorParameter> parameters = selectorParameters(table, selector);
            String method = selectorMethodName(selector);
            if ("order".equals(selector.kind) && !parameters.isEmpty()) {
                out.append("  private long selector").append(i)
                        .append("Range(){ensureSelector").append(i)
                        .append("();return ((long)selector").append(i)
                        .append("Sidecar.size())&0xffffffffL;}\n");
            }
            out.append("  private long selector").append(i).append("Range(");
            appendSelectorParameters(out, parameters);
            out.append("){" );
            if (!parameters.isEmpty()) {
                out.append("validateSelector").append(i).append("Arguments(");
                for (int parameter = 0; parameter < parameters.size(); parameter++) {
                    if (parameter > 0) out.append(',');
                    out.append("value").append(parameter);
                }
                out.append(");");
            }
            out.append("ensureSelector").append(i).append("();int from=0,to=selector")
                    .append(i).append("Sidecar.size();");
            if (!parameters.isEmpty()) {
                out.append("while(from<to){int middle=(from+to)>>>1;if(compareSelector")
                        .append(i).append("ToValues(selector").append(i)
                        .append("Sidecar.rowAt(middle)");
                appendSelectorArguments(out, parameters.size());
                out.append(")<0)from=middle+1;else to=middle;}int start=from;to=selector")
                        .append(i).append("Sidecar.size();while(from<to){int middle=(from+to)>>>1;if(compareSelector")
                        .append(i).append("ToValues(selector").append(i)
                        .append("Sidecar.rowAt(middle)");
                appendSelectorArguments(out, parameters.size());
                out.append(")<=0)from=middle+1;else to=middle;}int end=from;return ((long)start<<32)|(end&0xffffffffL);}");
            } else {
                out.append("return ((long)to)&0xffffffffL;}");
            }
            out.append("\n  private int selector").append(i)
                    .append("RowAt(int position){return selector").append(i)
                    .append("Sidecar.rowAt(position);}\n");

            out.append("  private void ensureSelector").append(i)
                    .append("(){if(!selector").append(i)
                    .append("Sidecar.isDirty())return;int count=size();long retained=selectorSidecarRetainedBytes();long proposed=retained-selector")
                    .append(i).append("Sidecar.retainedBytes()+selector").append(i)
                    .append("Sidecar.retainedBytesAfterRebuild(count);long peak=retained-selector")
                    .append(i).append("Sidecar.retainedBytes()+selector").append(i)
                    .append("Sidecar.rebuildPeakBytes(count);long limit=runtimePlan().requireTable(TABLE).maximumSidecarScratchBytes();if(peak<0L||peak>limit)throw RuntimeFailures.memoryLimitExceeded(TABLE,\"sidecar.rebuild\",limit,peak);state.preflightSidecarScratch(proposed,peak,\"sidecar.rebuild\");try{int[] staged=selector")
                    .append(i).append("Sidecar.stage(count);for(int row=0;row<count;row++)staged[row]=row;sortSelector")
                    .append(i).append("(staged,count);");
            if ("unique".equals(selector.kind)) {
                out.append("for(int position=1;position<count;position++)if(compareSelector")
                        .append(i).append("LeafValues(staged[position-1],staged[position])==0)throw RuntimeFailures.uniqueConstraintViolation(TABLE,")
                        .append(q(selector.name)).append(",staged[position-1],staged[position],")
                        .append(q(method)).append(");");
            }
            out.append("selector").append(i).append("Sidecar.commit(staged,count);state.sidecarRebuilt(count);}finally{long current=selectorSidecarRetainedBytes();state.sidecarScratch(current,Math.max(current,peak));}}\n")
                    .append("  private void sortSelector").append(i)
                    .append("(int[] values,int length){if(length<2)return;int[] auxiliary=selector").append(i).append("Sidecar.scratch(length);for(int width=1;width<length;width=width>length/2?length:width*2){for(int start=0;start<length;start+=width*2){int middle=Math.min(start+width,length),end=Math.min(start+width*2,length),a=start,b=middle,w=start;while(a<middle||b<end){if(b>=end||(a<middle&&compareSelector")
                    .append(i).append("Rows(values[a],values[b])<=0))auxiliary[w++]=values[a++];else auxiliary[w++]=values[b++];}System.arraycopy(auxiliary,start,values,start,end-start);}}}\n")
                    .append("  private int compareSelector").append(i).append("LeafValues(int left,int right){");
            appendSelectorComparison(out, table, selector, "left", "right", selector.leaves.size(), true, method);
            out.append("return 0;}\n  private int compareSelector").append(i)
                    .append("Rows(int left,int right){int compared=compareSelector").append(i)
                    .append("LeafValues(left,right);return compared!=0?compared:left<right?-1:left==right?0:1;}\n");
            if (!parameters.isEmpty()) {
                out.append("  private int compareSelector").append(i).append("ToValues(int row,");
                appendSelectorParameters(out, parameters);
                out.append("){");
                appendSelectorComparison(out, table, selector, "row", null,
                        selectorParameterLeafCount(selector), false, method, parameters);
                out.append("return 0;}\n  private void validateSelector").append(i)
                        .append("Arguments(");
                appendSelectorParameters(out, parameters);
                out.append("){");
                int validatedLeaves = selectorParameterLeafCount(selector);
                for (int leafIndex = 0; leafIndex < validatedLeaves; leafIndex++) {
                    SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
                    out.append(leaf.storageType).append(" checked").append(leafIndex)
                            .append('=').append(selectorParameterArgument(
                                    parameters, leafIndex, leaf, method)).append(';');
                }
                out.append("}\n");
            }
        }
    }

    private static boolean selectorUsesField(SelectorSpec selector, FieldSpec field) {
        String prefix = field.logicalName + ".";
        for (SelectorLeafSpec leaf : selector.leaves) {
            if (field.logicalName.equals(leaf.path) || leaf.path.startsWith(prefix)) return true;
        }
        return false;
    }

    private static void appendSelectorChangeRuntime(
            SourceBuilder out, TableSpec table, SelectorSpec selector, int index) {
        String mutator = table.name("Mutator");
        out.append("  private boolean selector").append(index)
                .append("ChangedByMutator(int target,").append(mutator)
                .append(" mutation){return ");
        appendSelectorChangedExpression(
                out, table, selector, "target", "mutation", null, "mutator.commit");
        out.append(";}\n  private boolean selector").append(index)
                .append("ChangedByUpdate(int[] selected,int count){for(int index=0;index<count;index++){int target=selected[index];if(");
        appendSelectorChangedExpression(
                out, table, selector, "target", null, "index", "rows.update");
        out.append(")return true;}return false;}\n");
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

    private static void appendUniqueValidationRuntime(SourceBuilder out, TableSpec table) {
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
        appendSelectorStrictValidation(out, table, "UpdateOnly", "row", "rows.update");
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
                    .append("ChangedByMutator(target,mutation)){ensureSelector").append(i)
                    .append("();int from=0,to=selector").append(i)
                    .append("Sidecar.size();while(from<to){int middle=(from+to)>>>1;if(compareSelector")
                    .append(i).append("RowToMutator(selector").append(i)
                    .append("Sidecar.rowAt(middle),target,mutation)<0)from=middle+1;else to=middle;}if(from<selector")
                    .append(i).append("Sidecar.size()){int conflict=selector").append(i)
                    .append("Sidecar.rowAt(from);if(compareSelector").append(i)
                    .append("RowToMutator(conflict,target,mutation)==0)throw RuntimeFailures.uniqueConstraintViolation(TABLE,")
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
            out.append("if(changedUniqueCount==0)return;int[] lookup=prepareCandidateScratch();long retained=4L*(long)candidateScratch.length+updateBytes(updateScratchCapacity);long unit=HashCompositeKeySpace.estimatedPeakBytes(size());long uniqueBytes=unit>Long.MAX_VALUE/(long)changedUniqueCount?Long.MAX_VALUE:unit*(long)changedUniqueCount;long required=uniqueBytes==Long.MAX_VALUE||retained>Long.MAX_VALUE-uniqueBytes?Long.MAX_VALUE:retained+uniqueBytes;long limit=runtimePlan().requireTable(TABLE).maximumUpdateScratchBytes();if(required>limit)throw RuntimeFailures.memoryLimitExceeded(TABLE,\"rows.update\",limit,required);state.updateScratch(required,required);try{Arrays.fill(lookup,0,size(),-1);for(int index=0;index<selectedCount;index++)lookup[selected[index]]=index;");
            for (int i = 0; i < table.selectors.size(); i++) {
                if (!"unique".equals(table.selectors.get(i).kind)) continue;
                out.append("if(unique").append(i).append("Changed)");
                appendUniqueProbeLoop(out, table.selectors.get(i), i, "Update",
                        "size()", "candidate", "lookup", "rows.update");
            }
            out.append("}finally{state.updateScratch(retained,required);}");
        }
        out.append("}\n");

        for (int i = 0; i < table.selectors.size(); i++) {
            SelectorSpec selector = table.selectors.get(i);
            if (!"unique".equals(selector.kind)) continue;
            appendUniqueModeMethods(out, table, selector, i, "Append", batch + " batch", "addBatch");
            appendUniqueModeMethods(out, table, selector, i, "Replacement", batch + " batch", "replaceAll");
            appendUniqueModeMethods(out, table, selector, i, "Update", "int[] lookup", "rows.update");
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
        out.append("{int uniqueCount=").append(count)
                .append(";HashCompositeKeySpace uniqueSpace=new HashCompositeKeySpace(uniqueCount);for(int ")
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
                .append(candidate).append(");}} ");
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

    private static String selectorMutatorValueOrLive(
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

    private static String selectorStorageValue(
            SelectorLeafSpec leaf, String value, String operation) {
        if ("float".equals(leaf.storageType)) {
            return "KeyCanonicalization.strictFloatStorage(TABLE," + q(leaf.path) + ","
                    + value + "," + q(operation) + ")";
        }
        if ("double".equals(leaf.storageType)) {
            return "KeyCanonicalization.strictDoubleStorage(TABLE," + q(leaf.path) + ","
                    + value + "," + q(operation) + ")";
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

    private static String selectorHashBits(String type, String value) {
        if ("boolean".equals(type)) return "(" + value + "?1L:0L)";
        if ("java.lang.String".equals(type)) return "(long)" + value + ".hashCode()";
        if ("float".equals(type)) return "(long)Float.floatToIntBits(" + value + ')';
        if ("double".equals(type)) return "Double.doubleToLongBits(" + value + ')';
        return "(long)(" + value + ')';
    }

    private static void appendSelectorArguments(SourceBuilder out, int parameters) {
        for (int i = 0; i < parameters; i++) out.append(",value").append(i);
    }

    private static void appendSelectorParameters(
            SourceBuilder out, List<SelectorParameter> parameters) {
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) out.append(',');
            out.append(parameters.get(i).publicType).append(" value").append(i);
        }
    }

    private static int selectorParameterLeafCount(SelectorSpec selector) {
        return "order".equals(selector.kind)
                ? Math.max(0, selector.leaves.size() - 1) : selector.leaves.size();
    }

    private static List<SelectorParameter> selectorParameters(
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

    private static String selectorMethodName(SelectorSpec selector) {
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
        return ("order".equals(selector.kind) ? "by" : "findBy") + suffix;
    }

    private static void appendSelectorComparison(
            SourceBuilder out, TableSpec table, SelectorSpec selector,
            String leftRow, String rightRow, int leaves, boolean rowToRow,
            String operation) {
        appendSelectorComparison(out, table, selector, leftRow, rightRow,
                leaves, rowToRow, operation, null);
    }

    private static void appendSelectorComparison(
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
                        + "," + value + "," + q(operation) + ")."
                        + relativeJavaPath(group.javaPath, groupedLeaf.javaName);
                if (groupedLeaf.enumType != null) {
                    value = "RuntimeFailures.requiredEnumValue(TABLE," + q(leaf.path) + ","
                            + value + "," + q(operation) + ").ordinal()";
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
                    + value + "," + q(operation) + ").ordinal()";
        }
        if ("float".equals(leaf.storageType)) {
            return "KeyCanonicalization.strictFloatStorage(TABLE," + q(leaf.path) + ","
                    + value + "," + q(operation) + ")";
        }
        if ("double".equals(leaf.storageType)) {
            return "KeyCanonicalization.strictDoubleStorage(TABLE," + q(leaf.path) + ","
                    + value + "," + q(operation) + ")";
        }
        return value;
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

    private static SelectorBinding selectorBinding(TableSpec table, SelectorLeafSpec selector) {
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
                .append(batch).append(" batch){HashCompositeKeySpace staged=newAppendValidationKeySpace(batch.size(),\"addBatch\");try{for(int row=0;row<batch.size();row++){String value=batch.")
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
                .append(" batch){HashCompositeKeySpace staged=newAppendValidationKeySpace(batch.size(),\"addBatch\");try{for(int row=0;row<batch.size();row++){long hash=");
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
            out.append("  public int findRowIndex(");
            appendValueLeafParameters(out, key);
            out.append("){state.checkActive(\"findRowIndex\");return compositeLookupLeaves(");
            appendValueLeafArguments(out, key);
            out.append(",\"findRowIndex\");}\n  public int rowIndexOf(");
            appendValueLeafParameters(out, key);
            out.append("){state.checkActive(\"rowIndexOf\");return keyRowLeaves(");
            appendValueLeafArguments(out, key);
            out.append(",\"rowIndexOf\");}\n");
            return;
        }
        ValueLeafSpec leaf = key.valueLeaves.get(0);
        String parameter = leaf.stem(key);
        out.append("  public int findRowIndex(").append(leaf.primitive).append(' ')
                .append(parameter)
                .append("){state.checkActive(\"findRowIndex\");return keySpace.rowOf(")
                .append(key.keySpaceValueFromStorage(parameter, "findRowIndex"))
                .append(");}\n  public int rowIndexOf(").append(leaf.primitive).append(' ')
                .append(parameter)
                .append("){state.checkActive(\"rowIndexOf\");int row=keySpace.rowOf(")
                .append(key.keySpaceValueFromStorage(parameter, "rowIndexOf"))
                .append(");if(row<0)throw RuntimeFailures.missingValueKey(TABLE,")
                .append(q(key.logicalName)).append(",\"rowIndexOf\");return row;}\n");
    }

    private static void appendKeySpaceLifecycleRuntime(
            SourceBuilder out, FieldSpec key) {
        String validationType = key.appendValidationKeySpaceType();
        out.append("  private ").append(key.keySpaceType())
                .append(" newKeySpace(int expected,String operation){long bytes=RuntimeCompatibility.estimatedKeySpaceBytes(runtimePlan().requireTable(TABLE),")
                .append(q(key.keySpaceImplementation())).append(',')
                .append(key.sparseIntEligible() ? "true" : "false")
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
                .append("  private void ensureAppendKeyCapacity(int additional,String operation){long previous=keySpace.retainedBytes(),proposed=keySpace.retainedBytesAfterEnsureAdditional(additional),allocation=keySpace.allocationBytesDuringEnsureAdditional(additional);state.preflightAppendStorage(additional,proposed,operation);state.preflightKeySpaceStorage(proposed,operation);state.reserveBulkScratch(allocation,operation);try{keySpace.ensureAdditionalCapacity(additional);}catch(RuntimeException failure){state.releaseBulkScratch(allocation,operation);throw failure;}catch(Error failure){state.releaseBulkScratch(allocation,operation);throw failure;}state.releaseBulkScratch(allocation,operation);if(keySpace.retainedBytes()!=proposed)throw RuntimeFailures.internalInvariant(\"append_key_capacity\",TABLE,operation);state.commitKeySpaceStorage(previous,proposed,operation);}\n");
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
                        .append(q(field.logicalName)).append(",value,\"rows.update\");");
                for (int leafIndex = 0; leafIndex < field.valueLeaves.size(); leafIndex++) {
                    ValueLeafSpec leaf = field.valueLeaves.get(leafIndex);
                    out.append(updateScratch(fieldIndex, leafIndex))
                            .append("[row]=").append(leaf.storageValue(field,
                                    "required." + leaf.javaName, "rows.update"))
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
                        .append(field.storageValue("value", "rows.update")).append(';');
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
                                .append(q(field.logicalName)).append(",\"rows.update.leaf\");");
                    }
                    out.append(target).append("[row]=")
                            .append(leaf.storageValue(field, "value", "rows.update"))
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
        out.append("  }\n  void commitMutator(int row,long epoch,").append(mutator).append(" mutation){ownership.preflightMutation(\"mutator.commit\");state.checkRowIndex(row,\"mutator.commit\");if(epoch!=structuralEpoch())throw RuntimeFailures.staleMutator(TABLE,epoch,structuralEpoch());state.beginOperation(\"mutator.commit\");try{validateMutatorValues(mutation);validateSelectorMutator(mutation);validateUniqueMutator(row,mutation);boolean changed=false;\n");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("    boolean selector").append(i)
                    .append("Changed=selector").append(i)
                    .append("ChangedByMutator(row,mutation);\n");
        }
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
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("    if(selector").append(i).append("Changed)markSelector")
                    .append(i).append("Dirty();\n");
        }
        out.append("    state.endOperationSuccess(\"mutator.commit\",1L,1L,changed?1L:0L);}catch(SomaRuntimeException failure){state.endOperationFailure(\"mutator.commit\",0L,0L,failure.code());throw failure;}catch(RuntimeException failure){state.abortOperation(\"mutator.commit\");throw failure;}catch(Error failure){state.abortOperation(\"mutator.commit\");throw failure;}}\n");
    }

    private void appendUpdateScratch(SourceBuilder out, TableSpec table) {
        out.append("  int[] prepareCandidateScratch(){int required=size();long bytes=4L*(long)required+updateBytes(updateScratchCapacity);long limit=runtimePlan().requireTable(TABLE).maximumUpdateScratchBytes();if(bytes>limit)throw RuntimeFailures.memoryLimitExceeded(TABLE,\"rows.update\",limit,bytes);state.preflightUpdateScratch(bytes,\"rows.update\");if(candidateScratch.length<required)candidateScratch=Arrays.copyOf(candidateScratch,required);state.updateScratch(4L*(long)candidateScratch.length+updateBytes(updateScratchCapacity),4L*(long)candidateScratch.length+updateBytes(updateScratchCapacity));return candidateScratch;}\n")
                .append("  void prepareUpdateScratch(int required){if(required<=updateScratchCapacity)return;long bytes=4L*(long)candidateScratch.length+updateBytes(required);long limit=runtimePlan().requireTable(TABLE).maximumUpdateScratchBytes();if(bytes>limit)throw RuntimeFailures.memoryLimitExceeded(TABLE,\"rows.update\",limit,bytes);state.preflightUpdateScratch(bytes,\"rows.update\");\n");
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
        out.append("  }}\n  long publishUpdate(int[] rows,int count){validateSelectorUpdate(count);validateUniqueUpdate(rows,count);long changed=0L;");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("boolean selector").append(i).append("Changed=selector")
                    .append(i).append("ChangedByUpdate(rows,count);");
        }
        out.append("for(int i=0;i<count;i++){int row=rows[i];boolean rowChanged=false;\n");
        for (int fieldIndex = 0; fieldIndex < table.fields.size(); fieldIndex++) {
            FieldSpec field = table.fields.get(fieldIndex);
            if (field.key) {
                continue;
            }
            String different = field.flattenedValueStorage()
                    ? flattenedScratchDifferent(table, field, "row", "i", "rows.update")
                    : field.valueBacked()
                            ? scalarValueDifferent(table, field,
                                    field.javaName + "Column.get(row)",
                                    updateScratch(fieldIndex) + "[i]", "rows.update")
                    : field.enumType != null
                            ? field.javaName + "Column.get(row)!="
                                    + updateScratch(fieldIndex) + "[i]"
                            : accessAwareDifferent(table, field, field.javaName + "Value(row)",
                                    updateScratch(fieldIndex) + "[i]", "rows.update");
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
                        stored = canonicalAccessStorage(accessLeaf, stored, "rows.update");
                    }
                    out.append("    ").append(leaf.physicalName(field)).append("Column.set(row,")
                            .append(stored).append(");\n");
                }
            } else {
                String stored = field.enumType == null
                        ? field.storageValue(updateScratch(fieldIndex) + "[i]", "rows.update")
                        : updateScratch(fieldIndex) + "[i]";
                SelectorLeafSpec accessLeaf = selectorLeaf(table, field.logicalName);
                if (accessLeaf != null) {
                    stored = canonicalAccessStorage(accessLeaf, stored, "rows.update");
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
        out.append("    if(rowChanged)changed++;}");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("if(selector").append(i).append("Changed)markSelector")
                    .append(i).append("Dirty();");
        }
        out.append("return changed;}\n")
                .append("  private void releaseRetainedScratch(){candidateScratch=new int[0];pipelineScratch=new int[0];sortScratch=new int[0];removeMarks=new boolean[0];updateScratchCapacity=0;");
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
        out.append("  RemoveResult removeSelected(int[] selected,int count,long scanned,long sidecarMaintained,long sidecarRebuilt,String operation){state.preflightStructuralOperation(operation);int previous=size();if(count<0||count>previous)throw RuntimeFailures.internalInvariant(\"remove_selection_count\",TABLE,operation);if(removeMarks.length<previous){requireOperationScratch(pipelineScratch.length,sortScratch.length,previous,operation);removeMarks=Arrays.copyOf(removeMarks,previous);recordOperationScratch();}Arrays.fill(removeMarks,0,previous,false);for(int i=0;i<count;i++){int row=selected[i];if(row<0||row>=previous||removeMarks[row])throw RuntimeFailures.internalInvariant(\"remove_selection_identity\",TABLE,operation);removeMarks[row]=true;}");
        if (!table.children.isEmpty()) {
            out.append("beginRetireSelection(selected,count,operation,true);releaseRetired(false,operation);");
        }
        if (table.keyed()) {
            FieldSpec key = table.keyField();
            if (key.compositeKey()) {
                out.append("    for(int row=0;row<previous;row++)if(removeMarks[row]){int slot=compositeStoredSlot(row,operation);if(slot<0)throw RuntimeFailures.internalInvariant(\"composite_key_missing\",TABLE,operation);keySpace.removeAt(slot);}\n");
            } else {
                out.append("    for(int row=0;row<previous;row++)if(removeMarks[row])keySpace.remove(")
                        .append(key.valueBacked()
                                ? key.keySpaceValueFromStorage(key.javaName + "Column.get(row)", "rows.remove")
                                : key.keySpaceValue(key.javaName + "Value(row)", "rows.remove"))
                        .append(");\n");
            }
        }
        out.append("int write=0;long compacted=0L;for(int read=0;read<previous;read++){if(removeMarks[read])continue;if(write!=read){\n");
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
                                ? key.keySpaceValueFromStorage(key.javaName + "Column.get(read)", "rows.remove")
                                : key.keySpaceValue(key.javaName + "Value(read)", "rows.remove"))
                        .append(",write);\n");
            }
        }
        out.append("    compacted++;}write++;}clearColumns(write,previous);state.commitStructuralRemove(previous,write,operation);");
        out.append("if(count!=0)markSelectorSidecarsDirty();return state.removeResult(scanned,count,count,compacted,sidecarMaintained,sidecarRebuilt);}\n");
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

    private static String q(String value) {
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

    private static final class SelectorBinding {
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

    private static final class SelectorParameter {
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
            return sparseIntEligible() ? "IntKeySpace" : "HashIntKeySpace";
        }

        boolean sparseIntEligible() {
            return key && valueType == null && enumType == null
                    && "int".equals(primitive);
        }

        String keySpaceConstruction(String plan, String expectedSize) {
            return sparseIntEligible()
                    ? "RuntimeCompatibility.createIntKeySpace(" + plan + "," + expectedSize + ")"
                    : "new " + keySpaceType() + "(" + expectedSize + ")";
        }

        String appendValidationKeySpaceType() {
            return sparseIntEligible() ? "HashIntKeySpace" : keySpaceType();
        }

        String appendValidationKeySpaceConstruction(String expectedSize) {
            return "new " + appendValidationKeySpaceType() + "(" + expectedSize + ")";
        }

        String appendValidationKeySpaceImplementation() {
            if ("HashCompositeKeySpace".equals(appendValidationKeySpaceType())) {
                return "hash-composite-v1";
            }
            if ("HashLongKeySpace".equals(appendValidationKeySpaceType())) {
                return "hash-long-v1";
            }
            return "hash-int-v1";
        }

        String requireInsertKey(String space, String key, String operation) {
            return sparseIntEligible()
                    ? space + ".requireInsertKey(" + key + ",TABLE,"
                            + q(logicalName) + "," + q(operation) + ");"
                    : "";
        }

        String keySpaceImplementation() {
            if (compositeKey()) return "hash-composite-v1";
            if ("long".equals(storagePrimitive) || "double".equals(storagePrimitive)) {
                return "hash-long-v1";
            }
            return "hash-int-v1";
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

        String columnPipelineType() {
            return enumType == null ? cap(primitive) + "ColumnPipeline"
                    : "EnumColumnPipeline<" + enumType + ">";
        }

        String columnViewType() {
            return enumType == null ? cap(primitive) + "ColumnView"
                    : "EnumColumnView<" + enumType + ">";
        }

        String columnPipelineConstruction(String presence) {
            if (enumType == null) {
                return "GeneratedColumnAccess." + primitive + "Pipeline(state,"
                        + javaName + "Column," + presence
                        + ",TABLE," + q(logicalName) + ")";
            }
            return "GeneratedColumnAccess.enumPipeline(state," + javaName + "Column,"
                    + presence + ",TABLE," + q(logicalName) + "," + enumConstantsName() + ")";
        }

        String columnViewConstruction(String presence) {
            if (enumType == null) {
                return "GeneratedColumnAccess." + primitive + "View(state,"
                        + javaName + "Column," + presence
                        + ",TABLE," + q(logicalName) + ")";
            }
            return "GeneratedColumnAccess.enumView(state," + javaName + "Column,"
                    + presence + ",TABLE," + q(logicalName) + "," + enumConstantsName() + ")";
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

        String columnPipelineType() {
            return enumType == null ? cap(primitive) + "ColumnPipeline"
                    : "EnumColumnPipeline<" + enumType + ">";
        }

        String columnViewType() {
            return enumType == null ? cap(primitive) + "ColumnView"
                    : "EnumColumnView<" + enumType + ">";
        }

        String columnPipelineConstruction(FieldSpec owner, String presence) {
            if (enumType == null) {
                return "GeneratedColumnAccess." + primitive + "Pipeline(state,"
                        + columnName(owner) + "," + presence + ",TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ")";
            }
            return "GeneratedColumnAccess.enumPipeline(state," + columnName(owner) + ","
                    + presence + ",TABLE," + q(owner.logicalName + "." + logicalName)
                    + "," + enumConstantsName(owner) + ")";
        }

        String columnViewConstruction(FieldSpec owner, String presence) {
            if (enumType == null) {
                return "GeneratedColumnAccess." + primitive + "View(state,"
                        + columnName(owner) + "," + presence + ",TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ")";
            }
            return "GeneratedColumnAccess.enumView(state," + columnName(owner) + ","
                    + presence + ",TABLE," + q(owner.logicalName + "." + logicalName)
                    + "," + enumConstantsName(owner) + ")";
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
