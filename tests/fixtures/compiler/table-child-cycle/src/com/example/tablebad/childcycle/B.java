package com.example.tablebad.childcycle;

import io.github.somaruntime.soma.annotation.SomaChild;
import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;
import java.util.List;

@SomaTable public final class B {
    @SomaField public int value;
    @SomaChild public List<A> children;
    public B() {}
}
