package com.example.unicode;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class UnicodeOrderValue {
    @SomaField(name = "\uF900") int bmpFirstByCodePoint;
    @SomaField(name = "\uD801\uDC00") int supplementarySecondByCodePoint;
    @SomaField @SomaDefault("\uD800") String isolatedHigh;
    @SomaField @SomaDefault("\uD801") String isolatedOther;
}
