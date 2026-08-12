# SOMA Vectorized Physical Pipeline扩展临时设计Baseline Freeze与Readiness审查

类型：Active Bounded Temporary / Final Design Review / Baseline Freeze / Implementation Readiness

状态：`PASS / TEMPORARY_DESIGN_BASELINE_FROZEN / IMPLEMENTATION_AUTHORIZED / VP1_ACTIVE`

Owner：本专题临时设计最终审核、冻结集合、readiness、授权边界和进入实施前的当前状态

日期：2026-08-12

## 1. 最终结论

本次最终审核通过。当前专题的设计工作已经完成，临时设计可以作为下一阶段implementation的冻结输入。

结论严格区分：

```text
Temporary Design completeness   PASS
Temporary Design Baseline       FROZEN
Implementation readiness        READY
Implementation authorization    NOT_GRANTED
Formal Design promotion         NOT_PERFORMED
Production implementation       NOT_STARTED
Qualification/release           NOT_PERFORMED / NOT_AUTHORIZED
```

Product Owner在2026-08-12明确要求完成“临时设计的固化、以指导下一阶段实施”，因此本记录将该请求解释为
Temporary Baseline Freeze授权。请求没有授权production source、正式Design晋升、commit/push、publication
或release；这些状态不能从Freeze自动推断。

随后Product Owner于2026-08-12另行明确授权Codex按冻结VP1-VP3自主完成全部实施。本授权允许本专题范围内
production code、tests、benchmark、Conformance和正式Owner promotion；不允许扩张冻结capability、第三
artifact、新dependency、GitHub Release/Package、签名或正式release声明。

## 2. 审核输入与方法

审核输入：

- 正式[Blueprint](../../blueprint/README.md)、[Design路由](../../design/README.md)与
  [Conformance边界](../../conformance/README.md)；
- Planning、Execution、Architecture、Storage、Core五个直接相关正式Owner；
- Canonical IR/Execution S1-S6计划、正式晋升与最终资格；
- [Vectorized Physical Pipeline第一阶段正式晋升](../../conformance/v1-vectorized-physical-pipeline-phase1-promotion.md)；
- [current-state盘点](current-state-audit.md)、[冻结临时设计](design.md)、
  [bounded feasibility](feasibility-validation.md)与[pre-freeze self-review](readiness-review.md)；
- `e2ce237736cd6042fbce2d90c2522e1a280e7d7c` production source中planner/refinement、finite kernel、
  encoded representation与shared scheduler的实际代码形状。

审核方向：产品目标、Owner唯一性、主叙事、capability边界、representation/resource/parallel合同、
过度设计、replacement closure、slice可执行性、evidence与授权边界。

本次没有修改production source或正式Design，也没有因文档固化重复运行未改变输入的Maven/benchmark。
R2.3已有4项targeted test和1M AUTO/OFF bounded evidence仍作为方向输入，不被外推为qualification。

## 3. 最终发现与修正

| Finding | 风险 | 最终修正 | 状态 |
|---|---|---|---|
| Candidate与Frozen Design可能并存 | 同一专题出现双Design Owner | `candidate-design.md`唯一迁移为`design.md`，不保留兼容副本 | CLOSED |
| 原S2 primitive matrix是开放式路线 | 实施把能力矩阵当产品承诺，形成cross-product | 当前implementation只准入encoded integral与ordered `long[]`；其他type/terminal全部DEFER | CLOSED |
| 原S4 complex operator即使默认none仍占据slice | 路线图驱动GroupBy/Join过早扩张 | 从实施计划删除complex operator slice；未来必须独立governance | CLOSED |
| boolean cell为`ADMIT_CONDITIONAL` | 冻结基线仍含未闭合null/presence裁决 | 明确改为`DEFER`，不要求packed-word access | CLOSED |
| multi-leaf RLE predicate/project没有执行边界 | 若直接准入会被迫建立通用run zipper和新scratch模型 | encoded-plain支持multi-leaf direct；RLE只准入single distinct leaf，其他shape显式scalar/fallback | CLOSED |
| terminal refinement既是当前事实又可能成为长期Owner | eligibility/resource重复 | VP1要求final plan一次拥有decision并完成old refinement replacement closure | CLOSED |
| pre-freeze self-review仍显示`NOT_FROZEN` | Current status可能被旧review覆盖 | 保留其provenance，但current Freeze/Readiness唯一由本记录拥有 | CLOSED |

未发现需要改变Blueprint、public/generated语义、Canonical semantics、numeric/failure visibility、Java 8、
two-artifact topology或dependency的阻断项。

## 4. 冻结设计完整性

