package com.hgtech.soma.examples.scheduler.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "setup_times", defaultCapacity = 4096)
public final class SetupTime {
  @SomaKey public SetupTimeKey setupKey;
  @SomaField public long setupMinutes;
}
