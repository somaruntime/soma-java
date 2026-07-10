package com.example.soma.enumkeyed;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaSemantic;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
public final class TimeKeyedTick {
    @SomaKey(semantic = SomaSemantic.TIME)
    public long nanosOfDay;

    @SomaField
    public int payload;

    public TimeKeyedTick() {
    }
}
