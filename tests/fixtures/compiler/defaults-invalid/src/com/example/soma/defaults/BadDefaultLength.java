package com.example.soma.defaults;

import io.github.somaruntime.soma.annotation.SomaDefault;
import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "bad_default_length")
public final class BadDefaultLength {
    @SomaField @SomaDefault(DefaultLiterals.X4097) public String value;
}
