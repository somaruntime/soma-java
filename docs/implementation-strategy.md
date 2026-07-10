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
  -> javac 8 parse-phase @SomaValue lowering
  -> validated normalized schema by JSR 269 processor
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
- javac 8 plugin activation、effective-type lowering 和 compiler identity；
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

### Phase 0：Compiler/build vertical slice

实现：

- javac 8 parse-phase plugin skeleton；
- `@SomaValue` class/field/constructor/equality/hash/toString lowering；
- JSR 269 processor skeleton 和 effective-model validation；
- root reactor、Maven Wrapper、CI 和 external consumer fixture；
- supported/unsupported compiler fail-closed diagnostics；
- public API/compatibility/build/security owner contract checks。

出口：

- direct javac 与 Maven external consumer 看到同一 effective type；
- transformer 缺失、JDK 9+ adapter mismatch 和 conflicting source declaration 稳定失败；
- compile/classfile/golden/clean-repeatability evidence；
- build-only processor 不进入 application runtime graph。

Phase 0 只证明 compiler/build foundation，不生成虚假的 G2/G4 feature-complete claim。

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

## 8. 防缩水执行协议

### 8.1 唯一 V1 目标与 Phase 语义

本项目只有一个产品实施目标：完成 Java-only SOMA V1，并满足 G0-G6。Phase 0 至 Phase 6 只表示实现顺序和验证 checkpoint，不是独立产品版本、release、Codex goal 或兼容性层级。

`V1.0 RC` 是完整功能 checkpoint：所有 V1 功能 capability 和 G0-G5 evidence 必须落地，不是缩小版产品。G6-only license artifact、support matrix、SCM/contact、signing/provenance 可以在功能 RC 时保持 `not-started` 或 `blocked`，但仍属于同一个 V1 release 总目标；G6 未通过时，总 release Goal 不得标记 complete，也不得公开分发或声明正式支持矩阵。

实施过程中禁止：

- 把 Phase 改写为 `v0.1`、`v0.2`、MVP、Lite、Basic 或其他缩水目标；
- 用 Maven `0.1.0-SNAPSHOT` artifact version 推导产品只需达到“V0.1”；
- 因当前 slice 未覆盖某项能力，就把该能力改成 non-goal、future major version 或无目标阶段的 backlog；
- 用 phase-local test passed、示例能跑或 smoke passed 表示完整 V1 goal 已完成。

尚未进入当前 Phase 的 V1 capability 只能保持 `not-started`，仍属于同一个 V1 目标和最终 Gate。

### 8.2 单调收敛原则

每个 Phase 的产物必须是最终 V1 架构的有效子集。后续 Phase 应通过增加 capability、补齐 evidence 或替换 contract-preserving internal algorithm 收敛到 V1，不能依赖以下迁移才能成立：

- 删除或重命名前期 public/generated API；
- 迁移 schema、materialized shape、error、lifecycle 或 compatibility 语义；
- 把前期 canonical live storage 或 hot execution path 整体替换成正式架构；
- 让 consumer 从临时 `V0` surface 迁移到真正 V1 surface；
- 把为当前测试而增加的 stub、fake 或 test-only bypass 当作后续实现基础。

允许“breadth 尚未实现”，不允许“已实现语义是近似版”：

- Phase 1 可以没有 `KeySpace`，但 dense table 必须使用最终方向的 packed primitive `TableStore`；
- Phase 1 可以只有 scan source，但不能用 `Stream<schema object>` 或 reflection interpreter 代替 Row Pipeline；
- Phase 4 前可以没有 child ownership，但不能先允许 share/reparent，再承诺后续收紧；
- 某个 public/generated capability 可以尚未生成，但不能先生成需要 consumer 迁移的缩水签名。

临时实现只有同时满足以下条件才可进入代码：不改变 public/generated contract；不成为 canonical live storage/hot path；被隔离在 internal/test boundary；有明确删除触发条件；不被 Gate report 当作 capability evidence。

如果从当前 slice 到完整 V1 必须依赖 public migration、核心事实迁移或主执行路径重写，当前 slice 必须停止，不能以“以后重构”为理由继续。

### 8.3 V1 capability ledger

Capability ID 是实施、PR 和 evidence 的稳定 traceability key，不拥有行为语义。“控制 Owner / 实现 Owner 链”只表达跨层追踪顺序：每项具体设计事实仍以链接文档中的唯一 Owner 为准，不产生联合 Owner。修改 ID、移出 V1、改变完整出口或删除最终 Gate 都属于正式 scope change。

