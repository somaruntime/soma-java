package com.example.tablebad.childshape;

import io.github.somaruntime.soma.annotation.SomaChild;
import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;
import java.util.List;

@SomaTable public final class BadListKeyed {
    @SomaField public int id;
    @SomaChild public List<KeyedRow> rows;
    public BadListKeyed() {}
}
