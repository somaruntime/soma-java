# FJSP runtime state 示例与 E2E 场景契约

状态：正式设计文档
日期：2026-07-07
Owner：`soma-examples`

## 1. 文档定位

本文是 FJSP 场景的唯一正式示例文档，合并原 `runtime-state-schema-examples.md` 中的 FJSP schema 示例和原 `fjsp-e2e-scenario.md` 中的 E2E 场景契约。

本文定义 `soma_java` V1 examples 必须证明的端到端场景和 evidence 边界。

Examples 负责暴露跨模块集成问题，但不拥有 annotation schema、processor、runtime core 或 generated API 的正式技术事实。

本场景遵守 [Runtime state schema 典型示例](runtime-state-schema-examples.md) 中的通用建模规则。

## 2. 场景边界

FJSP 示例表达 `FCFS + SPT` 一类构造解过程中的 runtime state：

```text
jobs / operations / materials / machines / processing times / setup times
  -> operation release
  -> machine_candidates keyed runtime frontier
  -> per-machine indicator update
  -> dynamic sorted dispatch
  -> assignment mutation / frontier cleanup
  -> DTO export
```

不表达完整 APS、CP-SAT、局部搜索、重排优化或多目标优化。`ProcessingTime` 和 `SetupTime` 是 keyed lookup table，不嵌入 `Operation` 生命周期；`MachineCandidate` 是 keyed runtime frontier，用于保存 operation release 后可加工的 `(MachineId, OperationKey)` 候选。它不是每轮临时 `replaceAll(batch)` workspace，也不在 schema 中固化 dispatch rule order。

## 3. V1 最小 E2E 场景

V1 最小 E2E 场景是简化 FJSP 的 `FCFS + SPT` dispatch：

```text
request DTO or protobuf adapter
  -> solver API loader
  -> Java annotation schema / generated tables
  -> Java columnar runtime core
  -> FCFS + SPT solver core
  -> solver API exporter
  -> response DTO or protobuf adapter
```

`SOMA` library 本身不依赖 protobuf。示例可以使用 plain Java request/response DTO 模拟 API boundary；真实业务项目可在外层接 protobuf。

## 4. Schema source 示例

```java
@SomaSchema(
    name = "fjsp_runtime_state",
    generatedPackage = "com.example.fjsp.state.generated",
    version = "1"
)
package com.example.fjsp.state;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSchema;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;
import com.hgtech.soma.annotation.SomaValue;

public enum MachineState {
    READY,
    DOWN
}

@SomaValue
public final class JobId {
    @SomaField
    public long value;
}

@SomaValue
public final class OperationId {
    @SomaField
    public long value;
}

@SomaValue
public final class MachineId {
    @SomaField
    public long value;
}

@SomaValue
public final class MaterialId {
    @SomaField
    public long value;
}

@SomaValue
public final class SetupFamilyId {
    @SomaField
    public long value;
}

@SomaValue
public final class OperationKey {
    @SomaField
    public JobId jobId;

    @SomaField
    public OperationId operationId;
}

@SomaValue
public final class OperationMachineKey {
    @SomaField
    public OperationKey operationKey;

    @SomaField
    public MachineId machineId;
}

@SomaValue
public final class SetupFamilyPair {
    @SomaField
    public SetupFamilyId fromFamily;

    @SomaField
    public SetupFamilyId toFamily;
}

@SomaValue
public final class SetupTimeKey {
    @SomaField
    public MachineId machineId;

    @SomaField
    public SetupFamilyPair familyPair;
}

@SomaTable(name = "jobs", defaultCapacity = 1024)
@SomaOrder(name = "by_dispatch_order", by = {
    @SomaSort("inputOrder"),
    @SomaSort("jobId.value")
})
public final class Job {
    @SomaKey
    public JobId jobId;

    @SomaField
    public long inputOrder;

    @SomaField
    public long dueMinute;

    @SomaField
    public int operationCount;

    @SomaField
    @SomaDefault("0")
    public int nextSequenceNo;

    @SomaOptional
    public Long completedMinute;

    @SomaOptional
    public Long tardinessMinutes;
}

@SomaTable(name = "operations", defaultCapacity = 4096)
@SomaIndex(name = "by_job_sequence", fields = {
    "operationKey.jobId.value",
    "sequenceNo"
})
public final class Operation {
    @SomaKey
    public OperationKey operationKey;

    @SomaField
    public long inputOrder;

    @SomaField
    public int sequenceNo;

    @SomaField
    public long releaseMinute;

    @SomaField
    public long jobReadyMinute;

    @SomaField
    public long materialReadyMinute;

    @SomaField
    public SetupFamilyId setupFamily;

    @SomaOptional
    public MachineId assignedMachine;

    @SomaOptional
    public Long setupStartMinute;

    @SomaOptional
    public Long setupMinutes;

    @SomaOptional
    public Long startMinute;

    @SomaOptional
    public Long processingMinutes;

    @SomaOptional
    public Long endMinute;
}

@SomaTable(name = "materials", defaultCapacity = 4096)
public final class Material {
    @SomaKey
    public MaterialId materialId;

    @SomaField
    public long readyMinute;
}

@SomaTable(name = "machines", defaultCapacity = 128)
@SomaIndex(name = "by_state", fields = {"state"})
@SomaOrder(name = "by_available_time", by = {
    @SomaSort("availableFromMinute"),
    @SomaSort("machineId.value")
})
public final class Machine {
    @SomaKey
    public MachineId machineId;

    @SomaField
    @SomaDefault("READY")
    public MachineState state;

    @SomaField
    @SomaDefault("0")
    public long availableFromMinute;

    @SomaOptional
    public SetupFamilyId lastSetupFamily;
}

@SomaTable(name = "processing_times", defaultCapacity = 8192)
@SomaIndex(name = "by_operation", fields = {
    "operationMachineKey.operationKey.jobId.value",
    "operationMachineKey.operationKey.operationId.value"
})
public final class ProcessingTime {
    @SomaKey
    public OperationMachineKey operationMachineKey;

    @SomaField
    public long processingMinutes;
}

@SomaTable(name = "setup_times", defaultCapacity = 1024)
@SomaIndex(name = "by_machine_to_family", fields = {
    "setupTimeKey.machineId.value",
    "setupTimeKey.familyPair.toFamily.value"
})
public final class SetupTime {
    @SomaKey
    public SetupTimeKey setupTimeKey;

    @SomaField
    public long setupMinutes;
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
    public long operationReleaseMinute;

    @SomaField
    public long jobReadyMinute;

    @SomaField
    public long materialReadyMinute;

    @SomaField
    public long baseReadyMinute;

    @SomaField
    public long processingMinutes;

    @SomaField
    public long setupMinutes;

    @SomaField
    public long effectiveReadyMinute;

    @SomaField
    public long fcfsValue;

    @SomaField
    public long sptValue;

    @SomaField
    public boolean indicatorReady;
}
```

