package com.example.soma.breadth;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "string_key_rows")
public final class StringKeyRow {
    @SomaKey public String id;
    @SomaField public int value;
    @SomaField @SomaOptional public String note;
}
