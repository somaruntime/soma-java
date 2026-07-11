package com.hgtech.soma.examples.vrp;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "route_visit_rows", defaultCapacity = 32)
@SomaOrder(name = "by_position", by = {@SomaSort("position")})
public final class RouteVisitRow {
    @SomaField public int position;
    @SomaField public CustomerId customerId;
    @SomaField public long arrivalMinute;
    @SomaField public long departureMinute;
    @SomaField public int loadAfterVisit;
}