## 5. Runtime state coverage

示例 runtime state 至少覆盖：

- `Operation` keyed table；
- `Job` keyed table；
- `Material` keyed table；
- `Machine` keyed table；
- `MachineCandidate` keyed runtime frontier table；
- `ProcessingTime` keyed lookup table；
- `SetupTime` keyed lookup table；
- enum；
- value key；
- nested value；
- optional field；
- table-level index；
- table-level order；
- Row Pipeline lazy terminal；
- ColumnView。

## 6. Dispatch flow

默认示例算法：

1. 从 request boundary 导入 jobs、operations、materials、machines、processing times 和 setup times；
2. batch import 初始化 generated tables；
3. 当 operation release 时，通过 `ProcessingTime.findByOperation(operationKey)` 为可加工 machine 生成 `MachineCandidate` rows；
4. 每轮从 `Machine.byAvailableTime().firstOrThrow()` 选择下一个可用 machine；
5. 对 `MachineCandidate.findByMachine(machineId)` 的候选更新 setup、effective ready time、FCFS value 和 SPT value；
6. 对同一 machine 的候选执行 `sorted(dispatchRuleComparator).firstOrThrow()`，选择下一个 operation；
7. mutation 写回 operation assignment；
8. 更新 machine availability 和 last setup family；
9. 使用 `MachineCandidate.findByOperation(operationKey).remove()` 删除该 operation 的全部候选；
10. release 后续 operation，并重复直到全部 operation assigned；
11. fetch/materialize DTOs；
12. export response boundary。

算法正确性不是 SOMA 的完整 APS 承诺。该示例只用于证明 runtime state API 能支撑典型调度 hot loop。

`releaseNextOperations(...)` 应依赖 `Operation.by_job_sequence(jobId, nextSequenceNo)`、`Job.nextSequenceNo` 或 material / predecessor readiness 的明确索引或业务队列，不能退化为全表扫描。operation release 后，再通过 `ProcessingTime.findByOperation(operationKey)` 增量加入 `MachineCandidate` frontier。

SOMA V1 只保证单张 table mutation 后的 table 内部不变量。`Operation` assignment、`Machine` availability 更新、`MachineCandidate` frontier 删除、`Job` 和 `Material` 推进等跨 table 提交序列，不具备 runtime transaction 语义；其一致性、提交顺序、失败处理和补偿策略由 solver loop 拥有。任一步失败时，solver loop 应停止本轮、回滚外部 snapshot，或重建 `MachineCandidate` frontier，不能假设 SOMA runtime 自动补偿。

`MachineCandidate` 是 keyed runtime frontier。候选 row 存在表示该 `(MachineId, OperationKey)` 组合仍处于可选 frontier；operation 被选中后，solver loop 应通过 `findByOperation(operationKey).remove()` 删除所有相关候选，而不是保留长期 `active` 标志。`indicatorReady` 只是当前 machine snapshot / 当前 dispatch 轮次下的 indicator 计算状态，不能作为长期业务状态或候选有效性事实。Dispatch rule 属于 solver 策略，示例不在 `MachineCandidate` schema 上声明 `byMachineDispatchRule` 这类 order。

## 7. Lookup missing semantics

FJSP example 必须明确区分 runtime missing key 与业务不可行：

