# 连续仿真 runtime state 临时蓝图

状态：临时蓝图
日期：2026-07-07
适用范围：`soma_java` Java-only V1 设计讨论

## 1. 目标与适用范围

本文基于 `soma-examples/docs/simulation-runtime-state-example.md`，审视 tank / valve network 连续仿真场景下 SOMA runtime state 的建模方式。

连续仿真和 FJSP / VRP 的主要差异是：核心 hot path 不是候选 frontier 选择，而是 state vector 的重复扫描更新、event queue 的时间有序消费，以及 trace buffer 的批量追加和导出。

适用边界：

- 只讨论 Java 8 generated table / Row Pipeline / ColumnView 使用方式；
- 只讨论仿真运行期间的 runtime state，不讨论 ODE solver、数值积分策略、并行调度或事件业务规则；
- SOMA 保存 hot runtime data plane，simulator OOP 层拥有物理模型、事件语义、采样策略和跨 table 一致性；
- 本文是临时蓝图，不是正式 schema contract。

## 2. 当前示例中的 runtime tables

现有正式示例包含以下 table：

| Table | 当前形态 | 生命周期判断 | 主要访问方式 |
|---|---|---|---|
| `Tank` | keyed entity state | 长期 runtime entity state | `fetch(tankId)`、`mutate(tankId)`、`by_tank_id` |
| `Valve` | keyed entity state | 长期 runtime entity state | `fetch(valveId)`、`by_from_tank`、`by_to_tank` |
| `FlowCoefficient` | keyed lookup data | 导入后只读 lookup | `fetch(valveMaterialKey)` |
| `StateVectorRow` | dense long-lived state | packed state vector | `by_vector_index()`、ColumnView / row-index scan |
| `PendingEventRow` | dense event queue state | 跨 step 待处理事件队列 | `by_event_time()` |
| `TraceSampleRow` | dense trace / export buffer | 采样输出缓冲 | append / batch export / `by_time_entity()` |

`StateVectorRow.vectorIndex` 是当前向量布局位置，不是 stable logical key。`PendingEventRow.sequenceNo` 是 event ordering tie-breaker，也不是 entity identity。`TraceSampleRow` 是输出记录，不应成为仿真状态事实源。

## 3. 主 hot loop 拆解

典型 time-step simulation loop 可以拆成：

```text
initialize tanks / valves / flow_coefficients
  -> initialize state_vector_rows
  -> initialize pending_event_rows
  -> for each simulation step:
       consume due events by event time
       apply events to entity state or state vector
       compute derivatives
       integrate state vector
       sync selected entity state if needed
       append trace samples by sampling policy
       publish DTO/export only at boundary
```

该 hot loop 中有三类不同的数据压力：

- `StateVectorRow`：高频、密集、重复扫描和 primitive update；
- `PendingEventRow`：按 event time 有序读取，消费后 structural remove 或批量 compact；
- `TraceSampleRow`：高频 append，但通常不参与下一步计算。

如果每个 step 都执行：

```java
stateVectorRows.replaceAll(buildStateVectorFromTanksAndValves());
```

这会把实体状态读取、向量布局重建、DTO/builder 构造和 order sidecar dirty/rebuild 混在一起，破坏 state vector 作为 long-lived dense state 的意义。

更好的方向是：state vector 初始化一次，仿真过程中以 row-index / ColumnView / Row Pipeline update 原地更新；只有向量结构发生变化时才重建。

## 4. 生命周期判断

### 4.1 `StateVectorRow`

`StateVectorRow` 应是 dense long-lived state：

- `vectorIndex` 是 packed vector slot；
- `value`、`derivative`、`scale` 是 hot loop 读写字段；
- 初始化后 layout 通常保持稳定；
- 适合 ColumnView 或 generated primitive cursor 访问；
- 不适合 keyed table，因为每步主要是连续扫描而不是按 key lookup。

关键风险是它可能和 `Tank.levelLiters`、`Tank.temperatureCelsius`、`Valve.openingRatio` 形成双事实源。该蓝图采用明确的 source-of-truth 选择：

