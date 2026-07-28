package com.hgtech.soma.runtime;

/** Resource envelope、materialization budget 与 failure 能力契约。 */
public final class RuntimeResourceAndFailureContractCheck {
    private RuntimeResourceAndFailureContractCheck() {
    }

    public static void main(String[] args) {
        RuntimeCoreContractCases.runResourceAndFailure();
        System.out.println("runtime-resource-and-failure-contract: ok");
    }
}
