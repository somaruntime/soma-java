# SOMA Java V1 Implementation Readiness Review

类型：Conformance Review

状态：`HISTORICAL_PASS / SUPERSEDED`

历史结论：`READY_FOR_IMPLEMENTATION`

Implementation authorization：`NOT_GRANTED`

正式事实源：是（只拥有2026-08-03早期baseline的historical readiness evidence）

Owner：SOMA Java V1 pre-implementation completeness、traceability、repository readiness 与
implementation admission

审查日期：2026-08-03

当前替代Owner：[实施前最终全局一致性审核](v1-final-pre-implementation-global-consistency-review.md)。

本审查完成后，Product Owner增加了核心抽象、叙事与不变量证明链作为implementation前置review。
该专题已经[正式晋升](../design/core-abstractions-and-narratives.md)，targeted readiness delta也已由
最终审核完成。因此本文不再拥有current readiness，保留用于说明早期baseline与变化来源。

## 1. 审查问题

本审查回答：正式 Blueprint、Design、Implementation Plan、Conformance Gates 与 repository
surface 是否已经形成一套完整、自洽、可执行的实施输入，implementation 是否仍会被迫临场
决定普通用户可观察的产品语义。

本审查不回答也不暗示：production code 是否存在、性能是否达标、artifact 是否可用、是否
可以 commit/push、是否可以发布。

## 2. 独立结论

当时结论为 `READY_FOR_IMPLEMENTATION`，同时保持 implementation authorization 为
`NOT_GRANTED`。

成立理由：

1. BP-1 至 BP-15 均有唯一 Design Owner、实施 slice 与最低 evidence Gate；
2. schema、generated exact Java 8 surface、long-domain chunked storage、typed IR、reference
   interpreter、mutation、Group/Join、parallel、resource、compression、failure 与 artifact
   boundary 已形成相互闭合的规范性合同；
3. I0-I8 按依赖关系从真实 build boundary 推进到 product qualification，每个 slice 都有
   exit、stop 与 change protocol；
4. G1-G10 覆盖 compile、consumer、negative、runtime、differential、concurrency、resource、
   scenario、performance、security、package 与 release evidence；
5. 当时的大规模引擎候选 Temporary 的长期事实和 bounded evidence 已有唯一正式去向，
   不再存在与该正式 baseline 并行的 Design；
6. 尚未固定的内容均被限制为 evidence-driven internal choice 或显式的后续 admission，不能
   静默改变 public capability、result、order、null/missing、failure、lifecycle 或 resource
   visibility。

精确含义是：Product Owner 若随后单独授权 implementation，可以从
[I0](../engineering/v1-implementation-plan.md)开始；I0 exit 未通过前不得进入 I1。它不是
“可以一次性把整个系统写完”，也不是 production/release readiness。

## 3. 审查方法

本次不是沿用 2026-08-01 的旧结论，而是针对提升后的 North Star 重新执行：

1. 冻结并分类 Candidate、final Design 与 review supplement；
2. 正向检查 Blueprint requirement 是否有 Design、slice、Gate 承接；
3. 反向检查每个 Design surface、slice 与 Gate 是否能追溯到 Blueprint；
4. 检查 exact generated Java shape、capability absence 与 Java 8 bounded feasibility；
5. 检查 state、currentness、order、null/missing、numeric、failure、resource 与并行合同是否跨
   Owner 一致；
6. 检查一亿行愿景、百万行资格目标与尚未测量的 performance claim 是否被正确分离；
7. 检查 Temporary replacement、entry route、historical record 与当前 claim boundary；
8. 执行 Markdown link、stale-state、repository inventory、traceability 与 whitespace 验证。

## 4. Product readiness

### 4.1 产品叙事已经闭合

SOMA 是嵌入 Java application、运行在单 JVM 进程内、面向 schema-known mutable Tables 的
编译式列式计算引擎。Application 通过自然 Java object、generated typed API 与 Stream-like
operation 表达 point、scan、aggregate、GroupBy 与 binary relation；processor 形成 exact
surface，runtime 经 typed IR、语义等价优化和 resource admission 在 chunked storage、Key、
Index 与压缩表示上执行。

它从 Java Stream 学习 source/intermediate/terminal、lazy、one-shot、熟悉命名、sequential
default 与 explicit parallel，但额外拥有 Table identity、Key/Index、mutable authoritative
state、relation planning、atomic publication 与 structured failure。它不是 Java Stream
replacement、数据库、ORM、DataFrame、持久化系统或跨 Table transaction owner。

### 4.2 North Star 没有被误写成现有能力

- 一亿行以上是 long-domain、chunked、IR-driven 架构不得封死的 North Star；
- 第一阶段以百万行数据上的高效、低分配、资源受控操作建立 qualification；
- 16 core/32 GB 只是预定 qualification envelope，不是 minimum 或 maximum；
- exact throughput、memory、scale threshold 必须在 implementation/profile 后批准并进入 G9；
- 当前不存在 production performance、compatibility、artifact 或 release claim。

