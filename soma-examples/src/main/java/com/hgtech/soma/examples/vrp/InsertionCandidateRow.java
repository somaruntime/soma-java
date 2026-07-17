package com.hgtech.soma.examples.vrp;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "insertion_candidate_rows", defaultCapacity = 16384)
@SomaIndex(name = "by_route", fields = {"routeId.value"})
public final class InsertionCandidateRow {
    @SomaField public CustomerId customerId;
    @SomaField public RouteId routeId;
    @SomaField public int insertAfterPosition;
    @SomaField public long deltaDistanceMeters;
    @SomaField public long projectedArrivalMinute;
    @SomaField public long violationPenalty;
}
