package com.example.invalid;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

public final class LocalValueOwner {
    public void declareLocalValue() {
        @SomaValue
        class LocalValue {
            @SomaField
            int value;
        }
        new LocalValue();
    }
}
