package com.hgtech.soma.benchmarks.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;
import com.hgtech.soma.annotation.SomaUnique;

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
