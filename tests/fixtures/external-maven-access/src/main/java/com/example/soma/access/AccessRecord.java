package com.example.soma.access;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaIndex;
import io.github.somaruntime.soma.annotation.SomaTable;
import io.github.somaruntime.soma.annotation.SomaUnique;

@SomaTable(name = "AccessRecord", defaultCapacity = 4)
@SomaIndex(name = "by_state", fields = {"state"})
@SomaIndex(name = "by_group", fields = {"group"})
@SomaUnique(name = "by_code", fields = {"code"})
public final class AccessRecord {
    @SomaField public int code;
    @SomaField public int state;
    @SomaField public int group;
    @SomaField public int score;

    public AccessRecord() {
    }
}
