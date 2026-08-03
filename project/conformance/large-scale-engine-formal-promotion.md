# SOMA 大规模编译式 Table 引擎正式晋升记录

类型：Conformance / Promotion Record

状态：`PASS`

正式事实源：是（本次晋升来源、replacement closure与claim boundary）

Owner：2026-08-03 大规模编译式Table引擎候选到正式Blueprint/Design/Engineering/Conformance
的晋升证据

审查日期：2026-08-03

后续状态：本记录继续拥有该候选的promotion provenance；current readiness、后续P1 closure与核心
抽象promotion由[最终全局一致性审核](v1-final-pre-implementation-global-consistency-review.md)
拥有。

## 1. 晋升范围

Product Owner确认SOMA当前V1 North Star为“一亿行以上、编译式、支持关系计算的单进程Table
引擎”，并批准：

- 最终候选设计；
- 审核补充中的自动内存预算、reference interpreter、三个用户旅程、Predicate IR裁决与
  Java 8 feasibility evidence；
- “候选设计收口、正式晋升与实施准入审查”整体治理任务。

本记录只证明文档authority与implementation-readiness input完成，不证明production capability。

## 2. Frozen candidate source

晋升前Temporary source fingerprint：

| Source | SHA-256 |
|---|---|
| `project/temp/soma-large-scale-compiled-table-engine/README.md` | `64e17d7acfff7251759725ddfb2d1aca91755e4ad317878eb66fa67e99f55f7a` |
| `project/temp/soma-large-scale-compiled-table-engine/final-design.md` | `6fa65817500a9118291bbbb779dbad3a058461798935669d23dcf8278e20e216` |
| `project/temp/soma-large-scale-compiled-table-engine/review-supplement.md` | `6981ed3d879ae7c81d8d1b83a8429e4c1ceb6396d97a8eda3cf71e7a7d3cc3d4` |

这些文件在晋升时是尚未进入Git历史的working-tree Temporary。Digest标识本次实际审查的精确
输入，但不能单独重建原文。原文不作为parallel Design归档；需要长期保留的合同、evidence与
limitation已经进入下表所列正式Owner。

候选在freeze前完成两项replacement correction：

1. 删除`Runtime.maxMemory() * 50%`固定合同，采用versioned automatic effective-budget policy；
2. 关闭历史README中的Right Join convenience“待评估”，正式V1不提供Right Join。

## 3. Promotion matrix

| Candidate concern | Formal Owner |
|---|---|
| 产品定义、North Star、阶段目标、Java Stream差异、三个场景、成功标准 | [Blueprint](../blueprint/README.md) |
| Composition、annotation、Field role/type、generated object、full regeneration | [Schema Design](../design/schema-and-generation.md) |
| Group/Table、long Chunk、leaf/null、Key/Index、order/capacity、compression/backend seam | [Storage Design](../design/data-model-and-storage.md) |
| Direct source、Selection/View、operation set、Group/Join、materialization、metadata/explain | [Logical API](../design/logical-api.md) |
| Exact annotation/shared/generated Java 8 surface与absence | [Generated Signature](../design/generated-api-signatures.md) |
| Predicate IR、rewrite、Index substitution、Join/Group planner、statistics、reference interpreter | [Planning and Optimization](../design/planning-and-optimization.md) |
| Group guard、binding、mutation、parallel、configuration、budget、quiescence | [Execution Design](../design/execution-and-concurrency.md) |
| Result、failure code/mapping/precedence/sanitization/failed-state | [Results and Failures](../design/results-and-failures.md) |
| Two artifacts、build、runtime components、storage/Index/compression/scheduler mechanism | [Implementation Architecture](../design/implementation-architecture.md) |
| I0-I8、exit/stop/Loader trigger | [Implementation Plan](../engineering/v1-implementation-plan.md) |
| G1-G10 production evidence | [Implementation Gates](v1-implementation-gates.md) |

## 4. New Design Owner admission

新增`planning-and-optimization.md`不是新增产品能力或module。它独立拥有IR/rewrite/reference
oracle：

- consumer：runtime/compiler implementer与Conformance；
- lifecycle：terminal logical plan到physical plan；
- failure：对外仍映射到Execution/Results Owner；
- necessity：避免Logical API或Implementation Architecture成为全能Owner；
- evidence：Java 8 expression/callback shape、rewrite/Join-kind/Index/order/null differential、
  `_explain()`。

Design index已记录完整surface admission。

## 5. Promoted product decisions

晋升后正式关闭：

- direct source、sequential default、explicit `parallel()`；
- View/Editor borrowed scope与Selection-only mutation；
- long-domain chunked on-heap first；
- optional Key、multiple exact non-unique Index；
- typed GroupBy、binary Equality/Cross Join、default Inner、no Right/multi-way/non-equality；
- null-never-Join、outer MISSING truth、duplicate/order/cardinality；
- Predicate IR + safe pushdown/residual/Index substitution、no `filterLeft/filterRight`；
- reference interpreter correctness oracle；
- application-owned `ForkJoinPool`、Group external serialization；
- automatic/explicit global memory budget、AUTO/OFF compression、no spill；
- structured failure、zero publication、metadata/explain boundary；
- two artifact + full regeneration；
- off-heap/mmap future seam without V1 surface；
- no Loader/Batch until profile trigger。

## 6. Feasibility evidence admitted

Accepted bounded evidence：

- Amazon Corretto 8 `1.8.0_502` generated type-shape fixtures；
- direct source、View/Selection/ReadStream、Join builder与ForkJoinPool narrowing；
- Predicate IR vs lambda callback overload no ambiguity；
- wrong-owner/Semi-right/arbitrary Executor negative；
- 100 Table、24 scalar Field/Table、99 Join overload/Table generated-surface stress：Java 8 compile
  completed without classfile/constant-pool hard boundary；
- scheduler paper feasibility与three complete paper journeys。

这些evidence只证明selected Java 8/design shape可表达；不证明production processor、storage、
optimizer、scheduler、compression、performance或release。

## 7. Intentional implementation-time choices

以下不是产品设计空白，允许由profile/evidence选择：

- exact Chunk rows/bytes；
- hash mixing/load/shard/growth；
- codec sampling/threshold/overlay density；
- Join/group physical cost coefficient；
- small/large mutation threshold；
- task multiplier/scratch block（floating canonical tree除外）；
- internal class/package/node/data structure。

一旦影响public API、result、order、null/missing、failure、budget visibility或backend seam，必须
回到Temporary/Product Owner，不能视为internal choice。

Metadata exact carrier member topology是唯一明确的implementation-evidence admission项；稳定
类别已固定，exact member必须在I7经Java 8 consumer/API diff后准入。

## 8. Replacement closure

- Temporary全部长期事实已进入唯一Owner；
- accepted feasibility与限制进入本Conformance record；
- Loader trigger进入Engineering/G9；
- 本次promotion scope内没有当时已知的unresolved P0/P1；后续更精细审核的finding由最终全局
  一致性审核关闭；
- Temporary不再拥有active fact，可从working tree删除；
- 本record保存source identity、promotion mapping与replacement evidence；不声称Git history可
  恢复未tracked的Temporary原文；
- 不建立archive docs目录或parallel Design。

## 9. Claim boundary

本次晋升不表示：

- API可被application使用；
- production module/artifact/build存在；
- million/100M performance成立；
- Java 8 support matrix完成；
- security/package/release qualification通过；
- implementation、commit、push或release已授权。

这些事实只能由G1-G10 production evidence建立。
