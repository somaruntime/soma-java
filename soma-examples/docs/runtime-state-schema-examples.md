# Runtime state schema 典型示例

类型：Report / 开发者 current-executable 索引
状态：当前
Owner：`soma-examples` output
受众：评估 SOMA 当前四类 executable scenario 的开发者
适用版本：最后 implementation-affecting baseline `b991f4c`
输入事实源：当前 example source、[Blueprint](../../docs/blueprints/README.md)、Design 与 G5 evidence
事实范围：runtime-state scenario 通用建模规则、索引和覆盖矩阵
非事实范围：具体场景 schema、public contract 和 benchmark result
最后审查日期：2026-07-20

> 本文是当前 executable scenario 的开发者索引，不拥有目标设计。目标形态由 Blueprint 拥有，长期规范性语义由 Design 拥有，差距由 Conformance 记录。

## 1. 目标

本文是 `soma_java` V1 当前 runtime-state examples 的总览入口，记录四类示例的现有建模形态、教学结构、场景索引和覆盖矩阵。每个具体场景的 schema source、运行流程和使用边界位于独立文档。

四类典型场景是：

1. FJSP 构造解过程；
2. VRP 构造解过程；
3. 连续仿真过程；
4. game runtime state。

这些示例以“读者先看懂算法，再理解 SOMA 如何让运行时状态更简洁”为组织原则。FJSP
先落成可读入口、输入模型、算法 loop、table lifecycle 与领域结果分离的参考结构，错误、
lifecycle、golden 和 gate-only fixture 进入 test source；其他三个示例是否采用同一结构，
须在 FJSP 可读性审查后分别决定，不能机械复制。上层 OOP 负责 workflow orchestration、
algorithm strategy、domain rule 和 solver / simulator / game loop；SOMA 负责
schema-defined hot layout、key/exact-index access、explicit dynamic sort、packed dense state 和 detached
materialized object。

代码块保存在各场景文档中。真实 Java 项目中，`package-info.java`、enum、`@SomaValue` 和 `@SomaTable` schema-backed row class 应按 Java 文件规则拆分。`@SomaTable` class 本身就是 detached single-row materialization shape；processor 不再生成 public `XxxRecord` 第二类型。

## 2. 通用建模规则

四个示例共同遵守以下规则：

- 在选择 keyed/dense、root/child 和 access path 前，先把 table 或明确 field group 分类为 input facts、working state 或 result facts，并标明 authoritative/rebuildable/derived；
- 每个正式场景为核心 table/phase 提供 Access Pattern Card，记录 rows、hot columns、access/mutation mix、selectivity、optional/child density、working set 和 allocation/export frequency；该 card 属于 scenario/runtime plan，不进入 Schema/hash；
- 不同 data role 默认按生命周期、可变性和访问模式分开；因 hot-loop locality 或单行一致性合并时必须说明理由和唯一事实源，不能为了输出方便复制一份 result shadow table；
- `@SomaTable` class 同时定义 row schema 与 detached single-row materialization shape，但它不是 runtime row storage；keyed table 的 `fetch(key)` 直接返回该 schema class 的 detached object；
- Java logical schema/materialization 中 `List<R>` 表示 dense table，`Map<K, R>` 表示 keyed table；runtime 内部仍使用独立 columnar `TableStore`，不把 Java collection 作为 live storage；
- table 只分为 keyed table 与 dense table；`entity`、`lookup`、`workspace`、`matrix`、`event queue` 是建模场景，不是 annotation role；
- 有 stable logical key 且需要 `fetch(key)` / `containsKey(key)` / uniqueness 的 runtime data 建模为 keyed table；
- 没有 stable key、以 packed scan、row-index iteration、批量替换或矩阵行访问为主的数据建模为 dense table；
- 频繁查询的静态或导入后只读数据，如果有自然唯一 key，优先建成 keyed lookup table；
- `@SomaValue` 表达 compiler-defined immutable inline value / composite key；字段默认具有 `public final` 语义，并获得 canonical construction、value equality/hash 与 `toString()`；flatten 后成为 table leaf columns，内部不允许 child table field；
- cross-table reference 使用 scalar、enum、semantic scalar 或 value key，例如 `MachineId`、`CustomerId`、`UnitId`，不保存另一个 root table object；
- child table 只用于 parent row owns child table instance 的生命周期关系；FJSP 的 operation candidate machines 与 VRP 的 route visits 使用 dense child，连续仿真和 game 示例没有为了展示功能而强行引入 child；
- required child materialize 为非 `null` 的 `List` / `Map`，空 child 为 empty collection；optional child absent 为 `null`，present-empty 仍为非 `null` empty collection；
- optional field 由 `@SomaField` 或 `@SomaChild` 加 `@SomaOptional` 表达；optional primitive schema type 必须使用 boxed type，例如 `Long`、`Integer`、`Double`、`Boolean`，absent materialize 为 `null`；
- `defaultCapacity` 只是 allocation hint，不进入 logical `schema_hash`。

