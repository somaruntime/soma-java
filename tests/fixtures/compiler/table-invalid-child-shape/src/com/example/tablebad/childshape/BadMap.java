package com.example.tablebad.childshape;

import io.github.somaruntime.soma.annotation.SomaChild;
import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;
import java.util.Map;

@SomaTable public final class BadMap {
    @SomaField public int id;
    @SomaChild public Map<Integer, DenseRow> denseRows;
    @SomaChild public Map<Long, KeyedRow> wrongKey;
    public BadMap() {}
}
