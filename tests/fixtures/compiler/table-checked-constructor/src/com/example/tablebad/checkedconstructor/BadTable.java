package com.example.tablebad.checkedconstructor;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;

import java.io.IOException;

@SomaTable
public final class BadTable {
    @SomaField
    public int value;

    public BadTable() throws IOException {
    }
}
