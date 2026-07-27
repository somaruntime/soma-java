# SOMA Runtime Boundary、Group、Scale Readiness 独立设计与范围审计

类型：Temporary

状态：`passed`；P6 promoted at `d5f713d`

Owner：P5 independent design/scope audit

正式事实源：否

事实范围：对 P4 集成设计的独立审计、blocking finding、最小修正与复核结论

非事实范围：正式产品语义、production conformance、qualification 或 release readiness

初始审计对象：`develop@335a9629e6d0`

最后审查日期：2026-07-28

## 1. 审计方法

三个相互独立的只读审计分别检查：

1. TV0–TV9 A01–A21 evidence 是否被过度外推；
2. 当前 production/public/generated surface 是否能按 slice 实施且不缩减能力；
3. 三个 Example、产品旅程与 qualification 是否具有可判定验收。

审计只报告会改变设计、实施可行性或 Goal DoD 的问题；不重开 TV9 候选、不做命名
漫游，也不把当前实现困难当成降低目标的理由。

初始一致结论为：

```text
CHANGES_REQUIRED / BLOCKED_FOR_P6
```

## 2. Blocking finding 与修正

| ID | 初始 finding | 设计风险 | 已应用的最小修正 |
|---|---|---|---|
| P5-B01 | multi-source 被强制要求同 schema、同 explicit Group、同 root 仅一实例 | 静默删除 cross-Group/cross-schema/self-join/active-staging 能力 | `SomaGroupPlan` 支持多 schema、多 stable slot；read-only Invocation 保留跨 Group/aggregate acquisition |
| P5-B02 | Group Metadata、统一 Observation、DataFlow Explain 与 guard acquisition 双 Owner | runtime-core/dataflow 反向依赖或第二事实路径 | runtime Metadata、Group/Table Observation、DataFlow Explain、Invocation Observation 精确分责；Invocation 独占 guard protocol |
| P5-B03 | Group create、member attach、parent/root/Invocation ledger 无原子协议 | 双计数、check-then-commit、release underflow、Small 税不确定 | frozen slots；Group create 只验证 envelope；attach parent reserve→private construct→publish-once；root 仅 attribution；Invocation 独立 |
| P5-B04 | declared String profile 被描述为 production hard admission | runtime 无法在禁止 identity set/reflection 时验证 length/sharing/identity | structural exact hard ledger、String `DECLARED_UNVERIFIED` estimate、qualification-only actual heap/dedup 三种强度 |
| P5-B05 | 四类类型未处置 live generic `Object` public/generated protocol | 实施会保留冲突 path 或制造 temporary adapter | 增加 current surface→closed kind→RETAIN/MIGRATE/REMOVE→slice/protocol replacement matrix |
| P5-B06 | callback facade 绕过 `Definition→Template→Invocation` | 第三 execution lifecycle、consumer 污染 identity | callback 类型只是现有 lifecycle 的 generated typed facade；visitor 是 invocation parameter；legacy borrow 原子迁移 |
| P5-B07 | qualification 缺 Column/Batch/Window/Fast/Soak 与客观判定 | artifact 存在但 DoD 不可判定 | 补全 workload、String Small/Medium、Q-SOAK、preregistered environment/budget/oracle/timeout/fork/comparator/status |
| P5-B08 | large locator 被强制 segmented，但 TV7 只验证 flat | evidence 过度外推并可能伤害 point path | `FLAT/BOUNDED_SEGMENTED` internal candidate；flat 是 baseline；segmented 必须先过 production point/rehash/growth evidence |
| P5-B09 | unknown relation bound 没有 fail-closed 规则 | callback 可绕过 resource predictability | 无法由 compiler/plan/validated facts 证明 finite bound 时，除独立有界 scalar/fused terminal 外在 enumeration/callback 前拒绝 |

## 3. Important finding 与修正

