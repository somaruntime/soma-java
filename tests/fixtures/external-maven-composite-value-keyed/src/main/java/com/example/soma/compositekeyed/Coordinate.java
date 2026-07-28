package com.example.soma.compositekeyed;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class Coordinate {
    @SomaField
    int sequence;

    @SomaField
    double position;
}
