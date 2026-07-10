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
            out.append("  private ").append(field.primitive).append("[] ")
                    .append(field.javaName).append("Values;\n");
            if (field.optional) {
                out.append("  private long[] ").append(field.javaName).append("Presence;\n");
            }
        }
        out.append("\n  public ").append(batch).append("() { this(")
                .append(table.defaultCapacity).append("); }\n")
                .append("  public ").append(batch).append("(int initialCapacity) {\n")
                .append("    if (initialCapacity < 0) throw new IllegalArgumentException(\"initialCapacity must be non-negative\");\n")
                .append("    capacity = initialCapacity;\n");
        for (FieldSpec field : table.fields) {
            out.append("    ").append(field.javaName).append("Values = new ")
                    .append(field.primitive).append("[initialCapacity];\n");
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

        out.append("  public void clear() { size = 0;\n");
        for (FieldSpec field : table.fields) {
            if (field.optional) {
                out.append("    Arrays.fill(").append(field.javaName).append("Presence, 0L);\n");
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
            out.append("    ").append(field.primitive).append("[] new")
                    .append(cap(field.javaName)).append("Values = Arrays.copyOf(")
                    .append(field.javaName).append("Values, next);\n");
            if (field.optional) {
                out.append("    long[] new").append(cap(field.javaName))
                        .append("Presence = Arrays.copyOf(").append(field.javaName)
                        .append("Presence, (next + 63) >>> 6);\n");
            }
        }
        for (FieldSpec field : table.fields) {
            out.append("    ").append(field.javaName).append("Values = new")
                    .append(cap(field.javaName)).append("Values;\n");
            if (field.optional) {
                out.append("    ").append(field.javaName).append("Presence = new")
                        .append(cap(field.javaName)).append("Presence;\n");
            }
        }
        out.append("    capacity = next;\n  }\n\n");
        for (FieldSpec field : table.fields) {
            String c = cap(field.javaName);
            out.append("  private void set").append(c).append("(int row, ")
                    .append(field.primitive).append(" value) { ").append(field.javaName)
                    .append("Values[row] = ").append(field.batchStorageValue("value")).append(";");
            if (field.optional) {
                out.append(" setPresent(").append(field.javaName).append("Presence, row, true);");
            }
            out.append(" }\n")
                    .append("  ").append(field.primitive).append(" ").append(field.javaName)
                    .append("Value(int row) { return ").append(field.javaName).append("Values[row]; }\n");
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
                .append("  private final ").append(table.name("Table")).append(" table;\n")
                .append("  private final byte[] kinds;private final Predicate[] predicates;private final Comparator[] comparators;private final long[] counts;private boolean consumed;\n")
                .append("  ").append(rows).append('(').append(table.name("Table")).append(" table){this(table,new byte[0],new Predicate[0],new Comparator[0],new long[0]);}\n")
                .append("  private ").append(rows).append('(').append(table.name("Table")).append(" table,byte[] kinds,Predicate[] predicates,Comparator[] comparators,long[] counts){this.table=table;this.kinds=kinds;this.predicates=predicates;this.comparators=comparators;this.counts=counts;}\n")
                .append("  public ").append(rows).append(" filter(Predicate value){if(value==null)throw new NullPointerException(\"predicate\");return append(FILTER,value,null,0L);}\n")
                .append("  public ").append(rows).append(" skip(long value){if(value<0L)throw new IllegalArgumentException(\"count must be non-negative\");return append(SKIP,null,null,value);}\n")
                .append("  public ").append(rows).append(" limit(long value){if(value<0L)throw new IllegalArgumentException(\"count must be non-negative\");return append(LIMIT,null,null,value);}\n")
                .append("  public ").append(rows).append(" sorted(Comparator value){if(value==null)throw new NullPointerException(\"comparator\");return append(SORT,null,value,0L);}\n")
                .append("  private ").append(rows).append(" append(byte kind,Predicate predicate,Comparator comparator,long count){check(\"intermediate\");int n=kinds.length;byte[] nk=Arrays.copyOf(kinds,n+1);Predicate[] np=Arrays.copyOf(predicates,n+1);Comparator[] nc=Arrays.copyOf(comparators,n+1);long[] nn=Arrays.copyOf(counts,n+1);nk[n]=kind;np[n]=predicate;nc[n]=comparator;nn[n]=count;consumed=true;return new ").append(rows).append("(table,nk,np,nc,nn);}\n\n")
                .append("  public long count(){start(\"rows.count\");try{Selection s=select(Integer.MAX_VALUE);table.endSuccess(\"rows.count\",s.scanned,s.length,0L);return s.length;}catch(SomaRuntimeException f){table.endFailure(\"rows.count\",0L,0L,f.code());throw f;}catch(Error f){table.endFailure(\"rows.count\",0L,0L,\"callback_failed\");throw f;}}\n")
                .append("  public boolean anyMatch(Predicate value){return matchTerminal(value,true);}\n")
                .append("  public boolean noneMatch(Predicate value){return !matchTerminal(value,false);}\n")
                .append("  private boolean matchTerminal(Predicate value,boolean any){if(value==null)throw new NullPointerException(\"predicate\");String op=any?\"rows.anyMatch\":\"rows.noneMatch\";start(op);long reached=0L,scanned=0L;try{Cursor c=new Cursor(table);if(!hasSort()){long[] seen=new long[kinds.length];for(int rowIndex=0;rowIndex<table.size()&&!limitReached(seen,0,kinds.length);rowIndex++){scanned++;if(matches(rowIndex,c,seen,0,kinds.length)){reached++;if(test(value,c,rowIndex,op)){table.endSuccess(op,scanned,reached,0L);return true;}}}table.endSuccess(op,scanned,reached,0L);return false;}Selection s=select(Integer.MAX_VALUE);scanned=s.scanned;for(int i=0;i<s.length;i++){reached++;if(test(value,c,s.rows[i],op)){table.endSuccess(op,scanned,reached,0L);return true;}}table.endSuccess(op,scanned,reached,0L);return false;}catch(SomaRuntimeException f){table.endFailure(op,scanned,reached,f.code());throw f;}catch(Error f){table.endFailure(op,scanned,reached,\"callback_failed\");throw f;}}\n")
                .append("  public void forEach(Consumer value){if(value==null)throw new NullPointerException(\"consumer\");start(\"rows.forEach\");long reached=0L;Selection s=null;try{s=select(Integer.MAX_VALUE);Cursor c=new Cursor(table);for(int i=0;i<s.length;i++){reached++;c.open(s.rows[i]);try{value.accept(c);}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"rows.forEach\",\"consumer\",callback);}finally{c.close();}}table.endSuccess(\"rows.forEach\",s.scanned,reached,0L);}catch(SomaRuntimeException f){table.endFailure(\"rows.forEach\",s==null?0L:s.scanned,reached,f.code());throw f;}catch(Error f){table.endFailure(\"rows.forEach\",s==null?0L:s.scanned,reached,\"callback_failed\");throw f;}}\n")
                .append("  public Optional<").append(table.carrierType).append("> findFirst(){start(\"rows.findFirst\");try{Selection s=select(1);Optional<").append(table.carrierType).append("> result=s.length==0?Optional.<").append(table.carrierType).append(">empty():Optional.of(table.materializeRow(s.rows[0]));table.endSuccess(\"rows.findFirst\",s.scanned,s.length,0L);return result;}catch(SomaRuntimeException f){table.endFailure(\"rows.findFirst\",0L,0L,f.code());throw f;}}\n")
                .append("  public ").append(table.carrierType).append(" firstOrThrow(){start(\"rows.firstOrThrow\");try{Selection s=select(1);if(s.length==0)throw RuntimeFailures.emptyResult(").append(q(table.logicalName)).append(",\"rows.firstOrThrow\");").append(table.carrierType).append(" result=table.materializeRow(s.rows[0]);table.endSuccess(\"rows.firstOrThrow\",s.scanned,1L,0L);return result;}catch(SomaRuntimeException f){table.endFailure(\"rows.firstOrThrow\",0L,0L,f.code());throw f;}}\n")
                .append("  public List<").append(table.carrierType).append("> fetchAll(){start(\"rows.fetchAll\");try{Selection s=select(Integer.MAX_VALUE);List<").append(table.carrierType).append("> result=table.materializeRows(s.rows,s.length);table.endSuccess(\"rows.fetchAll\",s.scanned,s.length,0L);return result;}catch(SomaRuntimeException f){table.endFailure(\"rows.fetchAll\",0L,0L,f.code());throw f;}}\n")
                .append("  public int[] rowIndexes(){start(\"rows.rowIndexes\");try{Selection s=select(Integer.MAX_VALUE);int[] result=Arrays.copyOf(s.rows,s.length);table.endSuccess(\"rows.rowIndexes\",s.scanned,s.length,0L);return result;}catch(SomaRuntimeException f){table.endFailure(\"rows.rowIndexes\",0L,0L,f.code());throw f;}}\n")
                .append("  public UpdateResult update(Updater value){if(value==null)throw new NullPointerException(\"updater\");start(\"rows.update\");long reached=0L;Selection s=null;try{s=select(Integer.MAX_VALUE);table.prepareUpdateScratch(s.length);table.loadUpdateScratch(s.rows,s.length);MutableCursor c=new MutableCursor(table);for(int i=0;i<s.length;i++){reached++;c.open(i,s.rows[i]);try{value.update(c);}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"rows.update\",\"updater\",callback);}finally{c.close();}}long changed=table.publishUpdate(s.rows,s.length);table.endSuccess(\"rows.update\",s.scanned,s.length,changed);return table.updateResult(s.scanned,s.length,changed);}catch(SomaRuntimeException f){table.endFailure(\"rows.update\",s==null?0L:s.scanned,reached,f.code());throw f;}catch(Error f){table.endFailure(\"rows.update\",s==null?0L:s.scanned,reached,\"callback_failed\");throw f;}}\n\n")
                .append("  public RemoveResult remove(){start(\"rows.remove\");Selection s=null;try{s=select(Integer.MAX_VALUE);RemoveResult result=table.removeSelected(s.rows,s.length,s.scanned,\"rows.remove\");table.endSuccess(\"rows.remove\",s.scanned,s.length,s.length);return result;}catch(SomaRuntimeException f){table.endFailure(\"rows.remove\",s==null?0L:s.scanned,s==null?0L:s.length,f.code());throw f;}catch(Error f){table.endFailure(\"rows.remove\",s==null?0L:s.scanned,s==null?0L:s.length,\"callback_failed\");throw f;}}\n\n")
                .append("  private Selection select(int maximum){int[] values=table.preparePipelineScratch();int firstSort=firstSort();long[] seen=new long[kinds.length];Cursor cursor=new Cursor(table);int length=0;long scanned=0L;for(int rowIndex=0;rowIndex<table.size()&&!limitReached(seen,0,firstSort);rowIndex++){scanned++;if(matches(rowIndex,cursor,seen,0,firstSort))values[length++]=rowIndex;if(firstSort==kinds.length&&length>=maximum)break;}for(int stage=firstSort;stage<kinds.length;stage++){if(kinds[stage]==SORT){stableSort(values,length,comparators[stage]);}else if(kinds[stage]==FILTER){int write=0;for(int i=0;i<length;i++)if(test(predicates[stage],cursor,values[i],\"rows.filter\"))values[write++]=values[i];length=write;}else if(kinds[stage]==SKIP){int remove=(int)Math.min((long)length,counts[stage]);System.arraycopy(values,remove,values,0,length-remove);length-=remove;}else{length=(int)Math.min((long)length,counts[stage]);}}if(length>maximum)length=maximum;return new Selection(values,length,scanned);}\n")
                .append("  private int firstSort(){for(int i=0;i<kinds.length;i++)if(kinds[i]==SORT)return i;return kinds.length;}\n")
                .append("  private boolean hasSort(){return firstSort()!=kinds.length;}\n")
                .append("  private boolean limitReached(long[] seen,int from,int to){for(int i=from;i<to;i++)if(kinds[i]==LIMIT&&seen[i]>=counts[i])return true;return false;}\n")
                .append("  private boolean matches(int rowIndex,Cursor cursor,long[] seen,int from,int to){for(int i=from;i<to;i++){if(kinds[i]==FILTER){if(!test(predicates[i],cursor,rowIndex,\"rows.filter\"))return false;}else if(kinds[i]==SKIP){if(seen[i]<counts[i]){seen[i]++;return false;}}else if(kinds[i]==LIMIT){if(seen[i]>=counts[i])return false;seen[i]++;}}return true;}\n")
                .append("  private boolean test(Predicate value,Cursor cursor,int rowIndex,String operation){cursor.open(rowIndex);try{return value.test(cursor);}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",operation,\"predicate\",callback);}finally{cursor.close();}}\n")
                .append("  private void stableSort(int[] values,int length,Comparator comparator){int[] auxiliary=table.prepareSortScratch(length);Cursor left=new Cursor(table),right=new Cursor(table);for(int width=1;width<length;width=width>length/2?length:width*2){for(int start=0;start<length;start+=width*2){int middle=Math.min(start+width,length),end=Math.min(start+width*2,length),a=start,b=middle,w=start;while(a<middle||b<end){if(b>=end||(a<middle&&compare(comparator,left,right,values[a],values[b])<=0))auxiliary[w++]=values[a++];else auxiliary[w++]=values[b++];}System.arraycopy(auxiliary,start,values,start,end-start);}}}\n")
                .append("  private int compare(Comparator value,Cursor left,Cursor right,int a,int b){left.open(a);right.open(b);try{return value.compare(left,right);}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(").append(q(table.logicalName)).append(",\"rows.sorted\",\"comparator\",callback);}finally{left.close();right.close();}}\n")
                .append("  private void start(String operation){check(operation);consumed=true;table.begin(operation);}\n  private void check(String operation){if(consumed)throw RuntimeFailures.pipelineConsumed(").append(q(table.logicalName)).append(",operation);}\n")
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
            out.append("  private final ").append(field.columnType).append(' ').append(field.javaName)
                    .append("Column=new ").append(field.columnType).append("();\n");
            if (field.optional) out.append("  private final PresenceBitmap ").append(field.javaName).append("Presence=new PresenceBitmap();\n");
        }
        if (table.keyed()) {
            out.append("  private final ").append(table.keyField().keySpaceType())
                    .append(" keySpace;\n");
        }
        out.append("  private final DenseTableState state;\n")
                .append("  private int[] candidateScratch=new int[0],pipelineScratch=new int[0],sortScratch=new int[0];private boolean[] removeMarks=new boolean[0];private int updateCapacity;\n");
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            out.append("  private ").append(field.primitive).append("[] update")
                    .append(cap(field.javaName)).append("=new ").append(field.primitive).append("[0];\n");
            if (field.optional) out.append("  private boolean[] update").append(cap(field.javaName)).append("Present=new boolean[0];\n");
        }
        out.append("\n  private ").append(name).append("(RuntimePlan plan,TablePlan tablePlan){\n")
                .append("    ColumnGroup columns=new ColumnGroup(tablePlan.initialCapacity()");
        for (FieldSpec field : table.fields) {
            out.append(',').append(field.javaName).append("Column");
            if (field.optional) out.append(',').append(field.javaName).append("Presence");
        }
        out.append(");\n    state=new DenseTableState(TABLE,plan,tablePlan,columns);\n");
        if (table.keyed()) {
            out.append("    keySpace=new ").append(table.keyField().keySpaceType())
                    .append("(tablePlan.initialCapacity());\n");
        }
        out.append("  }\n\n")
                .append("  public static ").append(name).append(" create(){return create(defaultRuntimePlan());}\n")
                .append("  public static ").append(name).append(" create(RuntimePlan plan){if(plan==null)throw new NullPointerException(\"plan\");TablePlan tablePlan=RuntimeCompatibility.verify(METADATA,plan,TABLE);return new ").append(name).append("(plan,tablePlan);}\n")
                .append("  public static RuntimePlan defaultRuntimePlan(){return DEFAULT_RUNTIME_PLAN;}\n")
                .append("  private static RuntimePlan createDefaultRuntimePlan(){return RuntimePlan.builder(")
                .append(q(schemaHash)).append(",RuntimeCompatibility.RUNTIME_COMPATIBILITY,RuntimeCompatibility.GENERATED_PROTOCOL,RuntimeCompatibility.PLAN_PROTOCOL,RuntimeCompatibility.ALLOCATION_ESTIMATOR).addTable(TablePlan.builder(TABLE,RuntimeCompatibility.DENSE_ALGORITHM).initialCapacity(")
                .append(table.defaultCapacity).append(").growthRatio(3,2).maximumUpdateScratchBytes(268435456L).build()).build();}\n")
                .append("  public RuntimePlan runtimePlan(){return state.runtimePlan();}\n  public int size(){return state.size();}\n  public int capacity(){return state.capacity();}\n  public long structuralEpoch(){return state.structuralEpoch();}\n  public boolean isReleased(){return state.isReleased();}\n\n");
        if (table.keyed()) {
            out.append("  public void addBatch(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");state.prepareAppend(0);validateAppendKeys(batch);int count=batch.size();int start=state.prepareAppend(count);copyBatch(batch,0,start,count);installBatchKeys(batch,start,count);state.commitAppend(start,count);}\n")
                    .append("  public void replaceAll(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");state.prepareReplace(0);validateReplacementKeys(batch);int count=batch.size();int previous=state.prepareReplace(count);copyBatch(batch,0,0,count);if(previous>count)clearColumns(count,previous);keySpace.clear();installBatchKeys(batch,0,count);state.commitReplace(previous,count);}\n")
                    .append("  public void clear(){int previous=state.prepareClear();clearColumns(0,previous);keySpace.clear();state.commitClear(previous);}\n")
                    .append("  public void release(){int previous=state.prepareRelease();if(previous>=0){clearColumns(0,previous);keySpace.clear();state.commitRelease(previous);}}\n\n");
        } else {
            out.append("  public void addBatch(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");int count=batch.size();int start=state.prepareAppend(count);copyBatch(batch,0,start,count);state.commitAppend(start,count);}\n")
                    .append("  public void replaceAll(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");int count=batch.size();int previous=state.prepareReplace(count);copyBatch(batch,0,0,count);if(previous>count)clearColumns(count,previous);state.commitReplace(previous,count);}\n")
                    .append("  public void clear(){int previous=state.prepareClear();clearColumns(0,previous);state.commitClear(previous);}\n")
                    .append("  public void release(){int previous=state.prepareRelease();if(previous>=0){clearColumns(0,previous);state.commitRelease(previous);}}\n\n");
        }
        out.append("  private void copyBatch(").append(table.name("Batch")).append(" batch,int source,int target,int count){for(int i=0;i<count;i++){int s=source+i,t=target+i;\n");
        for (FieldSpec field : table.fields) {
            out.append("    ").append(field.javaName).append("Column.set(t,").append(field.storageValue("batch." + field.javaName + "Value(s)")).append(");\n");
            if (field.optional) out.append("    if(batch.").append(field.javaName).append("Present(s))").append(field.javaName).append("Presence.setPresent(t);else ").append(field.javaName).append("Presence.clearPresent(t);\n");
        }
        out.append("  }}\n  private void clearColumns(int from,int to){\n");
        for (FieldSpec field : table.fields) {
            out.append("    ").append(field.javaName).append("Column.clearRange(from,to);\n");
            if (field.optional) out.append("    ").append(field.javaName).append("Presence.clearRange(from,to);\n");
        }
        out.append("  }\n\n");
        if (table.keyed()) {
            FieldSpec key = table.keyField();
            out.append("  private void validateAppendKeys(").append(table.name("Batch")).append(" batch){")
                    .append(key.keySpaceType()).append(" staged=new ").append(key.keySpaceType())
                    .append("(batch.size());for(int row=0;row<batch.size();row++){")
                    .append(key.primitive).append(" key=batch.").append(key.javaName)
                    .append("Value(row);").append(key.keySpaceValueType()).append(" keySlot=")
                    .append(key.keySpaceValue("key", "addBatch"))
                    .append(";if(keySpace.contains(keySlot)||staged.contains(keySlot))throw RuntimeFailures.duplicateKey(TABLE,key,\"addBatch\");staged.put(keySlot,row);}}\n")
                    .append("  private void validateReplacementKeys(").append(table.name("Batch")).append(" batch){")
                    .append(key.keySpaceType()).append(" staged=new ").append(key.keySpaceType())
                    .append("(batch.size());for(int row=0;row<batch.size();row++){")
                    .append(key.primitive).append(" key=batch.").append(key.javaName)
                    .append("Value(row);").append(key.keySpaceValueType()).append(" keySlot=")
                    .append(key.keySpaceValue("key", "replaceAll"))
                    .append(";if(staged.contains(keySlot))throw RuntimeFailures.duplicateKey(TABLE,key,\"replaceAll\");staged.put(keySlot,row);}}\n")
                    .append("  private void installBatchKeys(").append(table.name("Batch")).append(" batch,int start,int count){for(int row=0;row<count;row++)keySpace.put(")
                    .append(key.keySpaceValue("batch." + key.javaName + "Value(row)", "addBatch"))
                    .append(",start+row);}\n")
                    .append("  private int keyRow(").append(key.primitive).append(" key,String operation){int row=keySpace.rowOf(")
                    .append(key.keySpaceValueExpression("key", "operation"))
                    .append(");if(row<0)throw RuntimeFailures.missingKey(TABLE,key,operation);return row;}\n\n");
        }
        out.append("\n")
                .append("  public ").append(table.carrierType).append(" fetchAt(int rowIndex){return fetchAt(rowIndex,runtimePlan().defaultMaterializationBudget());}\n")
                .append("  public ").append(table.carrierType).append(" fetchAt(int rowIndex,MaterializationBudget budget){int row=state.checkRowIndex(rowIndex,\"fetchAt\");if(budget==null)throw new NullPointerException(\"budget\");MaterializationTracker tracker=new MaterializationTracker(budget,TABLE);tracker.checkOwnershipDepth(0);tracker.addTableInstances(1L);tracker.addRows(1L);accountRow(tracker,row);return carrier(row);}\n")
                .append("  public List<").append(table.carrierType).append("> materialize(){return materialize(runtimePlan().defaultMaterializationBudget());}\n")
                .append("  public List<").append(table.carrierType).append("> materialize(MaterializationBudget budget){state.checkActive(\"materialize\");if(budget==null)throw new NullPointerException(\"budget\");int count=size();MaterializationTracker tracker=new MaterializationTracker(budget,TABLE);tracker.checkOwnershipDepth(0);tracker.addTableInstances(1L);tracker.addRows(count);tracker.addEstimatedBytes(40L+8L*(long)count);for(int i=0;i<count;i++)accountRow(tracker,i);List<").append(table.carrierType).append("> result=new ArrayList<").append(table.carrierType).append(">(count);for(int i=0;i<count;i++)result.add(carrier(i));return result;}\n")
                .append("  private void accountRow(MaterializationTracker tracker,int row){long leaves=0L,bytes=").append(16L + 8L * table.fields.size()).append("L;\n");
        for (FieldSpec field : table.fields) {
            if (field.optional) out.append("    if(").append(field.javaName).append("Present(row)){leaves++;bytes+=16L;}\n");
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
            out.append("  public boolean containsKey(").append(key.primitive).append(" key){state.checkActive(\"containsKey\");return keySpace.contains(")
                    .append(key.keySpaceValue("key", "containsKey"))
                    .append(");}\n")
                    .append("  public java.util.Optional<").append(table.carrierType).append("> find(").append(key.primitive).append(" key){state.checkActive(\"find\");int row=keySpace.rowOf(")
                    .append(key.keySpaceValue("key", "find"))
                    .append(");return row<0?java.util.Optional.<").append(table.carrierType).append(">empty():java.util.Optional.of(fetchAt(row));}\n")
                    .append("  public ").append(table.carrierType).append(" fetch(").append(key.primitive).append(" key){return fetchAt(keyRow(key,\"fetch\"));}\n")
                    .append("  public ").append(table.name("Mutator")).append(" mutate(").append(key.primitive).append(" key){return mutateAt(keyRow(key,\"mutate\"));}\n")
                    .append("  public void delete(").append(key.primitive).append(" key){state.beginOperation(\"delete\");long scanned=0L;try{int row=keyRow(key,\"delete\");scanned=1L;int[] selected=preparePipelineScratch();selected[0]=row;removeSelected(selected,1,1L,\"delete\");state.endOperationSuccess(\"delete\",1L,1L,1L);}catch(SomaRuntimeException failure){state.endOperationFailure(\"delete\",scanned,0L,failure.code());throw failure;}catch(Error failure){state.endOperationFailure(\"delete\",scanned,0L,\"callback_failed\");throw failure;}}\n")
                    .append("  public ").append(table.name("Keys")).append(" keys(){state.checkActive(\"keys\");return new ").append(table.name("Keys")).append("(this);}\n");
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
        for (FieldSpec field : table.fields) {
            String type = cap(field.primitive);
            String presence = field.optional ? field.javaName + "Presence" : "null";
            out.append("  public ").append(type).append("ColumnPipeline ")
                    .append(field.javaName).append("Values(){return new ").append(type)
                    .append("ColumnPipeline(state,").append(field.javaName).append("Column,")
                    .append(presence).append(",TABLE,").append(q(field.logicalName)).append(");}\n")
                    .append("  public ").append(type).append("ColumnView ")
                    .append(field.javaName).append("Column(){return new ").append(type)
                    .append("ColumnView(state,").append(field.javaName).append("Column,")
                    .append(presence).append(",TABLE,").append(q(field.logicalName)).append(");}\n");
        }
        out.append("  public TableStats statsSnapshot(){return state.statsSnapshot();}\n  public void resetStats(){state.resetStats();}\n\n")
                .append("  void begin(String operation){state.beginOperation(operation);}\n  void endSuccess(String operation,long scanned,long matched,long changed){state.endOperationSuccess(operation,scanned,matched,changed);}\n  void endFailure(String operation,long scanned,long matched,String code){state.endOperationFailure(operation,scanned,matched,code);}\n  UpdateResult updateResult(long scanned,long matched,long changed){return state.updateResult(scanned,matched,changed,0L,0L);}\n")
                .append("  int[] preparePipelineScratch(){int required=size();if(pipelineScratch.length<required)pipelineScratch=Arrays.copyOf(pipelineScratch,required);return pipelineScratch;}\n")
                .append("  int[] prepareSortScratch(int required){if(sortScratch.length<required)sortScratch=Arrays.copyOf(sortScratch,required);return sortScratch;}\n")
                .append("  ").append(table.carrierType).append(" materializeRow(int row){return fetchAt(row);}\n")
                .append("  List<").append(table.carrierType).append("> materializeRows(int[] rows,int count){MaterializationBudget budget=runtimePlan().defaultMaterializationBudget();MaterializationTracker tracker=new MaterializationTracker(budget,TABLE);tracker.checkOwnershipDepth(0);tracker.addTableInstances(1L);tracker.addRows(count);tracker.addEstimatedBytes(40L+8L*(long)count);for(int i=0;i<count;i++)accountRow(tracker,rows[i]);List<").append(table.carrierType).append("> result=new ArrayList<").append(table.carrierType).append(">(count);for(int i=0;i<count;i++)result.add(carrier(rows[i]));return result;}\n");
        appendTableFieldAccess(out, table);
        appendMutatorCommit(out, table);
        appendUpdateScratch(out, table);
        appendRemove(out, table);
        return out.append("}\n").toString();
    }

    private void appendTableFieldAccess(StringBuilder out, TableSpec table) {
        for (FieldSpec field : table.fields) {
            String c = cap(field.javaName);
            out.append("  ").append(field.primitive).append(' ').append(field.javaName).append("Value(int row){return ")
                    .append(field.publicValue(field.javaName + "Column.get(row)")).append(";}\n");
            if (field.optional) out.append("  boolean ").append(field.javaName).append("Present(int row){return ").append(field.javaName).append("Presence.isPresent(row);}\n");
            if (field.key) {
                continue;
            }
            out.append("  ").append(field.primitive).append(" update").append(c).append("Value(int row){return update").append(c).append("[row];}\n")
                    .append("  void setUpdate").append(c).append("(int row,").append(field.primitive).append(" value){update").append(c).append("[row]=value;");
            if (field.optional) out.append("update").append(c).append("Present[row]=true;");
            out.append("}\n");
            if (field.optional) out.append("  boolean update").append(c).append("Present(int row){return update").append(c).append("Present[row];}\n  void clearUpdate").append(c).append("(int row){update").append(c).append("[row]=").append(field.zero()).append(";update").append(c).append("Present[row]=false;}\n");
        }
    }

    private void appendMutatorCommit(StringBuilder out, TableSpec table) {
        out.append("  void commitMutator(int row,long epoch,").append(table.name("Mutator")).append(" mutation){state.checkRowIndex(row,\"mutator.commit\");if(epoch!=structuralEpoch())throw RuntimeFailures.staleMutator(TABLE,epoch,structuralEpoch());state.beginOperation(\"mutator.commit\");boolean changed=false;\n");
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            String c = cap(field.javaName);
            String newValue = "mutation." + field.javaName + "Value()";
            String different = field.different(field.javaName + "Value(row)", newValue);
            if (field.optional) different = field.javaName + "Present(row)!=mutation." + field.javaName + "Present()||(" + field.javaName + "Present(row)&&" + different + ")";
            out.append("    if(").append(different).append(")changed=true;\n")
                    .append("    ").append(field.javaName).append("Column.set(row,").append(field.storageValue(newValue)).append(");\n");
            if (field.optional) out.append("    if(mutation.").append(field.javaName).append("Present())").append(field.javaName).append("Presence.setPresent(row);else ").append(field.javaName).append("Presence.clearPresent(row);\n");
        }
        out.append("    state.endOperationSuccess(\"mutator.commit\",1L,1L,changed?1L:0L);}\n");
    }

    private void appendUpdateScratch(StringBuilder out, TableSpec table) {
        out.append("  int[] prepareCandidateScratch(){int required=size();long bytes=4L*(long)required+updateBytes(updateCapacity);long limit=runtimePlan().requireTable(TABLE).maximumUpdateScratchBytes();if(bytes>limit)throw RuntimeFailures.memoryLimitExceeded(TABLE,\"rows.update\",limit,bytes);if(candidateScratch.length<required)candidateScratch=Arrays.copyOf(candidateScratch,required);state.updateScratch(4L*(long)candidateScratch.length+updateBytes(updateCapacity),4L*(long)candidateScratch.length+updateBytes(updateCapacity));return candidateScratch;}\n")
                .append("  void prepareUpdateScratch(int required){if(required<=updateCapacity)return;long bytes=4L*(long)candidateScratch.length+updateBytes(required);long limit=runtimePlan().requireTable(TABLE).maximumUpdateScratchBytes();if(bytes>limit)throw RuntimeFailures.memoryLimitExceeded(TABLE,\"rows.update\",limit,bytes);\n");
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            String c = cap(field.javaName);
            out.append("    ").append(field.primitive).append("[] new").append(c).append("=Arrays.copyOf(update").append(c).append(",required);\n");
            if (field.optional) out.append("    boolean[] new").append(c).append("Present=Arrays.copyOf(update").append(c).append("Present,required);\n");
        }
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            String c = cap(field.javaName);
            out.append("    update").append(c).append("=new").append(c).append(";\n");
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
            out.append("    update").append(c).append("[i]=").append(field.javaName).append("Value(row);\n");
            if (field.optional) out.append("    update").append(c).append("Present[i]=").append(field.javaName).append("Present(row);\n");
        }
        out.append("  }}\n  long publishUpdate(int[] rows,int count){long changed=0L;for(int i=0;i<count;i++){int row=rows[i];boolean rowChanged=false;\n");
        for (FieldSpec field : table.fields) {
            if (field.key) {
                continue;
            }
            String c = cap(field.javaName);
            String different = field.different(field.javaName + "Value(row)", "update" + c + "[i]");
            if (field.optional) different = field.javaName + "Present(row)!=update" + c + "Present[i]||(" + field.javaName + "Present(row)&&" + different + ")";
            out.append("    if(").append(different).append(")rowChanged=true;\n")
                    .append("    ").append(field.javaName).append("Column.set(row,").append(field.storageValue("update" + c + "[i]")).append(");\n");
            if (field.optional) out.append("    if(update").append(c).append("Present[i])").append(field.javaName).append("Presence.setPresent(row);else ").append(field.javaName).append("Presence.clearPresent(row);\n");
        }
        out.append("    if(rowChanged)changed++;}return changed;}\n");
    }

    private void appendRemove(StringBuilder out, TableSpec table) {
        out.append("  RemoveResult removeSelected(int[] selected,int count,long scanned,String operation){int previous=size();if(count<0||count>previous)throw RuntimeFailures.internalInvariant(\"remove_selection_count\",TABLE,operation);if(removeMarks.length<previous)removeMarks=Arrays.copyOf(removeMarks,previous);Arrays.fill(removeMarks,0,previous,false);for(int i=0;i<count;i++){int row=selected[i];if(row<0||row>=previous||removeMarks[row])throw RuntimeFailures.internalInvariant(\"remove_selection_identity\",TABLE,operation);removeMarks[row]=true;}");
        if (table.keyed()) {
            FieldSpec key = table.keyField();
            out.append("    for(int row=0;row<previous;row++)if(removeMarks[row])keySpace.remove(")
                    .append(key.keySpaceValue(key.javaName + "Value(row)", "rows.remove"))
                    .append(");\n");
        }
        out.append("int write=0;long compacted=0L;for(int read=0;read<previous;read++){if(removeMarks[read])continue;if(write!=read){\n");
        for (FieldSpec field : table.fields) {
            out.append("    ").append(field.javaName).append("Column.set(write,")
                    .append(field.javaName).append("Column.get(read));\n");
            if (field.optional) {
                out.append("    if(").append(field.javaName).append("Presence.isPresent(read))")
                        .append(field.javaName).append("Presence.setPresent(write);else ")
                        .append(field.javaName).append("Presence.clearPresent(write);\n");
            }
        }
        if (table.keyed()) {
            FieldSpec key = table.keyField();
            out.append("    keySpace.updateRow(")
                    .append(key.keySpaceValue(key.javaName + "Value(read)", "rows.remove"))
                    .append(",write);\n");
        }
        out.append("    compacted++;}write++;}clearColumns(write,previous);state.commitStructuralRemove(previous,write,operation);return state.removeResult(scanned,count,count,compacted,0L,0L);}\n");
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

        TableSpec(Element origin, String carrierType, String carrierSimpleName,
                  String logicalName, int defaultCapacity, List<FieldSpec> fields) {
            this.origin = origin;
            this.carrierType = carrierType;
            this.carrierSimpleName = carrierSimpleName;
            this.logicalName = logicalName;
            this.defaultCapacity = defaultCapacity;
            this.fields = new ArrayList<FieldSpec>(fields);
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
                result += field.bytes();
                if (field.optional) result++;
            }
            return result;
        }
    }

    static final class FieldSpec {
        final String javaName;
        final String logicalName;
        final String primitive;
        final String boxed;
        final String columnType;
        final boolean optional;
        final boolean key;

        FieldSpec(String javaName, String logicalName, String primitive,
                  String boxed, String columnType, boolean optional, boolean key) {
            this.javaName = javaName;
            this.logicalName = logicalName;
            this.primitive = primitive;
            this.boxed = boxed;
            this.columnType = columnType;
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
            return boxed + ".valueOf(" + expression + ")";
        }

        String storageValue(String expression) { return expression; }
        String publicValue(String expression) { return expression; }

        String batchStorageValue(String expression) {
            if (!key) {
                return expression;
            }
            if ("float".equals(primitive)) {
                return "KeyCanonicalization.strictFloatStorage(TABLE,"
                        + q(logicalName) + "," + expression + ",\"batch.write\")";
            }
            if ("double".equals(primitive)) {
                return "KeyCanonicalization.strictDoubleStorage(TABLE,"
                        + q(logicalName) + "," + expression + ",\"batch.write\")";
            }
            return expression;
        }

        String keySpaceType() {
            if ("long".equals(primitive) || "double".equals(primitive)) {
                return "HashLongKeySpace";
            }
            return "HashIntKeySpace";
        }

        String keySpaceValue(String expression, String operation) {
            return keySpaceValueExpression(expression, q(operation));
        }

        String keySpaceValueExpression(String expression, String operationExpression) {
            if ("boolean".equals(primitive)) {
                return "(" + expression + "?1:0)";
            }
            if ("byte".equals(primitive) || "short".equals(primitive)
                    || "int".equals(primitive) || "long".equals(primitive)) {
                return expression;
            }
            if ("float".equals(primitive)) {
                return "KeyCanonicalization.strictFloatKeyBits(TABLE,"
                        + q(logicalName) + "," + expression + "," + operationExpression + ")";
            }
            if ("double".equals(primitive)) {
                return "KeyCanonicalization.strictDoubleKeyBits(TABLE,"
                        + q(logicalName) + "," + expression + "," + operationExpression + ")";
            }
            throw new IllegalStateException("unsupported primitive key: " + primitive);
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
            if ("boolean".equals(primitive) || "byte".equals(primitive)) return 1;
            if ("short".equals(primitive)) return 2;
            if ("int".equals(primitive) || "float".equals(primitive)) return 4;
            return 8;
        }
    }
}
