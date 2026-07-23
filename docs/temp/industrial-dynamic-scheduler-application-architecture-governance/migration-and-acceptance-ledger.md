# 迁移与验收账本

类型：Temporary

状态：active

Owner：industrial-dynamic-scheduler migration and acceptance ledger

事实范围：实施 slice、旧责任接管、验收证据和最终引用闭包

非事实范围：正式应用 Design、SOMA 产品能力、未执行的性能结论和发布状态

最后审查日期：2026-07-23

## 1. Immutable points

| 阶段 | Commit | 状态 | 证据 |
|---|---|---|---|
| 治理前实现 | `048b225` | passed | Zulu JDK 8 完整 `project-check: ok` |
| Stage 0 协议/审计 | `7f43f1d` | passed | docs Gate、完整 `project-check: ok` |
| Stage 1 详细设计 | 待提交 | active | docs Gate |
| implementation candidate | 待形成 | pending | 专项 Gate |
| final cutover | 待形成 | pending | 完整 Gate、Report、Temporary退役 |

首次完整 Gate 遇到 Maven Central TLS transient；固定 TLS 1.2 后同一基线完整通过。
该环境处置不改变项目文件或支持矩阵。

## 2. 责任接管

| 旧责任 | 目标唯一 Owner | 状态 |
|---|---|---|
| CLI 手工 runtime 编排 | `SchedulingSolver` / `SomaSchedulingSolver` | pending |
| runtime 导出 + 摘要分离 | 完整 `ScheduleResult` / assembler | pending |
| validator 拥有 checksum | `ScheduleChecksum` | pending |
| nested input records | top-level Problem model | pending |
| generator static utility | `SchedulingProblemFactory` implementation | pending |
| bootstrap 全部职责 | runtime factory/projector/verifier | pending |
| IndustrialScheduler 全部职责 | engine/event/frontier/committer | pending |
| `state` 技术包 | `schema` 技术投影 | pending |
| main fixture/oracle/checks | test fixture/oracle/verification | pending |
| main benchmark/JVM metrics | test benchmark | pending |
| SchedulerConfig benchmark keys | `BenchmarkOptions` test config | pending |

## 3. 能力验收

| 事实 | 基线 | Candidate | 最终 |
|---|---|---|---|
| 四 profile deterministic input | passed | pending | pending |
| correctness expected schedule | passed | pending | pending |
| complete domain validator | passed | pending | pending |
| one-shot/lifecycle/IndexSnapshot negative | passed | pending | pending |
| long-run 10,000 operations | passed | pending | pending |
| multi-fork allocation/GC/high-water | passed | pending | pending |
| ordinary consumer clean/repeat | passed | pending | pending |
| Java 8 + generated/schema reproducibility | passed | pending | pending |
| production JAR excludes evidence | failed by design | pending | pending |
| package dependency DAG | failed by design | pending | pending |
| canonical Problem/Solver/Result journey | failed by design | pending | pending |

## 4. 实施顺序

### S2：边界优先

- 新增 detached Result model/checksum/assembler；
- 新增 Solver/Session facade；
- CLI、validation、verification、benchmark 切换到 facade；
- 删除 runtime -> validation；
- 提交并运行 correctness/default。

### S3：内部责任

- 拆 Problem records/validator/index/checksum 与 Factory；
- 拆 Solver engine/event/frontier/committer；
- 拆 Runtime factory/projector/verifier；
- 原子重命名 state -> schema；
- 四 profile 和 long-run 通过后提交。

### S4：source-set 与 Gate

- 迁移 fixture/oracle/checks/verification/benchmark；
- 拆 benchmark config；
- 更新 POM/script classpath；
- 增加 architecture/JAR fail-closed 规则；
- isolated/repeat/multi-fork 通过后提交 immutable candidate。

### S5–S6：固化

- 更新应用 Blueprint/Design/Validation/README；
- 更新必要 Implementation Map/Conformance；
- 完整 Gate 与 scope non-regression；
- Governance Report；
- Temporary 删除、入口恢复、最终提交。

## 5. 最终引用闭包

最终必须无 current 命中：

```text
com.hgtech.soma.examples.scheduler.state
runtime.IndustrialScheduler
runtime.ScheduleResult
runtime.SchedulerRuntimeBootstrap
config.SchedulerConfig
problem.SchedulingProblemGenerator
evidence.SchedulerVerification
evidence.SchedulerBenchmark
validation.TinyScheduleOracle
```

历史 Governance Report 中的实现基线描述可以保留，但不得作为 current 入口或类型
导航。Checker 的禁止恢复断言可以保留旧 identity 字符串。
