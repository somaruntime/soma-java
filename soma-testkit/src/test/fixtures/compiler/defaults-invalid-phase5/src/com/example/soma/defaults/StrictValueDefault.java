package com.example.soma.defaults;

import com.hgtech.soma.annotation.*;

@SomaTable(name = "strict_value_default")
@SomaIndex(name = "by_value", fields = {"value.x"})
public final class StrictValueDefault {
    @SomaField public NonFiniteValue value;
}
