package example.i1;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.internal.GeneratedGroup;
import io.github.somaruntime.soma.internal.GeneratedRuntime;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

public final class CapabilityRejectionMain {

    private CapabilityRejectionMain() {
    }

    public static void main(String[] arguments) throws Exception {
        expectInvalid(() -> GeneratedRuntime.createGroup(
                MethodHandles.lookup(),
                new Object()));

        SomaGroup valid = Soma.createGroup();
        Field runtimeField = SomaGroup.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        GeneratedGroup internalGroup = (GeneratedGroup) runtimeField.get(valid);
        expectInvalid(() -> SomaGroup.create(new Object(), internalGroup));
        expectInvalid(() -> EntityTable.create(new Object(), internalGroup));
        expectInvalid(() -> EntityTable.create(null, internalGroup));
        System.out.println("i1-capability-rejection: ok");
    }

    private static void expectInvalid(Action action) {
        try {
            action.run();
            throw new AssertionError("foreign generated capability was accepted");
        } catch (SomaOperationException failure) {
            if (failure.code() != SomaFailureCode.INVALID_ARGUMENT) {
                throw failure;
            }
        }
    }

    private interface Action {
        void run();
    }
}