- `StateVectorRow` 是数值状态 source-of-truth；
- `Tank.levelLiters`、`Tank.temperatureCelsius`、`Valve.openingRatio` 只作为边界观测 / DTO / export cache；
- 数值积分、事件应用和 derivative 计算只写 `StateVectorRow`；
- `Tank` 对应字段只能在 simulator 定义的同步点写入，例如 step boundary、export boundary 或 diagnostic snapshot；
- 如果同步失败，simulator 必须停止本 step、回滚到外部 snapshot，或丢弃 cache 并从 `StateVectorRow` 重建，不能让 SOMA runtime 猜测哪边权威。

如果某个项目坚持 `Tank` 是事实源，则 `StateVectorRow` 必须降级为派生 workspace，且不能跨 step 保存未同步值；这属于另一条场景，不应混入本文蓝图。

### 4.2 `PendingEventRow`

`PendingEventRow` 是 dense event queue workspace：

- row 没有 stable logical key；
- event 按 `(eventTimeMillis, sequenceNo)` 排序消费；
- 新事件可批量 append；
- due events 被消费后 remove 或 compact；
- 不是事件历史，历史应进入 trace / log / export，不保留在 pending queue。

这里和 FJSP dispatch 不同：`by_event_time` 是稳定 ordered access path，不是临时策略排序。使用 maintained `@SomaOrder` 是合理的，但它不是 heap、priority queue 或 range-pop API。`byEventTime().filter(...).forEach(...)` 后再 `byEventTime().filter(...).remove()` 是 two-terminal 写法，可能每 step 扫描 pending queue 两次；queue size、due ratio、remove/compact 和 order sidecar dirty/rebuild 必须进入 benchmark。

### 4.3 `TraceSampleRow`

`TraceSampleRow` 是 dense trace buffer / export buffer：

- 主要写入方向是 append；
- 主要读取方向是导出、诊断、可视化；
- 不应在主仿真计算中频繁反查；
- 高频采样时维护 `by_time_entity` order sidecar 可能带来写放大。

本蓝图选择保留 `TraceSampleRow.by_time_entity`，但只用于 export / diagnostic terminal。Trace 写入可以按 chunk 或 step batch 追加；`by_time_entity` 的 lazy rebuild 成本只应在导出或诊断阶段支付并计入 stats，不能进入每步 state-vector hot path。如果未来 benchmark 证明该 order 对高频 trace 写入过重，应改成 chunk export 或 export boundary dynamic sort，而不是在主循环中实时维护。

## 5. 潜在瓶颈识别

主要风险：

- **state vector rebuild**：每步重建 dense table 会破坏 cache locality 和 capacity reuse；
- **DTO materialization 泄漏到 hot loop**：每步大量 `fetch(tankId)` / detached DTO 会把 columnar hot path 退化成 object graph；
- **FlowCoefficient 随机 lookup 热点**：按 valve/material 每步 `fetch` 可能成为随机访问瓶颈；
- **event queue remove 频繁触发 sidecar dirty**：逐条 remove due event 可能比批量消费/compact 更昂贵；
- **trace order 成本口径**：append trace 后 order sidecar 可能 dirty；`by_time_entity` 的 lazy rebuild 只应在 export / diagnostic terminal 支付，不能混入主循环 claim；
- **双事实源 drift**：`Tank` 与 `StateVectorRow` 如果都可被写，会产生状态漂移；
- **ColumnView lifecycle 冲突**：active ColumnView 下做 structural mutation 可能返回 `view_pinned` 类错误。
- **preprojection 缺失**：`FlowCoefficient.fetch` 如果在 derivative inner loop 中重复执行，应预投影到 valve-local dense row 或 state vector adjacent column，否则 keyed lookup 成本会被物理模型计算掩盖。

## 6. 推荐的 schema annotation 草案

当前正式示例方向基本正确，但蓝图建议强化 state vector ownership，并给 `StateVectorRow` 增加稳定的 entity mapping 字段，用于边界同步，而不是用于 keyed lookup。

```java
@SomaSchema(
    name = "continuous_sim_runtime_state",
    generatedPackage = "com.example.sim.state.generated",
    version = "1"
)
package com.example.sim.state;

public enum SimVariableKind {
    LEVEL_LITERS,
    TEMPERATURE_CELSIUS,
    VALVE_OPENING_RATIO
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
    public SimVariableKind variableKind;

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
    public SimVariableKind variableKind;

    @SomaField
    public double value;
}
```

注意：

