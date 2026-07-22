# SOMA Access Model 与 Candidate Scan Stage 1 收口审查

类型：Temporary

状态：Stage 1 完成；无 Stage 2 实施授权

Owner：SOMA Java Access Model / Candidate Scan 专题治理

事实范围：Stage 1 交付物追踪、完整性、一致性、evidence 充分性、范围非回退与出口判断

非事实范围：Stage 2 已获授权、目标 API 已实现、正式 Design 已切换或性能收益已实现

基线：`5217c27ae4a07a5ec8ec3b70ae92224aac5706d2`

最后审查日期：2026-07-22

## 1. 审查结论

Stage 1 已按“Access Model 先于 Pipeline IR”的上游顺序完成产品分析、current API 映射、组合合法性、成本模型、benchmark evidence、API/命名/语义裁决、物理详细设计和 Stage 2 实施包。

设计审查未发现仍需 implementation-time 决定的产品语义、canonical 名称或 plan topology。本轮 validation Gate 已全部通过，Stage 1 可以关闭，但不会自动进入 Stage 2。

## 2. 目标追踪

| 强制步骤 | 产出 | 审查结果 |
|---|---|---|
| Access Pattern Catalog | [Access Model](access-model.md) `AP-01..27` | 完整 |
| API Coverage Matrix | [API 覆盖矩阵](access-api-coverage.md) | current signature、重复、缺口和 cardinality 均有 evidence |
| Composition Algebra | Candidate、Point、Column、Key、Bulk 五条代数 | 合法组合和跨域禁止规则完整 |
| Cost / Benchmark Matrix | Access Model 成本模型 + [Evidence](stage-1-evidence.md) | pattern、component、JFR、code-size、FJSP 已对应 |
| API Optimization Decisions | [Stage 1 决策](stage-1-decisions.md) 第 2–4 节 | 唯一候选目标，无平行 shortlist |
| Physical Decisions | Stage 1 决策第 5–8 节 | callback/stats、plan、storage、specialization、identity 已关闭 |
| Pipeline IR | [Pipeline IR](pipeline-ir.md) | semantic/bound/physical/representation 和 executor 路径完整 |
| Implementation package | [Stage 2 实施包](stage-2-implementation-package.md) | slice、migration、Gate、停止条件完整；明确未授权 |

不存在从当前数组实现反向定义 Access Model 的倒置依赖。

## 3. Access Model 完整性

`AP-01..27` 覆盖当前已知基本模式：

- source/direct：packed、current Index、primary key、secondary unique、secondary exact group、owned child；
- traversal/projection：Key、single Column、ColumnView、IndexSnapshot gather；
- candidate stage：filter、skip、limit、dynamic sort；
- candidate terminal：probe、borrow、scalar Index、snapshot、single/bulk materialize、update、remove；
- mutation/bulk：point mutation、addBatch、replaceAll、clear、replaceChildren。

范围没有按当前四场景调用频率裁剪。Range/order index、join、generic reduction、public top-k、Java Stream 和跨 Table transaction 均被明确排除，而不是遗漏。

## 4. 决策闭合

| 决策组 | 数量 | 状态 | 主要结论 |
|---|---:|---|---|
| `AM-DEC-01..06` | 6 | 全部关闭 | Access Model umbrella、Table packed source、Unique point、scalar Index、Traversal、family 边界 |
| `OP-DEC-01..06` | 6 | 全部关闭 | Scan/Cursor/Traversal/IndexSnapshot 词汇、clean cutover、one-shot lifecycle |
| `RP-DEC-01..08` | 8 | 全部关闭 | logical stats、callback、typed source、inline 3、arg-min、拒绝扩展、retained accounting |

“关闭”表示成为本 Temporary 的唯一 Stage 2 候选输入，不表示已经成为正式 Design。若 Stage 2 evidence 触发实施包停止条件，必须返回 Owner，不得由实现者静默改写这些决定。

## 5. 跨文档一致性

本轮逐项交叉检查得到：

