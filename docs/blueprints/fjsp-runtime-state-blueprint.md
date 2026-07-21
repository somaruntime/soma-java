# FJSP runtime state 蓝图

类型：Blueprint

状态：正式

Owner：FJSP 目标场景

事实范围：FJSP 构造解中 SOMA 的目标角色、使用者体验、数据分层和核心访问流程

非事实范围：调度算法正确性、精确公共 API 契约、当前实现状态、benchmark 结论和 release readiness

设计约束入口：[Schema 与生成 API](../design/schema-and-generated-api.md)、[Table、存储与访问](../design/table-storage-and-access.md)、[Correctness 与 failure](../design/correctness-and-failure.md)、[Runtime Plan 与可观测性](../design/runtime-plan-and-observability.md)、[性能模型](../design/performance-model.md)

最后审查日期：2026-07-21

## 1. 场景目标

本蓝图站在 FJSP solver 开发者的视角，展示如何用 SOMA 保存构造解过程中的 input facts、working state、增量 candidate frontier 和 result facts，并在普通 Java 代码中完成 release、select、commit 和 successor release。

它要回答的不是“SOMA 内部用了什么数组或 hash 算法”，而是：

- Schema 应当怎样表达 job、operation、machine、setup 和 candidate；
- annotation processor 会给 solver 提供哪些领域化接口；
- `findByMachine -> update` 与 `findByMachine -> filter -> sorted -> terminal` 如何组合；
- 哪些数据属于 SOMA，哪些策略和队列仍属于 solver；
- readable reference path 与 allocation-aware hot path 的边界在哪里。

除明确标为 application-owned 的 queue/solver helper 外，示例名称参考当前可执行 FJSP 场景，以便蓝图能够被代码和测试验证。未标为算法伪代码的片段按目标 Java 8 使用代码审查：允许省略 import 和外围 owner，但必须具有明确的时间单位、total comparator、失败边界和 allocation 口径。精确契约仍由 [Schema 与生成 API](../design/schema-and-generated-api.md) 及 [Table、存储与访问](../design/table-storage-and-access.md) 拥有。当前代码是否已经达到目标由 Conformance 判断，本蓝图不作当前能力声明。

## 2. 使用者最终看到的求解循环

从 solver 使用者视角，目标调用形态接近下面的代码。`machineQueue` 和 `selectNextEligibleMachine(...)` 由 application 拥有，不是 SOMA 生成接口。

```java
while (assignments.size() < totalOperationCount) {
    MachineAvailability event =
        selectNextEligibleMachine(machineQueue, frontier);
    Machine machine = machines.fetch(event.machineId());
    MachineId machineId = machine.machineId;

    refreshIndicators(machineId, machine.availableFromMinute,
        machine.lastSetupFamily);

    MachineCandidate chosen = frontier.findByMachine(machineId)
        .filter(row -> row.indicatorReady())
        .sorted(byFcfsThenSpt)
        .firstOrThrow();

    AssignmentResult committed = commit(machineId, chosen);
    ReleasedMachines newlyReleased = releaseSuccessorIfAny(
        chosen.candidateKey.operationKey,
        committed.endMinute());
    refreshQueueMembership(machineQueue, machines, frontier,
        machineId, newlyReleased);
}
```

这段代码表达三个边界：

1. SOMA 保存 machine、operation、frontier 和 assignment 的当前事实；
2. solver 拥有 dispatch comparator、machine event queue、跨表提交顺序和失败处置；
3. pipeline 的每个 stage 只处理前一个 stage 留下的 candidate Index。

`selectNextEligibleMachine(...)` 必须丢弃 generation/available-time 已与 `Machine` 不一致、machine state 当前不可调度或已无 candidate 的 stale heap entry；若 assignment 尚未完成而 heap 已无 eligible machine，应返回明确的 infeasible/deadlock outcome，不能空转。`releaseSuccessorIfAny(...)` 先完成 successor progress 与 frontier publish，再返回本次新增候选涉及的 machine set；`refreshQueueMembership(...)` 按 `MachineId` 去重 committed/newly-released machines，以 `Machine` 当前 availability/state 更新 indexed heap，并且只激活仍有 candidate 的 machine。三个 helper 都属于 application，不能把 queue 与 Table 的同步伪装成 SOMA 原子操作。

