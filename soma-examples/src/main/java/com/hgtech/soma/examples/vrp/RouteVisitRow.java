package com.hgtech.soma.examples.vrp;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "route_visit_rows", defaultCapacity = 32)
public final class RouteVisitRow {
    @SomaField public int position;
    @SomaField public CustomerId customerId;
    @SomaField public long arrivalMinute;
    @SomaField public long departureMinute;
    @SomaField public int loadAfterVisit;
}
