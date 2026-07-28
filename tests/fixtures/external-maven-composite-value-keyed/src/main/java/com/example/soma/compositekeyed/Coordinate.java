package com.example.soma.compositekeyed;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class Coordinate {
    @SomaField
    int sequence;

    @SomaField
    double position;
}
