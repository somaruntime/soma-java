# SOMA Access Model 与 Pipeline 产品化 Stage 1 章程

类型：Temporary

状态：完成

Owner：SOMA Java Access Model / Candidate Scan 专题治理

事实范围：Stage 1 的意图、目标、分析顺序、授权边界、交付物与完成标准

非事实范围：正式 Blueprint/Design、当前 public/generated contract、已经获得的 Stage 2 实施授权

专题建立基线：`5217c27ae4a07a5ec8ec3b70ae92224aac5706d2`

最后审查日期：2026-07-22

## 1. 意图

Stage 1 不从“怎样压缩现有 `*Rows` 的五组数组”开始，也不把 Pipeline 当成 SOMA 全部访问能力。它先建立完整的 **SOMA Access Model**，再由访问语义推导 API、Pipeline IR、物理执行和性能优化。

这里的 Access Pattern 不是 FJSP、VRP 等场景 workload 分类，而是 SOMA 产品本身允许用户对 packed columnar table 执行的基本访问方式。场景只用于证明这些基本模式是否自然、完整和有真实需求。

## 2. 总目标

形成一份可直接驱动 Stage 2 的产品设计包，使 SOMA 的访问体系满足：

1. 每个重要 Access Pattern 都有明确语义、cardinality、sequence、validity 和成本边界；
2. 每个 Pattern 都能映射到唯一、自然的 canonical API，必要的 bridge 明确标识而不冒充第二套语义；
3. Point、Candidate、Column、Key、Bulk 和 Ownership 不被强行塞进一个泛型 Pipeline；
4. Candidate Pipeline 的合法组合、one-shot lifecycle、callback、failure、stats 和 mutation 语义可证明；
5. 性能优化从 candidate count、touched columns、lookup probe、scratch、snapshot 和 materialization 等真实成本推导；
6. compact plan、source specialization、terminal specialization 和 generated code size 有当前 evidence 支撑；
7. 核心命名体现 columnar access，而不是把 live storage、cursor、materialized object 都混称为 Row；
8. Stage 2 的变更面、迁移方式、identity 影响和验证 Gate 完整可执行。

## 3. 上游分析顺序

Stage 1 的强制顺序是：

```text
Access Pattern Catalog
  -> Access Pattern / API Coverage Matrix
  -> Composition Algebra and Legality
  -> Cost Model and Benchmark Matrix
  -> API Optimization Decisions
  -> Physical Execution and Performance Decisions
  -> Pipeline IR / Detailed Design
```

后一步必须引用前一步的事实或决策。不得先选定新 API 或 compact representation，再反向挑选 Access Pattern 为其背书。

## 4. 范围

### 4.1 纳入

- packed 全表、current Index、primary key、secondary unique、secondary exact group；
- owner-scoped child、Key traversal、typed single-column traversal、ColumnView；
- `IndexSnapshot` sparse gather；
- filter、skip、limit、dynamic sort；
- count/match、borrowed traversal、first/best-one；
- IndexSnapshot、单项和批量 materialization；
- point mutation、candidate update、swap-remove；
- Batch、addBatch、replaceAll、clear、replaceChildren；
- generated naming、lifecycle、stats、callback、compatibility identity；
- plan allocation、scratch、class/code size、component 与 FJSP evidence。

### 4.2 排除

- range index、maintained order、join、SQL planner、generic expression tree；
- Java Stream、parallel execution、Spliterator、`map/flatMap/collect`；
- application event queue、priority queue、heap 或业务 transaction；
- 新 annotation、Schema 语义和第三方依赖；
- G6、发布主体、签名、SCM 或 public release readiness；
- 未经重新准入的 `allMatch`、generic reduction、public top-k。

## 5. 授权边界

本 Stage 已获授权执行分析、Temporary 详细设计、benchmark/test evidence 增强和自审。它没有授权：

- 修改正式 Blueprint、Design、Implementation Map、Conformance 或 Report 的长期事实；
- 修改 production annotation、processor、runtime、generated public API 或 scenario code；
- 开始 Stage 2 实施或把 Temporary 决策表述为当前已支持能力；
- 修改 release/version/publishing 状态。

