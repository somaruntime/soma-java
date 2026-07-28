package io.github.somaruntime.soma.runtime.generated;

import java.util.Objects;

/** 由 processor 固化并在 create boundary 验证的 generated identity。 */
public final class GeneratedMetadata {
    private final String schemaHash;
    private final String generatedTarget;
    private final String compilerIdentity;
    private final String generatedProtocol;
    private final String runtimeCompatibility;
    private final String planProtocol;
    private final String algorithm;
    private final String allocationEstimator;

    public GeneratedMetadata(
            String schemaHash,
            String generatedTarget,
            String compilerIdentity,
            String generatedProtocol,
            String runtimeCompatibility,
            String planProtocol,
            String algorithm,
            String allocationEstimator) {
        this.schemaHash = Objects.requireNonNull(schemaHash, "schemaHash");
        this.generatedTarget = Objects.requireNonNull(generatedTarget, "generatedTarget");
        this.compilerIdentity = Objects.requireNonNull(compilerIdentity, "compilerIdentity");
        this.generatedProtocol = Objects.requireNonNull(generatedProtocol, "generatedProtocol");
        this.runtimeCompatibility = Objects.requireNonNull(runtimeCompatibility, "runtimeCompatibility");
        this.planProtocol = Objects.requireNonNull(planProtocol, "planProtocol");
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
        this.allocationEstimator = Objects.requireNonNull(allocationEstimator, "allocationEstimator");
    }

    public String schemaHash() { return schemaHash; }
    public String generatedTarget() { return generatedTarget; }
    public String compilerIdentity() { return compilerIdentity; }
    public String generatedProtocol() { return generatedProtocol; }
    public String runtimeCompatibility() { return runtimeCompatibility; }
    public String planProtocol() { return planProtocol; }
    public String algorithm() { return algorithm; }
    public String allocationEstimator() { return allocationEstimator; }
}
