package io.github.somaruntime.soma.runtime;

/** Storage publication、column lifecycle 与 exact access 能力契约。 */
public final class RuntimeStorageAndAccessContractCheck {
    private RuntimeStorageAndAccessContractCheck() {
    }

    public static void main(String[] args) {
        RuntimeCoreContractCases.runStorageAndAccess();
        System.out.println("runtime-storage-and-access-contract: ok");
    }
}
