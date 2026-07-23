# 目标架构候选

类型：Temporary

状态：active

Owner：grassing-individual-simulation target application architecture

事实范围：候选 vocabulary、production journey、责任边界、依赖方向和 lifecycle 设计

非事实范围：已经实现的 API、正式应用 Design、SOMA 产品语义和最终性能结论

最后审查日期：2026-07-23

## 1. 设计原则

- 使用仿真领域词汇，不机械复制调度应用的 Problem/Solver；
- canonical facade 隐藏 SOMA aggregate 和 lifecycle；
- Engine 只编排，System 拥有单一领域过程；
- Runtime 只拥有 live aggregate、primitive world 和 scratch；
- Result 默认轻量 detached，完整状态导出必须显式付费；
- evidence 只验证 production contract，不反向进入 production；
- 不因拆分类而增加逐 tick 分配、Java Stream 或对象图 materialization。

## 2. 候选 production journey

```text
SimulationConfigLoader
  -> SimulationConfig
  -> SimulationScenarioFactory
  -> immutable SimulationScenario
  -> Simulator
       -> SimulationSession
       -> SimulationEngine
       -> ordered Systems
       -> SimulationRuntime
  -> detached SimulationResult
```

推荐 vocabulary：

| 概念 | 候选名称 | 责任 |
|---|---|---|
| 生效参数 | `SimulationConfig` | immutable typed domain/run parameters |
| 配置装载 | `SimulationConfigLoader` | properties、override、strict key、canonical text |
| 初始场景 | `SimulationScenario` | config + detached grass/population + input checksum |
| 场景工厂 | `SimulationScenarioFactory` | 创建 Scenario 的接口 |
| 合成工厂 | `SyntheticSimulationScenarioFactory` | deterministic synthetic generation |
| 普通入口 | `Simulator` / `SomaSimulator` | `run(scenario)` |
| 逐 tick 入口 | `SimulationSession` | one-shot prepare/step/finish/close |
| 内部编排 | `SimulationEngine` | 固定 system 顺序，不拥有 application facade |
| 结果 | `SimulationResult` | detached summary、checksum、diagnostics |
| 可选完整输出 | `SimulationSnapshot` | 显式请求、数组化、带预算或输出策略 |

Stage 1 必须最终裁决这些名称和最小 public surface；当前文档不授权直接生成新 API。

## 3. 候选 package DAG

```text
application
  -> config
  -> scenario
  -> simulation facade
       -> engine
            -> system
            -> runtime
                 -> schema
       -> result

test fixture / oracle / verification / benchmark
  -> production contracts
```

建议 production concern：

- `application`：composition root 和 CLI rendering；
- `config`：loader 与 immutable config；
- `scenario`：detached input、top-level individual seed、factory；
- `simulation`：Simulator/Session facade；
- `engine`：one-shot tick orchestrator 和 result assembly；
- `system`：growth、metabolism、reproduction、grassing、searching、trace；
- `runtime`：aggregate、factory、projection、projection verification；
- `schema`：annotation schema 和 generated package；
- `result`：Result、Summary、Diagnostics、可选 Snapshot；
- `support`：deterministic random 和 stable hash。

这仍是一个 Maven child。Package 分责不等于为每个 concern 创建 module、interface 或
对象层；内部 type 优先 package-private。

## 4. Session 与 Result lifecycle

普通调用：

```java
SimulationResult result = simulator.run(scenario);
```

逐 tick 或测量调用：

```java
try (SimulationSession session = simulator.prepare(scenario)) {
  while (session.hasNextTick()) {
    session.step();
  }
  SimulationResult result = session.finish();
}
```

候选状态机：

```text
READY -> RUNNING -> FINISHED -> CLOSED
          \ failure -> CLOSED
```

Session 是唯一 runtime owner。成功、失败和显式关闭都必须 release aggregate；
`SimulationRuntime` 不进入普通调用者 surface。

默认 `SimulationResult` 保存：

- config/input/result checksum；
- ticks、population、maximum population；
- mode、birth/death、grass/energy summary；
- schema/runtime-plan identity 和轻量 diagnostics。

Result 不保存 Table、Cursor、Index、IndexSnapshot、ColumnView、Batch 或 mutable
schema record。

完整最终状态不能默认复制。若 Blueprint 确认需要，则由显式
`SnapshotPolicy`/budget 创建 primitive-array `SimulationSnapshot`；large/long-run
必须能够选择不产生 Snapshot。

## 5. Engine 与 System

`SimulationEngine` 只负责：

1. 推进 tick；
2. 按固定顺序调用 System；
3. 更新 maximum population；
4. 在配置边界记录 trace；
5. 在 finish 时调用 Result assembler。

候选 System：

- `GrassGrowthSystem`
- `MetabolismSystem`
- `ReproductionSystem`
- `GrassingSystem`
- `SearchingSystem`
- `TraceSystem`

System 持有或借用可复用 primitive scratch，不在每 tick 创建集合、Stream 或 DTO。
拆分后仍必须保持当前浮点累加顺序、random process kind 和 publish 顺序。

## 6. Runtime 与 Schema

Runtime 候选拆分：

- `SimulationRuntimeFactory`：plan/table create 和失败关闭；
- `RuntimeProjector`：Scenario 到 tables/grass 的一次性投影；
- `RuntimeProjectionVerifier`：投影完成后的 current-index/column invariant；
- `SimulationRuntime`：唯一 aggregate、world 和 scratch owner；
- test-only access：materialization、IndexSnapshot 负路径和 TableStats evidence。

`state` 候选原子重命名为 `schema`。该变更只改变 application-owned schema identity；
Table、字段、key、exact index、capacity 和 Access Model 语义必须保持不变，并通过
clean/repeat schema/generated reproducibility 验证。

Grass grid 继续是 `double[]`，因为它是固定尺寸、坐标寻址、整列遍历的 primitive
field；治理不为扩大 SOMA 使用面而建表。

## 7. Source-set

production resources 只保留正式可运行的 default 配置。以下内容进入 test：

- correctness、large、long-run profile；
- benchmark warmup/fork/measurement config；
- AoS reference implementation；
- invalid-input、lifecycle、IndexSnapshot 和 order-independence checks；
- verification、benchmark 和 JVM metrics；
- test-only runtime/materialization access。

专项 Gate 使用 `target/test-classes` 执行 evidence，但 ordinary production runtime
classpath 和 production JAR 不包含 test implementation。

## 8. Stage 1 必须裁决

- `SimulationScenario` 是否合并现有 Config 与 InitialState，还是只拥有 InitialState；
- facade 最终采用 `Simulator` 还是 `SimulationRunner`；
- stepwise Session 是否属于 canonical production API；
- Result 的最小稳定字段和 optional Snapshot policy；
- production invariant checker 与 test oracle 的精确分界；
- System 是独立 package-private type，还是少量保持在 Engine 内；
- application-owned schema identity 重命名策略及 checksum/golden 迁移。

这些裁决完成前不得开始批量 package move。