### 4.3 Scope 足够明确

V1 包含 typed GroupBy、same-Group binary Equality/Cross Join、Predicate IR、reference
interpreter、long-domain on-heap Chunk、transparent AUTO/OFF compression 与 bounded
ForkJoin parallel。

V1 排除 ChildTable、cross-Table transaction、generic multi-way/non-equality Join、Loader/
Batch、off-heap/mmap implementation、implicit spill、prepared query、window、approximate
aggregate、public physical Column/backend SPI 与 predecessor compatibility。未来 seam 不形成
placeholder API。

## 5. Design readiness matrix

| Design Owner | 独立责任 | 审查结果 |
|---|---|---|
| [Schema 与编译生成](../design/schema-and-generation.md) | composition、annotation、Field role/type、generated identity/object、diagnostic、full regeneration | CLOSED |
| [数据模型与存储](../design/data-model-and-storage.md) | Group/Table identity、long-domain StateRoot/Chunk、leaf/null、Key/Index、order、compression/backend seam | CLOSED |
| [逻辑层 API](../design/logical-api.md) | direct source、point/Selection、View、query/aggregate/Group/Join、metadata/explain surface | CLOSED |
| [Generated API Signature](../design/generated-api-signatures.md) | exact Java 8 annotation/shared/generated grammar、functional shape 与 capability absence | CLOSED |
| [规划与优化](../design/planning-and-optimization.md) | typed Logical/Predicate IR、rewrite、Index substitution、Join/Group planning、reference oracle | CLOSED |
| [执行、并发与并行](../design/execution-and-concurrency.md) | binding、Group guard、mutation publish、ForkJoin scheduling、resource admission、quiescence | CLOSED |
| [Result 与 Structured Failure](../design/results-and-failures.md) | result、failure carrier/code、mapping、precedence、sanitization、failed-state guarantee | CLOSED |
| [Implementation Architecture](../design/implementation-architecture.md) | two artifacts、build/runtime components、storage/Index/compression/scheduler baseline mechanism | CLOSED |

这里的 `CLOSED` 表示 implementation 不需要自行裁决长期产品合同，不表示任何 production
Gate 已经 PASS。

新增 Planning Owner 通过 surface admission：它拥有从 typed logical plan 到合法 physical
choice 的独立 lifecycle，避免 Logical API 与 Implementation Architecture 形成全能 Owner；
它不是 public planner SPI、新 module 或第二套 query language。

## 6. Blueprint → Design → Slice → Gate traceability

| Blueprint | Primary Design | First/major slices | Evidence Gates |
|---|---|---|---|
| BP-1 自然 typed surface、不泄漏 physical runtime | Schema、Logical、Signature | I0-I3、I7 | G1、G2、G4 |
| BP-2 compile-time schema/capability absence | Schema、Signature | I0-I2 | G1、G2 |
| BP-3 Group/Table identity 与隔离 | Schema、Storage、Execution | I1、I2、I4 | G2、G3、G5 |
| BP-4 long-domain chunked authoritative state | Storage、Architecture | I1、I2 | G3、G9 |
| BP-5 direct source、lazy one-shot、terminal binding | Logical、Planning、Execution | I1、I3 | G4、G5 |
| BP-6 Table-local all-or-nothing mutation | Logical、Execution、Failure、Architecture | I1、I4 | G5 |
| BP-7 aggregate、GroupBy、binary relation | Storage、Logical、Planning | I5 | G6 |
| BP-8 typed IR、等价 optimizer、reference oracle | Planning、Architecture | I3、I5、I6 | G4、G6、G7 |
| BP-9 sequential default、bounded equivalent parallel | Planning、Execution、Architecture | I6 | G7 |
| BP-10 managed memory、AUTO compression、no spill | Storage、Execution、Architecture | I4、I7、I8 | G5、G8、G9 |
| BP-11 normal outcome 与 fail-closed separation | Failure、Execution | I1、I4、I6 | G5、G7 |
| BP-12 primitive specialization、O(1)/O(P) View | Storage、Logical、Signature、Architecture | I1-I3、I6、I8 | G2-G4、G7、G9 |
| BP-13 application-owned external boundary | Blueprint、Storage、Execution | I2、I4、I5、I8 | G3、G5、G6、G9 |
| BP-14 two artifact、full regeneration、no reflection fallback | Schema、Signature、Architecture | I0、I2、I7 | G1、G2、G10 |
| BP-15 evidence-bounded product/release claim | All + Conformance | I0-I8 | G1-G10 |

反向审查也成立：I0-I8 与 G1-G10 都能追溯到至少一个 BP requirement，没有仅因“以后可能
有用”而创建的 implementation slice、module、public SPI 或 Gate。

## 7. Implementation plan readiness

计划采用真实纵向依赖，而不是 flat/boxed/reflection MVP：

