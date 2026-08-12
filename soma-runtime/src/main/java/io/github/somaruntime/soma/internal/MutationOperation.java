package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.UpdateResult;

/** Coordinates frozen Selection mutation, full staging and one atomic publication. */
final class MutationOperation {

    private MutationOperation() {
    }

    static UpdateResult update(
            LogicalRowPlan logical,
            GeneratedCallbacks.EditorAction updater) {
        CanonicalRowOperation canonical = CanonicalRowLowering.operation(
                logical.owner(),
                logical,
                CanonicalRowOperation.TerminalKind.UPDATE,
                HostCallbackHandle.editorAction(
                        logical.owner().logicalIdentity(), updater));
        if (canonical == null) {
            throw new AssertionError("Selection update failed Canonical lowering");
        }
        return canonicalUpdate(logical.owner(), canonical, updater);
    }

    static RemoveResult remove(LogicalRowPlan logical) {
        CanonicalRowOperation canonical = CanonicalRowLowering.operation(
                logical.owner(),
                logical,
                CanonicalRowOperation.TerminalKind.REMOVE,
                null);
        if (canonical == null) {
            throw new AssertionError("Selection remove failed Canonical lowering");
        }
        return canonicalRemove(logical.owner(), canonical);
    }

    private static UpdateResult canonicalUpdate(
            GeneratedTable table,
            CanonicalRowOperation canonical,
            GeneratedCallbacks.EditorAction updater) {
        try (GroupOperationGuard.Lease operation =
                     table.acquireMutation(SomaOperation.UPDATE)) {
            BoundCanonicalRowOperation bound = new BoundCanonicalRowOperation(
                    canonical,
                    table,
                    table.layout(),
                    table.currentRoot(),
                    SomaOperation.UPDATE,
                    operation.provenance());
            NormalizedCanonicalRow normalized = CanonicalRowPlanner.normalize(bound);
            CanonicalRowPhysicalPlan physical = CanonicalRowPlanner.plan(
                    normalized,
                    CanonicalRowPhysicalRequest.mutation(
                            CanonicalRowPhysicalRequest.MutationHandoff.UPDATE_WRITE_SET,
                            canonicalSelectionScratch(bound, true)));
            try (GlobalMemoryManager.TemporaryLease ignored =
                         table.leaseMutationTemporary(
                                 physical.resources.temporaryBytes,
                                 SomaOperation.UPDATE,
                                 bound.provenance)) {
                beginCanonicalCursors(table, bound);
                try {
                    CanonicalRowExecutionFrame frame =
                            new CanonicalRowExecutionFrame(physical);
                    CanonicalParallelRowScheduler.prepare(frame);
                    IntLocatorBuffer selected = CanonicalRowExecution.locators(frame);
                    CanonicalMutationHandoff handoff =
                            CanonicalMutationHandoff.update(table.layout(), selected);
                    frame.attachMutationHandoff(handoff);
                    try {
                        int matched = handoff.size();
                        if (matched == 0) return table.selectionUpdateResult(0, 0);
                        GeneratedSelectionEditor editor = table.selectionEditor();
                        SelectionWriteSet writeSet = handoff.writeSet();
                        editor.begin(bound.root, bound.provenance);
                        try {
                            for (int index = 0; index < selected.size(); index++) {
                                int locator = selected.get(index);
                                editor.enter(locator);
                                CallbackExecutionScope.enter();
                                try {
                                    updater.accept();
                                    writeSet.capture(
                                            index,
                                            editor.originalValues(),
                                            editor,
                                            bound.root.directory);
                                } catch (Exception failure) {
                                    throw editor.callbackFailure(failure);
                                } finally {
                                    CallbackExecutionScope.exit();
                                    editor.leave();
                                }
                            }
                        } finally {
                            editor.end();
                        }
                        if (writeSet.changedRowCount() == 0) {
                            return table.selectionUpdateResult(matched, 0);
                        }
                        return table.publishSelectionUpdate(
                                bound.root,
                                writeSet,
                                matched,
                                bound.provenance);
                    } finally {
                        handoff.clear();
                    }
                } finally {
                    endCanonicalCursors(table);
                }
            }
        }
    }

