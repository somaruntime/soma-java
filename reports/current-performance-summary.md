# 当前性能摘要

类型：Report / 性能快照

状态：当前（只限本报告环境与 lanes）

Owner：SOMA Java 性能输出

受众：评估当前 runtime 形状和后续优化价值的维护者

适用版本：core product baseline `fd82eba`；reference-application architecture
`69e5dc6` / `287350d`；performance baseline implementation `5be618a`

输入事实源：[三层性能基线治理报告](2026-07-24-three-layer-performance-baseline-governance-report.md)、
三份 checked-in baseline、neutral component artifact 和两个 application Gate

事实范围：当前 Candidate Scan component、三类 representative generated
footprint 与两个应用的环境感知 multi-fork regression baseline

非事实范围：跨环境 SLA、正式支持矩阵、普遍性能优势或 G6

测量日期：2026-07-24

环境：Azul Zulu OpenJDK `1.8.0_492-b09`，macOS `26.5.2`，arm64/aarch64

方法：component ThreadMXBean exact allocation，普通 Gate 5 fork；两个应用普通
Gate 3 fork × 3 measurements；baseline 校准 9 fork；三 surface clean code-size

最后审查日期：2026-07-24

## 1. 当前边界

Runtime 使用 packed `[0,size)`、keyed/dense swap-remove、eager grouped exact index、table-local `IndexBuffer` 与 compatibility v4。完整 Access Model 区分 Point、Candidate、Column、Key、Bulk 和 Ownership；只有 Candidate Scan 使用 lazy one-shot stage plan。

三层模型区分 component baseline、两个 application-owned integrated baseline 与
public performance claim。当前前两层已进入回归 Gate，第三层仍为空。全部
measurement/baseline/result artifact 均为 `claimAllowed=false`，G6 仍 blocked。

## 2. Neutral component

普通 Gate 每次执行 5 个独立 JVM fork，每 fork 产生 16 条 allocation 与 24 条
exact-index memory record。Strict validator、9 个 comparator negative paths 和
当前环境 baseline 均通过。代表性 baseline：

| Lane | 当前 allocation B/op | allocation 上限 | median timing 上限 ns/op |
|---|---:|---:|---:|
| packed zero-stage count | 4.4928 | 16 | 84 |
| exact zero-stage count | 88.0928 | 89 | 140 |
| exact one-filter count | 192.0736 | 193 | 867 |
| exact three-stage count | 240.0736 | 241 | 1,105 |
| exact five-stage overflow count | 464.0736 | 465 | 1,552 |
| exact filter-sort scalar Index | 272.0736 | 273 | 1,112 |
| long ColumnTraversal | 44.5968 | 160 | 1,601 |

24 条 memory lane 的 `retainedBytes` 使用全等值 Gate；cardinality-aware exact-index
primitive payload 相对 right-sized estimate 的 retained slack 为 `0..63 bytes`。
Snapshot、materialization、plan/handle 和 table-retained scratch 分开计量。

## 3. Application integrated lanes

### 3.1 Industrial dynamic scheduler

Default profile 为 192 operations；input/result/schema/runtime-plan identity 在 9-fork
校准中完全一致。校准结果：

- allocation 范围 `8,319,504..8,320,064 bytes`，Gate 上限
  `8,736,068 bytes`；
- solve p50 `37,619,668 ns`、p90/max `39,133,584 ns`，median Gate 上限
  `56,429,502 ns`；
- Young/Full GC count 与 pause 均为 `0`；
- exact-index、update scratch、operation scratch high-water 分别为
  `3,015`、`7,560`、`368 bytes`。

Correctness、large 与 long-run profile 另行证明 6/8,000/10,000 operations、完整约束 validator、failure/lifecycle 和持续 frontier churn；这些不被混入 default timing。

### 3.2 Grassing individual simulation

Default profile 为 800 initial individuals、500 ticks，maximum population 1,139；
全部 identity/checksum 在 9-fork 校准中一致。校准结果：

- allocation 范围 `7,439,296..7,440,784 bytes`，Gate 上限
  `7,812,824 bytes`；
- tick p50 `66,086,417 ns`、p90/max `76,318,167 ns`，median Gate 上限
  `99,129,626 ns`；
- Young/Full GC count 与 pause 均为 `0`；
- exact-index、update scratch、operation scratch high-water 分别为
  `64,333`、`27,336`、`12,776 bytes`；
- population table growth count 为 `1`。

Correctness 使用逐 tick AoS 位级 oracle；large/long-run 分别覆盖 30,000
individuals × 300 ticks 与 5,000 individuals × 2,000 ticks。性能 lane 不替代
这些 correctness guard。

## 4. Generated footprint

首个两应用切换候选采用 per-surface fixed candidate + 15% ceiling：

| Surface | Scan count | Scan source bytes | source lines | Scan family class bytes | nested classes |
|---|---:|---:|---:|---:|---:|
| neutral benchmark | 6 | 144,720 | 644 | 203,536 | 45 |
| industrial scheduler | 12 | 296,763 | 1,279 | 425,166 | 90 |
| grassing simulation | 2 | 47,835 | 213 | 68,276 | 15 |

Checker 同时生成 surface、逐 Scan 与逐 schema footprint，并要求三层汇总闭合。该 Gate 防止同一候选的生成规模无意膨胀；它不是长期容量承诺，也不能证明某个 feature 的单独因果。

## 5. 解释边界

三个 comparator Gate 在精确匹配环境中为 `passed`；其他环境只能在 artifact 完全
合法后得到 `not-applicable`。旧 FJSP 100k A/B 仍是历史证据，不再是 current
integrated lane。当前数据不能外推到其他机器、workload、production SLA、支持
矩阵或 public release claim。
