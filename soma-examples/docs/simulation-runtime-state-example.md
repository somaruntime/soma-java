# 连续仿真 runtime state 示例

类型：Report / 开发者 current-executable 场景

状态：当前

Owner：`soma-examples` output

受众：使用或维护当前 simulation runtime-state example 的开发者

适用版本：最后 implementation-affecting baseline `fd82eba`

输入事实源：当前 example source、[连续仿真 Blueprint](../../docs/blueprints/simulation-runtime-state-blueprint.md)、Design 与 phase-6 evidence

事实范围：当前 simulation schema、event/vector/trace journey、数值与失败边界

非事实范围：完整仿真平台、SOMA public contract 和性能优势

最后审查日期：2026-07-23

> 本文只记录当前 executable example。目标仍由 Blueprint 拥有，长期语义仍由 Design 拥有。

## 1. 当前场景

[`SimulationScenario.java`](../src/main/java/com/hgtech/soma/examples/simulation/SimulationScenario.java) 运行一个 tank/valve event-boundary simulation：

```text
validate/import definitions + dense vector
  -> validate immutable vector layout
  -> schedule immutable events in application PriorityQueue
  -> integrate to next event boundary
  -> apply same-time events in total order
  -> optionally rebuild Table event projection
  -> append/export trace and final vector projection
```

仿真时间是从 session origin 起算的 non-negative `simulationTimeNanos`，不是 wall-clock `DATE_TIME`。Fixture 在相同纳秒时刻放入两个事件，直接验证 `(simulationTimeNanos, sequenceNo)` 的稳定全序。

## 2. 当前数据角色

| Role | 当前载体 | 权威性与访问方式 |
|---|---|---|
| input facts | `TankDefinition`、`ValveDefinition`、`FlowCoefficient` | keyed、import 后只读；topology exact access 与 coefficient required lookup |
| numeric working state | `StateVectorRow` | 唯一 authoritative numeric state；long-lived dense vector |
| pending-event state | application `PriorityQueue<SimEvent>` | 尚未消费事件的唯一 queue state |
| diagnostic projection | `PendingEventRow` | 从 heap 在显式 boundary 重建；event loop 不反向消费它 |
| result/export | `TraceSampleRow` 与 final vector projection | trace 不反向成为 state source；final state 直接从 vector 导出 |

Definition table 不再保存 `levelLiters`、`openingRatio` 等 mutable numeric shadow。`StateVectorRow.vectorIndex` 只是当前 dense layout slot，不是 stable business identity。

## 3. 关键 schema 投影

```java
@SomaTable(name = "tank_definitions", defaultCapacity = 256)
public final class TankDefinition {
    @SomaKey public TankId tankId;
    @SomaField public MaterialId materialId;
    @SomaField public double capacityLiters;
}

@SomaTable(name = "valve_definitions", defaultCapacity = 512)
@SomaIndex(name = "by_from_tank", fields = {"fromTank.value"})
@SomaIndex(name = "by_to_tank", fields = {"toTank.value"})
public final class ValveDefinition {
    @SomaKey public ValveId valveId;
    @SomaField public TankId fromTank;
    @SomaField public TankId toTank;
    @SomaField public double maxFlowLitersPerSecond;
    @SomaField public boolean enabled;
}

@SomaTable(name = "state_vector_rows", defaultCapacity = 4096)
public final class StateVectorRow {
    @SomaField public int vectorIndex;
    @SomaField public SimEntityKind entityKind;
    @SomaField public long entityId;
    @SomaField public SimVariableKind variableKind;
    @SomaField public double value;
    @SomaField public double derivative;
    @SomaField public double scale;
}

@SomaTable(name = "pending_event_rows", defaultCapacity = 1024)
public final class PendingEventRow {
    @SomaField public long simulationTimeNanos;
    @SomaField public long sequenceNo;
    @SomaField public SimEventKind eventKind;
    @SomaField public SimEntityKind targetKind;
    @SomaField public long targetId;
    @SomaField @SomaOptional public Double numericPayload;
}
```

完整声明以 [`com.hgtech.soma.examples.simulation`](../src/main/java/com/hgtech/soma/examples/simulation) 为准。`TraceSampleRow.sampleTimeNanos` 使用同一 session-relative 单位。

## 4. Vector 与 event 协议

进入 time loop 前，application 验证：

- `vectorIndex` 唯一、连续并完整覆盖 `[0,size)`；
- `(entityKind, entityId, variableKind)` mapping 唯一；
- value、derivative、scale 均 finite，且 scale 非零；
- topology/parameter 数值合法，时间 arithmetic 不溢出。

`Simulator` 分配单调 `sequenceNo`，在溢出前 fail closed；heap node 是 immutable application value，不保存 SOMA Index。Java `PriorityQueue` iterator 被明确视为无序，event projection 只能无序复制；需要有序诊断时必须对 projection 另做显式 total sort。

`advanceTo` 每次先推进到 `min(targetTime, heapHeadTime)`，完整计算 derivative staging，再通过一次 Table update 发布；到达边界后按 `(time, sequence)` 消费全部 due event。过去时刻的 event 被拒绝，event apply/integration 的任何异常都会把 simulator 标为 fail-stop。

## 5. 数值原子性

`IntegrationWorkspace` 在 session 创建时分配并复用 `double[] nextValues`；derivative 也使用固定 scratch。每个 step 分成：

1. 只读 ColumnView 扫描当前完整 vector；
2. 验证 finite/scale 并计算全部 next values；
3. 关闭 read views；
4. 单次 `update` 发布新 value。

Fixture 用一个 zero-scale row 触发 `SimulationNumericsException`，并验证两行 value 都保持原值。因此这里证明的是单次 Table operation 的失败原子性，不是 heap + Table 的跨结构 transaction。

## 6. Trace 与性能边界

- trace 使用 reusable `TraceSampleRowBatch` 批量追加；只在 export boundary 按 `(time, entityKind, entityId, variableKind)` 排序；
- event heap、event Table projection 与 trace 分开计量；Table scan/sort 不被描述为 priority queue；
- state-vector hot columns 的当前 APC width 为 44 bytes；fixture 记录 2 个 vector rows、5 个实际 changed-row mutations 和 2 个 trace exports；
- phase-6 Gate 固定 schema/hash、generated/public surface、same-time order、late-event rejection、numeric atomicity和 projection 非权威性。

这些结果只证明当前 Java 8 fixture 的 executable accounting，不构成通用积分器精度或 throughput claim。

## 7. 非目标

本示例不实现 adaptive/high-order integrator、动态 topology、分布式仿真、持久化 event log、rollback checkpoint、跨 Table transaction 或 wall-clock scheduling。
