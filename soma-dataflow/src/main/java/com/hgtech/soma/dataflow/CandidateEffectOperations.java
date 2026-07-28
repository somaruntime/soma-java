package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.CandidateEffectAccess;
import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.runtime.RemoveResult;
import com.hgtech.soma.runtime.UpdateResult;

abstract class CandidateEffectOperation<
        B extends DataFlowBinding, R>
        extends SingleSourceOperation<R> {
    final CandidateProgram<B> program;
    final CandidateEffectAccess<B> access;

    CandidateEffectOperation(
            CandidateProgram<B> program,
            CandidateEffectAccess<B> access) {
        super(program);
        this.program = program;
        this.access = access;
    }

    @SuppressWarnings("unchecked")
    final B binding(ExecutionFrame frame) {
        return (B) frame.binding(source);
    }

    @Override
    public final String physicalPlan() {
        return "candidate-freeze[selection,epoch]"
                + " -> release-read-guard"
                + " -> single-table-safe-point[" + effectName() + "]";
    }

    @Override
    public final boolean effectful() {
        return true;
    }

    abstract String effectName();
}

final class CandidateUpdateOperation<B extends DataFlowBinding>
        extends CandidateEffectOperation<B, UpdateResult> {
    private final Object updater;
    private final long opaqueIdentity;

    CandidateUpdateOperation(
            CandidateProgram<B> program,
            CandidateEffectAccess<B> access,
            Object updater) {
        super(program, access);
        if (updater == null) {
            throw new NullPointerException("updater");
        }
        this.updater = updater;
        opaqueIdentity = DataFlowSupport.nextOpaqueIdentity();
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->update("
                + access.identity() + ",opaque-instance-"
                + opaqueIdentity + ")";
    }

    @Override
    public String logicalShape() {
        return "Candidate -> MutationSet<single-source> -> UpdateResult";
    }

    @Override
    public String logicalPlan() {
        return program.canonical()
                + " -> FreezeMutationSet -> Update";
    }

    @Override
    String effectName() {
        return "update";
    }

    @Override
    public ExecutionOutcome<UpdateResult> execute(
            ExecutionFrame frame) {
        ParallelCandidateSelection parallel =
                CandidateParallelKernels.select(
                        program, frame, "dataflow.update.freeze");
        CandidateSelection selected = parallel == null
                ? program.select(frame, "dataflow.update.freeze")
                : new CandidateSelection(
                        parallel.indexes,
                        parallel.matched,
                        parallel.scanned);
        final B binding = binding(frame);
        final long expectedEpoch = binding.structuralEpoch();
        final int[] indexes = selected.materializedIndexes(
                frame, "dataflow.update.indexes");
        final int count = selected.size;
        return ExecutionOutcome.effect(
                new DeferredEffect<UpdateResult>() {
                    @Override
                    public UpdateResult commit() {
                        return access.update(
                                binding,
                                expectedEpoch,
                                indexes,
                                count,
                                updater);
                    }
                },
                selected.scanned,
                selected.size,
                1L,
                parallel == null ? 1 : parallel.tasks,
                parallel == null ? 1 : parallel.workers);
    }
}

final class CandidateRemoveOperation<B extends DataFlowBinding>
        extends CandidateEffectOperation<B, RemoveResult> {
    CandidateRemoveOperation(
            CandidateProgram<B> program,
            CandidateEffectAccess<B> access) {
        super(program, access);
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->remove("
                + access.identity() + ")";
    }

    @Override
    public String logicalShape() {
        return "Candidate -> MutationSet<single-source> -> RemoveResult";
    }

    @Override
    public String logicalPlan() {
        return program.canonical()
                + " -> FreezeMutationSet -> Remove";
    }

    @Override
    String effectName() {
        return "remove";
    }

    @Override
    public ExecutionOutcome<RemoveResult> execute(
            ExecutionFrame frame) {
        ParallelCandidateSelection parallel =
                CandidateParallelKernels.select(
                        program, frame, "dataflow.remove.freeze");
        CandidateSelection selected = parallel == null
                ? program.select(frame, "dataflow.remove.freeze")
                : new CandidateSelection(
                        parallel.indexes,
                        parallel.matched,
                        parallel.scanned);
        final B binding = binding(frame);
        final long expectedEpoch = binding.structuralEpoch();
        final int[] indexes = selected.materializedIndexes(
                frame, "dataflow.remove.indexes");
        final int count = selected.size;
        return ExecutionOutcome.effect(
                new DeferredEffect<RemoveResult>() {
                    @Override
                    public RemoveResult commit() {
                        return access.remove(
                                binding,
                                expectedEpoch,
                                indexes,
                                count);
                    }
                },
                selected.scanned,
                selected.size,
                1L,
                parallel == null ? 1 : parallel.tasks,
                parallel == null ? 1 : parallel.workers);
    }
}
