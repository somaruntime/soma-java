package com.example.soma.defaults;

import com.hgtech.soma.annotation.*;

@SomaTable(name = "strict_scalar_default")
@SomaOrder(name = "by_score", by = @SomaSort("score"))
public final class StrictScalarDefault {
    @SomaField @SomaDefault("NaN") public float score;
}
