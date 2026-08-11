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
            CanonicalRowPhysicalPlan physical = CanonicalRowPlanner.plan(normalized);
            long scratch = CheckedLong.add(
                    physical.resources.temporaryBytes,
                    canonicalSelectionScratch(bound),
                    bound.operation,
                    bound.provenance);
            try (GlobalMemoryManager.TemporaryLease ignored =
                         table.leaseMutationTemporary(
                                 scratch,
                                 SomaOperation.UPDATE,
                                 bound.provenance)) {
                beginCanonicalCursors(table, bound);
                try {
                    CanonicalRowExecutionFrame frame =
                            new CanonicalRowExecutionFrame(physical);
                    CanonicalParallelRowScheduler.prepare(frame);
                    IntLocatorBuffer selected = CanonicalRowExecution.locators(frame);
                    int matched = selected.size();
                    if (matched == 0) return table.selectionUpdateResult(0, 0);

                    TableChunkDirectory candidate =
                            bound.root.directory.copyForUpdates(selected);
                    GeneratedSelectionEditor editor = table.selectionEditor();
                    int changed = 0;
                    boolean indexedValueChanged = false;
                    editor.begin(bound.root, bound.provenance);
                    try {
                        for (int index = 0; index < selected.size(); index++) {
                            int locator = selected.get(index);
                            editor.enter(locator);
                            CallbackExecutionScope.enter();
                            try {
                                updater.accept();
                                if (editor.changed()) {
                                    indexedValueChanged |= editor.indexedValueChanged();
                                    candidate.write(locator, editor);
                                    changed++;
                                }
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
                    if (changed == 0) {
                        return table.selectionUpdateResult(matched, 0);
                    }
                    return table.publishSelectionUpdate(
                            bound.root,
                            candidate,
                            matched,
                            changed,
                            indexedValueChanged,
                            bound.provenance);
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
            CanonicalRowPhysicalPlan physical = CanonicalRowPlanner.plan(normalized);
            long scratch = CheckedLong.add(
                    physical.resources.temporaryBytes,
                    canonicalSelectionScratch(bound),
                    bound.operation,
                    bound.provenance);
            try (GlobalMemoryManager.TemporaryLease ignored =
                         table.leaseMutationTemporary(
                                 scratch,
                                 SomaOperation.REMOVE,
                                 bound.provenance)) {
                beginCanonicalCursors(table, bound);
                try {
                    CanonicalRowExecutionFrame frame =
                            new CanonicalRowExecutionFrame(physical);
                    CanonicalParallelRowScheduler.prepare(frame);
                    IntLocatorBuffer selected = CanonicalRowExecution.locators(frame);
                    if (selected.size() == 0) {
                        return table.selectionRemoveResult(0);
                    }
                    TypedValues copyScratch = new TypedValues(table.layout());
                    TableChunkDirectory candidate =
                            bound.root.directory.copyForSelectionRemove(
                                    selected,
                                    bound.root.size,
                                    copyScratch,
                                    bound.provenance);
                    return table.publishSelectionRemove(
                            bound.root,
                            candidate,
                            selected.size(),
                            bound.provenance);
                } finally {
                    endCanonicalCursors(table);
                }
            }
        }
    }

    private static long canonicalSelectionScratch(
            BoundCanonicalRowOperation bound) {
        RowExecutionSupport.arrayLength(
                bound.root.size, bound.operation, bound.provenance);
        long perRow = CheckedLong.multiply(
                bound.root.size, 192L, bound.operation, bound.provenance);
        return CheckedLong.add(
                CheckedLong.add(
                        bound.root.managedBytes,
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
