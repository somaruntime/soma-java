package com.example.soma.breadth;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class LeafDefaults {
    @SomaField @SomaDefault("3") int count;
    @SomaField @SomaDefault("leaf") String label;
}