- `variableKind` 使用 enum，避免把可读 `String` 放进 hot schema；可读名称应放到低频 mapping table 或导出层；
- `StateVectorRow` 不声明 key，`vectorIndex` 仍不是 stable business identity；
- `PendingEventRow.by_event_time` 是稳定事件队列访问路径，可以保留 `@SomaOrder`；
- `TraceSampleRow.by_time_entity` 被保留为 export / diagnostic order；它的 lazy rebuild 成本不得混入 step hot path claim。

## 7. 推荐的 Java 8 SOMA API 使用流程

### 7.1 初始化

```java
void initializeSimulation(SimulationInput input) {
    tanks.addBatch(buildTankBatch(input));
    valves.addBatch(buildValveBatch(input));
    flowCoefficients.addBatch(buildFlowCoefficientBatch(input));

    stateVectorRows.replaceAll(buildInitialStateVector(input));
    pendingEventRows.replaceAll(buildInitialEvents(input));
    traceSampleRows.clear();
}
```

### 7.2 消费 due events

```java
void consumeDueEvents(long stepEndMillis) {
    pendingEventRows.byEventTime()
        .filter(e -> e.eventTimeMillis() <= stepEndMillis)
        .forEach(e -> applyEvent(e));

    pendingEventRows.byEventTime()
        .filter(e -> e.eventTimeMillis() <= stepEndMillis)
        .remove();
}
```

这里故意分成两个 terminal。Row Pipeline 是 one-shot，且 callback 内不应对同一 table 做 structural mutation。事件应用如果会修改 `Tank`、`Valve` 或 `StateVectorRow`，由 simulator 保证不会形成跨 table mutation cycle。

该写法不是 heap pop，也不是 prefix range remove。大队列、少量 due event、频繁新增事件时，two-terminal scan 和 remove/compact 成本可能成为热点；benchmark 必须覆盖 queue size、due ratio、order sidecar dirty/rebuild 和 remove stats。

### 7.3 更新 state vector

```java
void integrateStep(double dtSeconds) {
    stateVectorRows.byVectorIndex()
        .update(s -> {
            double next = s.value() + s.derivative() * dtSeconds / s.scale();
            s.setValue(next);
        });
}
```

如果数值内核极端 hot，可以使用 generated ColumnView 做只读 primitive scan，再在释放 view 后进入写入阶段：

```java
int size = stateVectorRows.size();
double[] nextValues = new double[size];

try (DoubleColumnView values = stateVectorRows.valueColumn();
     DoubleColumnView derivatives = stateVectorRows.derivativeColumn();
     DoubleColumnView scales = stateVectorRows.scaleColumn()) {
    for (int row = 0; row < size; row++) {
        nextValues[row] = values.getDouble(row)
            + derivatives.getDouble(row) * dtSeconds / scales.getDouble(row);
    }
}

for (int row = 0; row < size; row++) {
    stateVectorRows.mutateAt(row)
        .setValue(nextValues[row])
        .commit();
}
```

该两阶段写法避免在 active ColumnView 下同表写入。只有 runtime contract 明确允许 active view 下 fixed-width non-structural update 时，才可以把读写合并到同一 view scope；否则应使用 Row Pipeline update cursor 或上面的 scratch 分阶段方案。

### 7.4 追加 trace samples

```java
void sampleTrace(long sampleTimeMillis) {
    TraceSampleBatch batch = traceSampleRows.newBatch();

    stateVectorRows.byVectorIndex()
        .forEach(s -> batch.add()
            .setSampleTimeMillis(sampleTimeMillis)
            .setEntityKind(s.entityKind())
            .setEntityId(s.entityId())
            .setVariableKind(s.variableKind())
            .setValue(s.value()));

    traceSampleRows.addBatch(batch);
}
```

Trace 写入应按采样周期批量发生，不应在每个 primitive update 后逐行 append。

## 8. Cache 友好性分析

该场景的 cache 友好性主要来自：

- `StateVectorRow` 是 dense packed table，适合连续 scan；
- primitive field 通过 Row Pipeline cursor 或 ColumnView 读取，避免 per-row DTO allocation；
- event queue 和 trace buffer 使用 dense rows，结构简单；
- `FlowCoefficient` 作为 keyed lookup 与 topology/entity state 分离。

主要削弱因素：

