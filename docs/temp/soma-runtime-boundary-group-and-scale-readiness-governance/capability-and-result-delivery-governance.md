# SOMA Capability Model 与 Result Delivery 治理

类型：Temporary

状态：active（P4 Capability/Result Delivery 已通过 P5 独立审计）

Owner：SOMA capability model、physical binding 与 result delivery governance

正式事实源：否

实施授权：当前阶段仅限本专题 Temporary 文档收口和对 SOMA 的只读审计；正式
Blueprint/Design、production、test、benchmark、public/generated contract 与构建
变更由后续 Goal phase 和 accepted design 控制

事实范围：本专题确认的 capability-first 审查方法、封闭能力集合、能力绑定与替换
边界、Eager Detached 默认能力、callback-scoped streaming 受限试点、TV9 evidence
与停止条件

非事实范围：已接受的 Capability public API、开放插件/SPI、精确 callback
signature、ordinary Iterator、closeable pull cursor、production 实施或 readiness

上位专题：[SOMA Runtime Boundary、Group 与 Scale Readiness 治理指导](README.md)

设计输入：[SOMA 系统设计、核心抽象与叙事再审视](system-design-and-narrative-governance.md)

技术输入：[Scale Architecture 技术假设与验证协议](scale-architecture-technical-validation.md)

集成决策：[Runtime Boundary、Group、Scale Readiness 与产品化集成设计](integrated-final-design.md)

最后审查日期：2026-07-28

## 1. 治理意图

SOMA 可以被理解为：

> schema-defined state owners + compiler-bound capability set + deterministic execution。

Capability 是 SOMA 的核心抽象轴之一，但不是唯一抽象。它不能脱离状态 Owner、
Metadata、Plan、lifecycle、failure 和 evidence，退化成一组任意接口或运行时插件。

本专题使用三层心智模型：

```text
State / Owner
  -> SomaGroup、root Table、owned child、Result

Capability
  -> Storage、Access、Mutation、Relation、Execution、Result Delivery、
     Resource、Observation

Plan / Lifecycle
  -> Metadata、Descriptor、Plan、Effective、Template、Invocation、Observation
```

最终设计应允许在不改变稳定产品语义的前提下，局部替换经过验证的物理实现；这种
可替换性服务于长期演进，不等于向 application 开放任意实现注入。

## 2. V1 封闭 Capability Set

最终详细设计至少审查以下能力族：

| 能力族 | 稳定语义责任 | 典型 Owner |
|---|---|---|
| Schema / Metadata | schema identity、Descriptor、Plan、Effective、Observation | processor、runtime control plane |
| Storage / Layout | packed columns、capacity、Segment、presence、retained state | runtime-core |
| Access | Point、Candidate、Column、Key、Bulk、Ownership | generated facade、runtime-core |
| Mutation | append、batch、Delta、update、remove、clear、atomic publish | runtime-core |
| Exact / Relation | Primary、Unique、Index、Group、Join、Expand | runtime-core、dataflow |
| Transformation | Shape、Expression、Operator、Definition、Template | dataflow |
| Execution | Invocation、kernel、scheduler、parallel、cancel、merge | dataflow |
| Result Delivery | Eager Detached、callback-scoped streaming | dataflow/result Owner |
| Resource | storage、scratch、output、task、worker、GC headroom admission | runtime-core、dataflow |
| Observation / Failure | stats、explain、structured error、compatibility | cross-owner |

这是一组设计能力，不要求每一项都对应一个 public Java interface。最终类型形态由
使用频率、替换边界、hot/cold path、generated API 和 compatibility 共同决定。

## 3. Capability 准入规则

一个候选只有同时回答以下问题，才成为长期 Capability：

1. 它是否表达稳定、独立的语义责任；
2. 是否有唯一 Owner、lifecycle、invariant 和 failure boundary；
3. 是否存在真实的局部替换可能，而不是为了抽象而抽象；
4. 物理选择能否在 create/bind/operation boundary 完成；
5. 替换后 public/generated semantics、determinism 和 compatibility 是否保持；
6. 是否有独立 contract、differential、performance 或 application evidence。

否则它只应是内部 helper、physical strategy 或实现细节。

## 4. 接口、泛型与 hot path

- interface/generic 适合 public/generated typed contract、cold control path、
  build/bind、test oracle 和 package-private strategy boundary；
- V1 不建设 ServiceLoader、动态插件 registry、第三方 SPI 或任意 application
  implementation injection；
- physical implementation 默认 package-private/internal，由 Effective Metadata 在
  create/bind/operation boundary 确定；
- hot path 只消费已经绑定的 concrete typed column、ordinal、primitive state 和
  specialized/monomorphic/generated kernel；
- 不允许逐 row interface dispatch、generic `Object` storage、boxing tuple、
  Metadata interpreter、reflection 或 generic callback graph；
- “可替换”不要求同时保留多条 production canonical path；完成切换后旧路径必须按
  replacement closure 退役。

## 5. Result Delivery Capability

Result Delivery 与 Candidate intermediate、Materialization、Effect 是不同边界。
本专题冻结两种候选能力：

### 5.1 Eager Detached

Eager Detached 继续是 V1 默认能力：

