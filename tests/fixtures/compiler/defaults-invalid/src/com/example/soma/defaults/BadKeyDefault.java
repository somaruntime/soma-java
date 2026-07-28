package com.example.soma.defaults;

import io.github.somaruntime.soma.annotation.*;

@SomaTable(name = "bad_key_default")
public final class BadKeyDefault {
    @SomaKey @SomaDefault("1") public int id;
}
