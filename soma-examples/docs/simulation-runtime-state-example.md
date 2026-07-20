# 连续仿真 runtime state 示例

类型：Report / 开发者 current-executable 场景
状态：当前
Owner：`soma-examples` output
受众：使用或维护当前 Simulation runtime-state example 的开发者
适用版本：最后 implementation-affecting baseline `b991f4c`
输入事实源：当前 example source、[Simulation Blueprint](../../docs/blueprints/simulation-runtime-state-blueprint.md)、Design 与 G5 evidence
事实范围：连续仿真 data role、Access Pattern Card、schema 和使用边界
非事实范围：ODE/numerical solver、public contract 和性能 claim
最后审查日期：2026-07-20

> 本文记录当前 executable example，不拥有目标设计。目标形态与当前代码的已知差距见 [Conformance](../../docs/conformance/known-gaps.md)；文中的“必须/应当”只复述正式 Owner 或验证要求。

## 1. 文档定位

本文记录 `连续仿真 runtime state 示例` 的当前代码投影，并从 [Runtime state schema 典型示例](runtime-state-schema-examples.md) 进入其 executable context。

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

### 2.1 Access Pattern Card

| Core path | Cardinality/working set | Access/mutation mix | Allocation/evidence boundary |
|---|---|---|---|
| `StateVectorRow` | stable vector slots × step count；记录 variable-kind distribution | repeated full/partition primitive scan + non-structural update | Row/Column path 与 primitive-array baseline；touched bytes 和 steady-state allocation/op 分开 |
| pending events | queue size、due ratio、optional payload density | external min-heap scheduling；table 只承载batch ingest/diagnostic/export | heap operation、table append、swap-remove scratch 和 bitmap path 分开 |
| trace/coefficient | sample rate × variables；valve/material combinations | trace batch append/export；coefficient point lookup/preprojection | trace export/materialization 与 integration hot loop 分开；lookup load/collision/reuse 单独记录 |

Fixture/benchmark 必须补充 vector working set、scan/update ratio、event due ratio、trace sampling ratio、JIT warmup/forks、summary/diagnostic stats mode 和 final export frequency；这些值不进入 Schema/hash。

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

public enum SimVariableKind {
    LEVEL_LITERS,
    TEMPERATURE_CELSIUS,
    VALVE_OPENING_RATIO
}

@SomaValue
public class TankId {
    @SomaField
    long value;
}

@SomaValue
public class ValveId {
    @SomaField
    long value;
}

@SomaValue
public class MaterialId {
    @SomaField
    long value;
}

@SomaValue
public class ValveMaterialKey {
    @SomaField
    ValveId valveId;

    @SomaField
    MaterialId materialId;
}

@SomaTable(name = "tanks", defaultCapacity = 256)
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
public final class StateVectorRow {
    @SomaField
    public int vectorIndex;

    @SomaField
    public SimEntityKind entityKind;

    @SomaField
    public long entityId;

    @SomaField
    public SimVariableKind variableKind;

    @SomaField
    public double value;

    @SomaField
    public double derivative;

    @SomaField
    public double scale;
}

@SomaTable(name = "pending_event_rows", defaultCapacity = 1024)
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

    @SomaField
    @SomaOptional
    public Double numericPayload;
}

@SomaTable(name = "trace_sample_rows", defaultCapacity = 65536)
public final class TraceSampleRow {
    @SomaField(semantic = SomaSemantic.DATE_TIME)
    public long sampleTimeMillis;

    @SomaField
    public SimEntityKind entityKind;

    @SomaField
    public long entityId;

    @SomaField
    public SimVariableKind variableKind;

    @SomaField
    public double value;
}
```

## 4. 使用方式

- `Tank`、`Valve` 是 keyed entity state；
- `FlowCoefficient` 是 keyed lookup table；
- `StateVectorRow` 是 dense packed state vector，`vectorIndex` 只是当前向量布局位置；
- `PendingEventRow` 是 dense event batch / diagnostic workspace；真正的下一事件调度由 simulator 外部最小堆负责；
- `TraceSampleRow` 是 dense trace buffer / export buffer；
- simulator OOP 层负责数值积分、事件应用和采样策略，SOMA 不拥有仿真算法。

Source-of-truth 口径：

- canonical 示例选择 `StateVectorRow` 作为数值状态事实源；
- `StateVectorRow.entityKind + entityId + variableKind` 定义 vector slot 对应的业务变量，`vectorIndex` 仍只是当前 dense layout 位置，不是 stable key；
- `Tank.levelLiters`、`Tank.temperatureCelsius`、`Valve.openingRatio` 只作为 boundary cache / DTO / export snapshot；数值积分、事件应用和 derivative 计算应写入 `StateVectorRow`；
- simulator 只能在 step boundary、export boundary 或 diagnostic snapshot 同步这些 cache 字段；同步失败时，应停止 step、回滚外部 snapshot，或丢弃 cache 并从 `StateVectorRow` 重建；
- 如果某个项目选择 `Tank` / `Valve` 为事实源，则 `StateVectorRow` 必须降级为派生 workspace，不能和本示例的 long-lived dense source-of-truth 口径混用。

事件队列不强行建模为 SOMA Table：simulator 使用外部最小堆按 `(eventTimeMillis, sequenceNo)` 调度。只有需要批量诊断、导出或列式分析时才把 event facts 写入 `PendingEventRow`；若在表内临时筛选 due rows，使用全量列扫描和显式 `sorted(...)`，并单独计量扫描、排序与 swap-remove。

`TraceSampleRow` 是 trace / export buffer，不反向成为仿真状态事实源。需要 time/entity 顺序时只在 export / diagnostic terminal 显式排序，不应进入每 step state-vector hot path 的性能 claim。

`ColumnView` 只用于明确的 hot path primitive scan。示例默认采用读 view 关闭后再写入的两阶段模式；在 active ColumnView 下进行同 table structural mutation 应被视为 `view_pinned` 风险，除非 runtime contract 明确允许某类固定宽度非结构性更新。

`FlowCoefficient.fetch(valveMaterialKey)` 的缺失在 canonical 示例中表示 required lookup missing / 输入不完整。若 derivative inner loop 每 step 每 valve 都需要 coefficient，simulator 应考虑在 step 前预投影到 valve-local dense row 或 state vector adjacent column；SOMA 不自动 join 或自动 preprojection。
