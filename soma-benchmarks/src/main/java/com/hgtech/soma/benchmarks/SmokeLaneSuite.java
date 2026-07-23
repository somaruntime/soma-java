package com.hgtech.soma.benchmarks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** G5 smoke lane compatibility facade and run orchestrator. */
final class SmokeLaneSuite {
    static final List<String> REQUIRED_LANES = SmokeLaneContract.REQUIRED_LANES;

    private SmokeLaneSuite() {
    }

    static List<BenchmarkRecord> run(BenchmarkConfig config, BenchmarkEnvironment environment) {
        ArrayList<BenchmarkRecord> records = new ArrayList<BenchmarkRecord>();
        for (String lane : REQUIRED_LANES) {
            for (int warmup = 0; warmup < config.warmupIterations; warmup++) {
                SmokeLaneWorkloads.execute(config, lane, warmup);
            }
            LaneObservation aggregate = null;
            for (int iteration = 0; iteration < config.measurementIterations; iteration++) {
                LaneObservation current = SmokeLaneWorkloads.execute(config, lane, iteration + 17);
                if (aggregate == null) aggregate = current;
                else SmokeLaneAggregation.merge(aggregate, current);
            }
            if (aggregate == null || aggregate.measurementNanos <= 0L) {
                throw new IllegalStateException("lane produced no measurement: " + lane);
            }
            aggregate = SmokeLaneEvidence.finish(aggregate, SmokeLaneContract.phaseKey(lane));
            records.add(BenchmarkModel.record(environment, config, aggregate));
        }
        return records;
    }

    static String workloadId(String lane) {
        return SmokeLaneContract.workloadId(lane);
    }

    static void validateWorkloadEvidence(String lane, Map<String, Object> evidence) {
        SmokeLaneContract.validateWorkloadEvidence(lane, evidence);
    }

    static void validateLaneRecord(String lane, Map<String, Object> record) {
        SmokeLaneContract.validateLaneRecord(lane, record);
    }
}
