package com.example.soma.access;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;
import io.github.somaruntime.soma.annotation.SomaUnique;

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
