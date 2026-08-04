package example.i1;

import io.github.somaruntime.soma.SomaConfiguration;

public final class ClassLoadThenConfigureMain {

    private ClassLoadThenConfigureMain() {
    }

    public static void main(String[] arguments) throws Exception {
        Class.forName("example.i1.Soma", true, ClassLoadThenConfigureMain.class.getClassLoader());
        Soma.configure(SomaConfiguration.builder()
                .memoryBudgetBytes(64L << 20)
                .build());
        Soma.defaultGroup();
        System.out.println("i1-class-load-then-configure: ok");
    }
}
