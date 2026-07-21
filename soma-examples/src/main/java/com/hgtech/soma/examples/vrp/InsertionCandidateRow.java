package com.hgtech.soma.examples.vrp;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "insertion_candidate_rows", defaultCapacity = 16384)
public final class InsertionCandidateRow {
    @SomaField public RouteId routeId;
    @SomaField public CustomerId customerId;
    @SomaField public int insertionOrdinal;
    @SomaField public long routeVersion;
    @SomaField public long deltaDistanceMeters;
    @SomaField public long projectedArrivalSecond;
    @SomaField public int projectedLoad;
    @SomaField public long projectedTotalDurationSeconds;
}
