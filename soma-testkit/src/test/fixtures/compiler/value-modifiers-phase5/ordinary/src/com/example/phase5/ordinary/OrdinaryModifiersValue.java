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

@com.hgtech.soma.annotation.SomaValue
public class OrdinaryModifiersValue {
    @com.hgtech.soma.annotation.SomaField
    @SomaKey
    long keyed;

    @com.hgtech.soma.annotation.SomaField
    @SomaChild
    long child;

    @com.hgtech.soma.annotation.SomaField
    @SomaOptional
    long optional;
}
