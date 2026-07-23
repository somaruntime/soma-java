package com.hgtech.soma.examples.scheduler.state;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "transport_times", defaultCapacity = 4096)
public final class TransportTime {
  @SomaKey public MachinePairKey machinePair;
  @SomaField public long transportMinutes;
}
