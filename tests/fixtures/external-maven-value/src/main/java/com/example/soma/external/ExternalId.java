package com.example.soma.external;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class ExternalId {
    @SomaField
    public long value;

    public static ExternalId of(long value) {
        return new ExternalId(value);
    }
}
