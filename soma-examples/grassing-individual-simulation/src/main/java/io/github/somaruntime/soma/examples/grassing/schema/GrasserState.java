package io.github.somaruntime.soma.examples.grassing.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaIndex;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "grasser_states", defaultCapacity = 16384)
@SomaIndex(name = "by_mode", fields = {"mode"})
public final class GrasserState {
  @SomaKey public GrasserId grasserId;
  @SomaField public int x;
  @SomaField public int y;
  @SomaField public double energy;
  @SomaField public BehaviourMode mode;
  @SomaField public int movementDirection;
}
