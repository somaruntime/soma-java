# SOMA Vectorized Physical Pipeline与Morsel-Driven Execution治理专题

类型：Active Bounded Temporary / Frozen Design and Implementation Governance

状态：`DESIGN_BASELINE_FROZEN / IMPLEMENTATION_AUTHORIZED / VP1_COMPLETED / VP2_ACTIVE`

Owner：第一阶段正式基线之上的finite physical pipeline扩展问题、冻结临时设计、VP1-VP3实施路线与
Temporary replacement closure

Product Owner激活：2026-08-12

Product Owner临时设计固化：2026-08-12

Product Owner实施授权：2026-08-12（VP1-VP3完整范围）

最后更新：2026-08-12

## 1. 当前结论与授权

本专题的设计阶段已经完成。经过current-state、bounded feasibility、正式Owner一致性、过度设计和实施
可执行性审核，临时设计已经冻结为`vector-pipeline-expansion-vp1`：

```text
第一阶段正式finite kernel基线
    -> VP1 single final decision + encoded-native integral count/sum
        -> VP2 ordered long[] representation-native + parallel materialization
            -> VP3 qualification + formal promotion + Temporary closure
```

当前状态严格区分：

```text
Temporary Design Baseline    FROZEN
Implementation Readiness     READY
Implementation Authorization GRANTED
Formal Design Promotion      NOT_PERFORMED
Production Implementation    VP1_COMPLETED / VP2_ACTIVE
Release/Publication          NOT_AUTHORIZED
```

Product Owner已在Design Freeze之后另行授权按冻结VP1-VP3完整实施。该授权不扩张冻结capability、public/
generated API、artifact、dependency或release边界；一次只推进一个active slice。

## 2. 唯一文档地图

| 文档 | 唯一责任 | 当前性 |
|---|---|---|
| [Current-state盘点](current-state-audit.md) | `e2ce237` carrier、call path、capability和evidence snapshot | frozen input |
| [冻结临时设计](design.md) | 本专题capability、Owner、representation、resource、parallel与fallback合同 | current Design baseline |
| [有限可行性验证](feasibility-validation.md) | code-shape、4项targeted test与1M AUTO/OFF方向证据 | bounded evidence，不是qualification |
| [Pre-freeze自审](readiness-review.md) | R2.4审查provenance | historical input，不拥有current status |
| [Baseline Freeze与Readiness](baseline-freeze-and-readiness.md) | 最终审核、冻结集合、readiness和授权边界 | current status Owner |
| [Implementation Plan](implementation-plan.md) | VP1-VP3顺序、范围、Gate、性能守卫和恢复点 | frozen implementation input |

不存在并行的`candidate-design.md`。正式长期事实仍由
[Planning](../../design/planning-and-optimization.md)、
[Execution](../../design/execution-and-concurrency.md)、
[Architecture](../../design/implementation-architecture.md)、
[Storage](../../design/data-model-and-storage.md)和
[Core](../../design/core-abstractions-and-narratives.md)拥有；VP3资格通过后稳定增量才晋升回这些Owner。

## 3. 为什么治理

[第一阶段正式晋升](../../conformance/v1-vectorized-physical-pipeline-phase1-promotion.md)已经证明，
PLAIN primitive hot path可以由逐值visitor下降为once-per-Chunk typed loop，parallel aggregate可以由
O(rows) locator prefix下降为O(Chunk count) partial。

R2当前证据同时表明：

- 1M direct Field sum的AUTO/OFF bounded median约为`3.545 / 0.389 ms`；
- typed filter + projected sum约为`7.771 / 2.785 ms`；
- direct materialize约为`1.179 / 0.354 ms`；
- callback、mapped reference和Index exact没有同类representation gap。

这些数字只确定优先级，不是稳定SLA。它们说明下一阶段应优化encoded integral traversal和ordered long
materialization，而不是建立通用vector engine、扩张callback/stateful或重写Group/Join。

## 4. 冻结目标与范围

总目标：在Java frontend与Canonical semantics不变的前提下，由PhysicalPlan一次选择finite kernel、
representation handler、parallel strategy和完整ResourceEstimate；ExecutionFrame在lease后只消费该
decision，使用现有Chunk和shared ordinal-work lifecycle执行。