| Capability ID | V1 capability | 控制 Owner / 实现 Owner 链 | 首次进入 / 完整出口 | 最终 Gate | 禁止替代 |
|---|---|---|---|---|---|
| `V1-ANNOTATION-SCHEMA` | table/value/type/optional/default/key/access/child declaration | [Annotation schema](../soma-annotations/docs/annotation-schema-contract.md) | Phase 0 / Phase 5 | G1/G2 | 缩水 annotation subset、runtime 反推 schema |
| `V1-COMPILER-LOWERING` | javac 8 lowering 与 fail-closed compiler identity | [Compiler integration](../soma-processor/docs/compiler-integration-contract.md) | Phase 0 / Phase 5 | G2/G4 | `--release 8`、processor-only 或 runtime 补救 |
| `V1-PROCESSING-MODEL` | collection、validation、normalized model 与 diagnostics | [Schema processing](../soma-processor/docs/schema-processing-contract.md) | Phase 0 / Phase 5 | G1/G2 | codegen 重读 raw Element 补语义 |
| `V1-SCHEMA-HASH` | canonical schema hash 与 compatibility identity | [Schema processing](../soma-processor/docs/schema-processing-contract.md) | Phase 0 / Phase 5 | G2/G4 | path/time-dependent hash 或延迟到 hot path 检查 |
| `V1-PUBLIC-COMPATIBILITY` | public/generated/internal classification、manifest 与 processor/runtime pairing | [Public compatibility](public-api-compatibility-contract.md) | Phase 0 / Phase 6 | G2/G4/G6 | 用 pre-1.0 理由静默 breaking、internal 泄漏 |
| `V1-GENERATED-API` | Table、Batch、Direct API、Row/Key/Column Pipeline、Mutator、child、materializer 与 typed result | [Generated API](generated-table-api-contract.md) / [Code generation](../soma-processor/docs/code-generation-contract.md) | Phase 1 / Phase 5 | G2/G4 | temporary V0 facade、`XxxRecord` 第二模型 |
| `V1-DENSE-STORAGE` | dense packed rows、primitive columns 与 presence | [TableStore](../soma-runtime-core/docs/table-store-contract.md) | Phase 1 / Phase 1 | G3/G4 | `List<Row>`、DTO/object graph live storage |
| `V1-ROW-PIPELINE` | one-shot fused Row Pipeline 与 reusable Cursor | [Generated API](generated-table-api-contract.md) / [Runtime performance](../soma-runtime-core/docs/runtime-performance-implementation-contract.md) | Phase 1 / Phase 5 | G2/G3/G4 | Java Stream、metadata interpreter、per-row object |
| `V1-COLUMN-ACCESS` | Column Pipeline 与 typed readonly ColumnView | [Generated API](generated-table-api-contract.md) / [Code generation](../soma-processor/docs/code-generation-contract.md) | Phase 1 / Phase 5 | G2/G3/G4 | 用 schema-object materialization 代替 primitive hot access |
| `V1-KEYED-IDENTITY` | generated key、SparseInt/Hash KeySpace、key API 与 Key Pipeline | [Generated API](generated-table-api-contract.md) / [TableStore](../soma-runtime-core/docs/table-store-contract.md) | Phase 2 / Phase 5 | G2/G3 | `HashMap<Key,Integer>` canonical path、transient tuple lookup |
| `V1-ACCESS-STRUCTURES` | index、unique、order、grouped source 与 dynamic sort | [TableStore](../soma-runtime-core/docs/table-store-contract.md) / [Code generation](../soma-processor/docs/code-generation-contract.md) | Phase 3 / Phase 5 | G2/G3 | 只保留 scan、隐藏 rebuild storm |
| `V1-MUTATION` | Direct/Mutator/pipeline mutation、compaction 与 result stats | [Generated API](generated-table-api-contract.md) / [Runtime correctness](runtime-correctness-model.md) | Phase 1 / Phase 5 | G2/G3 | key setter、expected failure partial state |
| `V1-CHILD-OWNERSHIP` | parent-owned keyed/dense child 与 cascade lifecycle | [Annotation schema](../soma-annotations/docs/annotation-schema-contract.md) / [Runtime lifecycle](../soma-runtime-core/docs/runtime-lifecycle-contract.md) | Phase 4 / Phase 5 | G1/G3/G4 | share、attach、reparent 或 Java Collection live child |
| `V1-MATERIALIZATION` | schema object/List/Map recursive detached materialization 与 budget | [Materialization](materialization-contract.md) | Phase 1 / Phase 5 | G1/G2/G3/G4 | shallow/partial graph、hidden write-back、无 budget overload |
| `V1-RUNTIME-LIFECYCLE` | epoch、view pin、release 与 single-owner aggregate | [Runtime lifecycle](../soma-runtime-core/docs/runtime-lifecycle-contract.md) | Phase 1 / Phase 4 | G3/G5 | 隐藏 stale/released、伪 transaction/concurrency |
| `V1-RUNTIME-ERRORS` | structured errors、stable code/context 与 stats | [Runtime errors](../soma-runtime-core/docs/runtime-errors-and-diagnostics-contract.md) | Phase 1 / Phase 5 | G3/G5 | generic exception、silent fallback、日志代替 result |
| `V1-RUNTIME-PLAN` | immutable effective plan、identity 与 validation | [Runtime plan](../soma-runtime-core/docs/runtime-plan-contract.md) | Phase 1 / Phase 6 | G3/G4/G5 | mutable hidden defaults、unknown option ignore |
| `V1-PERFORMANCE-SHAPE` | packed/primitive/fused/allocation-bounded kernel discipline | [Runtime performance](../soma-runtime-core/docs/runtime-performance-implementation-contract.md) | Phase 1 / each phase | G2/G3/G5 | 功能结束后再重写 hot path |
| `V1-SECURITY-INTEGRITY` | generated artifact integrity、resource abuse、diagnostic exposure 与 supply chain | [Security model](security-model.md) | Phase 0 / each phase | G0-G6 | source injection、unchecked resource growth、敏感 payload 泄漏 |
| `V1-EVIDENCE-TOOLING` | compile/golden/invariant/materialization/shape helpers | [Testkit](../soma-testkit/docs/testkit-contract.md) | Phase 0 / each phase | G1-G5 | test-only bypass public contract、golden 自动接受 |
| `V1-CONSUMER-PACKAGE` | Java 8 external consumer、artifact graph 与 package smoke | [Build contract](build-and-dependency-contract.md) | Phase 0 / Phase 6 | G4/G6 | IDE/loose source classpath 代替 consumer artifact |
| `V1-SCENARIO-BENCHMARK` | formal scenarios、Access Pattern Cards 与 benchmark smoke | [Scenario overview](../soma-examples/docs/runtime-state-schema-examples.md) / [Benchmark](../soma-benchmarks/docs/benchmark-evidence-contract.md) | Phase 6 / Phase 6 | G5 | demo total time、smoke 冒充 performance claim |
| `V1-RELEASE-EVIDENCE` | compatibility、reproducibility、known limitations 与 release reports | [Version/release](versioning-and-release-contract.md) / [Validation gates](validation-gates.md) | Phase 6 / Phase 6 | G6 | 本机测试或 phase-local evidence 冒充 release readiness |

