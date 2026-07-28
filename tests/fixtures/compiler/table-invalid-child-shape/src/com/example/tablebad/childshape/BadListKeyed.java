package com.example.tablebad.childshape;

import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;
import java.util.List;

@SomaTable public final class BadListKeyed {
    @SomaField public int id;
    @SomaChild public List<KeyedRow> rows;
    public BadListKeyed() {}
}
