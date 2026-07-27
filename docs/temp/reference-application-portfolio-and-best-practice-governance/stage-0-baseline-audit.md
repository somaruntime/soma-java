# Stage 0 实施前基线审计

类型：Temporary

状态：active（Stage 0 evidence）

Owner：SOMA reference application portfolio and best-practice governance

正式事实源：否

实施授权：只读审计与 Stage 0 文档证据

事实范围：起始基线上的应用结构、SOMA 使用、coverage、Gate 和详细设计候选

非事实范围：最终缺陷判定、Stage 1 详细设计、production 修复和正式 Conformance

最后审查日期：2026-07-27

## 1. 基线与审计输入

起始仓库基线：

```text
6bf0c0e546f22aa95d9d8b8a2748640aa43acd39
```

该 commit 已收口 Transformation/DataFlow 与 correctness preservation 治理；
worktree 在 Stage 0 开始时干净，`docs/temp/` 只有 `.gitkeep`。

Stage 0 读取了：

- 产品 Blueprint、系统架构、Access、Transformation、DataFlow、failure 和性能
  Design；
- reference application Implementation Map、Conformance、current performance
  summary 和治理 Report；
- 两个 child 的 Blueprint/Design/Validation、POM、production/test source；
- reference application、application-specific、performance 和 documentation Gate；
- 当前六份 application baseline 与两份 neutral component baseline。

Stage 0 不修改 production、POM、正式 Design、应用正式文档、脚本、测试或
baseline，也不运行大规模 performance calibration。

## 2. 当前 Portfolio 事实

`soma-examples/pom.xml` 当前只聚合：

```text
industrial-dynamic-scheduler
grassing-individual-simulation
```

两个 child POM 都没有继承领域 parent，是普通 Java 8 consumer。聚合模块不产出
共享 JAR。正式系统架构、应用入口、Implementation Map、Conformance 和多个 Gate
都显式声明“两个应用”；增加第三个 application 需要最终原子更新这些 Owner 和
checker。

当前源码形状只作为导航：

| Application | production Java | test Java | schema Table | profile baseline |
|---|---:|---:|---:|---:|
| industrial scheduler | 64 | 11 | 9 | 3 |
| grassing simulation | 32 | 11 | 2 | 3 |

数字不构成复杂度判定或删除依据。

当前八份 performance baseline 为两份 neutral component 和六份 application
profile。普通 application profile 使用 3 fork；新 baseline 通常至少 5 fork；
9 fork 不是失败后的默认重跑策略。

## 3. Industrial scheduler 现状

### 3.1 已成立的主叙事

```text
versioned config
  -> immutable SchedulingProblem
  -> Solver / one-shot Session
  -> Runtime projection
  -> event + primitive frontier + revalidation + commit
  -> detached ScheduleResult
  -> independent domain validation
```

应用使用九张 schema Table，并按业务 cardinality 选择 primary、secondary unique、
exact group、ColumnView 和 Batch。Candidate pool、per-machine group、heap、
event queue 和 resource calendar 是应用自有结构，不伪装成 SOMA Table。

### 3.2 当前 DataFlow coupling

`DispatchEngine` 在每次 solve 中创建 `AssignmentSummaryFlow`，后者使用 static
Definition/Template 和 one-shot Context/Invocation，从 assignment Table 推导
count、makespan、job completion 和 tardiness。DataFlow stats 进入 production
package-private `SolveEvidence`，再由 test access 验证。

这条实现语义当前正确且已有 5-fork non-regression，但同时让 scheduler 承担：

```text
dispatch algorithm narrative
+ reusable DataFlow application trace
+ DataFlow execution evidence bridge
```

目标裁决已冻结为删除 `AssignmentSummaryFlow`。Stage 1 仍需设计 direct
single-pass replacement、Result proof 和 coverage 原子迁移，不能把“已决定删除”
误写成“可以立即删除”。

### 3.3 Stage 1 证明义务

| ID | 候选问题 | 当前分类 |
|---|---|---|
| `RA-IND-001` | Solver/Session/Engine/Frontier/Committer 的抽象与方法叙事是否保持同层 | application structure audit |
| `RA-IND-002` | DataFlow stats 进入 production `SolveEvidence` 是否只服务测试 | ownership/evidence candidate |
| `RA-IND-003` | 单遍 summary 怎样从 authoritative assignment facts 推导全部 Result 指标 | detailed-design obligation |
| `RA-IND-004` | 删除后 `soma-dataflow` dependency 的 compile/runtime 必要性 | dependency evidence obligation |
| `RA-IND-005` | 新 coverage 建立前后的 baseline identity 与性能比较方式 | migration/evidence obligation |

这些项目尚未判定为 production defect。

## 4. Grassing simulation 现状

### 4.1 已成立的主叙事

