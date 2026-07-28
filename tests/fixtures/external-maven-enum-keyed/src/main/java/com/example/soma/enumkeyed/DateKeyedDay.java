package com.example.soma.enumkeyed;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaSemantic;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
public final class DateKeyedDay {
    @SomaKey(semantic = SomaSemantic.DATE)
    public int epochDay;

    @SomaField
    public int payload;

    public DateKeyedDay() {
    }
}
