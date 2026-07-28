package com.example.soma.dense;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
public final class PrimitiveSample {
    @SomaField public boolean requiredBoolean;
    @SomaField public byte requiredByte;
    @SomaField public short requiredShort;
    @SomaField public int requiredInt;
    @SomaField public long requiredLong;
    @SomaField public float requiredFloat;
    @SomaField public double requiredDouble;

    @SomaField @SomaOptional public Boolean optionalBoolean;
    @SomaField @SomaOptional public Byte optionalByte;
    @SomaField @SomaOptional public Short optionalShort;
    @SomaField @SomaOptional public Integer optionalInt;
    @SomaField @SomaOptional public Long optionalLong;
    @SomaField @SomaOptional public Float optionalFloat;
    @SomaField @SomaOptional public Double optionalDouble;

    public PrimitiveSample() {
    }
}
