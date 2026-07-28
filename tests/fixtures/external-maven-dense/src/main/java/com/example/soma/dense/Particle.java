package com.example.soma.dense;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaOptional;
import io.github.somaruntime.soma.annotation.SomaTable;

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