- 若每步 `fetch(tankId)` materialize DTO，会破坏 columnar hot path；
- 若每步 `replaceAll(stateVectorBatch)`，order sidecar 和 storage rewrite 会成为不必要成本；
- 若 trace 和 state vector 放在同一 hot loop path，trace append / order dirty 可能污染 cache；
- 若 `FlowCoefficient.fetch` 在每个 valve 的每个 step 随机触发，可能需要把 coefficients 预投影到 valve-local dense rows 或 state vector adjacent columns。

因此蓝图建议：

- state vector 初始化后原地更新；
- due events 批量消费和批量 remove；
- trace buffer 与 state vector 计算路径分离；
- required lookup 在 step 前尽量预绑定或缓存到 dense row，不在 inner loop 重复 random lookup。

`FlowCoefficient` 的推荐口径是：低频 topology 初始化或 material 变化时可以 keyed `fetch`；如果 derivative inner loop 每 step 每 valve 都需要 coefficient，应预投影到 valve-local dense row、state vector adjacent column，或其他连续 layout。benchmark 需要单独比较 keyed lookup、预投影 dense row 和 ColumnView primitive scan。

## 9. SOMA V1 可能暴露的问题

- Dense table 需要被正式呈现为 long-lived state，不只是 `replaceAll` workspace；
- Row Pipeline update 是否足够表达高频 primitive vector update，需要和 ColumnView lane 做 benchmark 对比；
- active ColumnView 与 mutation 的关系需要在示例中讲清，否则用户容易写出 view-pinned 的 hot loop；
- event queue 的 ordered access 很自然，但 SOMA `@SomaOrder` 不是 heap，不能宣称插入/删除复杂度等价于专用 priority queue；
- trace buffer 高频追加时，order sidecar dirty/rebuild 成本可能暴露 runtime stats 不足；
- `StateVectorRow` 是本文采用的数值状态 source-of-truth，`Tank` 对应字段只是同步 cache；同步点和失败恢复由 simulator 定义；
- `FlowCoefficient` inner-loop lookup 是否需要 preprojection 必须用 benchmark 证明。

## 10. 自审结论

### 10.1 通过项

- 没有把连续仿真误建模成 candidate frontier；
- `StateVectorRow` 保持 dense long-lived state，符合 packed scan 和 primitive update；
- 明确 `StateVectorRow` 是数值状态 source-of-truth，`Tank` 对应字段是边界 cache；
- `PendingEventRow.by_event_time` 作为稳定 event queue order 保留，区别于策略 dynamic sort；
- `TraceSampleRow` 被定位为 export/diagnostic buffer，不反向成为状态事实源；
- 明确了 simulator OOP 层拥有数值模型、事件规则和跨 table 一致性。

### 10.2 风险和坏味道

- `SimVariableKind` 若未来不够表达业务变量，应改成 numeric `variableId` 或 value key，而不是回退到 hot path String；
- ColumnView hot path 必须保持只读 view 与写入阶段分离，除非 runtime contract 明确允许；
- `Tank` cache 与 `StateVectorRow` 同步失败会导致 drift，必须有 simulator-level 恢复策略；
- `PendingEventRow` two-terminal consume/remove 在大队列低 due ratio 下可能成本较高；
- `TraceSampleRow.by_time_entity` 只适合作为 export/diagnostic order，不应进入每步主循环。

### 10.3 待验证事项

- State vector Row Pipeline update 与 ColumnView / primitive loop 的性能分界；
- queue size、due ratio、due event remove/compact、order sidecar dirty/rebuild 成本；
- trace buffer append/export order 是否需要 chunk table、分段 export 或 export boundary dynamic sort；
- `FlowCoefficient` preprojection：keyed lookup、valve-local dense row、state vector adjacent column 和 ColumnView primitive scan 的对比；
- state vector 和 entity table 的同步周期及错误恢复策略。

### 10.4 当前判定

该蓝图已按审查结论修正后保留为临时蓝图。正式示例需要同步 `StateVectorRow` source-of-truth、变量维度、event queue order 语义、trace buffer 隔离和 ColumnView `view_pinned` 边界；后续建议进入 benchmark 的 lane 包括：`StateVectorRow` update、`PendingEventRow` due consume/remove、`TraceSampleRow` append/export order、`FlowCoefficient` preprojection。
