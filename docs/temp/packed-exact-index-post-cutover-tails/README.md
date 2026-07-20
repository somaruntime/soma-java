# Packed Index / Exact Access 切换后尾项治理临时设计

状态：临时设计（待分项决策与实施）
正式事实源：否
实施授权：无；本文只记录尾项，不授权修改正式 Owner、API、Schema 或 runtime
专题实施基线：`4b6fa43 perf: adopt packed exact indexes and swap removal`
来源报告：[Packed Index / Exact Access / IndexBuffer 重设计实施收口](../../../reports/2026-07-17-packed-exact-index-runtime-redesign-report.md)
正式事实 Owner：[Generated Table API 契约](../../generated-table-api-contract.md)、[Runtime 正确性模型](../../runtime-correctness-model.md)、[Runtime 性能模型](../../runtime-performance-model.md)、[Runtime 性能实现契约](../../../soma-runtime-core/docs/runtime-performance-implementation-contract.md)、[文档治理规则](../../documentation-governance.md)
最后审查日期：2026-07-19

## 1. 定位与结论

本文记录 Packed Index / Exact Access / IndexBuffer breaking cutover 完成后的遗留治理项，防止它们散落在对话、旧报告或源码印象中。

本文不重新打开已经完成的架构迁移，也不否定当前实现收口结论。基于 `4b6fa43` 的 fresh 审查与完整 `./scripts/check.sh`，当前状态是：

- approved design、正式 Owner、Schema/hash、generated API、runtime、examples、benchmark 与 external consumer 已迁移到 packed/exact v3；
- Sparse Set、maintained order、dirty selector rebuild 和 stable compaction 不再属于当前架构；
- keyed/dense table 均保持 packed `[0,size)`，结构删除采用 swap-remove/tail-fill；
- primary/unique/nonunique exact access 在 mutation boundary eager incremental 维护；
- 完整项目 Gate 得到 `project-check: ok`，没有发现阻断当前专题关闭的 correctness 缺陷；
- 仍有报告治理、API 误用防护、allocation/retained memory 和可维护性债务，因此不能宣称“零尾巴”。

本临时设计只拥有尾项清单、优先级、审查问题和候选实施阶段，不拥有新的 API、Schema 或 runtime 事实。任何被接受的设计决策必须先迁入唯一正式 Owner，再修改实现。

## 2. 不得回退的既定边界

后续处理本文任何尾项，都不得：

- 恢复 Sparse Set、dirty/lazy full-table rebuild、maintained order 或 stable physical traversal；
- 为降低 allocation 暴露没有 owner/epoch 防护的 live raw Index view；
- 用 hash-only identity 代替 canonical full equality；
- 破坏 packed swap-remove、mutation failure atomicity、resource preflight、structural epoch 或 child ownership；
- 把 schema object、DTO/Collection graph、reflection、metadata interpreter、Java Stream、boxing 或 per-row object allocation 引入 runtime hot storage/path；
- 把 range lookup、通用 B+ tree/skip list、Table-owned event queue、并发、跨表事务、持久化或序列化塞回本专题；
- 把 G6、publishing、SCM、签名或 support matrix 伪装为本次功能/性能尾项。

违反上述任一边界的方案不是“收尾优化”，而是新的正式架构变更，必须停止并请求用户决策。

## 3. 证据基线

### 3.1 当前实施与验证

2026-07-19 在 `4b6fa43`、Azul Zulu OpenJDK `1.8.0_492-b09`、Apache Maven `3.9.16`、macOS `26.5.2`、aarch64 上重新执行：

```text
./scripts/check.sh
```

结果为 `project-check: ok`。同轮 FJSP allocation/GC 诊断得到：

| 指标 | 当前观测值 |
|---|---:|
| solve time | `599.575125 ms` |
| throughput | `166,784.771 op/s` |
| allocated bytes/operation | `6,473.9324 B/op` |
| total allocated bytes | `647,393,240` |
| Young GC | `19 / 46 ms` |
| Full GC | `0 / 0 ms` |

