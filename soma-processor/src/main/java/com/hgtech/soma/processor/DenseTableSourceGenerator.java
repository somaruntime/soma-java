package com.hgtech.soma.processor;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/** Deterministic emitter for the Phase 1 schema-specific dense facade. */
final class DenseTableSourceGenerator {
    private final Filer filer;
    private final String generatedPackage;
    private final String schemaHash;

    DenseTableSourceGenerator(Filer filer, String generatedPackage, String schemaHash) {
        this.filer = filer;
        this.generatedPackage = generatedPackage;
        this.schemaHash = schemaHash;
    }

    void generate(TableSpec table) throws IOException {
        write(table.name("Row"), rowSource(table), table.origin);
        write(table.name("MutableRow"), mutableRowSource(table), table.origin);
        write(table.name("Batch"), batchSource(table), table.origin);
        write(table.name("Mutator"), mutatorSource(table), table.origin);
        write(table.name("Rows"), rowsSource(table), table.origin);
        if (table.keyed()) {
            write(table.name("Keys"), keysSource(table), table.origin);
        }
        write(table.name("Table"), tableSource(table), table.origin);
    }

    private void write(String simpleName, String source, Element origin) throws IOException {
        JavaFileObject file = filer.createSourceFile(generatedPackage + "." + simpleName, origin);
        Writer writer = file.openWriter();
        try {
            writer.write(source);
        } finally {
            writer.close();
        }
    }

    private String header() {
        return "package " + generatedPackage + ";\n\n";
    }

    private String rowSource(TableSpec table) {
        StringBuilder out = new StringBuilder(header());
        out.append("public interface ").append(table.name("Row")).append(" {\n");
        for (FieldSpec field : table.fields) {
            if (field.optional) {
                out.append("  boolean ").append(field.javaName).append("Present();\n")
                        .append("  boolean ").append(field.javaName).append("Absent();\n");
            }
            out.append("  ").append(field.primitive).append(' ')
                    .append(field.javaName).append("();\n");
            if (field.optional) {
                out.append("  ").append(field.primitive).append(' ')
                        .append(field.javaName).append("Or(")
                        .append(field.primitive).append(" defaultValue);\n");
            }
        }
        return out.append("}\n").toString();
    }

