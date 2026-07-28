package com.example.tablebad.childcycle;

import io.github.somaruntime.soma.annotation.SomaChild;
import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;
import java.util.List;

@SomaTable public final class A {
    @SomaField public int value;
    @SomaChild public List<B> children;
    public A() {}
}
