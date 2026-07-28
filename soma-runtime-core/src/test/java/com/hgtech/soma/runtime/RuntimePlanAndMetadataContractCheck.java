package com.hgtech.soma.runtime;

/** Runtime plan、canonical identity 与 Metadata 能力契约。 */
public final class RuntimePlanAndMetadataContractCheck {
    private RuntimePlanAndMetadataContractCheck() {
    }

    public static void main(String[] args) {
        RuntimeCoreContractCases.runPlanAndMetadata();
        System.out.println("runtime-plan-and-metadata-contract: ok");
    }
}
