package com.example.soma.invalidkey;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
public final class InvalidKeyed {
    @SomaKey public boolean unsupportedBooleanKey;
    @SomaKey public int duplicateIntKey;
    @SomaField public int payload;

    public InvalidKeyed() {
    }
}
