# SOMA Java 产品蓝图

类型：Blueprint

状态：正式

Owner：SOMA Java 产品蓝图

事实范围：目标用户、目标形态、目标使用体验、核心价值与非目标

非事实范围：精确 API 契约、实现算法、当前支持状态、性能结论和 release readiness

设计约束入口：[Design 导航、层次与 Owner](../design/README.md)

最后审查日期：2026-07-29

## 1. 这份蓝图面向谁

这份蓝图从使用者视角说明 SOMA Java 希望成为什么，以及一段真实业务代码使用它时应当是什么感觉。使用者不需要先理解 runtime 内部的 column、bucket 或 compaction 算法，也应当能够完成 Schema 建模、生成代码、导入数据、查询、更新、选择和导出。

文中的代码同时承担两项职责：展示目标体验，并让目标尽量接近可编译、可验证的 Java 8 用法。未特别标为伪代码的片段都按“目标使用代码”审查：允许省略 import、外围 owner 和普通业务 helper，但不能依赖未说明的 SOMA 语义。示例统一使用领域中性的 `Work`、`Group` 和 `Candidate` 名称，不指向某个参考应用；精确契约由 [Schema 与生成 API](../design/schema-and-generated-api.md) 拥有，当前实现位置由 Implementation Map 记录。

## 2. 用户要解决的问题

SOMA Java 是面向 Java 8 的 Schema-Defined、Compiler-Specialized、JVM Heap-Resident 高性能运行时状态计算库。它面向需要在一个进程内维护大量、高频变化 state，并围绕这些 state 反复执行 typed local computation 的工程人员。典型代码同时具有以下特征：

- 数据结构稳定，但字段会被反复读取和更新；
- 既需要连续遍历，也需要按领域 Value Object 精确找到零个、一个或多个候选；
- 一次业务操作通常是“先缩小候选集，再筛选、排序、更新或取第一项”；
- 一个业务规则、算法步骤或 simulation system 需要反复执行 Selection、Projection、Aggregation、Group、Join 或 finite Window；
- hot loop 不能被大量临时记录对象、装箱集合、反射或隐藏的全表重建主导；
- 边界处仍希望使用普通、类型安全的 Java 对象，而不是让业务代码直接操作裸数组。

SOMA 的目标不是把业务算法藏进一个通用查询引擎，而是让使用者声明稳定的数据形态和访问路径，由编译器生成领域化 facade，由 runtime 负责 packed columnar storage、exact access、候选 Index 执行和受控 typed transformation。Application 仍拥有 event loop、solver policy、domain rule、I/O、transaction 和 recovery。

## 3. 使用者心智模型

```text
annotation schema
    -> javac 生成 Descriptor Metadata 与 schema-specific API
    -> application 直接使用默认 RuntimePlan，或在 freeze 前调整 Plan Builder
    -> application 创建 implicit single-root Group，或显式创建 SomaGroup
    -> batch 导入 packed columns
    -> 按 Point / Candidate / Column / Key / Bulk / Ownership 选择访问族
    -> Candidate Scan 从 Packed / Exact source 组合有序 stage
    -> 复杂或重复规则可以定义为 immutable typed DataFlow
    -> 每次绑定当前 Table/parameter，执行 one-shot Invocation
    -> terminal 默认发布完整 Eager Detached result；受限 read-only callback
       delivery 只在同步调用栈内按需消费
    -> observation/explain 说明 effective plan、资源与本次执行
```

使用者将 runtime 理解为三个正交轴：

```text
State / Owner       SomaGroup -> root ownership aggregate -> owned children
Capability          Schema/Metadata、Storage、Access、Mutation、Relation、
                    Transformation、Execution、Result Delivery、Resource、
                    Observation/Failure
Plan / Lifecycle    Descriptor -> Builder -> Effective Plan ->
                    Definition -> Template -> Invocation -> Observation
```

建模时，使用者依次回答：

1. 这份数据是 input fact、working state、frontier/workspace，还是 result fact？
2. 一行是否具有跨结构变更仍稳定的领域 identity？有则使用 keyed table，没有则使用 dense table。
3. 这张表是 root，还是严格属于某个 parent row 的 child？
4. 哪些 value equality access 会稳定、频繁地出现，值得声明为 `@SomaUnique` 或 `@SomaIndex`？
5. 多个 root 是否需要共同 logical identity、resource envelope 与 release？只有需要时
   才显式使用 Group；read-only multi-source DataFlow 本身不要求同一 Group。

