# Runtime state schema 典型示例

类型：Report / 开发者 current-executable 索引

状态：当前

Owner：`soma-examples` output

受众：评估或维护四类 current executable scenario 的开发者

适用版本：最后 implementation-affecting baseline `fd82eba`

输入事实源：当前 example source、[Blueprint](../../docs/blueprints/README.md)、Design 与 phase-6 evidence

事实范围：四类 executable scenario 的共同建模边界、导航和当前覆盖

非事实范围：具体 Blueprint 决策、SOMA public contract 和 benchmark claim

最后审查日期：2026-07-23

> 本文是当前 executable scenario 的开发者索引，不拥有目标设计。目标由 Blueprint 拥有，长期语义由 Design 拥有，差距由 Conformance 记录。

## 1. 当前结构

四个 Java 8 scenario 都采用“application workflow + SOMA schema-specific storage”的同一分工，但不会机械复制同一种 Table 形态：

| 场景 | 当前核心形态 | Application-owned structure |
|---|---|---|
| FJSP | keyed definitions/state/result + owned child + keyed candidate frontier | indexed machine minimum heap、dispatch/commit/fail-stop |
| VRP | keyed definitions/assignment + parent-owned visits + dense candidate workspace | route projection、total comparator、cross-root commit |
| Simulation | keyed definitions + authoritative dense vector + trace/projection rows | event `PriorityQueue`、numerical model、fail-stop session |
| Game | keyed definitions/state/tile/cache + dense move/damage phases | action context/revision、cache recovery、damage staging |

统一入口是 [`ScenarioSuite.java`](../src/main/java/com/hgtech/soma/examples/ScenarioSuite.java)。四个场景的 source-of-truth、ordering、stale/failure 和 export 边界都由可执行断言覆盖。

## 2. 共同建模规则

- 先区分 input facts、authoritative working state、derived workspace/cache 和 result/export，再选择 keyed/dense、root/child 和 exact access；
- stable logical key 且需要 point lookup/uniqueness 的数据使用 keyed table；phase-local replace/scan/sort 数据使用 dense table；
- `@SomaIndex` / `@SomaUnique` 只服务已证明的 equality access，不表达业务 order；动态顺序用带完整 tie-break 的 `sorted(...)` 或 application-owned heap；
- `@SomaValue` 是 immutable inline value/composite key，跨 Table reference 只保存 scalar/value identity，不保存另一个 root object；
- child 只表达 exclusive lifecycle ownership；FJSP candidate-machine definitions 与 VRP route visits 使用 child，Simulation/Game 不为对象层级强行建 child；
- application 长期保存 identity 时使用 `@SomaKey`；Index/IndexSnapshot 只在同步、只读消费批次内立即使用；
- reusable Batch/scratch 只在 Table 已完成 detached copy 后清空，不能跨并发 phase 共享；
- 单次 Table operation 失败原子；跨 Table、heap、cache 的提交与恢复由 application 明确拥有；
- 物理遍历顺序没有业务语义，swap-remove 后仍由 key、field 或 comparator 重新定位；
- detached materialization 和 external DTO 只出现在明确 export boundary，不进入 canonical hot storage/path。

## 3. 场景导航

- [FJSP runtime state](fjsp-runtime-state-example.md)：schema、frontier、unique sequence 与 import proof；
- [FJSP E2E](fjsp-e2e-scenario.md)：release/dispatch/commit/heap/failure journey；
- [VRP runtime state](vrp-runtime-state-example.md)：definition/assignment split、all-ordinal projection、route rewrite；
- [连续仿真 runtime state](simulation-runtime-state-example.md)：authoritative vector、event heap、nanosecond clock、numeric staging；
- [Game runtime state](game-runtime-state-example.md)：definition/state split、keyed occupancy、stale action、damage staging。

## 4. 当前覆盖矩阵

| 示例 | Input facts | Authoritative state/result | Derived state | 主要证明点 |
|---|---|---|---|---|
| FJSP | job/operation/machine/setup | machine/job/operation progress、assignment | keyed candidate frontier indicators | complete preflight、publish-before-heap、FCFS/SPT、checked commit |
| VRP | customer/vehicle/travel | route + visits、customer assignment | unassigned/candidate workspace | empty/non-empty ordinal、hard constraints、stale version、authoritative-first commit |
| Simulation | tank/valve/coefficient | `StateVectorRow` | event Table projection | same-time heap order、event boundary、numeric atomicity、trace export |
| Game | player/unit/tile/ability definitions | player/unit state | occupancy cache、move/damage phases | stale action、cache rebuild、damage total order/fail-stop |

## 5. Evidence 边界

[`check-examples-phase6.sh`](../../scripts/check-examples-phase6.sh) 在 full JDK 8 下执行 clean build、四场景 journey、schema/hash golden、222 个 generated-type manifest、public API facts、class major 52、Access Pattern marker 与禁止 reflection/Stream/hot-path materialization 的静态门禁。

这些证据证明当前 fixture 与 generated surface 可重放，不证明完整业务算法、跨机器性能优势或 public release readiness。性能结论必须转到 benchmark artifact 和正式 Report。

## 6. 非目标

本组示例不定义完整 APS/VRP solver、仿真平台、game engine、schema migration、persistence/wire format、跨 root transaction 或发布支持矩阵。
