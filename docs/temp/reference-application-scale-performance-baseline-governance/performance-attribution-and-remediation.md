# 性能归因与修复协议

类型：Temporary

状态：active（Stage 4 结果审查）

Owner：SOMA reference application scale performance baseline governance

正式事实源：否

实施授权：保持领域和 SOMA 契约语义的 evidence-backed 内部优化

事实范围：本专题性能发现的分类、证据、修复准入和停止边界

非事实范围：预先认定瓶颈 Owner、修改正式 API/Schema/runtime 语义、public claim

最后审查日期：2026-07-24

## 1. 原则

本专题测量的是完整应用 hot operation，但完整应用数字不能直接归因给 SOMA。
每个显著问题必须先证明可复现，再按以下 Owner 分类：

```text
measurement / environment
  -> non-SOMA application algorithm
  -> example SOMA state modeling
  -> example SOMA access/API usage
  -> generated/runtime internal implementation
```

“显著问题”包括：目标 workload 不可完成、OOM、非线性恶化、持续 GC、allocation
异常、index/scratch/capacity 高水位异常，或同环境多 fork 超过候选 envelope。

慢并不自动构成缺陷。若 workload 成本与声明算法、访问次数和数据规模一致，结果
可以是有效基线；只有不必要工作、错误建模、错误访问路径或内部实现浪费才进入
优化。

## 2. 归因顺序

### 2.1 Measurement / environment

先检查：

- exact workload/input/result/schema/runtime-plan identity；
- fork、warmup、measurement、heap、JVM/OS/CPU identity；
- setup、validation、日志、class loading 或 background noise 是否混入；
- allocation 是否只覆盖同步 hot operation；
- GC delta、pause 和时间源是否合法；
- 问题能否在独立 JVM 重现。

无效 artifact 先修测量，不得据其修改应用或 core。

### 2.2 Non-SOMA application algorithm

检查不由 SOMA 拥有的工作，例如：

- scheduler 每次 dispatch 的完整 frontier sort、machine/resource calendar 和
  result assembly；
- simulation 的 logical-world primitive array fill/scan、random addressing、
  trace 和 result hashing；
- 领域算法本身的复杂度是否与 workload 规模一致。

这类问题只有在保持领域结果、tie-break、系统顺序和失败边界时才能优化。不得把
减少约束、减少系统或改变结果当作性能收益。

### 2.3 Example SOMA state modeling

Table 是否需要拆分或合并，不按字段数裁决。列式 Table 很宽不表示一次操作读取
全部列。只有以下证据支持调整：

- 生命周期、ownership 或 cardinality 不同；
- capacity、clear、replace、release 责任不同；
- hot access 总是局限于独立状态域，当前布局造成额外维护；
- 索引维护作用于无关 mutation；
- 跨 Table lookup/commit 成本与拆分收益有同语义对照。

过度拆表会增加 lookup、同步和 failure boundary，同样视为设计问题。调整必须
更新 application-owned design，并保持 detached input/result 和领域语义。

### 2.4 Example SOMA access/API usage

建立每条 hot path 的 Access Pattern Card，检查：

- point lookup 是否误用 packed/group scan；
- recurring selective exact group 是否缺失正确 `@SomaIndex`；
- 低选择性或高 mutation 字段是否维护了无收益索引；
- 是否存在不必要 full sort、snapshot、materialization 或 object graph；
- ColumnView、Batch、Candidate Scan、update/remove terminal 与 scratch 是否按
  预期复用；
- callback 是否引入非必要 Value Object、boxing 或跨 Table probe。

必须同时计算读取收益与 index maintenance 成本，不能以“有查询”作为建索引的
充分理由。

### 2.5 SOMA generated/runtime internal

只有 application path 已正确建模和使用 API，且 component reproduction 仍能
重现问题时，才归因到 SOMA 内部。候选包括：

- plan/stage/cursor 或 callback 边界产生非必要对象；
- exact index probe、rehash、maintenance 或 compaction repair 异常；
- scratch 未复用或 capacity 反复增长；
- update/remove/swap-remove 执行不必要的列、索引或 validation 工作；
- generated specialization/fusion 未覆盖已设计的 canonical path。

修复只能是内部 refinement；public/generated API token、Schema artifact、
Access Model、callback/lifecycle/failure/index 语义必须保持。

## 3. 证据梯度

问题归因至少需要：

1. exact identity 的独立 JVM 重现；
2. workload scaling 点或 per-operation/per-tick 归一化趋势；
3. CPU/allocation/GC/TableStats 或等价低扰动证据；
4. 去掉非目标阶段或构造领域中性 component reproduction；
5. 同结果、同 tie-break、同 failure/lifecycle 的 A/B；
6. 优化后多 fork 复核。

JFR、profiler 和单 fork 只属于诊断 evidence；它们可以定位热点，不能单独形成
baseline 或性能结论。若工具不可用，使用已有 counters、阶段 timing 和隔离
reproduction，不猜测 Owner。

## 4. 修复准入

每个修复 slice 必须记录：

- observed symptom、workload、环境和 artifact；
- attribution category 与排除的相邻 Owner；
- before/after correctness identity；
- before/after timing、allocation、GC 和相关 high-water；
- 是否影响 production code、generated footprint 或 Gate 成本；
- 为什么该 slice 独立正确，不依赖未来重写。

允许保留“没有收益”的诊断基础设施；没有端到端或 component 收益的 production
优化必须回退，不强行合入。

## 5. 防 benchmark-specific 优化

禁止：

- hard-code profile、checksum、规模、seed 或结果；
- 为 benchmark 增加 production-only bypass；
- 跳过 validation、index maintenance、result assembly 或正常 lifecycle；
- 减少字段、约束、system、candidate 或 tick；
- 只优化当前 seed 的物理顺序；
- 用更大 heap 隐藏 allocation/GC 后沿用旧环境 identity；
- 用单次最好结果或放宽 threshold 宣称修复。

## 6. 停止条件

若修复需要以下任一变化，保留证据并请求用户裁决：

- SOMA public/generated API 或 annotation Schema 不兼容变化；
- Access Model、Index 生命周期、ownership、lifecycle、failure/runtime 语义变化；
- 应用领域约束、算法结果、tie-break 或 system publish 顺序变化；
- 缩小目标 workload 或弱化 correctness/Gate；
- 引入第三方依赖；
- 建立 public claim、支持矩阵或 release readiness；
- 将本专题扩大为新的产品能力设计。
