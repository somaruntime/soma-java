# SOMA Vectorized Physical Pipeline 与 Morsel-Driven Execution 能力扩展治理

类型：Queued Bounded Temporary / Candidate Design Workspace

状态：`QUEUED / NOT_ACTIVE / NOT_DESIGN / IMPLEMENTATION_NOT_AUTHORIZED`

Owner：第一阶段正式晋升之后，未来physical capability扩展的候选问题空间、设计与slice route

最后更新：2026-08-12

## 1. 当前边界

第一阶段已经由
[正式Design](../../design/README.md)与
[Conformance](../../conformance/v1-vectorized-physical-pipeline-phase1-promotion.md)接管：finite primitive
Chunk kernel、PhysicalPlan单次decision、shared ordinal-work lifecycle与Chunk-morsel partial是当前
baseline，不在本文重新定义。

本文只保留Product Owner已同意继续研究的能力扩展路线。它不是active governance、正式Design、
Implementation Plan或授权；开始前必须单独完成Candidate Design、独立一致性审查、Baseline Freeze与
Implementation Authorization。

## 2. 治理目标

在不改变public/generated API、Canonical semantics、Reference oracle、resource/failure contract和
两artifact topology的前提下，评估哪些`Source × Representation × Type × Predicate × Terminal × Mode`
cell值得加入finite physical kernel，并让parallel execution用bounded morsel/partial减少数据搬运。

设计必须优先避免三类过度设计：

- 不建立boxed universal Batch DAG或一类一算子的class hierarchy；
- 不为未激活slice预建placeholder、SPI、cache或feature flag；
- 不因“数据库引擎通常如此”而跳过SOMA真实consumer、profile和代码规模准入。

## 3. 候选Slice Map

### R2 — Candidate Design与Baseline Freeze

只设计，不扩张production能力。至少裁决：

- 最小physical pipeline责任模型；
- capability matrix与pipeline breaker；
- selection/materialization/partial-state strategy；
- PhysicalPlan、ResourceEstimate、ExecutionFrame、representation和scheduler Owner关系；
- eligible/fallback、Reference differential、explain和diagnostic边界；
- 后续每slice的功能、代码、架构、工程质量Claim与Stop Rule。

### R3 / S1 — AUTO encoded-aware aggregate与predicate

第一优先候选：encoded Field sum、simple typed predicate、predicate + integral sum。不得通过关闭AUTO、
整体无预算解压或复制PLAIN storage truth获得收益。

### R4 / S2 — Finite primitive kernel matrix

按真实profile逐cell准入primitive type、pure typed predicate、count/match/min/max/sum、可保持既有
numeric tree的floating aggregate，以及ordered primitive materialization。没有消费者或稳定收益的cell
不进入production。

### R5 / S3 — Morsel-driven terminal expansion

扩展typed-only full traversal terminal的worker-local partial；保持caller participation、bounded
drainers、canonical ordinal merge和no O(rows) membership materialization。Existing Chunk默认就是morsel，
只有profile证明粒度问题时才设计sub-Chunk range。

### R6 / S4 — 至多一个复杂operator

根据届时profile在GroupBy partial、Equality Join probe-side或“不准入复杂operator”中选择一个。不得同时
激活GroupBy与Join，也不为了路线完整强行实现。

### R7 / S5 — 全局资格与Temporary closure

最终覆盖10K/1M/10M、PLAIN/AUTO、sequential/parallel、eligible/fallback、allocation/temporary/CPU、
Reference differential、正式reference journeys、ABI/two-artifact/package与source delivery。稳定事实晋升后
删除本文。

## 4. 共同不变量

- Canonical IR是唯一semantic truth；Reference保持独立test-only oracle；
- PhysicalPlan是所有physical eligibility、kernel、partition与resource decision的唯一Owner；
- Execution只消费admitted plan，不能重建另一套planner或resource model；
- Row range与morsel共用现有shared ordinal-work lifecycle；
- callback/stateful barrier、order、numeric、failure、terminal-start binding与quiescence不变；
- O(N) result/selection/hash与O(chunks/participants) partial在work前checked admission；
- unsupported shape使用现有optimized path，不转Reference、不伪装unsupported；
- 每个新增cell必须同时有Reference differential、resource/failure evidence与profile收益；
- public Batch/Vector、third artifact、new dependency、runtime codegen与SOMA Engine不在当前路线。

## 5. 激活条件

只有Product Owner明确启动R2，本专题才可从`QUEUED`变为active。R2未冻结前不得修改production source；
设计反例若需要改变public semantics、numeric/failure/resource visibility或产品边界，必须回到对应正式
Owner并等待裁决。