`@SomaKey` 表示 primary unique identity；`@SomaUnique` 表示 secondary unique access；`@SomaIndex` 表示 secondary non-unique exact access。它们不是 B+ 树、排序索引或 range query 声明。

Pipeline 只服务 CandidateAccess，不代表 SOMA 的全部访问模型。已知 Key、Unique 或 current Index 时优先 PointAccess；只读单列时使用 ColumnTraversal/ColumnView；批量导入和 child replacement 保持各自的 staging/ownership boundary。使用者不需要为了 API 形式统一，把所有操作都拼成万能链。

Transformation 也不替代 Access。Ad-hoc DSL 适合一次局部计算；Reusable DataFlow 适合反复执行的 dispatch rule、算法步骤或 simulation system。两者共享 Shape、Expression、Operator、Result 和 Effect 语义，但 direct point/column/candidate fast path 仍可保持更低固定成本。

## 4. 从 annotation schema 开始

下面用一组领域中性的 candidate facts 展示 SOMA 的目标建模方式。

```java
@SomaSchema(
    name = "candidate_runtime_state",
    generatedPackage = "com.example.runtime.generated",
    version = "1")
package com.example.runtime;
```

领域 identity 继续使用 Value Object：

```java
@SomaValue
public class GroupId {
    @SomaField long value;
}

@SomaValue
public class CandidateKey {
    @SomaField WorkId workId;
    @SomaField GroupId groupId;
}
```

一张具有 stable identity 和稳定 exact access path 的 candidate table 可以这样表达：

```java
@SomaTable(name = "candidate_facts", defaultCapacity = 8192)
@SomaIndex(name = "by_group", fields = {"candidateKey.groupId.value"})
@SomaIndex(name = "by_work", fields = {"candidateKey.workId.value"})
public final class CandidateFact {
    @SomaKey public CandidateKey candidateKey;
    @SomaField public long baseReadyTime;
    @SomaField public long duration;
    @SomaField public long effectiveReadyTime;
    @SomaField public long primaryScore;
    @SomaField public long secondaryScore;
    @SomaField public boolean eligible;
}
```

这个声明表达的是访问语义：候选由 `(work, group)` 唯一标识；应用经常按 group 选择候选，也经常在 work 被提交后一次删除它的全部候选。它没有声明长期排序，因为排序字段和 tie-breaker 属于调用点的动态策略。

## 5. 用户看到的生成接口

编译后，使用者面对的不是 generic metadata API，而是一组 schema-specific 类型：

| 生成形态 | 用户用途 |
|---|---|
| `CandidateFactTable` | create、reserve、Point/Candidate/Bulk/ownership access、lifecycle |
| generated `SchemaMetadata` | immutable Descriptor、default plan 与 mutable-before-freeze Plan Builder |
| `CandidateFactBatch` | typed bulk import/append staging |
| `CandidateFactScan` | lazy typed Candidate source/stage/terminal |
| `CandidateFactCursor` / `CandidateFactUpdateCursor` | callback-scoped read/update access |
| typed mutation builder | 单个 keyed row 的受控变更与 commit |
| typed `ColumnTraversal` / `KeyTraversal` | one-shot 单列或 logical key traversal |
| primitive `ColumnView` | 按当前 Index 读取 hot primitive leaf |
| child table facade | 访问 parent-owned live child，而不是物化 `List` |
| `IndexSnapshot` | 显式复制一次 Table operation 的 Index 结果，供紧接着的同步只读批次消费 |
| `CandidateFactDataFlow` | 每 Table 一个 typed Source/column expression/binding companion |
| `DataFlowDefinition` / `DataFlowTemplate` | immutable logical rule 与可复用 compiled template |
| `DataFlowInvocation` / `DataFlowContext` | one-shot current-state execution 与显式资源/executor lifecycle |
| scalar / detached-columnar result | 不构造 per-element record graph 的 transformation output |
| callback delivery facade | 复用 Definition/Template/Invocation lifecycle 的同步 read-only 按需消费 |
| Group/runtime Metadata 与 Observation | detached topology、effective identity、current/high-water 与 execution explain |
| typed Delta / `applyDelta` | keyed Table 的 detached ordered change 与 single-aggregate safe-point apply |

这些类型应让 IDE completion、javac type checking 和生成 diagnostics 成为主要使用界面。runtime 内部的 hash slot、relocation link、owner token 和 backing array 不进入 public application model。

## 6. 一次完整的使用旅程

### 6.1 创建、预留和批量导入