### 8.4 Capability 状态与 slice 协议

Capability 状态进入 PR、phase report 或 gate report，不进入本文的长期事实。允许状态为：

- `not-started`：仍属于 V1，但尚未进入实现；
- `in-progress`：当前 slice 正在实现；
- `implemented-unverified`：已有实现但缺少对应 evidence；
- `evidenced`：Owner contract 要求的当前层级 evidence 已通过；
- `blocked`：保留在 V1，记录阻塞原因和所需决策。

禁止使用 `dropped`、`optional`、无目标阶段的 `deferred`，或把 capability 移到未批准的后续版本。

每个 implementation slice 开始前必须记录：完整 V1 目标、涉及的 Capability ID、唯一 Owner、当前 slice 出口、故意未实现的 V1 breadth、禁止捷径和计划 evidence。

每个 slice 结束时必须记录：Capability 状态变化、实际 evidence、未实现项仍对应的 Phase/Gate、是否改变 Owner contract，以及后续达到 V1 是 additive completion/internal refinement 还是需要 migration/rewrite。后一种情况阻塞 closeout。

Phase 通过只表示该 checkpoint 的出口满足；完整 V1 goal 在 G0-G6 全部通过前不得标记完成。

### 8.5 Scope change hard stop

以下情况必须在编码前停止并由用户明确决定：

- 修改 capability ledger、正式 Owner、public/schema/runtime contract 或最终 Gate；
- 把 capability 移出 V1，改成新版本、MVP、Lite 或 indefinite backlog；
- 引入前期 consumer 后续必须迁移的 public/generated surface；
- 用临时 canonical storage/hot path 替代正式架构；
- 为通过当前实现或测试而反向修改宪法、实现策略、non-goal 或 Gate。

批准 scope change 后必须先修改唯一 Owner 和本节 ledger，再实现代码；implementation PR 不能先落 shortcut，再补文档解释。

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

Compiler support matrix、artifact dependency/publication、public compatibility、security boundary 和 release prerequisites 分别回到对应 root/module owner contract，不由某个 implementation PR 临时决定。

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
