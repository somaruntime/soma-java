package com.example.soma.breadth;

import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

import java.util.Map;

@SomaTable(name = "string_parents")
public final class StringParent {
    @SomaField public int id;
    @SomaChild public Map<String, StringKeyRow> children;
}