```java
RuntimePlan.Builder planBuilder = SchemaMetadata.newPlan();
planBuilder.table(CandidateFactTable.metadata())
    .planningRows(expectedCandidateCount)
    .maximumRows(maximumCandidateCount);
planBuilder.resourceBudget(memoryBudget);
RuntimePlan plan = planBuilder.build();

CandidateFactTable candidates = CandidateFactTable.create(plan);
candidates.reserve(expectedCandidateCount);

CandidateFactBatch batch = new CandidateFactBatch(batchSize);
batch.addValues(key, baseReady, duration, baseReady, 0L, 0L, false);
candidates.addBatch(batch);
```

Batch 是一次写入的 typed staging boundary，不是 live row storage。对大型输入，application 可以分批复用导入流程；求解或仿真结束后，由 aggregate owner 调用 `release()`。

单 root convenience 在内部拥有一个 implicit Group，不给普通用户增加配置税。多个
root 只有在确需共同 composition/lifecycle/resource 时才使用 frozen
`SomaGroupPlan` 和 stable member slot；同一个 Group 可以组合多个 schema 和同一种
root 的多个实例。跨 Group、跨 schema、active/staging 与 self-join 的 read-only
DataFlow 继续通过显式 source binding 工作。

### 6.2 primary key 与 exact group

```java
CandidateFact one = candidates.fetch(candidateKey);

long countInGroup = candidates
    .scanByGroup(groupId)
    .count();
```

`fetch(...)` 返回 detached schema object，适合点查和边界代码；`scanByGroup(...)` 直接从增量维护的 exact structure 得到当前 group，读取时不能因为 dirty sidecar 回退到全表重建或全表排序。对于 `@SomaUnique`，canonical 路径直接使用 `findIndexByX/requireIndexByX/findByX/fetchByX` 等 point family；只有确实需要 filter/sort stage 时才进入 `scanByX`。

### 6.3 对当前候选组更新

```java
UpdateResult refreshed = candidates.scanByGroup(groupId).update(candidate -> {
    long effectiveReady = Math.max(groupReady, candidate.baseReadyTime());
    long completion = Math.addExact(effectiveReady, candidate.duration());

    candidate.setEffectiveReadyTime(effectiveReady);
    candidate.setPrimaryScore(effectiveReady);
    candidate.setSecondaryScore(completion);
    candidate.setEligible(true);
});
```

这些 score 只表示示例 application 已明确命名的动态策略输入；`eligible=false` 时它们不能被选择逻辑消费。实际应用必须给字段定义单位和 comparator 语义，并在 import 时验证非负值、使用 checked arithmetic 防止溢出。

update callback 收到 callback-scoped UpdateCursor。它不是可以缓存或跨 Table operation 使用的 live entity object。terminal 成功后，`UpdateResult` 报告 matched/changed；失败时不能暴露部分提交。callback 不得重入 `candidates` 或产生外部副作用。

### 6.4 缩小候选、动态排序并取第一项

```java
CandidateFact chosen = candidates.scanByGroup(groupId)
    .filter(candidate -> candidate.eligible())
    .sorted(selectionComparator)
    .firstOrThrow();
```

这段代码的目标执行语义是：

```text
by-group exact source  -> L1
filter(L1)             -> L2
sorted(L2)             -> L3
firstOrThrow(L3)       -> detached CandidateFact
```

如果全表有 100,000 行、`by-group` 命中 10,000 行、filter 后剩 1,000 行，排序只作用于这 1,000 个 Index。每个 stage 不能重新扩展到全表，也不应创建 1,000 个 detached object。

### 6.5 低物化 hot path

当 hot loop 只需要少数字段时，使用者可以显式取得 Index 结果，再通过 primitive column 读取：

```java
int selected = candidates.scanByGroup(groupId)
    .filter(candidate -> candidate.eligible())
    .sorted(selectionComparator)
    .requireIndex();

try (LongColumnView workIds =
         candidates.candidateKeyWorkIdValueColumn();
     LongColumnView durations = candidates.durationColumn()) {
    long workId = workIds.getLong(selected);
    long duration = durations.getLong(selected);
    // application hot-path logic
}
```

这条 best-one 路径避免物化完整 `CandidateFact`，也不先创建多项 `IndexSnapshot`。返回的 current Index 只能在当前同步只读批次内立即消费；来源 Table 任意 mutation/lifecycle 变化后必须丢弃，跨 Table operation 引用必须使用 `@SomaKey`。

确实需要批量稀疏 gather 时，调用 `indexSnapshot()` 显式复制最终 Index sequence，再在同一个只读批次中配合 ColumnView 消费；`requireCurrent` 只是可选边界防御。Runtime 在一次 Candidate Scan 内部使用的 primitive scratch 统一称为 `IndexBuffer`，它在 terminal 后 reset，不作为 public `List<Integer>`、record collection 或 application state 暴露。

