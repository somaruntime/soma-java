# SOMA Java V1 实现策略

状态：正式设计文档
Owner：根项目协调层
事实范围：跨模块实现架构、垂直切片顺序、阶段出口和 V1 防缩水规则
非事实范围：具体 class/package、runtime 算法参数、项目排期和 release 结果
最后审查日期：2026-07-10

## 1. 目标

本文把正式设计映射为可验证的实施路线。实施顺序可以渐进，但每个阶段都必须朝完整 V1 contract 收敛，不能把阶段性缺失改写成永久非目标。

## 2. 推荐实现架构

采用：

> Schema-specific generated facade + small annotation-agnostic runtime kernel。

```text
annotation source
  -> validated normalized schema
  -> schema-specific generated binding
  -> small generic TableStore components
  -> primitive specialized hot path
```

这一区分同时满足：

- public API 类型安全；
- runtime-core 不解析 annotation；
- generated code 不复制完整 storage engine；
- hot loop 可以静态绑定 primitive columns；
- correctness logic 在共享 kernel 中复用；
- metadata 不进入 per-row/per-cell interpreter。

## 3. 不采用的方向

### 3.1 Metadata interpreter runtime

Reject as canonical hot path：逐 cell 解释 field metadata、type switch、reflection 或 boxed value 会破坏 JIT、allocation 和 locality 目标。

Metadata 可以用于 create/diagnostics/compatibility，不驱动 inner loop。

### 3.2 Fully generated storage engine per schema

V1 不为每张 table 复制完整 RowSpace/KeySpace/index/lifecycle engine。它不一定语义错误，但代码规模、golden、bug surface 和维护成本过高。

### 3.3 Schema object/DTO live storage

Reject：`List<R>`、mutable DTO、schema object graph 或 Java Stream object row 不能成为 canonical runtime storage。

## 4. 分工

### 4.1 Processor/generated code

负责：

- annotation collection、validation、normalization、hash；
- schema-specific Table/Batch/Cursor/Mutator/child/materializer；
- static primitive column/selector binding；
- public name/type/error adaptation；
- deterministic output 和 compatibility metadata。

不得：

- 在 generated code 中复制通用 lifecycle/sidecar engine；
- 通过 reflection/metadata switch 执行 hot terminal；
- 暴露 runtime internal handle。

### 4.2 Runtime-core

负责：

- RowSpace、ColumnStore、presence、KeySpace；
- index/unique/order、AccessPath；
- mutation/compaction/epoch；
- ownership registry、cascade lifecycle；
- materialization accounting；
- structured runtime errors/stats；
- packed/primitive/fused/allocation-bounded kernel。

不得解析 annotation 或拥有 schema logical semantics。

## 5. 关键执行边界

### 5.1 Create

```text
generated metadata
  -> compatibility/precondition
  -> TableStore components
  -> active empty table
```

Create failure 不发布 half-initialized table。

### 5.2 Batch/import

```text
generated Batch builder
  -> local validation/default/presence
  -> runtime prepare capacity/key/unique/child
  -> publish base facts
  -> maintain/dirty sidecars
  -> commit epoch
```

Expected failure 保持旧事实。

### 5.3 Direct/pipeline mutation

Generated mutator/cursor 直接绑定 primitive storage。MutationCoordinator 负责 key/unique precheck、sidecar、compaction、view pin 和 epoch。

### 5.4 Materialization

Generated materializer 解释 schema-specific object shape；runtime 提供 row/child traversal、budget counters 和 path。两者都遵守根级 [Materialization 契约](materialization-contract.md)。

## 6. Performance vertical slice

每一阶段必须同时提供最小 correctness 与 performance-shape evidence：

```text
schema fixture
  -> generated binding
  -> runtime kernel
  -> invariant assertion
  -> allocation/shape benchmark smoke
```

不能等所有功能结束后才发现 hot path 已经采用 object/boxing/metadata interpreter。

## 7. 实施阶段

