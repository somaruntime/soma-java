package example.i1;

import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperationException;

public final class ConfigureFirstMain {

    private ConfigureFirstMain() {
    }

    public static void main(String[] arguments) {
        Soma.configure(SomaConfiguration.builder()
                .memoryBudgetBytes(64L << 20)
                .build());
        if (Soma.entityTable() == null) {
            throw new AssertionError("configured Table missing");
        }
        try {
            Soma.configure(SomaConfiguration.builder().build());
            throw new AssertionError("second configure was accepted");
        } catch (SomaOperationException failure) {
            if (failure.code() != SomaFailureCode.CONFIGURATION_FROZEN) {
                throw failure;
            }
        }
        System.out.println("i1-configure-first: ok");
    }
}
