package com.example.soma.access;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaIndex;
import io.github.somaruntime.soma.annotation.SomaTable;
import io.github.somaruntime.soma.annotation.SomaUnique;

@SomaTable
@SomaIndex(name = "by_active", fields = {"active"})
@SomaUnique(name = "by_metric", fields = {"metric"})
public final class BooleanDoubleAccess {
    @SomaField public int id;
    @SomaField public boolean active;
    @SomaField public double metric;

    public BooleanDoubleAccess() {
    }
}