### Phase 1：Dense table 最小闭环

实现：

- primitive ColumnStore + presence；
- packed RowSpace；
- Batch/replaceAll/addBatch/clear；
- dense direct access；
- default Row Pipeline scan/filter/limit/count/forEach/update；
- Cursor reuse、fusion、no-per-row allocation baseline；
- lifecycle/released/error minimum。

出口：

- Java 8 generated package 可编译运行；
- packed/presence invariant 通过；
- Direct API/Row Pipeline golden 通过；
- primitive scan 与 allocation smoke 有结构化结果。

### Phase 2：Keyed identity

实现：

- generated key；
- SparseInt/Hash KeySpace；
- contains/find/fetch/mutate/delete/keys；
- duplicate/missing/canonical floating；
- remove compaction repair。

出口：

- key differential/property evidence；
- domain/load/collision/rehash stats；
- no transient composite tuple hot lookup。

### Phase 3：AccessStructures

实现：

- index、unique、order；
- grouped source；
- dirty/rebuild lifecycle；
- dynamic row-index sort；
- UpdateResult/RemoveResult stats。

出口：

- selector/unique/order oracle；
- clean/dirty/rebuild evidence；
- rebuild-storm observable；
- primitive sidecar shape。

### Phase 4：Parent-owned child 与 materialization

实现：

- child ownership validation/registry；
- required/optional child facade；
- cascade/replacement/view-pinned；
- recursive materializer；
- deterministic budget/path；
- explicit testkit comparator。

出口：

- no share/reparent/orphan/cycle；
- replacement all-or-nothing；
- depth/table/row/leaf/allocation budget errors；
- child cardinality/locality diagnostic lanes。

### Phase 5：Processor/codegen hardening

完成：

- full annotation validation；
- canonical normalized schema/hash；
- `@SomaValue` lowering；
- deterministic source/resources；
- API collision/diagnostics；
- compile/golden/package matrices；
- JIT-friendly generated shape review。

这一阶段补全 breadth，不表示前四阶段可以绕过 processor contract。

### Phase 6：Scenario 和 release evidence

完成：

- FJSP、VRP、Simulation、Game formal scenarios；
- Access Pattern Cards；
- G4/G5 package/example/benchmark smoke；
- component performance-shape lanes；
- G6 evidence review。

## 8. 防缩水矩阵

| V1 capability | 首次进入 | 最终 gate |
|---|---|---|
| dense/primitive/presence/Row Pipeline | Phase 1 | G3/G4 |
| keyed/KeySpace/key API | Phase 2 | G2/G3 |
| index/unique/order/dynamic sort | Phase 3 | G2/G3 |
| child ownership/deep materialization | Phase 4 | G1-G3 |
| complete processing/codegen | Phase 5 | G1/G2 |
| scenario/benchmark/release evidence | Phase 6 | G4-G6 |

开发阶段可以暂缺后续能力，但正式 V1 readiness 不得删除对应 gate。

## 9. 实现期决策边界

可以由实现和 benchmark 决定：

- package-private class/algorithm；
- growth/hash/index/scratch/pooling/stats strategy；
- concrete helper API；
- benchmark scale/warmup/repetition；
- runtime-plan default calibration。

必须回到正式设计决策：

- annotation/public API shape；
- ownership/optional/default/key semantics；
- generated compatibility/hash；
- lifecycle/error/materialization contract；
- concurrency/persistence boundary；
- V1 gate 或 release claim。

## 10. 验证原则

每一 vertical slice 至少包含：

- owner contract review；
- compile/golden；
- runtime invariant/differential；
- expected error path；
- performance-shape smoke；
- docs/index synchronization。

最终 gate 以 [V1 验证门禁](validation-gates.md) 为准；smoke 不等于 correctness、performance 或 release proof。

## 11. 非目标

本文不承诺人员、日期、story point、具体 class/package 或 benchmark 数字，也不把开发顺序当作 public compatibility contract。
