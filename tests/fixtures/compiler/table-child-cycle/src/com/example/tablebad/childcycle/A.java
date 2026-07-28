package com.example.tablebad.childcycle;

import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;
import java.util.List;

@SomaTable public final class A {
    @SomaField public int value;
    @SomaChild public List<B> children;
    public A() {}
}
