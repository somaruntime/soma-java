package com.example.soma.access;

import com.hgtech.soma.annotation.SomaDirection;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;
import com.hgtech.soma.annotation.SomaUnique;

@SomaTable
@SomaIndex(name = "by_active", fields = {"active"})
@SomaUnique(name = "by_metric", fields = {"metric"})
@SomaOrder(name = "by_active_metric", by = {
        @SomaSort("active"),
        @SomaSort(value = "metric", direction = SomaDirection.DESC)
})
public final class BooleanDoubleAccess {
    @SomaField public int id;
    @SomaField public boolean active;
    @SomaField public double metric;

    public BooleanDoubleAccess() {
    }
}