| 设计问题 | 冻结答案 | 结果 |
|---|---|---|
| 为什么治理 | AUTO encoded typed scan仍显著慢于OFF；first-stage mechanism证明方向成立 | COMPLETE |
| Semantic truth | Canonical/Bound唯一；Reference保持独立oracle | COMPLETE |
| Physical decision Owner | Planning一次形成kernel、handler、partition与ResourceEstimate | COMPLETE |
| Execution Owner | lease后Frame只消费decision；shared ordinal-work负责parallel lifecycle | COMPLETE |
| Representation | PLAIN typed、encoded integral run、overlay scalar/current fallback；不decode-all | COMPLETE |
| Finite pipeline | Table scan + pure typed integral predicate + optional primitive projection + admitted terminal | COMPLETE |
| Frozen cells | VP1 integral count/sum；VP2 ordered `long[]` | COMPLETE |
| Breaker/fallback | callback、mapped、stateful、Index/Group/Relation等稳定走existing optimized path | COMPLETE |
| Numeric/order/null | signed-128、canonical ordinal、non-null integral；floating/boolean不准入 | COMPLETE |
| Resource | complete peak在work前admit；aggregate O(C)，materialization result + O(C/P) | COMPLETE |
| Parallel | existing Chunk为morsel；不建sub-Chunk或第二scheduler | COMPLETE |
| Diagnostics | only unstable category/reason；不冻结private string/class | COMPLETE |
| Failure/stop | 沿用正式failure/quiescence；所有扩张条件有Stop Rule | COMPLETE |
| Implementation route | VP1 -> VP2 -> VP3，one active slice、exit evidence、恢复点明确 | COMPLETE |

没有剩余Product Owner语义裁决。未来是否授权整个VP1-VP3 implementation是生命周期授权问题，不是Design
缺口。

## 5. Owner与Traceability

| 冻结内容 | 长期唯一Owner | 实施期间临时Owner |
|---|---|---|
| final Physical decision、handler choice、ResourceEstimate | Planning | `design.md` |
| lease、Frame、scheduler、quiescence | Execution | `design.md` |
| finite kernel与runtime component seam | Architecture | `design.md` |
| integral run/typed representation access | Storage | `design.md` |
| A17/A19/A20/A22/A26 narrative与INV-12/15/16 routing | Core | `design.md` |
| VP1-VP3顺序、Gate、性能和恢复点 | future Engineering/Conformance | `implementation-plan.md` |

Temporary只拥有当前topic增量，不覆盖正式Owner。VP3必须将通过资格的稳定增量晋升回正式Design和
Conformance，然后删除本Temporary；若implementation失败，Temporary不得成为“已经正式设计”的历史借口。

## 6. 过度设计与坏味道审查

本冻结基线明确不建立：general DAG、public/internal Batch/Vector SPI、per-cell class hierarchy、runtime
codegen、Java Vector API、decode cache、second storage truth、sub-Chunk work stealing、second scheduler、
prepared/cached plan、GroupBy/Join specialization或SOMA Engine placeholder。

保留的复杂性只有正式合同必需的部分：

- one final decision防止eligibility/resource重复；
- representation-owned access防止执行层复制storage truth；
- signed-128与canonical merge保护现有numeric semantics；
- conservative admission、failure/quiescence保护生产可预测性；
- two-pass只用于data-only typed predicate，防止callback重复和O(N) locator staging。

Verdict：`NO_BLOCKING_OVERDESIGN`。

## 7. Readiness与实施授权边界

Readiness为`READY`，因为：

- 目标、范围、非目标与consumer明确；
- 现有production seam足以承载，不需要先做新prototype；
- VP1、VP2各有精确能力、资源公式、negative boundary、性能门槛和删除条件；
- VP3拥有formal promotion与Temporary replacement closure；
- 风险均有最早防线、evidence或Stop Rule；
- 没有未关闭产品语义或重大架构裁决。

Authorization已由Product Owner另行授予。当前按冻结[Implementation Plan](implementation-plan.md)只激活
VP1；VP1 exit closure前不得进入VP2。

## 8. 冻结集合与Fingerprint

冻结集合：

| Temporary Owner | SHA-256 |
|---|---|
| `project/temp/soma-vectorized-physical-pipeline-expansion-governance/design.md` | `3fb80614b75dd511a1c822c7ea685aec4740a41cb8c4f7e8bc7a8eda148e3859` |
| `project/temp/soma-vectorized-physical-pipeline-expansion-governance/implementation-plan.md` | `758d323f2cf0fb74f3e84ea0adef5357cd3a3eb59ddf1bb861b3fa7137f79aca` |

集合fingerprint：

```text
vector-pipeline-expansion-vp1:cc442377cdc81136184cc993d68c7743f308b6dcb3b7286c09dff6b5575a60c4
```

集合fingerprint按上表顺序对两个文件运行`shasum -a 256`，再对这两行标准输出运行一次
`shasum -a 256`得到。

该fingerprint冻结规范性Temporary input，不冻结private class name、method layout、codec threshold、Chunk
width、cost coefficient或diagnostic字符串。若实施需要改变冻结capability、语义、Owner或Stop Rule，必须
回到Product Owner；L4 private mechanism在不改变这些合同且证据闭合时可以审慎调整。

## 9. Final disposition

```text
R2 design work                 COMPLETED
Temporary design baseline      FROZEN
Implementation plan            FROZEN
Implementation readiness       READY
Active implementation slice    VP1
Implementation authorization   GRANTED
Formal promotion               DEFERRED_TO_VP3
Release/publication             NOT_AUTHORIZED
```
