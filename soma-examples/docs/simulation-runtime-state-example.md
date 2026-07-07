# 连续仿真 runtime state 示例

状态：正式设计文档
日期：2026-07-07
Owner：`soma-examples`

## 1. 文档定位

本文是 `连续仿真 runtime state 示例` 的独立场景文档。它从 [Runtime state schema 典型示例](runtime-state-schema-examples.md) 拆分而来，遵守该总览文档中的通用建模规则。

## 2. 场景边界

连续仿真示例表达 tank / valve network 的 time-step simulation runtime state：

```text
tank and valve entity state
  -> flow coefficient lookup
  -> state vector dense rows
  -> pending event dense rows
  -> trace sample dense rows
```

不表达 ODE solver 实现、数值积分策略、并行仿真调度或事件业务规则。SOMA 只承载可被 simulator hot loop 反复读取、扫描和更新的状态容器。

## 3. Schema source 示例

```java
@SomaSchema(
    name = "continuous_sim_runtime_state",
    generatedPackage = "com.example.sim.state.generated",
    version = "1"
)
package com.example.sim.state;

import com.hgtech.soma.annotation.SomaSemantic;

public enum SimEntityKind {
    TANK,
    VALVE
}

public enum SimEventKind {
    VALVE_SETPOINT,
    MATERIAL_FEED,
    SENSOR_SAMPLE
}

@SomaValue
public final class TankId {
    @SomaField
    public long value;
}

@SomaValue
public final class ValveId {
    @SomaField
    public long value;
}

@SomaValue
public final class MaterialId {
    @SomaField
    public long value;
}

@SomaValue
public final class ValveMaterialKey {
    @SomaField
    public ValveId valveId;

    @SomaField
    public MaterialId materialId;
}

@SomaTable(name = "tanks", defaultCapacity = 256)
@SomaOrder(name = "by_tank_id", by = {
    @SomaSort("tankId.value")
})
public final class Tank {
    @SomaKey
    public TankId tankId;

    @SomaField
    public MaterialId materialId;

    @SomaField
    public double levelLiters;

    @SomaField
    public double capacityLiters;

    @SomaField
    public double temperatureCelsius;

    @SomaField(semantic = SomaSemantic.DATE_TIME)
    public long lastUpdateMillis;
}

@SomaTable(name = "valves", defaultCapacity = 512)
@SomaIndex(name = "by_from_tank", fields = {"fromTank.value"})
@SomaIndex(name = "by_to_tank", fields = {"toTank.value"})
public final class Valve {
    @SomaKey
    public ValveId valveId;

    @SomaField
    public TankId fromTank;

    @SomaField
    public TankId toTank;

    @SomaField
    public double openingRatio;

    @SomaField
    public double maxFlowLitersPerSecond;

    @SomaField
    @SomaDefault("true")
    public boolean enabled;
}

@SomaTable(name = "flow_coefficients", defaultCapacity = 1024)
public final class FlowCoefficient {
    @SomaKey
    public ValveMaterialKey valveMaterialKey;

    @SomaField
    public double coefficient;
}

@SomaTable(name = "state_vector_rows", defaultCapacity = 4096)
@SomaOrder(name = "by_vector_index", by = {
    @SomaSort("vectorIndex")
})
public final class StateVectorRow {
    @SomaField
    public int vectorIndex;

    @SomaField
    public SimEntityKind entityKind;

    @SomaField
    public long entityId;

    @SomaField
    public double value;

    @SomaField
    public double derivative;

    @SomaField
    public double scale;
}

@SomaTable(name = "pending_event_rows", defaultCapacity = 1024)
@SomaOrder(name = "by_event_time", by = {
    @SomaSort("eventTimeMillis"),
    @SomaSort("sequenceNo")
})
public final class PendingEventRow {
    @SomaField(semantic = SomaSemantic.DATE_TIME)
    public long eventTimeMillis;

    @SomaField
    public long sequenceNo;

    @SomaField
    public SimEventKind eventKind;

    @SomaField
    public SimEntityKind targetKind;

    @SomaField
    public long targetId;

    @SomaOptional
    public Double numericPayload;
}

@SomaTable(name = "trace_sample_rows", defaultCapacity = 65536)
@SomaOrder(name = "by_time_entity", by = {
    @SomaSort("sampleTimeMillis"),
    @SomaSort("entityKind"),
    @SomaSort("entityId")
})
public final class TraceSampleRow {
    @SomaField(semantic = SomaSemantic.DATE_TIME)
    public long sampleTimeMillis;

    @SomaField
    public SimEntityKind entityKind;

    @SomaField
    public long entityId;

    @SomaField
    public double value;
}
```

## 4. 使用方式

- `Tank`、`Valve` 是 keyed entity state；
- `FlowCoefficient` 是 keyed lookup table；
- `StateVectorRow` 是 dense packed state vector，`vectorIndex` 只是当前向量布局位置；
- `PendingEventRow` 是 dense event queue workspace，通过 `by_event_time` 取下一批事件；
- `TraceSampleRow` 是 dense trace buffer / export buffer；
- simulator OOP 层负责数值积分、事件应用和采样策略，SOMA 不拥有仿真算法。
