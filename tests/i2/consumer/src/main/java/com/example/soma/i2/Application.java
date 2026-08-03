package com.example.soma.i2;

import com.example.soma.i2.soma.ScalarRecord;
import com.example.soma.i2.soma.ScalarRecordTable;
import com.example.soma.i2.soma.Soma;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.UpdateResult;
import java.util.List;
import java.util.Optional;

public final class Application {
    private Application() { }

    public static void main(String[] args) {
        ScalarRecordTable table = Soma.scalarRecordTable();
        if (table.size() != 0L || table.capacity() != 0L) throw new AssertionError("lazy empty");
        table.reserve(3L);
        if (table.capacity() < 3L) throw new AssertionError("reserve");
        table.add(new ScalarRecord(1L, 7, "alpha", true, (byte) 1, (short) 2, 'A', 10,
                0.5f, 1.5d, null, StateCode.READY));
        table.add(new ScalarRecord(2L, 7, "beta", false, (byte) 2, (short) 3, 'B', 20,
                Float.NaN, -0.0d, "note", StateCode.RUNNING));
        table.add(new ScalarRecord(3L, 8, null, true, (byte) 3, (short) 4, 'C', 30,
                1.0f, 2.0d, "other", StateCode.DONE));
        if (table.size() != 3L) throw new AssertionError("size");
        Optional<ScalarRecord> found = table.find(2L);
        if (!found.isPresent() || found.get().machine() != 7 || !Float.isNaN(found.get().ratio())) {
            throw new AssertionError("typed find");
        }
        if (table.filter(table.enabled.eq(true)).count() != 2L) throw new AssertionError("boolean");
        if (table.filter(table.small.gt((byte) 1)).count() != 2L) throw new AssertionError("byte");
        if (table.filter(table.medium.ge((short) 3)).count() != 2L) throw new AssertionError("short");
        if (table.filter(table.marker.eq('B')).count() != 1L) throw new AssertionError("char");
        if (table.filter(table.count.ge(20)).count() != 2L) throw new AssertionError("int");
        if (table.filter(table.ratio.eq(Float.NaN)).count() != 1L) throw new AssertionError("float canonical");
        if (table.filter(table.score.eq(-0.0d)).count() != 1L) throw new AssertionError("double canonical");
        if (table.filter(table.note.isNull()).count() != 1L) throw new AssertionError("nullable reference");
        if (table.filter(table.state.eq(StateCode.DONE)).count() != 1L) throw new AssertionError("enum");
        if (table.byMachine(7).count() != 2L) throw new AssertionError("int index");
        if (table.byLabel(null).count() != 1L) throw new AssertionError("null index");
        if (!table.selectAll().anyMatch(view -> view.id() == 2L)) throw new AssertionError("anyMatch");
        if (!table.selectAll().allMatch(view -> view.id() > 0L)) throw new AssertionError("allMatch");
        if (!table.selectAll().noneMatch(view -> view.id() < 0L)) throw new AssertionError("noneMatch");
        List<String> labels = table.selectAll().filter(table.label.isNotNull())
                .map(view -> view.label()).toList();
        if (labels.size() != 2 || !"alpha".equals(labels.get(0))) throw new AssertionError("mapped list");
        String[] labelArray = table.selectAll().filter(table.label.isNotNull())
                .map(view -> view.label()).toArray(String.class);
        if (labelArray.length != 2 || !"beta".equals(labelArray[1])) throw new AssertionError("mapped array");
        long mappedSum = table.selectAll().mapToLong(view -> view.count()).sum();
        if (mappedSum != 60L) throw new AssertionError("mapToLong");
        ScalarRecordTable overflow = Soma.createGroup().scalarRecordTable();
        overflow.add(new ScalarRecord(100L, 1, "x", true, (byte) 0, (short) 0, 'X', Integer.MAX_VALUE,
                0.0f, 0.0d, null, StateCode.READY));
        overflow.add(new ScalarRecord(101L, 1, "y", true, (byte) 0, (short) 0, 'Y', Integer.MAX_VALUE,
                0.0f, 0.0d, null, StateCode.READY));
        long cancelledSum = overflow.selectAll().mapToLong(view -> view.id() == 100L
                ? Long.MAX_VALUE : -1L).sum();
        if (cancelledSum != Long.MAX_VALUE - 1L) throw new AssertionError("extended sum");
        try {
            table.selectAll().filter(view -> { throw new IllegalStateException("boom"); }).findFirst();
            throw new AssertionError("findFirst callback escaped");
        } catch (SomaOperationException failure) {
            if (failure.code() != io.github.somaruntime.soma.SomaFailureCode.CALLBACK_FAILED) {
                throw new AssertionError("findFirst callback mapping");
            }
        }
        try {
            table.selectAll().filter(view -> { throw new IllegalStateException("boom"); }).toList();
            throw new AssertionError("toList callback escaped");
        } catch (SomaOperationException failure) {
            if (failure.code() != io.github.somaruntime.soma.SomaFailureCode.CALLBACK_FAILED) {
                throw new AssertionError("toList callback mapping");
            }
        }
        UpdateResult update = table.update(1L, editor -> editor.count(editor.count() + 5));
        if (update.matched() != 1L || update.changed() != 1L || table.get(1L).count() != 15) {
            throw new AssertionError("point update");
        }
        try {
            table.add(new ScalarRecord(1L, 1, "duplicate", false, (byte) 0, (short) 0, 'D', 0,
                    0.0f, 0.0d, null, StateCode.READY));
            throw new AssertionError("duplicate accepted");
        } catch (SomaOperationException expected) {
            // Stable duplicate-key failure is part of the scalar breadth slice.
        }
        if (Soma.defaultGroup().scalarRecordTable() != table) throw new AssertionError("default identity");
        ScalarRecord detached = table.get(1L);
        detached.count(1000);
        if (table.get(1L).count() != 15) throw new AssertionError("detached result");
    }
}