```text
versioned config
  -> detached SimulationScenario
  -> Simulator / one-shot Session
  -> Runtime + ordered tick systems
  -> detached SimulationResult
  -> AoS per-tick differential
```

应用使用 keyed `GrasserState` 与 dense `TraceSample` 两张 Table。`by_mode` exact
group、packed filter/sort/update/remove、Batch、ColumnView、swap-remove 和
stable key 都有自然消费者。Grass world、cell scratch 和 deterministic random
保持 application-owned primitive 表示。

系统顺序为：

```text
growth -> metabolism/death -> reproduction
  -> grassing -> searching -> trace
```

Reproduction 和 grassing 已采用 staged primitive decision、single-Table operation
和 application fail-stop 边界；AoS oracle、physical-order independence、
default/large/long-run baseline 提供较强 evidence。

### 4.2 与当前产品治理的时间差

应用 Blueprint/Design 的最后审查为 2026-07-23/24。2026-07-27 的
Transformation/DataFlow 治理明确排除 grassing 业务迁移；POM 也以
“Protocol alignment only”解释 `soma-dataflow` 依赖。没有 production
Definition/Template/Invocation。

这不构成缺陷：direct Candidate Scan 本身是当前 canonical path。需要审查的是
应用是否符合最新 Access、Transformation、aggregate fault、ownership、lifecycle
和构造正确性，而不是是否使用 reusable DataFlow。

### 4.3 Stage 1 证明义务

| ID | 候选问题 | 当前分类 |
|---|---|---|
| `RA-GRA-001` | Config/Scenario/Simulation/Runtime/System/Result 抽象与依赖叙事 | application structure audit |
| `RA-GRA-002` | tick、birth/death、next identity、world publish 和 Session state 的唯一 Owner | invariant-owner audit |
| `RA-GRA-003` | `step()`/`finish()` 失败及 cleanup 是否覆盖 RuntimeException、unexpected Error 和 cleanup suppression | failure proof obligation |
| `RA-GRA-004` | production `RuntimeProjectionVerifier` 的逐值复核是必要 boundary defense 还是重复证明 | construction/evidence candidate |
| `RA-GRA-005` | runtime diagnostics 进入 public domain `SimulationResult` 是否符合事实与 evidence 分层 | result-boundary candidate |
| `RA-GRA-006` | Result summary/checksum 的重复 sorted traversal 与 mutable assembler fields 是否保持清晰 Owner | narrative/performance candidate |
| `RA-GRA-007` | `soma-dataflow` 依赖属于 generated companion 必需还是含混的展示残留 | dependency evidence obligation |
| `RA-GRA-008` | 现有 direct pipeline 是否覆盖全部自然 Transformation，且没有强迫 DataFlow 的必要 | conformance obligation |

`RA-GRA-003`–`008` 是需要证据的候选，不是 Stage 0 已确认缺陷。Stage 1 可以裁决
保持现状。

## 5. RTD rule engine 当前状态

仓库当前没有第三个 application、domain model、Schema、config、Gate 或 baseline。
Stage 0 只冻结目标责任，不预造代码：

```text
detached snapshot/delta
  -> independent RTD runtime
  -> reusable dispatch-rule Template
  -> repeated one-shot Invocation
  -> detached Command/Result
  -> application-owned commit
```

Stage 1 必须先形成业务模型，而不是从 Operator 清单倒推一组 Table。至少需要裁决：

| ID | 详细设计问题 |
|---|---|
| `RA-RTD-001` | Work、Resource、Capability、runtime snapshot/delta 的领域语义 |
| `RA-RTD-002` | Config/Factory/Runtime/Rule/Execution/Result 的依赖方向 |
| `RA-RTD-003` | Source、Join、GroupBy、Aggregation 和 selection 的自然业务组合 |
| `RA-RTD-004` | Template identity/retention、Invocation binding 和 parameter contract |
| `RA-RTD-005` | sequential/adaptive/managed/borrowed executor 的等价与 ownership |
| `RA-RTD-006` | budget、cancellation、detached command 和 controlled effect |
| `RA-RTD-007` | Java multi-Table orchestration、preflight、compensation/rebuild/fail-stop |
| `RA-RTD-008` | correctness、scale、soak、parallel workload 和 baseline identity |

新应用不得复制 industrial scheduler 的 frontier/event/assignment business model，
也不得扩张到 MES/database synchronization。

## 6. 当前 Evidence 与 Gate

| 责任 | 当前入口 |
|---|---|
| child isolation / ordinary consumer | `check-reference-applications.sh` |
| scheduler correctness/architecture | `check-industrial-scheduler.sh` |
| simulation AoS/lifecycle/architecture | `check-grassing-simulation.sh` |
| application default/large/long-run | Fast / Scale / Soak / Full scripts |
| baseline topology | `check-performance-baseline-architecture.sh` |
| generated Scan/DataFlow footprint | `check-scan-code-size.sh` |
| neutral DataFlow semantics | `check-dataflow-reference.sh` |
| neutral DataFlow performance | `check-dataflow-performance.sh` |
| aggregate closeout | `check.sh` |

