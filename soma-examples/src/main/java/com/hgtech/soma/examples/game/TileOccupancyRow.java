package com.hgtech.soma.examples.game;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaTable;

/** 由 GameUnitState.position 可重建的 keyed cache。 */
@SomaTable(name = "tile_occupancy_rows", defaultCapacity = 4096)
public final class TileOccupancyRow {
  @SomaKey public GridPosition position;
  @SomaField @SomaOptional public UnitId occupantUnit;
}
