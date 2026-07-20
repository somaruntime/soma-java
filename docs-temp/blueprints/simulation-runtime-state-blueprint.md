# 连续仿真 runtime state 蓝图

类型：Blueprint

状态：候选

Owner：连续仿真目标场景

事实范围：dense 数值状态、外部事件堆、trace 与导出路径的目标使用形态

非事实范围：积分算法、物理模型、精确公共 API、当前实现状态和性能结论

服务设计：[设计宪法](../design/soma-java-design-constitution.md)、[Table、存储与访问](../design/table-storage-and-access.md)、[性能模型](../design/performance-model.md)

当前实现参考：[Simulation schema 示例](../../soma-examples/docs/simulation-runtime-state-example.md)、[场景与 benchmark 地图](../implementation-map/scenario-and-benchmark-map.md)

最后审查日期：2026-07-20

2026-07-17 baseline：SOMA 已删除 maintained order 与 dirty sidecar；真正的 event queue 由 simulator-owned min-heap 按 `(eventTimeMillis, sequenceNo)` 维护。`PendingEventRow` 只承担 batch ingest、诊断、导出或列式分析；表内需要顺序时显式 `.sorted(totalComparator)`。dense/keyed 删除均为 packed swap-remove，不保证物理遍历顺序。

## 1. 目标与适用范围

本文基于 `soma-examples/docs/simulation-runtime-state-example.md`，审视 tank / valve network 连续仿真场景下 SOMA runtime state 的建模方式。

连续仿真和 FJSP / VRP 的主要差异是：核心 hot path 不是候选 frontier 选择，而是 state vector 的重复扫描更新、外部 event heap 的时间有序消费，以及 trace buffer 的批量追加和导出。

适用边界：

- 只讨论 Java 8 generated table / Row Pipeline / ColumnView 使用方式；
- 只讨论仿真运行期间的 runtime state，不讨论 ODE solver、数值积分策略、并行调度或事件业务规则；
- SOMA 保存 hot runtime data plane，simulator OOP 层拥有物理模型、事件语义、采样策略和跨 table 一致性；
- 本文定义目标使用形态，不是精确 schema/API contract；代码片段用于表达使用者意图。

本蓝图中的 `@SomaTable` class 同时定义 row schema 与 detached single-row materialization shape，但不是 live runtime storage。Materializing API/terminal 返回 schema class 或 `List`/`Map`，Row Pipeline callback 参数仍是 callback-scoped Row Cursor。SOMA ownership aggregate 只允许单线程同步访问，不提供并发访问、跨 table transaction、序列化或持久化；trace/export 只是 runtime buffer 和外部 adapter boundary。

## 2. 当前示例中的 runtime tables

当前 executable example 包含以下 table：

| Table | 当前形态 | 生命周期判断 | 主要访问方式 |
|---|---|---|---|
| `Tank` | keyed entity state | 长期 runtime entity state | `fetch(tankId)`、`mutate(tankId)`、`by_tank_id` |
| `Valve` | keyed entity state | 长期 runtime entity state | `fetch(valveId)`、`by_from_tank`、`by_to_tank` |
| `FlowCoefficient` | keyed lookup data | 导入后只读 lookup | `fetch(valveMaterialKey)` |
| `StateVectorRow` | dense long-lived state | packed state vector | physical scan、explicit sort、ColumnView / Index scan |
| `PendingEventRow` | dense event batch / diagnostic workspace | ingest、诊断或导出边界 | batch append / explicit filter-sort / swap-remove |
| `TraceSampleRow` | dense trace / export buffer | 采样输出缓冲 | append / boundary explicit sort / batch export |

`StateVectorRow.vectorIndex` 是当前向量布局位置，不是 stable logical key。`PendingEventRow.sequenceNo` 是 event ordering tie-breaker，也不是 entity identity。`TraceSampleRow` 是输出记录，不应成为仿真状态事实源。

