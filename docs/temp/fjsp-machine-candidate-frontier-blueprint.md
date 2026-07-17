# FJSP MachineCandidate frontier 临时蓝图

状态：长期研究蓝图
正式事实源：否
已固化内容：[FJSP schema 示例](../../soma-examples/docs/fjsp-runtime-state-example.md)、[FJSP E2E 场景](../../soma-examples/docs/fjsp-e2e-scenario.md)、[Runtime-state benchmark 契约](../../soma-benchmarks/docs/runtime-state-benchmark-contract.md)
仍在研究：frontier lifecycle、indicator/update 粒度和 claim-grade evidence
最后审查日期：2026-07-17

对齐基线：[设计宪法](../soma-table-design-constitution.md)、[Generated Table API](../generated-table-api-contract.md)、[Runtime 性能模型](../runtime-performance-model.md)、[TableStore 契约](../../soma-runtime-core/docs/table-store-contract.md)

2026-07-17 baseline：SOMA 已删除 maintained order 与 dirty sidecar；keyed/dense 均采用 packed swap-remove，`@SomaIndex` 只提供 always-current exact group，业务顺序必须显式 `.sorted(...)`，machine availability queue 如需跨轮次维护则由 application-owned heap 承担。本文只继续研究 FJSP 场景取舍，不再把旧 order/sidecar 作为候选 runtime 方案。

## 1. 目标

本文记录 FJSP 构造解场景下，使用 SOMA 保存运行中候选 frontier 的一版临时方案。
该方案只对 FJSP 这类候选有稳定 `(MachineId, OperationKey)` 身份、需要跨轮次保留并按 machine / operation 局部清理的场景成立，不应被推广为所有候选集合的默认建模方式。

核心方向：

- `MachineCandidateTable` 保存 runtime frontier；
- operation release 时，把可加工的 `(MachineId, OperationKey)` 加入 frontier；
- dispatch 时，只对当前 machine 的候选计算动态 indicator；
- dispatch rule 使用手动 dynamic sort，不在 schema 中固化 `byMachineDispatchRule`；
- 某个 operation 被选中后，从 `MachineCandidateTable` 删除该 operation 相关的全部候选；
- SOMA 保存 hot runtime state，solver/application loop 拥有调度策略和跨 table 一致性。

本蓝图中的 `@SomaTable` class 同时定义 row schema 与 detached single-row materialization shape，但不表示 live runtime storage。`fetch(...)`、`findFirst()`、`firstOrThrow()` 直接返回对应 schema class；Row Pipeline callback 参数仍是 callback-scoped Row Cursor。`@SomaValue` 由 compiler 提供 immutable/public-final-field/value-equality 语义。SOMA ownership aggregate 只允许单线程同步访问，不提供并发访问、跨 table transaction、序列化或持久化。

## 2. Application data role 与设计原则

### 2.1 数据职责审核

本场景先按 application data role 分类，再选择 keyed/dense、exact index、显式排序和访问路径：

| Data role | Table / field group | 权威性与生命周期 | 设计判断 |
|---|---|---|---|
| input facts | `OperationDefinition.candidateMachines` dense child、`SetupTime` | import 后 authoritative、read-only/read-mostly | candidate-machine rows 由 operation 独占并按 operation 连续遍历；与运行状态和结果分开 |
| working state | `MachineState`、`OperationRuntimeState`、`MaterialState`、job progress | solve 期间 authoritative mutable state | 只保存算法下一步真正依赖的状态，不混入 external DTO shape |
| working state | `MachineCandidate` | authoritative current-frontier state，但可由 input + progress/state 重建 | candidate copy/indicator 必须有明确 invalidation/rebuild 规则 |
| result facts | `OperationAssignment` | 每个 operation 最多一条 authoritative assignment | 独立于 operation definition/runtime state；absence 表示尚未产生 assignment |

原草案的 `OperationState` 同时保存 `sequenceNo/releaseTime/setupFamily` 输入、`jobReadyTime/materialReadyTime` 工作状态以及 `assignedMachine/startTime/endTime` 结果，职责过多。本蓝图采用拆分后的推荐方向：

```text
OperationDefinition       // input facts
OperationRuntimeState     // working state
OperationAssignment       // result facts
```

这不是 SOMA 强制的 schema 形式，而是本场景的默认建模选择。若后续 benchmark 证明 definition + state 的跨表读取是主要瓶颈，可以把被每次 release/dispatch 共同读取的只读 leaf 受控预投影到 frontier row；不得重新维护第二份 authoritative assignment。`OperationAssignment` 只有在 assignment 是独立 identity/lifecycle/result access path 时才成立，本蓝图正好满足这一条件。

