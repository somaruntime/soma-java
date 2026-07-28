package io.github.somaruntime.soma.processor;

import static io.github.somaruntime.soma.processor.DenseTableCodegenModel.FieldSpec;
import static io.github.somaruntime.soma.processor.DenseTableCodegenModel.ValueLeafSpec;
import static io.github.somaruntime.soma.processor.DenseSourceNames.updateScratch;

/** Shared value-object construction expressions used by dense emitters. */
final class DenseValueSourceSupport {
    private DenseValueSourceSupport() {
    }

    static String compositeValueExpression(
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

    static String compositeUpdateValueExpression(
            int fieldIndex, FieldSpec field, String row) {
        String result = field.valueConstructionTemplate;
        for (int i = 0; i < field.valueLeaves.size(); i++) {
            ValueLeafSpec leaf = field.valueLeaves.get(i);
            String storage = updateScratch(fieldIndex, i) + "[" + row + "]";
            result = result.replace("@{" + i + "}@", leaf.publicValue(field, storage));
        }
        return result;
    }

}
