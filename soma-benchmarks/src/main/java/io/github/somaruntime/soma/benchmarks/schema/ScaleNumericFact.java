package io.github.somaruntime.soma.benchmarks.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

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