Benchmark harness 可以增加仅用于归因的 lane，但不得改变 production runtime，也不得把 dirty harness artifact 表述为 clean commit-bound release evidence。

## 6. 不得回退的不变量

- packed SoA、current Index 与 swap-remove 保持；物理遍历顺序不稳定；
- `@SomaKey` 是 primary stable identity，`@SomaUnique` 是 secondary unique access；
- exact structure eager/incremental 维护，不做 dirty read-time rebuild 或 scan fallback；
- stage 只处理上一 candidate set；dynamic sort 只排序当前候选；
- Index 是当前物理位置，`IndexSnapshot` 不是 stable identity 或 row snapshot；
- callback-scoped borrow 不逃逸；同 aggregate 同步、single-owner、non-reentrant；
- mutation failure atomicity、resource preflight、full equality 与 bounded diagnostics 不因优化回退；
- hot path 不引入 per-element object、boxing graph、reflection、metadata interpreter 或 Java Stream。

## 7. Evidence 纪律

每项设计结论必须标明来源类别：

| 类别 | 可支持的结论 |
|---|---|
| 正式 Design | 不变量、能力边界、长期语义输入 |
| generated API / source / `javap` | 当前实现与兼容面 |
| test/oracle | 当前可执行语义和失败边界 |
| component benchmark | 可归因的局部成本与 allocation shape |
| scenario benchmark | integrated non-regression、checksum 和 GC |
| scenario source inventory | API 自然度和 chain distribution，不代表总体市场需求 |

单机结果不外推为跨环境 SLA；一次 allocation 数字不直接等于 library-only allocation；未运行的设计不声明收益。

## 8. Stage 1 交付物

| 交付物 | 责任 |
|---|---|
| 本章程 | 意图、范围、顺序与完成定义 |
| [Access Model](access-model.md) | Pattern catalog、组合代数、合法性与成本语义 |
| [API 覆盖矩阵](access-api-coverage.md) | 当前 API 映射、重复、缺口与抽象问题 |
| [Stage 1 Evidence](stage-1-evidence.md) | chain、allocation、code size、oracle、FJSP 基线 |
| [Stage 1 决策](stage-1-decisions.md) | API、命名、lifecycle、stats、callback、identity 裁决 |
| [Pipeline IR](pipeline-ir.md) | 目标 semantic/bound/physical/representation |
| [Stage 2 实施包](stage-2-implementation-package.md) | 模块 slice、迁移、Gate、停止条件 |
| [Stage 1 收口审查](stage-1-review.md) | 追踪关系、范围非回退、未授权项与出口判断 |

## 9. 完成标准

Stage 1 完成必须同时满足：

- Access Pattern catalog 覆盖全部已知基本访问模式；
- 当前 API 对每个 Pattern 的覆盖、cardinality 和成本表达均有判定；
- 所有合法组合和非法跨域组合都有规则；
- OP/RP 决策及新增 Access 决策全部关闭为接受、拒绝或明确排除；
- compact plan 与 inline/overflow 设计由 chain/allocation evidence 支撑；
- Stage 2 每个实施 slice 都是最终设计的有效子集，不依赖临时 public alias；
- correctness、allocation、throughput、GC、retained scratch、code size 和 scenario Gate 完整；
- `./scripts/check-docs.sh`、相关 benchmark validator 与 `./scripts/check.sh` 通过；
- 明确说明 Stage 2 仍需用户授权，Temporary 不冒充正式 Design。

## 10. 目标防偏移

以下情况视为 Stage 1 失败，而不是“可后续优化”：

- 只优化现有数组分配，却未解决 Access Model 和 API cardinality；
- 只做 rename，却保留相同概念混乱；
- 为统一 family 引入 `Object` 化 generic runtime；
- 只以 FJSP 热链裁剪其他基本 Access Pattern；
- 用兼容 alias 长期保留两套 canonical API；
- 把尚未实施的目标能力写成当前事实；
- 因 benchmark 波动而放宽 correctness、failure 或 lifecycle 语义。
