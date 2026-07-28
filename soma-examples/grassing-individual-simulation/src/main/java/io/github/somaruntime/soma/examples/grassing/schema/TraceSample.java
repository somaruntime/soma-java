package io.github.somaruntime.soma.examples.grassing.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "trace_samples", defaultCapacity = 1024)
public final class TraceSample {
  @SomaField public long tick;
  @SomaField public int population;
  @SomaField public double totalGrass;
  @SomaField public double totalEnergy;
  @SomaField public int grassingPopulation;
  @SomaField public int searchingPopulation;
  @SomaField public long births;
  @SomaField public long deaths;
}
