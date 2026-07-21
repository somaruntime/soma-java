# 连续仿真 runtime state 蓝图

类型：Blueprint

状态：正式

Owner：连续仿真目标场景

事实范围：dense 数值状态、外部事件堆、trace 与导出路径的目标使用形态

非事实范围：积分算法、物理模型、精确公共 API、当前实现状态和性能结论

设计约束入口：[Schema 与生成 API](../design/schema-and-generated-api.md)、[Table、存储与访问](../design/table-storage-and-access.md)、[Materialization 边界](../design/materialization-boundary.md)、[Correctness 与 failure](../design/correctness-and-failure.md)、[性能模型](../design/performance-model.md)

最后审查日期：2026-07-21

目标约束：真正的 event queue 由 simulator-owned min-heap 按 `(simulationTimeNanos, sequenceNo)` 维护。`PendingEventRow` 只承担 batch ingest、诊断、导出或列式分析；Table 内需要顺序时显式 `.sorted(totalComparator)`，物理遍历顺序不构成业务契约。

## 1. 目标与适用范围

本文展示 tank / valve network 连续仿真场景下 SOMA runtime state 的目标建模方式。

连续仿真和 FJSP / VRP 的主要差异是：核心 hot path 不是候选 frontier 选择，而是 state vector 的重复扫描更新、外部 event heap 的时间有序消费，以及 trace buffer 的批量追加和导出。

适用边界：

- 只讨论 Java 8 generated table / Row Pipeline / ColumnView 使用方式；
- 只讨论仿真运行期间的 runtime state，不讨论 ODE solver、数值积分策略、并行调度或事件业务规则；
- SOMA 保存 hot runtime data plane，simulator OOP 层拥有物理模型、事件语义、采样策略和跨 table 一致性；
- 本文定义目标使用形态，不是精确 schema/API contract；未标为算法伪代码的片段按目标 Java 8 使用代码审查。示例采用从 session origin 起算的 non-negative `simulationTimeNanos`，不把仿真时钟冒充 wall-clock `DATE_TIME`；具体积分器可以替换，但事件顺序、状态事实源、资源复用和失败边界必须保持明确。

本蓝图中的 `@SomaTable` class 同时定义 row schema 与 detached single-row materialization shape，但不是 live runtime storage。Materializing API/terminal 返回 schema class 或 `List`/`Map`，Row Pipeline callback 参数仍是 callback-scoped Row Cursor。SOMA ownership aggregate 只允许单线程同步访问，不提供并发访问、跨 table transaction、序列化或持久化；trace/export 只是 runtime buffer 和外部 adapter boundary。

## 2. 目标数据角色与 Table 形态

| Table / structure | 目标形态 | 生命周期责任 | 主要访问方式 |
|---|---|---|---|
| `TankDefinition` | keyed input fact | import 后 authoritative、read-only | `fetch(tankId)`、topology lookup |
| `ValveDefinition` | keyed input fact | import 后 authoritative、read-only | `fetch(valveId)`、`by_from_tank`、`by_to_tank` |
| `FlowCoefficient` | keyed lookup data | 导入后只读 lookup | `fetch(valveMaterialKey)` |
| `StateVectorRow` | dense long-lived state | packed state vector | physical scan、explicit sort、ColumnView / Index scan |
| application event heap | application-owned priority queue | 尚未消费事件的唯一 queue state | `(simulationTimeNanos, sequenceNo)` push/pop |
| `PendingEventRow` | 可选 dense event projection | ingest、诊断或导出边界 | batch append / explicit filter-sort / remove |
| `TraceSampleRow` | dense trace / export buffer | 采样输出缓冲 | append / boundary explicit sort / batch export |

`StateVectorRow.vectorIndex` 是当前向量布局位置，不是 stable logical key。`PendingEventRow.sequenceNo` 是 event ordering tie-breaker，也不是 entity identity。`TraceSampleRow` 是输出记录，不应成为仿真状态事实源。

