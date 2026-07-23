package com.hgtech.soma.benchmarks;

/** Assembles canonical evidence fields after workload measurement. */
final class SmokeLaneEvidence {
    private SmokeLaneEvidence() {
    }

    static LaneObservation finish(LaneObservation result, String phase) {
        long reads = Math.max(result.explicitReads, result.scanned + result.lookups);
        long mutations = Math.max(result.explicitMutations, result.changed + result.removed);
        result.mutationReadRatio = BenchmarkModel.object(
                "mutations", Long.valueOf(mutations), "reads", Long.valueOf(reads));
        if (result.materializationInvocations > 0L
                && (Boolean.FALSE.equals(result.materializationStats.get("applicable"))
                || "generated-row-materializer".equals(
                        result.materializationStats.get("implementation")))) {
            result.materializationStats = BenchmarkModel.object(
                    "implementation", "generated-row-materializer",
                    "invocations", Long.valueOf(result.materializationInvocations),
                    "rows", Long.valueOf(result.materialized),
                    "estimatedBytes", result.materializationEstimatedAllocationBytes == 0L
                            ? null : Long.valueOf(
                            result.materializationEstimatedAllocationBytes),
                    "observationKind", "measured");
        }
        result.workloadId = SmokeLaneContract.workloadId(result.lane);
        result.workloadEvidence = BenchmarkModel.object(
                "executed", Boolean.TRUE,
                "proof", SmokeLaneContract.proofKey(result.lane),
                "implementation", SmokeLaneContract.isGeneratedLane(result.lane)
                        ? "generated-api" : "runtime-kernel",
                "positiveCount", Long.valueOf(Math.max(1L, result.operations)),
                "phase", phase);
        result.accessPatternCard = BenchmarkModel.object(
                "rowsCardinality", Long.valueOf(result.rows),
                "hotColumns", SmokeLaneContract.hotColumns(result.lane),
                "accessSource", phase,
                "readMutationMix", result.mutationReadRatio,
                "selectivity", result.scanned == 0L ? 0.0d
                        : (double) result.matched / result.scanned,
                "optionalDensity", result.optionalDensity,
                "childDensity", result.lane.startsWith("child_locality") ? "one-parent-local" : "not-applicable",
                "workingSetBytes", Long.valueOf(result.workingSetBytes),
                "allocationExport", result.materializationInvocations > 0L
                        ? "explicit-materialization-boundary" : "no-materialization-in-workload",
                "phaseBoundary", phase);
        return result;
    }
}
