package com.example.soma.breadth;

import io.github.somaruntime.soma.annotation.SomaDefault;
import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class LeafDefaults {
    @SomaField @SomaDefault("3") int count;
    @SomaField @SomaDefault("leaf") String label;
}
