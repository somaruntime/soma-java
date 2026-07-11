package com.hgtech.soma.examples.game;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "players", defaultCapacity = 16)
public final class Player {
    @SomaKey public PlayerId playerId;
    @SomaField public int teamNo;
    @SomaField @SomaDefault("0") public long score;
}
