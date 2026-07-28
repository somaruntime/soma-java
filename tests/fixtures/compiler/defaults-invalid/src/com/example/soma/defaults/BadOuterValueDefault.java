package com.example.soma.defaults;

import io.github.somaruntime.soma.annotation.*;

@SomaTable(name = "bad_outer_value_default")
public final class BadOuterValueDefault {
    @SomaField @SomaDefault("0,0") public Point point;
}