### 2.2 Access Pattern Card

以下 card 是场景/runtime-plan 输入，不是 Schema fact；具体 rows、working-set bytes 和比例必须由 benchmark scale/fixture 提供，不能从 `defaultCapacity` 推断。

| Table / phase | Rows/cardinality | Hot columns | Access / mutation mix | Locality / allocation boundary |
|---|---|---|---|---|
| `OperationDefinition.candidateMachines` | operation count × per-operation candidate-machine count；必须记录 empty/typical/high child cardinality | `machineId`、`processingTime` | operation release 时 parent-key locate + child packed scan；input import 后只读 | live child facade 应 object-free；parent `fetch` deep materialization 只作 reference/export 对照 |
| `MachineCandidate` frontier | current released-unscheduled machine-operation pairs | candidate key、setup family、ready/setup/FCFS/SPT indicators | release batch append；按 machine exact-group update/sort；按 operation exact-group remove | by_machine/by_operation selectivity、dynamic-sort scratch、swap-remove 与 exact-index maintenance 分开计量 |
| `MachineState` | machine count | `nextAvailableTime`、`lastSetupFamily` | repeated explicit arg-min + one-row mutate；或 application heap | 比较全量 arg-min 与外部 heap 的 mutation/read 成本，不把业务队列塞入 Table 物理顺序 |
| `OperationRuntimeState` / `OperationAssignment` | operation count / assigned operation count | ready fields / result times | point lookup + result insert | split lookup 与 co-located baseline 比较；不得复制 authoritative assignment |
| `SetupTime` | machine/setup-family combinations | key leaves、`setupTime` | dispatch indicator phase random point lookup | 记录 load factor、collision、reuse；comparator 内禁止 lookup |

全场景还必须记录 touched columns/bytes、frontier group size、selector cardinality、mutation/read ratio、steady-state allocation/op、stats mode 和 boundary export frequency。

### 2.3 Frontier 不是每轮临时 workspace

不推荐每轮执行：

```java
candidateRows.replaceAll(buildCandidatesFor(machineId, now));
```

这会把 cross-table 读取、ready time 计算、builder 构造、batch 写入和排序成本隐藏在一次 workspace rebuild 中。

更好的方向是把候选集作为长期 runtime frontier 增量维护：

```text
operation release
  -> OperationDefinition.candidateMachines dense child scan
  -> add (machineId, operationKey) candidates
  -> dispatch 时按 machine 局部计算 indicator
  -> dynamic sort 选择候选
  -> remove candidates for selected operation
```

### 2.4 Schema 只固化稳定访问路径

`MachineCandidateTable` 必须支持：

- 按 machine 查候选：dispatch 时使用；
- 按 operation 查候选：operation 被选中后批量删除。

不强制在 schema 中声明 dispatch rule order。`FCFS + SPT`、`SPT + setup`、`EDD`、`CR` 或加权规则都属于 solver 策略，不是 table 的永久事实。

### 2.5 Indicator 可以小颗粒度计算

Indicator 字段可以分阶段计算：

```text
operation release
  -> 初始化稳定字段
  -> 更新 operation/job/material ready 指标
  -> dispatch 前读取 MachineState.lastSetupFamily
  -> 计算 setupTime / effectiveReadyTime / dispatch values
  -> dynamic sort
```

但 comparator 中不能做跨 table lookup。所有用于排序的值应先写入 candidate row，再用 read-only row cursor 排序。

## 3. Schema annotation 草案

以下代码只表达 schema 形态和访问路径，最终 generated API 名称以 codegen golden 为准。