### 2.1 Application data role 边界

| Data role | Table / field group | 权威性与生命周期 | 优化判断 |
|---|---|---|---|
| input facts | `TankDefinition`、`ValveDefinition`、`FlowCoefficient`、initial conditions | import 后 authoritative、read-only/read-mostly | topology/parameter 与 numeric working state 分开 |
| working state | `StateVectorRow` | simulation 期间 authoritative numeric state | long-lived dense state；不在 entity table 再维护 authoritative numeric copy |
| working state | application min-heap | 当前尚未消费事件的 authoritative queue state | SOMA Table 不承担 priority queue；`PendingEventRow` 只是可丢弃的 ingest/diagnostic projection |
| result facts | `TraceSampleRow` | sampling policy 产生的 authoritative sampled trace | 只服务 export/diagnostic，不反向驱动积分状态 |
| result facts | final state projection | 由最终 `StateVectorRow` 直接 materialize/map | 不维护内容相同的 `FinalState` shadow table |

本场景最适合三段式分离。推荐将 `Tank` / `Valve` 的 topology、material、capacity 等稳定字段明确为 input definition；数值 `level/temperature/opening` 只在 `StateVectorRow` 权威维护；trace/final output 从 state vector 显式导出。若为了低频 UI/debug 保留 entity numeric cache，它只能是 derived cache，并需要明确同步点和可丢弃重建语义，不能成为第四份结果事实。

### 2.2 Access Pattern Card

以下 card 是 simulation runtime plan 输入，不进入 Schema/hash。实际 vector size、step count、event/trace density 和 working-set bytes 必须由 fixture/benchmark 明确。

| Table / phase | Rows/cardinality | Hot columns | Access / mutation mix | Locality / allocation boundary |
|---|---|---|---|---|
| `StateVectorRow` | stable vector slots；记录 total rows 与 variable-kind distribution | `value`、`derivative`、`scale`，mapping fields只在 boundary 使用 | 每 step full/partition sequential scan + non-structural update | Row/Column path 与 primitive arrays 对照；记录 touched bytes、allocation/op、layout stability 和 stats overhead |
| application event heap / `PendingEventRow` projection | queue size、due-event ratio | event time、sequence、kind、target、optional payload | heap push/pop；必要时 batch project + table scan/sort/remove | heap 与 Table projection 分开计量；记录 due selectivity、swap-remove scratch 和 optional density |
| `TraceSampleRow` | sample rate × observed variables × steps | time/entity/variable/value | batch append；低频 explicit sorted export | append 与 export sort/materialization 分离；记录 capacity high-water、GC/reference cost |
| `FlowCoefficient` | valve/material combinations | key leaves、coefficient | derivative phase repeated point lookup or preprojected dense access | 记录 load/collision、random lookup count、projection build/reuse；inner loop 不隐藏 lookup |
| definition tables | tank/valve/topology count | phase-specific stable leaves | initialization/low-frequency lookup | 与 state vector working set 分开；不在每 step materialize definition object |

Benchmark 必须记录 step count、scan/update ratio、variable distribution、optional density、event due ratio、trace sampling ratio、JIT warmup/forks、summary/diagnostic stats mode 和 final export frequency。

## 3. 主 hot loop 拆解

典型 time-step simulation loop 可以拆成：

```text
initialize tanks / valves / flow_coefficients
  -> initialize state_vector_rows
  -> initialize application event heap
  -> for each simulation step:
       consume due events by event time
       apply events to entity state or state vector
       compute derivatives
       integrate state vector
       project selected entity observation only if needed
       append trace samples by sampling policy
       materialize schema object/List / map external DTO only at boundary
```

该 hot loop 中有三类不同的数据压力：

- `StateVectorRow`：高频、密集、重复扫描和 primitive update；
- application event heap：按 event time/sequence 有序 pop；可选 `PendingEventRow` 只做 batch projection；
- `TraceSampleRow`：高频 append，但通常不参与下一步计算。

