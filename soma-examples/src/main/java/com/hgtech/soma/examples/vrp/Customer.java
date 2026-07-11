package com.hgtech.soma.examples.vrp;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "customers", defaultCapacity = 4096)
@SomaIndex(name = "by_state", fields = {"state"})
@SomaOrder(name = "by_due_then_input", by = {
        @SomaSort("dueMinute"), @SomaSort("inputOrder"),
        @SomaSort("customerId.value")})
public final class Customer {
    @SomaKey public CustomerId customerId;
    @SomaField public long inputOrder;
    @SomaField public LocationId locationId;
    @SomaField public int demand;
    @SomaField public long readyMinute;
    @SomaField public long dueMinute;
    @SomaField public long serviceMinutes;
    @SomaField @SomaDefault("UNASSIGNED") public CustomerState state;
    @SomaField @SomaOptional public RouteId assignedRoute;
    @SomaField @SomaOptional public Integer assignedPosition;
    @SomaField @SomaOptional public Long arrivalMinute;
}
