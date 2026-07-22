# SOMA Access Model 与 Candidate Scan 产品化治理

类型：Temporary

状态：Stage 2 production/public API/runtime 实现与工作树 Gate 完成；正式固化待授权

Owner：SOMA Java Access Model / Candidate Scan 专题治理

事实范围：本专题的意图、Access Model、Candidate Scan 目标设计、阶段、Temporary 裁决、evidence、实施包与退役条件

非事实范围：正式 Blueprint/Design 语义、已提交的 Stage 2 候选、正式性能声明和 release readiness

正式事实源：否

专题建立基线：`5217c27ae4a07a5ec8ec3b70ae92224aac5706d2`

最后审查日期：2026-07-22

当前目录名沿用专题建立时的工作名称。`Row Pipeline` 与 `Operation Pipeline` 已退出目标 canonical language；目录名不重新定义产品术语。

## 1. 权威与授权

输入正式 Owner：[产品蓝图](../../blueprints/soma-java-product-blueprint.md)、[设计宪法](../../design/soma-java-design-constitution.md)、[系统架构](../../design/system-architecture.md)、[领域语言](../../design/domain-language.md)、[Table、存储与访问](../../design/table-storage-and-access.md)、[Schema 与生成 API](../../design/schema-and-generated-api.md)、[Ownership 与 lifecycle](../../design/ownership-and-lifecycle.md)、[Correctness 与 failure](../../design/correctness-and-failure.md)、[性能模型](../../design/performance-model.md)、[Runtime Plan 与可观测性](../../design/runtime-plan-and-observability.md)。

实现核对入口：[Compiler 与 codegen Map](../../implementation-map/compiler-and-codegen-map.md)、[Runtime Core Map](../../implementation-map/runtime-core-map.md)。当前性能入口：[当前性能摘要](../../../reports/current-performance-summary.md)。

本目录的裁决仍不是正式产品事实。用户已于 2026-07-22 授权 Stage 2 的
production/public API/runtime 实施，但没有授权正式 Owner 固化；因此当前可以实现并
验证候选，不得提前修改正式 Design、Report 或删除 Temporary。

## 2. 文档职责

| 文档 | 责任 |
|---|---|
| 本 README | 专题入口、状态、边界、阶段与退出条件 |
| [Stage 1 章程](stage-1-charter.md) | 意图、目标、强制分析顺序、范围与完成标准 |
| [Access Model](access-model.md) | 基本 Access Pattern、组合代数、合法性与成本模型 |
| [API 覆盖矩阵](access-api-coverage.md) | 当前 API 映射、重复、缺口与抽象问题 |
| [Stage 1 Evidence](stage-1-evidence.md) | API、场景链、allocation、JFR、code size、oracle 与 FJSP 基线 |
| [Stage 1 决策](stage-1-decisions.md) | Access/API、命名、语义、物理表示、migration 与 identity 的唯一候选裁决 |
| [产品模型](product-model.md) | 目标使用体验与各 Access family 的产品边界 |
| [操作目录](operation-catalog.md) | source/stage/terminal descriptor 与 legality matrix |
| [Pipeline IR](pipeline-ir.md) | Candidate Scan semantic、bound、physical 与 compact representation 详细设计 |
| [核心术语](core-terminology.md) | canonical target vocabulary 与 current-to-target mapping |
| [Stage 2 实施包](stage-2-implementation-package.md) | 模块切片、迁移、Gate、停止条件与实际完成状态 |
| [Stage 2 Evidence](stage-2-evidence.md) | production/API/runtime 实现、correctness、allocation、JFR、code size 与 FJSP A/B |
| [Stage 0 审查](stage-0-review.md) | Access Model 建立前的历史候选审查与 Stage 1 输入 |
| [Stage 1 收口审查](stage-1-review.md) | 追踪、自审、验证和 Stage 2 readiness 判断 |

这些文档共同属于一个 Temporary topic，不各自成为平行 Owner。发生表述冲突时，以 `Stage 1 决策` 为本专题候选裁决入口，以正式 Design 为长期约束。