```java
@SomaSchema(
    name = "fjsp_runtime_state",
    generatedPackage = "com.example.fjsp.state.generated",
    version = "1"
)
package com.example.fjsp.state;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaSchema;
import com.hgtech.soma.annotation.SomaTable;
import com.hgtech.soma.annotation.SomaValue;
import java.util.List;

@SomaValue
public class JobId {
    @SomaField
    long value;
}

@SomaValue
public class OperationId {
    @SomaField
    long value;
}

@SomaValue
public class MachineId {
    @SomaField
    long value;
}

@SomaValue
public class MaterialId {
    @SomaField
    long value;
}

@SomaValue
public class SetupFamilyId {
    @SomaField
    long value;
}

@SomaValue
public class OperationKey {
    @SomaField
    JobId jobId;

    @SomaField
    OperationId operationId;
}

@SomaValue
public class OperationMachineKey {
    @SomaField
    MachineId machineId;

    @SomaField
    OperationKey operationKey;
}

@SomaValue
public class SetupTimeKey {
    @SomaField
    MachineId machineId;

    @SomaField
    SetupFamilyId fromFamily;

    @SomaField
    SetupFamilyId toFamily;
}

@SomaTable(name = "machines", defaultCapacity = 128)
public final class MachineState {
    @SomaKey
    public MachineId machineId;

    @SomaField
    public long nextAvailableTime;

    @SomaField
    @SomaOptional
    public SetupFamilyId lastSetupFamily;
}

@SomaTable(name = "candidate_machine_definitions", defaultCapacity = 8)
public final class CandidateMachineDefinition {
    @SomaField
    public MachineId machineId;

    @SomaField
    public long processingTime;
}

@SomaTable(name = "operation_definitions", defaultCapacity = 4096)
@SomaIndex(name = "by_job_sequence", fields = {
    "operationKey.jobId.value",
    "sequenceNo"
})
public final class OperationDefinition {
    @SomaKey
    public OperationKey operationKey;

    @SomaField
    public int sequenceNo;

    @SomaField
    public long releaseTime;

    @SomaField
    public SetupFamilyId setupFamily;

    @SomaChild(initialCapacity = 8)
    public List<CandidateMachineDefinition> candidateMachines;
}

@SomaTable(name = "operation_runtime_states", defaultCapacity = 4096)
public final class OperationRuntimeState {
    @SomaKey
    public OperationKey operationKey;

    @SomaField
    public long jobReadyTime;

    @SomaField
    public long materialReadyTime;
}

@SomaTable(name = "operation_assignments", defaultCapacity = 4096)
public final class OperationAssignment {
    @SomaKey
    public OperationKey operationKey;

    @SomaField
    public MachineId assignedMachine;

    @SomaField
    public long setupStartTime;

    @SomaField
    public long startTime;

    @SomaField
    public long endTime;
}

@SomaTable(name = "materials", defaultCapacity = 4096)
public final class MaterialState {
    @SomaKey
    public MaterialId materialId;

    @SomaField
    public long readyTime;
}

@SomaTable(name = "setup_times", defaultCapacity = 2048)
public final class SetupTime {
    @SomaKey
    public SetupTimeKey setupTimeKey;

    @SomaField
    public long setupTime;
}

@SomaTable(name = "machine_candidates", defaultCapacity = 8192)
@SomaIndex(name = "by_machine", fields = {
    "candidateKey.machineId.value"
})
@SomaIndex(name = "by_operation", fields = {
    "candidateKey.operationKey.jobId.value",
    "candidateKey.operationKey.operationId.value"
})
public final class MachineCandidate {
    @SomaKey
    public OperationMachineKey candidateKey;

    @SomaField
    public SetupFamilyId targetSetupFamily;

    @SomaField
    public long operationReleaseTime;

    @SomaField
    public long jobReadyTime;

    @SomaField
    public long materialReadyTime;

    @SomaField
    public long baseReadyTime;

    @SomaField
    public long processingTime;

    // Dispatch 前按当前 machine state 计算。
    @SomaField
    public long setupTime;

    @SomaField
    public long effectiveReadyTime;

    @SomaField
    public long fcfsValue;

    @SomaField
    public long sptValue;

    @SomaField
    public boolean indicatorReady;
}
```

## 4. 主流程草案

### 4.1 Operation release

当某个 operation release 时，只初始化稳定字段和不依赖当前 machine setup state 的指标。

```java
void releaseOperation(OperationKey operationKey) {
    OperationDefinition definition = operationDefinitions.fetch(operationKey);
    OperationRuntimeState runtime = operationRuntimeStates.fetch(operationKey);
    long baseReadyTime = max3(
        definition.releaseTime,
        runtime.jobReadyTime,
        runtime.materialReadyTime
    );

    CandidateBatch batch = machineCandidates.newBatch();

    for (CandidateMachineDefinition candidate : definition.candidateMachines) {
        OperationMachineKey key = new OperationMachineKey(
            candidate.machineId,
            operationKey
        );

        batch.add()
            .setCandidateKey(key)
            .setTargetSetupFamily(definition.setupFamily)
            .setOperationReleaseTime(definition.releaseTime)
            .setJobReadyTime(runtime.jobReadyTime)
            .setMaterialReadyTime(runtime.materialReadyTime)
            .setBaseReadyTime(baseReadyTime)
            .setProcessingTime(candidate.processingTime)
            .setSetupTime(0L)
            .setEffectiveReadyTime(0L)
            .setFcfsValue(0L)
            .setSptValue(0L)
            .setIndicatorReady(false);
    }

    machineCandidates.addBatch(batch);
}
```

