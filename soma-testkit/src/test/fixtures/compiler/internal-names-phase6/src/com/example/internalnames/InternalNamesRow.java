package com.example.internalnames;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

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
