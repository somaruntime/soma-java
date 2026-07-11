package com.example.soma.defaults;

import com.hgtech.soma.annotation.*;

@SomaTable(name = "strict_value_default")
@SomaOrder(name = "by_value", by = @SomaSort("value.x"))
public final class StrictValueDefault {
    @SomaField public NonFiniteValue value;
}
