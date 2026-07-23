# SOMA Java 产品蓝图

类型：Blueprint

状态：正式

Owner：SOMA Java 产品蓝图

事实范围：目标用户、目标形态、目标使用体验、核心价值与非目标

非事实范围：精确 API 契约、实现算法、当前支持状态、性能结论和 release readiness

设计约束入口：[Design 导航、层次与 Owner](../design/README.md)

最后审查日期：2026-07-23

## 1. 这份蓝图面向谁

这份蓝图从使用者视角说明 SOMA Java 希望成为什么，以及一段真实业务代码使用它时应当是什么感觉。使用者不需要先理解 runtime 内部的 column、bucket 或 compaction 算法，也应当能够完成 Schema 建模、生成代码、导入数据、查询、更新、选择和导出。

文中的代码同时承担两项职责：展示目标体验，并让目标尽量接近可编译、可验证的 Java 8 用法。未特别标为伪代码的片段都按“目标使用代码”审查：允许省略 import、外围 owner 和普通业务 helper，但不能依赖未说明的 SOMA 语义。示例中的具体类名和方法名参考当前 FJSP 场景，但不由 Blueprint 定义；精确契约由 [Schema 与生成 API](../design/schema-and-generated-api.md) 拥有，当前实现位置由 Implementation Map 记录。

## 2. 用户要解决的问题

SOMA Java 面向需要在一个 Java 8 进程内维护大量、高频变化 runtime state 的工程人员。典型代码同时具有以下特征：

- 数据结构稳定，但字段会被反复读取和更新；
- 既需要连续遍历，也需要按领域 Value Object 精确找到零个、一个或多个候选；
- 一次业务操作通常是“先缩小候选集，再筛选、排序、更新或取第一项”；
- hot loop 不能被大量临时 row object、装箱集合、反射或隐藏的全表重建主导；
- 边界处仍希望使用普通、类型安全的 Java 对象，而不是让业务代码直接操作裸数组。

SOMA 的目标不是把业务算法藏进一个通用查询引擎，而是让使用者声明稳定的数据形态和访问路径，由编译器生成领域化 facade，由 runtime 负责 packed columnar storage、exact access 和候选 Index 执行。

## 3. 使用者心智模型

```text
annotation schema
    -> javac 生成 schema-specific API
    -> application 创建并拥有 table lifecycle
    -> batch 导入 packed columns
    -> 按 Point / Candidate / Column / Key / Bulk / Ownership 选择访问族
    -> Candidate Scan 从 Packed / Exact source 组合有序 stage
    -> terminal 按需返回 current Index、借用 Cursor、复制 snapshot、
       物化 detached object 或提交 mutation
```

建模时，使用者只需要依次回答四个问题：

1. 这份数据是 input fact、working state、frontier/workspace，还是 result fact？
2. 一行是否具有跨结构变更仍稳定的领域 identity？有则使用 keyed table，没有则使用 dense table。
3. 这张表是 root，还是严格属于某个 parent row 的 child？
4. 哪些 value equality access 会稳定、频繁地出现，值得声明为 `@SomaUnique` 或 `@SomaIndex`？

`@SomaKey` 表示 primary unique identity；`@SomaUnique` 表示 secondary unique access；`@SomaIndex` 表示 secondary non-unique exact access。它们不是 B+ 树、排序索引或 range query 声明。

Pipeline 只服务 CandidateAccess，不代表 SOMA 的全部访问模型。已知 Key、Unique 或 current Index 时优先 PointAccess；只读单列时使用 ColumnTraversal/ColumnView；批量导入和 child replacement 保持各自的 staging/ownership boundary。使用者不需要为了 API 形式统一，把所有操作都拼成万能链。

## 4. 从 annotation schema 开始

下面截取 FJSP 中的一组核心 Schema。它展示 SOMA 的目标建模方式，而不是要求所有项目照搬这些业务字段。

