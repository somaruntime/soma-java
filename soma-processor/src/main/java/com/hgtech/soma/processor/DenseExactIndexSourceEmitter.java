package com.hgtech.soma.processor;

import java.util.List;

import static com.hgtech.soma.processor.DenseSelectorSourceSupport.SelectorBinding;
import static com.hgtech.soma.processor.DenseTableCodegenModel.SelectorLeafSpec;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.SelectorParameter;
import static com.hgtech.soma.processor.DenseTableCodegenModel.SelectorSpec;
import static com.hgtech.soma.processor.DenseTableCodegenModel.TableSpec;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.appendExactIndexReplacementRuntime;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.appendNoMutableSelectorChange;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.appendSelectorArguments;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.appendSelectorBatchEquality;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.appendSelectorChangeRuntime;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.appendSelectorComparison;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.appendSelectorHash;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.appendSelectorParameters;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.appendSelectorRowBatchEquality;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.appendSelectorValueArguments;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.appendUniqueValidationRuntime;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.hasMutableSelectors;
import static com.hgtech.soma.processor.DenseSourceNames.q;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.selectorBinding;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.selectorCanChange;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.selectorHashBits;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.selectorMethodName;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.selectorMutatorValueOrLive;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.selectorParameterLeafCount;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.selectorParameters;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.selectorStorageValue;

/** Exact-index runtime source的byte-stable专用emitter。 */
final class DenseExactIndexSourceEmitter {
    private DenseExactIndexSourceEmitter() {
    }

