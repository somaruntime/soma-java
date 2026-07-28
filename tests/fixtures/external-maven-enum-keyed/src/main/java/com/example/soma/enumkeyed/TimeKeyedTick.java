package com.example.soma.enumkeyed;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaSemantic;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
public final class TimeKeyedTick {
    @SomaKey(semantic = SomaSemantic.TIME)
    public long nanosOfDay;

    @SomaField
    public int payload;

    public TimeKeyedTick() {
    }
}
