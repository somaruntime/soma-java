# Access Model / Candidate Scan 性能报告

类型：Report / 性能证据

状态：当前（仅限本报告环境与lanes）

Owner：SOMA Java Access Model / Candidate Scan 性能输出

受众：评估当前runtime形状、allocation与集成回归风险的维护者

适用版本：Stage 1 baseline `2f0d11676ae6f11ac7c0bb88590d109a6a24dc2b`；Stage 2 candidate `fd82ebaa8dc4331b7cd9b3c62392d2472185f25f`

输入事实源：commit-bound component/code-size/FJSP artifacts、完整Gate与[治理报告](2026-07-23-access-model-candidate-scan-governance-report.md)

事实范围：Candidate Scan component allocation/memory、JFR attribution、generated code size与FJSP 100k多fork A/B

非事实范围：跨机器SLA、正式支持矩阵、普遍性能优势、G6或public release readiness

测量日期：2026-07-23

环境：Azul Zulu OpenJDK `1.8.0_492-b09`、javac 8、Maven `3.9.16`、macOS `26.5.2` / Darwin `25.5.0`、arm64 / JVM aarch64

方法：component使用ThreadMXBean exact allocation，4096 elements/64 groups、warmup 2000、measurement 5000；FJSP使用5个独立JVM、每fork warmup 2/measurement 3、A/B交错

最后审查日期：2026-07-23

## 1. 结论

`fd82eba`通过本专题全部correctness、allocation、memory、code-size与integrated Gate。相对Stage 1，Packed零stage、exact stage链、best-one和ColumnTraversal的operation allocation显著下降；FJSP固定100k workload的5/5 fork solve median均改善，solve allocation median下降24.54%。

这些数字是当前机器、JDK、workload和measurement方法下的local diagnostic，全部artifact保持`claimAllowed=false`。Young/Full GC time存在小幅反向波动，因此本报告不把GC time写成确定收益，也不外推production SLA。

## 2. Correctness 与 identity guard

Stage 1/Stage 2 A/B使用同一seed、dataset与JVM参数。30条FJSP measurement均得到：

- assignments `100000`；
- completed jobs `1000`；
- makespan `33891`；
- total tardiness `12234615`；
- checksum `-1508405863224505168`。

FJSP、VRP、Simulation、Game四份Schema SHA-256在两个版本间完全一致。Stage 1 plan hash为`24c525f1082841041843cc84b1c19eecb1a7719e5ac268ab36f56ea1ce6a1962`，Stage 2因compatibility v4输入确定性变为`4040304b25ac865ff1e098629410a8ddd31f81a8db05fe98adad74b0c8769b06`；各自measurement内唯一稳定。

## 3. Component allocation

16条allocation与24条memory record全部通过validator。主要lane如下：

| Lane | Stage 1 B/op | Stage 2 B/op | 变化 | Stage 2 Gate |
|---|---:|---:|---:|---:|
| Packed zero-stage count | 83.7216 | 4.4272 | -94.71% | `<=16` |
| exact zero-stage count | 112.0736 | 88.0928 | -21.40% | `<=89` |
| exact one-filter count | 400.0736 | 192.0736 | -51.99% | `<=193` |
| exact three-stage count | 560.0736 | 240.0736 | -57.14% | `<=241` |
| exact five-stage overflow count | 1000.0736 | 464.0736 | -53.60% | `<=465` |
| exact filter-sort scalar Index | 648.0960 snapshot baseline | 272.0736 | -58.02% | `<=273` |
| long ColumnTraversal | 564.7280 | 44.5968 | -92.10% | `<=160` |

其他边界：exact zero-stage scalar Index `120.096 B/op`，IndexSnapshot `336.0736 B/op`，materialization `600.312 B/op`，primary point lookup `0.0736 B/op`。Snapshot和materialization保持独立成本lane，没有被混入non-materializing Scan收益。

Component artifact：`target/post-cutover-components.rj19ih/post-cutover-components.jsonl`，SHA-256 `5d8930823e2502dca95e69087dfc981374d06e55c44a589360e9678d8dc3473a`。

## 4. Retained memory

