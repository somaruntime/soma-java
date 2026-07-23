# 个体生态仿真参考应用架构详细设计

类型：Temporary

状态：accepted for implementation

Owner：grassing-individual-simulation application architecture governance

事实范围：本专题的 production API、职责、依赖、生命周期、迁移与验证设计

非事实范围：SOMA 产品语义、尚未完成的实现事实、跨环境性能结论和发布准备度

最后审查日期：2026-07-23

## 1. 设计裁决

本专题采用唯一 production journey：

```text
SimulationConfigLoader
  -> SimulationConfig
  -> SimulationScenarioFactory
  -> SimulationScenario
  -> Simulator
       -> SimulationSession
  -> SimulationResult
```

普通调用者不接触 Engine、System、Runtime、generated Table 或其 lifecycle。逐 tick
控制是仿真应用的自然能力，因此 `SimulationSession` 属于 canonical API，而不是
benchmark 专用旁路。

`SimulationScenario` 同时拥有生效 Config 和 detached initial state，消除
Config/InitialState 可被错误配对的状态。`Simulator` 是稳定角色名；
`SomaSimulator` 是 SOMA-backed production implementation。

本轮不引入 `SimulationSnapshot`。当前 Blueprint 只要求 detached summary Result，
而完整 world 导出会建立新的长期 API 和显著分配成本。若未来出现真实 consumer
需求，必须另行设计显式 budget/policy；不得把默认全量物化作为本轮的隐藏完成项。

## 2. Production surface

### 2.1 Config

`SimulationConfig` 是不可变的领域与运行参数值对象：

- 保留当前 config/generator version、seed、world、population、ticks、grass、
  energy、reproduction、grassing、searching 和 trace 参数；
- 提供 typed accessor、领域校验、canonical text 和 config checksum；
- 不负责资源定位、Properties 解析、override 合并或 benchmark 参数。

`SimulationConfigLoader` 负责：

- 从显式资源或文件选择器读取配置；
- strict key、override 和类型解析；
- 创建并验证 `SimulationConfig`；
- 不读取 benchmark warmup/forks/measurements。

benchmark 参数由 test source-set 的 `BenchmarkOptions` 独立装载。由于责任分离会
一次性改变 config canonical text/checksum，Gate 不要求它与旧值相等；但同一输入
的 checksum 必须 clean/repeat 稳定。input/result checksum 必须与治理前逐位一致。

### 2.2 Scenario

`SimulationScenario` 是不可变、detached 的单次仿真输入：

- 拥有同一个 `SimulationConfig`；
- 拥有 row-major grass primitive array；
- 拥有只读的 `IndividualSeed` 序列；
- 保存与治理前算法完全相同的 input checksum；
- 构造时完成 defensive copy，访问时不泄露 mutable backing storage。

`IndividualSeed` 取代 nested `SimulationInitialState.IndividualInput`。字段、排序、
位表示和 stable ID 语义不变。

`SimulationScenarioFactory` 只定义：

```java
SimulationScenario create(SimulationConfig config);
```

`SyntheticSimulationScenarioFactory` 使用现有 generator version 和
`DeterministicRandom` 生成 Scenario。Factory 不依赖 SOMA runtime/generated，
也不持有 live state。

### 2.3 Simulator 与 Session

`Simulator` 定义：

```java
SimulationResult run(SimulationScenario scenario);
SimulationSession prepare(SimulationScenario scenario);
```

`run` 必须通过 `prepare` 的同一 production path 完成，不能维护第二套语义。

`SimulationSession` 是 `AutoCloseable` 的 one-shot runtime owner：

```text
READY --step--> RUNNING --last step/finish--> FINISHED --close--> CLOSED
READY --finish--> FINISHED
READY/RUNNING --failure--> CLOSED
```

- `hasNextTick()` 只观察状态；
- `step()` 最多推进一个 tick，超过配置 tick 或 close 后失败；
- `currentResult()` 返回当时的 detached summary，不关闭 Session；
- `finish()` 运行剩余 tick、返回最终 detached Result，并关闭 live Runtime；
- `close()` 幂等释放资源；
- 任意运行异常必须 fail-stop 关闭 Runtime，Session 不可复用。

`SomaSimulator` 是 composition boundary；runtime factory、projection 和 engine 都
是 implementation detail。

### 2.4 Result

`SimulationResult` 位于 `result` package，全部字段私有且不可变，至少包含：

- completed ticks、population、maximum population；
- grass/energy、birth/death 和 mode summary；
- config/input/result checksum；
- `SimulationDiagnostics`。

`SimulationDiagnostics` 只包含 detached、轻量的 schema hash、runtime-plan hash、
capacity/growth 等运行标识。它不持有 Table、Index、IndexSnapshot、Cursor、
ColumnView、Batch、record 或 mutable array。

Result 在 `finish()`/`close()` 后必须可独立读取。Result checksum 的输入和顺序与
治理前完全一致；Diagnostics 不参与领域 result checksum。

## 3. 内部职责与依赖

### 3.1 Package DAG

允许的 production 依赖方向：

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

`runtime` 内部按类型区分 engine/system/projection 责任，不为制造目录对称而增加
public interface。内部类型优先 package-private。任何 production package 都不得
依赖 test/evidence/oracle。

