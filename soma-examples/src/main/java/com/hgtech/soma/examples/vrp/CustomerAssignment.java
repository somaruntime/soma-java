package com.hgtech.soma.examples.vrp;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

/** Customer 的唯一 assignment result；row absence 表示尚未分配。 */
@SomaTable(name = "customer_assignments", defaultCapacity = 4096)
public final class CustomerAssignment {
  @SomaKey public CustomerId customerId;
  @SomaField public RouteId routeId;
}
