# SOMA Reference Application Portfolio 与最佳实践治理

类型：Temporary

状态：active（Stage 4 complete）

Owner：SOMA reference application portfolio and best-practice governance

正式事实源：否

实施授权：本专题 Stage 1–6 的应用、evidence、脚本、正式固化和阶段性提交

事实范围：专题意图、产品输入、应用职责、独立性、不变量、阶段、停止条件和验收协议

非事实范围：当前正式产品语义、已确认应用缺陷、已批准 production 修改和发布声明

起始仓库基线：`6bf0c0e546f22aa95d9d8b8a2748640aa43acd39`

Stage 0 immutable baseline：`b893653`

最后审查日期：2026-07-27

## 1. 意图

`soma-examples` 不只是功能演示，而应由多个严肃、独立的 Java 8 reference
application 共同展示 SOMA 的最佳实践。每个应用必须从自己的 business model
推导状态表示、Access Pattern、Transformation、控制流和 evidence，不能为了覆盖
API 成为“厨房水槽”。

本专题以以下产品层次为输入：

```text
Table / Access Model
  -> Transformation Model
  -> DataFlow Execution Model
```

- Table/Access 拥有 packed columnar state、point/exact/scan/column/bulk/mutation；
- Transformation 描述 shape、cardinality、order 和 lineage 的改变；
- DataFlow 把 Transformation 组织为可复用、可绑定、可规划和可并行执行的有限
  DAG。

单 Table 也可以完成 Selection、Projection、GroupBy 和 Aggregation；Join 才引入
多来源。DataFlow 不是所有应用控制流的替代品。

## 2. 目标

本专题必须同时完成：

1. 建立三个相互独立的 reference application portfolio；
2. 为每个应用确定单一、自然的产品叙事和 capability/evidence ownership；
3. 使用“抽象、叙事、不变量 Owner、按构造即正确”复审两个现有应用；
4. 使 `grassing-individual-simulation` 与当前 SOMA Access、Transformation、
   failure、ownership、lifecycle 和性能标准一致；
5. 新建严肃的 `real-time-dispatch-rule-engine`，承担 reusable DataFlow 的主要
   application trace；
6. 在替代 coverage 完整后，从工业调度删除 `AssignmentSummaryFlow` 及其展示性
   evidence；
7. 以 application-owned 单遍 summary 保持工业调度 Result 的领域语义和性能；
8. 保持三个应用的输入生成、运行时状态、Result 和 evidence 清晰分责；
9. 最终原子固化正式文档、Conformance、Report 和 Gate，并删除 Temporary。

成功不以每个应用使用更多 SOMA API、增加 Table、增加测试或统一目录形状为前提。
若业务模型证明某种 primitive array、heap、queue 或 Java orchestration 更合适，
保留它就是最佳实践。

## 3. 产品与并发边界

SOMA 采用：

> single-owner、synchronous operation，而不是通用线程安全容器。

一次 Table operation 或 DataFlow Invocation 期间，application 必须独占相关
runtime state。SOMA 可以在同步调用内部使用受控 worker，但不提供：

- 多调用者并发访问或通用线程安全 Table；
- 跨 Table 事务、自动回滚或分布式一致性；
- application event loop、状态机或补偿逻辑；
- JDBC/CDC、MES 同步、checkpoint 或 retry。

跨 Table 有两条正式路径：

```text
DataFlow:
  bound Sources
    -> bulk Transformation
    -> detached Result / Command

Java orchestration:
  Table A operation
    -> stable key / primitive / detached value
    -> Table B operation
    -> application preflight / compensation / rebuild / fail-stop
```

实际应用可以由 DataFlow 推导批量候选或命令，再由 Java 完成业务判断和顺序提交。
跨 operation 不得保存 current Index、Cursor、ColumnView 或 borrowed handle。

## 4. Portfolio 裁决

三个应用必须在源码、领域模型、Schema、配置、Factory、Runtime、Result、测试、
基线和文档上互相独立。`soma-examples` 只聚合，不产出领域共享 JAR；应用之间不得
import 对方 package，也不得共享领域 fixture、test helper 或性能 artifact。