```java
@SomaSchema(
    name = "fjsp_runtime_state",
    generatedPackage = "com.hgtech.soma.examples.fjsp.schema.generated",
    version = "1")
package com.hgtech.soma.examples.fjsp.schema;
```

领域 identity 继续使用 Value Object：

```java
@SomaValue
public class MachineId {
    @SomaField long value;
}

@SomaValue
public class OperationMachineKey {
    @SomaField OperationKey operationKey;
    @SomaField MachineId machineId;
}
```

一张具有 stable identity 和两个稳定 access path 的 frontier table 可以这样表达：

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

这个声明表达的是业务语义：候选由 `(operation, machine)` 唯一标识；solver 经常按 machine 选择候选，也经常在 operation 被分配后一次删除它的全部候选。它没有声明 FCFS/SPT 的长期排序，因为该顺序是调用点的动态策略。

## 5. 用户看到的生成接口

编译后，使用者面对的不是 generic metadata API，而是一组 schema-specific 类型：

| 生成形态 | 用户用途 |
|---|---|
| `MachineCandidateTable` | create、reserve、Point/Candidate/Bulk/ownership access、lifecycle |
| `MachineCandidateBatch` | typed bulk import/append staging |
| `MachineCandidateScan` | lazy typed Candidate source/stage/terminal |
| `MachineCandidateCursor` / `MachineCandidateUpdateCursor` | callback-scoped read/update access |
| typed mutation builder | 单个 keyed row 的受控变更与 commit |
| typed `ColumnTraversal` / `KeyTraversal` | one-shot 单列或 logical key traversal |
| primitive `ColumnView` | 按当前 Index 读取 hot primitive leaf |
| child table facade | 访问 parent-owned live child，而不是物化 `List` |
| `IndexSnapshot` | 显式复制一次 operation 的 Index 结果，供紧接着的同步只读批次消费 |

这些类型应让 IDE completion、javac type checking 和生成 diagnostics 成为主要使用界面。runtime 内部的 hash slot、relocation link、owner token 和 backing array 不进入 public application model。

## 6. 一次完整的使用旅程

### 6.1 创建、预留和批量导入

```java
RuntimePlan plan = MachineCandidateTable.defaultRuntimePlan()
    .toBuilder()
    .maximumAggregateStorageBytes(memoryBudget)
    .build();

MachineCandidateTable candidates = MachineCandidateTable.create(plan);
candidates.reserve(expectedCandidateCount);

MachineCandidateBatch batch = new MachineCandidateBatch(batchSize);
batch.addValues(key, family, release, jobReady, materialReady,
    baseReady, processing, 0L, baseReady, 0L, 0L, false);
candidates.addBatch(batch);
```

Batch 是一次写入的 typed staging boundary，不是 live row storage。对大型输入，application 可以分批复用导入流程；求解或仿真结束后，由 aggregate owner 调用 `release()`。

### 6.2 primary key 与 exact group

```java
MachineCandidate one = candidates.fetch(candidateKey);

long countOnMachine = candidates
    .scanByMachine(machineId)
    .count();
```

`fetch(...)` 返回 detached schema object，适合点查和边界代码；`scanByMachine(...)` 直接从增量维护的 exact structure 得到当前 group，读取时不能因为 dirty sidecar 回退到全表重建或全表排序。对于 `@SomaUnique`，canonical 路径直接使用 `findIndexByX/requireIndexByX/findByX/fetchByX` 等 point family；只有确实需要 filter/sort stage 时才进入 `scanByX`。

### 6.3 对当前候选组更新

```java
UpdateResult refreshed = candidates.scanByMachine(machineId).update(candidate -> {
    long effectiveReady = Math.max(machineReady, candidate.baseReadyMinute());
    long setup = setupMinutes(machineId, lastSetupFamily,
        candidate.targetSetupFamilyValue());
    long serviceDuration = Math.addExact(setup, candidate.processingMinutes());

    candidate.setSetupMinutes(setup);
    candidate.setEffectiveReadyMinute(effectiveReady);
    candidate.setFcfsValue(effectiveReady);
    candidate.setSptValue(serviceDuration);
    candidate.setIndicatorReady(true);
});
```

