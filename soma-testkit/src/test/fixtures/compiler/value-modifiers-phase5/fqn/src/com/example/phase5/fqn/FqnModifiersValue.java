package com.example.phase5.fqn;

@com.hgtech.soma.annotation.SomaValue
public class FqnModifiersValue {
    @com.hgtech.soma.annotation.SomaField
    @com.hgtech.soma.annotation.SomaKey
    long keyed;

    @com.hgtech.soma.annotation.SomaField
    @com.hgtech.soma.annotation.SomaChild
    long child;

    @com.hgtech.soma.annotation.SomaField
    @com.hgtech.soma.annotation.SomaOptional
    Long optional;
}
