package com.example.soma.access;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaIndex;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
@SomaIndex(name = "by_route", fields = {"key.routeId.value"})
public final class Visit {
    @SomaKey public RoutePositionKey key;
    @SomaField public int payload;

    public Visit() {
    }
}
