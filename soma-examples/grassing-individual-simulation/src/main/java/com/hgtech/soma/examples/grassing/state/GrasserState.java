package com.hgtech.soma.examples.grassing.state;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

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
