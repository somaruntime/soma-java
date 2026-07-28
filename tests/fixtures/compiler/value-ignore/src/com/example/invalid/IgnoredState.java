package com.example.invalid;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIgnore;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class IgnoredState {
    @SomaField
    int value;

    @SomaIgnore
    int cache;
}
