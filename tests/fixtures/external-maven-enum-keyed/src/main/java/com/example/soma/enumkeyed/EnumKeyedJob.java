package com.example.soma.enumkeyed;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
public final class EnumKeyedJob {
    @SomaKey
    public LifecycleState state;

    @SomaField
    public int payload;

    public EnumKeyedJob() {
    }
}
