# SOMA Transformation Model 与 Typed DataFlow 产品化治理报告

类型：Report / Governance

状态：当前

Owner：SOMA Transformation/DataFlow 治理输出

受众：SOMA 产品 Owner、维护者与 Gate reviewer

适用版本：production candidate `2aa8c15`，industrial application trace
`586d523`

输入事实源：正式 Blueprint/Design/Implementation Map/Conformance、production
code、generated artifacts、contract/reference/consumer evidence、component 与
reference-application baseline、Zulu JDK 8 完整 Gate

事实范围：本专题意图、最终设计、实施覆盖、正确性/性能 evidence、工业应用验证、
scope non-regression 与正式收口

非事实范围：重新定义正式 Design、跨环境性能 SLA、G6 或 public release readiness

最后审查日期：2026-07-27

## 1. 结论

本专题已按冻结目标完整实现并通过 promotion preflight。SOMA 当前产品形态为：

```text
Schema
  -> compiler-specialized packed columnar runtime state
  -> Access Model
  -> typed Transformation Definition
  -> bound one-shot Invocation
  -> detached Result / safe-point Effect
```

Access Model 没有被 generic graph 取代；Candidate Scan 仍是专用 one-shot fast
path。Transformation Model 拥有 Shape、Expression、Operator、Result、Effect、
lineage、order 和 legality；Typed DataFlow 是可复用定义、绑定和执行这些语义的
机制。application 仍拥有业务 control flow、I/O、跨 Table sequencing 和恢复。

Stage 0–6 已完成，Temporary 中的稳定事实已提升至唯一正式 Owner，过程文档已
删除。G6 仍因真实发布事实不足而 blocked，本专题没有扩大 release claim。

## 2. 产品与设计闭环

新增两个长期 Design Owner：

- [Transformation Model](../docs/design/transformation-model.md)：定义 Data Shape、
  Value/Expression、Selection/Projection/Aggregation/Prefix/Partition/Combine/
  Rearrangement/Join/Expand/Window、Result/Effect/Delta 和组合合法性；
- [DataFlow Execution Model](../docs/design/dataflow-execution-model.md)：定义
  Definition/Template/Context/Invocation、binding、identity、resource、
  diagnostics、sequential/parallel 与 safe-point commit。

产品 Blueprint、设计宪法、系统架构、Schema/generated API、correctness、
ownership、materialization、performance 和 compatibility Owner 已同步。当前
协议为 generated/runtime v5、transformation/kernel v1；annotation Schema hash
和 RuntimePlan v3 责任保持独立。

明确非目标包括 SQL/数据库、无限 stream、自动增量视图、full/cross/theta join、
跨 Table transaction、blocking I/O、隐式 common pool、off-heap/native backend
和 Java 8 之外的产品。

## 3. 实施闭环

新增独立 `soma-dataflow` 模块，并由 processor 为每张 Table 生成至多一个
`<Table>DataFlow` companion；keyed Table 同时生成 typed Delta 与 safe-point
`applyDelta`。实现覆盖：

- packed/exact/point/current Index/Column/Key/child source；
- lazy filter/skip/limit/stable sort/top-k 与低物化 terminal；
- primitive/object projection，count/match/sum/average/min/max/prefix；
- predicate partition、ordered combine、GroupBy；
- inner、left-outer、left-semi、left-anti equi Join；
- owned-child expand、finite count/range window；
- borrow、IndexSnapshot、detached columnar、materialize；
- candidate update/remove 与 ordered keyed Delta；
- immutable Definition/Template、one-shot Invocation；
- managed dedicated 或 borrowed executor 的 adaptive parallel。

没有引入 generic object hot-path executor、Java Stream、per-element DTO/boxing
collection、v4 compatibility adapter、全局 cache 或第二套 storage/lifecycle。

## 4. 按构造即正确

生产代码而不是洪水式测试承担主要正确性责任：

| 不变量 | 唯一 Owner | 防线 |
|---|---|---|
| Value、parameter、source 与 callback fence | typed expression / `ParameterSlot` | immutable construction |
| Shape、lineage 与 operator legality | shape-specific flow type | 非法组合不进入 public surface |
| source/output/effect/identity 唯一 | `DataFlowDefinition.Builder` | one-shot build，publish 前真实校验 |
| compatibility、binding、budget、lifecycle | Template / Invocation | stable public failure、one-shot state |
| detached/live、epoch、effect publish | shape result / generated effect bridge | commit 前真实校验 |
| generated/runtime compatibility | processor / generated binding | v5/hash/ownership fail closed |

内部 `assert` 只守护已由上游证明、关闭后不会破坏数据或语义的推导事实；越界、
错误 lineage、失效 Index、错误 publish 或原子性风险仍使用真实 internal
failure。

Evidence 集中在构造契约、性质/reference differential 和代表性组合，没有为每个
方法复制相同 null/lifecycle 用例，也没有冻结 private helper 或内部数组布局。

## 5. 正确性与兼容性 evidence

以下 evidence 均在 production candidate 上通过：

