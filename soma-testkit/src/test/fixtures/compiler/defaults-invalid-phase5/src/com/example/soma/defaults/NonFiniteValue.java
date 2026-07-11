package com.example.soma.defaults;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class NonFiniteValue {
    @SomaField @SomaDefault("Infinity") float x;
}
