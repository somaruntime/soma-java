package com.hgtech.soma.examples.game;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "map_tile_definition_rows", defaultCapacity = 4096)
public final class MapTileDefinitionRow {
  @SomaKey public GridPosition position;
  @SomaField public TerrainType terrain;
  @SomaField public int moveCost;
  @SomaField public boolean blocksSight;
}
