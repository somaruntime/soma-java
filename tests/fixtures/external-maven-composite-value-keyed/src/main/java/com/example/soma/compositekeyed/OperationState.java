package com.example.soma.compositekeyed;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
public final class OperationState {
    @SomaKey
    public OperationKey key;

    @SomaField
    public int payload;

    public OperationState() {
    }
}
