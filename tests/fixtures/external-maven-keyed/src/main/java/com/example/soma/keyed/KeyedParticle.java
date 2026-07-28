package com.example.soma.keyed;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaOptional;
import io.github.somaruntime.soma.annotation.SomaTable;

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