可读性优先的 `firstOrThrow()` 会物化一个 detached `MachineCandidate`。真正的 hot loop 可以改用后文的 `IndexSnapshot + ColumnView` 形态，避免物化完整 row，但仍需单独计量显式 snapshot 成本。

## 3. 数据角色与 Table 选择

| 数据 | 角色 | Table 形态 | 稳定 identity / ownership | 主要访问 |
|---|---|---|---|---|
| `JobDefinition` | input fact | keyed root | `JobId` | key fetch、按输入顺序显式 sort |
| `OperationDefinition` | input fact | keyed root | `OperationKey` | key fetch、by-job-sequence exact access |
| `CandidateMachineDefinition` | input fact | dense child | parent operation owns | operation-local packed scan |
| `Machine` | working state | keyed root | `MachineId` | point fetch/mutate、scan |
| `OperationRuntimeState` | working state | keyed root | `OperationKey` | point fetch/mutate |
| `SetupTime` | input lookup | keyed root | machine/from-family/to-family | exact point lookup |
| `MachineCandidate` | incremental frontier | keyed root | `(operation, machine)` | by-machine update/select、by-operation remove |
| `OperationAssignment` | result fact | keyed root | `OperationKey` | append、point fetch、final export |

`CandidateMachineDefinition` 是 operation input 的组成部分，生命周期严格属于 parent，因此适合 dense child。`MachineCandidate` 不同：它会在多个 dispatch round 之间存活，需要按 machine 和 operation 两个方向访问，并具有稳定 `(operation, machine)` identity，因此是独立 keyed frontier。

frontier 中“存在某个 candidate row”就表示该候选当前有效。application 不再维护一份与 row presence 等价的 active bitmap、DTO list 或 object graph shadow。

## 4. Access Pattern Card

| 操作 | Source | 候选规模 | 后续 stage | Mutation / structural effect |
|---|---|---:|---|---|
| release operation | operation key + dense child | 单个 operation 的可选机器数 | batch build | frontier append |
| refresh machine indicators | `by_machine` exact group | 该 machine 的 released candidates | update | in-place field mutation |
| choose candidate | `by_machine` exact group | exact group 经 filter 后的子集 | total sort + top 1 | 无结构变化 |
| commit assignment | operation/machine keys | 单行或单 group | typed writes | assignment append、machine mutate |
| retire operation | `by_operation` exact group | 该 operation 的全部 machine candidates | remove | swap-remove + exact relocation |
| release successor | `by_job_sequence` exact group | 预期 0 或 1 | point update + release | frontier append |
| export result | assignment rows | 全部已分配 operations | optional explicit order | detached materialization |

蓝图不为 FCFS、SPT、available time 或其他动态指标建立 maintained order。它们是每次 dispatch 重新计算或读取的值；只有 `by_machine`、`by_operation` 和 `by_job_sequence` 这类稳定 equality access 才进入 Schema。

## 5. Schema：namespace 与 Value Object

### 5.1 Schema namespace

```java
@SomaSchema(
    name = "fjsp_runtime_state",
    generatedPackage = "com.hgtech.soma.examples.fjsp.schema.generated",
    version = "1")
package com.hgtech.soma.examples.fjsp.schema;
```

### 5.2 领域 identity

```java
@SomaValue
public class MachineId {
    @SomaField long value;
}

@SomaValue
public class OperationKey {
    @SomaField JobId jobId;
    @SomaField OperationId operationId;
}

@SomaValue
public class OperationMachineKey {
    @SomaField OperationKey operationKey;
    @SomaField MachineId machineId;
}
```

这些 Value Object 让业务代码继续使用 `MachineId`、`OperationKey`，同时允许生成代码把 leaf field flatten 到 primitive column 和 locator key 中。它们具有 compiler-defined immutable/value-equality 语义；业务不需要把 identity 降级成无类型的 `long`。

## 6. Schema：input 与 working state

### 6.1 Operation input 及其 owned child

