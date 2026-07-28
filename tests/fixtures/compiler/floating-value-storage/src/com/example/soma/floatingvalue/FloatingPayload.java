package com.example.soma.floatingvalue;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class FloatingPayload {
    @SomaField float single;
    @SomaField double wide;
}