如果每个 step 都执行：

```java
stateVectorRows.replaceAll(buildStateVectorFromTanksAndValves());
```

这会把实体状态读取、向量布局重建和 Batch/builder 构造混在一起，破坏 state vector 作为 long-lived dense state 的意义。

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
- `TankDefinition` / `ValveDefinition` 不保存 `levelLiters`、`temperatureCelsius`、`openingRatio` 等可写 numeric shadow；boundary mapper 通过 state-vector entity mapping 构造 DTO；
- 如果 application 为低频观察保留 numeric cache，它只能是可丢弃的 derived export cache，不是 result fact 或第二 source-of-truth；
- 数值积分、事件应用和 derivative 计算只写 `StateVectorRow`；
- 对应 cache 字段只能在 simulator 定义的同步点写入，例如 step boundary、export boundary 或 diagnostic snapshot；
- 如果同步失败，simulator 必须停止本 step、回滚到外部 snapshot，或丢弃 cache 并从 `StateVectorRow` 重建，不能让 SOMA runtime 猜测哪边权威。

如果某个项目坚持 `Tank` 是事实源，则 `StateVectorRow` 必须降级为派生 workspace，且不能跨 step 保存未同步值；这属于另一条场景，不应混入本文蓝图。

### 4.2 `PendingEventRow`

`PendingEventRow` 不是 canonical event queue，而是 dense event batch / diagnostic workspace：

- row 没有 stable logical key；
- application min-heap 按 `(simulationTimeNanos, sequenceNo)` push/pop；
- 只有 batch ingest、诊断、导出或列式分析需要时才投影进 Table；
- Table 内临时 due-row 处理使用 scan/filter、显式 sort 与 swap-remove；
- event history 进入 trace / log / export，不与 pending heap 或 projection 混用。

如果某个 benchmark 刻意比较 Table-only 方案，必须明确它不是 SOMA 推荐的 priority queue 实现，并把全量 scan、dynamic sort、two-terminal remove 和 heap push/pop 分开计量。

### 4.3 `TraceSampleRow`

`TraceSampleRow` 是 dense trace buffer / export buffer：

- 主要写入方向是 append；
- 主要读取方向是导出、诊断、可视化；
- 不应在主仿真计算中频繁反查；
- 高频采样时每步排序会带来不必要写后读成本。

Trace 写入按 chunk 或 step batch 追加；需要 `(time, entity, variable)` 顺序时，只在 export / diagnostic boundary 执行显式 sort。该排序成本不得混入每步 state-vector hot path；数据量过大时使用 chunk export 或外部归并。

### 4.4 浮点异常值与业务有效性

`StateVectorRow.value`、`derivative`、`scale` 和 `TraceSampleRow.value` 都是普通 floating payload，不参与 key/index/unique。SOMA 因而允许 Java IEEE-754 的 `NaN`、positive/negative infinity 和 negative zero；这些值不是 optional absence，absence 只能由 presence bitmap 表达。

物理模型通常需要更严格的业务约束，例如：

- state value / derivative 必须 finite；
- scale 必须 finite 且不为零；
- 某些变量必须非负或位于稳定区间；
- integration 产生 NaN/Infinity 时必须停止 step、恢复外部 snapshot，或写入明确 diagnostic outcome。

这些约束由 loader/simulator/application 负责，SOMA runtime 不把普通 payload 的 NaN/Infinity 自动解释为错误、缺失或终止信号。蓝图的 canonical simulation lane 应在 step 前后显式验证 finite/scale invariant，并把失败与 runtime typed error 分开记录。

### 4.5 为什么 state/event/trace 不按 entity 拆成 child

`StateVectorRow` 的核心访问模式是跨实体连续 vector scan，把它拆成 `Tank` / `Valve` 的 per-entity child 会破坏统一 layout 和 primitive locality。Event heap / `PendingEventRow` projection 由 simulation timeline 拥有，可能跨多个 target entity；`TraceSampleRow` 由 sampling/export phase 拥有，也不是任一 entity row 的独占 subtree。