```java
@SomaTable(name = "operation_definitions", defaultCapacity = 4096)
@SomaUnique(name = "by_job_sequence", fields = {
    "operationKey.jobId.value", "sequenceNo"})
public final class OperationDefinition {
    @SomaKey public OperationKey operationKey;
    @SomaField public int sequenceNo;
    @SomaField public long releaseMinute;
    @SomaField public SetupFamilyId setupFamily;

    @SomaChild(initialCapacity = 8)
    public List<CandidateMachineDefinition> candidateMachines;
}

@SomaTable(name = "candidate_machine_definitions", defaultCapacity = 8)
public final class CandidateMachineDefinition {
    @SomaField public MachineId machineId;
    @SomaField public long processingMinutes;
}
```

`candidateMachines` 在 Schema 中使用 `List` 表达 detached materialization shape 和 ownership，但 live runtime storage 仍是 parent-owned packed child table，不是 Java `List<Row>`。

### 6.2 Machine 与 operation progress

```java
@SomaTable(name = "machines", defaultCapacity = 128)
public final class Machine {
    @SomaKey public MachineId machineId;
    @SomaField @SomaDefault("READY") public MachineState state;
    @SomaField @SomaDefault("0") public long availableFromMinute;
    @SomaField @SomaOptional public SetupFamilyId lastSetupFamily;
}

@SomaTable(name = "operation_runtime_states", defaultCapacity = 4096)
public final class OperationRuntimeState {
    @SomaKey public OperationKey operationKey;
    @SomaField public long jobReadyMinute;
    @SomaField public long materialReadyMinute;
}
```

optional family 使用 presence + value 语义，不使用任意 sentinel。这里必须由业务明确规定：absent 表示“初始加工不需要 setup”；如果实际问题使用默认 setup family，就应在 import 时写入该 family，不能把 absent 偷换成任意默认值。machine available-time 的跨轮顺序由 solver 的最小堆维护，`Machine` table 仍是 working-state 事实 Owner。由于 canonical loop 不按 `Machine.state` 做 exact group access，目标 schema 不为它维护未使用的 secondary index。

### 6.3 Setup lookup

```java
@SomaValue
public class SetupTimeKey {
    @SomaField MachineId machineId;
    @SomaField SetupFamilyPair familyPair;
}

@SomaTable(name = "setup_times", defaultCapacity = 1024)
public final class SetupTime {
    @SomaKey public SetupTimeKey setupTimeKey;
    @SomaField public long setupMinutes;
}
```

完整 setup identity 由 primary key 表达，canonical refresh 也只做完整 key lookup，因此目标 schema 不额外维护 secondary index。若另一个已证明的 operation 确实需要稳定的 machine/to-family group，再为该 access pattern 声明 `@SomaIndex`；不能因为字段“可能会查”就预付每次 mutation 的维护成本。

## 7. Schema：增量 frontier 与 result

### 7.1 Machine candidate frontier

```java
@SomaTable(name = "machine_candidates", defaultCapacity = 8192)
@SomaIndex(name = "by_machine", fields = {
    "candidateKey.machineId.value"})
@SomaIndex(name = "by_operation", fields = {
    "candidateKey.operationKey.jobId.value",
    "candidateKey.operationKey.operationId.value"})
public final class MachineCandidate {
    @SomaKey public OperationMachineKey candidateKey;
    @SomaField public SetupFamilyId targetSetupFamily;
    @SomaField public long operationReleaseMinute;
    @SomaField public long jobReadyMinute;
    @SomaField public long materialReadyMinute;
    @SomaField public long baseReadyMinute;
    @SomaField public long processingMinutes;
    @SomaField public long setupMinutes;
    @SomaField public long effectiveReadyMinute;
    @SomaField public long fcfsValue;
    @SomaField public long sptValue;
    @SomaField public boolean indicatorReady;
}
```

Schema 不声明 FCFS/SPT maintained order：它们是一次 dispatch 的 comparator 输入，不是 table 的永久物理顺序。Canonical policy 在 `indicatorReady=true` 时保持 `fcfsValue == effectiveReadyMinute`，并把 `sptValue` 定义为 `setupMinutes + processingMinutes`；二者都是可由 candidate 与 machine state 重建的 derived indicator。所有输入时间非负且加法必须 overflow-safe。`@SomaIndex` group 中保存的是 current Index access structure；swap-remove 后 runtime 必须同步修复 relocation。

### 7.2 Assignment result

