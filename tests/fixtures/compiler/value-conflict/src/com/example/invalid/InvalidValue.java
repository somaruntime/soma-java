package com.example.invalid;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class InvalidValue {
    @SomaField
    int value;

    @Override
    public int hashCode() {
        return value;
    }
}
