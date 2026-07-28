package com.example.soma.compositekeyed;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

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
