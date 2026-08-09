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
        GeneratedTable table = logical.owner();
        try (GroupOperationGuard.Lease operation =
                     table.acquireMutation(SomaOperation.UPDATE)) {
            BoundRowPlan bound = new BoundRowPlan(
                    logical,
                    table.currentRoot(),
                    SomaOperation.UPDATE,
                    operation.provenance());
            long scratch = selectionScratch(bound);
            try (GlobalMemoryManager.TemporaryLease ignored =
                         table.leaseMutationTemporary(
                                 scratch,
                                 SomaOperation.UPDATE,
                                 bound.provenance)) {
                LongLocatorBuffer selected = freezeSelection(bound);
                long matched = selected.size();
                if (matched == 0L) return table.selectionUpdateResult(0L, 0L);

                TableChunkDirectory candidate =
                        bound.root.directory.copyForUpdates(selected);
                GeneratedSelectionEditor editor = table.selectionEditor();
                long changed = 0L;
                boolean indexedValueChanged = false;
                editor.begin(bound.root, bound.provenance);
                try {
                    for (int index = 0; index < selected.size(); index++) {
                        long locator = selected.get(index);
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
                if (changed == 0L) {
                    return table.selectionUpdateResult(matched, 0L);
                }
                return table.publishSelectionUpdate(
                        bound.root,
                        candidate,
                        matched,
                        changed,
                        indexedValueChanged,
                        bound.provenance);
            }
        }
    }

    static RemoveResult remove(LogicalRowPlan logical) {
        GeneratedTable table = logical.owner();
        try (GroupOperationGuard.Lease operation =
                     table.acquireMutation(SomaOperation.REMOVE)) {
            BoundRowPlan bound = new BoundRowPlan(
                    logical,
                    table.currentRoot(),
                    SomaOperation.REMOVE,
                    operation.provenance());
            long scratch = selectionScratch(bound);
            try (GlobalMemoryManager.TemporaryLease ignored =
                         table.leaseMutationTemporary(
                                 scratch,
                                 SomaOperation.REMOVE,
                                 bound.provenance)) {
                LongLocatorBuffer selected = freezeSelection(bound);
                if (selected.size() == 0) return table.selectionRemoveResult(0L);
                TypedValues copyScratch = new TypedValues(table.layout());
                TableChunkDirectory candidate =
                        bound.root.directory.copyForSelectionRemove(
                                selected,
                                bound.root.size,
                                copyScratch,
                                bound.provenance);
                return table.publishSelectionRemove(
                        bound.root, candidate, selected.size(), bound.provenance);
            }
        }
    }

    private static LongLocatorBuffer freezeSelection(BoundRowPlan bound) {
        GeneratedTable table = bound.logical.owner();
        table.queryCursor().begin(bound.root, bound.operation, bound.provenance);
        try {
            table.secondaryQueryCursor().begin(
                    bound.root, bound.operation, bound.provenance);
            try {
                return RowExecutor.locators(bound);
            } finally {
                table.secondaryQueryCursor().end();
            }
        } finally {
            table.queryCursor().end();
        }
    }

    private static long selectionScratch(BoundRowPlan bound) {
        RowExecutionSupport.arrayLength(
                bound.root.size, bound.operation, bound.provenance);
        long perRow = CheckedLong.multiply(
                bound.root.size, 192L, bound.operation, bound.provenance);
        long literals = CheckedLong.multiply(
                bound.logical.inLiteralCount(),
                256L,
                bound.operation,
                bound.provenance);
        return CheckedLong.add(
                CheckedLong.add(
                        bound.root.managedBytes,
                        perRow,
                        bound.operation,
                        bound.provenance),
                CheckedLong.add(
                        literals, 4096L, bound.operation, bound.provenance),
                bound.operation,
                bound.provenance);
    }
}
