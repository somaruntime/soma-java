# FJSP runtime state schema 示例

类型：Report / 开发者 current-executable 场景
状态：当前
Owner：`soma-examples` output
受众：使用或维护当前 FJSP runtime-state example 的开发者
适用版本：最后 implementation-affecting baseline `a137b10`
输入事实源：当前 example source、[FJSP Blueprint](../../docs/blueprints/fjsp-runtime-state-blueprint.md)、Design 与 G5 evidence
事实范围：FJSP data role、Access Pattern Card、schema source 和 runtime-state coverage
非事实范围：dispatch E2E flow、SOMA public contract 和 benchmark result
最后审查日期：2026-07-21

> 本文记录当前 executable example，不拥有目标设计。文中的“必须/应当”只复述所链接 Blueprint、Design 或现有验证要求；发生冲突时以正式 Owner 为准。

## 1. 文档定位

本文是 FJSP runtime-state schema 的当前可执行示例。它说明当前代码如何把 input facts、working state、result facts、owned candidate-machine input 和 keyed MachineCandidate frontier 映射为 SOMA schema。

Dispatch/commit/error/G5 evidence 由 [FJSP E2E 场景契约](fjsp-e2e-scenario.md) 拥有。Schema/API/runtime semantics 以对应 owner contract 为准。

## 2. 场景边界

FJSP 示例表达 `FCFS + SPT` 一类构造解过程中的 runtime state：

```text
jobs / operation definitions with candidate-machine children / machines / setup times
  -> operation release
  -> machine_candidates keyed runtime frontier
  -> per-machine indicator update
  -> dynamic sorted dispatch
  -> assignment mutation / frontier cleanup
  -> detached schema objects / boundary DTO export
```

不表达完整 APS、CP-SAT、局部搜索、重排优化或多目标优化。每个 `OperationDefinition` 独占一个 `candidateMachines` dense child，`SetupTime` 是 keyed lookup table；`MachineCandidate` 是 keyed runtime frontier，用于保存 operation release 后可加工的 `(MachineId, OperationKey)` 候选。它不是每轮临时 `replaceAll(batch)` workspace，也不在 schema 中固化 dispatch rule order。Flat `ProcessingTime(operationKey, machineId)` 只保留为相同语义的 benchmark baseline，不是 canonical input model。

### 2.1 Access Pattern Card

| Core path | Cardinality/working set | Access/mutation mix | Allocation/evidence boundary |
|---|---|---|---|
| operation candidate child | operation count × empty/typical/high candidate-machine count | release 时 parent-key child scan；input 后只读 | live child scan 不 materialize；与 flat grouped-index baseline 比较 locality 与 child-instance overhead |
| `MachineCandidate` frontier | current released-unscheduled pairs；记录 by_machine/by_operation group size | batch add、exact-group update、dynamic sort、grouped swap-remove | `IndexBuffer` retained bytes、exact-index probe/collision/rehash、steady-state allocation/op 分开 |
| machine/setup/assignment | machine/operation/setup-matrix scale | application minimum heap、point lookup/mutate、result insert | heap size/update/stale drop、HashKeySpace load/collision、split lookup/export 分开；heap不保存SOMA Index |

Fixture/benchmark 必须补充 hot columns、touched bytes/working set、selector selectivity、mutation/read ratio、JIT warmup/forks、stats mode 和 export frequency；这些值不进入 Schema/hash。

## 3. Schema source 示例

