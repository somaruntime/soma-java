package com.example.soma.enumkeyed;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaSemantic;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
public final class DateTimeKeyedMoment {
    @SomaKey(semantic = SomaSemantic.DATE_TIME)
    public long epochMillis;

    @SomaField
    public int payload;

    public DateTimeKeyedMoment() {
    }
}
