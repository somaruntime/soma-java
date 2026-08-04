package example.i1;

import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperationException;

public final class DefaultFirstMain {

    private DefaultFirstMain() {
    }

    public static void main(String[] arguments) {
        Soma.defaultGroup();
        try {
            Soma.configure(SomaConfiguration.builder().build());
            throw new AssertionError("default-first configure was accepted");
        } catch (SomaOperationException failure) {
            if (failure.code() != SomaFailureCode.CONFIGURATION_FROZEN) {
                throw failure;
            }
        }
        System.out.println("i1-default-first: ok");
    }
}
