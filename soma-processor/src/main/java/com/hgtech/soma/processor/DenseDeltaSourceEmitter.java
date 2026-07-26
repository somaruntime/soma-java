package com.hgtech.soma.processor;

import static com.hgtech.soma.processor.DenseSourceNames.q;
import static com.hgtech.soma.processor.DenseTableCodegenModel.FieldSpec;
import static com.hgtech.soma.processor.DenseTableCodegenModel.TableSpec;
import static com.hgtech.soma.processor.DenseTableCodegenModel.ValueLeafSpec;

/** Emits the detached, ordered Delta command type for keyed Tables. */
final class DenseDeltaSourceEmitter {
    private final String generatedPackage;
    private final String schemaHash;

    DenseDeltaSourceEmitter(String generatedPackage, String schemaHash) {
        this.generatedPackage = generatedPackage;
        this.schemaHash = schemaHash;
    }

    String deltaSource(TableSpec table) {
        if (!table.keyed()) {
            throw new IllegalArgumentException("Delta requires a keyed Table");
        }
        FieldSpec key = table.keyField();
        String delta = table.name("Delta");
        String batch = table.name("Batch");
        SourceBuilder out = new SourceBuilder(
                "// SOMA-GENERATED: soma-processor-v1\npackage "
                        + generatedPackage + ";\n\n");
        out.append("import com.hgtech.soma.runtime.generated.KeyCanonicalization;\n")
                .append("import com.hgtech.soma.runtime.generated.RuntimeFailures;\n")
                .append("import java.util.Arrays;\n\n")
                .append("public final class ").append(delta).append(" {\n")
                .append("  private static final String TABLE=")
                .append(q(table.logicalName)).append(";\n")
                .append("  private static final String SCHEMA_HASH=")
                .append(q(schemaHash)).append(";\n")
                .append("  static final byte INSERT=1,UPDATE=2,DELETE=3;\n");
        appendKeyEnumConstants(out, key);
        out.append("  private byte[] kinds;\n")
                .append("  private ").append(key.primitive).append("[] keys;\n")
                .append("  private int[] rowIndexes;\n")
                .append("  private ").append(batch).append(" rows;\n")
                .append("  private int size;\n")
                .append("  private boolean expectedStructuralEpochSet;\n")
                .append("  private long expectedStructuralEpoch;\n\n")
                .append("  public ").append(delta).append("(){this(16);}\n")
                .append("  public ").append(delta)
                .append("(int initialCapacity){if(initialCapacity<0)throw new IllegalArgumentException(\"initialCapacity must be non-negative\");")
                .append("kinds=new byte[initialCapacity];keys=new ")
                .append(key.primitive).append("[initialCapacity];rowIndexes=new int[initialCapacity];")
                .append("rows=new ").append(batch).append("(initialCapacity);}\n\n")
                .append("  public String schemaHash(){return SCHEMA_HASH;}\n")
                .append("  public String tableIdentity(){return TABLE;}\n")
                .append("  public int size(){return size;}\n")
                .append("  public boolean isEmpty(){return size==0;}\n")
                .append("  public ").append(delta)
                .append(" expectStructuralEpoch(long epoch){if(epoch<0L)throw new IllegalArgumentException(\"epoch must be non-negative\");expectedStructuralEpoch=epoch;expectedStructuralEpochSet=true;return this;}\n")
                .append("  public boolean hasExpectedStructuralEpoch(){return expectedStructuralEpochSet;}\n")
                .append("  public long expectedStructuralEpoch(){if(!expectedStructuralEpochSet)throw new IllegalStateException(\"expected structural epoch is not set\");return expectedStructuralEpoch;}\n")
                .append("  public ").append(delta)
                .append(" clearExpectedStructuralEpoch(){expectedStructuralEpochSet=false;expectedStructuralEpoch=0L;return this;}\n")
                .append("  public ").append(delta).append(" insert(")
                .append(table.carrierType).append(" row){return addRow(INSERT,row);}\n")
                .append("  public ").append(delta).append(" update(")
                .append(table.carrierType).append(" row){return addRow(UPDATE,row);}\n")
                .append("  public ").append(delta).append(" delete(")
                .append(key.primitive).append(" key){ensureOne();keys[size]=canonicalKey(key);kinds[size]=DELETE;rowIndexes[size]=-1;size++;return this;}\n")
                .append("  public Operation operationAt(int index){checkEntry(index);switch(kinds[index]){case INSERT:return Operation.INSERT;case UPDATE:return Operation.UPDATE;case DELETE:return Operation.DELETE;default:throw RuntimeFailures.internalInvariant(\"delta_operation\",TABLE,\"delta.operationAt\");}}\n")
                .append("  public ").append(key.primitive)
                .append(" keyAt(int index){checkEntry(index);return keys[index];}\n\n")
                .append("  private ").append(delta).append(" addRow(byte kind,")
                .append(table.carrierType).append(" row){if(row==null)throw new NullPointerException(\"row\");ensureOne();int rowIndex=rows.size();rows.add(row);keys[size]=rows.")
                .append(key.javaName).append("Value(rowIndex);kinds[size]=kind;rowIndexes[size]=rowIndex;size++;return this;}\n")
                .append("  private void ensureOne(){if(size==Integer.MAX_VALUE)throw new IllegalStateException(\"delta size overflow\");if(size<kinds.length)return;int next=(int)Math.min((long)Integer.MAX_VALUE,Math.max((long)size+1L,Math.max(1L,((long)size*3L+1L)/2L)));kinds=Arrays.copyOf(kinds,next);keys=Arrays.copyOf(keys,next);rowIndexes=Arrays.copyOf(rowIndexes,next);}\n")
                .append("  private void checkEntry(int index){if(index<0||index>=size)throw new IndexOutOfBoundsException(\"delta entry: \"+index);}\n");
        appendCanonicalKey(out, key);
        appendSameKey(out, key);
        out.append("  byte kindAt(int index){return kinds[index];}\n")
                .append("  int rowIndexAt(int index){return rowIndexes[index];}\n")
                .append("  ").append(batch).append(" rows(){return rows;}\n")
                .append("  boolean sameKey(int left,int right){return sameKeyValue(keys[left],keys[right]);}\n")
                .append("  ").append(delta).append(" copy(){")
                .append(delta).append(" copy=new ").append(delta)
                .append("(size);copy.size=size;copy.kinds=Arrays.copyOf(kinds,size);copy.keys=Arrays.copyOf(keys,size);copy.rowIndexes=Arrays.copyOf(rowIndexes,size);copy.rows=rows.copy();copy.expectedStructuralEpochSet=expectedStructuralEpochSet;copy.expectedStructuralEpoch=expectedStructuralEpoch;return copy;}\n")
                .append("  public enum Operation{INSERT,UPDATE,DELETE}\n")
                .append("}\n");
        return out.toString();
    }

