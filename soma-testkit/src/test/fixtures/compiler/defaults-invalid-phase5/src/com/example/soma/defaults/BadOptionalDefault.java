package com.example.soma.defaults;

import com.hgtech.soma.annotation.*;

@SomaTable(name = "bad_optional_default")
public final class BadOptionalDefault {
    @SomaField @SomaOptional @SomaDefault("1") public Integer value;
}