### 6.6 parent-owned child

如果 option definition 严格属于某个 work input，可以把它建模为 child：

```java
@SomaTable(name = "work_definitions")
public final class WorkDefinition {
    @SomaKey public WorkId workId;
    @SomaField public int ordinal;
    @SomaChild(initialCapacity = 8)
    public List<OptionDefinition> options;
}
```

运行时通过 parent key 取得 live child facade：

```java
OptionDefinitionTable options = definitions.options(workId);

options.forEach(option -> {
    // 只扫描这个 work 拥有的 dense child rows
});
```

child 的 lifecycle 属于 parent aggregate。它不是可以 share/reparent 的独立 root，Schema 中的 `List` 也不意味着 runtime 以 Java Collection graph 保存 live data。

### 6.7 定义并重复执行 typed DataFlow

当同一计算需要反复作用于当前 state 时，用户可以把逻辑规则与 live Table 分开：

```java
CandidateFactDataFlow.Source source =
    CandidateFactDataFlow.source("candidates");

DataFlowDefinition<OptionalLongResult> bestScore =
    source.candidates()
        .filter(source.columns().eligible())
        .project(source.columns().primaryScore())
        .min();

DataFlowTemplate<OptionalLongResult> template = bestScore.compile();
DataFlowContext context = DataFlowContext.managedParallel(4);
try {
    OptionalLongResult score = template.newInvocation(context)
        .bind(source, CandidateFactDataFlow.bind(candidates))
        .execute();

    if (score.isPresent()) {
        consume(score.value());
    }
} finally {
    context.close();
}
```

Definition/Template 不保存 Table、current Index 或 executor。每次 Invocation 显式绑定当前 source，是 one-shot；adaptive parallel 可以根据有界成本回退 sequential，结果、顺序和失败语义必须相同。用户也可以选择 `DataFlowContext.sequential()`，或把自己的 `ExecutorService` 以 borrowed 模式传入；SOMA 不会关闭 caller executor，也不会隐式使用 common pool。

同一模型还支持 typed Projection/Aggregation、Partition/Combine、GroupBy、inner/left-outer/left-semi/left-anti equi Join、owned-child Expand、Prefix Scan 和 finite ordered Window。Grouped/Joined/Windowed 结果默认 read-only；跨 Table 计算只产生 detached result/command，不伪装成跨 Table transaction。

局部只执行一次的简单表达可以直接 build/execute；反复规则显式保留 Template。Definition 是 lazy semantics，只有 Invocation terminal 才读取当前 state。`DataFlowExplain` 和 `DataFlowStats` 是 detached diagnostics，不是业务结果或 planner 的第二事实源。

Eager Detached 是所有 terminal 的默认 Result Delivery：完整构造后一次发布，返回后
不持有 source guard。callback-scoped streaming 是唯一 Lazy Output 能力，只用于
明确 opt-in 的同步 one-shot read-only terminal；Cursor/guard 不得逃逸，consumer
返回 `false` 可以 early stop，外部 side effect 不由 SOMA 回滚。它不提供
`Iterator`、pull cursor、Publisher、async push 或 partial detached result，也不能
绕过 high-expansion resource preflight。

### 6.8 边界物化与释放

```java
List<ResultFact> result =
    results.fetchAll(MaterializationBudget.defaults());

candidates.release();
results.release();
```

materialization 返回 detached object graph，适用于结果导出、测试 oracle 和 adapter 输入。它与后续 DTO/wire mapping 是两个边界；大规模导出必须有显式预算，不能混入 storage hot-path 的性能声明。多个 root table 应由 application aggregate owner 在 `finally`/`close` 路径按明确顺序释放；不能只展示正常路径上的零散 `release()`，让构建或求解异常泄漏 lifecycle。

## 7. 顺序、删除与失败语义

使用者必须能够预期以下行为：

- keyed table 和 dense table 都采用 packed swap-remove；删除后不保证物理遍历顺序；
- 未显式排序的 `firstOrThrow()`、`limit(n)`、`indexSnapshot()` 和 `fetchAll()` 只反映执行时的 source sequence；
- `sorted(...)` 只产生本次候选访问顺序，不维护跨 Table operation 的优先队列；
- range 条件通过列式 scan/filter 表达，V1 不自动维护 range index；
- primary/exact access、swap-remove relocation 和 column mutation 必须在 table operation 边界保持一致；
- SOMA 不提供跨 root table transaction，application 负责跨表提交顺序、失败停止和 aggregate 重建/丢弃策略；
- 同一 ownership aggregate 采用同步、非重入访问模型，不承诺并发 mutation。

