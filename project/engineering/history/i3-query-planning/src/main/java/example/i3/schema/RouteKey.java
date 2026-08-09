package example.i3.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaValue;

@SomaValue
final class RouteKey {
    @SomaField long from;
    @SomaField long to;
}
