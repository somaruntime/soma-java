package com.hgtech.soma.processor;

import java.util.ArrayList;
import java.util.List;

import static com.hgtech.soma.processor.DenseScanExecutionSourceSupport.*;
import static com.hgtech.soma.processor.DenseSelectorCodegenModel.*;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.*;
import static com.hgtech.soma.processor.DenseSourceNames.*;
import static com.hgtech.soma.processor.DenseTableCodegenModel.*;
import static com.hgtech.soma.processor.DenseValueSourceSupport.*;

/** Emits the schema-specific packed Table implementation. */
final class DenseTableSourceEmitter {
    private final String generatedPackage;
    private final String schemaHash;
    private final List<TableSpec> schemaTables;

    DenseTableSourceEmitter(
            String generatedPackage,
            String schemaHash,
            List<TableSpec> schemaTables) {
        this.generatedPackage = generatedPackage;
        this.schemaHash = schemaHash;
        this.schemaTables = new ArrayList<TableSpec>(schemaTables);
    }

    private String header() {
        return "// SOMA-GENERATED: soma-processor-v1\npackage "
                + generatedPackage + ";\n\n";
    }

    String tableSource(TableSpec table) {
        String name = table.name("Table");
        SourceBuilder out = new SourceBuilder(header());
        out.append("import com.hgtech.soma.runtime.*;\n")
                .append("import com.hgtech.soma.runtime.generated.*;\n")
                .append("import com.hgtech.soma.dataflow.DeltaApplyResult;\n")
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
        out.append(");\n    state=new DenseTableState(TABLE,plan,tablePlan,columns,ownership);\n");
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
        if (table.keyed()) {
            appendDeltaApply(out, table);
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
                .append("  void requireDataFlowRootSource(){state.checkActive(\"dataflow.bind\");if(owned)throw RuntimeFailures.ownedDataFlowSource(TABLE,\"dataflow.bind\");}\n")
                .append("  long dataFlowAggregateInstanceId(){return ownership.aggregateInstanceId();}\n")
                .append("  Object dataFlowPhysicalIdentity(){return ownership.physicalIdentity();}\n")
                .append("  long dataFlowStructuralEpoch(){return state.structuralEpoch();}\n")
                .append("  int dataFlowPackedSize(){return state.size();}\n")
                .append("  IndexSnapshot dataFlowIndexSnapshot(int[] indexes,int length){return IndexSnapshots.copyOf(indexSnapshotOwner,state.structuralEpoch(),indexes,length);}\n")
                .append("  void acquireDataFlow(String operation){state.checkActive(operation);ownership.beginDataFlow(operation);}\n")
                .append("  void releaseDataFlow(String operation){ownership.endDataFlow(operation);}\n")
                .append("  UpdateResult dataFlowUpdate(long expectedEpoch,int[] rows,int count,")
                .append(table.name("Scan"))
                .append(".Updater value){if(value==null)throw new NullPointerException(\"updater\");ownership.preflightMutation(\"dataflow.update\");long current=state.structuralEpoch();if(expectedEpoch!=current)throw RuntimeFailures.staleIndexSnapshot(TABLE,expectedEpoch,current,\"dataflow.update\");state.beginOperation(\"dataflow.update\");long reached=0L;boolean selected=false;try{selected=true;prepareUpdateScratch(count);loadUpdateScratch(rows,count);")
                .append(table.name("Scan"))
                .append(".MutableCursor cursor=new ")
                .append(table.name("Scan"))
                .append(".MutableCursor(this);for(int i=0;i<count;i++){reached++;cursor.open(i,rows[i]);state.beginCallback(\"dataflow.update.updater\");try{value.update(cursor);}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(TABLE,\"dataflow.update\",\"updater\",callback);}finally{state.endCallback(\"dataflow.update.updater\");cursor.close();}}long changed=publishUpdate(rows,count);state.endOperationSuccess(\"dataflow.update\",count,count,changed);return state.updateResult(count,count,changed);}catch(SomaRuntimeException failure){state.endOperationFailure(\"dataflow.update\",count,reached,failure.code());throw failure;}catch(RuntimeException failure){state.abortOperation(\"dataflow.update\");throw failure;}catch(Error failure){state.abortOperation(\"dataflow.update\");throw failure;}finally{if(selected)clearUpdateScratch(count);}}\n")
                .append("  RemoveResult dataFlowRemove(long expectedEpoch,int[] rows,int count){ownership.preflightMutation(\"dataflow.remove\");long current=state.structuralEpoch();if(expectedEpoch!=current)throw RuntimeFailures.staleIndexSnapshot(TABLE,expectedEpoch,current,\"dataflow.remove\");state.beginOperation(\"dataflow.remove\");try{RemoveResult result=removeSelected(rows,count,count,\"dataflow.remove\");state.endOperationSuccess(\"dataflow.remove\",count,count,count);return result;}catch(SomaRuntimeException failure){state.endOperationFailure(\"dataflow.remove\",count,0L,failure.code());throw failure;}catch(RuntimeException failure){state.abortOperation(\"dataflow.remove\");throw failure;}catch(Error failure){state.abortOperation(\"dataflow.remove\");throw failure;}}\n")
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
        appendDataFlowAccessRuntime(out, table);
        appendTableFieldAccess(out, table);
        appendMutatorCommit(out, table);
        appendUpdateScratch(out, table);
        appendRemove(out, table);
        DenseExactIndexSourceEmitter.appendRuntime(out, table);
        appendOwnedLifecycle(out, table);
        return out.append("}\n").toString();
    }

    private static void appendDataFlowAccessRuntime(
            SourceBuilder out, TableSpec table) {
        String scan = table.name("Scan");
        out.append("  int dataFlowCurrentIndex(int index){return state.checkGuardedRowIndex(index,\"dataflow.point\");}\n")
                .append("  void dataFlowRequireCurrent(IndexSnapshot snapshot){if(snapshot==null)throw new NullPointerException(\"snapshot\");if(!IndexSnapshots.isOwnedBy(snapshot,indexSnapshotOwner))throw RuntimeFailures.indexSnapshotWrongTable(TABLE,\"dataflow.gather\");long current=state.structuralEpoch();if(snapshot.structuralEpoch()!=current)throw RuntimeFailures.staleIndexSnapshot(TABLE,snapshot.structuralEpoch(),current,\"dataflow.gather\");for(int i=0;i<snapshot.size();i++)state.checkGuardedRowIndex(snapshot.indexAt(i),\"dataflow.gather\");}\n")
                .append("  void dataFlowBorrow(int[] rows,int count,")
                .append(scan)
                .append(".Consumer consumer){if(consumer==null)throw new NullPointerException(\"consumer\");")
                .append(scan).append(".Cursor cursor=new ").append(scan)
                .append(".Cursor(this);for(int i=0;i<count;i++){cursor.open(rows[i]);state.beginCallback(\"dataflow.borrow.consumer\");try{consumer.accept(cursor);}catch(SomaRuntimeException failure){throw failure;}catch(RuntimeException callback){throw RuntimeFailures.callbackFailed(TABLE,\"dataflow.borrow\",\"consumer\",callback);}finally{state.endCallback(\"dataflow.borrow.consumer\");cursor.close();}}}\n")
                .append("  List<").append(table.carrierType)
                .append("> dataFlowMaterializeRows(int[] rows,int count,MaterializationBudget budget){if(budget==null)throw new NullPointerException(\"budget\");String operation=\"dataflow.materialize\";ownership.beginDataFlowMaterialization(operation);MaterializationTracker tracker=null;try{tracker=new MaterializationTracker(budget,TABLE);tracker.enterOwnership(this,TABLE);tracker.checkOwnershipDepth(0,TABLE);tracker.addTableInstances(1L,TABLE);tracker.addRows(count,TABLE);tracker.addListAllocation(count,TABLE);for(int i=0;i<count;i++)accountRowRecursive(tracker,rows[i],0,TABLE);tracker.exitOwnership(this,TABLE);MaterializationAllocation.preflight(operation,tracker.estimatedBytes(),TABLE);List<")
                .append(table.carrierType).append("> result=new ArrayList<")
                .append(table.carrierType)
                .append(">(count);for(int i=0;i<count;i++)result.add(carrierRecursive(rows[i],0,TABLE));return result;}finally{ownership.endDataFlowMaterialization(operation);}}\n");
        if (!table.keyed()) {
            return;
        }
        FieldSpec key = table.keyField();
        out.append("  int dataFlowKeyIndex(")
                .append(key.primitive)
                .append(" key){return ");
        if (key.compositeKey()) {
            out.append("compositeLookup(key,\"dataflow.point\")");
        } else {
            out.append("keySpace.rowOf(")
                    .append(key.keySpaceValue("key", "dataflow.point"))
                    .append(')');
        }
        out.append(";}\n");
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

    private static void appendDeltaApply(
            SourceBuilder out, TableSpec table) {
        String delta = table.name("Delta");
        String batch = table.name("Batch");
        FieldSpec key = table.keyField();
        out.append("  public DeltaApplyResult applyDelta(").append(delta)
                .append(" delta){if(delta==null)throw new NullPointerException(\"delta\");")
                .append(delta).append(" detached=delta.copy();ownership.preflightMutation(\"delta.apply\");")
                .append("long beforeEpoch=state.structuralEpoch();if(detached.hasExpectedStructuralEpoch()&&detached.expectedStructuralEpoch()!=beforeEpoch)throw RuntimeFailures.staleDelta(TABLE,detached.expectedStructuralEpoch(),beforeEpoch,\"delta.apply\");")
                .append("for(int right=1;right<detached.size();right++)for(int left=0;left<right;left++)if(detached.sameKey(left,right))throw RuntimeFailures.duplicateDeltaTarget(TABLE,left,right,\"delta.apply\");")
                .append("int beforeSize=state.size();if(detached.size()==0)return DeltaApplyResult.committed(0L,0L,0L,beforeSize,beforeSize,beforeEpoch,beforeEpoch);")
                .append(batch).append(" working=").append(batch)
                .append(".snapshotOf(this);long inserted=0L,updated=0L,deleted=0L;")
                .append("for(int entry=0;entry<detached.size();entry++){int row=deltaRow(working,detached.keyAt(entry));byte kind=detached.kindAt(entry);")
                .append("if(kind==").append(delta)
                .append(".INSERT){if(row>=0)throw RuntimeFailures.deltaInsertTargetPresent(TABLE,entry,\"delta.apply\");working.appendFrom(detached.rows(),detached.valueIndexAt(entry));inserted++;}")
                .append("else if(kind==").append(delta)
                .append(".UPDATE){if(row<0)throw RuntimeFailures.deltaTargetAbsent(TABLE,entry,\"delta.apply\");working.replaceFrom(row,detached.rows(),detached.valueIndexAt(entry));updated++;}")
                .append("else if(kind==").append(delta)
                .append(".DELETE){if(row<0)throw RuntimeFailures.deltaTargetAbsent(TABLE,entry,\"delta.apply\");working.swapRemove(row);deleted++;}")
                .append("else throw RuntimeFailures.internalInvariant(\"delta_operation\",TABLE,\"delta.apply\");}")
                .append("long publishEpoch=state.structuralEpoch();if(publishEpoch!=beforeEpoch)throw RuntimeFailures.staleDelta(TABLE,beforeEpoch,publishEpoch,\"delta.apply\");")
                .append("replaceAll(working);return DeltaApplyResult.committed(inserted,updated,deleted,beforeSize,state.size(),beforeEpoch,state.structuralEpoch());}\n")
                .append("  private int deltaRow(").append(batch).append(" rows,")
                .append(key.primitive)
                .append(" key){for(int row=0;row<rows.size();row++)if(rows.keyMatches(row,key))return row;return -1;}\n");
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
        out.append("  boolean dataFlow").append(c)
                .append("PresentAt(int index){if(index<0||index>=state.size())throw RuntimeFailures.internalInvariant(\"dataflow_parent_index\",TABLE,\"dataflow.snapshot\");return ")
                .append(child.optional
                        ? child.javaName + "ChildPresence.isPresent(index)"
                        : "true")
                .append(";}\n");
        out.append("  ").append(child.tableType()).append(" dataFlow")
                .append(c).append("At(int index){if(index<0||index>=state.size())throw RuntimeFailures.internalInvariant(\"dataflow_parent_index\",TABLE,\"dataflow.expand\");");
        if (child.optional) {
            out.append("if(!").append(child.javaName)
                    .append("ChildPresence.isPresent(index))return null;");
        }
        out.append("long handle=").append(child.javaName)
                .append("HandleColumn.get(index);if(handle==0L)return null;long owner=ownerTokenColumn.get(index);return(")
                .append(child.tableType()).append(")ownership.resolve(handle,owner,")
                .append(q(child.logicalName))
                .append(",\"dataflow.expand\");}\n");
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

}