因此 Table 形态的 state、event projection 和 trace 继续作为 root-level dense table。只有 simulation session 本身被明确建模为 parent row，并且整个 aggregate 的 lifecycle、key-scoped live child access 和 deep materialization boundary 都已正式设计时，才评估 session-owned child；不得为了表达对象层级而牺牲全局 hot scan。

## 5. 场景对 Design 的压力

主要风险：

- **state vector rebuild**：每步重建 dense table 会破坏 cache locality 和 capacity reuse；
- **Object materialization 泄漏到 hot loop**：每步大量 `fetch(tankId)` / detached schema object 会把 columnar hot path 退化成 object graph；
- **FlowCoefficient 随机 lookup 热点**：按 valve/material 每步 `fetch` 可能成为随机访问瓶颈；
- **把 Table 当 priority queue**：大队列低 due ratio 下的全量 scan/sort/remove 会掩盖专用 heap 的优势；
- **trace order 成本口径**：explicit boundary sort 只应在 export / diagnostic terminal 支付，不能混入主循环 claim；
- **双事实源 drift**：`Tank` 与 `StateVectorRow` 如果都可被写，会产生状态漂移；
- **ColumnView lifecycle 冲突**：active ColumnView 下做 structural mutation 可能返回 `view_pinned` 类错误；
- **preprojection 缺失**：`FlowCoefficient.fetch` 如果在 derivative inner loop 中重复执行，应预投影到 valve-local dense row 或 state vector adjacent column，否则 keyed lookup 成本会被物理模型计算掩盖。

## 6. 推荐的 schema annotation 草案

目标 schema 强化 state vector ownership，并给 `StateVectorRow` 增加稳定的 entity mapping 字段，用于边界同步，而不是用于 keyed lookup。

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
    @SomaField
    public long simulationTimeNanos;

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
    @SomaField
    public long sampleTimeNanos;

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
- application min-heap 是 pending event queue 的唯一推荐调度结构；
- `PendingEventRow` / `TraceSampleRow` 需要业务顺序时在 diagnostic/export boundary 显式排序。

## 7. 推荐的 Java 8 SOMA API 使用流程

### 7.1 初始化

```java
void initializeSimulation(SimulationInput input) {
    tankDefinitions.addBatch(buildTankDefinitionBatch(input));
    valveDefinitions.addBatch(buildValveDefinitionBatch(input));
    flowCoefficients.addBatch(buildFlowCoefficientBatch(input));

    stateVectorRows.replaceAll(buildInitialStateVector(input));
    validateDenseVectorLayout(stateVectorRows);
    integrationWorkspace.ensureCapacity(stateVectorRows.size());

    eventHeap.clear();
    eventHeap.addAll(buildImmutableEvents(input));
    if (eventProjectionEnabled) {
        pendingEventRows.replaceAll(projectEvents(eventHeap));
    } else {
        pendingEventRows.clear();
    }
    traceSampleRows.clear();
}
```

`eventHeap` 是 pending event 的唯一事实源。`PendingEventRow` 只在显式启用 projection 时从 heap 重建，不能被 event loop 反向消费成第二个 queue。Java 8 `PriorityQueue` 的 iterator 不保证 priority order，因此 `projectEvents(...)` 只能做无序 projection；需要有序诊断输出时复制后显式排序，不能靠遍历 heap 得到时间序。`validateDenseVectorLayout` 验证 `vectorIndex` 唯一、连续且覆盖 `[0,size)`，并验证 `(entityKind, entityId, variableKind)` mapping 唯一；进入 time loop 后不得对 state-vector table 做 structural mutation，除非先结束当前 run、重建 layout 与所有 application scratch。

### 7.2 按事件边界推进 simulation clock

