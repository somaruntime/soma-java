package io.github.somaruntime.benchmarks.kernel.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaValue;

@SomaValue
final class RouteKey {
    @SomaField long origin;
    @SomaField long destination;
    @SomaField String lane;
}

