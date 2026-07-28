package io.github.somaruntime.soma.processor;

import java.util.List;

import static io.github.somaruntime.soma.processor.DenseSelectorCodegenModel.*;
import static io.github.somaruntime.soma.processor.DenseSourceNames.*;
import static io.github.somaruntime.soma.processor.DenseTableCodegenModel.*;

/** Shared selector binding, validation and exact-index source support. */
final class DenseSelectorSourceSupport {
    private DenseSelectorSourceSupport() {
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
                    .append("  private final class ExactIndexStage{ExactIndexStage(){}long retainedBytes(){return 0L;}void publish(String operation){}void discard(String operation){}}\n");
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
        out.append("if(stage.retainedBytes()>scratch)throw internalInvariant(\"exact_index_estimator\",TABLE,operation);return stage;}catch(RuntimeException failure){stage.discard(operation);throw failure;}catch(Error failure){stage.discard(operation);throw failure;}}\n")
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
        out.append("return value;}void publish(String operation){if(consumed)throw internalInvariant(\"exact_index_stage_consumed\",TABLE,operation);long previous=exactIndexRetainedBytes(),proposed=retainedBytes();state.preflightExactIndexStorage(proposed,operation);state.releaseBulkScratch(scratchBytes,operation);scratchBytes=0L;state.commitExactIndexStorage(previous,proposed,operation);");
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

    static SelectorLeafSpec selectorLeaf(TableSpec table, String path) {
        for (SelectorSpec selector : table.selectors) {
            for (SelectorLeafSpec leaf : selector.leaves) {
                if (leaf.path.equals(path)) return leaf;
            }
        }
        return null;
    }

    static String canonicalAccessStorage(
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

    static void appendSourceLeafArguments(SourceBuilder out, int leafCount) {
        for (int leafIndex = 0; leafIndex < leafCount; leafIndex++) {
            out.append(",sourceLeaf").append(leafIndex);
        }
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

    static String selectorParameterArgument(
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

    static String compareExpression(String type, String left, String right) {
        if ("boolean".equals(type)) {
            return left + "==" + right + "?0:(" + left + "?1:-1)";
        }
        if ("java.lang.String".equals(type)) return left + ".compareTo(" + right + ')';
        if ("float".equals(type)) return "Float.compare(" + left + ',' + right + ')';
        if ("double".equals(type)) return "Double.compare(" + left + ',' + right + ')';
        return left + "<" + right + "?-1:" + left + ">" + right + "?1:0";
    }

    static String flattenedValueDifferent(
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

    static String flattenedScratchDifferent(
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

    static String accessAwareDifferent(
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

    static String scalarValueDifferent(
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

}