这些数据是本机单次 fresh 诊断，不升级为跨机器或 production 性能声明。它与专题报告中的 `6,475.93784 B/op`、Full GC 为零及约 `8.3%` allocation 增量一致，未发现 cutover 后性能回退。

### 3.2 尾项分级

- `P1`：不阻断当前实现运行，但在宣称“零尾巴治理收口”前应处理或形成明确正式决定；
- `P2`：需要独立 evidence 或设计决策的后续优化，不应临时扩张当前 API；
- `P3`：内部可维护性和测试可读性改进，可在不改变输出的内部 refinement 中完成。

## 4. 尾项清单

| ID | 优先级 | 尾项 | 当前判断 | 处理类型 |
|---|---|---|---|---|
| `TAIL-DOC-01` | P1 | 当前报告没有直接绑定最终实施 commit，旧报告仍可被独立误读为当前架构 | 确认存在 | 报告治理卫生 |
| `TAIL-API-01` | P1 | `IndexSnapshot` stale/wrong-table 防护由调用者主动触发 | 确认存在 API 误用窗口 | 正式 API/正确性设计决策 |
| `TAIL-PERF-01` | P2 | steady-state allocation 仍高于旧基线约 `8.3%` | trade-off 已记录，仍可优化 | component benchmark + 实现优化 |
| `TAIL-MEM-01` | P2 | exact index 按最坏 distinct-group cardinality 预留 | 低基数 selector 可能过度保留内存 | retained-memory evidence + 内部设计 |
| `TAIL-NAME-01` | P2 | `KeySpace` 命名可能继续携带旧 Sparse Set 心智模型 | 候选语义债务，尚未裁决 | public/runtime naming review |
| `TAIL-MAINT-01` | P2 | `DenseTableSourceGenerator` 职责和体积过度集中 | 确认存在维护风险 | internal codegen refactor |
| `TAIL-TEST-01` | P3 | exact-index 直接组件测试粒度较粗 | 不是 coverage 缺口，但降低局部变更可审查性 | dedicated component test refinement |

### 4.1 `TAIL-DOC-01`：报告 evidence 与历史报告卫生

#### 当前事实

- 当前 HEAD 是 `4b6fa43`；
- [专题收口报告](../../../reports/2026-07-17-packed-exact-index-runtime-redesign-report.md)登记 baseline `6f91e57`，A/B 表中把新实现写为“本专题实现工作树”，结尾仅声明提交哈希以 Git 历史为准；
- [Goal execution status](../../../reports/java-v1-goal-execution-status.md)仍把 `6f91e57`写为“当前实现迁移基线”，没有直接登记 `4b6fa43`；
- [Phase 3 access structures 报告](../../../reports/java-v1-phase-3-access-structures-report.md)、[旧 G3 runtime-core 报告](../../../soma-runtime-core/reports/java-v1-g3-runtime-core-report.md)和[旧 G5 examples 报告](../../../soma-examples/reports/java-v1-g5-examples-report.md)仍描述 `@SomaOrder`、dirty/lazy rebuild、`SparseIntKeySpace` 或 maintained order；
- 根级报告索引已经用 2026-07-17 专题报告覆盖当前 G0–G5 形态，因此正式设计事实没有漂移，但直接打开旧 contributor report 仍可能误读。

#### 推荐出口

1. 用 dated post-cutover audit/report 明确绑定实施 commit `4b6fa43` 和 fresh Gate 环境；
2. 更新 Goal execution status 的当前实现引用；
3. 在旧 Phase 3、G3、G5 contributor report 的索引入口增加明确的 `superseded` 标记，或者按文档治理规则迁入 `reports/archive/`；
4. 不重写旧报告当时的数值和历史结论，只修正当前性导航。

#### 完成条件

- 从根报告索引、模块报告索引和 Goal status 均能唯一到达当前 cutover evidence；
- 旧报告单独阅读时不会被误认为当前 runtime/API 事实；
- `./scripts/check-docs.sh` 与 `git diff --check` 通过。

