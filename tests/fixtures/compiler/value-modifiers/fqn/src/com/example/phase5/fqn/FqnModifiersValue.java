package com.example.phase5.fqn;

@io.github.somaruntime.soma.annotation.SomaValue
public class FqnModifiersValue {
    @io.github.somaruntime.soma.annotation.SomaField
    @io.github.somaruntime.soma.annotation.SomaKey
    long keyed;

    @io.github.somaruntime.soma.annotation.SomaField
    @io.github.somaruntime.soma.annotation.SomaChild
    long child;

    @io.github.somaruntime.soma.annotation.SomaField
    @io.github.somaruntime.soma.annotation.SomaOptional
    Long optional;
}
