package io.github.somaruntime.soma.benchmarks.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaOptional;
import io.github.somaruntime.soma.annotation.SomaTable;

/**
 * Reference-backed String payload shape used by scale Group/Join/lifecycle
 * qualification.
 */
@SomaTable(name = "scale_string_facts", defaultCapacity = 4096)
public final class ScaleStringFact {
    @SomaField public int ordinal;
    @SomaField public String label;
    @SomaField @SomaOptional public String note;
}