24条exact-index cardinality lane的retained capacity slack为`0..63 bytes`。Candidate Scan plan/handle由caller短期持有，不计入TableStats retained storage；Table-owned IndexBuffer、sort/update scratch继续进入current/high-water accounting。

该口径防止把transient plan allocation隐藏为Table retained memory，也防止为了降低B/op把application graph挂在consumed handle上。

## 5. JFR allocation attribution

20,000 iteration profile记录84个`ObjectAllocationInNewTLAB`与67个`ObjectAllocationOutsideTLAB` sample，其中main thread 136个。主要可控sample为：

- 24 B generated Scan handle；
- 64 B typed exact source；
- 56 B inline storage、24 B overflow owner、32 B terminal evaluation；
- overflow arrays与benchmark memory-matrix primitive arrays。

Main thread只有一个String sample，位于Table `addBatch`初始化，不位于Scan、ColumnTraversal或ColumnView hot traversal。除显式materialization lane外，没有发现Stream、Iterator或per-candidate schema object allocation。

JFR会扰动ThreadMXBean B/op，因此只用于type/stack attribution，不用于envelope判定。JFR SHA-256：`b969136e247cbc8152c4f8d87dd1e5765609206a192d14f93c7f0b1fcf26a8c4`；attribution JSON SHA-256：`a86d487189bf09b85b4ca2c121f8f8ee874133abb278eaab0275b6d2196a5f13`。

## 6. Generated code size

33-table clean compile：

| 维度 | Stage 1 | Stage 2 | 增幅 | Gate |
|---|---:|---:|---:|---:|
| generated Scan source bytes | 684,384 | 785,446 | 14.77% | `<=15%` |
| generated Scan source lines | 2,980 | 3,392 | 13.83% | `<=15%` |
| Scan family class bytes | 957,266 | 1,091,692 | 14.04% | `<=15%` |
| nested class count | 231 | 239 | 3.46% | `<=15%` |

Clean javac wall time为`7,448 ms`。Source bytes距当前Gate上限仅1,595 bytes，是本轮最敏感的防回归指标；它已被自动Gate约束，但不应被误写为充足余量。

Artifact：`target/scan-code-size.FWl5xJ/scan-code-size.properties`，SHA-256 `313a55fc7e439514ef1726c7242c9873f4982e5a207cbc72078cbf8def4880eb`。

## 7. FJSP 100k 多fork A/B

JVM参数固定为`-Xms512m -Xmx512m -Xmn96m -XX:+UseParallelGC`。每个版本15个有效measurement：

| 指标 | Stage 1 | Stage 2 | 变化 |
|---|---:|---:|---:|
| solve median | 296.967 ms | 261.666 ms | -11.89% |
| solve range | 275.744–396.261 ms | 251.340–320.053 ms | — |
| solve allocation median | 3380.292 B/op | 2550.615 B/op | -24.54% |
| total allocation median | 9319.638 B/op | 8491.112 B/op | -8.89% |
| Young GC合计 | 279 / 744 ms | 264 / 755 ms | count -5.38%，time +1.48% |
| Full GC合计 | 5 / 247 ms | 5 / 262 ms | count相同，time +6.07% |

逐fork solve median为：`292.501 -> 274.137`、`296.967 -> 265.301`、`326.631 -> 252.232`、`281.593 -> 257.435`、`282.624 -> 261.666 ms`。5/5均更快。

Stage 1五fork拼接SHA-256为`1107155d806daf7796690cd54ccf2540a93790247df10c39c245d98a9e1d4230`；Stage 2为`6fe8df1343af3df598f39b1eb562335ca111b46f1295627fd7b6822a903442bb`。

## 8. 解释与后续约束

本证据支持：

- 当前Candidate Scan实现没有出现已测lane的allocation或integrated性能回退；
- compact typed plan、source/terminal specialization、scalar best-one与Traversal命名清理实现了可测收益；
- code-size仍在专题Gate内，但source bytes已接近上限；
- GC count与allocation方向改善，GC time波动不足以形成收益claim。

本证据不支持跨JDK/OS/architecture/workload的普遍优势，也不替代正式support matrix。后续任何Scan surface或generator扩张必须重跑component、code-size和integrated多fork证据；不得通过删除语义、减少correctness guard或放宽阈值维持“通过”。
