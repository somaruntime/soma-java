package io.github.somaruntime.benchmarks.kernel.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 4_096L)
final class RouteWeight {
    @SomaKey long weightId;
    @SomaIndex RouteKey route;
    @SomaField long weight;
    @SomaField boolean enabled;
}

