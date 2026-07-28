package com.example.soma.compositekeyed;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class OperationKey {
    @SomaField
    long machineId;

    @SomaField
    Coordinate coordinate;

    @SomaField
    String scope;

    @SomaField
    KeyKind kind;

    @SomaField
    float lane;
}
