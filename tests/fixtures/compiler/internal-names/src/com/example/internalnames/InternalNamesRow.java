package com.example.internalnames;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "InternalNamesRow", defaultCapacity = 4)
public final class InternalNamesRow {
    @SomaKey public int id;
    @SomaField public int capacity;
    @SomaField public int scratchCapacity;
    @SomaField public int updateScratchCapacity;
    @SomaField public int candidateScratch;

    public InternalNamesRow() {
    }
}
