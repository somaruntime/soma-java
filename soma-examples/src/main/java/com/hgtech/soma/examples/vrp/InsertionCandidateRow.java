package com.hgtech.soma.examples.vrp;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "insertion_candidate_rows", defaultCapacity = 16384)
@SomaOrder(name = "by_best_delta", by = {
        @SomaSort("violationPenalty"), @SomaSort("deltaDistanceMeters"),
        @SomaSort("projectedArrivalMinute"), @SomaSort("customerId.value")})
public final class InsertionCandidateRow {
    @SomaField public CustomerId customerId;
    @SomaField public RouteId routeId;
    @SomaField public int insertAfterPosition;
    @SomaField public long deltaDistanceMeters;
    @SomaField public long projectedArrivalMinute;
    @SomaField public long violationPenalty;
}
