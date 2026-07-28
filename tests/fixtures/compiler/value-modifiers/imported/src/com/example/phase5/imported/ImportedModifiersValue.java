package com.example.phase5.imported;

import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class ImportedModifiersValue {
    @SomaField
    @SomaKey
    long keyed;

    @SomaField
    @SomaChild
    long child;

    @SomaField
    @SomaOptional
    Long optional;
}
