package com.example.soma.dense;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "Particle", defaultCapacity = 4)
public final class Particle {
    @SomaField
    public int id;

    @SomaField
    public long ticks;

    @SomaField
    public float x;

    @SomaField
    @SomaOptional
    public Integer energy;

    public Particle() {
    }
}
