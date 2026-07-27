package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.dataflow.generated.CandidateIndexAccess;
import com.hgtech.soma.dataflow.generated.CandidateEffectAccess;
import com.hgtech.soma.dataflow.generated.OwnedChildAccess;
import com.hgtech.soma.dataflow.generated.PointIndexAccess;
import com.hgtech.soma.dataflow.generated.SnapshotGatherAccess;
import com.hgtech.soma.dataflow.generated.CandidateBorrowAccess;
import com.hgtech.soma.dataflow.generated.CandidateMaterializationAccess;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.MaterializationBudget;
import com.hgtech.soma.runtime.RemoveResult;
import com.hgtech.soma.runtime.UpdateResult;

import java.util.Collections;
import java.util.List;

/**
 * Narrow construction bridge used by generated schema companions.
 *
 * <p>Application code receives generated typed sources and does not implement this
 * protocol directly.</p>
 */
public final class GeneratedDataFlow {
    public static final String TRANSFORMATION_PROTOCOL = "soma-transformation-v2";
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

    public static <B extends DataFlowBinding, T> PointFlow<B, T> point(
            SourceSlot<B> source,
            PointIndexAccess<B> access,
            CandidateMaterializationAccess<B, T> materialization) {
        required(source, "source");
        required(access, "access");
        required(materialization, "materialization");
        return new PointFlow<B, T>(
                new CandidateFlow<B>(
                        new CandidatePlan<B>(
                                new PointCandidateInput<B>(source, access))),
                materialization);
    }

    public static <B extends DataFlowBinding> CandidateFlow<B> gather(
            SourceSlot<B> source,
            ParameterSlot<IndexSnapshot> snapshot,
            SnapshotGatherAccess<B> access) {
        required(source, "source");
        required(snapshot, "snapshot");
        required(access, "access");
        return new CandidateFlow<B>(
                new CandidatePlan<B>(
                        new SnapshotCandidateInput<B>(
                                source, snapshot, access)));
    }

    public static <B extends DataFlowBinding>
    DataFlowDefinition<LongScalarResult> borrow(
            SourceSlot<B> source,
            CandidateFlow<B> candidates,
            CandidateBorrowAccess<B> access,
            Object consumer) {
        CandidateProgram<B> program =
                requireCandidateSource(source, candidates, "borrow");
        return DataFlowDefinition.of(
                new CandidateBorrowOperation<B>(
                        program,
                        required(access, "access"),
                        required(consumer, "consumer")));
    }

    public static <B extends DataFlowBinding, T>
    DataFlowDefinition<List<T>> materialize(
            SourceSlot<B> source,
            CandidateFlow<B> candidates,
            CandidateMaterializationAccess<B, T> access,
            MaterializationBudget budget) {
        CandidateProgram<B> program =
                requireCandidateSource(source, candidates, "materialize");
        return DataFlowDefinition.of(
                new CandidateMaterializeOperation<B, T>(
                        program,
                        required(access, "access"),
                        required(budget, "budget"),
                        "Candidate"));
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

    public static <B extends DataFlowBinding> LongExpression<B> longParameter(
            SourceSlot<B> source, final ParameterSlot<Long> parameter) {
        required(source, "source");
        required(parameter, "parameter");
        return new LongExpression<B>(
                source,
                new LongNode() {
                    @Override
                    public long evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        return frame.parameter(parameter).longValue();
                    }

                    @Override
                    public String canonical() {
                        return "long-parameter(" + parameter.canonical() + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                "parameter." + parameter.name(),
                Collections.<ParameterSlot<?>>singletonList(parameter),
                true);
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

    public static <B extends DataFlowBinding> DoubleExpression<B> doubleParameter(
            SourceSlot<B> source, final ParameterSlot<Double> parameter) {
        required(source, "source");
        required(parameter, "parameter");
        return new DoubleExpression<B>(
                source,
                new DoubleNode() {
                    @Override
                    public double evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        return frame.parameter(parameter).doubleValue();
                    }

                    @Override
                    public String canonical() {
                        return "double-parameter(" + parameter.canonical() + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                "parameter." + parameter.name(),
                Collections.<ParameterSlot<?>>singletonList(parameter),
                true);
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
                    public boolean evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        ExpressionNodes.requirePresent(
                                frame, presence, binding, index, path);
                        return value.evaluate(frame, binding, index);
                    }

                    @Override
                    public String canonical() {
                        return "optional-boolean(" + value.canonical() + ")";
                    }
                },
                presence,
                path);
    }

    public static <B extends DataFlowBinding> BooleanExpression<B> booleanParameter(
            SourceSlot<B> source, final ParameterSlot<Boolean> parameter) {
        required(source, "source");
        required(parameter, "parameter");
        return new BooleanExpression<B>(
                source,
                new BooleanNode() {
                    @Override
                    public boolean evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        return frame.parameter(parameter).booleanValue();
                    }

                    @Override
                    public String canonical() {
                        return "boolean-parameter(" + parameter.canonical() + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                "parameter." + parameter.name(),
                Collections.<ParameterSlot<?>>singletonList(parameter),
                true);
    }

    public static <B extends DataFlowBinding> StringExpression<B>
    requiredString(SourceSlot<B> source, int columnOrdinal, String path) {
        return new StringExpression<B>(
                source,
                ExpressionNodes.stringField(columnOrdinal, path),
                ExpressionNodes.alwaysPresent(),
                path);
    }

    public static <B extends DataFlowBinding> StringExpression<B>
    optionalString(SourceSlot<B> source, int columnOrdinal, String path) {
        return new StringExpression<B>(
                source,
                ExpressionNodes.stringField(columnOrdinal, path),
                ExpressionNodes.present(columnOrdinal, path),
                path);
    }

    public static <B extends DataFlowBinding> StringExpression<B>
    stringParameter(
            SourceSlot<B> source, final ParameterSlot<String> parameter) {
        required(source, "source");
        required(parameter, "parameter");
        return new StringExpression<B>(
                source,
                new StringNode() {
                    @Override
                    public String evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        return frame.parameter(parameter);
                    }

                    @Override
                    public String canonical() {
                        return "string-parameter(" + parameter.canonical() + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                "parameter." + parameter.name(),
                Collections.<ParameterSlot<?>>singletonList(parameter),
                true);
    }
}
