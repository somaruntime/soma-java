package com.example.soma.keyed;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
public final class ShortKeyed {
    @SomaKey public short id;
    @SomaField public int payload;
    public ShortKeyed() {
    }
}
