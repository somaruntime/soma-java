package com.example.soma.defaults;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class Point {
    @SomaField int x;
    @SomaField int y;
}
