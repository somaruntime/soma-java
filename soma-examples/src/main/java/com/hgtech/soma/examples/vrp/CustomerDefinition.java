package com.hgtech.soma.examples.vrp;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

/** Import 后只读的 customer input facts。 */
@SomaTable(name = "customer_definitions", defaultCapacity = 4096)
public final class CustomerDefinition {
  @SomaKey public CustomerId customerId;
  @SomaField public long inputOrder;
  @SomaField public LocationId locationId;
  @SomaField public int demand;
  @SomaField public long readySecond;
  @SomaField public long dueSecond;
  @SomaField public long serviceSeconds;
}
