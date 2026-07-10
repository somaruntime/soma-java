package com.example.soma.keyed;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(defaultCapacity = 2)
public final class LongKeyedParticle {
    @SomaKey
    public long id;

    @SomaField
    public long energy;

    public LongKeyedParticle() {
    }
}
