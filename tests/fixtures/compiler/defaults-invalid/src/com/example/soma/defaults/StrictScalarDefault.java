package com.example.soma.defaults;

import io.github.somaruntime.soma.annotation.*;

@SomaTable(name = "strict_scalar_default")
@SomaIndex(name = "by_score", fields = {"score"})
public final class StrictScalarDefault {
    @SomaField @SomaDefault("NaN") public float score;
}
