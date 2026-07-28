package com.example.soma.defaults;

import io.github.somaruntime.soma.annotation.*;
import java.util.List;

@SomaTable(name = "bad_child_default")
public final class BadChildDefault {
    @SomaChild @SomaDefault("empty") public List<ChildRow> children;
}
