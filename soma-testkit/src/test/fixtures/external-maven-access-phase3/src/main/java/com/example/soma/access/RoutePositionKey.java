package com.example.soma.access;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class RoutePositionKey {
    @SomaField RouteId routeId;
    @SomaField int position;
}
