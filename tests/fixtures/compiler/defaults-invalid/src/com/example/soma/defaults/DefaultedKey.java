package com.example.soma.defaults;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class DefaultedKey {
    @SomaField @SomaDefault("7") int value;
}