### 4.2 `TAIL-API-01`：`IndexSnapshot` 的强制安全边界

#### 当前事实

`IndexSnapshot` 携带 owner token、structural epoch 和 detached `int[]`。`indexAt(position)` 只做 snapshot 内部 position 边界检查；generated table 的 `requireCurrent(snapshot)` 才执行 wrong-table、stale epoch 和 current Index 范围验证。

因此存在如下误用窗口：

```text
capture snapshot
  -> table structural mutation / swap-remove
  -> caller未执行requireCurrent
  -> snapshot.indexAt(i)仍返回数值合法、但可能已指向其他record的Index
```

当前 single-thread ownership 模型避免并发竞态，但不能阻止调用者在同一线程中遗漏校验。现有设计是“提供安全检查”，而不是“强制所有 live Index 消费都经过安全检查”。

#### 待决问题

1. raw `IndexSnapshot.indexAt()` 是否应继续作为公开 detached sequence 原语；
2. stale 检测应在整个 snapshot 消费前执行一次，还是在每次 live table access 时执行；
3. 是否引入 generated guarded accessor，例如 `table.indexAt(snapshot, position)`；
4. 是否引入 callback-scoped current-index consumption，并在 callback 内禁止结构修改；
5. 是否需要把 raw/unsafe 与 guarded API 显式分层；
6. owner/epoch 引用和校验频率对 hot path allocation、inlining 与吞吐的实际影响。

#### 禁止捷径

- 不能删除 structural epoch 或 detached ownership；
- 不能仅依赖文档警告而声称“API 已强制安全”；
- 不能未经正式 API Owner 决策增加、删除或重命名 public/generated method；
- 不能为零校验开销暴露 live mutable `IndexBuffer`。

#### 完成条件

- 用户先裁决安全性与 hot-path 成本的取舍；
- 接受的语义先迁入 Generated Table API 契约和 Runtime 正确性模型；
- wrong-table、stale、current、swap-remove 后误用和 callback mutation 均有明确 test oracle；
- 若引入新路径，必须补 component allocation/throughput 对照并重放 public API、external consumer 与完整 Gate。

### 4.3 `TAIL-PERF-01`：allocation 尚未优化到极限

#### 当前事实

当前 FJSP 的 CPU 第一热点和 read-time full-table sort 已消除，Full GC 保持为零；但 allocation/op 比旧 dirty/rebuild baseline 高约 `8.3%`。已知 allocation boundary 至少包括：

- `rowIndexes()` 把 table-local selection 复制成 detached `IndexSnapshot`；
- Row Pipeline 第一次 stage expansion 分配 stage arrays，每个 intermediate 仍形成 one-shot generated Rows wrapper；
- Cursor、materialization、key/value export 和 application-owned Value Object；
- 尚未提供 allocation-free single-index terminal。

这不是当前 correctness 或可用性 blocker，但说明“临时对象/GC 问题已彻底解决”不是允许的 claim。

#### 推荐 evidence 顺序

1. 建立 `exact source -> filter -> sorted -> limit(1) -> rowIndexes` component allocation lane；
2. 分离 source/Rows wrapper、stage arrays、selection scratch、snapshot copy、materialization 和 application Value Object；
3. 区分 import/growth retained allocation 与 steady-state lookup allocation；
4. 使用 JFR 或 current-thread allocation counter 做归因，不用 wall-clock 单次结果猜测；
5. 只有在新 public contract 已明确后，才评估 `firstIndexOrThrow` 或 callback-scoped first terminal。

#### 完成条件

- 有同语义、同环境、可重复的 component baseline；
- 优化保持 detached snapshot、full equality、resource preflight 和 mutation atomicity；
- 完整 Gate、FJSP semantics/checksum 与 Full GC 结果不回退；
- 没有证据前不预设必须达到的 B/op 数字，也不把单机结果升级为 production claim。

### 4.4 `TAIL-MEM-01`：exact-index distinct group 容量

