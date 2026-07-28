package com.example.soma.defaults;

import io.github.somaruntime.soma.annotation.*;

@SomaTable(name = "bad_value_key_default")
public final class BadValueKeyDefault {
    @SomaKey public DefaultedKey id;
}
