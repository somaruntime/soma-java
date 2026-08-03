package com.example.soma.i2;

import com.example.soma.i2.soma.ScalarRecord;
import com.example.soma.i2.soma.ScalarRecordTable;
import com.example.soma.i2.soma.Soma;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperationException;
import java.util.concurrent.ForkJoinPool;

/** Failure probe for an explicitly unavailable application-owned parallel pool. */
public final class ParallelFailureProbe {
    private ParallelFailureProbe() {
    }

    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool(1);
        pool.shutdown();
        Soma.configure(SomaConfiguration.builder().parallelExecutor(pool).build());
        ScalarRecordTable table = Soma.scalarRecordTable();
        table.add(new ScalarRecord(1L, 1, "one", true, (byte) 1, (short) 1, 'A', 1,
                1.0f, 1.0d, null, StateCode.READY));
        try {
            table.selectAll().parallel().count();
            throw new AssertionError("shutdown pool accepted");
        } catch (SomaOperationException failure) {
            if (failure.code() != SomaFailureCode.PARALLEL_EXECUTOR_UNAVAILABLE) {
                throw new AssertionError("wrong shutdown-pool failure", failure);
            }
        }
    }
}
