package com.hgtech.soma.examples.fjsp;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "setup_times", defaultCapacity = 1024)
@SomaIndex(name = "by_machine_to_family", fields = {
        "setupTimeKey.machineId.value", "setupTimeKey.familyPair.toFamily.value"})
public final class SetupTime {
    @SomaKey public SetupTimeKey setupTimeKey;
    @SomaField public long setupMinutes;
}
