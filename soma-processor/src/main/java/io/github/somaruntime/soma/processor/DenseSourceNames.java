package io.github.somaruntime.soma.processor;

import static io.github.somaruntime.soma.processor.DenseTableCodegenModel.FieldSpec;
import static io.github.somaruntime.soma.processor.DenseTableCodegenModel.TableSpec;
import static io.github.somaruntime.soma.processor.DenseTableCodegenModel.ValueLeafSpec;

/** Shared deterministic Java-source naming and quoting helpers. */
final class DenseSourceNames {
    private DenseSourceNames() {
    }

    static String cap(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /** Internal update buffers use normalized numeric slots, never schema-derived identifiers. */
    static String updateScratch(int fieldIndex) {
        return "updateField" + fieldIndex;
    }

    static String updateScratch(int fieldIndex, int leafIndex) {
        return "updateField" + fieldIndex + "Leaf" + leafIndex;
    }

    static String updatePresenceScratch(int fieldIndex) {
        return "updateField" + fieldIndex + "Present";
    }

    static String updateValueMethod(int fieldIndex) {
        return "updateField" + fieldIndex + "Value";
    }

    static String setUpdateMethod(int fieldIndex) {
        return "setUpdateField" + fieldIndex;
    }

    static String updatePresentMethod(int fieldIndex) {
        return "updateField" + fieldIndex + "IsPresent";
    }

    static String clearUpdateMethod(int fieldIndex) {
        return "clearUpdateField" + fieldIndex;
    }

    static int fieldIndex(TableSpec table, FieldSpec field) {
        for (int index = 0; index < table.fields.size(); index++) {
            if (table.fields.get(index) == field) return index;
        }
        throw new IllegalStateException("field is not owned by table: " + field.javaName);
    }

    static int leafIndex(FieldSpec field, ValueLeafSpec leaf) {
        for (int index = 0; index < field.valueLeaves.size(); index++) {
            if (field.valueLeaves.get(index) == leaf) return index;
        }
        throw new IllegalStateException("leaf is not owned by field: " + leaf.javaName);
    }

    static String q(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') out.append('\\');
            out.append(c);
        }
        return out.append('"').toString();
    }

}