```java
@SomaTable(name = "operation_assignments", defaultCapacity = 4096)
public final class OperationAssignment {
    @SomaKey public OperationKey operationKey;
    @SomaField public MachineId assignedMachine;
    @SomaField public long setupStartMinute;
    @SomaField public long setupMinutes;
    @SomaField public long startMinute;
    @SomaField public long processingMinutes;
    @SomaField public long endMinute;
}
```

每个 operation 只能产生一个 assignment，因此 result table 使用 `OperationKey` 作为 primary identity。machine 或 operation state 中不再复制一份等价 assignment 结果。

## 8. 生成接口的使用地图

annotation processor 应让 solver 获得类似下面的 schema-specific API：

| 生成接口 | FJSP 用法 |
|---|---|
| `OperationDefinitionTable.fetch/rowIndexOf` | 找到 operation input |
| `OperationDefinitionTable.findByJobSequence` | 通过 secondary unique 找 0/1 个 successor operation |
| `OperationDefinitionTable.candidateMachines(key)` | 获取 live dense child facade |
| `MachineTable.fetch/mutate` | 读取和推进 machine working state |
| `SetupTimeTable.fetch/rowIndexOf` | setup exact lookup |
| `MachineCandidateTable.findByMachine` | refresh/select 当前 machine group |
| `MachineCandidateTable.findByOperation` | commit 后删除 operation group |
| `MachineCandidateRows.Comparator` | typed total comparator |
| `OperationAssignmentBatch` | typed result append |
| typed `ColumnView` | 通过 Index 读取 primitive hot fields |

这张表是使用导航，不拥有精确 signature。生成接口不得要求 solver 构造 generic expression tree、反射访问 field name，或把 runtime row 转成 `Map<String,Object>`。

## 9. 建立一个 FJSP instance

一个求解实例应当是所有 root tables 的 application lifecycle owner：

```java
RuntimePlan defaults = OperationDefinitionTable.defaultRuntimePlan();
RuntimePlan plan = defaults.toBuilder()
    .maximumAggregateStorageBytes(memoryBudget)
    .maximumOwnershipTableInstances(ownershipBudget)
    .build();

MachineTable machines = MachineTable.create(plan);
OperationDefinitionTable definitions = OperationDefinitionTable.create(plan);
OperationRuntimeStateTable operationStates =
    OperationRuntimeStateTable.create(plan);
SetupTimeTable setupTimes = SetupTimeTable.create(plan);
MachineCandidateTable frontier = MachineCandidateTable.create(plan);
OperationAssignmentTable assignments =
    OperationAssignmentTable.create(plan);

machines.reserve(machineCount);
definitions.reserve(operationCount);
operationStates.reserve(operationCount);
setupTimes.reserve(setupTimeCount);
frontier.reserve(frontierCapacity);
assignments.reserve(operationCount);
```

input 通过 typed Batch 分块导入。只有全部 import、key/unique validation、job/operation/machine 等跨表引用完整性、每个 job 的 sequence 连续性、同一 operation 下 candidate machine 不重复、非负时间检查和 required setup lookup 完整性检查都成功后 instance 才进入 solver。所有时间加法使用 `Math.addExact` 或等价 checked helper。构建失败时 application aggregate owner 在 `finally`/`close` 路径按逆序释放已经创建的 roots，不能把半导入状态交给求解循环。

## 10. Release：从 operation child 增量建立 frontier

可读性优先的写法可以 materialize `OperationDefinition` 及其 child `List`，但这会分配 detached object graph：

```java
OperationDefinition operation = definitions.fetch(operationKey);
for (CandidateMachineDefinition candidate : operation.candidateMachines) {
    // build frontier rows
}
```

hot path 应通过 live child facade 扫描当前 operation 的候选机：

```java
CandidateMachineDefinitionTable eligible =
    definitions.candidateMachines(operationKey);
MachineCandidateBatch batch = reusableFrontierBatch;
batch.clear();

final long baseReady = Math.max(operationRelease,
    Math.max(jobReady, materialReady));

eligible.forEach(candidate -> batch.addValues(
    new OperationMachineKey(operationKey,
        new MachineId(candidate.machineIdValue())),
    targetSetupFamily,
    operationRelease,
    jobReady,
    materialReady,
    baseReady,
    candidate.processingMinutes(),
    0L,
    baseReady,
    0L,
    0L,
    false));

frontier.addBatch(batch);
```

