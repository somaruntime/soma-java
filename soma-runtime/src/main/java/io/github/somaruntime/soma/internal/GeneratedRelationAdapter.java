package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFieldEndpoint;
import io.github.somaruntime.soma.SomaKeyableField;

/** One generated Table's lightweight typed bridge into shared relation carriers. */
public interface GeneratedRelationAdapter<V, LS> {
    GeneratedTable table();
    V view();
    int keyableFieldIndex(SomaKeyableField<V, ?> field);
    int fieldIndex(SomaFieldEndpoint<V, ?> field);
    Object fieldValue(int fieldIndex);
    LS readStream(GeneratedRelation relation);
}
