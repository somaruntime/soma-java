package io.github.somaruntime.soma.benchmarks.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaIndex;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;
import io.github.somaruntime.soma.annotation.SomaUnique;

/**
 * Bounded String Key/Unique/Index qualification shape.
 *
 * <p>This shape is deliberately separate from the 100M shared-payload shape:
 * high-cardinality String access structures have a different admission
 * envelope.</p>
 */
@SomaTable(name = "scale_string_access_facts", defaultCapacity = 4096)
@SomaUnique(name = "unique_alias", fields = {"alias"})
@SomaIndex(name = "by_bucket", fields = {"bucket"})
public final class ScaleStringAccessFact {
    @SomaKey public String id;
    @SomaField public String alias;
    @SomaField public String bucket;
    @SomaField public long metric;
}