```text
I0 build/full regeneration
    -> I1 primitive chunked Table
        -> I2 schema/type/Key/Index breadth
            -> I3 query IR/reference interpreter
                -> I4 mutation/failure/resource
                    -> I5 Group/Join
                        -> I6 bounded parallel
                            -> I7 compression/diagnostic closure
                                -> I8 scenario/performance/security/package qualification
```

关键控制：

- 每个 slice 必须交付 positive、negative、failed-state 与 Conformance evidence；
- reference interpreter 先成为 correctness oracle，再准入 optimizer/parallel；
- production 第一条 storage path 已是 long-domain chunked design；
- public deviation、reference differential、zero-publication、resource bound 或 full-regeneration
  失败都会触发 stop rule；
- stop rule 建立 bounded Temporary，不保留 hidden compatibility 或继续堆叠补丁。

## 8. Readiness findings and closure

| ID | 审查发现 | 关闭方式 | Status |
|---|---|---|---|
| RR-1 | Candidate 固定 `Runtime.maxMemory() * 50%` 会把机器策略误当兼容合同 | 改为 freeze 时的 versioned conservative automatic policy；metadata 暴露 effective value，允许显式配置 | CLOSED |
| RR-2 | Typed IR/rewrite/reference interpreter 没有独立 Design Owner | 新增 Planning and Optimization Owner 并完成 surface admission | CLOSED |
| RR-3 | 早期 exact shape 曾让 Field identity 看起来也是 predicate | `SomaField<R,V>`只作为 Field identity/source marker；比较方法生成 expression | CLOSED |
| RR-4 | 旧 readiness 只覆盖 BP-1…BP-10、I0…I7、G1…G8 | 基于当前 BP-1…BP-15、I0…I8、G1…G10 完整重审 | CLOSED |
| RR-5 | Candidate 与旧入口仍形成 parallel current narrative | 晋升矩阵、source fingerprint 与 replacement record 固定后退役 Temporary，入口只路由正式 Owner | CLOSED |
| RR-6 | Metadata exact member topology 尚无 implementation/consumer evidence | 稳定信息类别与安全边界已固定；exact member 仅允许 I7 经 API diff/Java 8 consumer admission | CONTROLLED |
| RR-7 | Chunk geometry、hash/codec cost 与 performance threshold 无 profile | 保留为 internal/evidence-driven choice；G3/G8/G9 负责验证，不允许改变 public semantics | CONTROLLED |
| RR-8 | Repeated add 可能使百万行初始化成为瓶颈 | I4/G9 profile trigger；只有证据触发才建立 Loader Temporary，不预建 Batch API | CONTROLLED |

`CONTROLLED` 项不是 I0 blocker：其可选择空间与 admission evidence 已固定，且有最早验证
slice、失败 stop rule 和禁止越界的 Owner。若 evidence 失败，它会成为真实 blocker，不能以
重复试验或隐藏 surface 绕过。

## 9. Repository readiness

本审查结论形成时的 active checkout 只保留产品/工程正式文档、治理入口、Conformance
records、license/security 与品牌资产：

- 不含 predecessor 或 production source；
- 不含 production Maven reactor/module/generated API/test/benchmark/Example；
- 不含 CI/release/package workflow 或 build artifact；
- 不含当时的大规模引擎 active Temporary 或 parallel Design；
- root/project/agent entry 路由到当前正式 Owner；
- historical evidence 明确标记，不覆盖 current baseline；
- selected delivery profile 尚未建立，未来必须使用 allowlist。

因此 I0 可以从真实空 implementation boundary 建立 build spine，不需要先删除伪 module 或
兼容层。

本审查完成后新增的核心抽象专题是有意建立的后续governance input，不改变“没有production
implementation surface”的事实。其promotion、correction与targeted delta review已经由当前
最终审核记录，本文不外推current status。

## 10. Evidence and claim boundary

当前 evidence 只支持：

- 候选到正式文档的 replacement closure；
- selected Java 8 generated type、Join builder、Predicate IR/callback 与 ForkJoin narrowing 的
  bounded feasibility；
- 100+ Table/Field/Join generated-surface stress 没有触及所测 classfile hard boundary；
- scheduler paper feasibility 与三个完整 paper journey；
- 文档 authority、traceability、link 与 repository-surface consistency。

当前 evidence 不支持：production processor/storage/planner/scheduler/compression、百万或一亿
行性能、完整 Java 8 support、security/package/release readiness。G1-G10 全部是 `NOT_RUN`。

## 11. Authorization boundary

开始 I0 仍需要 Product Owner 明确 implementation authorization。该授权不会自动包含：

- commit 或 push；
- 未经 admission 的 dependency/network access；
- GitHub workflow、Release、Package、signing 或 publication；
- performance/support/compatibility claim；
- 并行启动多个 implementation slice。

若 I0 前发现会改变普通用户 capability、signature、result、order、null/missing、numeric、
failure、lifecycle、resource visibility 或 product boundary 的新选择，本审查必须重新打开。
Local class naming、internal data structure、test implementation 与已受控的 profile coefficient
不属于新的产品裁决。
