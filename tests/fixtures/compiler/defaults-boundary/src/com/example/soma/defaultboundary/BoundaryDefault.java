package com.example.soma.defaultboundary;

import io.github.somaruntime.soma.annotation.SomaDefault;
import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "boundary_default")
public final class BoundaryDefault {
    @SomaField @SomaDefault(DefaultLiterals.X4096) public String value;
    @SomaField @SomaDefault("line1\nline2\t\"quoted\"\\tail")
    public String escaped;
}
