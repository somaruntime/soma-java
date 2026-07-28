package com.example.soma.keyed;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(defaultCapacity = 2)
public final class KeyedParticle {
    @SomaKey
    public int id;

    @SomaField
    public int energy;

    @SomaField
    @SomaOptional
    public Integer priority;

    public KeyedParticle() {
    }
}
