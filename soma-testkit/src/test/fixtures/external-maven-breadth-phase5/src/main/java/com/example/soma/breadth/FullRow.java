package com.example.soma.breadth;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSemantic;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "full_rows")
@SomaOrder(name = "by_score", by = @SomaSort("score"))
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
