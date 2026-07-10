package com.example.invalid;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class GenericValue<T> {
    @SomaField
    int value;
}