    private static void appendKeyEnumConstants(SourceBuilder out, FieldSpec key) {
        if (key.enumType != null) {
            out.append("  private static final ").append(key.enumType).append("[] ")
                    .append(key.enumConstantsName()).append('=')
                    .append(key.enumType).append(".values();\n");
        }
        for (ValueLeafSpec leaf : key.valueLeaves) {
            if (leaf.enumType != null) {
                out.append("  private static final ").append(leaf.enumType).append("[] ")
                        .append(leaf.enumConstantsName(key)).append('=')
                        .append(leaf.enumType).append(".values();\n");
            }
        }
    }

    private static void appendCanonicalKey(SourceBuilder out, FieldSpec key) {
        out.append("  private static ").append(key.primitive)
                .append(" canonicalKey(").append(key.primitive).append(" key){");
        if (key.valueBacked()) {
            out.append(key.primitive)
                    .append(" required=RuntimeFailures.requiredValue(TABLE,")
                    .append(q(key.logicalName))
                    .append(",key,\"delta.key\");");
            String result = key.valueConstructionTemplate;
            for (int index = 0; index < key.valueLeaves.size(); index++) {
                ValueLeafSpec leaf = key.valueLeaves.get(index);
                String local = "leaf" + index;
                out.append(leaf.storagePrimitive).append(' ').append(local)
                        .append('=').append(leaf.storageValue(
                                key, "required." + leaf.javaName, "delta.key"))
                        .append(';');
                result = result.replace(
                        "@{" + index + "}@", leaf.publicValue(key, local));
            }
            out.append("return ").append(result).append(';');
        } else if (key.enumType != null) {
            out.append("return RuntimeFailures.requiredEnumValue(TABLE,")
                    .append(q(key.logicalName)).append(",key,\"delta.key\");");
        } else if ("java.lang.String".equals(key.primitive)) {
            out.append("return RuntimeFailures.requiredValue(TABLE,")
                    .append(q(key.logicalName)).append(",key,\"delta.key\");");
        } else {
            out.append("return ").append(key.storageValue("key", "delta.key"))
                    .append(';');
        }
        out.append("}\n");
    }

    private static void appendSameKey(SourceBuilder out, FieldSpec key) {
        out.append("  private static boolean sameKeyValue(")
                .append(key.primitive).append(" left,")
                .append(key.primitive).append(" right){return ");
        if (key.valueBacked()) {
            for (int index = 0; index < key.valueLeaves.size(); index++) {
                if (index > 0) {
                    out.append("&&");
                }
                ValueLeafSpec leaf = key.valueLeaves.get(index);
                String left = leaf.keyInputStorage(
                        key, "left." + leaf.javaName, q("delta.key"));
                String right = leaf.keyInputStorage(
                        key, "right." + leaf.javaName, q("delta.key"));
                out.append(leaf.keyEqual(
                        key, left, right, q("delta.key")));
            }
        } else if ("java.lang.String".equals(key.primitive)) {
            out.append("left.equals(right)");
        } else if ("float".equals(key.primitive)) {
            out.append("KeyCanonicalization.strictFloatKeyBits(TABLE,")
                    .append(q(key.logicalName))
                    .append(",left,\"delta.key\")==KeyCanonicalization.strictFloatKeyBits(TABLE,")
                    .append(q(key.logicalName)).append(",right,\"delta.key\")");
        } else if ("double".equals(key.primitive)) {
            out.append("KeyCanonicalization.strictDoubleKeyBits(TABLE,")
                    .append(q(key.logicalName))
                    .append(",left,\"delta.key\")==KeyCanonicalization.strictDoubleKeyBits(TABLE,")
                    .append(q(key.logicalName)).append(",right,\"delta.key\")");
        } else {
            out.append("left==right");
        }
        out.append(";}\n");
    }
}
