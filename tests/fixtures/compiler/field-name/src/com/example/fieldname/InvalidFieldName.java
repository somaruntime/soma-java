package com.example.fieldname;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class InvalidFieldName {
    @SomaField(name = "bad-name")
    int value;
}
