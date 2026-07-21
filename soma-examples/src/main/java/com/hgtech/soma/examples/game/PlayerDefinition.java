package com.hgtech.soma.examples.game;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "player_definitions", defaultCapacity = 16)
public final class PlayerDefinition {
  @SomaKey public PlayerId playerId;
  @SomaField public int teamNo;
}
