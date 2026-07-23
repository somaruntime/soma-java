package com.hgtech.soma.processor;

import java.util.ArrayList;
import java.util.List;

import static com.hgtech.soma.processor.DenseTableCodegenModel.FieldSpec;
import static com.hgtech.soma.processor.DenseTableCodegenModel.SelectorLeafSpec;
import static com.hgtech.soma.processor.DenseTableCodegenModel.SelectorSpec;
import static com.hgtech.soma.processor.DenseTableCodegenModel.TableSpec;
import static com.hgtech.soma.processor.DenseTableCodegenModel.ValueGroupSpec;
import static com.hgtech.soma.processor.DenseTableCodegenModel.ValueLeafSpec;

/** Canonical selector parameter grouping shared by admission and source emission. */
final class DenseSelectorCodegenModel {
    private DenseSelectorCodegenModel() {
    }

    static int selectorParameterLeafCount(SelectorSpec selector) {
        return selector.leaves.size();
    }

    static List<SelectorParameter> selectorParameters(
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

    static List<String> selectorPublicParameterTypes(
            TableSpec table, SelectorSpec selector) {
        List<String> result = new ArrayList<String>();
        for (SelectorParameter parameter : selectorParameters(table, selector)) {
            result.add(parameter.publicType);
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

    static final class SelectorParameter {
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

    static final class SelectorGroupBinding {
        final FieldSpec field;
        final ValueGroupSpec group;

        SelectorGroupBinding(FieldSpec field, ValueGroupSpec group) {
            this.field = field;
            this.group = group;
        }
    }
}