- `ProcessingTime` 表示 operation-machine pair 是否可加工。通过 `findByOperation(operationKey)` 生成候选时，某台机器没有对应 `ProcessingTime` row 表示该机器不是候选；如果某个 released operation 没有任何 candidate row，solver core 将其解释为当前无可行机器，而不是 SOMA runtime error。
- `MachineCandidate` 表示已经 release 且仍未被分配的候选 frontier。某个 operation 不在 frontier 中，可能表示它尚未 release、已经被分配、或因业务规则暂不可行；具体解释由 solver core 拥有。
- 如果 loader 或 solver core 已经确定某个 `OperationMachineKey` 必须存在，再调用 `processingTimes.fetch(operationMachineKey)` 或 `firstOrThrow()` 时缺失，应作为 typed missing key / empty required result 错误暴露。
- `SetupTime` 在 V1 canonical FJSP 中是 required setup matrix lookup。除第一道工序或机器没有 `lastSetupFamily` 且业务规则定义 setup 为 `0` 的情况外，缺失 `SetupTime` row 表示输入或模型不完整，应通过 typed missing key / required lookup error 暴露。
- 如果未来示例要表达 sparse setup matrix，例如缺失 setup 表示不可行或默认 `0`，必须先修改本契约，不能由 runtime 自行猜测。

SOMA runtime 只提供 `find(...)` / `containsKey(...)` / empty Row Pipeline / typed missing error 等基础语义。候选不可行、输入不完整、默认 setup 等业务解释属于 loader 或 solver core。

## 8. API usage points

示例必须覆盖以下 generated API 使用点：

- `create()`；
- `reserve(size)`；
- `addBatch(batch)`；
- keyed `fetch(key)`；
- keyed `find(key)` or empty Row Pipeline for optional lookup；
- `containsKey(key)`；
- generated grouped index access, for example `findByOperation(operationKey)`；
- generated machine frontier index access, for example `findByMachine(machineId)`；
- generated order access；
- generated optional presence predicate；
- Row Pipeline `findFirst()` / `firstOrThrow()`；
- Row Pipeline `sorted(comparator)` dynamic sort；
- `mutate(key).field(...).commit()`；
- Row Pipeline `update(updater)` for indicator refresh；
- Row Pipeline `remove()` for selected operation candidate cleanup；
- `delete(key)` if scenario includes single-key deletion；
- typed `ColumnView` read；
- DTO materialization for export。

## 9. Error and lifecycle evidence

Examples smoke 至少证明：

- duplicate key 可观察；
- required lookup missing key / empty required result 可观察；
- optional lookup empty result 不被误报为 runtime failure；
- invalid selector 在 compile/processor 阶段失败；
- schema hash metadata 可读取；
- stale view 可观察；
- released view 可观察；
- view pinned 可观察；
- runtime stats 可读取。

如果某项属于 runtime unit gate 而不适合 E2E smoke，应在 G5 report 中引用对应 G3/G4 evidence，不能静默省略。

## 10. Benchmark smoke boundary

Examples 可以作为 benchmark smoke 的基础，但 benchmark smoke 只能证明：

- benchmark runner 能运行；
- scenario、scale、environment、metrics 能结构化记录；
- release artifact 能完成基本 performance path。

Benchmark smoke 不能单独支撑“更快”“更省内存”或“生产级大规模 hot path”声明。

## 11. 使用方式

- `Job`、`Operation`、`Material`、`Machine` 是 keyed entity state；
- `ProcessingTime`、`SetupTime` 是 keyed lookup data；
- `MachineCandidate` 是 keyed runtime frontier，primary key 是包含 `MachineId` 与 `OperationKey` 的组合；
- `MachineCandidate.by_machine` 支撑当前 machine dispatch，`MachineCandidate.by_operation` 支撑 operation 被选中后的候选清理；
- `MachineCandidate` 不声明 dispatch order，FCFS、SPT 和 setup 相关 indicator 由 solver loop 在 dispatch 前更新，再通过 Row Pipeline `sorted(comparator)` 动态排序；
- `assignedMachine` 是 cross-table key reference，`OperationTable.fetch(key)` materialize `Operation` DTO；
- 上层 dispatch loop 决定 operation release、indicator 计算、FCFS + SPT 排序和跨 table mutation 顺序，SOMA 不拥有调度策略。

## 12. Non-goals

FJSP example V1 不做：

- 完整 APS 求解器；
- 最优性证明；
- CP-SAT propagation；
- 多目标优化；
- 全业务约束覆盖；
- protobuf library dependency inside SOMA core；
- persistence format；
- UI / service integration。

## 13. G5 report

G5 examples report 应记录：

- 使用的 module versions / commit hash；
- generated schema hash；
- Java 8 smoke command；
- request boundary -> loader -> generated tables -> solver core -> exporter -> response boundary 完整路径；
- ordered access evidence；
- grouped index/order source evidence；
- required lookup missing error 与 optional lookup empty result evidence；
- Row Pipeline lazy terminal evidence；
- ColumnView evidence；
- stale/released/view_pinned evidence；
- runtime stats evidence；
- benchmark smoke 是否执行；
- known limitations。