## 8. 非规范性参考应用

产品 Blueprint 不拥有任何领域算法或参考应用目标。仓库提供三个独立 Java 8
consumer，用于观察上述通用能力怎样从不同 business model 自然投影到真实
application boundary：

- [工业动态调度引擎](../../soma-examples/industrial-dynamic-scheduler/README.md)：direct Access、Candidate Scan 与应用自有 frontier/event loop；
- [个体生态仿真](../../soma-examples/grassing-individual-simulation/README.md)：packed 迭代状态、exact group、staged mutation 与确定性 lifecycle；
- [实时派工规则引擎](../../soma-examples/real-time-dispatch-rule-engine/README.md)：reusable multi-source DataFlow、受控并行、detached command 与应用自有提交。

它们各自拥有 application Blueprint、Design、配置、输入生成、正确性和性能
evidence；彼此不共享领域模型或 fixture，也不承担 API kitchen sink 责任。其代码
和文档不能反向定义 SOMA Design，也不能把 SOMA 扩张成 solver、仿真器、ECS
framework、MES adapter 或事务引擎。

## 9. 目标成功标准

从使用者视角，目标形态只有在以下条件同时成立时才算成功：

- annotation 足以表达 table kind、identity、ownership、field 和稳定 exact access；
- 生成 API 能在普通 Java 8 代码中完成 Point、Candidate、Column、Key、Bulk 与 Ownership access；
- runtime state 只有一个权威 live storage，不形成 DTO/object graph shadow；
- V1 field 只属于 primitive-backed scalar、String reference-backed immutable
  scalar、compiler-flattened `@SomaValue` 或 parent-owned child；application object
  以 stable ID + sidecar 关联；
- exact access 在写入时增量维护，读取不触发隐藏的全表重建；
- candidate stage 只处理上一 stage 的 Index，排序只处理当前候选集；
- materialization、IndexSnapshot 和 external DTO mapping 都是显式、可预算的成本边界；
- typed Transformation 覆盖常用 Shape/Operator，且每个大结果都有非 object-graph 的 canonical consumption；
- ad-hoc DSL 与 reusable DataFlow 共享语义；Definition/Template 可复用，Invocation one-shot；
- sequential 是 parallel 的语义基准，managed/borrowed executor ownership、budget、cancel 和 safe-point Effect 对 application 可见；
- 一个 bounded scheduler 把 Storage Segment、Parallel Morsel 与 cache/JIT
  Execution Vector 分责；Small/Medium 有 direct fast path，单 Segment 也可在成本
  足够时拆为多个 morsel；
- Small、Medium、单表 1M 和两个同时驻留的 1M root 均有明确的受约束
  production-shape qualification；10M/100M 继续作为非阻塞 research/stress
  profile，用于发现扩展性与资源边界，但不构成 V1 readiness 前置条件；
  String claim 同时声明长度、cardinality、sharing、field role 与同时存活 Table 数；
- failure、lifecycle 和跨表责任对 application 可见；
- 示例、测试、benchmark 和 external consumer 能共同验证这里描述的用户旅程。

## 10. 非目标

SOMA Java V1 不以以下能力为目标：

- Python、C ABI、native runtime 或跨语言 FFI；
- 持久化、SQL/query language、分布式执行、数据库同步或事务；
- SQL/DDL/DML/DQL 兼容层、foreign key、table reference 或通用数据库接口；现代
  SQL 系统只作为逻辑语义、类型、约束和执行引擎设计的参考，不改变 SOMA 的
  schema-specific typed Java 产品定义；
- off-heap/native 第二存储后端、无限 stream、retained temporal Window 或 automatic incremental view maintenance；
- dictionary/character arena/intern String backend、任意 Java object/array/DTO/
  Collection graph 的 live schema storage；
- full-outer/cross/theta join、任意 flatMap 或通用 DataFrame；
- 隐式 common pool、无约束并行或并发 Table API；
- ordinary `Iterator`、closeable pull cursor、Generator、Publisher、async push 或
  partial detached output；
- 自动维护任意业务顺序、range tree 或 application event queue；
- 替 application 决定领域不变量、调度策略、跨表一致性或失败补偿；
- 通过 materialized Java object graph 充当 live runtime storage；
- 用隐藏 scan fallback、反射 metadata interpreter 或 boxed collection hot path 换取表面易用性。

可执行的完整使用旅程从 [`soma-examples`](../modules/soma-examples/README.md) 进入。
