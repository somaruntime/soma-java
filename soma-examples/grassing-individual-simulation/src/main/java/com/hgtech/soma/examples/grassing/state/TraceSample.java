package com.hgtech.soma.examples.grassing.state;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "trace_samples", defaultCapacity = 1024)
public final class TraceSample {
  @SomaField public long tick;
  @SomaField public int population;
  @SomaField public double totalGrass;
  @SomaField public double totalEnergy;
  @SomaField public long births;
  @SomaField public long deaths;
}