这里把 FCFS 指标定义为当前 machine 上的 `effectiveReady`，把 SPT 定义为本次占用 machine 的 `setup + processing` 时长；`indicatorReady=false` 时两个策略字段只是不可消费的占位值。如果某个 solver 采用不同 FCFS/SPT 定义，应该使用不同的策略名称和 comparator，而不是保留同名字段却静默改变含义。领域时间必须在 import 时验证为非负，并使用 checked addition 防止溢出。

update callback 收到 callback-scoped UpdateCursor。它不是可以缓存或跨 operation 使用的 live entity object。terminal 成功后，`UpdateResult` 报告 matched/changed；失败时不能暴露部分提交。`setupMinutes(...)` 是 application lookup helper，不是 SOMA 内建调度规则；canonical FJSP 约定 absent `lastSetupFamily` 表示初始加工不需要 setup，否则执行 required exact lookup。该 helper 不得重入 `candidates` 或产生外部副作用。

### 6.4 缩小候选、动态排序并取第一项

```java
MachineCandidate chosen = candidates.scanByMachine(machineId)
    .filter(candidate -> candidate.indicatorReady())
    .sorted(dispatchComparator)
    .firstOrThrow();
```

这段代码的目标执行语义是：

```text
by-machine exact group -> L1
filter(L1)             -> L2
sorted(L2)             -> L3
firstOrThrow(L3)       -> detached MachineCandidate
```

如果全表有 100,000 行、`by-machine` 命中 10,000 行、filter 后剩 1,000 行，排序只作用于这 1,000 个 Index。每个 stage 不能重新扩展到全表，也不应创建 1,000 个 detached row object。

### 6.5 低物化 hot path

当 hot loop 只需要少数字段时，使用者可以显式取得 Index 结果，再通过 primitive column 读取：

```java
int selected = candidates.scanByMachine(machineId)
    .filter(candidate -> candidate.indicatorReady())
    .sorted(dispatchComparator)
    .requireIndex();

try (LongColumnView operationIds =
         candidates.candidateKeyOperationKeyOperationIdValueColumn();
     LongColumnView processing = candidates.processingMinutesColumn()) {
    long operationId = operationIds.getLong(selected);
    long processingMinutes = processing.getLong(selected);
    // application hot-path logic
}
```

这条 best-one 路径避免物化完整 `MachineCandidate`，也不先创建多项 `IndexSnapshot`。返回的 current Index 只能在当前同步只读批次内立即消费；来源 Table 任意 mutation/lifecycle 变化后必须丢弃，跨 operation 引用必须使用 `@SomaKey`。

确实需要批量稀疏 gather 时，调用 `indexSnapshot()` 显式复制最终 Index sequence，再在同一个只读批次中配合 ColumnView 消费；`requireCurrent` 只是可选边界防御。Runtime 在一次 Candidate Scan 内部使用的 primitive scratch 统一称为 `IndexBuffer`，它在 terminal 后 reset，不作为 public `List<Integer>`、record collection 或 application state 暴露。

### 6.6 parent-owned child

如果候选机定义严格属于某个 operation input，可以把它建模为 child：

```java
@SomaTable(name = "operation_definitions")
public final class OperationDefinition {
    @SomaKey public OperationKey operationKey;
    @SomaField public int sequenceNo;
    @SomaChild(initialCapacity = 8)
    public List<CandidateMachineDefinition> candidateMachines;
}
```

运行时通过 parent key 取得 live child facade：

```java
CandidateMachineDefinitionTable eligible =
    definitions.candidateMachines(operationKey);

eligible.forEach(candidate -> {
    // 只扫描这个 operation 拥有的 dense child rows
});
```

