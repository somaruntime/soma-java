package com.example.phase0;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class MachineId {
    @SomaField
    long value;

    public static MachineId of(long value) {
        return new MachineId(value);
    }
}
