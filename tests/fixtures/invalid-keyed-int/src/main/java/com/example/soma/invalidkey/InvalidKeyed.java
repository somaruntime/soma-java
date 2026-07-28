package com.example.soma.invalidkey;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
public final class InvalidKeyed {
    @SomaKey public boolean unsupportedBooleanKey;
    @SomaKey public int duplicateIntKey;
    @SomaField public int payload;

    public InvalidKeyed() {
    }
}
