# SOMA Grassing 空间个体仿真 Reference Application 与性能治理

类型：Temporary / Bounded Governance Topic / Candidate Design

状态：`ACTIVE_DESIGN / SELF_REVIEW_PASS / OWNER_APPROVAL_REQUIRED / IMPLEMENTATION_NOT_STARTED / NOT_FORMAL_DESIGN`

日期：2026-08-12

正式事实源：否

Owner：`simulation` reference application 的候选业务模型、SOMA state mapping、
UI/headless 边界、目录架构、Application/SOMA 分阶段优化路径、迁移范围与资格标准

> 本文已完成 Codex 自主设计审核，但尚未获得 Product Owner implementation authorization。
> 当前不授权 implementation、SOMA runtime/processor 修改、public/generated API 变更、
> 新 production dependency 或性能宣称。实施前必须由 Product Owner 审核本文并明确授权。

## 1. 为什么进行本次治理

当前 `soma-examples/simulation` 只使用 `Event` 与 `EntityState` 两张 Table，处理三个
离散事件。它能证明显式事件顺序、detached decision、point update 和 remove，
但不能代表一个严肃的空间个体仿真 application，也无法同时检验：

- 大量长期存活个体的列式运行时状态；
- 大量高频 Field 更新与周期性结构变更；
- 高频 `cellId` Index membership 迁移与分布变化；
- Table、Field、IndexSelection、GroupBy 与两 Table Relation 的组合；
- 仿真内核、统计、UI 与 headless 性能测量的边界；
- 相同 seed/config 下的 deterministic result 与不变量。