| Application | 主叙事 | 主要 SOMA 责任 |
|---|---|---|
| `industrial-dynamic-scheduler` | 高性能动态调度算法与显式业务编排 | direct Access、Candidate Scan、key/exact/column/batch/mutation，以及应用自有 frontier、heap、event loop 和跨 Table 顺序提交 |
| `grassing-individual-simulation` | 高频迭代 runtime-state 仿真 | packed traversal、exact group、staged mutation、Batch、swap-remove、确定性、生命周期和 fail-stop |
| `real-time-dispatch-rule-engine` | 可复用 dispatch rule DataFlow | Definition/Template/Invocation、多 Source、Join、GroupBy、并行、budget/cancellation、detached Result/Command 和 controlled Effect |

Capability matrix 只说明各应用自然证明了什么，不让应用形成依赖，也不要求每个
应用覆盖全部 API。第三个应用必须是普通业务应用，不得退化为 operator fixture 或
`dataflow-demo`。

详细现状和目标矩阵见
[Portfolio 职责与覆盖](portfolio-responsibility-and-coverage.md)。

Stage 1 详细设计与不变量闭包见：

- [三应用详细设计](stage-1-detailed-design.md)；
- [不变量与 Evidence 矩阵](stage-1-invariant-and-evidence-matrix.md)。

Stage 2 实施与非回归结论见
[Grassing 一致性治理收口](stage-2-grassing-closeout.md)。

Stage 3 实施与非回归结论见
[RTD Reference Application 收口](stage-3-rtd-closeout.md)。

Stage 4 实施与非回归结论见
[Industrial DataFlow 责任迁移收口](stage-4-industrial-closeout.md)。

## 5. 两个现有应用的治理方法

审计沿少量完整 vertical slice 展开，不逐类套模板：

```text
Business intent
  -> application abstraction
  -> representation and access
  -> operation narrative
  -> invariant owner and defense
  -> detached result / failure / release
  -> evidence
```

每个关键不变量至少回答：

```text
Invariant
  -> unique Owner
  -> construction / validation defense
  -> failure state
  -> representative evidence
```

- public/application boundary 使用真实校验和稳定异常；
- immutable input/result、Factory、one-shot session/builder 在事实产生处排除
  非法状态；
- internal `assert` 只守护关闭后也不会损坏语义的纯推导事实；
- 测试证明防线，不重复冻结 private helper、数组布局或相同 forwarding case；
- 方法叙事保持相同或相邻抽象层次，按真实执行因果展开。

## 6. 已冻结的迁移裁决

### 6.1 Industrial scheduler

`AssignmentSummaryFlow` 的最终目标是删除。删除前必须由新应用建立不低于当前的
DataFlow application coverage；不得出现“先删除、以后再补”的中间状态。

替代实现从 authoritative `OperationAssignmentTable` 通过 direct column/access
或 application-owned 单遍 summarizer 推导：

- assignment count；
- makespan；
- per-job completion；
- total/weighted tardiness。

它不得在 dispatch commit 中维护第二份累计事实，也不得改变 `ScheduleResult`、
checksum、独立 validator、domain behavior 或性能阈值。是否还能移除工业调度的
`soma-dataflow` 依赖，必须依据 generated companion 的真实 compile/runtime
契约另行裁决。

### 6.2 Grassing simulation

该应用必须复审并对齐当前 SOMA 设计，但不强制引入 reusable DataFlow。direct
Candidate Scan、exact group、ColumnView、Batch 和 application-owned primitive
world/scratch 若符合 business model，应保持 canonical。

### 6.3 RTD rule engine

目标 journey 为：

```text
versioned config
  -> detached runtime snapshot / delta
  -> RTD runtime state
  -> reusable dispatch-rule DataFlow Template
  -> repeated one-shot Invocation
  -> detached DispatchCommand / Result
  -> application-owned commit
```

数据库/MES 同步、JDBC/CDC、重试、事务和分布式执行不进入该应用。输入边界从
detached Batch/Delta 开始，输出边界止于 detached Command/Result。

## 7. 非回归约束

本专题不得静默改变或削弱：

- Schema-Defined、Compiler-Specialized、JVM Heap-Resident、Java 8 定位；
- annotation Schema、public/generated API、protocol 和 SOMA 三层产品模型；
- packed storage、key/unique/exact、Index/IndexSnapshot、swap-remove；
- ownership、lifecycle、single-operation failure atomicity 和 aggregate fault；
- DataFlow Shape/operator、parallel determinism、executor ownership 和 safe point；
- 两个现有应用的领域能力、canonical journey、Result identity 和独立性；
- correctness、AoS/domain validator、order independence、failure/lifecycle Gate；
- 六个既有 application workload、性能阈值和 `claimAllowed=false`；
- Zulu JDK 8 唯一验真边界、G0–G5 和 G6 blocked。

