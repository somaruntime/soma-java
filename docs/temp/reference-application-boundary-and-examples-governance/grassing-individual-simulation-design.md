# 个体生态仿真参考应用详细设计

类型：Temporary

状态：active

Owner：个体生态仿真参考应用候选设计

事实范围：grasser–grass 应用的领域模型、确定性、SOMA projection、system order、正确性和 evidence 要求

非事实范围：SOMA ECS 能力、Artemis-odb 兼容性、精确 generated API 或最终性能结论

最后审查日期：2026-07-23

参考来源：`https://git.ufz.de/oesa/ecs-tutorial`

## 1. 应用目标与边界

应用独立实现一个二维 grass field 上的个体仿真：

- grass logistic growth；
- grasser metabolism；
- stochastic reproduction；
- grassing；
- searching/random walk；
- birth、death 和 behaviour transition。

参考项目使用 Artemis-odb 解释 ECS；本应用只借鉴领域模型和可视效果，不复制其代码、Java 11 构建或 ECS API。应用不得向 SOMA 引入 `World`、Component composition、Aspect subscription、System scheduling 或 injection。

## 2. Canonical data shape

默认 SOMA projection 是一张 stable-keyed `GrasserState`，而不是 Position/Energy/Movement 三张 component Table：

```text
GrasserState
  grasserId
  x
  y
  energy
  mode = GRASSING | SEARCHING
  movementDirection
```

`movementDirection` 只在 searching 时有业务意义。是否为 `mode` 维护 `@SomaIndex` 必须用实际 group ratio、transition ratio 和 scan cost 裁决；canonical path 只能有一个，另一个只可作为同语义 benchmark baseline。

Grass field 使用 application-owned row-major primitive `float[]`。它固定尺寸、按坐标直接寻址，不为了扩大 SOMA 使用面强行建成 Table。Random source、system schedule、renderer 和 checkpoint 同样由 application 拥有。

## 3. 配置、初始状态生成与运行时装载

应用把初始生态系统的生成与 tick runtime 分离：

```text
SimulationConfig
  -> InitialStateGenerator
  -> SimulationInitialState
  -> SimulationRuntimeBootstrap
  -> SimulationRuntime
```

- `SimulationConfig` 从版本化 `.properties` 读取 world width/height、initial population、grass/energy distribution、模型参数、ticks、seed、trace/checkpoint 边界；
- `InitialStateGenerator` 只生成 detached grass cells 和 stable-keyed grasser initial records，不持有 SOMA facade，也不执行 system；
- `SimulationInitialState` 在装载前完成 bounds、finite、identity、capacity 校验并提供稳定 input checksum；
- `SimulationRuntimeBootstrap` 复制 grass grid 并批量装载 SOMA state；此后 runtime 不再调用 generator；
- correctness、default、large、long-run 使用独立配置；CLI 覆盖项写入最终配置摘要；
- generation determinism、bootstrap projection、tick determinism 分别测试，避免用 runtime checksum 掩盖输入漂移。

改变问题规模或初始状态只通过配置和 generator 完成，不能在 system loop 中插入测试数据分支。

## 4. 显式 system 顺序

每个 tick 固定执行：

```text
1. logistic grass growth
2. grasser metabolism and death decision
3. reproduction decision and offspring staging
4. grassing behaviour and mode transition
5. searching movement and mode transition
6. invariant/trace/render boundary as configured
```

系统顺序是应用语义，不由 SOMA runtime 调度。结构 mutation 不在 borrowed/update callback 中发生：offspring 先进入 reusable Batch，death 通过独立 Candidate terminal，system boundary 后再进入下一阶段。

## 5. 确定性

共享 `Random` 的消耗顺序会把物理遍历顺序变成模型语义，因此 canonical random 必须由稳定事实派生：

```text
random(seed, tick, grasserId, processKind, drawIndex)
```

实现可使用 application-owned 64-bit mixing 函数产生可重放的 uniform/Gaussian-like draw，但不得引入第三方依赖。相同 seed/config 必须在 swap-remove、capacity growth 和合法 source-order 差异下保持相同领域结果。

如果某一 system 需要严格 identity order，必须显式使用 key/snapshot/sort，并把成本计入 evidence；不得依赖 packed physical order。

## 6. 两阶段领域更新

Grass 与 grasser state 属于同一 simulation aggregate，但 SOMA 不提供跨结构 transaction。Canonical system 必须：

1. 在只读 traversal 中把决策和 grass delta 写入可丢弃的 primitive staging；
2. 完整验证 finite、bounds、energy、cell budget 和 offspring capacity；
3. 先提交可能失败的 SOMA operation；
4. 再执行设计为 non-throwing 的 primitive grass publish；
5. 任一 authoritative publish 后发生异常则 session fail-stop 或从 application checkpoint 恢复。

不能在 SOMA update callback 内直接修改 authoritative grass grid、创建 entity、绘图或调用另一 Table。

## 7. Access Model 覆盖

| Family | 应用使用 |
|---|---|
| Point | stable `GrasserId` lookup、边界诊断 |
| Candidate | packed/group source、filter、update、death remove |
| Column | position/energy/mode hot traversal、renderer/trace |
| Key | deterministic state checksum/export |
| Bulk | initial population、offspring append、optional trace |
| Snapshot/materialization | 同步 validator、final summary |

该应用不为覆盖 Ownership 而虚构 child；Ownership 由 core fixture 和调度应用证明。

## 8. Oracle 与 invariant

至少验证：

- grass cell finite 且在业务上下界内；
- grasser position 在 world bounds；
- energy、direction finite；
- mode 与 movementDirection consumption 一致；
- identity 唯一，birth id checked monotonic；
- dead grasser 不再参与后续 system；
- offspring energy 与 parent split 守恒规则；
- grass consumption 不超出 staged cell budget；
- 同 config/seed/ticks 输出 deterministic checksum；
- allocation/structural change 不改变领域结果。

小规模提供独立 AoS oracle；large/long-run 使用 population、grass total、energy total、mode counts、birth/death counts 和 stable-key checksum。

## 9. 运行与输出

- headless CLI 是 correctness 和 benchmark authority；
- Java 8 Swing renderer 可选，只消费 read-only column/snapshot boundary；
- renderer 不改变 tick、random 或 mutation 顺序；
- trace 默认按周期采样，不在每个 primitive update 后 materialize；
- 命令以配置文件为 canonical input，并支持显式覆盖 world size、initial population、ticks、seed 和 output；
- 不使用 Java Stream hot path。

## 10. Evidence

至少提供：

- tiny exact oracle；
- deterministic repeat、capacity-growth 和 swap-remove tests；
- negative numeric/config tests；
- artifact-isolated clean/repeat build；
- versioned correctness/default/large/long-running 配置、input checksum 与 runtime checksum；
- warmup + 多 fork tick throughput；
- allocation、GC、high-water、population churn；
- correctness checksum；
- 默认 `claimAllowed=false`。
