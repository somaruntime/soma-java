package com.example.soma.enumkeyed;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaSemantic;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
public final class DateTimeKeyedMoment {
    @SomaKey(semantic = SomaSemantic.DATE_TIME)
    public long epochMillis;

    @SomaField
    public int payload;

    public DateTimeKeyedMoment() {
    }
}
