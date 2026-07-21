package com.hgtech.soma.examples.fjsp.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "setup_times", defaultCapacity = 1024)
public final class SetupTime {
  @SomaKey public SetupTimeKey setupTimeKey;
  @SomaField public long setupMinutes;
}
