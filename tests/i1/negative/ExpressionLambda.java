package com.example.soma.i1.negative;

import com.example.soma.i1.soma.WorkItemTable;
import io.github.somaruntime.soma.SomaExpression;

final class ExpressionLambda {
    private final SomaExpression<WorkItemTable.View> expression = view -> true;
}
