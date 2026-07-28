package com.example.soma.enumkeyed;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
public final class EnumKeyedJob {
    @SomaKey
    public LifecycleState state;

    @SomaField
    public int payload;

    public EnumKeyedJob() {
    }
}
