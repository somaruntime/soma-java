# SOMA 代码与测试规模治理

类型：Temporary

状态：active（等待最终设计与实施授权）

Owner：SOMA code/test scale、replacement 与 evidence governance

正式事实源：否

实施授权：仅限 Temporary 文档收口和对 SOMA 的只读审计；不授权删除、合并或修改
production、test、benchmark、script、public/generated contract 或正式 Gate

事实范围：本专题确认的代码与测试规模目标、审查准则、Capability/Result Delivery
影响、三个 Example 后置审计、裁决语言、替代闭环和验证边界

非事实范围：当前代码或测试已经冗余、LOC 配额、已批准的删除清单、能力/Gate 缩减、
production 修改授权

上位专题：[SOMA Runtime Boundary、Group 与 Scale Readiness 治理指导](README.md)

设计输入：[SOMA 系统设计、核心抽象与叙事再审视](system-design-and-narrative-governance.md)

技术输入：[Scale Architecture 技术假设与验证协议](scale-architecture-technical-validation.md)

能力输入：[Capability Model 与 Result Delivery](capability-and-result-delivery-governance.md)

产品输入：[产品目标形态与长任务执行](productization-and-goal-execution-governance.md)

当前事实复核基线：`6cdc34f673c7bead208173e13df913e7c0a719fe`

最后审查日期：2026-07-28

## 1. 治理意图

本专题要求围绕 accepted 核心抽象和 canonical system narrative 控制 SOMA 的代码与
测试规模：

> 使用能够完整表达设计、关闭不变量并提供必要证据的最少代码和测试。

“更小”是消除无语义增益责任、重复路径和重复 evidence 后的结果，不是预设 LOC、
文件数、类数或测试数配额。必要产品能力、明确 Owner、独立 failure domain 和
compatibility evidence 不因数量目标被删除。

最终目标：

- 一个能力只有一个 canonical production path 和一个事实 Owner；
- abstraction 只在提供语义、生命周期、不变量、边界或可替换机制价值时存在；
- forwarding/adapter/compatibility 代码有明确期限和 replacement Owner；
- tests 直接证明 Design capability，不冻结无契约意义的 private implementation；
- public/generated/default journey 与三个 Example 保持产品叙事一致；
- 同一 invariant 不在同一 failure domain 内机械重复；
- property/reference differential 替代无新增语义的组合枚举；
- code/test/doc/evidence 与最终设计同时完成替代和删除闭环。

## 2. 与现有复杂度治理的关系

当前正式治理使用复杂度软触发器，不以 LOC 判定失败。本专题不推翻该原则，而是新增
更明确的目标方向：

```text
Design Intent + Core Abstraction + Canonical Narrative
  -> required responsibility and evidence
  -> minimum complete code/test set
```

历史上为了责任分离而增加的代码可能是必要复杂度；历史上通过大 suite、重复 helper、
兼容 delegate 或分叉 fast path 形成的规模也不因此自动合理。所有判断必须重新核对
semantic role、Owner、lifecycle、invariant、Access Pattern、failure domain 和
replacement。

## 3. Production Code 审查

### 3.1 应当保留

- 唯一拥有 runtime fact、invariant、lifecycle 或 failure boundary 的实现；
- generated/static specialization 所需的直接机械路径；
- compiler-owned 四类 type/storage classification、String whitelist、
  `@SomaValue` flattening 与 arbitrary-object rejection；
- public/generated compatibility 或 migration 仍需要的边界；
- 不同 Access Pattern、Shape 或 resource/failure 语义对应的独立路径；
- 为消除 reflection、boxing、object graph、generic dispatch 或 hot-path固定税而存在
  的 evidence-backed specialization；
- Capability 的 cold-path binding 与唯一 canonical hot-path implementation；
- Eager Detached 默认和经过 TV9/Design 准入的 callback-scoped streaming；
- 仍由目标 Blueprint/Design 明确要求的 capability。

### 3.2 候选简化、合并或删除

- 不再服务 accepted abstraction 或 canonical narrative 的旧路径；
- 与 canonical path 语义、Owner、lifecycle、failure 和成本完全重合的平行实现；
- 没有 compatibility、ownership、resource 或 observation 价值的透明转发层；
- 已完成替代但仍驻留的 temporary adapter、bridge、fallback 或 test-only hook；
- 为历史实现布局而存在、正式 contract 不依赖的 helper/object graph；
- 没有目标 workload/evidence 支持的 generic mechanism 或 speculative extension；
- 被更简单的 generated/static binding 完整替代的 dynamic registry/interpreter。

### 3.3 不能独立作为删除理由

- 只有一个实现或 caller；
- 类、方法或文件较长；
- 调用层次较深；
- 与另一结构都使用 array/map/index；
- static scan 报告“未使用”，但 generated、reflection-free binding、external
  consumer、failure path 或 future-compatible seam 尚未核对；
- benchmark 中暂时没有收益，但该实现仍是 correctness/reference Owner。

## 4. Test 与 Evidence 审查

### 4.1 每项测试必须说明

- 对应哪个 Blueprint/Design capability 或明确 regression；
- 证明哪个 invariant、contract、failure domain、consumer 或 measurement 问题；
- 为什么不能由已有 unit/property/differential/external/application/benchmark 证据
  覆盖；
- 若删除，由哪个保留测试或新 oracle 完整替代；
- 是否冻结 public/generated contract，还是只耦合 private implementation。

### 4.2 应当保留

- compiler positive/negative fixture 的独立 diagnostic 语义；
- String payload/Key/Unique/Index/Group/Join 的 value-semantics、collision、
  mutation/clear/release 与 reference-retention representative evidence；
- arbitrary Java object field 拒绝、`@SomaValue` leaf flattening 和 owned child
  Collection-graph exclusion 的 compiler/runtime boundary evidence；
