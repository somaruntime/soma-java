package com.example.soma.access;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
@SomaIndex(name = "by_route", fields = {"key.routeId.value"})
@SomaOrder(name = "by_route_position", by = {
        @SomaSort("key.routeId.value"),
        @SomaSort("key.position")
})
public final class Visit {
    @SomaKey public RoutePositionKey key;
    @SomaField public int payload;

    public Visit() {
    }
}
