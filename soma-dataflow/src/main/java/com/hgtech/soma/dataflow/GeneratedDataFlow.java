package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.dataflow.generated.CandidateIndexAccess;
import com.hgtech.soma.dataflow.generated.CandidateEffectAccess;
import com.hgtech.soma.dataflow.generated.OwnedChildAccess;
import com.hgtech.soma.runtime.RemoveResult;
import com.hgtech.soma.runtime.UpdateResult;

/**
 * Narrow construction bridge used by generated schema companions.
 *
 * <p>Application code receives generated typed sources and does not implement this
 * protocol directly.</p>
 */
public final class GeneratedDataFlow {
    public static final String TRANSFORMATION_PROTOCOL = "soma-transformation-v1";
    public static final String KERNEL_PROTOCOL = "soma-kernel-v1";

    private GeneratedDataFlow() {
    }

    public static <B extends DataFlowBinding> CandidateFlow<B> candidates(
            SourceSlot<B> source) {
        return new CandidateFlow<B>(source);
    }

    public static <B extends DataFlowBinding> CandidateFlow<B> exactCandidates(
            SourceSlot<B> source, CandidateIndexAccess<B> access) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (access == null) {
            throw new NullPointerException("access");
        }
        return new CandidateFlow<B>(
                new CandidatePlan<B>(
                        new ExactCandidateInput<B>(source, access)));
    }

    public static <
            P extends DataFlowBinding,
            C extends DataFlowBinding>
    ExpandedFlow<P, C> ownedChildren(
            SourceSlot<P> parentSource,
            CandidateFlow<P> parents,
            SourceSlot<C> childSource,
            OwnedChildAccess<P, C> access) {
        if (parentSource == null) {
            throw new NullPointerException("parentSource");
        }
        if (parents == null) {
            throw new NullPointerException("parents");
        }
        if (childSource == null) {
            throw new NullPointerException("childSource");
        }
        if (access == null) {
            throw new NullPointerException("access");
        }
        CandidateProgram<P> parentProgram = parents.compileProgram();
        if (parentProgram.source() != parentSource) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_owned_parent_source_mismatch",
                    parentSource.alias(),
                    "dataflow.expand");
        }
        return new ExpandedFlow<P, C>(
                parentProgram, childSource, access);
    }

    public static <B extends DataFlowBinding>
    DataFlowDefinition<UpdateResult> update(
            SourceSlot<B> source,
            CandidateFlow<B> candidates,
            CandidateEffectAccess<B> access,
            Object updater) {
        CandidateProgram<B> program =
                requireCandidateSource(source, candidates, "update");
        return DataFlowDefinition.of(
                new CandidateUpdateOperation<B>(
                        program, required(access, "access"), updater));
    }

    public static <B extends DataFlowBinding>
    DataFlowDefinition<RemoveResult> remove(
            SourceSlot<B> source,
            CandidateFlow<B> candidates,
            CandidateEffectAccess<B> access) {
        CandidateProgram<B> program =
                requireCandidateSource(source, candidates, "remove");
        return DataFlowDefinition.of(
                new CandidateRemoveOperation<B>(
                        program, required(access, "access")));
    }

    private static <B extends DataFlowBinding>
    CandidateProgram<B> requireCandidateSource(
            SourceSlot<B> source,
            CandidateFlow<B> candidates,
            String operation) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (candidates == null) {
            throw new NullPointerException("candidates");
        }
        CandidateProgram<B> program = candidates.compileProgram();
        if (program.source() != source) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_effect_source_mismatch",
                    source.alias(),
                    "dataflow." + operation);
        }
        return program;
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }
        return value;
    }

    public static <B extends DataFlowBinding> LongExpression<B> requiredLong(
            SourceSlot<B> source, int columnOrdinal, String path) {
        return new LongExpression<B>(
                source,
                ExpressionNodes.longField(columnOrdinal, path),
                ExpressionNodes.alwaysPresent(),
                path);
    }

    public static <B extends DataFlowBinding> LongExpression<B> optionalLong(
            SourceSlot<B> source, int columnOrdinal, String path) {
        return new LongExpression<B>(
                source,
                ExpressionNodes.longField(columnOrdinal, path),
                ExpressionNodes.present(columnOrdinal, path),
                path);
    }

    public static <B extends DataFlowBinding> DoubleExpression<B> requiredDouble(
            SourceSlot<B> source, int columnOrdinal, String path) {
        return new DoubleExpression<B>(
                source,
                ExpressionNodes.doubleField(columnOrdinal, path),
                ExpressionNodes.alwaysPresent(),
                path);
    }

    public static <B extends DataFlowBinding> DoubleExpression<B> optionalDouble(
            SourceSlot<B> source, int columnOrdinal, String path) {
        return new DoubleExpression<B>(
                source,
                ExpressionNodes.doubleField(columnOrdinal, path),
                ExpressionNodes.present(columnOrdinal, path),
                path);
    }

    public static <B extends DataFlowBinding> BooleanExpression<B> requiredBoolean(
            SourceSlot<B> source, int columnOrdinal, String path) {
        return new BooleanExpression<B>(
                source,
                ExpressionNodes.booleanField(columnOrdinal, path),
                ExpressionNodes.alwaysPresent(),
                path);
    }

    public static <B extends DataFlowBinding> BooleanExpression<B> optionalBoolean(
            SourceSlot<B> source, int columnOrdinal, String path) {
        final BooleanNode value =
                ExpressionNodes.booleanField(columnOrdinal, path);
        final BooleanNode presence =
                ExpressionNodes.present(columnOrdinal, path);
        return new BooleanExpression<B>(
                source,
                new BooleanNode() {
                    @Override
                    public boolean evaluate(DataFlowBinding binding, int index) {
                        ExpressionNodes.requirePresent(
                                presence, binding, index, path);
                        return value.evaluate(binding, index);
                    }

                    @Override
                    public String canonical() {
                        return "optional-boolean(" + value.canonical() + ")";
                    }
                },
                presence,
                path);
    }

    public static <B extends DataFlowBinding, T> ObjectExpression<B, T>
    requiredObject(SourceSlot<B> source, int columnOrdinal, String path) {
        return new ObjectExpression<B, T>(
                source,
                ExpressionNodes.objectField(columnOrdinal, path),
                ExpressionNodes.alwaysPresent(),
                path);
    }

    public static <B extends DataFlowBinding, T> ObjectExpression<B, T>
    optionalObject(SourceSlot<B> source, int columnOrdinal, String path) {
        return new ObjectExpression<B, T>(
                source,
                ExpressionNodes.objectField(columnOrdinal, path),
                ExpressionNodes.present(columnOrdinal, path),
                path);
    }
}
