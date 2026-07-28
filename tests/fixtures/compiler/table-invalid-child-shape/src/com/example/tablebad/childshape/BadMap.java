package com.example.tablebad.childshape;

import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;
import java.util.Map;

@SomaTable public final class BadMap {
    @SomaField public int id;
    @SomaChild public Map<Integer, DenseRow> denseRows;
    @SomaChild public Map<Long, KeyedRow> wrongKey;
    public BadMap() {}
}
