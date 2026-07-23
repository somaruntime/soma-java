# 目标架构

类型：Temporary

状态：active

Owner：industrial-dynamic-scheduler target architecture

事实范围：工业动态调度参考应用的目标责任、依赖方向、production contract、source-set 和 package 候选

非事实范围：当前实现状态、SOMA 产品 Design、精确 generated API 和最终验证结论

最后审查日期：2026-07-23

## 1. 产品角色

该 child 是一个正式 Java 8 reference application，而不是调度产品 SDK。它必须同时：

1. 展示普通项目如何通过清晰应用边界消费 SOMA；
2. 实现可重放、约束完整的工业动态调度流程；
3. 提供不污染 production artifact 的 correctness/performance evidence。

因此 production journey 与 evidence journey 共享同一个 `SchedulingSolver` contract，
但不共享 runner、fixture 或 benchmark infrastructure。

## 2. 依赖方向

```text
application
  -> config
  -> problem/factory
  -> solver contract
       -> solver implementation
            -> runtime
                 -> schema/generated + SOMA runtime
            -> result assembly
  -> result validation

test/evidence
  -> production packages
  -> fixture/oracle/runtime checks/benchmark metrics
```

强制规则：

- `problem` 不依赖 SOMA annotation、runtime 或 generated code；
- `solver` 不依赖 application、validation、test 或 evidence；
- `runtime` 不依赖 solver、result validation、test 或 evidence；
- `result` 不暴露 `SchedulerRuntime`、SOMA Table、current Index 或 generated cursor；
- `validation` 只依赖 detached problem/result；
- `schema` 是 SOMA 技术投影，不拥有 Problem、Solver 或 Result 的应用语义；
- production source 不 import test/evidence package。

依赖图必须无环。

## 3. Production contract

普通调用只有一条 canonical 路径：

```java
SchedulingProblem problem = problemFactory.create(problemConfig);
SchedulingSolver solver = new SomaSchedulingSolver();
ScheduleResult result = solver.solve(problem);
ScheduleValidator.validate(problem, result);
```

`SchedulingSolver` 是应用级求解契约：

```java
public interface SchedulingSolver {
  ScheduleResult solve(SchedulingProblem problem);
  SchedulingSession prepare(SchedulingProblem problem);
}
```

`solve(problem)` 是推荐入口，内部完成 prepare、solve、result assembly 和 release。
`prepare(problem)` 只服务需要分离 bootstrap/solve measurement 或显式 session
lifecycle 的高级调用；它不暴露 runtime。

```java
public interface SchedulingSession extends AutoCloseable {
  ScheduleResult solve();
  void close();
}
```

Session one-shot、非线程安全。`solve()` 成功或失败都关闭内部 runtime；未执行的 session
由调用者 `close()`。第二次 `solve()` 必须被拒绝。

## 4. 完整 Result 边界

`ScheduleResult` 必须包含：

- immutable、detached `List<ScheduledOperation>`；
- completed jobs、makespan、tardiness、weighted tardiness、processed events；
- stable result checksum；
- immutable `SolveDiagnostics`，包含 schema/runtime-plan identity 和既有 runtime
  high-water/evidence counters。

关闭 session/runtime 后，Result 的读取、校验、checksum 和顺序扰动检查仍然有效。
`ScheduledOperation` 是应用结果类型，不携带 SOMA annotation，也不暴露
`OperationAssignment` schema record。

`ScheduleResultAssembler` 是 schema materialization 到 detached result 的唯一边界。
checksum 由 Result 关注点拥有，不再由 Validator 反向提供给 Solver。

## 5. Problem 与 Factory

`SchedulingProblem` 是 immutable aggregate；输入 record 改为可独立导航的顶层类型：

- `JobSpec`
- `MachineSpec`
- `MaintenanceInterval`
- `ResourceSpec`
- `OperationSpec`
- `MachineOption`
- `SetupTimeSpec`
- `TransportTimeSpec`
- `ExternalEvent` / `ExternalEventType`

辅助责任拆分为：

- `SchedulingProblemValidator`：identity/reference/range/matrix/event preflight；
- `SchedulingProblemIndex`：稳定 point lookup；
- `SchedulingProblemChecksum`：input identity；
- `SchedulingProblemFactory`：从某种来源创建已验证 Problem；
- `SyntheticSchedulingProblemFactory`：当前 deterministic generator 实现。

Factory 靠近它创建的 Problem，不建立收纳无关工厂的通用 `factory/` 杂物包。

## 6. Config

Production 只拥有：

- `ProblemGenerationConfig`：synthetic sample/workload 输入参数；
- `ProblemConfigLoader`：properties、CLI override、strict key 与 canonical text。

`benchmark.warmup/forks/measurements` 由 test source 的 `BenchmarkOptions` 独立拥有。
`default.properties` 是可运行 sample；correctness/large/long-run 是 versioned
test workloads。外部 `.properties` 路径继续受支持。

本应用当前没有需要配置化的求解策略，因此不创建空洞 `SolverConfig`。若未来确有
多策略，先通过应用 Design 增加。

## 7. Solver 分工

原 `IndustrialScheduler` 拆为：

- `DispatchEngine`：主循环、终止条件和统计聚合；
- `ExternalEventProcessor`：事件消费、job gate、machine delay；
- `CandidateFrontier`：operation release、candidate refresh、selection、version
  revalidation 和 retire；
- `AssignmentCommitter`：显式跨 Table 写顺序、resource calendar、successor release；
- `SelectedCandidate`：可复用的 application scratch；
- `DispatchSummary`：内部 solve outcome，不是最终 Result。

拆分按共同变化原因进行，不把每个私有方法机械变成一个类。

## 8. Runtime 与 Schema

Runtime 分工：

- `SchedulerRuntime`：单次 session 的 Table/application-state lifecycle owner；
- `SchedulerRuntimeFactory`：RuntimePlan、Table 创建和失败清理；
- `RuntimeProjector`：reserve、Batch import 和 application calendar 初始化；
- `RuntimeProjectionVerifier`：bootstrap projection equivalence；
- `MachineCalendar`、`ResourceCalendar`、`JobGate`：application runtime structures；
- `RuntimeSnapshot`：result/evidence 所需的 immutable diagnostic snapshot。

原 `state` package 原子重命名为 `schema`，generated package 同步为
`schema.generated`。Table name、字段、key/unique/index/child、default/optional 和
访问语义保持不变；该应用 schema/hash identity 允许随 package cutover 更新，但必须
clean/repeat 稳定。

## 9. Package 与 source-set

```text
src/main/java/com/hgtech/soma/examples/scheduler/
├── application/
├── config/
├── problem/
├── result/
├── solver/
├── runtime/
├── schema/
└── support/

src/test/java/com/hgtech/soma/examples/scheduler/
├── benchmark/
├── fixture/
├── oracle/
└── verification/
```

`ScheduleValidator` 是真实应用可选的独立结果校验器，保留在 production
`result` 关注点。Fixture、oracle、Index/lifecycle negative checks、verification
runner、benchmark 和 JVM metrics 全部进入 test source-set。

无 JUnit 或新依赖。专项脚本显式运行 `target/test-classes` 中的 main-based suite。

## 10. 不采用的方案

- 不拆成多个 production Maven module：规模不足以抵消构建和发布复杂度；
- 不保留旧 facade/package 作为长期兼容层：该 reference application 不是已发布 SDK；
- 不让 `ScheduleResult` 直接携带 SOMA materialized schema record；
- 不把 validator 放进 solver correctness path；
- 不为了目录整齐拆出无独立责任的一类一包；
- 不修改 SOMA core 来迁就示例重构。
