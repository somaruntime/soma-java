package com.example.soma.access;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaTable;
import com.hgtech.soma.annotation.SomaUnique;

@SomaTable
@SomaIndex(name = "by_metric", fields = {"metric"})
@SomaUnique(name = "unique_metric", fields = {"metric"})
public final class FloatingAccess {
    @SomaField public int id;
    @SomaField public float metric;

    public FloatingAccess() {
    }
}