### 2.1 Application data role 审核

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
- 推荐的 optimized blueprint 不在 `TankDefinition` / `ValveDefinition` 保存 `levelLiters`、`temperatureCelsius`、`openingRatio`；boundary mapper 通过 state-vector entity mapping 构造 DTO；
- 如果兼容旧示例而保留这些字段，它们只能作为可丢弃的 derived export cache，不是 result fact 或第二 source-of-truth；
- 数值积分、事件应用和 derivative 计算只写 `StateVectorRow`；
- 兼容旧示例时，`Tank`/`Valve` 对应 cache 字段只能在 simulator 定义的同步点写入，例如 step boundary、export boundary 或 diagnostic snapshot；
- 如果同步失败，simulator 必须停止本 step、回滚到外部 snapshot，或丢弃 cache 并从 `StateVectorRow` 重建，不能让 SOMA runtime 猜测哪边权威。

如果某个项目坚持 `Tank` 是事实源，则 `StateVectorRow` 必须降级为派生 workspace，且不能跨 step 保存未同步值；这属于另一条场景，不应混入本文蓝图。

### 4.2 `PendingEventRow`

`PendingEventRow` 不是 canonical event queue，而是 dense event batch / diagnostic workspace：

- row 没有 stable logical key；
- application min-heap 按 `(eventTimeMillis, sequenceNo)` push/pop；
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

因此三者继续作为 root-level dense table。只有 future simulation session 本身被明确建模为 parent row，并且整个 state/event/trace aggregate 的 lifecycle、key-scoped live child access 和 deep materialization boundary 都已正式设计时，才评估 session-owned child；不得为了表达对象层级而牺牲全局 hot scan。

## 5. 潜在瓶颈识别

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