- Slice A–F canonical contract 与 public/generated `javap`；
- Definition/Builder/Invocation/Context/resource/cancellation/parallel failure；
- fixed seed `534f4d4152454631` 的 48-trial plain-array reference differential，
  覆盖 Candidate、Projection/Reduction/Prefix、Partition/Combine、Group、四种
  Join 和 Window；
- sequential/parallel result identity；
- external Java 8 Maven consumer 同时消费 direct Access 和 generated DataFlow；
- schema/hash/golden、generated-runtime binding、keyed Delta、child/breadth；
- one-companion-per-Table、clean/repeat byte stability 与 `+15%` footprint Gate。

三个代表 surface 的 DataFlow footprint：

| Surface | Table / companion | source bytes / lines | family class bytes / nested |
|---|---:|---:|---:|
| neutral benchmark | `6 / 6` | `68,269 / 684` | `232,402 / 61` |
| industrial scheduler | `9 / 9` | `100,661 / 987` | `363,990 / 91` |
| grassing simulation | `2 / 2` | `21,780 / 224` | `77,658 / 20` |

## 6. 性能结论

DataFlow component baseline 固定为 3 fork、checksum identity、p90/all-equal
Gate。关键结论：

- 4,096 项 sum 保持 sequential；65,536 项 branchy sum 使用 parallel 后由约
  `366,600 ns/op` 降至 `109,320 ns/op`；
- primitive projection parallel 由约 `446,294 ns/op` 降至
  `184,826 ns/op`，但小规模并行开销高于 sequential；
- adaptive 默认 crossover 因此保守固定为 `65,536` candidates；
- `limit(1)` effect 的 scratch 由约 264 KiB 降至约 1.8 KiB；
- 15 lanes 的 sequential/parallel checksum 一致，最大 Young GC 为 2，
  Full/unknown GC 为 0。

当前 physical decision 为 branchy primitive parallel、stable left hash join、
buffered partition、finite offset window；Tree Reduction、Merge/Sort-Merge Join、
in-place partition 和 vector kernel 仍是可替换 physical strategy，不进入 public
语义。

所有数字只用于当前 Zulu JDK 8/macOS aarch64 回归，`claimAllowed=false`。

## 7. Industrial application trace

工业动态调度保持应用自有 primitive `CandidateFrontier` 负责高频 refresh 和
global best-one。新增 `AssignmentSummaryFlow` 只从 authoritative
`OperationAssignmentTable` 推导：

- assignment count；
- global makespan；
- `job + due + priority` 分组的 job completion；
- detached tardiness、weighted tardiness 和 execution evidence。

最初实现按 operation 累加 tardiness，被 canonical correctness Gate 识别为领域
错误；修复落在事实 Owner，以每个 job 的最大 end time 计算。没有改变 Schema、
Index、public Solver API、Result identity 或 dispatch hot loop。

default、large、long-run 各一次 5-fork non-regression 全部通过原阈值：

| Profile | solve range | hot allocation | Young / Full GC |
|---|---:|---:|---:|
| 1,000 operations | `14.68..15.75 ms` | `4.06 MB` | `0 / 0` |
| 100,000 operations | `1.30..1.33 s` | `121.93..126.59 MB` | `2 / 0` |
| 10,000 long-run operations | `47.45..50.91 ms` | `11.86..14.99 MB` | `1 / 0` |

仅 generated-runtime v5 的 RuntimePlan hash/provenance 迁移，阈值未重算或放宽。
`grassing-individual-simulation` 明确不做业务 DataFlow 迁移；它只参与生成协议、
companion footprint 和完整构建的非回归验证。

## 8. Promotion preflight

在 production candidate `2aa8c15` 上执行完整：

```text
./scripts/check.sh
```

环境为 Azul Zulu OpenJDK `1.8.0_492-b09`、Maven `3.9.16`、macOS
`26.5.2`、aarch64。结果为 `project-check: ok`，覆盖 reactor、compiler/codegen、
public/generated/runtime、Access、child、external consumer、DataFlow Slice A–F、
reference differential、benchmark smoke、post-cutover、两个 reference
application 与 3-fork DataFlow baseline。`claimAllowed=false`；G6 不在该结果中。

正式文档固化只改变 Owner/导航，没有修改该候选代码，因此收口后只重跑文档 Gate
与 `git diff --check`，不重复三分四十秒的全量 Gate。

## 9. Scope non-regression

- 产品定位、Transformation 最低能力包络、parallel、safe-point handoff 全部落地；
- annotation Schema、Access Model、Candidate Scan fast path、current Index、
  ownership、lifecycle、swap-remove 和单 Table 失败原子性未缩水；
- 两个 reference application、六个 workload、既有阈值和 G0–G5 未弱化；
- industrial trace 是真实 production consumer，不以 fixture 或 test-only bypass
  代替；
- grassing 业务迁移按授权明确排除，没有被暗中扩大；
- 未引入第三方依赖、Java 8 之外支持、release readiness 或外部同步能力；
- 没有 Temporary API、双协议、重复 Owner、future rewrite dependency 或未登记
  implementation compromise。

当前 Conformance 只保留原有 CF-005/CF-006 发布准备差距；本专题没有新增产品
偏差。后续数据库/MES 同步须另开 “RTD Runtime State Synchronization” 设计专题，
不得反向扩张 DataFlow 的计算边界。
