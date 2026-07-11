package com.example.tablebad.childshape;

import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;
import java.util.List;

@SomaTable public final class BadRaw {
    @SomaField public int id;
    @SuppressWarnings("rawtypes") @SomaChild public List raw;
    public BadRaw() {}
}
