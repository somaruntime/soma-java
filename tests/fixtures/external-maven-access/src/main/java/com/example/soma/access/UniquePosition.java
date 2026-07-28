package com.example.soma.access;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;
import com.hgtech.soma.annotation.SomaUnique;

@SomaTable
@SomaUnique(name = "by_position_key", fields = {
        "positionKey.routeId.value", "positionKey.position"
})
public final class UniquePosition {
    @SomaKey public int id;
    @SomaField public RoutePositionKey positionKey;

    public UniquePosition() {
    }
}