每个 operation 只在变为 released 时增量加入 frontier。`reusableFrontierBatch` 由 solver instance 按单个 operation 的 candidate-machine 上限准入并同步复用；`addBatch` 完成 detached copy 后才可再次 `clear()`。当前 generated value-key `addValues` 仍可能为每个 candidate 构造 `OperationMachineKey`/`MachineId`，因此这里是“无 child materialization、复用 Batch storage”的路径，不冒充零分配。是否需要 flattened primitive batch writer 必须由 allocation benchmark 触发正式 Design。

`fcfsValue`/`sptValue` 在 `indicatorReady=false` 时使用中性占位值，只有 refresh 成功后才可进入 comparator。solver 不在每轮调度前重建全量 machine-operation 笛卡尔积。

## 11. Refresh：只更新某台 machine 的候选

### 11.1 可读性优先路径

```java
frontier.findByMachine(machineId).update(row -> {
    long setup = lastFamily == null ? 0L : setupTimes.fetch(
        new SetupTimeKey(machineId,
            new SetupFamilyPair(lastFamily,
                new SetupFamilyId(row.targetSetupFamilyValue()))))
        .setupMinutes;
    long effectiveReady = Math.max(
        row.baseReadyMinute(), machineReady);

    row.setSetupMinutes(setup);
    row.setEffectiveReadyMinute(effectiveReady);
    row.setFcfsValue(effectiveReady);
    row.setSptValue(Math.addExact(setup, row.processingMinutes()));
    row.setIndicatorReady(true);
});
```

这段代码清楚展示领域意图，但每个 candidate 的 `SetupTimeKey` 和 detached `SetupTime` 都可能形成临时对象，因此只能作为 reference path。

### 11.2 allocation-aware 路径

```java
try (LongColumnView setupMinutes = setupTimes.setupMinutesColumn()) {
    frontier.findByMachine(machineId).update(row -> {
        long setup = lastFamilyPresent
            ? setupMinutes.getLong(setupTimes.rowIndexOf(
                machineId.value,
                lastFamilyValue,
                row.targetSetupFamilyValue()))
            : 0L;
        long effectiveReady = Math.max(
            machineReady, row.baseReadyMinute());

        row.setSetupMinutes(setup);
        row.setEffectiveReadyMinute(effectiveReady);
        row.setFcfsValue(effectiveReady);
        row.setSptValue(Math.addExact(setup, row.processingMinutes()));
        row.setIndicatorReady(true);
    });
}
```

这条路径使用 flattened key leaf 和 primitive column，不为每个候选物化 setup row。`findByMachine` 必须直接进入当前 exact group；如果全表有 100,000 个候选而该 machine 只有 10,000 个，update 只处理这 10,000 个。

## 12. Select：在当前 group 内筛选和排序

### 12.1 total comparator

```java
MachineCandidateRows.Comparator byFcfsThenSpt = (left, right) -> {
    int compared = Long.compare(left.fcfsValue(), right.fcfsValue());
    if (compared != 0) return compared;

    compared = Long.compare(left.sptValue(), right.sptValue());
    if (compared != 0) return compared;

    compared = Long.compare(
        left.candidateKeyOperationKeyJobIdValue(),
        right.candidateKeyOperationKeyJobIdValue());
    if (compared != 0) return compared;

    compared = Long.compare(
        left.candidateKeyOperationKeyOperationIdValue(),
        right.candidateKeyOperationKeyOperationIdValue());
    if (compared != 0) return compared;

    return Long.compare(left.candidateKeyMachineIdValue(),
        right.candidateKeyMachineIdValue());
};
```

comparator 覆盖完整领域 tie-break，因而不依赖 table 的物理遍历顺序。它只读取 candidate row 中已经准备好的指标，不在比较过程中访问 setup table 或执行 mutation。

### 12.2 可读性优先 terminal

```java
MachineCandidate chosen = frontier.findByMachine(machineId)
    .filter(row -> row.indicatorReady())
    .sorted(byFcfsThenSpt)
    .firstOrThrow();
```

`firstOrThrow()` 在 empty result 时产生 typed failure；成功时返回 detached schema object。它适合作为清晰的 reference path，但每轮至少会物化一个 row。

### 12.3 低物化 terminal

