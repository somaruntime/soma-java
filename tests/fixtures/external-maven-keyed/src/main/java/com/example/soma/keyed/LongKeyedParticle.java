package com.example.soma.keyed;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(defaultCapacity = 2)
public final class LongKeyedParticle {
    @SomaKey
    public long id;

    @SomaField
    public long energy;

    public LongKeyedParticle() {
    }
}
