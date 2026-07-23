# 当前性能摘要

类型：Report / 性能快照

状态：当前（只限本报告环境与lanes）

Owner：SOMA Java 性能输出

受众：评估当前runtime形状和后续优化价值的维护者

适用版本：commit `fd82ebaa8dc4331b7cd9b3c62392d2472185f25f`，`0.2.0-SNAPSHOT`

输入事实源：[Access Model / Candidate Scan性能报告](2026-07-23-access-model-candidate-scan-performance-report.md)、[治理报告](2026-07-23-access-model-candidate-scan-governance-report.md)

事实范围：当前Candidate Scan component、generated code size与FJSP 100k多fork diagnostic

非事实范围：跨环境SLA、正式支持矩阵、普遍性能优势或G6

测量日期：2026-07-23

环境：Azul Zulu OpenJDK `1.8.0_492-b09`，macOS `26.5.2`，arm64/aarch64

方法：component ThreadMXBean exact allocation；33-table clean code-size；FJSP 5独立JVM × 3 measurement A/B

最后审查日期：2026-07-23

## 1. 当前边界

当前runtime使用packed `[0,size)`、keyed/dense swap-remove、eager grouped exact index、table-local `IndexBuffer`与compatibility v4。完整Access Model区分Point、Candidate、Column、Key、Bulk和Ownership；只有Candidate Scan使用lazy one-shot stage plan。

以下结果只证明`fd82eba`在本报告环境和固定lanes的形状。全部artifact为`claimAllowed=false`，G6仍blocked。

## 2. Component摘要

| Lane | allocated B/op |
|---|---:|
| Packed zero-stage count | 4.4272 |
| exact zero-stage count | 88.0928 |
| exact one-filter count | 192.0736 |
| exact three-stage count | 240.0736 |
| exact five-stage overflow count | 464.0736 |
| exact filter-sort scalar Index | 272.0736 |
| long ColumnTraversal | 44.5968 |

16条allocation与24条memory record全部通过；exact-index cardinality retained slack为`0..63 bytes`。Snapshot、materialization、plan/handle和table-retained scratch分别计量。

## 3. FJSP 100k多fork

相同dataset/JVM参数下，Stage 1 `2f0d116`到Stage 2 `fd82eba`：

| 指标 | Stage 1 | Stage 2 | 变化 |
|---|---:|---:|---:|
| solve median | 296.967 ms | 261.666 ms | -11.89% |
| solve allocation median | 3380.292 B/op | 2550.615 B/op | -24.54% |
| total allocation median | 9319.638 B/op | 8491.112 B/op | -8.89% |
| Young GC | 279 / 744 ms | 264 / 755 ms | count下降、time小幅上升 |
| Full GC | 5 / 247 ms | 5 / 262 ms | count相同、time小幅上升 |

5/5独立JVM fork的solve median均改善；30条记录的assignments、jobs、makespan、tardiness与checksum完全一致。GC time反向波动不作收益声明。

## 4. Generated code size

33-table clean compile相对Stage 1：generated Scan source bytes +14.77%、source lines +13.83%、family class bytes +14.04%、nested class count +3.46%，均在15%专题Gate内。Source bytes距上限仅1,595 bytes，是当前最敏感的防回归指标。

## 5. 解释边界

详细method、artifact hash、JFR attribution和逐fork数据见[专题性能报告](2026-07-23-access-model-candidate-scan-performance-report.md)。本摘要不支持跨机器、跨workload、production SLA、正式支持矩阵或public release claim。
