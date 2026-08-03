package com.example.soma.i1;

import com.example.soma.i1.soma.WorkItem;
import com.example.soma.i1.soma.WorkItemTable;
import com.example.soma.i1.soma.Soma;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.UpdateResult;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class Application {
    private Application() {
    }

    public static void main(String[] arguments) {
        WorkItemTable table = Soma.workItemTable();
        if (table != Soma.defaultGroup().workItemTable()) {
            throw new AssertionError("default accessor identity");
        }
        if (table.size() != 0L || table.capacity() != 0L) {
            throw new AssertionError("empty table state");
        }
        table.reserve(5L);
        if (table.capacity() < 5L) {
            throw new AssertionError("reserve did not establish capacity");
        }
        table.add(new WorkItem(0L, 10L));
        table.add(new WorkItem(1L, 40L));
        table.add(new WorkItem(2L, 60L));
        if (table.size() != 3L) {
            throw new AssertionError("append size");
        }
        Optional<WorkItem> found = table.find(1L);
        if (!found.isPresent() || found.get().duration() != 40L) {
            throw new AssertionError("find result");
        }
        WorkItem detached = found.get();
        detached.duration(999L);
        if (table.get(1L).duration() != 40L) {
            throw new AssertionError("find result was not detached");
        }
        if (table.filter(table.duration.gt(30L)).count() != 2L) {
            throw new AssertionError("typed filter");
        }
        if (table.filter(view -> view.duration() >= 40L).count() != 2L) {
            throw new AssertionError("callback filter");
        }
        WorkItemTable.Selection oneShot = table.selectAll().filter(table.duration.ge(0L));
        if (oneShot.count() != 3L) {
            throw new AssertionError("one-shot first terminal");
        }
        try {
            oneShot.count();
            throw new AssertionError("one-shot pipeline was replayed");
        } catch (SomaOperationException exception) {
            if (exception.code() != SomaFailureCode.PIPELINE_ALREADY_CONSUMED) {
                throw new AssertionError("wrong one-shot failure");
            }
        }
        WorkItemTable.Selection predecessor = table.selectAll();
        WorkItemTable.Selection branch = predecessor.filter(table.duration.ge(0L));
        try {
            predecessor.count();
            throw new AssertionError("intermediate did not claim its predecessor");
        } catch (SomaOperationException exception) {
            if (exception.code() != SomaFailureCode.PIPELINE_ALREADY_CONSUMED) {
                throw new AssertionError("wrong predecessor claim failure");
            }
        }
        try {
            predecessor.filter(view -> true);
            throw new AssertionError("claimed predecessor accepted a second branch");
        } catch (SomaOperationException exception) {
            if (exception.code() != SomaFailureCode.PIPELINE_ALREADY_CONSUMED) {
                throw new AssertionError("wrong branch claim failure");
            }
        }
        if (branch.count() != 3L) {
            throw new AssertionError("claimed child was not independently executable");
        }
        UpdateResult changed = table.update(1L, editor ->
                editor.duration(editor.duration() + 5L));
        if (changed.matched() != 1L || changed.changed() != 1L
                || table.get(1L).duration() != 45L) {
            throw new AssertionError("changed update");
        }
        UpdateResult noOp = table.update(1L, editor -> editor.duration(45L));
        if (noOp.matched() != 1L || noOp.changed() != 0L) {
            throw new AssertionError("no-op update");
        }
        UpdateResult missing = table.update(99L, editor -> {
            throw new AssertionError("missing update invoked callback");
        });
        if (missing.matched() != 0L || missing.changed() != 0L) {
            throw new AssertionError("missing update result");
        }
        try {
            table.add(new WorkItem(1L, 77L));
            throw new AssertionError("duplicate key accepted");
        } catch (SomaOperationException exception) {
            if (exception.code() != SomaFailureCode.DUPLICATE_KEY) {
                throw new AssertionError("wrong duplicate failure");
            }
        }
        try {
            table.get(99L);
            throw new AssertionError("missing key accepted");
        } catch (SomaOperationException exception) {
            if (exception.code() != SomaFailureCode.MISSING_KEY) {
                throw new AssertionError("wrong missing failure");
            }
        }
        try {
            table.update(2L, editor -> {
                editor.duration(1234L);
                throw new IllegalStateException("abort");
            });
            throw new AssertionError("failed callback was accepted");
        } catch (SomaOperationException exception) {
            if (exception.code() != SomaFailureCode.CALLBACK_FAILED
                    || table.get(2L).duration() != 60L) {
                throw new AssertionError("failed callback publication");
            }
        }
        try {
            table.update(0L, editor -> {
                throw new AssertionError("error callback");
            });
            throw new AssertionError("Error callback was accepted");
        } catch (AssertionError expected) {
            // JVM Error is propagated unchanged, but the Group guard must be released.
        }
        table.update(0L, editor -> editor.duration(11L));
        final WorkItemTable.Editor[] escaped = new WorkItemTable.Editor[1];
        table.update(0L, editor -> escaped[0] = editor);
        try {
            escaped[0].duration();
            throw new AssertionError("escaped editor remained usable");
        } catch (SomaOperationException exception) {
            if (exception.code() != SomaFailureCode.CALLBACK_SCOPE_VIOLATION) {
                throw new AssertionError("wrong escaped editor failure");
            }
        }
        final CountDownLatch workerDone = new CountDownLatch(1);
        final AtomicReference<SomaOperationException> crossThreadFailure =
                new AtomicReference<SomaOperationException>();
        table.update(0L, editor -> {
            Thread worker = new Thread(() -> {
                try {
                    editor.duration();
                } catch (SomaOperationException exception) {
                    crossThreadFailure.set(exception);
                } finally {
                    workerDone.countDown();
                }
            });
            worker.start();
            try {
                workerDone.await();
                worker.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        });
        if (crossThreadFailure.get() == null
                || crossThreadFailure.get().code() != SomaFailureCode.CALLBACK_SCOPE_VIOLATION) {
            throw new AssertionError("cross-thread Editor use was accepted");
        }
        WorkItemTable other = Soma.createGroup().workItemTable();
        other.add(new WorkItem(0L, 7L));
        if (other == table || other.size() != 1L || table.size() != 3L) {
            throw new AssertionError("explicit group isolation");
        }
        SomaExpression<WorkItemTable.View> foreignExpression = table.duration.gt(0L);
        WorkItemTable.Selection foreignReceiver = other.selectAll();
        try {
            foreignReceiver.filter(foreignExpression);
            throw new AssertionError("foreign expression was accepted");
        } catch (SomaOperationException exception) {
            if (exception.code() != SomaFailureCode.INVALID_ARGUMENT) {
                throw new AssertionError("wrong foreign expression failure");
            }
        }
        if (foreignReceiver.count() != 1L) {
            throw new AssertionError("foreign expression failure consumed receiver");
        }
    }
}
