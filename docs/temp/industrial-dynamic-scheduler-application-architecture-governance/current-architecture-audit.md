# 当前架构审计

类型：Temporary

状态：active

Owner：industrial-dynamic-scheduler current architecture audit

事实范围：治理起点的源码结构、依赖方向、职责集中、source-set 和使用入口证据

非事实范围：目标架构、正式应用 Design、SOMA 产品能力和实施授权

最后审查日期：2026-07-23

## 1. 基线

- 当前 HEAD：`048b225`；
- reference application implementation baseline：`955c956`；
- 42 个 Java 文件全部位于 `src/main/java`，没有 `src/test/java`；
- 手写 Java 共 3,505 行；`SchedulingProblem`、`IndustrialScheduler`、
  `ScheduleValidator` 和 `SchedulerRuntimeBootstrap` 共 1,784 行；
- 当前 package 为 root、`config`、`problem`、`state`、`runtime`、
  `validation`、`evidence` 和 `support`。

已有强项包括 ordinary consumer isolation、detached generator/runtime 分离、完整
领域闭环、四类 workload、领域 validator、long-run 和多 fork evidence。本专题不
否定这些事实。

## 2. 已确认问题

### A1：依赖方向成环

正式应用 Design 声明 `runtime -> validation/evidence` 的单向流程，但
`IndustrialScheduler.resultChecksum()` 反向调用 `ScheduleValidator`；
`ScheduleValidator` 又依赖 runtime `ScheduleResult`，形成
`runtime <-> validation` package cycle。

### A2：Result 边界不完整

`IndustrialScheduler.solve()` 只返回统计摘要。CLI、verification、benchmark 和
oracle 都必须再调用 `SchedulerRuntime.exportAssignments()`，因此 runtime lifecycle
泄漏到 application/evidence，关闭 runtime 前后的结果边界不清晰。

### A3：缺少 canonical Solver 门面

四条路径重复执行：

```text
SchedulerRuntimeBootstrap.load
  -> new IndustrialScheduler
  -> solve
  -> exportAssignments
  -> validate
  -> close
```

这不是普通 composition-root 重复，而是缺少一个拥有 runtime lifecycle 和完整
result assembly 的 production contract。

### A4：production 与 evidence 混装

`SchedulingProblemFixtures`、`TinyScheduleOracle`、`SchedulerRuntimeChecks`、
`SchedulerVerification`、`SchedulerBenchmark` 和 `JvmMetrics` 全在
`src/main/java`。普通 JAR 因而携带测试输入、负路径、benchmark 和平台测量代码；
Surefire 已配置但没有实际测试 source-set。

### A5：配置关注点混合

`SchedulerConfig` 同时拥有 synthetic problem generation、workload 规模和
benchmark warmup/forks/measurements。每个 production run 都必须提供 benchmark
key；类名也掩盖了它主要是 synthetic workload 配置，而不是 solver 配置。

### A6：主要类型承担多个共同变化原因

- `SchedulingProblem`：输入 record、lookup index、预检、derived capacity、
  checksum 和八个嵌套模型；
- `IndustrialScheduler`：event、release、frontier refresh、selection、
  revalidation、commit、metrics 和 result checksum；
- `SchedulerRuntimeBootstrap`：plan、capacity、全部 projection、calendar 构造和
  projection verification；
- `ScheduleValidator`：领域 invariant、claimed-summary comparison、checksum 和
  validation summary。

### A7：技术 Schema 与应用概念混淆

`state/` 实际是唯一 annotation schema package，内部同时包含 definition、
authoritative runtime state、derived frontier、result table 和 value object。
单 package 对 codegen 有合理性，但 `state` 命名没有表达其技术投影责任，也不能
替代 Problem/Runtime/Result 的应用边界。

## 3. 根因判断

此前专题的目标是解除旧四场景与 SOMA 产品的所有权耦合，并建立 ordinary consumer
和替代 evidence。其详细设计明确采用紧凑的 `problem/state/runtime/validation/evidence`
结构，因此当前实现基本符合当时设计。

当前问题不是该切换失败，而是新的产品化目标要求更高：参考应用不仅要“能证明
SOMA 可用”，还应展示真实项目怎样隔离领域输入、求解契约、SOMA runtime 和结果。
因此需要先提升应用 Design，再让实现服务于新 Design。

## 4. Stage 0 裁决

- 值得进行应用架构治理，不能只移动目录或改名；
- 不拆成多个 Maven production module；一个独立 application artifact 足够；
- annotation schema 可以保持单 package，但应成为明确的技术投影边界；
- test/evidence 应退出 production JAR，但现有无第三方、脚本驱动的验证方式可以保留；
- 先建立完整 Result 与 Solver 门面，再拆内部类型，避免大爆炸式改包；
- 正式应用文档在 candidate 通过前保持稳定。
