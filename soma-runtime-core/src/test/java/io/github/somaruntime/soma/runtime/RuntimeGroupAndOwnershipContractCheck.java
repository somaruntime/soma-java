package io.github.somaruntime.soma.runtime;

/** Group composition、child ownership 与 lifecycle 能力契约。 */
public final class RuntimeGroupAndOwnershipContractCheck {
    private RuntimeGroupAndOwnershipContractCheck() {
    }

    public static void main(String[] args) {
        RuntimeCoreContractCases.runGroupAndOwnership();
        System.out.println("runtime-group-and-ownership-contract: ok");
    }
}
