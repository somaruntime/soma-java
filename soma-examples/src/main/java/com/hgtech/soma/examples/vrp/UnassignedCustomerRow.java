package com.hgtech.soma.examples.vrp;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "unassigned_customer_rows", defaultCapacity = 4096)
@SomaOrder(name = "by_due_then_input", by = {
        @SomaSort("dueMinute"), @SomaSort("inputOrder"),
        @SomaSort("customerId.value")})
public final class UnassignedCustomerRow {
    @SomaField public CustomerId customerId;
    @SomaField public int demand;
    @SomaField public long dueMinute;
    @SomaField public long inputOrder;
}