当前 executable example 方向基本正确，但蓝图建议强化 state vector ownership，并给 `StateVectorRow` 增加稳定的 entity mapping 字段，用于边界同步，而不是用于 keyed lookup。

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
    pendingEventRows.replaceAll(buildInitialEvents(input));
    traceSampleRows.clear();
}
```

### 7.2 消费 due events

```java
void consumeDueEvents(long stepEndMillis) {
    while (!eventHeap.isEmpty()
            && eventHeap.peek().eventTimeMillis <= stepEndMillis) {
        applyEvent(eventHeap.remove());
    }
}
```

heap node 保存 stable event facts，不保存 SOMA packed Index。Optimized blueprint 的数值事件只修改 `StateVectorRow`；如果未来允许 topology/definition 变化，应作为独立场景设计，并由 simulator 保证不会形成跨 table mutation cycle。若额外维护 `PendingEventRow` projection，heap 与 Table 的一致性由 simulator 明确拥有。

### 7.3 更新 state vector

```java
void integrateStep(double dtSeconds) {
    stateVectorRows.rows()
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

    stateVectorRows.rows()
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
- primitive field 通过 Row Pipeline cursor 或 ColumnView 读取，避免 per-row schema-object allocation；
- event queue 和 trace buffer 使用 dense rows，结构简单；
- `FlowCoefficient` 作为 keyed lookup 与 topology/entity state 分离。

主要削弱因素：

- 若每步 `fetch(tankId)` materialize schema object，会破坏 columnar hot path；
- 若每步 `replaceAll(stateVectorBatch)`，storage rewrite 会成为不必要成本；
- 若 trace 和 state vector 放在同一 hot loop path，trace append / boundary sort 可能污染 cache；
- 若 `FlowCoefficient.fetch` 在每个 valve 的每个 step 随机触发，可能需要把 coefficients 预投影到 valve-local dense rows 或 state vector adjacent columns。

因此蓝图建议：

- state vector 初始化后原地更新；
- due events 批量消费和批量 remove；
- trace buffer 与 state vector 计算路径分离；
- required lookup 在 step 前尽量预绑定或缓存到 dense row，不在 inner loop 重复 random lookup。

Boundary export 应明确分成三个阶段：SomaTable -> Materialized Object、Materialized Object -> external DTO、DTO -> wire/file。递归 object/List materialization 使用 runtime plan 默认或显式 `MaterializationBudget`；三个阶段的 allocation/time 不得混入 state-vector update claim。`@SomaTable` row 不生成 structural equality/hash，测试内容比较使用 testkit 显式 comparator。

Final-state export 直接读取最终 `StateVectorRow` facts，并由 entity mapping 组装 external DTO；`TraceSampleRow` 只表达被 sampling policy 选中的历史结果。二者不能互相替代，也不需要复制一个与 state vector 内容相同的 `FinalStateRow`。

`FlowCoefficient` 的推荐口径是：低频 topology 初始化或 material 变化时可以 keyed `fetch`；如果 derivative inner loop 每 step 每 valve 都需要 coefficient，应预投影到 valve-local dense row、state vector adjacent column，或其他连续 layout。benchmark 需要单独比较 keyed lookup、预投影 dense row 和 ColumnView primitive scan。

## 9. SOMA V1 可能暴露的问题

- Dense table 需要被正式呈现为 long-lived state，不只是 `replaceAll` workspace；
- Row Pipeline update 是否足够表达高频 primitive vector update，需要和 ColumnView lane 做 benchmark 对比；
- active ColumnView 与 mutation 的关系需要在示例中讲清，否则用户容易写出 view-pinned 的 hot loop；
- event queue 使用 application-owned min-heap；SOMA Table 只承担可选 projection，不能宣称其 scan/sort/remove 等价于 priority queue；
- trace buffer 高频追加时，必须把 boundary dynamic sort 与 append 分开计量；
- `StateVectorRow` 是本文采用的数值状态 source-of-truth；推荐 definition table 不再保存 numeric shadow fields，兼容旧示例的 cache 同步点和失败恢复由 simulator 定义；
- `FlowCoefficient` inner-loop lookup 是否需要 preprojection 必须用 benchmark 证明；
- 普通 floating payload 的 finite/scale 业务 invariant 必须由 simulator 明确校验，不能依赖 SOMA 自动拒绝 NaN/Infinity。

## 10. 自审结论

### 10.1 通过项

- 没有把连续仿真误建模成 candidate frontier；
- `StateVectorRow` 保持 dense long-lived state，符合 packed scan 和 primitive update；
- 明确 `StateVectorRow` 是数值状态 source-of-truth；optimized blueprint 默认不维护 `Tank`/`Valve` numeric cache，兼容旧示例时只允许 derived boundary cache；
- application min-heap 作为稳定 event queue 结构，`PendingEventRow` 降级为可选 projection；
- `TraceSampleRow` 被定位为 export/diagnostic buffer，不反向成为状态事实源；
- input definition、numeric working state、trace/final result 已形成清晰三段，final state 不复制 shadow table；
- 明确了 simulator OOP 层拥有数值模型、事件规则和跨 table 一致性。

### 10.2 风险和坏味道

- `SimVariableKind` 若未来不够表达业务变量，应改成 numeric `variableId` 或 value key，而不是回退到 hot path String；
- ColumnView hot path 必须保持只读 view 与写入阶段分离，除非 runtime contract 明确允许；
- 兼容旧示例时，`Tank`/`Valve` numeric cache 与 `StateVectorRow` 同步失败会导致 drift，必须有 simulator-level 恢复策略；optimized blueprint 默认不维护这份 cache；
- `PendingEventRow` two-terminal consume/remove 在大队列低 due ratio 下可能成本较高；
- `TraceSampleRow` 的 explicit sorted export 不应进入每步主循环；
- `NaN`/Infinity 传播策略若未固定，会使相同 runtime storage 在不同 simulator 中产生不同失败语义。

### 10.3 待验证事项

- State vector Row Pipeline update 与 ColumnView / primitive loop 的性能分界；
- queue size、due ratio、heap push/pop 与可选 Table projection/swap-remove 成本；
- trace buffer append/export order 是否需要 chunk table、分段 export 或 export boundary dynamic sort；
- `FlowCoefficient` preprojection：keyed lookup、valve-local dense row、state vector adjacent column 和 ColumnView primitive scan 的对比；
- state vector 和 entity table 的同步周期及错误恢复策略；
- finite value、non-zero scale 和 integration exceptional-value policy 的 fixture/error evidence；
- input definition + state vector 分离与旧 entity numeric cache 方案的 export/lookup/allocation 对比。

### 10.4 当前判定

本蓝图要求目标示例把 topology/parameter input definition、authoritative numeric working state、trace/final result 分开，并删除或明确降级 entity numeric shadow cache。后续场景专题应分别验证 `StateVectorRow` update、application event heap 与可选 Table projection、`TraceSampleRow` append/export、final-state projection 和 `FlowCoefficient` preprojection，不能把这些成本合并成一个笼统结论。