    static void appendRuntime(SourceBuilder out, TableSpec table) {
        boolean mutableSelectors = hasMutableSelectors(table);
        out.append("  private static long addExactMetric(long left,long right){return left<0L||right<0L||Long.MAX_VALUE-left<right?Long.MAX_VALUE:left+right;}\n")
                .append("  private long exactIndexRetainedBytes(){long value=0L;");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("value=addExactMetric(value,selector").append(i)
                    .append("Index.retainedBytes());");
        }
        out.append("return value;}\n  private long exactIndexRetainedBytesAfterEnsure(int requiredRows,int additionalGroups){long value=0L;");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("value=addExactMetric(value,selector").append(i)
                    .append("Index.retainedBytesAfterEnsure(requiredRows,additionalGroups));");
        }
        out.append("return value;}\n  private long exactIndexStorageHighWaterBytes(){long value=0L;");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("value=addExactMetric(value,selector").append(i)
                    .append("Index.storageHighWaterBytes());");
        }
        out.append("return value;}\n  private TableStats exactIndexStats(TableStats base){long entries=0L,groups=0L,probes=0L,collisions=0L,rehashes=0L;");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("entries=addExactMetric(entries,selector").append(i)
                    .append("Index.entryCount());groups=addExactMetric(groups,selector")
                    .append(i).append("Index.groupCount());probes=addExactMetric(probes,selector")
                    .append(i).append("Index.probeCount());collisions=addExactMetric(collisions,selector")
                    .append(i).append("Index.collisionCount());rehashes=addExactMetric(rehashes,selector")
                    .append(i).append("Index.rehashCount());");
        }
        out.append("return TableStats.withExactIndexes(base,")
                .append(table.selectors.size())
                .append(",entries,groups,probes,collisions,rehashes,exactIndexRetainedBytes(),exactIndexStorageHighWaterBytes());}\n")
                .append("  private void resetExactIndexMetrics(){");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("selector").append(i).append("Index.resetMetrics();");
        }
        out.append("}\n  private void clearExactIndexes(){");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("selector").append(i).append("Index.clear();");
        }
        out.append("}\n  private void releaseExactIndexes(String operation){long previous=exactIndexRetainedBytes();");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("selector").append(i).append("Index.release();");
        }
        out.append("state.commitExactIndexStorage(previous,0L,operation);}\n")
                .append("  private void ensureExactIndexCapacity(int requiredRows,int additionalGroups,String operation){ensureExactIndexCapacities(requiredRows,operation");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append(",additionalGroups");
        }
        out.append(");}\n  private void ensureExactIndexCapacities(int requiredRows,String operation");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append(",int additional").append(i);
        }
        out.append("){long previous=exactIndexRetainedBytes(),proposed=0L;");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("proposed=addExactMetric(proposed,selector").append(i)
                    .append("Index.retainedBytesAfterEnsure(requiredRows,additional")
                    .append(i).append("));");
        }
        out.append("state.preflightExactIndexStorage(proposed,operation);try{");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("selector").append(i)
                    .append("Index.ensureCapacity(requiredRows,additional")
                    .append(i).append(");");
        }
        out.append("}finally{long actual=exactIndexRetainedBytes();if(actual!=previous)state.commitExactIndexStorage(previous,actual,operation);}}\n")
                .append("  private void ensureExactIndexAppendCapacity(int requiredRows,")
                .append(table.name("Batch"))
                .append(" batch,String operation){long scratch=ExactGroupCounter.estimatedRetainedBytes(batch.size());");
        out.append("state.reserveBulkScratch(scratch,operation);try{");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("long groups").append(i).append("=selector")
                    .append(i).append("BatchGroups(batch,true);");
        }
        out.append("ensureExactIndexCapacities(requiredRows,operation");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append(",(int)groups").append(i);
        }
        out.append(");}finally{state.releaseBulkScratch(scratch,operation);}}\n")
                .append("  private void linkExactIndexRows(int from,int count){for(int row=from;row<from+count;row++){");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("linkSelector").append(i).append("Row(row);");
        }
        out.append("}}\n  private void unlinkExactIndexRows(int[] rows,int count){for(int position=0;position<count;position++){int row=rows[position];");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("selector").append(i).append("Index.unlink(row);");
        }
        out.append("}}\n  private void relocateExactIndexRows(int from,int to){");
        for (int i = 0; i < table.selectors.size(); i++) {
            out.append("selector").append(i).append("Index.relocate(from,to);");
        }
        out.append("}\n  private void prepareMutatorExactIndexes(int target,")
                .append(table.name("Mutator")).append(" mutation){");
        if (mutableSelectors) {
            for (int i = 0; i < table.selectors.size(); i++) {
                if (!selectorCanChange(table, table.selectors.get(i))) continue;
                out.append("boolean changed").append(i).append("=selector")
                        .append(i).append("ChangedByMutator(target,mutation);");
            }
            out.append("if(");
            appendNoMutableSelectorChange(out, table, "changed");
            out.append(")return;ensureExactIndexCapacity(size(),1,\"mutator.commit\");");
            for (int i = 0; i < table.selectors.size(); i++) {
                if (!selectorCanChange(table, table.selectors.get(i))) continue;
                out.append("if(changed").append(i).append(")selector")
                        .append(i).append("Index.unlink(target);");
            }
        }
        out.append("}\n  private void publishMutatorExactIndexes(int target,")
                .append(table.name("Mutator")).append(" mutation){");
        for (int i = 0; i < table.selectors.size(); i++) {
            if (!selectorCanChange(table, table.selectors.get(i))) continue;
            out.append("if(!selector").append(i)
                    .append("Index.isLinked(target))linkSelector")
                    .append(i).append("Row(target);");
        }
        out.append("}\n  private void prepareUpdateExactIndexes(int[] rows,int count){");
        if (mutableSelectors) {
            for (int i = 0; i < table.selectors.size(); i++) {
                if (!selectorCanChange(table, table.selectors.get(i))) continue;
                out.append("boolean changed").append(i).append("=selector")
                        .append(i).append("ChangedByUpdate(rows,count);");
            }
            out.append("if(");
            appendNoMutableSelectorChange(out, table, "changed");
            out.append(")return;ensureExactIndexCapacity(size(),count,\"scan.update\");for(int index=0;index<count;index++){int target=rows[index];");
            for (int i = 0; i < table.selectors.size(); i++) {
                if (!selectorCanChange(table, table.selectors.get(i))) continue;
                out.append("if(changed").append(i).append("&&selector")
                        .append(i).append("ChangedByUpdateRow(target,index))selector")
                        .append(i).append("Index.unlink(target);");
            }
            out.append("}");
        }
        out.append("}\n  private void publishUpdateExactIndexes(int[] rows,int count){");
        if (mutableSelectors) {
            out.append("for(int index=0;index<count;index++){int target=rows[index];");
            for (int i = 0; i < table.selectors.size(); i++) {
                if (!selectorCanChange(table, table.selectors.get(i))) continue;
                out.append("if(!selector").append(i)
                        .append("Index.isLinked(target))linkSelector")
                        .append(i).append("Row(target);");
            }
            out.append("}");
        }
        out.append("}\n");

        for (int i = 0; i < table.selectors.size(); i++) {
            appendSelectorChangeRuntime(out, table, table.selectors.get(i), i);
        }

        appendUniqueValidationRuntime(out, table);

        for (int i = 0; i < table.selectors.size(); i++) {
            SelectorSpec selector = table.selectors.get(i);
            List<SelectorParameter> parameters = selectorParameters(table, selector);
            String method = selectorMethodName(selector);
            out.append("  private long selector").append(i)
                    .append("HashRow(int row){long hash=1469598103934665603L;");
            appendSelectorHash(out, table, selector, "row", null, null, method);
            out.append("return hash;}\n  private long selector").append(i)
                    .append("HashBatch(").append(table.name("Batch"))
                    .append(" batch,int row){long hash=1469598103934665603L;");
            appendSelectorHash(out, table, selector, "row", "batch", null, "replaceAll");
            out.append("return hash;}\n");
            if ("unique".equals(selector.kind)) {
                out.append("  private long selector").append(i)
                        .append("HashMutator(int target,")
                        .append(table.name("Mutator"))
                        .append(" mutation){long hash=1469598103934665603L;");
                for (SelectorLeafSpec leaf : selector.leaves) {
                    SelectorBinding binding = selectorBinding(table, leaf);
                    String value = binding.field.key
                            ? selectorStorageValue(
                                    leaf, binding.columnExpression("target"),
                                    "mutator.commit")
                            : selectorMutatorValueOrLive(
                                    binding, leaf, "mutation", "target",
                                    "mutator.commit");
                    out.append("hash=(hash^")
                            .append(selectorHashBits(leaf.storageType, value))
                            .append(")*1099511628211L;");
                }
                out.append("return hash;}\n");
            }
            out.append("  private long selector").append(i).append("HashValues(");
            appendSelectorParameters(out, parameters);
            if (!parameters.isEmpty()) out.append(',');
            out.append("String operation");
            out.append("){long hash=1469598103934665603L;");
            appendSelectorHash(out, table, selector, null, null, parameters, "$operation");
            out.append("return hash;}\n  private int compareSelector").append(i)
                    .append("LeafValues(int left,int right){");
            appendSelectorComparison(out, table, selector, "left", "right", selector.leaves.size(), true, method);
            out.append("return 0;}\n  private int compareSelector").append(i)
                    .append("ToValues(int row,");
            appendSelectorParameters(out, parameters);
            if (!parameters.isEmpty()) out.append(',');
            out.append("String operation");
            out.append("){");
            appendSelectorComparison(out, table, selector, "row", null,
                    selectorParameterLeafCount(selector), false, "$operation", parameters);
            out.append("return 0;}\n  private boolean selector").append(i)
                    .append("BatchEqual(").append(table.name("Batch"))
                    .append(" batch,int left,int right){return ");
            appendSelectorBatchEquality(out, table, selector, "batch", "left", "right", "replaceAll");
            out.append(";}\n  private boolean selector").append(i)
                    .append("RowBatchEqual(int left,").append(table.name("Batch"))
                    .append(" batch,int right){return ");
            appendSelectorRowBatchEquality(
                    out, table, selector, "left", "batch", "right", "addBatch");
            out.append(";}\n  private int selector").append(i).append("Group(");
            appendSelectorParameters(out, parameters);
            if (!parameters.isEmpty()) out.append(',');
            out.append("String operation");
            out.append("){long hash=selector").append(i).append("HashValues(");
            appendSelectorValueArguments(out, parameters.size());
            if (!parameters.isEmpty()) out.append(',');
            out.append("operation");
            out.append(");int group=selector").append(i).append("Index.firstGroup(hash);while(group>=0&&compareSelector")
                    .append(i).append("ToValues(selector").append(i)
                    .append("Index.representativeRow(group)");
            appendSelectorArguments(out, parameters.size());
            out.append(",operation");
            out.append(")!=0){selector").append(i)
                    .append("Index.recordCollision();group=selector").append(i)
                    .append("Index.nextHashGroup(group);}return group;}\n  private void linkSelector")
                    .append(i).append("Row(int row){long hash=selector").append(i)
                    .append("HashRow(row);int group=selector").append(i)
                    .append("Index.firstGroup(hash);while(group>=0&&compareSelector")
                    .append(i).append("LeafValues(selector").append(i)
                    .append("Index.representativeRow(group),row)!=0){selector")
                    .append(i).append("Index.recordCollision();group=selector")
                    .append(i).append("Index.nextHashGroup(group);}if(group<0)group=selector")
                    .append(i).append("Index.createGroup(hash);");
            if ("unique".equals(selector.kind)) {
                out.append("else if(selector").append(i)
                        .append("Index.groupSize(group)!=0)throw RuntimeFailures.internalInvariant(\"unique_exact_index_conflict\",TABLE,\"exact-index.link\");");
            }
            out.append("selector").append(i).append("Index.link(group,row);}\n")
                    .append("  private long selector").append(i)
                    .append("BatchGroups(").append(table.name("Batch"))
                    .append(" batch,boolean compareLive){ExactGroupCounter staged=new ExactGroupCounter(batch.size());int additional=0;try{for(int row=0;row<batch.size();row++){long hash=selector")
                    .append(i).append("HashBatch(batch,row);int group=staged.firstGroup(hash);while(group>=0&&!selector")
                    .append(i).append("BatchEqual(batch,staged.representativeRow(group),row)){group=staged.nextHashGroup(group);}if(group<0){staged.createGroup(hash,row);if(compareLive){int live=selector")
                    .append(i).append("Index.firstGroup(hash);while(live>=0&&!selector")
                    .append(i).append("RowBatchEqual(selector").append(i)
                    .append("Index.representativeRow(live),batch,row)){selector")
                    .append(i).append("Index.recordCollision();live=selector")
                    .append(i).append("Index.nextHashGroup(live);}if(live<0)additional++;}}}return((long)staged.groupCount()<<32)|((long)additional&4294967295L);}finally{staged.release();}}\n")
                    .append("  private GroupedExactIndex buildSelector").append(i)
                    .append("Index(").append(table.name("Batch"))
                    .append(" batch,String operation){int distinct=(int)(selector").append(i)
                    .append("BatchGroups(batch,false)>>>32);GroupedExactIndex staged=new GroupedExactIndex(batch.size(),distinct);for(int row=0;row<batch.size();row++){long hash=selector")
                    .append(i).append("HashBatch(batch,row);int group=staged.firstGroup(hash);while(group>=0&&!selector")
                    .append(i).append("BatchEqual(batch,staged.representativeRow(group),row)){staged.recordCollision();group=staged.nextHashGroup(group);}");
            if ("unique".equals(selector.kind)) {
                out.append("if(group>=0)throw RuntimeFailures.uniqueConstraintViolation(TABLE,")
                        .append(q(selector.name))
                        .append(",staged.representativeRow(group),row,operation);");
            }
            out.append("if(group<0)group=staged.createGroup(hash);staged.link(group,row);}staged.inheritMetrics(selector")
                    .append(i).append("Index.probeCount(),selector").append(i)
                    .append("Index.collisionCount(),selector").append(i)
                    .append("Index.rehashCount(),selector").append(i)
                    .append("Index.storageHighWaterBytes());return staged;}\n");
        }

        appendExactIndexReplacementRuntime(out, table);
    }
}
