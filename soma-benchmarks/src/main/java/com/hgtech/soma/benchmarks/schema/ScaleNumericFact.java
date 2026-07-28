package com.hgtech.soma.benchmarks.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

/**
 * Narrow production-qualification shape for primitive keyed scale lanes.
 *
 * <p>The table is intentionally narrow: the 100M evidence is bounded to this
 * schema and must not be projected to wider schemas.</p>
 */
@SomaTable(name = "scale_numeric_facts", defaultCapacity = 4096)
public final class ScaleNumericFact {
    @SomaKey public long id;
    @SomaField public int groupId;
    @SomaField public long metric;
}