### 3.2 Engine 与 System

`SimulationEngine` 只拥有 tick、maximum population、固定编排和 Result assembly。
每个过程由独立、package-private 的 System type 拥有：

```text
GrassGrowthSystem
MetabolismSystem
ReproductionSystem
GrassingSystem
SearchingSystem
TraceRecorder
```

规范顺序固定为：

```text
growth -> metabolism/death -> reproduction
       -> grassing -> searching -> trace
```

拆分只移动现有语句，不改变：

- candidate 访问、sort、update/remove 或 batch publish 顺序；
- 浮点表达式和累加顺序；
- random process kind、identity、tick 和 draw index；
- stable child ID 分配；
- scratch array 复用。

System 不在逐 tick hot path 创建 Stream、Collection、DTO 或 materialized record
graph。`TraceRecorder` 继续使用 SOMA trace Table，不把 trace 变成 Java List live
storage。

### 3.3 Runtime 与投影

- `SimulationRuntimeFactory`：创建 plan/table、调用 projector/verifier、失败关闭；
- `RuntimeProjector`：把 Scenario 一次性投影到 grass primitive world 和 Tables；
- `RuntimeProjectionVerifier`：只验证投影完成时的结构 invariant；
- `SimulationRuntime`：唯一 aggregate、grass、scratch 和 live table owner；
- `SimulationEngine`/Systems：借用 Runtime，但不拥有关闭权；
- `SimulationSession`：唯一 lifecycle owner。

materialization、IndexSnapshot 负路径、TableStats 和内部状态读取进入 test-only
`SimulationRuntimeTestAccess`，不进入 production surface。

### 3.4 Schema identity

应用 annotation 声明从 `state` 原子迁移到 `schema`：

```text
com.hgtech.soma.examples.grassing.state
  -> com.hgtech.soma.examples.grassing.schema

com.hgtech.soma.examples.grassing.state.generated
  -> com.hgtech.soma.examples.grassing.schema.generated
```

schema name 改为 `grassing_individual_simulation_schema`。这是 application-owned
identity 的 clean cutover，预期 schema/runtime-plan hash 改变；字段、key、exact
index、capacity 和 Access Model 语义不得改变。旧 package/type 不保留兼容壳。

## 4. Source-set 与配置

`src/main` 只保留 production：

- Application、Config/Loader、Scenario/Factory；
- Simulator/Session、Engine/System、Runtime/Schema/Result、Support；
- `config/default.properties`。

`src/test` 拥有：

- correctness、large、long-run profile；
- `benchmark/default.properties`；
- `BenchmarkOptions`、JvmMetrics 和 multi-fork benchmark；
- AoS oracle、verification、negative checks、order-independence fixture；
- test-only runtime access。

测试可以访问 production contract，但 production compile/runtime classpath 和 JAR
不得包含 test implementation。配置选择器仍支持显式文件，以便 ordinary consumer
在不依赖 test resources 时自定义问题规模和初始状态。

## 5. Evidence 与 identity 规则

实施 candidate 必须同时证明：

- 四 profile 的 input/result checksum 确定性；
- correctness 每 tick AoS 位级等价；
- initial-order independence；
- churn/invariant 和 2,000-tick long-run；
- lifecycle/IndexSnapshot/invalid-input 负路径；
- 三个独立 JVM fork 的 checksum、allocation、GC 和 runtime high-water；
- isolated local repository、clean/repeat manifest、Java major 52；
- generated/schema clean/repeat reproducibility；
- production JAR purity、package DAG 和 retired identity fail-closed。

Identity 迁移规则：

| Identity | 裁决 |
|---|---|
| domain input checksum | 必须与治理前相同 |
| domain result checksum | 必须与治理前相同 |
| config checksum | 移除 benchmark keys 后建立新稳定值 |
| schema/runtime-plan hash | schema clean cutover 后建立新稳定值 |
| random addressing | 必须逐位不变 |

所有本机 evidence 继续标记 `claimAllowed=false`；单次 wall-clock 不能形成性能结论。

## 6. 实施切片

1. 建立 Config/Scenario/Simulator/Session/Result 最终边界，并让 Application 只走
   facade；旧 Runtime 先作为内部实现复用；
2. 拆 Config loader、runtime factory/projector/verifier、Engine/System，并原子迁移
   Schema；
3. 把 oracle/verification/benchmark/checks 和非 default 资源迁入 test source-set；
4. 强化专项 Gate，形成 immutable implementation candidate；
5. 完整验证后原子更新 Blueprint/Design/Implementation Map/Conformance/Report，
   删除 Temporary。

每个切片提交时必须可独立保留并通过相称 Gate。禁止建立临时 public adapter、
双 canonical path 或“后续再清理”的并行架构。

## 7. Scope non-regression

本设计只重构 reference application 的边界和内部责任：

- 不修改 SOMA core module；
- 不改变 annotation Schema 语义、Access Model、runtime protocol 或 Index lifecycle；
- 不删除任何领域过程、场景目标或 evidence lane；
- 不降低性能 Gate；
- 不将测试便利 API 暴露为 production contract；
- 不处理工业动态调度应用、release 或 G6。

因此，Stage 2–4 只能是本设计的 additive completion 和 internal refinement；若实现
要求突破上述边界，必须停止。