    private String mutableRowSource(TableSpec table) {
        StringBuilder out = new StringBuilder(header());
        out.append("public interface ").append(table.name("MutableRow"))
                .append(" extends ").append(table.name("Row")).append(" {\n");
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            out.append("  void set").append(cap(field.javaName)).append('(')
                    .append(field.primitive).append(" value);\n");
            if (field.optional) {
                out.append("  void clear").append(cap(field.javaName)).append("();\n");
            }
        }
        return out.append("}\n").toString();
    }

    private String batchSource(TableSpec table) {
        String batch = table.name("Batch");
        StringBuilder out = new StringBuilder(header());
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
        out.append("  }\n\n  public int size() { return size; }\n")
                .append("  public int capacity() { return capacity; }\n")
                .append("  public boolean isEmpty() { return size == 0; }\n\n")
                .append("  public ").append(batch).append(" add(").append(table.carrierType)
                .append(" detachedRow) {\n")
                .append("    if (detachedRow == null) throw new NullPointerException(\"detachedRow\");\n")
                .append("    ensureOne();\n");
        for (FieldSpec field : table.fields) {
            if (field.optional) {
                out.append("    if (detachedRow.").append(field.javaName).append(" == null) {\n")
                        .append("      set").append(cap(field.javaName)).append("Absent(size);\n")
                        .append("    } else {\n      set").append(cap(field.javaName))
                        .append("(size, detachedRow.").append(field.javaName).append('.')
                        .append(field.unboxMethod()).append("());\n    }\n");
            } else {
                out.append("    set").append(cap(field.javaName)).append("(size, detachedRow.")
                        .append(field.javaName).append(");\n");
            }
        }
        out.append("    size++;\n    return this;\n  }\n\n")
                .append("  public ").append(batch).append(" addValues(Writer writer) {\n")
                .append("    if (writer == null) throw new NullPointerException(\"writer\");\n")
                .append("    BuilderImpl row = new BuilderImpl();\n")
                .append("    try { writer.write(row); } catch (Error error) { row.close(); throw error; }")
                .append(" catch (RuntimeException failure) { row.close(); throw RuntimeFailures.callbackFailed(TABLE, \"batch.addValues\", \"writer\", failure); }\n")
                .append("    row.close();\n");
        for (FieldSpec field : table.fields) {
            if (!field.optional) {
                out.append("    if (!row.").append(field.javaName).append("Assigned) throw RuntimeFailures.missingRequiredField(TABLE, ")
                        .append(q(field.logicalName)).append(", \"batch.addValues\");\n");
            }
        }
        out.append("    ensureOne();\n");
        for (FieldSpec field : table.fields) {
            if (field.optional) {
                out.append("    if (row.").append(field.javaName).append("Present) set")
                        .append(cap(field.javaName)).append("(size, row.").append(field.javaName)
                        .append("Value); else set").append(cap(field.javaName)).append("Absent(size);\n");
            } else {
                out.append("    set").append(cap(field.javaName)).append("(size, row.")
                        .append(field.javaName).append("Value);\n");
            }
        }
        out.append("    size++;\n    return this;\n  }\n\n");

        if (table.directSlots() <= 254) {
            out.append("  public ").append(batch).append(" addValues(");
            appendDirectParameters(out, table);
            out.append(") {\n    ensureOne();\n");
            for (FieldSpec field : table.fields) {
                if (field.optional) {
                    out.append("    if (").append(field.javaName).append("Present) set")
                            .append(cap(field.javaName)).append("(size, ").append(field.javaName)
                            .append("Value); else set").append(cap(field.javaName)).append("Absent(size);\n");
                } else {
                    out.append("    set").append(cap(field.javaName)).append("(size, ")
                            .append(field.javaName).append(");\n");
                }
            }
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
            }
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
                out.append("  private void set").append(c).append("Absent(int row) { ")
                        .append(field.javaName).append("Values[row] = ").append(field.zero()).append("; setPresent(")
                        .append(field.javaName).append("Presence, row, false); }\n")
                        .append("  boolean ").append(field.javaName).append("Present(int row) { return present(")
                        .append(field.javaName).append("Presence, row); }\n");
            }
        }
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
        out.append("  }\n\n  private static final class BuilderImpl implements RowBuilder {\n")
                .append("    private boolean active = true;\n");
        for (FieldSpec field : table.fields) {
            out.append("    private ").append(field.primitive).append(' ')
                    .append(field.javaName).append("Value;\n")
                    .append("    private boolean ").append(field.javaName)
                    .append(field.optional ? "Present;\n" : "Assigned;\n");
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
        return out.append("    private void close() { active = false; }\n")
                .append("    private void check() { if (!active) throw RuntimeFailures.internalInvariant(\"escaped_batch_row_builder\", TABLE, \"batch.addValues\"); }\n")
                .append("  }\n}\n").toString();
    }

    private String mutatorSource(TableSpec table) {
        String name = table.name("Mutator");
        StringBuilder out = new StringBuilder(header());
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
            if (field.optional) out.append("  private boolean ").append(field.javaName).append("Present;\n");
        }
        out.append("\n  ").append(name).append('(').append(table.name("Table"))
                .append(" table, int rowIndex, long epoch) {\n    this.table = table; this.rowIndex = rowIndex; this.epoch = epoch;\n");
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            out.append("    this.").append(field.javaName).append("Value = table.")
                    .append(field.javaName).append("Value(rowIndex);\n");
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
                    .append(field.javaName).append("Value = value;");
            if (field.optional) out.append(' ').append(field.javaName).append("Present = true;");
            out.append(" return this; }\n");
            if (field.optional) {
                out.append("  public ").append(name).append(" clear").append(c)
                        .append("() { check(); ").append(field.javaName).append("Value = ")
                        .append(field.zero()).append("; ").append(field.javaName)
                        .append("Present = false; return this; }\n");
            }
            out.append("  ").append(field.primitive).append(' ').append(field.javaName)
                    .append("Value() { return ").append(field.javaName).append("Value; }\n");
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
        StringBuilder out = new StringBuilder(header());
        out.append("import com.hgtech.soma.runtime.SomaRuntimeException;\n")
                .append("import com.hgtech.soma.runtime.generated.RuntimeFailures;\n")
                .append("import java.util.ArrayList;\nimport java.util.List;\nimport java.util.Optional;\n")
                .append("import java.util.function.Consumer;\n\n")
                .append("public final class ").append(name).append(" {\n")
                .append("  private final ").append(tableName).append(" table;\n")
                .append("  ").append(name).append('(').append(tableName).append(" table){this.table=table;}\n")
                .append("  public void forEach(Consumer<").append(key.boxed).append("> consumer){if(consumer==null)throw new NullPointerException(\"consumer\");table.begin(\"keys.forEach\");long scanned=0L;try{int size=table.size();for(int row=0;row<size;row++){scanned++;try{consumer.accept(").append(key.boxValue("table." + key.javaName + "Value(row)")).append(");}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"keys.forEach\",\"consumer\",callback);}}table.endSuccess(\"keys.forEach\",scanned,scanned,0L);}catch(SomaRuntimeException failure){table.endFailure(\"keys.forEach\",scanned,scanned,failure.code());throw failure;}catch(Error failure){table.endFailure(\"keys.forEach\",scanned,scanned,\"callback_failed\");throw failure;}}\n")
                .append("  public List<").append(key.boxed).append("> fetchAll(){table.begin(\"keys.fetchAll\");long scanned=0L;try{int size=table.size();List<").append(key.boxed).append("> result=new ArrayList<").append(key.boxed).append(">(size);for(int row=0;row<size;row++){scanned++;result.add(").append(key.boxValue("table." + key.javaName + "Value(row)")).append(");}table.endSuccess(\"keys.fetchAll\",scanned,scanned,0L);return result;}catch(SomaRuntimeException failure){table.endFailure(\"keys.fetchAll\",scanned,scanned,failure.code());throw failure;}catch(Error failure){table.endFailure(\"keys.fetchAll\",scanned,scanned,\"callback_failed\");throw failure;}}\n")
                .append("  public Optional<").append(key.boxed).append("> findFirst(){table.begin(\"keys.findFirst\");try{if(table.size()==0){table.endSuccess(\"keys.findFirst\",0L,0L,0L);return Optional.empty();}").append(key.boxed).append(" result=").append(key.boxValue("table." + key.javaName + "Value(0)")).append(";table.endSuccess(\"keys.findFirst\",1L,1L,0L);return Optional.of(result);}catch(SomaRuntimeException failure){table.endFailure(\"keys.findFirst\",0L,0L,failure.code());throw failure;}catch(Error failure){table.endFailure(\"keys.findFirst\",0L,0L,\"callback_failed\");throw failure;}}\n")
                .append("  public ").append(key.boxed).append(" firstOrThrow(){table.begin(\"keys.firstOrThrow\");try{if(table.size()==0)throw RuntimeFailures.emptyResult(").append(q(table.logicalName)).append(",\"keys.firstOrThrow\");").append(key.boxed).append(" result=").append(key.boxValue("table." + key.javaName + "Value(0)")).append(";table.endSuccess(\"keys.firstOrThrow\",1L,1L,0L);return result;}catch(SomaRuntimeException failure){table.endFailure(\"keys.firstOrThrow\",0L,0L,failure.code());throw failure;}catch(Error failure){table.endFailure(\"keys.firstOrThrow\",0L,0L,\"callback_failed\");throw failure;}}\n")
                .append("}\n");
        return out.toString();
    }

    private String rowsSource(TableSpec table) {
        String rows = table.name("Rows");
        String row = table.name("Row");
        String mutable = table.name("MutableRow");
        StringBuilder out = new StringBuilder(header());
        out.append("import com.hgtech.soma.runtime.RemoveResult;\n")
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
                .append("  public long count(){start(\"rows.count\");try{Selection s=select(Integer.MAX_VALUE);table.endSuccess(\"rows.count\",s.scanned,s.length,0L);return s.length;}catch(SomaRuntimeException f){table.endFailure(\"rows.count\",attemptedScanned,attemptedMatched,f.code());throw f;}catch(RuntimeException f){table.abort(\"rows.count\");throw f;}catch(Error f){table.abort(\"rows.count\");throw f;}}\n")
                .append("  public boolean anyMatch(Predicate value){return matchTerminal(value,true);}\n")
                .append("  public boolean noneMatch(Predicate value){return !matchTerminal(value,false);}\n")
                .append("  private boolean matchTerminal(Predicate value,boolean any){if(value==null)throw new NullPointerException(\"predicate\");String op=any?\"rows.anyMatch\":\"rows.noneMatch\";start(op);long reached=0L,scanned=0L;try{Cursor c=new Cursor(table);if(!hasSort()){int initial=source==null?table.size():source.size();long[] seen=new long[kinds.length];for(int position=0;position<initial&&!limitReached(seen,0,kinds.length);position++){int rowIndex=source==null?position:source.rowAt(position);scanned++;if(matches(rowIndex,c,seen,0,kinds.length)){reached++;if(test(value,c,rowIndex,op)){table.endSuccess(op,scanned,reached,0L);return true;}}}table.endSuccess(op,scanned,reached,0L);return false;}Selection s=select(Integer.MAX_VALUE);scanned=s.scanned;for(int i=0;i<s.length;i++){reached++;if(test(value,c,s.rows[i],op)){table.endSuccess(op,scanned,reached,0L);return true;}}table.endSuccess(op,scanned,reached,0L);return false;}catch(SomaRuntimeException f){table.endFailure(op,scanned==0L?attemptedScanned:scanned,reached==0L?attemptedMatched:reached,f.code());throw f;}catch(RuntimeException f){table.abort(op);throw f;}catch(Error f){table.abort(op);throw f;}}\n")
                .append("  public void forEach(Consumer value){if(value==null)throw new NullPointerException(\"consumer\");start(\"rows.forEach\");long reached=0L;Selection s=null;try{s=select(Integer.MAX_VALUE);Cursor c=new Cursor(table);for(int i=0;i<s.length;i++){reached++;c.open(s.rows[i]);try{value.accept(c);}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"rows.forEach\",\"consumer\",callback);}finally{c.close();}}table.endSuccess(\"rows.forEach\",s.scanned,reached,0L);}catch(SomaRuntimeException f){table.endFailure(\"rows.forEach\",s==null?attemptedScanned:s.scanned,reached==0L&&s==null?attemptedMatched:reached,f.code());throw f;}catch(RuntimeException f){table.abort(\"rows.forEach\");throw f;}catch(Error f){table.abort(\"rows.forEach\");throw f;}}\n")
                .append("  public Optional<").append(table.carrierType).append("> findFirst(){start(\"rows.findFirst\");try{Selection s=select(1);Optional<").append(table.carrierType).append("> result=s.length==0?Optional.<").append(table.carrierType).append(">empty():Optional.of(table.materializeRow(s.rows[0]));table.endSuccess(\"rows.findFirst\",s.scanned,s.length,0L);return result;}catch(SomaRuntimeException f){table.endFailure(\"rows.findFirst\",attemptedScanned,attemptedMatched,f.code());throw f;}catch(RuntimeException f){table.abort(\"rows.findFirst\");throw f;}catch(Error f){table.abort(\"rows.findFirst\");throw f;}}\n")
                .append("  public ").append(table.carrierType).append(" firstOrThrow(){start(\"rows.firstOrThrow\");try{Selection s=select(1);if(s.length==0)throw RuntimeFailures.emptyResult(sourcePath(),\"rows.firstOrThrow\");").append(table.carrierType).append(" result=table.materializeRow(s.rows[0]);table.endSuccess(\"rows.firstOrThrow\",s.scanned,1L,0L);return result;}catch(SomaRuntimeException f){table.endFailure(\"rows.firstOrThrow\",attemptedScanned,attemptedMatched,f.code());throw f;}catch(RuntimeException f){table.abort(\"rows.firstOrThrow\");throw f;}catch(Error f){table.abort(\"rows.firstOrThrow\");throw f;}}\n")
                .append("  public List<").append(table.carrierType).append("> fetchAll(){start(\"rows.fetchAll\");try{Selection s=select(Integer.MAX_VALUE);List<").append(table.carrierType).append("> result=table.materializeRows(s.rows,s.length);table.endSuccess(\"rows.fetchAll\",s.scanned,s.length,0L);return result;}catch(SomaRuntimeException f){table.endFailure(\"rows.fetchAll\",attemptedScanned,attemptedMatched,f.code());throw f;}catch(RuntimeException f){table.abort(\"rows.fetchAll\");throw f;}catch(Error f){table.abort(\"rows.fetchAll\");throw f;}}\n")
                .append("  public int[] rowIndexes(){start(\"rows.rowIndexes\");try{Selection s=select(Integer.MAX_VALUE);int[] result=Arrays.copyOf(s.rows,s.length);table.endSuccess(\"rows.rowIndexes\",s.scanned,s.length,0L);return result;}catch(SomaRuntimeException f){table.endFailure(\"rows.rowIndexes\",attemptedScanned,attemptedMatched,f.code());throw f;}catch(RuntimeException f){table.abort(\"rows.rowIndexes\");throw f;}catch(Error f){table.abort(\"rows.rowIndexes\");throw f;}}\n")
                .append("  public UpdateResult update(Updater value){if(value==null)throw new NullPointerException(\"updater\");start(\"rows.update\");long reached=0L;Selection s=null;try{s=select(Integer.MAX_VALUE);table.prepareUpdateScratch(s.length);table.loadUpdateScratch(s.rows,s.length);MutableCursor c=new MutableCursor(table);for(int i=0;i<s.length;i++){reached++;c.open(i,s.rows[i]);try{value.update(c);}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"rows.update\",\"updater\",callback);}finally{c.close();}}long changed=table.publishUpdate(s.rows,s.length);table.endSuccess(\"rows.update\",s.scanned,s.length,changed);return table.updateResult(s.scanned,s.length,changed,0L,rebuiltSidecars());}catch(SomaRuntimeException f){table.endFailure(\"rows.update\",s==null?attemptedScanned:s.scanned,reached==0L&&s==null?attemptedMatched:reached,f.code());throw f;}catch(RuntimeException f){table.abort(\"rows.update\");throw f;}catch(Error f){table.abort(\"rows.update\");throw f;}}\n\n")
                .append("  public RemoveResult remove(){start(\"rows.remove\");Selection s=null;try{s=select(Integer.MAX_VALUE);RemoveResult result=table.removeSelected(s.rows,s.length,s.scanned,0L,rebuiltSidecars(),\"rows.remove\");table.endSuccess(\"rows.remove\",s.scanned,s.length,s.length);return result;}catch(SomaRuntimeException f){table.endFailure(\"rows.remove\",s==null?attemptedScanned:s.scanned,s==null?attemptedMatched:s.length,f.code());throw f;}catch(RuntimeException f){table.abort(\"rows.remove\");throw f;}catch(Error f){table.abort(\"rows.remove\");throw f;}}\n\n")
                .append("  private Selection select(int maximum){attemptedScanned=0L;attemptedMatched=0L;int initial=source==null?table.size():source.size();int firstSort=firstSort();int required=firstSort<kinds.length?initial:Math.min(initial,maximum);int[] values=table.preparePipelineScratch(required);long[] seen=new long[kinds.length];Cursor cursor=new Cursor(table);int length=0;long scanned=0L;for(int position=0;position<initial&&!limitReached(seen,0,firstSort);position++){int rowIndex=source==null?position:source.rowAt(position);scanned++;attemptedScanned=scanned;if(matches(rowIndex,cursor,seen,0,firstSort)){values[length++]=rowIndex;attemptedMatched=length;}if(firstSort==kinds.length&&length>=maximum)break;}for(int stage=firstSort;stage<kinds.length;stage++){if(kinds[stage]==SORT){stableSort(values,length,comparators[stage]);}else if(kinds[stage]==FILTER){int write=0;for(int i=0;i<length;i++)if(test(predicates[stage],cursor,values[i],\"rows.filter\"))values[write++]=values[i];length=write;attemptedMatched=length;}else if(kinds[stage]==SKIP){int remove=(int)Math.min((long)length,counts[stage]);System.arraycopy(values,remove,values,0,length-remove);length-=remove;attemptedMatched=length;}else{length=(int)Math.min((long)length,counts[stage]);attemptedMatched=length;}}if(length>maximum)length=maximum;attemptedMatched=length;return new Selection(values,length,scanned);}\n")
                .append("  private int firstSort(){for(int i=0;i<kinds.length;i++)if(kinds[i]==SORT)return i;return kinds.length;}\n")
                .append("  private boolean hasSort(){return firstSort()!=kinds.length;}\n")
                .append("  private boolean limitReached(long[] seen,int from,int to){for(int i=from;i<to;i++)if(kinds[i]==LIMIT&&seen[i]>=counts[i])return true;return false;}\n")
                .append("  private boolean matches(int rowIndex,Cursor cursor,long[] seen,int from,int to){for(int i=from;i<to;i++){if(kinds[i]==FILTER){if(!test(predicates[i],cursor,rowIndex,\"rows.filter\"))return false;}else if(kinds[i]==SKIP){if(seen[i]<counts[i]){seen[i]++;return false;}}else if(kinds[i]==LIMIT){if(seen[i]>=counts[i])return false;seen[i]++;}}return true;}\n")
                .append("  private boolean test(Predicate value,Cursor cursor,int rowIndex,String operation){cursor.open(rowIndex);try{return value.test(cursor);}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",operation,\"predicate\",callback);}finally{cursor.close();}}\n")
                .append("  private void stableSort(int[] values,int length,Comparator comparator){int[] auxiliary=table.prepareSortScratch(length);Cursor left=new Cursor(table),right=new Cursor(table);for(int width=1;width<length;width=width>length/2?length:width*2){for(int start=0;start<length;start+=width*2){int middle=Math.min(start+width,length),end=Math.min(start+width*2,length),a=start,b=middle,w=start;while(a<middle||b<end){if(b>=end||(a<middle&&compare(comparator,left,right,values[a],values[b])<=0))auxiliary[w++]=values[a++];else auxiliary[w++]=values[b++];}System.arraycopy(auxiliary,start,values,start,end-start);}}}\n")
                .append("  private int compare(Comparator value,Cursor left,Cursor right,int a,int b){left.open(a);right.open(b);try{return value.compare(left,right);}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"rows.sorted\",\"comparator\",callback);}finally{left.close();right.close();}}\n")
                .append("  private void start(String operation){check(operation);consumed=true;attemptedScanned=0L;attemptedMatched=0L;table.begin(operation);sidecarRebuildBaseline=table.sidecarRebuildCount();}\n  private long rebuiltSidecars(){return table.sidecarRebuildCount()-sidecarRebuildBaseline;}\n  private void check(String operation){if(consumed)throw RuntimeFailures.pipelineConsumed(sourcePath(),operation);}\n")
                .append("  private String sourcePath(){return source==null?").append(q(table.logicalName)).append(":").append(q(table.logicalName + ".")).append("+source.name();}\n")
                .append("  static abstract class Source{abstract int size();abstract int rowAt(int position);abstract String name();}\n")
                .append("  public interface Predicate{boolean test(").append(row).append(" row);}\n  public interface Consumer{void accept(").append(row).append(" row);}\n  public interface Updater{void update(").append(mutable).append(" row);}\n  public interface Comparator{int compare(").append(row).append(" left,").append(row).append(" right);}\n")
                .append("  private static final class Selection{final int[] rows;final int length;final long scanned;Selection(int[] rows,int length,long scanned){this.rows=rows;this.length=length;this.scanned=scanned;}}\n")
                .append("  private static final class Cursor implements ").append(row).append(" {\n    protected final ").append(table.name("Table")).append(" table;protected int row;protected boolean active;Cursor(").append(table.name("Table")).append(" table){this.table=table;}void open(int row){this.row=row;active=true;}void close(){active=false;}void valid(){if(!active)throw RuntimeFailures.internalInvariant(\"escaped_row_cursor\",").append(q(table.logicalName)).append(",\"cursor\");}\n");
        appendCursorMethods(out, table, false);
        out.append("  }\n\n  private static final class MutableCursor implements ").append(mutable).append(" {\n    private final ").append(table.name("Table")).append(" table; private int scratch,row; private boolean active; MutableCursor(").append(table.name("Table")).append(" table){this.table=table;} void open(int scratch,int row){this.scratch=scratch;this.row=row;active=true;} void close(){active=false;} void valid(){if(!active)throw RuntimeFailures.internalInvariant(\"escaped_mutable_cursor\",").append(q(table.logicalName)).append(",\"cursor\");}\n");
        appendCursorMethods(out, table, true);
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            out.append("    public void set").append(cap(field.javaName)).append('(').append(field.primitive).append(" value){valid();table.setUpdate").append(cap(field.javaName)).append("(scratch,value);}\n");
            if (field.optional) out.append("    public void clear").append(cap(field.javaName)).append("(){valid();table.clearUpdate").append(cap(field.javaName)).append("(scratch);}\n");
        }
        return out.append("  }\n}\n").toString();
    }

    private void appendCursorMethods(StringBuilder out, TableSpec table, boolean scratch) {
        for (FieldSpec field : table.fields) {
            String index = scratch && !field.key ? "scratch" : "row";
            String value = scratch && !field.key
                    ? "update" + cap(field.javaName) + "Value(" + index + ")"
                    : field.javaName + "Value(" + index + ")";
            String present = scratch && !field.key
                    ? "update" + cap(field.javaName) + "Present(" + index + ")"
                    : field.javaName + "Present(" + index + ")";
            if (field.optional) {
                out.append("    public boolean ").append(field.javaName).append("Present(){valid();return table.").append(present).append(";}\n")
                        .append("    public boolean ").append(field.javaName).append("Absent(){return !").append(field.javaName).append("Present();}\n")
                        .append("    public ").append(field.primitive).append(' ').append(field.javaName).append("(){valid();if(!table.").append(present).append(")throw RuntimeFailures.optionalAbsent(").append(q(table.logicalName)).append(',').append(q(field.logicalName)).append(",\"cursor.get\");return table.").append(value).append(";}\n")
                        .append("    public ").append(field.primitive).append(' ').append(field.javaName).append("Or(").append(field.primitive).append(" defaultValue){valid();return table.").append(present).append("?table.").append(value).append(":defaultValue;}\n");
            } else {
                out.append("    public ").append(field.primitive).append(' ').append(field.javaName).append("(){valid();return table.").append(value).append(";}\n");
            }
        }
    }

    private String tableSource(TableSpec table) {
        String name = table.name("Table");
        StringBuilder out = new StringBuilder(header());
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
        if (table.keyed()) {
            out.append("  private ").append(table.keyField().keySpaceType())
                    .append(" keySpace;\n");
        }
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("  private final RowPermutationSidecar selector")
                    .append(i).append("Sidecar=new RowPermutationSidecar();\n");
        }
        out.append("  private final DenseTableState state;\n")
                .append("  private int[] candidateScratch=new int[0],pipelineScratch=new int[0],sortScratch=new int[0];private boolean[] removeMarks=new boolean[0];private int updateCapacity;\n");
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    out.append("  private ").append(leaf.storagePrimitive)
                            .append("[] update").append(cap(leaf.physicalName(field)))
                            .append("=new ").append(leaf.storagePrimitive).append("[0];\n");
                }
            } else {
                out.append("  private ").append(field.storagePrimitive).append("[] update")
                        .append(cap(field.javaName)).append("=new ").append(field.storagePrimitive).append("[0];\n");
            }
            if (field.optional) out.append("  private boolean[] update").append(cap(field.javaName)).append("Present=new boolean[0];\n");
        }
        out.append("\n  private ").append(name).append("(RuntimePlan plan,TablePlan tablePlan){\n")
                .append("    ColumnGroup columns=new ColumnGroup(tablePlan.initialCapacity()");
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
        out.append(");\n    state=new DenseTableState(TABLE,plan,tablePlan,columns);\n");
        if (table.keyed()) {
            out.append("    keySpace=new ").append(table.keyField().keySpaceType())
                    .append("(tablePlan.initialCapacity());\n");
        }
        out.append("  }\n\n")
                .append("  public static ").append(name).append(" create(){return create(defaultRuntimePlan());}\n")
                .append("  public static ").append(name).append(" create(RuntimePlan plan){if(plan==null)throw new NullPointerException(\"plan\");TablePlan tablePlan=RuntimeCompatibility.verifyAccess(RuntimeCompatibility.verify(METADATA,plan,TABLE),")
                .append(table.selectors.isEmpty() ? "false" : "true")
                .append(");return new ").append(name).append("(plan,tablePlan);}\n")
                .append("  public static RuntimePlan defaultRuntimePlan(){return DEFAULT_RUNTIME_PLAN;}\n")
                .append("  private static RuntimePlan createDefaultRuntimePlan(){return RuntimePlan.builder(")
                .append(q(schemaHash)).append(",RuntimeCompatibility.RUNTIME_COMPATIBILITY,RuntimeCompatibility.GENERATED_PROTOCOL,RuntimeCompatibility.PLAN_PROTOCOL,RuntimeCompatibility.ALLOCATION_ESTIMATOR).addTable(TablePlan.builder(TABLE,RuntimeCompatibility.DENSE_ALGORITHM).initialCapacity(")
                .append(table.defaultCapacity).append(").growthRatio(3,2).maximumUpdateScratchBytes(268435456L)");
        if (!table.selectors.isEmpty()) {
            out.append(".accessStrategy(RuntimeCompatibility.PRIMITIVE_SORTED_PERMUTATION).sidecarMaintenancePolicy(RuntimeCompatibility.DIRTY_LAZY_REBUILD).maximumSidecarScratchBytes(268435456L)");
        }
        out.append(".build()).build();}\n")
                .append("  public RuntimePlan runtimePlan(){return state.runtimePlan();}\n  public int size(){return state.size();}\n  public int capacity(){return state.capacity();}\n  public long structuralEpoch(){return state.structuralEpoch();}\n  public boolean isReleased(){return state.isReleased();}\n\n");
        if (table.keyed()) {
            String keySpaceType = table.keyField().keySpaceType();
            out.append("  public void addBatch(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");state.prepareAppend(0);int count=batch.size();if(count==0)return;")
                    .append("validateSelectorAppend(batch);validateUniqueAppend(batch);").append(keySpaceType).append(" staged=stageAppendKeys(batch);int start=state.prepareAppend(count);copyBatch(batch,0,start,count);state.commitAppend(start,count);keySpace=staged;markSelectorSidecarsDirty();}\n")
                    .append("  public void replaceAll(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");state.prepareReplace(0);")
                    .append("validateSelectorReplacement(batch);validateUniqueReplacement(batch);").append(keySpaceType).append(" staged=stageReplacementKeys(batch);int count=batch.size();int previous=state.prepareReplace(count);copyBatch(batch,0,0,count);if(previous>count)clearColumns(count,previous);state.commitReplace(previous,count);keySpace=staged;if(previous!=count||count!=0)markSelectorSidecarsDirty();}\n")
                    .append("  public void clear(){int previous=state.prepareClear();clearColumns(0,previous);keySpace.clear();clearSelectorSidecars();state.commitClear(previous);}\n")
                    .append("  public void release(){int previous=state.prepareRelease();if(previous>=0){clearColumns(0,previous);keySpace.clear();releaseSelectorSidecars();state.commitRelease(previous);}}\n\n");
        } else {
            out.append("  public void addBatch(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");state.prepareAppend(0);int count=batch.size();if(count==0)return;validateSelectorAppend(batch);validateUniqueAppend(batch);int start=state.prepareAppend(count);copyBatch(batch,0,start,count);state.commitAppend(start,count);markSelectorSidecarsDirty();}\n")
                    .append("  public void replaceAll(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");state.prepareReplace(0);int count=batch.size();validateSelectorReplacement(batch);validateUniqueReplacement(batch);int previous=state.prepareReplace(count);copyBatch(batch,0,0,count);if(previous>count)clearColumns(count,previous);state.commitReplace(previous,count);if(previous!=count||count!=0)markSelectorSidecarsDirty();}\n")
                    .append("  public void clear(){int previous=state.prepareClear();clearColumns(0,previous);clearSelectorSidecars();state.commitClear(previous);}\n")
                    .append("  public void release(){int previous=state.prepareRelease();if(previous>=0){clearColumns(0,previous);releaseSelectorSidecars();state.commitRelease(previous);}}\n\n");
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
        out.append("  }\n\n");
        if (table.keyed()) {
            FieldSpec key = table.keyField();
            out.append("  private int appendKeyCapacity(int batchSize){long total=(long)size()+(long)batchSize;if(total>Integer.MAX_VALUE)throw RuntimeFailures.memoryLimitExceeded(TABLE,\"addBatch\",Integer.MAX_VALUE,total);return(int)total;}\n");
            if (key.compositeValueKey()) {
                appendCompositeKeyRuntime(out, table, key);
            } else {
                out.append("  private ").append(key.keySpaceType()).append(" stageAppendKeys(")
                        .append(table.name("Batch")).append(" batch){")
                        .append(key.keySpaceType()).append(" staged=new ").append(key.keySpaceType())
                        .append("(appendKeyCapacity(batch.size()));for(int row=0;row<size();row++)staged.put(")
                        .append(key.valueBacked()
                                ? key.keySpaceValueFromStorage(key.javaName + "Column.get(row)", "addBatch")
                                : key.keySpaceValue(key.javaName + "Value(row)", "addBatch"))
                        .append(",row);for(int row=0;row<batch.size();row++){")
                        .append(key.valueBacked() ? key.storagePrimitive : key.primitive).append(" key=batch.")
                        .append(key.javaName).append(key.valueBacked() ? "StorageValue(row);" : "Value(row);")
                        .append(key.keySpaceValueType()).append(" keySlot=")
                        .append(key.valueBacked() ? key.keySpaceValueFromStorage("key", "addBatch") : key.keySpaceValue("key", "addBatch"))
                        .append(";if(staged.contains(keySlot))throw ")
                        .append(key.valueBacked() ? "RuntimeFailures.duplicateValueKey(TABLE," + q(key.logicalName) + ",\"addBatch\")" : "RuntimeFailures.duplicateKey(TABLE,key,\"addBatch\")")
                        .append(";staged.put(keySlot,size()+row);}return staged;}\n")
                        .append("  private ").append(key.keySpaceType()).append(" stageReplacementKeys(")
                        .append(table.name("Batch")).append(" batch){")
                        .append(key.keySpaceType()).append(" staged=new ").append(key.keySpaceType())
                        .append("(batch.size());for(int row=0;row<batch.size();row++){")
                        .append(key.valueBacked() ? key.storagePrimitive : key.primitive).append(" key=batch.")
                        .append(key.javaName).append(key.valueBacked() ? "StorageValue(row);" : "Value(row);")
                        .append(key.keySpaceValueType()).append(" keySlot=")
                        .append(key.valueBacked() ? key.keySpaceValueFromStorage("key", "replaceAll") : key.keySpaceValue("key", "replaceAll"))
                        .append(";if(staged.contains(keySlot))throw ")
                        .append(key.valueBacked() ? "RuntimeFailures.duplicateValueKey(TABLE," + q(key.logicalName) + ",\"replaceAll\")" : "RuntimeFailures.duplicateKey(TABLE,key,\"replaceAll\")")
                        .append(";staged.put(keySlot,row);}return staged;}\n")
                        .append("  private int keyRow(").append(key.primitive).append(" key,String operation){int row=keySpace.rowOf(")
                        .append(key.keySpaceValueExpression("key", "operation"))
                        .append(");if(row<0)throw ")
                        .append(key.valueBacked() ? "RuntimeFailures.missingValueKey(TABLE," + q(key.logicalName) + ",operation)" : "RuntimeFailures.missingKey(TABLE,key,operation)")
                        .append(";return row;}\n\n");
            }
        }
        out.append("\n")
                .append("  public ").append(table.carrierType).append(" fetchAt(int rowIndex){return fetchAt(rowIndex,runtimePlan().defaultMaterializationBudget());}\n")
                .append("  public ").append(table.carrierType).append(" fetchAt(int rowIndex,MaterializationBudget budget){int row=state.checkRowIndex(rowIndex,\"fetchAt\");if(budget==null)throw new NullPointerException(\"budget\");MaterializationTracker tracker=new MaterializationTracker(budget,TABLE);tracker.checkOwnershipDepth(0);tracker.addTableInstances(1L);tracker.addRows(1L);accountRow(tracker,row);return carrier(row);}\n")
                .append("  public List<").append(table.carrierType).append("> materialize(){return materialize(runtimePlan().defaultMaterializationBudget());}\n")
                .append("  public List<").append(table.carrierType).append("> materialize(MaterializationBudget budget){state.checkActive(\"materialize\");if(budget==null)throw new NullPointerException(\"budget\");int count=size();MaterializationTracker tracker=new MaterializationTracker(budget,TABLE);tracker.checkOwnershipDepth(0);tracker.addTableInstances(1L);tracker.addRows(count);tracker.addEstimatedBytes(40L+8L*(long)count);for(int i=0;i<count;i++)accountRow(tracker,i);List<").append(table.carrierType).append("> result=new ArrayList<").append(table.carrierType).append(">(count);for(int i=0;i<count;i++)result.add(carrier(i));return result;}\n")
                .append("  private void accountRow(MaterializationTracker tracker,int row){long leaves=0L,bytes=").append(16L + 8L * table.fields.size()).append("L;\n");
        for (FieldSpec field : table.fields) {
            if (field.optional) out.append("    if(").append(field.javaName).append("Present(row)){leaves++;bytes+=16L;}\n");
            else if (field.flattenedValueStorage()) out.append("    leaves+=")
                    .append(field.valueLeaves.size()).append("L;bytes+=16L;\n");
            else out.append("    leaves++;\n");
        }
        out.append("    tracker.addLeafValues(leaves);tracker.addEstimatedBytes(bytes);}\n")
                .append("  private ").append(table.carrierType).append(" carrier(int row){").append(table.carrierType).append(" value=new ").append(table.carrierType).append("();\n");
        for (FieldSpec field : table.fields) {
            if (field.optional) {
                out.append("    value.").append(field.javaName).append('=').append(field.javaName)
                        .append("Present(row)?").append(field.boxValue(field.javaName + "Value(row)"))
                        .append(":null;\n");
            } else out.append("    value.").append(field.javaName).append('=').append(field.javaName).append("Value(row);\n");
        }
        out.append("    return value;}\n\n");
        if (table.keyed()) {
            FieldSpec key = table.keyField();
            if (key.compositeValueKey()) {
                out.append("  public boolean containsKey(").append(key.primitive).append(" key){state.checkActive(\"containsKey\");return compositeLookup(key,\"containsKey\")>=0;}\n")
                        .append("  public java.util.Optional<").append(table.carrierType).append("> find(").append(key.primitive).append(" key){state.checkActive(\"find\");int row=compositeLookup(key,\"find\");return row<0?java.util.Optional.<").append(table.carrierType).append(">empty():java.util.Optional.of(fetchAt(row));}\n")
                        .append("  public ").append(table.carrierType).append(" fetch(").append(key.primitive).append(" key){return fetchAt(keyRow(key,\"fetch\"));}\n")
                        .append("  public ").append(table.name("Mutator")).append(" mutate(").append(key.primitive).append(" key){return mutateAt(keyRow(key,\"mutate\"));}\n")
                        .append("  public void delete(").append(key.primitive).append(" key){state.beginOperation(\"delete\");long scanned=0L;try{int row=keyRow(key,\"delete\");scanned=1L;int[] selected=preparePipelineScratch(1);selected[0]=row;removeSelected(selected,1,1L,0L,0L,\"delete\");state.endOperationSuccess(\"delete\",1L,1L,1L);}catch(SomaRuntimeException failure){state.endOperationFailure(\"delete\",scanned,0L,failure.code());throw failure;}catch(RuntimeException failure){state.abortOperation(\"delete\");throw failure;}catch(Error failure){state.abortOperation(\"delete\");throw failure;}}\n")
                        .append("  public ").append(table.name("Keys")).append(" keys(){state.checkActive(\"keys\");return new ").append(table.name("Keys")).append("(this);}\n");
            } else {
                out.append("  public boolean containsKey(").append(key.primitive).append(" key){state.checkActive(\"containsKey\");return keySpace.contains(")
                        .append(key.keySpaceValue("key", "containsKey"))
                        .append(");}\n")
                        .append("  public java.util.Optional<").append(table.carrierType).append("> find(").append(key.primitive).append(" key){state.checkActive(\"find\");int row=keySpace.rowOf(")
                        .append(key.keySpaceValue("key", "find"))
                        .append(");return row<0?java.util.Optional.<").append(table.carrierType).append(">empty():java.util.Optional.of(fetchAt(row));}\n")
                        .append("  public ").append(table.carrierType).append(" fetch(").append(key.primitive).append(" key){return fetchAt(keyRow(key,\"fetch\"));}\n")
                        .append("  public ").append(table.name("Mutator")).append(" mutate(").append(key.primitive).append(" key){return mutateAt(keyRow(key,\"mutate\"));}\n")
                        .append("  public void delete(").append(key.primitive).append(" key){state.beginOperation(\"delete\");long scanned=0L;try{int row=keyRow(key,\"delete\");scanned=1L;int[] selected=preparePipelineScratch(1);selected[0]=row;removeSelected(selected,1,1L,0L,0L,\"delete\");state.endOperationSuccess(\"delete\",1L,1L,1L);}catch(SomaRuntimeException failure){state.endOperationFailure(\"delete\",scanned,0L,failure.code());throw failure;}catch(RuntimeException failure){state.abortOperation(\"delete\");throw failure;}catch(Error failure){state.abortOperation(\"delete\");throw failure;}}\n")
                        .append("  public ").append(table.name("Keys")).append(" keys(){state.checkActive(\"keys\");return new ").append(table.name("Keys")).append("(this);}\n");
            }
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
                .append("  public ").append(table.carrierType).append(" firstOrThrow(){return rows().firstOrThrow();}\n")
                .append("  public java.util.List<").append(table.carrierType).append("> fetchAll(){return rows().fetchAll();}\n")
                .append("  public int[] rowIndexes(){return rows().rowIndexes();}\n")
                .append("  public UpdateResult update(").append(table.name("Rows")).append(".Updater updater){return rows().update(updater);}\n")
                .append("  public RemoveResult remove(){return rows().remove();}\n");
        appendSelectorSources(out, table);
        for (FieldSpec field : table.fields) {
            if (field.valueBacked()) {
                continue;
            }
            String presence = field.optional ? field.javaName + "Presence" : "null";
            out.append("  public ").append(field.columnPipelineType()).append(' ')
                    .append(field.javaName).append("Values(){return ")
                    .append(field.columnPipelineConstruction(presence)).append(";}\n")
                    .append("  public ").append(field.columnViewType()).append(' ')
                    .append(field.javaName).append("Column(){return ")
                    .append(field.columnViewConstruction(presence)).append(";}\n");
        }
        out.append("  public TableStats statsSnapshot(){return state.statsSnapshot();}\n  public void resetStats(){state.resetStats();}\n\n")
                .append("  void begin(String operation){state.beginOperation(operation);}\n  void endSuccess(String operation,long scanned,long matched,long changed){state.endOperationSuccess(operation,scanned,matched,changed);}\n  void endFailure(String operation,long scanned,long matched,String code){state.endOperationFailure(operation,scanned,matched,code);}\n  void abort(String operation){state.abortOperation(operation);}\n  long sidecarRebuildCount(){return state.sidecarRebuildCount();}\n  UpdateResult updateResult(long scanned,long matched,long changed,long maintained,long rebuilt){return state.updateResult(scanned,matched,changed,maintained,rebuilt);}\n")
                .append("  int[] preparePipelineScratch(int required){if(required<0||required>size())throw RuntimeFailures.internalInvariant(\"pipeline_scratch_size\",TABLE,\"rows\");if(pipelineScratch.length<required)pipelineScratch=Arrays.copyOf(pipelineScratch,required);return pipelineScratch;}\n")
                .append("  int[] prepareSortScratch(int required){if(sortScratch.length<required)sortScratch=Arrays.copyOf(sortScratch,required);return sortScratch;}\n")
                .append("  ").append(table.carrierType).append(" materializeRow(int row){return fetchAt(row);}\n")
                .append("  List<").append(table.carrierType).append("> materializeRows(int[] rows,int count){MaterializationBudget budget=runtimePlan().defaultMaterializationBudget();MaterializationTracker tracker=new MaterializationTracker(budget,TABLE);tracker.checkOwnershipDepth(0);tracker.addTableInstances(1L);tracker.addRows(count);tracker.addEstimatedBytes(40L+8L*(long)count);for(int i=0;i<count;i++)accountRow(tracker,rows[i]);List<").append(table.carrierType).append("> result=new ArrayList<").append(table.carrierType).append(">(count);for(int i=0;i<count;i++)result.add(carrier(rows[i]));return result;}\n");
        appendTableFieldAccess(out, table);
        appendMutatorCommit(out, table);
        appendUpdateScratch(out, table);
        appendRemove(out, table);
        appendSelectorRuntime(out, table);
        return out.append("}\n").toString();
    }

    private void appendSelectorSources(StringBuilder out, TableSpec table) {
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

    private void appendSelectorRuntime(StringBuilder out, TableSpec table) {
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
                    .append("Sidecar.isDirty())return;int count=size();long retained=selectorSidecarRetainedBytes();long peak=retained-selector")
                    .append(i).append("Sidecar.retainedBytes()+selector").append(i)
                    .append("Sidecar.rebuildPeakBytes(count);long limit=runtimePlan().requireTable(TABLE).maximumSidecarScratchBytes();if(peak<0L||peak>limit)throw RuntimeFailures.memoryLimitExceeded(TABLE,\"sidecar.rebuild\",limit,peak);try{int[] staged=selector")
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
            StringBuilder out, TableSpec table, SelectorSpec selector, int index) {
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
                    : selectorMutatorValue(binding, leaf, "mutation", "mutator.commit");
            out.append("int compare").append(leafIndex).append('=')
                    .append(compareExpression(leaf.storageType, left, right)).append(';')
                    .append("if(compare").append(leafIndex).append("!=0)return ")
                    .append("DESC".equals(leaf.direction) ? "-compare" : "compare")
                    .append(leafIndex).append(';');
        }
        out.append("return 0;}\n");
    }

    private static void appendSelectorChangedExpression(
            StringBuilder out, TableSpec table, SelectorSpec selector,
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
                    ? selectorMutatorValue(binding, leaf, mutation, operation)
                    : selectorUpdateValue(binding, leaf, scratch, operation);
            out.append('(').append(compareExpression(
                    leaf.storageType, live, candidate)).append(")!=0");
        }
        if (!emitted) out.append("false");
    }

    private static void appendUniqueValidationRuntime(StringBuilder out, TableSpec table) {
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
            out.append("if(changedUniqueCount==0)return;int[] lookup=prepareCandidateScratch();long retained=4L*(long)candidateScratch.length+updateBytes(updateCapacity);long unit=HashCompositeKeySpace.estimatedPeakBytes(size());long uniqueBytes=unit>Long.MAX_VALUE/(long)changedUniqueCount?Long.MAX_VALUE:unit*(long)changedUniqueCount;long required=uniqueBytes==Long.MAX_VALUE||retained>Long.MAX_VALUE-uniqueBytes?Long.MAX_VALUE:retained+uniqueBytes;long limit=runtimePlan().requireTable(TABLE).maximumUpdateScratchBytes();if(required>limit)throw RuntimeFailures.memoryLimitExceeded(TABLE,\"rows.update\",limit,required);state.updateScratch(required,required);try{Arrays.fill(lookup,0,size(),-1);for(int index=0;index<selectedCount;index++)lookup[selected[index]]=index;");
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
            StringBuilder out, TableSpec table, String mode,
            String row, String operation) {
        java.util.Set<String> emitted = new java.util.LinkedHashSet<String>();
        for (SelectorSpec selector : table.selectors) {
            for (SelectorLeafSpec leaf : selector.leaves) {
                if (!("float".equals(leaf.storageType) || "double".equals(leaf.storageType))
                        || !emitted.add(leaf.path)) continue;
                SelectorBinding binding = selectorBinding(table, leaf);
                String value;
                if ("Append".equals(mode) || "Replacement".equals(mode)) {
                    value = selectorBatchValue(binding, leaf, "batch", row, operation);
                } else if ("MutatorOnly".equals(mode)) {
                    if (binding.field.key) continue;
                    value = selectorMutatorValue(binding, leaf, "mutation", operation);
                } else {
                    if (binding.field.key) continue;
                    value = selectorUpdateValue(binding, leaf, row, operation);
                }
                out.append(value).append(';');
            }
        }
    }

    private static void appendUniqueProbeLoop(
            StringBuilder out, SelectorSpec selector, int index, String mode,
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
            StringBuilder out, TableSpec table, SelectorSpec selector, int index,
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
            String mutated = selectorMutatorValue(binding, leaf, "mutation", operation);
            return "(" + row + "==target?" + mutated + ":" + live + ")";
        }
        if ("Update".equals(mode)) {
            if (binding.field.key) return live;
            String updated = selectorUpdateValue(binding, leaf, "lookup[" + row + "]", operation);
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

    private static String selectorUpdateValue(
            SelectorBinding binding, SelectorLeafSpec leaf,
            String scratch, String operation) {
        if (binding.valueLeaf != null && binding.field.flattenedValueStorage()) {
            return selectorStorageValue(leaf,
                    "update" + cap(binding.valueLeaf.physicalName(binding.field))
                            + "[" + scratch + "]",
                    operation);
        }
        String value = "update" + cap(binding.field.javaName) + "[" + scratch + "]";
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

    private static void appendSelectorArguments(StringBuilder out, int parameters) {
        for (int i = 0; i < parameters; i++) out.append(",value").append(i);
    }

    private static void appendSelectorParameters(
            StringBuilder out, List<SelectorParameter> parameters) {
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
            StringBuilder out, TableSpec table, SelectorSpec selector,
            String leftRow, String rightRow, int leaves, boolean rowToRow,
            String operation) {
        appendSelectorComparison(out, table, selector, leftRow, rightRow,
                leaves, rowToRow, operation, null);
    }

    private static void appendSelectorComparison(
            StringBuilder out, TableSpec table, SelectorSpec selector,
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
            String right = "update" + cap(leaf.physicalName(field))
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

    private void appendCompositeKeyRuntime(
            StringBuilder out, TableSpec table, FieldSpec key) {
        String batch = table.name("Batch");
        String duplicateAppend = "RuntimeFailures.duplicateValueKey(TABLE,"
                + q(key.logicalName) + ",\"addBatch\")";
        String duplicateReplace = "RuntimeFailures.duplicateValueKey(TABLE,"
                + q(key.logicalName) + ",\"replaceAll\")";

        out.append("  private HashCompositeKeySpace stageAppendKeys(").append(batch)
                .append(" batch){HashCompositeKeySpace staged=new HashCompositeKeySpace(appendKeyCapacity(batch.size()));for(int row=0;row<size();row++){staged.ensureInsertCapacity();long hash=");
        appendCompositeColumnHash(out, key, "row", "\"addBatch\"");
        out.append(";staged.putAt(compositeInsertionSlot(staged,hash),hash,row);}for(int row=0;row<batch.size();row++){staged.ensureInsertCapacity();long hash=");
        appendCompositeBatchHash(out, key, "batch", "row", "\"addBatch\"");
        out.append(";if(compositeAppendSlot(staged,hash,batch,row,\"addBatch\")>=0)throw ")
                .append(duplicateAppend)
                .append(";staged.putAt(compositeInsertionSlot(staged,hash),hash,size()+row);}return staged;}\n");

        out.append("  private HashCompositeKeySpace stageReplacementKeys(").append(batch)
                .append(" batch){HashCompositeKeySpace staged=new HashCompositeKeySpace(batch.size());for(int row=0;row<batch.size();row++){staged.ensureInsertCapacity();long hash=");
        appendCompositeBatchHash(out, key, "batch", "row", "\"replaceAll\"");
        out.append(";if(compositeBatchSlot(staged,hash,batch,row,\"replaceAll\")>=0)throw ")
                .append(duplicateReplace)
                .append(";staged.putAt(compositeInsertionSlot(staged,hash),hash,row);}return staged;}\n");

        out.append("  private int compositeInsertionSlot(HashCompositeKeySpace space,long hash){int slot=space.firstSlot(hash),deleted=-1;while(!space.isEmpty(slot)){if(!space.isLive(slot)&&deleted<0)deleted=slot;slot=space.nextSlot(slot);}return deleted>=0?deleted:slot;}\n")
                .append("  private int compositeAppendSlot(HashCompositeKeySpace space,long hash,").append(batch)
                .append(" batch,int batchRow,String operation){int slot=space.firstSlot(hash);while(!space.isEmpty(slot)){if(space.isLive(slot)&&space.hashAt(slot)==hash){int row=space.rowAt(slot);if(row<size()?compositeBatchTableEquals(batch,batchRow,row,operation):compositeBatchEquals(batch,batchRow,row-size(),operation))return slot;}slot=space.nextSlot(slot);}return -1;}\n")
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
            StringBuilder out, FieldSpec key, String batch, String row, String operation) {
        out.append("compositeHash(");
        for (int i = 0; i < key.valueLeaves.size(); i++) {
            if (i > 0) out.append(',');
            out.append(batch).append('.').append(key.valueLeaves.get(i).physicalName(key))
                    .append("StorageValue(").append(row).append(')');
        }
        out.append(',').append(operation).append(')');
    }

    private static void appendCompositeColumnHash(
            StringBuilder out, FieldSpec key, String row, String operation) {
        out.append("compositeHash(");
        for (int i = 0; i < key.valueLeaves.size(); i++) {
            if (i > 0) out.append(',');
            out.append(key.valueLeaves.get(i).physicalName(key)).append("Column.get(")
                    .append(row).append(')');
        }
        out.append(',').append(operation).append(')');
    }

    private static void appendCompositeValueHash(
            StringBuilder out, FieldSpec key, String value, String operation) {
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
            StringBuilder out, FieldSpec key, String batch, String batchRow,
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
            StringBuilder out, FieldSpec key, String batch, String leftRow,
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
            StringBuilder out, FieldSpec key, String leftRow,
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
            StringBuilder out, FieldSpec key, String value, String row, String operation) {
        for (int i = 0; i < key.valueLeaves.size(); i++) {
            if (i > 0) out.append("&&");
            ValueLeafSpec leaf = key.valueLeaves.get(i);
            out.append(leaf.keyEqual(key,
                    leaf.keyInputStorage(key, value + "." + leaf.javaName, operation),
                    leaf.physicalName(key) + "Column.get(" + row + ")", operation));
        }
    }

    private void appendTableFieldAccess(StringBuilder out, TableSpec table) {
        for (FieldSpec field : table.fields) {
            String c = cap(field.javaName);
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
            if (field.key) {
                continue;
            }
            if (field.flattenedValueStorage()) {
                out.append("  ").append(field.primitive).append(" update").append(c)
                        .append("Value(int row){return ")
                        .append(compositeUpdateValueExpression(field, "row")).append(";}\n")
                        .append("  void setUpdate").append(c).append("(int row,")
                        .append(field.primitive).append(" value){")
                        .append(field.primitive).append(" required=RuntimeFailures.requiredValue(TABLE,")
                        .append(q(field.logicalName)).append(",value,\"rows.update\");");
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    out.append("update").append(cap(leaf.physicalName(field)))
                            .append("[row]=").append(leaf.storageValue(field,
                                    "required." + leaf.javaName, "rows.update"))
                            .append(';');
                }
            } else {
                out.append("  ").append(field.primitive).append(" update").append(c).append("Value(int row){return ")
                        .append(field.publicValue("update" + c + "[row]")).append(";}\n")
                        .append("  void setUpdate").append(c).append("(int row,").append(field.primitive)
                        .append(" value){update").append(c).append("[row]=")
                        .append(field.storageValue("value", "rows.update")).append(';');
            }
            if (field.optional) out.append("update").append(c).append("Present[row]=true;");
            out.append("}\n");
            if (field.optional) out.append("  boolean update").append(c).append("Present(int row){return update").append(c).append("Present[row];}\n  void clearUpdate").append(c).append("(int row){update").append(c).append("[row]=").append(field.storageZero()).append(";update").append(c).append("Present[row]=false;}\n");
        }
    }

    private void appendMutatorCommit(StringBuilder out, TableSpec table) {
        String mutator = table.name("Mutator");
        out.append("  private void validateMutatorValues(").append(mutator)
                .append(" mutation){\n");
        for (FieldSpec field : table.fields) {
            if (field.key) continue;
            String guard = field.optional ? "if(mutation." + field.javaName + "Present()){" : "";
            if (!guard.isEmpty()) out.append("    ").append(guard);
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
            out.append('\n');
        }
        out.append("  }\n  void commitMutator(int row,long epoch,").append(mutator).append(" mutation){state.checkRowIndex(row,\"mutator.commit\");if(epoch!=structuralEpoch())throw RuntimeFailures.staleMutator(TABLE,epoch,structuralEpoch());state.beginOperation(\"mutator.commit\");try{validateMutatorValues(mutation);validateSelectorMutator(mutation);validateUniqueMutator(row,mutation);boolean changed=false;\n");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("    boolean selector").append(i)
                    .append("Changed=selector").append(i)
                    .append("ChangedByMutator(row,mutation);\n");
        }
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            String c = cap(field.javaName);
            String newValue = "mutation." + field.javaName + "Value()";
            String different = field.flattenedValueStorage()
                    ? flattenedValueDifferent(table, field, "row", newValue, "mutator.commit")
                    : accessAwareDifferent(table, field,
                            field.javaName + "Value(row)", newValue, "mutator.commit");
            if (field.optional) different = field.javaName + "Present(row)!=mutation." + field.javaName + "Present()||(" + field.javaName + "Present(row)&&" + different + ")";
            out.append("    if(").append(different).append(")changed=true;\n");
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
            if (field.optional) out.append("    if(mutation.").append(field.javaName).append("Present())").append(field.javaName).append("Presence.setPresent(row);else ").append(field.javaName).append("Presence.clearPresent(row);\n");
        }
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("    if(selector").append(i).append("Changed)markSelector")
                    .append(i).append("Dirty();\n");
        }
        out.append("    state.endOperationSuccess(\"mutator.commit\",1L,1L,changed?1L:0L);}catch(SomaRuntimeException failure){state.endOperationFailure(\"mutator.commit\",0L,0L,failure.code());throw failure;}catch(RuntimeException failure){state.abortOperation(\"mutator.commit\");throw failure;}catch(Error failure){state.abortOperation(\"mutator.commit\");throw failure;}}\n");
    }

    private void appendUpdateScratch(StringBuilder out, TableSpec table) {
        out.append("  int[] prepareCandidateScratch(){int required=size();long bytes=4L*(long)required+updateBytes(updateCapacity);long limit=runtimePlan().requireTable(TABLE).maximumUpdateScratchBytes();if(bytes>limit)throw RuntimeFailures.memoryLimitExceeded(TABLE,\"rows.update\",limit,bytes);if(candidateScratch.length<required)candidateScratch=Arrays.copyOf(candidateScratch,required);state.updateScratch(4L*(long)candidateScratch.length+updateBytes(updateCapacity),4L*(long)candidateScratch.length+updateBytes(updateCapacity));return candidateScratch;}\n")
                .append("  void prepareUpdateScratch(int required){if(required<=updateCapacity)return;long bytes=4L*(long)candidateScratch.length+updateBytes(required);long limit=runtimePlan().requireTable(TABLE).maximumUpdateScratchBytes();if(bytes>limit)throw RuntimeFailures.memoryLimitExceeded(TABLE,\"rows.update\",limit,bytes);\n");
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            String c = cap(field.javaName);
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    String physical = cap(leaf.physicalName(field));
                    out.append("    ").append(leaf.storagePrimitive).append("[] new")
                            .append(physical).append("=Arrays.copyOf(update")
                            .append(physical).append(",required);\n");
                }
            } else {
                out.append("    ").append(field.storagePrimitive).append("[] new").append(c).append("=Arrays.copyOf(update").append(c).append(",required);\n");
            }
            if (field.optional) out.append("    boolean[] new").append(c).append("Present=Arrays.copyOf(update").append(c).append("Present,required);\n");
        }
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            String c = cap(field.javaName);
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    String physical = cap(leaf.physicalName(field));
                    out.append("    update").append(physical).append("=new")
                            .append(physical).append(";\n");
                }
            } else {
                out.append("    update").append(c).append("=new").append(c).append(";\n");
            }
            if (field.optional) out.append("    update").append(c).append("Present=new").append(c).append("Present;\n");
        }
        out.append("    updateCapacity=required;state.updateScratch(bytes,bytes);}\n")
                .append("  private long updateBytes(int capacity){return (long)capacity*").append(table.updateWidth()).append("L;}\n")
                .append("  void loadUpdateScratch(int[] rows,int count){for(int i=0;i<count;i++){int row=rows[i];\n");
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            String c = cap(field.javaName);
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    String physical = leaf.physicalName(field);
                    out.append("    update").append(cap(physical)).append("[i]=")
                            .append(physical).append("Column.get(row);\n");
                }
            } else {
                out.append("    update").append(c).append("[i]=").append(field.javaName).append("Column.get(row);\n");
            }
            if (field.optional) out.append("    update").append(c).append("Present[i]=").append(field.javaName).append("Present(row);\n");
        }
        out.append("  }}\n  long publishUpdate(int[] rows,int count){validateSelectorUpdate(count);validateUniqueUpdate(rows,count);long changed=0L;");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("boolean selector").append(i).append("Changed=selector")
                    .append(i).append("ChangedByUpdate(rows,count);");
        }
        out.append("for(int i=0;i<count;i++){int row=rows[i];boolean rowChanged=false;\n");
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            String c = cap(field.javaName);
            String different = field.flattenedValueStorage()
                    ? flattenedScratchDifferent(table, field, "row", "i", "rows.update")
                    : field.enumType != null
                            ? field.javaName + "Column.get(row)!=update" + c + "[i]"
                            : accessAwareDifferent(table, field, field.javaName + "Value(row)",
                                    "update" + c + "[i]", "rows.update");
            if (field.optional) different = field.javaName + "Present(row)!=update" + c + "Present[i]||(" + field.javaName + "Present(row)&&" + different + ")";
            out.append("    if(").append(different).append(")rowChanged=true;\n");
            if (field.flattenedValueStorage()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    String stored = "update" + cap(leaf.physicalName(field)) + "[i]";
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
                        ? field.storageValue("update" + c + "[i]", "rows.update")
                        : "update" + c + "[i]";
                SelectorLeafSpec accessLeaf = selectorLeaf(table, field.logicalName);
                if (accessLeaf != null) {
                    stored = canonicalAccessStorage(accessLeaf, stored, "rows.update");
                }
                out.append("    ").append(field.javaName).append("Column.set(row,")
                        .append(stored).append(");\n");
            }
            if (field.optional) out.append("    if(update").append(c).append("Present[i])").append(field.javaName).append("Presence.setPresent(row);else ").append(field.javaName).append("Presence.clearPresent(row);\n");
        }
        out.append("    if(rowChanged)changed++;}");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("if(selector").append(i).append("Changed)markSelector")
                    .append(i).append("Dirty();");
        }
        out.append("return changed;}\n");
    }

    private void appendRemove(StringBuilder out, TableSpec table) {
        out.append("  RemoveResult removeSelected(int[] selected,int count,long scanned,long sidecarMaintained,long sidecarRebuilt,String operation){int previous=size();if(count<0||count>previous)throw RuntimeFailures.internalInvariant(\"remove_selection_count\",TABLE,operation);if(removeMarks.length<previous)removeMarks=Arrays.copyOf(removeMarks,previous);Arrays.fill(removeMarks,0,previous,false);for(int i=0;i<count;i++){int row=selected[i];if(row<0||row>=previous||removeMarks[row])throw RuntimeFailures.internalInvariant(\"remove_selection_identity\",TABLE,operation);removeMarks[row]=true;}");
        if (table.keyed()) {
            FieldSpec key = table.keyField();
            if (key.compositeValueKey()) {
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
        if (table.keyed()) {
            FieldSpec key = table.keyField();
            if (key.compositeValueKey()) {
                out.append("    int keySlot=compositeStoredSlot(read,operation);if(keySlot<0)throw RuntimeFailures.internalInvariant(\"composite_key_missing\",TABLE,operation);keySpace.updateRowAt(keySlot,write);\n");
            } else {
                out.append("    keySpace.updateRow(")
                        .append(key.valueBacked()
                                ? key.keySpaceValueFromStorage(key.javaName + "Column.get(read)", "rows.remove")
                                : key.keySpaceValue(key.javaName + "Value(read)", "rows.remove"))
                        .append(",write);\n");
            }
        }
        out.append("    compacted++;}write++;}clearColumns(write,previous);state.commitStructuralRemove(previous,write,operation);if(count!=0)markSelectorSidecarsDirty();return state.removeResult(scanned,count,count,compacted,sidecarMaintained,sidecarRebuilt);}\n");
    }

    private static void appendDirectParameters(StringBuilder out, TableSpec table) {
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
            FieldSpec field, String row) {
        String result = field.valueConstructionTemplate;
        for (int i = 0; i < field.valueLeaves.size(); i++) {
            ValueLeafSpec leaf = field.valueLeaves.get(i);
            String storage = "update" + cap(leaf.physicalName(field))
                    + "[" + row + "]";
            result = result.replace("@{" + i + "}@", leaf.publicValue(field, storage));
        }
        return result;
    }

    private static String cap(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
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
        final List<SelectorSpec> selectors;

        TableSpec(Element origin, String carrierType, String carrierSimpleName,
                  String logicalName, int defaultCapacity, List<FieldSpec> fields,
                  List<SelectorSpec> selectors) {
            this.origin = origin;
            this.carrierType = carrierType;
            this.carrierSimpleName = carrierSimpleName;
            this.logicalName = logicalName;
            this.defaultCapacity = defaultCapacity;
            this.fields = new ArrayList<FieldSpec>(fields);
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

        ValueGroupSpec(
                String javaPath, String logicalPath, String javaType,
                int firstLeaf, int leafCount) {
            this.javaPath = javaPath;
            this.logicalPath = logicalPath;
            this.javaType = javaType;
            this.firstLeaf = firstLeaf;
            this.leafCount = leafCount;
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

        FieldSpec(String javaName, String logicalName, String primitive,
                  String boxed, String storagePrimitive, String columnType,
                  String enumType, String valueType, String valueLeafJavaName,
                  String valueConstructionTemplate, List<ValueLeafSpec> valueLeaves,
                  List<ValueGroupSpec> valueGroups,
                  boolean optional, boolean key) {
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
        }

        String zero() {
            if ("boolean".equals(primitive)) return "false";
            if ("byte".equals(primitive)) return "(byte)0";
            if ("short".equals(primitive)) return "(short)0";
            if ("long".equals(primitive)) return "0L";
            if ("float".equals(primitive)) return "0.0f";
            if ("double".equals(primitive)) return "0.0d";
            if (enumType != null) return "null";
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
            if (enumType != null || valueType != null) {
                return expression;
            }
            return boxed + ".valueOf(" + expression + ")";
        }

        String storageValue(String expression, String operation) {
            if (valueType != null) {
                return "RuntimeFailures.requiredValue(TABLE," + q(logicalName) + ","
                        + expression + "," + q(operation) + ")." + valueLeafJavaName;
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
            if (compositeValueKey()) {
                return "HashCompositeKeySpace";
            }
            if ("long".equals(storagePrimitive) || "double".equals(storagePrimitive)) {
                return "HashLongKeySpace";
            }
            return "HashIntKeySpace";
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
            return "HashLongKeySpace".equals(keySpaceType()) ? "long" : "int";
        }

        String different(String left, String right) {
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
                String type = cap(primitive) + "ColumnPipeline";
                return "new " + type + "(state," + javaName + "Column," + presence
                        + ",TABLE," + q(logicalName) + ")";
            }
            return "new EnumColumnPipeline<" + enumType + ">(state," + javaName + "Column,"
                    + presence + ",TABLE," + q(logicalName) + "," + enumConstantsName() + ")";
        }

        String columnViewConstruction(String presence) {
            if (enumType == null) {
                String type = cap(primitive) + "ColumnView";
                return "new " + type + "(state," + javaName + "Column," + presence
                        + ",TABLE," + q(logicalName) + ")";
            }
            return "new EnumColumnView<" + enumType + ">(state," + javaName + "Column,"
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

        String storageValue(
                FieldSpec owner, String expression, String operation) {
            if ("float".equals(storagePrimitive)) {
                return "KeyCanonicalization.strictFloatStorage(TABLE,"
                        + q(owner.logicalName + "." + logicalName) + ","
                        + expression + "," + q(operation) + ")";
            }
            if ("double".equals(storagePrimitive)) {
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
