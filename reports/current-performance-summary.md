# 当前性能摘要

类型：Report / 性能快照

状态：当前（只限本报告环境与 lanes）

Owner：SOMA Java 性能输出

最后审查日期：2026-07-24

受众：评估当前 runtime 形状和后续优化价值的维护者

适用版本：core product baseline `fd82eba`；industrial scheduler candidate
`a7d4fde`；其余 reference-application / performance baseline
`938b3d5`；final codegen stability `c0fa1c9`

输入事实源：[三层性能基线治理报告](2026-07-24-three-layer-performance-baseline-governance-report.md)、
[Reference Application 大规模性能基线治理报告](2026-07-24-reference-application-scale-performance-baseline-governance-report.md)、
[Industrial Dynamic Scheduler 设计与性能治理报告](2026-07-24-industrial-scheduler-design-and-performance-governance-report.md)、
七份 checked-in baseline、neutral component artifact 和 Fast/Scale/Soak/Full Gate

事实范围：当前 Candidate Scan component、三类 representative generated
footprint 与两个应用六个 profile 的环境感知 multi-fork regression baseline

非事实范围：跨环境 SLA、正式支持矩阵、普遍性能优势或 G6

测量日期：2026-07-24

环境：Azul Zulu OpenJDK `1.8.0_492-b09`，macOS `26.5.2`，arm64/aarch64

方法：component ThreadMXBean exact allocation，普通 Gate 5 fork；六个应用
profile 普通 Gate 3 fork；current baseline 保留既有 9-fork provenance，后续
baseline 通常以 5 fork 建立，9 fork 只用于明确授权的方差诊断；scheduler
artifact v4 同时记录 hot solve 与 canonical end-to-end；三 surface clean
code-size

## 1. 当前边界

Runtime 使用 packed `[0,size)`、keyed/dense swap-remove、eager grouped exact index、table-local `IndexBuffer` 与 compatibility v4。完整 Access Model 区分 Point、Candidate、Column、Key、Bulk 和 Ownership；只有 Candidate Scan 使用 lazy one-shot stage plan。

三层模型区分 component baseline、六个 application-owned profile baseline 与
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

| Profile | Workload | 9-fork hot solve range / median | Timing limit | Allocation range / limit |
|---|---|---:|---:|---:|
| default | 1,000 operations、10 machines、3 candidates/op | `13.459..15.522 / 14.174 ms` | `21.261 ms` | `3.956 / 4.946 MB` |
| large | 100,000 operations、100 machines、3 candidates/op | `1.278..1.295 / 1.287 s` | `1.931 s` | `111.032..117.538 / 143.338 MB` |
| long-run | 10,000 operations、100 machines、3 candidates/op | `44.056..49.702 / 47.050 ms` | `70.575 ms` | `11.008..15.022 / 17.270 MB` |

| Profile | canonical end-to-end range / median | Timing limit | Allocation range / limit |
|---|---:|---:|---:|
| default | `22.255..24.496 / 22.830 ms` | `34.245 ms` | `8.097..8.098 / 10.123 MB` |
| large | `1.318..1.335 / 1.327 s` | `1.990 s` | `227.001..241.513 / 288.904 MB` |
| long-run | `70.904..77.993 / 73.962 ms` | `110.943 ms` | `31.035..35.049 / 42.303 MB` |

Default、large、long-run 的 frontier 分别为 `30 / 3,000 / 300`。hot solve
归一化 median 为 `4,725 / 12,871 / 4,637 ns/operation` 和
`1,319 / 1,147 / 1,382 B/operation`。Global selection 使用每机一个代表项的
indexed min-heap；machine/resource version 只使相关 root group 增量重算，不再
每次对全量 candidate 动态排序。Large 校准最大 Young GC 为 `2/8 ms`、
Full GC 为零；long-run 为 `1/3 ms`、Full GC 为零。

早期治理候选暴露逐 operation owned child Table 导致 large canonical path 约
`718..724 MB` allocation 且 3/9 fork 出现 Full GC。最终 flat
eligible-machine exact-group projection 将其降至 `227.001..241.513 MB`，
9/9 fork 均无 Full GC；这是 application Schema/access 归因，不是 SOMA core
缺陷。

Application allocation 是带 JVM 优化噪声的 fitness signal，按跨 fork median
比较；GC maximum 与 runtime high-water all-equal 分别守住压力和确定性边界。
单个 allocation maximum 超限不再触发自动 rebaseline。

### 3.2 Grassing individual simulation

| Profile | Workload | 9-fork hot operation range / median | Timing limit | Allocation range / limit |
|---|---|---:|---:|---:|
| default | 1,000 individuals × 1,000 ticks、128 × 72 | `145.689..165.054 / 146.905 ms` | `220.357 ms` | `11.530..11.531 / 12.108 MB` |
| large | 100,000 individuals × 1,000 ticks、1280 × 720 | `5.913..6.080 / 5.948 s` | `8.922 s` | `426.403 / 447.723 MB` |
| long-run | 10,000 individuals × 10,000 ticks、400 × 225 | `3.577..3.649 / 3.604 s` | `5.406 s` | `47.352..47.358 / 49.726 MB` |

Maximum population 为 `1,433 / 158,318 / 13,837`；归一化 median 为
`48,969 / 5,948,031 / 360,405 ns/tick` 和
`3,844 / 426,403 / 4,736 B/tick`。Large 的 live population 约为 default 的
110 倍，timing 与 scratch high-water 同阶增长。其每 fork 均出现一次 Full GC，
最大 pause `28 ms`，不足 hot operation 的 `0.5%`，没有 GC thrash 证据。
Long-run 的 10,000 ticks 总 allocation 约 47 MB 且 GC 为零，持续 churn 未形成
随 tick 累积的临时对象失控。

两应用的 correctness、failure、lifecycle 和 result identity 均在性能数字之前
通过；Fast、Scale、Soak 分责，Full 组合六个 workload。表中 MB/ms 为可读摘要，
正式 baseline 保存原始 bytes/nanos。

## 4. Generated footprint

首个两应用切换候选采用 per-surface fixed candidate + 15% ceiling：

| Surface | Scan count | Scan source bytes | source lines | Scan family class bytes | nested classes |
|---|---:|---:|---:|---:|---:|
| neutral benchmark | 6 | 144,720 | 644 | 203,536 | 45 |
| industrial scheduler | 9 | 218,605 | 940 | 311,595 | 65 |
| grassing simulation | 2 | 47,835 | 213 | 68,276 | 15 |

Checker 同时生成 surface、逐 Scan 与逐 schema footprint，并要求三层汇总闭合。该 Gate 防止同一候选的生成规模无意膨胀；它不是长期容量承诺，也不能证明某个 feature 的单独因果。

## 5. 解释边界

component、Fast、Scale、Soak 和 Full comparator Gate 在精确匹配环境中为
`passed`；其他环境只能在 artifact 完全合法后得到 `not-applicable`。旧 FJSP
100k A/B 仍是历史证据，其约 262 ms 的 machine-local arg-min 不包含当前
scheduler 的 global total order、secondary resource、maintenance、transport
和 dynamic delay 语义，不能直接比较。当前数据不能外推到其他机器、workload、
production SLA、支持矩阵或 public release claim。