```java
void advanceTo(long targetTimeNanos) {
    if (targetTimeNanos < currentTimeNanos) {
        throw new IllegalArgumentException("simulation time cannot move backwards");
    }

    consumeDueEventsAt(currentTimeNanos);
    while (currentTimeNanos < targetTimeNanos) {
        long segmentEnd = eventHeap.isEmpty()
            ? targetTimeNanos
            : Math.min(targetTimeNanos,
                eventHeap.peek().simulationTimeNanos);
        if (segmentEnd < currentTimeNanos) {
            throw new IllegalStateException("late event in simulation heap");
        }

        long elapsedNanos = Math.subtractExact(
            segmentEnd, currentTimeNanos);
        computeDerivativesAt(currentTimeNanos);
        integrateStep(elapsedNanos * 1.0e-9d);
        currentTimeNanos = segmentEnd;
        consumeDueEventsAt(currentTimeNanos);
    }
}

void consumeDueEventsAt(long timeNanos) {
    while (!eventHeap.isEmpty()
            && eventHeap.peek().simulationTimeNanos == timeNanos) {
        SimEvent event = eventHeap.peek();
        applyEvent(event);
        if (eventHeap.poll() != event) {
            throw new IllegalStateException("event heap head changed during apply");
        }
    }
}
```

heap node 是 immutable application value，保存 stable event facts，不保存 SOMA packed Index；`sequenceNo` 在 session 内唯一、单调分配并在溢出前 fail closed，使 `(simulationTimeNanos, sequenceNo)` 成为 total order。同一 event apply 期间只允许调度不早于当前时刻、且 sequence 更大的 event，因此已应用 node 仍应是 heap head；不能修改已进入 Java 8 `PriorityQueue` 的排序字段。

目标形态的数值事件只修改 `StateVectorRow`；如果允许 topology/definition 变化，应作为独立场景设计，并由 simulator 保证不会形成跨 table mutation cycle。若额外维护 `PendingEventRow` projection，它在明确 boundary 重建，不能要求 heap 与 projection 在每次 pop 后跨结构原子同步。`advanceTo` 不是跨 heap/Table transaction；apply 或 integration 失败后 simulator 停止使用当前 session，或从 application checkpoint 恢复，不能重试一份可能已部分应用的 event。

`computeDerivativesAt(...)` 是 application numerical-model phase：它从同一个 current state 计算完整 derivative staging，再以单 Table atomic update 发布，不能在 comparator 中做 topology lookup，也不能让一部分 row 使用新 derivative、另一部分仍使用旧 derivative。这里的 `integrateStep` 展示一次 Euler-style state update；高阶或 adaptive integrator 可以替换它，并拥有自己的 substep/derivative refresh，但必须保留 event boundary、staging、数值校验与失败边界。

### 7.3 更新 state vector

```java
void integrateStep(double dtSeconds) {
    if (!Double.isFinite(dtSeconds) || dtSeconds < 0.0d) {
        throw new IllegalArgumentException("invalid integration interval");
    }

    stateVectorRows.rows()
        .update(s -> {
            double value = s.value();
            double derivative = s.derivative();
            double scale = s.scale();
            if (!Double.isFinite(value)
                    || !Double.isFinite(derivative)
                    || !Double.isFinite(scale)
                    || scale == 0.0d) {
                throw new SimulationNumericsException(
                    "invalid state vector row");
            }

            double next = value + derivative * dtSeconds / scale;
            if (!Double.isFinite(next)) {
                throw new SimulationNumericsException(
                    "non-finite integration result");
            }
            s.setValue(next);
        });
}
```

`dtSeconds` 在 operation 外验证；row-local value、derivative、scale 和 next value 在 staged update callback 中验证。任一 callback 失败都会使本次单 Table update 不发布部分结果；application `SimulationNumericsException` 即使由 runtime callback envelope 包装，也必须通过 cause/category 与 storage failure 分开记录，并按 simulator checkpoint/fail-stop 规则处理，不能把 `NaN` 当作缺失或继续传播。

