package com.hgtech.soma.examples.game;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaTable;

/** Mutable unit facts；player/initiative 是 definition 的只读 hot-path preprojection。 */
@SomaTable(name = "game_unit_states", defaultCapacity = 1024)
@SomaIndex(name = "by_player", fields = {"playerId.value"})
@SomaIndex(name = "by_state", fields = {"state"})
public final class GameUnitState {
  @SomaKey public UnitId unitId;
  @SomaField public PlayerId playerId;
  @SomaField public int initiative;
  @SomaField public GridPosition position;
  @SomaField public int hp;
  @SomaField public int actionPoints;
  @SomaField @SomaDefault("READY") public UnitState state;
  @SomaField @SomaOptional public UnitId targetUnit;
}
