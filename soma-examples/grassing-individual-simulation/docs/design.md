# 个体生态仿真应用 Design

类型：应用 Design

状态：当前

Owner：grassing-individual-simulation

对 SOMA 产品规范性：否

事实范围：canonical journey、应用分层、detached Scenario/Result、runtime aggregate、system顺序、随机与失败边界

最后审查日期：2026-07-28

## 责任边界

唯一 production journey 为：

```text
SimulationConfigLoader -> SimulationConfig
  -> SimulationScenarioFactory -> SimulationScenario
  -> Simulator / SimulationSession
  -> SimulationResult
```

production concern 与依赖方向固定为：

```text
application -> config, scenario, simulation, result
scenario    -> config, support
simulation  -> scenario, result, runtime
runtime     -> config, scenario, result, schema, support
schema      -> SOMA annotations only
result      -> Java platform only
config      -> support
support     -> Java platform only
```

- `application`：composition root 与 CLI rendering；
- `config`：strict properties、typed immutable config 与稳定 identity；
- `scenario`：detached input、`IndividualSeed`、Factory；
- `simulation`：canonical `Simulator`/`SimulationSession` facade；
- `runtime`：factory、projection、aggregate、engine、ordered systems 与 staging；
- `schema`：application-owned annotation schema；
- `result`：detached immutable Result/Diagnostics；
- `support`：无状态 deterministic random 与 stable hash。

`config/scenario/support` 不 import SOMA runtime 或 generated package；runtime 不
调用 Scenario Factory。Grass grid、cell scratch、random function 和 system order
由应用拥有。AoS oracle、verification、negative checks、benchmark 与 JVM metrics
只位于 test source-set，不进入 production JAR。

## Session 与结果

`SomaSimulator.run()` 必须复用 `prepare()` 的同一条执行路径。
`SimulationSession` 是 one-shot live runtime owner：

```text
READY -> RUNNING -> FINISHED -> CLOSED
   \        \ failure --------> CLOSED
```

`step()` 最多推进一个 tick；`currentResult()` 只产生当时的 detached summary；
`finish()` 完成剩余 tick 并释放 runtime；`close()` 幂等。失败后 Session 不可重用。
`SimulationResult` 与 `SimulationDiagnostics` 不持有 Table、Index、IndexSnapshot、
Cursor、ColumnView、Batch、record 或 mutable array。默认结果不复制完整 world；
本设计当前不建立 `SimulationSnapshot` contract。

## Authoritative state

- `GrasserStateTable` 是 live individual state，`GrasserId` 是跨 operation identity；
- `TraceSampleTable` 只保存低频 summary，不参与 system 决策；
- `double[] grass` 是二维 world 的 row-major authoritative field；
- `Index` 只在一个同步只读批次中立即消费；长期引用必须使用 key；
- 两张root Table由`grassing-simulation-session`显式SomaGroup组合，连同scratch
  由单个`SimulationRuntime` aggregate拥有，并由`SimulationSession`统一release；
- `SimulationRuntimeFactory` 与 `RuntimeProjector` 分别拥有 resource creation
  和一次性投影；完整逐值投影复核由 test-only
  `SimulationRuntimeTestAccess.verifyProjection` 承担，不进入 production hot
  path。

Factory先创建冻结Group并atomic attach两张root；partial-create或projection失败只
关闭Group一次。`SimulationRuntime.runtimeMetadata()`返回detached
`SomaGroupMetadata`供lifecycle/evidence读取，不参与system hot loop。

删除使用 swap-remove，不承诺 packed 遍历顺序。结果 checksum 先按 stable ID
canonicalize 个体，再编码 grass 的固定 cell 顺序。

## Engine、Mutation 与失败

`SimulationSessionLifecycle` 是 READY/RUNNING/FINISHED/CLOSED 与 fail-stop cleanup
的唯一 Owner；ordinary `RuntimeException` 和 unexpected `Error` 都会先保留
primary failure，再以 suppressed 记录 cleanup failure。`SimulationEngine` 只拥有
tick、maximum population、固定编排和 result assembly。
六个 package-private Owner 按固定顺序执行：

```text
GrassGrowthSystem -> MetabolismSystem -> ReproductionSystem
  -> GrassingSystem -> SearchingSystem -> TraceRecorder
```

拆分不得改变 candidate access、浮点表达式与累加顺序、random addressing、stable
child ID、Batch publish 或 authoritative grass publish 顺序。

单次 SOMA operation 保持其既有失败原子性；应用不声称跨 Table/grass transaction。
两阶段系统遵循：

1. 只读扫描形成可丢弃 primitive decision；
2. 完成 finite、bounds 和 cell budget 校验；
3. 提交可能失败的 SOMA operation；
4. 执行不抛异常的 grass primitive publish；
5. authoritative publish 后的意外异常使 session fail-stop。

结构 mutation 不发生在 borrowed/update callback 中。繁殖按稳定 ID 暂存 parent
事实，构建 offspring Batch，原子更新 parent energy 后批量 append；若跨 operation
后续失败，不继续复用该 session。

## 成本边界

- growth：`O(world cells)`；
- metabolism、grassing、searching：`O(current population)`；
- death：candidate scan + swap-remove；
- reproduction：candidate filter，选中 parent 的 stable sort 与 batch append；
- mode access：`@SomaIndex` exact group；
- trace/result：低频 stable sort、Column/IndexSnapshot gather 或 materialization。

`SimulationResultAssembler` 的一次 stable-ID traversal 同时推导 energy 与
individual checksum；mode count 由独立 exact-group pass 产生。所有 accumulator
都是 operation-local，不在 Runtime 中维护 Result shadow。

配置在装载时一次解析为 typed primitive 字段并缓存 canonical text/checksum，
hot loop 不重复解析字符串或计算配置 identity。系统 loop 不使用 Java Stream，
也不逐 tick materialize object graph。

production 只内置 default config；correctness/large/long-run profiles、benchmark
参数和 evidence 均属于 test。应用自己的 annotation package/name 使用
`schema` / `grassing_individual_simulation_schema`；这是 application-owned clean
cutover，不改变 SOMA Schema 语义、字段、key、exact index、capacity 或 Access
Model。

本应用只拥有个体生态仿真的领域设计；SOMA Access Model 与 runtime 语义仍由
[根级正式 Design](../../../docs/design/README.md)拥有。
