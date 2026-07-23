# 个体生态仿真应用 Blueprint

类型：应用 Blueprint

状态：当前

Owner：grassing-individual-simulation

对 SOMA 产品规范性：否

事实范围：应用目标体验、模型边界和成功标准

最后审查日期：2026-07-23

## 使用目标

使用者通过版本化配置生成可重放的 grass field 与 grasser population，再通过
统一 facade 执行完整仿真或逐 tick 控制 Session，最后获得与 live runtime 完全
分离的结果。问题规模、初始场景和运行期状态是三个明确边界：

```text
properties / explicit overrides
  -> SimulationConfigLoader
  -> immutable SimulationConfig
  -> SimulationScenarioFactory
  -> detached SimulationScenario
  -> Simulator / SimulationSession
  -> detached SimulationResult
```

修改 world、population、seed 或 tick 数不需要改变 runtime code。相同生效配置与
seed 必须产生相同 input checksum；改变初始装载的物理顺序不能改变领域结果。
Scenario Factory 不持有 live runtime；Simulator 不反向读取配置文件或生成输入。

普通调用只需要：

```java
SimulationConfig config =
    new SimulationConfigLoader().load("config/simulation.properties");
SimulationScenarioFactory factory =
    new SyntheticSimulationScenarioFactory();
SimulationScenario scenario = factory.create(config);

Simulator simulator = new SomaSimulator();
SimulationResult result = simulator.run(scenario);
```

需要逐 tick 控制时使用同一 production path：

```java
try (SimulationSession session = simulator.prepare(scenario)) {
  while (session.hasNextTick()) {
    session.step();
  }
  SimulationResult result = session.finish();
}
```

Session 是 one-shot live-state owner；失败后 fail-stop，`finish()` 或 `close()`
释放 runtime。`SimulationResult` 是关闭后仍可读取的轻量 detached summary，不
隐式复制完整 world。完整状态导出不是当前参考应用的 contract；出现真实需求时应
单独设计显式 budget/policy。

## SOMA projection

Grass grid 是固定尺寸、按坐标寻址的 application-owned `double[]`，不会为了扩大
SOMA 使用面而强行建表。长期存在、需要 stable identity、group access 和
swap-remove 的个体状态投影为：

```java
@SomaTable(name = "grasser_states", defaultCapacity = 16384)
@SomaIndex(name = "by_mode", fields = {"mode"})
public final class GrasserState {
  @SomaKey public GrasserId grasserId;
  @SomaField public int x;
  @SomaField public int y;
  @SomaField public double energy;
  @SomaField public BehaviourMode mode;
  @SomaField public int movementDirection;
}
```

Scenario 在 runtime factory 中一次性投影：

```java
GrasserStateBatch batch = new GrasserStateBatch(scenario.population());
for (IndividualSeed value : scenario.individuals()) {
  batch.addValues(new GrasserId(value.id()), value.x(), value.y(), value.energy(),
      mode(value.mode()), value.movementDirection());
}
grassers.replaceAll(batch);
```

此后 runtime 不再调用 Scenario Factory，也不持有 Factory 的可变状态。

## Tick journey

每个 tick 固定执行：

```text
1. logistic grass growth
2. metabolism and death remove
3. deterministic reproduction staging and batch append
4. grassing group update and grass publish
5. searching movement and mode transition
6. configured trace/invariant boundary
```

典型 SOMA 调用保持领域意图可读：

```java
grassers.filter(c -> c.energy() <= 0.0).remove();

grassers.scanByMode(BehaviourMode.GRASSING)
    .update(c -> {
      int cell = cell(c.x(), c.y());
      c.setEnergy(c.energy() + cellShare[cell]);
      if (cellShare[cell] < grassingAmount * 0.5) {
        c.setMode(BehaviourMode.SEARCHING);
      }
    });
```

回调内只修改当前候选；birth 先写入 reusable Batch，death 使用独立 terminal。
Grass consumption 先在 primitive scratch 中完整计算并校验，SOMA update 成功后才
发布到 authoritative grass grid。

## 确定性与边界

随机 draw 由以下稳定事实寻址：

```text
random(seed, tick, grasserId, processKind, drawIndex)
```

它不消费共享 `Random`，因此 swap-remove、capacity growth 和 packed order 不会
改变随机序列。繁殖候选显式按 stable ID 排序后分配单调 child ID；同一 cell 的
可消费草量在全部 grasser 之间等分，结果不依赖 group 内遍历顺序。

Grass 保留配置化 seed-bank floor，消费只使用 floor 以上的 biomass，避免纯
logistic 方程在精确 0 上进入不可恢复的吸收态。应用借鉴 grasser–grass 领域
目标，但不依赖 Artemis-odb，也不把 SOMA 扩展成 ECS。

## 输出

Canonical CLI 输出最终生效配置、config/input/result checksum、tick、population、
maximum population、birth/death、grass/energy 总量。Renderer 不是 correctness
或 performance authority；本参考应用当前以可重放 headless journey 为正式入口。