### 4.2 Dispatch loop

Dispatch rule 不使用 schema order，而是在当前 machine 的候选集上显式 dynamic sort。

```java
while (scheduledCount < totalOperationCount) {
    MachineState machine = machines.byAvailableTime().firstOrThrow();
    MachineId machineId = machine.machineId;
    long machineReady = machine.nextAvailableTime;
    SetupFamilyId lastFamily = machine.lastSetupFamily != null
        ? machine.lastSetupFamily
        : DEFAULT_SETUP_FAMILY;

    machineCandidates.findByMachine(machineId)
        .update(c -> {
            SetupTimeKey setupKey = new SetupTimeKey(
                machineId,
                lastFamily,
                c.targetSetupFamily()
            );

            long setup = setupTimes.fetch(setupKey).setupTime;
            long effectiveReady = Math.max(c.baseReadyTime(), machineReady);

            c.setSetupTime(setup);
            c.setEffectiveReadyTime(effectiveReady);
            c.setFcfsValue(effectiveReady);
            c.setSptValue(setup + c.processingTime());
            c.setIndicatorReady(true);
        });

    MachineCandidate chosen = machineCandidates.findByMachine(machineId)
        .filter(c -> c.indicatorReady())
        .sorted((a, b) -> {
            int byFcfs = Long.compare(a.fcfsValue(), b.fcfsValue());
            if (byFcfs != 0) {
                return byFcfs;
            }

            int bySpt = Long.compare(a.sptValue(), b.sptValue());
            if (bySpt != 0) {
                return bySpt;
            }

            int byJob = Long.compare(
                a.candidateKey().operationKey().jobId().value(),
                b.candidateKey().operationKey().jobId().value()
            );
            if (byJob != 0) {
                return byJob;
            }

            return Long.compare(
                a.candidateKey().operationKey().operationId().value(),
                b.candidateKey().operationKey().operationId().value()
            );
        })
        .firstOrThrow();

    commitAssignment(chosen);
    scheduledCount++;
}
```

### 4.3 Commit assignment

```java
void commitAssignment(MachineCandidate chosen) {
    OperationMachineKey chosenKey = chosen.candidateKey;
    MachineId machineId = chosenKey.machineId;
    OperationKey operationKey = chosenKey.operationKey;

    long setupStart = chosen.effectiveReadyTime;
    long start = setupStart + chosen.setupTime;
    long end = start + chosen.processingTime;

    OperationAssignmentBatch assignmentBatch = operationAssignments.newBatch();
    assignmentBatch.add()
        .setOperationKey(operationKey)
        .setAssignedMachine(machineId)
        .setSetupStartTime(setupStart)
        .setStartTime(start)
        .setEndTime(end);
    operationAssignments.addBatch(assignmentBatch);

    machines.mutate(machineId)
        .setNextAvailableTime(end)
        .setLastSetupFamily(chosen.targetSetupFamily)
        .commit();

    machineCandidates.findByOperation(operationKey).remove();

    releaseNextOperations(operationKey, end);
}
```

上面的 `fetch()` / `firstOrThrow()` 各自 materialize detached schema object；`OperationDefinition` 还会递归构造 candidate-machine `List`。它们是可读性优先的 reference path，不代表零分配 hot path；benchmark 必须单独记录 schema object/List allocation、dynamic-sort row-index buffer 和后续 external DTO mapping。`@SomaTable` row 不生成 structural equality/hash，候选身份比较继续使用 immutable `@SomaValue` equality。

`OperationAssignment` 是结果事实的唯一 owner；`OperationDefinition` 和 `OperationRuntimeState` 不再保存 assignment shadow fields。`commitAssignment(...)` 是 solver-level sequence，不是 SOMA transaction。推荐提交顺序是先新增 assignment 和更新 machine availability，再删除 selected operation 的全部 candidate，最后 release 后续 operation；如果任一步失败，solver loop 必须停止本轮、回滚外部 snapshot，或根据 assignment/result fact 重建 `MachineCandidate` frontier，不能假设 runtime 会跨 table 自动补偿。