- `Access Model` 是完整产品 umbrella；`Candidate Scan` 只负责 CandidateAccess；
- Table 自身是 Packed source，目标不保留 `rows()`，也不新增 `scan()`；
- `@SomaIndex` 使用 `scanByX`，`@SomaUnique` 使用 point family，并只保留显式 `scanByX` bridge；
- current Index、`IndexSnapshot`、Key identity 与 materialized object 没有混成同一种返回 shape；
- `IndexSnapshot` 仍是 detached Index copy，mutation/lifecycle 后失效，不升级为 stable row snapshot；
- Key/Column 是 terminal-only one-shot Traversal，不继承 Candidate stages；`ColumnView` 保持 scoped random read；
- exact source 在调用时验证/展开 typed selector leaf，在 terminal 绑定 current group facts；
- compact plan 为 per-operation owner + generation handle、inline 3 + tested overflow，不使用 Table-global/ThreadLocal state；
- clean API cutover、runtime protocol v4、Schema/plan identity 不变的责任边界一致；
- 产品示例、术语、操作目录、IR 和实施 migration card 使用同一目标词汇。

未发现 `Row Pipeline`、`Operation Pipeline` 或现有 generated 名称仍被误写为目标 canonical language；它们只出现在 current-state、历史或 migration 语境。

## 6. Evidence 充分性与限制

Evidence 足以决定：

- inline capacity 3 且保留 4/5/16 overflow oracle；
- 删除 anonymous exact Source 与 per-stage full handle；
- 使用单组 compact stage storage；
- 增加 scalar Index terminal 和 stable arg-min specialization；
- 预绑定 Column diagnostic literals；
- 同时设置 allocation 与 generated code-size Gate。

Evidence 不支持并且设计没有宣称：

- 零分配的所有 Scan；
- 跨机器 latency SLA；
- snapshot/materialization/application callback allocation 可以被 plan 优化消除；
- top-k、range、generic reduction 或新 annotation；
- 当前代码已经具备目标 API 或 runtime。

Component v2 使用未提交 benchmark harness，因此只作为 Stage 1 归因证据；clean v1 artifact 与 harness/source checksum 保留为交叉基线。Stage 2 必须重新生成 commit-bound A/B evidence。

## 7. 范围非回退

本 Stage 实际修改面限定为：

- `docs/temp/row-pipeline-execution-and-lazy-plan-governance/` 的 Temporary 设计包；
- `docs/README.md` 对 active Temporary 的事实性登记；
- `soma-benchmarks` component runner/validator 与对应 script 的诊断 lane 扩展。

没有修改 annotation、Schema、processor、runtime-core、testkit contract、examples/scenario、正式 Blueprint/Design、Implementation Map、Conformance、Report 或 release 配置。Benchmark 修改不改变 production runtime 或 public/generated API。

保持的不变量包括 packed SoA、swap-remove、unstable physical order、primary stable Key、secondary exact incremental maintenance、full equality、caller-responsibility Index、owned child、failure atomicity、resource preflight 与 Java 8。

## 8. 风险转交而非设计尾项

下列事项是 Stage 2 必须通过 Gate 证明的实现风险，不是留给实现者自由选择的设计问题：

1. clean rename 的迁移规模与 generated name collision；
2. protocol v4 fail-closed 与 Schema/plan identity byte parity；
3. compact plan 在 callback failure、allocation failure和consumed cleanup 下的 lifecycle；
4. allocation 降低是否以 generated source/class 膨胀为代价；
5. stable arg-min、logical stats、candidate mutation 与 exact relocation 的等语义；
6. FJSP hot path 收益和四场景 checksum non-regression。

这些风险均已在实施包中绑定 oracle、performance envelope 或停止条件；没有以 future roadmap 形式遗留。

## 9. Validation 记录

| Gate | 本轮结果 |
|---|---|
| `./scripts/check-docs.sh` | 通过：`scope-check: ok`、`doc-check: ok` |
| `./scripts/check-post-cutover-components.sh` | 通过：12 allocation + 24 memory records，artifact validator `ok` |
| `git diff --check` | 通过 |
| `./scripts/check.sh` | 通过：clean Java 8 reactor、runtime/processor/testkit、external consumer、四场景、FJSP、benchmark 与 `project-check: ok` |

执行后必须记录实际结果；失败不能通过缩小 Stage 1 目标或删除 evidence lane 绕过。

## 10. 出口判断

设计、evidence 与第 9 节 Gate 已满足 Stage 1 完成标准。本 Stage 的唯一下一步问题是：用户是否明确授权执行 [Stage 2 实施包](stage-2-implementation-package.md)。

在获得授权前，目标 API、命名、protocol v4 和 compact executor 都必须保持 Temporary，不得被表述为当前 SOMA 能力。
