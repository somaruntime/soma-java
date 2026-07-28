package com.example.soma.defaultboundary;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "boundary_default")
public final class BoundaryDefault {
    @SomaField @SomaDefault(DefaultLiterals.X4096) public String value;
}
