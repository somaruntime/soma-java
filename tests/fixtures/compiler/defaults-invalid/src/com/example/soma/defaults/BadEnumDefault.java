package com.example.soma.defaults;

import io.github.somaruntime.soma.annotation.*;

@SomaTable(name = "bad_enum_default")
public final class BadEnumDefault {
    @SomaField @SomaDefault("UNKNOWN") public State state;
}