#### 当前事实

`GroupedExactIndex(int expectedRows)` 同时把 row capacity 和 group capacity 设为 `expectedRows`；generated table create 使用：

```text
GroupedExactIndex.estimatedRetainedBytes(initialCapacity, initialCapacity)
new GroupedExactIndex(initialCapacity)
```

`reserve(expectedCapacity)` 又按所有新增 row 都可能创建新 group 的最坏情况，为每个 selector 传入 `additionalGroups = additionalRows`。

这保证 reserve 后 mutation 不因 group/bucket 临时增长而破坏 resource preflight，但对 machine/state 等低基数 selector 可能长期保留远多于实际 group 数的 group arrays 和 buckets。

#### 推荐 evidence 与候选方向

先建立以下 component matrix：

| 维度 | 至少覆盖 |
|---|---|
| row count | small / typical / configured capacity boundary |
| distinct group count | `1`、低基数、`sqrt(N)`、接近 `N` |
| mutation | append、update regroup、swap-remove relocate、clear/reuse |
| 观测 | retained/current/high-water bytes、growth/rehash、allocation、probe/collision |

得到证据后再比较：

- 内部分离 row capacity 与 group capacity；
- group/bucket adaptive growth，同时保持 publication 前完整 preflight；
- runtime plan 中的 expected distinct groups；
- 是否确有必要增加 schema/user tuning hint。

不得在缺少 workload evidence 时直接增加新的 annotation 或用户参数。

#### 完成条件

- low-cardinality 与 high-cardinality retained-memory 数据可重复；
- 选定策略对 worst-case unique selector 仍正确、可预估且 failure-atomic；
- `TableStats.exactIndexStorageCurrentBytes/highWaterBytes` 与 estimator 保持自洽；
- generated consumer randomized oracle、resource-limit、growth、collision 和完整 Gate 通过。

### 4.5 `TAIL-NAME-01`：`KeySpace` 命名裁决

#### 当前事实

Sparse Set 已删除，但以下 public/runtime contract 仍使用 `KeySpace` 作为 primary key locator 的统称：

- `TablePlan.keySpaceStrategy()` 与 canonical plan JSON；
- `TableStats.keySpaceImplementation/capacity/used/probe/collision/rehash`；
- generated `keySpace` 字段及 `HashInt/HashLong/HashCompositeKeySpace` 类型；
- runtime-core Owner 和报告术语。

`KeySpace` 可以被解释为与实现无关的 primary identity lookup 空间，因此这不是已确认的 API 缺陷；但它也可能持续让使用者联想到已删除的 ECS/Sparse Set 架构。

#### 待决问题与出口

- 明确裁决 `KeySpace` 是继续保留的 canonical umbrella term，还是 pre-release breaking rename 候选；
- 若保留，在 glossary/Owner 中明确它“不等同于 Sparse Set”；
- 若重命名，必须整体审查 `primaryLocator`、`keyLocator` 或 `primaryIndex` 的一致性，并评估 public API、RuntimePlan canonical JSON/hash、compatibility identity、golden 与 external consumer 影响；
- 未经用户批准不得仅因内部审美执行命名迁移。

### 4.6 `TAIL-MAINT-01`：code generator 职责集中

#### 当前事实

`soma-processor/src/main/java/com/hgtech/soma/processor/DenseTableSourceGenerator.java` 当前约 `4,351` 行，同时生成 facade、Rows、Cursor/Mutator、Batch、columns、primary locator、exact index、mutation、child/materialization、stats 和 RuntimePlan binding。大量 emitted source 位于长 `StringBuilder.append(...)` 语句中。

现有 golden、source/bytecode shape 与 external consumer Gate 能守住结果，但局部变更的 blast radius、diff 可读性和失败定位成本偏高。

#### 推荐出口

- 只做 internal emitter responsibility split，例如 Rows、ExactIndex、Mutation、TableFacade emitter；
- 第一轮保持生成源码逐字节不变，避免把结构重构和 API 行为变更混在一起；
- 每个拆分步骤重放 compiler/codegen golden、javap、source/bytecode forbidden-shape 与 external consumer；
- 不引入第三方模板引擎或 runtime metadata interpreter。

