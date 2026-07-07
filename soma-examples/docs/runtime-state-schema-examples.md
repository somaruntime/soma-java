# Runtime state schema 典型示例

状态：正式设计文档
日期：2026-07-07
Owner：`soma-examples`

## 1. 目标

本文是 `soma_java` V1 runtime state schema 示例的总览入口，负责维护四类示例共同遵守的建模规则、场景索引和覆盖矩阵。每个具体场景的 schema source、运行流程和使用边界已经拆分到独立文档，避免后续扩展示例时在一个大文件中相互牵动。

四类典型场景是：

1. FJSP 构造解过程；
2. VRP 构造解过程；
3. 连续仿真过程；
4. game runtime state。

这些示例只表达构造、仿真或 game loop 运行期间的高性能 runtime data container，不表达 optimization search、策略选择、规则调度或 UI / service 集成。上层 OOP 负责 workflow orchestration、algorithm strategy、domain rule 和 solver / simulator / game loop；SOMA 负责 schema-defined hot layout、key/index/order access、packed dense state 和 DTO materialization。

代码块保存在各场景文档中。真实 Java 项目中，`package-info.java`、enum、`@SomaValue` 和 `@SomaTable` DTO class 应按 Java 文件规则拆分。

## 2. 通用建模规则

四个示例共同遵守以下规则：

- DTO class 同时是 schema source 和 materialized DTO contract；keyed table 的 `fetch(key)` 返回 DTO detached copy，不引入 `fetchDto()`；
- table 只分为 keyed table 与 dense table；`entity`、`lookup`、`workspace`、`matrix`、`event queue` 是建模场景，不是 annotation role；
- 有 stable logical key 且需要 `fetch(key)` / `containsKey(key)` / uniqueness 的 runtime data 建模为 keyed table；
- 没有 stable key、以 packed scan、row-index iteration、批量替换或矩阵行访问为主的数据建模为 dense table；
- 频繁查询的静态或导入后只读数据，如果有自然唯一 key，优先建成 keyed lookup table；
- `@SomaValue` 表达 inline value / composite key，会 flatten 到 table leaf columns；`@SomaValue` 内部不允许 table typed field；
- cross-table reference 使用 scalar、enum、semantic scalar 或 value key，例如 `MachineId`、`CustomerId`、`UnitId`，不保存另一个 root table object；
- child table 只用于 parent row owns child table instance 的生命周期关系，四个示例默认不使用 child table；
- optional scalar DTO 字段使用 boxed type，例如 `Long`、`Integer`、`Double`、`Boolean`，absent materialize 为 `null`；
- `defaultCapacity` 只是 allocation hint，不进入 logical `schema_hash`。

## 3. 场景索引

| 场景 | 独立文档 | 适用边界 |
|---|---|---|
| FJSP 构造解 | [FJSP runtime state 示例与 E2E 场景契约](fjsp-runtime-state-example.md) | `FCFS + SPT` dispatch hot loop、`MachineCandidate` keyed runtime frontier、E2E smoke 契约 |
| VRP 构造解 | [VRP 构造解 runtime state 示例](vrp-runtime-state-example.md) | greedy insertion / cheapest insertion、route sequence dense rows、insertion candidate workspace |
| 连续仿真 | [连续仿真 runtime state 示例](simulation-runtime-state-example.md) | tank / valve time-step simulation、state vector、event queue、trace buffer |
| Game runtime | [Game runtime state 示例](game-runtime-state-example.md) | grid tactics / turn-based game loop、map dense layout、move/damage workspace |

## 4. 四类示例的覆盖矩阵

| 示例 | Keyed entity state | Keyed lookup data | Dense long-lived state | Dense workspace / export buffer | 主要证明点 |
|---|---|---|---|---|---|
| FJSP | `Job`、`Operation`、`Material`、`Machine`、`MachineCandidate` | `ProcessingTime`、`SetupTime` | 无 | 无 | runtime frontier 是 keyed table，不是每轮 dense workspace |
| VRP | `Customer`、`Vehicle`、`Route` | `TravelCost` | `RouteVisitRow` | `UnassignedCustomerRow`、`InsertionCandidateRow` | route sequence 是 packed rows，不是 stable key rows |
| 连续仿真 | `Tank`、`Valve` | `FlowCoefficient` | `StateVectorRow` | `PendingEventRow`、`TraceSampleRow` | state vector 是 hot numeric state，trace 是 export / diagnostic buffer |
| Game | `Player`、`GameUnit` | `AbilityCost` | `MapTileRow` | `MoveCandidateRow`、`PendingDamageRow` | SOMA 可承载 game hot state，但不是 ECS / engine |

## 5. 对正式契约的覆盖说明

四个示例覆盖并验证 `soma_java` V1 annotation contract 的几个边界。示例文档不拥有 annotation contract；如果示例与正式契约冲突，以 `soma-annotations/docs/annotation-schema-contract.md` 为准。

- `@SomaField` 比 `@SomaColumn` 更符合 Java DTO schema source；column 是 runtime flatten 之后的物理概念；
- `@SomaKey` 必须是 table direct field 的 logical identity，value key 足以表达 composite key；
- 不需要 `@SomaEnum`，Java enum 被 SOMA field 引用后自动纳入 schema；
- 不需要 `@SomaTableRole`，四个示例中的角色都能由 keyed / dense table 与命名文档说明表达；
- `@SomaOrder` 的多个声明应理解为 named ordered access path，不是 table physical order；
- optional scalar DTO 字段必须使用 boxed type，否则无法表达 absent materialized DTO；
- semantic scalar 只在确实需要时间语义时显式声明，例如连续仿真的 `DATE_TIME`。

## 6. Non-goals

本组示例不定义：

- generated API 的最终方法签名；
- runtime core 的 sidecar 内部结构；
- benchmark 规模、性能结论或对比口径；
- 完整业务求解器、仿真器或 game engine；
- schema migration 或跨版本兼容策略；
- persistence / wire format / protobuf schema。