```java
IndexSnapshot selected = frontier.findByMachine(machineId)
    .filter(row -> row.indicatorReady())
    .sorted(byFcfsThenSpt)
    .limit(1)
    .rowIndexes();

if (selected.size() != 1) {
    throw new IllegalStateException("dispatch requires one candidate");
}

int row = selected.indexAt(0);
try (LongColumnView jobIds =
         frontier.candidateKeyOperationKeyJobIdValueColumn();
     LongColumnView operationIds =
         frontier.candidateKeyOperationKeyOperationIdValueColumn();
     LongColumnView targetFamilies =
         frontier.targetSetupFamilyValueColumn();
     LongColumnView effectiveReady =
         frontier.effectiveReadyMinuteColumn();
     LongColumnView processing = frontier.processingMinutesColumn();
     LongColumnView setup = frontier.setupMinutesColumn()) {
    long jobId = jobIds.getLong(row);
    long operationId = operationIds.getLong(row);
    long targetSetupFamily = targetFamilies.getLong(row);
    long effectiveReadyMinute = effectiveReady.getLong(row);
    long processingMinutes = processing.getLong(row);
    long setupMinutesValue = setup.getLong(row);
    // 构造一个只含 commit 所需 primitive facts 的 application-local value
}
```

这条路径避免完整 `MachineCandidate` materialization，但 `IndexSnapshot` 是显式复制的 public 结果，application-local commit value 也可能产生一次小对象分配。Caller 必须在这一个同步只读批次中立即完成 ColumnView 读取，期间不修改 frontier，随后丢弃 snapshot；任何 frontier mutation/lifecycle 变化都会使其失效。`requireCurrent` 只可作为测试、调试或边界防御。Blueprint 不把该路径写成零分配；是否需要新的 callback-scoped first terminal，必须经过独立 Design 和 benchmark，而不是在场景代码中暗自引入。

## 13. Candidate Index 的逐级缩减

对一条 100,000-row frontier，目标执行形态是：

```text
MachineCandidateTable             100,000 current Index
  -> findByMachine(machineId)       10,000 current Index
  -> filter(indicatorReady)          1,000 current Index
  -> sorted(total comparator)         1,000 current Index, reordered
  -> limit(1)                             1 current Index
  -> terminal                              detached row or IndexSnapshot
```

runtime 可以用 table-local、可复用的 primitive `IndexBuffer` 承载 L1/L2/L3，并在 terminal 成功或失败后 reset。`IndexBuffer` 不进入 generated public model；`IndexSnapshot` 只服务紧接着的同步只读消费批次，需要跨 operation 保存候选引用时 solver 必须保存 `@SomaKey` identity。

任何 stage 都不得把当前 group 扩展回全表。`@SomaIndex` 也不得使用 dirty-on-write、read-time full rebuild/sort 的 sidecar 语义。

## 14. Machine event queue 的边界

machine available-time 是跨 dispatch round 持续变化的业务优先级，适合 application-owned 最小堆。Canonical 方案使用 `MachineId -> heap slot` 的 indexed heap，使 machine 可以在没有 released candidate 时 inactive、在新 operation release 时重新 activate：

```java
MachineAvailabilityQueue machineQueue =
    new IndexedMachineAvailabilityQueue(machineIds, byTimeThenMachineId);
```

queue 只保存由 machine state 派生的选择顺序，`MachineTable` 保存权威 machine state。solver 先从 heap 取出 machine，再以 table 当前值校验 availability；commit 后先更新 table，再更新 inactive heap slot，只有该 machine 仍有候选时才重新 activate。release 新 operation 时，frontier publish 成功后激活涉及的 machine。

普通 Java 8 `PriorityQueue` 也可以作为较简单的 reference 实现，但 queued entry 的排序字段必须 immutable；`PriorityQueue` 不提供 decrease-key，不能原地修改已经入队的 availability。采用重复 immutable entry 时必须带 generation/version 并在 pop 时淘汰 stale entry。不能让 `machines.rows().sorted(...)` 冒充跨轮 persistent event queue；一次显式 sort 只适合小规模 reference path 或 benchmark 对照。

## 15. Commit、retire 与 successor release

一次 assignment 的 application-owned 提交顺序可以是：