上面的 Row Pipeline 是 canonical readable path。耦合数值内核需要先读取完整旧向量、再发布新向量时，使用 session-owned reusable scratch，而不是每 step 分配新数组或逐 row 创建 mutator：

```java
int size = stateVectorRows.size();
double[] nextValues = integrationWorkspace.nextValues(size);

try (IntColumnView vectorIndexes = stateVectorRows.vectorIndexColumn();
     DoubleColumnView values = stateVectorRows.valueColumn();
     DoubleColumnView derivatives = stateVectorRows.derivativeColumn();
     DoubleColumnView scales = stateVectorRows.scaleColumn()) {
    for (int row = 0; row < size; row++) {
        int slot = vectorIndexes.getInt(row);
        double value = values.getDouble(row);
        double derivative = derivatives.getDouble(row);
        double scale = scales.getDouble(row);
        if (!Double.isFinite(value)
                || !Double.isFinite(derivative)
                || !Double.isFinite(scale)
                || scale == 0.0d) {
            throw new SimulationNumericsException(
                "invalid state vector row");
        }
        double next = value + derivative * dtSeconds / scale;
        if (!Double.isFinite(next)) {
            throw new SimulationNumericsException(
                "non-finite integration result");
        }
        nextValues[slot] = next;
    }
}

stateVectorRows.rows().update(row ->
    row.setValue(nextValues[row.vectorIndex()]));
```

该两阶段写法避免在 active ColumnView 下同表写入，并把大数组变成有明确上限、随 session 释放的 application scratch。它可能仍有一次 operation/callback 级开销，但没有 per-row object 或 per-step `double[size]` allocation；是否需要新的 writable/bulk primitive generated API 只能在现有 Row Pipeline lane 已被 benchmark 证明不足后进入 Design，不能在 Blueprint 中虚构。

### 7.4 追加 trace samples

```java
void sampleTrace(long sampleTimeNanos) {
    if (sampleTimeNanos < 0L
            || sampleTimeNanos <= lastSampleTimeNanos) {
        throw new IllegalArgumentException("trace time must increase");
    }

    TraceSampleRowBatch batch = reusableTraceBatch;
    batch.clear();

    stateVectorRows.rows()
        .forEach(s -> batch.addValues(
            sampleTimeNanos,
            s.entityKind(),
            s.entityId(),
            s.variableKind(),
            s.value()));

    traceSampleRows.addBatch(batch);
    lastSampleTimeNanos = sampleTimeNanos;
}
```

`lastSampleTimeNanos` 初始为 `-1`，只在 `addBatch` 成功后推进；结合唯一 state-vector mapping，保证每次采样的 `(sampleTimeNanos, entityKind, entityId, variableKind)` 唯一。`reusableTraceBatch` 在 session 创建时按采样上限准入，并只在当前同步调用中复用；`addBatch` 完成 detached staging copy 后下一次采样才 `clear()`。Trace 写入按采样周期批量发生，不在每个 primitive update 后逐行 append。稳定 export 顺序使用上述完整 tuple 的 total comparator；enum 次序采用 schema 声明顺序，不使用 `toString()`。

## 8. Cache 友好性分析

该场景的 cache 友好性主要来自：

- `StateVectorRow` 是 dense packed table，适合连续 scan；
- primitive field 通过 Row Pipeline cursor 或 ColumnView 读取，避免 per-row schema-object allocation；
- 可选 event projection 和 trace buffer 使用 dense rows，结构简单；
- `FlowCoefficient` 作为 keyed lookup 与 topology/entity state 分离。

主要削弱因素：

- 若每步 `fetch(tankId)` materialize schema object，会破坏 columnar hot path；
- 若每步 `replaceAll(stateVectorBatch)`，storage rewrite 会成为不必要成本；
- 若 trace 和 state vector 放在同一 hot loop path，trace append / boundary sort 可能污染 cache；
- 若 `FlowCoefficient.fetch` 在每个 valve 的每个 step 随机触发，可能需要把 coefficients 预投影到 valve-local dense rows 或 state vector adjacent columns。

