# FJSP MachineCandidate frontier 临时蓝图

状态：临时蓝图
日期：2026-07-07
适用范围：`soma_java` Java-only V1 设计讨论

## 1. 目标

本文记录 FJSP 构造解场景下，使用 SOMA 保存运行中候选 frontier 的一版临时方案。

核心方向：

- `MachineCandidateTable` 保存 runtime frontier；
- operation release 时，把可加工的 `(MachineId, OperationKey)` 加入 frontier；
- dispatch 时，只对当前 machine 的候选计算动态 indicator；
- dispatch rule 使用手动 dynamic sort，不在 schema 中固化 `byMachineDispatchRule`；
- 某个 operation 被选中后，从 `MachineCandidateTable` 删除该 operation 相关的全部候选；
- SOMA 保存 hot runtime state，solver/application loop 拥有调度策略和跨 table 一致性。

## 2. 设计原则

### 2.1 Frontier 不是每轮临时 workspace

不推荐每轮执行：

```java
candidateRows.replaceAll(buildCandidatesFor(machineId, now));
```

这会把 cross-table 读取、ready time 计算、builder 构造、batch 写入和排序成本隐藏在一次 workspace rebuild 中。

更好的方向是把候选集作为长期 runtime frontier 增量维护：

```text
operation release
  -> processing_times.findByOperation(operationKey)
  -> add (machineId, operationKey) candidates
  -> dispatch 时按 machine 局部计算 indicator
  -> dynamic sort 选择候选
  -> remove candidates for selected operation
```

### 2.2 Schema 只固化稳定访问路径

`MachineCandidateTable` 必须支持：

- 按 machine 查候选：dispatch 时使用；
- 按 operation 查候选：operation 被选中后批量删除。

不强制在 schema 中声明 dispatch rule order。`FCFS + SPT`、`SPT + setup`、`EDD`、`CR` 或加权规则都属于 solver 策略，不是 table 的永久事实。

### 2.3 Indicator 可以小颗粒度计算

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
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSchema;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;
import com.hgtech.soma.annotation.SomaValue;

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
    public MachineId machineId;

    @SomaField
    public OperationKey operationKey;
}

@SomaValue
public final class SetupTimeKey {
    @SomaField
    public MachineId machineId;

    @SomaField
    public SetupFamilyId fromFamily;

    @SomaField
    public SetupFamilyId toFamily;
}

@SomaTable(name = "machines", defaultCapacity = 128)
@SomaOrder(name = "by_available_time", by = {
    @SomaSort("nextAvailableTime"),
    @SomaSort("machineId.value")
})
public final class MachineState {
    @SomaKey
    public MachineId machineId;

    @SomaField
    public long nextAvailableTime;

    @SomaOptional
    public SetupFamilyId lastSetupFamily;
}

@SomaTable(name = "operations", defaultCapacity = 4096)
@SomaIndex(name = "by_job_sequence", fields = {
    "operationKey.jobId.value",
    "sequenceNo"
})
public final class OperationState {
    @SomaKey
    public OperationKey operationKey;

    @SomaField
    public int sequenceNo;

    @SomaField
    public long releaseTime;

    @SomaField
    public long jobReadyTime;

    @SomaField
    public long materialReadyTime;

    @SomaField
    public SetupFamilyId setupFamily;

    @SomaOptional
    public MachineId assignedMachine;

    @SomaOptional
    public Long startTime;

    @SomaOptional
    public Long endTime;
}

@SomaTable(name = "materials", defaultCapacity = 4096)
public final class MaterialState {
    @SomaKey
    public MaterialId materialId;

    @SomaField
    public long readyTime;
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
    public long processingTime;
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
    OperationState operation = operations.fetch(operationKey);
    long baseReadyTime = max3(
        operation.releaseTime,
        operation.jobReadyTime,
        operation.materialReadyTime
    );

    CandidateBatch batch = machineCandidates.newBatch();

    processingTimes.findByOperation(operationKey)
        .forEach(pt -> {
            OperationMachineKey key = pt.operationMachineKey();

            batch.add()
                .setCandidateKey(key)
                .setTargetSetupFamily(operation.setupFamily)
                .setOperationReleaseTime(operation.releaseTime)
                .setJobReadyTime(operation.jobReadyTime)
                .setMaterialReadyTime(operation.materialReadyTime)
                .setBaseReadyTime(baseReadyTime)
                .setProcessingTime(pt.processingTime())
                .setSetupTime(0L)
                .setEffectiveReadyTime(0L)
                .setFcfsValue(0L)
                .setSptValue(0L)
                .setIndicatorReady(false);
        });

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
    SetupFamilyId lastFamily = machine.lastSetupFamilyOr(DEFAULT_SETUP_FAMILY);

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
    OperationMachineKey chosenKey = chosen.candidateKey();
    MachineId machineId = chosenKey.machineId();
    OperationKey operationKey = chosenKey.operationKey();

    long setupStart = chosen.effectiveReadyTime();
    long start = setupStart + chosen.setupTime();
    long end = start + chosen.processingTime();

    operations.mutate(operationKey)
        .setAssignedMachine(machineId)
        .setStartTime(start)
        .setEndTime(end)
        .commit();

    machines.mutate(machineId)
        .setNextAvailableTime(end)
        .setLastSetupFamily(chosen.targetSetupFamily())
        .commit();

    machineCandidates.findByOperation(operationKey).remove();

    releaseNextOperations(operationKey, end);
}
```

`releaseNextOperations(...)` 由 solver/application loop 负责，它应根据 job precedence、material readiness 和业务规则决定下一批 operation 是否 release。

## 5. 审核结论

### 5.1 通过项

- `MachineCandidateTable` 是 keyed frontier，不是每轮临时 `replaceAll` workspace；
- `active` 字段已移除，候选存在即有效，失效通过 `remove()` 表达；
- schema 只固化 `by_machine` 和 `by_operation` 两个稳定访问路径；
- dispatch rule 保持在 solver 策略层，通过 `sorted(comparator)` 表达；
- setup time 在 dispatch 开始时基于 `MachineState.lastSetupFamily` 快照计算；
- comparator 只读取 candidate row 字段，不做 cross-table lookup；
- operation 被选中后，使用 `findByOperation(operationKey).remove()` 删除所有相关候选。

### 5.2 风险和坏味道

- 如果单台 machine frontier 很大，`sorted(comparator)` 会成为热点；届时需要 benchmark 后再考虑 top-k buffer 或稳定 schema order；
- `setupTimes.fetch(setupKey)` 缺失必须有明确业务语义：typed error、默认 0，或候选不可行，不能由 runtime 猜测；
- dispatch loop 连续修改 `operations`、`machines`、`machineCandidates`，SOMA V1 不提供跨 table transaction，一致性由 solver/application loop 保证；
- `releaseNextOperations(...)` 不能退化成全表扫描，应依赖 job sequence、material dependency 或其他 lookup/index；
- `indicatorReady` 只是本轮 machine dispatch 的计算状态，不能被误用成长期业务状态；
- 动态排序不是 maintained `@SomaOrder`，不能宣称与 order sidecar 性能等价。

## 6. 当前建议

该蓝图适合作为后续 FJSP 示例或 benchmark 场景的候选方向。正式化前需要补充：

- generated API 命名 golden；
- `SetupTime` 缺失语义；
- `releaseNextOperations(...)` 的依赖索引设计；
- dynamic sort 与 maintained order source 的 benchmark lane；
- cross-table commit 失败时的 solver-level error handling。