`releaseNextOperations(...)` 由 solver/application loop 负责。它不应退化为全表扫描，而应至少依赖以下事实源之一：

- `OperationDefinition.by_job_sequence(jobId, nextSequenceNo)`；
- 独立 `JobProgress.nextSequenceNo` 或等价 job progress state；
- material / predecessor readiness 的明确索引或业务队列。

被 release 的 operation 再通过 `OperationDefinition.candidateMachines` dense child 增量加入 `MachineCandidate` frontier。若后续 benchmark 证明 deep materialization allocation 成为热点，应改用 generated live child facade 按 parent key 遍历同一 child storage，而不是恢复 flat shadow fact。

## 5. 审核结论

### 5.1 通过项

- `MachineCandidateTable` 是 keyed frontier，不是每轮临时 `replaceAll` workspace；
- 该 frontier 模式只适用于 FJSP 这类有稳定或 epoch 内稳定 candidate identity 的场景，不是 SOMA 示例的通用默认；
- `active` 字段已移除，候选存在即有效，失效通过 `remove()` 表达；
- schema 只固化 `by_machine` 和 `by_operation` 两个稳定访问路径；
- dispatch rule 保持在 solver 策略层，通过 `sorted(comparator)` 表达；
- setup time 在 dispatch 开始时基于 `MachineState.lastSetupFamily` 快照计算；
- comparator 只读取 candidate row 字段，不做 cross-table lookup；
- operation 被选中后，使用 `findByOperation(operationKey).remove()` 删除所有相关候选；
- input definition、working state 和 result assignment 已分开，assignment 不再与 operation runtime state 双写。
- candidate-machine input 使用 parent-owned dense child `List`，保持 per-operation ownership 与 packed locality；runtime frontier 仍是独立 keyed table，不与 input child 混合。

### 5.2 风险和坏味道

- 如果单台 machine frontier 很大，`sorted(comparator)` 会成为热点；届时需要 benchmark 后再考虑 top-k buffer；跨轮次 queue 使用 application-owned heap；
- `setupTimes.fetch(setupKey)` 缺失必须有明确业务语义：canonical FJSP 建议作为 required lookup error；若业务希望默认 0 或候选不可行，必须在场景契约中显式改写，不能由 runtime 猜测；
- dispatch loop 连续修改 `operationAssignments`、`machines`、`machineCandidates`，SOMA V1 不提供跨 table transaction，一致性由 solver/application loop 保证；
- `releaseNextOperations(...)` 不能退化成全表扫描，应依赖 job sequence、material dependency 或其他 lookup/index；
- `indicatorReady` 只是本轮 machine dispatch 的计算状态，不能被误用成长期业务状态；
- 动态排序是当前正式业务顺序机制；不能把一次性排序与 application-owned heap 的跨轮次维护成本混为一谈；
- `MachineCandidate` 同时需要按 machine dispatch 和按 operation cleanup，不属于任一 parent row 的独占生命周期，因此不应改成 `MachineState` 或 `OperationRuntimeState` 的 child table。
- `CandidateMachineDefinition.defaultCapacity` / `@SomaChild.initialCapacity` 是 per-child-instance hint，必须按单个 operation 的典型候选机器数设置；不能使用 root 总行数规模。

## 6. 当前建议

该蓝图适合作为后续 FJSP 示例或 benchmark 场景的候选方向。正式化前需要补充：

- generated API 命名 golden；
- 非 canonical `SetupTime` 缺失语义变体，例如默认 0 或缺失表示不可行，必须另行修改场景契约；
- `releaseNextOperations(...)` 的依赖索引设计；
- dynamic sort、top-k 内部优化和 application-owned heap 的同语义 benchmark lane；
- candidate-machine dense child scan 与 flat composite-key/index baseline；
- frontier add/update/remove、setup lookup、machine arg-min/外部 heap、Materialized Object export、external DTO adapter 与 dense workspace rebuild 对照 lane；
- export/fetchAll 使用 runtime plan 默认或显式 `MaterializationBudget`，并与 hot-loop Row Pipeline 成本分开统计；
- cross-table commit 失败时的 solver-level error handling；
- `OperationDefinition + OperationRuntimeState` 分表 lookup 与旧 co-located row 的 benchmark；若需要 preprojection，只复制 hot derived leaf，不复制 authoritative assignment；
- result export 以 `OperationAssignment` 为权威结果事实，必要时由 exporter 显式读取 definition/lookup facts 组装 external DTO。
