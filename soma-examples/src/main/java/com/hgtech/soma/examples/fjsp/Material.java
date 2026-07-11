package com.hgtech.soma.examples.fjsp;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "materials", defaultCapacity = 4096)
public final class Material {
    @SomaKey public MaterialId materialId;
    @SomaField public long readyMinute;
}
