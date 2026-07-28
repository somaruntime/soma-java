package com.example.soma.valuekeyed;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
public final class ValueKeyedMachine {
    @SomaKey
    public MachineId id;

    @SomaField
    public int payload;

    public ValueKeyedMachine() {
    }
}