```java
OperationAssignmentBatch assignment = reusableAssignmentBatch;
assignment.clear();
assignment.addValues(
    operationKey, machineId, setupStart, setupMinutes,
    start, processingMinutes, end);
assignments.addBatch(assignment);

machines.mutate(machineId)
    .setAvailableFromMinute(end)
    .setLastSetupFamily(targetSetupFamily)
    .commit();

RemoveResult retired = frontier
    .findByOperation(operationKey)
    .remove();

ReleasedMachines newlyReleased =
    releaseSuccessorIfAny(operationKey, end);
```

`releaseSuccessorIfAny(...)` 只在 successor 存在时更新其 `OperationRuntimeState.jobReadyMinute` 并调用 release；最后一个 operation 返回空 machine set。返回值是 solver-instance-owned、可复用的 bounded primitive machine set，不要求每次 commit 构造 boxed collection。外部 indexed heap 只在全部 SOMA 写入成功后由主循环刷新。初始化 first operations 时使用同一 release/queue-membership 协议，避免 table frontier 已有候选而 heap 尚未激活的分裂状态。

`reusableAssignmentBatch` 是 solver-instance-owned single-row staging；`addBatch` 完成 copy 后才清空复用，避免为每个 operation 构造新的 Batch。它不改变 assignment append、machine mutate、frontier retire、successor release 和 external heap refresh 之间非事务性的事实。

`setupStart`、`start` 和 `end` 分别通过 checked `max`/`Math.addExact` 计算；negative duration、overflow 或 required setup lookup missing 都是 malformed problem/solver failure，不能通过 wraparound 继续调度。

`findByOperation(...).remove()` 只删除已分配 operation 的 candidate group。packed table 使用 swap-remove，runtime 同步修复 primary locator、`by_machine` 和 `by_operation`；solver 不能依赖删除前后的物理 Index 或遍历顺序。

SOMA V1 不提供跨 root table transaction。assignment、machine、frontier、job progress 和 external heap 的提交顺序由 solver 定义；任一步失败后，application 停止使用并丢弃当前 FJSP instance，或按它自己已经证明的恢复协议处理，不能假设 SOMA 自动跨表回滚。

## 16. Materialization、结果和 lifecycle

求解结束后，assignment 可以在显式预算下导出：

```java
List<OperationAssignment> result =
    assignments.fetchAll(MaterializationBudget.defaults());
```

返回值是 detached object list；后续 DTO、JSON 或 UI mapping 仍属于 application。若需要稳定输出顺序，solver/exporter 必须按领域 key 显式排序，不能依赖 assignment table 的当前物理顺序。

整个求解实例应由一个 lifecycle owner 释放所有 root tables。owned child 随 parent 释放，不能单独 share、reparent 或在 parent 之后继续访问。

## 17. 蓝图必须由什么证明

场景只有在下面的 evidence 同时成立时，才能说明 SOMA 确实服务了这份蓝图：

- compile/golden test 证明以上 annotation（包括 `by_job_sequence` secondary unique、无未使用 machine/setup selector）能生成所需 typed API；
- executable FJSP scenario 覆盖 import、release、refresh、select、commit、retire、successor release 和 export；
- invariant test 证明 primary/exact access 在 update 与 swap-remove 后保持一致；
- selection trace 证明 exact group、filter 和 sort 只处理逐级缩减的 Index；
- allocation lane 分别测量 readable materialization、Row Pipeline、`IndexSnapshot`、ColumnView 和 external DTO mapping；
- benchmark 比较 incremental frontier 与 per-round rebuild，并记录吞吐、allocation/op、bytes/op、Young/Full GC 和 pause；
- failure test 证明单 table mutation 不暴露部分提交，并验证 solver 对跨表失败的停止/丢弃策略；
- deterministic test 证明 total comparator 与显式 export order 不依赖物理遍历顺序。

## 18. 场景非目标

本蓝图不要求 SOMA：

- 实现 FCFS、SPT 或任何 FJSP 调度算法；
- 拥有 machine event queue、跨表 transaction 或 solver rollback；
- 自动维护 dynamic order、range index 或全局 top-k structure；
- 把所有 solver-local 临时量都建模成 table；
- 用 detached `OperationDefinition`/child `List` 或 `MachineCandidate` object graph 充当 hot storage；
- 对当前实现尚未经过测试和 benchmark 的行为作性能或成熟度声明。
