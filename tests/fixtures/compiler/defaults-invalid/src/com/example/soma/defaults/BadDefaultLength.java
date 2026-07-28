package com.example.soma.defaults;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "bad_default_length")
public final class BadDefaultLength {
    @SomaField @SomaDefault(DefaultLiterals.X4097) public String value;
}
