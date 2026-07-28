package com.example.soma.breadth;

import io.github.somaruntime.soma.annotation.SomaChild;
import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;

import java.util.Map;

@SomaTable(name = "string_parents")
public final class StringParent {
    @SomaField public int id;
    @SomaChild public Map<String, StringKeyRow> children;
}
