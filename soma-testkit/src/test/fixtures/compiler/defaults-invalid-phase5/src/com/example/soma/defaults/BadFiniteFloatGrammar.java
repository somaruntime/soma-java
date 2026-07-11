package com.example.soma.defaults;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "bad_finite_float_grammar")
public final class BadFiniteFloatGrammar {
    @SomaField @SomaDefault("0x1.0p0") public double hexadecimal;
    @SomaField @SomaDefault("1.0f") public float suffix;
    @SomaField @SomaDefault(" 1.0") public double leadingWhitespace;
    @SomaField @SomaDefault("1.0 ") public double trailingWhitespace;
}
