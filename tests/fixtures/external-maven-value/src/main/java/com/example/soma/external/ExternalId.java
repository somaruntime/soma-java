package com.example.soma.external;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class ExternalId {
    @SomaField
    public long value;

    public static ExternalId of(long value) {
        return new ExternalId(value);
    }
}