本次治理将其替换为一个基于 SOMA 的草地—个体空间仿真项目。它使用离散 tick
推进模型状态，同时允许个体在二维空间中使用连续坐标。模型意图
参考 [OESA ECS Tutorial](https://git.ufz.de/oesa/ecs-tutorial)公开描述的 grassing
individual-based model，但从本 SOMA Candidate Design 独立实现，不复制 Artemis-ODB
代码、ECS runtime 或原 UI source。

## 2. 治理意图

本次治理首先是对 SOMA 的 downstream 验证和优化反馈环，其次才是可视化示例：

> 先使用现有 public/generated API 建立一个可重放、可观察、可 headless 测量的
> 严肃空间个体仿真项目；再依次优化 application 使用方式和 profile 已证明属于
> SOMA 的通用成本，用真实的多 Table 状态和高频更新同时提升 Example 与 SOMA。

交付目标：

1. 替换当前三事件 demo，不并行保留两套 `simulation` 叙事；
2. 实现草生长、代谢、繁殖、进食、搜索和随机游走的完整 tick loop；
3. 提供 Swing/AWT UI，形成草量绿色深浅、进食个体和搜索个体的动态画面；
4. 提供真正的 headless 路径，关闭 UI 后不创建窗口、EDT、frame snapshot
   或 pacing sleep，并能在没有显示设备的 server/container/CI 环境完整运行；
5. 提供确定性 fingerprint、统计、结果校验、测试与同步 benchmark；
6. 先在 application 边界内完成 A/B 优化，不用 shadow-state workaround 掩盖问题；
7. application 合理路径耗尽后，再以 profile 识别 SOMA 的通用漏洞和优化机会；
8. 最终交付高质量、高性能的 Example，并沉淀后续 Example 可复用的治理范式。

## 3. 硬边界与 stop rule

### 3.1 SOMA 修改准入线

本专题默认只能修改：

- `soma-examples/simulation` application source、test、configuration 与 README；
- 与新场景直接对应的 `benchmarks` simulation workload；
- `soma-examples/README.md`、qualification route 与当前 Conformance projection；
- 本 Temporary 专题及其后续正式 Conformance 记录。

第一、第二阶段不得修改：

- `soma-runtime` 和 `soma-processor`；
- SOMA public/generated API、Blueprint 或 Design 产品语义；
- SOMA 存储、Index、IR、optimizer、execution 或 compression 算法；
- production artifact topology 或 dependency baseline。

SOMA 是本专题的被测产品，不是 Example 的可随意调整实现细节。实施开始、Application
优化结束和专题关闭时，必须对 `soma-runtime`、`soma-processor`、正式 Blueprint/Design
及 generated public surface 做 scope-diff 核对。前两阶段这些范围必须保持零变更。
“Example 暂时需要”不构成修改 SOMA 的授权。

如果现有 API 无法自然表达某个正常仿真步骤：

```text
先核对本 Candidate Design
    -> 排除 application 建模错误
        -> 尝试现有 public/generated API 的直接组合
            -> 仍无法成立时保留最小复现与 profile 证据
                -> 停止本专题并请求 Product Owner 裁决
```

不得为了使 Example 通过或 benchmark 变快而在 SOMA 中增加特例 API、场景分支、hidden
mode、application-specific Index 或无正式 Design 依据的快速路径。

### 3.2 三阶段治理路径

```text
Stage 1  建立高质量 Example
    -> 模型、Owner、目录、UI/headless、正确性与初始性能基线闭合

Stage 2  Application 优化
    -> 只调整公开 API 的组合、数据访问、scratch、统计和 snapshot 策略
    -> 每项改动保持相同 fingerprint，并形成 before/after

Stage 3  条件式 SOMA 优化
    -> 仅在 Application 合理路径已经耗尽后启动
    -> profile 证明主要剩余成本属于 SOMA runtime/processor
    -> 只实施可推广、符合正式 Design、没有场景分支的优化
```

Stage 3 不是强制产生 SOMA code diff。若主要成本属于业务模型、JVM、Swing、随机数或
不可避免的数据搬运，则以已解释边界关闭。一个 SOMA candidate 只有同时满足以下条件才可实施：

1. 由正常 public/generated API 的可重放 workload 触发，不依赖误用或异常输入；
2. 已用 Application A/B 排除更简单的上层优化，且不能靠权威 shadow state 获得；
3. JFR/async-profiler、allocation、managed-memory 或 phase timing 至少一种证据明确定位 Owner；
4. 优化可服务相同 operator/data-shape 的其他场景，不以 `simulation` 名称或 schema 分支生效；
5. 不改变 Blueprint、public/generated API、结果、顺序、失败、资源或 publication 语义；
6. 有 reference differential、targeted regression、既有性能前沿和其他 Example 防退化证据。

若 candidate 需要改变产品语义、正式 Design、public/generated API、新 dependency 或 artifact，
必须停止本专题，建立独立 bounded governance topic 并等待 Product Owner 裁决。

### 3.3 非目标

本次不建设：

- 通用 ECS framework、dynamic Component registry 或 System plugin SPI；
- 通用仿真 framework、event bus、workflow engine 或 distributed simulation；
- 科学级生态模型校准或现实生态预测；
- persistence、checkpoint/restart、network UI 或 remote control；
- JavaFX、game engine、chart library 或其他新 dependency；
- 为提高测得的速度而长期维护一份与 SOMA Table 平行的 shadow state；
- 未经 fixed-host benchmark/profile 证明的绝对性能或跨硬件 SLA。

### 3.4 后续 Example 的复用范式

本专题同时建立后续 SOMA Example 的最小治理模板：

```text
一个有代表性的真实场景
    -> 一个工程化、可独立理解和运行的高质量 Example
        -> Application 层 profile 与最佳使用方式优化
            -> 证据充分时才进入 SOMA 通用优化
                -> 更好的 SOMA + 更好的 Example + 可重放证据
```

这里的顺序是准入关系，不是形式化文档流程。后续 Example 可以采用不同的领域模型、目录和
验证方法，但不得跳过以下原则：先让场景和 application 正确；先消除 application 自己拥有的
浪费；SOMA 变更必须由通用 runtime evidence 驱动；最终同时交付用户可学习的项目与可供产品
治理复用的性能、正确性证据。不得把 Example 变成专门迎合 benchmark 的微型夹具，也不得把
一次场景需求直接晋升为 SOMA capability。

## 4. 产品叙事与所有权

```text
Configuration / model parameters
        -> application initializes SOMA runtime state
            -> application executes a fixed process schedule
                -> SOMA owns each Table-local authoritative transition
                    -> application derives statistics or detached snapshot
                        -> console and optional UI observe the tick boundary
```

所有权分配：

| Owner | 责任 |
|---|---|
| Application | 生态模型参数、simulation clock、process order、random semantics、停止条件、UI pacing |
| SOMA Group | `GrassCellState` 与 `GrasserState` 两张 Table 的状态隔离和 operation serialization |
| SOMA Table | 每次 Table-local update/add/remove 的 authoritative state 与 atomic publication |
| Presentation | 只读 detached snapshot，不直接读写 Table，不消费仿真 RNG |
| Validation | 校验结果、不变量与 fingerprint，不修正仿真状态 |

SOMA V1 不提供 cross-Table transaction。同一 process 中对草地和个体的两次发布
是 application-owned protocol。异常时第一版采用 fail-stop：停止 run、报告失败、不继续消费
random stream，不声称整个 tick 可回滚。

## 5. 业务模型

### 5.1 World

默认参考模型：

- `100 x 100` grass cells；
- `1,000` initial grassers；
- initial grass `0.8f`；
- initial/satiation grasser energy `1.0f`；
- grass logistic growth rate `0.01f`；
- per-tick metabolism `0.02f`；
- reproduction probability `0.05f`；
- grazing consumption `0.05f`；
- 只有扣除一次 `0.05f` 进食后 grass 仍高于 `0.1f` 才能进食，否则开始搜索；
- searching grasser 在当前 grass 达到 `0.2f` 时恢复进食；
- random-walk speed `0.25f`，direction standard deviation `30°`；
- fixed seed，相同配置必须可重放。

空间是有边界的二维连续平面。草地是规则网格，`cellId = y * width + x`。
这里的 `x/y` 是从零开始的整数 cell coordinate；由连续位置换算时使用非负值上的
`(int) x / (int) y`。个体碰到边界时反向，不使用 toroidal wrap。

初始 `cellId` 为 `0..width*height-1`，初始 `grasserId` 为
`0..initialGrassers-1`；这也自然覆盖 zero Key 合法合同。每个初始个体的位置分别由
`nextFloat() * width/height` 生成，energy 为 `1.0f`、`searching=false`、direction 为
`0.0f`。非 searching 状态下 direction 没有行为含义，但仍保持确定的零值。

### 5.2 Process schedule

每个 tick 固定执行：

```text
1. GrassGrowth
2. Metabolism and death
3. Reproduction
4. Grazing or switch to searching
5. Searching, movement or switch to grazing
6. Optional statistics and visualization snapshot
```

这个顺序是模型语义，不交给 optimizer 重排。每个 process 内部的 SOMA query/terminal
可按现有合同规划与执行，但不得跨 process 改变可观察状态转移顺序。

### 5.3 Determinism

- 一个 application-owned `java.util.Random` 拥有全部模型随机性；
- initialization 与 tick processing 使用同一 random sequence；
- UI、statistics、validation 绝不消费 RNG；
- 任何会影响 RNG 消费顺序的个体遍历，都先按 `grasserId` 显式排序；
- Table packed remove 或 canonical order 变化不得改变结果；
- UI/headless 和不同 render/statistics interval 不得改变最终 fingerprint。

确定性合同限定为同一 Java 8 runtime、同一配置和同一执行模式下的 bit-identical replay；
不在本专题承诺不同 CPU/JDK 对三角函数和浮点实现的跨平台 bit identity。

## 6. SOMA Scheme

### 6.1 GrassCellState

```java
@SomaTable(defaultCapacity = 16_384)
final class GrassCellState {
    @SomaKey int cellId;
    @SomaField float grass;
}
```

`cellId` 是 stable spatial identity。`x/y` 由 `cellId` 和 world width 计算，不重复存储。

### 6.2 GrasserState

```java
@SomaTable(defaultCapacity = 4_096)
final class GrasserState {
    @SomaKey int grasserId;
    @SomaIndex int cellId;

    @SomaField boolean searching;
    @SomaField float x;
    @SomaField float y;
    @SomaField float energy;
    @SomaField float direction;
}
```

`cellId` 是个体当前所属的离散 cell membership，必须始终等于
`floor(y) * width + floor(x)`。它是有意识的派生访问路径，用于 point grass lookup、
occupancy GroupBy 和两 Table Equality Join。移动时 `x/y/cellId` 在同一
`GrasserState` point update 中发布。

`direction` 使用弧度，规范化到 `[0, 2π)`；只有 `searching=true` 时参与行为和绘制。
它不是第三种状态，也不用于建立 Index。

### 6.3 两态行为的正式裁决

第一版使用 `boolean searching`，不使用 `GrasserBehavior` Enum 或裸 `int`：

- 模型中只有 grazing/searching 两个合法状态；
- `searching` 在 application API 中有明确语义，`false` 自然表示 grazing；
- boolean 是 SOMA exact primitive leaf，非 null，没有逐行 reference payload；
- `int` 会允许非法 code，却不提供额外能力或性能收益；
- 当前 SOMA Enum baseline 是 `reference/ordinal representation`，不能在本 Example
  中虚假假设它已始终 lower 为 primitive code；
- 如果未来出现第三种真实 behavior，再以 schema 变更引入 Enum，不预建占位值。

`searching` 不建 secondary Index。它只有两个大 Bucket、可能每个 tick 频繁变化，
且 grazing/searching process 本来就需要处理对应的大量个体。第一版使用
`filter(grassers.searching.eq(...))` 的 typed scan。不为了展示 Index API 而制造一个不合理
的访问路径。`cellId` Index 已能真实检验高频 membership 迁移、occupancy
分布和两 Table relation。

这是初始 Application design，不是预先宣布的性能最优解。Stage 2 必须用 profile 比较
“每 tick 两次 typed scan”的成本；若其成为主要热点，只能先在 Application 层评估减少重复
materialization或调整 process-local scratch，不能维护一份跨 tick 的 grazing/searching
权威 membership。任何新的 SOMA access path 仍按第 3.2 节准入。

### 6.4 Authoritative state boundary

- 两张 SOMA Table 是 tick boundary 上唯一 authoritative simulation state；
- 不保留与 Table 平行的长期 `float[]` grass grid 或 grasser cache；
- process 中可使用 `toArray()` 生成的短命 detached decision input；它只在一个 process 内
  存活，下一次 Table mutation 后不得继续作为 current state 使用；
- snapshot 是 UI-owned detached projection，不能回写 Table；
- statistics/fingerprint 是派生结果，不是第二份状态真相。

这个边界是为了真实检验 SOMA。如果自然 generated API 路径产生显著分配或性能成本，
应当用 profile 报告，不先用 application shadow array 绕开。

## 7. Process 实现合同

### 7.1 GrassGrowthProcess

```text
grassCells.selectAll().update(...)
```

每个 cell 执行 logistic growth，结果 clamp 到 `[0, capacity]`。这是一次 Table-local
Selection mutation，不为每个 cell 发布 point update。

公式固定为：

```text
next = grass + growthRate * grass * (capacity - grass) / capacity
```

### 7.2 MetabolismProcess

```text
grasserStates.selectAll().update(energy -= consumption)
grasserStates.filter(energy <= 0).remove()
```

死亡删除在代谢 update 完成后作为一次独立 Selection remove。记录实际 removed count。

### 7.3 ReproductionProcess

1. 按 `grasserId` 排序并 materialize tick-start parent snapshot；
2. 按稳定顺序消费 RNG；
3. 命中 reproduction 时 point update parent energy；
4. 分配严格单调、checked `int` child ID；
5. add child，位置和 `cellId` 继承 parent，parent/child energy 各为原值的一半，
   `searching=false`、`direction=0.0f`。

Offspring 不重新进入当次 reproduction snapshot，但可以参加后续 grazing process。

### 7.4 GrazingProcess

1. `filter(searching.eq(false))` 按 `grasserId` 显式排序后 materialize；
2. 通过 grasser `cellId` Key-get `GrassCellState`；
3. 若 `energy >= satiationEnergy`，本 tick 不进食也不切换状态；
4. 若 `grass - grazingConsumption > minimumGrassAfterGrazing`，从 grass 中扣除固定
   `grazingConsumption`，并给 grasser 增加同量 energy；
5. 否则 point update `searching=true`，并使用一次 `nextFloat()` 把 direction 初始化到
   `[0, 2π)`。

同 cell 多个体按 `grasserId` 顺序观察前一个体已发布的 grass 变化，因而结果确定。
这条顺序是业务语义，因此不能把这些 point operations 并行化或改写为 unordered aggregate。

### 7.5 SearchingProcess

1. `filter(searching.eq(true))` 按 `grasserId` 显式排序后 materialize；
2. 当前 cell grass 达到阈值时发布 `searching=false`；
3. 否则消费一次 Gaussian random turn，执行
   `direction += gaussian * turnStdDevRadians`，规范化后计算 candidate position；
4. 位置合法时在同一 point update 中发布 `x/y/cellId/direction`；
5. candidate 落在 world 外时位置保持不变，仅将 direction 反转 `π` 并规范化。

Grazing 使用 tick-start grazing membership；Searching 在其后重新绑定当前 Table，因此刚刚切换为
`searching=true` 的个体可以在同一个 tick 进入 Searching。刚刚恢复 grazing 的个体要到下一
tick 才会再次进食。这是固定 process schedule 的自然结果。

### 7.6 Reentrancy boundary

SOMA callback scope 内不执行同 Group 的嵌套 Table operation。需要跨 Table point access 的
reproduction/grazing/searching 先结束 query terminal，使用 detached snapshot 在 application loop
中发布后续 mutation。这是 SOMA 正常 lifecycle contract，不是 workaround。

## 8. 查询、统计与两 Table 操作

统计按配置的 interval 执行，不混入每个 process 的业务语义。至少使用：

- `grassCells.grass.sum()/average()`；
- `grasserStates.energy.sum()/average()`；
- `filter(searching.eq(true/false)).count()`；
- `groupBy(cellId).count()` 观察 occupancy；
- `grasserStates.join(grassCells).on(grasserStates.cellId, grassCells.cellId).inner()`
  验证 cell membership 并计算个体当前可见草量；
- Table/Group `_metadata()` 观察 retained 与 representation，不作业务决策。

Join count 必须等于 live grasser count。这同时验证移动后 `cellId` Index/Field 与
Grass Key 的一致性。

## 9. API 与目录架构

保留一个 Maven module，不为 UI 或 headless 新建 artifact：

```text
soma-examples/simulation/
├── README.md
├── pom.xml
├── config/
│   └── grassing.properties
└── src/
    ├── main/java/io/github/somaruntime/examples/simulation/
    │   ├── application/
    │   │   ├── SimulationMain.java
    │   │   └── SimulationApplication.java
    │   ├── configuration/
    │   │   ├── SimulationConfig.java
    │   │   └── SimulationConfigLoader.java
    │   ├── engine/api/
    │   │   ├── SimulationEngine.java
    │   │   ├── SimulationRunResult.java
    │   │   ├── SimulationStatistics.java
    │   │   └── SimulationSnapshot.java
    │   ├── engine/core/
    │   │   ├── SomaSimulationEngine.java
    │   │   ├── SimulationInitializer.java
    │   │   ├── SimulationRuntime.java
    │   │   └── process/
    │   │       ├── GrassGrowthProcess.java
    │   │       ├── MetabolismProcess.java
    │   │       ├── ReproductionProcess.java
    │   │       ├── GrazingProcess.java
    │   │       └── SearchingProcess.java
    │   ├── runtime/schema/
    │   │   ├── GrassCellState.java
    │   │   ├── GrasserState.java
    │   │   └── package-info.java
    │   ├── presentation/
    │   │   ├── console/ConsoleStatisticsReporter.java
    │   │   └── ui/
    │   │       ├── SimulationWindow.java
    │   │       └── SimulationPanel.java
    │   └── validation/
    │       └── SimulationResultValidator.java
    └── test/java/io/github/somaruntime/examples/simulation/
        ├── configuration/SimulationConfigLoaderTest.java
        ├── engine/core/SomaSimulationEngineTest.java
        ├── presentation/ui/SimulationPanelTest.java
        └── validation/SimulationResultValidatorTest.java
```

测试只复用仓库已经准入的 JUnit Jupiter `5.11.4` 与 Surefire `3.5.2` test-only stack，
不新增 production dependency。`config/grassing.properties` 是 Example 的用户可见输入，必须随
`README.md` 和 `src/main` 一同进入 source delivery allowlist 与本地 package qualification；
它不是只在开发机存在的测试资源。

不引入 `SimulationProcess` interface、process registry、observer bus、DI container 或每个类的
interface/implementation 成对。五个 process 是 package-private concrete capability，
`SomaSimulationEngine` 显式拥有它们并固定调用顺序。
本模型没有复杂的静态业务对象图；`SimulationConfig` 已拥有全部初始化参数。
因此不为目录对称建立 `GrassingModel/Factory/Validator` 三层；
`SimulationInitializer` 校验 checked capacity/parameter，然后直接将初始状态写入
SOMA Table，避免导入前构建一份完整状态副本。

### 9.1 SimulationEngine

候选 API 只固定 application 真正需要的同步控制与观察能力：

```java
public interface SimulationEngine {
    void step();
    long tick();
    SimulationStatistics statistics();
    SimulationSnapshot snapshot();
}
```

Engine 在 construction 时接收已校验的 immutable `SimulationConfig`，并由
`SimulationInitializer` 创建初始 SOMA state。`Soma.configure(...)` 是 JVM/ClassLoader 级
one-time lifecycle，不属于 Engine：`SimulationApplication` 必须在创建任何 Group/Table 前根据
process-level config 恰好配置一次，Engine、process 和 renderer 均不得重复配置。测试 JVM 使用
一个 suite-level 固定 SOMA configuration；单个 test case 可以改变模型参数，但不能把
memory/compression 当作可重复切换的 per-engine option。
`snapshot()` 是显式的 detached materialization，只有 UI 或调试路径调用。
`step()` 恰好执行一个完整 tick，成功后 `tick()` 增加一；失败则 run 进入 fail-stop，不能继续
调用 `step()`。它不把“种群归零”解释为自动停止，停止条件由 `SimulationApplication` 拥有。
Engine 不创建 background thread。UI 模式由 Application 创建一个 simulation thread；headless
模式直接在 main calling thread 执行。`SimulationRunResult` 由 Application 在正常停止或失败时，
结合 engine statistics、计时与验证结果形成，不再让 Engine 拥有第二套 run lifecycle。

## 10. Configuration

继续使用 Java 8 标准 `.properties`，不增加 YAML/TOML dependency。

```properties
model.worldWidth=100
model.worldHeight=100
model.initialGrassers=1000
model.initialGrass=0.8
model.initialEnergy=1.0
model.satiationEnergy=1.0
model.grassCapacity=1.0
model.grassGrowthRate=0.01
model.metabolism=0.02
model.reproductionProbability=0.05
model.grazingConsumption=0.05
model.minimumGrassAfterGrazing=0.1
model.resumeGrazingAt=0.2
model.walkSpeed=0.25
model.turnStdDevDegrees=30.0
model.randomSeed=0

run.maxTicks=10000
run.statisticsEveryTicks=100
run.memoryBudgetBytes=536870912
run.compression=AUTO

visualization.enabled=true
visualization.renderEveryTicks=2
visualization.cellSize=6
visualization.frameDelayMillis=10
```

CLI 允许覆盖 `--ui`、`--headless`、`--ticks=<N>` 和 `--config=<path>`，不建设通用
CLI framework。默认配置打开画面；`--headless` 明确关闭画面并进入真实性能路径，
`--ui` 可覆盖一个禁用了 visualization 的配置。Repository qualification 固定使用
`--headless`。

运行模式在任何 SOMA state、RNG 或 AWT/Swing class 初始化前完成解析。显式 `--headless`
优先于 properties 中的 `visualization.enabled=true`；显式 `--ui` 需要可用的 graphics
environment，否则 fail fast 并提示改用 `--headless`，不能静默切换模式。正式 headless
smoke 必须使用下列同形命令，证明它不是依赖本机桌面的“隐藏窗口模式”：

```text
java -Djava.awt.headless=true ... SimulationMain --headless --ticks=<N>
```

`run.memoryBudgetBytes` 与 `run.compression` 是 process-start SOMA configuration，不是 Engine
热配置。Application 必须先完成 properties/CLI merge 和全部 validation，再调用一次
`Soma.configure(...)`，随后才创建 Engine/Group/Table 或消费 RNG。configuration freeze 后的
重复配置继续遵守 SOMA `CONFIGURATION_FROZEN` 合同；Example 不捕获并绕过该失败。

Configuration construction 必须在创建 `SomaGroup` 和消费 RNG 前完成以下 validation：

- `worldWidth * worldHeight`、initial population 和未来 child ID 都在 checked `int` 结构域；
- capacity/energy/rate/probability/threshold/speed 全部 finite，且 range 合法；
- `0 <= minimumGrassAfterGrazing < resumeGrazingAt <= grassCapacity`；
- `0 < grazingConsumption < grassCapacity - minimumGrassAfterGrazing`；
- `0 < initialEnergy <= satiationEnergy`，metabolism 非负，reproduction probability 位于 `[0,1]`；
- ticks/interval/cell size/delay 合法，memory budget 为正；
- unknown property 和 unsupported CLI option fail fast，避免拼写错误静默采用默认值。

## 11. UI 与 headless 合同

### 11.1 UI

- 只使用 Java 8 Swing/AWT；
- green intensity 表示 grass quantity；
- grazing grasser 是白色方块；
- searching grasser 是按 direction 旋转的黄色/橙色三角形；
- engine 在完整 tick 边界生成 immutable detached snapshot；
- EDT 仅读 snapshot 并绘制，不访问 SOMA Table；
- `SimulationWindow` 只保留 latest snapshot，慢渲染覆盖旧帧，不无界堆积；
- close window 只设置 application stop request，不从 EDT 修改 Table；
- UI pacing 发生在 application loop，不属于 engine tick time。

`SimulationSnapshot` 使用 grass `float[]` 与 grasser primitive arrays，不对 UI 暴露 generated
View、Table 或 SOMA runtime handle。snapshot 生成期间不执行 mutation。

### 11.2 Headless

`--headless` 或 `visualization.enabled=false` 时，Example 必须能在无 `DISPLAY`、无 WindowServer
会话且 `java.awt.headless=true` 的环境中完成 initialization、全部 tick、statistics、validation
和正常退出。该模式与 UI 共用同一个 `SomaSimulationEngine` 和模型语义，只替换
presentation/run pacing。具体合同为：

- 不加载/初始化 `JFrame`、Toolkit、EDT 或 window listener；
- 不生成 `SimulationSnapshot`；
- 不创建 render buffer 或调用 `repaint`；
- 不执行 `Thread.sleep`；
- 统计只在 configured interval 和 final result 时计算；
- 报告的 kernel elapsed time 不包含 configuration、initialization、console I/O 或 validation；
- 正常完成后输出稳定 fingerprint、validation status 与核心统计，并以 exit code `0` 退出；
- 不得捕获 `HeadlessException` 后继续，也不得通过虚拟显示设备伪造资格。

Headless 是 Example 的一等公开运行方式，也是 Stage 2/3 benchmark 与 profile 的 canonical
execution mode；UI 数据不能作为 SOMA 性能结论的输入。

UI/headless 的相同 seed/config/tick count 必须得到相同 final fingerprint。
自动化测试不依赖真实屏幕、窗口焦点或人工 close；renderer 使用 off-screen image，window
lifecycle 只在非-headless 环境运行受控 smoke。CI 的逻辑等价测试由 headless engine 与显式
snapshot-on/off 两条 application path 完成。

## 12. Statistics 与结果

最终输出至少包含：

```text
ticks
kernelElapsedNanos
ticksPerSecond
initial/final grasser count
births/deaths
grazing/searching count
mean/min/max energy
mean/min/max grass
occupied cell count
SOMA retained bytes
SOMA representation bytes
deterministic state fingerprint
validation status
```

UI run 另行记录 snapshot/render/pacing time，不把它合并为 SOMA kernel time。

Fingerprint 按 stable logical identity 计算：Grass 按 `cellId`，Grasser 按 `grasserId`，float
使用 canonical `Float.floatToIntBits`。不使用 Table locator、object identity、Index Bucket order
或 UI frame 作为 fingerprint input。

空种群是正常模型结果：count/sum 为零，mean/min/max 使用明确 absence，而不是写入零值或
NaN sentinel。最终 validation 仍检查 grass state、population balance 和 fingerprint。

## 13. 正确性与测试体系

### 13.1 模型不变量

- `0 <= grass <= grassCapacity`；
- live grasser `energy > 0`；
- `0 <= x < worldWidth` 且 `0 <= y < worldHeight`；
- `cellId == floor(y) * width + floor(x)`；
- `grasserId` 唯一、非负且不溢出 checked `int`；
- `grazingCount + searchingCount == liveGrasserCount`；
- Grass/Grasser Join count 等于 liveGrasserCount；
- births/deaths 与 population balance 一致；
- `births == nextGrasserId - initialGrassers`，且
  `initialGrassers + births - deaths == liveGrasserCount`；
- UI/headless 不改变逻辑状态。

### 13.2 必须测试

1. properties parse/default/override 与 invalid config failure；
2. small fixed-seed initialization fingerprint；
3. 每个 process 的正向 transition；
4. death remove、reproduction add、behavior typed update 与 indexed `cellId` update；
5. packed remove 后确定性不受 Table order 影响；
6. same seed repeated run fingerprint；
7. UI-disabled run 不构建 snapshot/window；
8. `-Djava.awt.headless=true ... --headless` process smoke，无 AWT/EDT thread、正常退出且输出
   fingerprint/validation `PASS`；
9. off-screen `BufferedImage` renderer test，可在 headless CI 执行；
10. window close/stop request 不留下 simulation thread；
11. default reference scenario smoke 与 result validator；
12. IndexSelection、GroupBy、Join 统计与独立 application calculation 等价；
13. empty-population statistics 与 result validation；
14. SOMA metadata size/retained/representation 与业务状态一致。

测试围绕模型不变量、process narrative、SOMA lifecycle 和完整场景，不为每个
private helper 建立碎片化测试。

## 14. 性能和 profile 合同

本 Example 提供可读、可运行的 application；严格性能证据仍由 `benchmarks/`
拥有。现有 `simulation` Narrow operator benchmark 已拥有长期 baseline 和历史 Conformance，
不能被新业务 loop 静默改义。它的 scenario identity、`NARROW` shape、workload 与 result contract
继续保持 `simulation`；本专题新增独立 `simulation-application` journey，两者分开记录和比较，
不通过重命名破坏已有历史序列。

`simulation-application` 分开记录：

- initialization/Table ingest；
- grass Selection update；
- metabolism Selection update/remove；
- reproduction point update/add；
- behavior typed filter/update 与 `cellId` Index mutation；
- grazing/searching detached materialization 和 point access；
- statistics Field/Index/GroupBy/Join；
- optional snapshot materialization；
- complete headless tick loop；
- retained bytes、temporary reservation、Java allocation、heap/RSS 与 fingerprint。

规模不再复用单一 `rows` 含糊表示 cell、grasser 和 tick。Application benchmark 显式记录
`worldCells / initialGrassers / executedTicks / finalGrassers`，并至少提供：

- `smoke`：小 world、短 ticks，用于每次 qualification 的确定性与 correctness；
- `default`：`100 x 100`、`1,000` grassers 和固定 tick 数，用于端到端 before/after；
- `stress`：只通过 config 扩大 world/population/ticks，用于 profile，不进入默认快速 Gate。

首轮不设置绝对性能通过线，先建立 fixed-host baseline，并核对：

- headless 不存在 UI/sleep/snapshot 污染；
- 不存在与行数无关的意外 O(N²) 路径；
- 内存归因能区分 retained Table state、temporary reservation 和 application/UI allocation；
- 大量 add/remove/indexed update 不触发已治理问题的回归；
- UI/headless 之间只有可解释的 presentation overhead，没有 result drift。

Stage 2 不建设第二套长期存在的 raw-array/manual Grassing engine。Application candidate 通过同一
SOMA authoritative state 上的机制级 A/B 比较，既有 `simulation` kernel benchmark 继续提供
manual/JDK/SOMA 的低层对照；若 profile 临时需要最小 raw reproduction，它只能存在于
`benchmarks/` 非 production 证据中，不能进入 Example 主路径或成为第二个业务事实 Owner。

性能差不自动授权修改 SOMA。先完成可读性、正确性不变的 Application A/B；只有满足
第 3.2 节全部 admission 条件，才进入 Stage 3 通用 SOMA 优化。无法满足时交付可重放证据和
Owner 归因，不用一次未达预期的结果强行制造 code change。

## 15. 迁移和 replacement closure

### Stage 1 / Slice A — Application skeleton and model

- 建立 configuration/initialization/API/core 目录；
- 用两张新 Table 替换 `Event/EntityState`；
- 实现 fixed-seed initialization 和基本不变量；
- 删除旧 `SimulationService`、`Event`、`EntityState`、`EventType`，不留兼容层。

### Stage 1 / Slice B — Process loop and correctness

- 按固定顺序实现五个 process；
- 建立 statistics、fingerprint、result validator 和场景测试；
- 建立 deterministic repeated-run 与 Index/Join/GroupBy 证据。

### Stage 1 / Slice C — UI and headless

- 实现 detached primitive snapshot；
- 实现 latest-frame Swing renderer 和 window lifecycle；
- 证明 headless no-UI-allocation/no-sleep 与 UI/headless fingerprint 等价；
- 在无显示设备和 `-Djava.awt.headless=true` 的独立 JVM 中重放 packaged Example。

### Stage 1 / Slice D — Benchmark and repository integration

- 保留既有 Narrow workload 的历史语义，新增 `simulation-application` journey；
- 更新 Examples README、qualification route 期望输出与 current projection；`scripts/qualify.sh`
  必须显式传入 `--headless`，不能让默认 UI 进入 CI 或 repository Gate；
- 在 source delivery allowlist 中保留 `simulation/src/main` 并新增 `simulation/config`，验证配置文件
  实际进入 source bundle；
- 运行 targeted tests、Examples reactor、benchmark smoke、`scripts/check.sh`；
- 建立正式 Conformance 记录，将本 Temporary 稳定内容晋升后删除本专题。

### Stage 2 — Application profile 与优化

- 在 Stage 1 正确性 baseline 上执行 phase/allocation profile；
- 依次审查 materialization 次数、重复排序、point access、统计频率、snapshot copy 与 UI pacing；
- 一次只改变一个 application mechanism，保持相同 fingerprint/invariant；
- 同时保留小规模正确性和 default/stress before/after，拒绝只对单一运行有利的偶然优化；
- 形成 application 最佳实践和无法继续上移的剩余 hotspot 清单。

### Stage 3 — 条件式 SOMA profile 与通用优化

- 只有 Stage 2 exit 后仍存在显著 SOMA-owned hotspot 才进入；
- 先建立最小 operator/data-shape reproduction，再改 runtime；
- 每项优化从正式 Design 重新推导，不能由 Example source/type/name 触发；
- 运行 reference differential、changed-family tests、全仓 check/qualification，以及
  performance-frontier、100K FJSP、其他 reference application 防退化；
- 若收益不稳定、回归无法解释或需要产品语义变化，撤销 candidate 或暂停等待裁决。

历史 I8 Qualification 保留 Event/State 当时的 provenance，不回写为新实现证据。

## 16. Definition of Done

本专题只有同时满足以下条件才可关闭：

1. 旧 three-event source 完全退出，只剩一套 Grassing simulation narrative；
2. 两张 SOMA Table 是唯一 authoritative runtime state，无 shadow-state workaround；
3. 五个 process、deterministic schedule、fixed seed 与全部不变量成立；
4. UI 达到可观察动态仿真效果，window lifecycle 安全；
5. headless 路径能在无显示设备、`java.awt.headless=true` 的独立 JVM 中完整运行，不创建
   Toolkit/EDT/UI/snapshot/sleep，正常输出并退出，且与 UI 结果等价；
6. 正确性、determinism、SOMA lifecycle、Index/GroupBy/Join 和 renderer tests 全部通过；
7. 原 Narrow benchmark 语义和历史比较能力保留，新增 application journey 产生分阶段、内存分层
   和 fingerprint 证据且不夹带 UI overhead；
8. Stage 2 至少完成一次 profile-driven Application A/B，并记录 accepted/rejected candidate；
9. 若进入 Stage 3，每个 SOMA 变更都满足通用准入、reference differential 与跨场景防退化；
   若不进入，必须有证据说明剩余成本不属于可行动的 SOMA hotspot；
10. SOMA public/generated API、Blueprint/Design 语义、artifact 与 production dependency baseline
    未被改变；只复用既有 JUnit test-only stack；
11. Examples build、targeted tests、benchmark smoke、changed-family qualification 和 full repository
    check 通过；
12. README/current projection/Conformance 与 executable fact 一致，Temporary replacement closure 完成；
13. 修改作为干净提交推送 `develop`，不发布 Release/Package。

## 17. 自主设计审核结论

当前 Candidate Design 已关闭以下关键问题：

- 产品目标是 SOMA downstream validation，不是 ECS framework；
- 行为两态使用 `boolean searching`，不使用 Enum reference 或裸 int code；
- 权威状态是两张 SOMA Table，不建平行 shadow arrays；
- process order、RNG、cross-Table protocol 与 failure 由 application 拥有；
- UI 只消费 detached snapshot，headless 是实质性无 UI 执行路径；
- 只用 Java 8 Swing/AWT 和 `.properties`，不新增 dependency/artifact；
- Stage 1/2 不允许修改 SOMA；Stage 3 只准入 evidence-backed、Design-compatible 的通用优化；
- 既有 Narrow benchmark 不被新 application journey 静默替换；
- 旧 Example、benchmark 与 current projection 有完整 replacement closure。

本轮自主审核额外识别并关闭：

| Finding | 风险 | 关闭方式 |
|---|---|---|
| 模型语义只写到名称 | 实现者自行猜测参考模型，结果不可比较 | 固定初始化、五个 process、RNG、状态转移、tick membership 和不变量 |
| Engine 与 Application lifecycle 重叠 | `step/result/stop/thread` 出现双 Owner | Engine 只推进单 tick；Application 拥有 run、stop、thread 与 result |
| SOMA one-time configuration 未归属 | 多 Engine/test 重复配置导致 `CONFIGURATION_FROZEN` | process 启动前由 Application 恰好配置一次，测试 suite 共享固定配置 |
| 默认 UI 污染 qualification | CI 弹窗、EDT、sleep 或 hang | repository route 强制 `--headless`，renderer 使用 off-screen test |
| 新业务 benchmark 覆盖历史 `simulation` | 历史性能序列失真 | 原 identity/shape/contract 不变，新增 `simulation-application` |
| config 未进入交付 allowlist | Git 用户拿到源码却缺少默认运行输入 | 明确新增 `simulation/config` source-delivery route |
| “优化 SOMA”边界过宽 | 场景特供 patch、目标漂移 | Stage 1/2 runtime 零变更；Stage 3 使用六项通用准入和独立 stop rule |
| 为未来扩展预建 framework | Example 先演化成 ECS/仿真平台 | 拒绝 SPI、registry、event bus、DI、第三 artifact 与第二套业务 engine |

审核结论为 `SELF_REVIEW_PASS`：当前候选模型、Owner、生命周期、目录、测试、UI/headless、
benchmark、三阶段优化和 replacement closure 已经自洽，未发现必须继续设计才能实施的 blocker，
也未发现需要保留的预建抽象。唯一剩余准入是 Product Owner 对本 Candidate Design 的审核与
implementation authorization；获得授权后按 Stage 1 Slice A-D 开始，不把本 Temporary 冒充正式
Design。
