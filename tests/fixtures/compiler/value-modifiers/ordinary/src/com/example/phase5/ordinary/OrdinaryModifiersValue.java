package com.example.phase5.ordinary;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@interface SomaKey {
}

@Target(ElementType.FIELD)
@interface SomaChild {
}

@Target(ElementType.FIELD)
@interface SomaOptional {
}

@io.github.somaruntime.soma.annotation.SomaValue
public class OrdinaryModifiersValue {
    @io.github.somaruntime.soma.annotation.SomaField
    @SomaKey
    long keyed;

    @io.github.somaruntime.soma.annotation.SomaField
    @SomaChild
    long child;

    @io.github.somaruntime.soma.annotation.SomaField
    @SomaOptional
    long optional;
}
