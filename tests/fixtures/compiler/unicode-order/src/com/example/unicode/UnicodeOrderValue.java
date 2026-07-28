package com.example.unicode;

import io.github.somaruntime.soma.annotation.SomaDefault;
import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class UnicodeOrderValue {
    @SomaField(name = "\uF900") int bmpFirstByCodePoint;
    @SomaField(name = "\uD801\uDC00") int supplementarySecondByCodePoint;
    @SomaField @SomaDefault("\uD800") String isolatedHigh;
    @SomaField @SomaDefault("\uD801") String isolatedOther;
}