- Eager Detached 与 callback-scoped streaming 的 logical-result differential、
  early-stop、exception/cancel、guard/scratch cleanup 和 retention evidence；
- public/generated/schema golden 与普通 external consumer；
- runtime ownership/lifecycle/failure atomicity 的 representative invariant；
- operator/order/absence/multiplicity 的 property/reference differential；
- 证明不同 application boundary 的少量 canonical scenario；
- 有明确 workload、validator、environment 和 claim boundary 的性能 evidence；
- compatibility、packaging、安全或 release 的独立 failure domain。

### 4.3 候选简化、合并或删除

- 在同一 Owner、同一输入分类和同一 failure boundary 重复验证同一 invariant；
- 只冻结 private helper、内部 node、backing array 或调用次数且没有 footprint contract；
- 已删除 contract、旧 canonical token、temporary adapter 或 superseded behavior 的测试；
- 无新增语义的组合枚举，可由 parameterized/property/differential test 覆盖；
- 与 production test 重复、但没有独立 external consumer 或 measurement 价值的
  benchmark admission；
- 只证明“脚本打印 ok”“类存在”或无 validator artifact 的伪 evidence；
- 为 test-only bypass 或不同 correctness model 服务的 fixture。

测试合并不能把 compiler、runtime、external consumer、application 和 performance
这些独立 failure domain 压成一个通用 runner。减少测试规模不得降低 failure-path、
compatibility、determinism、resource、scope non-regression 或 release Gate。

## 5. 审查矩阵与裁决语言

每个 code/test artifact 使用同一矩阵：

| 字段 | 内容 |
|---|---|
| Artifact | package/class/test/script/lane |
| Kind | production、generated support、test、fixture、benchmark、script |
| Design Link | 服务的核心抽象和 narrative step |
| Owner | 事实、不变量或 evidence Owner |
| Lifecycle/Failure Domain | 创建、使用、失效、失败边界 |
| Access/Cost | hot/cold path、fixed/retained/scratch/build cost |
| Replacement | canonical replacement 与迁移状态 |
| Evidence | 保留、合并或删除所需证明 |
| Decision | 以下唯一裁决值 |

```text
CORE_REQUIRED
SUPPORTING_REQUIRED
SIMPLIFY
MERGE_CANDIDATE
REMOVE_AFTER_REPLACEMENT
NEEDS_EVIDENCE
OUT_OF_SCOPE
```

`MERGE_CANDIDATE` 和 `REMOVE_AFTER_REPLACEMENT` 不是删除授权。只有最终设计确认
canonical replacement，并且 references、contract、test migration 和 Gate 完整闭合
后，才能进入实施。

## 6. Replacement Closure

删除或合并前必须同时满足：

1. accepted Design 已明确原责任是否继续存在；
2. canonical replacement 的 Owner、API/internal path 和 lifecycle 已关闭；
3. 全仓引用、generated source、external consumer、script 和文档已核对；
4. 原 artifact 独有的 correctness/failure/evidence 已迁移或被明确否决；
5. compatibility、schema/hash、protocol、artifact identity 和 migration 已裁决；
6. 窄测试与全部适用 Gate 通过；
7. code/test/Implementation Map/Conformance/Report 不再引用旧路径；
8. scope non-regression 证明没有删除目标能力或独立 evidence domain。

没有 replacement closure 时，artifact 可以标记为 `NEEDS_EVIDENCE`，但不得因为
“看起来没用”直接删除。

## 7. 执行顺序

```text
freeze scale-control goal
  -> standalone technical validation
  -> evidence-driven final SOMA design
  -> code/test inventory against accepted abstractions and narrative
  -> Owner disposition
  -> authorized replacement/removal slices
  -> focused evidence after each slice
  -> post-core reference-application audit and necessary governance
  -> full scope non-regression
  -> formal closeout
```

技术验证 Lab 自身不进入 SOMA code/test inventory；它在治理完成后整体删除。SOMA
代码和测试只在最终设计完成并获得实施授权后裁剪。

## 8. 三个 Example 的后置审计

Production core、public/generated contract 与 canonical capability path 稳定后，再
审计三个 Example：

- industrial dynamic scheduler；
- grassing individual simulation；
- real-time dispatch rule engine。

审计先使用最终 Product Blueprint、Design、Capability/Result Delivery contract 和
application 自有 Blueprint/Design/evidence 做差分。当前正式 Conformance 已认为三者
一致且 evidenced，因此默认裁决不是“必须重写”，而是：

```text
RETAIN
CLARIFY
GOVERN_AFTER_CORE
```

只有发现新设计造成真实偏差时才修改；不得为了展示 Metadata、Capability 或 callback
streaming 而把自然的 direct Access/Transformation 路径改成 kitchen sink。任何治理
都必须保持三个应用独立、只消费 public artifacts、各自拥有领域事实和 evidence，并
重新通过 correctness、isolation、Fast/Scale/Soak/Full 适用 Gate。

## 9. 停止条件

- 最终设计未完成时，不建立删除清单；
- evidence 不能区分重复与独立 failure domain 时，保持 `NEEDS_EVIDENCE`；
- 删除会缩减 Blueprint、public/generated contract、correctness 或 Gate 时立即停止；
- 为减少文件数而制造 God Class、万能 runner、generic executor 或平行事实源时停止；
- replacement 依赖未来工作才能成立时，不实施当前删除；
- Example 与最终最佳实践一致时，不进行装饰性重构；
- G6 发布事实和外部支持矩阵不因代码规模治理改变。

本文件在最终 code/test replacement closure、全部适用 evidence、scope
non-regression 和正式 Owner 固化完成后，随上位 Temporary 一并删除。
