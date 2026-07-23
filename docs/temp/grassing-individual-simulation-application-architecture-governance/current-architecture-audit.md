# 当前架构审计

类型：Temporary

状态：active

Owner：grassing-individual-simulation current architecture audit

事实范围：治理起点的源码、资源、依赖、应用入口、evidence 和 Gate 事实

非事实范围：正式应用 Design、未来实现决定、SOMA 产品语义和跨环境性能结论

最后审查日期：2026-07-23

## 1. 基线

治理前 immutable baseline 为：

```text
1c1bc22 docs: close scheduler architecture governance
```

该基线已在 Azul Zulu full JDK 8 通过完整 `./scripts/check.sh`，最终输出
`project-check: ok`。本审计只评价应用架构，不推翻既有 correctness 和
performance evidence。

## 2. 已有成熟能力

当前应用已经具备：

- 版本化配置和 detached initial-state generation；
- 与 packed order 无关的 deterministic random；
- 固定 system 顺序、stable child identity 和 two-phase grass publish；
- SOMA key、exact group、filter/update/remove、Batch、ColumnView 和
  IndexSnapshot 使用；
- correctness/default/large/long-run 四类 workload；
- correctness 逐 tick AoS 位级等价；
- initial-order independence、invariant、lifecycle negative path；
- 三个独立 JVM fork 的 allocation、GC 和 runtime high-water evidence；
- ordinary consumer isolation、Java 8 和 schema/generated reproducibility。

因此，本专题不是“补齐功能”，而是纠正 production architecture 与 evidence
architecture 的耦合。

## 3. 当前物理形态

```text
src/main/java
├── SimulationApplication
├── config
├── model
├── runtime
├── state
├── support
├── validation
└── evidence

src/test/java
└── empty
```

当前共有 22 个 main Java 文件、0 个 test Java 文件。四个 profile 和 benchmark
measurement 参数全部位于 main resources。

## 4. 主要发现

### P1：production 与 evidence 没有 source-set 边界

`SimulationBenchmark`、`SimulationVerification`、`JvmMetrics`、完整 AoS
`ReferenceSimulation`、`SimulationRuntimeChecks` 和负路径验证均处于
`src/main/java`。Maven 默认 production JAR 会把这些类型视为产品实现。

当前 POM 声明 surefire，但没有 test source；这说明应用以 executable main class
替代了正式 test/evidence 边界。

### P1：缺少 canonical application facade

`SimulationApplication` 直接执行：

```text
Config.load
  -> InitialStateGenerator.generate
  -> SimulationRuntimeBootstrap.load
  -> new SimulationEngine(runtime).run
  -> SimulationValidator.validate(config, runtime, result)
  -> runtime.close
```

普通使用者必须理解 runtime bootstrap、engine、live validation 和 close 顺序。
`SimulationResult` 位于 runtime package，Validator 仍依赖 live Runtime；结果边界
没有真正从运行期状态分离。

### P2：共同变化原因集中

- `SimulationConfig` 同时负责文件定位、properties 合并、全部 key、类型解析、
  领域校验、benchmark 参数、canonical text 和 checksum；
- `SimulationEngine` 同时负责 system 编排、五个 system、scratch、trace、
  summary、checksum 和 result assembly；
- `SimulationRuntime` 同时负责 aggregate ownership、业务读取、materialization、
  IndexSnapshot 示例和 benchmark stats；
- `SimulationValidator` 同时负责 production invariant、AoS 对比和 invalid-input
  test；
- `SimulationRuntimeBootstrap` 同时承担 plan、table create、projection 和 projection
  verification。

这些类型当前仍可维护，但已经存在不同原因一起变化的趋势。

### P2：命名和 Gate 尚未表达正式架构

`state` 实际只承载 annotation schema，而 live state 还包含 grass grid、tick 和
scratch；名称会把 schema declaration 与 runtime state 混为一谈。

专项 Gate 当前保护 config/generator 不依赖 SOMA runtime、四 profile、oracle、
long-run 和 multi-fork，但没有 fail-closed 验证：

- production/test package shape；
- production package DAG；
- production JAR purity；
- benchmark 参数不进入 production config；
- retired package/type identity 不恢复。

## 5. 当前判定

| 维度 | 判定 |
|---|---|
| 领域能力 | 成熟且 evidenced |
| SOMA 使用覆盖 | 成熟且 evidenced |
| 确定性与数值边界 | 设计良好 |
| canonical API | 不完整 |
| source-set / JAR purity | 不合格 |
| package responsibility | 可运行但仍有 prototype 味道 |
| Gate 的架构保护 | 不足 |

结论：值得进行独立架构治理。治理收益高于风险，但必须用 checksum、逐 tick AoS、
order independence 和多 fork evidence 保护数值与性能语义。