不得引入第三方依赖、Java Stream hot path、DTO/object graph live storage、
reflection/metadata interpreter、跨应用共享领域 JAR、隐式 common pool 或跨
Table transaction。

## 8. 授权边界与停止条件

当前授权覆盖本专题 Stage 1–6 的应用、evidence、脚本、正式固化和阶段性提交。
正式 Design 在 Stage 6 原子切换前保持稳定；本授权不扩大 SOMA public/generated
API、annotation Schema、protocol 或核心语义。

后续即使获得应用内部实施授权，出现以下情况仍必须停止并请求决定：

- SOMA public/generated API、annotation Schema、protocol 或核心语义需要变化；
- application domain behavior、Result contract 或 canonical journey 需要不兼容
  变化；
- 需要删除领域能力、缩小 workload、放宽阈值或弱化 Gate；
- 需要引入第三方依赖、跨 Table transaction、数据库同步或分布式能力；
- 新应用无法在不复制工业调度业务模型的前提下自然承载 DataFlow；
- 需要把 application 发现扩大成 SOMA core 治理；
- 性能回归无法归因，或只能通过反复 fork/rebaseline 掩盖。

Conformance 发现不扩大授权。正式 Design 在最终候选完成并获得切换授权前保持
稳定。

## 9. 阶段

| Stage | 当前状态 | 责任 | 退出条件 |
|---|---|---|---|
| 0 | COMPLETE | 协议、职责矩阵、现状审计、非回归约束和实施前基线 | Temporary 自洽；事实、候选问题和 Gate 已登记 |
| 1 | COMPLETE | 两个现有应用详细审计、RTD business model 与三应用详细设计 | 抽象/叙事/不变量矩阵闭合；迁移顺序和 evidence 设计明确 |
| 2 | COMPLETE | grassing 一致性治理 | 应用内部候选保持业务语义并通过专项 Gate |
| 3 | COMPLETE | 独立 RTD rule engine 实现 | canonical journey、DataFlow/parallel/effect evidence 成立 |
| 4 | COMPLETE | industrial 复审与 DataFlow 责任原子迁移 | `AssignmentSummaryFlow` 删除；Result/性能/coverage 不回归 |
| 5 | PENDING | 三应用 correctness、scale、soak、parallel 与 portfolio Gate | evidence Owner 唯一；无 coverage 缺口 |
| 6 | PENDING | scope non-regression、完整 Gate、正式固化和 Temporary 退役 | Report/Owner/导航完成；无尾项 |

Stage 1 可以裁决某个局部实现保持现状，但不能取消两个应用的完整复审、第三个应用
或 `AssignmentSummaryFlow` 删除目标。每个实施 slice 必须可以独立保留，不能依赖
未来重写才正确。

## 10. 验证策略

- Stage 0：`./scripts/check-docs.sh`、`git diff --check` 和一次
  `./scripts/check.sh`，不运行大规模 application performance calibration；
- 应用切片：各自 correctness/architecture Gate，先 semantic 后 performance；
- 新 DataFlow：reference differential、sequential/parallel identity、managed/
  borrowed executor、budget/cancel/effect 和 canonical E2E；
- 性能：稳定候选后才运行 multi-fork；普通 3 fork，新 baseline 按正式
  Engineering 规则建立，不用 9 fork 反复打圈；
- 最终收口：一次完整 Gate、必要的 Fast/Scale/Soak/Full 和
  `git diff --check`。

## 11. 完成条件

专题只有在以下条件全部满足后才能退役：

- 三应用 portfolio 和独立性由代码、POM、文档和 Gate 共同强制；
- 两个现有应用完成最新方法复审，真实偏差关闭，保留项有证据；
- grassing 与当前 SOMA 契约和质量标准一致；
- RTD rule engine 是严肃、独立、可配置、可重放的 application；
- RTD 完整接管 DataFlow application trace；
- `AssignmentSummaryFlow` 和展示性 coupling 已删除，工业 Result/性能不回归；
- application production 不携带无正当运行时责任的 test/evidence implementation；
- 正式长期事实提升至唯一 Owner，Implementation Map/Conformance/Report 同步；
- 全部专项和完整 Gate 通过；
- Temporary 删除，`docs/README.md` 恢复无 active topic 状态。

Stage 0、审计文档、第三个应用骨架或 coverage 迁移一半都不能代替最终收口。