    private static RemoveResult canonicalRemove(
            GeneratedTable table,
            CanonicalRowOperation canonical) {
        try (GroupOperationGuard.Lease operation =
                     table.acquireMutation(SomaOperation.REMOVE)) {
            BoundCanonicalRowOperation bound = new BoundCanonicalRowOperation(
                    canonical,
                    table,
                    table.layout(),
                    table.currentRoot(),
                    SomaOperation.REMOVE,
                    operation.provenance());
            NormalizedCanonicalRow normalized = CanonicalRowPlanner.normalize(bound);
            CanonicalRowPhysicalPlan physical = CanonicalRowPlanner.plan(
                    normalized,
                    CanonicalRowPhysicalRequest.mutation(
                            CanonicalRowPhysicalRequest.MutationHandoff.REMOVE_PLAN,
                            canonicalSelectionScratch(bound, false)));
            try (GlobalMemoryManager.TemporaryLease ignored =
                         table.leaseMutationTemporary(
                                 physical.resources.temporaryBytes,
                                 SomaOperation.REMOVE,
                                 bound.provenance)) {
                beginCanonicalCursors(table, bound);
                try {
                    CanonicalRowExecutionFrame frame =
                            new CanonicalRowExecutionFrame(physical);
                    CanonicalParallelRowScheduler.prepare(frame);
                    IntLocatorBuffer selected = CanonicalRowExecution.locators(frame);
                    CanonicalMutationHandoff handoff =
                            CanonicalMutationHandoff.remove(
                                    selected, bound.root.size, bound.provenance);
                    frame.attachMutationHandoff(handoff);
                    try {
                        if (handoff.size() == 0) {
                            return table.selectionRemoveResult(0);
                        }
                        return table.publishSelectionRemove(
                                bound.root,
                                handoff.removePlan(),
                                bound.provenance);
                    } finally {
                        handoff.clear();
                    }
                } finally {
                    endCanonicalCursors(table);
                }
            }
        }
    }

    private static long canonicalSelectionScratch(
            BoundCanonicalRowOperation bound,
            boolean update) {
        RowExecutionSupport.arrayLength(
                bound.root.size, bound.operation, bound.provenance);
        long perRow = CheckedLong.multiply(
                bound.root.size, 192L, bound.operation, bound.provenance);
        long mutationStaging;
        if (update
                && bound.layout.indexCount() == 0
                && bound.root.directory.encodedChunkCount() == 0L) {
            mutationStaging = bound.layout.selectionWriteSetUpperBoundBytes(
                    bound.root.size,
                    bound.operation,
                    bound.provenance);
        } else {
            mutationStaging = bound.root.managedBytes;
        }
        return CheckedLong.add(
                CheckedLong.add(
                        mutationStaging,
                        perRow,
                        bound.operation,
                        bound.provenance),
                4096L,
                bound.operation,
                bound.provenance);
    }

    private static void beginCanonicalCursors(
            GeneratedTable table,
            BoundCanonicalRowOperation bound) {
        table.queryCursor().begin(bound.root, bound.operation, bound.provenance);
        try {
            table.secondaryQueryCursor().begin(
                    bound.root, bound.operation, bound.provenance);
        } catch (RuntimeException failure) {
            table.queryCursor().end();
            throw failure;
        }
    }

    private static void endCanonicalCursors(GeneratedTable table) {
        table.secondaryQueryCursor().end();
        table.queryCursor().end();
    }

}

/** Frame-owned frozen boundary between physical Selection and mutation publication. */
final class CanonicalMutationHandoff {
    private final IntLocatorBuffer membership;
    private final SelectionWriteSet writeSet;
    private final SelectionRemovePlan removePlan;

    private CanonicalMutationHandoff(
            IntLocatorBuffer membership,
            SelectionWriteSet writeSet,
            SelectionRemovePlan removePlan) {
        if (membership == null || (writeSet == null) == (removePlan == null)) {
            throw new AssertionError("invalid canonical mutation handoff");
        }
        this.membership = membership;
        this.writeSet = writeSet;
        this.removePlan = removePlan;
    }

    static CanonicalMutationHandoff update(
            GeneratedTableLayout layout,
            IntLocatorBuffer membership) {
        return new CanonicalMutationHandoff(
                membership,
                new SelectionWriteSet(layout, membership),
                null);
    }

    static CanonicalMutationHandoff remove(
            IntLocatorBuffer membership,
            int oldSize,
            Object provenance) {
        return new CanonicalMutationHandoff(
                membership,
                null,
                SelectionRemovePlan.prepare(membership, oldSize, provenance));
    }

    SelectionWriteSet writeSet() {
        if (writeSet == null) throw new AssertionError("write set is unavailable");
        return writeSet;
    }

    int size() {
        return membership.size();
    }

    SelectionRemovePlan removePlan() {
        if (removePlan == null) throw new AssertionError("remove plan is unavailable");
        return removePlan;
    }

    void clear() {
        if (writeSet != null) writeSet.clear();
        if (removePlan != null) removePlan.clear();
        // Membership is owned by the Frame and contains only primitive locators.
    }
}