因此蓝图建议：

- state vector 初始化后原地更新；
- due events 只从 application heap 按 total order 消费；可选 Table projection 在独立 boundary 重建或清理；
- trace buffer 与 state vector 计算路径分离；
- required lookup 在 step 前尽量预绑定或缓存到 dense row，不在 inner loop 重复 random lookup。

Boundary export 应明确分成三个阶段：SomaTable -> Materialized Object、Materialized Object -> external DTO、DTO -> wire/file。递归 object/List materialization 使用 runtime plan 默认或显式 `MaterializationBudget`；三个阶段的 allocation/time 不得混入 state-vector update claim。`@SomaTable` row 不生成 structural equality/hash，测试内容比较使用 testkit 显式 comparator。

Final-state export 直接读取最终 `StateVectorRow` facts，并由 entity mapping 组装 external DTO；`TraceSampleRow` 只表达被 sampling policy 选中的历史结果。二者不能互相替代，也不需要复制一个与 state vector 内容相同的 `FinalStateRow`。

`FlowCoefficient` 的推荐口径是：低频 topology 初始化或 material 变化时可以 keyed `fetch`；如果 derivative inner loop 每 step 每 valve 都需要 coefficient，应预投影到 valve-local dense row、state vector adjacent column，或其他连续 layout。benchmark 需要单独比较 keyed lookup、预投影 dense row 和 ColumnView primitive scan。

## 9. 目标形态必须处理的边界

- Dense table 在此承担 long-lived state，不只是 `replaceAll` workspace；
- Row Pipeline update 是否足够表达高频 primitive vector update，需要和 ColumnView lane 做 benchmark 对比；
- 使用 journey 必须讲清 active ColumnView 与 mutation 的关系，避免形成 view-pinned hot loop；
- event queue 使用 application-owned min-heap；SOMA Table 只承担可选 projection，不能宣称其 scan/sort/remove 等价于 priority queue；
- trace buffer 高频追加时，必须把 boundary dynamic sort 与 append 分开计量；
- `StateVectorRow` 是本文采用的数值状态 source-of-truth；definition table 不保存 numeric shadow fields，任何 derived cache 的同步点和失败恢复由 simulator 定义；
- `FlowCoefficient` inner-loop lookup 是否需要 preprojection 必须用 benchmark 证明；
- 普通 floating payload 的 finite/scale 业务 invariant 必须由 simulator 明确校验，不能依赖 SOMA 自动拒绝 NaN/Infinity。

## 10. 目标决策与证明义务

### 10.1 目标决策

- `StateVectorRow` 是 dense long-lived authoritative numeric state；definition table 不保存可写 numeric shadow；
- application min-heap 是唯一 event queue，使用 immutable node 和 `(simulationTimeNanos, sequenceNo)` total order；积分在 event boundary 分段，`PendingEventRow` 只是可选 projection；
- `TraceSampleRow` 是 export/diagnostic buffer，不反向驱动积分状态；final state 直接从 state vector 投影；
- simulator 拥有数值模型、异常值策略、事件规则、projection/cache 同步和跨 table 一致性；
- ColumnView hot path 默认把 live read scope 与写入阶段分开，除非下位 Design 明确允许固定宽度写入。

### 10.2 采用前证明义务

- 比较 state-vector Row Pipeline update、ColumnView + reusable scratch 与 primitive baseline 的成本边界，并验证 steady step 不分配 `double[size]`；
- 分开验证 heap push/pop、可选 Table projection、trace append/export 和 final-state materialization；
- 比较 `FlowCoefficient` keyed lookup、preprojected dense row、adjacent column 与 ColumnView scan；
- 为 finite value、non-zero scale 和 integration exceptional value 固化 simulator invariant 与 failure evidence；
- 验证 definition/state 分离后的 export、lookup、allocation，以及任何 derived numeric cache 的同步和重建语义。