## 3. 治理意图

SOMA 不是 Java Stream，也不只是 Candidate Pipeline。它需要先定义完整的 Access Model，再决定 API、Pipeline IR 和物理优化：

```text
Access Pattern Catalog
  -> Access Pattern / API Coverage Matrix
  -> Composition Algebra and Legality
  -> Cost Model and Benchmark Matrix
  -> API Optimization Decisions
  -> Physical Execution and Performance Decisions
  -> Candidate Scan IR / Detailed Design
```

这条顺序保证：

- Pipeline 只表达 CandidateAccess，不吞并 Point、Column、Key、Bulk 和 Ownership；
- cardinality、validity、sequence 与成本先于方法名和数组布局；
- compact plan、source/terminal specialization 与命名都能追溯到产品需求和 evidence；
- 实现困难不能反向缩小 Access Model 或保留两套 canonical API。

## 4. 目标体系

SOMA Access Model 包含五条彼此有边界的路径：

```text
CandidateAccess := CandidateSource Stage* CandidateTerminal
PointAccess     := CurrentIndex | PrimaryKey | SecondaryUnique -> point terminal
ColumnAccess    := FullTraversal | CurrentIndexRead | IndexSnapshotGather
KeyAccess       := KeyTraversal -> Borrow | Materialize
BulkAccess      := Batch -> Append | Replace | Clear | ReplaceChildren
```

CandidateAccess 的目标产品名称是 **Candidate Scan**。Table 本身是 packed candidate source；`@SomaIndex` 暴露 exact-group Scan；`@SomaUnique` 以 point family 为 canonical access，并保留显式 Candidate bridge。完整语义见 Access Model，目标 API 见 Stage 1 决策与产品模型。

## 5. 不得回退的边界

本专题不得：

- 将 `Stream<T>`、schema object、DTO、Java Collection graph、boxing tuple、reflection 或 metadata interpreter 引入 canonical hot storage/path；
- 为“统一”而把 Table element、Key、primitive Column、Point 和 Bulk 强塞进一个 `Object` 化 Pipeline；
- 恢复 Sparse Set、dirty selector rebuild、read-time scan fallback、maintained order、stable compaction 或 stable physical Index；
- 暴露内部 `IndexBuffer`、backing array、bucket、row link、IR 或 runtime protocol；
- 允许 operation branch/reuse、callback reentrancy、同 aggregate 并发执行或跨 Table transaction；
- 使用 Table-global/ThreadLocal mutable plan 代替独立 one-shot operation；
- 为减少 allocation 破坏 full equality、resource preflight、materialization budget、failure atomicity 或 Index caller-responsibility；
- 引入 SQL、join、range/order index、parallel execution、generic `map/flatMap/collect/reduce` 或 public top-k；
- 用单机 benchmark 声明跨环境 SLA、production readiness 或 G6。

## 6. Stage 1 已关闭的核心裁决

下表只作导航，不替代 [Stage 1 决策](stage-1-decisions.md)：

| 关注点 | 唯一候选结论 |
|---|---|
| umbrella | `SOMA Access Model`；Pipeline family 为 `Candidate Scan` |
| packed source | Table 本身；删除 `rows()`，不增加 `scan()` |
| exact group | `scanByX(...)` |
| secondary unique | `contains/findIndex/requireIndex/find/fetch/mutate/deleteByX`；`scanByX` 仅作 Candidate bridge |
| first/best-one | Candidate Scan 增加 `findIndex()` / `requireIndex()`，不先创建 Snapshot |
| callback borrow | `*Cursor` / `*UpdateCursor` |
| snapshot | `indexSnapshot()`；仍遵循 caller-responsibility current Index 契约 |
| Key/Column | `*KeyTraversal` / typed `*ColumnTraversal`，one-shot；不扩充 Candidate stages |
| lifecycle | 每条 operation 独立 plan + generation handle；禁止 branch/reuse |
| compact plan | inline 3；第 4 stage 起使用 kind/callback/argument overflow arrays |
| exact source | generated typed leaf capture；terminal-time current group binding |
| best-one execution | 单 Sort 兼容尾部使用 stable arg-min；k > 1 继续 full stable sort |
| stats | 记录 semantic logical work；caller-held plan bytes 不进入 TableStats |
| compatibility | clean cutover；generated/runtime protocol v4；Schema identity 不变；plan hash因compatibility输入变化而确定性更新 |