```java
@SomaSchema(
    name = "fjsp_runtime_state",
    generatedPackage = "com.example.fjsp.state.generated",
    version = "1"
)
package com.example.fjsp.state;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaSchema;
import com.hgtech.soma.annotation.SomaTable;
import com.hgtech.soma.annotation.SomaUnique;
import com.hgtech.soma.annotation.SomaValue;
import java.util.List;

public enum MachineState {
    READY,
    DOWN
}

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
    OperationKey operationKey;

    @SomaField
    MachineId machineId;
}

@SomaValue
public class SetupFamilyPair {
    @SomaField
    SetupFamilyId fromFamily;

    @SomaField
    SetupFamilyId toFamily;
}

@SomaValue
public class SetupTimeKey {
    @SomaField
    MachineId machineId;

    @SomaField
    SetupFamilyPair familyPair;
}

@SomaTable(name = "job_definitions", defaultCapacity = 1024)
public final class JobDefinition {
    @SomaKey
    public JobId jobId;

    @SomaField
    public long inputOrder;

    @SomaField
    public long dueMinute;

    @SomaField
    public int operationCount;

}

@SomaTable(name = "job_runtime_states", defaultCapacity = 1024)
public final class JobRuntimeState {
    @SomaKey
    public JobId jobId;

    @SomaField
    @SomaDefault("0")
    public int nextSequenceNo;
}

@SomaTable(name = "job_results", defaultCapacity = 1024)
public final class JobResult {
    @SomaKey
    public JobId jobId;

    @SomaField
    public long completedMinute;

    @SomaField
    public long tardinessMinutes;
}

@SomaTable(name = "candidate_machine_definitions", defaultCapacity = 8)
public final class CandidateMachineDefinition {
    @SomaField
    public MachineId machineId;

    @SomaField
    public long processingMinutes;
}

@SomaTable(name = "operation_definitions", defaultCapacity = 4096)
@SomaUnique(name = "by_job_sequence", fields = {
    "operationKey.jobId.value",
    "sequenceNo"
})
public final class OperationDefinition {
    @SomaKey
    public OperationKey operationKey;

    @SomaField
    public int sequenceNo;

    @SomaField
    public long releaseMinute;

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
    public long jobReadyMinute;

    @SomaField
    public long materialReadyMinute;
}

@SomaTable(name = "operation_assignments", defaultCapacity = 4096)
public final class OperationAssignment {
    @SomaKey
    public OperationKey operationKey;

    @SomaField
    public MachineId assignedMachine;

    @SomaField
    public long setupStartMinute;

    @SomaField
    public long setupMinutes;

    @SomaField
    public long startMinute;

    @SomaField
    public long processingMinutes;

    @SomaField
    public long endMinute;
}

@SomaTable(name = "machines", defaultCapacity = 128)
public final class Machine {
    @SomaKey
    public MachineId machineId;

    @SomaField
    @SomaDefault("READY")
    public MachineState state;

    @SomaField
    @SomaDefault("0")
    public long availableFromMinute;

    @SomaField
    @SomaOptional
    public SetupFamilyId lastSetupFamily;
}

@SomaTable(name = "setup_times", defaultCapacity = 1024)
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

## 4. Runtime state coverage

示例 runtime state 至少覆盖：

- `OperationDefinition` keyed input table 与 `candidateMachines` dense child；
- `OperationRuntimeState` keyed working-state table；
- `OperationAssignment` keyed result table；
- `JobDefinition` keyed input table、`JobRuntimeState` keyed working-state table 与 `JobResult` keyed result table；
- `Machine` keyed table；
- `MachineCandidate` keyed runtime frontier table；
- `SetupTime` keyed lookup table；
- `OperationDefinition.by_job_sequence` secondary unique access；
- enum；
- value key；
- nested value；
- optional field；
- table-level index；
- exact index 与 unique-current invariant；
- 物理遍历不保证业务顺序；
- 显式 dynamic sort 与 `IndexBuffer` candidate freeze；
- Row Pipeline lazy terminal；
- ColumnView。

当前 import preflight 还验证 job sequence 连续完整、operation candidate machine 不重复、全部引用存在、required setup transition matrix 完整、时间非负且 worst-case checked arithmetic 不溢出。Frontier release 使用 reusable Batch 和 bounded primitive machine scratch，publish 完整 candidate group 后 application heap 才刷新 membership；未 refresh row 保持 `indicatorReady=false` 且 FCFS/SPT 为中性 `0`。

## 5. 与正式设计和当前实现的关系

- Annotation 与 generated access 的规范性语义以 [Schema 与生成 API](../../docs/design/schema-and-generated-api.md) 为准；当前精确 surface 由 [可执行契约地图](../../docs/implementation-map/executable-contract-map.md) 登记的代码、golden 与 validator 拥有；
- Child materialization 以 [物化边界](../../docs/design/materialization-boundary.md) 为准；
- MachineCandidate frontier 是 FJSP scenario decision，不是所有 candidate set 的默认结构。

## 6. 非目标

本文不定义 solver feasibility、dispatch policy、cross-table transaction、runtime implementation 或性能优势。
