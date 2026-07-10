package com.example.fieldname;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class InvalidFieldName {
    @SomaField(name = "bad-name")
    int value;
}
