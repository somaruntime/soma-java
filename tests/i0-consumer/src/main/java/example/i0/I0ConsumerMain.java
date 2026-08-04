package example.i0;

public final class I0ConsumerMain {

    private I0ConsumerMain() {
    }

    public static void main(String[] arguments) {
        if (!"example.i0.schema".equals(SomaCompositionLinkage.schemaPackage())) {
            throw new AssertionError("unexpected schema package");
        }
        if (!SomaCompositionLinkage.fingerprint().matches("[0-9a-f]{64}")) {
            throw new AssertionError("unexpected composition fingerprint");
        }
        System.out.println("soma-i0-consumer: ok");
    }
}