- 完整构造后一次性发布；
- terminal 返回后不持有 source guard；
- 结果可脱离 source lifecycle；
- failure、cancel 或预算拒绝不暴露 partial result；
- 适用于 scalar、bounded detached columnar、materialized object 和完整 Effect。

### 5.2 callback-scoped streaming

callback-scoped streaming 已被 TV9 接受为唯一 limited read-only Lazy Output 试点：

- 只在明确选择的 read-only terminal/interface 上试点；
- 同步、one-shot，只在 terminal 调用栈内消费；
- generated Cursor、guard 和 live borrow 不得缓存、逃逸或跨 operation 使用；
  String getter 返回的 immutable String value 可以由 application 保留；
- source guard、scratch、budget、cancel 和 cleanup 由 SOMA 在调用范围内关闭；
- callback failure 必须确定性传播并释放 guard/scratch；
- callback 已执行的 application side effect 不由 SOMA 回滚；
- callback consumer 支持有界 early stop；P5 已将 generated Candidate visit、
  boolean consumer、`DeliveryResult` 以及唯一
  Definition→Template→Invocation lifecycle 冻结；
- 不用于 mutation、Effect、跨 root commit 或需要完整结果后才能发布的语义；
- 不把高扩张度关系变成“可支持”：能够证明超预算或无法由 compiler/plan/validated
  maintained facts 证明 finite bound 的输出，除独立有界 scalar/fused terminal 外，
  都在 relation enumeration/callback 前拒绝。

### 5.3 明确排除

本次治理不新增：

- ordinary `Iterator<T>` 返回值；
- closeable pull cursor / `ResultCursor`；
- terminal 返回后继续持有 source guard 的对象；
- Python Generator、`Flow.Publisher`、Reactive Streams 或异步 push；
- partial detached result publication。

现有 Table callback-scoped `Cursor` / `UpdateCursor` 是 Access 能力，不等于本节
排除的 closeable pull cursor。现有 DataFlow Candidate/Value/Group/Join/Window
`borrow(consumer)` 则是 incumbent Result Delivery surface，必须迁入第 5.2 节唯一
lifecycle 并删除旧 signature，不能重分类成 Access 后双轨保留。

## 6. TV9：Result Delivery Technical Validation

TV9 已在独立 Lab 中完成，只比较：

```text
Eager Detached baseline
callback-scoped streaming candidate
```

验证至少覆盖：

- Small/Medium 固定税；
- `1M / 10M` 完整消费；
- first、`1K`、`1%`、`50%` 等 early-stop profile；
- 只让 final candidate 进入一次 bounded single-100M projection，以及一次两个
  100M root source 的 bounded/early-stop relation qualification；
- allocation、retained peak、scratch、JVM heap 与 GC；
- primitive、reference-backed String 和 typed projection；
- bounded Join/Group result；
- consumer exception、cancel、deadline、source mutation/release conflict；
- deterministic order、checksum、guard/scratch cleanup；
- high-expansion output 的 preflight rejection，不实际枚举巨量结果；
- 与 Eager Detached 相同的 logical result、failure category 和 resource accounting。

100M lane 不用于调参，也不尝试 full high-expansion pair output。TV9 不验证 public
API 命名，不引入 production SOMA code，也不重新打开
Iterator/cursor/Publisher 候选。

判定语言只有：

```text
accepted for limited read-only pilot
rejected
inconclusive
```

实际裁决：

- callback-scoped streaming：
  `accepted for limited read-only pilot`；
- Eager Detached：继续作为默认；
- independent reference、callback-driven early stop、逐项 String value/reference、
  bounded Group/relation、consumer failure、cancel/deadline、mutation/release
  conflict、cleanup 与 canonical typed preflight 均通过；
- 1M primitive / 10M String callback allocation 都是 `184 B`，对应 Eager 为
  `16,000,240 B` / `200,000,256 B`；
- logical single/double-100M 只证明 synthetic Result Delivery mechanics，不形成
  production readiness；
- implementation/raw/decision revision：
  `75fe7a7` / `bbc13e8` / `cf322ab`。

## 7. 最终设计必须关闭

1. Capability 的稳定语义、Owner、lifecycle、invariant 和替换边界；
2. 哪些是 public/generated typed contract，哪些只属于 internal strategy；
3. Capability binding 与完整 Metadata/Effective Plan 的关系；
4. Eager Detached 的默认性和 callback streaming 的显式 opt-in surface；
5. callback-scoped Cursor、可保留 String value、early stop、exception、cancel、
   guard 和 diagnostics；
6. Result Delivery 与 Materialization、Borrow、Effect、output budget 的非等同边界；
7. compatibility、migration、test、benchmark 和 Example adoption。

## 8. 停止条件

- 不因“未来可能替换”制造开放 SPI、公共 physical strategy 或多条永久 canonical path；
- 不因 TV9 接受受限 mechanics 就提前固定 callback public signature；
- 不用 streaming 掩盖无界 Join、未知 cardinality 或缺失 budget；
- 不将 read-only callback 试点扩张为 mutation/effect/transaction；
- 不因新增 capability layer 引入 per-row dispatch、boxing、generic object 或
  Metadata interpreter；
- TV9 已到达 decision point，不再增加候选或重复实验。
