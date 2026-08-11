# SOMA Canonical Logical IR 与执行引擎 S6 最终资格

类型：Conformance / Final Implementation Qualification / Replacement Closure

状态：`PASS / S6_CLOSED / S1-S6_COMPLETED / NO_ACTIVE_SLICE`

日期：2026-08-12

Owner：Canonical Logical IR与执行引擎M1最终资格、性能防退化、Owner closure与claim boundary

上游：[正式晋升与实施准入](v1-canonical-ir-execution-engine-promotion-readiness.md) ·
[S1-S6实施计划](../engineering/canonical-ir-execution-engine-implementation-plan.md) ·
[规划与优化Design](../design/planning-and-optimization.md) ·
[执行Design](../design/execution-and-concurrency.md)

## 1. 最终结论

S6通过，S1-S6全部完成。Row、Field、Mapped、Primitive、Relation、Group与Selection已经由同一套
Canonical semantic、terminal-start Bound、Normalized、PhysicalPlan、resource admission和
operation-local ExecutionFrame主线承载；reference interpreter从Bound语义独立执行，optimized与parallel
继续使用specialized kernel。旧Row planning/execution carrier、迁移adapter和双重decision truth已经退出。

本专题没有改变Blueprint、public/generated API或Java frontend语义，没有新增dependency、production
artifact、public/internal SPI、JSON/Workflow或SOMA Engine实现。GitHub Release/Package、远端Maven
publication、签名与正式release声明仍未授权。

## 2. 最终责任闭环

```text
generated Java facade
    -> CanonicalOperation
        -> terminal-start BoundOperation
            +-- independent ReferenceInterpreter
            +-- NormalizedOperation
                    -> PhysicalPlan + ResourceEstimate
                        -> actual resource admission
                            -> operation-local ExecutionFrame
                                -> specialized sequential / parallel operator
```

- Canonical/Bound只拥有语义、identity、terminal-start root与provenance；
- Normalized只拥有已证明等价的规范化结果；
- PhysicalPlan拥有access path、algorithm、partition与保守资源估计；
- lease成功后，ExecutionFrame及其operator-local hot state才拥有cursor、membership、hash、worker和scratch；
- reference不消费Normalized或Physical decision，也不作为production fallback；
- sequential、optimized与parallel共享逻辑结果、顺序、failure和resource合同，但不合并为boxed universal executor。

Relation的right Index cursor最终保持lease-scoped operator local。Frame仍拥有生命周期和admission边界，
cursor不被Bound/Plan持有，也不跨execution存活；局部形态同时保留HotSpot scalar replacement机会。

## 3. 正确性与交付证据

- Java 8 full clean资格：runtime 71 tests、processor 34 tests均为0 failure/error；processor规模证据为
  112 Tables、448 Fields、224 Indexes；
- 最终源码重新编译后，`group-relation.sh`与`parallel-execution.sh`均`PASS`，覆盖deterministic
  generation、normal/negative Java 8 consumer、public signature和artifact isolation；
- 最终`./scripts/check.sh`交付重放`PASS`：三个reference application、独立packaged Java 8 scheduling
  consumer、source/javadoc artifacts、checksum、SBOM、provenance与runtime dependency边界全部闭合；
- scheduling正常路径为100,000 operations、makespan 50,281、correctness `PASS`；
- old-carrier exact-symbol scan确认`BoundRowPlan`、`NormalizedRowPlan`、`RowOptimizer`、`RowExecutor`、
  `OptimizedSequentialRowExecutor`、`ReferenceRowInterpreter`、`ParallelRowScheduler`与
  `StableTopLocatorHeap`在production/test/build入口中均不存在；
- `git diff --check`通过；冻结Engineering Plan SHA-256仍为
  `eecf4e03d0da4bf131243f3426b8013a76fa7c380ffbdd289ee7ce930bfaa93d`。

## 4. 性能防退化

以下数据是同一台M5 Pro、Java 8、当前进程状态下的fixed-host snapshot，不是跨硬件SLA。所有操作均通过
correctness/fingerprint；10K、1M与10M的source、stateful、relation/group和mutation family均已执行。

| Scale | Representative operation | Final median |
|---|---|---:|
| 10K | Field sum / typed Table filter / Index exact count | 0.189 / 0.394 / 0.018 ms |
| 1M | Field sum / filter / materialize | 6.645 / 16.329 / 6.296 ms |
| 1M | Join / filtered Join / Semi / Anti | 186.023 / 246.941 / 130.808 / 153.916 ms |
| 10M | Field sum / filter / materialize | 63.700 / 158.321 / 58.714 ms |
| 10M | sort-limit / top | 93.739 / 101.554 ms |
| 10M | Join / filtered Join / Semi / Anti | 2.127 / 2.479 / 1.484 / 1.819 s |
| 10M | Selection update / remove | 481.707 / 2,709.541 ms |

1M Join按三次fresh JVM取median，相对正式S4 fixed-host基线168.680 ms为`+10.3%`，低于冻结计划的
`15% + 2 ms`阻断线。为排除代码因果，在同一时段、同一机器对detached S4 parent与当前实现执行10M
A/B：S4/current Join为2.086/2.127 s（`+1.9%`），而filtered、Semi、Anti均未退化。由此确认此前
1.674 s历史结果是JIT、温度与host状态快照，不构成本次变更的因果回归。

标准100K FJSP warm benchmark为581.317 ms（3 warmups、7 samples），correctness与makespan均通过，
保持既有约0.51-0.60 s fixed-host能力边界。本专题没有为该场景准入application frontier、ordered
access path或场景特化runtime补丁。

## 5. 审查与Gate disposition

主Agent按用户要求执行一次bounded fresh review，没有用多轮subagent审查替代交付。审查仅覆盖：

- old carrier与bridge replacement closure；
- Canonical/Bound/Normalized/Physical/Frame ownership；
- reference/optimized/parallel独立性；
- public/generated surface、package与publication boundary；
- 性能变化的同机因果A/B。

未发现新的P0/P1、双IR、双physical decision、未准入surface或Design drift。受本专题影响的G4、G5、
G6、G7、G9及G10 local qualification保持`PASS`；G1-G10整体仍为`PASS`。

## 6. Final disposition

```text
S1-S6 implementation       COMPLETED
Replacement closure        PASS
Correctness qualification  PASS
Performance guard          PASS
Owner consistency          PASS
Active implementation      NONE
Release/publication        NOT_AUTHORIZED
```

本记录关闭Canonical IR/Execution M1治理的production replacement gap。后续优化必须继续服从现有
Blueprint与九个Design Owner；若改变产品语义、artifact/dependency或SOMA Engine边界，必须重新进入独立治理。