当前 DataFlow coverage 由 generated external consumer、reference differential、
neutral component benchmark 和 industrial application trace 分层承担。RTD
application 只接管 application-integrated trace，不替代前三层。

Stage 1 必须设计 checker 演进，使其从固定两个 child/六个 application baseline
过渡到三个独立 child 及其正式 workload，同时避免把数量硬编码成产品语义。

### 6.1 Stage 0 验证事实

Stage 0 在以下环境执行验证：

```text
JDK: Azul Zulu 1.8.0_492-b09
Maven: 3.9.16
OS: macOS 26.5.2
Architecture: aarch64
```

`./scripts/check-docs.sh` 与 `git diff --check` 通过。`./scripts/check.sh`
完成了文档、reactor、public API、compiler/codegen、runtime、external
consumer、两个 reference application、DataFlow reference differential 和
post-cutover component performance 等前序 Gate；随后在 DataFlow component
performance admission 启动时失败：

```text
java.lang.NoSuchMethodError:
com.hgtech.soma.benchmarks.DataFlowComponentBenchmark.access$0(int)
```

失败后检查当前 class 文件，外层
`DataFlowComponentBenchmark.access$0(int)` 与嵌套 `Workload` 的调用描述符一致。
因此 Stage 0 不把该现象判定为 SOMA runtime、DataFlow 语义或 benchmark 性能
缺陷，也不形成任何性能退化结论；当前证据更符合增量编译期间外层类与嵌套类
class-set 不一致的 Gate reliability 候选。

| ID | 优先级 | 详细设计证明义务 |
|---|---|---|
| `RA-GATE-001` | P1 | 在不扩大 fork 的前提下，复现并定位 DataFlow benchmark class-set 产生与装载顺序；判断 Gate 是否需要 clean/repeat 或 outer/nested descriptor preflight，并确保失败发生在性能 fork 前 |

由于 Stage 0 只授权文档与只读审计，本轮不修改 checker，也不为追求绿色结果反复
运行 multi-fork benchmark。完整 Gate 当前应如实记录为“未完成：存在
`RA-GATE-001`”，不能表述为通过；这不改变两个应用既有 Gate 已在本次执行中通过
的事实。

Stage 1 已关闭该候选：private outer helper 形成的 synthetic accessor 是
outer/nested class-set 的脆弱契约；实现已消除该 accessor，并在性能 fork 前加入
descriptor preflight。两次 clean 编译表明 raw class SHA 会受 Zulu javac 8
等价 lowering 变化影响，因此 Gate 不把 raw SHA 当作语义身份；完整 15-lane
single admission 已通过。该结论没有改变 benchmark workload、fork、threshold
或 baseline。

## 7. 风险与控制

| 风险 | 控制 |
|---|---|
| 三应用变成统一模板 | 只统一质量标准，不统一业务包结构 |
| grassing 被强迫 DataFlow 化 | 每项 API 必须有 business/access 证据 |
| RTD 退化为 operator kitchen sink | 先业务模型和 canonical journey，后能力选择 |
| 先删 industrial coverage | RTD evidence 通过后原子迁移 |
| 将 application 发现误归因 core | 先业务算法、application representation/API usage，再要求领域中性 reproduction |
| 性能测试打圈 | 语义候选稳定后才 multi-fork；普通 3 fork，禁止失败后自动扩大 |
| 文档中间状态成为正式事实 | 正式 Design/应用文档保持稳定，最终原子切换 |
| 以 LOC/文件数判断架构 | 只按抽象、Owner、变化原因、叙事和 evidence 裁决 |

## 8. Stage 0 结论

- 三应用独立 portfolio 的目标自洽，且比单个 kitchen-sink example 更符合产品
  分层；
- 两个现有应用不是待推翻的 prototype，已有架构和性能治理是有效起点；
- industrial 的双重叙事和 DataFlow coverage 迁移是真实、已裁决的治理任务；
- grassing 没有 reusable DataFlow 不是缺陷，但需要按最新方法完成应用内部复审；
- grassing failure、projection、Result/diagnostics 和 dependency 边界存在值得
  Stage 1 证明的候选，不得在 Stage 0 直接修复；
- 第三个应用必须先完成 business model 和详细设计，不能从当前 API 反向拼装；
- 当前没有证据要求修改 SOMA core、public/generated API 或正式产品语义；
- 完整 Gate 暂未通过，`RA-GATE-001` 必须在 Stage 1 先完成窄诊断和 checker
  详细设计，不能用重复多 fork 掩盖；
- Stage 1 应完成三应用详细设计和证据方案，再申请 production 实施授权。