child 的 lifecycle 属于 parent aggregate。它不是可以 share/reparent 的独立 root，Schema 中的 `List` 也不意味着 runtime 以 Java Collection graph 保存 live data。

### 6.7 边界物化与释放

```java
List<OperationAssignment> result =
    assignments.fetchAll(MaterializationBudget.defaults());

candidates.release();
assignments.release();
```

materialization 返回 detached object graph，适用于结果导出、测试 oracle 和 adapter 输入。它与后续 DTO/wire mapping 是两个边界；大规模导出必须有显式预算，不能混入 storage hot-path 的性能声明。多个 root table 应由 application aggregate owner 在 `finally`/`close` 路径按明确顺序释放；不能只展示正常路径上的零散 `release()`，让构建或求解异常泄漏 lifecycle。

## 7. 顺序、删除与失败语义

使用者必须能够预期以下行为：

- keyed table 和 dense table 都采用 packed swap-remove；删除后不保证物理遍历顺序；
- 未显式排序的 `firstOrThrow()`、`limit(n)`、`indexSnapshot()` 和 `fetchAll()` 只反映执行时的 source sequence；
- `sorted(...)` 只产生本次候选访问顺序，不维护跨 operation 的优先队列；
- range 条件通过列式 scan/filter 表达，V1 不自动维护 range index；
- primary/exact access、swap-remove relocation 和 column mutation 必须在 table operation 边界保持一致；
- SOMA 不提供跨 root table transaction，application 负责跨表提交顺序、失败停止和 aggregate 重建/丢弃策略；
- 同一 ownership aggregate 采用同步、非重入访问模型，不承诺并发 mutation。

## 8. 代表性目标场景

| 场景 | SOMA 主要承载 | application 继续拥有 |
|---|---|---|
| FJSP | machine/operation state、增量 candidate frontier、exact lookup | 调度规则、machine event queue、跨表提交 |
| VRP | customer/route state、route-local child、候选 workspace | 插入策略、可行性、路线事务 |
| 连续仿真 | dense state vector、lookup facts、trace buffer | 数值模型、event min-heap、时间推进 |
| Game | entity state、phase workspace、derived exact cache | game rules、pathfinding、render/network scheduling |

这些场景用于检验同一组可迁移 access pattern，而不是把 SOMA 扩张成 solver、数据库、仿真器、ECS scheduler 或游戏引擎。

## 9. 目标成功标准

从使用者视角，目标形态只有在以下条件同时成立时才算成功：

- annotation 足以表达 table kind、identity、ownership、field 和稳定 exact access；
- 生成 API 能在普通 Java 8 代码中完成 Point、Candidate、Column、Key、Bulk 与 Ownership access；
- runtime state 只有一个权威 live storage，不形成 DTO/object graph shadow；
- exact access 在写入时增量维护，读取不触发隐藏的全表重建；
- candidate stage 只处理上一 stage 的 Index，排序只处理当前候选集；
- materialization、IndexSnapshot 和 external DTO mapping 都是显式、可预算的成本边界；
- failure、lifecycle 和跨表责任对 application 可见；
- 示例、测试、benchmark 和 external consumer 能共同验证这里描述的用户旅程。

## 10. 非目标

SOMA Java V1 不以以下能力为目标：

- Python、C ABI、native runtime 或跨语言 FFI；
- 持久化、查询语言、分布式执行或数据库事务；
- 自动维护任意业务顺序、range tree 或 application event queue；
- 替 application 决定领域不变量、调度策略、跨表一致性或失败补偿；
- 通过 materialized Java object graph 充当 live runtime storage；
- 用隐藏 scan fallback、反射 metadata interpreter 或 boxed collection hot path 换取表面易用性。

完整的 FJSP 使用旅程见 [FJSP runtime state 蓝图](fjsp-runtime-state-blueprint.md)。
