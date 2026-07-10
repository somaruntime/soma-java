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
                    .append("Values[row] = value;");
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
            out.append("  private ").append(field.primitive).append(' ')
                    .append(field.javaName).append("Value;\n");
            if (field.optional) out.append("  private boolean ").append(field.javaName).append("Present;\n");
        }
        out.append("\n  ").append(name).append('(').append(table.name("Table"))
                .append(" table, int rowIndex, long epoch) {\n    this.table = table; this.rowIndex = rowIndex; this.epoch = epoch;\n");
        for (FieldSpec field : table.fields) {
            out.append("    this.").append(field.javaName).append("Value = table.")
                    .append(field.javaName).append("Value(rowIndex);\n");
            if (field.optional) out.append("    this.").append(field.javaName).append("Present = table.").append(field.javaName).append("Present(rowIndex);\n");
        }
        out.append("  }\n\n");
        for (FieldSpec field : table.fields) {
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

    private String rowsSource(TableSpec table) {
        String rows = table.name("Rows");
        String row = table.name("Row");
        String mutable = table.name("MutableRow");
        StringBuilder out = new StringBuilder(header());
        out.append("import com.hgtech.soma.runtime.SomaRuntimeException;\n")
                .append("import com.hgtech.soma.runtime.UpdateResult;\n")
                .append("import com.hgtech.soma.runtime.generated.RuntimeFailures;\n")
                .append("import java.util.Arrays;\n\n")
                .append("public final class ").append(rows).append(" {\n")
                .append("  private static final byte FILTER = 1, SKIP = 2, LIMIT = 3;\n")
                .append("  private final ").append(table.name("Table")).append(" table;\n")
                .append("  private final byte[] kinds; private final Predicate[] predicates; private final long[] counts;\n")
                .append("  private boolean consumed;\n\n")
                .append("  ").append(rows).append('(').append(table.name("Table"))
                .append(" table) { this(table, new byte[0], new Predicate[0], new long[0]); }\n")
                .append("  private ").append(rows).append('(').append(table.name("Table"))
                .append(" table, byte[] kinds, Predicate[] predicates, long[] counts) { this.table=table; this.kinds=kinds; this.predicates=predicates; this.counts=counts; }\n\n")
                .append("  public ").append(rows).append(" filter(Predicate predicate) { if (predicate==null) throw new NullPointerException(\"predicate\"); return append(FILTER,predicate,0L); }\n")
                .append("  public ").append(rows).append(" skip(long count) { if (count<0L) throw new IllegalArgumentException(\"count must be non-negative\"); return append(SKIP,null,count); }\n")
                .append("  public ").append(rows).append(" limit(long count) { if (count<0L) throw new IllegalArgumentException(\"count must be non-negative\"); return append(LIMIT,null,count); }\n\n")
                .append("  private ").append(rows).append(" append(byte kind, Predicate predicate, long count) { check(\"intermediate\"); byte[] nk=Arrays.copyOf(kinds,kinds.length+1); Predicate[] np=Arrays.copyOf(predicates,predicates.length+1); long[] nc=Arrays.copyOf(counts,counts.length+1); nk[kinds.length]=kind; np[predicates.length]=predicate; nc[counts.length]=count; consumed=true; return new ")
                .append(rows).append("(table,nk,np,nc); }\n\n")
                .append("  public long count() {\n    start(\"rows.count\"); long scanned=0L, matched=0L; Cursor cursor=new Cursor(table); long[] seen=new long[kinds.length];\n")
                .append("    try { for(int i=0;i<table.size() && !limitReached(seen);i++){ scanned++; cursor.open(i); if(matches(cursor,seen)){ matched++; } cursor.close(); } table.endSuccess(\"rows.count\",scanned,matched,0L); return matched; }\n")
                .append("    catch(SomaRuntimeException failure){ cursor.close(); table.endFailure(\"rows.count\",scanned,matched,failure.code()); throw failure; } catch(Error fatal){ cursor.close(); table.endFailure(\"rows.count\",scanned,matched,\"callback_failed\"); throw fatal; }\n  }\n\n")
                .append("  public void forEach(Consumer consumer) { if(consumer==null) throw new NullPointerException(\"consumer\"); start(\"rows.forEach\"); long scanned=0L,matched=0L; Cursor cursor=new Cursor(table); long[] seen=new long[kinds.length];\n")
                .append("    try { for(int i=0;i<table.size() && !limitReached(seen);i++){ scanned++; cursor.open(i); if(matches(cursor,seen)){ matched++; try{ consumer.accept(cursor); }catch(RuntimeException callback){ throw RuntimeFailures.callbackFailed(")
                .append(q(table.logicalName)).append(",\"rows.forEach\",\"consumer\",callback); } } cursor.close(); } table.endSuccess(\"rows.forEach\",scanned,matched,0L); }\n")
                .append("    catch(SomaRuntimeException failure){ cursor.close(); table.endFailure(\"rows.forEach\",scanned,matched,failure.code()); throw failure; } catch(Error fatal){ cursor.close(); table.endFailure(\"rows.forEach\",scanned,matched,\"callback_failed\"); throw fatal; }\n  }\n\n")
                .append("  public UpdateResult update(Updater updater) { if(updater==null) throw new NullPointerException(\"updater\"); start(\"rows.update\"); long scanned=0L,matched=0L; Cursor cursor=new Cursor(table); long[] seen=new long[kinds.length];\n")
                .append("    try { int[] candidates=table.prepareCandidateScratch(); for(int i=0;i<table.size() && !limitReached(seen);i++){ scanned++; cursor.open(i); if(matches(cursor,seen)){ candidates[(int)matched++]=i; } cursor.close(); } table.prepareUpdateScratch((int)matched); table.loadUpdateScratch(candidates,(int)matched); MutableCursor mutable=new MutableCursor(table); for(int i=0;i<(int)matched;i++){ mutable.open(i); try{ updater.update(mutable); }catch(RuntimeException callback){ throw RuntimeFailures.callbackFailed(")
                .append(q(table.logicalName)).append(",\"rows.update\",\"updater\",callback); } mutable.close(); } long changed=table.publishUpdate(candidates,(int)matched); table.endSuccess(\"rows.update\",scanned,matched,changed); return table.updateResult(scanned,matched,changed); }\n")
                .append("    catch(SomaRuntimeException failure){ cursor.close(); table.endFailure(\"rows.update\",scanned,matched,failure.code()); throw failure; } catch(Error fatal){ cursor.close(); table.endFailure(\"rows.update\",scanned,matched,\"callback_failed\"); throw fatal; }\n  }\n\n")
                .append("  private void start(String operation){ check(operation); consumed=true; table.begin(operation); }\n")
                .append("  private void check(String operation){ if(consumed) throw RuntimeFailures.pipelineConsumed(")
                .append(q(table.logicalName)).append(",operation); }\n")
                .append("  private boolean limitReached(long[] seen){ for(int i=0;i<kinds.length;i++) if(kinds[i]==LIMIT && seen[i]>=counts[i]) return true; return false; }\n")
                .append("  private boolean matches(Cursor cursor,long[] seen){ for(int i=0;i<kinds.length;i++){ if(kinds[i]==FILTER){ boolean accepted; try{ accepted=predicates[i].test(cursor); }catch(RuntimeException callback){ throw RuntimeFailures.callbackFailed(")
                .append(q(table.logicalName)).append(",\"rows.filter\",\"predicate\",callback); } if(!accepted)return false; } else if(kinds[i]==SKIP){ if(seen[i]<counts[i]){seen[i]++;return false;} } else { if(seen[i]>=counts[i])return false; seen[i]++; } } return true; }\n\n")
                .append("  public interface Predicate { boolean test(").append(row).append(" row); }\n")
                .append("  public interface Consumer { void accept(").append(row).append(" row); }\n")
                .append("  public interface Updater { void update(").append(mutable).append(" row); }\n\n")
                .append("  private static final class Cursor implements ").append(row).append(" {\n    protected final ").append(table.name("Table")).append(" table; protected int row; protected boolean active; Cursor(").append(table.name("Table")).append(" table){this.table=table;} void open(int row){this.row=row;active=true;} void close(){active=false;} void valid(){if(!active)throw RuntimeFailures.internalInvariant(\"escaped_row_cursor\",").append(q(table.logicalName)).append(",\"cursor\");}\n");
        appendCursorMethods(out, table, false);
        out.append("  }\n\n  private static final class MutableCursor implements ").append(mutable).append(" {\n    private final ").append(table.name("Table")).append(" table; private int scratch; private boolean active; MutableCursor(").append(table.name("Table")).append(" table){this.table=table;} void open(int scratch){this.scratch=scratch;active=true;} void close(){active=false;} void valid(){if(!active)throw RuntimeFailures.internalInvariant(\"escaped_mutable_cursor\",").append(q(table.logicalName)).append(",\"cursor\");}\n");
        appendCursorMethods(out, table, true);
        for (FieldSpec field : table.fields) {
            out.append("    public void set").append(cap(field.javaName)).append('(').append(field.primitive).append(" value){valid();table.setUpdate").append(cap(field.javaName)).append("(scratch,value);}\n");
            if (field.optional) out.append("    public void clear").append(cap(field.javaName)).append("(){valid();table.clearUpdate").append(cap(field.javaName)).append("(scratch);}\n");
        }
        return out.append("  }\n}\n").toString();
    }

    private void appendCursorMethods(StringBuilder out, TableSpec table, boolean scratch) {
        for (FieldSpec field : table.fields) {
            String index = scratch ? "scratch" : "row";
            String value = scratch ? "update" + cap(field.javaName) + "Value(" + index + ")" : field.javaName + "Value(" + index + ")";
            String present = scratch ? "update" + cap(field.javaName) + "Present(" + index + ")" : field.javaName + "Present(" + index + ")";
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
        out.append("  private final DenseTableState state;\n")
                .append("  private int[] candidateScratch=new int[0]; private int updateCapacity;\n");
        for (FieldSpec field : table.fields) {
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
        out.append(");\n    state=new DenseTableState(TABLE,plan,tablePlan,columns);\n  }\n\n")
                .append("  public static ").append(name).append(" create(){return create(defaultRuntimePlan());}\n")
                .append("  public static ").append(name).append(" create(RuntimePlan plan){if(plan==null)throw new NullPointerException(\"plan\");TablePlan tablePlan=RuntimeCompatibility.verify(METADATA,plan,TABLE);return new ").append(name).append("(plan,tablePlan);}\n")
                .append("  public static RuntimePlan defaultRuntimePlan(){return DEFAULT_RUNTIME_PLAN;}\n")
                .append("  private static RuntimePlan createDefaultRuntimePlan(){return RuntimePlan.builder(")
                .append(q(schemaHash)).append(",RuntimeCompatibility.RUNTIME_COMPATIBILITY,RuntimeCompatibility.GENERATED_PROTOCOL,RuntimeCompatibility.PLAN_PROTOCOL,RuntimeCompatibility.ALLOCATION_ESTIMATOR).addTable(TablePlan.builder(TABLE,RuntimeCompatibility.DENSE_ALGORITHM).initialCapacity(")
                .append(table.defaultCapacity).append(").growthRatio(3,2).maximumUpdateScratchBytes(268435456L).build()).build();}\n")
                .append("  public RuntimePlan runtimePlan(){return state.runtimePlan();}\n  public int size(){return state.size();}\n  public int capacity(){return state.capacity();}\n  public long structuralEpoch(){return state.structuralEpoch();}\n  public boolean isReleased(){return state.isReleased();}\n\n")
                .append("  public void addBatch(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");int count=batch.size();int start=state.prepareAppend(count);copyBatch(batch,0,start,count);state.commitAppend(start,count);}\n")
                .append("  public void replaceAll(").append(table.name("Batch")).append(" batch){if(batch==null)throw new NullPointerException(\"batch\");int count=batch.size();int previous=state.prepareReplace(count);copyBatch(batch,0,0,count);if(previous>count)clearColumns(count,previous);state.commitReplace(previous,count);}\n")
                .append("  public void clear(){int previous=state.prepareClear();clearColumns(0,previous);state.commitClear(previous);}\n")
                .append("  public void release(){int previous=state.prepareRelease();if(previous>=0){clearColumns(0,previous);state.commitRelease(previous);}}\n\n")
                .append("  private void copyBatch(").append(table.name("Batch")).append(" batch,int source,int target,int count){for(int i=0;i<count;i++){int s=source+i,t=target+i;\n");
        for (FieldSpec field : table.fields) {
            out.append("    ").append(field.javaName).append("Column.set(t,").append(field.storageValue("batch." + field.javaName + "Value(s)")).append(");\n");
            if (field.optional) out.append("    if(batch.").append(field.javaName).append("Present(s))").append(field.javaName).append("Presence.setPresent(t);else ").append(field.javaName).append("Presence.clearPresent(t);\n");
        }
        out.append("  }}\n  private void clearColumns(int from,int to){\n");
        for (FieldSpec field : table.fields) {
            out.append("    ").append(field.javaName).append("Column.clearRange(from,to);\n");
            if (field.optional) out.append("    ").append(field.javaName).append("Presence.clearRange(from,to);\n");
        }
        out.append("  }\n\n")
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
        out.append("    return value;}\n\n")
                .append("  public ").append(table.name("Mutator")).append(" mutateAt(int rowIndex){int row=state.checkRowIndex(rowIndex,\"mutateAt\");return new ").append(table.name("Mutator")).append("(this,row,structuralEpoch());}\n")
                .append("  public ").append(table.name("Rows")).append(" rows(){state.checkActive(\"rows\");return new ").append(table.name("Rows")).append("(this);}\n")
                .append("  public ").append(table.name("Rows")).append(" filter(").append(table.name("Rows")).append(".Predicate predicate){return rows().filter(predicate);}\n")
                .append("  public ").append(table.name("Rows")).append(" skip(long count){return rows().skip(count);}\n")
                .append("  public ").append(table.name("Rows")).append(" limit(long count){return rows().limit(count);}\n")
                .append("  public long count(){return rows().count();}\n")
                .append("  public void forEach(").append(table.name("Rows")).append(".Consumer consumer){rows().forEach(consumer);}\n")
                .append("  public UpdateResult update(").append(table.name("Rows")).append(".Updater updater){return rows().update(updater);}\n")
                .append("  public TableStats statsSnapshot(){return state.statsSnapshot();}\n  public void resetStats(){state.resetStats();}\n\n")
                .append("  void begin(String operation){state.beginOperation(operation);}\n  void endSuccess(String operation,long scanned,long matched,long changed){state.endOperationSuccess(operation,scanned,matched,changed);}\n  void endFailure(String operation,long scanned,long matched,String code){state.endOperationFailure(operation,scanned,matched,code);}\n  UpdateResult updateResult(long scanned,long matched,long changed){return state.updateResult(scanned,matched,changed,0L,0L);}\n");
        appendTableFieldAccess(out, table);
        appendMutatorCommit(out, table);
        appendUpdateScratch(out, table);
        return out.append("}\n").toString();
    }

    private void appendTableFieldAccess(StringBuilder out, TableSpec table) {
        for (FieldSpec field : table.fields) {
            String c = cap(field.javaName);
            out.append("  ").append(field.primitive).append(' ').append(field.javaName).append("Value(int row){return ")
                    .append(field.publicValue(field.javaName + "Column.get(row)")).append(";}\n");
            if (field.optional) out.append("  boolean ").append(field.javaName).append("Present(int row){return ").append(field.javaName).append("Presence.isPresent(row);}\n");
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
            String c = cap(field.javaName);
            out.append("    ").append(field.primitive).append("[] new").append(c).append("=Arrays.copyOf(update").append(c).append(",required);\n");
            if (field.optional) out.append("    boolean[] new").append(c).append("Present=Arrays.copyOf(update").append(c).append("Present,required);\n");
        }
        for (FieldSpec field : table.fields) {
            String c = cap(field.javaName);
            out.append("    update").append(c).append("=new").append(c).append(";\n");
            if (field.optional) out.append("    update").append(c).append("Present=new").append(c).append("Present;\n");
        }
        out.append("    updateCapacity=required;state.updateScratch(bytes,bytes);}\n")
                .append("  private long updateBytes(int capacity){return (long)capacity*").append(table.updateWidth()).append("L;}\n")
                .append("  void loadUpdateScratch(int[] rows,int count){for(int i=0;i<count;i++){int row=rows[i];\n");
        for (FieldSpec field : table.fields) {
            String c = cap(field.javaName);
            out.append("    update").append(c).append("[i]=").append(field.javaName).append("Value(row);\n");
            if (field.optional) out.append("    update").append(c).append("Present[i]=").append(field.javaName).append("Present(row);\n");
        }
        out.append("  }}\n  long publishUpdate(int[] rows,int count){long changed=0L;for(int i=0;i<count;i++){int row=rows[i];boolean rowChanged=false;\n");
        for (FieldSpec field : table.fields) {
            String c = cap(field.javaName);
            String different = field.different(field.javaName + "Value(row)", "update" + c + "[i]");
            if (field.optional) different = field.javaName + "Present(row)!=update" + c + "Present[i]||(" + field.javaName + "Present(row)&&" + different + ")";
            out.append("    if(").append(different).append(")rowChanged=true;\n")
                    .append("    ").append(field.javaName).append("Column.set(row,").append(field.storageValue("update" + c + "[i]")).append(");\n");
            if (field.optional) out.append("    if(update").append(c).append("Present[i])").append(field.javaName).append("Presence.setPresent(row);else ").append(field.javaName).append("Presence.clearPresent(row);\n");
        }
        out.append("    if(rowChanged)changed++;}return changed;}\n");
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

        FieldSpec(String javaName, String logicalName, String primitive,
                  String boxed, String columnType, boolean optional) {
            this.javaName = javaName;
            this.logicalName = logicalName;
            this.primitive = primitive;
            this.boxed = boxed;
            this.columnType = columnType;
            this.optional = optional;
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