冻结准入：

- Table count与schema-known integral Field sum；
- zero/simple pure typed integral predicate；
- PLAIN typed array与integral encoded run-level execution；
- overlay scalar/current fallback；
- existing Chunk作为morsel的count/sum partial；
- ordered `long[]` encoded-native与parallel materialization；
- PLAIN/AUTO/overlay、sequential/parallel与existing optimized fallback的完整证据。

明确不准入：

- boolean、其他primitive array、min/max/average/summary与floating kernel扩张；
- callback、mapped、stateful、Index residual、GroupBy、Join和mutation specialization；
- sub-Chunk、general DAG、Batch/Vector container、runtime codegen、Java Vector API或plan cache；
- public/generated API、dependency、第三production artifact、Java版本或SOMA Engine变化。

这些排除项没有“以后顺手补齐”的隐含授权；如未来有真实profile，必须重新做bounded admission。

## 5. 冻结主叙事

```text
generated Java frontend
    -> existing Canonical / Bound / Normalized semantics
        -> planner receives a closed terminal requirement
            -> one final PhysicalPlan + ResourceEstimate
                -> resource admission
                    -> operation-local ExecutionFrame
                        -> once-per-Chunk representation dispatch
                            -> PLAIN typed / encoded run / overlay scalar
                                -> sequential sink or Chunk partial/range
                                    -> canonical merge / detached result
```

核心不变量：

1. Canonical IR和Reference oracle不因kernel改变；
2. eligibility、representation、parallel和resource只形成一次final Physical decision；
3. Execution不在lease后重新规划或增加未计费scratch；
4. borrowed typed/run access不逃逸Frame，不成为第二storage truth；
5. callback/stateful breaker不被重排、重复调用或跳过；
6. result、order、numeric、null、failure、currentness和quiescence与正式合同一致；
7. unsupported shape稳定走existing optimized path，不转Reference；
8. 一次只有一个active implementation slice。

## 6. 实施顺序

精确内容见[冻结Implementation Plan](implementation-plan.md)：

- **VP1**：single final Physical decision与encoded-native integral count/sum/predicate；
- **VP2**：ordered `long[]` representation-native与Chunk-morsel parallel materialization；
- **VP3**：10K/1M/10M、Reference/resource/performance/reference applications、formal promotion和
  Temporary closure。

不存在primitive matrix或complex operator slice。每个slice只有在功能、代码、架构、工程质量、matched
performance、bounded review、Conformance和干净checkpoint全部闭合后才能进入下一项。

## 7. Stop Rules

以下任一条件出现时暂停并等待Product Owner裁决：

- 需要改变Blueprint、public/generated API、Canonical semantics或正式failure/numeric合同；
- 需要第三artifact、新dependency、新Java版本、Vector/native API或runtime codegen；
- 需要第二planner、executor、scheduler、resource model或storage truth；
- 需要整体decode、跨terminal cache、sub-Chunk或general DAG才能取得收益；
- 希望激活冻结矩阵外的type/terminal、GroupBy或Join；
- 正确性、资源和性能不能同时成立；
- 证据不再变化，却继续增加测试、review或抽象。

## 8. 专题关闭条件

本Temporary只有在获授权的VP1-VP3全部关闭后退役：

1. production只保留一个final Physical decision和一个parallel lifecycle；
2. 已准入cell的semantic/resource/failure/performance证据闭合；
3. old refinement、adapter、flag、dead fixture和重复Owner全部退出；
4. 稳定M1增量由正式Design和Conformance接管；
5. project、AGENTS、Design、Engineering与Conformance入口一致；
6. 本Temporary删除，不作为平行Design或历史过程档案长期保留。

## 9. 当前下一步

VP1已经通过[正式资格](../../conformance/vectorized-pipeline-expansion-vp1-qualification.md)，single final
Physical decision与encoded-native integral scan均已闭合。当前active slice为VP2；Codex只按冻结
[Implementation Plan](implementation-plan.md)实施ordered `long[]` representation-native materialization与
bounded parallel count/prefix/write，VP2 exit closure前不进入VP3。
