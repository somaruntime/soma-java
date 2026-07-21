package com.hgtech.soma.examples.game;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "game_unit_definitions", defaultCapacity = 1024)
public final class GameUnitDefinition {
  @SomaKey public UnitId unitId;
  @SomaField public PlayerId playerId;
  @SomaField public UnitClassId unitClassId;
  @SomaField public int initiative;
}