### 4.7 `TAIL-TEST-01`：exact-index 组件测试粒度

#### 当前事实

`GroupedExactIndex` 的直接 handwritten smoke 主要集中在 `RuntimeCorePhase1Check.testExactIndexProtocolAndStats()`；generated consumer、randomized differential oracle、collision/growth 和完整 Gate 提供了更广的跨层覆盖。因此这不是“缺少正确性 evidence”，而是组件级测试可读性与局部诊断粒度不足。

#### 推荐出口

- 在修改 group capacity、free-list、rehash 或 relocate 算法时，新增独立 `GroupedExactIndex` randomized differential test；
- 覆盖 repeated create/release group、hash collision chain、update regroup、swap-remove relocate、clear/reuse、capacity boundary 和 metrics invariant；
- 不重复已有 generated external consumer，只把 primitive kernel 的局部失败定位缩短。

## 5. 分阶段实施建议

### Stage A：post-cutover 文档卫生

只处理 `TAIL-DOC-01`。这是唯一建议在宣称“零尾巴治理收口”前立即完成的低风险项，不修改 API、Schema、runtime 或 Gate。

出口：current commit/evidence 可定位、旧报告不误导、docs check 通过。

### Stage B：`IndexSnapshot` 安全契约决策

只处理 `TAIL-API-01`。先做 API/correctness 设计审查和 microbenchmark，再由用户选择；未裁决前不得实施 public surface。

出口：唯一 Owner 接管正式决定，misuse oracle 与成本 evidence 完整。

### Stage C：allocation 与 retained-memory 专题

联合建立 `TAIL-PERF-01`、`TAIL-MEM-01` 的 component evidence，但分别裁决 steady-state allocation 与 distinct-group capacity，避免用一个指标掩盖另一个指标。

出口：同语义基线、归因、优化收益与 non-regression Gate 完整。

### Stage D：命名与内部可维护性

- `TAIL-NAME-01` 是 public/compatibility decision，必须独立批准；
- `TAIL-MAINT-01` 和 `TAIL-TEST-01` 是 internal refinement，可在生成输出不变时逐步推进；
- 不把 public rename 与 generator split 放入同一提交。

## 6. 关闭标准与文档生命周期

本临时专题满足以下条件后删除：

1. `TAIL-DOC-01` 已完成；
2. `TAIL-API-01` 已由用户裁决，并迁入唯一正式 Owner 或明确接受现状；
3. P2/P3 项分别完成、明确接受现状，或被拆成有独立边界和 evidence 计划的新专题；
4. 所有接受的稳定事实已迁入对应 Owner，报告和索引同步；
5. 与实际变更 surface 相称的 targeted validation、`./scripts/check-docs.sh`、`git diff --check` 和最终 `./scripts/check.sh` 通过；
6. 形成 dated closeout evidence 后，删除本文及其 `docs/temp/README.md` 索引项。

关闭本临时设计不要求完成 G6，也不得改变 G6 的独立状态。

## 7. 已复核且不进入尾项清单

- 当前 `GroupedExactIndex.clear()` 只对 `rowGroups`、`rowPrevious`、`rowNext` 各执行一次 `Arrays.fill(...)`；先前疑似“重复 fill”的机械残留在 `4b6fa43` 中不存在，不建立虚假待办；
- G6、publishing、SCM、签名和 support matrix 是独立发布边界，不属于本专题尾项；
- range lookup、maintained order、Table-owned event queue、stable traversal、concurrency、cross-table transaction、persistence 和 serialization 是明确 non-goal；
- `docs/temp/` 中四份长期研究蓝图由用户明确保留，且已标记非事实源，不是 cutover 遗留垃圾；
- 历史报告保留当时事实本身不是缺陷；缺陷只在当前性导航不清或缺少 superseded 标识。
