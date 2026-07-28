package com.example.soma.access;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaIndex;
import io.github.somaruntime.soma.annotation.SomaTable;
import io.github.somaruntime.soma.annotation.SomaUnique;

@SomaTable
@SomaIndex(name = "by_metric", fields = {"metric"})
@SomaUnique(name = "unique_metric", fields = {"metric"})
public final class FloatingAccess {
    @SomaField public int id;
    @SomaField public float metric;

    public FloatingAccess() {
    }
}