| ID | finding | 已应用修正 |
|---|---|---|
| P5-I01 | Group/member admission 时点和 membership currentness 不明确 | slot 在 GroupPlan 冻结；attach 原子 admission；membershipEpoch 只影响 topology snapshot，Template 显式 bind |
| P5-I02 | relation fan-out/reuse crossover 仍是 inconclusive | versioned formula 消费 access cost、fan-out/skew、reuse、preaggregate/hash scratch、output bound；进入 Explain/Q evidence |
| P5-I03 | callback production evidence 过于概括 | Q-DELIVERY 增 generated cursor/value、segment boundary、outer absence、non-escape/use-after、real cancel/deadline、String/GC/resource |
| P5-I04 | String frozen semantics 没有全部进入 qualification | Q-1M 增 optional/presence、equal-value/different-object、epoch/reference、order/filter/materialization、String leaf |
| P5-I05 | `expectedMaximumRows` 同时像 hint 与 hard limit | 拆为 `planningRows` hint 与 `maximumRows` hard contract |
| P5-I06 | stable Group identity 未闭合 | `logicalGroupId` 与 opaque runtime `groupInstanceId` 分离；本轮不新增 snapshot codec/API |
| P5-I07 | breaking migration 只有顺序，没有 exact impact | 增 surface/classification/replacement/identity/evidence matrix |
| P5-I08 | Candidate-only callback 与 incumbent Borrow/RTD 不一致 | current borrow shapes 统一迁入一个 callback lifecycle；RTD 默认 retain Eager，P9 只按真实收益采用 |
| P5-I09 | Guide 只有主题清单 | 增 compile/run simple/advanced journey、String/scale example、diagnostics cookbook 与 snippet Gate |
| P5-I10 | P8 migration 与 P9 Example 审计混合 | P8 只做 contract-required migration；P9 才做独立产品审计，禁止展示性重构 |
| P5-I11 | 一次性 Lab/Temporary 清理要求缺失 | P6 design-first promotion 与 P11 evidence/reference closure 后删除、不归档规则显式化 |

## 4. Scope non-regression 检查

修正后设计必须同时保持：

- SOMA 仍是 runtime-state computing library，不降为 column storage；
- 四类 V1 类型、String reference baseline 与 arbitrary object exclusion 不变；
- Eager Detached 默认，callback-scoped streaming 是唯一 Lazy Output capability；
- Small/Medium/1M/10M、single/double 100M 与 String profile 全覆盖；
- 一个 bounded scheduler，Segment/Morsel/Execution Vector 三层正交；
- Group 是可选 composition/resource/lifecycle Owner，不删除独立 aggregate
  multi-source；
- G6/public readiness 继续 blocked，Lab/local evidence 不外推。

## 5. Re-audit exit

复核必须基于修正后的精确 commit，并逐项回答：

1. P5-B01–P5-B09 是否全部关闭且没有产生新的 parallel fact；
2. P5-I01–P5-I11 是否有可实施 Owner/slice/evidence；
3. S1–S10 是否均为最终设计的有效子集，不依赖 temporary public API；
4. qualification 是否可客观判定且没有用 synthetic evidence 冒充 production；
5. P6 是否可以在 Design target 与 Conformance current gap 同时原子固化。

复核对象：`develop@f226d569e6d0`

三路差量复核结果：

| audit | result | conclusion |
|---|---|---|
| TV0–TV9 evidence boundary | PASS | locator、unknown bound、callback/String production requalification、relation formula 与临时资产清理均关闭，无 overclaim |
| production implementability | PASS | 原 7 BLOCKER / 3 IMPORTANT 全部关闭，无新增实施 blocker |
| product journey/Example/qualification | PASS | Group admission、Owner 投影、可判定 qualification、RTD、Guide 与 P8/P9 分层全部关闭 |

P5 最终裁决：

```text
PASS / PROMOTED_IN_P6
```

该裁决只表示设计通过了正式 promotion 门槛；`d5f713d` 已完成 P6。它不表示
production 已实现、qualification 已通过或 G6 readiness 改变。
