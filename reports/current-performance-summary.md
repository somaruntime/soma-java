# 当前性能摘要

类型：Report / 性能快照

状态：当前（只限本报告环境与 lanes）

Owner：SOMA Java 性能输出

受众：评估当前 runtime 形状和后续优化价值的维护者

适用版本：commit `a137b10`

事实范围：commit `a137b10`的 packed exact component 与 FJSP 100k integrated diagnostic

非事实范围：跨环境 SLA、普遍性能优势、新旧四场景性能对比或 G6

测量日期：2026-07-21

环境：Azul Zulu OpenJDK `1.8.0_492-b09`，macOS `26.5.2`，aarch64

输入事实源：[2026-07-21 四场景 Blueprint 采纳报告](2026-07-21-four-scenario-blueprint-adoption-report.md)、[2026-07-20 post-cutover 尾项报告](2026-07-20-packed-exact-index-post-cutover-closeout-report.md)

最后审查日期：2026-07-21

## 1. 当前边界

当前 runtime 仍使用 packed `[0,size)`、swap-remove、eager grouped exact index 与 table-local `IndexBuffer`。四场景采纳改变了 FJSP/VRP/Simulation/Game 的 schema 和 application workflow，未改变 core runtime 结构。

2026-07-20 的 FJSP dynamic-sort/heap A/B 仍支持“application heap 是该旧 workload 下的正确结构决策”，但不再是当前 FJSP journey 的可比性能数据。新旧 dispatch、preflight 和 workflow 不同，因此本报告不计算“相对提升”。

## 2. Packed exact component

`./scripts/check-post-cutover-components.sh` 使用2,000次warmup和5,000次measurement；每个lane的measurement window均0次Young/Full GC。

| Lane | allocated B/op |
|---|---:|
| exact source → count | 116.1696 |
| exact source → filter → count | 400.0736 |
| exact source → filter → sort → single snapshot | 648.0960 |
| exact source → filter → sort → materialize | 1,090.4928 |

65,536 rows / 16 distinct groups 的 exact-index primitive retained payload 为`787,024 B`，row-worst-case为`3,211,264 B`，减少`75.492%`；65,536 distinct groups时两者均为`3,211,264 B`。该数字排除JVM object header/alignment。

commit-bound artifact SHA-256：`6f0220a708e33ab4753ac5ebb83363011d50af3c43d16907d87364346b4b9539`。

## 3. 当前 FJSP 100k 诊断

Workload为1,000 jobs × 100 operations、100 machines、每operation 3 candidates，seed `1397706049`；JVM为`-Xms512m -Xmx512m -Xmn96m -XX:+UseParallelGC`，2次warmup + 5次measurement。

| 指标 | 当前观测 |
|---|---:|
| solve time 中位数 | 295.352292 ms |
| total allocation 中位数 | 925,795,272 B |
| allocation/op 中位数 | 9,257.95272 B |
| Young GC（5次合计） | 94 / 243 ms |
| Full GC（5次合计） | 2 / 88 ms |

5/5次measurement均得到100,000 assignments、1,000 completed jobs、makespan `33891`、total tardiness `12234615`、checksum `-1508405863224505168` 和同一 RuntimePlan hash。artifact SHA-256：`de557d62fc61874383baf63eae1e95a818eebfbefda6bcfdd110e97309be2a8e`。

## 4. 解释边界

两组 artifact 均为 `claimAllowed=false`。它们支持当前 component allocation/cardinality 形状和 FJSP 该固定 workload 在本机的诊断，不支持跨机器、跨 workload、production SLA、正式支持矩阵或与2026-07-20旧FJSP数据的直接对比。

后续性能优化仍必须保留 caller-responsibility Index、resource preflight、collision full equality、mutation atomicity、packed swap-remove 和 public API 兼容；不得为降低 allocation 恢复 dirty rebuild、maintained order 或 unsafe stable Index。