`AM-DEC-01..06`、`OP-DEC-01..06`、`RP-DEC-01..08` 已全部关闭，没有保留 implementation-time naming 或 semantic choice。

## 7. Evidence 摘要

Stage 1 在同一 Zulu JDK 8 / macOS aarch64 环境建立了以下诊断事实：

- 四场景 35 条 CandidateAccess 中，18 条为零 stage、13 条为一 stage、3 条为两 stage、1 条为三 stage，没有超过三 stage；deep-chain oracle 仍要求 overflow；
- current exact source → count 为 `112.0736 B/op`，一 stage filter 为 `400.0736 B/op`，三 stage count 为 `560.0736 B/op`，五 stage 为 `1000.0736 B/op`；
- JFR 将可控分配归因到 anonymous exact Source、80 B `*Rows` handle、五组 parallel arrays、Cursor 和 Column diagnostic String；
- 33 张 generated table 的 Rows family class aggregate 为 `957,266 B`，因此 Stage 2 同时设置 allocation 与 code-size Gate；
- FJSP 100k 基线 checksum、plan hash稳定，solve median `282.045 ms`、solve allocation median `3300.045 B/operation`。

所有数字都是本机 Stage 1 诊断，不是正式性能承诺。clean v1 与 dirty benchmark-harness v2 的 provenance、artifact checksum 和限制见 Evidence 文档。

## 8. 阶段状态

| 阶段 | 状态 | 出口 |
|---|---|---|
| Stage 0：产品/IR/术语候选 | 完成 | 识别需要更上游的 Access Model |
| Stage 1：Access Model、evidence、裁决与详细设计 | 完成 | 全部决策关闭；实施包完整；Gate 通过 |
| Stage 2：production/public 原子实现 | 工作树完成 | target API/runtime/tests/scenarios 全部落地，无双轨 |
| Stage 3：验证、调优与一致性 | 工作树 Gate 完成 | correctness/performance/scenario/full Gate 均通过；正式固化前需在 immutable candidate 重放 |
| Stage 4：正式固化与 Temporary 删除 | 未开始 | 唯一 Owner 原子更新，Governance Report，删除本目录 |

阶段只是同一治理专题的执行顺序，不是降级 roadmap。Stage 2 每个切片都必须是最终设计的有效子集，最终 cutover 不保留 temporary alias 或第二套 executor。

## 9. 验证与停止条件

Stage 1 收口至少执行：

```text
./scripts/check-docs.sh
./scripts/check-post-cutover-components.sh
git diff --check
./scripts/check.sh
```

Stage 2 的 correctness、identity、allocation、GC、code-size、FJSP 与 scope non-regression Gate 由实施包统一拥有。若实现要求改变 Schema identity、callback/failure/stats 语义、引入 generic runtime、保留兼容双轨或越过本专题能力边界，必须停止并回到 Owner 裁决。

## 10. 正式固化与退出

Stage 2/3 已在基于 `2f0d116` 的未提交工作树完成。只有用户授权正式切换，且在
immutable candidate 上重放必要 Gate 后，才执行：

```text
accepted Temporary facts
  -> Blueprint / Design unique Owners
  -> Implementation Map / Conformance / Engineering
  -> commit-bound Governance + Performance Report
  -> checker + full Gate
  -> delete this Temporary topic
```

正式文档在最后保持原子切换，不提前进入中间状态。当前唯一下一步授权问题是：
是否提交 Stage 2 候选并执行正式 Owner 固化、Governance/Performance Report 与
Temporary 删除；当前工作树 Gate 通过本身不自动扩大该权限。
