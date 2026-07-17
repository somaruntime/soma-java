package com.hgtech.soma.examples.game;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "map_tile_rows", defaultCapacity = 4096)
public final class MapTileRow {
    @SomaField public GridPosition position;
    @SomaField public TerrainType terrain;
    @SomaField public int moveCost;
    @SomaField public boolean blocksSight;
    @SomaField @SomaOptional public UnitId occupantUnit;
}
