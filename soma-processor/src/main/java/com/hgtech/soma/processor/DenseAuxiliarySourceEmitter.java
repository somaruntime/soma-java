package com.hgtech.soma.processor;

import java.util.ArrayList;
import java.util.List;

import static com.hgtech.soma.processor.DenseSourceNames.*;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.*;
import static com.hgtech.soma.processor.DenseTableCodegenModel.*;
import static com.hgtech.soma.processor.DenseValueSourceSupport.*;

/** Emits the Cursor, UpdateCursor, Batch, Mutator, KeyTraversal and Scan facades. */
final class DenseAuxiliarySourceEmitter {
    private final String generatedPackage;
    private final List<TableSpec> schemaTables;

    DenseAuxiliarySourceEmitter(String generatedPackage, List<TableSpec> schemaTables) {
        this.generatedPackage = generatedPackage;
        this.schemaTables = new ArrayList<TableSpec>(schemaTables);
    }

    private String header() {
        return "// SOMA-GENERATED: soma-processor-v1\npackage "
                + generatedPackage + ";\n\n";
    }

    private String childKeyEquality(ChildSpec child, String left, String right) {
        FieldSpec key = requireTable(schemaTables, child.tableLogicalName).keyField();
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

    String cursorSource(TableSpec table) {
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

    String updateCursorSource(TableSpec table) {
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

    String batchSource(TableSpec table) {
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
        appendBatchDeltaSupport(out, table);
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

    String mutatorSource(TableSpec table) {
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

    String keyTraversalSource(TableSpec table) {
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

    String scanSource(TableSpec table) {
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

    private static void appendBatchDeltaSupport(
            SourceBuilder out, TableSpec table) {
        String batch = table.name("Batch");
        String tableType = table.name("Table");
        out.append("  static ").append(batch).append(" snapshotOf(")
                .append(tableType)
                .append(" source){if(source==null)throw new NullPointerException(\"source\");int count=source.size();")
                .append(batch).append(" result=new ").append(batch)
                .append("(count);for(int row=0;row<count;row++)result.appendFromTable(source,row);return result;}\n")
                .append("  private void appendFromTable(").append(tableType)
                .append(" source,int row){ensureOne();");
        for (FieldSpec field : table.fields) {
            String c = cap(field.javaName);
            if (field.optional) {
                out.append("if(source.").append(field.javaName)
                        .append("Present(row))set").append(c)
                        .append("(size,source.").append(field.javaName)
                        .append("Value(row));else set").append(c)
                        .append("Absent(size);");
            } else {
                out.append("set").append(c).append("(size,source.")
                        .append(field.javaName).append("Value(row));");
            }
        }
        for (ChildSpec child : table.children) {
            String c = cap(child.javaName);
            out.append("boolean ").append(child.javaName)
                    .append("IsPresent=source.dataFlow").append(c)
                    .append("PresentAt(row);")
                    .append(child.tableType()).append(' ').append(child.javaName)
                    .append("Table=source.dataFlow").append(c).append("At(row);")
                    .append(child.batchType()).append(" staged").append(c)
                    .append('=').append(child.javaName)
                    .append("IsPresent?(").append(child.javaName)
                    .append("Table==null?new ").append(child.batchType())
                    .append("(0):").append(child.batchType()).append(".snapshotOf(")
                    .append(child.javaName).append("Table)):null;")
                    .append(child.javaName).append("Values[size]=staged")
                    .append(c).append(';').append(child.javaName)
                    .append("Present[size]=").append(child.javaName)
                    .append("IsPresent;");
        }
        out.append("size++;}\n")
                .append("  void appendFrom(").append(batch)
                .append(" source,int sourceRow){checkSourceRow(source,sourceRow);ensureOne();copyRow(source,sourceRow,size,true);size++;}\n")
                .append("  void replaceFrom(int targetRow,").append(batch)
                .append(" source,int sourceRow){if(targetRow<0||targetRow>=size)throw new IndexOutOfBoundsException(\"target row: \"+targetRow);checkSourceRow(source,sourceRow);copyRow(source,sourceRow,targetRow,true);}\n")
                .append("  void swapRemove(int row){if(row<0||row>=size)throw new IndexOutOfBoundsException(\"row: \"+row);int last=size-1;if(row!=last)copyRow(this,last,row,false);clearRow(last);size=last;}\n")
                .append("  private static void checkSourceRow(").append(batch)
                .append(" source,int row){if(source==null)throw new NullPointerException(\"source\");if(row<0||row>=source.size)throw new IndexOutOfBoundsException(\"source row: \"+row);}\n")
                .append("  private void copyRow(").append(batch)
                .append(" source,int sourceRow,int targetRow,boolean detachChildren){");
        for (FieldSpec field : table.fields) {
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    String physical = leaf.physicalName(field);
                    out.append(physical).append("Values[targetRow]=source.")
                            .append(physical).append("Values[sourceRow];");
                }
            } else {
                out.append(field.javaName)
                        .append("Values[targetRow]=source.")
                        .append(field.javaName).append("Values[sourceRow];");
            }
            if (field.optional) {
                out.append("setPresent(").append(field.javaName)
                        .append("Presence,targetRow,present(source.")
                        .append(field.javaName).append("Presence,sourceRow));");
            }
        }
        for (ChildSpec child : table.children) {
            out.append(child.batchType()).append(" source")
                    .append(cap(child.javaName)).append("=source.")
                    .append(child.javaName).append("Values[sourceRow];")
                    .append(child.javaName).append("Values[targetRow]=source")
                    .append(cap(child.javaName))
                    .append("==null?null:(detachChildren?source")
                    .append(cap(child.javaName)).append(".copy():source")
                    .append(cap(child.javaName)).append(");")
                    .append(child.javaName).append("Present[targetRow]=source.")
                    .append(child.javaName).append("Present[sourceRow];");
        }
        out.append("}\n  private void clearRow(int row){");
        for (FieldSpec field : table.fields) {
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    if ("java.lang.String".equals(leaf.storagePrimitive)) {
                        out.append(leaf.physicalName(field))
                                .append("Values[row]=null;");
                    }
                }
            } else if ("java.lang.String".equals(field.storagePrimitive)) {
                out.append(field.javaName).append("Values[row]=null;");
            }
            if (field.optional) {
                out.append("setPresent(").append(field.javaName)
                        .append("Presence,row,false);");
            }
        }
        for (ChildSpec child : table.children) {
            out.append(child.javaName).append("Values[row]=null;")
                    .append(child.javaName).append("Present[row]=false;");
        }
        out.append("}\n");
        if (table.keyed()) {
            appendBatchKeyMatch(out, table.keyField());
        }
    }

    private static void appendBatchKeyMatch(
            SourceBuilder out, FieldSpec key) {
        out.append("  boolean keyMatches(int row,").append(key.primitive)
                .append(" key){return ");
        if (key.valueBacked()) {
            out.append("key!=null&&");
            if (!key.flattenedValueStorage()) {
                ValueLeafSpec leaf = key.valueLeaves.get(0);
                String input = leaf.keyInputStorage(
                        key, "key." + leaf.javaName, q("delta.apply"));
                out.append(leaf.keyEqual(
                        key,
                        key.javaName + "Values[row]",
                        input,
                        q("delta.apply")));
            } else {
                for (int index = 0;
                        index < key.valueLeaves.size();
                        index++) {
                    if (index > 0) {
                        out.append("&&");
                    }
                    ValueLeafSpec leaf = key.valueLeaves.get(index);
                    String input = leaf.keyInputStorage(
                            key,
                            "key." + leaf.javaName,
                            q("delta.apply"));
                    out.append(leaf.keyEqual(
                            key,
                            leaf.physicalName(key) + "Values[row]",
                            input,
                            q("delta.apply")));
                }
            }
        } else if (key.enumType != null) {
            out.append(key.javaName).append("Values[row]==RuntimeFailures.requiredEnumValue(TABLE,")
                    .append(q(key.logicalName))
                    .append(",key,\"delta.apply\").ordinal()");
        } else if ("java.lang.String".equals(key.primitive)) {
            out.append(key.javaName)
                    .append("Values[row].equals(RuntimeFailures.requiredValue(TABLE,")
                    .append(q(key.logicalName)).append(",key,\"delta.apply\"))");
        } else {
            out.append(key.javaName).append("Values[row]==")
                    .append(key.storageValue("key", "delta.apply"));
        }
        out.append(";}\n");
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

}
