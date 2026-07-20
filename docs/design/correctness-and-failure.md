# Correctness 与 failure 设计

类型：Design

状态：正式

Owner：SOMA runtime correctness 与 failure semantics

设计层次：`Q` 横切质量

主要关注点：一致性、失败原子性、结构化错误与可信状态

上位设计：[设计宪法](soma-java-design-constitution.md)

服务蓝图：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

事实范围：可见原子性、invariant、structured errors、callback/resource failure 和失败后的可信状态

非事实范围：具体存储算法、日志策略、application 事务和编译期诊断文本

最后审查日期：2026-07-20

## 1. 稳定状态

每个公开可观察的 stable state 必须同时满足：

- live Index packed 为 `[0,size)`；
- 所有 leaf column/presence/child handle 与 size 对齐；
- keyed primary locator 与 current Index 一致；
- unique/index structures 与 leaf values/current Index 一致；
- ownership forest 无 dangling/share/cycle；
- structural epoch、view pin、active operation 和 released state 自洽；
- stats current facts 不伪造 table state。

测试必须验证这些设计不变量，而不只是复现当前代码行为。

## 2. Mutation 原子性

可能失败的 mutation 分为 validation/preflight、staging 和 commit：

```text
validate input and callback feasibility
  -> preflight identity/unique/ownership/resource/epoch
  -> stage columns, locator, exact-index and child deltas
  -> publish one coherent state
```

Expected failure 发生时，operation 开始前的 live facts、size、capacity identity、epoch 和 ownership 必须保持可用；result 的 changed/removed 为零。不得用“逐步写入后尽力回滚”作为公开语义。

Raw `VirtualMachineError` 不包装成可恢复 SOMA error。Growth 必须 stage-before-publish，使 raw allocation failure 前不破坏旧 live state，但 library 不承诺 JVM 在 fatal error 后可继续可靠工作。

## 3. Error envelope

Runtime public/generated API 使用 unchecked structured exception，至少提供：

```text
category
stable code
operation
logical table/field/selector/ownership path
bounded safe context
cause when applicable
```

Caller 不解析 message 判断恢复策略。Context 必须 deterministic、immutable、bounded、safe-to-render；不得包含 absolute local path、credential、full row dump、raw handle/bucket/Index，且不得调用任意 payload 的 `toString()`。

稳定 category 包括 invalid input、lookup、conflict、lifecycle、compatibility、resource、callback 和 internal。Code 使用 lowercase snake_case，发布后不得复用为不同语义。

## 4. Failure 分类与可信度

| 类别 | 语义 | aggregate 状态 |
|---|---|---|
| invalid input / lookup / conflict | caller 输入或当前事实不满足 operation | 仍可信 |
| lifecycle | handle、view、pipeline 或 table 已失效 | 其他合法 owner 仍可信 |
| compatibility | artifact/plan/schema 不匹配 | create boundary 不发布 aggregate |
| resource | 显式 budget/provider/preflight 拒绝 | 旧状态仍可信 |
| callback | application callback 抛出普通异常 | 按 mutation atomicity 保持旧状态 |
| internal | impossible state 或 invariant violation | 当前 aggregate fail fast，不再 normal access |

Application callback 的 cause 保留但不解析 message。已有 SOMA structured exception 原样传播；普通 application runtime exception 可以包装为 callback failure；fatal JVM error 不伪装为 recoverable callback error。

SOMA 的原子性只覆盖 table facts。Callback 已经产生的外部 I/O、日志、其他 root mutation 或 application side effect 不会被 runtime 自动回滚，调用方必须避免或自行补偿。

## 5. Reentrancy 与 escape

- 同一 aggregate active operation 期间的嵌套访问必须 fail closed；
- cursor/mutator 不能保存到 callback 外；
- one-shot pipeline/mutation 被消费后不能复用；
- stale view/snapshot/mutator 不能降级为当前 row access；
- comparator、diagnostics 或 error rendering 不触发 hidden table access。

## 6. Failure diagnostics

Error envelope 只提供 bounded、deterministic 的 failure context，不产生 stdout/stderr、全局 logger、网络/文件 I/O 或 hot-loop 格式化副作用。Stats mode、snapshot/reset、high-water 和 plan identity 由 [Runtime Plan 与可观测性](runtime-plan-and-observability.md)拥有；Application 决定日志、采样、脱敏和 trace 关联。

## 7. 跨表边界

SOMA 只保证单 table/ownership aggregate correctness。Application 在多个 roots 之间必须定义提交顺序、幂等、snapshot、compensation 或可重建事实；不能把单表 atomicity 外推为业务 transaction。
