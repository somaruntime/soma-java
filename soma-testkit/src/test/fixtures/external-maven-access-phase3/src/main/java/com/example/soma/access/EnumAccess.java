package com.example.soma.access;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
@SomaIndex(name = "by_state", fields = {"state"})
@SomaOrder(name = "by_state_rank", by = {
        @SomaSort("state"), @SomaSort("rank")
})
public final class EnumAccess {
    @SomaField public int id;
    @SomaField public AccessState state;
    @SomaField public int rank;

    public EnumAccess() {
    }
}
