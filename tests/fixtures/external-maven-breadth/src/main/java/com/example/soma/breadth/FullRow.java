package com.example.soma.breadth;

import io.github.somaruntime.soma.annotation.SomaDefault;
import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaOptional;
import io.github.somaruntime.soma.annotation.SomaSemantic;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "full_rows")
public final class FullRow {
    @SomaField @SomaDefault("true") public boolean active;
    @SomaField @SomaDefault("-7") public byte small;
    @SomaField @SomaDefault("9") public short medium;
    @SomaField @SomaDefault("7") public int priority;
    @SomaField @SomaDefault("1234567890123") public long total;
    @SomaField @SomaDefault("NaN") public float payload;
    @SomaField @SomaDefault("Infinity") public double ratio;
    @SomaField @SomaDefault("-0.0") public float score;
    @SomaField @SomaDefault("default") public String label;
    @SomaField @SomaDefault("READY") public State state;
    @SomaField(semantic = SomaSemantic.DATE) @SomaDefault("2026-01-02") public int day;
    @SomaField(semantic = SomaSemantic.TIME) @SomaDefault("12:34:56.123456789") public long time;
    @SomaField(semantic = SomaSemantic.DATE_TIME) @SomaDefault("2026-01-02T03:04:05Z") public long instant;
    @SomaField public String name;
    @SomaField @SomaOptional public String note;
    @SomaField @SomaOptional public State optionalState;
    @SomaField public Point point;
    @SomaField @SomaOptional public Point optionalPoint;
    @SomaField public LeafDefaults leafDefaults;
    @SomaField public ScalarValue scalar;
    @SomaField @SomaOptional public ScalarValue optionalScalar;
}