## 3. 场景索引

| 场景 | 独立文档 | 适用边界 |
|---|---|---|
| FJSP 构造解 | [FJSP runtime state 示例与 E2E 场景契约](fjsp-runtime-state-example.md) | `FCFS + SPT` dispatch hot loop、`MachineCandidate` keyed runtime frontier、E2E smoke 契约 |
| VRP 构造解 | [VRP 构造解 runtime state 示例](vrp-runtime-state-example.md) | greedy insertion / cheapest insertion、route sequence dense rows、insertion candidate workspace |
| 连续仿真 | [连续仿真 runtime state 示例](simulation-runtime-state-example.md) | tank / valve time-step simulation、state vector、event queue、trace buffer |
| Game runtime | [Game runtime state 示例](game-runtime-state-example.md) | grid tactics / turn-based game loop、map dense layout、move/damage workspace |

## 4. 四类示例的覆盖矩阵

| 示例 | Input facts | Working state | Result facts / export | 主要证明点 |
|---|---|---|---|---|
| FJSP | `OperationDefinition.candidateMachines` dense child、`SetupTime` | machine/job/operation progress、`MachineCandidate` | `OperationAssignment` | input、working state、result 分离；runtime frontier 是 keyed table |
| VRP | `TravelCost`、customer/vehicle static fields | route/customer state、per-route `Route.visits` dense child、unassigned/candidate workspace | route plan 与 customer assignment facts | route sequence 是 parent-owned packed rows，不是 stable key rows |
| 连续仿真 | topology、`FlowCoefficient`、initial conditions | `StateVectorRow`、`PendingEventRow` | `TraceSampleRow` / final-state export | state vector 是 hot numeric state，trace 不反向成为状态事实源 |
| Game | terrain/ability/static entity definition | unit/player state、occupancy cache、move/damage workspace | battle outcome / snapshot projection | SOMA 可承载 game hot state，但不是 ECS / engine |

## 5. 对正式契约的覆盖说明

四个示例覆盖并验证 `soma_java` V1 annotation contract 的几个边界。示例文档不拥有 annotation contract；目标语义以 [Schema 与生成 API](../../docs/design/schema-and-generated-api.md) 为准，当前精确 annotation/generated surface 由 [可执行契约地图](../../docs/implementation-map/executable-contract-map.md) 登记的代码、golden 与 validator 拥有。

- `@SomaField` 比 `@SomaColumn` 更符合 Java logical schema；column 是 runtime flatten 之后的物理概念；
- `@SomaKey` 必须是 table direct field 的 logical identity，value key 足以表达 composite key；
- 不需要 `@SomaEnum`，Java enum 被 SOMA field 引用后自动纳入 schema；
- 不需要 `@SomaTableRole`，四个示例中的角色都能由 keyed / dense table 与命名文档说明表达；
- `@SomaIndex` / `@SomaUnique` 只表达 exact access；业务顺序通过本次 Row Pipeline 的 `sorted(...)` 显式建立；
- optional scalar schema 字段必须使用 boxed type，否则 detached schema object 无法表达 absent；
- semantic scalar 只在确实需要时间语义时显式声明，例如连续仿真的 `DATE_TIME`。

## 6. Non-goals

本组示例不定义：

- generated API 的最终方法签名；
- runtime core 的 exact-index / `IndexBuffer` 内部结构；
- benchmark 结果或对比结论；具体规模 preset 与计时口径由 `soma-benchmarks` 拥有，
  examples 只提供可复用的同语义算法 kernel；
- 完整业务求解器、仿真器或 game engine；
- schema migration 或跨版本兼容策略；
- persistence / wire format / protobuf schema。
