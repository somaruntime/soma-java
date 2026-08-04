package example.i1;

import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperationException;

public final class BudgetFailureMain {

    private BudgetFailureMain() {
    }

    public static void main(String[] arguments) {
        Soma.configure(SomaConfiguration.builder().memoryBudgetBytes(1024L).build());
        EntityTable table = Soma.entityTable();
        try {
            table.reserve(1L);
            throw new AssertionError("budget failure was not raised");
        } catch (SomaOperationException failure) {
            if (failure.code() != SomaFailureCode.RESOURCE_LIMIT_EXCEEDED) {
                throw failure;
            }
        }
        if (table.size() != 0L || table.capacity() != 0L) {
            throw new AssertionError("budget failure published state");
        }
        System.out.println("i1-budget-failure: ok");
    }
}
