package com.example.soma.access;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class RoutePositionKey {
    @SomaField RouteId routeId;
    @SomaField int position;
}
