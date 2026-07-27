# Reference Application Portfolio 职责与覆盖

类型：Temporary

状态：active（Stage 0 candidate）

Owner：SOMA reference application portfolio and best-practice governance

正式事实源：否

实施授权：Stage 0 只读映射

事实范围：三个应用的候选业务叙事、独立性、SOMA 使用面和 evidence ownership

非事实范围：正式模块数量、已实现第三应用、最终 API 清单和 performance claim

最后审查日期：2026-07-27

## 1. Portfolio 原则

Portfolio 的目标不是让每个应用证明 SOMA 的全部能力，而是让真实 business model
自然选择正确工具：

```text
Business facts and decisions
  -> authoritative runtime state
  -> Access Pattern
  -> Transformation / Java orchestration
  -> Result / Effect
  -> application evidence
```

应用之间只能共享：

- SOMA public artifacts；
- Java 8/Zulu 8 工程边界；
- project-level documentation、artifact 和 Gate protocol；
- 领域中性的 benchmark comparator/validator。

应用之间不得共享领域 schema、model、runtime、result、config、factory、fixture、
oracle、benchmark implementation 或 baseline。聚合 POM 不提供领域 parent、
dependency management 或 shortcut。

## 2. 目标职责矩阵

| 关注点 | Industrial scheduler | Grassing simulation | RTD rule engine |
|---|---|---|---|
| business model | 动态调度、事件与约束提交 | 个体生态、tick 与 birth/death churn | 周期性实时派工规则 |
| primary state | jobs/operations/machines/resources/assignments | grasser population、grass world、trace | work/resource/capability/runtime snapshot |
| canonical control | Java event loop + solver | Java tick engine + ordered systems | reusable Template + repeated Invocation |
| point/key/unique | primary/unique lookup | stable individual key | runtime identity lookup |
| exact group | eligible machines | behavior mode | capability/status group |
| packed scan | bounded definition/result traversal | population hot traversal | bound source candidate traversal |
| ColumnView | hot primitive frontier input | projection/result diagnostics as justified | typed binding or direct point support |
| Candidate Scan | local exact candidate and direct terminal | filter/sort/update/remove | single-source regions inside graph |
| Batch | initial projection、assignment append | initial projection、offspring/trace append | detached snapshot/delta admission |
| swap-remove | runtime candidate/state as required | death | only if business state requires |
| application structure | heap/frontier/event/calendar | primitive world/scratch/system order | rule definition, runtime, invocation, command |
| Transformation | direct access and application summary | natural ad-hoc pipeline only | primary logical computation |
| reusable DataFlow | no portfolio responsibility | no forced adoption | primary responsibility |
| multi-source Join | not required for dispatch hot loop | not required | natural rule computation |
| parallel | application algorithm remains explicit | only if domain/order permits | managed and borrowed execution evidence |
| controlled Effect | Java sequential single-Table commit | staged mutation and fail-stop | detached command and safe-point effect |
| cross-Table consistency | preflight/revalidate/fail-stop | staged publish/fail-stop | application-owned commit, no transaction |
| detached output | `ScheduleResult` | `SimulationResult` | `DispatchCommand` / Result |

表格是责任边界，不是预先冻结的 API 使用清单。Stage 1 必须从业务叙事证明每项
使用或不使用，而不是机械填满单元格。

## 3. Industrial dynamic scheduler

当前应用已自然证明：

- keyed/unique/exact/ColumnView/Batch 与 current Index 生命周期；
- application-owned primitive candidate pool、machine-local group 和 min-heap；
- event loop、version revalidation、跨 Table 顺序提交和 fail-stop；
- detached Problem/Result、独立领域 validator 和三 profile 性能。

当前额外承担的 reusable DataFlow trace 是 solve 终点的
`AssignmentSummaryFlow`。目标状态改为：

```text
authoritative assignment Table
  -> application-owned direct single-pass summary
  -> detached ScheduleResult
```

它不再拥有 DataFlow Definition/Template/Invocation/stats 的 portfolio coverage。
Candidate frontier 继续由应用结构拥有，不迁入通用 graph。

## 4. Grassing individual simulation

当前应用已自然证明：

- keyed individual state、mode exact group、packed traversal；
- filter/sort/update/remove、Batch、swap-remove；
- deterministic random、stable identity 和 physical-order independence；
- application-owned `double[]` world 与 reusable primitive scratch；
- one-shot Session、partial-create cleanup、detached Result；
- AoS per-tick differential 与 default/large/long-run evidence。

目标不是把 systems 改写为 reusable DataFlow。Stage 1 应重点证明：

- Simulation/Runtime/Engine/System/Result 的抽象和同层叙事；
- tick、birth/death、world publish、next identity 和 Session state 的唯一 Owner；
- expected/internal/unexpected failure 后的 fail-stop 与 cleanup；
- projection validation、Result diagnostics 和 evidence 是否位于正确边界；
- 当前 direct pipeline 是否已是最自然的 Transformation 形态。

## 5. Real-time dispatch rule engine

第三个应用必须从独立业务模型开始，不能复制 scheduler 的 operation、machine
frontier、event queue 或 Result。

候选业务叙事：

```text
detached snapshot / delta
  -> validate and project RTD state
  -> bind reusable dispatch-rule Template
  -> filter readiness
  -> join capability/resource facts
  -> project priority and feasibility
  -> group / aggregate / select
  -> detached DispatchCommand + diagnostics
  -> application preflight and single-Table safe-point commits
```

Stage 1 需要裁决：

- Work/Resource/Capability 等领域对象和 authoritative Table；
- config、snapshot/delta、Factory、Runtime、Rule、Execution、Result 的边界；
- rule Definition identity、parameter、Template retention 和 Invocation ownership；
- sequential/adaptive parallel 的业务等价边界；
- Join expansion、output、scratch、task、deadline/cancellation budget；
- command 与 multi-Table Java orchestration 的失败策略；
- default/scale/soak workload 和可重放 identity。

该应用不实现 MES adapter、database polling、CDC、retry、checkpoint、事务或
distributed DataFlow。

## 6. Evidence ownership

| Evidence | Owner |
|---|---|
| SOMA operator/Shape semantics | `soma-dataflow` reference differential |
| SOMA kernel/allocation mechanics | neutral component benchmark |
| generated public consumer compatibility | external consumer Gate |
| scheduler domain correctness | scheduler validator/oracle |
| simulation domain correctness | AoS per-tick oracle |
| RTD rule correctness | RTD domain oracle/reference evaluator |
| application architecture/JAR isolation | each application Gate |
| application performance | each application-owned baseline |
| portfolio independence and coverage | root orchestration checker |

新 RTD application 不能用 component fixture 代替 canonical production journey；
也不应重复证明全部 operator 组合。它证明业务集成、反复 Invocation、资源/并行和
detached command 边界。

## 7. Coverage 迁移顺序

```text
freeze existing industrial DataFlow coverage
  -> design and implement independent RTD journey
  -> establish RTD correctness / parallel / resource / E2E evidence
  -> prove portfolio Gate has no coverage gap
  -> replace industrial summary with direct application summarizer
  -> remove AssignmentSummaryFlow and display-only evidence
  -> revalidate industrial Result and performance
```

迁移完成前，工业调度当前 DataFlow coverage 仍是实施事实，但 Temporary 已明确其
目标责任将退役。正式文档在最终原子切换前不写入中间状态。
